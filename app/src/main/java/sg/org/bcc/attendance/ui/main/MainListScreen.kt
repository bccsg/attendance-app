package sg.org.bcc.attendance.ui.main

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.ui.queue.QueueScreen
import sg.org.bcc.attendance.ui.theme.DeepGreen
import sg.org.bcc.attendance.ui.theme.PastelGreen
import sg.org.bcc.attendance.ui.theme.Purple40
import sg.org.bcc.attendance.ui.components.AppIcon
import sg.org.bcc.attendance.ui.components.AppIcons
import sg.org.bcc.attendance.ui.components.DateIcon
import sg.org.bcc.attendance.util.EventSuggester
import sg.org.bcc.attendance.util.SetStatusBarIconsColor
import java.time.LocalTime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainListScreen(
    viewModel: MainListViewModel = hiltViewModel(),
    onNavigateToEventManagement: () -> Unit = {}
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val attendees by viewModel.attendees.collectAsState()
    val queueCount by viewModel.queueCount.collectAsState()
    val showPresent by viewModel.showPresent.collectAsState()
    val showAbsent by viewModel.showAbsent.collectAsState()
    val isShowSelectedOnlyMode by viewModel.isShowSelectedOnlyMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val syncPending by viewModel.syncPending.collectAsState()
    val isAuthed by viewModel.isAuthed.collectAsState()
    val hasSyncError by viewModel.hasSyncError.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val cloudProfile by viewModel.cloudProfile.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val showCloudStatusDialog by viewModel.showCloudStatusDialog.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    val requiredDomain = viewModel.requiredDomain
    val presentIds by viewModel.presentIds.collectAsState()
    val pendingIds by viewModel.pendingIds.collectAsState()
    val queueIds by viewModel.queueIds.collectAsState()
    val attendeeGroupsMap by viewModel.attendeeGroupsMap.collectAsState()
    val presentBadgeCount by viewModel.presentBadgeCount.collectAsState()
    val pendingBadgeCount by viewModel.pendingBadgeCount.collectAsState()
    val presentPoolCount by viewModel.presentPoolCount.collectAsState()
    val pendingPoolCount by viewModel.pendingPoolCount.collectAsState()
    val currentEventId by viewModel.currentEventId.collectAsState()
    val currentEvent by viewModel.currentEvent.collectAsState()
    val currentEventTitle by viewModel.currentEventTitle.collectAsState()
    val availableEvents by viewModel.availableEvents.collectAsState()
    val selectedAttendeeForDetail by viewModel.selectedAttendeeForDetail.collectAsState()
    val canNavigateBackInDetail by viewModel.canNavigateBackInDetail.collectAsState()
    val previousAttendeeName by viewModel.previousAttendeeName.collectAsState()
    val detailAttendeeGroups by viewModel.detailAttendeeGroups.collectAsState()
    val groupMembersMap by viewModel.groupMembersMap.collectAsState()
    val showQueueSheet by viewModel.showQueueSheet.collectAsState()
    val fabState by viewModel.fabState.collectAsState()
    val textScale by viewModel.textScale.collectAsState()

    val isSelectionMode = selectedIds.isNotEmpty()
    var isSearchActive by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "SyncPulsing")
    val syncAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SyncAlpha"
    )

    // Ensure status bar content remains white
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    if (selectedAttendeeForDetail != null) {
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.dismissAttendeeDetail()
            },
            sheetState = detailSheetState,
            dragHandle = null,
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = 0.32f),
            tonalElevation = 0.dp
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                floatingActionButton = {
                    val state = fabState
                    if (state is MainListViewModel.FabState.AddAttendee) {
                        ExtendedFloatingActionButton(
                            text = { Text("Queue ${state.name}") },
                            icon = { AppIcon(resourceId = AppIcons.PlaylistAdd, contentDescription = null) },
                            onClick = {
                                viewModel.addAttendeeToQueue(selectedAttendeeForDetail!!.id)
                                viewModel.setShowQueueSheet(true)
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = CircleShape,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                },
                floatingActionButtonPosition = FabPosition.End
            ) { padding ->
                Column(modifier = Modifier.fillMaxWidth().padding(padding).navigationBarsPadding()) {
                    Spacer(modifier = Modifier.height(56.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header area
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                BottomSheetDefaults.DragHandle()
                            }
                            
                            SetStatusBarIconsColor(isLight = false)
                            
                            // Content extends to bottom
                            AttendeeDetailContent(
                                attendee = selectedAttendeeForDetail!!,
                                groups = detailAttendeeGroups,
                                groupMembersMap = groupMembersMap,
                                attendeeGroupsMap = attendeeGroupsMap,
                                textScale = textScale,
                                presentIds = presentIds,
                                queueIds = queueIds,
                                canNavigateBack = canNavigateBackInDetail,
                                previousName = previousAttendeeName,
                                onBack = viewModel::popAttendeeDetail,
                                onAttendeeClick = viewModel::showAttendeeDetail,
                                onAddAttendeeToQueue = {
                                    viewModel.addAttendeeToQueue(selectedAttendeeForDetail!!.id)
                                    viewModel.setShowQueueSheet(true)
                                },
                                onAddGroupToQueue = { groupId ->
                                    viewModel.addGroupToQueue(groupId)
                                    viewModel.setShowQueueSheet(true)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCloudStatusDialog) {
        SetStatusBarIconsColor(isLight = false)
        CloudStatusDialog(
            isAuthed = isAuthed,
            cloudProfile = cloudProfile,
            syncProgress = syncProgress,
            isDemoMode = isDemoMode,
            loginError = loginError,
            onLogin = viewModel::onLoginTrigger,
            onLogout = viewModel::onLogout,
            onDismiss = { viewModel.setShowCloudStatusDialog(false) },
            onManualSync = viewModel::doManualSync
        )
    }

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.setShowQueueSheet(false)
            },
            sheetState = sheetState,
            dragHandle = null,
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = 0.32f),
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                Spacer(modifier = Modifier.height(56.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            BottomSheetDefaults.DragHandle()
                        }
                        
                        SetStatusBarIconsColor(isLight = false)
                                                QueueScreen(
                                                    onBack = {
                                                        viewModel.setShowQueueSheet(false)
                                                    },
                                                    onActionComplete = { message: String, shouldClose: Boolean ->
                                                        if (shouldClose) {
                                                            viewModel.setShowQueueSheet(false)
                                                        }
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar(message)
                                                        }
                                                    },
                                                    currentEventId = currentEventId,
                                                    textScale = textScale
                                                )                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (!isSearchActive) {
                    if (isSelectionMode) {
                        FloatingActionButton(
                            modifier = Modifier.padding(bottom = 16.dp),
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    viewModel.confirmSelection()
                                    viewModel.setShowQueueSheet(true)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = CircleShape
                        ) {
                            AppIcon(resourceId = AppIcons.PlaylistAdd, contentDescription = "Queue selected")
                        }
                    } else {
                        ExtendedFloatingActionButton(
                            modifier = Modifier.padding(bottom = 16.dp),
                            text = { Text("Scan QR") },
                            icon = { AppIcon(resourceId = AppIcons.QrCodeScanner, contentDescription = null) },
                            onClick = {
                                scope.launch { snackbarHostState.showSnackbar("QR Scanner coming soon") }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = CircleShape
                        )
                    }
                }
            },
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 4.dp
                ) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        TopAppBar(
                            navigationIcon = {
                                if (isSelectionMode) {
                                    IconButton(onClick = viewModel::clearSelection) {
                                        AppIcon(resourceId = AppIcons.Close, contentDescription = "Clear Selection", tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            },
                            title = {
                                if (isSelectionMode) {
                                    Text("${selectedIds.size} Selected", color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    currentEvent?.let { event ->
                                        val parts = event.title.split(" ", limit = 3)
                                        val date = if (parts.isNotEmpty()) EventSuggester.parseDate(parts[0]) else null
                                        val timeStr = if (parts.size > 1) parts[1] else "0000"
                                        val name = if (parts.size > 2) parts[2] else "Unnamed Event"
                                        val time = try {
                                            LocalTime.of(timeStr.take(2).toInt(), timeStr.takeLast(2).toInt())
                                        } catch (e: Exception) {
                                            LocalTime.MIDNIGHT
                                        }
                                        val formattedTime = time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { onNavigateToEventManagement() }
                                        ) {
                                            DateIcon(date = date, textScale = 0.8f)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = formattedTime,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = event.cloudEventId ?: event.id.take(8),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }
                                        }
                                    } ?: Text("Attendance", color = MaterialTheme.colorScheme.onPrimary)
                                }
                            },
                            actions = {
                                var showMenu by remember { mutableStateOf(false) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelectionMode) {
                                        // Checklist Toggle
                                        IconButton(
                                            onClick = { 
                                                viewModel.toggleShowSelectedOnlyMode()
                                                if (viewModel.isShowSelectedOnlyMode.value) {
                                                    isSearchActive = false
                                                    viewModel.onSearchQueryChange("")
                                                }
                                            },
                                            modifier = if (isShowSelectedOnlyMode) {
                                                Modifier.background(
                                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                                    shape = CircleShape
                                                )
                                            } else Modifier
                                        ) {
                                            AppIcon(
                                                resourceId = AppIcons.Checklist, 
                                                contentDescription = "Show Selected Only",
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = viewModel::onSyncMasterList) {
                                            val syncIcon = when {
                                                hasSyncError -> AppIcons.CloudAlert
                                                isSyncing -> AppIcons.Cloud
                                                isDemoMode || !isAuthed -> AppIcons.CloudOff
                                                else -> AppIcons.CloudDone
                                            }
                                            AppIcon(
                                                resourceId = syncIcon,
                                                contentDescription = "Sync Status",
                                                tint = when {
                                                    hasSyncError -> MaterialTheme.colorScheme.errorContainer
                                                    else -> MaterialTheme.colorScheme.onPrimary
                                                },
                                                modifier = if (isSyncing) Modifier.alpha(syncAlpha) else Modifier
                                            )
                                        }
                                        IconButton(onClick = { showMenu = true }) {
                                            AppIcon(resourceId = AppIcons.MoreVert, contentDescription = "More Options", tint = MaterialTheme.colorScheme.onPrimary)
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Text Size", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) },
                                                onClick = { },
                                                enabled = false
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        RadioButton(
                                                            selected = textScale == 1.0f,
                                                            onClick = null // Handled by item click
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Normal")
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.setTextScale(1.0f)
                                                    showMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        RadioButton(
                                                            selected = textScale > 1.0f,
                                                            onClick = null // Handled by item click
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Large")
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.setTextScale(1.5f)
                                                    showMenu = false
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
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .windowInsetsPadding(WindowInsets.ime)
                        .height(80.dp)
                        .pointerInput(isSelectionMode) {
                            if (isSelectionMode) return@pointerInput
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount < -20) { // Significant swipe up
                                    viewModel.setShowQueueSheet(true)
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
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
                                IconButton(onClick = { 
                                    isSearchActive = true 
                                    viewModel.deactivateShowSelectedOnlyMode()
                                }) {
                                    AppIcon(resourceId = AppIcons.PersonSearch, contentDescription = "Search")
                                }
    
                                // Center: Filter Chips
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val isSelectionMode = selectedIds.isNotEmpty()
                                    val isPresentVisible = showPresent && presentPoolCount > 0
                                    val isPendingVisible = showAbsent && pendingPoolCount > 0
                                    
                                    // No special treatment for only one is set to visible in selection mode
                                    val isBranded = !isSelectionMode && (isPresentVisible != isPendingVisible)
                                    
                                    val brandedChipColors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
    
                                    val defaultChipColors = FilterChipDefaults.filterChipColors(
                                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
    
                                    val presentChipColors = if (isBranded && isPresentVisible) {
                                        brandedChipColors
                                    } else defaultChipColors
    
                                    val pendingChipColors = if (isBranded && isPendingVisible) {
                                        brandedChipColors
                                    } else defaultChipColors
    
                                    val presentBadgeColor = if (isSelectionMode) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
    
                                    val presentBadgeContentColor = if (isSelectionMode) {
                                        MaterialTheme.colorScheme.onSecondary
                                    } else {
                                        MaterialTheme.colorScheme.onError
                                    }
    
                                    val pendingBadgeColor = MaterialTheme.colorScheme.secondary
                                    val pendingBadgeContentColor = MaterialTheme.colorScheme.onSecondary
    
                                    BadgedBox(
                                        badge = {
                                            if (presentBadgeCount > 0) {
                                                Badge(
                                                    containerColor = presentBadgeColor,
                                                    contentColor = presentBadgeContentColor
                                                ) { 
                                                    Text(presentBadgeCount.toString()) 
                                                }
                                            }
                                        }
                                    ) {
                                        FilterChip(
                                            selected = showPresent,
                                            onClick = { viewModel.onShowPresentToggle() },
                                            label = { Text("Present") },
                                            enabled = presentPoolCount > 0,
                                            colors = presentChipColors,
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
                                            if (pendingBadgeCount > 0) {
                                                Badge(
                                                    containerColor = pendingBadgeColor,
                                                    contentColor = pendingBadgeContentColor
                                                ) { 
                                                    Text(pendingBadgeCount.toString()) 
                                                }
                                            }
                                        }
                                    ) {
                                        FilterChip(
                                            selected = showAbsent,
                                            onClick = { viewModel.onShowAbsentToggle() },
                                            label = { Text("Pending") },
                                            enabled = pendingPoolCount > 0,
                                            colors = pendingChipColors,
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
                                IconButton(onClick = { viewModel.setShowQueueSheet(true) }) {
                                    AppIcon(resourceId = queueIcon, contentDescription = "View Queue")
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            LaunchedEffect(attendees, availableEvents) {
                if (attendees.isNotEmpty() && availableEvents.isEmpty()) {
                    onNavigateToEventManagement()
                }
            }
    
            if (attendees.isEmpty() && searchQuery.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        AppIcon(
                            resourceId = AppIcons.PersonSearch,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "No attendees found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Try syncing with the master list to download data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = viewModel::onSyncMasterList,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            AppIcon(resourceId = AppIcons.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Master List")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    items(attendees, key = { it.id }) { attendee ->
                        val isSelected = selectedIds.contains(attendee.id)
                        val isPresent = presentIds.contains(attendee.id)
                        val isPending = pendingIds.contains(attendee.id)
                        val isInQueue = queueIds.contains(attendee.id)
                        val groups = attendeeGroupsMap[attendee.id] ?: emptyList()
    
                        AttendeeListItem(
                            attendee = attendee,
                            searchQuery = searchQuery,
                            textScale = textScale,
                            isSelected = isSelected,
                            isPresent = isPresent,
                            isPending = isPending,
                            isInQueue = queueIds.contains(attendee.id),
                            isSelectionMode = isSelectionMode,
                            groups = groups,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(attendee.id)
                                } else {
                                    viewModel.showAttendeeDetail(attendee)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    viewModel.enterSelectionMode(attendee.id)
                                }
                            },
                            onPhotoClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(attendee.id)
                                } else {
                                    viewModel.enterSelectionMode(attendee.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    
        if (showCloudStatusDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .pointerInput(Unit) {} // Consume touches
            )
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
    groups: List<String> = emptyList(),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    showGroupIcon: Boolean = true, // Added to toggle visibility
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPhotoClick: () -> Unit
) {
    val photoSize = 40.dp * textScale

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else if (isSelectionMode) {
            Color(0xFFF2F0F7)
        } else {
            backgroundColor
        }
    ) {
        ListItem(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = getHighlightedText(attendee.shortName ?: attendee.fullName, searchQuery),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize * textScale
                        )
                    )
                    if (showGroupIcon && groups.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AppIcon(
                            resourceId = AppIcons.Groups,
                            contentDescription = "In Groups",
                            modifier = Modifier.size(16.dp * textScale),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }
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
                Surface(
                    shape = CircleShape,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isPresent -> PastelGreen
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier
                        .size(photoSize)
                        .clip(CircleShape)
                        .clickable { onPhotoClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSelected) {
                            AppIcon(
                                resourceId = AppIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(photoSize * 0.6f)
                            )
                        } else if (isPending) {
                            AppIcon(
                                resourceId = AppIcons.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(photoSize * 0.6f)
                            )
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
                                color = if (isPresent) DeepGreen else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttendeeDetailContent(
    attendee: Attendee,
    groups: List<sg.org.bcc.attendance.data.local.entities.Group>,
    groupMembersMap: Map<String, List<Attendee>>,
    attendeeGroupsMap: Map<String, List<String>>,
    textScale: Float,
    presentIds: Set<String>,
    queueIds: Set<String>,
    canNavigateBack: Boolean = false,
    previousName: String? = null,
    onBack: () -> Unit = {},
    onAttendeeClick: (Attendee) -> Unit,
    onAddAttendeeToQueue: () -> Unit,
    onAddGroupToQueue: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Profile Header area: WHITE
        Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
            if (canNavigateBack && previousName != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBack() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        resourceId = AppIcons.ArrowBack, 
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Return to $previousName",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            ListItem(
                leadingContent = {
                    val isPresent = presentIds.contains(attendee.id)
                    Surface(
                        shape = CircleShape,
                        color = if (isPresent) PastelGreen else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isPresent) {
                                AppIcon(
                                    resourceId = AppIcons.PersonCheck,
                                    contentDescription = null,
                                    tint = DeepGreen,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Text(
                                    text = (attendee.shortName ?: attendee.fullName).take(1).uppercase(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                },
                headlineContent = {
                    Text(
                        text = attendee.fullName,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                supportingContent = {
                    Column {
                        if (attendee.shortName != null) {
                            Text(
                                text = attendee.shortName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ID: ${attendee.id}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            if (queueIds.contains(attendee.id)) {
                                Spacer(modifier = Modifier.width(8.dp))
                                AppIcon(
                                    resourceId = AppIcons.BookmarkAdded,
                                    contentDescription = "In Queue",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            HorizontalDivider()
        }

        // List Area: surfaceContainerLow
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            groups.forEach { group ->
                val members = groupMembersMap[group.groupId]?.filter { it.id != attendee.id } ?: emptyList()
                
                stickyHeader {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${members.size} ${if (members.size == 1) "other" else "others"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { onAddGroupToQueue(group.groupId) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                AppIcon(
                                    resourceId = AppIcons.PlaylistAdd, 
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Queue group",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                items(members, key = { "${group.groupId}_${it.id}" }) { member ->
                    val memberGroups = attendeeGroupsMap[member.id] ?: emptyList()
                    AttendeeListItem(
                        attendee = member,
                        searchQuery = "",
                        textScale = textScale,
                        isSelected = false,
                        isPresent = presentIds.contains(member.id),
                        isPending = false,
                        isInQueue = queueIds.contains(member.id),
                        isSelectionMode = false,
                        groups = memberGroups,
                        backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        showGroupIcon = false, // HIDE GROUP ICON IN DETAIL
                        onClick = { onAttendeeClick(member) },
                        onLongClick = { },
                        onPhotoClick = { onAttendeeClick(member) }
                    )
                }
            }
        }
    }
}

@Composable
fun CloudStatusDialog(
    isAuthed: Boolean,
    cloudProfile: CloudProfile?,
    syncProgress: SyncProgress,
    isDemoMode: Boolean,
    loginError: String? = null,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    onManualSync: () -> Unit
) {
    LaunchedEffect(isAuthed) {
        if (isAuthed) {
            onManualSync()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(resourceId = AppIcons.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cloud Status")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Login Error Section
                if (loginError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(
                                resourceId = AppIcons.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = loginError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Auth Section
                if (isAuthed && cloudProfile != null) {
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
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("Logout")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Not Authenticated", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        if (isDemoMode) {
                            Text(
                                "App is currently in DEMO MODE. Logging in will clear demo data and sync with the cloud master list.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                            Text("Login with Google")
                        }
                    }
                }

                HorizontalDivider()

                // Sync Info Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SyncInfoRow("Pending Sync Jobs", syncProgress.pendingJobs.toString())
                    SyncInfoRow("Next Pull Scheduled", syncProgress.nextScheduledPull?.let { 
                        try {
                            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                        } catch (e: Exception) {
                            "Unknown"
                        }
                    } ?: "None")
                    SyncInfoRow("Last Pull Status", syncProgress.lastPullStatus ?: "Unknown")
                    
                    if (syncProgress.lastErrors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Recent Errors:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        syncProgress.lastErrors.take(3).forEach { error ->
                            Text("- ${error.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
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
            if (isAuthed) {
                TextButton(onClick = onManualSync) {
                    Text("Sync Now")
                }
            }
        }
    )
}

@Composable
fun SyncInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

private fun getHighlightedText(fullText: String, query: String): AnnotatedString {
    if (query.isEmpty() || !fullText.contains(query, ignoreCase = true)) {
        return AnnotatedString(fullText)
    }

    val startIndex = fullText.indexOf(query, ignoreCase = true)
    val endIndex = startIndex + query.length

    return buildAnnotatedString {
        append(fullText.substring(0, startIndex))
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Purple40))
        append(fullText.substring(startIndex, endIndex))
        pop()
        append(fullText.substring(endIndex))
    }
}
