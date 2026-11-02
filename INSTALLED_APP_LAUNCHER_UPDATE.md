# Installed App Launcher — V26 matching update

## Changes
- Keeps the launcher limited to Android `ACTION_MAIN` + `CATEGORY_LAUNCHER` activities visible under the manifest's narrow package-visibility query; no `QUERY_ALL_PACKAGES` permission was added.
- Normalizes case, accents, punctuation and repeated whitespace before matching.
- Strips common English/Hinglish launch phrases such as "open", "kholo", "khol do", "chalao" and "shuru karo".
- Adds spoken-name aliases for common app names (for example YouTube/"you tube", Instagram/"insta", WhatsApp/"whats app", Facebook/"FB", and Free Fire/"freefire").
- Uses per-word typo similarity plus whole-name similarity so small speech-recognition mistakes can still match installed app labels.
- Retains a confidence threshold and a stronger gap requirement between the best and second-best match. Ambiguous commands are not launched automatically, reducing wrong-app launches.
- Launches the actual package/activity selected by PackageManager; no arbitrary shell commands or package names from the spoken text are executed.

## Examples intended to match
- "open YouTube", "you tube kholo", "open youtub"
- "insta kholo", "open instgram"
- "whats app chalao", "open watsapp"
- "freefire kholo", "open free fire max"

## Verification limits
The offline source-contract verifier passes. Android/Kotlin compilation and speech-recognition behaviour must still be checked on a device; recognition quality varies by Android speech service, language and accent. If two installed apps have similarly matching names, MAXXA deliberately declines to launch rather than guessing.
