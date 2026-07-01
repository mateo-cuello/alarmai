# Handoff Report: Alarm Compatibility & Fixes for Android 16 (API 37)

## 1. Observation
We observed the following code issues and configurations that explain the broken alarm symptoms and compatibility blockers with Android 16:

- **Missing `@JvmOverloads` in `AlarmViewModel`**:
  File: `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt` (lines 40-49)
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
  The constructor defines default parameters but lacks `@JvmOverloads`, meaning no 1-parameter constructor `AlarmViewModel(Application)` is compiled into Java bytecode.

- **Missing PendingIntent Background Activity Launch Privileges**:
  File: `app/src/main/java/com/mateocuello/alarmai/service/AlarmService.kt` (lines 43-48)
  ```kotlin
  val fullScreenPendingIntent = PendingIntent.getActivity(
      this,
      0,
      fullScreenIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
  )
  ```
  The `PendingIntent` for `AlarmActivity` does not include `ActivityOptions` with background activity starts enabled.

- **Unsafe Exact Alarm Scheduling**:
  File: `app/src/main/java/com/mateocuello/alarmai/MainActivity.kt` (lines 747-759)
  ```kotlin
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerTime,
          pendingIntent
      )
  }
  ```
  and `app/src/main/java/com/mateocuello/alarmai/receiver/AlarmScheduler.kt` (lines 76-83):
  ```kotlin
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          preAlarmTime,
          preAlarmPendingIntent
      )
  }
  ```
  For API >= 33, both paths call `setExactAndAllowWhileIdle` without checking `canScheduleExactAlarms()`.

- **Restricted Exact Alarm Permission Check**:
  File: `app/src/main/java/com/mateocuello/alarmai/MainActivity.kt` (lines 773-774)
  ```kotlin
  private fun checkExactAlarmPermission(context: Context) {
      if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) {
  ```
  The permission check is excluded for all devices running API >= 33 (Android 13+).

- **Improper Foreground Service Type (`specialUse`)**:
  File: `app/src/main/AndroidManifest.xml` (lines 23, 89-94)
  ```xml
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
  ...
  <service
      android:name=".service.AlarmService"
      android:foregroundServiceType="specialUse"
      android:directBootAware="true">
      <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="alarm" />
  </service>
  ```
  The service uses the highly restricted `specialUse` FGS type instead of `mediaPlayback`.

---

## 2. Logic Chain
1. **Symptom 3 (Crash on notification tap)**:
   - When the user taps the notification, the system launches `AlarmActivity`.
   - `AlarmActivity` attempts to resolve `AlarmViewModel` using `by viewModels()`.
   - Because `AlarmViewModel` constructor has default parameters but lacks `@JvmOverloads`, it only exists in bytecode as an 8-parameter constructor.
   - `ViewModelProvider.AndroidViewModelFactory` attempts to instantiate it reflectively with `AlarmViewModel(Application)`, which fails with a `NoSuchMethodException` and crashes the app.
   - *Conclusion*: Adding `@JvmOverloads` to `AlarmViewModel` constructor resolves the crash.

