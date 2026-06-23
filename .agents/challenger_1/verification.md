# Verification Report - Dynamic World Cup Repository Stress Testing

This report details the empirical and adversarial verification of `WorldCupRepository` under normal, edge-case, and adversarial conditions.

---

## 1. Test Suite Summary
All unit tests and stress tests compile and pass successfully.

- **Command executed**: `.\gradlew test`
- **Build Status**: `BUILD SUCCESSFUL`
- **Total Tests Run**: 14 tests in the repository test classes:
  - `WorldCupRepositoryTest`: 5 tests (all passed)
  - `WorldCupRepositoryStressTest`: 9 tests (all passed)

### Test Result Execution XML Data (Debug Unit Test)
The stress test run results (`app/build/test-results/testDebugUnitTest/TEST-com.mateocuello.alarmai.data.repository.WorldCupRepositoryStressTest.xml`):
```xml
<testsuite name="com.mateocuello.alarmai.data.repository.WorldCupRepositoryStressTest" tests="9" skipped="0" failures="0" errors="0" ...>
  <testcase name="testHTTP_500_fallsBackToAsset" classname="com.mateocuello.alarmai.data.repository.WorldCupRepositoryStressTest" time="0.121"/>
  <testcase name="testMissingResultsKey_fallsBackToAsset" classname="com.mateocuello.alarmai.data.repository.WorldCupRepositoryStressTest" time="0.03"/>
  <testcase name="testHTTP_404_fallsBackToAsset" classname="com.mateocuello.alarmai.data.repository.WorldCupRepositoryStressTest" time="0.002"/>
  <testcase name="testEmptyResults_fallsBackToAsset" classname="com.mateocuello.alarmai.data.repository.WorldCupRepositoryStressTest" time="0.003"/>
  <testcase name="testNetworkTimeout_fallsBackToAsset" classname="com.mateocuello.alarmai.data.repository.WorldCupRepositoryStressTest" time="0.007"/>
  <testcase name="testLocaleFallbacks" classname="com.mateocuello.alarmai.data.repository.WorldCupRepositoryStressTest" time="0.002"/>
  <testcase name="testCorruptMatchesInResultsArray" classname="com.mateocuello.alarmai.data.repository.WorldCupRepositoryStressTest" time="0.004"/>
  <testcase name="testInvalidJsonSyntax_fallsBackToAsset" classname="com.mateocuello.alarmai.data.repository.WorldCupRepositoryStressTest" time="0.005"/>
  <testcase name="testNullKeysAndDefaultValuesInAPI" classname="com.mateocuello.alarmai.data.repository.WorldCupRepositoryStressTest" time="0.002"/>
</testsuite>
```

---

## 2. Robustness & Stress-Testing Script
We created a new test suite file `app/src/test/java/com/mateocuello/alarmai/data/repository/WorldCupRepositoryStressTest.kt` targeting the edge-case responses and fallback triggers. The following behaviors were verified:

### A. Fallback Triggers (Asset `worldcup_2026.json` Fallback)
The fallback mechanism is successfully triggered under the following scenarios:
1. **Empty `"Results"` array**: Tested by returning `{"Results": []}`. Correctly falls back to asset.
2. **Missing `"Results"` key**: Tested by returning `{}`. Correctly falls back to asset.
3. **HTTP 404 Status Code**: Tested by making the stub client return `isSuccessful = false` with code 404. Correctly falls back to asset.
4. **HTTP 500 Status Code**: Tested by returning `isSuccessful = false` with code 500. Correctly falls back to asset.
5. **Network Timeout / Connection Failure**: Tested by having the stub client throw an `IOException("Simulated Network Timeout")`. Correctly falls back to asset.
6. **Invalid JSON syntax**: Tested by returning `{invalid_json`. Correctly falls back to asset.

### B. Payload Structure Robustness
1. **Null Keys and Default Values**:
   - `MatchDay = null`
   - `Home = null`
   - `Away.TeamName = []`
   - `GroupName = null`
   - `Stadium.CityName = null` (with valid `Stadium.City`)
   *Result*: The parser is resilient. It successfully extracts `round` from `StageName`, falls back to `City` when `CityName` is missing, inserts empty strings for missing teams, and records `group` as `null` without throwing a crashing exception.
2. **Locale Fallbacks**:
   - Tested translation arrays that omit English (`"en"`) but contain other locales (e.g. `fr-FR`, `pt-BR`).
   *Result*: The repository successfully falls back to the first available translation in the array, avoiding empty outputs.

---

## 3. Adversarial Review

### Challenge Summary
- **Overall risk assessment**: MEDIUM

### Identified Weaknesses & Vulnerabilities

#### 1. [Medium Risk] Partial Parsing Failure (Halts parsing, skips fallback)
- **Assumption Challenged**: "If a JSON parsing exception occurs during results processing, the system will fall back to assets."
- **Attack Scenario**: The FIFA API returns a payload where the first match is completely valid, but the second match object is corrupted (e.g., instead of an object, it is a raw String, or one of its child objects is a raw type).
- **Blast Radius**: The `parseFifaMatchesJson` method catches the `JSONException` outside the parsing loop, meaning it stops parsing immediately and returns the list of matches parsed *before* the corruption occurred. Because this returned list is not empty, `fetchAllMatches` accepts it and **fails to trigger the fallback asset**. Consequently, the user is presented with an incomplete and potentially incorrect match fixture list (e.g., only 1 match instead of the full tournament).
- **Mitigation**: Wrap the parsing of each individual match inside the `for` loop with its own `try-catch` block. This allows the parser to discard the single corrupted match and continue parsing subsequent valid matches. Alternatively, discard the entire dynamic parse list and trigger the fallback asset if any item in the API payload is corrupt.

#### 2. [Low Risk] Regex-based Fallback Asset Parser
- **Assumption Challenged**: "The local asset `worldcup_2026.json` will always be simple enough to parse via regex."
- **Attack Scenario**: If any nested objects (e.g., a nested JSON object for detailed venue structure or team stats) are added to `worldcup_2026.json` in the future, the regex pattern `\{([^}]+)}` will prematurely match up to the first closing brace `}` inside that nested object, producing invalid and malformed match bodies.
- **Blast Radius**: The fallback parsing will fail or yield corrupted fields.
- **Mitigation**: Parse the local fallback asset using the `org.json` parser (`JSONObject` and `JSONArray`) rather than using regex, ensuring full JSON spec conformance.

#### 3. [Low Risk] Non-Standard Date Strings
- **Assumption Challenged**: "Date strings from the API are always `yyyy-MM-ddTHH:mm:ssZ`."
- **Attack Scenario**: If the API format deviates slightly (e.g. missing leading zeros for hours like `2026-06-11T9:00:00Z`), the length check `dateRaw.length >= 19` fails, skipping time extraction and returning the raw string as the date, which will subsequently fail to match normalized queries.
- **Blast Radius**: Matches scheduled for that day are not matched or display incorrectly.
- **Mitigation**: Use formal ISO 8601 parsing or regular expressions to parse date and time components.
