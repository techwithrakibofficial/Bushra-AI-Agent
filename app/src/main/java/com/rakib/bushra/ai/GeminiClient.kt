package com.rakib.bushra.ai

import com.rakib.bushra.core.BushraLog
import com.rakib.bushra.core.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeminiResult(
    val parts: JSONArray?,
    val error: String?,
    val retryNext: Boolean = false,
    val model: String = ""
)

/** Gemini REST (generateContent) ক্লায়েন্ট: ফাংশন কলিং + একাধিক ফ্রি মডেলের ফলব্যাক */
class GeminiClient(private val prefs: Prefs) {

    fun generate(system: String, contents: List<JSONObject>, tools: JSONArray?): GeminiResult {
        val key = prefs.apiKey
        if (key.isBlank()) return GeminiResult(null, "NO_KEY")
        val models = prefs.models.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (models.isEmpty()) return GeminiResult(null, "NO_MODEL")

        var last = GeminiResult(null, "UNKNOWN")
        for (m in models) {
            val r = try {
                call(m, key, system, contents, tools)
            } catch (e: Exception) {
                GeminiResult(null, "নেটওয়ার্ক সমস্যা: ${e.message}", true, m)
            }
            if (r.error == null) return r
            BushraLog.add("এআই", "$m: ${r.error}".take(160))
            last = r
            if (!r.retryNext) return r
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

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                val msg = try {
                    JSONObject(text).getJSONObject("error").optString("message")
                } catch (e: Exception) {
                    text.take(200)
                }
                // key-ই ভুল হলে অন্য মডেলে চেষ্টা করে লাভ নেই; বাকি সব ক্ষেত্রে পরের মডেল চেষ্টা হবে
                val keyBad = code == 401 ||
                    (code == 400 && msg.contains("API key", ignoreCase = true))
                return GeminiResult(null, "HTTP $code: $msg", !keyBad, model)
            }

            val json = JSONObject(text)
            val cands = json.optJSONArray("candidates")
            if (cands == null || cands.length() == 0) {
                val block = json.optJSONObject("promptFeedback")?.optString("blockReason") ?: ""
                return GeminiResult(null, "উত্তর আসেনি $block".trim(), true, model)
            }
            val content = cands.getJSONObject(0).optJSONObject("content")
            val parts = content?.optJSONArray("parts") ?: JSONArray()
            return GeminiResult(parts, null, false, model)
        } finally {
            conn.disconnect()
        }
    }
}
