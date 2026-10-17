package com.rakib.bushra.core

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.rakib.bushra.actions.ActionExecutor
import com.rakib.bushra.ai.GeminiClient
import com.rakib.bushra.ai.Tools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** অ্যাপের মস্তিষ্ক: কথোপকথন, Gemini, অ্যাকশন চালানো এবং কথা বলা */
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

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val gen = AtomicInteger(0)
    private val pending = AtomicInteger(0)

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
        initTts()
    }

    fun isBusy(): Boolean = mutex.isLocked

    fun isSpeaking(): Boolean = pending.get() > 0

    // ------------------------------------------------------------------ কথোপকথন

    fun ask(text: String, callerMode: Boolean = false) {
        scope.launch { process(text, callerMode) }
    }

    private suspend fun process(text: String, callerMode: Boolean) {
        mutex.withLock {
            BushraState.addChat(true, text)
            BushraState.phase.value = Phase.THINKING
            val reply = try {
                runAgent(text, callerMode)
            } catch (e: Exception) {
                "দুঃখিত, একটা সমস্যা হয়েছে: ${e.message}"
            }
            BushraState.addChat(false, reply)
            history.add("user" to text)
            history.add("model" to reply)
            while (history.size > 16) history.removeAt(0)
            speak(reply)
        }
    }

    private suspend fun runAgent(userText: String, callerMode: Boolean): String {
        val contents = ArrayList<JSONObject>()
        for ((role, t) in history) contents.add(textContent(role, t))
        contents.add(textContent("user", userText))

        val tools: JSONArray? = if (callerMode) null else Tools.declarations()
        val system = systemPrompt(callerMode)

        var guard = 0
        while (guard++ < 12) {
            val r = gemini.generate(system, contents, tools)
            if (r.error != null) return friendlyError(r.error)
            val parts = r.parts ?: JSONArray()

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
                return if (out.isEmpty()) "ঠিক আছে।" else out
            }

            val responseParts = JSONArray()
            for (fc in calls) {
                val name = fc.optString("name")
                val args = fc.optJSONObject("args") ?: JSONObject()
                val result = executor.execute(name, args)
                responseParts.put(
                    JSONObject().put(
                        "functionResponse",
                        JSONObject().put("name", name).put("response", result)
                    )
                )
            }
            contents.add(JSONObject().put("role", "user").put("parts", responseParts))
        }
        return "কাজটা অনেক ধাপের হয়ে যাচ্ছে, তাই এখানেই থামলাম।"
    }

    private fun textContent(role: String, text: String): JSONObject =
        JSONObject().put("role", role)
            .put("parts", JSONArray().put(JSONObject().put("text", text)))

    private fun friendlyError(code: String): String = when {
        code == "NO_KEY" -> "Gemini API key দেওয়া হয়নি। সেটিংসে গিয়ে key বসান।"
        code == "NO_MODEL" -> "সেটিংসে কোনো মডেলের নাম নেই।"
        code.startsWith("HTTP 400") || code.startsWith("HTTP 403") ->
            "API key ঠিক নেই মনে হচ্ছে। সেটিংসে আবার দেখুন। ($code)"
        code.startsWith("HTTP 429") -> "ফ্রি লিমিট শেষ হয়ে গেছে, একটু পরে আবার বলুন।"
        else -> "দুঃখিত, এখন উত্তর আনতে পারলাম না। ($code)"
    }

    private fun systemPrompt(callerMode: Boolean): String {
        val now = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val mem = prefs.memoryMap()
        val memText = if (mem.isEmpty()) "(কিছু নেই)" else mem.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
        val owner = prefs.userName.ifBlank { "ব্যবহারকারী" }

        if (callerMode) {
            return """
তুমি "বুশরা", $owner-এর ব্যক্তিগত সহকারী। এখন একজন কলার $owner-কে ফোন করেছে এবং তুমি তার সাথে ফোনে কথা বলছ।
- ভদ্র, ছোট ও স্পষ্ট বাংলায় কথা বলো (১-২ বাক্য)। মার্কডাউন বা ইমোজি নয়।
- জানাও যে $owner এখন সরাসরি কথা বলতে পারছেন না; কলারের নাম ও কারণ জিজ্ঞেস করো এবং বার্তা পৌঁছে দেওয়ার কথা বলো।
- ফোনের কোনো কাজ করার অনুরোধ এলে বিনয়ের সাথে না বলো। ব্যক্তিগত বা আর্থিক তথ্য কাউকে দেবে না।
বর্তমান সময়: $now
""".trimIndent()
        }

        return """
তুমি "বুশরা" — $owner-এর ব্যক্তিগত এআই সহকারী ও কাছের বন্ধু। তুমি একজন তরুণী, উষ্ণ ও আন্তরিক, বন্ধুর মতো সহজ ভঙ্গিতে কথা বলো।

নিয়ম:
- সবসময় বাংলায় উত্তর দাও (ব্যবহারকারী অন্য ভাষায় বললে সেই ভাষায়)।
- উত্তর ছোট ও কথ্য রাখো (১-৩ বাক্য), কারণ তোমার কথা জোরে পড়ে শোনানো হয়। কোনো মার্কডাউন, তারকা, তালিকা বা ইমোজি ব্যবহার করবে না।
- কাজের অনুরোধ (কল, SMS, অ্যাপ খোলা, মেসেজ, সেটিংস ইত্যাদি) হলে সরাসরি টুল ব্যবহার করে করে ফেলো; আগে অনুমতি চেয়ো না।
- ব্যতিক্রম: ব্যাংক, পেমেন্ট, টাকা পাঠানো, পাসওয়ার্ড বা OTP-র কাজের আগে মুখে একবার নিশ্চিত হয়ে নাও।
- অ্যাপের ভেতরের কাজে (যেমন ফেসবুক স্টোরি, WhatsApp-এ কিছু করা): আগে open_app, তারপর read_screen দিয়ে দেখো, তারপর tap_text/type_text/swipe দিয়ে এগোও। প্রতিটা ধাপের পর read_screen দিয়ে যাচাই করো।
- টুলের ফলাফলে success=false এলে সৎভাবে জানাও কী হয়নি। কাজ না হলে হয়েছে বলে মিথ্যা বলবে না।
- ব্যবহারকারী কিছু মনে রাখতে বললে remember টুল ব্যবহার করো।
- ব্যবহারকারীকে মন খারাপ মনে হলে আগে কোমল কথায় পাশে থাকো।

বর্তমান সময়: $now

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
                BushraState.ttsStatus.value = "টেক্সট-টু-স্পিচ চালু করা যায়নি"
            }
        }
    }

    fun applyVoice() {
        val t = tts ?: return
        val locale = Locale.forLanguageTag(prefs.language)
        var r = t.setLanguage(locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            BushraState.ttsStatus.value =
                "ভয়েস ডেটা নেই: ফোনের সেটিংস > টেক্সট-টু-স্পিচ থেকে বাংলা ভয়েস নামান"
            r = t.setLanguage(Locale.getDefault())
        } else {
            BushraState.ttsStatus.value = "ভয়েস ঠিক আছে"
        }
        t.setSpeechRate(1.0f)
        t.setPitch(if (prefs.voiceFemale) 1.15f else 0.85f)
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
            if (BushraState.phase.value == Phase.SPEAKING) BushraState.phase.value = Phase.IDLE
            onSpeakFinished?.invoke()
        }
    }

    fun speak(raw: String) {
        val clean = raw
            .replace(Regex("[*#_`~]"), "")
            .replace(Regex("[\\p{So}\\p{Cs}]"), "")
            .trim()
        val t = tts
        if (clean.isEmpty() || t == null || !ttsReady) {
            BushraState.phase.value = Phase.IDLE
            onSpeakFinished?.invoke()
            return
        }
        t.stop()
        val g = gen.incrementAndGet()
        val chunks = clean.chunked(3000)
        pending.set(chunks.size)
        for ((i, c) in chunks.withIndex()) {
            t.speak(c, TextToSpeech.QUEUE_ADD, Bundle(), "$g-$i")
        }
    }

    fun stopSpeaking() {
        gen.incrementAndGet()
        pending.set(0)
        tts?.stop()
        if (BushraState.phase.value == Phase.SPEAKING) BushraState.phase.value = Phase.IDLE
    }
}
