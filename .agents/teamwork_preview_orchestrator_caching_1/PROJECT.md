# Project: AlarmAI Location Caching

## Architecture
- **PreferencesManager**: Stores latitude/longitude cache. We will add `hasCachedLocation(): Boolean` to check for the presence of cache keys.
- **MainActivity**: Triggers location fetch using `LocationProvider` and saves to `PreferencesManager` when the app is opened (in `onResume` and upon permission grant).
- **PrefetchWorker**: Uses cached location from `PreferencesManager` if available. Otherwise, falls back to live GPS fetch via `LocationProvider`.
- **AlarmViewModel**: Uses cached location instantly if available, else falls back to live GPS fetch. Launches background coroutine to refresh the cache silently when TTS starts if cached location was used.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Exploration & Analysis | Inspect files, propose integration details | none | DONE |
| 2 | Implementation | Implement location cache, MainActivity foreground tracking, AlarmViewModel / PrefetchWorker fallback and silent refresh | M1 | IN_PROGRESS |
| 3 | Verification & Auditing | Run unit tests, challenger checks, and forensic auditor | M2 | PLANNED |

## Interface Contracts
### PreferencesManager ↔ App Components
- `hasCachedLocation(): Boolean` -> Returns true if `latitude` and `longitude` are stored.
- `getLocation(): Pair<Double, Double>` -> Returns cached location (or default if not present).
- `saveLocation(lat: Double, lon: Double)` -> Saves coordinates to shared preferences.
