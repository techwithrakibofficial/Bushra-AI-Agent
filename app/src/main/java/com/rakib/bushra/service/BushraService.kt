package com.rakib.bushra.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.rakib.bushra.MainActivity
import com.rakib.bushra.core.BushraCore
import com.rakib.bushra.core.BushraState
import com.rakib.bushra.core.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * সবসময় চালু থাকা ভয়েস সার্ভিস: "বুশরা" ডাক শোনে, কমান্ড ধরে BushraCore-কে দেয়,
 * আর ইনকামিং কল সামলায়।
 */
class BushraService : Service() {

    companion object {
        @Volatile
        var instance: BushraService? = null
        const val ACTION_STOP = "com.rakib.bushra.STOP"
        private const val CHANNEL_ID = "bushra_service"
        private const val NOTIF_ID = 1001

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, BushraService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, BushraService::class.java))
        }
    }

    private enum class CallMode { NONE, RINGING_PROMPT, IN_CALL }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var running = false
    private var commandUntil = 0L

    private var callMode = CallMode.NONE
    private var ringingNumber: String? = null
    private var announced = false

    private val listenRunnable = Runnable { startListening() }

    // ------------------------------------------------------------------ জীবনচক্র

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_STICKY

        BushraCore.init(this)
        try {
            goForeground()
        } catch (e: Exception) {
            BushraState.lastHeard.value = "সার্ভিস চালু করা যায়নি: ${e.message}"
            stopSelf()
            return START_NOT_STICKY
        }

        running = true
        instance = this
        BushraState.serviceRunning.value = true
        BushraState.phase.value = Phase.IDLE

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bushra:listen").apply {
            acquire(12L * 60 * 60 * 1000)
        }

        BushraCore.onSpeakStarted = { handler.post { stopListening() } }
        BushraCore.onSpeakFinished = {
            handler.post {
                commandUntil = SystemClock.elapsedRealtime() + 7000
                scheduleListen(250)
            }
        }
        scheduleListen(500)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        instance = null
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        stopListening()
        BushraCore.onSpeakStarted = null
        BushraCore.onSpeakFinished = null
        BushraCore.stopSpeaking()
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        BushraState.serviceRunning.value = false
        BushraState.phase.value = Phase.IDLE
        super.onDestroy()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Bushra সেবা", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, BushraService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("বুশরা শুনছে")
            .setContentText("\"বুশরা\" বলে ডাকুন")
            .setContentIntent(open)
            .addAction(0, "বন্ধ করো", stop)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    // ------------------------------------------------------------------ শোনা

    private fun scheduleListen(delayMs: Long) {
        handler.removeCallbacks(listenRunnable)
        handler.postDelayed(listenRunnable, delayMs)
    }

    private fun stopListening() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    private fun startListening() {
        if (!running) return
        if (BushraCore.isSpeaking()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            BushraState.lastHeard.value = "মাইক্রোফোনের পারমিশন নেই"
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            BushraState.lastHeard.value = "এই ফোনে স্পিচ রিকগনিশন পাওয়া যায়নি"
            return
        }
        try {
            stopListening()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            r.setRecognitionListener(listener)
            recognizer = r
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, BushraCore.prefs.language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            r.startListening(i)
        } catch (e: Exception) {
            scheduleListen(1500)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (BushraState.phase.value == Phase.IDLE) BushraState.phase.value = Phase.LISTENING
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            BushraState.level.value = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            if (BushraState.phase.value == Phase.LISTENING) BushraState.phase.value = Phase.IDLE
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    BushraState.lastHeard.value = "মাইক্রোফোনের পারমিশন নেই"
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleListen(1500)
                SpeechRecognizer.ERROR_CLIENT -> scheduleListen(700)
                else -> scheduleListen(300)
            }
        }

        override fun onResults(results: Bundle?) {
            if (BushraState.phase.value == Phase.LISTENING) BushraState.phase.value = Phase.IDLE
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
            onHeard(list)
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ------------------------------------------------------------------ শোনা কথা বোঝা

    private fun onHeard(alts: List<String>) {
        val text = alts.firstOrNull()?.trim().orEmpty()
        if (text.isEmpty()) {
            scheduleListen(200)
            return
        }
        BushraState.lastHeard.value = text

        if (BushraCore.isBusy()) {
            scheduleListen(300)
            return
        }

        if (callMode == CallMode.RINGING_PROMPT) {
            handleRingingCommand(text)
            return
        }

        if (callMode == CallMode.IN_CALL && BushraCore.prefs.aiCallTalk) {
            BushraCore.ask(text, callerMode = true)
            return
        }

        val now = SystemClock.elapsedRealtime()
        val rest = findWake(alts)
        when {
            rest != null && rest.isBlank() -> {
                commandUntil = now + 8000
                BushraCore.speak("জি, বলুন।")
            }
            rest != null -> {
                commandUntil = now + 9000
                BushraCore.ask(rest)
            }
            now < commandUntil -> {
                commandUntil = now + 9000
                BushraCore.ask(text)
            }
            else -> scheduleListen(150)
        }
    }

    /** ডাকনাম পেলে তার পরের অংশ ফেরত দেয় (ফাঁকা হতে পারে); না পেলে null */
    private fun findWake(alts: List<String>): String? {
        val words = BushraCore.prefs.wakeWords.split(",")
            .map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        for (alt in alts) {
            val low = alt.lowercase()
            for (w in words) {
                val idx = low.indexOf(w)
                if (idx >= 0) {
                    return alt.substring(idx + w.length).trim(' ', ',', '।', '!', '?', '.')
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------ ইনকামিং কল

    fun onCallState(state: String, number: String?) {
        handler.post {
            if (!running) return@post
            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    if (!BushraCore.prefs.incomingCallEnabled) return@post
                    if (number != null) ringingNumber = number
                    if (callMode != CallMode.RINGING_PROMPT) {
                        callMode = CallMode.RINGING_PROMPT
                        announced = false
                        scope.launch {
                            delay(if (number == null) 1200L else 200L)
                            announceCall()
                        }
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    callMode = CallMode.IN_CALL
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    callMode = CallMode.NONE
                    ringingNumber = null
                    announced = false
                    try {
                        BushraCore.executor.setSpeakerRaw(false)
                    } catch (_: Exception) {
                    }
                    scheduleListen(500)
                }
            }
        }
    }

    private fun announceCall() {
        if (announced || callMode != CallMode.RINGING_PROMPT) return
        announced = true
        val who = BushraCore.executor.nameForNumber(ringingNumber) ?: ringingNumber ?: "অচেনা নম্বর"
        BushraCore.speak("$who ফোন করছে। ধরব, নাকি মেসেজ পাঠিয়ে দেব?")
    }

    private fun handleRingingCommand(text: String) {
        val t = text.lowercase()
        val accept = listOf("ধর", "রিসিভ", "receive", "answer", "উঠা", "স্পিকার", "speaker", "হ্যাঁ", "accept")
        val busy = listOf("মেসেজ", "message", "sms", "ব্যস্ত", "busy", "কেটে", "রিজেক্ট", "reject", "না")
        when {
            accept.any { t.contains(it) } -> {
                callMode = CallMode.IN_CALL
                scope.launch {
                    BushraCore.executor.answerCall()
                    delay(800)
                    BushraCore.executor.setSpeakerRaw(true)
                    scheduleListen(500)
                }
            }
            busy.any { t.contains(it) } -> {
                val num = ringingNumber
                scope.launch {
                    BushraCore.executor.endCall()
                    callMode = CallMode.NONE
                    if (num != null) {
                        try {
                            BushraCore.executor.sendSmsRaw(num, BushraCore.prefs.busySms)
                            BushraCore.speak("মেসেজ পাঠিয়ে দিয়েছি।")
                        } catch (e: Exception) {
                            BushraCore.speak("মেসেজ পাঠাতে পারলাম না।")
                        }
                    } else {
                        BushraCore.speak("কল কেটে দিয়েছি, কিন্তু নম্বর পাইনি তাই মেসেজ পাঠাতে পারলাম না।")
                    }
                }
            }
            else -> scheduleListen(250)
        }
    }
}
