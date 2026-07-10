package com.coshelper.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coshelper.audio.AudioRouter
import com.coshelper.ptt.PTTAccessibilityService

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val router = remember { AudioRouter.getInstance(context) }

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
    ) { permissionGranted = it }

    var inputDeviceId by remember { mutableStateOf(AudioRouter.preferredInputDeviceId ?: router.getDefaultInputDevice()?.id ?: -1) }
    var outputDeviceId by remember { mutableStateOf(AudioRouter.preferredOutputDeviceId ?: router.getDefaultOutputDevice()?.id ?: -1) }
    var communicationMode by remember { mutableStateOf(false) }

    var inputDevices by remember { mutableStateOf(router.getInputDevices()) }
    var outputDevices by remember { mutableStateOf(router.getOutputDevices()) }

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
                if (inputDeviceId != -1 && inputDeviceId !in inputIds) {
                    inputDeviceId = router.getDefaultInputDevice()?.id ?: -1
                    AudioRouter.setInputDevice(inputDeviceId)
                }
                if (outputDeviceId != -1 && outputDeviceId !in outputIds) {
                    outputDeviceId = router.getDefaultOutputDevice()?.id ?: -1
                    AudioRouter.setOutputDevice(outputDeviceId)
                }
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Text("返回", fontSize = 20.sp)
        }

        Text("设备设置", fontSize = 36.sp, color = MaterialTheme.colorScheme.onBackground)

        if (!permissionGranted) {
            Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("授权录音权限", fontSize = 24.sp)
            }
        }

        Text("输入设备", fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
        DeviceList(
            devices = inputDevices,
            selectedId = inputDeviceId,
            onSelect = { id ->
                inputDeviceId = id
                AudioRouter.setInputDevice(id)
            }
        )

        Text("输出设备", fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
        DeviceList(
            devices = outputDevices,
            selectedId = outputDeviceId,
            onSelect = { id ->
                outputDeviceId = id
                AudioRouter.setOutputDevice(id)
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("通话模式", fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
            Switch(
                checked = communicationMode,
                onCheckedChange = { checked ->
                    communicationMode = checked
                    router.setCommunicationMode(checked)
                }
            )
        }

        Button(onClick = {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            context.startActivity(intent)
        }) {
            Text("开启音量键 PTT 无障碍服务", fontSize = 22.sp)
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<AudioDeviceInfo>,
    selectedId: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        devices.forEach { device ->
            val name = device.productName?.toString() ?: "未知设备"
            val typeName = typeToString(device.type)
            val selected = device.id == selectedId
            Button(
                onClick = { onSelect(device.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(
                    text = "${if (selected) "[已选] " else ""}$typeName: $name",
                    fontSize = 20.sp,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

private fun typeToString(type: Int): String {
    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "内置麦克风"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "内置扬声器"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳机"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙 SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙 A2DP"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB 设备"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 耳机"
        else -> "设备 ($type)"
    }
}
