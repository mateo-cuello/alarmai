# Analysis: Alarm Functionality Issues and Android 16 (API 37) Compatibility

This report documents the root causes of the broken alarm functionality in the AlarmAI application and provides a specific fix strategy for full compatibility with Android 16 (API 37).

---

## 1. Git History Investigation
An analysis of the Git history between the last working commit `ed2f138063533d635ec7db66f2a4a1c8f5bf62e2` and the current state (`HEAD`) reveals several significant changes targeting permissions, receiver declarations, and background services:

### A. AndroidManifest.xml Modifications
- **Removed Permission**:
  ```xml
  - <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" android:maxSdkVersion="32" />
  ```
  The app now only declares `USE_EXACT_ALARM`.
- **FGS Permissions & Type Change**:
  ```xml
  - <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
  + <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
  ...
  - android:foregroundServiceType="mediaPlayback" />
  + android:foregroundServiceType="specialUse"
  + android:directBootAware="true">
  + <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="alarm" />
  ```
  The foreground service type was changed from `mediaPlayback` to `specialUse`, and `directBootAware="true"` was added.

### B. AlarmScheduler.kt Changes
- The main alarm scheduling logic was updated to use `setAlarmClock` instead of `setExactAndAllowWhileIdle` when scheduling alarms:
  ```kotlin
  val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, showPendingIntent)
  alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
  ```
- The pre-alarm scheduling logic (via `setExactAndAllowWhileIdle`) was bypassed for API >= 33 (Tiramisu) to run without checking `canScheduleExactAlarms()`:
  ```kotlin
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          preAlarmTime,
          preAlarmPendingIntent
      )
  }
  ```

### C. MainActivity.kt Changes
- The check for exact alarm permissions was restricted to API 31 & 32 (Android 12/12L):
  ```kotlin
  - if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
  + if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) {
  ```
- The test alarm trigger logic was bypassed for API >= 33 to call `setExactAndAllowWhileIdle` directly without checking `canScheduleExactAlarms()` first.

### D. AlarmViewModel.kt Changes
- The constructor was refactored to take default arguments for dependency injection:
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
  **Note**: This constructor does *not* have the `@JvmOverloads` annotation.

---

## 2. Symptom Analysis & Root Causes

### Symptom 1: "Test Alarm" Button Doesn't Work
- **Observation**: Tapping the "Test Alarm" button does not schedule any alarm and no notification appears.
- **Root Cause**:
  In `MainActivity.kt`, the `triggerTestAlarm` function calls `alarmManager.setExactAndAllowWhileIdle` directly on Android 13+ (API 33+) without checking if exact alarm permission is granted.
  Because the app targets Android 16 (API 37) and only declares `USE_EXACT_ALARM` in the manifest, the `SCHEDULE_EXACT_ALARM` permission is denied by default on Android 14+ devices.
  Consequently, `alarmManager.setExactAndAllowWhileIdle` throws a `SecurityException`. The exception is caught and logged in the try-catch block, resulting in a silent failure (the alarm is never scheduled).

### Symptom 2: When the Alarm Fires, Full-Screen UI Doesn't Show (Only Notification Appears)
- **Observation**: Only a standard heads-up notification appears when the alarm is triggered, instead of launching `AlarmActivity` over the lock screen.
- **Root Cause**:
  1. **Permission Check Exclusion**: In `AlarmService.kt`, the notification builder sets the full-screen intent only if `canUseFullScreen` is true:
     ```kotlin
     val canUseFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
         notificationManager.canUseFullScreenIntent()
     } else {
         true
     }
     ```
     On Android 14+ (UPSIDE_DOWN_CAKE), `canUseFullScreenIntent()` checks if the app is allowed to post full-screen intents. If the app is sideloaded/under development, or if the user has disabled the permission, this returns `false`.
  2. **Missing Background Activity Launch Privilege**: Starting in Android 14, apps launching activities from the background using a `PendingIntent` must explicitly opt-in to allow background activity starts (BAL) by setting `ActivityOptions.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)`. If this option is omitted, the system blocks the background activity launch from the `PendingIntent`, meaning the full-screen activity is suppressed and only the notification is shown.

### Symptom 3: Tapping the Notification Crashes with "AlarmAI has stopped working"
- **Observation**: Tapping the posted notification immediately crashes the app.
- **Root Cause**:
  Tapping the notification fires the `contentIntent` targeting `AlarmActivity`.
  In `AlarmActivity.kt`, the ViewModel is instantiated via the Kotlin extension `by viewModels()`:
  ```kotlin
  private val viewModel: AlarmViewModel by viewModels()
  ```
  The Android framework's default `ViewModelProvider.AndroidViewModelFactory` uses Java reflection to instantiate `AlarmViewModel`. It looks for a constructor taking *exactly* `Application` as the only parameter.
  Because `AlarmViewModel` has a multi-parameter constructor with default values but **lacks the `@JvmOverloads` annotation**, Kotlin only compiled a single constructor taking all 8 parameters into the Java bytecode.
  The Java runtime cannot locate a constructor taking only `Application`, resulting in a `NoSuchMethodException` and causing a crash when `AlarmActivity` attempts to start.

