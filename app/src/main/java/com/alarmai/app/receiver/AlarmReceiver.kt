package com.alarmai.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.alarmai.app.data.local.PreferencesManager
import com.alarmai.app.service.AlarmService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm fired!")
        
        val isTest = intent.getBooleanExtra("is_test", false)

        // A test firing must ring but must never mutate the saved alarm. Without this guard a
        // test alarm reschedules (or, for a one-shot, permanently deactivates) the real alarm.
        if (isTest) {
            startAlarmService(context)
            return
        }

        val prefs = PreferencesManager(context)
        val alarm = prefs.getAlarm()
        if (!alarm.isActive) return

        if (alarm.daysOfWeek.isNotEmpty()) {
            val scheduler = AlarmScheduler(context)
            scheduler.schedule(alarm, fromReceiver = true)
        } else {
            val updated = alarm.copy(isActive = false)
            prefs.saveAlarm(updated)
        }

        startAlarmService(context)
    }

    private fun startAlarmService(context: Context) {
        val serviceIntent = Intent(context, AlarmService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
