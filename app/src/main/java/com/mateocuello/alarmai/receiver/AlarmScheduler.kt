package com.mateocuello.alarmai.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mateocuello.alarmai.data.model.Alarm
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

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val nowTime = System.currentTimeMillis()
        if (alarm.daysOfWeek.isNotEmpty()) {
            val startOffset = if (fromReceiver) 1 else 0
            var found = false
            for (offset in startOffset..7) {
                val testCalendar = (calendar.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, offset)
                }
                val testDayOfWeek = testCalendar.get(Calendar.DAY_OF_WEEK)
                if (alarm.daysOfWeek.contains(testDayOfWeek)) {
                    if (offset > 0 || testCalendar.timeInMillis > nowTime) {
                        calendar.timeInMillis = testCalendar.timeInMillis
                        found = true
                        break
                    }
                }
            }
            if (!found) {
                if (calendar.timeInMillis <= nowTime) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        } else {
            if (calendar.timeInMillis <= nowTime) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Fallback if permission is missing
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Alarm scheduled for ${calendar.time}")
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Failed to schedule alarm: ${e.localizedMessage}")
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
            Log.d("AlarmScheduler", "Alarm cancelled")
        }
    }
}
