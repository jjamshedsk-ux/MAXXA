# Installed App Launcher — V28 source update

## Changes in this pass
- Expanded Hindi/Devanagari command particles and app aliases for common Android apps (Maps, Gmail, Phone, Messages, Camera, Gallery, Settings, Play Store, Telegram, Snapchat, Discord, browser, file manager and clock), while retaining the prior English/Hinglish aliases.
- Improved fuzzy multi-word matching with one-to-one token assignment so one recognized word cannot satisfy several app-name words.
- Added lifecycle-based refresh when the Installed Apps page resumes, so the visible list is refreshed after returning from another screen or after installing/uninstalling an app in Settings.
- Kept app discovery restricted to Android launcher activities visible through the manifest's narrow `<queries><intent>` declaration. No broad `QUERY_ALL_PACKAGES` permission is added.
- Kept ambiguity protection: if the best match is too weak or too close to another app, the launcher refuses to guess.
- Kept launch restricted to exported, enabled launcher activities and catches launch failures.

## Verification
- Existing repository offline source-contract verifier: PASS.
- Launcher-specific source checks: PASS (aliases, Hindi command forms, one-to-one matching, lifecycle refresh, package visibility declaration, no broad package permission).
- ZIP integrity: checked after packaging.
- Not verified here: Gradle/Kotlin compilation (no Gradle wrapper/system Gradle in this environment), Android runtime behavior, installed-app visibility on each OEM, and real Hindi/Hinglish speech-to-text accuracy. These require the user's Android build and device testing.

Speech recognition quality depends on the Android recognition service and its selected language/model. The launcher can correct common transcription differences, but cannot guarantee every accent, pronunciation, app alias, or third-party app visibility policy.
