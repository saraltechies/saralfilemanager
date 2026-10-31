package com.saraltechies.saralfilemanager.files

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

enum class OperationKind { COPY, MOVE, DELETE, ZIP, EXTRACT }

data class OperationProgress(
    val kind: OperationKind,
    val itemCount: Int,
    val currentName: String,
    val doneBytes: Long,
    val totalBytes: Long,
) {
    /** 0..1, or null when the amount of work isn't known (deleting). */
    val fraction: Float? get() = if (totalBytes > 0) (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

sealed interface OperationResult {
    val kind: OperationKind

    /** [output] is the zip made, the folder extracted to, or the single item pasted. */
    data class Done(override val kind: OperationKind, val count: Int, val output: File?) : OperationResult
    data class Cancelled(override val kind: OperationKind) : OperationResult
    data class Failed(override val kind: OperationKind, val reason: Reason) : OperationResult

    enum class Reason { INTO_ITSELF, NO_SPACE, NOT_ALLOWED, OTHER }
}

/** What Copy or Cut put aside, waiting for Paste. */
data class Clip(val paths: List<String>, val move: Boolean)

/**
 * Runs one copy, move, delete, zip or extract at a time in the app's scope, so it carries on when the screen
 * that started it closes. Screens show [progress], react to [results] and reload on [changes].
 */
class FileOperations(private val context: Context, private val scope: CoroutineScope) {

    private val _progress = MutableStateFlow<OperationProgress?>(null)
    val progress: StateFlow<OperationProgress?> = _progress.asStateFlow()

    private val _results = MutableSharedFlow<OperationResult>(extraBufferCapacity = 8)
    val results: SharedFlow<OperationResult> = _results.asSharedFlow()

    private val _changes = MutableStateFlow(0)
    /** Bumped after anything changes on storage, so open listings reload. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private val _clip = MutableStateFlow<Clip?>(null)
    val clip: StateFlow<Clip?> = _clip.asStateFlow()

    @Volatile
    private var running: FileJob? = null

    fun setClip(paths: List<String>, move: Boolean) {
        _clip.value = Clip(paths, move)
    }

    fun clearClip() {
        _clip.value = null
    }

    fun paste(destDir: String) {
        val clip = _clip.value ?: return
        val sources = clip.paths.map(::File).filter { it.exists() }
        if (clip.move) _clip.value = null
        val dest = File(destDir)
        if (clip.move) {
            // Renames within a volume are instant; only a move to another storage copies bytes.
            start(OperationKind.MOVE, sources, measure = { job -> if (sameVolume(sources, dest)) 0L else job.totalBytes(sources) }) {
                it.move(sources, dest)
            }
        } else {
            start(OperationKind.COPY, sources, measure = { it.totalBytes(sources) }) { it.copy(sources, dest) }
        }
    }

    fun delete(paths: List<String>) {
        val sources = paths.map(::File)
        start(OperationKind.DELETE, sources, measure = { 0L }) {
            it.delete(sources)
            emptyList()
        }
    }

    fun zip(paths: List<String>, destDir: String, zipName: String) {
        val sources = paths.map(::File)
        val zipFile = File(destDir, FileNames.unique(zipName, isDirectory = false) { File(destDir, it).exists() })
        start(OperationKind.ZIP, sources, measure = { it.totalBytes(sources) }) {
            it.zip(sources, zipFile)
            listOf(zipFile)
        }
    }

    fun extract(zipPath: String, destDir: String) {
        val zip = File(zipPath)
        start(OperationKind.EXTRACT, listOf(zip), measure = { it.zipContentBytes(zip) }) {
            listOf(it.extract(zip, File(destDir)))
        }
    }

    fun cancel() {
        running?.cancelled = true
    }

    /** For quick changes made outside an operation (new folder, rename): reindex and reload. */
    fun notifyChanged(vararg files: File) {
        scanMedia(files.map { it.absolutePath })
        _changes.update { it + 1 }
    }

    private fun start(
        kind: OperationKind,
        sources: List<File>,
        measure: (FileJob) -> Long,
        work: (FileJob) -> List<File>,
    ) {
        if (_progress.value != null || sources.isEmpty()) return
        _progress.value = OperationProgress(kind, sources.size, "", 0, 0)
        scope.launch(Dispatchers.IO) {
            var done = 0L
            var lastUpdate = 0L
            var currentName = ""
            val job = FileJob(
                onBytes = { bytes ->
                    done += bytes
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= PROGRESS_INTERVAL_MS) {
                        lastUpdate = now
                        _progress.update { it?.copy(doneBytes = done, currentName = currentName) }
                    }
                },
                onFile = { name ->
                    currentName = name
                    _progress.update { it?.copy(currentName = name) }
                },
            )
            running = job
            // Media index entries for what's about to move or disappear, gathered before it does.
            val before = if (kind == OperationKind.MOVE || kind == OperationKind.DELETE) filePaths(sources) else emptyList()
            var outputs = emptyList<File>()
            val result = try {
                val total = runCatching { measure(job) }.getOrDefault(0L)
                _progress.update { it?.copy(totalBytes = total) }
                outputs = work(job)
                OperationResult.Done(kind, sources.size, outputs.singleOrNull())
            } catch (_: CancellationException) {
                OperationResult.Cancelled(kind)
            } catch (_: IntoItselfException) {
                OperationResult.Failed(kind, OperationResult.Reason.INTO_ITSELF)
            } catch (_: SecurityException) {
                OperationResult.Failed(kind, OperationResult.Reason.NOT_ALLOWED)
            } catch (e: IOException) {
                val noSpace = e.message?.contains("ENOSPC") == true || e.message?.contains("No space", ignoreCase = true) == true
                OperationResult.Failed(kind, if (noSpace) OperationResult.Reason.NO_SPACE else OperationResult.Reason.OTHER)
            } finally {
                running = null
                _progress.value = null
            }
            scanMedia(before + filePaths(outputs))
            _changes.update { it + 1 }
            _results.emit(result)
        }
    }

    private fun sameVolume(sources: List<File>, dest: File): Boolean {
        val destRoot = volumeRoot(dest)
        return sources.all { volumeRoot(it) == destRoot }
    }

    /** "/storage/emulated/0" or "/storage/1234-ABCD". */
    private fun volumeRoot(file: File): String {
        val parts = file.absolutePath.split('/').filter { it.isNotEmpty() }
        return when {
            parts.size >= 3 && parts[0] == "storage" && parts[1] == "emulated" -> parts.take(3).joinToString("/")
            parts.size >= 2 && parts[0] == "storage" -> parts.take(2).joinToString("/")
            else -> parts.firstOrNull().orEmpty()
        }
    }

    /** Files at and below [roots], capped so a huge folder doesn't stall the media scanner. */
    private fun filePaths(roots: List<File>): List<String> =
        roots.asSequence().flatMap { it.walkTopDown() }.filter { it.isFile }.map { it.absolutePath }.take(MAX_SCAN).toList()

    private fun scanMedia(paths: List<String>) {
        if (paths.isEmpty()) return
        runCatching { MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null) }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 150L
        const val MAX_SCAN = 2000
    }
}
