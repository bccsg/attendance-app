package sg.org.bcc.attendance.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.local.entities.Group
import sg.org.bcc.attendance.ui.components.AppIcon
import sg.org.bcc.attendance.ui.components.AppIcons
import sg.org.bcc.attendance.ui.components.AttendeeListItem
import sg.org.bcc.attendance.ui.components.RotatingSyncIcon
import sg.org.bcc.attendance.ui.components.DateIcon
import sg.org.bcc.attendance.util.EventSuggester
import sg.org.bcc.attendance.sync.SyncState
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudResolutionScreen(
    onBack: () -> Unit,
    viewModel: CloudResolutionViewModel = hiltViewModel()
) {
    val missingAttendees by viewModel.missingAttendees.collectAsState()
    val missingGroups by viewModel.missingGroups.collectAsState()
    val missingEvents by viewModel.missingEvents.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val resolutionError by viewModel.resolutionError.collectAsState()
    
    var selectedEventForResolution by remember { mutableStateOf<Event?>(null) }
    var selectedAttendeeForResolution by remember { mutableStateOf<Attendee?>(null) }
    var selectedGroupForResolution by remember { mutableStateOf<Group?>(null) }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 4.dp
            ) {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text("Cloud Sync Resolution", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            AppIcon(resourceId = AppIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        if (syncProgress.syncState != SyncState.IDLE || isProcessing) {
                            val icon = if (syncProgress.syncState != SyncState.IDLE) syncProgress.cloudStatusIcon else AppIcons.Sync
                            val shouldRotate = if (syncProgress.syncState != SyncState.IDLE) syncProgress.shouldRotate else true
                            
                            RotatingSyncIcon(
                                resourceId = icon,
                                contentDescription = "Syncing",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                shouldRotate = shouldRotate
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            if (missingAttendees.isEmpty() && missingGroups.isEmpty() && missingEvents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppIcon(
                            resourceId = AppIcons.CloudDone, 
                            contentDescription = null, 
                            modifier = Modifier.size(64.dp), 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Everything is in sync", 
                            style = MaterialTheme.typography.bodyLarge, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (missingEvents.isNotEmpty()) {
                        item {
                            SectionHeader("Missing Events")
                        }
                        items(missingEvents, key = { "event_${it.id}" }) { event ->
                            MissingEventItem(
                                event = event,
                                onClick = { 
                                    viewModel.clearError()
                                    selectedEventForResolution = event 
                                }
                            )
                        }
                    }

                    if (missingAttendees.isNotEmpty()) {
                        item {
                            SectionHeader("Missing Attendees")
                        }
                        items(missingAttendees, key = { "attendee_${it.id}" }) { attendee ->
                            AttendeeListItem(
                                attendee = attendee,
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                onClick = { 
                                    viewModel.clearError()
                                    selectedAttendeeForResolution = attendee 
                                }
                            )
                        }
                    }

                    if (missingGroups.isNotEmpty()) {
                        item {
                            SectionHeader("Missing Groups")
                        }
                        items(missingGroups, key = { "group_${it.groupId}" }) { group ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        viewModel.clearError()
                                        selectedGroupForResolution = group 
                                    },
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = group.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = "ID: ${group.groupId}", 
                                            style = MaterialTheme.typography.labelSmall, 
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    },
                                    leadingContent = {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                AppIcon(
                                                    resourceId = AppIcons.Groups,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }

    if (selectedEventForResolution != null) {
        val event = selectedEventForResolution!!
        val parts = event.title.split(" ", limit = 3)
        val date = if (parts.isNotEmpty()) EventSuggester.parseDate(parts[0]) else null
        val timeStr = if (parts.size > 1) parts[1] else "0000"
        val name = if (parts.size > 2) parts[2] else "Unnamed Event"
        val time = try {
            LocalTime.of(timeStr.take(2).toInt(), timeStr.takeLast(2).toInt())
        } catch (e: Exception) {
            LocalTime.MIDNIGHT
        }
        val formattedTime = time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))

        ResolutionBottomSheet(
            description = "This event exists locally but its cloud sheet is missing. You can manually restore the sheet on the cloud and sync again, or use the actions below.",
            inUseWarning = null,
            isProcessing = isProcessing,
            resolutionError = resolutionError,
            canRemove = true,
            resolveButtonLabel = "Remove locally",
            onDismiss = { if (!isProcessing) selectedEventForResolution = null },
            onResolve = {
                event.let { 
                    viewModel.deleteEventLocally(it.id) {
                        selectedEventForResolution = null
                    }
                }
            },
            header = {
                ListItem(
                    modifier = Modifier.fillMaxWidth(),
                    leadingContent = {
                        DateIcon(date = date, textScale = 1.0f)
                    },
                    headlineContent = {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    supportingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cloud ID: ${event.cloudEventId ?: "N/A"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            },
            extraAction = {
                Button(
                    onClick = {
                        event.let { 
                            viewModel.recreateEvent(it.id) {
                                selectedEventForResolution = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    AppIcon(resourceId = AppIcons.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Re-create on Cloud")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        )
    }

    if (selectedAttendeeForResolution != null) {
        var isInUse by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(selectedAttendeeForResolution) {
            selectedAttendeeForResolution?.let {
                isInUse = viewModel.isAttendeeInUse(it.id)
            }
        }

        ResolutionBottomSheet(
            description = "This attendee exists locally but is missing on the cloud master list. You can manually restore the entry on the cloud and sync again.",
            inUseWarning = if (isInUse == true) "This attendee cannot be removed locally because they still have attendance records or are still referenced in the cloud group mappings. Removal is only possible after 30 days have passed since their last event, and they are removed from the cloud's 'Mappings' sheet." else null,
            isProcessing = isProcessing,
            resolutionError = resolutionError,
            canRemove = isInUse == false,
            onDismiss = { if (!isProcessing) selectedAttendeeForResolution = null },
            onResolve = {
                selectedAttendeeForResolution?.let { 
                    viewModel.removeAttendee(it.id) {
                        selectedAttendeeForResolution = null
                    }
                }
            },
            header = {
                selectedAttendeeForResolution?.let {
                    AttendeeListItem(
                        attendee = it,
                        onClick = { }
                    )
                }
            }
        )
    }

    if (selectedGroupForResolution != null) {
        var isInUse by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(selectedGroupForResolution) {
            selectedGroupForResolution?.let {
                isInUse = viewModel.isGroupInUse(it.groupId)
            }
        }

        ResolutionBottomSheet(
            title = selectedGroupForResolution?.name ?: "",
            id = selectedGroupForResolution?.groupId ?: "",
            description = "This group exists locally but is missing on the cloud master list. You can manually restore the entry on the cloud and sync again.",
            inUseWarning = if (isInUse == true) "This group cannot be removed locally because it has linked attendees. Removal is possible after all references are removed from the cloud's 'Mappings' sheet." else null,
            isProcessing = isProcessing,
            resolutionError = resolutionError,
            canRemove = isInUse == false,
            onDismiss = { if (!isProcessing) selectedGroupForResolution = null },
            onResolve = {
                selectedGroupForResolution?.let { 
                    viewModel.removeGroup(it.groupId) {
                        selectedGroupForResolution = null
                    }
                }
            }
        )
    }
}

@Composable
fun MissingEventItem(
    event: Event,
    onClick: () -> Unit
) {
    // Parse title: yyMMdd HHmm Name
    val parts = event.title.split(" ", limit = 3)
    val date = if (parts.isNotEmpty()) EventSuggester.parseDate(parts[0]) else null
    val timeStr = if (parts.size > 1) parts[1] else "0000"
    val name = if (parts.size > 2) parts[2] else "Unnamed Event"

    val time = try {
        LocalTime.of(timeStr.take(2).toInt(), timeStr.takeLast(2).toInt())
    } catch (e: Exception) {
        LocalTime.MIDNIGHT
    }
    val formattedTime = time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        ListItem(
            leadingContent = {
                DateIcon(date = date, textScale = 1.0f)
            },
            headlineContent = {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cloud ID: ${event.cloudEventId ?: "N/A"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolutionBottomSheet(
    title: String = "",
    id: String = "",
    description: String,
    inUseWarning: String?,
    isProcessing: Boolean,
    resolutionError: String?,
    canRemove: Boolean,
    resolveButtonLabel: String = "Remove",
    onDismiss: () -> Unit,
    onResolve: () -> Unit,
    header: @Composable (() -> Unit)? = null,
    extraAction: @Composable (ColumnScope.() -> Unit)? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
        ) {
            if (header != null) {
                header()
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "ID: $id",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            if (inUseWarning != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(resourceId = AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = inUseWarning,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (resolutionError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        text = resolutionError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            
            if (extraAction != null) {
                extraAction()
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = onResolve,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isProcessing && canRemove,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    AppIcon(resourceId = AppIcons.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(resolveButtonLabel)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
