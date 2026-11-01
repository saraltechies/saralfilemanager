package com.saraltechies.saralfilemanager.files

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** A storage the phone has mounted: internal storage, an SD card or a USB drive. */
data class Volume(
    val name: String,
    val path: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
}

/** Result of reading a folder. */
sealed interface Listing {
    data object Loading : Listing
    data class Loaded(val items: List<FileItem>) : Listing
    /** The folder is gone, or Android doesn't let apps read it (Android/data on Android 11+). */
    data object Unreadable : Listing
}

/** Reads folders, storage volumes and the home screen's categories. Changes go through [FileOperations]. */
class FileRepository(private val context: Context) {

    val primaryRoot: File get() = Environment.getExternalStorageDirectory()

    fun volumes(): List<Volume> {
        val manager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return manager.storageVolumes.mapNotNull { volume ->
            if (volume.state != Environment.MEDIA_MOUNTED && volume.state != Environment.MEDIA_MOUNTED_READ_ONLY) {
                return@mapNotNull null
            }
            val dir = volume.directoryCompat() ?: return@mapNotNull null
            val stat = runCatching { StatFs(dir.path) }.getOrNull() ?: return@mapNotNull null
            Volume(
                name = volume.getDescription(context),
                path = dir.absolutePath,
                totalBytes = stat.totalBytes,
                freeBytes = stat.availableBytes,
                isPrimary = volume.isPrimary,
                isRemovable = volume.isRemovable,
            )
        }
    }

    /** The volume [path] is on, so the browser knows where "up" stops. */
    fun volumeRootOf(path: String): String =
        volumes().map { it.path }.filter { ZipPaths.isInside(File(path), File(it)) }.maxByOrNull { it.length }
            ?: primaryRoot.absolutePath

    suspend fun list(path: String, showHidden: Boolean, sort: Comparator<FileItem>): Listing = withContext(Dispatchers.IO) {
        val dir = File(path)
        val children = dir.listFiles() ?: return@withContext Listing.Unreadable
        Listing.Loaded(
            children.asSequence()
                .filter { showHidden || !it.name.startsWith(".") }
                .map(FileItem::from)
                .sortedWith(sort)
                .toList(),
        )
    }

    /** Files and folders below [root] whose name contains [query], streamed as they're found (up to [MAX_RESULTS]). */
    fun search(root: String, query: String, showHidden: Boolean): Flow<List<FileItem>> = flow {
        val needle = query.trim()
        if (needle.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        val found = mutableListOf<FileItem>()
        var lastEmit = 0
        File(root).walkTopDown()
            .onEnter { showHidden || !it.name.startsWith(".") || it.absolutePath == root }
            .forEach { file ->
                currentCoroutineContext().ensureActive()
                if (file.absolutePath == root || found.size >= MAX_RESULTS) return@forEach
                if (!showHidden && file.name.startsWith(".")) return@forEach
                if (file.name.contains(needle, ignoreCase = true)) {
                    found += FileItem.from(file)
                    if (found.size - lastEmit >= EMIT_EVERY) {
                        lastEmit = found.size
                        emit(found.toList())
                    }
                }
            }
        emit(found.toList())
    }.flowOn(Dispatchers.IO)

    /** Every file of [category] on the phone, newest first, from Android's media index. */
    suspend fun category(category: FileCategory, showHidden: Boolean, sort: Comparator<FileItem>): List<FileItem> =
        withContext(Dispatchers.IO) {
            val extensions = category.extensions.toList()
            if (extensions.isEmpty()) return@withContext emptyList()
            val selection = extensions.joinToString(" OR ", prefix = "(", postfix = ")") { "${MediaStore.MediaColumns.DATA} LIKE ?" }
            val args = extensions.map { "%.$it" }.toTypedArray()
            queryFiles(selection, args, limit = MAX_CATEGORY_ITEMS)
                .filter { showHidden || !it.path.contains("/.") }
                .sortedWith(sort)
        }

    /** Files changed in the last [RECENT_DAYS] days, newest first, without hidden files and folders. */
    suspend fun recent(): List<FileItem> = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() / 1000 - RECENT_DAYS * 24L * 60 * 60
        queryFiles(
            "${MediaStore.MediaColumns.DATE_MODIFIED} > ? AND ${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL",
            arrayOf(since.toString()),
            limit = MAX_RECENT,
        ).filter { !it.path.contains("/.") }
    }

