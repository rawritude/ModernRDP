package com.modernrdp.ui.components

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.modernrdp.data.model.RdpConnection
import com.modernrdp.util.WakeOnLan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConnectionCard(
    connection: RdpConnection,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onConnect,
                onLongClick = onEdit,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = connection.name.ifBlank { connection.hostname },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(connection.hostname)
                        if (connection.port != 3389) append(":${connection.port}")
                        if (connection.username.isNotBlank()) {
                            append(" \u2022 ${connection.username}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (connection.lastConnected > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Last: ${formatTimestamp(connection.lastConnected)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Wake-on-LAN button (only shown when MAC address is configured)
            if (connection.macAddress.isNotBlank() && WakeOnLan.isValidMac(connection.macAddress)) {
                FilledTonalIconButton(
                    onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    WakeOnLan.send(connection.macAddress, connection.hostname)
                                }
                                Toast.makeText(context, "WoL packet sent", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "WoL failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Power,
                        contentDescription = "Wake on LAN",
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            FilledTonalIconButton(onClick = onConnect) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Connect",
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
