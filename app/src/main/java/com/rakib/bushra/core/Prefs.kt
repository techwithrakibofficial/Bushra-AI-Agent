package com.rakib.bushra.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("bushra_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    /** কমা দিয়ে আলাদা মডেলের তালিকা; একটা ব্যর্থ হলে পরেরটা চেষ্টা হয় */
    var models: String
        get() = sp.getString("models", DEFAULT_MODELS) ?: DEFAULT_MODELS
        set(v) = sp.edit().putString("models", v).apply()

    var language: String
        get() = sp.getString("language", "bn-BD") ?: "bn-BD"
        set(v) = sp.edit().putString("language", v.trim()).apply()

    var wakeWords: String
        get() = sp.getString("wake_words", DEFAULT_WAKE) ?: DEFAULT_WAKE
        set(v) = sp.edit().putString("wake_words", v).apply()

    var voiceFemale: Boolean
        get() = sp.getBoolean("voice_female", true)
        set(v) = sp.edit().putBoolean("voice_female", v).apply()

    var voiceName: String
        get() = sp.getString("voice_name", "") ?: ""
        set(v) = sp.edit().putString("voice_name", v).apply()

    var killSwitch: Boolean
        get() = sp.getBoolean("kill_switch", false)
        set(v) = sp.edit().putBoolean("kill_switch", v).apply()

    var incomingCallEnabled: Boolean
        get() = sp.getBoolean("incoming_call", true)
        set(v) = sp.edit().putBoolean("incoming_call", v).apply()

    var aiCallTalk: Boolean
        get() = sp.getBoolean("ai_call_talk", false)
        set(v) = sp.edit().putBoolean("ai_call_talk", v).apply()

    var busySms: String
        get() = sp.getString("busy_sms", DEFAULT_BUSY_SMS) ?: DEFAULT_BUSY_SMS
        set(v) = sp.edit().putString("busy_sms", v).apply()

    var userName: String
        get() = sp.getString("user_name", "রাকিব") ?: "রাকিব"
        set(v) = sp.edit().putString("user_name", v.trim()).apply()

    fun memoryMap(): Map<String, String> {
        val raw = sp.getString("memory", "{}") ?: "{}"
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

    fun putMemory(key: String, value: String) {
        val m = memoryMap().toMutableMap()
        m[key.trim()] = value.trim()
        saveMemory(m)
    }

    fun removeMemory(key: String) {
        val m = memoryMap().toMutableMap()
        m.remove(key)
        saveMemory(m)
    }

    private fun saveMemory(m: Map<String, String>) {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, v)
        sp.edit().putString("memory", o.toString()).apply()
    }

    companion object {
        const val DEFAULT_MODELS = "gemini-2.5-flash,gemini-2.0-flash,gemini-2.5-flash-lite"
        const val DEFAULT_WAKE = "বুশরা,বুসরা,বুশরাহ,বুশ্রা,বুছরা,bushra,busra,bushrah,booshra"
        const val DEFAULT_BUSY_SMS = "আমি এখন ব্যস্ত আছি, একটু পরে কল করছি।"
    }
}
