package com.saraltechies.saralfilemanager.files

/** Space taken by some files, and how many there are. */
data class Usage(val bytes: Long = 0, val count: Int = 0) {
    operator fun plus(fileBytes: Long) = Usage(bytes + fileBytes, count + 1)
}

/** The sizes on the home screen's category tiles. */
data class StorageUsage(val byCategory: Map<FileCategory, Usage> = emptyMap(), val downloads: Usage = Usage()) {
    operator fun get(category: FileCategory): Usage = byCategory[category] ?: Usage()
}
