# MAXXA AI V33 — Gemini Backend reliability pass

This update addresses the five requested source-level areas:

1. **HTTP errors and timeouts:** Android chat streaming maps 4xx/5xx statuses to actionable messages and distinguishes socket timeouts. Backend routes normalize provider failures and timeouts to safe gateway errors.
2. **Retry and fallback:** Backend Gemini chat and streaming calls retry transient upstream network failures and 429/5xx responses up to three attempts with bounded backoff. Non-retryable client errors are returned immediately. The app displays retry guidance instead of silently swallowing errors.
3. **Response parsing:** Android rejects malformed NDJSON rows, backend rejects malformed/empty provider JSON, and stream events surface malformed provider data and empty streams as explicit errors.
4. **Connection state:** The API settings readiness check now distinguishes CONNECTED, ONLINE-BUT-NOT-READY, and DISCONNECTED/TIMEOUT states.
5. **Streaming recovery:** The backend reports mid-stream failures as NDJSON error events; Android detects those events and empty streams rather than treating them as successful empty replies.

## Verification boundary

- JavaScript syntax checks: run with `node --check`.
- Android source contract checks: run with `scripts/verify_gemini_backend_v33.py`.
- Full Node integration tests require installing the backend's npm dependencies (`npm ci` in `backend/`).
- Android compilation, live Gemini API behavior, backend deployment, and device testing are not claimed as verified here.
