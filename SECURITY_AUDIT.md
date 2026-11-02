# MAXXA AI Security Audit — Source-Level Pass

Date: 2026-10-31

## Changes in this pass
- Production backend now fails closed unless `GEMINI_API_KEY` is configured and `MAXXA_CLIENT_TOKEN` is a non-placeholder secret at least 32 bytes long.
- Android backend HTTP requests now refuse redirects. This prevents a redirect response from forwarding the configured bearer token to another host.
- Added a regression test proving production startup fails when secrets are absent.
- Documented secure deployment, token generation, proxy trust, key rotation, rate-limit limitations, and the fact that a shared app token is not per-user authentication.

## Existing protections reviewed
- Gemini provider key is held on the backend and sent in the HTTPS provider request header, not in the URL or Android source.
- Android app disables cleartext traffic and rejects backend URLs that are not HTTPS or contain user-info, query, or fragment components.
- Android backup is disabled; backend access token is encrypted using Android Keystore AES-GCM before it is stored in app preferences.
- Backend uses Helmet, bounded JSON request bodies, message/prompt length limits, per-route and global rate limits, constant-time token comparison, upstream timeouts, bounded upstream response reads, and generic internal error handling.
- Backend listens on all interfaces for container hosting; production deployment must provide HTTPS at the hosting edge.

## Remaining security limitations — do not call this 100% secure
- A shared bearer token inside a distributed Android app can be extracted. It is a basic access gate, not strong user identity. Public release needs user accounts or another per-user auth design, revocation, per-user quotas, and abuse monitoring.
- Built-in rate limits are in-memory and per process. Multi-instance deployment needs a shared rate-limit store or edge/WAF limits.
- Security depends on the deployment host's TLS, secret storage, network restrictions, logging, patching, and quota configuration.
- No independent penetration test, dependency vulnerability audit, live deployment review, or Android runtime/device test was performed in this pass.
- A backend URL configured by the app user is a trust decision; use only a backend operated by the app owner.

## Verification
- `node --check` is run for backend JavaScript files.
- Automated backend tests should be run in GitHub Actions after dependency installation. Local test execution depends on installed npm dependencies.
- Android compilation and device tests are separate pending checks and are not security certification.
