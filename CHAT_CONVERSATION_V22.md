# MAXXA AI V22 — Chat & Conversation completion pass

## Added in source
- Chat composer attachment picker for JPEG, PNG, WebP, PDF and plain-text files.
- 5 MB per-file client-side limit; attachment bytes are read off the main thread.
- Secure backend accepts validated base64 attachment payloads, rejects unsupported MIME types and oversize content, and forwards image/PDF inline data to Gemini. Plain-text files are decoded and included as text.
- Streaming and non-streaming chat endpoints share the attachment validation/conversion helper.
- Conversation search now jumps to the matched message index in the selected conversation.
- Inline Markdown handling additionally strips common italic, underline-style emphasis, strikethrough and link syntax; existing headings, lists, tables and fenced code remain.

## Limits
- Attachments are sent to the configured Gemini backend; this is not persistent cloud file storage. The chat history stores the visible attachment label, not the original binary file, so a previously sent file cannot be re-opened/retried from history after the app is closed.
- Markdown rendering remains a lightweight renderer, not complete CommonMark/GFM, and code syntax highlighting is not included.
- PDF/image understanding depends on the configured Gemini model supporting those input modalities.

## Verification
- `node --check backend/app.js` and `node --check backend/test/app.test.js` pass.
- `python3 scripts/verify_source.py` passes all offline source-contract checks.
- Full Node tests could not execute in this environment because backend npm dependencies (including Express) are not installed.
- Android Gradle compilation and runtime/device testing have not been run here.
