package sg.org.bcc.attendance.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.ui.components.AppIcon
import sg.org.bcc.attendance.ui.components.AppIcons
import sg.org.bcc.attendance.ui.components.DateIcon
import sg.org.bcc.attendance.util.EventSuggester
import sg.org.bcc.attendance.util.SetStatusBarIconsColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

import sg.org.bcc.attendance.ui.components.pinchToScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventManagementScreen(
    viewModel: EventManagementViewModel = hiltViewModel(),
    currentEventId: String?,
    textScale: Float = 1.0f,
    onTextScaleChange: (Float) -> Unit = {},
    onEventSelected: (String) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val events by viewModel.manageableEvents.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val uiError by viewModel.uiError.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiError) {
        uiError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    SetStatusBarIconsColor(isLight = false)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 4.dp
            ) {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text("Events", color = MaterialTheme.colorScheme.onPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            AppIcon(resourceId = AppIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showCreateDialog = true }) {
                            AppIcon(resourceId = AppIcons.PlaylistAdd, contentDescription = "Create Event", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }
    ) { padding ->
        if (showCreateDialog) {
            CreateEventDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, date, time ->
                    viewModel.onCreateEvent(name, date, time) { newEvent ->
                        onEventSelected(newEvent.id)
                        onBack()
                    }
                    showCreateDialog = false
                }
            )
        }

        if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding), 
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    AppIcon(
                        resourceId = AppIcons.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "No recent events",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Create a new event to begin taking attendance.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        AppIcon(resourceId = AppIcons.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Event")
                    }
                    TextButton(onClick = { 
                        viewModel.logout(onLogoutComplete = {
                            onBack()
                            onLogout()
                        }) 
                    }) {
                        Text("Logout")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pinchToScale(textScale, onTextScaleChange)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                items(events, key = { it.id }) { event ->
                    EventItem(
                        event = event,
                        isSelected = event.id == currentEventId,
                        textScale = textScale,
                        isDemoMode = isDemoMode,
                        onClick = { 
                            onEventSelected(event.id)
                            onBack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EventItem(
    event: Event,
    isSelected: Boolean,
    textScale: Float,
    isDemoMode: Boolean = false,
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

    Column {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp * textScale)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DateIcon(date = date, textScale = textScale)
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize * textScale
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * textScale
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = event.cloudEventId ?: event.id.take(8),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = MaterialTheme.typography.labelSmall.fontSize * textScale
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        AppIcon(
                            resourceId = AppIcons.Check, 
                            contentDescription = "Selected", 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp * textScale)
                        )
                    }
                    
                    val syncIcon = when {
                        isDemoMode -> AppIcons.CloudOff
                        event.cloudEventId != null -> AppIcons.CloudDone
                        else -> null
                    }
                    
                    if (syncIcon != null) {
                        if (isSelected) Spacer(modifier = Modifier.width(8.dp))
                        AppIcon(
                            resourceId = syncIcon,
                            contentDescription = "Sync Status",
                            tint = if (isDemoMode) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                   else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp * textScale)
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventDialog(
    onDismiss: () -> Unit,
    onCreate: (String, LocalDate, String) -> Unit
) {
    val suggestedTitle = EventSuggester.suggestNextEventTitle()
    // title format: yyMMdd HHmm Name
    val parts = suggestedTitle.split(" ")
    val defaultDate = EventSuggester.parseDate(parts[0]) ?: LocalDate.now()
    val defaultTime = parts[1]
    val defaultName = parts.drop(2).joinToString(" ")

    var name by remember { mutableStateOf(defaultName) }
    var date by remember { mutableStateOf(defaultDate) }
    var time by remember { mutableStateOf(defaultTime) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = time.take(2).toIntOrNull() ?: 10,
            initialMinute = time.takeLast(2).toIntOrNull() ?: 30
        )

        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val hour = timePickerState.hour.toString().padStart(2, '0')
                    val minute = timePickerState.minute.toString().padStart(2, '0')
                    time = "$hour$minute"
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Event title: ${date.format(DateTimeFormatter.ofPattern("yyMMdd"))} ${time} $name",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Note: Event name and details cannot be edited once created.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                HorizontalDivider()

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Event Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    onValueChange = { },
                    label = { Text("Date") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            AppIcon(resourceId = AppIcons.CalendarMonth, contentDescription = "Select Date")
                        }
                    }
                )

                OutlinedTextField(
                    value = "${time.take(2)}:${time.takeLast(2)}",
                    onValueChange = { },
                    label = { Text("Time") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showTimePicker = true }) {
                            AppIcon(resourceId = AppIcons.Schedule, contentDescription = "Select Time")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, date, time) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        ),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min)
                .background(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = containerColor
                ),
            color = containerColor
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    text = "Select Time",
                    style = MaterialTheme.typography.labelMedium
                )
                content()
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    dismissButton()
                    confirmButton()
                }
            }
        }
    }
}
