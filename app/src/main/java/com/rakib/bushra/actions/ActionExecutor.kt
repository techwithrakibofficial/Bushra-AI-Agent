package com.rakib.bushra.actions

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.SmsManager
import com.rakib.bushra.core.Prefs
import com.rakib.bushra.service.BushraAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Gemini-র ডাকা প্রতিটি টুলের আসল কাজ এখানে হয় */
class ActionExecutor(private val ctx: Context, private val prefs: Prefs) {

    private data class Contact(val name: String, val number: String)

    private val alwaysAllowed = setOf("remember", "forget", "get_device_info", "wait")

    private fun res(ok: Boolean, msg: String): JSONObject =
        JSONObject().put("success", ok).put("message", msg)

    suspend fun execute(name: String, args: JSONObject): JSONObject =
        withContext(Dispatchers.Default) {
            if (prefs.killSwitch && name !in alwaysAllowed) {
                return@withContext res(false, "কিল সুইচ চালু আছে, তাই কোনো অ্যাকশন করা যাচ্ছে না।")
            }
            try {
                dispatch(name, args)
            } catch (e: SecurityException) {
                res(false, "অনুমতি নেই: ${e.message}")
            } catch (e: Exception) {
                res(false, "ত্রুটি: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

    private suspend fun dispatch(name: String, a: JSONObject): JSONObject {
        return when (name) {
            "open_app" -> openApp(a.optString("app_name"))
            "make_call" -> makeCall(a.optString("target"))
            "end_call" -> if (endCall()) res(true, "কল শেষ করা হয়েছে") else res(false, "কল শেষ করা যায়নি")
            "answer_call" -> if (answerCall()) res(true, "কল ধরা হয়েছে") else res(false, "কল ধরা যায়নি")
            "set_speaker" -> setSpeaker(a.optBoolean("on", true))
            "send_sms" -> sendSms(a.optString("target"), a.optString("message"))
            "read_sms" -> readSms(a.optInt("count", 5))
            "whatsapp_message" -> whatsappMessage(a.optString("target"), a.optString("message"))
            "whatsapp_call" -> whatsappCall(a.optString("target"), a.optBoolean("video", false))
            "get_contact" -> getContact(a.optString("name"))
            "send_email" -> sendEmail(a.optString("to"), a.optString("subject"), a.optString("body"))
            "open_url" -> openUrl(a.optString("url"))
            "web_search" -> openUrl("https://www.google.com/search?q=" + Uri.encode(a.optString("query")))
            "navigate" -> navigate(a.optString("place"))
            "play_youtube" -> playYoutube(a.optString("query"))
            "take_photo" -> takePhoto()
            "set_alarm" -> setAlarm(a.optInt("hour", -1), a.optInt("minute", 0), a.optString("label"))
            "set_timer" -> setTimer(a.optInt("seconds", 0))
            "add_calendar_event" -> addEvent(a.optString("title"), a.optString("datetime"), a.optInt("duration_min", 60))
            "open_settings" -> openSettings(a.optString("page"))
            "set_flashlight" -> setFlashlight(a.optBoolean("on", true))
            "set_volume" -> setVolume(a.optInt("percent", 50), a.optString("stream", "media"))
            "set_brightness" -> setBrightness(a.optInt("percent", 50))
            "set_ringer_mode" -> setRinger(a.optString("mode"))
            "get_device_info" -> deviceInfo()
            "copy_to_clipboard" -> copyClip(a.optString("text"))
            "get_clipboard" -> getClip()
            "read_screen" -> withSvc { res(true, it.readScreen()) }
            "tap_text" -> withSvc {
                val t = a.optString("text")
                if (it.clickByText(t)) res(true, "'$t' চাপা হয়েছে") else res(false, "'$t' স্ক্রিনে পাইনি বা চাপা যায়নি")
            }
            "tap_xy" -> withSvc {
                if (it.tap(a.optInt("x").toFloat(), a.optInt("y").toFloat())) res(true, "ট্যাপ করা হয়েছে")
                else res(false, "ট্যাপ করা যায়নি")
            }
            "swipe" -> withSvc {
                if (it.swipe(a.optString("direction"))) res(true, "স্ক্রল/সোয়াইপ হয়েছে") else res(false, "সোয়াইপ হয়নি")
            }
            "type_text" -> withSvc {
                if (it.typeText(a.optString("text"))) res(true, "লেখা হয়েছে") else res(false, "লেখার ঘর পাইনি")
            }
            "press_key" -> withSvc {
                if (it.global(a.optString("key"))) res(true, "হয়েছে") else res(false, "এই বাটন চাপা যায়নি")
            }
            "wait" -> {
                val s = a.optInt("seconds", 2).coerceIn(1, 10)
                delay(s * 1000L)
                res(true, "$s সেকেন্ড অপেক্ষা করেছি")
            }
            "remember" -> {
                prefs.putMemory(a.optString("key"), a.optString("value"))
                res(true, "মনে রাখলাম")
            }
            "forget" -> {
                prefs.removeMemory(a.optString("key"))
                res(true, "ভুলে গেলাম")
            }
            else -> res(false, "এই নামে কোনো অ্যাকশন নেই: $name")
        }
    }

    private fun withSvc(block: (BushraAccessibilityService) -> JSONObject): JSONObject {
        val svc = BushraAccessibilityService.instance
            ?: return res(false, "Accessibility সেবা চালু নেই। পারমিশন পেজ থেকে চালু করতে হবে।")
        return block(svc)
    }

    // ------------------------------------------------------------------ সাহায্যকারী

    private fun launch(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val c: Context = BushraAccessibilityService.instance ?: ctx
        return try {
            c.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun isInstalled(pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun digits(s: String): String = s.filter { it.isDigit() }

    private fun looksLikeNumber(s: String): Boolean = Regex("^[+0-9()\\-\\s]{4,}$").matches(s.trim())

    /** বাংলাদেশি নম্বরকে WhatsApp-এর আন্তর্জাতিক রূপে আনে */
    private fun waNumber(raw: String): String {
        val d = digits(raw)
        return when {
            raw.trim().startsWith("+") -> d
            d.startsWith("880") -> d
            d.startsWith("0") -> "88$d"
            else -> d
        }
    }

    private fun similar(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val limit = if (maxOf(a.length, b.length) > 5) 2 else 1
        if (kotlin.math.abs(a.length - b.length) > limit) return false
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length] <= limit
    }

    private fun findContacts(query: String): List<Contact> {
        val out = ArrayList<Contact>()
        val cr = ctx.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val nameCol = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        val numCol = ContactsContract.CommonDataKinds.Phone.NUMBER
        val proj = arrayOf(nameCol, numCol)

        cr.query(uri, proj, "$nameCol LIKE ?", arrayOf("%$query%"), null)?.use { c ->
            while (c.moveToNext()) {
                out.add(Contact(c.getString(0) ?: "", c.getString(1) ?: ""))
            }
        }
        if (out.isEmpty()) {
            val q = query.lowercase().trim()
            cr.query(uri, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(0) ?: continue
                    val nl = n.lowercase()
                    if (q.contains(nl) || nl.contains(q) || similar(nl, q)) {
                        out.add(Contact(n, c.getString(1) ?: ""))
                    }
                }
            }
        }
        return out.distinctBy { digits(it.number) }
    }

    private fun resolveNumber(target: String): Contact? {
        val t = target.trim()
        if (t.isEmpty()) return null
        if (looksLikeNumber(t)) return Contact(t, t)
        return findContacts(t).firstOrNull()
    }

    fun nameForNumber(number: String?): String? {
        if (number.isNullOrBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
            )
            ctx.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ অ্যাপ ও কল

    private val aliases = mapOf(
        "whatsapp" to "com.whatsapp", "হোয়াটসঅ্যাপ" to "com.whatsapp", "হোয়াটসঅ্যাপ" to "com.whatsapp",
        "facebook" to "com.facebook.katana", "ফেসবুক" to "com.facebook.katana",
        "messenger" to "com.facebook.orca", "মেসেঞ্জার" to "com.facebook.orca",
        "youtube" to "com.google.android.youtube", "ইউটিউব" to "com.google.android.youtube",
        "chrome" to "com.android.chrome", "ক্রোম" to "com.android.chrome",
        "gmail" to "com.google.android.gm", "জিমেইল" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps", "ম্যাপ" to "com.google.android.apps.maps",
        "telegram" to "org.telegram.messenger", "টেলিগ্রাম" to "org.telegram.messenger",
        "instagram" to "com.instagram.android", "ইনস্টাগ্রাম" to "com.instagram.android",
        "imo" to "com.imo.android.imoim", "ইমো" to "com.imo.android.imoim"
    )

    private fun openApp(query: String): JSONObject {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return res(false, "অ্যাপের নাম বলা হয়নি")
        val pm = ctx.packageManager

        val aliasPkg = aliases.entries.firstOrNull { q.contains(it.key) }?.value
        if (aliasPkg != null && isInstalled(aliasPkg)) {
            val li = pm.getLaunchIntentForPackage(aliasPkg)
            if (li != null && launch(li)) return res(true, "$query খোলা হয়েছে")
        }

        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val exact = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
        val part = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
        val pk = apps.firstOrNull { it.activityInfo.packageName.lowercase().contains(q) }
        val pick = exact ?: part ?: pk ?: return res(false, "'$query' নামে কোনো অ্যাপ পাইনি")
        val li = pm.getLaunchIntentForPackage(pick.activityInfo.packageName)
            ?: return res(false, "অ্যাপটা খোলা যায়নি")
        return if (launch(li)) res(true, "${pick.loadLabel(pm)} খোলা হয়েছে") else res(false, "অ্যাপ খোলা যায়নি")
    }

    private fun makeCall(target: String): JSONObject {
        val c = resolveNumber(target) ?: return res(false, "'$target' নামে কন্টাক্ট পাইনি")
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        tm.placeCall(Uri.fromParts("tel", c.number, null), Bundle())
        return res(true, "${c.name}-কে কল করা হচ্ছে")
    }

    fun answerCall(): Boolean {
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.acceptRingingCall()
            true
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun endCall(): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.endCall()
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    fun setSpeakerRaw(on: Boolean) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.isSpeakerphoneOn = on
    }

    private fun setSpeaker(on: Boolean): JSONObject {
        setSpeakerRaw(on)
        return res(true, if (on) "স্পিকার চালু" else "স্পিকার বন্ধ")
    }

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

    fun sendSmsRaw(number: String, message: String) {
        val sms = smsManager()
        val parts = sms.divideMessage(message)
        sms.sendMultipartTextMessage(number, null, parts, null, null)
    }

    private fun sendSms(target: String, message: String): JSONObject {
        if (message.isBlank()) return res(false, "মেসেজের লেখা নেই")
        val c = resolveNumber(target) ?: return res(false, "'$target' নামে কন্টাক্ট পাইনি")
        sendSmsRaw(c.number, message)
        return res(true, "${c.name}-কে SMS পাঠানো হয়েছে")
    }

    private fun readSms(count: Int): JSONObject {
        val n = count.coerceIn(1, 15)
        val list = JSONArray()
        ctx.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            null, null, "date DESC LIMIT $n"
        )?.use { c ->
            while (c.moveToNext()) {
                val addr = c.getString(0) ?: ""
                val who = nameForNumber(addr) ?: addr
                val body = (c.getString(1) ?: "").take(300)
                list.put(JSONObject().put("from", who).put("text", body))
            }
        }
        return res(true, "${list.length()}টি মেসেজ পাওয়া গেছে").put("messages", list)
    }

    private fun getContact(name: String): JSONObject {
        val found = findContacts(name)
        if (found.isEmpty()) return res(false, "'$name' নামে কন্টাক্ট পাইনি")
        val arr = JSONArray()
        for (c in found.take(5)) arr.put(JSONObject().put("name", c.name).put("number", c.number))
        return res(true, "${found.size}টি কন্টাক্ট পাওয়া গেছে").put("contacts", arr)
    }

    private fun sendEmail(to: String, subject: String, body: String): JSONObject {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to)))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
        return if (launch(i)) res(true, "ইমেইল ড্রাফট খোলা হয়েছে, পাঠানোর বাটন চাপতে হবে")
        else res(false, "ইমেইল অ্যাপ পাইনি")
    }

    // ------------------------------------------------------------------ WhatsApp

    private fun waPackage(): String? = when {
        isInstalled("com.whatsapp") -> "com.whatsapp"
        isInstalled("com.whatsapp.w4b") -> "com.whatsapp.w4b"
        else -> null
    }

    private suspend fun whatsappMessage(target: String, message: String): JSONObject {
        if (target.isBlank() || message.isBlank()) return res(false, "প্রাপক বা মেসেজ বলা হয়নি")
        val pkg = waPackage() ?: return res(false, "WhatsApp ইনস্টল করা নেই")
        val c = resolveNumber(target) ?: return res(false, "'$target' নামে কন্টাক্ট পাইনি")
        val wa = waNumber(c.number)
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa?text=" + Uri.encode(message)))
            .setPackage(pkg)
        if (!launch(i)) return res(false, "WhatsApp খোলা যায়নি")
        if (BushraAccessibilityService.instance == null) {
            return res(false, "চ্যাট খুলে মেসেজ লিখে রেখেছি, কিন্তু Accessibility চালু না থাকায় পাঠাতে পারিনি")
        }
        delay(2500)
        for (k in 0 until 12) {
            val svc = BushraAccessibilityService.instance
            if (svc != null && svc.clickSend(pkg)) {
                return res(true, "${c.name}-কে WhatsApp-এ মেসেজ পাঠানো হয়েছে")
            }
            delay(600)
        }
        return res(false, "চ্যাট খুলেছে কিন্তু পাঠানোর বাটন খুঁজে পাইনি")
    }

    private suspend fun whatsappCall(target: String, video: Boolean): JSONObject {
        val pkg = waPackage() ?: return res(false, "WhatsApp ইনস্টল করা নেই")
        val mime = if (video) "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
        else "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"

        if (!looksLikeNumber(target)) {
            val dataId = try {
                ctx.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Data._ID),
                    "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.Data.DISPLAY_NAME} LIKE ?",
                    arrayOf(mime, "%$target%"),
                    null
                )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            } catch (e: Exception) {
                null
            }
            if (dataId != null) {
                val i = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse("content://com.android.contacts/data/$dataId"), mime)
                    .setPackage(pkg)
                if (launch(i)) return res(true, "$target-কে WhatsApp ${if (video) "ভিডিও" else "ভয়েস"} কল করা হচ্ছে")
            }
        }

        // বিকল্প: চ্যাট খুলে কল আইকন চাপা
        val c = resolveNumber(target) ?: return res(false, "'$target' নামে কন্টাক্ট পাইনি")
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${waNumber(c.number)}")).setPackage(pkg)
        if (!launch(i)) return res(false, "WhatsApp খোলা যায়নি")
        if (BushraAccessibilityService.instance == null) {
            return res(false, "চ্যাট খুলেছি, কিন্তু কল বাটন চাপতে Accessibility চালু লাগবে")
        }
        delay(2500)
        val descs = if (video) arrayOf("Video call", "ভিডিও কল") else arrayOf("Voice call", "ভয়েস কল")
        for (k in 0 until 6) {
            val svc = BushraAccessibilityService.instance
            if (svc != null && svc.clickByDescription(*descs)) return res(true, "${c.name}-কে WhatsApp কল করা হচ্ছে")
            delay(600)
        }
        return res(false, "কল বাটন খুঁজে পাইনি")
    }

    // ------------------------------------------------------------------ ওয়েব, ম্যাপ, মিডিয়া

    private fun openUrl(url: String): JSONObject {
        val u = if (url.startsWith("http")) url else "https://$url"
        return if (launch(Intent(Intent.ACTION_VIEW, Uri.parse(u)))) res(true, "লিংক খোলা হয়েছে")
        else res(false, "ব্রাউজার খোলা যায়নি")
    }

    private fun navigate(place: String): JSONObject {
        if (place.isBlank()) return res(false, "জায়গার নাম বলা হয়নি")
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place)))
            .setPackage("com.google.android.apps.maps")
        if (launch(nav)) return res(true, "$place-এর নেভিগেশন চালু হয়েছে")
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)))
        return if (launch(geo)) res(true, "$place ম্যাপে খোলা হয়েছে") else res(false, "ম্যাপ অ্যাপ পাইনি")
    }

    private fun playYoutube(query: String): JSONObject {
        val url = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        val yt = Intent(Intent.ACTION_VIEW, url).setPackage("com.google.android.youtube")
        if (launch(yt)) return res(true, "YouTube-এ '$query' খোঁজা হয়েছে; ভিডিও চালাতে হলে read_screen দিয়ে দেখে tap_text করো")
        return if (launch(Intent(Intent.ACTION_VIEW, url))) res(true, "ব্রাউজারে YouTube খোলা হয়েছে")
        else res(false, "YouTube খোলা যায়নি")
    }

    private fun takePhoto(): JSONObject =
        if (launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))) res(true, "ক্যামেরা খোলা হয়েছে; শাটার বাটন চাপতে read_screen/tap_text ব্যবহার করো")
        else res(false, "ক্যামেরা খোলা যায়নি")

    // ------------------------------------------------------------------ সময়

    private fun setAlarm(hour: Int, minute: Int, label: String): JSONObject {
        if (hour !in 0..23 || minute !in 0..59) return res(false, "সময় ঠিক নেই")
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (launch(i)) res(true, "অ্যালার্ম সেট হয়েছে $hour:${minute.toString().padStart(2, '0')}")
        else res(false, "অ্যালার্ম অ্যাপ পাইনি")
    }

    private fun setTimer(seconds: Int): JSONObject {
        if (seconds <= 0) return res(false, "সময় ঠিক নেই")
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (launch(i)) res(true, "টাইমার চালু হয়েছে") else res(false, "টাইমার অ্যাপ পাইনি")
    }

    private fun addEvent(title: String, datetime: String, durationMin: Int): JSONObject {
        val start = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(datetime)?.time
        } catch (e: Exception) {
            null
        } ?: return res(false, "তারিখ-সময়ের ফরম্যাট ঠিক নেই (yyyy-MM-dd HH:mm)")
        val i = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + durationMin * 60_000L)
        return if (launch(i)) res(true, "ইভেন্টের ফর্ম খোলা হয়েছে, সেভ বাটন চাপতে হবে")
        else res(false, "ক্যালেন্ডার অ্যাপ পাইনি")
    }

    // ------------------------------------------------------------------ সেটিংস ও ডিভাইস

    private fun openSettings(page: String): JSONObject {
        val action = when (page) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "internet_panel" ->
                if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                else Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "date" -> Settings.ACTION_DATE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "about" -> Settings.ACTION_DEVICE_INFO_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "mobile_data" -> Settings.ACTION_WIRELESS_SETTINGS
            "language" -> Settings.ACTION_LOCALE_SETTINGS
            "privacy" -> Settings.ACTION_PRIVACY_SETTINGS
            "security" -> Settings.ACTION_SECURITY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return if (launch(Intent(action))) res(true, "সেটিংস খোলা হয়েছে ($page); দরকার হলে read_screen/tap_text দিয়ে এগোও")
        else res(false, "সেটিংস পেজ খোলা যায়নি")
    }

    private fun setFlashlight(on: Boolean): JSONObject {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return res(false, "ফ্ল্যাশলাইট পাইনি")
        cm.setTorchMode(id, on)
        return res(true, if (on) "ফ্ল্যাশলাইট জ্বলছে" else "ফ্ল্যাশলাইট বন্ধ")
    }

    private fun setVolume(percent: Int, stream: String): JSONObject {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val s = when (stream) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "call" -> AudioManager.STREAM_VOICE_CALL
            "notification" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        val max = am.getStreamMaxVolume(s)
        val level = (percent.coerceIn(0, 100) * max / 100)
        am.setStreamVolume(s, level, AudioManager.FLAG_SHOW_UI)
        return res(true, "ভলিউম $percent% করা হয়েছে")
    }

    private fun setBrightness(percent: Int): JSONObject {
        if (!Settings.System.canWrite(ctx)) {
            return res(false, "সেটিংস বদলানোর পারমিশন (Write settings) দেওয়া নেই")
        }
        val v = (percent.coerceIn(1, 100) * 255 / 100).coerceAtLeast(1)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v)
        return res(true, "উজ্জ্বলতা $percent% করা হয়েছে")
    }

    private fun setRinger(mode: String): JSONObject {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.ringerMode = when (mode) {
            "silent" -> AudioManager.RINGER_MODE_SILENT
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return res(true, "রিংগার মোড: $mode")
    }

    private fun deviceInfo(): JSONObject {
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1

        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val net = when {
            caps == null -> "ইন্টারনেট নেই"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "ওয়াইফাই"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "মোবাইল ডেটা"
            else -> "অন্য নেটওয়ার্ক"
        }
        val now = SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        return res(true, "ব্যাটারি $pct%, চার্জে আছে: $charging, নেটওয়ার্ক: $net, এখন: $now")
    }

    private suspend fun copyClip(text: String): JSONObject = withContext(Dispatchers.Main) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("bushra", text))
        res(true, "ক্লিপবোর্ডে কপি হয়েছে")
    }

    private suspend fun getClip(): JSONObject = withContext(Dispatchers.Main) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.getItemAt(0)?.text?.toString()
        if (t.isNullOrEmpty()) res(false, "ক্লিপবোর্ড খালি বা পড়ার অনুমতি নেই") else res(true, t)
    }
}
