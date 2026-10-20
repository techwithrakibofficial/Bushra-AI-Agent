package com.rakib.bushra.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakib.bushra.R
import com.rakib.bushra.core.BushraCore
import com.rakib.bushra.core.BushraLog
import com.rakib.bushra.core.BushraState
import com.rakib.bushra.core.LocaleHelper
import com.rakib.bushra.core.Prefs
import com.rakib.bushra.speech.EdgeTts
import com.rakib.bushra.speech.Emotion

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 10.dp), color = Clay.text, fontSize = 14.sp)
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
    var autoLang by remember { mutableStateOf(prefs.autoLang) }
    var wake by remember { mutableStateOf(prefs.wakeWords) }
    var userName by remember { mutableStateOf(prefs.userName) }
    var romantic by remember { mutableStateOf(prefs.romantic) }
    var female by remember { mutableStateOf(prefs.voiceFemale) }
    var engine by remember { mutableStateOf(prefs.ttsEngine) }
    var edgeVoice by remember { mutableStateOf(prefs.edgeVoice) }
    var edgeVoiceEn by remember { mutableStateOf(prefs.edgeVoiceEn) }
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
    val uiLang = remember { LocaleHelper.current(ctx) }

    val savedMsg = stringResource(R.string.set_saved)
    val testingMsg = stringResource(R.string.gem_testing)
    val sampleNext = stringResource(R.string.voice_sample_next)
    val sampleTest = stringResource(R.string.voice_sample_test)

    fun saveAll() {
        prefs.apiKey = apiKey
        prefs.models = models
        prefs.language = language
        prefs.autoLang = autoLang
        prefs.wakeWords = wake
        prefs.userName = userName
        prefs.romantic = romantic
        prefs.voiceFemale = female
        prefs.ttsEngine = engine
        prefs.edgeVoice = edgeVoice
        prefs.edgeVoiceEn = edgeVoiceEn
        prefs.alwaysCommand = always
        prefs.musicMode = music
        prefs.bubble = bubble
        prefs.callMode = callMode
        prefs.busySms = busySms
        prefs.callerInfo = callerInfo
        BushraCore.applyVoice()
        saved = savedMsg
    }

    fun setUiLanguage(lang: String) {
        if (lang == uiLang) return
        saveAll()
        LocaleHelper.set(ctx, lang)
        prefs.uiLang = lang
        ctx.findActivity()?.recreate()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            stringResource(R.string.set_title),
            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Clay.text
        )

        ClayButton(
            text = stringResource(R.string.set_save_all),
            onClick = { saveAll() },
            modifier = Modifier.fillMaxWidth()
        )
        if (saved.isNotBlank()) Text(saved, color = Clay.ok, fontSize = 13.sp)

        // ---- অ্যাপের ভাষা (English ডিফল্ট, বাংলা এক ট্যাপে)
        ClayCard {
            Text(stringResource(R.string.lang_title), fontWeight = FontWeight.Bold, color = Clay.text)
            Text(
                stringResource(R.string.lang_hint),
                fontSize = 13.sp, color = Clay.muted, modifier = Modifier.padding(vertical = 6.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClayButton(
                    text = stringResource(R.string.lang_en),
                    onClick = { setUiLanguage(LocaleHelper.EN) },
                    color = if (uiLang == LocaleHelper.EN) Clay.accent else Clay.surface,
                    textColor = if (uiLang == LocaleHelper.EN) Clay.onAccent else Clay.text,
                    modifier = Modifier.weight(1f)
                )
                ClayButton(
                    text = stringResource(R.string.lang_bn),
                    onClick = { setUiLanguage(LocaleHelper.BN) },
                    color = if (uiLang == LocaleHelper.BN) Clay.accent else Clay.surface,
                    textColor = if (uiLang == LocaleHelper.BN) Clay.onAccent else Clay.text,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        ClayCard {
            Text(stringResource(R.string.gem_title), fontWeight = FontWeight.Bold, color = Clay.text)
            Text(
                stringResource(R.string.gem_help),
                fontSize = 13.sp, color = Clay.muted, modifier = Modifier.padding(vertical = 6.dp)
            )
            ClayButton(
                text = stringResource(R.string.gem_get_key),
                onClick = { Perms.openUrl(ctx, "https://aistudio.google.com/apikey") },
                color = Clay.accent2,
                modifier = Modifier.fillMaxWidth()
            )
            ClayField(
                apiKey, { apiKey = it }, stringResource(R.string.gem_key_label),
                password = !showKey, modifier = Modifier.padding(top = 10.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ClayButton(
                    text = if (showKey) stringResource(R.string.gem_hide) else stringResource(R.string.gem_show),
                    onClick = { showKey = !showKey },
                    color = Clay.accent2,
                    modifier = Modifier.weight(1f)
                )
                ClayButton(
                    text = stringResource(R.string.gem_test),
                    onClick = {
                        saveAll()
                        testResult = testingMsg
                        BushraCore.testApi { testResult = it }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            if (testResult.isNotBlank()) {
                Text(testResult, fontSize = 13.sp, color = Clay.text, modifier = Modifier.padding(top = 8.dp))
            }
            ClayField(
                models, { models = it }, stringResource(R.string.gem_models_label),
                modifier = Modifier.padding(top = 10.dp)
            )
            ClayButton(
                text = stringResource(R.string.gem_models_reset),
                onClick = { models = Prefs.DEFAULT_MODELS },
                color = Clay.accent2,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        ClayCard {
            Text(stringResource(R.string.persona_title), fontWeight = FontWeight.Bold, color = Clay.text)
            ClayField(
                userName, { userName = it }, stringResource(R.string.persona_name_label),
                modifier = Modifier.padding(top = 8.dp)
            )
            SwitchRow(
                if (romantic) stringResource(R.string.persona_romantic_on) else stringResource(R.string.persona_romantic_off),
                romantic
            ) { romantic = it }
        }

        ClayCard {
            Text(stringResource(R.string.voice_title), fontWeight = FontWeight.Bold, color = Clay.text)
            SwitchRow(
                if (engine == "edge") stringResource(R.string.voice_edge_on) else stringResource(R.string.voice_edge_off),
                engine == "edge"
            ) { engine = if (it) "edge" else "system" }

            // বাংলা কণ্ঠ
            Text(
                stringResource(R.string.voice_bn_current, EdgeTts.displayName(edgeVoice)),
                fontSize = 13.sp, color = Clay.muted, modifier = Modifier.padding(top = 10.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ClayButton(
                    text = stringResource(R.string.voice_next),
                    onClick = {
                        val v = EdgeTts.bnVoices
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
                    text = stringResource(R.string.btn_try_voice),
                    onClick = {
                        prefs.edgeVoice = edgeVoice
                        prefs.ttsEngine = engine
                        BushraCore.speak("ইশ, তুমি এত মিষ্টি করে বললে আমার লজ্জা লাগছে!", Emotion.SHY)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // ইংরেজি কণ্ঠ
            Text(
                stringResource(R.string.voice_en_current, EdgeTts.displayName(edgeVoiceEn)),
                fontSize = 13.sp, color = Clay.muted, modifier = Modifier.padding(top = 14.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ClayButton(
                    text = stringResource(R.string.voice_next),
                    onClick = {
                        val v = EdgeTts.enVoices
                        val i = v.indexOfFirst { it.first == edgeVoiceEn }
                        edgeVoiceEn = v[(i + 1) % v.size].first
                        prefs.edgeVoiceEn = edgeVoiceEn
                        prefs.ttsEngine = engine
                        BushraCore.speak(sampleNext, Emotion.HAPPY)
                    },
                    color = Clay.accent2,
                    modifier = Modifier.weight(1f)
                )
                ClayButton(
                    text = stringResource(R.string.btn_try_voice),
                    onClick = {
                        prefs.edgeVoiceEn = edgeVoiceEn
                        prefs.ttsEngine = engine
                        BushraCore.speak(sampleTest, Emotion.SHY)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            SwitchRow(
                if (female) stringResource(R.string.voice_female) else stringResource(R.string.voice_male),
                female
            ) {
                female = it
                prefs.voiceFemale = it
            }
            ClayButton(
                text = stringResource(R.string.voice_download),
                onClick = { Perms.openTtsSettings(ctx) },
                color = Clay.accent2,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            if (ttsStatus.isNotBlank()) {
                Text(ttsStatus, fontSize = 12.sp, color = Clay.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }

        ClayCard {
            Text(stringResource(R.string.listen_title), fontWeight = FontWeight.Bold, color = Clay.text)
            Text(
                stringResource(R.string.listen_lang_label),
                fontSize = 13.sp, color = Clay.muted, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val bn = language.startsWith("bn")
                ClayButton(
                    text = stringResource(R.string.listen_lang_bn),
                    onClick = { language = "bn-BD" },
                    color = if (bn) Clay.accent else Clay.surface,
                    textColor = if (bn) Clay.onAccent else Clay.text,
                    modifier = Modifier.weight(1f)
                )
                ClayButton(
                    text = stringResource(R.string.listen_lang_en),
                    onClick = { language = "en-US" },
                    color = if (!bn) Clay.accent else Clay.surface,
                    textColor = if (!bn) Clay.onAccent else Clay.text,
                    modifier = Modifier.weight(1f)
                )
            }
            SwitchRow(stringResource(R.string.listen_auto), autoLang) { autoLang = it }
            ClayField(
                wake, { wake = it }, stringResource(R.string.listen_wake_label),
                modifier = Modifier.padding(top = 10.dp)
            )
            SwitchRow(stringResource(R.string.listen_music), music) { music = it }
            SwitchRow(stringResource(R.string.listen_bubble), bubble) { bubble = it }
            SwitchRow(stringResource(R.string.listen_always), always) { always = it }
        }

        ClayCard {
            Text(stringResource(R.string.call_title), fontWeight = FontWeight.Bold, color = Clay.text)
            val modeLabel = when (callMode) {
                "auto" -> stringResource(R.string.call_mode_auto)
                "ask" -> stringResource(R.string.call_mode_ask)
                else -> stringResource(R.string.call_mode_off)
            }
            Text(modeLabel, color = Clay.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            ClayButton(
                text = stringResource(R.string.call_change_mode),
                onClick = { callMode = when (callMode) { "ask" -> "auto"; "auto" -> "off"; else -> "ask" } },
                color = Clay.accent2,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            ClayField(
                busySms, { busySms = it }, stringResource(R.string.call_busy_label),
                modifier = Modifier.padding(top = 10.dp)
            )
            ClayField(
                callerInfo, { callerInfo = it }, stringResource(R.string.call_info_label),
                singleLine = false, modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                stringResource(R.string.call_warning),
                fontSize = 12.sp, color = Clay.muted, modifier = Modifier.padding(top = 8.dp)
            )
        }

        ClayCard {
            Text(stringResource(R.string.alias_title), fontWeight = FontWeight.Bold, color = Clay.text)
            Text(
                stringResource(R.string.alias_help),
                fontSize = 13.sp, color = Clay.muted, modifier = Modifier.padding(vertical = 6.dp)
            )
            for ((k, v) in aliases) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$k → $v", modifier = Modifier.weight(1f), color = Clay.text, fontSize = 14.sp)
                    ClayButton(
                        text = stringResource(R.string.delete),
                        onClick = { prefs.removeAlias(k); aliases = prefs.aliasMap() },
                        color = Clay.warn
                    )
                }
            }
            ClayField(nick, { nick = it }, stringResource(R.string.alias_nick), modifier = Modifier.padding(top = 10.dp))
            ClayField(real, { real = it }, stringResource(R.string.alias_real), modifier = Modifier.padding(top = 8.dp))
            ClayButton(
                text = stringResource(R.string.alias_add),
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
            Text(stringResource(R.string.mem_title), fontWeight = FontWeight.Bold, color = Clay.text)
            if (memory.isEmpty()) {
                Text(
                    stringResource(R.string.mem_empty), fontSize = 13.sp, color = Clay.muted,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            for ((k, v) in memory) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$k: $v", modifier = Modifier.weight(1f), color = Clay.text, fontSize = 14.sp)
                    ClayButton(
                        text = stringResource(R.string.delete),
                        onClick = { prefs.removeMemory(k); memory = prefs.memoryMap() },
                        color = Clay.warn
                    )
                }
            }
        }

        if (calls.isNotEmpty()) {
            ClayCard {
                Text(stringResource(R.string.calls_title), fontWeight = FontWeight.Bold, color = Clay.text)
                for (c in calls) {
                    Text(c, fontSize = 13.sp, color = Clay.text, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        ClayCard {
            Text(stringResource(R.string.log_title), fontWeight = FontWeight.Bold, color = Clay.text)
            if (log.isEmpty()) {
                Text(
                    stringResource(R.string.log_empty), fontSize = 13.sp, color = Clay.muted,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            for (l in log.takeLast(15).reversed()) {
                Text(l, fontSize = 12.sp, color = Clay.muted, modifier = Modifier.padding(top = 4.dp))
            }
            ClayButton(
                text = stringResource(R.string.log_clear),
                onClick = { BushraLog.clear() },
                color = Clay.accent2,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}
