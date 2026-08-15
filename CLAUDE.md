# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

AlarmAI is a single-module native Android app (Kotlin + Jetpack Compose, MVVM) that replaces a normal alarm with a spoken, conversational morning briefing: on dismiss it aggregates weather + news + calendar, sends them to Gemini, speaks the reply via TTS, then listens via STT in a loop.

`README.md` covers user-facing setup. This file is the architecture reference; when the two disagree, trust the source.

## Build / test commands

Windows shell uses `gradlew.bat`; the Bash tool can use `./gradlew`.

```powershell
.\gradlew.bat :app:assembleDebug            # build
.\gradlew.bat :app:installDebug             # build + install on connected device/emulator
.\gradlew.bat :app:testDebugUnitTest        # JVM unit tests (app/src/test)
.\gradlew.bat :app:connectedDebugAndroidTest # instrumented tests (app/src/androidTest, needs a device)
.\gradlew.bat :app:lintDebug                # Android lint
```

Configuration cache is on (`gradle.properties`). `lintDebug` needs network on first run to resolve `com.android.tools.lint:lint-gradle`; it fails under `--offline` with a configuration-cache write error, which is a missing-artifact problem, not a code problem.

Single test class or method:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.alarmai.app.receiver.AlarmTimeCalculatorTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.alarmai.app.receiver.AlarmTimeCalculatorTest.methodName"
```

Requires JDK 21 (`sourceCompatibility`/`jvmTarget` = 21), `compileSdk`/`targetSdk` 37, `minSdk` 26. `local.properties` (gitignored) supplies `sdk.dir`.

### API keys

`app/build.gradle.kts` reads a gitignored `.env` at the repo root at configuration time and injects `GEMINI_API_KEY` as a `BuildConfig` field. `PreferencesManager.getGeminiKey()` returns the SharedPreferences value if the user typed one in the settings UI, else falls back to `BuildConfig`. A blank Gemini key is not an error path — it silently selects **demo mode** (hardcoded canned briefing and keyword-matched replies in `GeminiAgentManager`), so a "working" build with no key still speaks.

There is no news API key. `NewsRepository` parses the public Google News RSS feed; the old `NEWS_API_KEY` plumbing was dead and has been removed.

`IntegrationTest.testRealGeminiApiCall` makes a live API call when the `GEMINI_API_KEY` *environment variable* is set, and self-skips via `Assume` otherwise.

## Architecture

Package root `com.alarmai.app`. There is **no DI framework**: collaborators are constructor parameters with real defaults, declared `@JvmOverloads constructor` (`AlarmViewModel`, `VoiceManager`, `GeminiAgentManager`) purely so unit tests can pass mocks. Keep that pattern when adding a dependency to those classes.

Component map:

| Area | Classes |
| --- | --- |
| UI | `MainActivity`/`MainViewModel` (settings, permissions, scheduling), `AlarmActivity`/`AlarmViewModel` (lockscreen wake-up, briefing, voice dialogue), `ui/theme` (`Color`/`Type`/`Theme` tokens + `Components.kt` shared widgets) |
| Scheduling | `AlarmScheduler`, `AlarmTimeCalculator`, `AlarmReceiver`, `PreAlarmReceiver`, `BootReceiver` |
| Background | `AlarmService` (ringtone foreground service), `PrefetchWorker` (WorkManager briefing prefetch) |
| Data | `PreferencesManager`, `LocationProvider`, `WeatherRepository`, `NewsRepository`, `CalendarRepository`, `VoiceManager`, `GeminiAgentManager`, `data/model` (`Alarm`, `GeminiModels`) |

### Two alarms per schedule

`AlarmScheduler.schedule()` sets **two** `AlarmManager` entries, and the mechanisms differ deliberately:

- **Main alarm** — `setAlarmClock()` with `REQUEST_CODE_MAIN` → `AlarmReceiver`. `setAlarmClock` (not `setExactAndAllowWhileIdle`) is what surfaces the system alarm icon and gets the least Doze throttling.
- **Pre-alarm** — 2 minutes earlier, `setExactAndAllowWhileIdle()` (with the API 31/32 `canScheduleExactAlarms()` fallback to `setAndAllowWhileIdle`) with `REQUEST_CODE_PRE` → `PreAlarmReceiver`. If the alarm is under 2 minutes away, prefetch fires immediately instead.

Every request code lives in `AlarmScheduler`'s companion (`REQUEST_CODE_MAIN` 1001, `_PRE` 1002, `_SHOW` 1003, `_TEST` 1004; `AlarmService` privately owns 1005/1006 for its full-screen and Stop intents). **Never reuse one.** `Intent.filterEquals()` ignores extras, so two `PendingIntent`s with the same request code and a structurally identical `Intent` are the *same* PendingIntent — that is how the "Test Alarm (Fires in 5 Seconds)" dev button used to overwrite and permanently disable the user's real alarm. The test button therefore uses both its own request code **and** a distinct action (`AlarmScheduler.ACTION_TEST_ALARM`); either alone is insufficient.

`AlarmReceiver` starts `AlarmService` (ringtone) and, for a repeating alarm, immediately reschedules with `fromReceiver = true` so `AlarmTimeCalculator` skips today; a one-shot alarm is flipped inactive. A firing carrying `is_test` rings but returns before any of that, so a test never mutates stored alarm state. The full-screen UI is reached two ways: the `setAlarmClock` show-intent, and `AlarmService`'s full-screen-intent notification.

`AlarmService` returns `START_NOT_STICKY` — under `START_STICKY` the system would restart it with a null intent after a low-memory kill and ring at an arbitrary later time. It also posts a Stop action on the notification (`ACTION_STOP` → `stopSelf()`) and auto-stops after `MAX_RING_MS` (10 minutes), since `setOngoing(true)` otherwise makes the ringing undismissable from the shade.

### Prefetch and the briefing cache

The alarm cannot afford a cold Gemini call — `PreAlarmReceiver` does no work itself (10s receiver limit → ANR) and only enqueues `PrefetchWorker` via WorkManager, which bypasses Doze network restrictions. Never move network work into a receiver.

Two things about that enqueue are load-bearing:

- It is **expedited** (`setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)`). Plain work is deferrable, and at T−2min the device is deep in Doze, so the prefetch routinely never ran before the alarm. Expedited work below API 31 runs as a short foreground service, which means `PrefetchWorker.getForegroundInfo()` is **mandatory** — without it `setExpedited` throws, and `minSdk` here is 26. That override needs the `FOREGROUND_SERVICE_DATA_SYNC` permission.
- It is `enqueueUniqueWork(PREFETCH_WORK_NAME, KEEP, …)`. `AlarmScheduler.schedule()` runs on *every* settings toggle, so plain `enqueue()` let a few rapid edits fan out into concurrent **billed** Gemini calls.

`PrefetchWorker` fetches weather/news/calendar in parallel, calls `GeminiAgentManager.startSessionDetailed()`, and stores `(briefing, prompt, timestamp)` via `savePrefetchedBriefing`. `startSessionDetailed` returns a `BriefingResult(prompt, text)` carrying the prompt that was *actually sent* — the worker used to rebuild its own copy, which diverged, so `reconstructSession` replayed a user turn that never happened. Don't reintroduce a second prompt builder. It skips outright if a briefing under 30 minutes old is already cached, and returns `Result.retry()` (up to `MAX_ATTEMPTS`) so a transient blip doesn't kill the morning.

On dismiss, `AlarmViewModel.dismissAndTalk()` uses the cache only if it is under **30 minutes** old, calls `reconstructSession()` to replay that prompt/response pair as the Gemini history, clears the cache, and speaks immediately; otherwise it fetches everything on demand. It short-circuits when a `sessionJob` is already active — rotation and `retry()` can both re-enter it.

**Repositories return a blank string when data is unavailable, never an error message.** `WeatherRepository`, `NewsRepository` and `CalendarRepository` log the failure and return `""`; `startSessionDetailed` drops blank sections when building the prompt. Returning the exception text made the briefing say "Today's weather: Failed to retrieve weather, SocketTimeoutException" out loud. Keep that contract when adding a data source.

`BootReceiver` reschedules on `BOOT_COMPLETED`/`LOCKED_BOOT_COMPLETED` **and** on `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` — an app update clears every pending `AlarmManager` entry, and a clock/timezone change leaves a scheduled alarm on the wrong wall time.

`PreferencesManager` always resolves to `createDeviceProtectedStorageContext()`, so every preference is readable during Direct Boot — that is what lets `BootReceiver` (`directBootAware`, listening for both `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED`) reschedule before first unlock. `PreAlarmReceiver` still bails out when `UserManagerCompat.isUserUnlocked` is false, since WorkManager needs the unlocked user.

Every receiver, `AlarmActivity`, and `AlarmService` are declared `directBootAware="true"` in the manifest; any new boot-path component must be too. `AlarmService` is a `specialUse` foreground service with subtype `alarm`, playing the ringtone through `MediaPlayer` on `USAGE_ALARM`.

### Voice loop

`AlarmViewModel` drives a state machine (`AlarmState`: RINGING → FETCHING_DATA → SPEAKING → LISTENING → THINKING, plus ERROR/FINISHED) over `VoiceManager`. TTS completion → 600ms delay → start listening; that delay job is tracked in `postSpeechJob` and cancelled by any user input, because an orphaned copy used to reopen the mic mid-`THINKING` and feed the agent's own voice back in. STT errors retry up to `MAX_STT_ERRORS` (5) with escalating backoff and then land in `ERROR`; an unrecoverable error ("not available", permission) skips straight there. The 2-minute silence nudge re-prompts at most `MAX_NO_SPEECH_NUDGES` (2) times before closing the session. Goodbye keywords (language-specific list) end it too.

`AlarmActivity` is `singleInstance`, so it overrides `onNewIntent` to `setIntent` and `resetForNewAlarm()` — otherwise a second alarm brings a stale `FINISHED`/`ERROR` screen forward while ringing. The direct-invoke branch in `onCreate` is guarded by `savedInstanceState == null`.

`VoiceManager` details that matter:
- `SpeechRecognizer` must be created, started, and destroyed on the main thread — all of that is posted to `Handler(context.mainLooper)`. A fresh recognizer is created per listen (reused instances corrupt). On API 33+ it prefers `createOnDeviceSpeechRecognizer` when available.
- It mutes only `BEEP_STREAMS` (`STREAM_SYSTEM`/`STREAM_NOTIFICATION`) to suppress the recognizer beep — **not `STREAM_MUSIC`**, which is where TTS plays; muting it swallowed the first syllables of every reply. Any new early-return path must call `unmuteBeep()` or the device stays muted.
- The mute is persisted (`PreferencesManager.saveBeepMuted`), because a process kill mid-listen used to leave the device silently muted forever with no in-process path back. `VoiceManager.recoverStaleMute(context)` — a companion function that deliberately does *not* spin up a TTS engine — runs from `MainActivity.onCreate` to self-heal.
- Audio focus is `AUDIOFOCUS_GAIN_TRANSIENT` with `USAGE_ASSISTANT`; *transient* loss is deliberately ignored to keep STT alive, and only permanent `AUDIOFOCUS_LOSS` fires `onSessionInterrupted`.

### Gemini client

`GeminiAgentManager` talks to `generativelanguage.googleapis.com/v1beta/.../generateContent` with **hand-rolled OkHttp + org.json** — there is no Google GenAI SDK dependency in use, so request/response shape (`Content`/`TextPart`/`FunctionCallPart`/`FunctionResponsePart`, `systemInstruction`, `tools`) is built and parsed manually in `contentToJson`/`parseResponseContent`. The Google Search grounding tool must be the camelCase key `"googleSearch": {}`. Unknown response part types are skipped rather than failing. The API key goes in an `x-goog-api-key` header, never the query string — in the URL it leaked into exception messages that get logged. The `OkHttpClient` is a single lazy `companion object` instance with explicit connect/read/call timeouts; don't construct one per request.

`app/build.gradle.kts` depends on `okhttp` core explicitly. It looks redundant next to `okhttp-logging`, but without it OkHttp 4 is on the classpath only because the logging interceptor out-votes the okhttp 3.x that Retrofit 2.9.0 pulls in — and this client uses the OkHttp 4 Kotlin extensions.

**History hygiene is the thing to be careful with.** Only text-bearing turns are committed to `session.history`; a response containing just a `functionCall` is dropped. `googleSearch` is server-side grounding, so a client-side function call can never be answered, and an unanswered one in history makes every subsequent request fail `400 INVALID_ARGUMENT` — unrecoverable for the rest of the session. `sendMessageInternal` likewise rolls the user turn back out of history when the request throws.

Model IDs live in one place: `data/model/GeminiModels.kt` (`CHAIN`, `DEFAULT` = `gemini-3.7-flash`, `isKnown`, `displayName`). It sits in `data.model` so both `data.local` and the UI can read it without depending on `data.repository`. `PreferencesManager.getGeminiModel()` coerces anything not in `CHAIN` back to `DEFAULT`, and settings has a picker driven by the same list. `modelChainFor()` puts the *configured* model at the head followed by the rest, so a call walks forward only on fallback-worthy errors — 429/503/404/quota/exhausted/`NOT_FOUND`; anything else propagates. 404 counts because a bad model id would otherwise hard-fail the first briefing. `session.modelName` is deliberately not reassigned on fallback: a transient 503 must not permanently downgrade the conversation.

### Compose UI

Both activities call `enableEdgeToEdge()` and the screen roots use `Modifier.safeDrawingPadding()` (plus `.imePadding()` wherever there is a text field). At `targetSdk` 37 edge-to-edge is enforced and cannot be opted out of, so anything drawn without inset padding lands under the status bar or the gesture nav bar. `Theme.kt` intentionally sets no `window.statusBarColor` — it is a deprecated no-op from API 35 and `enableEdgeToEdge()` owns the system bar treatment.

The visual language is **flat minimalist dark, and deliberately has no gradients** — replacing a flat fill with a `Brush` anywhere is a regression, not a flourish. Three rules keep it coherent:

- **Every colour token in `Color.kt` is opaque.** Do not reintroduce `Color.White.copy(alpha = …)` for surfaces or borders. The old screens had nine distinct white-alpha values for surfaces alone, and because they composited against whatever was behind them, the same card rendered differently on the two screens. Depth is a step up the neutral ramp (`Ink` → `Surface1` → `Surface2` → `Surface3`) separated by a 1dp `Line`; there are no shadows or elevation anywhere.
- **One accent.** `Accent` marks the primary action and the active state, nothing decorative. `secondary` and `tertiary` in the scheme resolve to neutrals on purpose, so a Material component that reaches for them comes out grey instead of inventing a hue. `AccentSurface`/`AccentLine` are the accent pre-composited over `Ink`, which is what keeps selected fills opaque.
- **Build from `Components.kt`**, which replaced the old `Glassmorphism.kt` (whose `glassmorphicCard()` was a no-op returning the receiver unchanged — the "glass" was really a white-alpha fill plus border, hand-repeated at five call sites that had already drifted apart). `SectionCard`, `SelectablePill`, `PrimaryButton`, `SecondaryButton`, `FieldLabel`, `FieldHint` and `appTextFieldColors()` are the vocabulary; add to that file rather than restyling inline.

`Type.kt` defines every style the app uses, including `displayLarge`/`displayMedium` for the clocks, so no screen has to `.copy(fontSize = …)` a style and leave a mismatched `lineHeight` behind. Weight tops out at SemiBold — hierarchy comes from size and colour. `AlarmAITheme` takes only `content`: the light scheme was unreachable (`darkTheme` defaulted true and no caller ever passed false) and dynamic colour is off on purpose, since letting the system recolour the app would undo the neutral palette.

The `day_toggle_$dayInt` test tags and the `selected` semantics on those pills are asserted by `MainActivityUiTest` — preserve them when restyling.

### Localization and prompt constraints

Language is a preference (`"es"` default, else `"en"`), not the device locale, and it selects the TTS locale, the STT language tag, the news RSS feed, and the system instruction. **All user-facing strings are hardcoded in Kotlin** as `if (language == "es") … else …` branches — `strings.xml` holds only the app name. Follow that when adding text.

Because output is spoken, the system instruction and any prompt work must keep: under 120 words, no markdown/asterisks/bullets/emoji, and end on an open-ended question.

## Testing conventions

Unit tests are plain JVM JUnit 4 + Mockito (`mockito-core` 5.x, `mockito-kotlin`) with `testOptions.unitTests.isReturnDefaultValues = true`. **No Robolectric** — Android framework interaction is faked with `Mockito.mockStatic` (e.g. `android.util.Log`, `SpeechRecognizer`) and `mockConstruction` (`Handler`, `Intent`, `Bundle`, `AudioFocusRequest.Builder`), which is why `VoiceManagerTest` has a large `@Before`. Anything you add that calls the framework in a constructor needs the same treatment or an injectable factory.

`PreferencesManagerTest` exists in both `app/src/test` and `app/src/androidTest`; the instrumented copy exercises real SharedPreferences.
