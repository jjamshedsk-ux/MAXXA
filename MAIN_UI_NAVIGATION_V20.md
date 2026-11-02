# MAXXA AI — Main UI & Navigation V20

## One-pass source update
- Added Image Generation, Code Generator, Translate, and Summarize to the navigation drawer so these tool screens are reachable from More as well as Home/Chat shortcuts.
- Added matching drawer icons for the newly exposed destinations.
- Retained explicit screen routes, animated screen transitions, system Back handling, adaptive centered content width, scrollable drawer, and minimum-height navigation touch targets.

## Verification
- `python3 scripts/verify_source.py` passes all offline source-contract checks.
- The verifier checks that every drawer destination has an explicit route.
- Android compilation and device/runtime behaviour were not tested in this environment because Gradle is not installed. The user can perform the Android build and phone testing separately.
