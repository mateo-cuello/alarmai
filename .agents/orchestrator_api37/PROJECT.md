# Project: API 37 Migration and Alarm Fixes

## Architecture
AlarmAI is a Compose-based Android alarm application. The build uses Gradle Version Catalog (`gradle/libs.versions.toml`) and Android Gradle Plugin (AGP) 8.13.2.
Upgrading compileSdk and targetSdk to 37 and Kotlin to 2.1+ requires replacing Compose Compiler Extension with the Compose Compiler Gradle Plugin.
The alarm chain: AlarmScheduler -> AlarmReceiver -> AlarmService -> AlarmActivity.
On Android 16 (API 37), exact alarm scheduling, foreground services, full-screen intents, and direct boot requires compatible permission usage and foreground types.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| 1 | Toolchain Upgrade | Upgrade targetSdk/compileSdk to 37, Kotlin to 2.1+, verify Gradle wrapper compatibility with AGP 8.13.2, apply Compose Compiler Gradle Plugin, resolve build dependency issues. | None | DONE |
| 2 | Alarm Functionality & API 37 Compatibility | Fix Test Alarm button, ensure AlarmActivity displays over lock screen, ensure notification tap launches AlarmActivity without crash, ensure AlarmService foreground type/permissions are correct, test on Android 16. | M1 | DONE |
| 3 | Environment Setup | Create `.env` file with API key placeholders, add `.env` to `.gitignore`, ensure app can read keys from `.env`. | M1 | DONE |
| 4 | Verification & Audit | Run all unit tests, install and test debug APK on Android 16 (API 37) emulator, perform forensic integrity audit. | M2, M3 | DONE |

## Interface Contracts
### Gradle & Kotlin Build System
- Android Gradle Plugin: 8.13.2
- Kotlin: 2.1+
- Gradle Wrapper: compatible with AGP 8.13.2 (e.g. 8.11+)
- compileSdk: 37, targetSdk: 37
- Compose Compiler: Gradle plugin `org.jetbrains.kotlin.plugin.compose`

### Alarm Trigger Chain
- `AlarmScheduler.kt` schedules alarm using `AlarmManager.setAlarmClock()`.
- `AlarmReceiver.kt` receives alarm broadcast and starts `AlarmService` as foreground.
- `AlarmService.kt` starts foreground service with type `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` and shows heads-up notification with Full Screen Intent.
- `AlarmActivity.kt` is launched via full-screen intent or notification tap, shows over lock screen.

## Code Layout
- Versions TOML: `gradle/libs.versions.toml`
- Root build file: `build.gradle.kts`
- App build file: `app/build.gradle.kts`
- Settings build file: `settings.gradle.kts`
- Manifest: `app/src/main/AndroidManifest.xml`
- Alarm files:
  - `app/src/main/java/com/mateocuello/alarmai/receiver/AlarmReceiver.kt`
  - `app/src/main/java/com/mateocuello/alarmai/service/AlarmService.kt`
  - `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmActivity.kt`
  - `app/src/main/java/com/mateocuello/alarmai/receiver/AlarmScheduler.kt`
