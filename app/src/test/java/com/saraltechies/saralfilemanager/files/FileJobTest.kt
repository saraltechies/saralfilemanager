package com.saraltechies.saralfilemanager.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException

class FileJobTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun tree(): File {
        val root = tmp.newFolder("Photos")
        File(root, "a.jpg").writeText("aaaa")
        File(root, "trip").mkdir()
        File(root, "trip/b.jpg").writeText("bb")
        return root
    }

    @Test fun copiesFoldersWithEverythingInside() {
        val source = tree()
        val dest = tmp.newFolder("Backup")
        var bytes = 0L
        val out = FileJob(onBytes = { bytes += it }).copy(listOf(source), dest)
        assertEquals(listOf(File(dest, "Photos")), out)
        assertEquals("bb", File(dest, "Photos/trip/b.jpg").readText())
        assertEquals(6L, bytes)
        assertTrue(File(source, "a.jpg").exists())
    }

    @Test fun copyIntoTheSameFolderKeepsBoth() {
        val folder = tmp.newFolder("Docs")
        val file = File(folder, "note.txt").apply { writeText("hi") }
        val out = FileJob().copy(listOf(file), folder)
        assertEquals("note (1).txt", out.single().name)
        assertEquals("hi", out.single().readText())
    }

    @Test fun refusesToCopyAFolderIntoItself() {
        val source = tree()
        assertThrows(IntoItselfException::class.java) { FileJob().copy(listOf(source), File(source, "trip")) }
        assertThrows(IntoItselfException::class.java) { FileJob().move(listOf(source), source) }
    }

    @Test fun movesAndLeavesNothingBehind() {
        val source = tree()
        val dest = tmp.newFolder("Moved")
        val out = FileJob().move(listOf(source), dest)
        assertEquals(File(dest, "Photos"), out.single())
        assertFalse(source.exists())
        assertEquals("aaaa", File(dest, "Photos/a.jpg").readText())
    }

    @Test fun moveToTheSameFolderDoesNothing() {
        val folder = tmp.newFolder("Same")
        val file = File(folder, "x.txt").apply { writeText("x") }
        assertTrue(FileJob().move(listOf(file), folder).isEmpty())
        assertTrue(file.exists())
    }

    @Test fun deletesFoldersRecursively() {
        val source = tree()
        FileJob().delete(listOf(source))
        assertFalse(source.exists())
    }

    @Test fun zipAndExtractRoundTrip() {
        val source = tree()
        val zip = File(tmp.root, "photos.zip")
        FileJob().zip(listOf(source), zip)
        assertTrue(zip.length() > 0)

        val dest = tmp.newFolder("Out")
        val folder = FileJob().extract(zip, dest)
        assertEquals("photos", folder.name)
        assertEquals("aaaa", File(folder, "Photos/a.jpg").readText())
        assertEquals("bb", File(folder, "Photos/trip/b.jpg").readText())

        // Extracting again doesn't overwrite the first folder.
        assertEquals("photos (1)", FileJob().extract(zip, dest).name)
    }

    @Test fun extractSkipsEntriesThatEscapeTheFolder() {
        val zip = File(tmp.root, "evil.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("../../escaped.txt"))
            out.write("x".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("ok.txt"))
            out.write("ok".toByteArray())
            out.closeEntry()
        }
        val dest = tmp.newFolder("Safe")
        val folder = FileJob().extract(zip, dest)
        assertEquals("ok", File(folder, "ok.txt").readText())
        assertFalse(File(tmp.root, "escaped.txt").exists())
        assertFalse(File(tmp.root.parentFile, "escaped.txt").exists())
    }

    @Test fun zipPathsRejectEscapes() {
        val root = tmp.newFolder("Root")
        assertNull(ZipPaths.target(root, "../x"))
        assertNull(ZipPaths.target(root, "a/../../x"))
        assertNull(ZipPaths.target(root, ""))
        assertEquals(File(root, "a/b.txt").canonicalFile, ZipPaths.target(root, "a\\b.txt"))
        // A sibling folder whose name starts the same isn't "inside".
        assertFalse(ZipPaths.isInside(File(tmp.root, "Root2/x"), root))
    }

    @Test fun cancellingStopsAndRemovesThePartialFile() {
        val big = File(tmp.root, "big.bin").apply { writeBytes(ByteArray(1024 * 1024)) }
        val dest = tmp.newFolder("Dest")
        lateinit var job: FileJob
        job = FileJob(onBytes = { job.cancelled = true })
        assertThrows(CancellationException::class.java) { job.copy(listOf(big), dest) }
        assertFalse(File(dest, "big.bin").exists())
    }
}
