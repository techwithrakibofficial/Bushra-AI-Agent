package com.rakib.bushra.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakib.bushra.R
import com.rakib.bushra.core.BushraCore
import com.rakib.bushra.core.BushraState
import com.rakib.bushra.core.ChatLine
import com.rakib.bushra.core.Phase
import com.rakib.bushra.service.BushraService

@Composable
fun HomeScreen(
    onToggleService: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val phase by BushraState.phase.collectAsState()
    val running by BushraState.serviceRunning.collectAsState()
    val level by BushraState.level.collectAsState()
    val chat by BushraState.chat.collectAsState()
    val heard by BushraState.lastHeard.collectAsState()
    var input by remember { mutableStateOf("") }
    var kill by remember { mutableStateOf(BushraCore.prefs.killSwitch) }

    val statusText = when {
        !running && phase == Phase.IDLE -> stringResource(R.string.status_sleeping)
        phase == Phase.LISTENING -> stringResource(R.string.status_listening)
        phase == Phase.THINKING -> stringResource(R.string.status_thinking)
        phase == Phase.SPEAKING -> stringResource(R.string.status_speaking)
        else -> stringResource(R.string.status_idle)
    }
    val quickPrompt = stringResource(R.string.tile_quick_prompt)
    val aiPrompt = stringResource(R.string.tile_ai_prompt)
    val shown = chat.takeLast(30)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- হেডার: লোগো, নাম, সেটিংস
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.bushra_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .border(2.dp, Brush.linearGradient(listOf(Clay.glow, Clay.accent2)), CircleShape)
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Clay.text)) { append("BUSHRA ") }
                            withStyle(SpanStyle(color = Clay.accent)) { append("AI") }
                        },
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(stringResource(R.string.home_tagline), color = Clay.muted, fontSize = 13.sp)
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clay(14.dp, Clay.surface, 6.dp)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) { Text("⚙", fontSize = 22.sp, color = Clay.accent) }
            }
        }

        // ---- গোলক + দুই পাশের চিপ
        item {
            Box(Modifier.fillMaxWidth().height(250.dp)) {
                Orb(phase, level, Modifier.fillMaxSize())
                InfoChip(
                    "✦", stringResource(R.string.chip_smart_title), stringResource(R.string.chip_smart_sub),
                    Modifier.align(Alignment.TopStart)
                )
                InfoChip(
                    "🛡", stringResource(R.string.chip_secure_title), stringResource(R.string.chip_secure_sub),
                    Modifier.align(Alignment.TopEnd)
                )
            }
        }

        // ---- শুভেচ্ছা/অবস্থা বাবল
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .widthIn(max = 320.dp)
                        .clay(26.dp, Clay.surface, 6.dp)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(
                        stringResource(R.string.home_greeting),
                        color = Clay.text,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    statusText,
                    color = Clay.accent,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (heard.isNotBlank()) {
                    Text(
                        stringResource(R.string.heard_prefix, heard),
                        color = Clay.muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // ---- বড় চালু/বন্ধ বাটন
        item {
            val colors = if (running) listOf(Clay.warn, Color(0xFFFF8A4C)) else listOf(Clay.accent, Clay.accent2)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clay(36.dp, Clay.surface, 12.dp)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Brush.horizontalGradient(colors))
                    .clickable { onToggleService() }
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("🎙", fontSize = 24.sp)
                Spacer(Modifier.size(12.dp))
                Text(
                    if (running) stringResource(R.string.btn_stop) else stringResource(R.string.btn_start),
                    color = Clay.onAccent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
                Spacer(Modifier.size(12.dp))
                Text("›", color = Clay.onAccent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (running) {
            item {
                ClayButton(
                    text = stringResource(R.string.btn_listen),
                    onClick = { BushraService.listenNow(ctx) },
                    color = Clay.accent2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ---- অ্যাকশন বন্ধের সুইচ
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clay(26.dp, Clay.surface, 6.dp)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("▐▌▐", color = Clay.accent, fontSize = 16.sp)
                Text(
                    stringResource(R.string.toggle_actions_off),
                    color = Clay.text,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
                ClaySwitch(kill) {
                    kill = it
                    BushraCore.prefs.killSwitch = it
                }
            }
        }

        // ---- লেখার ঘর + পাঠাও
        item {
            val send = {
                val t = input.trim()
                if (t.isNotEmpty()) {
                    input = ""
                    BushraCore.ask(t)
                }
            }
            Row(
                Modifier.fillMaxWidth().clay(30.dp, Clay.surface, 6.dp).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    if (input.isEmpty()) {
                        Text(stringResource(R.string.input_hint), color = Clay.muted, fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Clay.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(Clay.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.horizontalGradient(listOf(Clay.accent2, Clay.accent)))
                        .clickable { send() }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("➤", color = Clay.onAccent, fontSize = 16.sp)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(R.string.btn_send),
                        color = Clay.onAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // ---- চারটি টাইল
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("⚡", stringResource(R.string.tile_quick), Modifier.weight(1f)) { BushraCore.ask(quickPrompt) }
                Tile("🧠", stringResource(R.string.tile_ai), Modifier.weight(1f)) { BushraCore.ask(aiPrompt) }
                Tile("🛡", stringResource(R.string.tile_privacy), Modifier.weight(1f)) { onOpenPermissions() }
                Tile("☁", stringResource(R.string.tile_always), Modifier.weight(1f)) {
                    if (!running) onToggleService()
                }
            }
        }

        // ---- কথোপকথন
        if (shown.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.conversation_title),
                    color = Clay.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(shown, key = { it.id }) { line -> Bubble(line) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun InfoChip(glyph: String, title: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(max = 150.dp)
            .clay(20.dp, Clay.surface, 6.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(glyph, color = Clay.accent, fontSize = 18.sp)
            Spacer(Modifier.size(6.dp))
            Text(title, color = Clay.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Text(sub, color = Clay.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun Tile(glyph: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clay(22.dp, Clay.surface, 6.dp)
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(glyph, fontSize = 24.sp, color = Clay.accent)
        Text(
            label,
            color = Clay.text,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
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
                .clay(20.dp, if (line.fromUser) Clay.accent2.darken(0.45f) else Clay.surface, 6.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(line.text, color = Clay.text, fontSize = 15.sp)
        }
    }
}
