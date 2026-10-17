package com.rakib.bushra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakib.bushra.core.BushraCore
import com.rakib.bushra.core.BushraState

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = BushraCore.prefs
    val ttsStatus by BushraState.ttsStatus.collectAsState()

    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(prefs.models) }
    var language by remember { mutableStateOf(prefs.language) }
    var wake by remember { mutableStateOf(prefs.wakeWords) }
    var userName by remember { mutableStateOf(prefs.userName) }
    var female by remember { mutableStateOf(prefs.voiceFemale) }
    var incoming by remember { mutableStateOf(prefs.incomingCallEnabled) }
    var aiTalk by remember { mutableStateOf(prefs.aiCallTalk) }
    var busySms by remember { mutableStateOf(prefs.busySms) }
    var memory by remember { mutableStateOf(prefs.memoryMap()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("সেটিংস", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Clay.text)

        ClayCard {
            Text("Gemini (এআই)", fontWeight = FontWeight.Bold, color = Clay.text)
            Text(
                "aistudio.google.com থেকে ফ্রি API key নিয়ে এখানে বসান।",
                fontSize = 13.sp, color = Clay.muted, modifier = Modifier.padding(vertical = 6.dp)
            )
            ClayField(apiKey, { apiKey = it; prefs.apiKey = it }, "API key", password = !showKey)
            ClayButton(
                text = if (showKey) "key লুকাও" else "key দেখাও",
                onClick = { showKey = !showKey },
                color = Clay.accent2,
                modifier = Modifier.padding(top = 8.dp)
            )
            ClayField(
                models, { models = it; prefs.models = it }, "মডেলের তালিকা (কমা দিয়ে; একটা না চললে পরেরটা)",
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        ClayCard {
            Text("ভয়েস ও ভাষা", fontWeight = FontWeight.Bold, color = Clay.text)
            ClayField(
                language, { language = it; prefs.language = it }, "ভাষা কোড (bn-BD, en-US ...)",
                modifier = Modifier.padding(top = 8.dp)
            )
            ClayField(
                wake, { wake = it; prefs.wakeWords = it }, "ডাকনাম (কমা দিয়ে আলাদা)",
                modifier = Modifier.padding(top = 10.dp)
            )
            ClayField(
                userName, { userName = it; prefs.userName = it }, "তোমার নাম",
                modifier = Modifier.padding(top = 10.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (female) "মেয়েলি কণ্ঠ" else "ছেলেদের কণ্ঠ",
                    modifier = Modifier.weight(1f), color = Clay.text
                )
                ClaySwitch(female) {
                    female = it
                    prefs.voiceFemale = it
                    BushraCore.applyVoice()
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ClayButton(
                    text = "পরের ভয়েস",
                    onClick = {
                        val v = BushraCore.listVoices()
                        if (v.isNotEmpty()) {
                            val i = v.indexOf(prefs.voiceName)
                            prefs.voiceName = v[(i + 1) % v.size]
                            BushraCore.applyVoice()
                            BushraCore.speak("আমি বুশরা। এই কণ্ঠটা কেমন লাগছে?")
                        }
                    },
                    color = Clay.accent2,
                    modifier = Modifier.weight(1f)
                )
                ClayButton(
                    text = "ভয়েস ডেটা নামাও",
                    onClick = { Perms.openTtsSettings(ctx) },
                    color = Clay.accent2,
                    modifier = Modifier.weight(1f)
                )
            }
            if (ttsStatus.isNotBlank()) {
                Text(ttsStatus, fontSize = 12.sp, color = Clay.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }

        ClayCard {
            Text("ইনকামিং কল", fontWeight = FontWeight.Bold, color = Clay.text)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "কল এলে বুশরা জিজ্ঞেস করবে (ধরব, নাকি মেসেজ?)",
                    modifier = Modifier.weight(1f), color = Clay.text, fontSize = 14.sp
                )
                ClaySwitch(incoming) { incoming = it; prefs.incomingCallEnabled = it }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ধরার পর বুশরা নিজে কলারের সাথে কথা বলবে (পরীক্ষামূলক)",
                    modifier = Modifier.weight(1f), color = Clay.text, fontSize = 14.sp
                )
                ClaySwitch(aiTalk) { aiTalk = it; prefs.aiCallTalk = it }
            }
            ClayField(
                busySms, { busySms = it; prefs.busySms = it }, "ব্যস্ত থাকলে যে SMS যাবে",
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        ClayCard {
            Text("বুশরা যা মনে রেখেছে", fontWeight = FontWeight.Bold, color = Clay.text)
            if (memory.isEmpty()) {
                Text("এখনও কিছু নেই। বলো, \"মনে রাখো ...\"", fontSize = 13.sp, color = Clay.muted,
                    modifier = Modifier.padding(top = 6.dp))
            }
            for ((k, v) in memory) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$k: $v", modifier = Modifier.weight(1f), color = Clay.text, fontSize = 14.sp)
                    ClayButton(
                        text = "মোছো",
                        onClick = {
                            prefs.removeMemory(k)
                            memory = prefs.memoryMap()
                        },
                        color = Clay.warn
                    )
                }
            }
        }
    }
}
