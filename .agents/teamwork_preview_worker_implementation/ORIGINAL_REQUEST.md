## 2026-07-01T16:05:27Z
You are a teamwork_preview_worker. Your working directory is c:\Users\usuario\alarmai\.agents\teamwork_preview_worker_implementation.
Your task is to implement the foreground location caching features and fix the pre-existing compilation errors in the unit test files.

Follow these implementation details carefully:
1. In `app/src/main/java/com/mateocuello/alarmai/data/local/PreferencesManager.kt`:
   - Implement `hasCachedLocation(): Boolean` checking if KEY_LAT and KEY_LON are in SharedPreferences.

2. In `app/src/main/java/com/mateocuello/alarmai/MainViewModel.kt`:
   - Add `LocationProvider` dependency and define a `fetchLocation()` method that asynchronously fetches location in viewModelScope and saves it to PreferencesManager.

3. In `app/src/main/java/com/mateocuello/alarmai/MainActivity.kt`:
   - Call `viewModel.fetchLocation()` in `onResume()` and inside the location permission launcher callback when permission is granted.

4. In `app/src/main/java/com/mateocuello/alarmai/service/PrefetchWorker.kt`:
   - Update location-fetching block: check if cached location is available and use it instantly, otherwise fall back to live GPS fetch with a 3-second timeout.

5. In `app/src/main/java/com/mateocuello/alarmai/ui/alarm/AlarmViewModel.kt`:
   - Add a private tracking flag `wasCachedLocationUsed: Boolean`.
   - Update `dismissAndTalk()` to fetch location instantly if cached location is available, setting the flag `wasCachedLocationUsed = true`, else fall back to live GPS fetch (and set the flag appropriately).
   - Update `speakAgentResponse()` to launch a background coroutine under viewModelScope to silently refresh the location cache coordinates using locationProvider if `wasCachedLocationUsed` is true.

6. Fix the compilation errors in the test directory:
   - Identify and resolve compilation issues in `IntegrationTest.kt`, `GeminiAgentManagerTest.kt`, `AlarmViewModelTest.kt`, and `NewsRepositoryTest.kt` (such as references to the non-existent WorldCupRepository or startSession argument mismatches).

Verify your changes:
- Run `./gradlew compileDebugSources` to ensure the project builds successfully.
- Run `./gradlew testDebugUnitTest` to verify that all unit tests compile and pass.

Write your report to c:\Users\usuario\alarmai\.agents\teamwork_preview_worker_implementation\handoff.md detailing:
- The exact changes made.
- The command used to run tests and the results.
When done, send a message to the orchestrator (conversation ID: 124f24c9-24ca-4096-835c-a658ada7b0df).

MANDATORY INTEGRITY WARNING:
> DO NOT CHEAT. All implementations must be genuine. DO NOT
> hardcode test results, create dummy/facade implementations, or
> circumvent the intended task. A Forensic Auditor will independently
> verify your work. Integrity violations WILL be detected and your
> work WILL be rejected.
