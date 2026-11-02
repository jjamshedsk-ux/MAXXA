# MAXXA AI — Character Theme Restart & Save (V15)

## Source changes
- Centralized character theme persistence in `CharacterThemeStore`.
- Reads the saved selection during Compose startup, so the selected theme is restored after Activity recreation or app relaunch.
- Saves the selection with `SharedPreferences.commit()` before updating the selected UI state. This makes the disk write synchronous at the point of selection rather than relying on the asynchronous `apply()` write.
- Validates saved and requested theme names against the 12 supported gallery entries; invalid/missing values safely fall back to Itachi.
- Keeps the preference key `character_ui_theme` in the existing `maxxa_settings` preferences, preserving the previous V14 saved selection.
- Shows a Toast if persistence fails; it does not falsely show a failed save as selected.
- Character UI theme remains separate from `core_theme` and `anime_voice_pack`.

## Supported themes
Itachi, Naruto, Sasuke, Kakashi, Madara, Hinata, Sakura, Nezuko, Obito, Minato, Mitsuri, Nobara.

## Verification boundary
Source-level wiring and archive contents were checked. Android compilation, process-kill/relaunch testing, and visual runtime behavior have not been run in this environment. Recommended manual check: select a non-default character, fully close the app, reopen it, and confirm both the gallery's active card and app accent colours are restored.
