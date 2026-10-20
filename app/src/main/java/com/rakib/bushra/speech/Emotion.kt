package com.rakib.bushra.speech

/** কথার আবেগ: Edge TTS-এর গতি/সুর/জোর এবং ফোনের TTS-এর pitch/rate-এ রূপ নেয় */
enum class Emotion(
    val tag: String,
    val enTag: String,
    val rate: String,
    val pitch: String,
    val volume: String,
    val sysRate: Float,
    val sysPitch: Float
) {
    NORMAL("স্বাভাবিক", "normal", "+0%", "+0Hz", "+0%", 1.0f, 1.0f),
    HAPPY("খুশি", "happy", "+8%", "+6Hz", "+5%", 1.08f, 1.12f),
    EXCITED("উচ্ছ্বসিত", "excited", "+16%", "+9Hz", "+8%", 1.15f, 1.2f),
    SOFT("কোমল", "soft", "-10%", "+1Hz", "-8%", 0.92f, 1.05f),
    SAD("দুঃখী", "sad", "-14%", "-5Hz", "-10%", 0.88f, 0.9f),
    CURIOUS("কৌতূহলী", "curious", "+4%", "+5Hz", "+0%", 1.03f, 1.12f),
    ROMANTIC("রোমান্টিক", "romantic", "-8%", "+2Hz", "-10%", 0.93f, 1.08f),
    WORRIED("চিন্তিত", "worried", "-4%", "+3Hz", "+0%", 0.97f, 1.02f),
    SHY("লাজুক", "shy", "-6%", "+4Hz", "-12%", 0.95f, 1.1f),
    PLAYFUL("দুষ্টু", "playful", "+10%", "+7Hz", "+3%", 1.1f, 1.15f);

    companion object {
        /** বাংলা প্রম্পটের জন্য ট্যাগ তালিকা */
        val tagList: String = values().joinToString(", ") { "[${it.tag}]" }

        /** ইংরেজি প্রম্পটের জন্য ট্যাগ তালিকা */
        val tagListEn: String = values().joinToString(", ") { "[${it.enTag}]" }

        /** বাংলা বা ইংরেজি, যেকোনো ট্যাগ চেনে */
        fun fromTag(t: String): Emotion {
            val k = t.trim().lowercase()
            return values().firstOrNull { it.tag == k || it.enTag == k } ?: NORMAL
        }

        private val leadTag = Regex("^\\s*\\[([^\\]]{1,12})\\]\\s*")

        /** উত্তরের শুরুর [আবেগ] ট্যাগ আলাদা করে: (আবেগ, পরিষ্কার লেখা) */
        fun split(reply: String): Pair<Emotion, String> {
            val m = leadTag.find(reply)
            val emo = if (m != null) fromTag(m.groupValues[1]) else NORMAL
            var text = if (m != null) reply.substring(m.range.last + 1) else reply
            // ভুল করে মাঝে আরও [ট্যাগ] এলে সেগুলোও সরিয়ে ফেলে
            text = text.replace(Regex("\\[[^\\]]{1,12}\\]"), "").trim()
            return emo to text
        }
    }
}
