package com.mateocuello.alarmai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mateocuello.alarmai.service.PrefetchWorker

class PreAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("PreAlarmReceiver", "Pre-alarm triggered, scheduling background pre-fetching via WorkManager!")
        enqueuePrefetchWork(context)
    }

    companion object {
        fun startPrefetch(context: Context) {
            Log.d("PreAlarmReceiver", "Requesting immediate background pre-fetch via WorkManager")
            enqueuePrefetchWork(context)
        }

        private fun enqueuePrefetchWork(context: Context) {
            try {
                val prefetchWorkRequest = OneTimeWorkRequestBuilder<PrefetchWorker>().build()
                WorkManager.getInstance(context).enqueue(prefetchWorkRequest)
                Log.d("PreAlarmReceiver", "Successfully enqueued PrefetchWorker")
            } catch (e: Exception) {
                Log.e("PreAlarmReceiver", "Failed to enqueue PrefetchWorker: ${e.localizedMessage}")
            }
        }
    }
}