2. **Symptom 2 (Full-screen UI doesn't show)**:
   - Starting in Android 14 (API 34), launching activities from background contexts (like a `PendingIntent` from `AlarmService`) is blocked by default unless the `PendingIntent` is explicitly configured to allow background activity starts.
   - Since the full-screen pending intent in `AlarmService.kt` and `AlarmScheduler.kt` lacks these options, the system suppresses the launch and only posts the heads-up notification.
   - *Conclusion*: Attaching `ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED` to the `PendingIntent` resolves this.

3. **Symptom 1 (Test Alarm button doesn't work)**:
   - `MainActivity.kt` calls `setExactAndAllowWhileIdle` directly on Android 13+ without checking `canScheduleExactAlarms()`.
   - Since `SCHEDULE_EXACT_ALARM` is denied by default on Android 14+ and not checked, it throws `SecurityException` which is caught in the try-catch block, failing silently.
   - *Conclusion*: Changing the check to `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` and providing a fallback to `setAndAllowWhileIdle` when permission is not granted resolves the silent failure.

4. **Android 16 Compatibility (FGS type)**:
   - Android FGS guidelines state that playing background audio (ringtone) must use `mediaPlayback` rather than `specialUse`.
   - *Conclusion*: Reverting to `mediaPlayback` ensures compliance and prevents store rejection.

---

## 3. Caveats
- Since this is a read-only investigation, the proposed code changes were not written to the project's source directory (only documented in this folder).
- We assume that the target runtime is Android 16 (API 37) and the behavior matches the preview SDK specifications.

---

## 4. Conclusion
The alarm functionality is broken due to a reflection failure in ViewModel instantiation (crash on tap), missing background activity launch flags on PendingIntents (no full-screen UI), and unsafe scheduling of exact alarms (broken test alarm). Correcting these configuration issues and aligning FGS declarations with standard Android guidelines will restore full functionality and guarantee Android 16 compatibility.

---

## 5. Verification Method
### Independent Verification Steps:
1. Apply the proposed code changes listed below.
2. Run `./gradlew test` (or `gradlew.bat test` on Windows) to verify all unit tests pass successfully.
3. Build the application and launch it on an Android 16 (API 37) emulator or device.
4. Click the **Test Alarm** button. Verify it requests exact alarm permission if not granted, or schedules the alarm successfully.
5. Lock the device and wait for the alarm to trigger. Verify that the full-screen alarm screen (`AlarmActivity`) displays over the lock screen.
6. Trigger the alarm, let the notification show, and tap it. Verify that it opens the Assistant screen without crashing.

---

## 6. Proposed Code Changes

### A. Fix ViewModel Reflection Crash
In `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`:
```kotlin
// Before:
class AlarmViewModel(
    application: Application,
    private val prefs: PreferencesManager = PreferencesManager(application),
    ...

// After:
class AlarmViewModel @JvmOverloads constructor(
    application: Application,
    private val prefs: PreferencesManager = PreferencesManager(application),
    ...
```

### B. Allow Background Activity Start for PendingIntents
In `app/src/main/java/com/mateocuello/alarmai/service/AlarmService.kt`:
```kotlin
// Before:
val fullScreenPendingIntent = PendingIntent.getActivity(
    this,
    0,
    fullScreenIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

// After:
val options = android.app.ActivityOptions.makeBasic()
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
}
val fullScreenPendingIntent = PendingIntent.getActivity(
    this,
    0,
    fullScreenIntent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    options.toBundle()
)
```
Apply the same `ActivityOptions` change to `AlarmScheduler.kt` inside `schedule` for the `showPendingIntent`.

### C. Safe Exact Alarm Scheduling
In `app/src/main/java/com/mateocuello/alarmai/MainActivity.kt` inside `triggerTestAlarm`:
```kotlin
// Before:
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerTime,
        pendingIntent
    )
}

// After:
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
```
In `app/src/main/java/com/mateocuello/alarmai/receiver/AlarmScheduler.kt` inside `schedule` (pre-alarm scheduling):
Apply the exact same conditional checks and fallback logic to `preAlarmPendingIntent` to avoid `SecurityException` on API >= 33.

In `app/src/main/java/com/mateocuello/alarmai/MainActivity.kt` inside `checkExactAlarmPermission`:
```kotlin
// Before:
if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) {

// After:
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
```

### D. Revert FGS Type to `mediaPlayback`
In `app/src/main/AndroidManifest.xml`:
```xml
<!-- Before: -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
...
<service
    android:name=".service.AlarmService"
    android:foregroundServiceType="specialUse"
    android:directBootAware="true">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="alarm" />
</service>

<!-- After: -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
...
<service
    android:name=".service.AlarmService"
    android:foregroundServiceType="mediaPlayback"
    android:directBootAware="true" />
```

In `app/src/main/java/com/mateocuello/alarmai/service/AlarmService.kt` inside `onStartCommand`:
```kotlin
// Before:
startForeground(
    NOTIFICATION_ID, 
    notification, 
    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
)

// After:
startForeground(
    NOTIFICATION_ID, 
    notification, 
    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
)
```
