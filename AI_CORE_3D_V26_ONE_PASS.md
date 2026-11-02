# MAXXA AI Core 3D — V26 one-pass update

- Added a state-reactive energy envelope for Listening, Processing and Speaking, with distinct pulse cadence and intensity.
- Connected visual energy to the existing `voiceStage` state supplied by the Voice page; no second microphone recorder is created.
- Kept the optional normalized `voiceLevel` input for a future genuine audio-amplitude source.
- Clarified an important platform limitation: Android `SpeechRecognizer` does not provide raw microphone PCM amplitude to this UI, so Listening uses a responsive state animation, not a claim of measured mic loudness.
- Reduced orbit nodes and sphere mesh segments on smaller render surfaces to lower Canvas work on entry-level devices.
- Preserved tap activation, button role, state-aware accessibility description, pressed feedback, and existing seven-theme color inputs.
- Visual remains a Compose Canvas 3D-style projection, not a real-time 3D engine scene.

## Validation
Source-level changes applied. Android compilation and physical-device performance/accessibility validation have not been run; those are user-side checks.
