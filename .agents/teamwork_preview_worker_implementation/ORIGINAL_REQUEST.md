## 2026-06-23T02:23:30Z
<USER_REQUEST>
You are the Implementation Worker. Your task is to implement Milestone 2 & 3: Rebuild WorldCupRepository to dynamically query the official FIFA matches API.

## Objective
Rebuild `WorldCupRepository` to fetch match data dynamically from `https://api.fifa.com/api/v3/calendar/matches?idCompetition=17` using OkHttp, parse the JSON response, map it to `WorldCupMatch`, keep original method signatures, implement graceful fallback to `worldcup_2026.json` on network failure, and ensure all tests pass.

## Step-by-Step Instructions
1. Run existing unit tests via `.\gradlew test` to establish a baseline.
2. Probe the FIFA API structure: write a temporary test or runner that performs a real HTTP GET request using OkHttp to `https://api.fifa.com/api/v3/calendar/matches?idCompetition=17`. Print or log the structure of the JSON response to understand the exact fields (like Results, Date, Home, Away, TeamName, GroupName, Stadium, City).
3. Implement the dynamic API request in `WorldCupRepository.kt`.
   - Make the `OkHttpClient` injectable (e.g. via a constructor parameter with a default client value) to ensure testability.
   - Execute the GET request to the FIFA API.
   - Parse the JSON response (specifically under "Results") using standard JSON libraries.
   - Map fields (MatchDay/StageName -> round, Date -> date and time, Home.TeamName/Away.TeamName -> team1/team2, GroupName -> group, Stadium.City -> ground).
   - If the network request fails, fall back to parsing `worldcup_2026.json` from assets.
4. Modify/update the unit tests in `WorldCupRepositoryTest.kt` and `GeminiAgentManagerTest.kt`:
   - Mock OkHttpClient or use mock JSON responses to test the repository's network integration.
   - Verify that the tests do not rely on live internet connections for passing.
5. Run the unit tests again via `.\gradlew test` and confirm all tests pass successfully.
6. Remove any debugging/temporary files before submitting.

## MANDATORY INTEGRITY WARNING
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Please save your handoff.md in your working directory (.agents/teamwork_preview_worker_implementation/handoff.md) with details of your implementation, the tests run, and the outcomes.
</USER_REQUEST>
