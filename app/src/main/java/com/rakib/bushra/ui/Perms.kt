package com.rakib.bushra.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.rakib.bushra.service.BushraAccessibilityService

/** পারমিশনের অবস্থা যাচাই ও সেটিংস খোলার সাহায্যকারী */
object Perms {

    val runtime: Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.ANSWER_PHONE_CALLS)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun has(ctx: Context, p: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun allRuntimeGranted(ctx: Context): Boolean = runtime.all { has(ctx, it) }

    fun accessibilityEnabled(ctx: Context): Boolean {
        if (BushraAccessibilityService.instance != null) return true
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(ctx, BushraAccessibilityService::class.java)
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
        }
    }

    fun canWriteSettings(ctx: Context): Boolean = Settings.System.canWrite(ctx)

    fun canOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun ignoringBattery(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun open(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun pkgUri(ctx: Context): Uri = Uri.parse("package:${ctx.packageName}")

    fun openAccessibility(ctx: Context) = open(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openWriteSettings(ctx: Context) =
        open(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri(ctx)))

    fun openOverlay(ctx: Context) =
        open(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri(ctx)))

    fun openBattery(ctx: Context) =
        open(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(ctx)))

    fun openAppInfo(ctx: Context) =
        open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx)))

    fun openTtsSettings(ctx: Context) = open(ctx, Intent("com.android.settings.TTS_SETTINGS"))
}
