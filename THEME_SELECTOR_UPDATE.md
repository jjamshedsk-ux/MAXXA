# MAXXA AI — Seven Theme Selector

The Preferences screen contains the final seven AI Core themes:
1. Crimson Black
2. AMOLED Pure Black
3. Purple Neon
4. Cyber Blue
5. Emerald Green
6. Pink Anime
7. Custom Accent Color

The first six apply their palettes immediately. Custom Accent Color opens an eight-swatch picker; choosing a swatch applies and persists the custom palette. The selected theme and custom accent are stored in Android SharedPreferences. Existing `Crimson Neon` preference values are mapped to `Crimson Black`.

The AI Core is a lightweight Compose Canvas 3D-style animation, not a dedicated 3D engine. Runtime behaviour still needs Android build and device/emulator verification.
