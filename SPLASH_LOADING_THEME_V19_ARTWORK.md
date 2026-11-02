# MAXXA AI V19 — Character-Matched Loading Artwork

## Included source update
- Added 12 separate vertical loading artwork assets cropped from the user's supplied MAXXA AI collage.
- Mapped every Character Theme Gallery entry to its matching artwork: Itachi, Naruto, Sasuke, Kakashi, Madara, Hinata, Sakura, Nezuko, Obito, Minato, Mitsuri, and Nobara.
- Splash background now renders the selected character's actual artwork rather than an emoji placeholder.
- Existing theme palette continues to tint the splash gradient, glow, ring, and progress UI.
- Removed the emoji from the splash theme label; the selected character is represented by the artwork and theme name.
- AI Core Theme remains separate from the normal Character Theme selection.

## Verification boundary
- Checked that all 12 drawable assets exist and every `R.drawable.*_loading_art` reference in `SplashScreen` has a matching resource.
- Checked the ZIP archive integrity.
- Android Gradle compilation, device runtime, and visual comparison on a phone have not been performed.
