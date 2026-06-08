package com.mateocuello.alarmai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mateocuello.alarmai.data.local.PreferencesManager
import com.mateocuello.alarmai.service.AlarmService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm fired!")
        
        val prefs = PreferencesManager(context)
        val alarm = prefs.getAlarm()
        if (alarm.isActive) {
            if (alarm.daysOfWeek.isNotEmpty()) {
                val scheduler = AlarmScheduler(context)
                scheduler.schedule(alarm, fromReceiver = true)
            } else {
                val updated = alarm.copy(isActive = false)
                prefs.saveAlarm(updated)
            }
        }
        
        val serviceIntent = Intent(context, AlarmService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
