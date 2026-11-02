# MAXXA AI V23 — Gemini Backend Connection source pass

## Implemented in this update
- Fixed the streaming route's disconnect-handler scope: the handler is now available to the error path, detached in all exits, and the upstream reader is cancelled during cleanup.
- Added `/ready`, a readiness endpoint that reports only booleans/model name and never exposes secrets. It returns HTTP 200 only when both `GEMINI_API_KEY` and `MAXXA_CLIENT_TOKEN` are configured; otherwise HTTP 503.
- Changed Android's **Test Backend Connection** to call `/ready`, distinguishing a missing Gemini server secret from a missing client-auth token.
- Added backend tests for readiness behavior, secret non-disclosure, and bearer-token enforcement.
- Expanded deployment instructions to distinguish liveness (`/health`) from readiness (`/ready`) and document the shared-client-token limitation.

## Source checks
- `node --check backend/app.js`: PASS
- `node --check backend/server.js`: PASS
- `node --check backend/test/app.test.js`: PASS
- `python3 scripts/verify_source.py`: PASS
- Android manifest/XML parse: PASS
- ZIP integrity: PASS

## Not verifiable in this workspace
- `npm test` could not run because npm registry DNS/network access failed (`EAI_AGAIN`) and dependencies are not installed.
- No real Gemini API key or client token was supplied; no production secrets were created or embedded.
- No hosting account/deployment credentials were supplied; backend was not deployed.
- No live Gemini request, quota/billing behavior, TLS endpoint or production monitoring was verified.
- Android Gradle/SDK are unavailable in this workspace; no APK compile or device test is claimed.

## Required one-time deployment steps
1. Deploy `backend/` to a Node.js 20+ host with HTTPS.
2. Set `NODE_ENV=production`.
3. Add `GEMINI_API_KEY` as a server-side secret from the user's Google AI project.
4. Generate a random 32+ byte token (for example, `openssl rand -hex 32`) and set it as `MAXXA_CLIENT_TOKEN` on the host.
5. Optionally configure `GEMINI_MODEL`, `GEMINI_IMAGE_MODEL`, and the exact trusted proxy hop count.
6. Verify `/health` returns 200 and `/ready` returns 200 with all readiness booleans true.
7. Enter the HTTPS base URL and the same client token in MAXXA AI → API Key → Save HTTPS Backend, then use Test Backend Connection.
8. Confirm an actual chat response and image generation using the user's own API project and quota.

The backend source/configuration pass is complete. Live connection completion is intentionally not marked 100% until the user's real server secrets and hosting are configured and an end-to-end request succeeds. A shared token embedded in an Android client is not strong per-user authentication; a public multi-user release still needs account identity, per-user quotas and abuse controls.
