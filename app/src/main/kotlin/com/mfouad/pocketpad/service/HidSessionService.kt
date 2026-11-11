package com.mfouad.pocketpad.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mfouad.pocketpad.AppGraph
import com.mfouad.pocketpad.MainActivity
import com.mfouad.pocketpad.R
import com.mfouad.pocketpad.session.Phase
import com.mfouad.pocketpad.session.Problem
import com.mfouad.pocketpad.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * User-started foreground service (type connectedDevice) that keeps the process at foreground
 * importance while a session is active. The Android stack unregisters an HID app whose process
 * drops below "visible" importance (docs/ARCHITECTURE.md section 5.1).
 *
 * The service lives exactly as long as the session: it stops itself when the session ends.
 */
class HidSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchers: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val controller = AppGraph.controller
        if (intent?.action == ACTION_STOP) {
            controller.stop()
            if (!controller.state.value.isActive) stopSelf()
            return START_NOT_STICKY
        }
        if (!enterForeground(controller.state.value)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (watchers == null) {
            watchers =
                scope.launch {
                    launch { controller.sessionEnded.collect { finish() } }
                    launch { controller.state.collect { updateNotification(it) } }
                }
        }
        controller.start()
        // Not sticky: never restart a session the user did not start.
        return START_NOT_STICKY
    }

    private fun enterForeground(state: SessionState): Boolean =
        try {
            val type =
                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    0
                }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(state), type)
            true
        } catch (e: SecurityException) {
            AppGraph.controller.reportBlocked(Problem.SERVICE_START_BLOCKED, e.message)
            false
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException.
            AppGraph.controller.reportBlocked(Problem.SERVICE_START_BLOCKED, e.message)
            false
        }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(state: SessionState) {
        if (!state.isActive) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: SessionState): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, HidSessionService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val text =
            when (state.phase) {
                Phase.CONNECTED -> R.string.notification_connected
                Phase.CONNECTING -> R.string.notification_connecting
                Phase.REGISTERED -> R.string.notification_waiting
                Phase.STOPPING -> R.string.notification_stopping
                else -> R.string.notification_starting
            }
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_trackpad)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.action_stop), stop)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        // If the system or the user ends the service, end the session with it.
        AppGraph.controller.stop()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.mfouad.pocketpad.action.START"
        private const val ACTION_STOP = "com.mfouad.pocketpad.action.STOP"
        private const val CHANNEL_ID = "session"
        private const val NOTIFICATION_ID = 1

        fun createChannel(context: Context) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.notification_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Starts a session. Must be called from a visible activity in response to the user. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, HidSessionService::class.java).setAction(ACTION_START),
                )
            } catch (e: IllegalStateException) {
                AppGraph.controller.reportBlocked(Problem.SERVICE_START_BLOCKED, e.message)
            }
        }
    }
}
