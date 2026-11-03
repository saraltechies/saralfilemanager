package com.saraltechies.saralfilemanager.files

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.CoroutineScope
import com.saraltechies.saralfilemanager.util.FileOperationService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

enum class OperationKind { COPY, MOVE, DELETE, ZIP, EXTRACT, TRASH, RESTORE }

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
    data class Done(
        override val kind: OperationKind,
        val count: Int,
        val output: File?,
        /** What was just moved to the Bin, for Undo. */
        val binned: List<BinEntry> = emptyList(),
    ) : OperationResult
    data class Cancelled(override val kind: OperationKind) : OperationResult
    data class Failed(override val kind: OperationKind, val reason: Reason) : OperationResult

    enum class Reason { INTO_ITSELF, NO_SPACE, NOT_ALLOWED, BUSY, OTHER }
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
        // A cut stays ready to paste until there's room to do it.
        if (refuseWhileBusy(if (clip.move) OperationKind.MOVE else OperationKind.COPY)) return
        val sources = clip.paths.map(::File).filter { it.exists() }
        if (clip.move) _clip.value = null
        val dest = File(destDir)
        if (clip.move) {
            // Renames within a volume are instant; only a move to another storage copies bytes.
            val across = !sameVolume(sources, dest)
            start(OperationKind.MOVE, sources, measure = { job -> if (across) job.totalBytes(sources) else 0L }, long = across) {
                it.move(sources, dest)
            }
        } else {
            start(OperationKind.COPY, sources, measure = { it.totalBytes(sources) }, long = true) { it.copy(sources, dest) }
        }
    }

    fun delete(paths: List<String>) {
        val sources = paths.map(::File)
        start(OperationKind.DELETE, sources, measure = { 0L }) {
            it.delete(sources)
            emptyList()
        }
    }

    /** Moves to the Bin of each file's own storage, where it stays for [Bin.KEEP_DAYS] days. */
    fun trash(paths: List<String>) {
        val sources = paths.map(::File)
        val moved = ArrayList<BinEntry>()
        start(OperationKind.TRASH, sources, measure = { 0L }, binned = moved) { job ->
            var failed = 0
            for (source in sources) {
                if (job.cancelled) throw CancellationException("Cancelled")
                job.announce(source.name)
                try {
                    moved += Bin.moveIn(source, File("/" + volumeRoot(source)))
                } catch (_: IOException) {
                    failed++
                }
            }
            if (failed > 0) throw IOException("$failed item(s) could not be moved to the Bin")
            emptyList()
        }
    }

    /** Puts things from the Bin back where they came from. */
    fun restore(entries: List<BinEntry>) {
        start(OperationKind.RESTORE, entries.map { it.item }, measure = { 0L }) { job ->
            var failed = 0
            val restored = ArrayList<File>()
            for (entry in entries) {
                if (job.cancelled) throw CancellationException("Cancelled")
                job.announce(entry.name)
                try {
                    restored += Bin.restore(entry)
                } catch (_: IOException) {
                    failed++
                }
            }
            if (failed > 0) {
                scanMedia(filePaths(restored))
                throw IOException("$failed item(s) could not be restored")
            }
            restored
        }
    }

    /** Removes things from the Bin for good. */
    fun deleteForever(entries: List<BinEntry>) {
        start(OperationKind.DELETE, entries.map { it.item }, measure = { 0L }) { job ->
            job.delete(entries.map { it.folder })
            emptyList()
        }
    }

    fun zip(paths: List<String>, destDir: String, zipName: String) {
        val sources = paths.map(::File)
        val zipFile = File(destDir, FileNames.unique(zipName, isDirectory = false) { File(destDir, it).exists() })
        start(OperationKind.ZIP, sources, measure = { it.totalBytes(sources) }, long = true) {
            it.zip(sources, zipFile)
            listOf(zipFile)
        }
    }

    fun extract(zipPath: String, destDir: String) {
        val zip = File(zipPath)
        start(OperationKind.EXTRACT, listOf(zip), measure = { it.zipContentBytes(zip) }, long = true) {
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
        binned: List<BinEntry> = emptyList(),
        /** Likely to take a while, so the notification shows at once. */
        long: Boolean = false,
        work: (FileJob) -> List<File>,
    ) {
        if (refuseWhileBusy(kind) || sources.isEmpty()) return
        _progress.value = OperationProgress(kind, sources.size, "", 0, 0)
        // Work that goes on for more than a moment shows a notification, so Android lets it finish after you leave the
        // app. Android only allows starting it while the app is open, so long work can't wait to see how it goes.
        if (long) {
            FileOperationService.start(context)
        } else {
            scope.launch {
                delay(SERVICE_DELAY_MS)
                if (_progress.value != null) FileOperationService.start(context)
            }
        }
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
            val before = if (kind == OperationKind.MOVE || kind == OperationKind.DELETE || kind == OperationKind.TRASH) filePaths(sources) else emptyList()
            var outputs = emptyList<File>()
            val result = try {
                val total = runCatching { measure(job) }.getOrDefault(0L)
                _progress.update { it?.copy(totalBytes = total) }
                outputs = work(job)
                OperationResult.Done(kind, sources.size, outputs.singleOrNull(), binned)
            } catch (_: CancellationException) {
                OperationResult.Cancelled(kind)
            } catch (_: IntoItselfException) {
                OperationResult.Failed(kind, OperationResult.Reason.INTO_ITSELF)
            } catch (_: SecurityException) {
                OperationResult.Failed(kind, OperationResult.Reason.NOT_ALLOWED)
            } catch (e: IOException) {
                val noSpace = e.message?.contains("ENOSPC") == true || e.message?.contains("No space", ignoreCase = true) == true
                OperationResult.Failed(kind, if (noSpace) OperationResult.Reason.NO_SPACE else OperationResult.Reason.OTHER)
            } catch (_: Exception) {
                // Such as a zip with a badly formed name inside: say it failed rather than close the app.
                OperationResult.Failed(kind, OperationResult.Reason.OTHER)
            } finally {
                running = null
                _progress.value = null
            }
            // Reload now for what's read from disk, and again once the media index, which galleries read, has caught up.
            val changed = before + filePaths(outputs)
            _changes.update { it + 1 }
            _results.emit(result)
            if (scanAndWait(changed)) _changes.update { it + 1 }
        }
    }

    /** One thing runs at a time. True, after saying so, if something is running. */
    private fun refuseWhileBusy(kind: OperationKind): Boolean {
        if (_progress.value == null) return false
        _results.tryEmit(OperationResult.Failed(kind, OperationResult.Reason.BUSY))
        return true
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

    /** Files at and below [roots], up to a limit far beyond what a phone holds, as "select all" can be everything. */
    private fun filePaths(roots: List<File>): List<String> =
        roots.asSequence().flatMap { it.walkTopDown() }.filter { it.isFile }.map { it.absolutePath }.take(MAX_SCAN).toList()

    /** True if there was anything to scan. Gives up waiting after a while, as the index may be busy. */
    private suspend fun scanAndWait(paths: List<String>): Boolean {
        if (paths.isEmpty()) return false
        val left = AtomicInteger(paths.size)
        val done = CompletableDeferred<Unit>()
        runCatching {
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                if (left.decrementAndGet() == 0) done.complete(Unit)
            }
        }.onFailure { return false }
        withTimeoutOrNull(SCAN_WAIT_MS) { done.await() }
        return true
    }

    private fun scanMedia(paths: List<String>) {
        if (paths.isEmpty()) return
        runCatching { MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null) }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 150L
        const val SERVICE_DELAY_MS = 800L
        const val MAX_SCAN = 100_000
        const val SCAN_WAIT_MS = 5L * 60 * 1000
    }
}
