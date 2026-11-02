'use strict';

const express = require('express');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const { timingSafeEqual } = require('node:crypto');

const MAX_MESSAGE_CHARS = 20_000;
const UPSTREAM_TIMEOUT_MS = 55_000;
const MAX_UPSTREAM_BYTES = 8 * 1024 * 1024;
const MAX_IMAGE_PROMPT_CHARS = 4_000;

const RETRYABLE_UPSTREAM_STATUSES = new Set([429, 500, 502, 503, 504]);
const MAX_UPSTREAM_ATTEMPTS = 3;

function delay(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }

async function fetchWithRetry(fetchImpl, url, options, attempts = MAX_UPSTREAM_ATTEMPTS) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      const response = await fetchImpl(url, options);
      if (!RETRYABLE_UPSTREAM_STATUSES.has(response.status) || attempt === attempts) return response;
      const retryAfter = Number(response.headers?.get?.('retry-after') || 0);
      try { await response.body?.cancel(); } catch {}
      await delay(Math.min(retryAfter > 0 ? retryAfter * 1000 : 250 * (2 ** (attempt - 1)), 1500));
    } catch (error) {
      lastError = error;
      if (options.signal?.aborted || attempt === attempts) throw error;
      await delay(250 * (2 ** (attempt - 1)));
    }
  }
  throw lastError || new Error('UPSTREAM_REQUEST_FAILED');
}

async function readTextLimited(response, maxBytes) {
  if (!response.body) return '';
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > maxBytes) {
        await reader.cancel();
        throw new Error('UPSTREAM_RESPONSE_TOO_LARGE');
      }
      chunks.push(Buffer.from(value));
    }
  } finally {
    reader.releaseLock();
  }
  return Buffer.concat(chunks, total).toString('utf8');
}


function buildChatParts(message, attachment) {
  const parts = [{ text: message }];
  if (attachment === undefined || attachment === null) return parts;
  const mimeType = typeof attachment.mimeType === 'string' ? attachment.mimeType.toLowerCase() : '';
  const data = typeof attachment.data === 'string' ? attachment.data : '';
  const allowed = mimeType.startsWith('image/') && ['image/jpeg', 'image/png', 'image/webp'].includes(mimeType)
    || mimeType === 'application/pdf' || mimeType === 'text/plain';
  if (!allowed) throw Object.assign(new Error('Only JPEG, PNG, WebP, PDF and plain-text attachments are supported.'), { statusCode: 415 });
  if (!data || data.length > 7_000_000 || !/^[A-Za-z0-9+/]*={0,2}$/.test(data) || data.length % 4 !== 0) {
    throw Object.assign(new Error('Attachment is invalid or exceeds the 5 MB limit.'), { statusCode: 413 });
  }
  const bytes = Buffer.from(data, 'base64');
  if (!bytes.length || bytes.length > 5 * 1024 * 1024) throw Object.assign(new Error('Attachment is invalid or exceeds the 5 MB limit.'), { statusCode: 413 });
  if (mimeType === 'text/plain') {
    const text = bytes.toString('utf8');
    if (text.includes('\u0000')) throw Object.assign(new Error('Text attachment is not valid UTF-8 text.'), { statusCode: 400 });
    parts.push({ text: `\n\nAttached text file (${String(attachment.name || 'attachment').slice(0, 80)}):\n${text.slice(0, 100_000)}` });
  } else {
    parts.push({ inlineData: { mimeType, data } });
  }
  return parts;
}

