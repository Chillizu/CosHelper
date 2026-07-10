package com.coshelper.ui.screens

import android.Manifest
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coshelper.audio.AudioRouter
import com.coshelper.data.AudioSettingsRepository
import com.coshelper.rvc.RvcManager
import com.coshelper.rvc.RvcState
import com.coshelper.ui.components.AudioDevicePicker
import com.coshelper.ui.components.BottomActionBar
import com.coshelper.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RvcScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { RvcManager(context) }
    val state by manager.state.collectAsState()
    val info by manager.info.collectAsState()
    val router = remember { AudioRouter.getInstance(context) }
    val settingsRepo = remember { AudioSettingsRepository(context) }

    var permissionGranted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionGranted = it }

    // Model path with file picker
    var modelPath by remember { mutableStateOf("/sdcard/Download/rvc_model.onnx") }
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            modelPath = it.toString()
            manager.loadModel(it.toString())
        }
    }

    // Audio device states — read from AudioSettingsRepository
    var inputDevices by remember { mutableStateOf(router.getInputDevices()) }
    var outputDevices by remember { mutableStateOf(router.getOutputDevices()) }
    var inputDeviceId by remember {
        mutableStateOf(
            settingsRepo.getInputDevice("rvc")
                ?: router.getDefaultInputDevice()?.id
        )
    }
    var outputDeviceId by remember {
        mutableStateOf(
            settingsRepo.getOutputDevice("rvc")
                ?: router.getDefaultOutputDevice()?.id
        )
    }

    // Audio device callback for hotplug support
    DisposableEffect(router) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                inputDevices = router.getInputDevices()
                outputDevices = router.getOutputDevices()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                inputDevices = router.getInputDevices()
                outputDevices = router.getOutputDevices()
                val inputIds = inputDevices.map { it.id }
                val outputIds = outputDevices.map { it.id }
                if (inputDeviceId != null && inputDeviceId !in inputIds) {
                    inputDeviceId = router.getDefaultInputDevice()?.id
                    settingsRepo.setInputDevice("rvc", inputDeviceId)
                }
                if (outputDeviceId != null && outputDeviceId !in outputIds) {
                    outputDeviceId = router.getDefaultOutputDevice()?.id
                    settingsRepo.setOutputDevice("rvc", outputDeviceId)
                }
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    // Propagate device IDs to RvcManager on change
    DisposableEffect(inputDeviceId) {
        manager.setInputDevice(inputDeviceId)
        onDispose { }
    }
    DisposableEffect(outputDeviceId) {
        manager.setOutputDevice(outputDeviceId)
        onDispose { }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose { manager.cleanup() }
    }

    // Status mapping
    val statusIcon = when (state) {
        RvcState.Idle -> Icons.Default.Info
        RvcState.Loaded -> Icons.Default.CheckCircle
        RvcState.Running -> Icons.Default.Mic
        RvcState.Error -> Icons.Default.Error
    }
    val statusText = when (state) {
        RvcState.Idle -> "未加载模型"
        RvcState.Loaded -> "准备就绪"
        RvcState.Running -> "实时变声中"
        RvcState.Error -> info.ifEmpty { "未知错误" }
    }
    val canStart = state == RvcState.Loaded || state == RvcState.Running

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("变声器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!permissionGranted) {
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("授权录音权限")
                    }
                } else {
                    BottomActionBar(
                        text = if (state == RvcState.Running) "停止实时变声" else "开始实时变声",
                        onClick = {
                            if (state == RvcState.Running) manager.stop() else manager.start()
                        },
                        enabled = canStart
                    )
                }
                if (state != RvcState.Idle) {
                    TextButton(onClick = { manager.unload() }) {
                        Text("卸载模型")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status indicator
            StatusChip(
                icon = statusIcon,
                text = statusText,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Model path with file picker
            ListItem(
                headlineContent = {
                    Text(
                        text = modelPath,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text("模型路径", style = MaterialTheme.typography.bodySmall)
                },
                trailingContent = {
                    IconButton(onClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "选择模型文件")
                    }
                }
            )

            HorizontalDivider()

            // Per-feature audio device pickers
            AudioDevicePicker(
                title = "输入设备",
                devices = inputDevices,
                selectedId = inputDeviceId,
                onSelect = { id ->
                    inputDeviceId = id
                    settingsRepo.setInputDevice("rvc", id)
                },
                modifier = Modifier.fillMaxWidth()
            )

            AudioDevicePicker(
                title = "输出设备",
                devices = outputDevices,
                selectedId = outputDeviceId,
                onSelect = { id ->
                    outputDeviceId = id
                    settingsRepo.setOutputDevice("rvc", id)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
