# Toolchain & Build Configuration Analysis

This document provides a detailed analysis of the current project build configuration and specifies the step-by-step instructions for upgrading the build toolchain to support Android 16 (API 37), Kotlin 2.1+, and AGP 8.13.2.

---

## 1. Baseline System Status

We investigated the project configuration files and identified the following current versions:

*   **Gradle Wrapper Version**: `8.13`
    *   *Source*: `gradle/wrapper/gradle-wrapper.properties`
    *   *Property*: `distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip`
    *   *Verification*: Confirmed active launcher JVM is 21.0.10 and execution of `./gradlew --version` is successful.
*   **Kotlin Version**: `1.9.23`
    *   *Source*: `gradle/libs.versions.toml`
    *   *Property*: `[versions] -> kotlin = "1.9.23"`
*   **Android Gradle Plugin (AGP) Version**: `8.13.2`
    *   *Source*: `gradle/libs.versions.toml`
    *   *Property*: `[versions] -> agp = "8.13.2"`
*   **SDK Versions**:
    *   `compileSdk`: `34`
    *   `targetSdk`: `34`
    *   `minSdk`: `26`
    *   *Source*: `app/build.gradle.kts`

---

## 2. Current Compose Compiler Configuration

*   **Configuration Method**: Legacy Compose Compiler Extension (pre-Kotlin 2.0).
*   **Current Extension Version**: `1.5.11` (specifically selected to match Kotlin 1.9.23).
*   **Current Settings**:
    *   *App Build File (`app/build.gradle.kts`)*:
        ```kotlin
        buildFeatures {
            compose = true
            buildConfig = true
        }
        composeOptions {
            kotlinCompilerExtensionVersion = "1.5.11"
        }
        ```
*   **Limitation**: This setup is incompatible with Kotlin 2.0+ and 2.1+. In newer Kotlin versions, the compiler extension must be replaced with the official Jetpack Compose Compiler Gradle Plugin.

---

## 3. How to Upgrade compileSdk and targetSdk to 37

To migrate to Android 16 (API 37), the SDK version configurations in the app-level `build.gradle.kts` must be bumped.

### Step-by-Step Instructions:
1. Open `app/build.gradle.kts`.
2. Locate the `compileSdk` setting (typically around line 8) and change it from `34` to `37`.
3. Locate the `targetSdk` setting under `defaultConfig` (typically around line 13) and change it from `34` to `37`.

### Proposed Diff (`app/build.gradle.kts`):
```diff
 android {
     namespace = "com.mateocuello.alarmai"
-    compileSdk = 34
+    compileSdk = 37
 
     defaultConfig {
         applicationId = "com.mateocuello.alarmai"
         minSdk = 26
-        targetSdk = 34
+        targetSdk = 37
         versionCode = 1
         versionName = "1.0"
```

---

## 4. How to Upgrade Kotlin to 2.1+ (Compatible with AGP 8.13.2)

AGP 8.13.2 requires Kotlin 2.1+ for modern Android development. We will upgrade Kotlin to version `2.1.0` (or `2.1.10`), which is fully compatible.

### Step-by-Step Instructions:
1. Open `gradle/libs.versions.toml`.
2. Locate `kotlin = "1.9.23"` in the `[versions]` section.
3. Update the value to `"2.1.0"` (or `"2.1.10"`).

### Proposed Diff (`gradle/libs.versions.toml`):
```diff
 [versions]
 agp = "8.13.2"
-kotlin = "1.9.23"
+kotlin = "2.1.0"
```

---

## 5. How to Replace Compose Compiler Extension with the Compose Compiler Gradle Plugin

Beginning with Kotlin 2.0.0, the Compose Compiler is shipped directly within the Kotlin release cycle. The old `composeOptions` configuration must be removed, and the `org.jetbrains.kotlin.plugin.compose` plugin must be applied.

### Step-by-Step Instructions:
1. **Define the Plugin in the Version Catalog (`gradle/libs.versions.toml`)**:
   Under the `[plugins]` block, define the Kotlin Compose plugin, referencing the upgraded `kotlin` version.
   ```toml
   kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
   ```

2. **Register the Plugin in the Root Build File (`build.gradle.kts`)**:
   Add the alias definition to the `plugins` block and specify `apply false`.
   ```kotlin
   plugins {
       alias(libs.plugins.android.application) apply false
       alias(libs.plugins.kotlin.android) apply false
       alias(libs.plugins.kotlin.compose) apply false // Added
   }
   ```

3. **Apply the Plugin in the App Module Build File (`app/build.gradle.kts`)**:
   *   Add the Compose plugin alias under the `plugins` block.
   *   Remove the entire `composeOptions` block.
   *   Ensure `buildFeatures.compose = true` remains enabled.

### Proposed Code Changes:

#### A. `gradle/libs.versions.toml`
```diff
 [plugins]
 android-application = { id = "com.android.application", version.ref = "agp" }
 kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
+kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

#### B. `build.gradle.kts` (Root)
```diff
 plugins {
     alias(libs.plugins.android.application) apply false
     alias(libs.plugins.kotlin.android) apply false
+    alias(libs.plugins.kotlin.compose) apply false
 }
```

#### C. `app/build.gradle.kts`
```diff
 plugins {
     alias(libs.plugins.android.application)
     alias(libs.plugins.kotlin.android)
+    alias(libs.plugins.kotlin.compose)
 }
 
 android {
...
     buildFeatures {
         compose = true
         buildConfig = true
     }
-    composeOptions {
-        kotlinCompilerExtensionVersion = "1.5.11" // Matches Kotlin 1.9.23
-    }
     packaging {
```

---

## 6. How to Verify and Upgrade the Gradle Wrapper for AGP 8.13.2

Android Gradle Plugin (AGP) 8.13.2 requires a minimum compatible Gradle version of **Gradle 8.13**.

### Verification:
1. Check `gradle/wrapper/gradle-wrapper.properties` distribution URL:
   `distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip`
2. Run `./gradlew --version` to verify that Gradle is running at version `8.13`.
3. The project is **already using Gradle 8.13**, which satisfies the AGP 8.13.2 requirements. No further upgrade is needed.

### Upgrade Procedure (If a newer patch/version is required in the future):
To upgrade the Gradle wrapper systematically (updating both the properties file, the wrapper JAR, and helper scripts):
Run the following Gradle task from the project root:
```bash
./gradlew wrapper --gradle-version 8.13 --distribution-type bin
```
Or manually edit the `distributionUrl` in `gradle/wrapper/gradle-wrapper.properties` to point to the new distribution ZIP file (e.g. `gradle-8.13.1-bin.zip`).

---

## 7. Actionable Toolchain Upgrade Path Summary

To apply all proposed changes, the implementing agent should execute the following edits in sequence:

1.  **Modify `gradle/libs.versions.toml`**:
    *   Change `kotlin` version reference to `2.1.0`.
    *   Add `kotlin-compose` to `[plugins]`.
2.  **Modify `build.gradle.kts`**:
    *   Add `alias(libs.plugins.kotlin.compose) apply false` inside the `plugins` block.
3.  **Modify `app/build.gradle.kts`**:
    *   Add `alias(libs.plugins.kotlin.compose)` to the `plugins` block.
    *   Update `compileSdk` to `37`.
    *   Update `targetSdk` to `37`.
    *   Remove `composeOptions` block.
4.  **Verification Run**:
    *   Run `./gradlew assembleDebug` to compile the app and verify the new toolchain.
