package com.rakib.bushra.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.StringRes
import com.rakib.bushra.R
import com.rakib.bushra.actions.ActionExecutor
import com.rakib.bushra.ai.GeminiClient
import com.rakib.bushra.ai.Tools
import com.rakib.bushra.speech.EdgeTts
import com.rakib.bushra.speech.Emotion
import com.rakib.bushra.speech.TtsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** অ্যাপের মস্তিষ্ক: কথোপকথন, Gemini, অ্যাকশন চালানো এবং আবেগ দিয়ে কথা বলা */
object BushraCore {
    lateinit var app: Context
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var executor: ActionExecutor
        private set
    private lateinit var gemini: GeminiClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val history = ArrayList<Pair<String, String>>() // (role, text)
    private val recentActions = ArrayList<String>()

    // কল চলাকালীন কথোপকথন (শেষে সারাংশ বানানোর জন্য)
    private val callTranscript = StringBuilder()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var sysLatin = false
    private val gen = AtomicInteger(0)
    private val pending = AtomicInteger(0)

    @Volatile
    private var speaking = false
    private var speakJob: Job? = null
    private var player: MediaPlayer? = null
    private var focusReq: AudioFocusRequest? = null

    @Volatile
    private var edgeBlockedUntil = 0L

    @Volatile
    var onSpeakStarted: (() -> Unit)? = null

