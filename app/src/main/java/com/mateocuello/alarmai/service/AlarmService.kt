package com.mateocuello.alarmai.service

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
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mateocuello.alarmai.MainActivity
import com.mateocuello.alarmai.ui.alarm.AlarmActivity
import com.mateocuello.alarmai.data.local.PreferencesManager

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val CHANNEL_ID = "alarm_ai_channel"
        private const val NOTIFICATION_ID = 9999
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AlarmService", "Starting alarm service")
        
        // Setup Full Screen Intent
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
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

        if (canUseFullScreen) {
            notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        val notification = notificationBuilder.build()

        // Start Foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Fallback: If we can't use full screen intent, launch AlarmActivity directly
        if (!canUseFullScreen) {
            Log.d("AlarmService", "Full screen intent not allowed. Launching AlarmActivity directly.")
            try {
                startActivity(fullScreenIntent)
            } catch (e: Exception) {
                Log.e("AlarmService", "Failed to start AlarmActivity directly: ${e.localizedMessage}")
            }
        }

        // Start playing alarm ringtone
        playAlarmSound()

        return START_STICKY
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
                prepare()
                start()
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
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        Log.d("AlarmService", "Alarm service stopped and player released")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
