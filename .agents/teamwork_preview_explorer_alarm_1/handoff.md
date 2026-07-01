# Handoff Report — Alarm Compatibility Fixes for Android 16 (API 37)

This report details the observations, logic chain, and proposed fixes to address the broken alarm functionality in AlarmAI and ensure full compatibility with Android 16 (API 37).

---

## 1. Observation

During the read-only investigation, the following files and code locations were analyzed:

1. **`app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`**:
   - The primary constructor of `AlarmViewModel` has default arguments for all parameters after `application: Application` (lines 40-49):
     ```kotlin
     class AlarmViewModel(
         application: Application,
         private val prefs: PreferencesManager = PreferencesManager(application),
         ...
     ) : AndroidViewModel(application)
     ```
     No `@JvmOverloads` annotation is present.
   - In unit tests (`AlarmViewModelTest.kt`), the ViewModel is instantiated by passing all constructor arguments manually (lines 99-108). In the actual app (`AlarmActivity.kt`), it is instantiated via `private val viewModel: AlarmViewModel by viewModels()`.

2. **`app/src/main/java/com/mateocuello/alarmai/service/AlarmService.kt`**:
   - The full-screen pending intent is created at lines 43-48 without passing any `ActivityOptions` bundle:
     ```kotlin
     val fullScreenPendingIntent = PendingIntent.getActivity(
         this,
         0,
         fullScreenIntent,
         PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
     )
     ```
   - In `onStartCommand` (lines 74-82), `startForeground` is called with FGS type `specialUse`:
     ```kotlin
     startForeground(
         NOTIFICATION_ID, 
         notification, 
         ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
     )
     ```

3. **`app/src/main/AndroidManifest.xml`**:
   - At line 22, it declares:
     ```xml
     <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
     ```
     At lines 91-93, it declares:
     ```xml
     android:foregroundServiceType="specialUse"
     ...
     <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="alarm" />
     ```

4. **`app/src/main/java/com/mateocuello/alarmai/MainActivity.kt`**:
   - At lines 747-753, it schedules the test alarm using exact alarm API directly for Android 13+:
     ```kotlin
     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
         alarmManager.setExactAndAllowWhileIdle(
             AlarmManager.RTC_WAKEUP,
             triggerTime,
             pendingIntent
         )
     }
     ```
   - At line 780, the exact alarm permission request is constrained only to Android 12 (S/S_V2):
     ```kotlin
     if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2) {
     ```

5. **`app/src/main/java/com/mateocuello/alarmai/receiver/AlarmScheduler.kt`**:
   - At lines 69-74, it schedules the pre-alarm using exact alarm API directly for Android 13+:
     ```kotlin
     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
         alarmManager.setExactAndAllowWhileIdle(
             AlarmManager.RTC_WAKEUP,
             preAlarmTime,
             preAlarmPendingIntent
         )
     }
     ```

6. **`DEVELOPER_CONTEXT.md`**:
   - Under Section 1 ("System Architecture"), it specifies:
     > `AlarmService`: A Foreground Service (mediaPlayback type) managing alarm ringtone playback via MediaPlayer.

---

## 2. Logic Chain

1. **Symptom 3 (Crash on notification tap)**:
   - When the user taps the notification, the system launches `AlarmActivity` via `fullScreenPendingIntent`.
   - `AlarmActivity` delegates ViewModel creation to the default `ViewModelProvider.AndroidViewModelFactory` via `by viewModels()`.
   - Because `AlarmViewModel` has default arguments but lacks `@JvmOverloads`, Kotlin compiles only a multi-parameter constructor. The single-parameter constructor `AlarmViewModel(Application)` is absent in the bytecode.
   - Thus, the default factory's reflection-based lookup fails to find the expected constructor, throwing `NoSuchMethodException` and causing the crash.
   - *Conclusion*: Annotating `AlarmViewModel` constructor with `@JvmOverloads` resolves the crash.

2. **Symptom 2 (Full-screen alarm UI does not show)**:
   - On Android 14+ and especially Android 15/16, the OS blocks launching activities from the background via a `PendingIntent` by default.
   - For a background service or broadcast receiver to trigger an activity launch from a `PendingIntent`, the creator of the `PendingIntent` must pass `ActivityOptions` configured with `setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)`.
   - Because `AlarmService` does not provide these options for `fullScreenPendingIntent`, the OS silently blocks the background launch of `AlarmActivity`.
   - *Conclusion*: Supplying the background activity launch mode option to the `PendingIntent` restores the full-screen overlay behavior.

3. **Symptom 1 (Test Alarm button doesn't work)**:
   - Android 14+ denies the `SCHEDULE_EXACT_ALARM` permission by default for apps targeting API 34+.
   - Even when declaring `USE_EXACT_ALARM`, users can revoke it manually, or the permission might not be granted in test environments.
   - Bypassing the check for Android 13+ and directly calling `setExactAndAllowWhileIdle` triggers a `SecurityException` if the permission is not granted.
   - In addition, restricting the permission request screen redirection to Android 12 prevents users on Android 13+ from ever being prompted to enable it.
   - *Conclusion*: Wrapping the exact alarm schedule call in `canScheduleExactAlarms()` for all SDK >= 31, falling back to `setAndAllowWhileIdle()`, and extending the settings redirection check to SDK >= 31 fixes the crash and guides the user appropriately.

4. **Architectural FGS Type Violation**:
   - `DEVELOPER_CONTEXT.md` explicitly designates `AlarmService` as a `mediaPlayback` type foreground service.
   - Changing it to `specialUse` violates this guideline, requires complex justifications under Google Play rules, and requires the `specialUse` permission. Returning to `mediaPlayback` complies with project standards and standard Android behavior.

---

## 3. Caveats

- We assumed that `USE_EXACT_ALARM` is intended to be the main exact alarm permission mechanism on Google Play (as recommended for alarm apps). If the app is distributed outside Play Store or has other policy limits, using the `SCHEDULE_EXACT_ALARM` fallback is necessary.
- Testing on Android 16 (API 37) is based on standard developer preview documentation regarding background activity starts and exact alarms. Actual behavior on final hardware depends on target SDK constraints.

---

## 4. Conclusion

To make the alarm functionality fully compatible with Android 16 (API 37), the project must apply the changes contained in `alarm_compatibility_fix.patch` in this agent folder. This patch will:
1. Generate JVM constructor overloads for `AlarmViewModel`.
2. Allow background activity launches for `fullScreenPendingIntent` using `ActivityOptions`.
3. Restore the `mediaPlayback` foreground service type and permissions per architectural guidelines.
4. Safeguard all exact alarm scheduling calls with `canScheduleExactAlarms()` checks and settings page redirection for all Android 12+ devices.

---

## 5. Verification Method

1. **Apply the patch**:
   Run the following git command to apply the changes locally:
   ```bash
   git apply .agents/teamwork_preview_explorer_alarm_1/alarm_compatibility_fix.patch
   ```
2. **Run the build and unit tests**:
   Verify that the code compiles successfully and that all unit tests pass:
   ```bash
   ./gradlew test
   ```
3. **Verify manually on emulator (Android 14/15/16)**:
   - Launch the application and click the **Test Alarm** button. Verify it schedules and fires successfully (without throwing `SecurityException` or displaying a failure toast).
   - Lock the device before the alarm triggers. When it fires, verify the full-screen `AlarmActivity` overlay appears.
   - If the device is unlocked, verify tapping the notification launches `AlarmActivity` without crashing the application.
