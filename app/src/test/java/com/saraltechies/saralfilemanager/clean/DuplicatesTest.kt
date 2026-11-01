package com.saraltechies.saralfilemanager.clean

import com.saraltechies.saralfilemanager.apps.AppInfo
import com.saraltechies.saralfilemanager.apps.AppsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

class DuplicatesTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun file(path: String, bytes: ByteArray, modified: Long): File =
        File(tmp.root, path).apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
            setLastModified(modified)
        }

    @Test
    fun findsCopiesAndKeepsTheOldest() {
        val photo = Random(1).nextBytes(50_000)
        val original = file("DCIM/photo.jpg", photo, 1_000_000)
        val copy1 = file("Download/photo.jpg", photo, 2_000_000)
        val copy2 = file("Download/WhatsApp/photo (1).jpg", photo, 3_000_000)
        val other = file("DCIM/other.jpg", Random(2).nextBytes(50_000), 1_000_000)

        val groups = Duplicates.find(listOf(copy2, other, copy1, original))

        assertEquals(1, groups.size)
        assertEquals(listOf(original, copy1, copy2), groups[0])
        assertEquals(100_000L, Duplicates.freeable(groups))
    }

    @Test
    fun keepsTheCameraPhotoOverAnOlderForwardedCopy() {
        val photo = Random(5).nextBytes(40_000)
        val forwarded = file("Download/WhatsApp Images/IMG-20261020-WA0001.jpg", photo, 1_000)
        val camera = file("DCIM/Camera/IMG_20261021_101512.jpg", photo, 5_000)
        assertEquals(listOf(camera, forwarded), Duplicates.find(listOf(forwarded, camera)).single())
        assertEquals(2, Duplicates.copyLikeness(File("/sdcard/DCIM/photo (2).jpg")))
        assertEquals(0, Duplicates.copyLikeness(File("/sdcard/Pictures/Holi 2026.jpg")))
    }

    @Test
    fun sameSizeAndEdgesButDifferentMiddleAreNotCopies() {
        // Big files that match at the start and end but differ in the middle need the full comparison.
        val a = Random(3).nextBytes(200_000)
        val b = a.copyOf().also { it[100_000] = (it[100_000] + 1).toByte() }
        val groups = Duplicates.find(listOf(file("a.bin", a, 1), file("b.bin", b, 2)))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun smallFilesAndTheSameFileTwiceAreIgnored() {
        val bytes = Random(4).nextBytes(500)
        val one = file("one.txt", bytes, 1)
        file("two.txt", bytes, 2)
        assertTrue(Duplicates.find(listOf(one, one)).isEmpty())
        assertTrue(Duplicates.find(tmp.root.listFiles()!!.toList(), minBytes = 1_000).isEmpty())
        assertEquals(1, Duplicates.find(tmp.root.listFiles()!!.toList()).size)
    }

    @Test
    fun unusedAppsNeedThirtyDaysWithoutUse() {
        val day = AppsRepository.DAY
        val now = 400 * day
        fun app(name: String, installedDaysAgo: Int, usedDaysAgo: Int?, system: Boolean = false) = AppInfo(
            packageName = name, label = name, versionName = null, isSystem = system, apkPaths = emptyList(), apkBytes = 0,
            installedAt = now - installedDaysAgo * day, updatedAt = 0, lastUsedAt = usedDaysAgo?.let { now - it * day },
        )
        val apps = listOf(
            app("used", 100, 2), app("stale", 100, 45), app("never", 100, null),
            app("new", 5, null), app("system", 100, null, system = true),
        )
        assertEquals(listOf("stale", "never"), AppsRepository.unused(apps, now).map { it.packageName })
    }
}
