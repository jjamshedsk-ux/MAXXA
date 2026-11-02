from pathlib import Path
p = Path('app/src/main/java/com/maxxa/ai/MainActivity.kt')
s = p.read_text()
checks = {
    'debounced full-snapshot autosave': 'snapshotFlow { chats.toList() }' in s and 'delay(350)' in s,
    'attachment empty and size validation': 'require(bytes.isNotEmpty())' in s and 'bytes.size <= 5 * 1024 * 1024' in s,
    'attachment payload stored only on user message': 'if (line.isUser) line.attachment?.let' in s,
    'image attachment preview': 'private fun AttachmentPreview' in s and 'BitmapFactory.decodeByteArray' in s,
    'text attachment preview bounded': 'take(1200)' in s,
    'retry preserves original attachment': 'val retryAttachment = chats.getOrNull(lastUser)?.attachment' in s and 'retryAttachment) { chunk' in s,
    'retry and regenerate actions exist': 'Text("RETRY"' in s and 'Text("REGENERATE"' in s,
    'message deletion clears edit pointer': 'editingMessageIndex = -1; persistThreads()' in s,
    'Markdown code/table renderer retained': 'private fun ChatMessageContent' in s and 'tableRows' in s and 'COPY CODE' in s,
    'clear request failure recovery text': 'Request failed:' in s and '[Connection interrupted. Tap RETRY' in s,
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()):
    raise SystemExit(1)
print(f'All {len(checks)} Chat V32 source checks passed.')
