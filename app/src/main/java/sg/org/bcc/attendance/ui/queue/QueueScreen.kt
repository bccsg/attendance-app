package sg.org.bcc.attendance.ui.queue

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.repository.QueueItem
import sg.org.bcc.attendance.ui.components.AppIcon
import sg.org.bcc.attendance.ui.components.AppIcons
import sg.org.bcc.attendance.ui.components.HoldToActivateButton
import sg.org.bcc.attendance.ui.theme.PastelGreen
import sg.org.bcc.attendance.ui.theme.DeepGreen
import kotlin.math.abs
import kotlin.math.roundToInt

import sg.org.bcc.attendance.ui.components.pinchToScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onActionComplete: (String, Boolean) -> Unit,
    currentEventId: String?, // Changed from currentEventTitle to ensure DB commit works
    textScale: Float,
    onTextScaleChange: (Float) -> Unit = {},
    viewModel: QueueViewModel = hiltViewModel()
) {
    LaunchedEffect(currentEventId) {
        viewModel.setEventId(currentEventId)
    }

    val queueItems by viewModel.queueItems.collectAsState()
    val presentIds by viewModel.presentIds.collectAsState()
    
    val readyCount = queueItems.count { !it.isLater }
    val laterCount = queueItems.count { it.isLater }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    var processingIds by remember { mutableStateOf(setOf<String>()) }
    var processingState by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    fun handleSync(state: String) {
        if (currentEventId == null) return
        val itemsToSync = queueItems.filter { !it.isLater }
        if (itemsToSync.isEmpty()) return

        processingIds = itemsToSync.map { it.attendee.id }.toSet()
        processingState = state
        
        scope.launch {
            delay(600)
            viewModel.syncQueue(currentEventId, state)
            val message = "Marked ${itemsToSync.size} as ${if (state == "PRESENT") "Present" else "Pending"}"
            onActionComplete(message, laterCount == 0)
            
            // To prevent flashing, wait for StateFlow to update before clearing processingIds
            delay(300)
            processingIds = emptySet()
            processingState = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (queueItems.isNotEmpty()) {
                        IconButton(onClick = { 
                            if (laterCount > 0) {
                                showClearDialog = true 
                            } else {
                                scope.launch { viewModel.clearReadyQueue() }
                            }
                        }) {
                            AppIcon(
                                resourceId = AppIcons.PlaylistRemove, 
                                contentDescription = "Clear Queue",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                // Inline Row: Chips (Centered) + Scan QR (Right)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // CENTERED CHIPS
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Ready Chip
                        BadgedBox(
                            badge = {
                                if (readyCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) { Text(readyCount.toString()) }
                                }
                            }
                        ) {
                            Surface(
                                color = if (readyCount > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp),
                                tonalElevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(
                                        resourceId = AppIcons.Checklist,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (readyCount > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ready",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (readyCount > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }

                        // Later Chip
                        BadgedBox(
                            badge = {
                                if (laterCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ) { Text(laterCount.toString()) }
                                }
                            }
                        ) {
                            Surface(
                                color = if (laterCount > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp),
                                tonalElevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(
                                        resourceId = AppIcons.PushPin,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (laterCount > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Later",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (laterCount > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }

                    // RIGHT-ALIGNED SCAN QR FAB
                    FloatingActionButton(
                        onClick = {
                            scope.launch { snackbarHostState.showSnackbar("QR Scanner coming soon") }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(2.dp)
                    ) {
                        AppIcon(resourceId = AppIcons.QrCodeScanner, contentDescription = "Scan QR")
                    }
                }

                Surface(
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HoldToActivateButton(
                            iconResId = AppIcons.PersonCancel,
                            onActivate = { handleSync("ABSENT") },
                            width = Dp.Unspecified,
                            height = 64.dp,
                            holdDurationMs = 1000L,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(0.34f),
                            enabled = readyCount > 0 && processingIds.isEmpty(),
                            content = { tint, alpha ->
                                AppIcon(
                                    resourceId = AppIcons.PersonCancel,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(32.dp).alpha(alpha)
                                )
                            }
                        )
                        
                        HoldToActivateButton(
                            iconResId = AppIcons.PersonCheck,
                            onActivate = { handleSync("PRESENT") },
                            modifier = Modifier.weight(0.66f),
                            height = 64.dp,
                            holdDurationMs = 1000L,
                            color = MaterialTheme.colorScheme.primary,
                            enabled = readyCount > 0 && processingIds.isEmpty()
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column {
                if (queueItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(resourceId = AppIcons.BookmarkAdded, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Queue is empty", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .pinchToScale(textScale, onTextScaleChange)
                    ) {
                        items(queueItems, key = { it.attendee.id }) { item ->
                            val isAlreadyPresent = presentIds.contains(item.attendee.id)
                            val isMarkingPresent = processingIds.contains(item.attendee.id) && processingState == "PRESENT"
                            val isPresent = isAlreadyPresent || isMarkingPresent
                            val isProcessing = processingIds.contains(item.attendee.id)
                            
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
                                                val threshold = itemWidth * 0.25f
                                                val maxSwipe = itemWidth * 0.30f
                                                
                                                detectHorizontalDragGestures(
                                                    onDragEnd = {
                                                        if (isArmed) {
                                                            processingIds = processingIds + item.attendee.id
                                                            processingState = "REMOVE"
                                                            scope.launch {
                                                                delay(100)
                                                                viewModel.removeFromQueue(item.attendee.id)
                                                            }
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
                                    ) {
                                        QueueListItem(
                                            item = item,
                                            isPresent = isPresent,
                                            textScale = textScale,
                                            onToggle = { 
                                                if (swipeOffset.value == 0f) {
                                                    scope.launch {
                                                        viewModel.toggleLater(item.attendee.id, item.isLater) 
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    
                                    if (isProcessing) {
                                        val flashAlpha by animateFloatAsState(
                                            targetValue = 1f,
                                            animationSpec = tween(durationMillis = 100),
                                            label = "flashAlpha"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .alpha(flashAlpha)
                                                .background(
                                                    when(processingState) {
                                                        "PRESENT" -> PastelGreen
                                                        "REMOVE" -> MaterialTheme.colorScheme.errorContainer
                                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                                    }
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Queue") },
            text = { Text("Clear all items or keep those set aside for later?") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch { viewModel.clearReadyQueue() }
                        showClearDialog = false
                    }) { Text("Keep") }
                    
                    TextButton(onClick = {
                        scope.launch { viewModel.clearQueue() }
                        showClearDialog = false
                    }) { Text("Clear All") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun QueueListItem(
    item: QueueItem,
    isPresent: Boolean,
    textScale: Float,
    onToggle: () -> Unit
) {
    val alpha = if (item.isLater) 0.5f else 1f
    val avatarSize = 40.dp * textScale

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = if (item.isLater) {
            Color(0xFFF2F0F7)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        ListItem(
            modifier = Modifier.alpha(alpha),
            headlineContent = {
                Text(
                    text = item.attendee.shortName ?: item.attendee.fullName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * textScale
                    )
                )
            },
            supportingContent = {
                if (item.attendee.shortName != null) {
                    Text(
                        text = item.attendee.fullName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * textScale
                        )
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPresent) {
                        AppIcon(
                            resourceId = AppIcons.PersonCheck, 
                            contentDescription = "Already Present", 
                            tint = DeepGreen,
                            modifier = Modifier.size(20.dp * textScale)
                        )
                    }
                    if (item.isLater) {
                        if (isPresent) Spacer(modifier = Modifier.width(8.dp))
                        AppIcon(
                            resourceId = AppIcons.PushPin, 
                            contentDescription = "Later", 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp * textScale)
                        )
                    }
                }
            },
            leadingContent = {
                Surface(
                    shape = CircleShape,
                    color = if (!item.isLater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(avatarSize)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!item.isLater) {
                            AppIcon(
                                resourceId = AppIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(avatarSize * 0.6f)
                            )
                        } else {
                            Text(
                                text = (item.attendee.shortName ?: item.attendee.fullName).take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize * textScale
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
