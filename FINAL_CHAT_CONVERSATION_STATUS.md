# MAXXA AI — Chat & Conversation consolidated pass

## Source changes in this pass
- Streaming chunks are synchronously applied to Compose state on the main thread before the network operation completes, avoiding the previous queued-UI-update race with answer validation and persistence.
- Added a visible progress spinner alongside the thinking label.
- Retry remains limited to the latest failed/interrupted assistant response; Regenerate remains limited to the latest assistant response.
- Regenerate failure now replaces the in-progress latest assistant item with a retryable error instead of appending a second stray assistant item.
- Conversation switching/deletion controls and rename save are guarded while a request is in flight.
- Chat history and active thread continue to persist locally when Auto Save is enabled.
- Markdown rendering supports headings, lists, blockquotes, simple tables, fenced code blocks, and basic inline marker cleanup.
- Backend stream route flushes a final partial SSE event and aborts upstream work on client disconnect.
- Backend streaming test covers chunk forwarding and final partial-event flush.

## Checks completed
- `python3 -m py_compile scripts/verify_source.py` — PASS
- `python3 scripts/verify_source.py` — PASS (offline source-contract checks)
- `node --check backend/app.js` — PASS
- `node --check backend/server.js` — PASS
- `node --check backend/test/app.test.js` — PASS

## Checks blocked / not yet verified
- `node --test backend/test/app.test.js` — BLOCKED: backend dependencies are not installed (`express` missing); dependency installation/network unavailable in this environment.
- Android Gradle build — BLOCKED: no Gradle executable/wrapper or Android SDK in this environment.
- Actual Android runtime/UI tests — NOT RUN locally; requires an Android device/emulator.
- App-restart history restoration — source path exists but must be confirmed with a real app restart.
- Added a GitHub Actions Android 15 emulator smoke job to install and launch the debug APK after a successful CI build. The workflow has not yet been triggered, so its result is pending.

This is a consolidated source-level fix, not a claim that all nine runtime tests passed.


## Latest consolidated verification setup update
- Added `android-emulator-smoke` job to `.github/workflows/android-build.yml`. It downloads the built APK, installs it on an Android 15 emulator, launches `com.maxxa.ai`, checks that the process remains alive, and scans recent logcat output for a MAXXA crash.
- Local source-contract and JavaScript syntax checks were rerun after the workflow edit and passed.
- This CI job is a launch smoke test only; it does not replace interactive checks for streaming, retry, edit/resend, Markdown layout, chat management, or restart persistence.
