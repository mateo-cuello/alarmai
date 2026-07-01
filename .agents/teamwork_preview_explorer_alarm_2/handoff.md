# Handoff Report: Broken Alarm Investigation & Android 16 (API 37) Compatibility

This report synthesizes findings, logic, and fixes to resolve broken alarm behavior and establish compatibility with Android 16 (API 37).

---

## 1. Observation

1. **Test Alarm Behavior**:
   * File: `app/src/main/java/com/mateocuello/alarmai/receiver/AlarmReceiver.kt`
   * Lines 15-32:
     ```kotlin
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

         val serviceIntent = Intent(context, AlarmService::class.java)
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             context.startForegroundService(serviceIntent)
         } else {
             context.startService(serviceIntent)
         }
     }
     ```
   * Verbatim change from git diff: The start service call was nested inside the `if (alarm.isActive)` condition, preventing the service from launching if the main alarm is inactive.
   * File: `app/src/main/java/com/mateocuello/alarmai/MainActivity.kt`
   * Lines 734-777: `triggerTestAlarm()` uses `alarmManager.setExactAndAllowWhileIdle()` on API 33+ without checking `canScheduleExactAlarms()`, which throws `SecurityException` on API 34+ when permissions are denied.

2. **Full-Screen Activity Block**:
   * File: `app/src/main/java/com/mateocuello/alarmai/service/AlarmService.kt`
   * Lines 39-48:
     ```kotlin
     val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
         addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
     }
     val fullScreenPendingIntent = PendingIntent.getActivity(
         this,
         0,
         fullScreenIntent,
         PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
     )
     ```
   * Observation: The PendingIntent is created without explicit background activity launch permissions.

3. **ViewModel Instantiation Crash**:
   * File: `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`
   * Lines 40-49:
     ```kotlin
     class AlarmViewModel(
         application: Application,
         private val prefs: PreferencesManager = PreferencesManager(application),
         private val locationProvider: LocationProvider = LocationProvider(application),
         private val weatherRepository: WeatherRepository = WeatherRepository(),
         private val newsRepository: NewsRepository = NewsRepository(),
         private val calendarRepository: CalendarRepository = CalendarRepository(application),
         private val geminiAgentManager: GeminiAgentManager = GeminiAgentManager(application, prefs),
         private val voiceManager: VoiceManager = VoiceManager(application, prefs)
     ) : AndroidViewModel(application)
     ```
   * Observation: The constructor does not have `@JvmOverloads` annotation.
   * Symptom: Tapping the notification throws `NoSuchMethodException` from `ViewModelProvider` since it cannot find a constructor taking only `Application`.

---

## 2. Logic Chain

1. **Test Alarm Failure**:
   * `MainActivity.kt` triggers a test alarm which invokes `AlarmReceiver` without changing the preferences alarm active status.
   * Since `AlarmReceiver.kt` executes the service startup conditionally based on `alarm.isActive` (Observation 1), it skips launching `AlarmService` when the scheduled alarm is inactive.
   * Therefore, the test alarm fails silently.

2. **Full-Screen UI Fails to Display**:
   * Starting in Android 14 (API 34) and carried into Android 16 (API 37), background activity starts via PendingIntents are blocked by default.
   * Because `fullScreenPendingIntent` (Observation 2) does not declare background start authorization, the system blocks the activity display when the device is locked/backgrounded.

3. **Notification Tap Crash**:
   * `AlarmActivity` initializes `AlarmViewModel` using `by viewModels()`.
   * Under the hood, `ViewModelProvider` searches for a constructor that takes only `Application` to satisfy `AndroidViewModel`.
   * Because the constructor has multiple arguments with default parameters but lacks `@JvmOverloads` (Observation 3), the Kotlin compiler does not generate the single-argument overload.
   * Reflection fails to find a single-argument constructor, crashing with a `NoSuchMethodException` when launching `AlarmActivity`.

---

## 3. Caveats

* **Google Play Policy**: While `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` compiles and runs successfully, standard Google Play apps require developer console justification forms. For local builds/emulators, this functions perfectly.
* **Test Alarm Intent**: The suggested fix introduces an intent extra `is_test` to bypass the `alarm.isActive` check. We assume no other components broadcast intents to `AlarmReceiver` with matching extras.

---

## 4. Conclusion

The alarm system broke due to missing background activity launch privileges on the `fullScreenPendingIntent` (Android 14+ requirement), missing `@JvmOverloads` annotation on `AlarmViewModel` (causing reflection failure on activity load), and a conditional block in `AlarmReceiver` that restricts service starts to active alarms only (breaking the test alarm).

---

## 5. Verification Method

1. **Unit Tests**:
   * Run the test suite: `./gradlew test` (verifies standard regressions and existing behavior).
2. **Manual Execution Verification**:
   * Compile the project: `./gradlew assembleDebug`
   * Trigger "Test Alarm" and lock the device.
   * Verify:
     1. The alarm fires in 5 seconds (verifies test button and scheduler).
     2. The full-screen `AlarmActivity` UI displays over the lockscreen (verifies full-screen intent permissions and `ActivityOptions` settings).
     3. Tapping the notification launches the activity cleanly without crash (verifies constructor overload reflection).
