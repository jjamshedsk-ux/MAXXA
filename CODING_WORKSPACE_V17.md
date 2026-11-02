# MAXXA AI V17 — Coding Workspace

## Included
- Dedicated Coding Workspace destination in the navigation drawer.
- Multiline monospace code editor with a 500 KB input limit and live character/line counts.
- Language selector for Kotlin, Java, Python, JavaScript, TypeScript, HTML, CSS, C, C++, C#, Go, Rust, SQL, JSON, XML, Bash, Dart, PHP and Swift.
- Starter templates for Kotlin, Android Activity, Python, HTML, JavaScript and C++.
- Draft autosave for the current editor buffer in app-private SharedPreferences.
- Named local draft save/load/delete in app-private storage.
- Import through Android's document picker; export through Create Document.
- Copy and Android share-sheet actions.
- Filename extension updates when the language changes.
- Explicit status and error messages for local file operations.

## Scope and limitations
This is a source-level code editor and file workspace, not a compiler or full IDE. It does not execute arbitrary code, run a debugger, install language toolchains, or build APKs from inside the app. Use Termux, Android Studio, or another suitable development environment to compile and run projects. The editor stores a single source file per draft; it is not a multi-file project tree or Git client.

## Verification
The offline source-contract script checks that the workspace screen, navigation route, editor, templates, local persistence, import/export, copy/share, and capability disclosure are present. Android compilation and on-device behavior still require the user's separate build/device testing.
