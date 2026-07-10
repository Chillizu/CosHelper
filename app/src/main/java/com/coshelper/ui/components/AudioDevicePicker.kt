package com.coshelper.ui.components

import android.media.AudioDeviceInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.coshelper.utils.AppLogger

@Composable
fun AudioDevicePicker(
    title: String,
    devices: List<AudioDeviceInfo>,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val onSelectLogged: (Int?) -> Unit = { id ->
            AppLogger.d("AudioDevicePicker", "Selected: title=$title, id=${id ?: "default"}")
            onSelect(id)
        }

        LaunchedEffect(devices) {
            val sb = StringBuilder()
            sb.append("Device list: title=$title, count=${devices.size}")
            if (devices.isNotEmpty()) {
                sb.append(" [")
                devices.forEachIndexed { index, device ->
                    if (index > 0) sb.append("; ")
                    sb.append("id=${device.id}, type=${AppLogger.deviceTypeName(device.type)}, name=${device.productName}")
                }
                sb.append("]")
            }
            AppLogger.d("AudioDevicePicker", sb.toString())
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        // "使用全局默认" option (passes null)
        val isDefaultSelected = selectedId == null
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(
                    selected = isDefaultSelected,
                    onClick = { onSelectLogged(null) },
                    role = Role.RadioButton
                ),
            shape = RoundedCornerShape(8.dp),
            color = if (isDefaultSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            tonalElevation = if (isDefaultSelected) 2.dp else 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isDefaultSelected) Icons.Default.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (isDefaultSelected) "已选择" else "未选择",
                    modifier = Modifier.size(20.dp),
                    tint = if (isDefaultSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "使用全局默认",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDefaultSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        // Individual device options
        devices.forEach { device ->
            val deviceId = device.id
            val isSelected = deviceId == selectedId
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelectLogged(deviceId) },
                        role = Role.RadioButton
                    ),
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                tonalElevation = if (isSelected) 2.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "已选择" else "未选择",
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = device.productName?.toString() ?: "未知设备",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
        if (devices.isEmpty()) {
            Text(
                text = "无可用设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioDevicePickerPreview() {
    MaterialTheme {
        AudioDevicePicker(
            title = "输入设备",
            devices = emptyList(),
            selectedId = null,
            onSelect = {}
        )
    }
}
