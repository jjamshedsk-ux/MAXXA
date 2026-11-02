# MAXXA AI V13 — Image Studio & Display update

## Added in source
- Dedicated Image Studio card with prompt field and character/art direction style presets: Cinematic, Anime, Photorealistic, 3D Render, Watercolor, Pixel Art, Minimalist and Fantasy.
- Aspect-ratio selector for 1:1, 3:2, 2:3, 4:3, 3:4, 5:4, 4:5, 9:16 and 16:9.
- Generation progress state and clear success/error status.
- Generated-image preview in a bordered display card using fit scaling and bounded height for smaller screens.
- Export through Android's system Create Document picker; preserves PNG/JPEG/WebP extension based on the backend MIME type. No broad storage permission is requested.
- Clears stale preview before a new generation and prevents generation while a request is running or the prompt is empty/too long.

## Requirements / limitations
- Image generation requires a reachable HTTPS MAXXA backend, a configured client token when enabled, provider credentials on the server, and access/quota for the configured Gemini image model.
- Style presets are prompt guidance, not guaranteed exact rendering modes.
- Source-level checks do not prove Kotlin compilation, model availability, or runtime behavior. Android build and phone verification are left to the user.
