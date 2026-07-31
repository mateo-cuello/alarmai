package com.alarmai.app.service

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alarmai.app.MainActivity
import com.alarmai.app.ui.alarm.AlarmActivity
import com.alarmai.app.data.local.PreferencesManager

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var autoStopHandler: Handler? = null

    companion object {
        private const val CHANNEL_ID = "alarm_ai_channel"
        private const val NOTIFICATION_ID = 9999

        /** Explicit stop, fired by the notification's Stop action. */
        const val ACTION_STOP = "com.alarmai.app.action.STOP_ALARM"

        /** Distinct from AlarmScheduler.REQUEST_CODE_SHOW so the two PendingIntents don't collide. */
        private const val REQUEST_CODE_FULL_SCREEN = 1005
        private const val REQUEST_CODE_STOP = 1006

        /** Give up ringing after this long so a missed dismiss can't drain the battery. */
        private const val MAX_RING_MS = 10 * 60_000L
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent means the system restarted us after a kill. Returning START_STICKY here
        // used to make the alarm ring again at an arbitrary later time with nothing scheduled.
        if (intent == null) {
            Log.w("AlarmService", "Restarted with null intent; stopping instead of phantom ringing")
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_STOP) {
            Log.d("AlarmService", "Stop action received")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d("AlarmService", "Starting alarm service")

        // Setup Full Screen Intent
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_FULL_SCREEN,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val canUseFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            notificationManager.canUseFullScreenIntent()
        } else {
            true
        }

        // Build notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("AlarmAI is ringing!")
            .setContentText("Tap to open assistant")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)

        if (canUseFullScreen) {
            notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        val notification = notificationBuilder.build()

        // Start Foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start playing alarm ringtone. Guard against a redelivered start building a second player.
        if (mediaPlayer == null) {
            playAlarmSound()
        }
        scheduleAutoStop()

        return START_NOT_STICKY
    }

    private fun scheduleAutoStop() {
        autoStopHandler?.removeCallbacksAndMessages(null)
        autoStopHandler = Handler(Looper.getMainLooper()).also { handler ->
            handler.postDelayed({
                Log.w("AlarmService", "Max ring duration reached; stopping")
                stopSelf()
            }, MAX_RING_MS)
        }
    }

    private fun playAlarmSound() {
        try {
            val prefs = PreferencesManager(this)
            val ringtoneUriStr = prefs.getAlarmRingtoneUri()
            var alarmUri: Uri? = if (ringtoneUriStr.isNotEmpty()) Uri.parse(ringtoneUriStr) else null

            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            val volumePercent = prefs.getAlarmVolume()
            val volumeFloat = volumePercent / 100f

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri!!)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                setVolume(volumeFloat, volumeFloat)
                // prepareAsync, not prepare: onStartCommand runs on the main thread and a
                // content:// ringtone can block on I/O long enough to ANR the alarm.
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    Log.e("AlarmService", "MediaPlayer error what=$what extra=$extra")
                    false
                }
                prepareAsync()
            }
            Log.d("AlarmService", "Playing alarm sound with volume $volumePercent%")
        } catch (e: Exception) {
            Log.e("AlarmService", "Failed to play alarm sound: ${e.localizedMessage}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AlarmAI Alerts"
            val descriptionText = "Channel for exact alarm notification trigger"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setSound(null, null) // Silence channel sound, let MediaPlayer handle it
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoStopHandler?.removeCallbacksAndMessages(null)
        autoStopHandler = null
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        Log.d("AlarmService", "Alarm service stopped and player released")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
