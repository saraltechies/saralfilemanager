package com.saraltechies.saralfilemanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.saraltechies.saralfilemanager.ui.AppNav
import com.saraltechies.saralfilemanager.ui.theme.SaralFileManagerTheme
import com.saraltechies.saralfilemanager.util.AppShortcuts
import com.saraltechies.saralfilemanager.util.AppUpdates
import com.saraltechies.saralfilemanager.util.PlayStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** From the launcher shortcuts. */
    private var launchShortcut by mutableStateOf<String?>(null)
    private lateinit var appUpdates: AppUpdates

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // androidx.core's version rather than androidx.activity's, which still called deprecated system-bar
        // color APIs on Android 15+ and gets flagged in Play Console.
        WindowCompat.enableEdgeToEdge(window)
        val app = application as SaralFileManagerApp
        appUpdates = AppUpdates(this, app.settingsRepository)
        AppShortcuts.publish(this)
        if (savedInstanceState == null) readShortcut(intent)
        setContent {
            SaralFileManagerTheme {
                AppNav(
                    files = app.fileRepository,
                    operations = app.fileOperations,
                    settings = app.settingsRepository,
                    launchShortcut = launchShortcut,
                    onShortcutConsumed = { launchShortcut = null },
                    updateReady = appUpdates.updateReady,
                    onRestartForUpdate = appUpdates::completeUpdate,
                    onReviewMoment = { lifecycleScope.launch { PlayStore.requestReview(this@MainActivity) } },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readShortcut(intent)
    }

    private fun readShortcut(intent: Intent) {
        if (intent.action != ACTION_SHORTCUT) return
        val id = intent.getStringExtra(EXTRA_SHORTCUT) ?: return
        launchShortcut = id
        ShortcutManagerCompat.reportShortcutUsed(this, id)
    }

    companion object {
        const val ACTION_SHORTCUT = "com.saraltechies.saralfilemanager.action.SHORTCUT"
        const val EXTRA_SHORTCUT = "shortcut"
    }
}
