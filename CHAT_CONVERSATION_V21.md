# MAXXA AI V21 — Chat & Conversation source update

Implemented in the Android source:
- Conversation search across locally saved thread titles and message text.
- Automatic scroll to the newest chat row when messages are appended or generation state changes.
- Stop Generation action wired to coroutine cancellation and active HTTP connection disconnect.
- Share action for assistant responses using Android Sharesheet.
- Copy Code action for fenced code blocks, in addition to whole-response copy.
- Delete action for individual messages.
- Existing local multi-thread persistence, rename, edit, retry, regenerate, Markdown headings/lists/tables and streaming retained.

## Capability boundaries
- Attachments are not implemented in this update. The current backend chat contract accepts a text `message` only; image/file attachment support requires a coordinated Android + backend multimodal API change and request-size/security tests. No attachment button is presented as if it worked.
- Markdown rendering is a lightweight in-app renderer, not full CommonMark/GFM or syntax highlighting.
- Conversation search opens the matching conversation; it does not deep-scroll to the exact matched message.
- Local history remains on-device and is not cloud-synced.

## Verification boundary
Run `python3 scripts/verify_source.py` for offline source-contract checks. This is not a Kotlin/Gradle build, emulator test, backend deployment test, or device test. Android compilation and runtime behavior remain unverified here.
