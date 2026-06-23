# Android 16 (API 36) Compatibility Investigation Analysis

## Summary
This report analyzes two main compatibility requirements for Android 16 (API 36) within the AlarmAI codebase:
1. **WorkManager Direct Boot Safety**: Checking user unlock status before initializing/scheduling `WorkManager` in a `directBootAware` receiver (`PreAlarmReceiver.kt`).
2. **Exact Alarm Permission Cleanup**: Eliminating redundant runtime permissions checks and settings redirects on Android 13+ (API 33+) since `USE_EXACT_ALARM` is declared and automatically granted at install time (`AlarmScheduler.kt` and `MainActivity.kt`).

---

## 1. WorkManager Direct Boot Safety Check in PreAlarmReceiver.kt

### Exact Locations
- **File**: `app/src/main/java/com/mateocuello/alarmai/receiver/PreAlarmReceiver.kt`
- **Lines**: 24–32
- **Related Manifest Line**: `app/src/main/AndroidManifest.xml` line 73 (`android:directBootAware="true"` for `PreAlarmReceiver`)

### Problem Details
`PreAlarmReceiver` is declared as `directBootAware="true"`. This means it can be triggered before the user has entered their credentials after a device reboot (Direct Boot mode). 

However, `WorkManager` utilizes credential-encrypted (CE) storage by default for its database. When `WorkManager.getInstance(context)` is called during Direct Boot, the system throws an `IllegalStateException` or fails because the CE storage is not yet decrypted. To ensure compatibility and prevent crashes during Direct Boot, we must guard the WorkManager call using `UserManagerCompat.isUserUnlocked(context)`.

### Proposed Change Strategy
1. Import `androidx.core.os.UserManagerCompat` in `PreAlarmReceiver.kt`.
2. Wrap the initialization and enqueue call within a check to ensure the user is unlocked.
3. If the user is locked (Direct Boot is active), skip the scheduling and log a warning.

#### Before:
```kotlin
        private fun enqueuePrefetchWork(context: Context) {
            try {
                val prefetchWorkRequest = OneTimeWorkRequestBuilder<PrefetchWorker>().build()
                WorkManager.getInstance(context).enqueue(prefetchWorkRequest)
                Log.d("PreAlarmReceiver", "Successfully enqueued PrefetchWorker")
            } catch (e: Exception) {
                Log.e("PreAlarmReceiver", "Failed to enqueue PrefetchWorker: ${e.localizedMessage}")
            }
        }
```

#### After (Proposed):
```kotlin
        private fun enqueuePrefetchWork(context: Context) {
            if (!UserManagerCompat.isUserUnlocked(context)) {
                Log.w("PreAlarmReceiver", "User is locked (Direct Boot). Skipping WorkManager prefetch.")
                return
            }
            try {
                val prefetchWorkRequest = OneTimeWorkRequestBuilder<PrefetchWorker>().build()
                WorkManager.getInstance(context).enqueue(prefetchWorkRequest)
                Log.d("PreAlarmReceiver", "Successfully enqueued PrefetchWorker")
            } catch (e: Exception) {
                Log.e("PreAlarmReceiver", "Failed to enqueue PrefetchWorker: ${e.localizedMessage}")
            }
        }
```

---

## 2. Redundant Runtime Checks and Settings Redirects for Exact Alarms

### Context
`AndroidManifest.xml` declares both:
- `android.permission.SCHEDULE_EXACT_ALARM` (introduced in API 31)
- `android.permission.USE_EXACT_ALARM` (introduced in API 33)

On Android 13+ (API 33+), the `USE_EXACT_ALARM` permission is automatically granted to alarm clock apps at install time. The user cannot revoke this permission in the "Alarms & Reminders" settings screen. 
Calling `canScheduleExactAlarms()` will always return true on API 33+, making runtime permission checks and settings redirects (`Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`) redundant and potentially problematic (redirecting the user to a settings page with no toggles or crashing if the activity isn't found). These checks should be restricted only to Android 12 (API 31/32).

---

### A. AlarmScheduler.kt Analysis

- **File**: `app/src/main/java/com/mateocuello/alarmai/receiver/AlarmScheduler.kt`
- **Lines**: 69–89

#### Before:
```kotlin
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
```

#### After (Proposed):
Optimize the exact alarm branch logic so that API 33+ directly invokes the exact alarm scheduler:
```kotlin
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Pre-granted via USE_EXACT_ALARM on Android 13+
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        preAlarmTime,
                        preAlarmPendingIntent
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Needs dynamic check on Android 12 (S) and 12L (S_V2)
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
                    // pre-API 31
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        preAlarmTime,
                        preAlarmPendingIntent
                    )
                }
```

---

### B. MainActivity.kt Analysis

- **File**: `app/src/main/java/com/mateocuello/alarmai/MainActivity.kt`
- **Lines**: 163 (LaunchedEffect), 747–767 (`triggerTestAlarm`), and 773–795 (`checkExactAlarmPermission`)

#### Proposed Changes:
1. **checkExactAlarmPermission(context)**: Restrict the permission check and settings redirect solely to Android 12 (API 31/32) devices.
2. **triggerTestAlarm(context)**: Skip the `canScheduleExactAlarms()` check on API 33+ and directly set exact alarm.

#### Changes in Detail:

**1. checkExactAlarmPermission(context)**
- **Before**:
```kotlin
private fun checkExactAlarmPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(
                context,
                "Please enable 'Alarms & Reminders' permission in settings for exact alarms.",
                Toast.LENGTH_LONG
            ).show()
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general settings
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
            }
        }
    }
}
```

- **After (Proposed)**:
```kotlin
private fun checkExactAlarmPermission(context: Context) {
    // Only check and redirect for Android 12/12L (S/S_V2), where SCHEDULE_EXACT_ALARM is not pre-granted
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(
                context,
                "Please enable 'Alarms & Reminders' permission in settings for exact alarms.",
                Toast.LENGTH_LONG
            ).show()
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general settings
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
            }
        }
    }
}
```

**2. triggerTestAlarm(context)**
- **Before**:
```kotlin
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }
```

- **After (Proposed)**:
```kotlin
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }
```
