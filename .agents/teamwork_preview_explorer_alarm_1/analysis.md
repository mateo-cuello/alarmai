# AlarmAI Android 16 (API 37) Compatibility Analysis

This report documents the investigation into the broken wake-up alarm functionality and outlines the required changes to restore full compatibility with Android 16 (API 37).

---

## 1. Executive Summary
The alarm functionality in AlarmAI broke due to three main issues:
1. **Crash on Notification Tap (Symptom 3)**: A `NoSuchMethodException` occurs because `AlarmViewModel` has a constructor with multiple arguments with default values but lacks `@JvmOverloads`. Reflection-based instantiation by `ViewModelProvider` fails to locate the expected single-argument constructor `AlarmViewModel(Application)`.
2. **Missing Full-Screen Alarm UI (Symptom 2)**: Stricter background activity launch restrictions starting in Android 14/15/16 block the background launch of `AlarmActivity` via the notification's full-screen PendingIntent because `AlarmService` did not specify that background activity starts are allowed.
3. **Broken Test Alarm Button & Pre-Alarm Scheduling (Symptom 1)**: The exact alarm scheduling calls `setExactAndAllowWhileIdle` without checking `canScheduleExactAlarms()` on Android 13+ devices, throwing a `SecurityException` if the permission is not granted. Additionally, the permission check is erroneously restricted only to Android 12 (API 31/32).
4. **Architectural FGS Type Violation**: The foreground service type was changed to `specialUse`, which violates the architectural rules defined in `DEVELOPER_CONTEXT.md` requiring `mediaPlayback`.

---

## 2. Detailed Findings & Root Cause Analysis

### Finding A: AlarmViewModel Instantiation Crash (Symptom 3)
* **File affected**: `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`
* **Observation**: In `AlarmActivity.kt`, the ViewModel is initialized using `private val viewModel: AlarmViewModel by viewModels()`. However, `AlarmViewModel`'s constructor contains multiple parameters with default values:
  ```kotlin
  class AlarmViewModel(
      application: Application,
      private val prefs: PreferencesManager = PreferencesManager(application),
      ...
  ) : AndroidViewModel(application)
  ```
  Without the `@JvmOverloads` annotation, Kotlin compiles only the full constructor (and a synthetic default constructor not usable by standard Java reflection).
* **Root Cause**: `ViewModelProvider.AndroidViewModelFactory` attempts to instantiate `AlarmViewModel` via reflection, expecting a constructor signature of exactly `AlarmViewModel(Application)`. Because this constructor does not exist in the compiled bytecode, the app crashes with a `RuntimeException` / `NoSuchMethodException` when launching `AlarmActivity` (i.e. when tapping the notification or firing the full-screen intent).

### Finding B: Blocked Full-Screen Intent / Background Activity Launch (Symptom 2)
* **File affected**: `app/src/main/java/com/mateocuello/alarmai/service/AlarmService.kt`
* **Observation**: When the alarm fires, only a notification appears, and the full-screen UI (`AlarmActivity`) is not displayed.
* **Root Cause**: Starting in Android 14 (API 34) and hardened further in Android 15 (API 35) and Android 16 (API 37), the system enforces strict restrictions on launching activities from the background using a `PendingIntent`. For the system to allow a background service to start an activity, the creator of the `PendingIntent` must explicitly grant permission. The current implementation in `AlarmService` creates the `fullScreenPendingIntent` without providing any `ActivityOptions` specifying `setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)`. As a result, the OS blocks the full-screen intent activity launch.

### Finding C: Unsafe Exact Alarm Scheduling (Symptom 1)
* **Files affected**: `app/src/main/java/com/mateocuello/alarmai/MainActivity.kt` and `app/src/main/java/com/mateocuello/alarmai/receiver/AlarmScheduler.kt`
* **Observation**: The "Test Alarm" button fails and shows a failure toast, and the pre-alarm fails to schedule safely.
* **Root Cause**: 
  1. In `MainActivity.kt` (`triggerTestAlarm`) and `AlarmScheduler.kt` (`schedule`), the code was modified to bypass the `canScheduleExactAlarms()` check on Android 13+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU`) and call `setExactAndAllowWhileIdle()` directly.
  2. In Android 14+ (API 34+), the `SCHEDULE_EXACT_ALARM` permission is denied by default for apps targeting API 34+. Even if `USE_EXACT_ALARM` is declared, the permission might be manually revoked by the user, or not granted on certain testing environments. Calling `setExactAndAllowWhileIdle()` without checking `canScheduleExactAlarms()` throws a `SecurityException`.
  3. The runtime permission check `checkExactAlarmPermission` in `MainActivity.kt` was modified to only run on Android 12 (`Build.VERSION.SDK_INT == Build.VERSION_CODES.S || Build.VERSION.SDK_INT == Build.VERSION_CODES.S_V2`), meaning it fails to prompt or warn users on Android 13, 14, 15, or 16 devices if they do not have the permission.

### Finding D: Architectural FGS Type Violation
* **Files affected**: `app/src/main/AndroidManifest.xml` and `app/src/main/java/com/mateocuello/alarmai/service/AlarmService.kt`
* **Observation**: The foreground service type for `AlarmService` was changed to `specialUse`, and the manifest permission was updated to `FOREGROUND_SERVICE_SPECIAL_USE`.
* **Root Cause**: This violates Section 1 of `DEVELOPER_CONTEXT.md`, which states:
  > * **AlarmService**: A Foreground Service (mediaPlayback type) managing alarm ringtone playback via MediaPlayer. It routes audio through the alarm stream.
  
  Since the service plays audio via MediaPlayer, `mediaPlayback` is the correct, standard, and architecturally mandated foreground service type.

---

## 3. Recommended Fixes

To achieve full compatibility with Android 16 (API 37) while complying with all guidelines in `DEVELOPER_CONTEXT.md`, the following changes must be implemented:

1. **Annotate `AlarmViewModel` Constructor with `@JvmOverloads`**:
   Add `@JvmOverloads` to `AlarmViewModel` so Kotlin generates the single-argument constructor expected by `ViewModelProvider`.
   
2. **Grant Background Activity Start Permission to `fullScreenPendingIntent`**:
   In `AlarmService.kt`, pass `ActivityOptions` configured with `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` when creating the `PendingIntent` for `AlarmActivity`.

3. **Restore `mediaPlayback` Foreground Service Type**:
   - Update `AndroidManifest.xml` to request `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission and change `AlarmService` type to `mediaPlayback`.
   - Update `AlarmService.kt` to use `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` when calling `startForeground`.

4. **Harden Exact Alarm Scheduling**:
   - Check `canScheduleExactAlarms()` for all SDK >= 31 (Android 12+) before calling `setExactAndAllowWhileIdle()` in both `MainActivity.kt` and `AlarmScheduler.kt`. Fall back to `setAndAllowWhileIdle()` if the permission is not granted.
   - Update `checkExactAlarmPermission` in `MainActivity.kt` to run on all SDK >= 31.
