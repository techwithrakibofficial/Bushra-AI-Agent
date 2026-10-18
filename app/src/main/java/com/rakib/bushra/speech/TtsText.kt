package com.rakib.bushra.speech

/** TTS-এ পাঠানোর আগে লেখা পরিষ্কার করে, যাতে উচ্চারণ ঠিক হয় */
object TtsText {

    private const val BN_DIGITS = "০১২৩৪৫৬৭৮৯"

    fun clean(raw: String): String {
        var s = raw
            .replace(Regex("[*#_`~]"), "")
            .replace(Regex("[\\p{So}\\p{Cs}]"), "")
            .replace("%", " শতাংশ")
            .replace("&", " এবং ")
            .replace("\n", "। ")
        // ইংরেজি অঙ্ক বাংলা অঙ্কে: বাংলা ভয়েস তখন সংখ্যা ঠিকভাবে পড়ে
        val sb = StringBuilder(s.length)
        for (c in s) {
            if (c in '0'..'9') sb.append(BN_DIGITS[c - '0']) else sb.append(c)
        }
        s = sb.toString()
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /** বাক্যের সীমানা ধরে ছোট ছোট টুকরোয় ভাগ করে (প্রতিটি maxLen অক্ষরের মধ্যে) */
    fun chunks(text: String, maxLen: Int): List<String> {
        val out = ArrayList<String>()
        val sentences = text.split(Regex("(?<=[।.!?])\\s+"))
        var cur = StringBuilder()
        for (sen in sentences) {
            if (sen.isBlank()) continue
            if (cur.isNotEmpty() && cur.length + sen.length + 1 > maxLen) {
                out.add(cur.toString().trim())
                cur = StringBuilder()
            }
            if (sen.length > maxLen) {
                // খুব লম্বা বাক্য: শব্দের সীমানায় কাটে
                var rest = sen
                while (rest.length > maxLen) {
                    var cut = rest.lastIndexOf(' ', maxLen)
                    if (cut <= 0) cut = maxLen
                    out.add(rest.substring(0, cut).trim())
                    rest = rest.substring(cut).trim()
                }
                if (rest.isNotEmpty()) cur.append(rest).append(' ')
            } else {
                cur.append(sen).append(' ')
            }
        }
        if (cur.isNotBlank()) out.add(cur.toString().trim())
        return out.filter { it.isNotBlank() }
    }
}
