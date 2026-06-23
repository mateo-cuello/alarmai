# Project: Android 16 (API 36) Compatibility Fixes

## Architecture
AlarmAI is a Compose-based Android application. The changes modify service configurations, broadcast receivers, exact alarm permission flows, and speech recognition setup to align with Android 16 behavior and policies.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| 1 | Manifest & Service Updates (R1 + R2) | Remove SCHEDULE_EXACT_ALARM, add FOREGROUND_SERVICE_SPECIAL_USE. Update AlarmService to use FOREGROUND_SERVICE_TYPE_SPECIAL_USE, use alarm subtype property. Remove fallback startActivity. | None | IN_PROGRESS (Conv: 9e230e5c-0794-473b-81be-6742cf4f73ca) |
| 2 | Receiver & Runtime Logic (R3 + R5) | Guard PreAlarmReceiver with UserManagerCompat.isUserUnlocked. Remove/simplify canScheduleExactAlarms checks and redirects in AlarmScheduler and MainActivity. | None | IN_PROGRESS (Conv: 9e230e5c-0794-473b-81be-6742cf4f73ca) |
| 3 | On-Device Speech Recognition (R4) | Prefer SpeechRecognizer.createOnDeviceSpeechRecognizer in VoiceManager on API 31+. | None | IN_PROGRESS (Conv: 9e230e5c-0794-473b-81be-6742cf4f73ca) |
| 4 | Verification & Audit | Run compileDebugSources, unit tests, and the Forensic Auditor to verify integrity and correctness. | M1, M2, M3 | PLANNED |

## Code Layout
- AndroidManifest: `app/src/main/AndroidManifest.xml`
- AlarmService: `app/src/main/java/com/mateocuello/alarmai/service/AlarmService.kt`
- PreAlarmReceiver: `app/src/main/java/com/mateocuello/alarmai/receiver/PreAlarmReceiver.kt`
- VoiceManager: `app/src/main/java/com/mateocuello/alarmai/data/repository/VoiceManager.kt`
- AlarmScheduler: `app/src/main/java/com/mateocuello/alarmai/receiver/AlarmScheduler.kt`
- MainActivity: `app/src/main/java/com/mateocuello/alarmai/MainActivity.kt`