    /**
     * How much space each kind of file takes, from Android's media index in one pass, plus the Download folder.
     * Hidden files count too: they take space all the same.
     */
    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        val totals = HashMap<FileCategory, Usage>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE),
                "${MediaStore.MediaColumns.SIZE} > 0", null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    ensureActive()
                    val path = cursor.getString(0) ?: continue
                    val category = FileCategory.of(path.substringAfterLast('/'))
                    if (category == FileCategory.OTHER) continue
                    totals[category] = (totals[category] ?: Usage()) + cursor.getLong(1)
                }
            }
        }
        var downloads = Usage()
        File(primaryRoot, Environment.DIRECTORY_DOWNLOADS).walkTopDown().forEach {
            ensureActive()
            if (it.isFile) downloads += it.length()
        }
        StorageUsage(totals, downloads)
    }

    /**
     * Files from Android's media index matching [selection], as [FileItem]s, in [order]. Hidden files and folders
     * and the app-private Android folder are left out, so cleaning suggestions never touch them.
     */
    suspend fun mediaFiles(selection: String, args: Array<String>, order: String, limit: Int = 5000): List<FileItem> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<FileItem>()
            runCatching {
                context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"), arrayOf(MediaStore.MediaColumns.DATA), selection, args, order,
                )?.use { cursor ->
                    while (cursor.moveToNext() && items.size < limit) {
                        ensureActive()
                        val path = cursor.getString(0) ?: continue
                        if (path.contains("/.") || path.contains("/Android/")) continue
                        val file = File(path)
                        if (file.isFile) items += FileItem.from(file)
                    }
                }
            }
            items
        }

    /** Size and number of files below [file], for the details dialog. */
    suspend fun measure(files: List<File>): Pair<Long, Int> = withContext(Dispatchers.IO) {
        var bytes = 0L
        var count = 0
        files.forEach { root ->
            root.walkTopDown().forEach {
                ensureActive()
                if (it.isFile) {
                    bytes += it.length()
                    count++
                }
            }
        }
        bytes to count
    }

    @Throws(IOException::class)
    suspend fun createFolder(parent: String, name: String): File = withContext(Dispatchers.IO) {
        val folder = File(parent, name)
        if (folder.exists()) throw FileAlreadyExistsException(folder)
        if (!folder.mkdir()) throw IOException("Couldn't create $name")
        folder
    }

    @Throws(IOException::class)
    suspend fun createFile(parent: String, name: String): File = withContext(Dispatchers.IO) {
        val file = File(parent, name)
        if (!file.createNewFile()) throw FileAlreadyExistsException(file)
        file
    }

    @Throws(IOException::class)
    suspend fun rename(file: File, newName: String): File = withContext(Dispatchers.IO) {
        val target = File(file.parentFile, newName)
        if (target.exists() && !target.path.equals(file.path, ignoreCase = true)) throw FileAlreadyExistsException(target)
        if (!file.renameTo(target)) throw IOException("Couldn't rename ${file.name}")
        target
    }

    private fun queryFiles(selection: String, args: Array<String>, limit: Int): List<FileItem> {
        val uri = MediaStore.Files.getContentUri("external")
        val columns = arrayOf(MediaStore.MediaColumns.DATA)
        val order = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        val items = mutableListOf<FileItem>()
        runCatching {
            context.contentResolver.query(uri, columns, selection, args, order)?.use { cursor ->
                while (cursor.moveToNext() && items.size < limit) {
                    val path = cursor.getString(0) ?: continue
                    val file = File(path)
                    if (file.isFile) items += FileItem.from(file)
                }
            }
        }
        return items
    }

    private fun StorageVolume.directoryCompat(): File? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> directory
        isPrimary -> Environment.getExternalStorageDirectory()
        else -> uuid?.let { File("/storage/$it") }?.takeIf { it.isDirectory }
    }

    private companion object {
        const val MAX_RESULTS = 500
        const val EMIT_EVERY = 20
        const val MAX_CATEGORY_ITEMS = 3000
        const val MAX_RECENT = 30
        const val RECENT_DAYS = 30
    }
}
