package com.rakib.bushra.ai

import org.json.JSONArray
import org.json.JSONObject

/** Gemini-কে জানানো টুল (অ্যাকশন) তালিকা। ActionExecutor-এ এদের বাস্তবায়ন আছে। */
object Tools {

    private fun p(type: String, desc: String): JSONObject =
        JSONObject().put("type", type).put("description", desc)

    private fun enumP(desc: String, values: List<String>): JSONObject {
        val arr = JSONArray()
        for (v in values) arr.put(v)
        return p("string", desc).put("enum", arr)
    }

    private fun fn(
        name: String,
        desc: String,
        props: List<Pair<String, JSONObject>> = emptyList(),
        required: List<String> = emptyList()
    ): JSONObject {
        val o = JSONObject().put("name", name).put("description", desc)
        if (props.isNotEmpty()) {
            val pr = JSONObject()
            for ((k, v) in props) pr.put(k, v)
            val schema = JSONObject().put("type", "object").put("properties", pr)
            if (required.isNotEmpty()) {
                val req = JSONArray()
                for (r in required) req.put(r)
                schema.put("required", req)
            }
            o.put("parameters", schema)
        }
        return o
    }

    private val str = "string"

    fun declarations(): JSONArray {
        val a = JSONArray()
        // ---- অ্যাপ ও যোগাযোগ ----
        a.put(fn("open_app", "Open an installed app by its name (e.g. WhatsApp, Facebook, YouTube, Camera).",
            listOf("app_name" to p(str, "App name as the user said it")), listOf("app_name")))
        a.put(fn("make_call", "Place a normal phone call to a saved contact name or a phone number.",
            listOf("target" to p(str, "Contact name or phone number")), listOf("target")))
        a.put(fn("end_call", "End or reject the current/ringing phone call."))
        a.put(fn("answer_call", "Answer the ringing incoming call."))
        a.put(fn("set_speaker", "Turn speakerphone on or off.",
            listOf("on" to p("boolean", "true for speaker on")), listOf("on")))
        a.put(fn("send_sms", "Send an SMS text message directly.",
            listOf("target" to p(str, "Contact name or phone number"),
                "message" to p(str, "Message text")), listOf("target", "message")))
        a.put(fn("read_sms", "Read the latest received SMS messages.",
            listOf("count" to p("integer", "How many (default 5, max 15)"))))
        a.put(fn("whatsapp_message", "Send a WhatsApp text message to a contact (uses accessibility to press send).",
            listOf("target" to p(str, "Contact name or phone number"),
                "message" to p(str, "Message text")), listOf("target", "message")))
        a.put(fn("whatsapp_call", "Start a WhatsApp voice or video call to a contact.",
            listOf("target" to p(str, "Contact name or phone number"),
                "video" to p("boolean", "true for video call")), listOf("target")))
        a.put(fn("get_contact", "Look up phone numbers of a contact by name.",
            listOf("name" to p(str, "Contact name")), listOf("name")))
        a.put(fn("send_email", "Open an email draft addressed to someone.",
            listOf("to" to p(str, "Email address"), "subject" to p(str, "Subject"),
                "body" to p(str, "Body text")), listOf("to")))

        // ---- ওয়েব, ম্যাপ, মিডিয়া ----
        a.put(fn("open_url", "Open a web address in the browser.",
            listOf("url" to p(str, "Full URL")), listOf("url")))
        a.put(fn("web_search", "Search Google in the browser.",
            listOf("query" to p(str, "Search text")), listOf("query")))
        a.put(fn("navigate", "Start map navigation to a place.",
            listOf("place" to p(str, "Place name or address")), listOf("place")))
        a.put(fn("play_youtube", "Search YouTube for a video or song and open the results.",
            listOf("query" to p(str, "What to play/search")), listOf("query")))
        a.put(fn("take_photo", "Open the camera app to take a photo."))

        // ---- সময় ও রিমাইন্ডার ----
        a.put(fn("set_alarm", "Set an alarm clock.",
            listOf("hour" to p("integer", "Hour 0-23"), "minute" to p("integer", "Minute 0-59"),
                "label" to p(str, "Alarm label")), listOf("hour", "minute")))
        a.put(fn("set_timer", "Start a countdown timer.",
            listOf("seconds" to p("integer", "Duration in seconds")), listOf("seconds")))
        a.put(fn("add_calendar_event", "Open a calendar event form prefilled with details.",
            listOf("title" to p(str, "Event title"),
                "datetime" to p(str, "Start time as yyyy-MM-dd HH:mm (24h, local)"),
                "duration_min" to p("integer", "Duration in minutes")), listOf("title", "datetime")))

        // ---- ফোনের সেটিংস ----
        a.put(fn("open_settings", "Open a phone settings page.",
            listOf("page" to enumP("Which settings page", listOf(
                "wifi", "internet_panel", "bluetooth", "display", "sound", "battery", "apps",
                "location", "date", "accessibility", "nfc", "storage", "about", "airplane",
                "mobile_data", "language", "privacy", "security", "main"))), listOf("page")))
        a.put(fn("set_flashlight", "Turn the flashlight on or off.",
            listOf("on" to p("boolean", "true for on")), listOf("on")))
        a.put(fn("set_volume", "Set a volume level in percent.",
            listOf("percent" to p("integer", "0-100"),
                "stream" to enumP("Which volume", listOf("media", "ring", "alarm", "call", "notification"))),
            listOf("percent")))
        a.put(fn("set_brightness", "Set screen brightness in percent.",
            listOf("percent" to p("integer", "1-100")), listOf("percent")))
        a.put(fn("set_ringer_mode", "Set ringer mode.",
            listOf("mode" to enumP("Mode", listOf("normal", "vibrate", "silent"))), listOf("mode")))
        a.put(fn("get_device_info", "Get battery level, charging state, network and current date/time."))
        a.put(fn("copy_to_clipboard", "Copy text to the clipboard.",
            listOf("text" to p(str, "Text")), listOf("text")))
        a.put(fn("get_clipboard", "Read the clipboard text."))

        // ---- স্ক্রিন নিয়ন্ত্রণ (Accessibility) ----
        a.put(fn("read_screen", "Read what is currently on the screen: visible texts, buttons and their positions. Use before and after tapping inside apps."))
        a.put(fn("tap_text", "Tap a button/label on screen by its visible text or description.",
            listOf("text" to p(str, "Visible text or content description")), listOf("text")))
        a.put(fn("tap_xy", "Tap at screen coordinates (from read_screen).",
            listOf("x" to p("integer", "X pixel"), "y" to p("integer", "Y pixel")), listOf("x", "y")))
        a.put(fn("swipe", "Swipe the screen. 'up' scrolls content down (finger moves up).",
            listOf("direction" to enumP("Finger direction", listOf("up", "down", "left", "right"))),
            listOf("direction")))
        a.put(fn("type_text", "Type text into the focused or first input field on screen.",
            listOf("text" to p(str, "Text to type")), listOf("text")))
        a.put(fn("press_key", "Press a system button or open a system panel.",
            listOf("key" to enumP("Key", listOf("back", "home", "recents", "notifications",
                "quick_settings", "lock", "screenshot", "power_menu"))), listOf("key")))
        a.put(fn("wait", "Wait a few seconds for a screen to load.",
            listOf("seconds" to p("integer", "1-10")), listOf("seconds")))

        // ---- স্মৃতি ----
        a.put(fn("remember", "Save a fact about the user to long-term memory.",
            listOf("key" to p(str, "Short label, e.g. 'আম্মুর নাম্বার' or 'পছন্দের গান'"),
                "value" to p(str, "The fact")), listOf("key", "value")))
        a.put(fn("forget", "Delete a saved memory item.",
            listOf("key" to p(str, "Label to delete")), listOf("key")))
        return a
    }
}
