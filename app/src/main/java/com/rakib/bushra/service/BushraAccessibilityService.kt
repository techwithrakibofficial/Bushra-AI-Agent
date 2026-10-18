package com.rakib.bushra.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/** অ্যাপের ভেতরে স্ক্রিন পড়া, ট্যাপ, লেখা, স্ক্রল ও সিস্টেম বাটন চাপার সেবা */
class BushraAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BushraAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    // ------------------------------------------------------------------ পড়া

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "স্ক্রিন পড়া যায়নি (কোনো উইন্ডো পাইনি)"
        val sb = StringBuilder()
        sb.append("অ্যাপ: ").append(root.packageName).append('\n')
        val counter = intArrayOf(0)
        walk(root) { n ->
            if (counter[0] >= 100) return@walk
            val t = n.text?.toString()?.trim().orEmpty()
            val d = n.contentDescription?.toString()?.trim().orEmpty()
            val label = if (t.isNotEmpty()) t else d
            if (label.isEmpty() && !n.isEditable) return@walk
            val r = Rect()
            n.getBoundsInScreen(r)
            sb.append("- \"").append(label.take(80)).append("\"")
            if (n.isClickable) sb.append(" [ক্লিক]")
            if (n.isEditable) sb.append(" [লেখার ঘর]")
            if (n.isScrollable) sb.append(" [স্ক্রল]")
            sb.append(" @(").append(r.centerX()).append(',').append(r.centerY()).append(")\n")
            counter[0]++
        }
        return sb.toString()
    }

    private fun walk(node: AccessibilityNodeInfo?, visit: (AccessibilityNodeInfo) -> Unit) {
        if (node == null) return
        visit(node)
        for (i in 0 until node.childCount) {
            walk(node.getChild(i), visit)
        }
    }

    // ------------------------------------------------------------------ ট্যাপ

    fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNullOrEmpty()) return false
        for (n in nodes) {
            if (clickNode(n)) return true
        }
        return false
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        while (cur != null) {
            if (cur.isClickable && cur.isEnabled) {
                return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            cur = cur.parent
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.width() > 0 && r.height() > 0) return tap(r.centerX().toFloat(), r.centerY().toFloat())
        return false
    }

    /** WhatsApp-এর "পাঠান" বাটন খুঁজে চাপে */
    fun clickSend(pkg: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val byId = root.findAccessibilityNodeInfosByViewId("$pkg:id/send")
        if (!byId.isNullOrEmpty()) {
            for (n in byId) if (clickNode(n)) return true
        }
        var done = false
        walk(root) { n ->
            if (done) return@walk
            val d = n.contentDescription?.toString()?.trim().orEmpty()
            if (d.equals("Send", true) || d == "পাঠান" || d == "পাঠাও") {
                if (clickNode(n)) done = true
            }
        }
        return done
    }

    /** কল আইকনের মতো ছবি-বাটন, যার শুধু content description আছে */
    fun clickByDescription(vararg descs: String): Boolean {
        val root = rootInActiveWindow ?: return false
        var done = false
        walk(root) { n ->
            if (done) return@walk
            val d = n.contentDescription?.toString()?.trim().orEmpty()
            if (d.isNotEmpty() && descs.any { d.equals(it, true) }) {
                if (clickNode(n)) done = true
            }
        }
        return done
    }

    fun tap(x: Float, y: Float): Boolean {
        val p = Path()
        p.moveTo(x, y)
        return gesture(p, 60)
    }

    fun swipe(direction: String): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val p = Path()
        when (direction) {
            "up" -> { p.moveTo(w / 2, h * 0.75f); p.lineTo(w / 2, h * 0.25f) }
            "down" -> { p.moveTo(w / 2, h * 0.25f); p.lineTo(w / 2, h * 0.75f) }
            "left" -> { p.moveTo(w * 0.8f, h / 2); p.lineTo(w * 0.2f, h / 2) }
            "right" -> { p.moveTo(w * 0.2f, h / 2); p.lineTo(w * 0.8f, h / 2) }
            else -> return false
        }
        return gesture(p, 350)
    }

    private fun gesture(path: Path, durationMs: Long): Boolean {
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(g, null, null)
    }

    // ------------------------------------------------------------------ লেখা

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        var target: AccessibilityNodeInfo? = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (target == null || !target.isEditable) {
            var found: AccessibilityNodeInfo? = null
            walk(root) { n -> if (found == null && n.isEditable) found = n }
            target = found
        }
        if (target == null) return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ------------------------------------------------------------------ স্ক্রিনশট (ছবি দেখে বোঝার জন্য)

    /** স্ক্রিনের JPEG (base64) ফেরত দেয়; Android 11+ লাগে। ব্যর্থ হলে null */
    suspend fun screenshotBase64(): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return suspendCancellableCoroutine { cont ->
            try {
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            try {
                                val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                                val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                                result.hardwareBuffer.close()
                                if (soft == null) {
                                    if (cont.isActive) cont.resume(null)
                                    return
                                }
                                val scale = 720f / maxOf(soft.width, soft.height).toFloat()
                                val small = if (scale < 1f)
                                    Bitmap.createScaledBitmap(
                                        soft, (soft.width * scale).toInt(), (soft.height * scale).toInt(), true
                                    ) else soft
                                val out = ByteArrayOutputStream()
                                small.compress(Bitmap.CompressFormat.JPEG, 70, out)
                                if (cont.isActive) cont.resume(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resume(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    // ------------------------------------------------------------------ সিস্টেম বাটন

    fun global(key: String): Boolean {
        val action = when (key) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "power_menu" -> GLOBAL_ACTION_POWER_DIALOG
            "lock" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_LOCK_SCREEN else return false
            "screenshot" -> if (Build.VERSION.SDK_INT >= 28) GLOBAL_ACTION_TAKE_SCREENSHOT else return false
            else -> return false
        }
        return performGlobalAction(action)
    }
}
