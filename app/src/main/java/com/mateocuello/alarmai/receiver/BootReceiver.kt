package com.mateocuello.alarmai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mateocuello.alarmai.data.local.PreferencesManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received boot broadcast: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.LOCKED_BOOT_COMPLETED") {
            val prefs = PreferencesManager(context)
            val alarm = prefs.getAlarm()
            if (alarm.isActive) {
                val scheduler = AlarmScheduler(context)
                scheduler.schedule(alarm)
                Log.d("BootReceiver", "Rescheduled active alarm on reboot")
            }
        }
    }
}
