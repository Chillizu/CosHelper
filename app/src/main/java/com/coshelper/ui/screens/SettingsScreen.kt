package com.coshelper.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.imePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.coshelper.BuildConfig
import com.coshelper.audio.AudioRouter
import com.coshelper.data.AudioSettingsRepository
import com.coshelper.ui.components.AudioDevicePicker
import com.coshelper.utils.copyModelUriToFilesDir

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val router = remember { AudioRouter.getInstance(context) }
    val settingsRepo = remember { AudioSettingsRepository(context) }

    // Permission state
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

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // Audio device states
    var inputDevices by remember { mutableStateOf(router.getInputDevices()) }
    var outputDevices by remember { mutableStateOf(router.getOutputDevices()) }
    var globalInputDeviceId by remember {
        mutableStateOf(settingsRepo.getInputDevice(null))
    }
    var globalOutputDeviceId by remember {
        mutableStateOf(settingsRepo.getOutputDevice(null))
    }

    // Model paths (persisted)
    var rvcModelPath by remember { mutableStateOf(settingsRepo.getRvcModelPath()) }
    var sttModelPath by remember { mutableStateOf(settingsRepo.getSttModelPath()) }
    var hotspotKey by remember { mutableStateOf(settingsRepo.getHotspotKey()) }
    var showHotspotKeyDialog by remember { mutableStateOf(false) }

    val rvcFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            copyModelUriToFilesDir(context, it, "rvc_model.onnx")?.let { dest ->
                val path = dest.absolutePath
                rvcModelPath = path
                settingsRepo.setRvcModelPath(path)
            }
        }
    }
    val sttFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            copyModelUriToFilesDir(context, it, "stt_model.bin")?.let { dest ->
                val path = dest.absolutePath
                sttModelPath = path
                settingsRepo.setSttModelPath(path)
            }
        }
    }

    // STT recognition beep
    var sttBeepEnabled by remember {
        mutableStateOf(settingsRepo.getSttRecognitionBeep())
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
                if (globalInputDeviceId != null && globalInputDeviceId !in inputIds) {
                    globalInputDeviceId = null
                    settingsRepo.resetInputDevice(null)
                }
                if (globalOutputDeviceId != null && globalOutputDeviceId !in outputIds) {
                    globalOutputDeviceId = null
                    settingsRepo.resetOutputDevice(null)
                }
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("设置") },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
            horizontalAlignment = Alignment.Start
        ) {
            // ── Section 1: 音频默认 ──
            SectionHeader(title = "音频默认")
            AudioDevicePicker(
                title = "默认输入设备",
                devices = inputDevices,
                selectedId = globalInputDeviceId,
                onSelect = { id ->
                    globalInputDeviceId = id
                    settingsRepo.setInputDevice(null, id)
                },
                modifier = Modifier.fillMaxWidth()
            )

            AudioDevicePicker(
                title = "默认输出设备",
                devices = outputDevices,
                selectedId = globalOutputDeviceId,
                onSelect = { id ->
                    globalOutputDeviceId = id
                    settingsRepo.setOutputDevice(null, id)
                },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Section 2: 模型路径 ──
            SectionHeader(title = "模型路径")

            OutlinedTextField(
                value = rvcModelPath,
                onValueChange = {
                    rvcModelPath = it
                    settingsRepo.setRvcModelPath(it)
                },
                label = { Text("RVC ONNX 模型路径") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        rvcFilePickerLauncher.launch(arrayOf("*/*"))
                    }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "选择文件")
                    }
                }
            )

            OutlinedTextField(
                value = sttModelPath,
                onValueChange = {
                    sttModelPath = it
                    settingsRepo.setSttModelPath(it)
                },
                label = { Text("STT 模型路径") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        sttFilePickerLauncher.launch(arrayOf("*/*"))
                    }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "选择文件")
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Section 3: 热点与对讲 ──
            SectionHeader(title = "热点与对讲")

            ListItem(
                modifier = Modifier.clickable { showHotspotKeyDialog = true },
                headlineContent = { Text("热点加密密钥") },
                supportingContent = {
                    Text(
                        if (hotspotKey.isNotBlank()) "已设置" else "未设置（明文传输）",
                        color = if (hotspotKey.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "编辑")
                }
            )

            if (showHotspotKeyDialog) {
                var draftKey by remember { mutableStateOf(hotspotKey) }
                AlertDialog(
                    onDismissRequest = { showHotspotKeyDialog = false },
                    title = { Text("热点对讲加密密钥") },
                    text = {
                        OutlinedTextField(
                            value = draftKey,
                            onValueChange = { draftKey = it },
                            label = { Text("共享密钥（两端需一致）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                hotspotKey = draftKey
                                settingsRepo.setHotspotKey(draftKey)
                                showHotspotKeyDialog = false
                            }
                        ) {
                            Text("保存")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showHotspotKeyDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Section 4: 权限与无障碍 ──
            SectionHeader(title = "权限与无障碍")

            ListItem(
                headlineContent = { Text("录音权限") },
                supportingContent = {
                    Text(
                        if (permissionGranted) "已授权" else "未授权",
                        color = if (permissionGranted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.Mic, contentDescription = null)
                },
                trailingContent = {
                    if (!permissionGranted) {
                        IconButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "授权")
                        }
                    }
                }
            )

            ListItem(
                headlineContent = { Text("音量键 PTT 无障碍服务") },
                supportingContent = { Text("开启系统无障碍服务以使用音量键触发 PTT") },
                leadingContent = {
                    Icon(Icons.Default.Security, contentDescription = null)
                },
                trailingContent = {
                    IconButton(onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "设置")
                    }
                }
            )

            ListItem(
                modifier = Modifier.toggleable(
                    value = sttBeepEnabled,
                    role = Role.Switch,
                    onValueChange = { enabled ->
                        sttBeepEnabled = enabled
                        settingsRepo.setSttRecognitionBeep(enabled)
                    }
                ),
                headlineContent = { Text("STT 识别提示音") },
                supportingContent = { Text("开始和停止语音识别时播放提示音") },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = sttBeepEnabled,
                        onCheckedChange = null
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Section 4: 关于 ──
            SectionHeader(title = "关于")

            ListItem(
                headlineContent = { Text("MioKig") },
                supportingContent = { Text("版本 ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})") },
                leadingContent = {
                    Icon(Icons.Default.Info, contentDescription = null)
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    )
}
