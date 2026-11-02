# MAXXA AI — UI implementation pass

Date: 30 October 2026
Reference: `app/src/main/assets/maxxa_ui_reference.png`

## Screen coverage implemented in Compose
- Splash / loading
- Home dashboard with prompt field, primary shortcuts and quick actions
- Side navigation drawer
- Chat conversation
- Conversation history landing page (shows the locally stored current thread; multiple named threads/cloud sync are not implemented)
- Voice assistant with listening state, waveform and Stop Listening
- Voice Pack / language and speech preview controls (uses the Android TTS engine; custom character voice packs are not bundled)
- Secure backend connection screen (uses HTTPS backend URL rather than exposing a Gemini key on-device)
- Permissions screen (requests microphone only, the permission currently used by the app)
- Preferences and language selection
- Automation shortcuts
- Phone Control shortcuts
- Additional Settings with About, Help, rate-app placeholder, cache clear, permission settings and reset actions
- Image Generation, Code Generator, Summarize and Translate tool screens
- Help & Support FAQ and About screen

## Changes in this consolidated UI pass
- Split Chat and Conversation navigation: Chat opens the active chat; Conversation opens a local-history overview.
- Added a current-thread card, message count, latest-message preview and clear-history action to Conversation.
- Expanded Additional Settings to include About, Help, Rate App (disabled until public release), Clear Cache, Android App Permissions and Reset Settings.
- Added confirmation dialogs before clearing cache, clearing history or resetting preferences.
- Reset Settings restores in-memory preferences and removes the saved backend URL without deleting chat history.
- Preserved secure backend configuration: the Gemini API key remains server-side and is not entered or stored in the Android app.
- Preserved least-privilege permissions: only microphone permission is requested by this build.

## Important differences from the reference
- The reference is a contact sheet, not individual source layouts or measurement specs, so exact pixel matching cannot be established from it alone.
- Reference artwork is bundled and used as background/hero art, but exact per-screen crops and spacing may differ.
- The reference depicts a direct API-key screen; this app deliberately uses a secure HTTPS backend URL instead.
- The reference depicts broader permissions; this app intentionally requests microphone only.
- Reference voice pack lists character voices; this app uses voices available from the device's installed Android Text-to-Speech engine.
- The history UI represents one locally stored conversation thread. Multi-thread session management and cloud sync are not implemented.
- Rate App remains unavailable until a public store listing exists.
- Phone Control and Automation open Android destinations with user confirmation; they do not silently control other apps or protected system toggles.

## Verification status
- Source navigation and UI route coverage reviewed after changes.
- Kotlin source delimiter balance checked; this is not a substitute for Kotlin/Compose compilation.
- Android SDK, Gradle executable/wrapper, emulator and ADB are not available in this environment. Android compilation, APK generation, runtime interaction and device screenshots therefore remain unverified.
- A successful GitHub Actions Android build and screenshots from the target phone are required before claiming pixel-perfect UI or full runtime verification.

## Honest status
The reference screen set has a corresponding Compose route or a documented intentional security/platform difference. This is **source-level UI coverage**, not proof of 100% pixel-perfect matching or device-tested completion. Do not label the UI fully verified until the Android build and target-device screenshot comparison pass.


## Additional visual alignment pass (30 October 2026)
- Main header now switches to a screen-specific title with back navigation on secondary pages, while Home retains the MAXXA brand header and quick access icons.
- Chat now includes the reference-style Explain / Code / Translate / More quick-action chips above the composer.
- Voice view now uses the bundled artwork behind a crimson radial microphone treatment and waveform area.
- Language selection now presents English, Hindi and Hinglish as separate choices.
- These are source-level visual alignment changes. They still require a successful Android/Compose build and screenshots from the target device for measured pixel matching.

## Consolidated UI finish pass (31 October 2026)
- Finalized the splash composition: full-screen Itachi/Sakura artwork, animated logo pulse, MAXXA wordmark, assistant tagline, framed loading progress and fade-in/fade-out handoff to Home. The existing app icon asset was intentionally left unchanged per request.
- Added animated page-to-page transitions (fade + short vertical movement) for all routed screens while retaining the existing page layouts and bottom navigation.
- Added subtle content-size motion to reusable action/info cards and quick-action cards.
- Made the navigation drawer vertically scrollable on shorter displays to prevent its lower destinations from being clipped.
- Kept screen content in a dedicated full-size content container so page transitions do not overlap the persistent bottom navigation.
- Reviewed the reference contact sheet against implemented routes: Home, drawer, Chat, Voice, Voice Pack, secure backend connection (instead of exposing a client-side Gemini API key), Conversation, Permissions (microphone only), Preferences, Automation, Additional Settings, Language Selection, Image Generation, Code Generator, Summarize, Translate, Phone Control, Help and About.
- Security/platform deviations from the reference remain intentional: Gemini credentials stay server-side; Android permissions remain least-privilege; device actions require user confirmation and cannot silently change protected system settings.

## Final-pass verification limits
- Source-level layout and route review completed; ZIP integrity and backend JavaScript syntax checks are run for this package.
- Exact pixel-perfect certification cannot be established from the supplied contact-sheet reference alone. The actual target-device screenshots and screen dimensions are needed for measured comparison.
- Android/Compose compilation and runtime touch/animation verification still require a successful Android build and installation on the target phone. This environment has no Gradle executable, Android SDK, emulator or ADB.
- The existing app icon was deliberately not changed in this pass, as requested.

## Main UI & Navigation final source pass (31 October 2026)
- Added system Back handling: it closes the open navigation drawer first, otherwise returns any secondary screen to Home.
- Added a centered adaptive content column capped at 760 dp, with responsive horizontal insets for compact phones, larger phones and tablet-width windows.
- Increased bottom-navigation and drawer-row touch targets to a minimum of 48 dp.
- Kept the page transition container separate from persistent bottom navigation; retained animated route transitions.
- Extended `scripts/verify_source.py` with regression checks for Back handling, adaptive width/insets, touch targets, drawer route coverage and animated transitions.
- Offline source checks pass, including all 13 drawer destinations mapping to explicit Compose routes.
- Limitation: source checks cannot exercise actual taps, verify Compose measurement at runtime, or prove the Kotlin app compiles. Those require a successful CI Android build and emulator launch; target-device visual comparison remains separate.
