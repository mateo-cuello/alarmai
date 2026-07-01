# Migration and Fixes Plan

## Step 1: Investigation and Build Verification (Toolchain Upgrade)
- Spawn an Explorer agent to analyze Gradle/Kotlin version compatibility, the current setup of targetSdk/compileSdk, and Compose Compiler plugin.
- Create a working build plan.
- Verifiable by: Successful compilation of the project with SDK 37 and Kotlin 2.1+ under AGP 8.13.2 using the Compose Compiler Gradle plugin.

## Step 2: Implementation of Toolchain & Build Fixes
- Spawn a Worker agent to execute the upgrades in `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, and `gradle/wrapper/gradle-wrapper.properties`.
- Verifiable by: `./gradlew assembleDebug` compiling without warnings or errors.

## Step 3: Alarm Functionality & Android 16 (API 37) Compatibility Fixes
- Spawn an Explorer/Worker agent to investigate the broken alarm functionality:
  - Test Alarm button triggers the alarm chain.
  - AlarmActivity displays over the lock screen.
  - Notification tap works without crash.
  - AlarmService foreground service type compatibility.
- Implement the required fixes.
- Verifiable by: Build success and manual/automated checks showing correct alarm triggers.

## Step 4: Environment Configuration
- Spawn a Worker agent to create `.env` in the project root with the Gemini API key placeholder.
- Add `.env` to `.gitignore`.
- Ensure the app can read from `.env`.
- Verifiable by: checking `.gitignore` contents and verifying `.env` file exists.

## Step 5: Verification & Auditing
- Spawn a Reviewer/Challenger/Auditor to:
  - Run all unit tests `./gradlew test`.
  - Install the APK on Android 16 emulator and run emulator verification.
  - Perform the forensic integrity audit.
- Verifiable by: All unit tests passing, clean audit report.
