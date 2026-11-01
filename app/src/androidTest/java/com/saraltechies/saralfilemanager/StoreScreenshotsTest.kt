package com.saraltechies.saralfilemanager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Takes the Play Store screenshots from the real app on an emulator. It first fills shared storage with invented
 * files (gradient "photos", copies of some of them, a large video, old screenshots, music, documents and downloads),
 * so every screen has something to show. Run by the "Store screenshots" workflow, which pulls the PNGs.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = Environment.getExternalStorageDirectory()
    private val out get() = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    private fun s(id: Int) = context.getString(id)

    @Test
    fun storeScreenshots() {
        val files = seed()
        scan(files)
        ActivityScenario.launch(MainActivity::class.java).use {
            settle(4_000)
            shot("1-home")

            click(s(R.string.home_clean_action))
            settle(6_000)
            shot("2-free-up-space")

            click(s(R.string.clean_duplicates))
            settle(4_000)
            shot("3-duplicates")
            back()
            back()

            click(s(R.string.tile_apps))
            settle(2_000)
            // The emulator has only built-in apps; show them so the list isn't empty.
            click(s(R.string.more_options))
            click(s(R.string.apps_show_system))
            settle(3_000)
            shot("4-apps")
            click(s(R.string.app_name))
            settle(2_000)
            shot("5-app-details")
            back()
            back()

            click(s(R.string.category_images))
            settle(3_000)
            shot("6-images")
        }
    }

    /** Invented files; returns their paths for the media scanner. */
    private fun seed(): List<String> {
        val made = mutableListOf<String>()
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val camera = File(root, "DCIM/Camera").apply { mkdirs() }
        val colors = listOf(
            0xFF0D6EFD to 0xFF22D3EE, 0xFFDB2777 to 0xFFF59E0B, 0xFF16A34A to 0xFFA3E635, 0xFF9333EA to 0xFFF472B6,
            0xFFF97316 to 0xFFFDE047, 0xFF0F766E to 0xFF5EEAD4, 0xFF1E3A8A to 0xFF60A5FA, 0xFFB91C1C to 0xFFFCA5A5,
        )
        val photos = (0 until 24).map { i ->
            val (a, b) = colors[i % colors.size]
            val f = File(camera, "IMG_202610%02d_1%02d512.jpg".format(1 + i % 28, i))
            photo(f, a.toInt(), b.toInt(), i)
            f.setLastModified(now - i * day / 2)
            f
        }
        made += photos.map { it.path }
        // Copies: the same photos forwarded through a chat app and downloaded again.
        val whatsapp = File(root, "Download/WhatsApp Images").apply { mkdirs() }
        photos.take(5).forEachIndexed { i, p ->
            val copy = File(whatsapp, "IMG-20261020-WA00%02d.jpg".format(i))
            p.copyTo(copy, overwrite = true)
            copy.setLastModified(now - day)
            made += copy.path
        }
        photos[6].copyTo(File(root, "Download/${photos[6].name}"), overwrite = true).also { made += it.path }

        // Videos, with one large one.
        val movies = File(root, "DCIM/Camera")
        made += blob(File(movies, "VID_20261012_181530.mp4"), 160L * 1024 * 1024, now - 10 * day)
        made += blob(File(movies, "VID_20261018_093011.mp4"), 38L * 1024 * 1024, now - 4 * day)
        made += blob(File(root, "Movies/Diwali at home.mp4"), 122L * 1024 * 1024, now - 15 * day)

        // Old screenshots.
        val shots = File(root, "Pictures/Screenshots").apply { mkdirs() }
        (0 until 9).forEach { i ->
            val f = File(shots, "Screenshot_202608%02d-1015%02d.png".format(3 + i * 3, i))
            photo(f, 0xFFF1F5F9.toInt(), 0xFFCBD5E1.toInt(), i, png = true)
            f.setLastModified(now - (45 + i * 5) * day)
            made += f.path
        }

        // Music, documents and downloads.
        listOf("Raag Yaman.mp3", "Morning Bhajan.mp3", "Podcast - Saving money.mp3", "Lofi study mix.mp3").forEachIndexed { i, name ->
            made += blob(File(root, "Music/$name"), (4L + i * 3) * 1024 * 1024, now - (20 + i) * day, random = true)
        }
        listOf("Rent agreement 2026.pdf", "Electricity bill Oct.pdf", "Resume - Priya Sharma.docx", "Budget 2026.xlsx", "Trip plan.pdf").forEachIndexed { i, name ->
            made += blob(File(root, "Documents/$name"), (180L + i * 140) * 1024, now - (3 + i * 6) * day, random = true)
        }
        listOf("Train ticket.pdf", "Bank statement Sep.pdf", "Photos backup.zip", "Invoice 4471.pdf").forEachIndexed { i, name ->
            made += blob(File(root, "Download/$name"), (300L + i * 900) * 1024, now - (2 + i * 9) * day, random = true)
        }
        return made
    }

    private fun photo(file: File, from: Int, to: Int, seed: Int, png: Boolean = false) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 1200f, 900f, from, to, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, 1200f, 900f, paint)
        val random = Random(seed)
        val dot = Paint().apply { isAntiAlias = true; color = 0x55FFFFFF }
        repeat(6) { canvas.drawCircle(random.nextFloat() * 1200, random.nextFloat() * 900, 60f + random.nextFloat() * 160, dot) }
        file.outputStream().use { bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
    }

    /** A file of [bytes] (random content where it should not look like a copy of anything). */
    private fun blob(file: File, bytes: Long, modified: Long, random: Boolean = false): String {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            if (random) {
                val chunk = Random(file.name.hashCode()).nextBytes(64 * 1024)
                var left = bytes
                while (left > 0) {
                    val n = minOf(left, chunk.size.toLong()).toInt()
                    raf.write(chunk, 0, n)
                    chunk[0] = (chunk[0] + 1).toByte()
                    left -= n
                }
            } else {
                // Big videos: a unique header, then empty space.
                raf.write(Random(file.name.hashCode()).nextBytes(64 * 1024))
                raf.setLength(bytes)
            }
        }
        file.setLastModified(modified)
        return file.path
    }

    private fun scan(paths: List<String>) {
        val latch = CountDownLatch(paths.size)
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ -> latch.countDown() }
        latch.await(90, TimeUnit.SECONDS)
    }

    private fun back() {
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        settle()
    }

    private fun settle(ms: Long = 1_500) {
        compose.waitForIdle()
        Thread.sleep(ms)
        compose.waitForIdle()
    }

    private fun scrollTo(text: String) {
        runCatching { compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text)) }
        settle(500)
    }

    private fun click(text: String) {
        val matcher = (hasText(text) or hasContentDescription(text)) and hasClickAction()
        try {
            compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            shot("failed-" + text.filter { it.isLetterOrDigit() })
            val dump = runCatching { compose.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().printToString(maxDepth = 30) }.getOrElse { it.toString() }
            File(out, "failed-tree.txt").writeText("Looking for: $text\n\n$dump")
            throw e
        }
        compose.onAllNodes(matcher).onFirst().performClick()
        settle()
    }

    private fun shot(name: String) {
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
