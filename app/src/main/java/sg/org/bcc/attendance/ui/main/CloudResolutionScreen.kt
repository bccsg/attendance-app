package sg.org.bcc.attendance.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.local.entities.Group
import sg.org.bcc.attendance.ui.components.AppIcon
import sg.org.bcc.attendance.ui.components.AppIcons
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudResolutionScreen(
    onBack: () -> Unit,
    viewModel: CloudResolutionViewModel = hiltViewModel()
) {
    val missingAttendees by viewModel.missingAttendees.collectAsState()
    val missingGroups by viewModel.missingGroups.collectAsState()
    val missingEvents by viewModel.missingEvents.collectAsState()
    
    val scope = rememberCoroutineScope()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var processingIds by remember { mutableStateOf(setOf<String>()) }
    
    var selectedEventForResolution by remember { mutableStateOf<Event?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Sync Resolution", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(resourceId = AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (missingAttendees.isNotEmpty() || missingGroups.isNotEmpty() || missingEvents.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            AppIcon(
                                resourceId = AppIcons.DeleteSweep, 
                                contentDescription = "Delete All",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
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
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedEventForResolution = event },
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(event.title)
                                    },
                                    supportingContent = {
                                        Text("${event.date} • ${event.time}")
                                    },
                                    leadingContent = {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                AppIcon(
                                                    resourceId = AppIcons.CalendarMonth,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    },
                                    trailingContent = {
                                        AppIcon(
                                            resourceId = AppIcons.MoreVert,
                                            contentDescription = "Resolve",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }

                    if (missingAttendees.isNotEmpty()) {
                        item {
                            SectionHeader("Missing Attendees")
                        }
                        items(missingAttendees, key = { "attendee_${it.id}" }) { attendee ->
                            SwipeToDeleteItem(
                                id = attendee.id,
                                processingIds = processingIds,
                                onRemove = {
                                    processingIds = processingIds + attendee.id
                                    scope.launch {
                                        delay(100)
                                        viewModel.removeAttendee(attendee.id)
                                    }
                                }
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(attendee.shortName ?: attendee.fullName)
                                    },
                                    supportingContent = {
                                        if (attendee.shortName != null) {
                                            Text(attendee.fullName)
                                        }
                                    },
                                    leadingContent = {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = (attendee.shortName ?: attendee.fullName).take(1).uppercase(),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }

                    if (missingGroups.isNotEmpty()) {
                        item {
                            SectionHeader("Missing Groups")
                        }
                        items(missingGroups, key = { "group_${it.groupId}" }) { group ->
                            SwipeToDeleteItem(
                                id = group.groupId,
                                processingIds = processingIds,
                                onRemove = {
                                    processingIds = processingIds + group.groupId
                                    scope.launch {
                                        delay(100)
                                        viewModel.removeGroup(group.groupId)
                                    }
                                }
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(group.name)
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
        ModalBottomSheet(
            onDismissRequest = { selectedEventForResolution = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                Text(
                    text = selectedEventForResolution?.title ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "This event exists locally but its cloud sheet is missing. How would you like to resolve this?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                Button(
                    onClick = {
                        selectedEventForResolution?.let { viewModel.recreateEvent(it.id) }
                        selectedEventForResolution = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppIcon(resourceId = AppIcons.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Re-create on Cloud")
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = {
                        selectedEventForResolution?.let { viewModel.deleteEventLocally(it.id) }
                        selectedEventForResolution = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    AppIcon(resourceId = AppIcons.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Locally")
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Missing") },
            text = { Text("This will permanently remove all local records that do not exist on the cloud. Are you sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.purgeAll()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
            }
        )
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

@Composable
fun SwipeToDeleteItem(
    id: String,
    processingIds: Set<String>,
    onRemove: () -> Unit,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isProcessing = processingIds.contains(id)
    
    val swipeOffset = remember { Animatable(0f) }
    var itemWidth by remember { mutableFloatStateOf(0f) }
    var isArmed by remember { mutableStateOf(false) }

    LaunchedEffect(itemWidth) {
        if (itemWidth > 0) {
            val threshold = itemWidth * 0.25f
            snapshotFlow { swipeOffset.value }.collect { currentOffset ->
                val currentlyBeyond = abs(currentOffset) >= threshold
                if (currentlyBeyond != isArmed) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isArmed = currentlyBeyond
                }
            }
        }
    }

    AnimatedVisibility(
        visible = !isProcessing,
        exit = shrinkVertically(animationSpec = tween(400, delayMillis = 100)) + 
               fadeOut(animationSpec = tween(400, delayMillis = 100))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { itemWidth = it.width.toFloat() }
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier.matchParentSize().padding(horizontal = 24.dp),
                contentAlignment = if (swipeOffset.value > 0) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                AppIcon(
                    resourceId = AppIcons.PlaylistRemove, 
                    contentDescription = "Remove", 
                    tint = if (isArmed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                    .pointerInput(itemWidth) {
                        if (itemWidth <= 0) return@pointerInput
                        val maxSwipe = itemWidth * 0.30f
                        
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (isArmed) {
                                    onRemove()
                                } else {
                                    scope.launch {
                                        swipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val newOffset = (swipeOffset.value + dragAmount)
                                    .coerceIn(-maxSwipe, maxSwipe)
                                scope.launch {
                                    swipeOffset.snapTo(newOffset)
                                }
                            }
                        )
                    }
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                content()
            }
        }
    }
}
