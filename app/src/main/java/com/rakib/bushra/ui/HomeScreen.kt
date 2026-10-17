package com.rakib.bushra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakib.bushra.core.BushraCore
import com.rakib.bushra.core.BushraState
import com.rakib.bushra.core.ChatLine
import com.rakib.bushra.core.Phase

@Composable
fun HomeScreen(onToggleService: () -> Unit) {
    val phase by BushraState.phase.collectAsState()
    val running by BushraState.serviceRunning.collectAsState()
    val level by BushraState.level.collectAsState()
    val chat by BushraState.chat.collectAsState()
    val heard by BushraState.lastHeard.collectAsState()
    var input by remember { mutableStateOf("") }
    var kill by remember { mutableStateOf(BushraCore.prefs.killSwitch) }

    val statusText = when {
        !running && phase == Phase.IDLE -> "ঘুমিয়ে আছি। চালু করো, তারপর \"বুশরা\" বলে ডাকো।"
        phase == Phase.LISTENING -> "শুনছি…"
        phase == Phase.THINKING -> "ভাবছি…"
        phase == Phase.SPEAKING -> "বলছি…"
        else -> "\"বুশরা\" বলে ডাকো"
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "বুশরা",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Clay.text,
            modifier = Modifier.padding(top = 12.dp)
        )
        Orb(phase, level, Modifier.fillMaxWidth().height(200.dp))
        Text(
            statusText,
            color = Clay.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (heard.isNotBlank()) {
            Text(
                "শুনেছি: $heard",
                color = Clay.muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayButton(
                text = if (running) "বন্ধ করো" else "চালু করো",
                onClick = onToggleService,
                color = if (running) Clay.warn else Clay.accent,
                modifier = Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ClaySwitch(kill) {
                    kill = it
                    BushraCore.prefs.killSwitch = it
                }
                Text("অ্যাকশন বন্ধ", fontSize = 11.sp, color = Clay.muted)
            }
        }

        val shown = chat.takeLast(30)
        val listState = rememberLazyListState()
        LaunchedEffect(shown.size) {
            if (shown.isNotEmpty()) listState.animateScrollToItem(shown.lastIndex)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(shown, key = { it.id }) { line -> Bubble(line) }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            ClayField(
                value = input,
                onChange = { input = it },
                label = "লিখে বলো",
                modifier = Modifier.weight(1f)
            )
            ClayButton(
                text = "পাঠাও",
                onClick = {
                    val t = input.trim()
                    if (t.isNotEmpty()) {
                        input = ""
                        BushraCore.ask(t)
                    }
                },
                color = Clay.accent2
            )
        }
    }
}

@Composable
private fun Bubble(line: ChatLine) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .clay(20.dp, if (line.fromUser) Clay.accent2.lighten(0.45f) else Clay.surface, 6.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(line.text, color = Clay.text, fontSize = 15.sp)
        }
    }
}
