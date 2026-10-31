package com.saraltechies.saralfilemanager.files

import java.io.File

/** One entry in a listing, read once from the file system. */
data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    /** Entries inside a folder (-1 when it can't be read); 0 for files. */
    val childCount: Int = 0,
) {
    val file: File get() = File(path)
    val isHidden: Boolean get() = name.startsWith(".")
    val extension: String get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
    val category: FileCategory get() = if (isDirectory) FileCategory.OTHER else FileCategory.of(name)

    companion object {
        fun from(file: File): FileItem {
            val dir = file.isDirectory
            return FileItem(
                path = file.absolutePath,
                name = file.name,
                isDirectory = dir,
                size = if (dir) 0L else file.length(),
                lastModified = file.lastModified(),
                childCount = if (dir) file.list()?.size ?: -1 else 0,
            )
        }
    }
}
