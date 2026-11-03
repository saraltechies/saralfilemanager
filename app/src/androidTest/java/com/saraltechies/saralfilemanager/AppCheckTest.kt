package com.saraltechies.saralfilemanager

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * A wider check of the app on an emulator, on invented files: every main screen, real video playback, awkward files,
 * rotation, and big libraries. Each test stands alone so one failure doesn't hide the others. Screenshots and timings
 * go to the app's external files folder; the "App check" workflow pulls them. The video files are pushed by the workflow.
 */
@RunWith(AndroidJUnit4::class)
class AppCheckTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = Environment.getExternalStorageDirectory()
    private val out get() = File(context.getExternalFilesDir(null), "check").apply { mkdirs() }
    private fun s(id: Int) = context.getString(id)

    @Test
    fun everyMainScreen() {
        val photos = (0 until 12).map { photo("Pictures/CheckTour/tour-$it.jpg", Color.rgb(20 * it, 90, 200 - 10 * it)) }
        scan(photos.map { it.path })
        ActivityScenario.launch(MainActivity::class.java).use {
            settle(4_000)
            shot("tour-01-home")
            compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() }
            settle()
            shot("tour-02-home-lower")
            click(s(R.string.more_options))
            shot("tour-03-home-menu")
            back()

            listOf(
                R.string.category_images to "images",
                R.string.category_videos to "videos",
                R.string.category_audio to "audio",
                R.string.category_documents to "documents",
                R.string.category_archives to "archives",
                R.string.category_apps to "apk",
            ).forEach { (label, name) ->
                scrollHomeTo(s(label))
                click(s(label))
                settle(2_500)
                shot("tour-10-$name")
                back()
            }

            click(s(R.string.home_clean_action))
            settle(6_000)
            shot("tour-20-clean")
            back()

            scrollHomeTo(s(R.string.tile_apps))
            click(s(R.string.tile_apps))
            settle(3_000)
            shot("tour-21-apps")
            back()

            click(s(R.string.more_options))
            click(s(R.string.bin_title))
            settle(2_000)
            shot("tour-22-bin")
            back()

            scrollHomeTo(s(R.string.folder_downloads))
            click(s(R.string.folder_downloads))
            settle(2_000)
            shot("tour-30-folder")
            click(s(R.string.more_options))
            shot("tour-31-folder-menu")
            back()
            click(s(R.string.search))
            settle()
            shot("tour-32-search")
        }
        File(root, "Pictures/CheckTour").deleteRecursively()
    }

    /** The picture must keep changing while a real video plays. The workflow pushes check-video.mp4. */
    @Test
    fun videoReallyPlays() {
        val video = File(root, "DCIM/Camera/check-video.mp4")
        assertTrue("The workflow should have pushed the test video", video.exists())
        scan(listOf(video.path))
        ActivityScenario.launch(MainActivity::class.java).use {
            scrollHomeTo(s(R.string.category_videos))
            click(s(R.string.category_videos))
            waitFor(hasContentDescription(video.name), 30_000)
            settle()
            shot("video-1-list")
            compose.onAllNodes(hasContentDescription(video.name)).onFirst().performClick()
            settle(1_500)
            shot("video-2-viewer")
            val play = hasContentDescription(s(R.string.viewer_play)) and hasClickAction()
            waitFor(play)
            compose.onAllNodes(play).onFirst().performClick()
            Thread.sleep(3_000)
            val a = takeShot("video-3-playing-a")
            Thread.sleep(3_000)
            val b = takeShot("video-4-playing-b")
            assertTrue(
                "The video should not show the can't-play message",
                compose.onAllNodes(hasText(s(R.string.viewer_cant_play_video))).fetchSemanticsNodes().isEmpty(),
            )
            assertTrue("The picture should change while the video plays", differs(a, b))
            log("video: picture changed while playing")

            // Turning the phone during playback.
            it.onActivity { a2 -> a2.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            settle(3_000)
            shot("video-5-landscape")
            it.onActivity { a2 -> a2.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            settle(2_000)
            shot("video-6-back-to-portrait")
        }
    }

    /** Broken and awkward files must show a message or a placeholder, never crash. */
    @Test
    fun awkwardFiles() {
        val dir = File(root, "Pictures/CheckAwkward").apply { mkdirs() }
        val made = mutableListOf<File>()
        made += File(dir, "empty.jpg").apply { writeBytes(ByteArray(0)) }
        made += File(dir, "broken.jpg").apply { writeBytes(Random(1).nextBytes(40_000)) }
        made += File(dir, "broken.mp4").apply { writeBytes(Random(2).nextBytes(400_000)) }
        made += File(dir, "empty.mp4").apply { writeBytes(ByteArray(0)) }
        made += photo("Pictures/CheckAwkward/name with #hash %20 & 'quotes'.jpg", Color.RED)
        made += photo("Pictures/CheckAwkward/हिन्दी नाम 😀.jpg", Color.BLUE)
        made += photo("Pictures/CheckAwkward/" + "very long name ".repeat(14) + ".jpg", Color.GREEN)
        // A big camera photo, which must be scaled down rather than run out of memory.
        val big = Bitmap.createBitmap(9000, 6000, Bitmap.Config.RGB_565)
        Canvas(big).drawColor(Color.MAGENTA)
        made += File(dir, "huge-54mp.jpg").also { f -> f.outputStream().use { big.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
        big.recycle()
        scan(made.map { it.path })

        ActivityScenario.launch(MainActivity::class.java).use {
            // In the gallery, then opening every one in the viewer.
            click(s(R.string.category_images))
            settle(3_000)
            shot("awkward-1-gallery")
            openEach(made.filter { it.name.endsWith(".jpg") }, "photo")
            back()

            scrollHomeTo(s(R.string.category_videos))
            click(s(R.string.category_videos))
            settle(3_000)
            shot("awkward-2-videos")
            openEach(made.filter { it.name.endsWith(".mp4") }, "video")
            back()
        }
        dir.deleteRecursively()
        scan(made.map { it.path })
    }

    private fun openEach(files: List<File>, kind: String) {
        files.forEachIndexed { i, f ->
            val matcher = hasContentDescription(f.name)
            val found = runCatching { waitFor(matcher, 8_000) }.isSuccess
            if (!found) {
                // Files Android's media index refuses are simply not listed, which is fine.
                log("awkward: ${f.name.take(40)} is not listed")
                return@forEachIndexed
            }
            compose.onAllNodes(matcher).onFirst().performClick()
            settle(2_500)
            val play = hasContentDescription(s(R.string.viewer_play)) and hasClickAction()
            if (compose.onAllNodes(play).fetchSemanticsNodes().isNotEmpty()) {
                compose.onAllNodes(play).onFirst().performClick()
                settle(2_500)
            }
            shot("awkward-3-$kind-$i")
            log("awkward: opened ${f.name.take(40)}")
            back()
        }
    }

    /** Turning the phone and the activity being rebuilt must keep the selection and where you were. */
    @Test
    fun selectionAndViewerSurviveBeingRebuilt() {
        val photos = (0 until 8).map { photo("Pictures/CheckRotate/rotate-$it.jpg", Color.rgb(30 * it, 200 - 20 * it, 80)) }
        scan(photos.map { it.path })
        ActivityScenario.launch(MainActivity::class.java).use {
            click(s(R.string.category_images))
            waitFor(hasContentDescription("rotate-0.jpg"))
            compose.onAllNodes(hasContentDescription("rotate-0.jpg")).onFirst().performTouchInput { longClick() }
            waitFor(hasText(s(R.string.selected_count).format(1)))
            compose.onAllNodes(hasContentDescription("rotate-1.jpg")).onFirst().performClick()
            waitFor(hasText(s(R.string.selected_count).format(2)))

            it.recreate()
            settle(3_000)
            shot("rotate-1-selection-recreated")
            assertTrue(
                "The selection should survive the activity being rebuilt",
                compose.onAllNodes(hasText(s(R.string.selected_count).format(2))).fetchSemanticsNodes().isNotEmpty(),
            )
            it.onActivity { a -> a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            settle(3_000)
            shot("rotate-2-selection-landscape")
            it.onActivity { a -> a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            settle(2_000)
            click(s(R.string.clear_selection))

            compose.onAllNodes(hasContentDescription("rotate-2.jpg")).onFirst().performClick()
            settle(2_000)
            compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeLeft() }
            settle(1_500)
            shot("rotate-3-viewer-before")
            it.recreate()
            settle(3_000)
            shot("rotate-4-viewer-after")
        }
        File(root, "Pictures/CheckRotate").deleteRecursively()
        scan(photos.map { it.path })
    }

    /** A big phone: times how long the big screens take, and that they stay usable. */
    @Test
    fun bigLibraryAndBigFolder() {
        val dir = File(root, "Pictures/CheckBig").apply { mkdirs() }
        val seed = Bitmap.createBitmap(320, 240, Bitmap.Config.RGB_565)
        Canvas(seed).drawColor(Color.DKGRAY)
        val bytes = java.io.ByteArrayOutputStream().also { seed.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
        val start = System.currentTimeMillis()
        val count = 4_000
        val paths = (0 until count).map { i ->
            val f = File(dir, "big-%05d.jpg".format(i))
            f.writeBytes(bytes)
            f.setLastModified(start - i * 3_600_000L)
            f.path
        }
        log("big: wrote $count photos in ${System.currentTimeMillis() - start} ms")
        val scanStart = System.currentTimeMillis()
        scan(paths, minutes = 8)
        log("big: media scan took ${System.currentTimeMillis() - scanStart} ms")

        ActivityScenario.launch(MainActivity::class.java).use {
            settle(2_000)
            val runtime = Runtime.getRuntime()
            timed("big: gallery first photo") {
                click(s(R.string.category_images))
                waitFor(hasContentDescription("big-00000.jpg"), 60_000)
            }
            settle(2_000)
            shot("big-1-gallery")
            repeat(5) {
                compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp(durationMillis = 120) }
                Thread.sleep(400)
            }
            settle(1_000)
            shot("big-2-gallery-scrolled")

            // Select all of them.
            compose.onAllNodes(hasContentDescription("big-", substring = true)).onFirst().performTouchInput { longClick() }
            settle(1_000)
            timed("big: select all") {
                click(s(R.string.select_all))
                settle(500)
            }
            shot("big-3-all-selected")
            click(s(R.string.clear_selection))
            log("big: heap in use ${(runtime.totalMemory() - runtime.freeMemory()) / 1_048_576} MB of ${runtime.maxMemory() / 1_048_576} MB")
            back()

            // The same 4,000 files in the folder view.
            clickStorageCard()
            settle(1_500)
            click("Pictures")
            settle(1_500)
            timed("big: open a folder of 4,000 files") {
                click("CheckBig")
                waitFor(hasText("big-00000.jpg"), 60_000)
            }
            settle(1_000)
            shot("big-4-folder")
            repeat(5) {
                compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp(durationMillis = 120) }
                Thread.sleep(400)
            }
            settle(1_000)
            shot("big-5-folder-scrolled")
        }
        dir.deleteRecursively()
    }

    private fun click(text: String) {
        val matcher = (hasText(text) or hasContentDescription(text)) and hasClickAction()
        waitFor(matcher)
        compose.onAllNodes(matcher).onFirst().performClick()
        settle()
    }

    /** The phone's storage card on the home screen; its name depends on the Android version, its usage line doesn't. */
    private fun clickStorageCard() {
        val matcher = hasText(" used of ", substring = true) and hasClickAction()
        waitFor(matcher)
        compose.onAllNodes(matcher).onFirst().performClick()
        settle()
    }

    private fun scrollHomeTo(text: String) {
        repeat(6) {
            if (compose.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) return
            compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() }
            settle(500)
        }
    }

    private fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 15_000) {
        try {
            compose.waitUntil(timeoutMs) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            dump("Looking for: " + matcher.description)
            throw e
        }
    }

    private fun dump(what: String) {
        shot("failed-" + System.currentTimeMillis())
        val tree = runCatching { compose.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 30) }.getOrElse { it.toString() }
        log("FAILED $what\n$tree")
    }

    private fun timed(what: String, block: () -> Unit) {
        val start = System.currentTimeMillis()
        block()
        log("$what: ${System.currentTimeMillis() - start} ms")
    }

    private fun log(line: String) {
        File(out, "results.txt").appendText(line + "\n")
    }

    private fun photo(path: String, color: Int): File {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    private fun scan(paths: List<String>, minutes: Long = 1) {
        val latch = CountDownLatch(paths.size)
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ -> latch.countDown() }
        latch.await(minutes, TimeUnit.MINUTES)
    }

    private fun back() {
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        settle()
    }

    private fun settle(ms: Long = 1_200) {
        compose.waitForIdle()
        Thread.sleep(ms)
        compose.waitForIdle()
    }

    private fun takeShot(name: String): Bitmap {
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun shot(name: String) {
        runCatching { takeShot(name) }
    }

    private fun differs(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return true
        var changed = 0
        var y = a.height / 4
        while (y < a.height * 3 / 4) {
            var x = 0
            while (x < a.width) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) changed++
                x += 16
            }
            y += 16
        }
        return changed > 20
    }
}
