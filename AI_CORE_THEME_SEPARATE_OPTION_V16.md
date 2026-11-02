# AI Core Theme — Separate Option (V16)

- Added a dedicated **AI Core Theme** item to the navigation drawer.
- Added a standalone AI Core Theme page with the seven existing options and custom accent picker.
- Removed the AI Core Theme controls from Preferences so the two settings areas are distinct.
- Connected the selected Core theme palette directly to the 3D `AiCore3D` renderer.
- Kept Character Theme Gallery responsible for normal app UI colours; Core theme colours are independent.
- Core theme selection and custom accent continue to persist in existing SharedPreferences keys (`core_theme`, `custom_accent_hex`).

Android compilation and runtime/device behaviour have not been verified in this environment.
