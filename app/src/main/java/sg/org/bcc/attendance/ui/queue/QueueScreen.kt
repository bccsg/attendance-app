package sg.org.bcc.attendance.ui.queue

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.repository.QueueItem
import sg.org.bcc.attendance.ui.components.HoldToActivateButton
import sg.org.bcc.attendance.util.EventSuggester
import sg.org.bcc.attendance.ui.theme.DeepGreen
import sg.org.bcc.attendance.ui.theme.PastelGreen
import sg.org.bcc.attendance.ui.components.AppIcon
import sg.org.bcc.attendance.ui.components.AppIcons
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onActionComplete: (String) -> Unit = {},
    currentEventTitle: String = EventSuggester.suggestNextEventTitle(),
    textScale: Float = 1.0f
) {
    val items by viewModel.queueItems.collectAsState()
    val presentIds by viewModel.presentIds.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    // Track items being removed for animation
    var processingIds by remember { mutableStateOf(setOf<String>()) }
    var processingState by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentEventTitle) {
        viewModel.setEventTitle(currentEventTitle)
    }

    val activeCount = items.count { !it.isExcluded }
    val excludedCount = items.count { it.isExcluded }

    val sheetAlpha by animateFloatAsState(
        targetValue = if (showClearDialog) 0.38f else 1f,
        label = "sheetAlpha"
    )

    fun handleSync(state: String) {
        val activeIds = items.filter { !it.isExcluded }.map { it.attendee.id }.toSet()
        processingIds = activeIds
        processingState = state
        
        scope.launch {
            delay(500) // 100ms flash + 400ms fade/shrink
            viewModel.syncQueue(currentEventTitle, state)
            processingIds = emptySet()
            processingState = null
            
            val message = if (state == "PRESENT") {
                "Marked $activeCount present"
            } else {
                "Marked $activeCount pending"
            }
            
            if (excludedCount > 0) {
                snackbarHostState.showSnackbar(message)
            } else {
                onActionComplete(message)
            }
        }
    }

    Scaffold(
        modifier = Modifier.alpha(sheetAlpha),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(resourceId = AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { 
                            if (activeCount == 0 && excludedCount > 0) {
                                viewModel.clearQueue()
                            } else {
                                showClearDialog = true 
                            }
                        },
                        enabled = items.isNotEmpty()
                    ) {
                        AppIcon(resourceId = AppIcons.ClearQueue, contentDescription = "Clear Queue")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Ready Chip
                        Surface(
                            color = if (activeCount > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(
                                    resourceId = AppIcons.Groups,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (activeCount > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$activeCount ready",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (activeCount > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // Set Aside Chip
                        Surface(
                            color = if (excludedCount > 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(
                                    resourceId = AppIcons.PushPin,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (excludedCount > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$excludedCount set aside",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (excludedCount > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mark Pending (34% width)
                        HoldToActivateButton(
                            iconResId = AppIcons.PersonCancel,
                            onActivate = { handleSync("ABSENT") },
                            width = Dp.Unspecified,
                            height = 64.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(0.34f),
                            enabled = activeCount > 0 && processingIds.isEmpty()
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))

                        // Mark Present (66% width)
                        HoldToActivateButton(
                            iconResId = AppIcons.PersonCheck,
                            onActivate = { handleSync("PRESENT") },
                            width = Dp.Unspecified,
                            height = 64.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(0.66f),
                            enabled = activeCount > 0 && processingIds.isEmpty()
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear Queue") },
                text = { 
                    if (excludedCount > 0) {
                        Text("There are attendees that are set aside, do you want to clear them as well?")
                    } else {
                        Text("Are you sure you want to clear the queue?")
                    }
                },
                confirmButton = {
                    Row {
                        if (excludedCount > 0) {
                            TextButton(onClick = {
                                viewModel.clearActiveQueue()
                                showClearDialog = false
                            }) {
                                Text("Keep")
                            }
                        }
                        TextButton(onClick = {
                            viewModel.clearQueue()
                            showClearDialog = false
                        }) {
                            Text("Clear")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Queue is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(items, key = { it.attendee.id }) { item ->
                    val isPresent = presentIds.contains(item.attendee.id)
                    val isProcessing = processingIds.contains(item.attendee.id)
                    
                    var itemWidth by remember { mutableFloatStateOf(0f) }
                    val swipeOffset = remember { Animatable(0f) }
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
                        ) {
                            // Background Layer
                            val alignment = if (swipeOffset.value > 0) Alignment.CenterStart else Alignment.CenterEnd
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = alignment
                            ) {
                                val iconColor = if (isArmed) MaterialTheme.colorScheme.error 
                                               else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.38f)
                                AppIcon(
                                    resourceId = AppIcons.GroupRemove,
                                    contentDescription = "Remove from Queue",
                                    tint = iconColor,
                                    modifier = Modifier.size(32.dp * textScale)
                                )
                            }

                            // Foreground Layer (Swipable)
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                                    .pointerInput(itemWidth) {
                                        if (itemWidth <= 0) return@pointerInput
                                        val threshold = itemWidth * 0.25f
                                        val maxSwipe = itemWidth * 0.30f
                                        
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                if (abs(swipeOffset.value) >= threshold) {
                                                    viewModel.removeFromQueue(item.attendee.id)
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
                                            viewModel.toggleExclusion(item.attendee.id, item.isExcluded) 
                                        }
                                    }
                                )
                            }
                            
                            // Flash overlay (for commit actions)
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
                                            if (processingState == "PRESENT") PastelGreen 
                                            else MaterialTheme.colorScheme.secondaryContainer
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QueueListItem(
    item: QueueItem,
    isPresent: Boolean,
    textScale: Float,
    onToggle: () -> Unit
) {
    val alpha = if (item.isExcluded) 0.5f else 1f
    val photoSize = 40.dp * textScale
    val verticalPadding = (8.dp * textScale).coerceAtLeast(8.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        color = if (item.isExcluded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
    ) {
        ListItem(
            modifier = Modifier.padding(vertical = verticalPadding - 8.dp),
            headlineContent = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.attendee.shortName ?: item.attendee.fullName,
                        modifier = Modifier.alpha(alpha),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize * textScale
                        )
                    )
                }
            },
            supportingContent = {
                if (item.attendee.shortName != null) {
                    Text(
                        text = item.attendee.fullName,
                        modifier = Modifier.alpha(alpha),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * textScale
                        )
                    )
                }
            },
            trailingContent = {
                if (isPresent) {
                    AppIcon(
                        resourceId = AppIcons.PersonCheck,
                        contentDescription = "Already Present",
                        modifier = Modifier.size(20.dp * textScale),
                        tint = DeepGreen.copy(alpha = if (item.isExcluded) 0.3f else 0.6f)
                    )
                }
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(photoSize)
                        .clip(CircleShape)
                        .alpha(alpha)
                        .background(
                            if (!item.isExcluded) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.isExcluded) {
                        AppIcon(
                            resourceId = AppIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(photoSize * 0.6f)
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
        )
    }
}
