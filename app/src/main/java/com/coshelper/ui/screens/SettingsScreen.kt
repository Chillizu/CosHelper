package com.coshelper.ui.screens

import android.Manifest
import android.content.Intent
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.coshelper.BuildConfig
import com.coshelper.audio.AudioRouter
import com.coshelper.data.AudioSettingsRepository
import com.coshelper.ui.components.AudioDevicePicker

@Composable
fun SettingsScreen(onBack: () -> Unit) {
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

    // Audio device states
    var inputDevices by remember { mutableStateOf(router.getInputDevices()) }
    var outputDevices by remember { mutableStateOf(router.getOutputDevices()) }
    var globalInputDeviceId by remember {
        mutableStateOf(
            settingsRepo.getInputDevice(null)
                ?: router.getDefaultInputDevice()?.id
                ?: -1
        )
    }
    var globalOutputDeviceId by remember {
        mutableStateOf(
            settingsRepo.getOutputDevice(null)
                ?: router.getDefaultOutputDevice()?.id
                ?: -1
        )
    }

    // Model paths
    var rvcModelPath by remember { mutableStateOf("/sdcard/Download/rvc_model.onnx") }
    var sttModelPath by remember { mutableStateOf("") }
    val rvcFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            rvcModelPath = uri.toString()
            // In production, persist to SharedPreferences
        }
    }
    val sttFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            sttModelPath = uri.toString()
            // In production, persist to SharedPreferences
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
                if (globalInputDeviceId != -1 && globalInputDeviceId !in inputIds) {
                    globalInputDeviceId = router.getDefaultInputDevice()?.id ?: -1
                    settingsRepo.setInputDevice(null, globalInputDeviceId)
                }
                if (globalOutputDeviceId != -1 && globalOutputDeviceId !in outputIds) {
                    globalOutputDeviceId = router.getDefaultOutputDevice()?.id ?: -1
                    settingsRepo.setOutputDevice(null, globalOutputDeviceId)
                }
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
        horizontalAlignment = Alignment.Start
    ) {
        // Back button
        Button(onClick = onBack) {
            Text("返回", fontSize = 20.sp)
        }

        // ── Section 1: 音频默认 ──
        SectionHeader(title = "音频默认")
        AudioDevicePicker(
            title = "默认输入设备",
            devices = inputDevices,
            selectedId = globalInputDeviceId.takeIf { it != -1 },
            onSelect = { id ->
                globalInputDeviceId = id ?: -1
                settingsRepo.setInputDevice(null, id)
            },
            modifier = Modifier.fillMaxWidth()
        )

        AudioDevicePicker(
            title = "默认输出设备",
            devices = outputDevices,
            selectedId = globalOutputDeviceId.takeIf { it != -1 },
            onSelect = { id ->
                globalOutputDeviceId = id ?: -1
                settingsRepo.setOutputDevice(null, id)
            },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── Section 2: 模型路径 ──
        SectionHeader(title = "模型路径")

        OutlinedTextField(
            value = rvcModelPath,
            onValueChange = { rvcModelPath = it },
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

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = sttModelPath,
            onValueChange = { sttModelPath = it },
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

        // ── Section 3: 权限与无障碍 ──
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
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("授权", fontSize = 14.sp)
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
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text("设置", fontSize = 14.sp)
                }
            }
        )

        ListItem(
            headlineContent = { Text("STT 识别提示音") },
            supportingContent = { Text("开始和停止语音识别时播放提示音") },
            leadingContent = {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
            },
            trailingContent = {
                Switch(
                    checked = sttBeepEnabled,
                    onCheckedChange = { enabled ->
                        sttBeepEnabled = enabled
                        settingsRepo.setSttRecognitionBeep(enabled)
                    }
                )
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── Section 4: 关于 ──
        SectionHeader(title = "关于")

        ListItem(
            headlineContent = { Text("CosHelper") },
            supportingContent = { Text("版本 ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})") },
            leadingContent = {
                Icon(Icons.Default.Info, contentDescription = null)
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 22.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    )
}
