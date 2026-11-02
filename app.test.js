'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { createApp, MAX_MESSAGE_CHARS, MAX_UPSTREAM_BYTES } = require('../app');

async function withServer(app, run) {
  const server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  try { await run(`http://127.0.0.1:${server.address().port}`); }
  finally { await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve())); }
}

test('health endpoint reports service status', async () => {
  await withServer(createApp({ apiKey: '' }), async base => {
    const response = await fetch(`${base}/health`);
    assert.equal(response.status, 200);
    assert.equal((await response.json()).ok, true);
  });
});


test('readiness endpoint fails closed until Gemini key and client token are configured', async () => {
  await withServer(createApp({ apiKey: '', clientToken: '' }), async base => {
    const response = await fetch(`${base}/ready`);
    assert.equal(response.status, 503);
    const body = await response.json();
    assert.equal(body.geminiConfigured, false);
    assert.equal(body.clientAuthenticationConfigured, false);
    assert.equal(body.ok, false);
  });
});

test('readiness endpoint reports configured production prerequisites without exposing secrets', async () => {
  await withServer(createApp({ apiKey: 'private-gemini-key', clientToken: 'a'.repeat(48) }), async base => {
    const response = await fetch(`${base}/ready`);
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.ok, true);
    assert.equal(JSON.stringify(body).includes('private-gemini-key'), false);
    assert.equal(JSON.stringify(body).includes('a'.repeat(48)), false);
  });
});

test('configured client token is required for chat endpoints', async () => {
  let providerCalled = false;
  const fetchImpl = async () => {
    providerCalled = true;
    return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: 'ok' }] } }] }), { status: 200 });
  };
  await withServer(createApp({ apiKey: 'test-secret', clientToken: 'b'.repeat(48), fetchImpl }), async base => {
    const denied = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: 'hello' }) });
    assert.equal(denied.status, 401);
    assert.equal(providerCalled, false);
    const allowed = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json', authorization: `Bearer ${'b'.repeat(48)}` }, body: JSON.stringify({ message: 'hello' }) });
    assert.equal(allowed.status, 200);
    assert.equal(providerCalled, true);
  });
});

test('streaming chat forwards text chunks as newline-delimited JSON and flushes final event', async () => {
  const fetchImpl = async (url, options) => {
    assert.match(url, /:streamGenerateContent\?alt=sse$/);
    assert.equal(options.headers['x-goog-api-key'], 'stream-secret');
    const encoder = new TextEncoder();
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode('data: {"candidates":[{"content":{"parts":[{"text":"Hello "}]}}]}\n\n'));
        controller.enqueue(encoder.encode('data: {"candidates":[{"content":{"parts":[{"text":"MAXXA"}]}}]}'));
        controller.close();
      }
    });
    return new Response(body, { status: 200, headers: { 'content-type': 'text/event-stream' } });
  };
  await withServer(createApp({ apiKey: 'stream-secret', fetchImpl }), async base => {
    const response = await fetch(`${base}/api/chat/stream`, { method: 'POST', headers: { 'content-type': 'application/json', accept: 'application/x-ndjson' }, body: JSON.stringify({ message: 'hello' }) });
    assert.equal(response.status, 200);
    const rows = (await response.text()).trim().split('\n').map(row => JSON.parse(row));
    assert.deepEqual(rows, [{ text: 'Hello ' }, { text: 'MAXXA' }]);
  });
});

test('chat rejects missing server credentials', async () => {
  await withServer(createApp({ apiKey: '' }), async base => {
    const response = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: 'hello' }) });
    assert.equal(response.status, 503);
  });
});

test('chat rejects empty messages without contacting provider', async () => {
  let called = false;
  await withServer(createApp({ apiKey: 'test', fetchImpl: async () => { called = true; throw new Error('should not call'); } }), async base => {
    const response = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: '  ' }) });
    assert.equal(response.status, 400);
    assert.equal(called, false);
  });
});

