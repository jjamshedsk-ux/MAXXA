# MAXXA AI Voice Assistant — V24 source update

## Implemented in this pass
- Text-to-Speech initialization is now tracked explicitly. If a response arrives before TTS is ready, one pending utterance is queued and played when initialization succeeds instead of silently dropping the speech.
- TTS initialization failure completes the pending callback cleanly so the Voice UI does not remain stuck in Speaking.
- Speech output requests transient assistant audio focus and releases it after completion, error, stop, or interruption. Android retains control of Bluetooth/headset routing; the app does not force a device route.
- TTS progress handling now covers start, completion, error, and stop/interruption callbacks.
- Unique utterance IDs prevent callback collisions between rapid previews and replies.
- Stop Voice clears queued speech, stops current TTS, and abandons audio focus.
- Speech recognition requests partial hypotheses, but only final results are submitted to Gemini. This avoids sending unfinished speech as a chat prompt.
- Existing Hindi/English locale selection, Hinglish auto-detection preference, permission and recognizer error messaging, voice request cancellation, and voice-stage state machine are retained.

## Deliberate platform boundaries / remaining device-dependent validation
- Hindi/Hinglish recognition quality depends on the installed Android speech recognition service and language packs; app code cannot guarantee identical results across devices.
- Bluetooth/headset routing is handled by Android audio policy and the connected device. Physical route testing is required.
- The assistant remains tap-to-talk / foreground voice interaction. Always-on background microphone listening is not silently enabled; it requires a separately consented foreground-service UX, notification, and device-specific validation.
- Anime voice packs are pitch/rate presets using the installed TTS engine, not cloned actor/character voices.
- Kotlin/Compose compilation, physical microphone/TTS tests, interruption tests, and Bluetooth tests were not available in this environment.
