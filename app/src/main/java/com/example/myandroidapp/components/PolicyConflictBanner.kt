package com.example.myandroidapp.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myandroidapp.shared.AlertBannerState
import com.example.myandroidapp.shared.AlertSeverity

@Composable
fun PolicyConflictBanner(
    banner: AlertBannerState,
    modifier: Modifier = Modifier,
    onNavigate: ((String) -> Unit)? = null,
) {
    val (icon, tint) = when (banner.severity) {
        AlertSeverity.INFO -> Icons.Default.Info to MaterialTheme.colorScheme.primary
        AlertSeverity.WARNING -> Icons.Default.Warning to MaterialTheme.colorScheme.tertiary
        AlertSeverity.CRITICAL -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }

    Surface(
        color = tint.copy(alpha = 0.12f),
        contentColor = tint,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(banner.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(banner.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (banner.relatedRoute != null && onNavigate != null) {
                AssistChip(
                    onClick = { onNavigate(banner.relatedRoute) },
                    label = { Text("View details") },
                )
            }
        }
    }
}

