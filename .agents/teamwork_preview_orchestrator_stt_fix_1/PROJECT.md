# Project: Speech-to-Text Instant Failure Fix

## Architecture
- **AlarmViewModel**: State machine for the alarm lifecycle (RINGING -> FETCHING_DATA -> SPEAKING <-> LISTENING <-> THINKING -> FINISHED). Controls transitions and callbacks.
- **VoiceManager**: Handles Text-to-Speech (TTS) and Speech-to-Text (STT) setup and audio streaming (muting, unmuting). Uses Android's `SpeechRecognizer` and `TextToSpeech` APIs.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Diagnosis | Explorer analyzes the instant failure of Speech-to-Text when using SpeechRecognizer on Android 14. | none | DONE |
| 2 | Implementation | Worker implements the fix in VoiceManager / AlarmViewModel to ensure STT works reliably. | M1 | IN_PROGRESS |
| 3 | Review & Challenge | Reviewers review the fix and Challengers verify the functionality using unit tests and/or script checks. | M2 | PLANNED |
| 4 | Forensic Audit | Forensic Auditor validates the integrity of the solution. | M3 | PLANNED |

## Interface Contracts
- `VoiceManager.startListening()` and related callbacks must communicate voice inputs/errors to `AlarmViewModel`.
- `unmuteBeep()` must be called to restore system/media streams upon termination or error.
