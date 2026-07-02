# Handoff Report: TTS Playback Flow and Silent Location Caching

## 1. Observation
After examining the codebase in `c:\Users\usuario\alarmai`, we observed the following:

- **TTS Playback Start Point**:
  - In `AlarmViewModel.kt` (`c:\Users\usuario\alarmai\app\src\main\java\com\mateocuello\alarmai\ui\alarm\AlarmViewModel.kt`), TTS playback starts within the private method `speakAgentResponse(text: String)` on line 217 by invoking `voiceManager.speak(text)`:
    ```kotlin
    207:     private fun speakAgentResponse(text: String) {
    208:         cancelNoSpeechTimeout()
    209:         voiceManager.stopListening()
    ...
    217:         voiceManager.speak(text) {
    218:             // Callback when agent finishes speaking: add delay to let TTS fully release audio
    219:             viewModelScope.launch {
    220:                 // Do not stopSpeaking() here as it nullifies callback (which is already executed)
    221:                 kotlinx.coroutines.delay(600)
    222:                 startListeningForUser()
    223:             }
    224:         }
    225:     }
    ```
  - In `VoiceManager.kt` (`c:\Users\usuario\alarmai\app\src\main\java\com\mateocuello\alarmai\data\repository\VoiceManager.kt`), `VoiceManager.speak(...)` handles the actual TTS engine invocation on line 218:
    ```kotlin
    204:     fun speak(text: String, onComplete: () -> Unit) {
    ...
    218:         tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "alarm_briefing")
    219:     }
    ```

- **Current Location Caching and Fallback**:
  - In `AlarmViewModel.kt`, on-demand location fetch blocks for up to 3000ms checking live GPS via `locationProvider.getCurrentLocation()` on lines 149-152:
    ```kotlin
    149:                     // Get location coordinates (either pre-fetched or fetch on demand)
    150:                     val location = withTimeoutOrNull(3000) { locationProvider.getCurrentLocation() }
    151:                     val (lat, lon) = prefetchedLocation
    152:                         ?: location?.also { prefs.saveLocation(it.first, it.second) }
    153:                         ?: prefs.getLocation()
    ```
  - `PreferencesManager.kt` (`c:\Users\usuario\alarmai\app\src\main\java\com\mateocuello\alarmai\data\local\PreferencesManager.kt`) does not currently implement a `hasCachedLocation(): Boolean` method. It exposes `saveLocation` and `getLocation` at lines 100-114:
    ```kotlin
    100:     fun saveLocation(lat: Double, lon: Double) {
    101:         prefs.edit()
    ...
    107:     fun getLocation(): Pair<Double, Double> {
    ...
    ```

- **Test Suite Compilation Status**:
  - Running `./gradlew test` (or `./gradlew compileDebugUnitTestKotlin`) fails to compile the test suite due to missing references to `WorldCupRepository` and signature mismatches with `GeminiAgentManager.startSession` which no longer accepts `worldCupData`:
    ```
    e: file:///C:/Users/usuario/alarmai/app/src/test/java/com/mateocuello/alarmai/IntegrationTest.kt:29:37 Unresolved reference 'WorldCupRepository'.
    e: file:///C:/Users/usuario/alarmai/app/src/test/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModelTest.kt:13:48 Unresolved reference 'WorldCupRepository'.
    e: file:///C:/Users/usuario/alarmai/app/src/test/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModelTest.kt:140:128 Too many arguments for 'suspend fun startSession(...)'.
    ```

---

## 2. Logic Chain
- **Zero-Delay for the User**:
  - Waiting up to 3000ms for Fused Location Provider results (as done on line 150 of `AlarmViewModel.kt`) creates a noticeable delay when loading the morning briefing on demand.
  - To achieve zero delay, if a cached location is already available in the preferences, the app must read and use it instantly.
  - This requires adding `hasCachedLocation(): Boolean` to `PreferencesManager` checking if the coordinates are stored.
  - In `AlarmViewModel.dismissAndTalk()`, if `prefs.hasCachedLocation()` is `true`, we immediately load data using the cached coordinates and set a boolean flag `wasCachedLocationUsed = true`, bypassing the 3000ms timeout.
