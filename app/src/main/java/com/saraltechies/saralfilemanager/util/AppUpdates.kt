package com.saraltechies.saralfilemanager.util

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.saraltechies.saralfilemanager.data.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Google Play in-app updates (through the Play Store app, so no internet permission is needed). A new version
 * is offered as a background download (flexible update); once it's downloaded, [updateReady] turns true and the
 * home screen offers a restart. Releases given in-app update priority 4 or 5 (set through the Play Developer API) are
 * shown as a full-screen required update instead. "No thanks" pauses the offer for [REMIND_AFTER_DAYS] days.
 */
class AppUpdates(private val activity: ComponentActivity, private val settings: SettingsRepository) :
    DefaultLifecycleObserver {

    private val manager = AppUpdateManagerFactory.create(activity)
    private var askedThisSession = false

    var updateReady by mutableStateOf(false)
        private set

    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_CANCELED) {
                activity.lifecycleScope.launch { settings.setUpdateDeclinedAt(System.currentTimeMillis()) }
            }
        }

    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) updateReady = true
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        manager.registerListener(installListener)
    }

    override fun onResume(owner: LifecycleOwner) {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> updateReady = true
                // A required update that was interrupted (e.g. the app was closed) must be resumed.
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                    start(info, AppUpdateType.IMMEDIATE)
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && !askedThisSession -> offer(info)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        manager.unregisterListener(installListener)
    }

    /** Restarts the app into the downloaded version. */
    fun completeUpdate() {
        manager.completeUpdate()
    }

    private fun offer(info: AppUpdateInfo) {
        askedThisSession = true
        if (info.updatePriority() >= REQUIRED_PRIORITY && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            start(info, AppUpdateType.IMMEDIATE)
            return
        }
        if (!info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) return
        activity.lifecycleScope.launch {
            if (System.currentTimeMillis() - settings.updateDeclinedAt() >= REMIND_AFTER_DAYS * DAY_MS) {
                start(info, AppUpdateType.FLEXIBLE)
            }
        }
    }

    private fun start(info: AppUpdateInfo, type: Int) {
        runCatching { manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build()) }
    }

    private companion object {
        const val REQUIRED_PRIORITY = 4
        const val REMIND_AFTER_DAYS = 3
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
