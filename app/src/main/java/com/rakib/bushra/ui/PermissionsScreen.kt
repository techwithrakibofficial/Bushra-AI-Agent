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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakib.bushra.R
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
        PermItem(stringResource(R.string.perm_phone_t), stringResource(R.string.perm_phone_d),
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
        PermItem(stringResource(R.string.perm_mic_t), stringResource(R.string.perm_mic_d),
            Perms.has(ctx, android.Manifest.permission.RECORD_AUDIO)
        ) { requestPerms(arrayOf(android.Manifest.permission.RECORD_AUDIO)) },
        PermItem(stringResource(R.string.perm_sms_t), stringResource(R.string.perm_sms_d),
            Perms.has(ctx, android.Manifest.permission.SEND_SMS) &&
                Perms.has(ctx, android.Manifest.permission.READ_SMS)
        ) {
            requestPerms(arrayOf(
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.READ_SMS
            ))
        },
        PermItem(stringResource(R.string.perm_contacts_t), stringResource(R.string.perm_contacts_d),
            Perms.has(ctx, android.Manifest.permission.READ_CONTACTS)
        ) { requestPerms(arrayOf(android.Manifest.permission.READ_CONTACTS)) },
        PermItem(stringResource(R.string.perm_notif_t), stringResource(R.string.perm_notif_d),
            Build.VERSION.SDK_INT < 33 ||
                Perms.has(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
        ) {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPerms(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
            }
        },
        PermItem(stringResource(R.string.perm_access_t), stringResource(R.string.perm_access_d),
            Perms.accessibilityEnabled(ctx)
        ) { Perms.openAccessibility(ctx) },
        PermItem(stringResource(R.string.perm_notiflisten_t), stringResource(R.string.perm_notiflisten_d),
            Perms.notificationListenerEnabled(ctx)
        ) { Perms.openNotificationListener(ctx) },
        PermItem(stringResource(R.string.perm_write_t), stringResource(R.string.perm_write_d),
            Perms.canWriteSettings(ctx)
        ) { Perms.openWriteSettings(ctx) },
        PermItem(stringResource(R.string.perm_overlay_t), stringResource(R.string.perm_overlay_d),
            Perms.canOverlay(ctx)
        ) { Perms.openOverlay(ctx) },
        PermItem(stringResource(R.string.perm_battery_t), stringResource(R.string.perm_battery_d),
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
        Text(stringResource(R.string.perm_title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Clay.text)
        Text(stringResource(R.string.perm_count, grantedCount, items.size), color = Clay.muted)

        ClayButton(
            text = stringResource(R.string.perm_grant_all),
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
                        Text(stringResource(R.string.perm_on), color = Clay.ok, fontWeight = FontWeight.Bold)
                    } else {
                        ClayButton(
                            text = stringResource(R.string.perm_give),
                            onClick = item.action,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        ClayCard {
            Text(stringResource(R.string.perm_restricted_t), fontWeight = FontWeight.Bold, color = Clay.text)
            Text(
                stringResource(R.string.perm_restricted_b),
                fontSize = 13.sp,
                color = Clay.muted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            ClayButton(
                text = stringResource(R.string.perm_restricted_btn),
                onClick = { Perms.openAppInfo(ctx) },
                color = Clay.accent2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
