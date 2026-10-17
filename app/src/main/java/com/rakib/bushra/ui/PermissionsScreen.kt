package com.rakib.bushra.ui

import android.os.Build
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakib.bushra.core.BushraState

private class PermItem(
    val title: String,
    val desc: String,
    val granted: Boolean,
    val action: () -> Unit
)

@Composable
fun PermissionsScreen(requestPerms: (Array<String>) -> Unit) {
    val ctx = LocalContext.current
    // অ্যাপে ফিরে এলে অবস্থা নতুন করে যাচাই হয়
    @Suppress("UNUSED_VARIABLE")
    val tick = BushraState.resumeTick.collectAsState().value

    val items = listOf(
        PermItem("ফোন ও কল", "কল করা, ধরা, কাটা, ইনকামিং কল চেনা",
            Perms.has(ctx, android.Manifest.permission.CALL_PHONE) &&
                Perms.has(ctx, android.Manifest.permission.ANSWER_PHONE_CALLS) &&
                Perms.has(ctx, android.Manifest.permission.READ_PHONE_STATE) &&
                Perms.has(ctx, android.Manifest.permission.READ_CALL_LOG)
        ) {
            requestPerms(arrayOf(
                android.Manifest.permission.CALL_PHONE,
                android.Manifest.permission.ANSWER_PHONE_CALLS,
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.READ_CALL_LOG
            ))
        },
        PermItem("মাইক্রোফোন", "\"বুশরা\" ডাক ও কমান্ড শোনা",
            Perms.has(ctx, android.Manifest.permission.RECORD_AUDIO)
        ) { requestPerms(arrayOf(android.Manifest.permission.RECORD_AUDIO)) },
        PermItem("SMS", "মেসেজ পাঠানো ও পড়া",
            Perms.has(ctx, android.Manifest.permission.SEND_SMS) &&
                Perms.has(ctx, android.Manifest.permission.READ_SMS)
        ) {
            requestPerms(arrayOf(
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.READ_SMS
            ))
        },
        PermItem("কন্টাক্ট", "নাম বললে নম্বর খুঁজে পাওয়া",
            Perms.has(ctx, android.Manifest.permission.READ_CONTACTS)
        ) { requestPerms(arrayOf(android.Manifest.permission.READ_CONTACTS)) },
        PermItem("নোটিফিকেশন", "চালু থাকার নোটিফিকেশন দেখানো",
            Build.VERSION.SDK_INT < 33 ||
                Perms.has(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
        ) {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPerms(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
            }
        },
        PermItem("Accessibility (অ্যাপের ভেতরে কাজ)", "ফেসবুক, WhatsApp ইত্যাদির ভেতরে ট্যাপ, লেখা, স্ক্রল",
            Perms.accessibilityEnabled(ctx)
        ) { Perms.openAccessibility(ctx) },
        PermItem("সেটিংস বদলানো", "উজ্জ্বলতা বদলানো",
            Perms.canWriteSettings(ctx)
        ) { Perms.openWriteSettings(ctx) },
        PermItem("অন্য অ্যাপের ওপরে দেখানো", "ব্যাকগ্রাউন্ড থেকে অ্যাপ খুলতে সাহায্য করে",
            Perms.canOverlay(ctx)
        ) { Perms.openOverlay(ctx) },
        PermItem("ব্যাটারি অপ্টিমাইজেশন বন্ধ", "ফোন যেন বুশরাকে ঘুম পাড়িয়ে না দেয়",
            Perms.ignoringBattery(ctx)
        ) { Perms.openBattery(ctx) }
    )
    val grantedCount = items.count { it.granted }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("পারমিশন", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Clay.text)
        Text("$grantedCount/${items.size} টি চালু আছে", color = Clay.muted)

        ClayButton(
            text = "সব সাধারণ পারমিশন একসাথে দাও",
            onClick = { requestPerms(Perms.runtime) },
            modifier = Modifier.fillMaxWidth()
        )

        for (item in items) {
            ClayCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Bold, color = Clay.text)
                        Text(item.desc, fontSize = 13.sp, color = Clay.muted)
                    }
                    if (item.granted) {
                        Text("✔ চালু", color = Clay.ok, fontWeight = FontWeight.Bold)
                    } else {
                        ClayButton(
                            text = "দাও",
                            onClick = item.action,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        ClayCard {
            Text("Accessibility চালু করা না গেলে", fontWeight = FontWeight.Bold, color = Clay.text)
            Text(
                "Android 13+ এ ইন্টারনেট থেকে নামানো অ্যাপে এটা প্রথমে আটকানো থাকে। " +
                    "নিচের বাটনে অ্যাপের তথ্য পেজ খুলবে: ওপরের ডান কোণের ⋮ মেনু চাপুন, " +
                    "\"Allow restricted settings\" দিন, তারপর আবার Accessibility থেকে বুশরাকে চালু করুন।",
                fontSize = 13.sp,
                color = Clay.muted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            ClayButton(
                text = "অ্যাপের তথ্য পেজ খোলো",
                onClick = { Perms.openAppInfo(ctx) },
                color = Clay.accent2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
