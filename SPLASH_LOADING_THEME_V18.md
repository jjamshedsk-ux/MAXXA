# MAXXA AI V18 — Splash & Loading Animation

## Source implementation
- Splash screen now receives the persisted Character Theme Gallery selection.
- Splash gradient, glow, rotating ring, logo halo, labels, and progress indicator use the selected theme's accent/secondary/highlight palette.
- The selected character's emoji and theme name are displayed on the loading card.
- Existing Itachi splash artwork is used only for the Itachi theme; other themes use their own theme identity instead of showing Itachi artwork.
- Splash entrance/exit transitions are smoother and the logo halo/orbit animate continuously during the splash interval.
- Chat typing/loading spinner and label now use the active character theme accent instead of a fixed colour.
- AI Core Theme remains independent; splash follows the normal Character Theme Gallery selection.
- Selected theme is read from `CharacterThemeStore` at app start, so the saved selection controls the next launch splash.

## Verification boundary
- Source-level checks and ZIP integrity are performed for this archive.
- Android Gradle compilation, emulator/device runtime, and visual screenshot comparison are not claimed here.
- The splash uses a short presentation delay; the progress bar is an indeterminate visual indicator, not a measurement of backend readiness.
