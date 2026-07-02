# Original User Request

## Initial Request — 2026-06-23T02:51:29Z

Fix and harden the voice-to-text (STT) and text-to-speech (TTS) conversation loop in the AlarmAI Android app so users can interact with the AI assistant entirely hands-free after tapping "Dismiss & Talk". The user should be able to have a full multi-turn voice conversation without touching the screen. The only required touch is the initial "Dismiss & Talk" button.

Working directory: C:\Users\usuario\alarmai
Integrity mode: development

## Requirements

### R1. Reliable Speech-to-Text Recognition Loop

The `VoiceManager.startListening()` and `AlarmViewModel.startListeningForUser()` must work correctly through the full conversation lifecycle. Known critical issues to fix:

- `SystemClock.sleep(50)` on the main thread blocks the UI — replace with non-blocking delay
- `stopListening()` does not call `unmuteBeep()`, leaving audio streams permanently muted if called externally
- `stopSpeaking()` nullifies `ttsCompleteCallback` before TTS stops, creating a race condition
- No `SpeechRecognizer.isRecognitionAvailable()` check before attempting recognition
- After TTS completes, audio focus is abandoned then re-acquired 600ms later, creating a window for focus loss
- STT retry logic (max 5 errors with backoff) should gracefully recover and never leave the app stuck in an unresponsive state
- The `ERROR` state exists in `AlarmState` but is never used — it should be set when appropriate and the UI should handle it

### R2. Robust Voice Conversation State Machine

The `AlarmViewModel` state machine (RINGING → FETCHING_DATA → SPEAKING ↔ LISTENING ↔ THINKING → FINISHED) must handle all edge cases cleanly:

- Transitions between SPEAKING and LISTENING must be seamless — no dropped audio, no stuck states
- Error recovery should display user-friendly feedback and allow the user to continue the conversation
- The no-speech timeout (currently 2 min) should work reliably and re-engage the user
- Force close and goodbye detection must clean up all resources (TTS, STT, audio focus, muted streams)

### R3. Comprehensive Unit Tests for Voice Components

Create unit tests that verify the voice interaction logic works correctly. Tests should cover:

- `VoiceManager`: TTS initialization, speak/callback flow, startListening/result/error callbacks, mute/unmute lifecycle, audio focus management, shutdown cleanup
- `AlarmViewModel`: State machine transitions for the complete conversation flow, STT error retry logic with backoff, goodbye keyword detection (both English and Spanish), no-speech timeout behavior, manual mic restart
- All new tests must pass via `./gradlew testDebugUnitTest`

### R4. Safe Audio Stream Management

The aggressive muting of `STREAM_MUSIC`, `STREAM_SYSTEM`, and `STREAM_NOTIFICATION` must be made safe:

- Ensure `unmuteBeep()` is always called in all exit paths (normal completion, error, force close, app crash protection)
- Consider using `AudioManager.STREAM_VOICE_CALL` or an alternative approach that doesn't globally mute media streams
- Add a safety mechanism (e.g., `onCleared()` or `shutdown()` guarantee) so streams are never left muted

## Acceptance Criteria

### Voice Conversation Flow
- [ ] After "Dismiss & Talk", the full loop (FETCHING_DATA → SPEAKING → LISTENING → THINKING → SPEAKING → ...) completes without errors or stuck states
- [ ] The `ERROR` state is set when real errors occur and the UI displays a recovery option
- [ ] `stopListening()` properly unmutes audio streams in all code paths
- [ ] `stopSpeaking()` does not have a callback race condition
- [ ] No `SystemClock.sleep()` calls on the main thread
- [ ] `SpeechRecognizer.isRecognitionAvailable()` is checked before creating a recognizer
- [ ] Audio focus is maintained continuously during the SPEAKING → LISTENING transition (no gap)

### State Machine Robustness
- [ ] STT errors retry up to 5 times with increasing backoff, then transition to ERROR state with recovery UI
- [ ] Goodbye keywords work in both English and Spanish
- [ ] No-speech timeout correctly re-engages or finishes the session
- [ ] `forceClose()` and `onCleared()` guarantee all audio resources are released and streams unmuted

