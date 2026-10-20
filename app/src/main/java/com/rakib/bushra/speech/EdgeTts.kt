package com.rakib.bushra.speech

import com.rakib.bushra.core.BushraLog
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Microsoft Edge-এর ফ্রি নিউরাল TTS (bn-BD / bn-IN বাংলা ভয়েস)। কোনো API key লাগে না।
 * এটা অফিসিয়াল API নয়, তাই বন্ধ হয়ে গেলে অ্যাপ নিজে ফোনের TTS-এ ফিরে যায়।
 */
object EdgeTts {

    private const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val VERSION = "143.0.3650.75"
    private const val BASE =
        "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"

    /** (ছোট নাম, দেখানোর নাম, মেয়েলি কি না) */
    val voices: List<Triple<String, String, Boolean>> = listOf(
        Triple("bn-BD-NabanitaNeural", "নবনীতা (বাংলাদেশ, মেয়ে)", true),
        Triple("bn-IN-TanishaaNeural", "তানিশা (ভারত, মেয়ে)", true),
        Triple("bn-BD-PradeepNeural", "প্রদীপ (বাংলাদেশ, ছেলে)", false),
        Triple("bn-IN-BashkarNeural", "ভাস্কর (ভারত, ছেলে)", false),
        Triple("en-US-JennyNeural", "Jenny (US, female)", true),
        Triple("en-US-AriaNeural", "Aria (US, female)", true),
        Triple("en-IN-NeerjaNeural", "Neerja (India, female)", true),
        Triple("en-GB-SoniaNeural", "Sonia (UK, female)", true)
    )

    /** বাংলা কণ্ঠ */
    val bnVoices: List<Triple<String, String, Boolean>> get() = voices.filter { it.first.startsWith("bn-") }

    /** ইংরেজি কণ্ঠ */
    val enVoices: List<Triple<String, String, Boolean>> get() = voices.filter { it.first.startsWith("en-") }

    fun displayName(short: String): String =
        voices.firstOrNull { it.first == short }?.second ?: short

    @Volatile
    private var skewSeconds = 0L

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private class Attempt(val audio: ByteArray?, val status: Int, val dateHeader: String?, val error: String?)

    /** টেক্সট থেকে MP3 অডিও বানায়; ব্যর্থ হলে null */
    suspend fun synthesize(
        text: String,
        shortVoice: String,
        rate: String,
        pitch: String,
        volume: String
    ): ByteArray? {
        var a = attempt(text, shortVoice, rate, pitch, volume)
        val dh = a.dateHeader
        if (a.audio == null && a.status == 403 && dh != null) {
            // ফোনের ঘড়ি সার্ভারের সাথে না মিললে টোকেন ভুল হয়: সার্ভারের সময় দেখে ঠিক করে আবার চেষ্টা
            val server = parseDate(dh)
            if (server != null) {
                skewSeconds = server / 1000 - System.currentTimeMillis() / 1000
                a = attempt(text, shortVoice, rate, pitch, volume)
            }
        }
        if (a.audio == null) BushraLog.add("Edge TTS", "ব্যর্থ: ${a.error ?: "HTTP ${a.status}"}".take(140))
        return a.audio
    }

    private suspend fun attempt(
        text: String,
        shortVoice: String,
        rate: String,
        pitch: String,
        volume: String
    ): Attempt = suspendCancellableCoroutine { cont ->
        val done = AtomicBoolean(false)
        fun finish(r: Attempt) {
            if (done.compareAndSet(false, true) && cont.isActive) cont.resume(r)
        }

        val connId = UUID.randomUUID().toString().replace("-", "")
        val url = "$BASE?TrustedClientToken=$TOKEN&ConnectionId=$connId" +
            "&Sec-MS-GEC=${gec()}&Sec-MS-GEC-Version=1-$VERSION"
        val muid = UUID.randomUUID().toString().replace("-", "").uppercase(Locale.US)
        val req = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("User-Agent", UA)
            .header("Cookie", "muid=$muid;")
            .build()

        val audio = ByteArrayOutputStream()

        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val ts = jsDate()
                webSocket.send(
                    "X-Timestamp:$ts\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
                        "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
                        "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"
                )
                val reqId = UUID.randomUUID().toString().replace("-", "")
                webSocket.send(
                    "X-RequestId:$reqId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${ts}Z\r\n" +
                        "Path:ssml\r\n\r\n" +
                        ssml(text, shortVoice, rate, pitch, volume)
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    webSocket.close(1000, null)
                    val bytes = audio.toByteArray()
                    finish(Attempt(if (bytes.isNotEmpty()) bytes else null, 200, null, if (bytes.isEmpty()) "খালি অডিও" else null))
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val b = bytes.toByteArray()
                if (b.size < 2) return
                val headerLen = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
                if (2 + headerLen > b.size) return
                val headers = String(b, 2, headerLen, Charsets.UTF_8)
                if (!headers.contains("Path:audio")) return
                val start = 2 + headerLen
                if (b.size > start) audio.write(b, start, b.size - start)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(Attempt(null, response?.code ?: 0, response?.header("Date"), t.message))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val bytes = audio.toByteArray()
                finish(Attempt(if (bytes.isNotEmpty()) bytes else null, 0, null, "বন্ধ হয়ে গেছে ($code)"))
            }
        })
        cont.invokeOnCancellation { ws.cancel() }
    }

    private fun ssml(text: String, shortVoice: String, rate: String, pitch: String, volume: String): String {
        val parts = shortVoice.split("-")
        val longName = if (parts.size >= 3) {
            "Microsoft Server Speech Text to Speech Voice (${parts[0]}-${parts[1]}, ${parts.drop(2).joinToString("-")})"
        } else shortVoice
        val safe = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
            .filter { it == '\n' || it == '\t' || it.code >= 32 }
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
            "<voice name='$longName'><prosody pitch='$pitch' rate='$rate' volume='$volume'>$safe</prosody></voice></speak>"
    }

    private fun gec(): String {
        var secs = System.currentTimeMillis() / 1000 + skewSeconds + 11644473600L
        secs -= secs % 300
        val ticks = secs * 10_000_000L
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest("$ticks$TOKEN".toByteArray(Charsets.US_ASCII))
        val sb = StringBuilder()
        for (b in hash) sb.append(String.format("%02X", b))
        return sb.toString()
    }

    private fun jsDate(): String {
        val f = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(java.util.Date()) + " GMT+0000 (Coordinated Universal Time)"
    }

    private fun parseDate(h: String): Long? = try {
        val f = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        f.parse(h)?.time
    } catch (e: Exception) {
        null
    }
}
