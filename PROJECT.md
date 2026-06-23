# Project: Voice Loop Hardening

## Architecture
AlarmAI is a Compose-based Android application. The voice-to-text (STT) and text-to-speech (TTS) conversational loop is managed by `VoiceManager` and coordinate-driven state transitions inside `AlarmViewModel` which drives the UI in `AlarmActivity`.

```
+--------------------+       +--------------+       +---------------+
|    AlarmActivity   | <---> |AlarmViewModel| <---> |  VoiceManager |
| (Compose Chat UI)  |       | (Coordinator)|       | (TTS/STT/Focus|
+--------------------+       +--------------+       +---------------+
```

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| 1 | Exploration & Analysis | Identify failure points, deadlocks, focus gaps, and audio leak conditions in the conversation loop. | None | DONE |
| 2 | Implementation of Loop Fixes | Address UI blocking sleep, audio focus gaps, stream muting leaks, callback race conditions, unhandled ERROR UI state, and add error safety. | M1 | DONE |
| 3 | Unit Testing Implementation | Implement unit tests for key voice loop state machine transitions and retry scenarios. | M2 | IN_PROGRESS |
| 4 | Verification & Audit | Validate build, run checks, audit thread safety, and conduct full integration and regression tests. | M3 | PLANNED |

## Interface Contracts
### `VoiceManager` Public Interface
- `startSession()`
- `endSession()`
- `speak(text: String, onComplete: () -> Unit)`
- `startListening(onResult: (String) -> Unit, onError: (String) -> Unit, onRmsChanged: (Float) -> Unit)`
- `stopSpeaking()`
- `stopListening()`
- `shutdown()`

### `AlarmViewModel` Public Interface
- `dismissAndTalk()`
- `processUserSpeech(text: String)`
- `retry()`
- `forceClose()`

## Code Layout
- Voice Manager: `app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt`
- ViewModel: `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`
- Activity: `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmActivity.kt`
