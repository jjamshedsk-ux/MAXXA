'use strict';

const { createApp } = require('./app');

// Fail closed in production: never expose a public Gemini proxy without both
// the provider credential and a high-entropy app-to-backend bearer token.
if (process.env.NODE_ENV === 'production') {
  const apiKey = process.env.GEMINI_API_KEY || '';
  const clientToken = process.env.MAXXA_CLIENT_TOKEN || '';
  if (!apiKey || /replace_with|your[_-]?key/i.test(apiKey)) {
    throw new Error('GEMINI_API_KEY must be configured as a server secret in production');
  }
  if (Buffer.byteLength(clientToken, 'utf8') < 32 || /replace_with|your[_-]?token/i.test(clientToken)) {
    throw new Error('MAXXA_CLIENT_TOKEN must be a random secret of at least 32 characters in production');
  }
}

const PORT = Number(process.env.PORT || 8080);
if (!Number.isInteger(PORT) || PORT < 1 || PORT > 65535) {
  throw new Error('PORT must be an integer between 1 and 65535');
}

const app = createApp();
const server = app.listen(PORT, '0.0.0.0', () => {
  console.log(`MAXXA AI backend listening on port ${PORT}`);
});

function shutdown(signal) {
  console.log(`Received ${signal}; shutting down MAXXA AI backend`);
  server.close(error => {
    if (error) {
      console.error('Backend shutdown failed:', error);
      process.exitCode = 1;
    }
  });
  setTimeout(() => process.exit(1), 10_000).unref();
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