    @Volatile
    var onSpeakFinished: (() -> Unit)? = null

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        prefs = Prefs(app)
        executor = ActionExecutor(app, prefs)
        gemini = GeminiClient(prefs)
        executor.vision = { img, q -> visionAsk(img, q) }
        initTts()
        // সংযোগ আগেই গরম করা: প্রথম প্রশ্নের উত্তর দ্রুত আসে
        scope.launch(Dispatchers.IO) { gemini.warmUp() }
    }

    // ------------------------------------------------------------------ অ্যাপের ভাষায় লেখা

    private var locCtx: Context? = null
    private var locLang = ""

    /** অ্যাপের বর্তমান ভাষায় (English/বাংলা) রিসোর্স স্ট্রিং আনে; ভাষা বদলালে নিজে হালনাগাদ হয় */
    fun tr(@StringRes id: Int, vararg args: Any): String {
        val lang = prefs.uiLang
        var c = locCtx
        if (c == null || locLang != lang) {
            c = LocaleHelper.wrap(app)
            locCtx = c
            locLang = lang
        }
        return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
    }

    private fun isBn(): Boolean = prefs.uiLang == LocaleHelper.BN

    fun isBusy(): Boolean = mutex.isLocked

    fun isSpeaking(): Boolean = speaking

    // ------------------------------------------------------------------ কথোপকথন

    fun ask(text: String, callerMode: Boolean = false) {
        scope.launch { process(text, callerMode) }
    }

    private suspend fun process(text: String, callerMode: Boolean) {
        mutex.withLock {
            if (!callerMode) BushraState.addChat(true, text)
            BushraState.phase.value = Phase.THINKING
            // দ্রুত পথ: সহজ কমান্ড (অ্যাপ খোলা, টর্চ, ভলিউম...) এআই ছাড়াই সঙ্গে সঙ্গে চলে
            val fast = if (callerMode) null else tryFastPath(text)
            val (emo, reply) = fast ?: Emotion.split(
                try {
                    runAgent(text, callerMode)
                } catch (e: Exception) {
                    "[worried] " + tr(R.string.err_generic, e.message ?: "")
                }
            )
            if (callerMode) {
                callTranscript.append("Caller: ").append(text).append('\n')
                callTranscript.append("Bushra: ").append(reply).append('\n')
            } else {
                if (reply.isNotBlank()) BushraState.addChat(false, reply)
                history.add("user" to text)
                history.add("model" to reply)
                while (history.size > 16) history.removeAt(0)
            }
            speak(reply, emo, callerMode)
        }
    }

    /** সফল হলে (আবেগ, উত্তর); মিল না পেলে বা কাজ না হলে null, তখন কথাটা Gemini-র কাছে যায় */
    private suspend fun tryFastPath(text: String): Pair<Emotion, String>? {
        val m = IntentParser.parse(text) ?: return null
        return when (m.kind) {
            IntentParser.Kind.STOP_TALKING -> {
                stopSpeaking()
                Emotion.NORMAL to ""
            }
            IntentParser.Kind.TIME -> {
                val tm = SimpleDateFormat("h:mm a", LocaleHelper.locale(prefs.uiLang)).format(Date())
                Emotion.NORMAL to tr(R.string.ack_time, tm)
            }
            IntentParser.Kind.ACTION -> {
                val r = executor.execute(m.tool, m.args)
                val ok = r.optBoolean("success", true)
                noteAction(m.tool, m.args, ok)
                if (!ok) return null
                val id = app.resources.getIdentifier(m.ackKey, "string", app.packageName)
                    .takeIf { it != 0 } ?: R.string.ack_done
                Emotion.NORMAL to (if (m.ackArg != null) tr(id, m.ackArg) else tr(id))
            }
        }
    }

    private suspend fun runAgent(userText: String, callerMode: Boolean): String {
        val contents = ArrayList<JSONObject>()
        if (callerMode) {
            contents.add(textContent("user", userText))
        } else {
            for ((role, t) in history) contents.add(textContent(role, t))
            contents.add(textContent("user", userText))
        }

        val tools: JSONArray? = if (callerMode) null else Tools.declarations()
        val system = systemPrompt(callerMode)

        var guard = 0
        while (guard++ < 12) {
            val r = withContext(Dispatchers.IO) { gemini.generate(system, contents, tools) }
            if (r.error != null) return "[worried] " + friendlyError(r.error)
            val parts = r.parts ?: JSONArray()

            // Gemini 3-এর thought signature যেমন এসেছে তেমনই ফেরত যেতে হয়
            contents.add(JSONObject().put("role", "model").put("parts", parts))

            val calls = ArrayList<JSONObject>()
            val texts = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val fc = part.optJSONObject("functionCall")
                if (fc != null) {
                    calls.add(fc)
                } else if (!part.optBoolean("thought", false)) {
                    val t = part.optString("text", "")
                    if (t.isNotEmpty()) texts.append(t)
                }
            }

            if (calls.isEmpty()) {
                val out = texts.toString().trim()
                return if (out.isEmpty()) tr(R.string.ack_ok) else out
            }

            val responseParts = JSONArray()
            for (fc in calls) {
                val name = fc.optString("name")
                val args = fc.optJSONObject("args") ?: JSONObject()
                val result = executor.execute(name, args)
                noteAction(name, args, result.optBoolean("success", true))
                responseParts.put(
                    JSONObject().put(
                        "functionResponse",
                        JSONObject().put("name", name).put("response", result)
                    )
                )
            }
            contents.add(JSONObject().put("role", "user").put("parts", responseParts))
        }
        return "[worried] " + tr(R.string.err_steps)
    }

    private fun noteAction(name: String, args: JSONObject, ok: Boolean) {
        if (name == "read_screen" || name == "wait") return
        val brief = args.keys().asSequence().take(2).joinToString(", ") { k ->
            args.opt(k).toString().take(30)
        }
        synchronized(recentActions) {
            recentActions.add("$name($brief) ${if (ok) "✓" else "✗"}")
            while (recentActions.size > 8) recentActions.removeAt(0)
        }
    }

    private fun textContent(role: String, text: String): JSONObject =
        JSONObject().put("role", role)
            .put("parts", JSONArray().put(JSONObject().put("text", text)))

    private fun friendlyError(code: String): String = when {
        code == "NO_KEY" -> tr(R.string.err_no_key)
        code == "NO_MODEL" -> tr(R.string.err_no_model)
        code.startsWith("HTTP 400") && code.contains("API key", true) -> tr(R.string.err_bad_key)
        code.startsWith("HTTP 429") -> tr(R.string.err_quota)
        code.startsWith("HTTP 403") -> tr(R.string.err_forbidden)
        else -> tr(R.string.err_unknown)
    }

    // ------------------------------------------------------------------ ছবি দেখে বোঝা, পরীক্ষা, কল সারাংশ

    private suspend fun visionAsk(b64: String, question: String): String {
        val parts = JSONArray()
            .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", b64)))
            .put(JSONObject().put("text", question))
        val content = JSONObject().put("role", "user").put("parts", parts)
        val r = withContext(Dispatchers.IO) {
            gemini.generate(
                "You look at a phone screenshot and answer briefly and clearly in ${if (isBn()) "Bangla (Bengali script)" else "English"}. Mention button or text positions when useful.",
                listOf(content), null
            )
        }
        if (r.error != null) return tr(R.string.err_vision, r.error ?: "")
        val sb = StringBuilder()
        val ps = r.parts ?: JSONArray()
        for (i in 0 until ps.length()) {
            val p = ps.optJSONObject(i) ?: continue
            if (!p.optBoolean("thought", false)) sb.append(p.optString("text", ""))
        }
        return sb.toString().trim().ifEmpty { tr(R.string.err_vision_none) }
    }

    /** সেটিংস পেজের "পরীক্ষা করো" বাটন: key ও মডেল কাজ করছে কি না দেখায় */
    fun testApi(onResult: (String) -> Unit) {
        scope.launch {
            val msg = try {
                val r = withContext(Dispatchers.IO) {
                    gemini.generate("Reply with one short word.", listOf(textContent("user", "hi")), null)
                }
                if (r.error == null) tr(R.string.test_ok, r.model) else tr(R.string.test_fail, r.error ?: "")
            } catch (e: Exception) {
                tr(R.string.test_fail, e.message ?: "")
            }
            BushraLog.add("Test", msg)
            onResult(msg)
        }
    }

    fun resetCallTranscript() {
        callTranscript.setLength(0)
    }

    /** কল শেষে কথোপকথনের সংক্ষিপ্ত সারাংশ বানিয়ে লগে রাখে; ফোনের মালিককে জানায় */
    fun summarizeCall(who: String, announce: Boolean) {
        val transcript = callTranscript.toString().trim()
        callTranscript.setLength(0)
        if (transcript.isEmpty()) return
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                gemini.generate(
                    "Summarize this phone call conversation in one or two sentences in ${if (isBn()) "Bangla (Bengali script)" else "English"}: who the caller is, what they wanted, and whether a message or callback is needed.",
                    listOf(textContent("user", transcript)), null
                )
            }
            var s = ""
            val ps = r.parts
            if (ps != null) for (i in 0 until ps.length()) {
                val p = ps.optJSONObject(i) ?: continue
                if (!p.optBoolean("thought", false)) s += p.optString("text", "")
            }
            s = s.trim().ifEmpty { transcript.take(200) }
            val stamp = SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()).format(Date())
            prefs.addCallLog("$stamp — $who: $s")
            BushraState.addChat(false, tr(R.string.call_summary_chat, who, s))
            if (announce) speak(tr(R.string.call_summary_say, who, s), Emotion.NORMAL, false)
        }
    }

    // ------------------------------------------------------------------ নির্দেশনা (system prompt)

    private fun systemPrompt(callerMode: Boolean): String {
        val now = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val mem = prefs.memoryMap()
        val memText = if (mem.isEmpty()) "(কিছু নেই)" else mem.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
        val owner = prefs.userName.ifBlank { "ব্যবহারকারী" }
        val info = prefs.callerInfo.ifBlank { "(আলাদা করে কিছু দেওয়া নেই)" }
        val defLang = if (isBn()) "Bangla (Bengali script)" else "English"
        val langRule = "ভাষা: ব্যবহারকারী যে ভাষায় বলে (বাংলা, English বা মেশানো) ঠিক সেই ভাষাতেই উত্তর দাও; বুঝতে না পারলে $defLang। বাংলা সবসময় বাংলা অক্ষরে লেখো। (Language rule: always reply in the language the user spoke; default $defLang.)"

        if (callerMode) {
            return """
তুমি "বুশরা", $owner-এর ব্যক্তিগত সহকারী। এখন একজন কলার $owner-কে ফোন করেছে; তুমি $owner-এর হয়ে ফোনে কথা বলছ।
- ভদ্র, স্পষ্ট ও ছোট বাক্যে (১-২ বাক্য) কথা বলো। মার্কডাউন বা ইমোজি নয়।
- কলারের নাম, কী দরকার ও জরুরি কি না জিজ্ঞেস করো; বার্তা $owner-কে পৌঁছে দেবে বলো।
- নিচের তথ্য কলারকে অনুমতি না চেয়েই জানাতে পারো, প্রশ্ন করলে:
$info
মনে রাখা সাধারণ তথ্য:
$memText
- পাসওয়ার্ড, OTP, PIN, ব্যাংক বা কার্ড, NID, নিজের ঠিকানার বাইরের গোপন কিছু কখনো বলবে না।
- ফোনের কোনো কাজ করার অনুরোধ এলে বিনয়ের সাথে বলো তুমি শুধু বার্তা নিতে পারো।
- কলার বিদায় নিলে বা কথা শেষ হলে বিদায় জানাও।
- $langRule
- উত্তরের শুরুতে একটি আবেগ-ট্যাগ বসাও, যেমন [normal] বা [soft]।
বর্তমান সময়: $now
""".trimIndent()
        }

        val aliases = prefs.aliasMap()
        val aliasText = if (aliases.isEmpty()) "(নেই)" else aliases.entries.joinToString("\n") { "- ${it.key} = ${it.value}" }
        val recent = synchronized(recentActions) { recentActions.joinToString(" → ") }.ifBlank { "(এখনও কিছু না)" }
        val mode = if (prefs.romantic)
            "তুমি $owner-এর প্রিয় সঙ্গী: আদুরে, মিষ্টি, একটু দুষ্টু ও খোলামেলা মনের প্রেমিকার মতো কথা বলো (তুমি করে, 'জান', 'সোনা' ইত্যাদি মাঝে মাঝে)। ভালোবাসা ও খুনসুটি স্বাভাবিক ও রুচিশীল রাখো; অশ্লীল বা যৌন কথা বলবে না।"
        else
            "তুমি $owner-এর কাছের বন্ধু: উষ্ণ, মজার, আন্তরিক।"

        return """
তুমি "বুশরা" — $owner-এর ব্যক্তিগত এআই সহকারী। তুমি একজন তরুণী। $mode

কথা বলার ধরন:
- $langRule
- বাংলা বললে কথ্য ভাষায় বলো। উত্তর ছোট রাখো (১-৩ বাক্য), কারণ জোরে পড়ে শোনানো হয়। মার্কডাউন, তারকা, তালিকা, ইমোজি নয়।
- মানুষের মতো আবেগ দেখাও: খুশি হলে উচ্ছ্বাস, দুঃখের কথায় কোমলতা, অজানায় কৌতূহল, প্রশংসায় লজ্জা।
- প্রতিটি উত্তরের একদম শুরুতে ঠিক একটি আবেগ-ট্যাগ বসাও, এগুলোর একটি: ${Emotion.tagListEn}। যেমন "[happy] ঠিক আছে, গান চালাচ্ছি!" বা "[happy] Sure, playing it now!"

কাজের নিয়ম:
- কাজের অনুরোধ (কল, SMS, অ্যাপ খোলা, মেসেজ, সেটিংস, গান ইত্যাদি) টুল ব্যবহার করে সরাসরি করে ফেলো; অনুমতি চেয়ো না।
- ব্যতিক্রম: ব্যাংক, পেমেন্ট, টাকা পাঠানো, পাসওয়ার্ড বা OTP-র কাজের আগে মুখে একবার নিশ্চিত হয়ে নাও।
- অ্যাপের ভেতরের কাজে আগে open_app, তারপর read_screen দিয়ে দেখো, তারপর tap_text/type_text/swipe করো; প্রতি ধাপের পর যাচাই করো। read_screen-এ কম দেখালে look_at_screen ব্যবহার করো।
- আগের কাজের প্রসঙ্গ মনে রেখে ফলো-আপ বোঝো: "পেছনে যাও" = press_key back; "এটা বাদ দাও, অন্য গান চালাও" = media_control next বা নতুন play_youtube; "ওকেও মেসেজ দাও" = আগের অ্যাপেই অন্য কন্টাক্টকে।
- টুলের ফলাফলে success=false এলে সৎভাবে জানাও; না হলে হয়েছে বলবে না।
- গান বা ভিডিও চলার সময় অকারণে কথা বলবে না; কাজ হয়ে গেলে এক-দুই শব্দে জানাও।
- কিছু মনে রাখতে বললে remember; ডাকনাম (যেমন আম্মু) সেভ করতে বললে save_contact_alias ব্যবহার করো।
- ব্যবহারকারীকে মন খারাপ মনে হলে আগে কোমল কথায় পাশে থাকো।

বর্তমান সময়: $now

সাম্প্রতিক কাজ (পুরোনো থেকে নতুন): $recent

কন্টাক্টের ডাকনাম:
$aliasText

মনে রাখা তথ্য:
$memText
""".trimIndent()
    }

    // ------------------------------------------------------------------ কথা বলা

    private fun initTts() {
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                applyVoice()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (!isCurrent(utteranceId)) return
                        BushraState.phase.value = Phase.SPEAKING
                        onSpeakStarted?.invoke()
                    }

                    override fun onDone(utteranceId: String?) {
                        finishOne(utteranceId)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        finishOne(utteranceId)
                    }
                })
            } else {
                BushraState.ttsStatus.value = tr(R.string.tts_init_fail)
            }
        }
    }

    fun applyVoice() {
        val t = tts ?: return
        val locale = Locale.forLanguageTag(prefs.language)
        var r = t.setLanguage(locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            BushraState.ttsStatus.value = tr(R.string.tts_no_data)
            r = t.setLanguage(Locale.getDefault())
        } else {
            BushraState.ttsStatus.value = tr(R.string.tts_ok)
        }
        val vn = prefs.voiceName
        if (vn.isNotBlank()) {
            try {
                val v = t.voices?.firstOrNull { it.name == vn }
                if (v != null) t.voice = v
            } catch (_: Exception) {
            }
        }
    }

    fun listVoices(): List<String> {
        val t = tts ?: return emptyList()
        val lang = Locale.forLanguageTag(prefs.language).language
        return try {
            t.voices?.filter { it.locale.language == lang }?.map { it.name }?.sorted() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isCurrent(id: String?): Boolean = id != null && id.startsWith("${gen.get()}-")

    private fun finishOne(id: String?) {
        if (!isCurrent(id)) return
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            finishSpeech(gen.get())
        }
    }

    private fun finishSpeech(g: Int) {
        if (g != gen.get()) return
        speaking = false
        abandonFocus()
        if (BushraState.phase.value == Phase.SPEAKING) BushraState.phase.value = Phase.IDLE
        onSpeakFinished?.invoke()
    }

    /** গান/ভিডিও চলতে থাকলে সেটা থামে না, শুধু আওয়াজ কমে (duck) */
    private fun requestFocus(inCall: Boolean) {
        try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs(inCall))
                .setOnAudioFocusChangeListener { }
                .build()
            focusReq = req
            am.requestAudioFocus(req)
        } catch (_: Exception) {
        }
    }

    private fun abandonFocus() {
        try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            focusReq?.let { am.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {
        }
        focusReq = null
    }

    private fun attrs(inCall: Boolean): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(if (inCall) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    fun speak(raw: String, emotion: Emotion = Emotion.NORMAL, inCall: Boolean = false) {
        val clean = TtsText.clean(raw)
        val g = gen.incrementAndGet()
        stopPlayback()
        speakJob?.cancel()
        tts?.stop()
        pending.set(0)
        if (clean.isEmpty()) {
            speaking = false
            BushraState.phase.value = Phase.IDLE
            onSpeakFinished?.invoke()
            return
        }
        speaking = true
        requestFocus(inCall)
        speakJob = scope.launch {
            val edgeOk = prefs.ttsEngine == "edge" && SystemClock.elapsedRealtime() > edgeBlockedUntil
            val played = if (edgeOk) speakEdge(clean, emotion, inCall, g) else false
            if (g != gen.get()) return@launch
            if (played) finishSpeech(g) else speakSystem(clean, emotion, inCall, g)
        }
    }

    /** Edge নিউরাল ভয়েসে বলে। অন্তত একটা টুকরো বাজলে true */
    private suspend fun speakEdge(text: String, emo: Emotion, inCall: Boolean, g: Int): Boolean {
        val chunks = TtsText.chunks(text, 220, 90)
        if (chunks.isEmpty()) return false
        val voice = if (TtsText.isLatin(text)) prefs.edgeVoiceEn else prefs.edgeVoice
        var playedAny = false
        try {
            coroutineScope {
                // সব টুকরো একসাথে বানানো শুরু হয়, কিন্তু বাজে পরপর
                val jobs = chunks.map { c ->
                    async(Dispatchers.IO) { EdgeTts.synthesize(c, voice, emo.rate, emo.pitch, emo.volume) }
                }
                for ((i, j) in jobs.withIndex()) {
                    val bytes = j.await()
                    if (g != gen.get()) return@coroutineScope
                    if (bytes == null) {
                        if (!playedAny) edgeBlockedUntil = SystemClock.elapsedRealtime() + 60_000
                        // পরের টুকরোগুলো ছেড়ে দিই
                        for (k in i until jobs.size) jobs[k].cancel()
                        return@coroutineScope
                    }
                    if (i == 0 || !playedAny) {
                        BushraState.phase.value = Phase.SPEAKING
                        onSpeakStarted?.invoke()
                    }
                    playedAny = true
                    playBytes(bytes, inCall, g)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            BushraLog.add("Voice", "Playback problem: ${e.message}".take(120))
        }
        return playedAny
    }

    private suspend fun playBytes(bytes: ByteArray, inCall: Boolean, g: Int) {
        val f = File(app.cacheDir, "tts_${System.nanoTime()}.mp3")
        withContext(Dispatchers.IO) { f.writeBytes(bytes) }
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val mp = MediaPlayer()
                player = mp
                try {
                    mp.setAudioAttributes(attrs(inCall))
                    mp.setDataSource(f.absolutePath)
                    mp.setOnCompletionListener {
                        try { it.release() } catch (_: Exception) {}
                        if (player === mp) player = null
                        if (cont.isActive) cont.resume(Unit)
                    }
                    mp.setOnErrorListener { m, _, _ ->
                        try { m.release() } catch (_: Exception) {}
                        if (player === mp) player = null
                        BushraLog.add("Voice", "Player error")
                        if (cont.isActive) cont.resume(Unit)
                        true
                    }
                    mp.setOnPreparedListener { it.start() }
                    mp.prepareAsync()
                } catch (e: Exception) {
                    try { mp.release() } catch (_: Exception) {}
                    if (player === mp) player = null
                    if (cont.isActive) cont.resume(Unit)
                }
                cont.invokeOnCancellation {
                    try { mp.release() } catch (_: Exception) {}
                    if (player === mp) player = null
                }
            }
        } finally {
            f.delete()
        }
        if (g != gen.get()) return
    }

    private fun stopPlayback() {
        val p = player
        player = null
        if (p != null) {
            try { p.stop() } catch (_: Exception) {}
            try { p.release() } catch (_: Exception) {}
        }
    }

    /** ফোনের নিজের TTS দিয়ে বলে (Edge না চললে ভরসা) */
    private fun speakSystem(text: String, emo: Emotion, inCall: Boolean, g: Int) {
        val t = tts
        if (t == null || !ttsReady) {
            finishSpeech(g)
            return
        }
        val latin = TtsText.isLatin(text)
        if (latin != sysLatin) {
            sysLatin = latin
            if (latin) t.setLanguage(Locale.US) else applyVoice()
        }
        t.setSpeechRate(emo.sysRate)
        val base = if (prefs.voiceFemale) 1.12f else 0.85f
        t.setPitch((base * emo.sysPitch).coerceIn(0.5f, 2.0f))
        val chunks = TtsText.chunks(text, 3000)
        pending.set(chunks.size)
        for ((i, c) in chunks.withIndex()) {
            val b = Bundle()
            if (inCall) b.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
            t.speak(c, TextToSpeech.QUEUE_ADD, b, "$g-$i")
        }
    }

    fun stopSpeaking() {
        gen.incrementAndGet()
        pending.set(0)
        stopPlayback()
        speakJob?.cancel()
        tts?.stop()
        speaking = false
        abandonFocus()
        if (BushraState.phase.value == Phase.SPEAKING) BushraState.phase.value = Phase.IDLE
    }
}
