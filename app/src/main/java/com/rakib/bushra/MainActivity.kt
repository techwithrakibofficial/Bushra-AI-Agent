package com.rakib.bushra

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakib.bushra.core.BushraCore
import com.rakib.bushra.core.BushraState
import com.rakib.bushra.core.LocaleHelper
import com.rakib.bushra.service.BushraService
import com.rakib.bushra.ui.BushraTheme
import com.rakib.bushra.ui.Clay
import com.rakib.bushra.ui.HomeScreen
import com.rakib.bushra.ui.PermissionsScreen
import com.rakib.bushra.ui.Perms
import com.rakib.bushra.ui.SettingsScreen
import com.rakib.bushra.ui.clay

class MainActivity : ComponentActivity() {

    private var pendingStart = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        BushraState.resumeTick.value = BushraState.resumeTick.value + 1
        if (pendingStart && result[Manifest.permission.RECORD_AUDIO] == true) {
            pendingStart = false
            BushraService.start(this)
        }
    }

    /** অ্যাপের ভাষা (English ডিফল্ট / বাংলা) পুরো Activity-তে প্রয়োগ হয় */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BushraCore.init(this)
        setContent {
            BushraTheme {
                BushraRoot(
                    requestPerms = { permLauncher.launch(it) },
                    toggleService = { toggleService() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        BushraState.resumeTick.value = BushraState.resumeTick.value + 1
    }

    private fun toggleService() {
        if (BushraState.serviceRunning.value) {
            BushraService.stop(this)
            return
        }
        if (!Perms.has(this, Manifest.permission.RECORD_AUDIO)) {
            pendingStart = true
            permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        BushraService.start(this)
    }
}

@Composable
private fun BushraRoot(requestPerms: (Array<String>) -> Unit, toggleService: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Clay.bg, Clay.bgMid, Clay.bg)))
    ) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> HomeScreen(
                    onToggleService = toggleService,
                    onOpenSettings = { tab = 2 },
                    onOpenPermissions = { tab = 1 }
                )
                1 -> PermissionsScreen(requestPerms)
                else -> SettingsScreen()
            }
        }
        BottomNav(tab) { tab = it }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        "🏠" to stringResource(R.string.nav_home),
        "🛡" to stringResource(R.string.nav_permissions),
        "⚙" to stringResource(R.string.nav_settings)
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clay(30.dp, Clay.surface, 8.dp)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for ((i, item) in items.withIndex()) {
            val on = selected == i
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (on) Clay.accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(item.first, fontSize = 20.sp)
                Text(
                    item.second,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) Clay.accent else Clay.muted
                )
            }
        }
    }
}
