package com.alarmai.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alarmai.app.data.local.PreferencesManager

/**
 * Reschedules the active alarm whenever the OS drops the pending [android.app.AlarmManager]
 * entries or invalidates their trigger time.
 *
 * All four actions matter, not just boot: an app update silently clears every scheduled alarm,
 * and a clock or timezone change leaves an already-scheduled alarm pointing at the wrong wall
 * time. `AlarmTimeCalculator` recomputes from the stored hour/minute, so rescheduling is the fix
 * in every case.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received broadcast: $action")

        if (action !in RESCHEDULE_ACTIONS) return

        val prefs = PreferencesManager(context)
        val alarm = prefs.getAlarm()
        if (alarm.isActive) {
            AlarmScheduler(context).schedule(alarm)
            Log.d("BootReceiver", "Rescheduled active alarm after $action")
        }
    }
}
