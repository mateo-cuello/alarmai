# Original User Request

## Initial Request — 2026-06-23T02:17:58Z

Rebuild the World Cup match fixture functionality to query the official FIFA World Cup API dynamically instead of using local JSON files.

Working directory: c:\Users\usuario\alarmai
Integrity mode: development

## Requirements

### R1. Official FIFA API Endpoint Discovery
Find the official API or public-facing endpoint used by the official FIFA World Cup website to retrieve matches, dates, times, and teams for the 2026 World Cup (or general match queries). Perform HTTP GET tests to verify it returns a valid JSON response containing match data.

### R2. Rebuild WorldCupRepository
Modify `WorldCupRepository` to fetch match data from the discovered FIFA API dynamically using OkHttp (already available in the project dependencies). Parse the JSON response and map it to `WorldCupMatch` structures.
Remove the dependency on the local asset file `worldcup_2026.json` for match retrieval. Maintain fallback behavior or elegant error messages if network is unavailable.
Ensure the original function signatures and behaviors remain consistent:
- `getMatchesForDate(context: Context, dateString: String): List<WorldCupMatch>`
- `getTodayMatchesSummary(context: Context, dateString: String): String`
- `getMatchesByTeam(context: Context, teamName: String): String`

### R3. Verification and Integration Tests
Verify that all unit tests in `WorldCupRepositoryTest.kt` and `GeminiAgentManagerTest.kt` pass successfully. Update or mock files where necessary to accommodate the transition from the local JSON asset to network-based calls.

## Acceptance Criteria

### API Retrieval and Integration
- [ ] Successfully find and document a working, official FIFA API URL that returns 2026 World Cup matches.
- [ ] `WorldCupRepository` successfully queries this URL dynamically without using local assets for fixture data.
- [ ] Matches are parsed correctly into the application's `WorldCupMatch` class structure.

### Code Quality and Functionality
- [ ] The codebase compiles successfully without warnings or errors.
- [ ] Unit tests pass: `./gradlew test` (or specific task to run unit tests) succeeds.
- [ ] The Gemini agent tool invocation for `getWorldCupMatchesForDate` returns a correct and structured matches summary from the live API.

## Follow-up — 2026-06-23T02:51:29Z

Fix and harden the voice-to-text (STT) and text-to-speech (TTS) conversation loop in the AlarmAI Android app so users can interact with the AI assistant entirely hands-free after tapping "Dismiss & Talk". The user should be able to have a full multi-turn voice conversation without touching the screen. The only required touch is the initial "Dismiss & Talk" button.

Working directory: C:\Users\usuario\alarmai
Integrity mode: development

## Requirements

### R1. Reliable Speech-to-Text Recognition Loop

The `VoiceManager.startListening()` and `AlarmViewModel.startListeningForUser()` must work correctly through the full conversation lifecycle. Known critical issues to fix:

- `SystemClock.sleep(50)` on the main thread blocks the UI — replace with non-blocking delay
- `stopListening()` does not call `unmuteBeep()`, leaving audio streams permanently muted if called externally
- `stopSpeaking()` nullifies `ttsCompleteCallback` before TTS stops, creating a race condition
- No `SpeechRecognizer.isRecognitionAvailable()` check before attempting recognition
- After TTS completes, audio focus is abandoned then re-acquired 600ms later, creating a window for focus loss
- STT retry logic (max 5 errors with backoff) should gracefully recover and never leave the app stuck in an unresponsive state
- The `ERROR` state exists in `AlarmState` but is never used — it should be set when appropriate and the UI should handle it

### R2. Robust Voice Conversation State Machine

The `AlarmViewModel` state machine (RINGING → FETCHING_DATA → SPEAKING ↔ LISTENING ↔ THINKING → FINISHED) must handle all edge cases cleanly:

- Transitions between SPEAKING and LISTENING must be seamless — no dropped audio, no stuck states
- Error recovery should display user-friendly feedback and allow the user to continue the conversation
- The no-speech timeout (currently 2 min) should work reliably and re-engage the user
- Force close and goodbye detection must clean up all resources (TTS, STT, audio focus, muted streams)

### R3. Comprehensive Unit Tests for Voice Components

Create unit tests that verify the voice interaction logic works correctly. Tests should cover:

- `VoiceManager`: TTS initialization, speak/callback flow, startListening/result/error callbacks, mute/unmute lifecycle, audio focus management, shutdown cleanup
- `AlarmViewModel`: State machine transitions for the complete conversation flow, STT error retry logic with backoff, goodbye keyword detection (both English and Spanish), no-speech timeout behavior, manual mic restart
- All new tests must pass via `./gradlew testDebugUnitTest`

### R4. Safe Audio Stream Management

The aggressive muting of `STREAM_MUSIC`, `STREAM_SYSTEM`, and `STREAM_NOTIFICATION` must be made safe:

- Ensure `unmuteBeep()` is always called in all exit paths (normal completion, error, force close, app crash protection)
- Consider using `AudioManager.STREAM_VOICE_CALL` or an alternative approach that doesn't globally mute media streams
- Add a safety mechanism (e.g., `onCleared()` or `shutdown()` guarantee) so streams are never left muted

## Acceptance Criteria

### Voice Conversation Flow
- [ ] After "Dismiss & Talk", the full loop (FETCHING_DATA → SPEAKING → LISTENING → THINKING → SPEAKING → ...) completes without errors or stuck states
- [ ] The `ERROR` state is set when real errors occur and the UI displays a recovery option
- [ ] `stopListening()` properly unmutes audio streams in all code paths
- [ ] `stopSpeaking()` does not have a callback race condition
- [ ] No `SystemClock.sleep()` calls on the main thread
- [ ] `SpeechRecognizer.isRecognitionAvailable()` is checked before creating a recognizer
- [ ] Audio focus is maintained continuously during the SPEAKING → LISTENING transition (no gap)

### State Machine Robustness
- [ ] STT errors retry up to 5 times with increasing backoff, then transition to ERROR state with recovery UI
- [ ] Goodbye keywords work in both English and Spanish
- [ ] No-speech timeout correctly re-engages or finishes the session
- [ ] `forceClose()` and `onCleared()` guarantee all audio resources are released and streams unmuted

### Test Coverage
- [ ] At least 10 unit tests for `VoiceManager` covering speak, listen, error, mute/unmute, and shutdown
- [ ] At least 10 unit tests for `AlarmViewModel` covering all state transitions, retry logic, goodbye detection, and timeout
- [ ] All tests pass: `./gradlew testDebugUnitTest` exits with BUILD SUCCESSFUL
- [ ] Tests use Mockito to mock Android framework classes (`SpeechRecognizer`, `TextToSpeech`, `AudioManager`)

### Audio Safety
- [ ] `unmuteBeep()` is called in `stopListening()`, `forceClose()`, and `onCleared()`
- [ ] The `shutdown()` method is guaranteed to restore audio stream state
- [ ] No path exists where audio streams remain muted after the alarm conversation ends

### Build Verification
- [ ] The app compiles successfully: `./gradlew assembleDebug` exits with BUILD SUCCESSFUL
- [ ] All existing tests continue to pass alongside new tests
