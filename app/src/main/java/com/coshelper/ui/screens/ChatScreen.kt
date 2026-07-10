package com.coshelper.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coshelper.audio.AudioRouter
import com.coshelper.chat.ChatState
import com.coshelper.chat.ChatViewModel
import com.coshelper.chat.HotspotChatManager
import com.coshelper.data.AudioSettingsRepository
import com.coshelper.ptt.PTTAccessibilityService
import com.coshelper.ui.components.AudioDevicePicker
import com.coshelper.ui.components.RoundedPttButton
import com.coshelper.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
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

    val audioRouter = remember { AudioRouter.getInstance(context) }
    val audioSettingsRepo = remember { AudioSettingsRepository(context) }
    val inputDevices = remember { audioRouter.getInputDevices() }
    var selectedInputDeviceId by remember {
        mutableStateOf(audioSettingsRepo.getInputDevice("chat"))
    }

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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("对讲") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mode segmented button: Nearby / Hotspot
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = !useHotspot,
                        onClick = { useHotspot = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Nearby")
                    }
                    SegmentedButton(
                        selected = useHotspot,
                        onClick = { useHotspot = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("热点模式")
                    }
                }

                // Status chip
                StatusChip(text = displayStatus)

                if (!permissionsGranted) {
                    Button(
                        onClick = { permissionLauncher.launch(permissions.toTypedArray()) },
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Text("授权权限", style = MaterialTheme.typography.titleMedium)
                    }
                }

                if (permissionsGranted) {
                    if (!useHotspot) {
                        // Nearby connection controls
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state == ChatState.Idle) {
                                OutlinedButton(
                                    onClick = { viewModel.startChat() },
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                                ) {
                                    Text("开始连接", style = MaterialTheme.typography.titleMedium)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.stopChat() },
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                                ) {
                                    Text("停止", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    } else {
                        // Hotspot connection controls
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { hotspotManager.startServer() },
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Text("我开热点", style = MaterialTheme.typography.titleMedium)
                            }
                            OutlinedButton(
                                onClick = { hotspotManager.startClient() },
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Text("我连接热点", style = MaterialTheme.typography.titleMedium)
                            }
                            Button(
                                onClick = {
                                    hotspotManager.setFallbackHost("10.0.2.2")
                                    hotspotManager.startClient()
                                },
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Text("连接测试对端", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }

                // Per-feature audio input device picker
                AudioDevicePicker(
                    title = "音频输入设备",
                    devices = inputDevices,
                    selectedId = selectedInputDeviceId,
                    onSelect = { deviceId ->
                        selectedInputDeviceId = deviceId
                        audioSettingsRepo.setInputDevice("chat", deviceId)
                        viewModel.setInputDevice(deviceId)
                        hotspotManager.setInputDevice(deviceId)
                    }
                )
            }

            // Rounded PTT button
            val isPtt = if (useHotspot) isHotspotPtt else state is ChatState.Sending
            RoundedPttButton(
                active = isPtt,
                onClick = {
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
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}
