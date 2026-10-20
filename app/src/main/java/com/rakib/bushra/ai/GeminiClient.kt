package com.rakib.bushra.ai

import com.rakib.bushra.core.BushraLog
import com.rakib.bushra.core.Prefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GeminiResult(
    val parts: JSONArray?,
    val error: String?,
    val retryNext: Boolean = false,
    val model: String = ""
)

/**
 * Gemini REST (generateContent) ক্লায়েন্ট: ফাংশন কলিং + একাধিক ফ্রি মডেলের ফলব্যাক।
 *
 * দেরি কমানোর জন্য:
 *  - একটাই OkHttp ক্লায়েন্ট: HTTP/2 সংযোগ খোলা থাকে, প্রতিবার নতুন TLS হ্যান্ডশেক লাগে না
 *  - যে মডেল সম্প্রতি ব্যর্থ হয়েছে সেটা কিছুক্ষণ পিছিয়ে দেওয়া হয়, যাতে প্রতিবার ব্যর্থ মডেলে সময় নষ্ট না হয়
 */
class GeminiClient(private val prefs: Prefs) {

    companion object {
        private const val HOST = "https://generativelanguage.googleapis.com"
        private const val COOLDOWN_MS = 2 * 60 * 1000L
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(50, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** মডেল -> কখন পর্যন্ত পিছিয়ে রাখা হবে */
    private val coolUntil = ConcurrentHashMap<String, Long>()

    /** সংযোগ আগেই গরম করে রাখে, যাতে প্রথম প্রশ্নের উত্তর দ্রুত আসে (ব্যর্থ হলেও সমস্যা নেই) */
    fun warmUp() {
        try {
            val req = Request.Builder().url("$HOST/v1beta/models").head().build()
            http.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    private fun orderedModels(list: List<String>): List<String> {
        val now = System.currentTimeMillis()
        val (ready, cooling) = list.partition { (coolUntil[it] ?: 0L) <= now }
        return ready + cooling
    }

    fun generate(system: String, contents: List<JSONObject>, tools: JSONArray?): GeminiResult {
        val key = prefs.apiKey
        if (key.isBlank()) return GeminiResult(null, "NO_KEY")
        val models = prefs.models.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (models.isEmpty()) return GeminiResult(null, "NO_MODEL")

        var last = GeminiResult(null, "UNKNOWN")
        for (m in orderedModels(models)) {
            val r = try {
                call(m, key, system, contents, tools)
            } catch (e: Exception) {
                GeminiResult(null, "নেটওয়ার্ক সমস্যা: ${e.message}", true, m)
            }
            if (r.error == null) {
                coolUntil.remove(m)
                return r
            }
            BushraLog.add("এআই", "$m: ${r.error}".take(160))
            last = r
            if (!r.retryNext) return r
            coolUntil[m] = System.currentTimeMillis() + COOLDOWN_MS
        }
        return last
    }

    private fun call(
        model: String,
        key: String,
        system: String,
        contents: List<JSONObject>,
        tools: JSONArray?
    ): GeminiResult {
        val body = JSONObject()
        body.put(
            "systemInstruction",
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
        )
        val arr = JSONArray()
        for (c in contents) arr.put(c)
        body.put("contents", arr)
        if (tools != null && tools.length() > 0) {
            body.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools)))
            body.put(
                "toolConfig",
                JSONObject().put("functionCallingConfig", JSONObject().put("mode", "AUTO"))
            )
        }
        body.put("generationConfig", JSONObject().put("temperature", 0.9))

        val req = Request.Builder()
            .url("$HOST/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody(JSON))
            .build()

        return http.newCall(req).execute().use { resp ->
            val code = resp.code
            val text = resp.body?.string() ?: ""

            if (code !in 200..299) {
                val msg = try {
                    JSONObject(text).getJSONObject("error").optString("message")
                } catch (e: Exception) {
                    text.take(200)
                }
                // key-ই ভুল হলে অন্য মডেলে চেষ্টা করে লাভ নেই; বাকি সব ক্ষেত্রে পরের মডেল চেষ্টা হবে
                val keyBad = code == 401 ||
                    (code == 400 && msg.contains("API key", ignoreCase = true))
                return@use GeminiResult(null, "HTTP $code: $msg", !keyBad, model)
            }

            val json = JSONObject(text)
            val cands = json.optJSONArray("candidates")
            if (cands == null || cands.length() == 0) {
                val block = json.optJSONObject("promptFeedback")?.optString("blockReason") ?: ""
                return@use GeminiResult(null, "উত্তর আসেনি $block".trim(), true, model)
            }
            val content = cands.getJSONObject(0).optJSONObject("content")
            val parts = content?.optJSONArray("parts") ?: JSONArray()
            return@use GeminiResult(parts, null, false, model)
        }
    }
}
