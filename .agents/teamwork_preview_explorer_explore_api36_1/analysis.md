# Android 16 (API 36) Compatibility Analysis — Manifest & Service Updates

This analysis identifies required updates for Android 16 (API 36) compatibility, focusing on manifest permissions, AlarmService configuration, and `AlarmService.kt`.

## 1. AndroidManifest.xml Analysis

### File Path
`app/src/main/AndroidManifest.xml`

### Current Configuration & Observations
1. **Exact Alarm Permissions (Lines 14-15):**
   ```xml
   14:     <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
   15:     <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
   ```
   - **Issue:** The application uses both `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM`. For an alarm app, only `USE_EXACT_ALARM` is needed as it is auto-granted and avoids restrictive runtime permission checks/settings redirection.
   
2. **Foreground Service Permission (Line 23):**
   ```xml
   23:     <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
   ```
   - **Issue:** The application currently requests `FOREGROUND_SERVICE_MEDIA_PLAYBACK` for its ringtone service, which is a policy violation for general alarm use cases. It must be updated to `FOREGROUND_SERVICE_SPECIAL_USE`.

3. **AlarmService Declaration (Lines 88-93):**
   ```xml
   88:         <service
   89:             android:name=".service.AlarmService"
   90:             android:enabled="true"
   91:             android:exported="false"
   92:             android:foregroundServiceType="mediaPlayback"
   93:             android:directBootAware="true" />
   ```
   - **Issue:** The service currently uses `foregroundServiceType="mediaPlayback"`. Under Android 16, this must use `foregroundServiceType="specialUse"` and declare the required special use subtype property (`android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` set to `alarm`).

### Proposed Changes for `AndroidManifest.xml`
1. **Remove `SCHEDULE_EXACT_ALARM` permission:**
   - Delete line 14: `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />`
2. **Replace `FOREGROUND_SERVICE_MEDIA_PLAYBACK` with `FOREGROUND_SERVICE_SPECIAL_USE`:**
   - Change line 23 to:
     ```xml
     <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
     ```
3. **Update `AlarmService` configuration:**
   - Modify lines 88-93:
     ```xml
             <service
                 android:name=".service.AlarmService"
                 android:enabled="true"
                 android:exported="false"
                 android:foregroundServiceType="specialUse"
                 android:directBootAware="true">
                 <property
                     android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                     android:value="alarm" />
             </service>
     ```

---

## 2. AlarmService.kt Analysis

### File Path
`app/src/main/java/com/mateocuello/alarmai/service/AlarmService.kt`

### Current Configuration & Observations
1. **Foreground Service Type Constant (Lines 74-82):**
   ```kotlin
   74:         // Start Foreground
   75:         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
   76:             startForeground(
   77:                 NOTIFICATION_ID, 
   78:                 notification, 
   79:                 ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
   80:             )
   81:         } else {
   82:             startForeground(NOTIFICATION_ID, notification)
   83:         }
   ```
   - **Issue:** It starts the foreground service with `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` on line 79. This matches the manifest's old configuration and must be updated.

2. **Fallback Activity Launch (Lines 84-92):**
   ```kotlin
   84:         // Fallback: If we can't use full screen intent, launch AlarmActivity directly
   85:         if (!canUseFullScreen) {
   86:             Log.d("AlarmService", "Full screen intent not allowed. Launching AlarmActivity directly.")
   87:             try {
   88:                 startActivity(fullScreenIntent)
   89:             } catch (e: Exception) {
   90:                 Log.e("AlarmService", "Failed to start AlarmActivity directly: ${e.localizedMessage}")
   91:             }
   92:         }
   ```
   - **Issue:** Launching an activity from a background service via `startActivity()` when the full-screen intent is disabled/denied violates Android 16's strict background activity launch restrictions. The system's high-priority heads-up notification is sufficient.

### Proposed Changes for `AlarmService.kt`
1. **Update `startForeground` type constant:**
   - Change `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` on line 79 to `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE`.
2. **Remove fallback activity launch:**
   - Remove lines 84 to 92 entirely.