test('chat rejects oversized messages', async () => {
  await withServer(createApp({ apiKey: 'test' }), async base => {
    const response = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: 'x'.repeat(MAX_MESSAGE_CHARS + 1) }) });
    assert.equal(response.status, 413);
  });
});

test('chat returns text from provider response', async () => {
  const fetchImpl = async (url, options) => {
    assert.equal(options.method, 'POST');
    assert.match(url, /:generateContent$/);
    assert.equal(options.headers['x-goog-api-key'], 'test-secret');
    assert.equal(url.includes('key='), false, 'API key must not be placed in the URL');
    return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: 'MAXXA reply' }] } }] }), { status: 200, headers: { 'content-type': 'application/json' } });
  };
  await withServer(createApp({ apiKey: 'test-secret', fetchImpl }), async base => {
    const response = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: 'hello' }) });
    assert.equal(response.status, 200);
    assert.equal((await response.json()).text, 'MAXXA reply');
  });
});

test('chat sanitizes provider errors and maps upstream server errors', async () => {
  const fetchImpl = async () => new Response(JSON.stringify({ error: { message: 'temporary failure' } }), { status: 503 });
  await withServer(createApp({ apiKey: 'test', fetchImpl }), async base => {
    const response = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: 'hello' }) });
    assert.equal(response.status, 502);
    assert.match((await response.json()).error, /temporary failure/);
  });
});

test('chat rejects invalid provider JSON', async () => {
  const fetchImpl = async () => new Response('not-json', { status: 200 });
  await withServer(createApp({ apiKey: 'test', fetchImpl }), async base => {
    const response = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: 'hello' }) });
    assert.equal(response.status, 502);
  });
});

test('chat rejects provider response over the configured limit', async () => {
  const fetchImpl = async () => new Response('x'.repeat(MAX_UPSTREAM_BYTES + 1), { status: 200 });
  await withServer(createApp({ apiKey: 'test', fetchImpl }), async base => {
    const response = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: 'hello' }) });
    assert.equal(response.status, 502);
  });
});

test('chat rejects malformed JSON with a client error', async () => {
  await withServer(createApp({ apiKey: 'test' }), async base => {
    const response = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{bad json' });
    assert.equal(response.status, 400);
    assert.match((await response.json()).error, /valid JSON/i);
  });
});

test('chat maps provider timeout to a safe gateway error', async () => {
  const fetchImpl = async () => { const error = new Error('aborted'); error.name = 'AbortError'; throw error; };
  await withServer(createApp({ apiKey: 'test', fetchImpl }), async base => {
    const response = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: 'hello' }) });
    assert.equal(response.status, 502);
    assert.match((await response.json()).error, /timed out/i);
  });
});


test('image generation requires server credentials', async () => {
  await withServer(createApp({ apiKey: '' }), async base => {
    const response = await fetch(`${base}/api/image`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ prompt: 'A landscape' }) });
    assert.equal(response.status, 503);
  });
});

test('image generation rejects empty prompts without contacting provider', async () => {
  let called = false;
  await withServer(createApp({ apiKey: 'test', fetchImpl: async () => { called = true; throw new Error('should not call'); } }), async base => {
    const response = await fetch(`${base}/api/image`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ prompt: '  ' }) });
    assert.equal(response.status, 400);
    assert.equal(called, false);
  });
});

test('image generation returns generated image bytes as base64', async () => {
  const fetchImpl = async (url, options) => {
    assert.match(url, /models\/gemini-3\.1-flash-image:generateContent/);
    assert.equal(options.headers['x-goog-api-key'], 'test-secret');
    const request = JSON.parse(options.body);
    assert.deepEqual(request.generationConfig.responseModalities, ['IMAGE']);
    return new Response(JSON.stringify({ candidates: [{ content: { parts: [{ inlineData: { mimeType: 'image/png', data: 'aGVsbG8=' } }] } }] }), { status: 200, headers: { 'content-type': 'application/json' } });
  };
  await withServer(createApp({ apiKey: 'test-secret', fetchImpl }), async base => {
    const response = await fetch(`${base}/api/image`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ prompt: 'A calm landscape', aspectRatio: '16:9' }) });
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.mimeType, 'image/png');
    assert.equal(body.imageBase64, 'aGVsbG8=');
  });
});

