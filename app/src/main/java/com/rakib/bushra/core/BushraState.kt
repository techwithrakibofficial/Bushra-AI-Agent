package com.rakib.bushra.core

import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Phase { IDLE, LISTENING, THINKING, SPEAKING }

data class ChatLine(val fromUser: Boolean, val text: String, val id: Long = System.nanoTime())

/** UI আর সার্ভিসের মধ্যে ভাগ করা অবস্থা */
object BushraState {
    val phase = MutableStateFlow(Phase.IDLE)
    val serviceRunning = MutableStateFlow(false)
    val chat = MutableStateFlow<List<ChatLine>>(emptyList())
    val lastHeard = MutableStateFlow("")
    val level = MutableStateFlow(0f)
    val ttsStatus = MutableStateFlow("")
    val resumeTick = MutableStateFlow(0)

    fun addChat(fromUser: Boolean, text: String) {
        val cur = chat.value
        chat.value = (cur + ChatLine(fromUser, text)).takeLast(60)
    }
}

/** সমস্যা ধরার জন্য ছোট লগ: সেটিংস পেজে দেখা যায় */
object BushraLog {
    val lines = MutableStateFlow<List<String>>(emptyList())
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun add(tag: String, msg: String) {
        val line = "${fmt.format(Date())} [$tag] $msg"
        lines.value = (lines.value + line).takeLast(60)
    }

    fun clear() {
        lines.value = emptyList()
    }
}
