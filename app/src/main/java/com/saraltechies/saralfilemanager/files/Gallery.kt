package com.saraltechies.saralfilemanager.files

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A photo or video from Android's media index. [id] is its index entry (0 if it has none). */
data class MediaFile(val item: FileItem, val id: Long = 0, val durationMs: Long = 0) {
    val path: String get() = item.path
    val folder: String get() = File(item.path).parent ?: "/"
}

enum class GallerySort { NEWEST, OLDEST, LARGEST, SMALLEST, NAME }

/** The photos or videos of one day. [month] is set on the first day of a month, for a heading above it. */
data class GalleryDay(val date: LocalDate, val items: List<MediaFile>, val month: YearMonth? = null)

/** A folder holding photos or videos, with the newest as its cover. */
data class GalleryAlbum(val folder: String, val name: String, val items: List<MediaFile>) {
    val bytes: Long get() = items.sumOf { it.item.size }
    val cover: MediaFile get() = items.maxBy { it.item.lastModified }
}

/** Sorting, grouping and filtering for the gallery. */
object Gallery {

    fun sorted(items: List<MediaFile>, sort: GallerySort): List<MediaFile> = when (sort) {
        GallerySort.NEWEST -> items.sortedByDescending { it.item.lastModified }
        GallerySort.OLDEST -> items.sortedBy { it.item.lastModified }
        GallerySort.LARGEST -> items.sortedByDescending { it.item.size }
        GallerySort.SMALLEST -> items.sortedBy { it.item.size }
        GallerySort.NAME -> items.sortedBy { it.item.name.lowercase() }
    }

    /** Whether this order reads by date, so days can be shown as headings. */
    fun groupsByDay(sort: GallerySort) = sort == GallerySort.NEWEST || sort == GallerySort.OLDEST

    /** [items], already sorted by date, split by day. */
    fun days(items: List<MediaFile>, zone: ZoneId = ZoneId.systemDefault()): List<GalleryDay> {
        val out = ArrayList<GalleryDay>()
        var month: YearMonth? = null
        var day: LocalDate? = null
        var current = ArrayList<MediaFile>()
        fun flush() {
            val d = day ?: return
            val ym = YearMonth.from(d)
            out += GalleryDay(d, current, if (ym != month) ym else null)
            month = ym
        }
        for (item in items) {
            val date = Instant.ofEpochMilli(item.item.lastModified).atZone(zone).toLocalDate()
            if (date != day) {
                flush()
                day = date
                current = ArrayList()
            }
            current.add(item)
        }
        flush()
        return out
    }

    /** Folders with photos or videos, biggest collections first. */
    fun albums(items: List<MediaFile>): List<GalleryAlbum> = items.groupBy { it.folder }
        .map { (folder, list) -> GalleryAlbum(folder, File(folder).name.ifEmpty { folder }, list) }
        .sortedWith(compareByDescending<GalleryAlbum> { it.items.size }.thenBy { it.name.lowercase() })

    /** The file types present, most common first, to filter by. */
    fun types(items: List<MediaFile>): List<String> = items.groupingBy { it.item.extension }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }.filter { it.isNotEmpty() }

    /**
     * What a drag from [from] to [to] selects: [before], the selection when the drag began, plus everything between
     * the two, either way round. Dragging back unselects what the drag itself had added.
     */
    fun dragSelection(order: List<String>, before: Set<String>, from: Int, to: Int): Set<String> {
        if (from !in order.indices || to !in order.indices) return before
        return before + order.subList(minOf(from, to), maxOf(from, to) + 1)
    }

    /** "1:05", or "1:02:05" for an hour or more. */
    fun duration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:" + m.toString().padStart(2, '0') + ":" + ss else "$m:$ss"
    }

    /** Columns after a pinch; always between the two limits. */
    fun columns(value: Int): Int = value.coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    const val MIN_COLUMNS = 2
    const val MAX_COLUMNS = 6
    const val DEFAULT_COLUMNS = 4
}
