# MAXXA AI — 3D Animated AI Core V25 update

## Source changes
- Refined the Compose Canvas core with layered spherical shading, front-face depth-shaded latitude/longitude mesh, three counter-rotating orbital rings, and 16 orbiting energy nodes.
- Added a press response and an optional normalized `voiceLevel` input hook for amplitude-reactive scaling. The current Voice screen does not yet supply live microphone amplitude, so live waveform synchronization is not claimed.
- Preserved Listening, Processing, Speaking and Idle color states and the existing seven AI Core theme palette integration.
- Kept the renderer dependency-free and responsive to the available Canvas size for a broad Android device range.

## Honest limitations / verification
- This is a higher-fidelity projected 3D-style Canvas renderer, not a true 3D engine scene or mesh renderer (e.g. Filament/OpenGL).
- Live microphone amplitude wiring, Android build, GPU/frame-rate profiling, and physical-device touch/rotation checks remain to be verified by the user in their Android build environment.
- No Android compilation or device test is claimed by this source-only update.
