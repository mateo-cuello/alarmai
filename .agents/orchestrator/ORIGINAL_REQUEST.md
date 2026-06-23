# Original User Request

## Follow-up — 2026-06-23T19:55:26Z

Apply Android 16 (API 36) compatibility fixes to the AlarmAI Android app. The fixes address Google Play policy violations, foreground service type misuse, background activity launch restrictions, Direct Boot safety, and speech recognizer optimization.

Working directory: c:\Users\usuario\alarmai
Integrity mode: development

## Requirements

### R1. Manifest & Permission Cleanup
Remove the redundant `SCHEDULE_EXACT_ALARM` permission (keep only `USE_EXACT_ALARM`). Replace `FOREGROUND_SERVICE_MEDIA_PLAYBACK` with `FOREGROUND_SERVICE_SPECIAL_USE`. Change `AlarmService` foreground service type from `mediaPlayback` to `specialUse` and add the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property with value `alarm`.

### R2. AlarmService Foreground Type & Fallback Fix
Update `AlarmService.kt` to use `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` instead of `MEDIA_PLAYBACK`. Remove the invalid `startActivity()` fallback when full-screen intent permission is denied — the system's high-priority heads-up notification is sufficient.

### R3. Direct Boot Safety in PreAlarmReceiver
Guard the `WorkManager.getInstance(context).enqueue(...)` call in `PreAlarmReceiver` with a `UserManagerCompat.isUserUnlocked(context)` check. If the device is locked during Direct Boot, skip the prefetch and log a warning.

### R4. On-Device Speech Recognition Fallback
Update the `speechRecognizerFactory` in `VoiceManager.kt` to prefer `SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)` when available (API 31+), falling back to the standard network-based recognizer otherwise.

### R5. Redundant Runtime Check Cleanup (AlarmScheduler + MainActivity)
Remove or simplify the `canScheduleExactAlarms()` runtime check and settings redirect in both `AlarmScheduler.kt` and `MainActivity.kt`, since `USE_EXACT_ALARM` is auto-granted for alarm apps and does not require user action. The settings redirect UI flow for exact alarm permissions is unnecessary and confusing.

## Acceptance Criteria

### Build Integrity
- [ ] `.\gradlew compileDebugSources` completes with zero errors
- [ ] `.\gradlew testDebugUnitTest` passes all existing tests (39+) with zero failures

### Manifest Correctness
- [ ] `SCHEDULE_EXACT_ALARM` does NOT appear in AndroidManifest.xml
- [ ] `FOREGROUND_SERVICE_MEDIA_PLAYBACK` does NOT appear in AndroidManifest.xml
- [ ] `FOREGROUND_SERVICE_SPECIAL_USE` permission IS declared
- [ ] AlarmService declares `foregroundServiceType="specialUse"` with the alarm subtype property

### Code Correctness
- [ ] `AlarmService.kt` uses `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` constant
- [ ] `AlarmService.kt` does NOT call `startActivity()` as a fallback for denied full-screen intent
- [ ] `PreAlarmReceiver` checks `UserManagerCompat.isUserUnlocked()` before calling `WorkManager`
- [ ] `VoiceManager.kt` prefers on-device speech recognizer when available (API 31+)
- [ ] `AlarmScheduler.kt` and `MainActivity.kt` do NOT contain redundant `canScheduleExactAlarms()` checks or settings redirects for exact alarm permissions
- [ ] No regressions in existing functionality

### Security
- [ ] No API keys or sensitive credentials present in source code
