package com.maxxa.ai

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.os.Build
import java.io.File
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore

private val Bg = Color(0xFF090509)
private val Panel = Color(0xFF1A0B12)
private val Cyan = Color(0xFFFF426D)
private val Purple = Color(0xFFFF78B7)
private val Gold = Color(0xFFFFA6C8)
private val White = Color(0xFFF4F6FF)
private val Muted = Color(0xFFCC9BAE)

/** Allows Stop Voice to close an in-flight streaming HTTP connection, not just cancel its coroutine. */
private class VoiceRequestCancellation {
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)
    @Volatile private var cancelled = false

    fun reset() {
        cancelled = false
        activeConnection.getAndSet(null)?.disconnect()
    }

    fun attach(connection: HttpURLConnection) {
        if (cancelled) {
            connection.disconnect()
            throw CancellationException("Voice request cancelled")
        }
        activeConnection.set(connection)
        if (cancelled && activeConnection.compareAndSet(connection, null)) {
            connection.disconnect()
            throw CancellationException("Voice request cancelled")
        }
    }

    fun checkActive() {
        if (cancelled) throw CancellationException("Voice request cancelled")
    }

    fun cancel() {
        cancelled = true
        activeConnection.getAndSet(null)?.disconnect()
    }

    fun detach(connection: HttpURLConnection) {
        activeConnection.compareAndSet(connection, null)
    }
}

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var ignoreSpeechCallback = false
    private var speechSessionId = 0L
    private var activeUtteranceId: String? = null
    private var ttsReady = false
    private var pendingSpeech: Triple<String, String, () -> Unit>? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            Handler(Looper.getMainLooper()).post {
                activeUtteranceId = null
                tts?.stop()
                releaseSpeechAudioFocus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this) { status ->
            Handler(Looper.getMainLooper()).post {
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@post
                    ttsReady = true
                    val preferred = Locale("hi", "IN")
                    val availability = engine.isLanguageAvailable(preferred)
                    engine.language = if (availability >= TextToSpeech.LANG_AVAILABLE) preferred else Locale("en", "IN")
                    pendingSpeech?.let { (text, language, done) ->
                        pendingSpeech = null
                        speakText(text, language, done)
                    }
                } else {
                    ttsReady = false
                    pendingSpeech?.also { it.third() }
                    pendingSpeech = null
                }
            }
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        setContent { MaxxaApp(
            onListen = { language, onResult -> listen(language, onResult) },
            onStopListening = {
                speechSessionId++
                ignoreSpeechCallback = true
                speechRecognizer?.cancel()
                pendingSpeech?.third?.invoke()
                pendingSpeech = null
                tts?.stop()
                releaseSpeechAudioFocus()
            },
            onSpeak = { text, language, onDone -> speakText(text, language, onDone) }
        ) }
    }

    private fun requestSpeechAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .setWillPauseWhenDucked(false)
            .build()
        audioFocusRequest = request
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun releaseSpeechAudioFocus() {
        val manager = audioManager ?: return
        audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    private fun speakText(text: String, language: String, onDone: () -> Unit) {
        if (text.isBlank()) { onDone(); return }
        // A new utterance supersedes the previous one; only its completion may update UI.
        val utteranceId = "maxxa-reply-${System.nanoTime()}"
        activeUtteranceId = utteranceId
        if (!ttsReady || tts == null) {
            pendingSpeech?.third?.invoke()
            pendingSpeech = Triple(text, language, onDone)
            return
        }
        val engine = tts ?: run { onDone(); return }
        if (!requestSpeechAudioFocus()) { activeUtteranceId = null; onDone(); return }
        val locale = when (language) {
            "English" -> Locale("en", "IN")
            "Hindi" -> Locale("hi", "IN")
            else -> Locale("hi", "IN")
        }
        val availability = engine.isLanguageAvailable(locale)
        val selectedLocale = if (availability >= TextToSpeech.LANG_AVAILABLE) locale else Locale("en", "IN")
        engine.language = selectedLocale
        val selectedCharacter = getSharedPreferences("maxxa_settings", MODE_PRIVATE)
            .getString("anime_voice_pack", "Itachi") ?: "Itachi"
        val characterPreset = animeVoicePresets[selectedCharacter] ?: AnimeVoicePreset(0.96f, 0.96f)
        engine.setPitch(characterPreset.pitch)
        engine.setSpeechRate(characterPreset.rate)
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                Handler(Looper.getMainLooper()).post {
                    if (activeUtteranceId == utteranceId) { activeUtteranceId = null; releaseSpeechAudioFocus(); onDone() }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Handler(Looper.getMainLooper()).post {
                    if (activeUtteranceId == utteranceId) { activeUtteranceId = null; releaseSpeechAudioFocus(); onDone() }
                }
            }
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                Handler(Looper.getMainLooper()).post {
                    if (activeUtteranceId == utteranceId) { activeUtteranceId = null; releaseSpeechAudioFocus(); onDone() }
                }
            }
        })
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR && activeUtteranceId == utteranceId) { activeUtteranceId = null; releaseSpeechAudioFocus(); onDone() }
    }

    private fun listen(language: String, onResult: (String) -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onResult("VOICE_ERROR::Microphone permission is missing. Allow it in Android Settings and try again.")
            return
        }
        // Starting voice input interrupts TTS immediately, rather than recording the assistant.
        activeUtteranceId = null
        tts?.stop()
        releaseSpeechAudioFocus()
        val session = ++speechSessionId
        ignoreSpeechCallback = false
        val recognizer = speechRecognizer ?: run { onResult("VOICE_ERROR::Speech recognition is unavailable. Install or enable a speech recognition service in Android settings."); return }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val localeTag = when (language) {
                "English" -> "en-IN"
                "Hindi" -> "hi-IN"
                else -> "hi-IN"
            }
            // For Hinglish, avoid pinning the recognizer to Hindi: some Android speech
            // services then transliterate English words. Keep Hindi as a preference while
            // allowing the service to detect English segments/code-switching itself.
            if (language != "Hindi / Hinglish") putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, when (language) {
                "English" -> "Speak in English"
                "Hindi" -> "हिंदी में बोलें"
                else -> "Hindi aur English mix karke boliye"
            })
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (session != speechSessionId) return
                if (ignoreSpeechCallback) {
                    ignoreSpeechCallback = false
                } else {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error. Please try again."
                        SpeechRecognizer.ERROR_CLIENT -> "Voice service was interrupted. Tap to try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing. Allow it in Android settings."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service network unavailable. Check your internet connection."
                        SpeechRecognizer.ERROR_NO_MATCH -> "I couldn't understand that. Please speak clearly and try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice service is busy. Wait a moment and try again."
                        SpeechRecognizer.ERROR_SERVER -> "Speech service error. Please try again later."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the microphone and speak."
                        else -> "Voice input failed (code $error). Please try again."
                    }
                    onResult("VOICE_ERROR::$message")
                }
            }
            override fun onResults(results: Bundle?) {
                if (session != speechSessionId) return
                if (ignoreSpeechCallback) {
                    ignoreSpeechCallback = false
                } else {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                    if (spoken.isBlank()) onResult("VOICE_ERROR::I couldn't hear that clearly. Please try again.") else onResult(spoken)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                // Partial hypotheses are intentionally not submitted as chat messages.
                // Final recognition remains the only event that triggers Gemini requests.
                if (session == speechSessionId) partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            recognizer.startListening(intent)
        } catch (exception: SecurityException) {
            onResult("VOICE_ERROR::Microphone permission is required. Allow microphone access and try again.")
        } catch (exception: IllegalStateException) {
            onResult("VOICE_ERROR::Voice service is busy. Wait a moment and try again.")
        } catch (exception: RuntimeException) {
            onResult("VOICE_ERROR::Could not start voice input. Please restart the app and try again.")
        }
    }

    override fun onPause() {
        // Speech capture and TTS are foreground interactions only; release mic/audio promptly.
        speechSessionId++
        ignoreSpeechCallback = true
        runCatching { speechRecognizer?.cancel() }
        runCatching { tts?.stop() }
        activeUtteranceId = null
        pendingSpeech?.third?.invoke()
        pendingSpeech = null
        releaseSpeechAudioFocus()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(this) }.getOrNull()
        }
    }

    override fun onDestroy() {
        ignoreSpeechCallback = true
        speechSessionId++
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        tts?.stop()
        pendingSpeech?.third?.invoke()
        pendingSpeech = null
        ttsReady = false
        releaseSpeechAudioFocus()
        tts?.shutdown()
        super.onDestroy()
    }
}

/** Resolves natural Hindi/English/Hinglish voice commands against launchable apps visible to Android. */
private fun launchInstalledApp(context: android.content.Context, spoken: String, onAmbiguous: (List<Triple<String, String, String>>) -> Unit): Boolean {
    fun normalize(value: String): String = java.text.Normalizer.normalize(value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().replace(Regex("\\s+"), " ")

    var query = normalize(spoken)
    if (query.isBlank()) return false

    // Remove common assistant wake words and natural launch verbs without restricting app names.
    val prefixes = listOf("hey maxxa", "ok maxxa", "okay maxxa", "maxxa", "hey", "please", "app", "open", "launch", "start", "chalao", "kholo", "khol do", "kholna", "shuru karo", "shuru kar", "mujhe", "mere liye", "jara", "zara", "bhai", "हे मैक्सा", "हाय मैक्सा", "मैक्सा", "कृपया", "जरा", "ज़रा")
    val suffixes = listOf("please open", "please", "app kholo", "ko kholo", "ko khol do", "khol do", "kholo", "kholna", "open karo", "open kar do", "chalao", "chala do", "chala ke do", "start karo", "shuru karo", "on karo", "kar do", "open kar do", "open karo", "खोलो", "खोल दो", "खोलना", "चालू करो", "चला दो", "चलाओ", "शुरू करो", "ओपन करो", "ओपन कर दो", "ऐप खोलो")
    var changed: Boolean
    do {
        changed = false
        for (prefix in prefixes.sortedByDescending { it.length }) {
            val p = normalize(prefix)
            if (query == p) return false
            if (query.startsWith("$p ")) { query = query.removePrefix("$p ").trim(); changed = true; break }
        }
        if (changed) continue
        for (suffix in suffixes.sortedByDescending { it.length }) {
            val p = normalize(suffix)
            if (query.endsWith(" $p")) { query = query.removeSuffix(" $p").trim(); changed = true; break }
        }
    } while (changed)
    if (query.isBlank()) return false

    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    val candidates = runCatching { pm.queryIntentActivities(launcherIntent, 0) }.getOrDefault(emptyList())
        .mapNotNull { resolveInfo ->
            val activity = resolveInfo.activityInfo ?: return@mapNotNull null
            if (!activity.exported || !activity.enabled || !activity.applicationInfo.enabled) return@mapNotNull null
            val label = runCatching { resolveInfo.loadLabel(pm)?.toString()?.trim().orEmpty() }.getOrDefault("")
            if (label.isBlank()) return@mapNotNull null
            Triple(label, activity.packageName, activity.name)
        }.distinctBy { it.second }

    if (candidates.isEmpty()) return false
    // Common spoken aliases and speech-recognition spellings. These only expand the
    // search; the real installed app label/package must still score strongly.
    val aliases = mapOf(
        "youtube" to listOf("you tube", "youtub", "yutub", "यूट्यूब", "यु ट्यूब", "यूटुब", "यूट्यूब ऐप"),
        "instagram" to listOf("insta", "instagramm", "instgram", "इंस्टाग्राम", "इंस्टा", "इन्स्टाग्राम", "इंस्टाग्राम ऐप"),
        "whatsapp" to listOf("what's app", "watsapp", "whats app", "whatsap", "व्हाट्सएप", "व्हाट्सअप", "वाट्सएप", "व्हाट्स ऐप", "व्हाट्सएप ऐप"),
        "facebook" to listOf("fb", "face book", "फेसबुक", "फेस बुक", "फेसबुक ऐप"),
        "free fire" to listOf("freefire", "free fyre", "फ्री फायर", "फ्रीफायर", "फ्री फायर मैक्स", "फ्रीफायर मैक्स"),
        "chrome" to listOf("google chrome", " krom", "क्रोम", "गूगल क्रोम"),
        "spotify" to listOf("spotifi", "spoti fy", "स्पॉटिफाई", "स्पॉटिफाइ"),
        "google maps" to listOf("maps", "map", "google map", "गूगल मैप्स", "गूगल मैप", "नक्शा"),
        "gmail" to listOf("g mail", " जीमेल", "जिमेल", "जीमेल"),
        "phone" to listOf("dialer", "calling", "फोन", "कॉल", "डायलर"),
        "messages" to listOf("message", "sms", "मैसेज", "मैसेजेस", "संदेश"),
        "camera" to listOf("cam", "कैमरा"),
        "gallery" to listOf("photos", "photo", "गैलरी", "फोटो"),
        "settings" to listOf("setting", "सेटिंग", "सेटिंग्स"),
        "play store" to listOf("playstore", "google play", "प्ले स्टोर", "गूगल प्ले स्टोर"),
        "telegram" to listOf("tele gram", "टेलीग्राम"),
        "snapchat" to listOf("snap chat", "स्नैपचैट"),
        "discord" to listOf("डिस्कॉर्ड"),
        "x" to listOf("twitter", "ट्विटर", "एक्स"),
        "browser" to listOf("internet", "web browser", "ब्राउज़र", "इंटरनेट"),
        "file manager" to listOf("files", "file explorer", "फाइल मैनेजर", "फाइल्स"),
        "clock" to listOf("घड़ी", "अलार्म", "alarm clock")
    )
    // User-defined aliases are stored locally per installed package and survive restarts.
    // Aliases are bound to stable package names, never display labels. This avoids
    // collisions when two apps share a label and keeps aliases attached after relabeling.
    val customAliasesByPackage = runCatching {
        context.getSharedPreferences("maxxa_launcher_aliases", android.content.Context.MODE_PRIVATE)
            .all.mapNotNull { (pkg, raw) ->
                val values = (raw as? String).orEmpty().split('|').map(::normalize).filter { it.isNotBlank() }.toSet()
                if (values.isEmpty() || candidates.none { it.second == pkg }) null else pkg to values
            }.toMap()
    }.getOrDefault(emptyMap())
    val queryVariants = buildSet {
        add(query)
        aliases.forEach { (canonical, spokenForms) ->
            val normalizedForms = spokenForms.map(::normalize)
            if (query == canonical || query in normalizedForms ||
                normalizedForms.any { form -> form.isNotBlank() && (query.startsWith("$form ") || query.endsWith(" $form")) }) {
                add(canonical)
                addAll(normalizedForms)
            }
        }
        // Also compare compact forms for recognizers that merge or split app names.
        if (query.contains(" ")) add(query.replace(" ", ""))
    }

    fun tokenSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a.startsWith(b) || b.startsWith(a)) {
            val shorter = minOf(a.length, b.length).toDouble()
            val longer = maxOf(a.length, b.length).toDouble()
            if (shorter / longer >= 0.55) return 0.94 * shorter / longer + 0.06
        }
        val distance = levenshtein(a, b)
        val similarity = 1.0 - distance.toDouble() / maxOf(a.length, b.length, 1)
        val allowedErrors = when { maxOf(a.length, b.length) >= 9 -> 3; maxOf(a.length, b.length) >= 5 -> 2; else -> 1 }
        return if (distance <= allowedErrors) similarity else 0.0
    }

    fun scoreName(name: String, spoken: String): Double {
        if (name.isBlank() || spoken.isBlank()) return 0.0
        if (name == spoken) return 1.0
        if (name.startsWith(spoken) || spoken.startsWith(name)) {
            val short = minOf(name.length, spoken.length).toDouble()
            val long = maxOf(name.length, spoken.length).toDouble()
            if (short >= 4 && short / long >= 0.60) return 0.92 * short / long + 0.08
        }
        if (name.contains(spoken) || spoken.contains(name)) {
            val short = minOf(name.length, spoken.length)
            if (short >= 5) return 0.88
        }
        val qTokens = spoken.split(' ').filter { it.isNotBlank() }
        val nTokens = name.split(' ').filter { it.isNotBlank() }
        if (qTokens.isEmpty() || nTokens.isEmpty()) return 0.0
        // Every spoken word must find a reasonably close app-name word. This handles
        // small pronunciation/transcription errors without matching unrelated apps.
        // Greedy one-to-one token matching prevents a repeated query word from
        // incorrectly matching the same app-name token multiple times.
        val remaining = nTokens.toMutableList()
        val tokenScores = qTokens.map { q ->
            val bestIndex = remaining.indices.maxByOrNull { index -> tokenSimilarity(q, remaining[index]) }
            if (bestIndex == null) 0.0 else tokenSimilarity(q, remaining.removeAt(bestIndex))
        }
        val coverage = tokenScores.count { it >= 0.62 }.toDouble() / qTokens.size
        val average = tokenScores.average()
        val wholeDistance = 1.0 - levenshtein(spoken.replace(" ", ""), name.replace(" ", "")).toDouble() /
            maxOf(spoken.replace(" ", "").length, name.replace(" ", "").length, 1)
        return maxOf(coverage * 0.72 + average * 0.28, wholeDistance * 0.86)
    }

    val ranked = candidates.map { app ->
        val label = normalize(app.first)
        val packageLabel = normalize(app.second.substringAfterLast('.').replace('_', ' '))
        val packageAliases = customAliasesByPackage[app.second].orEmpty()
        val customAliasScore = packageAliases.maxOfOrNull { alias ->
            when {
                query == alias -> 1.0
                queryVariants.any { it == alias } -> 0.99
                else -> scoreName(alias, query) * 0.99
            }
        } ?: 0.0
        val score = maxOf(customAliasScore, queryVariants.maxOf { variant ->
            maxOf(scoreName(label, variant), scoreName(packageLabel, variant) * 0.96)
        })
        app to score
    }.sortedByDescending { it.second }

    val best = ranked.firstOrNull() ?: return false
    val second = ranked.getOrNull(1)?.second ?: 0.0
    // A slightly lower threshold supports speech typos; a margin still prevents
    // accidentally opening the wrong app when two names sound alike.
    val minimumScore = if (query.replace(" ", "").length <= 3) 0.90 else 0.72
    val ambiguityMargin = if (query.replace(" ", "").length <= 4) 0.14 else 0.10
    if (best.second < minimumScore) return false
    if (second >= minimumScore && best.second - second < ambiguityMargin) {
        onAmbiguous(ranked.takeWhile { it.second >= minimumScore && best.second - it.second < ambiguityMargin }
            .map { it.first }.distinctBy { it.second })
        return true
    }

    val (_, packageName, activityName) = best.first
    val intent = pm.getLaunchIntentForPackage(packageName)
        ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(packageName, activityName)
    return runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.onFailure { error ->
        val message = when (error) {
            is SecurityException -> "Android blocked opening this app. Check whether it is enabled."
            is android.content.ActivityNotFoundException -> "This app has no available launcher screen."
            else -> "Could not open the app. It may be disabled or unavailable."
        }
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }.getOrDefault(false)
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        val current = IntArray(b.length + 1)
        current[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
        }
        previous = current
    }
    return previous[b.length]
}

