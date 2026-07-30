package com.alarmai.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.os.UserManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.alarmai.app.service.PrefetchWorker
import java.util.concurrent.TimeUnit

class PreAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("PreAlarmReceiver", "Pre-alarm triggered, scheduling background pre-fetching via WorkManager!")
        enqueuePrefetchWork(context)
    }

    companion object {
        /** Unique work name so rapid alarm edits can't fan out into concurrent billed Gemini calls. */
        const val PREFETCH_WORK_NAME = "alarmai_prefetch"

        fun startPrefetch(context: Context) {
            Log.d("PreAlarmReceiver", "Requesting immediate background pre-fetch via WorkManager")
            enqueuePrefetchWork(context)
        }

        private fun enqueuePrefetchWork(context: Context) {
            try {
                if (!UserManagerCompat.isUserUnlocked(context)) {
                    Log.w("PreAlarmReceiver", "User is locked. Skipping prefetch work.")
                    return
                }
                val prefetchWorkRequest = OneTimeWorkRequestBuilder<PrefetchWorker>()
                    // Plain work is deferrable, and at T-2min the device is deep in Doze, so the
                    // prefetch would routinely not run before the alarm fired. Expedited work
                    // runs immediately; PrefetchWorker.getForegroundInfo() backs the pre-31 path.
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(PREFETCH_WORK_NAME, ExistingWorkPolicy.KEEP, prefetchWorkRequest)
                Log.d("PreAlarmReceiver", "Successfully enqueued PrefetchWorker")
            } catch (e: Exception) {
                Log.e("PreAlarmReceiver", "Failed to enqueue PrefetchWorker: ${e.localizedMessage}")
            }
        }
    }
}
