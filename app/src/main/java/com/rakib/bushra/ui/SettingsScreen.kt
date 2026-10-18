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
import com.rakib.bushra.core.BushraLog
import com.rakib.bushra.core.BushraState
import com.rakib.bushra.core.Prefs
import com.rakib.bushra.speech.EdgeTts
import com.rakib.bushra.speech.Emotion

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), color = Clay.text, fontSize = 14.sp)
        ClaySwitch(value, onChange)
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = BushraCore.prefs
    val ttsStatus by BushraState.ttsStatus.collectAsState()
    val log by BushraLog.lines.collectAsState()

    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(prefs.models) }
    var testResult by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(prefs.language) }
    var wake by remember { mutableStateOf(prefs.wakeWords) }
    var userName by remember { mutableStateOf(prefs.userName) }
    var romantic by remember { mutableStateOf(prefs.romantic) }
    var female by remember { mutableStateOf(prefs.voiceFemale) }
    var engine by remember { mutableStateOf(prefs.ttsEngine) }
    var edgeVoice by remember { mutableStateOf(prefs.edgeVoice) }
    var always by remember { mutableStateOf(prefs.alwaysCommand) }
    var music by remember { mutableStateOf(prefs.musicMode) }
    var bubble by remember { mutableStateOf(prefs.bubble) }
    var callMode by remember { mutableStateOf(prefs.callMode) }
    var busySms by remember { mutableStateOf(prefs.busySms) }
    var callerInfo by remember { mutableStateOf(prefs.callerInfo) }
    var memory by remember { mutableStateOf(prefs.memoryMap()) }
    var aliases by remember { mutableStateOf(prefs.aliasMap()) }
    var nick by remember { mutableStateOf("") }
    var real by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }
    val calls = remember { prefs.callLog() }

    fun saveAll() {
        prefs.apiKey = apiKey
        prefs.models = models
        prefs.language = language
        prefs.wakeWords = wake
        prefs.userName = userName
        prefs.romantic = romantic
        prefs.voiceFemale = female
        prefs.ttsEngine = engine
        prefs.edgeVoice = edgeVoice
        prefs.alwaysCommand = always
        prefs.musicMode = music
        prefs.bubble = bubble
        prefs.callMode = callMode
        prefs.busySms = busySms
        prefs.callerInfo = callerInfo
        BushraCore.applyVoice()
        saved = "✔ সেভ হয়েছে। (ভাসমান মাইক বদলালে বুশরা বন্ধ করে আবার চালু করো)"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("সেটিংস", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Clay.text)

        ClayButton(
            text = "সব সেভ করো",
            onClick = { saveAll() },
            modifier = Modifier.fillMaxWidth()
        )
        if (saved.isNotBlank()) Text(saved, color = Clay.ok, fontSize = 13.sp)

        ClayCard {
            Text("Gemini (এআই)", fontWeight = FontWeight.Bold, color = Clay.text)
            Text(
                "ফ্রি API key নিতে নিচের বাটন চাপো, Google-এ লগইন করে ‘Create API key’ দাও, তারপর key কপি করে এখানে বসাও।",
                fontSize = 13.sp, color = Clay.muted, modifier = Modifier.padding(vertical = 6.dp)
            )
            ClayButton(
                text = "ফ্রি API key নাও",
                onClick = { Perms.openUrl(ctx, "https://aistudio.google.com/apikey") },
                color = Clay.accent2,
                modifier = Modifier.fillMaxWidth()
            )
            ClayField(
                apiKey, { apiKey = it }, "API key",
                password = !showKey, modifier = Modifier.padding(top = 10.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ClayButton(
                    text = if (showKey) "লুকাও" else "দেখাও",
                    onClick = { showKey = !showKey },
                    color = Clay.accent2,
                    modifier = Modifier.weight(1f)
                )
                ClayButton(
                    text = "পরীক্ষা করো",
                    onClick = {
                        saveAll()
                        testResult = "পরীক্ষা চলছে…"
                        BushraCore.testApi { testResult = it }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            if (testResult.isNotBlank()) {
                Text(testResult, fontSize = 13.sp, color = Clay.text, modifier = Modifier.padding(top = 8.dp))
            }
            ClayField(
                models, { models = it }, "মডেলের তালিকা (কমা দিয়ে; একটা না চললে পরেরটা)",
                modifier = Modifier.padding(top = 10.dp)
            )
            ClayButton(
                text = "মডেল তালিকা আগের অবস্থায় আনো",
                onClick = { models = Prefs.DEFAULT_MODELS },
                color = Clay.accent2,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        ClayCard {
            Text("বুশরার স্বভাব", fontWeight = FontWeight.Bold, color = Clay.text)
            ClayField(
                userName, { userName = it }, "তোমার নাম",
                modifier = Modifier.padding(top = 8.dp)
            )
            SwitchRow(
                if (romantic) "প্রেমিকার মতো আদুরে কথা (চালু)" else "শুধু বন্ধুর মতো কথা",
                romantic
            ) { romantic = it }
        }

        ClayCard {
            Text("কণ্ঠ", fontWeight = FontWeight.Bold, color = Clay.text)
            SwitchRow(
                if (engine == "edge") "পরিষ্কার নিউরাল বাংলা কণ্ঠ (Edge, ফ্রি, ইন্টারনেট লাগে)"
                else "ফোনের নিজের কণ্ঠ",
                engine == "edge"
            ) { engine = if (it) "edge" else "system" }
            Text(
                "বর্তমান কণ্ঠ: ${EdgeTts.displayName(edgeVoice)}",
                fontSize = 13.sp, color = Clay.muted, modifier = Modifier.padding(top = 10.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ClayButton(
                    text = "পরের কণ্ঠ",
                    onClick = {
                        val v = EdgeTts.voices
                        val i = v.indexOfFirst { it.first == edgeVoice }
                        edgeVoice = v[(i + 1) % v.size].first
                        prefs.edgeVoice = edgeVoice
                        prefs.ttsEngine = engine
                        BushraCore.speak("আমি বুশরা। এই কণ্ঠটা কেমন লাগছে?", Emotion.HAPPY)
                    },
                    color = Clay.accent2,
                    modifier = Modifier.weight(1f)
                )
                ClayButton(
                    text = "শুনে দেখো",
                    onClick = {
                        prefs.edgeVoice = edgeVoice
                        prefs.ttsEngine = engine
                        BushraCore.speak("ইশ, তুমি এত মিষ্টি করে বললে আমার লজ্জা লাগছে!", Emotion.SHY)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            SwitchRow(if (female) "ফোনের কণ্ঠ: মেয়েলি" else "ফোনের কণ্ঠ: ছেলেদের", female) {
                female = it
                prefs.voiceFemale = it
            }
            ClayButton(
                text = "ফোনের ভয়েস ডেটা নামাও",
                onClick = { Perms.openTtsSettings(ctx) },
                color = Clay.accent2,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            if (ttsStatus.isNotBlank()) {
                Text(ttsStatus, fontSize = 12.sp, color = Clay.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }

        ClayCard {
            Text("শোনা", fontWeight = FontWeight.Bold, color = Clay.text)
            ClayField(
                language, { language = it }, "ভাষা কোড (bn-BD, en-US ...)",
                modifier = Modifier.padding(top = 8.dp)
            )
            ClayField(
                wake, { wake = it }, "ডাকনাম (কমা দিয়ে আলাদা)",
                modifier = Modifier.padding(top = 10.dp)
            )
            SwitchRow("গান চলার সময় ক্রমাগত শোনা বন্ধ (গান থামা ঠেকায়; ভাসমান মাইক/‘শোনো’ বাটন ব্যবহার করো)", music) { music = it }
            SwitchRow("স্ক্রিনে ভাসমান মাইক বাটন", bubble) { bubble = it }
            SwitchRow("‘বুশরা’ না বললেও যা শুনবে সবই কমান্ড", always) { always = it }
        }

        ClayCard {
            Text("ইনকামিং কল", fontWeight = FontWeight.Bold, color = Clay.text)
            val modeLabel = when (callMode) {
                "auto" -> "বুশরা নিজে কল ধরে কথা বলবে (পরীক্ষামূলক)"
                "ask" -> "কল এলে জিজ্ঞেস করবে: ধরব, নাকি মেসেজ?"
                else -> "কল নিয়ে কিছু করবে না"
            }
            Text(modeLabel, color = Clay.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            ClayButton(
                text = "মোড বদলাও",
                onClick = { callMode = when (callMode) { "ask" -> "auto"; "auto" -> "off"; else -> "ask" } },
                color = Clay.accent2,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            ClayField(
                busySms, { busySms = it }, "ব্যস্ত থাকলে যে SMS যাবে",
                modifier = Modifier.padding(top = 10.dp)
            )
            ClayField(
                callerInfo, { callerInfo = it }, "কলারকে যা জানাবে (যেমন: কোথায় থাকি, কখন ফ্রি থাকি)",
                singleLine = false, modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                "সতর্কতা: কিছু ফোনে Android কল চলাকালে মাইক বন্ধ রাখে, তখন কলারের কথা শোনা যায় না।",
                fontSize = 12.sp, color = Clay.muted, modifier = Modifier.padding(top = 8.dp)
            )
        }

        ClayCard {
            Text("কন্টাক্টের ডাকনাম", fontWeight = FontWeight.Bold, color = Clay.text)
            Text(
                "যেমন ‘আম্মু’ = ফোনে সেভ করা আসল নাম বা নম্বর। বুশরা তখন ঠিক মানুষটাকে চিনবে।",
                fontSize = 13.sp, color = Clay.muted, modifier = Modifier.padding(vertical = 6.dp)
            )
            for ((k, v) in aliases) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$k → $v", modifier = Modifier.weight(1f), color = Clay.text, fontSize = 14.sp)
                    ClayButton(
                        text = "মোছো",
                        onClick = { prefs.removeAlias(k); aliases = prefs.aliasMap() },
                        color = Clay.warn
                    )
                }
            }
            ClayField(nick, { nick = it }, "ডাকনাম", modifier = Modifier.padding(top = 10.dp))
            ClayField(real, { real = it }, "আসল নাম বা নম্বর", modifier = Modifier.padding(top = 8.dp))
            ClayButton(
                text = "ডাকনাম যোগ করো",
                onClick = {
                    if (nick.isNotBlank() && real.isNotBlank()) {
                        prefs.putAlias(nick, real)
                        aliases = prefs.aliasMap()
                        nick = ""
                        real = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
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
                        onClick = { prefs.removeMemory(k); memory = prefs.memoryMap() },
                        color = Clay.warn
                    )
                }
            }
        }

        if (calls.isNotEmpty()) {
            ClayCard {
                Text("সাম্প্রতিক কলের সারাংশ", fontWeight = FontWeight.Bold, color = Clay.text)
                for (c in calls) {
                    Text(c, fontSize = 13.sp, color = Clay.text, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        ClayCard {
            Text("সমস্যার লগ", fontWeight = FontWeight.Bold, color = Clay.text)
            if (log.isEmpty()) {
                Text("কোনো সমস্যা নেই।", fontSize = 13.sp, color = Clay.muted, modifier = Modifier.padding(top = 6.dp))
            }
            for (l in log.takeLast(15).reversed()) {
                Text(l, fontSize = 12.sp, color = Clay.muted, modifier = Modifier.padding(top = 4.dp))
            }
            ClayButton(
                text = "লগ মোছো",
                onClick = { BushraLog.clear() },
                color = Clay.accent2,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}
