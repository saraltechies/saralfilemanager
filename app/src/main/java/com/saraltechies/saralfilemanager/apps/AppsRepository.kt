package com.saraltechies.saralfilemanager.apps

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import com.saraltechies.saralfilemanager.files.FileNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One installed app, as shown on the Apps screen. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val isSystem: Boolean,
    /** The installed APK files. */
    val apkPaths: List<String>,
    val apkBytes: Long,
    /** App, data and cache sizes; only known with usage access. */
    val appBytes: Long? = null,
    val dataBytes: Long? = null,
    val cacheBytes: Long? = null,
    val installedAt: Long,
    val updatedAt: Long,
    /** When it was last opened, if usage access is on and it was used in the last year. */
    val lastUsedAt: Long? = null,
) {
    /** Everything the app takes when known (app + data, which includes cache), otherwise its APK files. */
    val totalBytes: Long get() = if (appBytes != null) appBytes + (dataBytes ?: 0) else apkBytes
}

/**
 * Apps you can open from the launcher (the ones people install and remove), their sizes and when they were last
 * used. Sizes beyond the APK and the last-used dates need "Usage access", which the person turns on in Settings.
 * Uninstalling and clearing data go through Android's own screens, which ask for confirmation.
 */
class AppsRepository(private val context: Context) {

    private val pm get() = context.packageManager

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Opens the Usage access setting, on this app's own switch where the phone allows it. */
    fun openUsageAccessSettings() {
        val own = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName))
        if (!start(own)) start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    suspend fun installed(): List<AppInfo> = withContext(Dispatchers.IO) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packages = pm.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }.distinct()
        val usage = hasUsageAccess()
        val lastUsed = if (usage) lastUsedTimes() else emptyMap()
        val stats = if (usage) context.getSystemService(StorageStatsManager::class.java) else null
        packages.mapNotNull { pkg ->
            ensureActive()
            runCatching {
                val info = pm.getPackageInfo(pkg, 0)
                val app = info.applicationInfo ?: return@runCatching null
                val apks = listOfNotNull(app.sourceDir) + app.splitSourceDirs.orEmpty()
                val sizes = stats?.let { runCatching { it.queryStatsForPackage(app.storageUuid, pkg, Process.myUserHandle()) }.getOrNull() }
                AppInfo(
                    packageName = pkg,
                    label = app.loadLabel(pm).toString(),
                    versionName = info.versionName,
                    isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    apkPaths = apks,
                    apkBytes = apks.sumOf { File(it).length() },
                    appBytes = sizes?.appBytes,
                    dataBytes = sizes?.dataBytes,
                    cacheBytes = sizes?.cacheBytes,
                    installedAt = info.firstInstallTime,
                    updatedAt = info.lastUpdateTime,
                    lastUsedAt = lastUsed[pkg],
                )
            }.getOrNull()
        }
    }

    private fun lastUsedTimes(): Map<String, Long> = runCatching {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        usm.queryAndAggregateUsageStats(now - 365L * DAY, now).mapValues { it.value.lastTimeUsed }.filterValues { it > 0 }
    }.getOrDefault(emptyMap())

    fun icon(packageName: String): Drawable? = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()

    fun open(app: AppInfo): Boolean = pm.getLaunchIntentForPackage(app.packageName)?.let(::start) ?: false

    /** Android's uninstall prompt. */
    fun uninstallIntent(app: AppInfo) = Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName))

    /** Android's app info page, with Storage → Clear cache / Clear storage. */
    fun openAppInfo(app: AppInfo): Boolean =
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName)))

    /**
     * Saves the app's installer to Download/Saral Files/APKs: one .apk, or for apps split into several parts, an
     * .apks archive of all of them (installable with apps that read that format).
     */
    suspend fun backUp(app: AppInfo): File = withContext(Dispatchers.IO) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Saral Files/APKs")
        dir.mkdirs()
        val base = (app.label + (app.versionName?.let { " $it" } ?: "")).replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { app.packageName }
        val split = app.apkPaths.size > 1
        val name = FileNames.unique(base + if (split) ".apks" else ".apk", isDirectory = false) { File(dir, it).exists() }
        val out = File(dir, name)
        try {
            if (!split) {
                File(app.apkPaths.first()).copyTo(out)
            } else {
                ZipOutputStream(out.outputStream().buffered()).use { zip ->
                    app.apkPaths.forEach { path ->
                        ensureActive()
                        zip.putNextEntry(ZipEntry(File(path).name))
                        File(path).inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
        out
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val UNUSED_DAYS = 30

        /** Apps not opened for [UNUSED_DAYS] days (and installed longer than that); needs last-used dates. */
        fun unused(apps: List<AppInfo>, now: Long = System.currentTimeMillis()): List<AppInfo> {
            val before = now - UNUSED_DAYS * DAY
            return apps.filter { !it.isSystem && it.installedAt < before && (it.lastUsedAt ?: 0L) < before }
        }
    }
}
