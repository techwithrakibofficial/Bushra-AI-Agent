package com.rakib.bushra.speech

/** TTS-এ পাঠানোর আগে লেখা পরিষ্কার করে, যাতে উচ্চারণ ঠিক হয় */
object TtsText {

    private const val BN_DIGITS = "০১২৩৪৫৬৭৮৯"

    /** লেখার বেশিরভাগ অক্ষর ল্যাটিন (ইংরেজি) হলে true: তখন ইংরেজি কণ্ঠ ব্যবহার হয় */
    fun isLatin(text: String): Boolean {
        var latin = 0
        var other = 0
        for (c in text) {
            if (!c.isLetter()) continue
            if (c.code < 0x250) latin++ else other++
        }
        return latin > 0 && latin >= other
    }

    fun clean(raw: String): String {
        val latin = isLatin(raw)
        var s = raw
            .replace(Regex("[*#_`~]"), "")
            .replace(Regex("[\\p{So}\\p{Cs}]"), "")
        s = if (latin) {
            s.replace("%", " percent").replace("&", " and ").replace("\n", ". ")
        } else {
            s.replace("%", " শতাংশ").replace("&", " এবং ").replace("\n", "। ")
        }
        if (!latin) {
            // ইংরেজি অঙ্ক বাংলা অঙ্কে: বাংলা ভয়েস তখন সংখ্যা ঠিকভাবে পড়ে
            val sb = StringBuilder(s.length)
            for (c in s) {
                if (c in '0'..'9') sb.append(BN_DIGITS[c - '0']) else sb.append(c)
            }
            s = sb.toString()
        }
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * বাক্যের সীমানা ধরে ছোট ছোট টুকরোয় ভাগ করে (প্রতিটি maxLen অক্ষরের মধ্যে)।
     * firstLen দিলে প্রথম টুকরো ছোট রাখা হয়, যাতে কথা শুরু হতে দেরি না হয়।
     */
    fun chunks(text: String, maxLen: Int, firstLen: Int = maxLen): List<String> {
        val out = ArrayList<String>()
        val sentences = text.split(Regex("(?<=[।.!?])\\s+"))
        var cur = StringBuilder()
        var limit = firstLen
        fun flush() {
            if (cur.isNotBlank()) out.add(cur.toString().trim())
            cur = StringBuilder()
            limit = maxLen
        }
        for (sen in sentences) {
            if (sen.isBlank()) continue
            if (cur.isNotEmpty() && cur.length + sen.length + 1 > limit) flush()
            if (sen.length > limit) {
                // খুব লম্বা বাক্য: শব্দের সীমানায় কাটে
                var rest = sen
                while (rest.length > limit) {
                    var cut = rest.lastIndexOf(' ', limit)
                    if (cut <= 0) cut = limit
                    out.add(rest.substring(0, cut).trim())
                    rest = rest.substring(cut).trim()
                    limit = maxLen
                }
                if (rest.isNotEmpty()) cur.append(rest).append(' ')
            } else {
                cur.append(sen).append(' ')
            }
        }
        flush()
        return out.filter { it.isNotBlank() }
    }
}
