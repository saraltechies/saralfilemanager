package com.saraltechies.saralfilemanager.files

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * The work behind copy, move, delete, zip and extract, on plain java.io so it runs (and is tested) off Android.
 * Set [cancelled] from any thread to stop between chunks; a half-written file is removed. [onBytes] reports
 * progress as bytes are written, and [onFile] the name being worked on.
 */
class FileJob(
    private val onBytes: (Long) -> Unit = {},
    private val onFile: (String) -> Unit = {},
) {
    @Volatile
    var cancelled = false

    /** Bytes that [copy], [move] (across storage), [zip] or [delete] will go through, for the progress bar. */
    fun totalBytes(sources: List<File>): Long = sources.sumOf { source ->
        source.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Copies [sources] into [destDir], renaming as "name (1)" where a name is taken. Returns the new top-level files. */
    fun copy(sources: List<File>, destDir: File): List<File> = sources.map { source ->
        checkNotInside(source, destDir)
        val target = File(destDir, freeName(destDir, source))
        copyRecursively(source, target)
        target
    }

    /**
     * Moves [sources] into [destDir]. Within one storage volume it's a rename; otherwise it copies and then deletes
     * the original. Items already in [destDir] are left alone.
     */
    fun move(sources: List<File>, destDir: File): List<File> = sources.mapNotNull { source ->
        checkNotInside(source, destDir)
        if (source.parentFile?.canonicalPath == destDir.canonicalPath) return@mapNotNull null
        val target = File(destDir, freeName(destDir, source))
        onFile(source.name)
        if (!source.renameTo(target)) {
            copyRecursively(source, target)
            checkCancelled()
            deleteRecursively(source)
        }
        target
    }

    /** Deletes files and whole folders. Throws if something couldn't be deleted (the rest is still removed). */
    fun delete(sources: List<File>) {
        var failed = 0
        for (source in sources) {
            checkCancelled()
            onFile(source.name)
            if (!deleteRecursively(source)) failed++
        }
        if (failed > 0) throw IOException("$failed item(s) could not be deleted")
    }

    /** Packs [sources] into [zipFile]. Paths inside the zip start at each source's own name. */
    fun zip(sources: List<File>, zipFile: File) {
        try {
            ZipOutputStream(FileOutputStream(zipFile).buffered()).use { out ->
                for (source in sources) {
                    val base = source.parentFile ?: source
                    source.walkTopDown().forEach { file ->
                        checkCancelled()
                        if (file.canonicalPath == zipFile.canonicalPath) return@forEach
                        val name = file.relativeTo(base).invariantSeparatorsPath
                        if (file.isDirectory) {
                            out.putNextEntry(ZipEntry("$name/").apply { time = file.lastModified() })
                            out.closeEntry()
                        } else {
                            onFile(file.name)
                            out.putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
                            FileInputStream(file).use { pump(it, out) }
                            out.closeEntry()
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            zipFile.delete()
            throw e
        }
    }

    /** Uncompressed size of [zipFile]'s entries, for the progress bar. */
    fun zipContentBytes(zipFile: File): Long =
        ZipFile(zipFile).use { zip -> zip.entries().asSequence().sumOf { it.size.coerceAtLeast(0) } }

    /**
     * Extracts [zipFile] into a new folder named after it inside [destDir] and returns that folder.
     * Entries that would land outside it ("../" tricks) are skipped.
     */
    fun extract(zipFile: File, destDir: File): File {
        val folderName = zipFile.name.substringBeforeLast('.').ifEmpty { zipFile.name }
        val folder = File(destDir, FileNames.unique(folderName, isDirectory = true) { File(destDir, it).exists() })
        if (!folder.mkdirs()) throw IOException("Couldn't create ${folder.name}")
        try {
            ZipFile(zipFile).use { zip ->
                for (entry in zip.entries()) {
                    checkCancelled()
                    val target = ZipPaths.target(folder, entry.name) ?: continue
                    if (entry.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    onFile(target.name)
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> writeFile(input, target) }
                    if (entry.time > 0) target.setLastModified(entry.time)
                }
            }
        } catch (e: CancellationException) {
            folder.deleteRecursively()
            throw e
        }
        return folder
    }

    private fun copyRecursively(source: File, target: File) {
        checkCancelled()
        if (source.isDirectory) {
            if (!target.mkdirs() && !target.isDirectory) throw IOException("Couldn't create ${target.name}")
            source.listFiles()?.forEach { copyRecursively(it, File(target, it.name)) }
                ?: throw IOException("Couldn't read ${source.name}")
        } else {
            onFile(source.name)
            FileInputStream(source).use { input -> writeFile(input, target) }
            target.setLastModified(source.lastModified())
        }
    }

    private fun writeFile(input: InputStream, target: File) {
        try {
            FileOutputStream(target).use { pump(input, it) }
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
    }

    private fun pump(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            checkCancelled()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            onBytes(read.toLong())
        }
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) file.listFiles()?.forEach { checkCancelled(); deleteRecursively(it) }
        return file.delete() || !file.exists()
    }

    private fun freeName(destDir: File, source: File) =
        FileNames.unique(source.name, source.isDirectory) { File(destDir, it).exists() }

    private fun checkNotInside(source: File, destDir: File) {
        if (ZipPaths.isInside(destDir, source)) throw IntoItselfException(source.name)
    }

    private fun checkCancelled() {
        if (cancelled) throw CancellationException("Cancelled")
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}

/** A folder can't be copied or moved into itself or one of its own subfolders. */
class IntoItselfException(val name: String) : IOException("Can't put $name inside itself")

object ZipPaths {
    /** Where entry [entryName] goes inside [destDir], or null when it would escape it ("zip slip"). */
    fun target(destDir: File, entryName: String): File? {
        val root = destDir.canonicalFile
        val out = File(root, entryName.replace('\\', '/')).canonicalFile
        return if (out != root && isInside(out, root)) out else null
    }

    /** True when [file] is [folder] itself or somewhere below it. */
    fun isInside(file: File, folder: File): Boolean {
        val f = file.canonicalPath
        val d = folder.canonicalPath
        return f == d || f.startsWith(d.trimEnd(File.separatorChar) + File.separator)
    }
}
