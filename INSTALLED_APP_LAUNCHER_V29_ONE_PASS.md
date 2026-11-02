# Installed App Launcher V29 — one-pass source update

- Added a per-installed-app custom alias editor. Aliases are comma-separated, persisted locally in SharedPreferences by package name, editable, and clearable.
- Voice matching now incorporates saved aliases alongside built-in Hindi, Hinglish, and English aliases.
- Ambiguous voice matches now open a choice dialog listing the close candidate apps; no app is launched until the user chooses one or cancels.
- Voice launch failures now provide a user-facing reason for blocked or missing launch activities.
- Retained normalized matching, one-to-one token assignment, score threshold and margin checks, lifecycle refresh, and narrow launcher package visibility (no QUERY_ALL_PACKAGES).

## Verification limits

Offline source-contract checks and the launcher-specific checks pass. This environment does not contain a Gradle wrapper or system Gradle, so Kotlin/Gradle compilation was not run. Android runtime behavior, OEM package-visibility differences, and actual speech-recognition quality still require building and testing on Android devices.