private fun updateChatOnMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        block()
        return
    }
    val completed = java.util.concurrent.CountDownLatch(1)
    Handler(Looper.getMainLooper()).post {
        try { block() } finally { completed.countDown() }
    }
    if (!completed.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
        throw IllegalStateException("Chat UI update timed out")
    }
}

private enum class VoiceStage { Idle, Listening, Processing, Speaking }

private data class ChatLine(val text: String, val isUser: Boolean, val attachment: ChatAttachment? = null)
private data class ChatAttachment(val name: String, val mimeType: String, val base64: String)
private data class ChatThread(val id: String, val title: String, val messages: List<ChatLine>)

@Composable
private fun MaxxaApp(
    onListen: (String, (String) -> Unit) -> Unit,
    onStopListening: () -> Unit,
    onSpeak: (String, String, () -> Unit) -> Unit
) {
    var showSplash by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf("Home") }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready when you are") }
    var voiceStage by remember { mutableStateOf(VoiceStage.Idle) }
    var pendingLauncherChoices by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    val isListening = voiceStage == VoiceStage.Listening
    val isSpeaking = voiceStage == VoiceStage.Speaking
    var voiceRequestJob by remember { mutableStateOf<Job?>(null) }
    val voiceRequestCancellation = remember { VoiceRequestCancellation() }
    val context = LocalContext.current
    // Keep system Back predictable: close the drawer first, otherwise return to Home.
    BackHandler(enabled = menuOpen || selectedPage != "Home") {
        if (menuOpen) menuOpen = false else selectedPage = "Home"
    }
    if (pendingLauncherChoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingLauncherChoices = emptyList() },
            title = { Text("Which app did you mean?") },
            text = { Text("More than one installed app matches your voice command. Choose the correct one.") },
            confirmButton = {
                Column {
                    pendingLauncherChoices.forEach { candidate ->
                        TextButton(onClick = {
                            val intent = context.packageManager.getLaunchIntentForPackage(candidate.second)
                                ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                                    .setClassName(candidate.second, candidate.third)
                            val launched = runCatching { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent); true }.getOrElse { false }
                            if (!launched) android.widget.Toast.makeText(context, "Could not open ${candidate.first}. The app may be disabled or unavailable.", android.widget.Toast.LENGTH_LONG).show()
                            pendingLauncherChoices = emptyList()
                        }) { Text(candidate.first) }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { pendingLauncherChoices = emptyList() }) { Text("CANCEL") } }
        )
    }
    val prefs = remember { context.getSharedPreferences("maxxa_settings", android.content.Context.MODE_PRIVATE) }
    val apiKey = "" // Provider keys are never stored in the Android app.
    var backendUrl by remember { mutableStateOf(prefs.getString("backend_url", "").orEmpty()) }
    var backendUrlInput by remember { mutableStateOf(backendUrl) }
    var backendToken by remember { mutableStateOf(decryptBackendToken(context, prefs.getString("backend_token_enc", "").orEmpty())) }
    var backendTokenInput by remember { mutableStateOf(backendToken) }
    var keySaved by remember { mutableStateOf(false) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRefresh++
    }
    var voiceEnabled by remember { mutableStateOf(prefs.getBoolean("voice_enabled", false)) }
    var animeVoicePack by remember { mutableStateOf(prefs.getString("anime_voice_pack", "Itachi").orEmpty().ifBlank { "Itachi" }) }
    var selectedCharacterTheme by remember { mutableStateOf(CharacterThemeStore.load(context)) }
    var autoSave by remember { mutableStateOf(prefs.getBoolean("auto_save", true)) }
    var typingIndicator by remember { mutableStateOf(prefs.getBoolean("typing_indicator", true)) }
    var textSize by remember { mutableStateOf(prefs.getString("text_size", "Medium").orEmpty()) }
    var chatStyle by remember { mutableStateOf(prefs.getString("chat_style", "Modern").orEmpty()) }
    var coreTheme by remember { mutableStateOf(prefs.getString("core_theme", "Crimson Black").orEmpty().let { if (it == "Crimson Neon") "Crimson Black" else it }) }
    var customAccentHex by remember { mutableStateOf(prefs.getString("custom_accent_hex", "#FF426D").orEmpty()) }
    var replyLanguage by remember { mutableStateOf(prefs.getString("reply_language", "Hindi / Hinglish").orEmpty()) }
    var backendCheck by remember { mutableStateOf("") }
    var checkingBackend by remember { mutableStateOf(false) }
    val welcomeLine = ChatLine("Namaste! Main MAXXA AI hoon. Bolo, main kaise help karun?", false)
    val threads = remember {
        mutableStateListOf<ChatThread>().apply {
            val stored = runCatching { org.json.JSONArray(prefs.getString("chat_threads", "[]")) }.getOrNull()
            if (stored != null) for (i in 0 until stored.length()) {
                val obj = stored.optJSONObject(i) ?: continue
                val rows = mutableListOf<ChatLine>()
                val messages = obj.optJSONArray("messages") ?: org.json.JSONArray()
                for (j in 0 until messages.length()) {
                    val row = messages.optJSONObject(j) ?: continue
                    val value = row.optString("text")
                    if (value.isNotBlank()) {
                        val a = row.optJSONObject("attachment")
                        val attachment = if (a != null) ChatAttachment(a.optString("name"), a.optString("mimeType"), a.optString("base64")) else null
                        rows.add(ChatLine(value, row.optBoolean("isUser"), attachment))
                    }
                }
                if (rows.isNotEmpty()) add(ChatThread(obj.optString("id", "thread-$i"), obj.optString("title", "Conversation ${i + 1}"), rows))
            }
            if (isEmpty()) {
                val legacy = runCatching { org.json.JSONArray(prefs.getString("chat_history", "[]")) }.getOrNull()
                val rows = mutableListOf<ChatLine>()
                if (legacy != null) for (i in 0 until legacy.length()) {
                    val row = legacy.optJSONObject(i) ?: continue
                    val value = row.optString("text")
                    if (value.isNotBlank()) {
                        val a = row.optJSONObject("attachment")
                        val attachment = if (a != null) ChatAttachment(a.optString("name"), a.optString("mimeType"), a.optString("base64")) else null
                        rows.add(ChatLine(value, row.optBoolean("isUser"), attachment))
                    }
                }
                if (rows.isEmpty()) rows.add(welcomeLine)
                add(ChatThread("thread-${System.currentTimeMillis()}", "Conversation 1", rows))
            }
        }
    }
    var activeThreadId by remember { mutableStateOf(prefs.getString("active_chat_thread", threads.first().id).orEmpty()) }
    if (threads.none { it.id == activeThreadId }) activeThreadId = threads.first().id
    val chats = remember(activeThreadId) {
        mutableStateListOf<ChatLine>().apply { addAll(threads.firstOrNull { it.id == activeThreadId }?.messages ?: listOf(welcomeLine)) }
    }
    var showChatManager by remember { mutableStateOf(false) }
    var showChatSearch by remember { mutableStateOf(false) }
    var chatSearchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var pendingChatAttachment by remember { mutableStateOf<ChatAttachment?>(null) }
    var pendingChatAttachmentError by remember { mutableStateOf("") }
    var jumpToMessageIndex by remember { mutableIntStateOf(-1) }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            pendingChatAttachmentError = ""
            val result = withContext(Dispatchers.IO) { runCatching {
                val mime = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
                require(mime.startsWith("image/") || mime == "application/pdf" || mime == "text/plain") { "Supported attachments: images, PDF and text files." }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read selected file.")
                require(bytes.isNotEmpty()) { "The selected file is empty." }
                require(bytes.size <= 5 * 1024 * 1024) { "Attachment must be 5 MB or smaller." }
                ChatAttachment(uri.lastPathSegment?.substringAfterLast('/')?.take(80)?.ifBlank { "attachment" } ?: "attachment", mime, Base64.encodeToString(bytes, Base64.NO_WRAP))
            } }
            result.onSuccess { pendingChatAttachment = it }.onFailure { pendingChatAttachmentError = it.message ?: "Attachment could not be loaded." }
        }
    }
    val chatCancellation = remember { VoiceRequestCancellation() }
    var chatRequestJob by remember { mutableStateOf<Job?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(threads.firstOrNull { it.id == activeThreadId }?.title.orEmpty()) }
    var editingMessageIndex by remember { mutableIntStateOf(-1) }
    fun persistThreads() {
        if (!autoSave) return
        val currentTitle = threads.firstOrNull { it.id == activeThreadId }?.title ?: "Conversation"
        val snapshot = threads.map { thread ->
            if (thread.id == activeThreadId) thread.copy(title = currentTitle, messages = chats.toList()) else thread
        }
        val json = org.json.JSONArray()
        snapshot.forEach { thread ->
            val rows = org.json.JSONArray()
            thread.messages.forEach { line ->
                val row = JSONObject().put("text", line.text).put("isUser", line.isUser)
                line.attachment?.let { a ->
                    row.put("attachment", JSONObject().put("name", a.name).put("mimeType", a.mimeType).put("base64", a.base64))
                }
                rows.put(row)
            }
            json.put(JSONObject().put("id", thread.id).put("title", thread.title).put("messages", rows))
        }
        prefs.edit().putString("chat_threads", json.toString()).putString("active_chat_thread", activeThreadId).apply()
    }
    // Observe the complete message snapshot, not only list size: streaming chunks,
    // edits and attachment changes all trigger a debounced autosave.
    LaunchedEffect(activeThreadId, autoSave) {
        if (autoSave) snapshotFlow { chats.toList() }
            .distinctUntilChanged()
            .collect {
                delay(350)
                persistThreads()
            }
    }
    // Voice utterances are submitted directly to the same conversation/backend as typed messages.
    fun submitVoiceMessage(rawText: String) {
        val spoken = rawText.trim()
        if (spoken.isBlank()) return
        if (launchInstalledApp(context, spoken) { choices -> pendingLauncherChoices = choices }) {
            status = "Opening app from your voice command…"
            selectedPage = "Voice"
            return
        }
        if (loading) {
            status = "MAXXA is busy answering. Please wait, then speak again."
            return
        }
        selectedPage = "Chat"
        status = "Processing your request…"
        voiceStage = VoiceStage.Processing
        chats.add(ChatLine(spoken, true))
        chats.add(ChatLine("", false))
        loading = true
        voiceRequestCancellation.reset()
        voiceRequestJob = scope.launch {
            val responseIndex = chats.lastIndex
            try {
                val conversationContext = chats.dropLast(1).takeLast(12).joinToString("\n") { line ->
                    (if (line.isUser) "User: " else "MAXXA: ") + line.text
                }
                val prompt = "You are MAXXA AI, a helpful multilingual assistant. Reply primarily in $replyLanguage. The user spoke this message through voice input; understand Hindi, English, and code-switched Hinglish naturally.\n\nRecent conversation:\n$conversationContext"
                withContext(Dispatchers.IO) {
                    askGeminiStream(apiKey, prompt, backendUrl, backendToken, voiceRequestCancellation) { chunk ->
                        Handler(Looper.getMainLooper()).post {
                            if (responseIndex < chats.size) chats[responseIndex] = ChatLine(chats[responseIndex].text + chunk, false)
                        }
                    }
                }
                val answer = chats.getOrNull(responseIndex)?.text.orEmpty()
                if (answer.isBlank()) throw IllegalStateException("The AI returned an empty response.")
                status = "Speaking…"
                voiceStage = VoiceStage.Speaking
                persistThreads()
                onSpeak(answer, replyLanguage) {
                    voiceStage = VoiceStage.Idle
                    status = "Ready when you are"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                voiceStage = VoiceStage.Idle
                val partial = chats.getOrNull(responseIndex)?.text.orEmpty()
                val message = error.message ?: "Check your backend connection and retry."
                chats[responseIndex] = ChatLine(if (partial.isBlank()) "Request failed: $message" else "$partial\n\n[Connection interrupted. Tap RETRY to try again.]", false)
                status = "Couldn't complete the voice request."
                persistThreads()
            } finally {
                loading = false
                voiceRequestJob = null
                persistThreads()
            }
        }
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRefresh++
        if (granted) {
            voiceStage = VoiceStage.Listening
            status = "Listening…"
            selectedPage = "Voice"
            onListen(replyLanguage) { spoken ->
                voiceStage = VoiceStage.Idle
                if (spoken.startsWith("VOICE_ERROR::")) {
                    status = spoken.removePrefix("VOICE_ERROR::")
                } else submitVoiceMessage(spoken)
            }
        } else {
            voiceStage = VoiceStage.Idle
            status = "Microphone permission denied. Allow it in Android Settings to use voice input."
        }
    }

    val pulse = rememberInfiniteTransition(label = "mic-pulse")
    val micScale by pulse.animateFloat(1f, 1.07f, infiniteRepeatable(tween(1250, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "mic-scale")

    LaunchedEffect(Unit) { delay(1750); showSplash = false }

    val coreThemeColors = when (coreTheme) {
        "AMOLED Pure Black" -> Triple(Color(0xFFB8C4D8), Color(0xFF526078), Color(0xFFE6EDF8))
        "Purple Neon" -> Triple(Color(0xFFB56CFF), Color(0xFF713BCE), Color(0xFFE3C5FF))
        "Cyber Blue" -> Triple(Color(0xFF55DFFF), Color(0xFF2866E8), Color(0xFFB9F5FF))
        "Emerald Green" -> Triple(Color(0xFF39E6A0), Color(0xFF087F62), Color(0xFFB7FFE0))
        "Pink Anime" -> Triple(Color(0xFFFF78B7), Color(0xFFE34E91), Color(0xFFFFD0E6))
        "Custom Accent Color" -> runCatching { val c = Color(android.graphics.Color.parseColor(customAccentHex)); Triple(c, c.copy(alpha = 0.72f), c.copy(alpha = 0.42f)) }.getOrDefault(Triple(Cyan, Purple, Gold))
        else -> Triple(Color(0xFFFF426D), Color(0xFF8D173B), Color(0xFFFFA6C8)) // Crimson Black
    }
    val characterThemeColors = characterUiThemes.firstOrNull { it.name == selectedCharacterTheme }?.let { Triple(it.accent, it.secondary, it.highlight) } ?: coreThemeColors
    MaterialTheme(colorScheme = darkColorScheme(
        primary = characterThemeColors.first, secondary = characterThemeColors.second, tertiary = characterThemeColors.third, background = Bg, surface = Panel,
        onPrimary = Bg, onBackground = White, onSurface = White
    )) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A0710), Bg, Color(0xFF080407))))) {
            Image(
                painter = painterResource(id = R.drawable.itachi_splash_art),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.13f),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            AnimatedVisibility(
                visible = showSplash,
                enter = fadeIn(tween(420)),
                exit = fadeOut(tween(520))
            ) {
                val activeSplashTheme = characterUiThemes.firstOrNull { it.name == selectedCharacterTheme } ?: characterUiThemes.first()
                SplashScreen(theme = activeSplashTheme)
            }
            if (!showSplash) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val horizontalInset = if (maxWidth >= 840.dp) 32.dp else if (maxWidth >= 600.dp) 24.dp else 16.dp
                    Column(
                        Modifier.fillMaxWidth()
                            .widthIn(max = 760.dp)
                            .fillMaxHeight()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = horizontalInset)
                    ) {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (selectedPage == "Home") "☰" else "‹", color = Cyan, fontSize = 29.sp, modifier = Modifier.clickable { if (selectedPage == "Home") menuOpen = true else selectedPage = "Home" }.padding(end = 16.dp))
                        if (selectedPage == "Home") {
                            Column(Modifier.weight(1f)) {
                                Text("MAXXA AI", color = White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                                Text("YOUR AI ASSISTANT", color = Cyan, fontSize = 9.sp, letterSpacing = 2.sp)
                            }
                            Text("♙", color = Gold, fontSize = 20.sp, modifier = Modifier.clickable { selectedPage = "Additional Settings" }.padding(horizontal = 8.dp))
                            Text("♧", color = Gold, fontSize = 20.sp, modifier = Modifier.clickable { selectedPage = "Permissions" }.padding(horizontal = 5.dp))
                        } else {
                            Column(Modifier.weight(1f)) {
                                Text(if (selectedPage == "Chat") "MAXXA AI" else selectedPage, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(if (selectedPage == "Chat") "● ONLINE" else "MAXXA AI • PERSONAL ASSISTANT", color = if (selectedPage == "Chat") Color(0xFFFF9FBE) else Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AnimatedContent(
                            targetState = selectedPage,
                            transitionSpec = {
                                (fadeIn(tween(190)) + slideInVertically(tween(190)) { it / 24 }) togetherWith
                                    (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 32 })
                            },
                            label = "maxxa-page-transition"
                        ) { page ->
                            Column(Modifier.fillMaxSize()) {
                                when (page) {
                        "Home" -> {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().height(242.dp),
                                        contentAlignment = Alignment.BottomStart
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.itachi_splash_art),
                                            contentDescription = "MAXXA anime artwork",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                        Box(
                                            Modifier.fillMaxSize().background(
                                                Brush.verticalGradient(listOf(Color(0x22090005), Color(0xAA090509), Color(0xF5090509)))
                                            )
                                        )
                                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                            Text("HELLO, I'M MAXXA", color = Color(0xFFFFA6C8), fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                                            Text("Your AI Assistant", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                                            Text("Ask me anything…", color = Muted, fontSize = 12.sp)
                                        }
                                    }
                                }
                                item {
                                    Surface(
                                        color = Color(0xEE160A11),
                                        shape = RoundedCornerShape(18.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xAAFF426D)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(Modifier.padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(
                                                value = input,
                                                onValueChange = { input = it },
                                                modifier = Modifier.weight(1f),
                                                placeholder = { Text("Ask me anything…", color = Muted) },
                                                minLines = 1, maxLines = 4,
                                                shape = RoundedCornerShape(14.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = Color.Transparent,
                                                    focusedBorderColor = Color.Transparent,
                                                    cursorColor = Cyan
                                                )
                                            )
                                            IconButton(onClick = {
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                    status = "Listening…"
                                                    voiceStage = VoiceStage.Listening
                                                    selectedPage = "Voice"
                                                    onListen(replyLanguage) { spoken ->
                                                        voiceStage = VoiceStage.Idle
                                                        if (spoken.startsWith("VOICE_ERROR::")) {
                                                            status = spoken.removePrefix("VOICE_ERROR::")
                                                        } else submitVoiceMessage(spoken)
                                                    }
                                                } else voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }) { Text("🎙", fontSize = 21.sp) }
                                            IconButton(onClick = { selectedPage = "Chat" }) {
                                                Text("➤", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                item {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                        listOf("Chat" to "☷", "Voice" to "🎙", "Image Generation" to "▧", "Code Generator" to "⌘").forEach { (page, icon) ->
                                            Surface(
                                                modifier = Modifier.weight(1f).height(76.dp).clickable { selectedPage = page },
                                                color = Panel,
                                                shape = RoundedCornerShape(16.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x77FF426D))
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                    Text(icon, color = Cyan, fontSize = 22.sp)
                                                    Text(if (page == "Image Generation") "Image" else if (page == "Code Generator") "Code" else page, color = White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                }
                                item {
                                    Text("QUICK ACTIONS", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        QuickCard("▣  GENERATE CODE", "Create a program", Modifier.weight(1f)) { selectedPage = "Code Generator" }
                                        QuickCard("▤  SUMMARIZE", "Key points", Modifier.weight(1f)) { selectedPage = "Summarize" }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        QuickCard("⇄  TRANSLATE", "Hindi • English", Modifier.weight(1f)) { selectedPage = "Translate" }
                                        QuickCard("✎  WRITE ANYTHING", "Messages and ideas", Modifier.weight(1f)) { selectedPage = "Chat" }
                                    }
                                }
                                item {
                                    Text("✦  SMARTER CONVERSATIONS WITH MAXXA AI  ✦", color = Muted, fontSize = 9.sp, letterSpacing = 0.7.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                }
                            }
                        }
                        "Chat" -> {
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("CONVERSATION", color = Gold, fontSize = 12.sp, letterSpacing = 2.sp)
                                    Text(threads.firstOrNull { it.id == activeThreadId }?.title ?: "Chat", color = Muted, fontSize = 10.sp)
                                }
                                Text("SEARCH", color = Muted, fontSize = 9.sp, modifier = Modifier.clickable { chatSearchQuery = ""; showChatSearch = true }.padding(7.dp))
                                Text("CHATS", color = Cyan, fontSize = 10.sp, modifier = Modifier.clickable(enabled = !loading) { showChatManager = true }.padding(8.dp))
                                Text("NEW +", color = White, fontSize = 10.sp, modifier = Modifier.clickable(enabled = !loading) {
                                    persistThreads()
                                    val id = "thread-${System.currentTimeMillis()}"
                                    threads.add(0, ChatThread(id, "Conversation ${threads.size + 1}", listOf(welcomeLine)))
                                    activeThreadId = id
                                    input = ""
                                }.padding(8.dp))
                                Text("✎", color = Gold, fontSize = 17.sp, modifier = Modifier.clickable(enabled = !loading) {
                                    renameValue = threads.firstOrNull { it.id == activeThreadId }?.title.orEmpty()
                                    showRenameDialog = true
                                }.padding(8.dp))
                            }
                            val clipboard = LocalClipboardManager.current
                            val chatListState = rememberLazyListState()
                            LaunchedEffect(chats.size, loading) { if (chats.isNotEmpty()) chatListState.animateScrollToItem(chats.lastIndex) }
                            LaunchedEffect(activeThreadId, jumpToMessageIndex) { if (jumpToMessageIndex >= 0 && chats.isNotEmpty()) { chatListState.animateScrollToItem(jumpToMessageIndex.coerceIn(0, chats.lastIndex)); jumpToMessageIndex = -1 } }
                            val latestAssistantIndex = chats.indexOfLast { !it.isUser }
                            LazyColumn(state = chatListState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(chats) { messageIndex, line ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.isUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                                        Surface(color = if (line.isUser) Color(0xFF57172A) else Panel, shape = if (chatStyle == "Classic") RoundedCornerShape(8.dp) else RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 560.dp)) {
                                            Column(Modifier.padding(13.dp)) {
                                                line.attachment?.let { AttachmentPreview(it) }
                                                ChatMessageContent(line.text, textSize)
                                                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    if (line.isUser) Text("EDIT", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !loading) {
                                                        input = line.text.substringAfter("\n", line.text).takeIf { line.text.startsWith("[Attachment:") } ?: line.text
                                                        pendingChatAttachment = line.attachment
                                                        editingMessageIndex = messageIndex
                                                    }.padding(top = 7.dp))
                                                    Text("DELETE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !loading) { chats.removeAt(messageIndex); editingMessageIndex = -1; persistThreads() }.padding(top = 7.dp))
                                                    if (!line.isUser) {
                                                        Text("COPY", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { clipboard.setText(AnnotatedString(line.text)) }.padding(top = 7.dp))
                                                        Text("SHARE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                                            runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, line.text), "Share MAXXA response")) }
                                                        }.padding(top = 7.dp))
                                                        val retryable = messageIndex == latestAssistantIndex && (line.text.startsWith("Request failed:") || line.text.startsWith("Retry failed:") || line.text.contains("[Connection interrupted."))
                                                        if (retryable) Text("RETRY", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !loading) {
                                                            val lastUser = chats.indexOfLast { it.isUser }
                                                            if (lastUser >= 0 && !loading) {
                                                                val retryAttachment = chats.getOrNull(lastUser)?.attachment
                                                                while (chats.size > lastUser + 1) chats.removeAt(chats.lastIndex)
                                                                val retryIndex = chats.size
                                                                chats.add(ChatLine("", false)); loading = true
                                                                chatCancellation.reset()
                                                                chatRequestJob = scope.launch {
                                                                    try {
                                                                        val ctx = chats.dropLast(1).takeLast(12).joinToString("\n") { row -> (if (row.isUser) "User: " else "MAXXA: ") + row.text }
                                                                        withContext(Dispatchers.IO) { askGeminiStream(apiKey, "You are MAXXA AI. Reply primarily in $replyLanguage.\n\n$ctx", backendUrl, backendToken, chatCancellation, retryAttachment) { chunk -> updateChatOnMain { if (retryIndex < chats.size) chats[retryIndex] = ChatLine(chats[retryIndex].text + chunk, false) } } }
                                                                    } catch (e: Exception) {
                                                                        val errorText = "Retry failed: ${e.message ?: "Check your connection."}"
                                                                        withContext(Dispatchers.Main) { if (retryIndex < chats.size) chats[retryIndex] = ChatLine(errorText, false) }
                                                                    } finally { loading = false; persistThreads(); chatRequestJob = null }
                                                                }
                                                            }
                                                        }.padding(top = 7.dp))
                                                        if (messageIndex == latestAssistantIndex) Text("REGENERATE", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !loading) {
                                                            val lastUser = chats.indexOfLast { it.isUser }
                                                            if (lastUser >= 0 && !loading) {
                                                                while (chats.size > lastUser + 1) chats.removeAt(chats.lastIndex)
                                                                loading = true
                                                                chatCancellation.reset()
                                                                chatRequestJob = scope.launch {
                                                                    try {
                                                                        val contextText = chats.takeLast(12).joinToString("\n") { row -> (if (row.isUser) "User: " else "MAXXA: ") + row.text }
                                                                        val responseIndex = chats.size
                                                                        chats.add(ChatLine("", false))
                                                                        withContext(Dispatchers.IO) { askGeminiStream(apiKey, "You are MAXXA AI. Reply primarily in $replyLanguage.\n\n$contextText", backendUrl, backendToken, chatCancellation, chats.lastOrNull { it.isUser }?.attachment) { chunk -> updateChatOnMain { if (responseIndex < chats.size) chats[responseIndex] = ChatLine(chats[responseIndex].text + chunk, false) } } }
                                                                    } catch (e: Exception) {
                                                                        val errorText = "Request failed: ${e.message ?: "Check your connection and retry."}"
                                                                        val target = chats.indexOfLast { !it.isUser }
                                                                        if (target >= 0) chats[target] = ChatLine(errorText, false)
                                                                        else chats.add(ChatLine(errorText, false))
                                                                    } finally { loading = false; persistThreads(); chatRequestJob = null }
                                                                }
                                                            }
                                                        }.padding(top = 7.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (loading && typingIndicator) item {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = characterThemeColors.first)
                                        Text("MAXXA is thinking…", color = characterThemeColors.first, fontSize = 13.sp)
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Explain", "Code", "Translate", "More").forEach { action ->
                                    Surface(color = Color(0xEE1A0B12), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x88FF426D)), modifier = Modifier.weight(1f).clickable {
                                        when (action) {
                                            "Code" -> selectedPage = "Code Generator"
                                            "Translate" -> selectedPage = "Translate"
                                            "Explain" -> input = if (input.isBlank()) "Explain this in simple words: " else "Explain: $input"
                                            else -> menuOpen = true
                                        }
                                    }) { Text(action, color = White, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
                                }
                            }
                            if (pendingChatAttachment != null) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text("📎 ${pendingChatAttachment?.name}", color = Cyan, fontSize = 10.sp, modifier = Modifier.weight(1f)); Text("REMOVE", color = Gold, fontSize = 9.sp, modifier = Modifier.clickable { pendingChatAttachment = null }.padding(6.dp)) }
                            if (pendingChatAttachmentError.isNotBlank()) Text(pendingChatAttachmentError, color = Gold, fontSize = 10.sp)
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { attachmentPicker.launch("*/*") }, enabled = !loading) { Text("＋", color = Cyan, fontSize = 20.sp) }
                                OutlinedTextField(value = input, onValueChange = { input = it.take(12000) }, modifier = Modifier.weight(1f), placeholder = { Text("Type a message…") }, shape = RoundedCornerShape(18.dp), minLines = 1, maxLines = 5)
                                Spacer(Modifier.width(8.dp))
                                if (loading) {
                                    OutlinedButton(onClick = { chatCancellation.cancel(); chatRequestJob?.cancel() }, shape = CircleShape, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold)) { Text("■") }
                                } else Button(enabled = input.isNotBlank() || pendingChatAttachment != null, onClick = {
                                    val attachment = pendingChatAttachment
                                    val typedMessage = input.trim().ifBlank { "Please analyze the attached file." }; val msg = if (attachment != null) "[Attachment: ${attachment.name}]\n$typedMessage" else typedMessage; input = ""; pendingChatAttachment = null
                                    if (editingMessageIndex >= 0 && editingMessageIndex < chats.size) {
                                        chats[editingMessageIndex] = ChatLine(msg, true, attachment)
                                        while (chats.size > editingMessageIndex + 1) chats.removeAt(chats.lastIndex)
                                    } else chats.add(ChatLine(msg, true, attachment))
                                    editingMessageIndex = -1
                                    chats.add(ChatLine("", false)); loading = true
                                    chatCancellation.reset()
                                    chatRequestJob = scope.launch {
                                        val responseIndex = chats.lastIndex
                                        try {
                                            val conversationContext = chats.dropLast(1).takeLast(12).joinToString("\n") { line -> (if (line.isUser) "User: " else "MAXXA: ") + line.text }
                                            val prompt = "You are MAXXA AI, a helpful multilingual assistant. Reply primarily in $replyLanguage unless the user explicitly requests another language. Preserve conversation context, use Markdown headings/lists/tables when useful, and fenced code blocks for code.\n\nRecent conversation:\n$conversationContext"
                                            withContext(Dispatchers.IO) {
                                                askGeminiStream(apiKey, prompt, backendUrl, backendToken, chatCancellation, attachment) { chunk ->
                                                    Handler(Looper.getMainLooper()).post {
                                                        if (responseIndex < chats.size) chats[responseIndex] = ChatLine(chats[responseIndex].text + chunk, false)
                                                    }
                                                }
                                            }
                                            val answer = chats[responseIndex].text
                                            if (answer.isBlank()) throw IllegalStateException("The AI returned an empty response.")
                                            persistThreads()
                                            if (voiceEnabled) onSpeak(answer, replyLanguage) { }
                                        } catch (cancelled: CancellationException) {
                                            val partial = chats.getOrNull(responseIndex)?.text.orEmpty()
                                            if (responseIndex < chats.size) chats[responseIndex] = ChatLine(if (partial.isBlank()) "Generation stopped." else "$partial\n\n[Generation stopped.]", false)
                                        } catch (error: Exception) {
                                            val partial = chats.getOrNull(responseIndex)?.text.orEmpty()
                                            chats[responseIndex] = ChatLine(if (partial.isBlank()) "Request failed: ${error.message ?: "Check your connection and retry."}" else "$partial\n\n[Connection interrupted. Tap RETRY to try again.]", false)
                                            persistThreads()
                                        } finally { loading = false; persistThreads(); chatRequestJob = null }
                                    }
                                }, shape = CircleShape, contentPadding = PaddingValues(14.dp)) { Text("➤") }
                            }
                        }
                        "Conversation" -> ConversationHistoryPage(
                            messages = chats,
                            onOpenConversation = { selectedPage = "Chat" },
                            onClearHistory = {
                                chats.clear(); chats.add(welcomeLine)
                                val current = threads.firstOrNull { it.id == activeThreadId }
                                threads.clear()
                                threads.add(ChatThread(current?.id ?: "thread-${System.currentTimeMillis()}", "Conversation 1", listOf(welcomeLine)))
                                activeThreadId = threads.first().id
                                prefs.edit().remove("chat_threads").putString("chat_history", "[]").apply()
                            }
                        )
                        "Voice" -> {
                            Spacer(Modifier.height(22.dp))
                            Text("VOICE ASSISTANT", color = Gold, fontSize = 11.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.Bold)
                            Text("Speak to MAXXA", color = White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 7.dp))
                            Text("Hindi, Hinglish ya English mein bolo. Main sun raha hoon.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 7.dp))
                            Spacer(Modifier.weight(1f))
                            BoxWithConstraints(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                                val coreSize = maxWidth.coerceAtMost(286.dp)
                                Image(painter = painterResource(id = R.drawable.itachi_splash_art), contentDescription = null, modifier = Modifier.fillMaxSize().alpha(0.38f), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66090005), Color(0xAA090509), Color(0xEE090509)))))
                                AiCore3D(
                                    voiceStage = when { isListening -> "Listening"; loading -> "Processing"; isSpeaking -> "Speaking"; else -> "Idle" },
                                    modifier = Modifier.size(coreSize),
                                    corePrimary = coreThemeColors.first,
                                    coreSecondary = coreThemeColors.second,
                                    onActivate = {
                                        if (voiceStage == VoiceStage.Idle && !loading) {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                status = "Listening…"
                                                voiceStage = VoiceStage.Listening
                                                onListen(replyLanguage) { spoken ->
                                                    voiceStage = VoiceStage.Idle
                                                    if (spoken.startsWith("VOICE_ERROR::")) status = spoken.removePrefix("VOICE_ERROR::") else submitVoiceMessage(spoken)
                                                }
                                            } else voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                )
                                Button(enabled = voiceStage == VoiceStage.Idle && !loading, onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        status = "Listening…"
                                        voiceStage = VoiceStage.Listening
                                        onListen(replyLanguage) { spoken ->
                                            voiceStage = VoiceStage.Idle
                                            if (spoken.startsWith("VOICE_ERROR::")) {
                                                status = spoken.removePrefix("VOICE_ERROR::")
                                            } else submitVoiceMessage(spoken)
                                        }
                                    } else voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }, modifier = Modifier.size(148.dp).scale(if (isListening) micScale else 1f), shape = CircleShape, border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF9FBE)), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF68162F), contentColor = White)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(when { isListening -> "LISTENING…"; loading -> "PROCESSING…"; isSpeaking -> "SPEAKING…"; else -> "TAP TO SPEAK" }, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                val heights = listOf(10, 18, 28, 16, 36, 22, 42, 18, 30, 14, 24, 10, 18)
                                heights.forEachIndexed { index, height ->
                                    val activeVoiceStage = isListening || loading || isSpeaking
                                    Box(Modifier.padding(horizontal = 2.dp).width(3.dp).height((if (activeVoiceStage) height + (index % 3) * 4 else 8).dp).background(if (activeVoiceStage) Cyan else Color(0xFF9A526D), RoundedCornerShape(4.dp)))
                                }
                            }
                            Text(status, color = Cyan, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center, maxLines = 2)
                            if (isListening || loading || isSpeaking) {
                                OutlinedButton(onClick = {
                                    onStopListening()
                                    voiceRequestCancellation.cancel()
                                    voiceRequestJob?.cancel()
                                    voiceRequestJob = null
                                    loading = false
                                    voiceStage = VoiceStage.Idle
                                    status = "Voice stopped."
                                }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) {
                                    Text("■  STOP VOICE", fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                        }
                        "Permissions" -> {
                            // Read permission state again after every system permission dialog.
                            // Request only permissions declared in the manifest and used by this build.
                            // Sensitive SMS, call, location and calendar access is intentionally not requested.
                            val permissionRows = listOf(
                                PermissionRow("🎙", "Microphone", "Voice input", listOf(Manifest.permission.RECORD_AUDIO))
                            )
                            Column(Modifier.fillMaxWidth().weight(1f)) {
                                Text("PERMISSIONS", color = Gold, fontSize = 12.sp, letterSpacing = 2.sp)
                                Text("Required for full features", color = Cyan, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
                                    items(permissionRows) { row ->
                                        val granted = row.permissions.isNotEmpty() && row.permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                                        Surface(
                                            color = Color(0xEE1A0B12),
                                            shape = RoundedCornerShape(13.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (granted) Color(0xFFFF6A91) else Color(0xFF8C2948)),
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                if (row.permissions.isEmpty()) {
                                                    permissionRefresh++
                                                } else {
                                                    permissionLauncher.launch(row.permissions.toTypedArray())
                                                }
                                            }
                                        ) {
                                            Row(Modifier.padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(Modifier.size(38.dp).background(if (granted) Color(0x33FF426D) else Color(0x33FF4E86), CircleShape), contentAlignment = Alignment.Center) {
                                                    Text(row.icon, color = if (granted) Cyan else Color(0xFFFF7DAB), fontSize = 18.sp)
                                                }
                                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                                    Text(row.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                    Text(row.subtitle, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(if (granted) "Allowed ✓" else "Not allowed ×", color = if (granted) Color(0xFFFF9FBE) else Color(0xFFFF628A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    Text(if (granted) "Granted" else "Enable", color = Cyan, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                                OutlinedButton(onClick = {
                                    runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) }
                                }, modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(48.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) {
                                    Text("⚙  MANAGE PERMISSIONS", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                }
                                Text("Only enable permissions for features you use. SMS and phone permissions are sensitive.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                            }
                        }
                        "AI Core Theme" -> AiCoreThemePage(
                            coreTheme = coreTheme,
                            customAccentHex = customAccentHex,
                            onThemeChange = { value -> coreTheme = value; prefs.edit().putString("core_theme", value).apply() },
                            onCustomAccentChange = { value -> customAccentHex = value; prefs.edit().putString("custom_accent_hex", value).apply() }
                        )
                        "Character Theme Gallery" -> CharacterThemeGalleryPage(
                            selectedTheme = selectedCharacterTheme,
                            onThemeSelected = { name ->
                                if (CharacterThemeStore.save(context, name)) {
                                    selectedCharacterTheme = name
                                } else {
                                    android.widget.Toast.makeText(context, "Theme save nahi ho paya. Dobara try karo.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        "Voice Pack" -> {
                            VoicePackPage(
                                language = replyLanguage,
                                voiceEnabled = voiceEnabled,
                                onLanguageChange = { lang -> replyLanguage = lang; prefs.edit().putString("reply_language", lang).apply() },
                                onVoiceChange = { enabled -> voiceEnabled = enabled; prefs.edit().putBoolean("voice_enabled", enabled).apply() },
                                selectedAnimeVoice = animeVoicePack,
                                onAnimeVoiceChange = { name -> animeVoicePack = name; prefs.edit().putString("anime_voice_pack", name).apply() },
                                onPreview = { sample, lang -> onSpeak(sample, lang) { } }
                            )
                        }
                        "API Key" -> {
                            Spacer(Modifier.height(18.dp))
                            Text("SECURE GEMINI CONNECTION", color = Gold, fontSize = 11.sp, letterSpacing = 2.5.sp)
                            Text("Connect your backend", color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            Text("MAXXA does not store a Gemini API key on your phone. Deploy the included backend, set GEMINI_API_KEY as a server secret, then enter its HTTPS URL here.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                            Surface(color = Panel, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FF426D)), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("BACKEND HTTPS URL", color = Cyan, fontSize = 11.sp, letterSpacing = 1.2.sp)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(value = backendUrlInput, onValueChange = { backendUrlInput = it; keySaved = false }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("https://your-server.example.com") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                                    Text("OPTIONAL BACKEND ACCESS TOKEN", color = Cyan, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 14.dp))
                                    OutlinedTextField(value = backendTokenInput, onValueChange = { backendTokenInput = it; keySaved = false }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Paste the server MAXXA_CLIENT_TOKEN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp))
                                    Text("If MAXXA_CLIENT_TOKEN is enabled on your server, paste the same token here. It is encrypted with Android Keystore on this device.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 6.dp))
                                    Button(onClick = {
                                        val candidate = backendUrlInput.trim().trimEnd('/')
                                        if (isValidHttpsBackendUrl(candidate)) {
                                            backendUrl = candidate
                                            backendToken = backendTokenInput.trim()
                                            val encryptedToken = runCatching { encryptBackendToken(context, backendToken) }.getOrElse { "" }
                                            prefs.edit().putString("backend_url", backendUrl).remove("gemini_api_key").apply {
                                                if (backendToken.isBlank()) remove("backend_token_enc") else putString("backend_token_enc", encryptedToken)
                                            }.apply()
                                            keySaved = backendToken.isBlank() || encryptedToken.isNotBlank()
                                        } else {
                                            // Do not leave an older, previously saved endpoint active after an invalid save.
                                            backendUrl = ""
                                            backendToken = ""
                                            backendTokenInput = ""
                                            prefs.edit().remove("backend_url").remove("backend_token_enc").apply()
                                            backendCheck = ""
                                            keySaved = false
                                        }
                                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(46.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Bg)) { Text("SAVE HTTPS BACKEND", fontWeight = FontWeight.Bold) }
                                    Text("Only HTTPS URLs are accepted. API credentials stay on the server.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                                    OutlinedButton(
                                        enabled = backendUrl.isNotBlank() && !checkingBackend,
                                        onClick = {
                                            checkingBackend = true
                                            backendCheck = "Checking secure backend…"
                                            scope.launch {
                                                backendCheck = withContext(Dispatchers.IO) { checkBackendHealth(backendUrl) }
                                                checkingBackend = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(46.dp),
                                        shape = RoundedCornerShape(15.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)
                                    ) { Text(if (checkingBackend) "CHECKING…" else "TEST BACKEND CONNECTION", fontWeight = FontWeight.Bold) }
                                    if (backendCheck.isNotBlank()) Text(backendCheck, color = if (backendCheck.startsWith("✓")) Color(0xFFFF9FBE) else Color(0xFFFF8A9E), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                                    OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) } }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) { Text("OPEN GOOGLE AI STUDIO", fontWeight = FontWeight.Bold) }
                                    if (keySaved) Text("✓ Secure backend URL saved", color = Color(0xFFFF9FBE), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                                }
                            }
                        }
                        "Preferences" -> PreferencesPage(
                            language = replyLanguage,
                            voiceEnabled = voiceEnabled,
                            autoSave = autoSave,
                            typingIndicator = typingIndicator,
                            textSize = textSize,
                            chatStyle = chatStyle,
                            coreTheme = coreTheme,
                            customAccentHex = customAccentHex,
                            onCustomAccentChange = { value -> customAccentHex = value; prefs.edit().putString("custom_accent_hex", value).apply() },
                            onCoreThemeChange = { value -> coreTheme = value; prefs.edit().putString("core_theme", value).apply() },
                            onTextSizeChange = { textSize = it; prefs.edit().putString("text_size", it).apply() },
                            onChatStyleChange = { chatStyle = it; prefs.edit().putString("chat_style", it).apply() },
                            onLanguageChange = { lang -> replyLanguage = lang; prefs.edit().putString("reply_language", lang).apply() },
                            onVoiceChange = { enabled -> voiceEnabled = enabled; prefs.edit().putBoolean("voice_enabled", enabled).apply() },
                            onAutoSaveChange = { enabled -> autoSave = enabled; prefs.edit().putBoolean("auto_save", enabled).apply() },
                            onTypingIndicatorChange = { enabled -> typingIndicator = enabled; prefs.edit().putBoolean("typing_indicator", enabled).apply() }
                        )
                        "Language Selection" -> LanguageSelectionPage(
                            language = replyLanguage,
                            onLanguageChange = { lang -> replyLanguage = lang; prefs.edit().putString("reply_language", lang).apply() },
                            onContinue = { selectedPage = "Home" }
                        )
                        "Automation" -> AutomationPage()
                        "Phone Control" -> PhoneControlPage()
                        "Coding Workspace" -> CodingWorkspacePage()
                        "Installed Apps" -> InstalledAppsPage()
                        "Additional Settings" -> AdditionalSettingsPage(
                            backendConfigured = backendUrl.isNotBlank(),
                            historyCount = chats.size,
                            onClearHistory = {
                                chats.clear(); chats.add(welcomeLine)
                                val current = threads.firstOrNull { it.id == activeThreadId }
                                threads.clear()
                                threads.add(ChatThread(current?.id ?: "thread-${System.currentTimeMillis()}", "Conversation 1", listOf(welcomeLine)))
                                activeThreadId = threads.first().id
                                prefs.edit().remove("chat_threads").putString("chat_history", "[]").apply()
                            },
                            onOpenAppSettings = {
                                runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))) }
                            },
                            onNavigate = { destination -> selectedPage = destination },
                            onClearCache = {
                                runCatching { context.cacheDir.deleteRecursively(); context.cacheDir.mkdirs() }
                                android.widget.Toast.makeText(context, "App cache cleared", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onResetSettings = {
                                prefs.edit().clear().apply()
                                backendUrl = ""
                                backendUrlInput = ""
                                backendToken = ""
                                backendTokenInput = ""
                                keySaved = false
                                checkingBackend = false
                                voiceEnabled = true
                                autoSave = true
                                typingIndicator = true
                                textSize = "Medium"
                                chatStyle = "Modern"
                                replyLanguage = "Hindi / Hinglish"
                                backendCheck = ""
                            }
                        )
                        "Image Generation" -> ToolPage(
                            title = "IMAGE PROMPT STUDIO",
                            description = "Generate an image from your prompt using the Gemini image model configured on your secure backend.",
                            placeholder = "A cute chibi character under sakura trees…",
                            buttonText = "GENERATE IMAGE",
                            apiKey = "",
                            instruction = "Create a polished image from the user's description.",
                            backendUrl = backendUrl,
                            backendToken = backendToken,
                            replyLanguage = replyLanguage,
                            imageGeneration = true,
                            onImageGenerated = { promptText, bytes, mime ->
                                val attachment = ChatAttachment("MAXXA_generated_${System.currentTimeMillis()}.${imageExtension(mime)}", mime, Base64.encodeToString(bytes, Base64.NO_WRAP))
                                chats.add(ChatLine("[Generated image] $promptText", false, attachment))
                                persistThreads()
                            }
                        )
                        "Code Generator" -> ToolPage(
                            title = "CODE GENERATOR",
                            description = "Describe the program, platform and language. Ask for complete code plus setup and testing instructions.",
                            placeholder = "Make a Kotlin calculator app…",
                            buttonText = "GENERATE CODE",
                            apiKey = "",
                            instruction = "You are MAXXA AI, an expert programming assistant. Produce a complete, practical solution for the user's request. State assumptions, include code in fenced code blocks, explain where files go, dependencies, run steps and tests. Never claim code was executed unless it was.",
                            backendUrl = backendUrl,
                            backendToken = backendToken,
                            replyLanguage = replyLanguage
                        )
                        "Summarize" -> ToolPage(
                            title = "SUMMARIZE",
                            description = "Paste an article, notes or long text. Choose a concise summary, key points and action items in your request.",
                            placeholder = "Paste the text you want summarized…",
                            buttonText = "SUMMARIZE TEXT",
                            apiKey = "",
                            instruction = "You are MAXXA AI. Summarize the user's supplied text accurately without adding unsupported facts. Provide a short overview, key points and any action items when relevant.",
                            backendUrl = backendUrl,
                            backendToken = backendToken,
                            replyLanguage = replyLanguage
                        )
                        "Translate" -> ToolPage(
                            title = "TRANSLATE",
                            description = "Paste text and mention the target language, for example Hindi, Hinglish or English.",
                            placeholder = "Text to translate and target language…",
                            buttonText = "TRANSLATE TEXT",
                            apiKey = "",
                            instruction = "You are MAXXA AI, a careful translator. Translate the user's text into the target language they specify. Preserve meaning, tone, names and formatting. If the target language is missing, ask which language they want.",
                            backendUrl = backendUrl,
                            backendToken = backendToken,
                            replyLanguage = replyLanguage
                        )
                        "Help & Support" -> HelpSupportPage()
                        "About" -> AboutPage()
                        else -> SimplePage(page.uppercase(), "Itachi Sakura theme • red neon cards • cute anime artwork.", "THEME", "Itachi • Sakura • Crimson Neon")
                                }
                            }
                        }
                    }
                    Surface(
                        color = Color(0xF5160A11),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x553F1B2A)),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            listOf("Home" to "⌂", "Chat" to "☷", "Voice" to "♫", "More" to "•••").forEach { (page, icon) ->
                                Column(
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        .clickable { if (page == "More") menuOpen = true else selectedPage = page },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(icon, color = if ((page == "Home" && selectedPage == "Home") || (page == "Chat" && selectedPage in listOf("Chat", "Conversation")) || (page == "Voice" && selectedPage == "Voice")) Cyan else Muted, fontSize = 18.sp)
                                    Text(page, color = White, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                    }
                }
                AnimatedVisibility(
                    visible = menuOpen,
                    enter = fadeIn(tween(160)) + slideInHorizontally(tween(220)) { -it },
                    exit = fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { -it }
                ) {
                    Row(Modifier.fillMaxSize().background(Color(0xCC050205))) {
                        Column(Modifier.fillMaxHeight().fillMaxWidth(0.82f).background(Brush.verticalGradient(listOf(Color(0xFF210A13), Bg))).verticalScroll(rememberScrollState()).padding(20.dp)) {
                            Surface(color = Color(0x33FF426D), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FF426D)), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(Cyan, Purple)), CircleShape), contentAlignment = Alignment.Center) {
                                        Image(painter = painterResource(id = R.drawable.maxxa_logo), contentDescription = "MAXXA logo", modifier = Modifier.fillMaxSize().padding(2.dp))
                                    }
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text("MAXXA AI", color = White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                                        Text("PERSONAL ASSISTANT", color = Cyan, fontSize = 9.sp, letterSpacing = 1.5.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(22.dp))
                            listOf("Home", "Chat", "Image Generation", "Code Generator", "Translate", "Summarize", "Character Theme Gallery", "AI Core Theme", "Voice Pack", "API Key", "Conversation", "Permissions", "Preferences", "Language Selection", "Automation", "Coding Workspace", "Installed Apps", "Phone Control", "Additional Settings", "Help & Support", "About").forEach { item ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .clickable { selectedPage = item; menuOpen = false }
                                    .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(menuIcon(item), color = if (selectedPage == item) Cyan else Gold, fontSize = 18.sp, modifier = Modifier.width(36.dp))
                                    Text(item, color = if (selectedPage == item) Cyan else White, fontSize = 15.sp, fontWeight = if (selectedPage == item) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text("Built for your voice.", color = Muted, fontSize = 12.sp)
                        }
                        Box(Modifier.fillMaxSize().clickable { menuOpen = false })
                    }
                }
            }
        }
    }
    if (showChatManager) {
        AlertDialog(
            onDismissRequest = { showChatManager = false },
            title = { Text("Your conversations") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    threads.forEach { thread ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(thread.title, color = White, modifier = Modifier.weight(1f).clickable(enabled = !loading) {
                                persistThreads()
                                activeThreadId = thread.id
                                showChatManager = false
                            }.padding(8.dp))
                            Text("×", color = Color(0xFFFF7897), modifier = Modifier.clickable(enabled = !loading) {
                                if (threads.size > 1) {
                                    threads.remove(thread)
                                    if (activeThreadId == thread.id) activeThreadId = threads.first().id
                                    persistThreads()
                                } else {
                                    chats.clear(); chats.add(welcomeLine)
                                    threads[0] = thread.copy(title = "Conversation 1", messages = listOf(welcomeLine))
                                    persistThreads()
                                }
                            }.padding(8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChatManager = false }) { Text("DONE") } },
            containerColor = Panel
        )
    }
    if (showChatSearch) {
        AlertDialog(
            onDismissRequest = { showChatSearch = false },
            title = { Text("Search conversations") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = chatSearchQuery, onValueChange = { chatSearchQuery = it.take(100) }, singleLine = true, label = { Text("Search message or chat title") })
                    val query = chatSearchQuery.trim().lowercase(Locale.ROOT)
                    val results = threads.flatMap { thread -> thread.messages.mapNotNull { line ->
                        if (query.isNotBlank() && (line.text.lowercase(Locale.ROOT).contains(query) || thread.title.lowercase(Locale.ROOT).contains(query))) thread to line else null
                    } }.take(30)
                    if (query.isNotBlank() && results.isEmpty()) Text("No matching messages found.", color = Muted)
                    results.forEach { (thread, line) ->
                        Surface(color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().clickable {
                            persistThreads()
                            activeThreadId = thread.id
                            jumpToMessageIndex = thread.messages.indexOf(line)
                            showChatSearch = false
                            selectedPage = "Chat"
                        }) {
                            Column(Modifier.padding(9.dp)) {
                                Text(thread.title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(line.text.take(140), color = White, fontSize = 10.sp, maxLines = 2)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChatSearch = false }) { Text("DONE") } },
            containerColor = Panel
        )
    }
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename conversation") },
            text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it.take(48) }, singleLine = true, label = { Text("Conversation name") }) },
            confirmButton = { TextButton(enabled = !loading, onClick = {
                val index = threads.indexOfFirst { it.id == activeThreadId }
                if (index >= 0 && renameValue.isNotBlank()) threads[index] = threads[index].copy(title = renameValue.trim())
                persistThreads(); showRenameDialog = false
            }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("CANCEL") } },
            containerColor = Panel
        )
    }
}


@Composable
private fun AttachmentPreview(attachment: ChatAttachment) {
    val decoded = remember(attachment.base64, attachment.mimeType) {
        runCatching { Base64.decode(attachment.base64, Base64.DEFAULT) }.getOrNull()
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("📎 ${attachment.name} • ${attachment.mimeType}", color = Cyan, fontSize = 10.sp)
        if (attachment.mimeType.startsWith("image/") && decoded != null) {
            val bitmap = remember(decoded) { BitmapFactory.decodeByteArray(decoded, 0, decoded.size) }
            if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = attachment.name,
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp))
            else Text("Image preview unavailable", color = Muted, fontSize = 10.sp)
        } else if (attachment.mimeType == "text/plain" && decoded != null) {
            val preview = remember(decoded) { runCatching { String(decoded, Charsets.UTF_8).take(1200) }.getOrDefault("") }
            if (preview.isNotBlank()) Text(preview, color = Muted, fontSize = 10.sp, maxLines = 8)
        } else if (attachment.mimeType == "application/pdf") {
            Text("PDF attached • available to the assistant when supported by the backend", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ChatMessageContent(text: String, textSize: String) {
    val clipboard = LocalClipboardManager.current
    val bodySize = when (textSize) { "Small" -> 12.sp; "Large" -> 16.sp; else -> 14.sp }
    val codeFence = Regex("```([\\w+#.-]*)\\n?([\\s\\S]*?)```")
    val matches = codeFence.findAll(text).toList()
    val segments = mutableListOf<Pair<Boolean, String>>()
    var cursor = 0
    matches.forEach { match ->
        if (match.range.first > cursor) segments += false to text.substring(cursor, match.range.first)
        segments += true to (match.groupValues[1] + "\u0000" + match.groupValues[2])
        cursor = match.range.last + 1
    }
    if (cursor < text.length || segments.isEmpty()) segments += false to text.substring(cursor)

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        segments.forEach { (isCode, content) ->
            if (isCode) {
                val parts = content.split("\u0000", limit = 2)
                Surface(color = Color(0xFF100B10), shape = RoundedCornerShape(9.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FF426D))) {
                    Column(Modifier.fillMaxWidth().padding(9.dp)) {
                        if (parts.firstOrNull().orEmpty().isNotBlank()) Text(parts[0].uppercase(Locale.ROOT), color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text("COPY CODE", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { clipboard.setText(AnnotatedString(parts.getOrElse(1) { "" }.trimEnd())) }.padding(bottom = 5.dp))
                        }
                        Text(parts.getOrElse(1) { "" }.trimEnd(), color = Color(0xFFFFD9E5), fontSize = bodySize, lineHeight = bodySize * 1.35f)
                    }
                }
            } else {
                val lines = content.trim('\n').lines()
                var index = 0
                while (index < lines.size) {
                    val line = lines[index]
                    val trimmed = line.trim()
                    val tableLine = trimmed.startsWith("|") && trimmed.endsWith("|")
                    if (tableLine) {
                        val tableRows = mutableListOf<List<String>>()
                        while (index < lines.size && lines[index].trim().let { it.startsWith("|") && it.endsWith("|") }) {
                            val cells = lines[index].trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
                            if (cells.isNotEmpty() && !cells.all { it.matches(Regex(":?-{3,}:?")) }) tableRows += cells
                            index++
                        }
                        if (tableRows.isNotEmpty()) {
                            Surface(color = Color(0xFF120B10), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x55FF426D))) {
                                Column(Modifier.fillMaxWidth().padding(7.dp)) {
                                    tableRows.forEachIndexed { rowIndex, cells ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            cells.forEach { cell ->
                                                Text(cell, color = if (rowIndex == 0) Gold else White, fontSize = bodySize * 0.88f, fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                            }
                                        }
                                        if (rowIndex == 0 && tableRows.size > 1) HorizontalDivider(color = Color(0x66FF426D))
                                    }
                                }
                            }
                        }
                        continue
                    }
                    when {
                        trimmed.startsWith("### ") -> Text(trimmed.removePrefix("### "), color = Gold, fontSize = bodySize * 1.05f, fontWeight = FontWeight.Bold)
                        trimmed.startsWith("## ") -> Text(trimmed.removePrefix("## "), color = Gold, fontSize = bodySize * 1.15f, fontWeight = FontWeight.Bold)
                        trimmed.startsWith("# ") -> Text(trimmed.removePrefix("# "), color = Gold, fontSize = bodySize * 1.25f, fontWeight = FontWeight.ExtraBold)
                        trimmed.matches(Regex("[-*+]\\s+.+")) -> Text("•  ${trimmed.dropWhile { it == '-' || it == '*' || it == '+' }.trim()}", color = White, fontSize = bodySize, lineHeight = bodySize * 1.4f)
                        trimmed.matches(Regex("\\d+[.)]\\s+.+")) -> Text(trimmed, color = White, fontSize = bodySize, lineHeight = bodySize * 1.4f)
                        trimmed.startsWith("> ") -> Text(trimmed.removePrefix("> "), color = Muted, fontSize = bodySize, lineHeight = bodySize * 1.4f)
                        trimmed.isBlank() -> Spacer(Modifier.height(2.dp))
                        else -> Text(trimmed.replace(Regex("\\*\\*(.+?)\\*\\*")) { it.groupValues[1] }.replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")) { it.groupValues[1] }.replace(Regex("_([^_]+)_")) { it.groupValues[1] }.replace(Regex("~~(.+?)~~")) { it.groupValues[1] }.replace(Regex("`([^`]+)`")) { it.groupValues[1] }.replace(Regex("\\[([^\\]]+)\\]\\((https?://[^)]+)\\)")) { "${it.groupValues[1]} (${it.groupValues[2]})" }, color = White, fontSize = bodySize, lineHeight = bodySize * 1.4f)
                    }
                    index++
                }
            }
        }
    }
}

@Composable
private fun ConversationHistoryPage(
    messages: List<ChatLine>,
    onOpenConversation: () -> Unit,
    onClearHistory: () -> Unit
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReferencePageHeading("CONVERSATION", "Your chat history", "Saved locally on this device when Auto Save is enabled.")
        val latest = messages.lastOrNull()?.text.orEmpty()
        Surface(
            color = Color(0xEE1A0B12),
            shape = RoundedCornerShape(17.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xAAFF426D)),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenConversation)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(Color(0x33FF426D), CircleShape), contentAlignment = Alignment.Center) {
                        Text("☷", color = Cyan, fontSize = 20.sp)
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Current conversation", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${messages.size} messages • Stored on this device", color = Muted, fontSize = 10.sp)
                    }
                    Text("›", color = Cyan, fontSize = 24.sp)
                }
                Divider(color = Color(0x553D2530))
                Text(latest.ifBlank { "Start a new conversation with MAXXA." }.take(180), color = Muted, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 3)
                Text("OPEN CHAT  →", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        ReferenceInfoCard("Privacy", "Your conversations are stored locally on this device when Auto Save is enabled. They are not cloud-synced.")
        OutlinedButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) { Text("CLEAR CONVERSATION HISTORY") }
    }
}

@Composable
private fun ToolPage(
    title: String,
    description: String,
    placeholder: String,
    buttonText: String,
    apiKey: String,
    instruction: String,
    backendUrl: String = "",
    backendToken: String = "",
    replyLanguage: String = "Hindi / Hinglish",
    imageGeneration: Boolean = false,
    onImageGenerated: ((String, ByteArray, String) -> Unit)? = null
) {
    var request by remember(title) { mutableStateOf("") }
    var result by remember(title) { mutableStateOf("") }
    var generatedImage by remember(title) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var generatedImageBytes by remember(title) { mutableStateOf<ByteArray?>(null) }
    var generatedImageMime by remember(title) { mutableStateOf("image/png") }
    var selectedAspectRatio by remember(title) { mutableStateOf("1:1") }
    var selectedStyle by remember(title) { mutableStateOf("Cinematic") }
    var aspectMenuExpanded by remember(title) { mutableStateOf(false) }
    var running by remember(title) { mutableStateOf(false) }
    var saving by remember(title) { mutableStateOf(false) }
    var generationCancellation by remember(title) { mutableStateOf<VoiceRequestCancellation?>(null) }
    var generationJob by remember(title) { mutableStateOf<Job?>(null) }
    var imageHistory by remember(title) { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePrefs = remember(context) { context.getSharedPreferences("maxxa_image_studio", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(imageGeneration) {
        if (imageGeneration) {
            val saved = runCatching { org.json.JSONArray(imagePrefs.getString("history", "[]")) }.getOrNull()
            imageHistory = buildList {
                if (saved != null) for (i in 0 until saved.length()) {
                    val row = saved.optJSONObject(i) ?: continue
                    val prompt = row.optString("prompt"); val mime = row.optString("mime"); val data = row.optString("data")
                    if (data.isNotBlank() && data.length < 5_500_000) add(Triple(prompt, mime, data))
                }
            }
        }
    }
    val imageStyles = listOf("Cinematic", "Anime", "Photorealistic", "3D Render", "Watercolor", "Pixel Art", "Minimalist", "Fantasy")
    val saveImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
        val bytes = generatedImageBytes
        if (uri != null && bytes != null) {
            scope.launch {
                saving = true
                result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("Could not open the selected destination.")
                        "Image saved successfully."
                    }.getOrElse { "Couldn't save image: ${it.message ?: "Storage error"}" }
                }
                saving = false
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 18.dp)) {
        ReferencePageHeading(title, description, if (imageGeneration) "Create, preview and export artwork from your prompt." else "Powered by your secure HTTPS Gemini backend.")
        Spacer(Modifier.height(12.dp))
        if (imageGeneration) {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x55FF426D)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("IMAGE STUDIO", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Describe the image you want to create", color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp, bottom = 10.dp))
                    OutlinedTextField(
                        value = request,
                        onValueChange = { request = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 230.dp),
                        placeholder = { Text(placeholder) },
                        shape = RoundedCornerShape(14.dp),
                        minLines = 4
                    )
                    Text("STYLE PRESETS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 13.dp, bottom = 7.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        imageStyles.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { style ->
                                    val active = selectedStyle == style
                                    Surface(
                                        color = if (active) Color(0x55FF426D) else Color(0xFF24131B),
                                        shape = RoundedCornerShape(20.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Cyan else Color(0x443D2530)),
                                        modifier = Modifier.weight(1f).clickable { selectedStyle = style }
                                    ) { Text(style, color = if (active) White else Muted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp)) }
                                }
                                if (row.size < 4) repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { aspectMenuExpanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("RATIO  $selectedAspectRatio", fontSize = 11.sp) }
                            DropdownMenu(expanded = aspectMenuExpanded, onDismissRequest = { aspectMenuExpanded = false }) {
                                listOf("1:1", "3:2", "2:3", "4:3", "3:4", "5:4", "4:5", "9:16", "16:9").forEach { ratio ->
                                    DropdownMenuItem(text = { Text(ratio) }, onClick = { selectedAspectRatio = ratio; aspectMenuExpanded = false })
                                }
                            }
                        }
                        Text("${request.length}/2000", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        } else {
            OutlinedTextField(value = request, onValueChange = { request = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp, max = 220.dp), placeholder = { Text(placeholder) }, shape = RoundedCornerShape(16.dp), minLines = 4)
        }
        Button(
            enabled = !running && !saving && request.isNotBlank() && request.length <= 2000,
            onClick = {
                val userRequest = request.trim()
                running = true
                result = ""
                generationCancellation?.cancel()
                val cancellation = VoiceRequestCancellation().also { generationCancellation = it }
                generatedImage = null
                generatedImageBytes = null
        generatedImageMime = "image/png"
                generationJob = scope.launch {
                    result = try {
                        if (imageGeneration) {
                            val styledPrompt = "Style: $selectedStyle. Create one original image based on this description: $userRequest"
                            val image = withContext(Dispatchers.IO) { requestGeneratedImage(styledPrompt, backendUrl, selectedAspectRatio, backendToken, cancellation) }
                            cancellation.checkActive()
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(image.first, 0, image.first.size, options)
                            require(options.outWidth > 0 && options.outHeight > 0) { "The server response is not a supported image." }
                            var sample = 1
                            while (options.outWidth / sample > 2048 || options.outHeight / sample > 2048) sample *= 2
                            val bitmap = BitmapFactory.decodeByteArray(image.first, 0, image.first.size, BitmapFactory.Options().apply { inSampleSize = sample })
                            val decoded = bitmap?.asImageBitmap()
                            if (decoded == null) "Image was returned but could not be decoded on this device." else {
                                generatedImageBytes = image.first
                                generatedImageMime = normalizeImageMime(image.second)
                                generatedImage = decoded
                                onImageGenerated?.invoke(userRequest, image.first, generatedImageMime)
                                val row = JSONObject().put("prompt", userRequest).put("mime", generatedImageMime).put("data", Base64.encodeToString(image.first, Base64.NO_WRAP))
                                val historyData = Base64.encodeToString(image.first, Base64.NO_WRAP)
                                val updated = (if (historyData.length <= 5_500_000) listOf(Triple(userRequest, generatedImageMime, historyData)) else emptyList())
                                    .plus(imageHistory).distinctBy { it.third }.take(5)
                                imageHistory = updated
                                val array = org.json.JSONArray(); updated.forEach { (prompt, mime, data) -> array.put(JSONObject().put("prompt", prompt).put("mime", mime).put("data", data)) }
                                imagePrefs.edit().putString("history", array.toString()).apply()
                                "Image generated successfully • ${generatedImageMime} • $selectedAspectRatio • $selectedStyle"
                            }
                        } else {
                            withContext(Dispatchers.IO) { askGemini(apiKey, "You are MAXXA AI. Reply primarily in $replyLanguage unless the user requests another language.\n\n$instruction\n\nUser request:\n$userRequest", backendUrl, backendToken) }
                        }
                    } catch (error: CancellationException) {
                        "Image generation cancelled."
                    } catch (error: Exception) {
                        error.message?.takeIf { it.isNotBlank() } ?: "MAXXA couldn't complete this request. Check your backend URL and internet connection, then try again."
                    } finally { running = false; generationCancellation = null; generationJob = null }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Bg)
        ) { Text(if (running) "CREATING…" else if (saving) "SAVING…" else buttonText, fontWeight = FontWeight.Bold) }
        if (running) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = Cyan, trackColor = Panel)
            if (imageGeneration) OutlinedButton(onClick = { generationCancellation?.cancel(); generationJob?.cancel(); running = false; result = "Image generation cancelled." }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("CANCEL GENERATION") }
        }
        if (imageGeneration && generatedImage != null) {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FF426D)), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("IMAGE PREVIEW", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                        Text(selectedAspectRatio, color = Muted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Image(bitmap = generatedImage!!, contentDescription = "Generated image preview", modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 480.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    OutlinedButton(onClick = { saveImageLauncher.launch("MAXXA_${System.currentTimeMillis()}.${when (generatedImageMime) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }}") }, enabled = !saving, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(12.dp)) {
                        Text(if (saving) "SAVING IMAGE…" else "SAVE IMAGE TO DEVICE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (imageGeneration && imageHistory.isNotEmpty()) {
            Text("RECENT GENERATIONS", color = Gold, fontSize = 11.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            imageHistory.forEachIndexed { index, entry ->
                val (promptText, mimeText, encodedText) = entry
                OutlinedButton(onClick = {
                    runCatching {
                        val bytes = Base64.decode(encodedText, Base64.DEFAULT)
                        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() ?: error("Saved image cannot be decoded")
                        generatedImageBytes = bytes; generatedImageMime = normalizeImageMime(mimeText); generatedImage = decoded; request = promptText; result = "Restored saved generation."
                    }.onFailure { result = "Couldn't restore image: ${it.message}" }
                }, modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp)) { Text("OPEN ${index + 1} • ${promptText.take(48)}") }
            }
        }
        if (result.isNotBlank()) {
            Text(if (imageGeneration) "STUDIO STATUS" else "RESULT", color = Gold, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FF426D)), modifier = Modifier.fillMaxWidth()) {
                Text(result, color = White, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

private data class PermissionRow(val icon: String, val title: String, val subtitle: String, val permissions: List<String>)

private fun menuIcon(item: String) = when (item) {
    "Home" -> "⌂"; "Chat" -> "☷"; "Voice" -> "♫"; "Permissions" -> "◈"; "Voice Pack" -> "♫"; "API Key" -> "⌘"
    "Conversation" -> "☷"; "Character Theme Gallery" -> "🎨"; "AI Core Theme" -> "◈"; "Coding Workspace", "Code Generator" -> "</>"; "Image Generation" -> "▧"; "Translate" -> "文"; "Summarize" -> "≡"; "Preferences" -> "⚙"; "Automation" -> "⌁"; "Installed Apps" -> "▦"; "Phone Control" -> "▣"; "Language Selection" -> "文"; else -> "⋯"
}

@Composable
private fun SplashScreen(theme: CharacterUiTheme) {
    val transition = rememberInfiniteTransition(label = "theme-splash")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1050, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "theme-splash-pulse"
    )
    val orbitRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6500), RepeatMode.Restart),
        label = "theme-splash-orbit"
    )
    val accent = theme.accent
    val secondary = theme.secondary
    val highlight = theme.highlight

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(secondary.copy(alpha = 0.58f), Color(0xFF100811), Color(0xFF070609)))
        )
    ) {
        // Each saved Character Theme selects its own real artwork (not an emoji placeholder).
        val characterArtwork = when (theme.name) {
            "Itachi" -> R.drawable.itachi_loading_art
            "Naruto" -> R.drawable.naruto_loading_art
            "Sasuke" -> R.drawable.sasuke_loading_art
            "Kakashi" -> R.drawable.kakashi_loading_art
            "Madara" -> R.drawable.madara_loading_art
            "Hinata" -> R.drawable.hinata_loading_art
            "Sakura" -> R.drawable.sakura_loading_art
            "Nezuko" -> R.drawable.nezuko_loading_art
            "Obito" -> R.drawable.obito_loading_art
            "Minato" -> R.drawable.minato_loading_art
            "Mitsuri" -> R.drawable.mitsuri_loading_art
            "Nobara" -> R.drawable.nobara_loading_art
            else -> R.drawable.itachi_loading_art
        }
        Image(
            painter = painterResource(id = characterArtwork),
            contentDescription = "${theme.name} cute character loading artwork",
            modifier = Modifier.fillMaxSize().alpha(0.96f),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(secondary.copy(alpha = 0.35f), Color(0x9909060B), Color(0xF2070609)))))

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(126.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxSize().rotate(orbitRotation)
                        .background(Brush.sweepGradient(listOf(accent.copy(alpha = 0.05f), highlight, accent.copy(alpha = 0.05f))), CircleShape)
                        .padding(2.dp).background(Color(0xFF100811), CircleShape)
                )
                Box(
                    Modifier.size(104.dp).scale(pulse)
                        .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), secondary.copy(alpha = 0.22f), Color.Transparent)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(78.dp)
                            .background(Brush.linearGradient(listOf(accent, secondary)), CircleShape)
                            .padding(3.dp).background(Color(0xFF090509), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(painter = painterResource(id = R.drawable.maxxa_logo), contentDescription = "MAXXA AI logo", modifier = Modifier.fillMaxSize().padding(4.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("MAXXA", color = White, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Text("AI", color = accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp)
            Text("YOUR AI ASSISTANT", color = highlight, fontSize = 10.sp, letterSpacing = 2.4.sp, modifier = Modifier.padding(top = 6.dp))
            Surface(
                color = secondary.copy(alpha = 0.32f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text("${theme.name.uppercase()} THEME", color = highlight, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = accent,
                        trackColor = highlight.copy(alpha = 0.22f)
                    )
                    Text("INITIALIZING MAXXA AI…", color = White.copy(alpha = 0.88f), fontSize = 9.sp, letterSpacing = 1.8.sp, modifier = Modifier.padding(top = 11.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.animateContentSize().clickable(onClick = onClick), color = Panel, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FF426D))) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PreferencesPage(
    language: String,
    voiceEnabled: Boolean,
    autoSave: Boolean,
    typingIndicator: Boolean,
    textSize: String,
    chatStyle: String,
    coreTheme: String,
    customAccentHex: String,
    onCustomAccentChange: (String) -> Unit,
    onCoreThemeChange: (String) -> Unit,
    onTextSizeChange: (String) -> Unit,
    onChatStyleChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onVoiceChange: (Boolean) -> Unit,
    onAutoSaveChange: (Boolean) -> Unit,
    onTypingIndicatorChange: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ReferencePageHeading("PREFERENCES", "Make MAXXA work your way", "Personalize language, appearance and chat behavior.")
        Surface(color = Color(0xEE1A0B12), shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x553F1B2A)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("LANGUAGE", color = Cyan, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                listOf("Hindi / Hinglish", "English").forEach { option ->
                    Row(Modifier.fillMaxWidth().clickable { onLanguageChange(option) }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (language == option) "◉" else "○", color = Cyan, fontSize = 18.sp, modifier = Modifier.width(30.dp))
                        Text(option, color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (language == option) Text("SELECTED", color = Color(0xFFFF9FBE), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Divider(color = Color(0x553D2530))
                PreferenceSwitchRow("Speak AI replies", "Read MAXXA responses aloud", voiceEnabled, onVoiceChange)
                Divider(color = Color(0x553D2530))
                PreferenceSwitchRow("Auto Save Conversation", "Keep chat history on this device", autoSave, onAutoSaveChange)
                PreferenceSwitchRow("Show Typing Indicator", "Show when MAXXA is preparing a reply", typingIndicator, onTypingIndicatorChange)
                Divider(color = Color(0x553D2530))
                Text("TEXT SIZE", color = Cyan, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Small", "Medium", "Large").forEach { size ->
                        FilterChip(selected = textSize == size, onClick = { onTextSizeChange(size) }, label = { Text(size, fontSize = 10.sp) })
                    }
                }
                Text("CHAT STYLE", color = Cyan, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Modern", "Classic").forEach { style ->
                        FilterChip(selected = chatStyle == style, onClick = { onChatStyleChange(style) }, label = { Text(style, fontSize = 10.sp) })
                    }
                }
            }
        }
        Text("Your choices are saved on this device.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun PreferenceSwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LanguageSelectionPage(language: String, onLanguageChange: (String) -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        ReferencePageHeading("LANGUAGE SELECTION", "Choose your language", "MAXXA will use this as its default response language.")
        listOf("English", "Hindi", "Hinglish").forEach { option ->
            Surface(color = Color(0xEE1A0B12), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (language == option) Cyan else Color(0x553F1B2A)), modifier = Modifier.fillMaxWidth().clickable { onLanguageChange(option) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(when (option) { "English" -> "A"; "Hindi" -> "अ"; else -> "हि" }, color = White, fontSize = 22.sp, modifier = Modifier.width(42.dp))
                    Text(option, color = White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(if (language == option) "✓" else "○", color = Cyan, fontSize = 20.sp)
                }
            }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(49.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Bg)) { Text("CONTINUE", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun AutomationPage() {
    val context = LocalContext.current
    fun launch(intent: Intent, fallback: String) {
        runCatching { context.startActivity(intent) }.onFailure {
            android.widget.Toast.makeText(context, fallback, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        ReferencePageHeading("AUTOMATION", "Your phone, one tap away", "Choose a shortcut. MAXXA always asks Android to open the action; it does not secretly control your device.")
        ReferenceActionRow("▣", "Open Dialer", "Open the phone app without placing a call", "OPEN") { launch(Intent(Intent.ACTION_DIAL), "Dialer is unavailable") }
        ReferenceActionRow("⌕", "Search & Browse", "Open your browser to search the web", "OPEN") { launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")), "Browser is unavailable") }
        ReferenceActionRow("⚙", "Device Settings", "Manage Wi-Fi, Bluetooth and system settings", "OPEN") { launch(Intent(android.provider.Settings.ACTION_SETTINGS), "Settings are unavailable") }
        ReferenceActionRow("▤", "Messages", "Open your default messaging app", "OPEN") { launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING), "Messaging app is unavailable") }
        ReferenceActionRow("◉", "App permissions", "Review MAXXA's Android permissions", "REVIEW") { launch(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)), "App settings are unavailable") }
        ReferenceInfoCard("Privacy first", "MAXXA does not silently monitor your screen, read private messages or place calls. Every shortcut opens an Android app or settings page for you to review.")
    }
}

private data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable
)

@Composable
private fun InstalledAppsPage() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var search by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var aliasTarget by remember { mutableStateOf<LaunchableApp?>(null) }
    var aliasText by remember { mutableStateOf("") }
    val aliasPrefs = remember { context.getSharedPreferences("maxxa_launcher_aliases", android.content.Context.MODE_PRIVATE) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val packageReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) { refreshKey++ }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(packageReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(packageReceiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(packageReceiver) } }
    }
    var loadingApps by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val apps = remember { mutableStateListOf<LaunchableApp>() }

    LaunchedEffect(refreshKey) {
        loadingApps = true
        error = ""
        val found = withContext(Dispatchers.IO) {
            runCatching {
                val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(query, 0)
                    .mapNotNull { info ->
                        val activity = info.activityInfo ?: return@mapNotNull null
                        val packageName = activity.packageName ?: return@mapNotNull null
                        if (!activity.exported || !activity.enabled || !activity.applicationInfo.enabled) return@mapNotNull null
                        val label = runCatching { info.loadLabel(context.packageManager)?.toString()?.trim().orEmpty() }.getOrDefault("")
                        if (label.isBlank()) return@mapNotNull null
                        // Keep the app in the list even if its icon provider is broken.
                        val icon = runCatching { info.loadIcon(context.packageManager) }.getOrNull()
                            ?: android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                        LaunchableApp(label, packageName, activity.name, icon)
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase(Locale.ROOT) }
            }.getOrElse { throwable ->
                error = throwable.message ?: "Installed apps could not be loaded."
                emptyList()
            }
        }
        apps.clear()
        apps.addAll(found)
        // Remove stale package-bound aliases after an app is uninstalled.
        val installedPackages = found.map { it.packageName }.toSet()
        aliasPrefs.all.keys.filter { it !in installedPackages }.forEach { stale -> aliasPrefs.edit().remove(stale).apply() }
        loadingApps = false
    }

    if (aliasTarget != null) {
        val target = aliasTarget!!
        AlertDialog(
            onDismissRequest = { aliasTarget = null; aliasText = "" },
            title = { Text("Custom voice aliases") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add names MAXXA should recognize for ${target.label}. Separate multiple aliases with commas.")
                    OutlinedTextField(
                        value = aliasText,
                        onValueChange = { aliasText = it },
                        label = { Text("Aliases") },
                        placeholder = { Text("e.g. insta, my photos") },
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleaned = aliasText.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    aliasPrefs.edit().putString(target.packageName, cleaned.joinToString("|")).apply()
                    aliasTarget = null
                    aliasText = ""
                    android.widget.Toast.makeText(context, "Aliases saved for ${target.label}", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("SAVE") }
            },
            dismissButton = {
                TextButton(onClick = { aliasPrefs.edit().remove(target.packageName).apply(); aliasTarget = null; aliasText = "" }) { Text("CLEAR") }
            }
        )
    }

    val filtered = apps.filter {
        it.label.contains(search.trim(), ignoreCase = true) ||
            it.packageName.contains(search.trim(), ignoreCase = true)
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("INSTALLED APP LAUNCHER", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Open apps on your phone", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Search an installed app and tap it to launch. MAXXA only opens apps you select.", color = Muted, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Search apps") },
                placeholder = { Text("YouTube, WhatsApp…") },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = White, unfocusedTextColor = White,
                    focusedBorderColor = Cyan, unfocusedBorderColor = Muted,
                    focusedLabelColor = Cyan, unfocusedLabelColor = Muted,
                    cursorColor = Cyan
                )
            )
            TextButton(onClick = { refreshKey++ }) { Text("REFRESH", color = Cyan, fontSize = 11.sp) }
        }
        Text("${filtered.size} apps", color = Muted, fontSize = 11.sp)
        when {
            loadingApps -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan)
            }
            error.isNotBlank() -> Text(error, color = Color(0xFFFF8A8A), fontSize = 13.sp)
            filtered.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(if (search.isBlank()) "No launchable apps were found." else "No apps match your search.", color = Muted, textAlign = TextAlign.Center)
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    Surface(
                        color = Color(0xFF1B101A),
                        shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x443F1B2A)),
                        modifier = Modifier.fillMaxWidth().clickable {
                            val intent = (context.packageManager.getLaunchIntentForPackage(app.packageName)
                                ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                                    .setClassName(app.packageName, app.activityName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                                .onFailure { android.widget.Toast.makeText(context, "Could not open ${app.label}. The app may be disabled or unavailable.", android.widget.Toast.LENGTH_LONG).show() }
                        }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val iconBitmap = remember(app.packageName) { runCatching { app.icon.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                            if (iconBitmap != null) {
                                Image(bitmap = iconBitmap, contentDescription = "${app.label} icon", modifier = Modifier.size(44.dp))
                            } else {
                                Box(Modifier.size(44.dp).background(Color(0x3342D9FF), CircleShape), contentAlignment = Alignment.Center) {
                                    Text(app.label.take(1).uppercase(), color = Cyan, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(app.label, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(app.packageName, color = Muted, fontSize = 9.sp, maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("OPEN  ›", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = {
                                    aliasText = aliasPrefs.getString(app.packageName, "").orEmpty().replace('|', ',')
                                    aliasTarget = app
                                }) { Text("ALIASES", color = Cyan, fontSize = 9.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneControlPage() {
    val context = LocalContext.current
    fun launch(intent: Intent, fallback: String) {
        runCatching { context.startActivity(intent) }.onFailure {
            android.widget.Toast.makeText(context, fallback, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val accessibilityEnabled = remember { mutableStateOf(MaxxaAccessibilityService.isEnabled()) }
    fun runPhoneAction(action: PhoneAction, label: String) {
        val ok = MaxxaAccessibilityService.perform(action)
        android.widget.Toast.makeText(context, if (ok) "$label requested" else "Enable MAXXA AI Phone Actions in Accessibility settings first", android.widget.Toast.LENGTH_LONG).show()
        accessibilityEnabled.value = MaxxaAccessibilityService.isEnabled()
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        ReferencePageHeading("PHONE CONTROL", "User-approved device actions", "Enable the optional Android Accessibility service, then tap an action. MAXXA does not read screen contents or control other apps with hidden taps.")
        ReferenceActionRow("♿", "Enable Accessibility Actions", if (accessibilityEnabled.value) "Service is connected" else "Open Android Accessibility settings and enable MAXXA AI Phone Actions", if (accessibilityEnabled.value) "ON" else "ENABLE") {
            launch(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS), "Accessibility settings are unavailable")
        }
        ReferenceActionRow("↶", "Back", "Navigate back one screen", "RUN") { runPhoneAction(PhoneAction.BACK, "Back") }
        ReferenceActionRow("⌂", "Home", "Return to your device launcher", "RUN") { runPhoneAction(PhoneAction.HOME, "Home") }
        ReferenceActionRow("▢", "Recent Apps", "Open Android's recent-apps overview", "RUN") { runPhoneAction(PhoneAction.RECENTS, "Recents") }
        ReferenceActionRow("▤", "Notifications", "Open the notification shade", "RUN") { runPhoneAction(PhoneAction.NOTIFICATIONS, "Notifications") }
        ReferenceActionRow("☷", "Quick Settings", "Open Android Quick Settings", "RUN") { runPhoneAction(PhoneAction.QUICK_SETTINGS, "Quick Settings") }
        ReferenceActionRow("⚙", "Open Settings", "Open the Android system settings screen", "OPEN") { launch(Intent(android.provider.Settings.ACTION_SETTINGS), "Settings are unavailable") }
        ReferenceActionRow("▣", "Open App Settings", "Manage MAXXA permissions and storage", "OPEN") { launch(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)), "App settings are unavailable") }
        ReferenceActionRow("⌕", "Open Browser", "Launch a browser for web search", "OPEN") { launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")), "Browser is unavailable") }
        ReferenceInfoCard("Privacy & limits", "Android requires you to enable Accessibility manually. This service only runs the five navigation actions you tap. It does not read screen text, inspect messages, perform gestures, type, make calls, or change protected toggles. Android/device policy may restrict some actions.")
    }
}

@Composable
private fun HelpSupportPage() {
    var expanded by remember { mutableStateOf(-1) }
    val questions = listOf(
        "How do I connect Gemini?" to "Deploy the included backend, add GEMINI_API_KEY as a server secret, then open API Key in MAXXA and save your HTTPS backend URL.",
        "Why is chat not responding?" to "Check your internet connection, confirm that the backend URL uses HTTPS, and use Test Backend Connection on the API Key page. The backend must be deployed and configured.",
        "Why is voice input unavailable?" to "Allow microphone permission in Android settings. Speech recognition availability and supported languages depend on the device and its installed speech services.",
        "Where are my conversations stored?" to "When Auto Save is enabled, chat history is stored locally in MAXXA's app preferences on this device. It is not cloud-synced in this build."
    )
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        ReferencePageHeading("HELP & SUPPORT", "How can we help?", "Quick answers for setup, chat and voice features.")
        questions.forEachIndexed { index, item ->
            Surface(color = Color(0xEE1A0B12), shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (expanded == index) Cyan else Color(0x553F1B2A)), modifier = Modifier.fillMaxWidth().clickable { expanded = if (expanded == index) -1 else index }) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("?", color = Cyan, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                        Text(item.first, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(if (expanded == index) "−" else "+", color = Cyan, fontSize = 20.sp)
                    }
                    AnimatedVisibility(visible = expanded == index, enter = fadeIn(tween(120)), exit = fadeOut(tween(100))) {
                        Text(item.second, color = Muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 30.dp, top = 9.dp))
                    }
                }
            }
        }
        ReferenceInfoCard("Still need help?", "This development build does not yet include a support-ticket or live-contact service. You can report bugs to the project maintainer with the screen name and steps to reproduce.")
    }
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(112.dp).background(Brush.radialGradient(listOf(Color(0xAAFF426D), Color(0x22FF78B7), Color.Transparent)), CircleShape), contentAlignment = Alignment.Center) {
            Image(painter = painterResource(id = R.drawable.maxxa_logo), contentDescription = "MAXXA AI logo", modifier = Modifier.size(82.dp))
        }
        Text("MAXXA AI", color = White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("YOUR PERSONAL AI ASSISTANT", color = Cyan, fontSize = 10.sp, letterSpacing = 1.8.sp)
        ReferenceInfoCard("Developer", "Riyaz • MAXXA AI Creator")
        Surface(color = Color(0xEE1A0B12), shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x88FF426D)), modifier = Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/riyaz_editz_21/"))) }) {
            Column(Modifier.padding(14.dp)) {
                Text("INSTAGRAM  ↗", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("@riyaz_editz_21", color = White, fontSize = 14.sp)
            }
        }
        ReferenceInfoCard("Version", "1.0.0 • Development build")
        ReferenceInfoCard("Made for", "Hindi • Hinglish • English\nChat • Voice • Code • Image tools • Translation")
        ReferenceInfoCard("Design", "Crimson neon interface with Itachi and Sakura-inspired artwork.")
        ReferenceInfoCard("Build status", "Some features require a deployed HTTPS backend. Device compatibility, live API behavior and release signing must be verified before public release.")
    }
}

@Composable
private fun AdditionalSettingsPage(
    backendConfigured: Boolean,
    historyCount: Int,
    onClearHistory: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onNavigate: (String) -> Unit,
    onClearCache: () -> Unit,
    onResetSettings: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmCache by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        ReferencePageHeading("ADDITIONAL SETTINGS", "Manage MAXXA AI", "App information, storage and device-level settings.")
        ReferenceActionRow("ⓘ", "About MAXXA AI", "App information and version", "OPEN") { onNavigate("About") }
        ReferenceActionRow("?", "Help & Support", "Setup help and common questions", "OPEN") { onNavigate("Help & Support") }
        ReferenceActionRow("★", "Rate App", "Rating will be available after public release", "SOON") { }
        Surface(color = Color(0xEE1A0B12), shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x553F1B2A)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CONNECTION", color = Cyan, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                Text(if (backendConfigured) "Secure HTTPS backend URL saved" else "Backend not configured", color = White, fontSize = 12.sp)
                Divider(color = Color(0x553D2530))
                Text("LOCAL STORAGE", color = Cyan, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                Text("$historyCount messages stored on this device", color = White, fontSize = 12.sp)
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("CLEAR CHAT HISTORY") }
            }
        }
        ReferenceActionRow("▤", "Clear Cache", "Remove temporary app files", "CLEAR") { confirmCache = true }
        ReferenceActionRow("⚙", "App Permissions", "Open Android permission controls", "OPEN", onOpenAppSettings)
        ReferenceActionRow("↺", "Reset Settings", "Restore MAXXA preferences to defaults", "RESET") { confirmReset = true }
        Text("MAXXA AI • Version 1.0.0 • Development build", color = Muted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear chat history?") },
        text = { Text("This removes locally saved conversation messages from this device.") },
        confirmButton = { TextButton(onClick = { onClearHistory(); confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
    )
    if (confirmCache) AlertDialog(
        onDismissRequest = { confirmCache = false },
        title = { Text("Clear app cache?") },
        text = { Text("Temporary cached files will be removed. Your saved conversations and settings will remain.") },
        confirmButton = { TextButton(onClick = { onClearCache(); confirmCache = false }) { Text("Clear cache") } },
        dismissButton = { TextButton(onClick = { confirmCache = false }) { Text("Cancel") } }
    )
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset MAXXA settings?") },
        text = { Text("This resets preferences and removes the saved backend URL. Chat history is not cleared.") },
        confirmButton = { TextButton(onClick = { onResetSettings(); confirmReset = false }) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
    )
}

@Composable
private fun SimplePage(title: String, body: String, label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReferencePageHeading(title, body, "MAXXA AI • Itachi and Sakura inspired experience")
        ReferenceInfoCard(label, value)
        if (title.contains("HELP")) {
            ReferenceActionRow("?", "Frequently Asked Questions", "Backend setup, voice input and app features", "HELP") { }
            ReferenceActionRow("✉", "Contact & Support", "Support ticket integration is not configured", "INFO") { }
            ReferenceActionRow("⚑", "Report a Bug", "Share the issue with the project maintainer", "INFO") { }
        } else if (title.contains("ABOUT")) {
            ReferenceInfoCard("Built for", "Hindi • Hinglish • English\nChat • Voice • Code • Translate")
            ReferenceInfoCard("Current status", "Development build. Some features require a deployed HTTPS backend and have not been verified on a physical device.")
        }
    }
}


private data class CharacterUiTheme(
    val name: String,
    val emoji: String,
    val subtitle: String,
    val accent: Color,
    val secondary: Color,
    val highlight: Color
)

private val characterUiThemes = listOf(
    CharacterUiTheme("Itachi", "🩸", "Crimson • Sharingan", Color(0xFFFF426D), Color(0xFF8D173B), Color(0xFFFFA6C8)),
    CharacterUiTheme("Naruto", "🍥", "Orange • Hokage energy", Color(0xFFFF9B35), Color(0xFFB95616), Color(0xFFFFD39A)),
    CharacterUiTheme("Sasuke", "⚡", "Indigo • Lightning", Color(0xFF8B83FF), Color(0xFF4B45B8), Color(0xFFC8C4FF)),
    CharacterUiTheme("Kakashi", "🥷", "Silver • Copy ninja", Color(0xFF9BB7D1), Color(0xFF536E8A), Color(0xFFD8E8F7)),
    CharacterUiTheme("Madara", "🔥", "Dark red • Uchiha", Color(0xFFE64B4B), Color(0xFF8B202A), Color(0xFFFFB0A8)),
    CharacterUiTheme("Hinata", "💜", "Lavender • Byakugan", Color(0xFFB58BFF), Color(0xFF7551B5), Color(0xFFE2D1FF)),
    CharacterUiTheme("Sakura", "🌸", "Pink • Cherry blossom", Color(0xFFFF79B7), Color(0xFFBE477F), Color(0xFFFFD0E5)),
    CharacterUiTheme("Nezuko", "🎋", "Rose • Bamboo", Color(0xFFFF8C9D), Color(0xFFB94E68), Color(0xFFFFD0D7)),
    CharacterUiTheme("Obito", "🌀", "Violet • Masked shadow", Color(0xFF9B70E8), Color(0xFF5A3A9A), Color(0xFFD9C2FF)),
    CharacterUiTheme("Minato", "✨", "Golden • Yellow flash", Color(0xFFFFC34D), Color(0xFFB77B1D), Color(0xFFFFE6A6)),
    CharacterUiTheme("Mitsuri", "💗", "Mint pink • Love", Color(0xFFFF83B9), Color(0xFF55A88B), Color(0xFFC1F3DE)),
    CharacterUiTheme("Nobara", "🔨", "Amber • Fearless", Color(0xFFE6A34D), Color(0xFF9C6327), Color(0xFFFFD9A8))
)

@Composable
private fun AiCoreThemePage(
    coreTheme: String,
    customAccentHex: String,
    onThemeChange: (String) -> Unit,
    onCustomAccentChange: (String) -> Unit
) {
    var showAccentPicker by remember { mutableStateOf(false) }
    val coreThemes = listOf("Crimson Black", "AMOLED Pure Black", "Purple Neon", "Cyber Blue", "Emerald Green", "Pink Anime", "Custom Accent Color")
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReferencePageHeading("AI CORE THEME", "Customize the 3D AI Core", "These colours control the AI Core independently from the normal app Character Theme Gallery.")
        coreThemes.forEach { theme ->
            val preview = when (theme) {
                "AMOLED Pure Black" -> Color(0xFFB8C4D8)
                "Purple Neon" -> Color(0xFFB56CFF)
                "Cyber Blue" -> Color(0xFF55DFFF)
                "Emerald Green" -> Color(0xFF39E6A0)
                "Pink Anime" -> Color(0xFFFF78B7)
                "Custom Accent Color" -> runCatching { Color(android.graphics.Color.parseColor(customAccentHex)) }.getOrDefault(Cyan)
                else -> Color(0xFFFF426D)
            }
            Surface(
                color = if (coreTheme == theme) Color(0x4435C8FF) else Color(0xEE1A0B12),
                shape = RoundedCornerShape(15.dp),
                border = androidx.compose.foundation.BorderStroke(if (coreTheme == theme) 2.dp else 1.dp, if (coreTheme == theme) Cyan else Color(0x553F1B2A)),
                modifier = Modifier.fillMaxWidth().clickable { if (theme == "Custom Accent Color") showAccentPicker = true else onThemeChange(theme) }
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(preview, CircleShape))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(theme, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (coreTheme == theme) "Currently selected" else "Apply to 3D AI Core", color = Muted, fontSize = 10.sp)
                    }
                    Text(if (coreTheme == theme) "✓" else "○", color = Cyan, fontSize = 20.sp)
                }
            }
        }
    }
    if (showAccentPicker) {
        val accentChoices = listOf("#FF426D", "#35C8FF", "#B56CFF", "#39E6A0", "#FF78B7", "#FFC45C", "#FFFFFF", "#FF7A35")
        AlertDialog(
            onDismissRequest = { showAccentPicker = false },
            title = { Text("Choose custom accent", color = White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select an accent colour for your AI Core.", color = Muted, fontSize = 12.sp)
                    accentChoices.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { hex ->
                                val swatch = Color(android.graphics.Color.parseColor(hex))
                                Surface(modifier = Modifier.size(42.dp).clickable { onCustomAccentChange(hex); onThemeChange("Custom Accent Color"); showAccentPicker = false }, shape = CircleShape, color = swatch, border = androidx.compose.foundation.BorderStroke(if (customAccentHex.equals(hex, true)) 3.dp else 1.dp, White)) {}
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAccentPicker = false }) { Text("CLOSE") } },
            containerColor = Panel
        )
    }
}

@Composable
private fun CharacterThemeGalleryPage(
    selectedTheme: String,
    onThemeSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Text("CHARACTER COLLECTION", color = Gold, fontSize = 11.sp, letterSpacing = 2.sp)
        Text("Choose your anime theme", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
        Text("12 character-inspired looks for the normal MAXXA app UI. This is separate from the 3D AI Core themes and Voice Packs.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 7.dp, bottom = 14.dp))
        Surface(color = Panel, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FF426D)), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${characterUiThemes.firstOrNull { it.name == selectedTheme }?.emoji ?: "🎨"}", fontSize = 28.sp)
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text("ACTIVE CHARACTER THEME", color = Cyan, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(selectedTheme, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Text("✓ ACTIVE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.weight(1f)) {
            items(characterUiThemes.chunked(2)) { rowThemes ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    rowThemes.forEach { theme ->
                        val selected = selectedTheme == theme.name
                        Surface(
                            color = Color(0xEE1A0B12),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) theme.accent else Color(0x553F1B2A)),
                            modifier = Modifier.weight(1f).clickable { onThemeSelected(theme.name) }
                        ) {
                            Column(Modifier.padding(11.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(42.dp).background(Brush.linearGradient(listOf(theme.accent.copy(alpha = 0.38f), theme.secondary.copy(alpha = 0.45f))), CircleShape), contentAlignment = Alignment.Center) {
                                        Text(theme.emoji, fontSize = 22.sp)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (selected) Text("✓", color = theme.highlight, fontSize = 17.sp, fontWeight = FontWeight.Black)
                                }
                                Text(theme.name, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                                Text(theme.subtitle, color = Muted, fontSize = 9.sp, maxLines = 1)
                                Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    listOf(theme.accent, theme.secondary, theme.highlight).forEach { swatch ->
                                        Box(Modifier.weight(1f).height(5.dp).background(swatch, RoundedCornerShape(5.dp)))
                                    }
                                }
                                Text(if (selected) "SELECTED" else "TAP TO APPLY", color = if (selected) theme.highlight else Muted, fontSize = 8.sp, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 7.dp))
                            }
                        }
                    }
                    if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private data class AnimeVoicePreset(val pitch: Float, val rate: Float)

private val animeVoicePresets = mapOf(
    "Itachi" to AnimeVoicePreset(0.86f, 0.90f),
    "Naruto" to AnimeVoicePreset(1.13f, 1.08f),
    "Sasuke" to AnimeVoicePreset(0.91f, 0.96f),
    "Kakashi" to AnimeVoicePreset(0.98f, 0.94f),
    "Madara" to AnimeVoicePreset(0.82f, 0.88f),
    "Hinata" to AnimeVoicePreset(1.08f, 0.94f),
    "Sakura" to AnimeVoicePreset(1.10f, 1.02f),
    "Nezuko" to AnimeVoicePreset(1.18f, 0.92f),
    "Obito" to AnimeVoicePreset(0.94f, 1.02f),
    "Minato" to AnimeVoicePreset(1.04f, 1.04f),
    "Mitsuri" to AnimeVoicePreset(1.16f, 1.00f),
    "Nobara" to AnimeVoicePreset(1.07f, 1.06f)
)

@Composable
private fun VoicePackPage(
    language: String,
    voiceEnabled: Boolean,
    selectedAnimeVoice: String,
    onLanguageChange: (String) -> Unit,
    onVoiceChange: (Boolean) -> Unit,
    onAnimeVoiceChange: (String) -> Unit,
    onPreview: (String, String) -> Unit
) {
    val characters = listOf("Itachi", "Naruto", "Sasuke", "Kakashi", "Madara", "Hinata", "Sakura", "Nezuko", "Obito", "Minato", "Mitsuri", "Nobara")
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ReferencePageHeading("ANIME VOICE PACKS", "Choose a character voice style", "12 character-inspired TTS presets. Each has Hindi/Hinglish and English previews.")
        Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp)) {
                Text("REPLY LANGUAGE", color = Cyan, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Hindi / Hinglish", "English").forEach { name ->
                        FilterChip(selected = language == name, onClick = { onLanguageChange(name) }, label = { Text(name, fontSize = 11.sp) })
                    }
                }
            }
        }
        characters.forEachIndexed { index, name ->
            val selected = selectedAnimeVoice == name
            Surface(color = Color(0xEE1A0B12), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Cyan else Color(0x663D2530)), modifier = Modifier.fillMaxWidth().clickable { onAnimeVoiceChange(name) }) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).background(Color(0x33FF426D), CircleShape), contentAlignment = Alignment.Center) { Text("${index + 1}", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(name, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(if (selected) "SELECTED VOICE STYLE" else "Character-inspired TTS preset", color = if (selected) Cyan else Muted, fontSize = 9.sp, letterSpacing = 0.5.sp)
                        }
                        Text(if (selected) "✓ ACTIVE" else "SELECT", color = if (selected) Cyan else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onAnimeVoiceChange(name); onPreview("Namaste! Main $name style mein MAXXA AI hoon. Aapki kya madad karun?", "Hindi / Hinglish") }, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 6.dp), shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) { Text("▶ Hindi", fontSize = 10.sp) }
                        OutlinedButton(onClick = { onAnimeVoiceChange(name); onPreview("Hello! This is the $name-inspired MAXXA voice style. How can I help you?", "English") }, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 6.dp), shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan)) { Text("▶ English", fontSize = 10.sp) }
                    }
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Speak AI replies", color = White, fontWeight = FontWeight.SemiBold)
                    Text("Read MAXXA's answers aloud using the selected style", color = Muted, fontSize = 11.sp)
                }
                Switch(checked = voiceEnabled, onCheckedChange = onVoiceChange)
            }
        }
        ReferenceInfoCard("Voice pack note", "These are character-inspired pitch and speed presets using your phone's installed Hindi and English Text-to-Speech voices. They are not original anime actor recordings or exact voice clones. Voice quality depends on your Android TTS engine.")
    }
}

@Composable
private fun ReferencePageHeading(eyebrow: String, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        Text(eyebrow, color = Gold, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(title, color = White, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
        Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun ReferenceInfoCard(title: String, body: String) {
    Surface(
        color = Color(0xEE1A0B12), shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x663D2530)),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title.uppercase(), color = Cyan, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
            Text(body, color = White, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun ReferenceActionRow(icon: String, title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xEE1A0B12), shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x553F1B2A)),
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(Color(0x33FF426D), CircleShape), contentAlignment = Alignment.Center) {
                Text(icon, color = Color(0xFFFF9FBE), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
            Text(action, color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("  ›", color = White, fontSize = 19.sp)
        }
    }
}

private const val BACKEND_TOKEN_KEY_ALIAS = "maxxa_backend_access_token_v1"

private fun getBackendTokenKey(): java.security.Key {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (keyStore.getKey(BACKEND_TOKEN_KEY_ALIAS, null) as? java.security.Key)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(KeyGenParameterSpec.Builder(
        BACKEND_TOKEN_KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build())
    return generator.generateKey()
}

private fun encryptBackendToken(context: android.content.Context, token: String): String {
    if (token.isBlank()) return ""
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, getBackendTokenKey())
    val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
    val packed = cipher.iv + encrypted
    return Base64.encodeToString(packed, Base64.NO_WRAP)
}

private fun decryptBackendToken(context: android.content.Context, encoded: String): String {
    if (encoded.isBlank()) return ""
    return runCatching {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 12)
        val iv = packed.copyOfRange(0, 12)
        val encrypted = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getBackendTokenKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrDefault("")
}

private fun isValidHttpsBackendUrl(value: String): Boolean {
    val parsed = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return false
    return parsed.scheme.equals("https", ignoreCase = true) &&
        !parsed.host.isNullOrBlank() &&
        parsed.userInfo == null &&
        parsed.query == null &&
        parsed.fragment == null
}

private fun checkBackendHealth(backendUrl: String): String {
    if (!isValidHttpsBackendUrl(backendUrl)) return "Enter a valid HTTPS backend URL with a hostname."
    var connection: HttpURLConnection? = null
    return try {
        val base = backendUrl.trim().trimEnd('/')
        connection = (URL("$base/ready").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(body) }.getOrNull()
        if (code in 200..299 && json?.optBoolean("ok") == true) "✓ CONNECTED — Gemini backend and required server credentials are ready."
        else if (json?.optBoolean("geminiConfigured") == false) "⚠ ONLINE, NOT READY — GEMINI_API_KEY is missing from server secrets."
        else if (json?.optBoolean("clientAuthenticationConfigured") == false) "⚠ ONLINE, NOT READY — MAXXA_CLIENT_TOKEN is missing from server secrets."
        else "✕ NOT READY — Backend readiness check failed (HTTP $code). Verify deployment and server logs."
    } catch (e: java.net.SocketTimeoutException) {
        "✕ CONNECTION TIMEOUT — Backend did not respond in time."
    } catch (_: Exception) {
        "✕ DISCONNECTED — Backend is unreachable. Check the HTTPS URL and internet connection."
    } finally {
        connection?.disconnect()
    }
}

private fun requestGeneratedImage(prompt: String, backendUrl: String, aspectRatio: String, backendToken: String = "", cancellation: VoiceRequestCancellation? = null): Pair<ByteArray, String> {
    require(isValidHttpsBackendUrl(backendUrl)) { "Configure a valid HTTPS backend URL in the API Key screen first." }
    var connection: HttpURLConnection? = null
    try {
        val base = backendUrl.trim().trimEnd('/')
        connection = (URL("$base/api/image").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 100_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (backendToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $backendToken")
        }
        cancellation?.attach(connection)
        val payload = JSONObject().put("prompt", prompt).put("aspectRatio", aspectRatio).toString()
        cancellation?.checkActive()
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        cancellation?.checkActive()
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(body) }.getOrNull()
        if (code !in 200..299) throw IllegalStateException("Image backend error (HTTP $code): ${json?.optString("error").orEmpty().ifBlank { "Check model access, quota and server logs." }}")
        val encoded = json?.optString("imageBase64").orEmpty()
        if (encoded.isBlank()) throw IllegalStateException("Backend returned no image data.")
        val mime = normalizeImageMime(json?.optString("mimeType").orEmpty())
        require(mime in setOf("image/png", "image/jpeg", "image/webp")) { "Backend returned an unsupported image format: $mime" }
        val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrElse { throw IllegalStateException("Backend returned invalid image data.") }
        require(bytes.isNotEmpty() && bytes.size <= 20 * 1024 * 1024) { "Generated image is empty or exceeds the 20 MB limit." }
        cancellation?.checkActive()
        return bytes to mime
    } finally { connection?.let { cancellation?.detach(it); it.disconnect() } }
}

private fun normalizeImageMime(value: String): String = value.substringBefore(';').trim().lowercase(Locale.ROOT).ifBlank { "image/png" }
private fun imageExtension(mime: String): String = when (normalizeImageMime(mime)) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }

private fun askGeminiStream(
    apiKey: String,
    message: String,
    backendUrl: String,
    backendToken: String,
    cancellation: VoiceRequestCancellation? = null,
    attachment: ChatAttachment? = null,
    onChunk: (String) -> Unit
) {
    if (backendUrl.isBlank()) throw IllegalStateException("No secure backend configured. Save your deployed HTTPS backend URL in API settings.")
    val base = backendUrl.trim().trimEnd('/')
    if (!isValidHttpsBackendUrl(base)) throw IllegalStateException("For security, enter a valid HTTPS backend URL only.")
    var connection: HttpURLConnection? = null
    var receivedText = false
    try {
        cancellation?.checkActive()
        connection = (URL("$base/api/chat/stream").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; instanceFollowRedirects = false; connectTimeout = 15000; readTimeout = 65000; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/x-ndjson")
            if (backendToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $backendToken")
        }
        cancellation?.attach(connection)
        val payloadJson = JSONObject().put("message", message)
        attachment?.let { payloadJson.put("attachment", JSONObject().put("name", it.name).put("mimeType", it.mimeType).put("data", it.base64)) }
        cancellation?.checkActive()
        connection.outputStream.use { it.write(payloadJson.toString().toByteArray(Charsets.UTF_8)) }
        cancellation?.checkActive()
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            val collected = StringBuilder()
            reader.forEachLine { line ->
                cancellation?.checkActive()
                if (line.isNotBlank()) {
                    val item = runCatching { JSONObject(line) }.getOrElse {
                        if (code in 200..299) throw IllegalStateException("Backend sent an invalid streaming response. Please retry.")
                        JSONObject()
                    }
                    val errorText = item.optString("error").takeIf { it.isNotBlank() }
                    if (errorText != null) throw IllegalStateException(errorText)
                    val chunk = item.optString("text")
                    if (chunk.isNotEmpty()) { receivedText = true; collected.append(chunk); onChunk(chunk) }
                }
            }
            collected.toString()
        }.orEmpty()
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
            val messageText = when (code) {
                400 -> "Bad request: ${detail.ifBlank { "Check your message or attachment." }}"
                401, 403 -> "Backend authentication failed. Check the saved MAXXA backend token."
                404 -> "Chat endpoint not found. Update/redeploy the backend."
                408, 504 -> "Backend request timed out. Please retry."
                429 -> "Backend is busy or rate-limited. Wait a moment and retry."
                in 500..599 -> "Backend server error (HTTP $code): ${detail.ifBlank { "Please retry later." }}"
                else -> "Backend request failed (HTTP $code): ${detail.ifBlank { "Check backend settings." }}"
            }
            throw IllegalStateException(messageText)
        }
        if (!receivedText) throw IllegalStateException("AI returned an empty response. Please retry.")
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        if (e is IllegalStateException) throw e
        cancellation?.checkActive()
        if (e is java.net.SocketTimeoutException) {
            throw IllegalStateException("Backend connection timed out. Please retry.", e)
        }
        throw IllegalStateException("Connection interrupted. Check internet/backend status and retry.", e)
    } finally {
        connection?.let { cancellation?.detach(it); it.disconnect() }
    }
}

private fun askGemini(apiKey: String, message: String, backendUrl: String = "", backendToken: String = ""): String {
    if (backendUrl.isBlank()) {
        return "No secure backend configured. Open the menu → API Key and save your deployed HTTPS backend URL."
    }
    var proxy: HttpURLConnection? = null
    return try {
        val base = backendUrl.trim().trimEnd('/')
        if (!isValidHttpsBackendUrl(base)) return "For security, enter a valid HTTPS backend URL only."
        proxy = (URL("$base/api/chat").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = 15000
            readTimeout = 65000
            instanceFollowRedirects = false
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (backendToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $backendToken")
        }
        val payload = JSONObject().put("message", message).toString()
        proxy.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val code = proxy.responseCode
        val body = (if (code in 200..299) proxy.inputStream else proxy.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(body) }.getOrNull()
        if (code in 200..299) {
            json?.optString("text")?.takeIf { it.isNotBlank() }
                ?: json?.optString("answer")?.takeIf { it.isNotBlank() }
                ?: "Backend returned an empty response."
        } else {
            val detail = json?.optString("error").orEmpty()
            when (code) {
                401, 403 -> "Backend authentication failed. Check the saved backend token."
                408, 504 -> "Backend request timed out. Please retry."
                429 -> "Backend is busy or rate-limited. Wait a moment and retry."
                in 500..599 -> "Backend server error (HTTP $code): ${detail.ifBlank { "Please retry later." }}"
                else -> "Backend request failed (HTTP $code): ${detail.ifBlank { "Check backend settings." }}"
            }
        }
    } catch (e: java.net.SocketTimeoutException) {
        "Backend connection timed out. Please retry."
    } catch (_: Exception) {
        "Backend is disconnected or unreachable. Check its HTTPS URL and internet connection."
    } finally {
        proxy?.disconnect()
    }
}
