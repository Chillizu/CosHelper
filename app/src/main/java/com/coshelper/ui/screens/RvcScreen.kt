package com.coshelper.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coshelper.rvc.RvcManager
import com.coshelper.rvc.RvcState

@Composable
fun RvcScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { RvcManager(context) }
    val state by manager.state.collectAsState()
    val info by manager.info.collectAsState()

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

    var modelPath by remember { mutableStateOf("/sdcard/Download/rvc_model.onnx") }

    DisposableEffect(Unit) {
        onDispose { manager.cleanup() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Text("返回", fontSize = 20.sp)
        }

        Text("变声器", fontSize = 36.sp, color = MaterialTheme.colorScheme.onBackground)

        Text(info, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "模型路径：$modelPath",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                // In real app, open file picker. For now, hard-coded path.
                manager.loadModel(modelPath)
            }) {
                Text("加载", fontSize = 20.sp)
            }
        }

        if (!permissionGranted) {
            Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text("授权录音权限", fontSize = 24.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("开启变声", fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
                Switch(
                    checked = state == RvcState.Running,
                    onCheckedChange = { checked ->
                        if (checked) manager.start() else manager.stop()
                    }
                )
            }
        }

        Button(onClick = { manager.unload() }) {
            Text("卸载模型", fontSize = 24.sp)
        }
    }
}
