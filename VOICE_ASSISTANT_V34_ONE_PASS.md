# Voice Assistant V34 – one-pass source update

This update addresses the seven Voice Assistant source areas without including Hey MAXXA background wake-word implementation:

1. Hinglish recognition: recognition is not hard-pinned to Hindi for the Hindi/Hinglish mode; language preference and prompt remain set so the installed Android recognizer can handle code-switching. Actual language quality depends on the device speech service.
2. Voice interruption: starting a new listening session stops TTS; Stop Listening invalidates the active recognition session so late callbacks cannot submit stale text.
3. TTS voice selection/quality: retained the 12 character-inspired pitch/rate presets and language fallback. These are device TTS presets, not actor recordings or cloned voices; output quality depends on installed TTS voices.
4. Audio focus: retained transient assistant speech focus and release on completion/error/interruption; focus-loss handling stops active speech.
5. State/race handling: recognition session IDs and utterance IDs prevent stale completion callbacks from affecting newer sessions.
6. Permission recovery: RECORD_AUDIO is checked immediately before recognizer start and returns a clear permission-specific message; existing Compose permission launcher remains in place.
7. Lifecycle: speech capture and TTS are cancelled on Activity pause; recognizer is recreated on resume when available; resources are released on destroy.

## Verification limits

Source-level checks can confirm the intended branches and ZIP integrity. Android compilation and real-device microphone/TTS/audio-focus behaviour have not been verified here. The actual wake-word/background listening feature remains a separate MAXXA module.
