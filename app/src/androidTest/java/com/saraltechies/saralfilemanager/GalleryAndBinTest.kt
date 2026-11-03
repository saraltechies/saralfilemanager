package com.saraltechies.saralfilemanager

import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saraltechies.saralfilemanager.files.Bin
import com.saraltechies.saralfilemanager.files.FileCategory
import com.saraltechies.saralfilemanager.files.Gallery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The new gallery, viewer and Bin on an emulator, tapped through as a person would, on invented photos. */
@RunWith(AndroidJUnit4::class)
class GalleryAndBinTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as SaralFileManagerApp
    private val root = Environment.getExternalStorageDirectory()
    private val out get() = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    private fun s(id: Int) = context.getString(id)

    /** So these test photos can't show up in the store screenshots, which run next on the same emulator. */
    @After
    fun cleanUp() {
        File(root, "Pictures/GalleryTest").deleteRecursively()
        File(root, Bin.FOLDER).deleteRecursively()
    }

    private fun photo(name: String, color: Int): File {
        val file = File(root, "Pictures/GalleryTest/$name")
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(600, 450, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    private fun scan(paths: List<String>) {
        val latch = CountDownLatch(paths.size)
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ -> latch.countDown() }
        latch.await(30, TimeUnit.SECONDS)
    }

    @Test
    fun galleryLoadsEverythingWithoutALimit(): Unit = runBlocking {
        val photos = (0 until 6).map { photo("limit-$it.jpg", Color.rgb(40 * it, 120, 200)) }
        scan(photos.map { it.path })
        val media = app.fileRepository.media(FileCategory.IMAGE, showHidden = false)
        assertTrue(photos.all { p -> media.any { it.path == p.path } })
        assertTrue(media.first { it.path == photos[0].path }.id > 0)
    }

    @Test
    fun selectDeleteUndoViewAndRestore() {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW)
        val photos = colors.mapIndexed { i, c -> photo("test-$i.jpg", c) }
        scan(photos.map { it.path })
        runBlocking { app.settingsRepository.setUseBin(true) }

        ActivityScenario.launch(MainActivity::class.java).use {
            click(s(R.string.category_images))
            waitFor(hasContentDescription("test-0.jpg"))
            shot("t1-gallery")

            // Pinching out makes the photos bigger, and it's remembered.
            compose.onAllNodes(isRoot()).onFirst().performTouchInput {
                val spread = androidx.compose.ui.geometry.Offset(300f, 0f)
                val near = androidx.compose.ui.geometry.Offset(40f, 0f)
                pinch(center - near, center - spread, center + near, center + spread)
            }
            waitUntil("pinching changes the columns") { runBlocking { app.settingsRepository.galleryPrefs(FileCategory.IMAGE.name).first().columns } < Gallery.DEFAULT_COLUMNS }
            Thread.sleep(500)
            shot("t1b-pinched")
            runBlocking { app.settingsRepository.setGalleryColumns(FileCategory.IMAGE.name, Gallery.DEFAULT_COLUMNS) }
            compose.waitForIdle()
            Thread.sleep(500)

            // Long-press selects, and the photo stays visible.
            node("test-0.jpg").performTouchInput { longClick() }
            waitFor(hasText(s(R.string.selected_count).format(1)))
            node("test-1.jpg").performClick()
            waitFor(hasText(s(R.string.selected_count).format(2)))
            shot("t2-selected")

            // To the Bin, and Undo brings them back.
            click(s(R.string.delete))
            click(s(R.string.bin_move))
            waitUntil("moved to the Bin") { !photos[0].exists() && !photos[1].exists() }
            // Gone from the gallery too, not left behind as an empty tile.
            waitUntil("gone from the gallery") { compose.onAllNodes(hasContentDescription("test-0.jpg")).fetchSemanticsNodes().isEmpty() }
            click(s(R.string.undo))
            waitUntil("restored") { photos[0].exists() && photos[1].exists() }
            assertEquals(0, binned().count { it.startsWith("test-") })
            waitFor(hasContentDescription("test-0.jpg"))

            // Press, hold and drag across photos.
            compose.waitForIdle()
            val to = node("test-3.jpg").fetchSemanticsNode().boundsInRoot.center
            node("test-0.jpg").performTouchInput {
                down(center)
                advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
                val here = center
                val target = androidx.compose.ui.geometry.Offset(here.x + (to.x - visibleCenterX()), here.y + (to.y - visibleCenterY()))
                moveTo(target)
                advanceEventTime(100)
                moveBy(androidx.compose.ui.geometry.Offset(2f, 2f))
                up()
            }
            waitUntil("a drag selects several") { selectedCount() >= 2 }
            shot("t3-dragged")
            click(s(R.string.clear_selection))

            // The viewer: open, swipe to the next, delete from there.
            node("test-2.jpg").performClick()
            waitFor(hasContentDescription(s(R.string.delete)))
            Thread.sleep(800)
            shot("t4-viewer")
            compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeLeft() }
            Thread.sleep(800)
            shot("t5-viewer-next")
            click(s(R.string.delete))
            click(s(R.string.bin_move))
            waitUntil("deleted from the viewer") { binned().any { it.startsWith("test-") } }
            val gone = photos.first { !it.exists() }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()

            // The Bin: find it and restore it.
            click(s(R.string.more_options))
            click(s(R.string.bin_title))
            waitFor(hasText(gone.name))
            Thread.sleep(1500)
            compose.waitForIdle()
            shot("t6-bin")
            compose.onAllNodes(hasText(gone.name)).onFirst().performClick()
            waitFor(hasContentDescription(s(R.string.bin_restore)))
            click(s(R.string.bin_restore))
            waitUntil("restored from the Bin") { gone.exists() }
        }
        photos.forEach { it.delete() }
    }

    @Test
    fun theOffSwitchDeletesStraightAway(): Unit = runBlocking {
        val photo = photo("off.jpg", Color.GRAY)
        app.settingsRepository.setUseBin(false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                scan(listOf(photo.path))
                click(s(R.string.category_images))
                waitFor(hasContentDescription("off.jpg"))
                node("off.jpg").performTouchInput { longClick() }
                waitFor(hasContentDescription(s(R.string.delete)))
                click(s(R.string.delete))
                // The dialog's button, not the one behind it in the top bar.
                val inDialog = hasText(s(R.string.delete)) and hasClickAction()
                waitFor(inDialog)
                compose.onAllNodes(inDialog).onFirst().performClick()
                waitUntil("deleted") { !photo.exists() }
                assertFalse(binned().contains("off.jpg"))
            }
        } finally {
            app.settingsRepository.setUseBin(true)
        }
    }

    /** A long copy shows the notification that keeps it running after leaving the app, and Cancel leaves nothing behind. */
    @Test
    fun aLongCopyShowsItsNotification() {
        val big = File(root, "Download/big-copy-test.bin")
        val dest = File(root, "Documents").apply { mkdirs() }
        RandomAccessFile(big, "rw").use { it.setLength(1500L * 1024 * 1024) }
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            app.fileOperations.setClip(listOf(big.path), move = false)
            app.fileOperations.paste(dest.path)
            waitUntilPlain("notification", 15_000) { manager.activeNotifications.any { it.id == 1 } }
            app.fileOperations.cancel()
            waitUntilPlain("finished", 15_000) { app.fileOperations.progress.value == null }
            waitUntilPlain("notification gone", 15_000) { manager.activeNotifications.none { it.id == 1 } }
            assertFalse(File(dest, big.name).exists())
        } finally {
            big.delete()
            File(dest, big.name).delete()
            app.fileOperations.clearClip()
        }
    }

    private fun binned(): List<String> = Bin.list(listOf(root)).map { it.name }

    private fun selectedCount(): Int = (1..40).firstOrNull { n ->
        compose.onAllNodes(hasText(s(R.string.selected_count).format(n))).fetchSemanticsNodes().isNotEmpty()
    } ?: 0

    private fun node(description: String): SemanticsNodeInteraction {
        waitFor(hasContentDescription(description))
        return compose.onAllNodes(hasContentDescription(description)).onFirst()
    }

    private fun androidx.compose.ui.test.TouchInjectionScope.visibleCenterX() = node("test-0.jpg").fetchSemanticsNode().boundsInRoot.center.x
    private fun androidx.compose.ui.test.TouchInjectionScope.visibleCenterY() = node("test-0.jpg").fetchSemanticsNode().boundsInRoot.center.y

    private fun waitFor(matcher: SemanticsMatcher) {
        try {
            compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            dump("Looking for: " + matcher.description)
            throw e
        }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(15_000) { condition() }
        } catch (e: Throwable) {
            dump("Waiting until: $what")
            throw e
        }
    }

    private fun waitUntilPlain(what: String, timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Timed out waiting for $what")
            Thread.sleep(100)
        }
    }

    private fun click(text: String) {
        val matcher = (hasText(text) or hasContentDescription(text) or androidx.compose.ui.test.hasAnyChild(hasText(text))) and hasClickAction()
        waitFor(matcher)
        compose.onAllNodes(matcher).onFirst().performClick()
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
    }

    private fun dump(title: String) {
        shot("t-failed")
        val tree = compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.joinToString("\n\n") { i ->
            runCatching { compose.onAllNodes(isRoot())[i].printToString(maxDepth = 30) }.getOrElse { it.toString() }
        }
        File(out, "t-failed.txt").writeText(title + "\n\n" + tree)
    }

    private fun shot(name: String) {
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
