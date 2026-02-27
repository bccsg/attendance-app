package sg.org.bcc.attendance.ui.main

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.ui.queue.QueueScreen
import sg.org.bcc.attendance.ui.theme.DeepGreen
import sg.org.bcc.attendance.ui.theme.PastelGreen
import sg.org.bcc.attendance.sync.*
import sg.org.bcc.attendance.ui.theme.Purple40
import sg.org.bcc.attendance.ui.components.AppIcon
import sg.org.bcc.attendance.ui.components.AppIcons
import sg.org.bcc.attendance.ui.components.AttendeeListItem
import sg.org.bcc.attendance.ui.components.RotatingSyncIcon
import sg.org.bcc.attendance.ui.components.DateIcon
import sg.org.bcc.attendance.ui.components.pinchToScale
import sg.org.bcc.attendance.util.EventSuggester
import sg.org.bcc.attendance.util.qr.QrInfo
import java.time.LocalTime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainListScreen(
    viewModel: MainListViewModel = hiltViewModel(),
    onNavigateToEventManagement: () -> Unit = {},
    onNavigateToSyncLogs: () -> Unit = {},
    onNavigateToQrScanner: () -> Unit = {}
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
    val authState by viewModel.authState.collectAsState()
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
    val activeQrInfo by viewModel.activeQrInfo.collectAsState()
    val canNavigateBackInDetail by viewModel.canNavigateBackInDetail.collectAsState()
    val previousAttendeeName by viewModel.previousAttendeeName.collectAsState()
    val detailAttendeeGroups by viewModel.detailAttendeeGroups.collectAsState()
    val groupMembersMap by viewModel.groupMembersMap.collectAsState()
    val showQueueSheet by viewModel.showQueueSheet.collectAsState()
    val showScannerSheet by viewModel.showScannerSheet.collectAsState()
    val fabState by viewModel.fabState.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val textScale by viewModel.textScale.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    val totalAttendeesCount by viewModel.totalAttendeesCount.collectAsState()
    val totalGroupsCount by viewModel.totalGroupsCount.collectAsState()
    val attendeesWithGroupCount by viewModel.attendeesWithGroupCount.collectAsState()
    val missingCloudAttendeesCount by viewModel.missingCloudAttendeesCount.collectAsState()
    val missingCloudGroupsCount by viewModel.missingCloudGroupsCount.collectAsState()
    val missingCloudEventsCount by viewModel.missingCloudEventsCount.collectAsState()

        val isSelectionMode = selectedIds.isNotEmpty()
        var isSearchActive by remember { mutableStateOf(false) }
        
                val focusRequester = remember { FocusRequester() }
                val activeSheet by viewModel.activeSheet.collectAsState()
                val isAnySheetActive = activeSheet != SheetType.NONE
            
                val scaffoldState = rememberBottomSheetScaffoldState(
                    bottomSheetState = rememberStandardBottomSheetState(
                        initialValue = SheetValue.Hidden,
                        skipHiddenState = false,
                        confirmValueChange = { newValue ->
                            if (isAnySheetActive && (newValue == SheetValue.Hidden || newValue == SheetValue.PartiallyExpanded)) {
                                viewModel.dismissAllSheets()
                            }
                            true
                        }
                    )
                )
                        val snackbarHostState = remember { SnackbarHostState() }
        
        val scope = rememberCoroutineScope()
    
        val isAdded = remember(selectedAttendeeForDetail, queueIds) {
            selectedAttendeeForDetail?.id?.let { queueIds.contains(it) } ?: false
        }
        
        var lastBackPressTime by remember { mutableStateOf(0L) }
        val context = LocalContext.current
    
        val fullyQueuedGroups = remember(groupMembersMap, queueIds) {
            groupMembersMap.filter { (_, members) -> 
                members.isNotEmpty() && members.all { queueIds.contains(it.id) } 
            }.keys
        }
    
        var lastQueuedGroupId by remember { mutableStateOf<String?>(null) }
        var wasIndividualAddedJustNow by remember { mutableStateOf(false) }
        
        var showAddedAnimation by remember { mutableStateOf(false) }
        var animatingGroups by remember { mutableStateOf(setOf<String>()) }
    
        // Reset interaction state when attendee changes
        LaunchedEffect(selectedAttendeeForDetail) {
            lastQueuedGroupId = null
            wasIndividualAddedJustNow = false
            showAddedAnimation = false
            animatingGroups = emptySet()
        }
    
                    BackHandler(enabled = isAnySheetActive) {
                        viewModel.dismissAllSheets()
                    }

                    BackHandler(enabled = isSearchActive && !isAnySheetActive) {
                        isSearchActive = false
                        viewModel.onSearchQueryChange("")
                    }

                    BackHandler(enabled = isSelectionMode && !isAnySheetActive && !isSearchActive) {
                        viewModel.clearSelection()
                    }

                    BackHandler(enabled = !isAnySheetActive && !isSelectionMode && !isSearchActive) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBackPressTime < 2000) {
                            (context as? Activity)?.finish()
                        } else {
                            lastBackPressTime = currentTime
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Press back again to exit",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }
                
                        LaunchedEffect(activeSheet) {
                            if (isAnySheetActive) {
                                scaffoldState.bottomSheetState.expand()
                            } else {
                                scaffoldState.bottomSheetState.hide()
                            }
                        }
                                                    
                                            LaunchedEffect(isAdded, fullyQueuedGroups, lastQueuedGroupId, wasIndividualAddedJustNow) {
            // Animation for Groups - only if user just clicked this group
            if (lastQueuedGroupId != null && fullyQueuedGroups.contains(lastQueuedGroupId)) {
                val groupId = lastQueuedGroupId!!
                animatingGroups = setOf(groupId)
                delay(1000)
                animatingGroups = emptySet()
                lastQueuedGroupId = null
                viewModel.dismissAttendeeDetail()
                viewModel.setShowQueueSheet(true)
            } 
            // Animation for Individual (FAB) - only if user just clicked the FAB
            else if (wasIndividualAddedJustNow && isAdded && selectedAttendeeForDetail != null) {
                showAddedAnimation = true
                delay(1000)
                showAddedAnimation = false
                wasIndividualAddedJustNow = false
                
                if (detailAttendeeGroups.isEmpty()) {
                    viewModel.dismissAttendeeDetail()
                }
            }
        }
    
        LaunchedEffect(isSearchActive) {
            if (isSearchActive) {
                focusRequester.requestFocus()
            }
        }
    
        LaunchedEffect(Unit) {
            viewModel.qrMessageEvent.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }
    
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenHeight = maxHeight
                val density = LocalDensity.current
                val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
                val availableSheetHeight = screenHeight - 56.dp - statusBarHeight
                    BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = 0.dp,
                sheetDragHandle = null,
                sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                sheetTonalElevation = 0.dp,
                sheetShadowElevation = 8.dp,
                sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            sheetContent = {
                                MainBottomSheetContent(
                                    activeSheet = activeSheet,
                                    availableHeight = availableSheetHeight,
                                    selectedAttendeeForDetail = selectedAttendeeForDetail,
                                    detailAttendeeGroups = detailAttendeeGroups,
                                    activeQrInfo = activeQrInfo,
                                    groupMembersMap = groupMembersMap,
                                    attendeeGroupsMap = attendeeGroupsMap,
                                    textScale = textScale,
                                    presentIds = presentIds,
                                    queueIds = queueIds,
                                    canNavigateBackInDetail = canNavigateBackInDetail,
                                    previousAttendeeName = previousAttendeeName,
                                    showAddedAnimation = showAddedAnimation,
                                    animatingGroups = animatingGroups,
                                    fabState = fabState,
                                    currentEventId = currentEventId,
                                    onDismiss = viewModel::dismissAllSheets,
                                    onPopAttendeeDetail = viewModel::popAttendeeDetail,
                                    onShowAttendeeDetail = viewModel::showAttendeeDetail,
                                    onAddAttendeeToQueue = { attendeeId ->
                                        wasIndividualAddedJustNow = true
                                        viewModel.addAttendeeToQueue(attendeeId)
                                    },
                                    onQrSelected = viewModel::onQrSelected,
                                    onAddGroupToQueue = { groupId ->
                                        lastQueuedGroupId = groupId
                                        viewModel.addGroupToQueue(groupId)
                                    },
                                    onSetShowQueueSheet = viewModel::setShowQueueSheet,
                                    onSetShowScannerSheet = viewModel::setShowScannerSheet,
                                    onProcessQrResult = viewModel::processQrResult,
                                    onTextScaleChange = viewModel::setTextScale,
                                    onShowSnackbar = { scope.launch { snackbarHostState.showSnackbar(it) } }
                                )
                            }
                
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background Content (The Scaffold we had before)
                    Scaffold(
                        snackbarHost = { },
                        floatingActionButton = {
                            if (!isSearchActive) {
                                val fabContainerColor by animateColorAsState(
                                    targetValue = if (isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                    label = "FABContainerColor"
                                )
                                val fabContentColor by animateColorAsState(
                                    targetValue = if (isSelectionMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                    label = "FABContentColor"
                                )

                                ExtendedFloatingActionButton(
                                    modifier = Modifier.padding(bottom = 24.dp),
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (selectedIds.isNotEmpty()) {
                                                viewModel.confirmSelection()
                                                viewModel.setShowQueueSheet(true)
                                            }
                                        } else {
                                            viewModel.setShowScannerSheet(true)
                                        }
                                    },
                                    containerColor = fabContainerColor,
                                    contentColor = fabContentColor,
                                    shape = CircleShape,
                                    icon = {
                                        AnimatedContent(
                                            targetState = if (isSelectionMode) AppIcons.PlaylistAdd else AppIcons.QrCodeScanner,
                                            transitionSpec = {
                                                (fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                                                    .togetherWith(fadeOut(animationSpec = tween(90)))
                                            },
                                            label = "FABIcon"
                                        ) { iconRes ->
                                            AppIcon(resourceId = iconRes, contentDescription = null)
                                        }
                                    },
                                    text = {
                                        AnimatedContent(
                                            targetState = if (isSelectionMode) "Queue ${selectedIds.size} selected" else "Scan QR",
                                            transitionSpec = {
                                                (fadeIn(animationSpec = tween(220, delayMillis = 90)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(220, delayMillis = 90)))
                                                    .togetherWith(fadeOut(animationSpec = tween(90)))
                                            },
                                            label = "FABText"
                                        ) { textValue ->
                                            Text(textValue)
                                        }
                                    }
                                )
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
                                                        RotatingSyncIcon(
                                                            resourceId = syncProgress.cloudStatusIcon,
                                                            contentDescription = "Sync Status",
                                                            tint = MaterialTheme.colorScheme.onPrimary,
                                                            shouldRotate = syncProgress.shouldRotate
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
                                                            text = { Text("Sort By", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) },
                                                            onClick = { },
                                                            enabled = false
                                                        )
                                                        DropdownMenuItem(
                                                            text = {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    RadioButton(
                                                                        selected = sortMode == SortMode.NAME_ASC,
                                                                        onClick = null // Handled by item click
                                                                    )
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Text("Name (A-Z)")
                                                                }
                                                            },
                                                            onClick = {
                                                                viewModel.setSortMode(SortMode.NAME_ASC)
                                                                showMenu = false
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    RadioButton(
                                                                        selected = sortMode == SortMode.RECENT_UPDATED,
                                                                        onClick = null // Handled by item click
                                                                    )
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Text("Recently Updated")
                                                                }
                                                            },
                                                            onClick = {
                                                                viewModel.setSortMode(SortMode.RECENT_UPDATED)
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
                        LaunchedEffect(attendees, availableEvents, isSyncing) {
                            if (!isSyncing && attendees.isNotEmpty() && availableEvents.isEmpty()) {
                                onNavigateToEventManagement()
                            }
                        }
                
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                        ) {
                            if (attendees.isEmpty() && searchQuery.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
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
                                        .pinchToScale(textScale, viewModel::setTextScale)
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
                                            isQueued = isInQueue,
                                            isSelectionMode = isSelectionMode,
                                            isGrouped = groups.isNotEmpty(),
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
                                            onAvatarClick = {
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
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 80.dp)
                    )
    
                                                                                                                                                                    // Custom Scrim for BottomSheetScaffold
                                                                                                                                                                    AnimatedVisibility(
                                                                                                                                                                        visible = isAnySheetActive,
                                                                                                                                                                        enter = fadeIn(),
                                                                                                                                                                        exit = fadeOut()
                                                                                                                                                                    ) {
                                                                                                                                                                        Box(
                                                                                                                                                                            modifier = Modifier
                                                                                                                                                                                .fillMaxSize()
                                                                                                                                                                                .background(Color.Black.copy(alpha = 0.32f))
                                                                                                                                                                                                            .pointerInput(Unit) {
                                                                                                                                                                                                                detectTapGestures {
                                                                                                                                                                                                                    viewModel.dismissAllSheets()
                                                                                                                                                                                                                }
                                                                                                                                                                                                            }
                                                                                                                                                                                
                                                                                                                                                                        )
                                                                                                                                                                    }
                                                                                                                                                    
                                                                                                                        }
            }
        }

    if (showCloudStatusDialog) {
        CloudStatusDialog(
            isAuthed = isAuthed,
            authState = authState,
            cloudProfile = cloudProfile,
            syncProgress = syncProgress,
            isDemoMode = isDemoMode,
            isOnline = isOnline,
            loginError = loginError,
            totalAttendeesCount = totalAttendeesCount,
            totalGroupsCount = totalGroupsCount,
            attendeesWithGroupCount = attendeesWithGroupCount,
            missingCloudAttendeesCount = missingCloudAttendeesCount,
            missingCloudGroupsCount = missingCloudGroupsCount,
            missingCloudEventsCount = missingCloudEventsCount,
            onLogin = viewModel::onLoginTrigger,
            onLogout = viewModel::onLogout,
            onDismiss = { viewModel.setShowCloudStatusDialog(false) },
            onManualSync = viewModel::doManualSync,
            onShowLogs = onNavigateToSyncLogs,
            onResolveMissing = viewModel::onNavigateToResolutionScreen
        )
    }
}

@Composable
fun MainBottomSheetContent(
    activeSheet: SheetType,
    availableHeight: androidx.compose.ui.unit.Dp,
    selectedAttendeeForDetail: Attendee?,
    detailAttendeeGroups: List<sg.org.bcc.attendance.data.local.entities.Group>,
    activeQrInfo: QrInfo?,
    groupMembersMap: Map<String, List<Attendee>>,
    attendeeGroupsMap: Map<String, List<String>>,
    textScale: Float,
    presentIds: Set<String>,
    queueIds: Set<String>,
    canNavigateBackInDetail: Boolean,
    previousAttendeeName: String?,
    showAddedAnimation: Boolean,
    animatingGroups: Set<String>,
    fabState: MainListViewModel.FabState,
    currentEventId: String?,
    onDismiss: () -> Unit,
    onPopAttendeeDetail: () -> Unit,
    onShowAttendeeDetail: (Attendee) -> Unit,
    onAddAttendeeToQueue: (String) -> Unit,
    onQrSelected: (Attendee, sg.org.bcc.attendance.data.local.entities.Group?) -> Unit,
    onAddGroupToQueue: (String) -> Unit,
    onSetShowQueueSheet: (Boolean) -> Unit,
    onSetShowScannerSheet: (Boolean) -> Unit,
    onProcessQrResult: (String) -> Boolean,
    onTextScaleChange: (Float) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = availableHeight)
            .navigationBarsPadding()
    ) {
        when (activeSheet) {
            SheetType.ATTENDEE_DETAIL -> {
                if (selectedAttendeeForDetail != null) {
                    val isGroupsEmpty = detailAttendeeGroups.isEmpty()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isGroupsEmpty) Modifier.wrapContentHeight() else Modifier.height(availableHeight))
                    ) {
                        AttendeeDetailContent(
                            attendee = selectedAttendeeForDetail,
                            groups = detailAttendeeGroups,
                            activeQrInfo = activeQrInfo,
                            attendeeName = selectedAttendeeForDetail.shortName ?: selectedAttendeeForDetail.fullName,
                            groupMembersMap = groupMembersMap,
                            attendeeGroupsMap = attendeeGroupsMap,
                            textScale = textScale,
                            onTextScaleChange = onTextScaleChange,
                            presentIds = presentIds,
                            queueIds = queueIds,
                            canNavigateBack = canNavigateBackInDetail,
                            previousName = previousAttendeeName,
                            onBack = onPopAttendeeDetail,
                            onAttendeeClick = onShowAttendeeDetail,
                            onAddAttendeeToQueue = { onAddAttendeeToQueue(selectedAttendeeForDetail.id) },
                            onQrSelected = { group -> onQrSelected(selectedAttendeeForDetail, group) },
                            onAddGroupToQueue = onAddGroupToQueue,
                            animatingGroups = animatingGroups
                        )

                        // FAB logic within sheet
                        val isInQueue = queueIds.contains(selectedAttendeeForDetail.id)
                        val attendeeName = if (fabState is MainListViewModel.FabState.AddAttendee) fabState.name else ""
                        val isVisible = fabState is MainListViewModel.FabState.AddAttendee || (showAddedAnimation && isInQueue)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isGroupsEmpty) Modifier.matchParentSize() else Modifier.height(availableHeight))
                                .padding(bottom = 16.dp, end = 16.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                if (activeQrInfo != null) {
                                    val context = LocalContext.current
                                    val bitmap = remember(activeQrInfo) {
                                        val name = selectedAttendeeForDetail.shortName ?: selectedAttendeeForDetail.fullName
                                        sg.org.bcc.attendance.util.qr.QrImageGenerator.createQrWithText(activeQrInfo, name)
                                    }
                                    
                                    ExtendedFloatingActionButton(
                                        onClick = {
                                            val fileName = "qr_${activeQrInfo.personId ?: activeQrInfo.groupId}.png"
                                            val uri = sg.org.bcc.attendance.util.qr.QrImageGenerator.saveAndGetUri(context, bitmap, fileName)
                                            if (uri != null) {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "image/png"
                                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(android.content.Intent.createChooser(intent, "Share QR Code"))
                                            }
                                        },
                                        text = { Text("Share QR") },
                                        icon = { AppIcon(resourceId = AppIcons.Share, contentDescription = null) },
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        shape = CircleShape
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                AnimatedVisibility(
                                    visible = isVisible,
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    val isAnimating = showAddedAnimation && isInQueue
                                    val containerColor by animateColorAsState(
                                        targetValue = if (isAnimating) DeepGreen else MaterialTheme.colorScheme.primary,
                                        label = "FabColor",
                                        animationSpec = tween(durationMillis = 500)
                                    )
                                    
                                    ExtendedFloatingActionButton(
                                        onClick = {
                                            if (!isAnimating) {
                                                onAddAttendeeToQueue(selectedAttendeeForDetail.id)
                                            }
                                        },
                                        icon = { 
                                            AnimatedContent(
                                                targetState = if (isAnimating) AppIcons.BookmarkAdded else AppIcons.PlaylistAdd,
                                                transitionSpec = {
                                                    fadeIn(animationSpec = tween(300)) togetherWith
                                                    fadeOut(animationSpec = tween(300))
                                                },
                                                label = "IconAnimation"
                                            ) { iconId ->
                                                AppIcon(resourceId = iconId, contentDescription = null) 
                                            }
                                        },
                                        text = { 
                                            AnimatedContent(
                                                targetState = if (isAnimating) "Queued" else "Queue $attendeeName",
                                                transitionSpec = {
                                                    fadeIn(animationSpec = tween(300)) togetherWith
                                                    fadeOut(animationSpec = tween(300))
                                                },
                                                label = "TextAnimation"
                                            ) { text -> Text(text) }
                                        },
                                        containerColor = containerColor,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        shape = CircleShape
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SheetType.QUEUE -> {
                QueueScreen(
                    onBack = { onSetShowQueueSheet(false) },
                    onActionComplete = { message, shouldClose ->
                        if (shouldClose) onSetShowQueueSheet(false)
                        onShowSnackbar(message)
                    },
                    currentEventId = currentEventId,
                    textScale = textScale,
                    onTextScaleChange = onTextScaleChange,
                    onNavigateToQrScanner = {
                        onSetShowQueueSheet(false)
                        onSetShowScannerSheet(true)
                    }
                )
            }
            SheetType.QR_SCANNER -> {
                sg.org.bcc.attendance.ui.qr.QrScannerContent(
                    onScanResult = { code -> onProcessQrResult(code) },
                    onBack = { onSetShowScannerSheet(false) },
                    textScale = textScale
                )
            }
            SheetType.NONE -> {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttendeeDetailContent(
    attendee: Attendee,
    groups: List<sg.org.bcc.attendance.data.local.entities.Group>,
    activeQrInfo: QrInfo?,
    attendeeName: String,
    groupMembersMap: Map<String, List<Attendee>>,
    attendeeGroupsMap: Map<String, List<String>>,
    textScale: Float,
    onTextScaleChange: (Float) -> Unit = {},
    presentIds: Set<String>,
    queueIds: Set<String>,
    canNavigateBack: Boolean = false,
    previousName: String? = null,
    onBack: () -> Unit = {},
    onAttendeeClick: (Attendee) -> Unit,
    onAddAttendeeToQueue: () -> Unit,
    onQrSelected: (sg.org.bcc.attendance.data.local.entities.Group?) -> Unit,
    onAddGroupToQueue: (String) -> Unit,
    animatingGroups: Set<String> = emptySet()
) {
    var showQrMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (canNavigateBack && previousName != null) {
            sg.org.bcc.attendance.ui.components.AppBottomSheetHeader(
                navigationText = "Return to $previousName",
                onNavigationClick = onBack,
                textScale = 1.0f
            )
        }

        // Profile Body: WHITE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AttendeeListItem(
                attendee = attendee,
                isPresent = presentIds.contains(attendee.id),
                isQueued = queueIds.contains(attendee.id),
                backgroundColor = MaterialTheme.colorScheme.surface,
                textScale = 1.25f,
                enabled = false,
                onClick = { },
                onAvatarClick = { },
                onLongClick = { },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (queueIds.contains(attendee.id)) {
                            AppIcon(
                                resourceId = AppIcons.BookmarkAdded,
                                contentDescription = "In Queue",
                                modifier = Modifier.size(28.dp * 1.25f).padding(end = 4.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }

                        Box {
                            IconButton(onClick = { showQrMenu = true }) {
                                AppIcon(
                                    resourceId = AppIcons.MoreVert,
                                    contentDescription = "QR Options",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp * 1.25f)
                                )
                            }
                            DropdownMenu(
                                expanded = showQrMenu,
                                onDismissRequest = { showQrMenu = false }
                            ) {
                                val isAttendeeQrActive = activeQrInfo != null && activeQrInfo.groupId == null
                                DropdownMenuItem(
                                    text = { Text("Attendee only") },
                                    leadingIcon = { AppIcon(resourceId = AppIcons.QrCode, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    enabled = !isAttendeeQrActive,
                                    onClick = {
                                        onQrSelected(null)
                                        showQrMenu = false
                                    }
                                )
                                groups.forEach { group ->
                                    val isGroupQrActive = activeQrInfo != null && activeQrInfo.groupId == group.groupId
                                    DropdownMenuItem(
                                        text = { Text(group.name) },
                                        leadingIcon = { AppIcon(resourceId = AppIcons.QrCode, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        enabled = !isGroupQrActive,
                                        onClick = {
                                            onQrSelected(group)
                                            showQrMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
            HorizontalDivider()
        }

        // List Area: surfaceContainerLow
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (groups.isEmpty()) Modifier.wrapContentHeight() else Modifier.weight(1f))
                .pinchToScale(textScale, onTextScaleChange)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            if (activeQrInfo != null) {
                item {
                    val bitmap = remember(activeQrInfo) {
                        sg.org.bcc.attendance.util.qr.QrImageGenerator.createQrWithText(activeQrInfo, attendeeName)
                    }
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code Preview",
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }

            if (groups.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No groups assigned",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(112.dp))
                }
            }

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
                            val allMembers = groupMembersMap[group.groupId] ?: emptyList()
                            val isGroupFullyQueued = allMembers.isNotEmpty() && allMembers.all { queueIds.contains(it.id) }
                            val isAnimating = animatingGroups.contains(group.groupId)
                            
                            val containerColor by animateColorAsState(
                                targetValue = if (isAnimating || isGroupFullyQueued) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                label = "GroupButtonContainerColor"
                            )
                            val contentColor by animateColorAsState(
                                targetValue = if (isAnimating || isGroupFullyQueued) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                label = "GroupButtonContentColor"
                            )

                            FilledTonalButton(
                                onClick = { onAddGroupToQueue(group.groupId) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp),
                                enabled = !isGroupFullyQueued && !isAnimating,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = containerColor,
                                    contentColor = contentColor,
                                    disabledContainerColor = containerColor,
                                    disabledContentColor = contentColor
                                )
                            ) {
                                AnimatedContent(
                                    targetState = if (isAnimating || isGroupFullyQueued) AppIcons.BookmarkAdded else AppIcons.PlaylistAdd,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(300))
                                    },
                                    label = "GroupIconAnimation"
                                ) { iconId ->
                                    AppIcon(
                                        resourceId = iconId, 
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    ) 
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                AnimatedContent(
                                    targetState = if (isAnimating || isGroupFullyQueued) "Queued" else "Queue ${allMembers.size}",
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(300))
                                    },
                                    label = "GroupTextAnimation"
                                ) { text ->
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                items(members, key = { "${group.groupId}_${it.id}" }) { member ->
                    AttendeeListItem(
                        attendee = member,
                        searchQuery = "",
                        textScale = textScale,
                        isPresent = presentIds.contains(member.id),
                        isQueued = queueIds.contains(member.id),
                        backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = { onAttendeeClick(member) }
                    )
                }
            }
        }
    }
}

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
