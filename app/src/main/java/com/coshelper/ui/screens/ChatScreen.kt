package com.coshelper.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coshelper.chat.ChatState
import com.coshelper.chat.ChatViewModel
import com.coshelper.chat.HotspotChatManager
import com.coshelper.ptt.PTTAccessibilityService

@Composable
fun ChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val status by viewModel.statusText.collectAsState()

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(
            permissions.all {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    var useHotspot by remember { mutableStateOf(false) }
    var isHotspotPtt by remember { mutableStateOf(false) }
    val hotspotManager = remember { HotspotChatManager(context) }
    val hotspotStatus by hotspotManager.status.collectAsState()
    val displayStatus = if (useHotspot) hotspotStatus else status

    DisposableEffect(useHotspot) {
        PTTAccessibilityService.setCallbacks(
            press = {
                if (permissionsGranted) {
                    if (useHotspot) hotspotManager.startPtt() else viewModel.pressPtt()
                }
            },
            release = {
                if (useHotspot) hotspotManager.stopPtt() else viewModel.releasePtt()
            }
        )
        onDispose {
            PTTAccessibilityService.setCallbacks({}, {})
            viewModel.stopChat()
            hotspotManager.cleanup()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onBack) {
                Text("返回", fontSize = 20.sp)
            }
            Button(onClick = { useHotspot = !useHotspot }) {
                Text(if (useHotspot) "热点模式" else "Nearby模式", fontSize = 20.sp)
            }
        }

        Text(
            text = displayStatus,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (!permissionsGranted) {
            Button(onClick = { permissionLauncher.launch(permissions.toTypedArray()) }) {
                Text("授权权限", fontSize = 24.sp)
            }
        } else {
            if (state == ChatState.Idle && !useHotspot) {
                Button(onClick = { viewModel.startChat() }) {
                    Text("开始连接", fontSize = 24.sp)
                }
            }
            if (useHotspot) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { hotspotManager.startServer() }) {
                        Text("我开热点", fontSize = 18.sp)
                    }
                    Button(onClick = { hotspotManager.startClient() }) {
                        Text("我连接热点", fontSize = 18.sp)
                    }
                    Button(onClick = {
                        hotspotManager.setFallbackHost("10.0.2.2")
                        hotspotManager.startClient()
                    }) {
                        Text("连接测试对端", fontSize = 18.sp)
                    }
                }
            }
        }

        val isPtt = if (useHotspot) isHotspotPtt else state is ChatState.Sending
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(32.dp)
                .background(
                    color = if (isPtt) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
                .clickable {
                    if (permissionsGranted) {
                        if (useHotspot) {
                            if (isHotspotPtt) {
                                hotspotManager.stopPtt()
                                isHotspotPtt = false
                            } else {
                                hotspotManager.startPtt()
                                isHotspotPtt = true
                            }
                        } else {
                            if (state is ChatState.Sending) {
                                viewModel.releasePtt()
                            } else {
                                viewModel.pressPtt()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isPtt) "说话中" else "按住说话",
                fontSize = 48.sp,
                color = if (isPtt) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
