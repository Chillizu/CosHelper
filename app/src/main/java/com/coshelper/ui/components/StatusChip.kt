package com.coshelper.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun StatusChip(
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    text: String,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            androidx.compose.material3.Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else null,
        modifier = modifier,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = null
    )
}

@Preview(showBackground = true)
@Composable
private fun StatusChipPreview() {
    MaterialTheme {
        StatusChip(
            icon = Icons.Default.Info,
            text = "就绪"
        )
    }
}
