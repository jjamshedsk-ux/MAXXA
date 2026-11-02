# Installed App Launcher V30 — One-pass source update

Implemented in Kotlin source:
- Custom aliases are keyed by installed package name and scored directly against the selected package, rather than being converted to display-label aliases.
- Alias editor preloads saved values for editing; CLEAR removes the package mapping; stale mappings are cleaned when installed-app inventory refreshes after uninstall.
- Launch exceptions now return failure (`false`) rather than reporting success. Ambiguous-choice launch also checks the actual startActivity result.
- Matching now uses stricter short-query thresholds and a larger ambiguity margin for very short names to reduce accidental launches.
- Installed-app inventory refreshes on lifecycle resume and package add/remove/change/replace broadcasts.
- Package visibility remains restricted to launcher-intent queries and selected known packages; no QUERY_ALL_PACKAGES permission is added.
- Existing Hindi/Hinglish/English command normalization and common pronunciation aliases are retained.

## Verification
- `python scripts/verify_launcher_v28.py`: all 12 launcher source-contract checks pass (script retains historical filename).
- `python scripts/verify_source.py`: all repository offline source-contract checks pass.
- ZIP archive integrity checked after packaging.
- Android Kotlin compilation was not run: this source archive does not include `gradlew`, and no Gradle wrapper is available in the project. Android build and real-device behavior remain to be checked by the project owner.
