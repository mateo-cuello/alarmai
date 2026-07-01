# Toolchain Upgrade Analysis

This report documents the current Android build toolchain configuration of AlarmAI and provides detailed, actionable instructions to upgrade the toolchain to support Android 16 (API 37), Kotlin 2.1+, and the modern Compose Compiler Gradle Plugin, while ensuring compatibility with Android Gradle Plugin (AGP) 8.13.2.

---

## 1. Current Toolchain Configuration

Based on a read-only inspection of the project's build files, here is the current configuration:

| Component | Version / Configuration | Config File Location | Reference Code / Property |
|---|---|---|---|
| **Gradle Wrapper** | `8.13` | `gradle/wrapper/gradle-wrapper.properties` | `distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip` |
| **Kotlin** | `1.9.23` | `gradle/libs.versions.toml` | `[versions]` block: `kotlin = "1.9.23"` |
| **AGP** | `8.13.2` | `gradle/libs.versions.toml` | `[versions]` block: `agp = "8.13.2"` |
| **Compile SDK** | `34` | `app/build.gradle.kts` | `compileSdk = 34` |
| **Target SDK** | `34` | `app/build.gradle.kts` | `targetSdk = 34` |
| **Min SDK** | `26` | `app/build.gradle.kts` | `minSdk = 26` |
| **Compose Compiler** | Extension `1.5.11` | `app/build.gradle.kts` | `composeOptions { kotlinCompilerExtensionVersion = "1.5.11" }` |
| **Java / JVM Toolchain** | Compatibility `21` | `app/build.gradle.kts` | `sourceCompatibility = JavaVersion.VERSION_21`, `targetCompatibility = JavaVersion.VERSION_21`, `kotlinOptions { jvmTarget = "21" }` |

---

## 2. Upgrade Guide & Step-by-Step Instructions

To achieve compatibility with Android 16 (API 37) and Kotlin 2.1+, the following changes should be applied:

### A. Upgrading SDK Versions (compileSdk & targetSdk to 37)
To compile and target Android 16, update the SDK configurations in `app/build.gradle.kts`:
- **Change `compileSdk` to `37`** (at line 8).
- **Change `targetSdk` to `37`** (at line 13).

```kotlin
// app/build.gradle.kts (Before)
compileSdk = 34
defaultConfig {
    ...
    targetSdk = 34
}

// app/build.gradle.kts (After)
compileSdk = 37
defaultConfig {
    ...
    targetSdk = 37
}
```

### B. Upgrading Kotlin to 2.1+
AGP 8.13.2 is fully compatible with Kotlin 2.1+. We recommend upgrading to **Kotlin 2.1.0** (or higher).
- Update the Kotlin version reference in the Gradle Version Catalog (`gradle/libs.versions.toml` at line 3):

```toml
# gradle/libs.versions.toml (Before)
kotlin = "1.9.23"

# gradle/libs.versions.toml (After)
kotlin = "2.1.0"
```

### C. Migrating from Compose Compiler Extension to Compose Compiler Gradle Plugin
Starting with Kotlin 2.0.0, the Jetpack Compose Compiler has been integrated into the Kotlin repository. It is now applied as a Gradle plugin (`org.jetbrains.kotlin.plugin.compose`) matching the Kotlin compiler version, and the legacy `composeOptions { kotlinCompilerExtensionVersion = ... }` configuration is deprecated/removed.

#### Step 1: Add the Compose Compiler Gradle Plugin to the Version Catalog
Open `gradle/libs.versions.toml` and declare the new plugin in the `[plugins]` block:

```toml
# gradle/libs.versions.toml (After)
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

#### Step 2: Register the Plugin in the Root Project
Open the root `build.gradle.kts` file and add the plugin to the `plugins` block:

```kotlin
// build.gradle.kts (Before)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// build.gradle.kts (After)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

#### Step 3: Apply the Plugin and Clean up Module Configuration in `app/build.gradle.kts`
Open `app/build.gradle.kts` and apply the plugin in the `plugins` block:

```kotlin
// app/build.gradle.kts (Before)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// app/build.gradle.kts (After)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
```

Then, **remove the legacy `composeOptions` block** completely (lines 43-45):

```kotlin
// app/build.gradle.kts (Remove this completely)
composeOptions {
    kotlinCompilerExtensionVersion = "1.5.11"
}
```

### D. Verifying and Upgrading Gradle Wrapper Compatibility
The current project uses **Gradle 8.13** as configured in `gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
```
- **Verification**: AGP 8.13.2 requires Gradle 8.13 or higher. Since the current wrapper version is `8.13`, it is already fully compatible.
- **Upgrading / Refreshing Gradle Wrapper**: If you ever need to manually update or regenerate the wrapper files (for instance, to fetch Gradle 8.13 fresh or ensure files are up-to-date), execute the following command from the project root:
  ```powershell
  ./gradlew wrapper --gradle-version 8.13 --distribution-type bin
  ```
  This command regenerates:
  - `gradlew`
  - `gradlew.bat`
  - `gradle/wrapper/gradle-wrapper.jar`
  - `gradle/wrapper/gradle-wrapper.properties` (sets `distributionUrl`)

---

## 3. Proposed Changes (Git Diff Patch)

To facilitate automated application of these changes by an implementer, a `.patch` file is provided at:
`c:\Users\usuario\alarmai\.agents\teamwork_preview_explorer_toolchain_2\toolchain_upgrade.patch`

This patch contains the exact modifications required for:
1. `gradle/libs.versions.toml`
2. `build.gradle.kts`
3. `app/build.gradle.kts`
