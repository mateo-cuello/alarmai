# Review Report - World Cup Matches API Integration

## Review Summary

**Verdict**: APPROVE

The World Cup matches API integration is successfully implemented. `WorldCupRepository` queries the dynamic FIFA matches API with OkHttp, parses localized arrays with fallbacks correctly, and gracefully falls back to the local asset in case of network failures or empty responses. Public interface signatures have been preserved. Tests in `WorldCupRepositoryTest` and `WorldCupRepositoryStressTest` are hermetic and cover successful/failed network scenarios. However, `GeminiAgentManagerTest` only covers offline/demo modes, with live network calls to Gemini remaining untested due to inline instantiation of `OkHttpClient`.

---

## Findings

### Major Finding 1: Lack of Network Flow Testing in `GeminiAgentManagerTest`

- **What**: `GeminiAgentManagerTest` does not cover successful or failed network flows for the Gemini API call (`makePostRequest`).
- **Where**: `app/src/test/java/com/mateocuello/alarmai/data/repository/GeminiAgentManagerTest.kt`
- **Why**: `GeminiAgentManager` instantiates `OkHttpClient()` directly in the `makePostRequest` method (line 549 of `GeminiAgentManager.kt`):
  ```kotlin
  private suspend fun makePostRequest(url: String, jsonBody: String): String = withContext(Dispatchers.IO) {
      val client = OkHttpClient()
      ...
  }
  ```
  This prevents injecting a mocked HTTP client or custom factory, making it difficult to write hermetic tests for non-demo mode flows without resorting to bytecode manipulation or starting a mock server.
- **Suggestion**: Refactor `GeminiAgentManager` to accept an `OkHttpClient` instance or a call factory in its constructor, similar to `WorldCupRepository`, so that the client can be mocked in unit tests.

### Minor Finding 2: `testParseMatches_realFile` Dependency on Local Filesystem

- **What**: The unit test `testParseMatches_realFile` reads directly from the project's assets directory.
- **Where**: `app/src/test/java/com/mateocuello/alarmai/data/repository/WorldCupRepositoryTest.kt` (lines 72-92)
- **Why**: It calls `java.io.File("src/main/assets/worldcup_2026.json")`, which makes the test run-environment-dependent (not strictly hermetic if the assets are modified or if the test is executed from a different directory).
- **Suggestion**: Use the mock asset manager setup like in `testFetchAllMatches_networkFailure_fallsBackToAsset` or load the file as a classpath resource.

---

## Verified Claims

- **WorldCupRepository fetches dynamically via OkHttp** → Verified via code review of `WorldCupRepository.kt` (lines 25-40) and mocked client execution test → **PASS**
- **JSON responses are parsed and localized correctly (falling back to English / first element)** → Verified by code analysis of `parseLocalizedArray` and stress tests in `WorldCupRepositoryStressTest.kt` (e.g. `testLocaleFallbacks`) → **PASS**
- **Graceful fallback to local asset file** → Verified via unit tests (`testFetchAllMatches_networkFailure_fallsBackToAsset`) and stress tests (`testHTTP_404_fallsBackToAsset`, `testNetworkTimeout_fallsBackToAsset`) → **PASS**
- **Interface signatures preserved** → Verified method signatures match `PROJECT.md` and call sites in `GeminiAgentManager.kt` → **PASS**
- **Hermetic tests in `WorldCupRepositoryTest`** → Verified they mock OkHttp factory and assets in mock context → **PASS**

---

## Coverage Gaps

- **Gemini Agent Manager live network calls** — risk level: **medium** — recommendation: **accept risk** for this PR since code changes are restricted to World Cup matches API integration, but schedule a refactoring task to support OkHttpClient injection in `GeminiAgentManager` for future testing.

---

## Unverified Items

- **Actual API behavior with live internet connection** — reason not verified: Agent is operating in `CODE_ONLY` network mode and cannot make external network calls.
