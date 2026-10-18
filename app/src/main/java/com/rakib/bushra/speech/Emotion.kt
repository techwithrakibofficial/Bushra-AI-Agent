package com.rakib.bushra.speech

/** কথার আবেগ: Edge TTS-এর গতি/সুর/জোর এবং ফোনের TTS-এর pitch/rate-এ রূপ নেয় */
enum class Emotion(
    val tag: String,
    val rate: String,
    val pitch: String,
    val volume: String,
    val sysRate: Float,
    val sysPitch: Float
) {
    NORMAL("স্বাভাবিক", "+0%", "+0Hz", "+0%", 1.0f, 1.0f),
    HAPPY("খুশি", "+8%", "+6Hz", "+5%", 1.08f, 1.12f),
    EXCITED("উচ্ছ্বসিত", "+16%", "+9Hz", "+8%", 1.15f, 1.2f),
    SOFT("কোমল", "-10%", "+1Hz", "-8%", 0.92f, 1.05f),
    SAD("দুঃখী", "-14%", "-5Hz", "-10%", 0.88f, 0.9f),
    CURIOUS("কৌতূহলী", "+4%", "+5Hz", "+0%", 1.03f, 1.12f),
    ROMANTIC("রোমান্টিক", "-8%", "+2Hz", "-10%", 0.93f, 1.08f),
    WORRIED("চিন্তিত", "-4%", "+3Hz", "+0%", 0.97f, 1.02f),
    SHY("লাজুক", "-6%", "+4Hz", "-12%", 0.95f, 1.1f),
    PLAYFUL("দুষ্টু", "+10%", "+7Hz", "+3%", 1.1f, 1.15f);

    companion object {
        val tagList: String = values().joinToString(", ") { "[${it.tag}]" }

        fun fromTag(t: String): Emotion =
            values().firstOrNull { it.tag == t.trim() } ?: NORMAL

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