function createApp({ apiKey = process.env.GEMINI_API_KEY, clientToken = process.env.MAXXA_CLIENT_TOKEN || '', model = process.env.GEMINI_MODEL || 'gemini-2.5-flash', fetchImpl = global.fetch } = {}) {
  if (clientToken && Buffer.byteLength(clientToken, 'utf8') < 32) {
    throw new Error('MAXXA_CLIENT_TOKEN must contain at least 32 characters when configured');
  }
  const app = express();
  app.disable('x-powered-by');
  app.use(helmet());
  app.use(express.json({ limit: '7mb', strict: true }));
  // Only trust a reverse proxy when explicitly configured by the host operator.
  const trustProxyHops = Number(process.env.TRUST_PROXY_HOPS || 0);
  if (Number.isInteger(trustProxyHops) && trustProxyHops >= 0 && trustProxyHops <= 5) app.set('trust proxy', trustProxyHops);
  app.use('/api/', rateLimit({ windowMs: 60_000, limit: 30, standardHeaders: 'draft-7', legacyHeaders: false }));
  const chatLimiter = rateLimit({ windowMs: 60_000, limit: 12, standardHeaders: 'draft-7', legacyHeaders: false, message: { error: 'Too many chat requests. Please wait a minute and try again.' } });
  const imageLimiter = rateLimit({ windowMs: 60_000, limit: 3, standardHeaders: 'draft-7', legacyHeaders: false, message: { error: 'Too many image requests. Please wait a minute and try again.' } });
  const requireClientToken = (req, res, next) => {
    if (!clientToken) return next(); // Development compatibility; configure a token before public deployment.
    const supplied = (req.get('authorization') || '').replace(/^Bearer\s+/i, '');
    const actual = Buffer.from(supplied, 'utf8');
    const expected = Buffer.from(clientToken, 'utf8');
    if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) {
      return res.status(401).json({ error: 'Unauthorized. Configure the MAXXA backend access token in the app.' });
    }
    return next();
  };

  app.get('/health', (_req, res) => res.json({ ok: true, service: 'MAXXA AI backend', authentication: clientToken ? 'required' : 'not_configured' }));
  app.get('/ready', (_req, res) => {
    const configured = Boolean(apiKey);
    const authenticated = Boolean(clientToken);
    res.status(configured && authenticated ? 200 : 503).json({
      ok: configured && authenticated,
      service: 'MAXXA AI backend',
      geminiConfigured: configured,
      clientAuthenticationConfigured: authenticated,
      model,
    });
  });

  app.post('/api/image', imageLimiter, requireClientToken, async (req, res) => {
    if (!apiKey) return res.status(503).json({ error: 'Backend is not configured. Set GEMINI_API_KEY on the server.' });
    const prompt = typeof req.body?.prompt === 'string' ? req.body.prompt.trim() : '';
    if (!prompt) return res.status(400).json({ error: 'prompt is required' });
    if (prompt.length > MAX_IMAGE_PROMPT_CHARS) return res.status(413).json({ error: `prompt is too long (${MAX_IMAGE_PROMPT_CHARS} characters maximum)` });
    const aspectRatio = ['1:1', '3:2', '2:3', '4:3', '3:4', '5:4', '4:5', '9:16', '16:9'].includes(req.body?.aspectRatio) ? req.body.aspectRatio : '1:1';
    const imageModel = process.env.GEMINI_IMAGE_MODEL || 'gemini-3.1-flash-image';
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 90_000);
    try {
      const upstream = await fetchImpl(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(imageModel)}:generateContent`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'x-goog-api-key': apiKey },
        body: JSON.stringify({
          contents: [{ role: 'user', parts: [{ text: prompt }] }],
          generationConfig: { responseModalities: ['IMAGE'], imageConfig: { aspectRatio } }
        }),
        signal: controller.signal,
      });
      const declaredLength = Number(upstream.headers?.get?.('content-length') || 0);
      if (declaredLength > MAX_UPSTREAM_BYTES) return res.status(502).json({ error: 'Image provider response exceeded the allowed size.' });
      const raw = await readTextLimited(upstream, MAX_UPSTREAM_BYTES);
      let data;
      try { data = JSON.parse(raw); } catch { return res.status(502).json({ error: 'Image provider returned an invalid response.' }); }
      if (!upstream.ok) {
        const providerMessage = typeof data?.error?.message === 'string' ? data.error.message.slice(0, 500) : `Image provider returned HTTP ${upstream.status}`;
        return res.status(upstream.status >= 500 ? 502 : upstream.status).json({ error: providerMessage });
      }
      const parts = data?.candidates?.[0]?.content?.parts || [];
      const image = parts.find(part => part?.inlineData?.data && /^image\/(png|jpeg|webp)$/.test(part.inlineData.mimeType || ''))?.inlineData;
      if (!image) return res.status(502).json({ error: 'Image provider returned no supported image. Check model access and billing/quota.' });
      return res.json({ mimeType: image.mimeType, imageBase64: image.data, model: imageModel });
    } catch (error) {
      if (error?.message === 'UPSTREAM_RESPONSE_TOO_LARGE') {
        return res.status(502).json({ error: 'Image provider response exceeded the allowed size.' });
      }
      return res.status(502).json({ error: error?.name === 'AbortError' ? 'Image generation timed out' : 'Could not reach image provider' });
    } finally { clearTimeout(timeout); }
  });

  app.post('/api/chat', chatLimiter, requireClientToken, async (req, res) => {
    if (!apiKey) return res.status(503).json({ error: 'Backend is not configured. Set GEMINI_API_KEY on the server.' });
    const message = typeof req.body?.message === 'string' ? req.body.message.trim() : '';
    if (!message) return res.status(400).json({ error: 'message is required' });
    if (message.length > MAX_MESSAGE_CHARS) return res.status(413).json({ error: `message is too long (${MAX_MESSAGE_CHARS} characters maximum)` });
    let chatParts;
    try { chatParts = buildChatParts(message, req.body?.attachment); } catch (error) { return res.status(error.statusCode || 400).json({ error: error.message }); }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);
    try {
      const upstream = await fetchWithRetry(fetchImpl, `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', 'x-goog-api-key': apiKey },
        body: JSON.stringify({ contents: [{ role: 'user', parts: chatParts }] }),
        signal: controller.signal,
      });
      const declaredLength = Number(upstream.headers?.get?.('content-length') || 0);
      if (declaredLength > MAX_UPSTREAM_BYTES) return res.status(502).json({ error: 'AI provider response exceeded the allowed size.' });
      let raw;
      try { raw = await readTextLimited(upstream, MAX_UPSTREAM_BYTES); }
      catch (error) {
        if (error?.message === 'UPSTREAM_RESPONSE_TOO_LARGE') return res.status(502).json({ error: 'AI provider response exceeded the allowed size.' });
        throw error;
      }
      let data;
      try { data = JSON.parse(raw); } catch { return res.status(502).json({ error: 'AI provider returned an invalid response.' }); }
      if (!upstream.ok) {
        const providerMessage = typeof data?.error?.message === 'string' ? data.error.message.slice(0, 500) : `AI provider returned HTTP ${upstream.status}`;
        return res.status(upstream.status >= 500 ? 502 : upstream.status).json({ error: providerMessage });
      }
      const text = (data?.candidates?.[0]?.content?.parts || []).map(part => typeof part?.text === 'string' ? part.text : '').filter(Boolean).join('\n').trim();
      if (!text) return res.status(502).json({ error: 'AI provider returned no text' });
      return res.json({ text });
    } catch (error) {
      return res.status(502).json({ error: error?.name === 'AbortError' ? 'AI provider timed out' : 'Could not reach AI provider' });
    } finally {
      clearTimeout(timeout);
    }
  });

  // Streaming chat endpoint: forwards Gemini SSE chunks as newline-delimited JSON.
  app.post('/api/chat/stream', chatLimiter, requireClientToken, async (req, res) => {
    if (!apiKey) return res.status(503).json({ error: 'Backend is not configured. Set GEMINI_API_KEY on the server.' });
    const message = typeof req.body?.message === 'string' ? req.body.message.trim() : '';
    if (!message) return res.status(400).json({ error: 'message is required' });
    if (message.length > MAX_MESSAGE_CHARS) return res.status(413).json({ error: `message is too long (${MAX_MESSAGE_CHARS} characters maximum)` });
    let chatParts;
    try { chatParts = buildChatParts(message, req.body?.attachment); } catch (error) { return res.status(error.statusCode || 400).json({ error: error.message }); }
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);
    let abortForClient = null;
    let reader = null;
    try {
      const upstream = await fetchWithRetry(fetchImpl, `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:streamGenerateContent?alt=sse`, {
        method: 'POST', headers: { 'content-type': 'application/json', 'x-goog-api-key': apiKey },
        body: JSON.stringify({ contents: [{ role: 'user', parts: chatParts }] }), signal: controller.signal,
      });
      if (!upstream.ok || !upstream.body) {
        const body = await upstream.text().catch(() => '');
        let messageText = `AI provider returned HTTP ${upstream.status}`;
        try { messageText = JSON.parse(body)?.error?.message || messageText; } catch {}
        return res.status(upstream.status >= 500 ? 502 : upstream.status).json({ error: messageText.slice(0, 500) });
      }
      res.status(200).set({ 'Content-Type': 'application/x-ndjson; charset=utf-8', 'Cache-Control': 'no-cache, no-transform', 'X-Accel-Buffering': 'no' });
      reader = upstream.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let emittedText = false;
      let clientClosed = false;
      abortForClient = () => {
        if (!res.writableEnded) {
          clientClosed = true;
          controller.abort();
          if (reader) reader.cancel().catch(() => {});
        }
      };
      res.on('close', abortForClient);
      const emitEvent = (event) => {
        const data = event.split(/\r?\n/).filter(line => line.startsWith('data:')).map(line => line.slice(5).trim()).join('\n');
        if (!data || data === '[DONE]' || clientClosed || res.writableEnded) return;
        let parsed; try { parsed = JSON.parse(data); } catch { res.write(JSON.stringify({ error: 'AI provider sent malformed streaming data.' }) + '\n'); return; }
        const providerError = parsed?.error?.message;
        if (providerError) { res.write(JSON.stringify({ error: String(providerError).slice(0, 500) }) + '\n'); return; }
        const chunk = (parsed?.candidates?.[0]?.content?.parts || []).map(part => typeof part?.text === 'string' ? part.text : '').join('');
        if (chunk) { emittedText = true; res.write(JSON.stringify({ text: chunk }) + '\n'); }
      };
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const events = buffer.split(/\r?\n\r?\n/);
        buffer = events.pop() || '';
        for (const event of events) emitEvent(event);
      }
      buffer += decoder.decode();
      if (buffer.trim()) emitEvent(buffer);
      if (!clientClosed && !res.writableEnded) {
        if (!emittedText) res.write(JSON.stringify({ error: 'AI provider returned an empty streaming response. Please retry.' }) + '\n');
        res.end();
      }
      res.off('close', abortForClient);
    } catch (error) {
      if (abortForClient) res.off('close', abortForClient);
      if (!res.destroyed) {
        if (!res.headersSent) res.status(502).json({ error: error?.name === 'AbortError' ? 'AI provider timed out' : 'Could not reach AI provider' });
        else if (!res.writableEnded) { res.write(JSON.stringify({ error: error?.name === 'AbortError' ? 'AI provider timed out' : 'Could not reach AI provider' }) + '\n'); res.end(); }
      }
    } finally {
      if (abortForClient) res.off('close', abortForClient);
      if (reader) { try { await reader.cancel(); } catch {} }
      clearTimeout(timeout);
    }
  });

  app.use((err, _req, res, _next) => {
    if (err?.type === 'entity.too.large') return res.status(413).json({ error: 'Request body is too large.' });
    if (err instanceof SyntaxError && 'body' in err) return res.status(400).json({ error: 'Request body must be valid JSON.' });
    return res.status(500).json({ error: 'Unexpected backend error.' });
  });
  return app;
}

module.exports = { createApp, MAX_MESSAGE_CHARS, MAX_IMAGE_PROMPT_CHARS, MAX_UPSTREAM_BYTES, readTextLimited, fetchWithRetry };
