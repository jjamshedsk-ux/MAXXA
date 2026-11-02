# MAXXA AI — Phone automation source pass

## Implemented
- Added an Android `AccessibilityService` declaration and service configuration XML.
- Added five explicit, foreground-UI-triggered Android global actions: Back, Home, Recents, Notifications, and Quick Settings.
- Added a Phone Control screen entry that opens Android Accessibility settings so the user can enable/disable the service manually.
- Added a live service-connected indicator and clear feedback when the service is not enabled.
- Kept the service deliberately least-privilege: `canRetrieveWindowContent=false`, `canPerformGestures=false`; it does not read screen text, inspect messages, tap controls, type, place calls, or change protected system toggles.

## User setup
1. Install the APK after a successful GitHub Actions build.
2. Open MAXXA AI → Phone Control.
3. Tap **Enable Accessibility Actions**.
4. In Android Accessibility settings, choose **MAXXA AI Phone Actions** and enable it yourself.
5. Return to MAXXA AI and tap one of the available actions.

Android/MIUI/HyperOS may show a security confirmation or restrict some global actions. The service cannot enable itself; Android requires the user to grant this special access manually.

## Verification status
- Source and manifest changes are present in this ZIP.
- ZIP integrity and XML parsing can be checked locally.
- Android compilation, installation, Accessibility settings behavior, and real-device global actions remain unverified until GitHub Actions and a physical device run successfully.
- This is a safe navigation-action bridge, not unrestricted automation of arbitrary third-party apps.

## Final static hardening update
- Narrowed the service event subscription from all event types to `typeWindowStateChanged`; event payloads remain ignored.
- Added `scripts/verify_source.py` and wired it into the Android CI job. It checks manifest binding, least-privilege accessibility flags, five supported global actions, SDK/plugin pins and artifact workflow configuration.
- Offline source-contract checks pass. This does not prove Android compilation or device behavior.
