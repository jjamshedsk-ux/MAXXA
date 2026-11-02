# MAXXA AI Voice Assistant — Consolidated Source Pass

## Updated
- Hindi, English, and Hindi/Hinglish recognition prompts.
- SpeechRecognizer error mapping for network, timeout, no-match, microphone permission, busy service, and service errors.
- Defensive handling when speech recognition is unavailable or cannot start.
- Voice errors now remain status messages instead of being submitted as user chat text.
- Text-to-Speech locale fallback if a requested voice is unavailable.
- Stop Listening stops pending recognition and speech output.
- Activity teardown cancels recognition and releases speech resources.

## Device-dependent checks still required
- Confirm Google/Android speech recognition service is installed and enabled.
- Grant microphone permission and test Hindi/Hinglish/English recognition on-device.
- Confirm installed TTS engine has Hindi and English voices; fallback is English if Hindi is unavailable.
- Verify network-dependent recognition behavior, interruptions, Bluetooth/headset routing, and screen lifecycle on the target phone.
- Compile the Android app and test on a physical device.

## Final source review fixes (2026-10-31)
- Invalid backend URL saves now clear the previously saved endpoint and encrypted access token instead of leaving an old backend silently active.
- Reset Settings now also clears the in-memory backend token fields and connection-check state, matching the cleared preferences.
- These are source-level state/security consistency fixes; Android compilation and device validation remain intentionally pending.

## Voice interaction source pass — 31 October 2026
- Voice-submitted utterances continue through the existing Gemini streaming backend and are automatically submitted without requiring the user to press Send.
- Voice-submitted Gemini answers are now always sent to Android TextToSpeech (independent of the optional setting used for typed-chat replies).
- Added a TTS utterance progress callback so the Voice screen can return from “Speaking…” to “Ready when you are” after playback completes or errors.
- The Voice screen now distinguishes Listening, Processing and Speaking in its primary control label; the waveform is active during all three stages.
- The voice stop control is available during listening and speaking and stops the recognizer/TTS engine.
- Improved the Hinglish recognition prompt while retaining Hindi/English locale selection and existing permission/error messages.
- Updated TTS call sites for the completion callback; typed-chat speech and voice preview retain their optional behavior.

### Verification limits
- `python3 scripts/verify_source.py`: PASS.
- `node --check backend/app.js` and `node --check backend/server.js`: PASS.
- Kotlin/Compose compilation and actual recognizer/TTS execution were not possible in this environment (no Gradle wrapper/executable or Android SDK/device). Hinglish accuracy and UI pixel matching therefore remain runtime/device-dependent and are not certified.

## Consolidated voice assistant state and interaction pass — 31 October 2026
- Replaced the loosely coupled listening/speaking booleans with an explicit `Idle → Listening → Processing → Speaking` voice-stage state model; state drives mic availability, stage label, waveform activity and Stop Voice visibility.
- Recognized speech continues to enter the same chat thread and auto-submits to the streaming backend without requiring the Send button.
- TTS starts only after the Gemini stream has completed and the final assistant response is available. TTS completion/error returns the voice stage to Idle.
- Stop Voice cancels the recognizer, stops TTS, and requests cancellation of the voice coroutine; the blocking `HttpURLConnection` stream is not guaranteed to abort immediately, so backend-request cancellation is not certified.
- Hindi uses `hi-IN`, English uses `en-IN`; Hinglish uses the Hindi recognition locale with a Hinglish prompt. Actual code-switching accuracy depends on the Android speech service and installed language packs and cannot be guaranteed by app code alone.
- Speech errors and microphone permission denial retain user-readable messages. TTS language falls back to English (India) if the selected device voice is unavailable.
- Voice screen retains the reference contact-sheet's centered crimson microphone, character-art backdrop, waveform and stop action. This is a source-level visual alignment only, not a pixel-perfect screenshot comparison.
- Offline source-contract checks were extended for the state model, auto-submit, stream-before-TTS ordering, TTS completion, stop actions, locale settings and error paths.
- Still unverified: Kotlin/Compose compilation, real microphone recognition, device TTS output, backend cancellation, live Gemini request, and on-device visual matching. The environment has no Android SDK/Gradle/ADB/emulator.

## Voice assistant consolidated follow-up — 31 October 2026

Applied the requested five voice-flow tasks together:
- Kept the explicit `Idle`, `Listening`, `Processing`, and `Speaking` state model and tied the mic button, waveform, status, and Stop control to those states.
- Hinglish recognition no longer hard-pins the recognizer's `EXTRA_LANGUAGE` to Hindi. It supplies Hindi as the preferred locale while allowing the Android recognition service to auto-detect mixed Hindi/English speech. Actual code-switching quality remains speech-service/device dependent.
- Added a request cancellation controller that tracks the active streaming `HttpURLConnection`. Stop Voice now cancels the coroutine and disconnects the active streaming connection, in addition to cancelling speech recognition and stopping TTS.
- Voice TTS starts only after the streaming call has completed and the accumulated response is non-empty; the utterance completion/error callback returns the UI to Idle.
- Compared the bundled Voice reference panel: retained the crimson circular microphone treatment, artwork backdrop, waveform, status and prominent Stop action. This is a source-level visual alignment, not a pixel-perfect device screenshot certification.

Offline verification on this source tree:
- `python3 scripts/verify_source.py` — PASS, including added voice cancellation and Hinglish source-contract checks.
- `node --check backend/app.js` — PASS.
- `node --check backend/server.js` — PASS.

Not verified in this environment: Kotlin/Compose compilation, APK build, actual microphone recognition, TTS playback on a device, network cancellation behavior on Android, and Poco M3 runtime/UI screenshots. The source checker is not a substitute for those tests.