---

## 3. Android 16 (API 37) Requirements

To ensure full compatibility with Android 16, the following requirements must be addressed:
1. **Exact Alarm Scheduling (API 31+)**:
   - Apps must check `canScheduleExactAlarms()` before calling `setExactAndAllowWhileIdle`.
   - If denied, the app should fall back to `setAndAllowWhileIdle` (which schedules an inexact alarm but does not crash or fail) and prompt the user to enable the permission.
2. **Background Activity Launch (BAL) for PendingIntents (API 34+)**:
   - When configuring `PendingIntent` instances for background launches (like full-screen intents), developers must attach `ActivityOptions` configured with `.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)`.
3. **Foreground Service (FGS) Type Declaration (API 34+)**:
   - The app currently uses `specialUse` for `AlarmService`. However, Android guidelines require that if a service plays background audio (such as the alarm ringtone via `MediaPlayer`), it must use the `mediaPlayback` foreground service type. Declaring `mediaPlayback` avoids Play Store rejection and complies with official Android FGS guidelines.
4. **Full-Screen Intent Check & Settings fallback (API 34+)**:
   - If `NotificationManager.canUseFullScreenIntent()` returns false, the app should guide the user to the "Show on lock screen" or "Use full screen intents" settings page using `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`.

---

## 4. Specific Fix Strategy

To make the alarm functionality fully compatible with Android 16, the following changes are required:

### 1. Add `@JvmOverloads` to `AlarmViewModel` Constructor
Add `@JvmOverloads` to `AlarmViewModel`'s constructor so Kotlin generates the required single-argument constructor for `ViewModelProvider.AndroidViewModelFactory`.
- **Target File**: `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`
- **Change**:
  ```kotlin
  class AlarmViewModel @JvmOverloads constructor(
      application: Application,
      private val prefs: PreferencesManager = PreferencesManager(application),
      ...
  ```

### 2. Configure Background Activity Launch Mode for PendingIntents
Attach `ActivityOptions` with background activity launch privileges to the `PendingIntent` used for the full-screen intent in `AlarmService.kt` and `AlarmScheduler.kt`.
- **Target File**: `app/src/main/java/com/mateocuello/alarmai/service/AlarmService.kt`
- **Change**:
  ```kotlin
  val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
  }
  val options = ActivityOptions.makeBasic()
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
  }
  val fullScreenPendingIntent = PendingIntent.getActivity(
      this,
      0,
      fullScreenIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      options.toBundle()
  )
  ```
- **Target File**: `app/src/main/java/com/mateocuello/alarmai/receiver/AlarmScheduler.kt`
  Apply the same `ActivityOptions` to the `showPendingIntent` inside `schedule`.

### 3. Implement Exact Alarm Permission Checking and Fallback
Update the exact alarm permission checking in `MainActivity.kt` and fallback in scheduling logic to prevent `SecurityException`.
- **Target File**: `app/src/main/java/com/mateocuello/alarmai/MainActivity.kt`
  - Change `checkExactAlarmPermission` check to `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`.
  - In `triggerTestAlarm`, check `canScheduleExactAlarms()` before calling `setExactAndAllowWhileIdle`. If false, fall back to `setAndAllowWhileIdle`.
- **Target File**: `app/src/main/java/com/mateocuello/alarmai/receiver/AlarmScheduler.kt`
  - In `schedule` (for the pre-alarm), check `canScheduleExactAlarms()` on Android 12+ (API 31+). If false, fall back to `setAndAllowWhileIdle`.

### 4. Revert Foreground Service Type to `mediaPlayback`
Revert `AlarmService` foreground service type from `specialUse` to `mediaPlayback` to comply with standard Android guidelines for playing audio in the background and avoid Google Play Store rejection.
- **Target File**: `app/src/main/AndroidManifest.xml`
  - Change `uses-permission` from `FOREGROUND_SERVICE_SPECIAL_USE` back to `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
  - Change `foregroundServiceType` on `.service.AlarmService` from `specialUse` back to `mediaPlayback`.
  - Remove the property tag `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" ... />` since it is not needed for `mediaPlayback`.
- **Target File**: `app/src/main/java/com/mateocuello/alarmai/service/AlarmService.kt`
  - Change the `startForeground` service type parameter from `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` to `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`.
