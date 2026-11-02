# Chat & Conversation V32 — one-pass source update

This update addresses the seven source-level improvement areas identified after V31:

1. **Autosave:** observe the complete chat message snapshot and debounce persistence, so streamed chunks, edits, deletes, and attachment changes are saved rather than relying only on `chats.size`.
2. **Attachment data:** reject empty/oversized files (5 MiB cap), preserve attachment payload only on the originating user message, and retain metadata needed to restore it. Payloads remain Base64 in SharedPreferences for backward compatibility; a separate file-store migration is still a future storage optimization, not claimed as implemented.
3. **Previews:** render image previews and bounded text/plain previews; show a clear PDF attachment status where an in-app PDF renderer is not present.
4. **Retry/regenerate:** preserve the original user attachment for retry, retain latest-response guards, and keep the in-flight action lock/cancellation path.
5. **History:** persist the active thread and all thread snapshots through the debounced full-message observer; keep existing rename/search/switch/delete controls.
6. **Markdown:** retain fenced-code copy, table, heading, and list rendering paths.
7. **Errors/recovery:** retain explicit empty-response, request-failure, interrupted-stream, and retry feedback; attachment validation failures surface a readable message.

## Verification

- Repository offline source-contract suite: PASS.
- ZIP integrity: PASS.
- Kotlin/Android compilation: not verified (the repository has no Gradle wrapper in this source package).
- Device/runtime/backend behaviour: not verified; these are intentionally excluded from the development percentage as requested.
