package sg.org.bcc.attendance.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sg.org.bcc.attendance.sync.SyncProgress
import sg.org.bcc.attendance.ui.components.AppIcon
import sg.org.bcc.attendance.ui.components.AppIcons
import sg.org.bcc.attendance.ui.components.RotatingSyncIcon
import sg.org.bcc.attendance.sync.SyncState

@Composable
fun CloudStatusDialog(
    isAuthed: Boolean,
    authState: sg.org.bcc.attendance.data.remote.AuthState,
    cloudProfile: CloudProfile?,
    syncProgress: SyncProgress,
    isDemoMode: Boolean,
    isOnline: Boolean,
    loginError: String? = null,
    totalAttendeesCount: Int = 0,
    totalGroupsCount: Int = 0,
    attendeesWithGroupCount: Int = 0,
    missingCloudAttendeesCount: Int = 0,
    missingCloudGroupsCount: Int = 0,
    missingCloudEventsCount: Int = 0,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    onManualSync: () -> Unit,
    onShowLogs: () -> Unit,
    onResolveMissing: () -> Unit
) {
    var isAcknowledgeLossChecked by remember { mutableStateOf(false) }
    val hasPendingJobs = syncProgress.pendingJobs > 0
    val canProceedWithAuthAction = !hasPendingJobs || isAcknowledgeLossChecked

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RotatingSyncIcon(
                    resourceId = syncProgress.cloudStatusIcon, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary,
                    shouldRotate = syncProgress.shouldRotate
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cloud Status", color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val errorMessage = when {
                    loginError != null -> loginError
                    authState == sg.org.bcc.attendance.data.remote.AuthState.EXPIRED -> "Session expired. Please login again to sync data."
                    syncProgress.lastErrors.firstOrNull()?.message != null -> syncProgress.lastErrors.firstOrNull()?.message
                    syncProgress.syncState == SyncState.ERROR -> "An unknown synchronization error occurred."
                    else -> null
                }

                // Single Error / Progress Banner
                val bannerData = when {
                    !isOnline -> Triple(
                        "No internet connection. Cloud features are unavailable.",
                        syncProgress.cloudStatusIcon,
                        MaterialTheme.colorScheme.errorContainer
                    )
                    syncProgress.isBlockingEventMissing && !isDemoMode -> Triple(
                        "Event missing on cloud. Attendance cannot be pushed.",
                        syncProgress.cloudStatusIcon,
                        MaterialTheme.colorScheme.errorContainer
                    )
                    errorMessage != null -> Triple(
                        errorMessage,
                        syncProgress.cloudStatusIcon,
                        MaterialTheme.colorScheme.errorContainer
                    )
                    else -> null
                }

                if (bannerData != null) {
                    val (text, _, _) = bannerData
                    val isMissingEventError = syncProgress.isBlockingEventMissing && !isDemoMode && text.startsWith("Event missing")
                    
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isMissingEventError) Modifier.clickable { onResolveMissing() } else Modifier)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Normal
                            )
                            if (isMissingEventError) {
                                Text(
                                    text = "Tap to resolve",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Auth Section
                if (isAuthed && cloudProfile != null && authState == sg.org.bcc.attendance.data.remote.AuthState.AUTHENTICATED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AppIcon(resourceId = AppIcons.Person, contentDescription = null)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(cloudProfile.email, style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    if (hasPendingJobs) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isAcknowledgeLossChecked = !isAcknowledgeLossChecked }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isAcknowledgeLossChecked,
                                onCheckedChange = { isAcknowledgeLossChecked = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "I acknowledge that ${syncProgress.pendingJobs} pending sync tasks will be lost if I logout.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canProceedWithAuthAction && !syncProgress.shouldRotate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("Logout")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (!isAuthed) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        "Using Demo Data",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "Login to establish connectivity with Master and Event Google Sheets.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        } else if (authState == sg.org.bcc.attendance.data.remote.AuthState.EXPIRED) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        AppIcon(resourceId = AppIcons.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(cloudProfile?.email ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                                    Text("Session Expired", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        if (hasPendingJobs) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isAcknowledgeLossChecked = !isAcknowledgeLossChecked }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = isAcknowledgeLossChecked,
                                    onCheckedChange = { isAcknowledgeLossChecked = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "I acknowledge that ${syncProgress.pendingJobs} pending sync tasks will be lost if I login with a different account.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        
                        Button(
                            onClick = onLogin, 
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canProceedWithAuthAction && !syncProgress.shouldRotate,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isAuthed) "Login Again" else "Login with Google")
                        }

                        if (isAuthed) {
                            TextButton(
                                onClick = onLogout,
                                enabled = canProceedWithAuthAction && !syncProgress.shouldRotate,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Logout", color = if (canProceedWithAuthAction && !syncProgress.shouldRotate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.38f))
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Statistics Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Statistics", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    SyncInfoRow("Total Attendees", totalAttendeesCount.toString())
                    SyncInfoRow("Total Groups", totalGroupsCount.toString())
                    SyncInfoRow("Attendees with Group", attendeesWithGroupCount.toString())
                }

                HorizontalDivider()

                // Sync Status Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sync Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    
                    SyncInfoRow("Pending Pushes", syncProgress.pendingJobs.toString())
                    
                    if (syncProgress.lastPullTime != null) {
                        val time = java.time.Instant.ofEpochMilli(syncProgress.lastPullTime).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                        SyncInfoRow("Last Pull", time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")))
                    }

                    SyncInfoRow("Last Pull Status", syncProgress.lastPullStatus ?: "Never", onClick = onShowLogs)

                    syncProgress.nextScheduledPull?.let { next ->
                        val nextPullStr = try {
                            java.time.Instant.ofEpochMilli(next).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                        } catch (e: Exception) {
                            "Unknown"
                        }
                        SyncInfoRow("Next Pull Scheduled", nextPullStr)
                    }

                    if (missingCloudAttendeesCount > 0 || missingCloudGroupsCount > 0 || missingCloudEventsCount > 0) {
                        val missingSummary = buildString {
                            if (missingCloudEventsCount > 0) append("${missingCloudEventsCount}E ")
                            if (missingCloudAttendeesCount > 0) append("${missingCloudAttendeesCount}A ")
                            if (missingCloudGroupsCount > 0) append("${missingCloudGroupsCount}G")
                        }.trim()
                        
                        SyncInfoRow(
                            label = "Missing on cloud",
                            value = missingSummary,
                            onClick = onResolveMissing
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            if (isAuthed && authState == sg.org.bcc.attendance.data.remote.AuthState.AUTHENTICATED) {
                TextButton(
                    onClick = onManualSync,
                    enabled = !syncProgress.shouldRotate && isOnline
                ) {
                    Text("Sync Now")
                }
            }
        }
    )
}

@Composable
fun SyncInfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, 
            style = MaterialTheme.typography.bodySmall, 
            fontWeight = FontWeight.Bold,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
    }
}
