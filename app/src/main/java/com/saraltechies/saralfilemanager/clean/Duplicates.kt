package com.saraltechies.saralfilemanager.clean

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Finds files with the same content. Files are first grouped by size (free), then by a hash of their first and last
 * [EDGE] bytes (cheap), and only then by a hash of the whole file, so big files are read in full only when they
 * really might be copies.
 */
object Duplicates {

    private const val EDGE = 16 * 1024

    /**
     * The copy to keep comes first in each group: the oldest file (most likely the original), then the shorter
     * path (a copy is usually deeper, like "Download/WhatsApp/…"), then by name, so the choice is always the same.
     */
    val keepFirst: Comparator<File> = compareBy<File>({ it.lastModified() }, { it.path.length }, { it.path })

    /** Groups of two or more identical files, each sorted with [keepFirst]. [checkCancelled] is called often. */
    fun find(files: List<File>, minBytes: Long = 1, checkCancelled: () -> Unit = {}): List<List<File>> =
        files.asSequence()
            .filter { it.isFile }
            .distinctBy { it.absolutePath }
            .map { it to it.length() }
            .filter { it.second >= minBytes }
            .groupBy({ it.second }, { it.first })
            .values
            .filter { it.size > 1 }
            .flatMap { sameSize ->
                sameSize.groupBy { checkCancelled(); hash(it, edgesOnly = true) }.values.filter { it.size > 1 }
                    .flatMap { sameEdges ->
                        // Files no bigger than the two edges were read whole already.
                        if (sameEdges.first().length() <= 2L * EDGE) listOf(sameEdges)
                        else sameEdges.groupBy { checkCancelled(); hash(it, edgesOnly = false) }.values.filter { it.size > 1 }
                    }
            }
            .map { it.sortedWith(keepFirst) }
            .sortedByDescending { group -> group.first().length() * (group.size - 1) }

    /** Bytes freed by deleting every copy but the first in each group. */
    fun freeable(groups: List<List<File>>): Long = groups.sumOf { group -> group.drop(1).sumOf { it.length() } }

    private fun hash(file: File, edgesOnly: Boolean): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            if (edgesOnly) {
                readInto(raf, 0, minOf(EDGE.toLong(), length), digest, buffer)
                if (length > EDGE) readInto(raf, maxOf(EDGE.toLong(), length - EDGE), length, digest, buffer)
            } else {
                readInto(raf, 0, length, digest, buffer)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrElse { "unreadable:" + file.absolutePath } // An unreadable file never matches another.

    private fun readInto(raf: RandomAccessFile, from: Long, to: Long, digest: MessageDigest, buffer: ByteArray) {
        raf.seek(from)
        var left = to - from
        while (left > 0) {
            val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read <= 0) break
            digest.update(buffer, 0, read)
            left -= read
        }
    }
}
