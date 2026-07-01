# Analysis: Broken Alarm Functionality and Android 16 (API 37) Compatibility

This document details the root cause analysis of the broken alarm functionality in the AlarmAI application and identifies the changes needed to make it fully compatible with Android 16 (API 37).

---

## 1. Git History Examination

Comparing the last working commit `ed2f138063533d635ec7db66f2a4a1c8f5bf62e2` with the current state (`HEAD`), we identified key changes in configuration and implementation:

### 1.1 AndroidManifest.xml
* **Removed Permission**: `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" android:maxSdkVersion="32" />` was removed in favor of `<uses-permission android:name="android.permission.USE_EXACT_ALARM" />`.
* **FGS Type Changed**: `AlarmService` shifted from `foregroundServiceType="mediaPlayback"` to `"specialUse"`:
  ```xml
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
  ...
  <service
      android:name=".service.AlarmService"
      android:enabled="true"
      android:exported="false"
      android:foregroundServiceType="specialUse"
      android:directBootAware="true">
      <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="alarm" />
  </service>
  ```

### 1.2 AlarmScheduler.kt
* **API Shift**: `AlarmScheduler.kt` switched from calling `setExactAndAllowWhileIdle()` to `setAlarmClock()` to schedule the main alarm, utilizing `AlarmManager.AlarmClockInfo` with a `showPendingIntent` targeting `AlarmActivity`.

### 1.3 AlarmReceiver.kt
* **Service Scope Changed**: The call to `context.startForegroundService(serviceIntent)` was moved inside the `if (alarm.isActive)` check block. Previously, it executed unconditionally outside the block.

### 1.4 AlarmService.kt
* **FGS Type Shift**: The foreground service starts with `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+ instead of `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`.
* **Full-Screen Intent Check**: Added runtime check for `notificationManager.canUseFullScreenIntent()`.

### 1.5 AlarmViewModel.kt
* **Constructor Refactor**: The constructor was updated to accept several helper dependencies with default parameters (for unit testing), but no `@JvmOverloads` annotation was added:
  ```kotlin
  class AlarmViewModel(
      application: Application,
      private val prefs: PreferencesManager = PreferencesManager(application),
      private val locationProvider: LocationProvider = LocationProvider(application),
      ...
  )
  ```

---

## 2. Symptom Analysis

### Symptom A: Test Alarm button doesn't work
1. **Root Cause**: In `MainActivity.kt`, the button schedules the test alarm by broadcasting to `AlarmReceiver`. In `AlarmReceiver.kt`, the service `AlarmService` is only started if `alarm.isActive` is `true`. Since testing a 5-second alarm is separate from the user's real scheduled morning alarm status, if the user does not have an active repeating/single alarm, `alarm.isActive` evaluates to `false`, and `AlarmReceiver` silently ignores the test broadcast.
2. **Additional Issue**: In `MainActivity.kt:triggerTestAlarm`, the test alarm is scheduled via `setExactAndAllowWhileIdle()` without checking `canScheduleExactAlarms()`. On API 34+ (including Android 16), calling this without permission throws a `SecurityException`.

### Symptom B: When the alarm fires, the full-screen alarm UI doesn't show (only a notification appears)
1. **Root Cause**: Starting in Android 14 (API 34) and carried through Android 15/16, the system strictly restricts background activity launches from PendingIntents (including full-screen intents). 
2. In `AlarmService.kt`, the `fullScreenPendingIntent` is constructed with `PendingIntent.FLAG_IMMUTABLE` but does not configure `ActivityOptions` to allow background activity launches. The OS blocks `AlarmActivity` from displaying over the lockscreen or launching in the background.
3. In `MainActivity.kt`, `triggerTestAlarm` schedules the test alarm via `setExactAndAllowWhileIdle`. Unlike `setAlarmClock()`, this does not signify to the OS that the app is acting as an active alarm clock, which prevents the auto-grant of the `USE_FULL_SCREEN_INTENT` privilege.

### Symptom C: Tapping the notification crashes with "AlarmAI has stopped working"
1. **Root Cause**: In Kotlin, a constructor with default parameters compiles to a single JVM constructor containing all arguments. Overloaded JVM constructors (e.g. taking just `Application`) are not generated unless `@JvmOverloads` is applied. 
2. When the user taps the notification, `AlarmActivity` launches, which calls `private val viewModel: AlarmViewModel by viewModels()`. Android's default `ViewModelProvider.Factory` reflects on the ViewModel's constructors to find a constructor with only `Application` (or `Application` and `SavedStateHandle`).
3. Since `@JvmOverloads` is missing on `AlarmViewModel`, the JVM only exposes the full 8-parameter constructor. The factory fails to instantiate `AlarmViewModel`, resulting in a `NoSuchMethodException` / `RuntimeException` and crashing the application.

---

## 3. Android 16 (API 37) Compatibility Requirements

Android 16 continues the background limitations and sandboxing introduced in Android 14/15:
1. **Exact Alarm Exemption**: Declaring `USE_EXACT_ALARM` allows the app to request exact alarms. However, scheduling via `setAlarmClock()` is the only way to automatically satisfy the exact alarm requirement for full-screen intent eligibility and background activity launches.
2. **Background Activity Launch restrictions**: Any `PendingIntent` designed to launch a full-screen UI (`setFullScreenIntent`) or launch an activity from the background must explicitly allow background activity start mode using `ActivityOptions.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)`.
3. **Foreground Service Types**: `specialUse` is allowed for custom alarm utilities if configured with `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="alarm" />` in the manifest. 

---

## 4. Specific Fix Strategy

### Fix 1: Fix the Test Alarm Button (Symptom A)
* **MainActivity.kt**: Modify `triggerTestAlarm()` to:
  1. Pass a boolean extra `is_test = true` in the Intent:
     ```kotlin
     val intent = Intent(context, AlarmReceiver::class.java).apply {
         putExtra("is_test", true)
     }
     ```
  2. Use `setAlarmClock` instead of `setExactAndAllowWhileIdle` to bypass exact alarm permission checks and register the intent as an alarm clock event:
     ```kotlin
     val showIntent = Intent(context, AlarmActivity::class.java).apply {
         flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
     }
     val showPendingIntent = PendingIntent.getActivity(
         context,
         1002,
         showIntent,
         PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
     )
     val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
     alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
     ```
* **AlarmReceiver.kt**: Read `is_test` from the incoming intent and start the service if it's true:
  ```kotlin
  val isTest = intent.getBooleanExtra("is_test", false)
  if (isTest || alarm.isActive) {
      // Start service...
  }
  ```

### Fix 2: Enable Full-Screen Activity Launch (Symptom B)
* **AlarmService.kt**: Create `fullScreenPendingIntent` with explicit background activity start privileges using `ActivityOptions`:
  ```kotlin
  val options = ActivityOptions.makeBasic()
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      options.setPendingIntentBackgroundActivityStartMode(
          ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
      )
  }
  val fullScreenPendingIntent = PendingIntent.getActivity(
      this,
      0,
      fullScreenIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      options.toBundle()
  )
  ```

### Fix 3: Resolve ViewModel Instantiation Crash (Symptom C)
* **AlarmViewModel.kt**: Add the `@JvmOverloads` annotation to the constructor of `AlarmViewModel` to generate the default constructor expected by Android's `ViewModelProvider`:
  ```kotlin
  class AlarmViewModel @JvmOverloads constructor(
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
