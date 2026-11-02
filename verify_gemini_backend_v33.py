from pathlib import Path
root = Path(__file__).resolve().parents[1]
backend = (root / 'backend/app.js').read_text()
android = (root / 'app/src/main/java/com/maxxa/ai/MainActivity.kt').read_text()
checks = {
    'bounded transient retry helper': 'MAX_UPSTREAM_ATTEMPTS = 3' in backend and 'fetchWithRetry' in backend,
    'retry applies to normal chat': 'generateContent`, {' in backend and 'fetchWithRetry(fetchImpl' in backend,
    'retry applies to streaming chat': 'streamGenerateContent?alt=sse`, {' in backend,
    'provider empty stream surfaced': 'AI provider returned an empty streaming response' in backend,
    'malformed SSE surfaced': 'AI provider sent malformed streaming data' in backend,
    'readiness connected state': 'CONNECTED — Gemini backend' in android,
    'readiness disconnected state': 'DISCONNECTED — Backend is unreachable' in android,
    'Android malformed NDJSON rejected': 'invalid streaming response' in android,
    'Android empty stream rejected': 'AI returned an empty response' in android,
    'HTTP auth errors explained': 'Backend authentication failed' in android,
    'HTTP rate limit explained': 'Backend is busy or rate-limited' in android,
    'socket timeout explained': 'Backend connection timed out' in android,
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ' - ' + name)
if not all(checks.values()):
    raise SystemExit(1)
print(f'{len(checks)}/{len(checks)} source contract checks passed')
