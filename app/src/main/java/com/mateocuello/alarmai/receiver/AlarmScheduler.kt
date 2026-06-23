package com.mateocuello.alarmai.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mateocuello.alarmai.data.model.Alarm
import com.mateocuello.alarmai.ui.alarm.AlarmActivity
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: Alarm, fromReceiver: Boolean = false) {
        if (!alarm.isActive) {
            cancel()
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextTime = AlarmTimeCalculator.calculateNextAlarmTime(alarm, System.currentTimeMillis(), fromReceiver)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nextTime
        }

        // Schedule Main Alarm
        try {
            val showIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val showPendingIntent = PendingIntent.getActivity(
                context,
                0,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(
                calendar.timeInMillis,
                showPendingIntent
            )
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d("AlarmScheduler", "Alarm scheduled for ${calendar.time}")
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Failed to schedule alarm: ${e.localizedMessage}")
        }

        // Schedule Pre-Alarm
        val now = System.currentTimeMillis()
        val preAlarmTime = nextTime - 120_000 // 2 minutes before
        val preAlarmIntent = Intent(context, PreAlarmReceiver::class.java)
        val preAlarmPendingIntent = PendingIntent.getBroadcast(
            context,
            1002,
            preAlarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (preAlarmTime > now) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        preAlarmTime,
                        preAlarmPendingIntent
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            preAlarmTime,
                            preAlarmPendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            preAlarmTime,
                            preAlarmPendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        preAlarmTime,
                        preAlarmPendingIntent
                    )
                }
                Log.d("AlarmScheduler", "Pre-alarm scheduled for ${java.util.Date(preAlarmTime)}")
            } catch (e: Exception) {
                Log.e("AlarmScheduler", "Failed to schedule pre-alarm: ${e.localizedMessage}")
            }
        } else {
            // Alarm is scheduled for less than 2 minutes from now. Trigger pre-fetch immediately.
            PreAlarmReceiver.startPrefetch(context)
        }
    }

    fun cancel() {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        val preIntent = Intent(context, PreAlarmReceiver::class.java)
        val prePendingIntent = PendingIntent.getBroadcast(
            context,
            1002,
            preIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (prePendingIntent != null) {
            alarmManager.cancel(prePendingIntent)
            prePendingIntent.cancel()
        }
        Log.d("AlarmScheduler", "Alarm and pre-alarm cancelled")
    }
}
