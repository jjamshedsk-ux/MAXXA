# Installed App Launcher — V27 source completion pass

## Included source changes
- Preserves PackageManager launcher-activity discovery and Android package-visibility `<queries><intent>` declaration, without requesting broad `QUERY_ALL_PACKAGES` access.
- Adds Devanagari/Hindi spoken aliases for YouTube, Instagram, WhatsApp, Facebook, Free Fire/Free Fire MAX, Chrome and Spotify.
- Normalizes aliases through the same Unicode normalization path used for recognized speech.
- Keeps typo-tolerant token and whole-name scoring, confidence threshold and ambiguity margin.
- Keeps launch restricted to exported, enabled launcher activities and uses PackageManager-derived package/activity targets only.
- Existing Installed Apps page refreshes the visible launcher-app list and provides manual search/tap launch.

## Verification boundary
This is a source-level improvement. Kotlin/Gradle compilation and actual Hindi/Hinglish speech-recognition behavior must be tested on the target Android devices. Speech-to-text output depends on the selected Android recognition service and language settings; no source matcher can guarantee every pronunciation or transcription.
