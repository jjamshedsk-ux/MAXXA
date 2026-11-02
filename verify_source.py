#!/usr/bin/env python3
"""Offline source-contract checks for MAXXA AI; not a substitute for Android builds/tests."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = '{http://schemas.android.com/apk/res/android}'


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f'FAIL: {message}')
    print(f'PASS: {message}')

manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
app = manifest.find('application')
require(app is not None, 'Android application element exists')
require(app.attrib.get(ANDROID_NS + 'usesCleartextTraffic') == 'false', 'cleartext traffic disabled')
service = app.find("service[@{0}name='.MaxxaAccessibilityService']".format(ANDROID_NS))
require(service is not None, 'AccessibilityService declared')
require(service.attrib.get(ANDROID_NS + 'permission') == 'android.permission.BIND_ACCESSIBILITY_SERVICE', 'AccessibilityService is system-bound')
require(service.attrib.get(ANDROID_NS + 'exported') == 'true', 'AccessibilityService exported for Android binding')

cfg = ET.parse(ROOT / 'app/src/main/res/xml/maxxa_accessibility_service.xml').getroot()
require(cfg.attrib.get(ANDROID_NS + 'canRetrieveWindowContent') == 'false', 'Accessibility cannot retrieve window content')
require(cfg.attrib.get(ANDROID_NS + 'canPerformGestures') == 'false', 'Accessibility cannot perform gestures')
require(cfg.attrib.get(ANDROID_NS + 'accessibilityEventTypes') == 'typeWindowStateChanged', 'Accessibility event scope is minimized')

service_src = (ROOT / 'app/src/main/java/com/maxxa/ai/MaxxaAccessibilityService.kt').read_text()
for action in ('GLOBAL_ACTION_BACK', 'GLOBAL_ACTION_HOME', 'GLOBAL_ACTION_RECENTS',
               'GLOBAL_ACTION_NOTIFICATIONS', 'GLOBAL_ACTION_QUICK_SETTINGS'):
    require(action in service_src, f'phone action mapping exists: {action}')
require('getRootInActiveWindow' not in service_src and 'dispatchGesture' not in service_src,
        'no screen scraping or gesture automation APIs in service')

app_gradle = (ROOT / 'app/build.gradle.kts').read_text()
root_gradle = (ROOT / 'build.gradle.kts').read_text()
workflow = (ROOT / '.github/workflows/android-build.yml').read_text()
require('compileSdk = 35' in app_gradle and 'targetSdk = 35' in app_gradle, 'Android SDK levels configured')
require('8.7.3' in root_gradle and '2.0.21' in root_gradle, 'Android/Kotlin plugin versions pinned')
require('java-version: \'17\'' in workflow and 'gradle-version: \'8.9\'' in workflow,
        'CI Java and Gradle versions configured')
require('assembleDebug' in workflow and 'upload-artifact@v4' in workflow,
        'CI builds and uploads debug APK artifact')

for file_name in ('backend/app.js', 'backend/server.js', 'backend/test/app.test.js'):
    require((ROOT / file_name).is_file(), f'backend source present: {file_name}')
print('All offline source-contract checks passed. This does not prove Kotlin compilation or runtime behavior.')

# Main UI & Navigation source-level regression checks.
main_activity = (ROOT / 'app/src/main/java/com/maxxa/ai/MainActivity.kt').read_text()
require('BackHandler(enabled = menuOpen || selectedPage != "Home")' in main_activity,
        'system Back closes the drawer or returns to Home')
require('widthIn(max = 760.dp)' in main_activity and 'horizontalInset' in main_activity,
        'main content uses adaptive centered width and screen-size insets')
require('heightIn(min = 48.dp)' in main_activity,
        'primary bottom navigation and drawer rows have minimum touch targets')
menu_match = re.search(r'listOf\("Home", "Chat".*?\)', main_activity, re.S)
require(menu_match is not None, 'navigation drawer destinations are defined')
menu_items = re.findall(r'"([^"\n]+)"', menu_match.group(0))
routes = set(re.findall(r'^\s*"([^"\n]+)"\s*->', main_activity, re.M))
missing_routes = [item for item in menu_items if item not in routes]
require(not missing_routes, 'all drawer destinations have explicit screen routes')
require('AnimatedContent(' in main_activity and 'maxxa-page-transition' in main_activity,
        'screen changes use animated page transitions')

# Chat & Conversation source-contract checks.
require('askGeminiStream' in main_activity and 'api/chat/stream' in (ROOT / 'app/src/main/java/com/maxxa/ai/MainActivity.kt').read_text(),
        'Android chat uses the streaming backend endpoint')
require('updateChatOnMain' in main_activity and 'CountDownLatch' in main_activity,
        'stream chunks are applied synchronously on the Android main thread')
for token, label in [('showChatManager', 'conversation manager'), ('showRenameDialog', 'rename dialog'),
                     ('RETRY', 'retry action'), ('REGENERATE', 'regenerate action'), ('EDIT', 'edit action'),
                     ('HorizontalDivider', 'Markdown table rendering'), ('startsWith("### ")', 'Markdown headings'),
                     ('Regex("[-*+]\\\\s+.+")', 'Markdown list rendering')]:
    require(token in main_activity, f'chat feature source exists: {label}')
require('MAXXA is thinking' in main_activity and 'CircularProgressIndicator' in main_activity and 'enabled = !loading' in main_activity,
        'chat loading indicator and in-flight interaction guard exist')
require('retryable = messageIndex == latestAssistantIndex' in main_activity,
        'retry is limited to the latest failed or interrupted assistant response')
require('messageIndex == latestAssistantIndex' in main_activity and 'REGENERATE' in main_activity,
        'regenerate is limited to the latest assistant response')
require('persistThreads()' in main_activity and 'active_chat_thread' in main_activity and 'chat_threads' in main_activity,
        'conversation history and active thread are persisted locally')
backend_source = (ROOT / 'backend/app.js').read_text()
require("/api/chat/stream" in backend_source and "res.off(\'close\', abortForClient)" in backend_source,
        'backend streaming route cleans up client disconnect listener')

# Voice assistant source-contract checks (offline; not a microphone/TTS runtime test).
require('private enum class VoiceStage { Idle, Listening, Processing, Speaking }' in main_activity,
        'voice assistant has explicit Idle/Listening/Processing/Speaking state machine')
for stage, label in [('VoiceStage.Listening', 'listening stage'), ('VoiceStage.Processing', 'processing stage'),
                     ('VoiceStage.Speaking', 'speaking stage'), ('VoiceStage.Idle', 'idle/reset stage')]:
    require(stage in main_activity, f'voice state transition exists: {label}')
require('submitVoiceMessage(spoken)' in main_activity,
        'recognized speech is automatically submitted without Send tap')
require('askGeminiStream' in main_activity and 'onSpeak(answer, replyLanguage)' in main_activity,
        'voice answer waits for completed Gemini stream before TTS playback')
require('UtteranceProgressListener()' in main_activity and 'override fun onDone' in main_activity,
        'TTS completion callback returns voice UI to idle')
require('speechRecognizer?.cancel()' in main_activity and 'tts?.stop()' in main_activity,
        'Stop Voice cancels recognition and stops speech playback')
require('voiceRequestJob?.cancel()' in main_activity,
        'Stop Voice requests cancellation of the voice response coroutine')
require('class VoiceRequestCancellation' in main_activity and 'activeConnection.getAndSet(null)?.disconnect()' in main_activity,
        'Stop Voice actively disconnects an in-flight streaming HTTP request')
require('cancellation?.checkActive()' in main_activity and 'cancellation?.attach(connection)' in main_activity,
        'voice streaming checks cancellation before sending and while reading chunks')
require('if (language != "Hindi / Hinglish") putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)' in main_activity,
        'Hinglish mode allows recognizer language auto-detection instead of forcing Hindi')
require('voiceStage == VoiceStage.Idle && !loading' in main_activity and 'if (isListening || loading || isSpeaking)' in main_activity,
        'voice controls and Stop button follow the explicit voice stage')
for token, label in [('ERROR_INSUFFICIENT_PERMISSIONS', 'permission error'),
                     ('ERROR_NETWORK_TIMEOUT', 'speech network timeout'),
                     ('ERROR_NO_MATCH', 'no speech match'), ('ERROR_RECOGNIZER_BUSY', 'recognizer busy'),
                     ('Microphone permission denied', 'permission denial message')]:
    require(token in main_activity, f'voice error handling covers {label}')
require('EXTRA_LANGUAGE_PREFERENCE' in main_activity and '"en-IN"' in main_activity and '"hi-IN"' in main_activity,
        'English and Hindi locale preferences are configured; Hinglish uses Hindi locale fallback')
require('app/src/main/assets/maxxa_ui_reference.png' in str(ROOT / 'app/src/main/assets/maxxa_ui_reference.png'),
        'voice UI reference asset is present')

# Installed app launcher contracts
manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
require('private fun InstalledAppsPage()' in main_activity, 'installed-app launcher page exists')
require('Intent.CATEGORY_LAUNCHER' in main_activity and 'queryIntentActivities' in main_activity, 'launcher queries only launchable activities')
require('it.label.contains(search.trim(), ignoreCase = true)' in main_activity or 'it.label.contains(search.trim()' in main_activity, 'installed-app search filters by app label')
require('TextButton(onClick = { refreshKey++ })' in main_activity, 'installed-app list has a refresh action')
require('getLaunchIntentForPackage(app.packageName)' in main_activity, 'tap-to-launch uses the installed package launch intent')
require('android.intent.action.MAIN' in manifest and 'android.intent.category.LAUNCHER' in manifest, 'manifest declares narrow launcher visibility query')
require('android.permission.QUERY_ALL_PACKAGES' not in manifest, 'launcher avoids broad QUERY_ALL_PACKAGES permission')

# Coding Workspace source-contract checks (not a Kotlin compile or editor runtime test).
coding_page = ROOT / 'app/src/main/java/com/maxxa/ai/CodingWorkspacePage.kt'
require(coding_page.is_file(), 'Coding Workspace screen source exists')
coding_source = coding_page.read_text()
for token, label in [
    ('"Kotlin", "Java", "Python"', 'multi-language editor choices'),
    ('CodeTemplate("HTML page"', 'starter templates'),
    ('BasicTextField(', 'editable code editor'),
    ('it.length <= 500_000', 'bounded editor input'),
    ('File(folder, name).writeText', 'private local draft persistence'),
    ('ActivityResultContracts.CreateDocument', 'document export flow'),
    ('ActivityResultContracts.OpenDocument', 'document import flow'),
    ('LocalClipboardManager.current', 'copy-to-clipboard support'),
    ('Intent.ACTION_SEND', 'share-code action'),
    ('MY DRAFTS', 'saved draft browser'),
    ('Code execution, debugging and APK compilation are not built into this workspace', 'honest execution capability disclosure'),
]:
    require(token in coding_source, f'Coding Workspace: {label}')
require('"Coding Workspace" -> CodingWorkspacePage()' in main_activity, 'Coding Workspace has a screen route')
require('"Automation", "Coding Workspace", "Installed Apps"' in main_activity, 'Coding Workspace is in navigation drawer')

# V20: all user-facing tool destinations are directly reachable from the More drawer.
for destination in ('"Image Generation"', '"Code Generator"', '"Translate"', '"Summarize"'):
    require(destination in main_activity[main_activity.find('listOf("Home", "Chat"'):],
            f'More drawer exposes {destination.strip(chr(34))}')

# V21 Chat & Conversation additions. These are source-presence checks only.
require('rememberLazyListState()' in main_activity and 'animateScrollToItem(chats.lastIndex)' in main_activity,
        'chat automatically scrolls to the latest message')
require('showChatSearch' in main_activity and 'Search conversations' in main_activity,
        'conversation search dialog exists')
require('chatCancellation.cancel()' in main_activity and 'chatRequestJob?.cancel()' in main_activity,
        'Stop Generation cancels coroutine and streaming connection')
require('Intent.ACTION_SEND' in main_activity and 'Share MAXXA response' in main_activity,
        'assistant response sharing uses Android Sharesheet')
require('COPY CODE' in main_activity and 'clipboard.setText(AnnotatedString(parts.getOrElse(1)' in main_activity,
        'fenced code blocks have a dedicated copy action')
require('Text("DELETE"' in main_activity, 'individual chat messages have a delete action')


# V23 Gemini backend readiness and Android preflight checks.
backend_app = (ROOT / 'backend/app.js').read_text()
require("app.get('/ready'" in backend_app, 'Gemini backend exposes a readiness endpoint')
require('geminiConfigured: configured' in backend_app and 'clientAuthenticationConfigured: authenticated' in backend_app,
        'readiness endpoint reports configuration booleans without secret values')
require("if (abortForClient) res.off('close', abortForClient)" in backend_app and 'await reader.cancel()' in backend_app,
        'streaming backend removes disconnect listeners and cancels upstream reader during cleanup')
require('URL("$base/ready")' in main_activity,
        'Android Test Backend Connection verifies readiness, not only liveness')

# V24 voice assistant source-contract checks
main_activity = (ROOT / "app/src/main/java/com/maxxa/ai/MainActivity.kt").read_text(encoding="utf-8")
for label, needle in [
    ("TTS readiness gate", "private var ttsReady = false"),
    ("queue speech until TTS initialization", "pendingSpeech = Triple(text, language, onDone)"),
    ("transient speech audio focus", "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"),
    ("release audio focus", "abandonAudioFocusRequest"),
    ("TTS stop/interruption callback", "override fun onStop(utteranceId: String?"),
    ("partial recognition enabled", "RecognizerIntent.EXTRA_PARTIAL_RESULTS, true"),
]:
    if needle not in main_activity:
        raise SystemExit(f"FAIL: missing V24 voice feature: {label}")
    print(f"PASS: {label}")
