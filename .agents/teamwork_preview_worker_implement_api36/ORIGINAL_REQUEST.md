## 2026-06-23T19:58:40Z

You are teamwork_preview_worker_1.
Your working directory is: c:\Users\usuario\alarmai\.agents\teamwork_preview_worker_implement_api36\

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Mission:
Implement the Android 16 (API 36) compatibility fixes across the AlarmAI codebase, run builds and tests to verify success, and document your changes.

Requirements to implement:

1. Manifest & Permission Cleanup (R1):
- File: app/src/main/AndroidManifest.xml
- Delete SCHEDULE_EXACT_ALARM permission declaration.
- Replace FOREGROUND_SERVICE_MEDIA_PLAYBACK permission with FOREGROUND_SERVICE_SPECIAL_USE.
- Update AlarmService service tag to use `android:foregroundServiceType="specialUse"` and add property `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="alarm" />` inside the service block.

2. AlarmService Foreground Type & Fallback Fix (R2):
- File: app/src/main/java/com/mateocuello/alarmai/service/AlarmService.kt
- Replace `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` with `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE`.
- Remove the entire fallback `startActivity(fullScreenIntent)` block within `if (!canUseFullScreen)`.

3. Direct Boot Safety in PreAlarmReceiver (R3):
- File: app/src/main/java/com/mateocuello/alarmai/receiver/PreAlarmReceiver.kt
- Import `androidx.core.os.UserManagerCompat`
- Guard the `WorkManager.getInstance(context).enqueue(...)` call with `UserManagerCompat.isUserUnlocked(context)` check. If locked, skip prefetch and log a warning.

4. On-Device Speech Recognition Fallback (R4):
- File: app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt
- Prefer `SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)` if API >= 31 (Build.VERSION_CODES.S) and on-device recognition is available (`SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)`), falling back to standard `SpeechRecognizer.createSpeechRecognizer(ctx)` otherwise.
- Make it testable by adding a companion object with an internal `sdkVersionProvider: () -> Int = { Build.VERSION.SDK_INT }` so unit tests can override/mock it.
- File: app/src/test/java/com/mateocuello/alarmai/data/repository/VoiceManagerTest.kt
- Reset `sdkVersionProvider` in `tearDown()`.
- Add unit tests verifying selection of on-device recognizer on API 31+ when available, fallback to standard when unavailable, and fallback on older APIs.

5. Redundant Runtime Check Cleanup (R5):
- File: app/src/main/java/com/mateocuello/alarmai/receiver/AlarmScheduler.kt
- Update the API level routing so that for API >= 33 (Tiramisu), we directly schedule exact alarms without checking `canScheduleExactAlarms()`, since it is auto-granted via USE_EXACT_ALARM. Keep the dynamic check only on API S (31) and S_V2 (32).
- File: app/src/main/java/com/mateocuello/alarmai/MainActivity.kt
- Restrict `checkExactAlarmPermission` check and Settings redirect to Android 12 (API 31 and 32) only.
- In `triggerTestAlarm`, skip `canScheduleExactAlarms()` check on API >= 33.

Verification:
- Compile the app using `.\gradlew compileDebugSources`.
- Run all unit tests using `.\gradlew testDebugUnitTest`. Make sure 100% of unit tests pass.

Deliverables:
- Write your completion details to c:\Users\usuario\alarmai\.agents\teamwork_preview_worker_implement_api36\handoff.md following the Handoff Protocol (Observation, Logic Chain, Caveats, Conclusion, Verification).
- Send a message to parent (conversation ID: 10462576-6182-4c65-a6e7-5fa6387890ea) when completed.
