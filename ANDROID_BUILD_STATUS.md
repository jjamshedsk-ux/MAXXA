# Android build status

This pass prepares the repository's Android build pipeline. The project is configured for Android Gradle Plugin 8.7.3, Gradle 8.9, Java 17, compile/target SDK 35, and min SDK 26. GitHub Actions explicitly installs the required Android SDK platform/build-tools before running `gradle clean assembleDebug`. The manifest also declares the speech-recognition and TTS service queries.

## Verification boundary

The current editing environment has Java 21 but no Gradle executable, Gradle wrapper, Android SDK, Android platform 35, or ADB. Outbound DNS/network access is unavailable, so the Gradle distribution and Android SDK cannot be downloaded here. Therefore no APK was produced and Kotlin/Compose compilation has **not** been verified in this environment. The first successful GitHub Actions `Build Android debug APK` run is the required compile/build proof.

## Build artifact

After pushing the extracted project to GitHub, run the **Build MAXXA AI** workflow (Actions -> Build MAXXA AI -> Run workflow). Download `MAXXA-AI-debug-APK` from a successful run. This is a debug APK, not a Play Store release-signed APK.

## Final source verification pass
An offline source-contract check is now part of CI and passed locally. It verifies core manifest/service declarations, least-privilege Accessibility configuration, action mappings, Android SDK/plugin pins, and APK artifact workflow configuration. It does not replace Gradle compilation. The current environment still lacks Gradle and Android SDK, so compilation remains pending.
