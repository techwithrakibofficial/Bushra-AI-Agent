package com.rakib.bushra.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("bushra_prefs", Context.MODE_PRIVATE)

    init {
        // পুরোনো (বন্ধ হয়ে যাওয়া) মডেলের তালিকা থাকলে নতুন ফ্রি মডেলে সরিয়ে দেয়
        if (sp.getInt("models_version", 0) < MODELS_VERSION) {
            sp.edit()
                .putString("models", DEFAULT_MODELS)
                .putInt("models_version", MODELS_VERSION)
                .apply()
        }
    }

    // ---------------------------------------------------------------- এআই

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    /** কমা দিয়ে আলাদা মডেলের তালিকা; একটা না চললে পরেরটা চেষ্টা হয় */
    var models: String
        get() = sp.getString("models", DEFAULT_MODELS) ?: DEFAULT_MODELS
        set(v) = sp.edit().putString("models", v).apply()

    /** অ্যাপের ভাষা: "en" (ডিফল্ট) বা "bn" */
    var uiLang: String
        get() = if (sp.getString("ui_lang", "en") == "bn") "bn" else "en"
        set(v) = sp.edit().putString("ui_lang", if (v == "bn") "bn" else "en").apply()

    var userName: String
        get() = sp.getString("user_name", "রাকিব") ?: "রাকিব"
        set(v) = sp.edit().putString("user_name", v.trim()).apply()

    /** true = রোমান্টিক সঙ্গী মোড, false = শুধু বন্ধু মোড */
    var romantic: Boolean
        get() = sp.getBoolean("romantic", true)
        set(v) = sp.edit().putBoolean("romantic", v).apply()

    // ---------------------------------------------------------------- শোনা

    var language: String
        get() = sp.getString("language", "bn-BD") ?: "bn-BD"
        set(v) = sp.edit().putString("language", v.trim()).apply()

    /** না বুঝলে বাংলা ও ইংরেজির মধ্যে নিজে বদলে শোনার চেষ্টা (Banglish-এর জন্য) */
    var autoLang: Boolean
        get() = sp.getBoolean("auto_lang", true)
        set(v) = sp.edit().putBoolean("auto_lang", v).apply()

    var wakeWords: String
        get() = sp.getString("wake_words", DEFAULT_WAKE) ?: DEFAULT_WAKE
        set(v) = sp.edit().putString("wake_words", v).apply()

    /** true হলে "বুশরা" না বললেও যা শোনে সবই কমান্ড */
    var alwaysCommand: Boolean
        get() = sp.getBoolean("always_command", false)
        set(v) = sp.edit().putBoolean("always_command", v).apply()

    /** গান চলার সময় ক্রমাগত শোনা বন্ধ রাখে (গান থেমে যাওয়া ঠেকায়) */
    var musicMode: Boolean
        get() = sp.getBoolean("music_mode", true)
        set(v) = sp.edit().putBoolean("music_mode", v).apply()

    /** স্ক্রিনে ভাসমান মাইক বাটন */
    var bubble: Boolean
        get() = sp.getBoolean("bubble", true)
        set(v) = sp.edit().putBoolean("bubble", v).apply()

    // ---------------------------------------------------------------- কথা বলা

    /** "edge" (পরিষ্কার নিউরাল বাংলা, ফ্রি) বা "system" (ফোনের নিজের TTS) */
    var ttsEngine: String
        get() = sp.getString("tts_engine", "edge") ?: "edge"
        set(v) = sp.edit().putString("tts_engine", v).apply()

    var voiceFemale: Boolean
        get() = sp.getBoolean("voice_female", true)
        set(v) = sp.edit().putBoolean("voice_female", v).apply()

    var edgeVoice: String
        get() = sp.getString("edge_voice", "bn-BD-NabanitaNeural") ?: "bn-BD-NabanitaNeural"
        set(v) = sp.edit().putString("edge_voice", v).apply()

    /** ইংরেজি উত্তর পড়ার Edge কণ্ঠ */
    var edgeVoiceEn: String
        get() = sp.getString("edge_voice_en", "en-US-JennyNeural") ?: "en-US-JennyNeural"
        set(v) = sp.edit().putString("edge_voice_en", v).apply()

    var voiceName: String
        get() = sp.getString("voice_name", "") ?: ""
        set(v) = sp.edit().putString("voice_name", v).apply()

    // ---------------------------------------------------------------- অ্যাকশন ও কল

    var killSwitch: Boolean
        get() = sp.getBoolean("kill_switch", false)
        set(v) = sp.edit().putBoolean("kill_switch", v).apply()

    /** "ask" = ধরব নাকি মেসেজ জিজ্ঞেস করবে, "auto" = বুশরা নিজে ধরে কথা বলবে, "off" = কিছু করবে না */
    var callMode: String
        get() = sp.getString("call_mode", if (sp.getBoolean("incoming_call", true)) "ask" else "off") ?: "ask"
        set(v) = sp.edit().putString("call_mode", v).apply()

    var busySms: String
        get() = sp.getString("busy_sms", DEFAULT_BUSY_SMS) ?: DEFAULT_BUSY_SMS
        set(v) = sp.edit().putString("busy_sms", v).apply()

    /** কলারকে জানানোর মতো তথ্য (বুশরা কলে এখান থেকে ও স্মৃতি থেকে বলবে) */
    var callerInfo: String
        get() = sp.getString("caller_info", "") ?: ""
        set(v) = sp.edit().putString("caller_info", v).apply()

    // ---------------------------------------------------------------- স্মৃতি

    fun memoryMap(): Map<String, String> = readMap("memory")

    fun putMemory(key: String, value: String) = putInMap("memory", key, value)

    fun removeMemory(key: String) = removeFromMap("memory", key)

    /** ডাকনাম -> আসল কন্টাক্টের নাম বা নম্বর */
    fun aliasMap(): Map<String, String> = readMap("aliases")

    fun putAlias(nick: String, target: String) = putInMap("aliases", nick, target)

    fun removeAlias(nick: String) = removeFromMap("aliases", nick)

    fun callLog(): List<String> {
        val raw = sp.getString("call_log", "[]") ?: "[]"
        val out = ArrayList<String>()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) out.add(a.optString(i))
        } catch (_: Exception) {
        }
        return out
    }

    fun addCallLog(line: String) {
        val cur = callLog().toMutableList()
        cur.add(0, line)
        val a = JSONArray()
        for (s in cur.take(20)) a.put(s)
        sp.edit().putString("call_log", a.toString()).apply()
    }

    // ---------------------------------------------------------------- ভেতরের সাহায্যকারী

    private fun readMap(name: String): Map<String, String> {
        val raw = sp.getString(name, "{}") ?: "{}"
        val out = LinkedHashMap<String, String>()
        try {
            val o = JSONObject(raw)
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                out[k] = o.optString(k)
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun putInMap(name: String, key: String, value: String) {
        if (key.isBlank()) return
        val m = readMap(name).toMutableMap()
        m[key.trim()] = value.trim()
        writeMap(name, m)
    }

    private fun removeFromMap(name: String, key: String) {
        val m = readMap(name).toMutableMap()
        m.remove(key)
        writeMap(name, m)
    }

    private fun writeMap(name: String, m: Map<String, String>) {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, v)
        sp.edit().putString(name, o.toString()).apply()
    }

    companion object {
        private const val MODELS_VERSION = 2
        const val DEFAULT_MODELS =
            "gemini-3.8-flash,gemini-3.6-flash,gemini-3.5-flash-lite,gemini-3.1-flash-lite,gemini-2.5-flash"
        const val DEFAULT_WAKE = "বুশরা,বুসরা,বুশরাহ,বুশ্রা,বুছরা,বুশরার,bushra,busra,bushrah,booshra"
        const val DEFAULT_BUSY_SMS = "আমি এখন ব্যস্ত আছি, একটু পরে কল করছি।"
    }
}
