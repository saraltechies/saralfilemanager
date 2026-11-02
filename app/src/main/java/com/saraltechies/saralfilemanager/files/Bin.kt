package com.saraltechies.saralfilemanager.files

import java.io.File
import java.io.IOException
import java.util.UUID

/** Something in the Bin: where it came from, when it was deleted, and where it is kept. */
data class BinEntry(
    /** This entry's own folder inside the Bin. */
    val folder: File,
    /** The deleted file or folder itself. */
    val item: File,
    val originalPath: String,
    val deletedAt: Long,
    val bytes: Long,
) {
    val name: String get() = item.name
    val originalFolder: String get() = File(originalPath).parent ?: "/"

    fun daysLeft(now: Long): Int {
        val left = deletedAt + Bin.KEEP_DAYS * DAY_MS - now
        return if (left <= 0) 0 else ((left + DAY_MS - 1) / DAY_MS).toInt()
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/**
 * Deleted files wait here for [KEEP_DAYS] days before they're really gone. Each storage keeps its own hidden Bin
 * folder, so moving a file there is an instant rename whatever its size, and Android's media index ignores it.
 *
 * ```
 * .SaralFilesBin/.nomedia
 * .SaralFilesBin/<when>-<id>/info        original path, then the time it was deleted
 * .SaralFilesBin/<when>-<id>/data/<name> the file or folder
 * ```
 */
object Bin {

    const val FOLDER = ".SaralFilesBin"
    const val KEEP_DAYS = 30
    private const val INFO = "info"
    private const val DATA = "data"

    /** Moves [file] into the Bin of the storage at [volumeRoot]. Throws if it can't be moved there. */
    fun moveIn(file: File, volumeRoot: File, now: Long = System.currentTimeMillis()): BinEntry {
        val bin = File(volumeRoot, FOLDER)
        if (!bin.isDirectory && !bin.mkdirs()) throw IOException("Can't create the Bin on $volumeRoot")
        // Keeps photos in the Bin out of gallery apps.
        File(bin, ".nomedia").let { if (!it.exists()) runCatching { it.createNewFile() } }
        if (ZipPaths.isInside(file, bin) || ZipPaths.isInside(bin, file)) throw IOException("Can't put ${file.name} in the Bin")
        val folder = File(bin, now.toString() + "-" + UUID.randomUUID().toString().take(8))
        val data = File(folder, DATA)
        if (!data.mkdirs()) throw IOException("Can't create a place in the Bin for ${file.name}")
        val bytes = sizeOf(file)
        File(folder, INFO).writeText(file.absolutePath + "\n" + now)
        val target = File(data, file.name)
        if (!file.renameTo(target)) {
            folder.deleteRecursively()
            throw IOException("Can't move ${file.name} to the Bin")
        }
        return BinEntry(folder, target, file.absolutePath, now, bytes)
    }

    /** Everything in the Bins of [volumeRoots], latest first. */
    fun list(volumeRoots: List<File>): List<BinEntry> = volumeRoots
        .flatMap { File(it, FOLDER).listFiles()?.toList().orEmpty() }
        .mapNotNull(::read)
        .sortedByDescending { it.deletedAt }

    /** Puts an entry back where it came from, as "name (1)" if that name has been taken since. Returns where it went. */
    fun restore(entry: BinEntry): File {
        val parent = File(entry.originalPath).parentFile ?: throw IOException("No folder to restore ${entry.name} to")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Can't recreate $parent")
        val target = File(parent, FileNames.unique(entry.name, entry.item.isDirectory) { File(parent, it).exists() })
        if (!entry.item.renameTo(target)) throw IOException("Can't restore ${entry.name}")
        entry.folder.deleteRecursively()
        return target
    }

    /** Removes an entry for good. False if some of it couldn't be removed. */
    fun deleteForever(entry: BinEntry): Boolean = entry.folder.deleteRecursively()

    /** Removes what has been in the Bin for longer than [KEEP_DAYS]. Returns how many were removed. */
    fun purgeExpired(volumeRoots: List<File>, now: Long = System.currentTimeMillis()): Int =
        list(volumeRoots).filter { it.daysLeft(now) == 0 }.count(::deleteForever)

    private fun read(folder: File): BinEntry? {
        if (!folder.isDirectory) return null
        val lines = runCatching { File(folder, INFO).readLines() }.getOrNull() ?: return null
        val path = lines.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val item = File(folder, DATA).listFiles()?.firstOrNull() ?: return null
        return BinEntry(folder, item, path, lines.getOrNull(1)?.toLongOrNull() ?: folder.lastModified(), sizeOf(item))
    }

    private fun sizeOf(file: File): Long = file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
