package sg.org.bcc.attendance.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.ui.queue.QueueScreen
import sg.org.bcc.attendance.ui.theme.DeepGreen
import sg.org.bcc.attendance.ui.theme.PastelGreen
import sg.org.bcc.attendance.ui.components.AppIcon
import sg.org.bcc.attendance.ui.components.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainListScreen(
    viewModel: MainListViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val attendees by viewModel.attendees.collectAsState()
    val queueCount by viewModel.queueCount.collectAsState()
    val showPresent by viewModel.showPresent.collectAsState()
    val showAbsent by viewModel.showAbsent.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val syncPending by viewModel.syncPending.collectAsState()
    val presentIds by viewModel.presentIds.collectAsState()
    val pendingIds by viewModel.pendingIds.collectAsState()
    val queueIds by viewModel.queueIds.collectAsState()
    val presentCategoryCount by viewModel.presentCategoryCount.collectAsState()
    val pendingCategoryCount by viewModel.pendingCategoryCount.collectAsState()
    val currentEventTitle by viewModel.currentEventTitle.collectAsState()
    val availableEvents by viewModel.availableEvents.collectAsState()
    val textScale by viewModel.textScale.collectAsState()

    val isSelectionMode = selectedIds.isNotEmpty()
    var showQueueSheet by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = sheetState,
            dragHandle = null,
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = 0.32f),
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(56.dp))
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BottomSheetDefaults.DragHandle()
                        }
                        QueueScreen(
                            onBack = { showQueueSheet = false },
                            onActionComplete = { message ->
                                showQueueSheet = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            currentEventTitle = currentEventTitle,
                            textScale = textScale
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = if (isSelectionMode) MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.surface,
                tonalElevation = if (isSelectionMode) 0.dp else 2.dp
            ) {
                Column {
                    if (isDemoMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "DEMO DATA - Sync master list to replace",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    TopAppBar(
                        navigationIcon = {
                            if (isSelectionMode) {
                                IconButton(onClick = viewModel::clearSelection) {
                                    AppIcon(resourceId = AppIcons.Close, contentDescription = "Clear Selection")
                                }
                            }
                        },
                        title = {
                            Column {
                                Text(if (isSelectionMode) "${selectedIds.size} Selected" else "Attendance")
                                if (!isSelectionMode) {
                                    Text(
                                        text = currentEventTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            var showMenu by remember { mutableStateOf(false) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelectionMode) {
                                    IconButton(onClick = {
                                        if (selectedIds.isNotEmpty()) {
                                            viewModel.confirmSelection()
                                            showQueueSheet = true
                                        }
                                    }) {
                                        AppIcon(resourceId = AppIcons.GroupAdd, contentDescription = "Add to Queue")
                                    }
                                } else {
                                    IconButton(onClick = viewModel::onSyncMasterList) {
                                        val syncIcon = if (isDemoMode) AppIcons.CloudOff 
                                                         else if (syncPending) AppIcons.CloudUpload 
                                                         else AppIcons.CloudDone
                                        AppIcon(
                                            resourceId = syncIcon,
                                            contentDescription = "Sync Master List",
                                            tint = if (isDemoMode) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                   else if (syncPending) MaterialTheme.colorScheme.error 
                                                   else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { showMenu = true }) {
                                        AppIcon(resourceId = AppIcons.MoreVert, contentDescription = "More Options")
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        availableEvents.forEach { event ->
                                            DropdownMenuItem(
                                                text = { Text(event) },
                                                onClick = {
                                                    viewModel.onSwitchEvent(event)
                                                    showMenu = false
                                                }
                                            )
                                        }
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("New Event...") },
                                            onClick = {
                                                viewModel.onCreateEvent("special event")
                                                showMenu = false
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { 
                                                Text(text = if (textScale == 1.0f) "Large Text" else "Normal Text") 
                                            },
                                            onClick = {
                                                viewModel.toggleTextScale()
                                                showMenu = false
                                            },
                                            leadingIcon = {
                                                AppIcon(resourceId = AppIcons.TextFields, contentDescription = null)
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.ime)
                    .height(if (isSearchActive) 80.dp else 72.dp)
                    .pointerInput(isSelectionMode) {
                        if (isSelectionMode) return@pointerInput
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -20) { // Significant swipe up
                                showQueueSheet = true
                            }
                        }
                    }
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .padding(horizontal = 8.dp),
                            placeholder = { Text("Search attendees...") },
                            leadingIcon = { AppIcon(resourceId = AppIcons.PersonSearch, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { 
                                    isSearchActive = false 
                                    viewModel.onSearchQueryChange("")
                                }) {
                                    AppIcon(resourceId = AppIcons.Close, contentDescription = "Close Search")
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Search
                            IconButton(onClick = { isSearchActive = true }) {
                                AppIcon(resourceId = AppIcons.PersonSearch, contentDescription = "Search")
                            }

                            // Center: Filter Chips
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.weight(1f)
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (presentCategoryCount > 0) {
                                            Badge { Text(presentCategoryCount.toString()) }
                                        }
                                    }
                                ) {
                                    FilterChip(
                                        selected = showPresent,
                                        onClick = { viewModel.onShowPresentToggle() },
                                        label = { Text("Present") },
                                        enabled = presentCategoryCount > 0,
                                        leadingIcon = {
                                            AppIcon(
                                                resourceId = if (showPresent) AppIcons.Visibility else AppIcons.VisibilityOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                BadgedBox(
                                    badge = {
                                        if (pendingCategoryCount > 0) {
                                            Badge { Text(pendingCategoryCount.toString()) }
                                        }
                                    }
                                ) {
                                    FilterChip(
                                        selected = showAbsent,
                                        onClick = { viewModel.onShowAbsentToggle() },
                                        label = { Text("Pending") },
                                        enabled = pendingCategoryCount > 0,
                                        leadingIcon = {
                                            AppIcon(
                                                resourceId = if (showAbsent) AppIcons.Visibility else AppIcons.VisibilityOff,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                            }

                            // Right: Queue Launcher with dynamic count icons
                            val queueIcon = when (queueCount) {
                                0 -> AppIcons.Filter.None
                                1 -> AppIcons.Filter.One
                                2 -> AppIcons.Filter.Two
                                3 -> AppIcons.Filter.Three
                                4 -> AppIcons.Filter.Four
                                5 -> AppIcons.Filter.Five
                                6 -> AppIcons.Filter.Six
                                7 -> AppIcons.Filter.Seven
                                8 -> AppIcons.Filter.Eight
                                9 -> AppIcons.Filter.Nine
                                else -> AppIcons.Filter.NinePlus
                            }
                            IconButton(onClick = { showQueueSheet = true }) {
                                AppIcon(resourceId = queueIcon, contentDescription = "View Queue")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            items(attendees, key = { it.id }) { attendee ->
                val isSelected = selectedIds.contains(attendee.id)
                val isPresent = presentIds.contains(attendee.id)
                val isPending = pendingIds.contains(attendee.id)
                val isInQueue = queueIds.contains(attendee.id)
                AttendeeListItem(
                    attendee = attendee,
                    searchQuery = searchQuery,
                    textScale = textScale,
                    isSelected = isSelected,
                    isPresent = isPresent,
                    isPending = isPending,
                    isInQueue = isInQueue,
                    isSelectionMode = isSelectionMode,
                    onClick = {
                        if (isSelectionMode) {
                            viewModel.toggleSelection(attendee.id)
                        }
                    },
                    onLongClick = {
                        if (!isSelectionMode) {
                            viewModel.enterSelectionMode(attendee.id)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttendeeListItem(
    attendee: Attendee,
    searchQuery: String,
    textScale: Float,
    isSelected: Boolean,
    isPresent: Boolean,
    isPending: Boolean,
    isInQueue: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val photoSize = 40.dp * textScale
    val verticalPadding = (8.dp * textScale).coerceAtLeast(8.dp)

    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else if (isSelectionMode) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        } else {
            Color.Transparent
        }
    ) {
        ListItem(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(vertical = verticalPadding - 8.dp), 
            headlineContent = {
                Text(
                    text = getHighlightedText(attendee.shortName ?: attendee.fullName, searchQuery),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize * textScale
                    )
                )
            },
            supportingContent = {
                if (attendee.shortName != null) {
                    Text(
                        text = getHighlightedText(attendee.fullName, searchQuery),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * textScale
                        )
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelectionMode && isPresent) {
                        AppIcon(
                            resourceId = AppIcons.PersonCheck,
                            contentDescription = "Already Present",
                            modifier = Modifier.size(20.dp * textScale),
                            tint = DeepGreen.copy(alpha = 0.6f)
                        )
                        if (isInQueue) Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (isInQueue) {
                        AppIcon(
                            resourceId = AppIcons.BookmarkAdded,
                            contentDescription = "In Queue",
                            modifier = Modifier.size(20.dp * textScale),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(photoSize)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary 
                            else if (!isSelectionMode && isPresent) PastelGreen
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { 
                            if (isSelectionMode) {
                                onClick()
                            } else {
                                onLongClick()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelectionMode) {
                        if (isSelected) {
                            AppIcon(
                                resourceId = AppIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(photoSize * 0.6f)
                            )
                        } else {
                            Text(
                                text = (attendee.shortName ?: attendee.fullName).take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize * textScale
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (isPresent) {
                        AppIcon(
                            resourceId = AppIcons.PersonCheck,
                            contentDescription = null,
                            tint = DeepGreen,
                            modifier = Modifier.size(photoSize * 0.6f)
                        )
                    } else {
                        Text(
                            text = (attendee.shortName ?: attendee.fullName).take(1).uppercase(),
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

@Composable
fun getHighlightedText(text: String, query: String): AnnotatedString {
    if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var currentIndex = 0
        
        while (currentIndex < text.length) {
            val index = lowerText.indexOf(lowerQuery, currentIndex)
            if (index == -1) {
                append(text.substring(currentIndex))
                break
            }
            
            append(text.substring(currentIndex, index))
            pushStyle(SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            ))
            append(text.substring(index, index + query.length))
            pop()
            currentIndex = index + query.length
        }
    }
}
