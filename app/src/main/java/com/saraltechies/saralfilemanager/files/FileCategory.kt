package com.saraltechies.saralfilemanager.files

/** The groups on the home screen. Decided by file extension, the same way for listings and for MediaStore queries. */
enum class FileCategory(val extensions: Set<String>) {
    IMAGE(setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "avif", "dng")),
    VIDEO(setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v", "ts")),
    AUDIO(setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr", "mid", "midi")),
    DOCUMENT(
        setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
            "txt", "rtf", "csv", "md", "epub", "html", "htm", "json", "xml",
        ),
    ),
    ARCHIVE(setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")),
    APK(setOf("apk", "apks", "xapk")),
    OTHER(emptySet());

    companion object {
        private val byExtension: Map<String, FileCategory> =
            entries.flatMap { category -> category.extensions.map { it to category } }.toMap()

        fun of(fileName: String): FileCategory =
            byExtension[fileName.substringAfterLast('.', "").lowercase()] ?: OTHER
    }
}
