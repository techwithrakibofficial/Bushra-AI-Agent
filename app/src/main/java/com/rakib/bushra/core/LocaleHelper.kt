package com.rakib.bushra.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * অ্যাপের ভাষা (English ডিফল্ট, বাংলা বেছে নেওয়া যায়)। সেটিংসের এক ট্যাপে পুরো অ্যাপ বদলে যায়।
 * Activity ও Service-এর কথা বলা/নোটিফিকেশনের লেখাও এই ভাষায় আসে।
 */
object LocaleHelper {
    const val EN = "en"
    const val BN = "bn"

    private const val PREFS = "bushra_prefs"
    private const val KEY = "ui_lang"

    /** Prefs তৈরির আগেই (attachBaseContext-এ) ভাষা পড়তে হয়, তাই সরাসরি SharedPreferences */
    fun current(base: Context): String {
        val v = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, EN) ?: EN
        return if (v == BN) BN else EN
    }

    fun set(base: Context, lang: String) {
        base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, if (lang == BN) BN else EN).apply()
    }

    fun locale(lang: String): Locale = if (lang == BN) Locale("bn", "BD") else Locale.ENGLISH

    fun wrap(base: Context): Context {
        val cfg = Configuration(base.resources.configuration)
        val loc = locale(current(base))
        Locale.setDefault(loc)
        cfg.setLocale(loc)
        return base.createConfigurationContext(cfg)
    }
}
