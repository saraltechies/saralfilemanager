package com.saraltechies.saralfilemanager.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.saraltechies.saralfilemanager.MainActivity
import com.saraltechies.saralfilemanager.R
import com.saraltechies.saralfilemanager.SaralFileManagerApp
import com.saraltechies.saralfilemanager.files.OperationKind
import com.saraltechies.saralfilemanager.files.OperationProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while a copy, move, zip or delete runs, so it finishes after you leave the app. The work itself
 * stays in [com.saraltechies.saralfilemanager.files.FileOperations]; this only shows its progress in a notification,
 * with Cancel, and stops when it's done.
 */
class FileOperationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watching: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val operations = (application as SaralFileManagerApp).fileOperations
        // Android requires this straight away, even if the work has just finished.
        ServiceCompat.startForeground(
            this, NOTIFICATION, build(operations.progress.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (intent?.action == ACTION_CANCEL) operations.cancel()
        if (watching == null) {
            watching = scope.launch {
                operations.progress.collectLatest { progress ->
                    if (progress == null) {
                        ServiceCompat.stopForeground(this@FileOperationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, build(progress))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 limits how long this kind of service may run: stop showing as running, and let the work carry on if it can. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun build(progress: OperationProgress?) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(progress?.let { resources.getQuantityString(label(it.kind), it.itemCount, it.itemCount) } ?: getString(R.string.app_name))
        .setContentText(progress?.currentName.orEmpty())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        .addAction(
            0, getString(R.string.cancel),
            PendingIntent.getService(
                this, 1, Intent(this, FileOperationService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .apply {
            val fraction = progress?.fraction
            if (fraction != null) setProgress(100, (fraction * 100).toInt(), false) else setProgress(0, 0, true)
        }
        .build()

    private fun label(kind: OperationKind): Int = when (kind) {
        OperationKind.COPY -> R.plurals.copying
        OperationKind.MOVE -> R.plurals.moving
        OperationKind.DELETE -> R.plurals.deleting
        OperationKind.ZIP -> R.plurals.compressing
        OperationKind.EXTRACT -> R.plurals.extracting
        OperationKind.TRASH -> R.plurals.moving_to_bin
        OperationKind.RESTORE -> R.plurals.restoring
    }

    companion object {
        private const val CHANNEL = "file_operations"
        private const val NOTIFICATION = 1
        private const val ACTION_CANCEL = "com.saraltechies.saralfilemanager.action.CANCEL_OPERATION"

        /** Started by FileOperations for work that goes on for more than a moment. Not fatal if Android says no. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, FileOperationService::class.java)) }
        }
    }
}