- **Silent Location Refresh**:
  - To ensure the cached location does not become permanently stale, we trigger a background update when TTS starts.
  - Placing this update trigger inside `speakAgentResponse(text)` (immediately before or during TTS start) ensures that the refresh executes asynchronously while the user is occupied listening to the briefing speech.
  - Using `viewModelScope.launch` ensures the coroutine runs off-thread and completes safely tied to the ViewModel lifecycle without delaying TTS playback.

---

## 3. Caveats
- **Permission Check**: The Fused Location Provider client will fail silently or return `null` if location permissions are not granted. In this case, `LocationProvider.getCurrentLocation()` safely returns `null` immediately, so the background fetch handles it gracefully.
- **Cache Invalidation**: Currently, there is no age limit on the cached coordinates themselves. They are refreshed whenever a cached location was used and TTS starts, but otherwise remain permanently valid.
- **Existing Compiler Failures**: The project's unit tests do not compile out-of-the-box because of the removed `WorldCupRepository` dependencies. This must be addressed first in the test files before executing the test command.

---

## 4. Conclusion
Below is the proposed design for implementing the instant location caching and silent background refresh:

### A. PreferencesManager.kt Addition
Add the following method to `PreferencesManager.kt` to check for cached location keys:
```kotlin
fun hasCachedLocation(): Boolean {
    return prefs.contains(KEY_LAT) && prefs.contains(KEY_LON)
}
```

### B. AlarmViewModel.kt Changes
1. Declare a private state flag `wasCachedLocationUsed` inside `AlarmViewModel`:
   ```kotlin
   private var wasCachedLocationUsed = false
   ```
2. Modify `dismissAndTalk()` to bypass GPS wait if cached location exists:
   ```kotlin
   // Get location coordinates instantly if cached is available, ensuring zero delay
   val (lat, lon) = if (prefetchedLocation != null) {
       wasCachedLocationUsed = false
       prefetchedLocation!!
   } else if (prefs.hasCachedLocation()) {
       wasCachedLocationUsed = true
       prefs.getLocation()
   } else {
       // Fallback to live GPS fetch if no cache exists
       val location = withTimeoutOrNull(3000) { locationProvider.getCurrentLocation() }
       if (location != null) {
           wasCachedLocationUsed = false
           prefs.saveLocation(location.first, location.second)
           location
       } else {
           wasCachedLocationUsed = true
           prefs.getLocation()
       }
   }
   ```
3. Update `speakAgentResponse(text)` to trigger the silent background location refresh:
   ```kotlin
   private fun speakAgentResponse(text: String) {
       cancelNoSpeechTimeout()
       voiceManager.stopListening()
       
       _agentSpeech.value = text
       addChatMessage(MessageSender.AGENT, text)
       _uiState.value = AlarmState.SPEAKING
       _userSpeech.value = ""
       _micVolume.value = 0f
       
       if (wasCachedLocationUsed) {
           wasCachedLocationUsed = false // Reset to avoid duplicate requests in a session
           viewModelScope.launch {
               try {
                   val location = locationProvider.getCurrentLocation()
                   if (location != null) {
                       prefs.saveLocation(location.first, location.second)
                       Log.d("AlarmViewModel", "Silently updated location cache: $location")
                   }
               } catch (e: Exception) {
                   Log.e("AlarmViewModel", "Silent location update failed: ${e.localizedMessage}")
               }
           }
       }
       
       voiceManager.speak(text) {
           // ...
       }
   }
   ```

---

## 5. Verification Method
1. **Resolve Test Compile Errors**:
   - Edit `IntegrationTest.kt`, `GeminiAgentManagerTest.kt`, and `AlarmViewModelTest.kt` to remove references to `WorldCupRepository`.
   - Remove `worldCupData` arguments in mock calls to `GeminiAgentManager.startSession`.
2. **Execute Tests**:
   - Run the unit test suite:
     ```powershell
     ./gradlew test
     ```
3. **Verify Caching Behavior via New Tests**:
   - Create a test `testDismissAndTalk_UsesCachedLocationInstantly_WhenCacheExists`: Mock `prefs.hasCachedLocation()` to return `true`. Call `dismissAndTalk()`, and verify that `locationProvider.getCurrentLocation()` is not invoked during initialization.
   - Create a test `testSpeakAgentResponse_SilentlyRefreshesCache_WhenCachedLocationWasUsed`: Mock `prefs.hasCachedLocation()` to return `true`. Call `dismissAndTalk()`, advance the dispatcher, and verify that `locationProvider.getCurrentLocation()` is invoked asynchronously when `speakAgentResponse` starts.
