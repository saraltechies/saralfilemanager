package com.saraltechies.saralfilemanager

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.SystemClock
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/** The 1.2.1 fixes that unit tests can't reach, and two things to find out. Run one at a time by the App check. */
@RunWith(AndroidJUnit4::class)
class FixCheckTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = Environment.getExternalStorageDirectory()
    private val out get() = File(context.getExternalFilesDir(null), "check").apply { mkdirs() }
    private val operations get() = (context.applicationContext as SaralFileManagerApp).fileOperations
    private fun s(id: Int) = context.getString(id)

    @Test
    fun duplicatesRefuseTheLastCopy() {
        val bytes = Random(5).nextBytes(40_000)
        val dir = File(root, "Pictures/CheckDup").apply { mkdirs() }
        val a = File(dir, "dup-a.jpg").apply { writeBytes(bytes) }
        val b = File(dir, "dup-b.jpg").apply { writeBytes(bytes) }
        scan(listOf(a.path, b.path))
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                settle(2_000)
                scrollHomeTo(s(R.string.home_clean_action))
                click(s(R.string.home_clean_action))
                settle(4_000)
                click(s(R.string.clean_duplicates))
                waitFor(hasText(a.name), 30_000)
                settle()
                shot("fix-dup-1-list")
                // The extra copy starts ticked, so the button already offers Select none.
                assertTrue(compose.onAllNodes(hasText(s(R.string.clean_select_none))).fetchSemanticsNodes().isNotEmpty())
                click(s(R.string.clean_select_none))
                click(s(R.string.select_all))
                shot("fix-dup-2-select-all")
                // Tapping these in turn must reach the point where only the last copy is left to tick, and be refused.
                var refused = false
                for (name in listOf(a.name, b.name, a.name)) {
                    click(name)
                    Thread.sleep(300)
                    compose.waitForIdle()
                    if (compose.onAllNodes(hasText(s(R.string.clean_keep_one))).fetchSemanticsNodes().isNotEmpty()) refused = true
                }
                shot("fix-dup-3-refused")
                assertTrue("Ticking every copy should be refused with a message", refused)
                log("duplicates: last copy refused")
            }
            assertTrue(a.exists() && b.exists())
        } finally {
            dir.deleteRecursively()
            scan(listOf(a.path, b.path))
        }
    }

    /** A long copy: its notification starts at once, a second task is refused out loud, and leaving doesn't stop it. */
    @Test
    fun longCopyShowsNotificationRefusesOthersAndSurvivesHome() {
        val dir = File(root, "Download/CheckCopy").apply { mkdirs() }
        val source = File(dir, "big.bin")
        val dest = File(root, "Download/CheckCopyTo").apply { mkdirs() }
        val other = File(dir, "keep-me.txt").apply { writeText("keep") }
        RandomAccessFile(source, "rw").use { f ->
            val block = ByteArray(1024 * 1024)
            repeat(BIG_MB) { f.write(block) }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                settle(2_000)
                instrumentation.runOnMainSync {
                    operations.setClip(listOf(source.path), move = false)
                    operations.paste(dest.path)
                    operations.delete(listOf(other.path))
                }
                val services = shell("dumpsys activity services ${context.packageName}")
                val running = operations.progress.value != null
                log("copy: still running when checked: $running; service started: ${services.contains("FileOperationService")}")
                assertTrue("The notification's service should start at once", services.contains("FileOperationService") || !running)
                assertTrue("The refused delete must leave the file", other.exists())
                waitFor(hasText(s(R.string.error_busy)), 5_000)
                shot("fix-copy-1-busy-message")
                instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                val deadline = SystemClock.uptimeMillis() + 180_000
                while (operations.progress.value != null && SystemClock.uptimeMillis() < deadline) Thread.sleep(500)
                assertEquals("The copy should finish after leaving the app", source.length(), File(dest, source.name).length())
                log("copy: finished after pressing Home")
            }
        } finally {
            dir.deleteRecursively()
            dest.deleteRecursively()
        }
    }

    /** A zip whose inside names aren't valid text: a message, not a crash, and nothing left behind. */
    @Test
    fun zipWithBadNamesDoesNotCrash() {
        val dir = File(root, "Download/CheckBadZip").apply { mkdirs() }
        val zip = File(dir, "bad.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("AAAAAAAA.txt"))
            z.write("hello".toByteArray())
            z.closeEntry()
        }
        // Change the name to bytes that aren't valid text, where it's stored in both places.
        val data = zip.readBytes()
        val name = "AAAAAAAA".toByteArray()
        var i = 0
        while (i <= data.size - name.size) {
            if (name.indices.all { data[i + it] == name[it] }) name.indices.forEach { data[i + it] = 0xFF.toByte() }
            i++
        }
        zip.writeBytes(data)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                settle(2_000)
                instrumentation.runOnMainSync { operations.extract(zip.path, dir.path) }
                val deadline = SystemClock.uptimeMillis() + 20_000
                while (operations.progress.value != null && SystemClock.uptimeMillis() < deadline) Thread.sleep(100)
                settle()
                shot("fix-badzip-1-after")
                log("bad zip: no crash; folder contents: " + dir.list()?.sorted())
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun folderSelectionSurvivesRebuildAndSearchWorks() {
        val dir = File(root, "Pictures/CheckSearch/sub").apply { mkdirs() }
        val zip = File(dir, "findme-pack.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("inside.txt"))
            z.write("hello".toByteArray())
            z.closeEntry()
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                settle(2_000)
                val card = hasText(" used of ", substring = true) and hasClickAction()
                waitFor(card)
                compose.onAllNodes(card).onFirst().performClick()
                settle(1_500)
                repeat(5) {
                    if (compose.onAllNodes(hasText("Pictures")).fetchSemanticsNodes().isNotEmpty()) return@repeat
                    compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeUp() }
                    settle(400)
                }
                click("Pictures")
                settle(1_000)

                // Selection in a folder, kept when the screen is rebuilt.
                waitFor(hasText("CheckSearch"))
                compose.onNode(hasText("CheckSearch")).performTouchInput { longClick() }
                settle()
                val one = hasText(context.getString(R.string.selected_count, 1))
                waitFor(one)
                scenario.recreate()
                settle(2_000)
                shot("fix-folder-1-after-rebuild")
                assertTrue("The selection should stay", compose.onAllNodes(one).fetchSemanticsNodes().isNotEmpty())
                click(s(R.string.clear_selection))
                log("folder: selection stayed")

                // Search says nothing found only at the end, and extract goes next to the zip.
                click(s(R.string.search))
                settle()
                compose.onNode(hasSetTextAction()).performTextInput("no-such-file-anywhere")
                waitFor(hasText(context.getString(R.string.search_nothing_found, "no-such-file-anywhere")), 20_000)
                shot("fix-search-1-nothing")
                back()
                back()
                click(s(R.string.search))
                settle()
                compose.onNode(hasSetTextAction()).performTextInput("findme")
                waitFor(hasText(zip.name), 20_000)
                click(zip.name)
                click(s(R.string.extract_here))
                val deadline = SystemClock.uptimeMillis() + 15_000
                val made = File(dir, "findme-pack/inside.txt")
                while (!made.exists() && SystemClock.uptimeMillis() < deadline) Thread.sleep(200)
                shot("fix-search-2-extracted")
                assertTrue("Extract should go next to the zip", made.exists())
                log("search: extracted next to the zip")
            }
        } finally {
            File(root, "Pictures/CheckSearch").deleteRecursively()
        }
    }

    /** Finding out, not asserting: what tapping an APK file does. */
    @Test
    fun whatTappingAnApkDoes() {
        val dir = File(root, "Download/CheckApk").apply { mkdirs() }
        val apk = File(dir, "check-app.apk")
        File(context.applicationInfo.sourceDir).copyTo(apk, overwrite = true)
        scan(listOf(apk.path))
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                settle(2_000)
                clickTile(s(R.string.category_apps))
                waitFor(hasText(apk.name), 30_000)
                shell("logcat -c")
                click(apk.name)
                Thread.sleep(1_000)
                shot("fix-apk-1-after-1s")
                Thread.sleep(3_000)
                shot("fix-apk-2-after-4s")
                val top = shell("dumpsys activity activities").lines().filter { "mResumedActivity" in it || "topResumedActivity" in it }.joinToString(" | ")
                val logs = shell("logcat -d -s PackageInstaller:* InstallStart:* PackageInstallerActivity:*").lines().takeLast(12).joinToString("\n")
                log("apk tap: on top: $top\n$logs")
                // Android 8 shows a crash message from its installer, which only Android can dismiss.
                shell("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
            }
        } finally {
            dir.deleteRecursively()
            scan(listOf(apk.path))
        }
    }

    /** Two quick taps on a back arrow must leave the home screen, not a blank one. */
    @Test
    fun doubleTapOnBackArrow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            settle(2_000)
            clickTile(s(R.string.tile_apps))
            settle(1_500)
            val arrow = hasContentDescription(s(R.string.back)) and hasClickAction()
            waitFor(arrow)
            compose.onAllNodes(arrow).onFirst().performTouchInput {
                click()
                advanceEventTime(40)
                click()
            }
            settle(2_000)
            shot("fix-back-1-after-double-tap")
            val home = hasText(s(R.string.app_name))
            val onHome = compose.onAllNodes(home).fetchSemanticsNodes().isNotEmpty()
            log("double back: home showing: $onHome")
            assertTrue("Home should be showing after two quick taps", onHome)
        }
    }

    /** Why the store-picture test stops here on a small screen with large text. */
    @Test
    fun appsMenuOffersSystemApps() {
        ActivityScenario.launch(MainActivity::class.java).use {
            settle(2_000)
            clickTile(s(R.string.tile_apps))
            settle(2_000)
            click(s(R.string.more_options))
            settle()
            shot("fix-apps-1-menu")
            val item = hasText(s(R.string.apps_show_system))
            log("apps menu: show system apps present: " + compose.onAllNodes(item).fetchSemanticsNodes().isNotEmpty())
            click(s(R.string.apps_show_system))
            settle(3_000)
            shot("fix-apps-2-system")
        }
    }

    private fun shell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    private fun scrollHomeTo(text: String) {
        val rootNode = compose.onAllNodes(isRoot()).onFirst()
        val height = rootNode.fetchSemanticsNode().boundsInRoot.height
        repeat(4) {
            rootNode.performTouchInput { swipe(androidx.compose.ui.geometry.Offset(center.x, height * 0.3f), androidx.compose.ui.geometry.Offset(center.x, height * 0.9f), 150) }
            settle(300)
        }
        val matcher = hasText(text, substring = true) and hasClickAction()
        repeat(12) {
            val onScreen = compose.onAllNodes(matcher).fetchSemanticsNodes().any { it.boundsInRoot.top > height * 0.12f && it.boundsInRoot.bottom < height * 0.88f }
            if (onScreen) return
            rootNode.performTouchInput { swipe(androidx.compose.ui.geometry.Offset(center.x, height * 0.7f), androidx.compose.ui.geometry.Offset(center.x, height * 0.45f), 300) }
            settle(400)
        }
    }

    private fun clickTile(text: String) {
        scrollHomeTo(text)
        val height = compose.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInRoot.height
        val matcher = hasText(text, substring = true) and hasClickAction()
        val index = compose.onAllNodes(matcher).fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.top > height * 0.12f && it.boundsInRoot.bottom < height * 0.88f }
        compose.onAllNodes(matcher)[index.coerceAtLeast(0)].performClick()
        settle()
    }

    private fun click(text: String) {
        val matcher = (hasText(text) or hasContentDescription(text)) and hasClickAction()
        waitFor(matcher)
        compose.onAllNodes(matcher).onFirst().performClick()
        settle()
    }

    private fun waitFor(matcher: SemanticsMatcher, timeoutMs: Long = 15_000) {
        try {
            compose.waitUntil(timeoutMs) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            shot("fix-failed-" + System.currentTimeMillis())
            log("FAILED looking for " + matcher.description + "\n" + runCatching { compose.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 30) }.getOrElse { it.toString() })
            throw e
        }
    }

    private fun back() {
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        settle()
    }

    private fun settle(ms: Long = 1_000) {
        compose.waitForIdle()
        Thread.sleep(ms)
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        runCatching {
            val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
            File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun log(line: String) {
        File(out, "results.txt").appendText(line + "\n")
    }

    private fun scan(paths: List<String>) {
        val latch = CountDownLatch(paths.size)
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ -> latch.countDown() }
        latch.await(1, TimeUnit.MINUTES)
    }

    private companion object {
        const val BIG_MB = 600
    }
}
