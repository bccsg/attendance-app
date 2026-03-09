package sg.org.bcc.attendance.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sg.org.bcc.attendance.ui.components.AppIcon
import sg.org.bcc.attendance.ui.components.AppIcons
import sg.org.bcc.attendance.util.AppUpdate

@Composable
fun UpdateDialog(
    updateState: AppUpdate,
    isDownloading: Boolean,
    progress: Float,
    onUpdate: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val updateToShow = when (updateState) {
        is AppUpdate.VersionsAvailable -> updateState.mainline ?: updateState.beta
        else -> null
    } ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    resourceId = AppIcons.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Update Available", color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = if (updateToShow.isBeta) {
                        "A new beta version (${updateToShow.version}) is available. Would you like to update now?"
                    } else {
                        "A new version (${updateToShow.version}) is available. It is recommended to keep the app up to date."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                if (isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text(
                            text = "Downloading... ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (updateState is AppUpdate.Error) {
                    Text(
                        text = updateState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (isDownloading) {
                TextButton(onClick = onCancel) {
                    Text("Cancel Download")
                }
            } else {
                Button(
                    onClick = { onUpdate(updateToShow.downloadUrl) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Update Now")
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}