### Test Coverage
- [ ] At least 10 unit tests for `VoiceManager` covering speak, listen, error, mute/unmute, and shutdown
- [ ] At least 10 unit tests for `AlarmViewModel` covering all state transitions, retry logic, goodbye detection, and timeout
- [ ] All tests pass: `./gradlew testDebugUnitTest` exits with BUILD SUCCESSFUL
- [ ] Tests use Mockito to mock Android framework classes (`SpeechRecognizer`, `TextToSpeech`, `AudioManager`)

### Audio Safety
- [ ] `unmuteBeep()` is called in `stopListening()`, `forceClose()`, and `onCleared()`
- [ ] The `shutdown()` method is guaranteed to restore audio stream state
- [ ] No path exists where audio streams remain muted after the alarm conversation ends

### Build Verification
- [ ] The app compiles successfully: `./gradlew assembleDebug` exits with BUILD SUCCESSFUL
- [ ] All existing tests continue to pass alongside new tests

## Follow-up — 2026-06-23T19:55:26Z

Apply Android 16 (API 36) compatibility fixes to the AlarmAI Android app. The fixes address Google Play policy violations, foreground service type misuse, background activity launch restrictions, Direct Boot safety, and speech recognizer optimization.

Working directory: c:\Users\usuario\alarmai
Integrity mode: development

## Requirements

### R1. Manifest & Permission Cleanup
Remove the redundant `SCHEDULE_EXACT_ALARM` permission (keep only `USE_EXACT_ALARM`). Replace `FOREGROUND_SERVICE_MEDIA_PLAYBACK` with `FOREGROUND_SERVICE_SPECIAL_USE`. Change `AlarmService` foreground service type from `mediaPlayback` to `specialUse` and add the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property with value `alarm`.

### R2. AlarmService Foreground Type & Fallback Fix
Update `AlarmService.kt` to use `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` instead of `MEDIA_PLAYBACK`. Remove the invalid `startActivity()` fallback when full-screen intent permission is denied — the system's high-priority heads-up notification is sufficient.

### R3. Direct Boot Safety in PreAlarmReceiver
Guard the `WorkManager.getInstance(context).enqueue(...)` call in `PreAlarmReceiver` with a `UserManagerCompat.isUserUnlocked(context)` check. If the device is locked during Direct Boot, skip the prefetch and log a warning.

### R4. On-Device Speech Recognition Fallback
Update the `speechRecognizerFactory` in `VoiceManager.kt` to prefer `SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)` when available (API 31+), falling back to the standard network-based recognizer otherwise.

### R5. Redundant Runtime Check Cleanup (AlarmScheduler + MainActivity)
Remove or simplify the `canScheduleExactAlarms()` runtime check and settings redirect in both `AlarmScheduler.kt` and `MainActivity.kt`, since `USE_EXACT_ALARM` is auto-granted for alarm apps and does not require user action. The settings redirect UI flow for exact alarm permissions is unnecessary and confusing.

## Acceptance Criteria

### Build Integrity
- [ ] `.\gradlew compileDebugSources` completes with zero errors
- [ ] `.\gradlew testDebugUnitTest` passes all existing tests (39+) with zero failures

### Manifest Correctness
- [ ] `SCHEDULE_EXACT_ALARM` does NOT appear in AndroidManifest.xml
- [ ] `FOREGROUND_SERVICE_MEDIA_PLAYBACK` does NOT appear in AndroidManifest.xml
- [ ] `FOREGROUND_SERVICE_SPECIAL_USE` permission IS declared
- [ ] AlarmService declares `foregroundServiceType="specialUse"` with the alarm subtype property

