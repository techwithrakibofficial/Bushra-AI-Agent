package com.rakib.bushra.core

import org.json.JSONObject

/**
 * দ্রুত পথ (fast path): সহজ ও স্পষ্ট কমান্ড (অ্যাপ খোলা, টর্চ, ভলিউম, সময়, কল ইত্যাদি) এআই-কে না জিজ্ঞেস করেই
 * সরাসরি চালানোর জন্য বাংলা ও ইংরেজি প্যাটার্ন মেলায়। মিল না পেলে বা কাজটা সফল না হলে কথাটা Gemini-র কাছে যায়,
 * তাই জটিল অনুরোধ কখনো আটকে যায় না।
 */
object IntentParser {

    enum class Kind { ACTION, STOP_TALKING, TIME }

    /**
     * @param tool ActionExecutor-এর টুলের নাম (ACTION হলে)
     * @param args টুলের আর্গুমেন্ট
     * @param ack সফল হলে যা বলবে: ackKey = রিসোর্সের নাম, ackArg = তার আর্গুমেন্ট
     */
    class Match(
        val kind: Kind,
        val tool: String = "",
        val args: JSONObject = JSONObject(),
        val ackKey: String = "ack_done",
        val ackArg: String? = null
    )

    // একাধিক ধাপের অনুরোধ চেনার শব্দ: এগুলো থাকলে Gemini-কে দেওয়া হয়
    private val chainWords = listOf(
        " and ", " then ", " after ", " also ", " with ", " send ", " message ", " text ",
        " এবং ", " তারপর ", " আর ", " পাঠাও", " মেসেজ", " লিখ", " বলো যে", " বল যে", "ভিডিও", "সার্চ", "খুঁজ"
    )

    private val punct = Regex("[\\p{Punct}।॥‘’“”]+")
    private val spaces = Regex("\\s+")

    private fun norm(s: String): String =
        " " + s.lowercase().replace(punct, " ").replace(spaces, " ").trim() + " "

