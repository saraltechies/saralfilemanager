package com.saraltechies.saralfilemanager

import android.app.Application
import com.saraltechies.saralfilemanager.data.SettingsRepository
import com.saraltechies.saralfilemanager.files.FileOperations
import com.saraltechies.saralfilemanager.files.FileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the app-wide singletons; small enough that a DI framework isn't worth it. */
class SaralFileManagerApp : Application() {
    /** Copies and moves run here, so they finish even if the screen that started them is closed. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsRepository by lazy { SettingsRepository(this) }
    val fileRepository by lazy { FileRepository(this) }
    val fileOperations by lazy { FileOperations(this, appScope) }
}
