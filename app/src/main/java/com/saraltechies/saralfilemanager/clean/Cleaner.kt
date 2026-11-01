package com.saraltechies.saralfilemanager.clean

import android.os.Environment
import android.provider.MediaStore
import com.saraltechies.saralfilemanager.files.FileItem
import com.saraltechies.saralfilemanager.files.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** The kinds of space-saving suggestions on the Clean screen. */
enum class CleanKind { DUPLICATES, LARGE, SCREENSHOTS, DOWNLOADS }

/**
 * One suggestion: the files it lists, grouped (duplicates come in groups of copies; the others are one group), and
 * which of them are ticked to start with (only the extra copies of duplicates; everything else is the person's
 * choice).
 */
data class CleanSuggestion(val kind: CleanKind, val groups: List<List<FileItem>>, val preselected: Set<String>) {
    val files: List<FileItem> get() = groups.flatten()
    val totalBytes: Long get() = files.sumOf { it.size }

    /** What deleting the ticked files frees, or for a list with nothing ticked, everything it lists. */
    val freeableBytes: Long
        get() = if (preselected.isEmpty()) totalBytes else files.filter { it.path in preselected }.sumOf { it.size }
}

/** Finds files worth removing: copies, very large files, old screenshots and downloads. Nothing is deleted here. */
class Cleaner(private val files: FileRepository) {

    suspend fun suggestion(kind: CleanKind): CleanSuggestion = when (kind) {
        CleanKind.DUPLICATES -> duplicates()
        CleanKind.LARGE -> single(kind, large())
        CleanKind.SCREENSHOTS -> single(kind, oldScreenshots())
        CleanKind.DOWNLOADS -> single(kind, downloads())
    }

    private fun single(kind: CleanKind, items: List<FileItem>) = CleanSuggestion(kind, if (items.isEmpty()) emptyList() else listOf(items), emptySet())

    /** Files of [LARGE_BYTES] or more, biggest first. */
    private suspend fun large(): List<FileItem> = files.mediaFiles(
        "${MediaStore.MediaColumns.SIZE} >= ?", arrayOf(LARGE_BYTES.toString()), "${MediaStore.MediaColumns.SIZE} DESC", limit = 500,
    )

    /** Screenshots older than [OLD_DAYS] days, oldest first. */
    private suspend fun oldScreenshots(): List<FileItem> {
        val before = System.currentTimeMillis() / 1000 - OLD_DAYS * 24L * 60 * 60
        return files.mediaFiles(
            "${MediaStore.MediaColumns.DATA} LIKE ? AND ${MediaStore.MediaColumns.DATE_MODIFIED} < ?",
            arrayOf("%/Screenshots/%", before.toString()),
            "${MediaStore.MediaColumns.DATE_MODIFIED} ASC",
        )
    }

    /** Everything in the Download folder (and below), biggest first. */
    private suspend fun downloads(): List<FileItem> = withContext(Dispatchers.IO) {
        File(files.primaryRoot, Environment.DIRECTORY_DOWNLOADS).walkTopDown()
            .onEnter { !it.name.startsWith(".") }
            .filter { it.isFile && !it.name.startsWith(".") }
            .map { ensureActive(); FileItem.from(it) }
            .sortedByDescending { it.size }
            .toList()
    }

    /** Identical files of [MIN_DUPLICATE_BYTES] or more; the copies after the first in each group are ticked. */
    private suspend fun duplicates(): CleanSuggestion = withContext(Dispatchers.IO) {
        val candidates = files.mediaFiles(
            "${MediaStore.MediaColumns.SIZE} >= ?", arrayOf(MIN_DUPLICATE_BYTES.toString()), "${MediaStore.MediaColumns.SIZE} DESC", limit = 20_000,
        )
        val groups = Duplicates.find(candidates.map { it.file }, MIN_DUPLICATE_BYTES) { ensureActive() }
            .map { group -> group.map(FileItem::from) }
        CleanSuggestion(CleanKind.DUPLICATES, groups, groups.flatMap { it.drop(1) }.map { it.path }.toSet())
    }

    companion object {
        const val LARGE_BYTES = 100L * 1024 * 1024
        const val MIN_DUPLICATE_BYTES = 100L * 1024
        const val OLD_DAYS = 30
    }
}