    fun parse(raw: String): Match? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val t = norm(text)
        val words = t.trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 8) return null

        // ---- চুপ করা
        val single = words.size == 1 &&
            words[0] in setOf("stop", "quiet", "silence", "চুপ", "থামো", "থামুন", "থাম")
        if (single || matchAny(t, " stop talking ", " be quiet ", " shut up ", " চুপ করো ", " চুপ কর ")) {
            return Match(Kind.STOP_TALKING)
        }

        if (chainWords.any { t.contains(it) }) return null

        // ---- সময়
        if (matchAny(t, " what time is it ", " what's the time ", " whats the time ", " tell me the time ",
                " current time ", " সময় কত ", " কয়টা বাজে ", " কটা বাজে ", " এখন কয়টা ", " এখন কটা ", " সময় কতো ")) {
            return Match(Kind.TIME)
        }

        // ---- টর্চ
        val flash = matchAny(t, " flashlight ", " torch ", " টর্চ ", " ফ্ল্যাশলাইট ", " ফ্ল্যাশ লাইট ", " ফ্ল্যাশ ")
        if (flash) {
            val off = matchAny(t, " off ", " disable ", " নেভাও ", " নিভাও ", " বন্ধ ", " অফ ", " নিভিয়ে ", " নেভান ")
            val on = matchAny(t, " on ", " enable ", " জ্বালাও ", " জ্বালান ", " চালু ", " অন ", " জ্বেলে ", " দাও ")
            if (off && !on) return Match(Kind.ACTION, "set_flashlight", JSONObject().put("on", false), "ack_flash_off")
            if (on || words.size <= 2) return Match(Kind.ACTION, "set_flashlight", JSONObject().put("on", true), "ack_flash_on")
        }

        // ---- ভলিউম
        if (matchAny(t, " volume ", " ভলিউম ", " আওয়াজ ", " সাউন্ড ")) {
            val num = Regex("\\d{1,3}").find(toAsciiDigits(t))?.value?.toIntOrNull()
            if (num != null) {
                return Match(Kind.ACTION, "set_volume", JSONObject().put("percent", num).put("stream", "media"), "ack_volume")
            }
            if (matchAny(t, " up ", " increase ", " raise ", " louder ", " higher ", " বাড়াও ", " বাড়ান ", " বেশি ", " জোরে ")) {
                return Match(Kind.ACTION, "media_control", JSONObject().put("action", "volume_up"), "ack_volume")
            }
            if (matchAny(t, " down ", " decrease ", " lower ", " reduce ", " softer ", " কমাও ", " কমান ", " কম ", " আস্তে ")) {
                return Match(Kind.ACTION, "media_control", JSONObject().put("action", "volume_down"), "ack_volume")
            }
            if (matchAny(t, " mute ", " মিউট ", " বন্ধ ")) {
                return Match(Kind.ACTION, "set_volume", JSONObject().put("percent", 0).put("stream", "media"), "ack_volume")
            }
        }

        // ---- উজ্জ্বলতা
        if (matchAny(t, " brightness ", " উজ্জ্বলতা ", " ব্রাইটনেস ")) {
            val num = Regex("\\d{1,3}").find(toAsciiDigits(t))?.value?.toIntOrNull()
            if (num != null) {
                return Match(Kind.ACTION, "set_brightness", JSONObject().put("percent", num), "ack_brightness")
            }
            if (matchAny(t, " max ", " full ", " maximum ", " বেশি ", " বাড়াও ", " বাড়ান ", " up ", " increase ")) {
                return Match(Kind.ACTION, "set_brightness", JSONObject().put("percent", 100), "ack_brightness")
            }
            if (matchAny(t, " min ", " low ", " lower ", " কম ", " কমাও ", " কমান ", " down ", " decrease ", " dim ")) {
                return Match(Kind.ACTION, "set_brightness", JSONObject().put("percent", 20), "ack_brightness")
            }
        }

        // ---- স্পিকার
        if (matchAny(t, " speaker ", " স্পিকার ")) {
            if (matchAny(t, " off ", " বন্ধ ", " অফ ")) {
                return Match(Kind.ACTION, "set_speaker", JSONObject().put("on", false), "ack_speaker_off")
            }
            if (matchAny(t, " on ", " চালু ", " অন ")) {
                return Match(Kind.ACTION, "set_speaker", JSONObject().put("on", true), "ack_speaker_on")
            }
        }

        // ---- মিডিয়া
        val hasSong = matchAny(t, " music ", " song ", " songs ", " track ", " গান ", " মিউজিক ", " গানটা ", " গানটি ")
        if (matchAny(t, " pause ", " পজ ") || (hasSong && matchAny(t, " pause ", " stop ", " থামাও ", " বন্ধ ", " থামান ", " পজ "))) {
            return Match(Kind.ACTION, "media_control", JSONObject().put("action", "pause"))
        }
        if (matchAny(t, " next song ", " next track ", " পরের গান ") ||
            (words.size <= 2 && matchAny(t, " skip ", " next ", " পরের ", " স্কিপ "))) {
            return Match(Kind.ACTION, "media_control", JSONObject().put("action", "next"))
        }
        if (matchAny(t, " previous song ", " previous track ", " আগের গান ") ||
            (words.size <= 2 && matchAny(t, " previous ", " আগের "))) {
            return Match(Kind.ACTION, "media_control", JSONObject().put("action", "previous"))
        }
        if (matchAny(t, " resume ", " continue music ", " play music ", " play the music ", " গান চালাও ", " গান চালু ", " চালিয়ে যাও ")) {
            return Match(Kind.ACTION, "media_control", JSONObject().put("action", "play"))
        }

        // ---- পেছনে / হোম
        if (words.size <= 3 && matchAny(t, " go back ", " back ", " পেছনে যাও ", " পিছনে যাও ", " পেছনে ", " ব্যাক ")) {
            return Match(Kind.ACTION, "press_key", JSONObject().put("key", "back"))
        }
        if (words.size <= 3 && matchAny(t, " go home ", " home screen ", " home ", " হোমে যাও ", " হোম স্ক্রিন ", " হোম ")) {
            return Match(Kind.ACTION, "press_key", JSONObject().put("key", "home"))
        }

        // ---- ক্যামেরা
        if (matchAny(t, " take a photo ", " take a picture ", " take photo ", " ছবি তোলো ", " ছবি তুলো ", " ফটো তোলো ")) {
            return Match(Kind.ACTION, "take_photo")
        }

        // ---- কল করা: "call X" / "X কে কল করো"
        englishCall.find(t)?.let { m ->
            val who = m.groupValues[1].trim()
            if (who.isNotEmpty()) {
                return Match(Kind.ACTION, "make_call", JSONObject().put("target", who), "ack_calling", who)
            }
        }
        bnCall.find(t)?.let { m ->
            val who = m.groupValues[1].trim().removeSuffix("কে").removeSuffix("-কে").trim()
            if (who.isNotEmpty()) {
                return Match(Kind.ACTION, "make_call", JSONObject().put("target", who), "ack_calling", who)
            }
        }

        // ---- অ্যাপ খোলা: "open X" / "X খোলো"
        englishOpen.find(t)?.let { m ->
            val app = m.groupValues[1].trim().removePrefix("the ").removeSuffix(" app").trim()
            if (app.isNotEmpty() && app.split(' ').size <= 3) {
                return Match(Kind.ACTION, "open_app", JSONObject().put("app_name", app), "ack_opening", app)
            }
        }
        bnOpen.find(t)?.let { m ->
            val app = m.groupValues[1].trim().removeSuffix("টা").removeSuffix("টি").trim()
            if (app.isNotEmpty() && app.split(' ').size <= 3) {
                return Match(Kind.ACTION, "open_app", JSONObject().put("app_name", app), "ack_opening", app)
            }
        }
        return null
    }

    private val englishCall = Regex("^ (?:please )?(?:call|phone|dial|ring) (.+?) $")
    private val bnCall = Regex("^ (.+?) (?:কে )?(?:কল|ফোন) (?:করো|কর|দাও|করুন|দিন) $")
    private val englishOpen = Regex("^ (?:please )?(?:open|launch|start) (.+?) $")
    private val bnOpen = Regex("^ (.+?) (?:খোলো|খুলো|খুলুন|ওপেন করো|ওপেন কর|ওপেন করুন|চালাও|চালু করো) $")

    private fun matchAny(t: String, vararg phrases: String): Boolean = phrases.any { t.contains(it) }

    private fun toAsciiDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            val i = "০১২৩৪৫৬৭৮৯".indexOf(c)
            if (i >= 0) sb.append(('0' + i)) else sb.append(c)
        }
        return sb.toString()
    }
}