### Code Correctness
- [ ] `AlarmService.kt` uses `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` constant
- [ ] `AlarmService.kt` does NOT call `startActivity()` as a fallback for denied full-screen intent
- [ ] `PreAlarmReceiver` checks `UserManagerCompat.isUserUnlocked()` before calling `WorkManager`
- [ ] `VoiceManager.kt` prefers on-device speech recognizer when available (API 31+)
- [ ] `AlarmScheduler.kt` and `MainActivity.kt` do NOT contain redundant `canScheduleExactAlarms()` checks or settings redirects for exact alarm permissions
- [ ] No regressions in existing functionality

### Security
- [ ] No API keys or sensitive credentials present in source code


## Follow-up — 2026-07-01T16:01:05Z

Implement a foreground location caching system in the AlarmAI Android app so the assistant can instantly access the latest known location for weather forecasts without blocking the morning alarm flow.

Working directory: c:\Users\usuario\alarmai
Integrity mode: benchmark

## Requirements

### R1. Foreground Location Tracking
The app must fetch and cache the user's location into `PreferencesManager` exclusively when the user opens the application (e.g. within MainActivity). It should not run continuous background services or WorkManager jobs specifically for location to save battery.

### R2. Smart Alarm Fallback and Refresh
- If a cached location is available, `AlarmViewModel` and `PrefetchWorker` must use it instantly.
- If no cached location exists, they must fall back to a live GPS fetch to ensure weather data is always retrieved.
- When the cached location is used, the system must trigger a live background location fetch to refresh the cache specifically *while* the Text-To-Speech (TTS) engine is reading the briefing, ensuring the user experiences zero delay.

## Acceptance Criteria

### Verification
- [ ] Code inspection confirms location is requested and saved to preferences in the UI lifecycle (e.g. `MainActivity`).
- [ ] `PrefetchWorker` and `AlarmViewModel` correctly implement the fallback logic (Cache -> Live GPS).
- [ ] A background coroutine is launched during or immediately before the TTS playback starts to refresh the location cache silently.
- [ ] The app builds successfully (`./gradlew assembleDebug`).

## Follow-up — 2026-07-01T16:03:15Z

Investigar y solucionar el problema de fallo instantáneo del Speech-to-Text (SST) al usar `SpeechRecognizer` en la aplicación AlarmAI, que ocurre tanto en la pantalla de bloqueo como con el dispositivo desbloqueado en Android 14.

Working directory: c:/Users/usuario/alarmai
Integrity mode: development

## Requirements

### R1. Diagnóstico del Error de SpeechRecognizer
El agente debe identificar la causa raíz del fallo instantáneo de `SpeechRecognizer` (que dispara la pantalla "No se pudo iniciar la entrada de voz"). 
*Información crucial proporcionada por el usuario:* El indicador de privacidad del micrófono (luz verde en la esquina superior derecha de la pantalla) **se enciende por un segundo** antes de que salte la pantalla de error. Esto significa que el permiso SÍ se otorga y el `SpeechRecognizer` llega a abrirse, pero luego se cancela o falla inmediatamente (posiblemente un error de nuestro lado, un timeout inmediato por configuración de audio focus, o una cancelación accidental en la lógica de la UI/ViewModel).

### R2. Implementación de una Solución Robusta y Fluida
El agente debe implementar la solución más fluida (seamless) para el usuario que arregle este error. La implementación debe asegurar que:
- El reconocimiento de voz inicie y mantenga la escucha de forma confiable, evitando cancelaciones prematuras.
- Se debe revisar la lógica de Focus de Audio y de ciclo de vida en `VoiceManager` y `AlarmViewModel` para evitar que el reconocimiento se interrumpa a sí mismo.
- No se debe comprometer la calidad ni el diseño de las funcionalidades existentes.

## Acceptance Criteria

### Solución del Crash / Fallo Instantáneo
- [ ] La aplicación ya no debe mostrar la pantalla de error "No se pudo iniciar la entrada de voz" de manera instantánea al intentar escuchar al usuario.
- [ ] El `SpeechRecognizer` debe mantenerse activo y captar el audio correctamente (indicado visualmente o en los logs por un cambio en el volumen RMS o por recibir resultados completos).
- [ ] La solución debe compilar y ejecutarse correctamente sin introducir excepciones no manejadas.
