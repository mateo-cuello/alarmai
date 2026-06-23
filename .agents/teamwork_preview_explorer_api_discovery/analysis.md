# FIFA World Cup API Discovery and Codebase Analysis

## 1. Existing World Cup References and Mock Files
We inspected the codebase and discovered the following files and references related to the World Cup:
* **Asset Files**:
  * `app/src/main/assets/worldcup_2026.json` (lines 1-729): A static JSON file containing the mock schedule for the 2026 World Cup. It contains an array of matches under the `"matches"` key, with fields: `round`, `date`, `time`, `team1`, `team2`, `group`, and `ground`.
  * `app/src/main/assets/worldcup_context.txt` (lines 1-16): Context instructions for the Gemini agent in Spanish and English, specifying how to call the `getWorldCupMatchesForDate` function tool.
* **Source Code**:
  * `app/src/main/java/com/mateocuello/alarmai/data/repository/WorldCupRepository.kt`: Defines the `WorldCupMatch` data class and `WorldCupRepository` class. It contains parsing logic (`parseMatches`), date normalization (`normalizeDateString`), and methods to fetch matches for a date (`getMatchesForDate`), format summaries (`getTodayMatchesSummary`), and search by team (`getMatchesByTeam`).
  * `app/src/main/java/com/mateocuello/alarmai/data/repository/GeminiAgentManager.kt`: Integrates `WorldCupRepository` within the Gemini function calling flow (methods `getWorldCupMatchesForDate` and `getWorldCupMatchesByTeam`).
  * `app/src/main/java/com/mateocuello/alarmai/service/PrefetchWorker.kt`: Calls `WorldCupRepository.getTodayMatchesSummary` to fetch and cache today's matches as part of the morning briefing prefetch.
* **Unit Tests**:
  * `app/src/test/java/com/mateocuello/alarmai/data/repository/WorldCupRepositoryTest.kt`: Tests match parsing using a mock JSON string and also tests parsing using the real `worldcup_2026.json` asset file.
  * `app/src/test/java/com/mateocuello/alarmai/data/repository/GeminiAgentManagerTest.kt`: Verifies the Gemini session initialization and tool serialization/deserialization.

---

## 2. Review of Codebase Integration Flow

### A. Fixture Loading
Currently, matches are loaded from the local asset `worldcup_2026.json`:
```kotlin
val jsonString = context.assets.open("worldcup_2026.json").use { inputStream ->
    BufferedReader(InputStreamReader(inputStream)).use { reader ->
        reader.readText()
    }
}
```
The file contents are parsed in `parseMatches(jsonString, dateString)` or `parseAllMatches(jsonString)` using a custom regex parser `Regex("""\{([^}]+)}""")` which finds JSON objects and extracts fields via `parseJsonField`.

### B. Fixture Formatting
The loaded fixtures are formatted into summaries via:
* `getTodayMatchesSummary`:
  ```
  FIFA World Cup 2026 Matches for <date>:
  - <round> (<group>): <team1> vs <team2> at <time> in <ground>
  ```
* `getMatchesByTeam`:
  ```
  FIFA World Cup 2026 matches for <teamName>:
  - <date>: <round> (<group>): <team1> vs <team2> at <time> in <ground>
  ```

### C. Testing
* `WorldCupRepositoryTest` checks regex parsing against `sampleJson` and asserts on the real asset file `worldcup_2026.json` for 2026-06-12 (expecting Canada vs Bosnia and USA vs Paraguay).
* `GeminiAgentManagerTest` mocks the `AssetManager` to return dummy data for `worldcup_context.txt` and tests agent serialization/deserialization with mock tool calls.

---

## 3. Official FIFA Matches API Integration Proposal

### A. Suggested URL Pattern
The official FIFA API endpoint for matches is hosted at:
`https://api.fifa.com/api/v3/calendar/matches`

To query official matches for the FIFA World Cup, use the competition ID parameter:
```
https://api.fifa.com/api/v3/calendar/matches?idCompetition=17
```
Optionally, to fetch matches for the specific tournament season (e.g. 2026 World Cup) and control count:
```
https://api.fifa.com/api/v3/calendar/matches?idCompetition=17&idSeason=<seasonId>&count=500
```
*(If the season ID for 2026 is unknown or dynamic, querying by `idCompetition=17` and filtering by year/date in the client is the most robust approach).*

### B. Expected Response Schema
The API returns a JSON object containing a `Results` array of match objects. A simplified schema representation:
```json
{
  "Results": [
    {
      "IdMatch": "400258554",
      "Date": "2026-06-11T19:00:00Z",
      "MatchDay": "1",
      "Home": {
        "ShortClubName": "MEX",
        "TeamName": [
          { "Description": "Mexico", "Locale": "en-GB" },
          { "Description": "México", "Locale": "es-ES" }
        ]
      },
      "Away": {
        "ShortClubName": "RSA",
        "TeamName": [
          { "Description": "South Africa", "Locale": "en-GB" },
          { "Description": "Sudáfrica", "Locale": "es-ES" }
        ]
      },
      "GroupName": [
        { "Description": "Group A", "Locale": "en-GB" }
      ],
      "StageName": [
        { "Description": "Group Stage", "Locale": "en-GB" }
      ],
      "Stadium": {
        "City": [
          { "Description": "Mexico City", "Locale": "en-GB" }
        ]
      }
    }
  ]
}
```

### C. Mapping Strategy to `WorldCupMatch`
The response fields should be mapped to the properties of the `WorldCupMatch` class as follows:

| `WorldCupMatch` Field | API Source Field / Mapping Logic | Example Value |
| --- | --- | --- |
| **`round`** | `"Matchday " + MatchDay` (or fallback to localized `StageName` description) | `"Matchday 1"` |
| **`date`** | Extract date prefix from `"Date"`: `Date.substring(0, 10)` | `"2026-06-11"` |
| **`time`** | Format timestamp or extract hour/minute and offset | `"19:00 UTC"` |
| **`team1`** | Find translation in `Home.TeamName` matching system language, or fallback to first description | `"Mexico"` |
| **`team2`** | Find translation in `Away.TeamName` matching system language, or fallback to first description | `"South Africa"` |
| **`group`** | Find translation in `GroupName` | `"Group A"` |
| **`ground`** | Find translation in `Stadium.City` | `"Mexico City"` |

---

## 4. Implementation Guidelines
1. **Network Library**: Use `OkHttpClient` to execute the asynchronous HTTP GET request.
2. **JSON Parsing**: Parse the JSON using `org.json.JSONObject` (standard in Android) or Gson, checking the `"Results"` array.
3. **Error Handling**: Implement fallback loading from the local asset `worldcup_2026.json` if the network query fails.
4. **Mocking in Tests**: Update `WorldCupRepositoryTest` and `GeminiAgentManagerTest` to use a mocked `OkHttpClient` or mock web server/response to verify integration.
