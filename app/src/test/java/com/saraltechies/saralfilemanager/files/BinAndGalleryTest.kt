package com.saraltechies.saralfilemanager.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class BinAndGalleryTest {

    @get:Rule val tmp = TemporaryFolder()
    private val day = 24L * 60 * 60 * 1000

    private fun file(path: String, text: String = "hello"): File =
        File(tmp.root, path).apply {
            parentFile?.mkdirs()
            writeText(text)
        }

    @Test
    fun movingToTheBinAndBack() {
        val photo = file("DCIM/photo.jpg")
        val entry = Bin.moveIn(photo, tmp.root, now = 1000)
        assertFalse(photo.exists())
        assertEquals("hello", entry.item.readText())
        assertEquals(5L, entry.bytes)
        assertTrue(File(tmp.root, Bin.FOLDER + "/.nomedia").exists())

        val listed = Bin.list(listOf(tmp.root)).single()
        assertEquals(photo.absolutePath, listed.originalPath)
        assertEquals(1000L, listed.deletedAt)
        assertEquals("photo.jpg", listed.name)

        val back = Bin.restore(listed)
        assertEquals(photo.absolutePath, back.absolutePath)
        assertEquals("hello", photo.readText())
        assertTrue(Bin.list(listOf(tmp.root)).isEmpty())
    }

    @Test
    fun aWholeFolderGoesAndComesBack() {
        file("Trip/a.jpg")
        file("Trip/sub/b.jpg", "longer text")
        val entry = Bin.moveIn(File(tmp.root, "Trip"), tmp.root)
        assertFalse(File(tmp.root, "Trip").exists())
        assertEquals(16L, entry.bytes)
        Bin.restore(entry)
        assertEquals("longer text", File(tmp.root, "Trip/sub/b.jpg").readText())
    }

    @Test
    fun restoringWhenTheNameIsTakenKeepsBoth() {
        val photo = file("DCIM/photo.jpg", "old")
        val entry = Bin.moveIn(photo, tmp.root)
        file("DCIM/photo.jpg", "new")
        val back = Bin.restore(entry)
        assertEquals("photo (1).jpg", back.name)
        assertEquals("old", back.readText())
        assertEquals("new", photo.readText())
    }

    @Test
    fun restoringWhenTheFolderIsGoneMakesItAgain() {
        val photo = file("Gone/deep/photo.jpg")
        val entry = Bin.moveIn(photo, tmp.root)
        File(tmp.root, "Gone").deleteRecursively()
        assertEquals(photo.absolutePath, Bin.restore(entry).absolutePath)
    }

    @Test
    fun theSameNameTwiceStaysApart() {
        val first = Bin.moveIn(file("a/x.txt", "one"), tmp.root, now = 1)
        val second = Bin.moveIn(file("b/x.txt", "two"), tmp.root, now = 2)
        assertEquals(listOf(second.originalPath, first.originalPath), Bin.list(listOf(tmp.root)).map { it.originalPath })
    }

    @Test
    fun theBinCanNotGoInItself() {
        File(tmp.root, Bin.FOLDER).mkdirs()
        assertTrue(runCatching { Bin.moveIn(File(tmp.root, Bin.FOLDER), tmp.root) }.isFailure)
        assertTrue(runCatching { Bin.moveIn(tmp.root, tmp.root) }.isFailure)
    }

    @Test
    fun daysLeftAndExpiry() {
        val now = 100 * day
        val entry = Bin.moveIn(file("x.txt"), tmp.root, now = now)
        assertEquals(30, entry.daysLeft(now))
        assertEquals(1, entry.daysLeft(now + 29 * day + 1))
        assertEquals(0, entry.daysLeft(now + 30 * day))
        assertEquals(0, Bin.purgeExpired(listOf(tmp.root), now + 29 * day))
        assertEquals(1, Bin.list(listOf(tmp.root)).size)
        assertEquals(1, Bin.purgeExpired(listOf(tmp.root), now + 31 * day))
        assertTrue(Bin.list(listOf(tmp.root)).isEmpty())
    }

    @Test
    fun aBrokenBinEntryIsIgnored() {
        File(tmp.root, Bin.FOLDER + "/junk").mkdirs()
        assertTrue(Bin.list(listOf(tmp.root)).isEmpty())
    }

    private fun media(path: String, date: LocalDate, bytes: Long = 1, hour: Int = 10) = MediaFile(
        FileItem(path, path.substringAfterLast('/'), false, bytes, date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()),
    )

    private val oct3 = LocalDate.of(2026, 10, 3)
    private val oct5 = LocalDate.of(2026, 10, 5)
    private val sep9 = LocalDate.of(2026, 9, 9)

    @Test
    fun daysAndMonths() {
        val items = Gallery.sorted(listOf(media("/a/1.jpg", sep9), media("/a/2.jpg", oct3), media("/a/3.jpg", oct5), media("/a/4.jpg", oct5, hour = 11)), GallerySort.NEWEST)
        val days = Gallery.days(items, ZoneOffset.UTC)
        assertEquals(listOf(oct5, oct3, sep9), days.map { it.date })
        assertEquals(listOf("/a/4.jpg", "/a/3.jpg"), days[0].items.map { it.path })
        assertEquals(YearMonth.of(2026, 10), days[0].month)
        assertNull(days[1].month)
        assertEquals(YearMonth.of(2026, 9), days[2].month)
    }

    @Test
    fun sorting() {
        val a = media("/x/b.jpg", oct3, bytes = 5)
        val b = media("/x/A.jpg", oct5, bytes = 9)
        assertEquals(listOf(b, a), Gallery.sorted(listOf(a, b), GallerySort.NEWEST))
        assertEquals(listOf(a, b), Gallery.sorted(listOf(b, a), GallerySort.OLDEST))
        assertEquals(listOf(b, a), Gallery.sorted(listOf(a, b), GallerySort.LARGEST))
        assertEquals(listOf(a, b), Gallery.sorted(listOf(b, a), GallerySort.SMALLEST))
        assertEquals(listOf(b, a), Gallery.sorted(listOf(a, b), GallerySort.NAME))
    }

    @Test
    fun albumsAndTypes() {
        val items = listOf(
            media("/s/Camera/1.jpg", oct3, 3), media("/s/Camera/2.png", oct5, 4), media("/s/WhatsApp/3.jpg", sep9, 1),
        )
        val albums = Gallery.albums(items)
        assertEquals(listOf("Camera", "WhatsApp"), albums.map { it.name })
        assertEquals(7L, albums[0].bytes)
        assertEquals("/s/Camera/2.png", albums[0].cover.path)
        assertEquals(listOf("jpg", "png"), Gallery.types(items))
    }

    @Test
    fun draggingSelectsARangeAndCanComeBack() {
        val order = listOf("a", "b", "c", "d", "e")
        assertEquals(setOf("z", "b", "c", "d"), Gallery.dragSelection(order, setOf("z"), 1, 3))
        assertEquals(setOf("b", "c", "d"), Gallery.dragSelection(order, emptySet(), 3, 1))
        // Coming back to the start leaves only the start and what was selected before.
        assertEquals(setOf("z", "b"), Gallery.dragSelection(order, setOf("z"), 1, 1))
        assertEquals(setOf("z"), Gallery.dragSelection(order, setOf("z"), 1, 9))
    }

    @Test
    fun durations() {
        assertEquals("0:05", Gallery.duration(5_000))
        assertEquals("1:05", Gallery.duration(65_000))
        assertEquals("1:02:05", Gallery.duration(3_725_000))
    }

    @Test
    fun columnsStayInLimits() {
        assertEquals(2, Gallery.columns(1))
        assertEquals(6, Gallery.columns(9))
        assertEquals(4, Gallery.columns(4))
    }
}
