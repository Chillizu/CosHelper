package com.coshelper.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.coshelper.audio.AudioRouter
import com.coshelper.data.AudioSettingsRepository
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.coshelper.stt.SttManager
import com.coshelper.ui.components.AudioDevicePicker
import com.coshelper.ui.components.BottomActionBar
import com.coshelper.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SttScreen() {
    val context = LocalContext.current
    val manager = remember { SttManager(context) }
    val audioRouter = remember { AudioRouter.getInstance(context) }
    val settingsRepo = remember { AudioSettingsRepository(context) }
    val text by manager.text.collectAsState()
    val isRecording by manager.isRecording.collectAsState()
    val status by manager.status.collectAsState()
    val modelLoaded by manager.isModelLoaded.collectAsState()

    val devices = remember { audioRouter.getInputDevices() }
    var selectedDeviceId by remember {
        mutableStateOf(settingsRepo.getInputDevice("stt") ?: -1)
    }

    var permissionGranted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val isLoading = status.startsWith("模型加载中")

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            manager.loadModelFromAssetsOrDefault()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            manager.cleanup()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("语音转文字") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (permissionGranted) {
                BottomActionBar(
                    text = if (isRecording) "停止" else "开始",
                    onClick = {
                        if (isRecording) {
                            manager.stop()
                        } else {
                            val effectiveId = selectedDeviceId.takeIf { it != -1 }
                            manager.setInputDevice(effectiveId)
                            manager.start()
                        }
                    },
                    enabled = modelLoaded
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusChip(
                text = status,
                modifier = Modifier.fillMaxWidth()
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = text.ifEmpty { "等待语音..." },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                )
            }

            AudioDevicePicker(
                title = "输入设备",
                devices = devices,
                selectedId = selectedDeviceId.takeIf { it != -1 },
                onSelect = { id ->
                    val deviceId = id ?: -1
                    selectedDeviceId = deviceId
                    if (id != null) {
                        settingsRepo.setInputDevice("stt", id)
                    } else {
                        settingsRepo.resetInputDevice("stt")
                    }
                }
            )

            if (!permissionGranted) {
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("授权录音权限")
                }
            }
        }
    }
}