test('image generation rejects provider responses over the configured limit', async () => {
  const fetchImpl = async () => new Response('x'.repeat(MAX_UPSTREAM_BYTES + 1), { status: 200 });
  await withServer(createApp({ apiKey: 'test', fetchImpl }), async base => {
    const response = await fetch(`${base}/api/image`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ prompt: 'A calm landscape' }) });
    assert.equal(response.status, 502);
    assert.match((await response.json()).error, /exceeded the allowed size/i);
  });
});


test('image generation rejects provider response with oversized declared content length', async () => {
  const fetchImpl = async () => new Response('x', { status: 200, headers: { 'content-length': String(MAX_UPSTREAM_BYTES + 1) } });
  await withServer(createApp({ apiKey: 'test', fetchImpl }), async base => {
    const response = await fetch(`${base}/api/image`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ prompt: 'A calm landscape' }) });
    assert.equal(response.status, 502);
    assert.match((await response.json()).error, /exceeded the allowed size/i);
  });
});

test('protected backend rejects missing or incorrect client tokens', async () => {
  await withServer(createApp({ apiKey: 'test', clientToken: 'a'.repeat(40), fetchImpl: async () => { throw new Error('provider must not be called'); } }), async base => {
    const missing = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ message: 'hello' }) });
    assert.equal(missing.status, 401);
    const wrong = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json', authorization: `Bearer ${'b'.repeat(40)}` }, body: JSON.stringify({ message: 'hello' }) });
    assert.equal(wrong.status, 401);
  });
});

test('protected backend accepts the configured bearer token', async () => {
  const fetchImpl = async () => new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: 'Authorized reply' }] } }] }), { status: 200 });
  await withServer(createApp({ apiKey: 'test', clientToken: 'c'.repeat(40), fetchImpl }), async base => {
    const response = await fetch(`${base}/api/chat`, { method: 'POST', headers: { 'content-type': 'application/json', authorization: `Bearer ${'c'.repeat(40)}` }, body: JSON.stringify({ message: 'hello' }) });
    assert.equal(response.status, 200);
    assert.equal((await response.json()).text, 'Authorized reply');
  });
});

test('backend refuses weak configured client tokens', () => {
  assert.throws(() => createApp({ apiKey: 'test', clientToken: 'too-short' }), /at least 32 characters/);
});

test('production server fails closed when secrets are missing', () => {
  const { spawnSync } = require('node:child_process');
  const { join } = require('node:path');
  const result = spawnSync(process.execPath, [join(__dirname, '..', 'server.js')], {
    env: { ...process.env, NODE_ENV: 'production', GEMINI_API_KEY: '', MAXXA_CLIENT_TOKEN: '', PORT: '0' },
    encoding: 'utf8',
    timeout: 5000,
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /GEMINI_API_KEY must be configured/);
});

test('chat attachment validation converts supported text and binary files into Gemini parts', () => {
  const { buildChatParts } = require('../app');
  const text = Buffer.from('hello from file').toString('base64');
  assert.deepEqual(buildChatParts('Please read', { mimeType: 'text/plain', data: text, name: 'note.txt' }), [
    { text: 'Please read' }, { text: '\n\nAttached text file (note.txt):\nhello from file' }
  ]);
  const image = Buffer.from('image-bytes').toString('base64');
  assert.deepEqual(buildChatParts('Describe', { mimeType: 'image/png', data: image }), [
    { text: 'Describe' }, { inlineData: { mimeType: 'image/png', data: image } }
  ]);
  assert.throws(() => buildChatParts('Read', { mimeType: 'application/x-executable', data: text }), /Only JPEG/);
});
