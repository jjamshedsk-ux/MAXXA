# MAXXA AI Chat & Conversation V31 source update

Seven source-level work areas updated together:
1. Multiline composers: bounded 4-line home prompt and 5-line chat composer; input length capped.
2. Send/stop: request job and cancellation token are shared with stop; retry/regenerate now use the same cancellation token and job slot.
3. Edit/regenerate/delete: existing edit/delete controls retained; edit restores attachment selection; regenerate uses the latest user turn.
4. History/autosave: message serialization now includes attachment metadata and payload, so a restored user turn retains its attachment.
5. Attachments: attachment indicator is shown in the message bubble; retry/regenerate resend the stored attachment.
6. Streaming/context: existing streaming callback retained; retry/regenerate include recent conversation context and cancellation checks.
7. Markdown/code/copy/share: existing renderer and controls retained; multiline and code/table rendering remains enabled.

Validation is source inspection only. Android compilation and device behaviour are not claimed as verified.
