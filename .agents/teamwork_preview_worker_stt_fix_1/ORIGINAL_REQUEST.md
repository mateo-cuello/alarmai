## 2026-07-01T16:08:35Z
You are a teamwork_preview_worker.
Your working directory is: c:\Users\usuario\alarmai\.agents\teamwork_preview_worker_stt_fix_1
Your task is to implement the fixes for the instant failure of Speech-to-Text (STT) when using SpeechRecognizer on Android 14 in AlarmAI.
Refer to the findings in:
- c:\Users\usuario\alarmai\.agents\teamwork_preview_explorer_stt_fix_1\analysis.md
- c:\Users\usuario\alarmai\.agents\teamwork_preview_explorer_stt_fix_1\handoff.md

Specifically, you need to:
1. Modify app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt to:
   - Implement cleanupSpeechRecognizer() to cancel and destroy any existing instance of SpeechRecognizer, setting it to null.
   - Call cleanupSpeechRecognizer() before creating a new SpeechRecognizer in startListening().
   - Update speechRecognizerFactory to prefer SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx) if API >= 33 and SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) is true. Otherwise, fall back to SpeechRecognizer.createSpeechRecognizer(ctx).
   - Remove the invalid/type-mismatched extra EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE from the recognition intent.
   - Ensure that unmuteBeep() is properly called during cleanup or on error.
2. Check and fix any compilation errors in the unit test files:
   - Identify why WorldCupRepository references are failing (perhaps it was removed or renamed, so references in tests should be mocked, stubbed, or removed if the feature is no longer present).
   - Check app/src/test/java/com/mateocuello/alarmai/data/repository/NewsRepositoryTest.kt for fetchRssHeadlines unresolved reference.
3. Run ./gradlew compileDebugSources and ./gradlew testDebugUnitTest to verify that the build compiles and all unit tests pass.
4. Report your changes, compilation results, and test outputs in changes.md and handoff.md.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Send a message to the orchestrator (conversation ID: c2c2dc86-6750-4453-ad81-f4f8bd4e33b7) when complete.
