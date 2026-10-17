package com.rakib.bushra

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rakib.bushra.core.BushraCore
import com.rakib.bushra.core.BushraState
import com.rakib.bushra.service.BushraService
import com.rakib.bushra.ui.BushraTheme
import com.rakib.bushra.ui.Clay
import com.rakib.bushra.ui.ClayButton
import com.rakib.bushra.ui.HomeScreen
import com.rakib.bushra.ui.PermissionsScreen
import com.rakib.bushra.ui.Perms
import com.rakib.bushra.ui.SettingsScreen

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
            .background(Clay.bg)
    ) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> HomeScreen(toggleService)
                1 -> PermissionsScreen(requestPerms)
                else -> SettingsScreen()
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val names = listOf("হোম", "পারমিশন", "সেটিংস")
            for ((i, n) in names.withIndex()) {
                ClayButton(
                    text = n,
                    onClick = { tab = i },
                    color = if (tab == i) Clay.accent else Clay.surface,
                    textColor = if (tab == i) Color.White else Clay.text,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
