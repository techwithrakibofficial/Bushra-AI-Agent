package com.rakib.bushra.core

import kotlinx.coroutines.flow.MutableStateFlow

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
        val next = (cur + ChatLine(fromUser, text)).takeLast(60)
        chat.value = next
    }
}
