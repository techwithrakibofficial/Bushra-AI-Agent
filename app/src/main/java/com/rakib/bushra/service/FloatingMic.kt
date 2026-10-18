package com.rakib.bushra.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** স্ক্রিনে ভাসমান মাইক বাটন: চাপলে বুশরা একবার শুনে কমান্ড নেয় (গান চললেও) */
class FloatingMic(private val ctx: Context, private val onTap: () -> Unit) {

    private var view: View? = null
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun show() {
        if (view != null) return
        if (!Settings.canDrawOverlays(ctx)) return
        val size = (52 * ctx.resources.displayMetrics.density).toInt()
        val tv = TextView(ctx).apply {
            text = "🎤"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC7C6CF0"))
                setStroke(3, Color.parseColor("#FFFFFFFF"))
            }
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 400
        }
        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        var moved = false
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt()
                    val dy = (e.rawY - sy).toInt()
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) moved = true
                    if (moved) {
                        lp.x = ox + dx
                        lp.y = oy + dy
                        try { wm.updateViewLayout(tv, lp) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap()
            }
            true
        }
        try {
            wm.addView(tv, lp)
            view = tv
        } catch (_: Exception) {
        }
    }

    fun hide() {
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
    }
}
