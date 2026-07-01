# Toolchain Upgrade Investigation & Analysis

## Executive Summary
This analysis details the required configuration updates to migrate the AlarmAI application to Android 16 (API 37), upgrade Kotlin to 2.1+, integrate the new Compose Compiler Gradle Plugin, and verify Gradle wrapper compatibility with Android Gradle Plugin (AGP) 8.13.2. A pre-compiled `.patch` file has been provided to automate these changes.

---

## 1. Current vs. Proposed Version State

The following table summarizes the current state of build versions and the recommended configuration updates for the toolchain upgrade:

| Component | Current Configuration | Target Configuration | Source Reference File |
| :--- | :--- | :--- | :--- |
| **Gradle Wrapper** | `8.13` | `8.13` (Already compatible) | `gradle/wrapper/gradle-wrapper.properties` |
| **Android Gradle Plugin (AGP)** | `8.13.2` | `8.13.2` (No change required) | `gradle/libs.versions.toml` |
| **Kotlin Version** | `1.9.23` | `2.1.0` (or `2.1.10`) | `gradle/libs.versions.toml` |
| **compileSdk** | `34` | `37` | `app/build.gradle.kts` |
| **targetSdk** | `34` | `37` | `app/build.gradle.kts` |
| **Compose Compiler** | Legacy Extension `1.5.11` | Gradle Plugin `org.jetbrains.kotlin.plugin.compose` (v2.1.0) | `app/build.gradle.kts` / `libs.versions.toml` |

---

## 2. Compose Compiler Configuration

### Current Legacy Setup
Currently, the Compose Compiler is configured using the legacy Compose Compiler Extension (tied to Kotlin 1.x). This is declared in `app/build.gradle.kts`:
```kotlin
buildFeatures {
    compose = true
}
composeOptions {
    kotlinCompilerExtensionVersion = "1.5.11" // Tied to Kotlin 1.9.23
}
```

### Proposed Gradle Plugin Setup
Starting with Kotlin 2.0.0, the Compose Compiler has been merged into the Kotlin repository. It must be applied as a Gradle plugin (`org.jetbrains.kotlin.plugin.compose`) matching the Kotlin version (e.g., `2.1.0`).
1. In `gradle/libs.versions.toml`, add under `[plugins]`:
   ```toml
   kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
   ```
2. In the root `build.gradle.kts`, declare the plugin without applying it:
   ```kotlin
   plugins {
       alias(libs.plugins.android.application) apply false
       alias(libs.plugins.kotlin.android) apply false
       alias(libs.plugins.kotlin.compose) apply false // Added
   }
   ```
3. In `app/build.gradle.kts`, apply the plugin and remove the legacy `composeOptions` block:
   ```kotlin
   plugins {
       alias(libs.plugins.android.application)
       alias(libs.plugins.kotlin.android)
       alias(libs.plugins.kotlin.compose) // Added
   }

   android {
       // ...
       buildFeatures {
           compose = true // Kept
       }
       // composeOptions block is deleted completely!
   }
   ```

---

## 3. Upgrading compileSdk and targetSdk to 37

To target Android 16 (API 37), modify the `android` block in `app/build.gradle.kts`:

- **Change `compileSdk = 34` to `compileSdk = 37`**
- **Change `targetSdk = 34` to `targetSdk = 37`**

*Example:*
```kotlin
android {
    namespace = "com.mateocuello.alarmai"
    compileSdk = 37 // Updated

    defaultConfig {
        applicationId = "com.mateocuello.alarmai"
        minSdk = 26
        targetSdk = 37 // Updated
        // ...
    }
}
```

---

## 4. Upgrading Kotlin to 2.1+

AGP 8.13.2 requires Kotlin 2.0+. To upgrade to Kotlin 2.1+ (e.g., `2.1.0`):
1. In `gradle/libs.versions.toml`, update the `kotlin` version under `[versions]`:
   ```toml
   [versions]
   kotlin = "2.1.0"
   ```
2. This will automatically update both `kotlin-android` and the new `kotlin-compose` plugins, as they both refer to the `kotlin` version reference.

---

## 5. Gradle Wrapper Verification & Upgrade

### Verification Method
1. Inspect the `gradle/wrapper/gradle-wrapper.properties` file. Look for the `distributionUrl` property.
   - *Current value:* `distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip`
2. Run the following command from the project root to verify the active Gradle version:
   ```powershell
   .\gradlew --version
   ```
   - *Current output:* `Gradle 8.13`

### Compatibility Check
- **AGP 8.13.2 compatibility:** According to Gradle-AGP compatibility matrices, AGP 8.13.x requires Gradle 8.13 or higher. Since the wrapper is already configured to `8.13`, it is fully compatible. No upgrade of Gradle itself is required.

### Upgrade Procedure (If Upgrade Was Necessary)
If a future upgrade is required (e.g., to Gradle 8.14+), it can be achieved by running:
```powershell
.\gradlew wrapper --gradle-version 8.13 --distribution-type bin
```
Alternatively, manually edit `gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
```

---

## 6. Implementation & Verification Plan

### Proposed Patch File
A unified diff is available at `.agents/teamwork_preview_explorer_toolchain_3/toolchain_upgrade.patch` and can be applied directly using git:
```powershell
git apply .agents/teamwork_preview_explorer_toolchain_3/toolchain_upgrade.patch
```

### Verification Commands
After the changes are applied by the implementer, verification should be performed using:
1. Clean build of the project:
   ```powershell
   .\gradlew clean assembleDebug
   ```
2. Run unit tests to verify Kotlin compiler and runtime integrity:
   ```powershell
   .\gradlew test
   ```
