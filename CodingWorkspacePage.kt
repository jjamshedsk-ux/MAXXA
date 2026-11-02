package com.maxxa.ai

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private data class CodeTemplate(val title: String, val language: String, val source: String)

@Composable
fun CodingWorkspacePage() {
    // File-local palette avoids accessing MainActivity.kt private properties.
    val Gold = Color(0xFFFFA6C8)
    val White = Color(0xFFF4F6FF)
    val Cyan = Color(0xFFFF426D)
    val Muted = Color(0xFFCC9BAE)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences("maxxa_coding_workspace", 0) }
    val languages = listOf("Kotlin", "Java", "Python", "JavaScript", "TypeScript", "HTML", "CSS", "C", "C++", "C#", "Go", "Rust", "SQL", "JSON", "XML", "Bash", "Dart", "PHP", "Swift")
    val templates = remember {
        listOf(
            CodeTemplate("Kotlin starter", "Kotlin", "fun main() {\n    println(\"Hello, MAXXA!\")\n}\n"),
            CodeTemplate("Android Activity", "Kotlin", "package com.example.app\n\nimport android.os.Bundle\nimport androidx.activity.ComponentActivity\n\nclass MainActivity : ComponentActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n    }\n}\n"),
            CodeTemplate("Python starter", "Python", "def main():\n    print(\"Hello, MAXXA!\")\n\nif __name__ == \"__main__\":\n    main()\n"),
            CodeTemplate("HTML page", "HTML", "<!doctype html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"utf-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n  <title>My page</title>\n  <link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n  <h1>Hello, MAXXA!</h1>\n  <script src=\"app.js\"></script>\n</body>\n</html>\n"),
            CodeTemplate("JavaScript starter", "JavaScript", "function main() {\n  console.log(\"Hello, MAXXA!\");\n}\n\nmain();\n"),
            CodeTemplate("C++ starter", "C++", "#include <iostream>\nusing namespace std;\n\nint main() {\n    cout << \"Hello, MAXXA!\" << endl;\n    return 0;\n}\n")
        )
    }
    var fileName by remember { mutableStateOf(prefs.getString("filename", "Main.kt") ?: "Main.kt") }
    var language by remember { mutableStateOf(prefs.getString("language", "Kotlin") ?: "Kotlin") }
    var source by remember { mutableStateOf(prefs.getString("source", templates[0].source) ?: templates[0].source) }
    var status by remember { mutableStateOf("Draft is stored privately on this device.") }
    var languageMenu by remember { mutableStateOf(false) }
    var templateMenu by remember { mutableStateOf(false) }
    var showFiles by remember { mutableStateOf(false) }
    var savedFiles by remember { mutableStateOf(emptyList<File>()) }
    val folder = remember { File(context.filesDir, "coding_workspace").apply { mkdirs() } }

    fun refreshFiles() { savedFiles = folder.listFiles()?.filter { it.isFile && it.extension == "txt" }?.sortedByDescending { it.lastModified() } ?: emptyList() }
    fun safeName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).trim('.')
        return if (cleaned.isBlank()) "Untitled" else cleaned
    }
    fun persistDraft() {
        prefs.edit().putString("filename", fileName).putString("language", language).putString("source", source).apply()
    }
    fun saveLocal() {
        runCatching {
            val name = safeName(fileName) + ".txt"
            File(folder, name).writeText("// MAXXA_CODE_LANGUAGE=$language\n// MAXXA_CODE_FILENAME=$fileName\n$source", Charsets.UTF_8)
            persistDraft(); refreshFiles(); status = "Saved locally: $name"
        }.onFailure { status = "Save failed: ${it.localizedMessage ?: "storage error"}" }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(source) }
            status = "Exported successfully."
        }.onFailure { status = "Export failed: ${it.localizedMessage ?: "write error"}" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Could not read selected file")
            source = text.take(500_000)
            fileName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Imported.txt"
            persistDraft(); status = "File opened. ${source.length} characters loaded."
        }.onFailure { status = "Open failed: ${it.localizedMessage ?: "read error"}" }
    }
    LaunchedEffect(Unit) { refreshFiles() }
    LaunchedEffect(fileName, language, source) { persistDraft() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MAXXA CODE LAB", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                Text("Coding Workspace", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Surface(color = Color(0x2238D6A5), shape = RoundedCornerShape(30.dp)) { Text("● LOCAL", color = Color(0xFF72E0B5), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) }
        }
        OutlinedTextField(value = fileName, onValueChange = { fileName = it }, label = { Text("File name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF693047), focusedLabelColor = Cyan, unfocusedLabelColor = Muted))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { languageMenu = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) { Text("LANGUAGE: $language") }
                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                    languages.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { language = item; fileName = fileName.substringBeforeLast('.', fileName) + "." + extensionFor(item); languageMenu = false }) }
                }
            }
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { templateMenu = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)) { Text("＋ TEMPLATE") }
                DropdownMenu(expanded = templateMenu, onDismissRequest = { templateMenu = false }) {
                    templates.forEach { template -> DropdownMenuItem(text = { Text(template.title) }, onClick = { language = template.language; fileName = template.title.replace(" ", "_") + "." + extensionFor(template.language); source = template.source; templateMenu = false; status = "${template.title} loaded." }) }
                }
            }
        }
        Surface(color = Color(0xFF100A10), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF653047)), modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().background(Color(0xFF21101A)).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("●", color = Color(0xFFFF6C8E), fontSize = 10.sp)
                    Text("  ${safeName(fileName)}", color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${source.lines().size} lines", color = Muted, fontSize = 10.sp)
                }
                BasicTextField(value = source, onValueChange = { if (it.length <= 500_000) source = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 620.dp).padding(12.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFE9DCE4), fontSize = 13.sp, lineHeight = 19.sp, fontFamily = FontFamily.Monospace), cursorBrush = androidx.compose.ui.graphics.SolidColor(Cyan), decorationBox = { inner ->
                    Box {
                        if (source.isEmpty()) Text("// Start writing your code here…", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        inner()
                    }
                })
            }
        }
        Text("${source.length} characters  •  ${source.lines().size} lines  •  500 KB editor limit", color = Muted, fontSize = 10.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(onClick = { saveLocal() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color(0xFF180710))) { Text("SAVE") }
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(source)); status = "Code copied to clipboard." }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = White)) { Text("COPY") }
            OutlinedButton(onClick = { exportLauncher.launch(safeName(fileName)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = White)) { Text("EXPORT") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/*", "application/json", "application/xml")) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) { Text("OPEN FILE") }
            OutlinedButton(onClick = { showFiles = !showFiles; refreshFiles() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) { Text(if (showFiles) "HIDE DRAFTS" else "MY DRAFTS") }
            OutlinedButton(onClick = {
                runCatching {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, fileName).putExtra(Intent.EXTRA_TEXT, source)
                    context.startActivity(Intent.createChooser(send, "Share code")); status = "Share sheet opened."
                }.onFailure { status = "Sharing unavailable on this device." }
            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) { Text("SHARE") }
        }
        if (showFiles) {
            Text("SAVED LOCAL DRAFTS", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (savedFiles.isEmpty()) Text("No local drafts yet. Tap SAVE to keep one on this device.", color = Muted, fontSize = 11.sp)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(savedFiles, key = { it.name }) { file ->
                    Surface(color = Color(0xFF1A0D15), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(file.name, color = White, fontSize = 11.sp); Text("${file.length()} bytes", color = Muted, fontSize = 9.sp) }
                            TextButton(onClick = { runCatching {
                                val content = file.readText(Charsets.UTF_8)
                                val lines = content.split("\n", limit = 3)
                                language = lines.getOrNull(0)?.removePrefix("// MAXXA_CODE_LANGUAGE=")?.takeIf { it in languages } ?: language
                                fileName = lines.getOrNull(1)?.removePrefix("// MAXXA_CODE_FILENAME=")?.takeIf { it.isNotBlank() } ?: file.name.removeSuffix(".txt")
                                source = lines.getOrNull(2).orEmpty()
                                persistDraft(); status = "Draft loaded."
                            }.onFailure { status = "Could not load draft." } }) { Text("OPEN", color = Cyan) }
                            TextButton(onClick = { file.delete(); refreshFiles(); status = "Draft deleted." }) { Text("DELETE", color = Color(0xFFFF7895)) }
                        }
                    }
                }
            }
        }
        Surface(color = Color(0xFF1A1118), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF563044))) {
            Column(Modifier.padding(12.dp)) {
                Text("WORKSPACE STATUS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(status, color = White, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                Text("Editor, local drafts, import, export, copy and share are available. Code execution, debugging and APK compilation are not built into this workspace; use a suitable IDE or runtime for those tasks.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 7.dp))
            }
        }
    }
}

private fun extensionFor(language: String): String = when (language) {
    "Kotlin" -> "kt"; "Java" -> "java"; "Python" -> "py"; "JavaScript" -> "js"; "TypeScript" -> "ts"; "HTML" -> "html"; "CSS" -> "css"; "C" -> "c"; "C++" -> "cpp"; "C#" -> "cs"; "Go" -> "go"; "Rust" -> "rs"; "SQL" -> "sql"; "JSON" -> "json"; "XML" -> "xml"; "Bash" -> "sh"; "Dart" -> "dart"; "PHP" -> "php"; "Swift" -> "swift"; else -> "txt"
}
