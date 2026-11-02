# MAXXA AI backend

This small Node.js service proxies text requests to Gemini so the provider key stays on the server, not inside the Android APK.

## Deploy
1. Deploy this `backend` directory to a Node.js host that supports Node 20+ and HTTPS (for example, a service you control).
2. Set `GEMINI_API_KEY` in the host's secret/environment settings. Do not commit a real `.env` file or API key.
3. Generate a unique random token of at least 32 characters and set it as `MAXXA_CLIENT_TOKEN` in the host's secret/environment settings. Never reuse your Gemini key as this token.
4. Optionally set `GEMINI_MODEL` (default `gemini-2.5-flash`), `GEMINI_IMAGE_MODEL` and `PORT` (usually supplied by the host). Set `TRUST_PROXY_HOPS` only to the exact proxy hop count documented by your host; otherwise leave it `0`.
5. Run `npm install --no-audit --no-fund` and `npm start` (this repository intentionally does not check in a lockfile yet).
6. Confirm `https://YOUR-HOST/health` returns `{ "ok": true, ... }`. This is a liveness check only.
7. Confirm `https://YOUR-HOST/ready` returns HTTP 200 with `ok: true`, `geminiConfigured: true`, and `clientAuthenticationConfigured: true`. It never returns secret values.
8. In MAXXA AI → API Key, save the backend HTTPS base URL and paste the same `MAXXA_CLIENT_TOKEN` in the Backend Access Token field. The app encrypts this token with Android Keystore on the device. Use **Test Backend Connection**; the app now checks readiness, not just whether a server responds.

## API
`POST /api/chat` with JSON `{ "message": "..." }` returns `{ "text": "..." }`. `POST /api/chat/stream` returns newline-delimited JSON text chunks for the Android streaming UI. `/health` is a liveness check; `/ready` verifies that both required production secrets are configured without exposing them. It enforces a JSON body limit, bounded upstream responses, a global per-IP rate limit plus stricter chat/image per-IP limits, and optional constant-time bearer-token authentication. Configure `MAXXA_CLIENT_TOKEN` before public deployment; when set, all `/api/chat` and `/api/image` requests require it. The app stores the access token encrypted with Android Keystore. Rate-limit counters use the process memory store and reset on restart; for multi-instance deployments, replace this with a shared store and add provider spending budgets, alerting and monitoring. The Android client requires an HTTPS backend URL; the Gemini provider key is never sent to the app. This repository does not deploy the service or create secrets for you.

## Image generation

`POST /api/image` accepts `{ "prompt": "...", "aspectRatio": "1:1" }` and returns `{ "mimeType": "image/png", "imageBase64": "...", "model": "..." }`. The server uses `GEMINI_IMAGE_MODEL` (default `gemini-3.1-flash-image`) and the server-side `GEMINI_API_KEY`; the key is sent in the `x-goog-api-key` header and is never exposed to the Android client. Image model access, quota and billing depend on the Google AI project. Supported aspect ratios are validated server-side; unknown values fall back to `1:1`.

## Docker deployment

Build from the project root with `docker build -f backend/Dockerfile -t maxxa-ai-backend ./backend`. Run with `GEMINI_API_KEY` and `MAXXA_CLIENT_TOKEN` supplied as runtime secrets, and publish container port `8080` behind an HTTPS reverse proxy. Do not bake secrets into the image. Configure `TRUST_PROXY_HOPS` only to the exact number of trusted proxies in front of the Node service.

## Production security requirements

- Deploy only behind HTTPS/TLS. The Android client rejects non-HTTPS backend URLs.
- Set `NODE_ENV=production`, `GEMINI_API_KEY`, and `MAXXA_CLIENT_TOKEN` as host-managed secrets. The server now refuses to start in production if the Gemini key is missing or the app token is shorter than 32 characters / still a placeholder.
- Generate `MAXXA_CLIENT_TOKEN` with a cryptographically secure random generator (at least 32 random bytes; 64 hexadecimal characters is suitable). Put the same value in MAXXA AI's Backend Access Token setting. Never commit it to Git or put it in the APK.
- Keep `TRUST_PROXY_HOPS=0` unless the deployment platform's exact proxy hop count is known. Incorrect proxy trust can undermine IP-based rate limiting.
- Use a hosting provider with network-level TLS, secret storage, request logging controls, and abuse monitoring. The built-in rate limiter is in-memory and per process; use a shared store / edge rate limit for multi-instance public deployments. The app-token is a shared client secret, not per-user identity; it can be extracted from a distributed APK. Do not launch a public multi-user service without account authentication, per-user quotas and abuse controls.
- Rotate the Gemini key and app token immediately if either is exposed. Restrict the Gemini key to the required API where the provider supports it, and configure quota/billing alerts.
- Do not treat the shared app token as per-user authentication: a token embedded in a distributed client can be extracted by a determined attacker. For a public multi-user release, add user sign-in, per-user quotas, revocation, and server-side abuse controls before launch.
