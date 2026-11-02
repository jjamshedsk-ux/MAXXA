# MAXXA AI — Animated AI Core completion pass

## Implemented in this source update
- Added `AiCore3D.kt`, a lightweight Compose Canvas animated core with layered radial shading, three rotating orbital paths, orbiting particles, rim glow and breathing pulse.
- Voice state is connected to the core: Idle, Listening, Processing and Speaking each receive distinct animated color treatment.
- The core is tap-accessible and exposes a state-aware accessibility description.
- Added persisted AI Core theme selection in Preferences: Crimson Neon, Cyber Blue and Violet Pulse. The selected palette is passed through MaterialTheme and consumed live by the core.
- Voice screen core size is capped to the available width (286 dp maximum) to avoid overflow on narrower devices.

## Verification status
- Source integration and static checks are run as part of this pass.
- This is a 3D-style Canvas rendering, not a polygon mesh or hardware-accelerated 3D engine.
- Android compilation, emulator/device animation smoothness, touch testing and screenshot-based responsive verification still require an Android SDK/Gradle environment and a physical device or emulator. These are not claimed as passed here.

## Seven-theme selector update (2026-10-31)
- Replaced the temporary three-option selector with the seven agreed options: Crimson Black, AMOLED Pure Black, Purple Neon, Cyber Blue, Emerald Green, Pink Anime, and Custom Accent Color.
- Added persistent custom accent selection with a built-in colour swatch picker.
- Theme palette now drives the app Material theme and the AI Core's idle palette; selection is stored in SharedPreferences.
- The former `Crimson Neon` preference migrates to `Crimson Black` on load.
