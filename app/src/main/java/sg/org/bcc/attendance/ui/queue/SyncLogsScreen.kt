package sg.org.bcc.attendance.ui.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import sg.org.bcc.attendance.data.local.dao.TriggerSummary
import sg.org.bcc.attendance.data.local.entities.SyncLog
import sg.org.bcc.attendance.ui.components.AppIcon
import sg.org.bcc.attendance.ui.components.AppIcons
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncLogsScreen(
    onBack: () -> Unit,
    viewModel: SyncLogsViewModel = hiltViewModel()
) {
    val triggers by viewModel.triggersSummary.collectAsState()
    val selectedTriggerId by viewModel.selectedTriggerId.collectAsState()
    val logs by viewModel.logsForSelectedTrigger.collectAsState()

    var showErrorDetail by remember { mutableStateOf<SyncLog?>(null) }

    androidx.activity.compose.BackHandler(enabled = selectedTriggerId != null) {
        viewModel.selectTrigger(null)
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 4.dp
            ) {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { 
                        Text(
                            if (selectedTriggerId == null) "Sync Sessions" else "Session Details",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectedTriggerId == null) onBack() else viewModel.selectTrigger(null)
                        }) {
                            AppIcon(AppIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.surface
        ) {
            if (selectedTriggerId == null) {
                TriggerList(triggers, onTriggerSelected = viewModel::selectTrigger)
            } else {
                LogList(
                    logs = logs,
                    onShowError = { showErrorDetail = it }
                )
            }
        }

        showErrorDetail?.let { log ->
            AlertDialog(
                onDismissRequest = { showErrorDetail = null },
                confirmButton = {
                    TextButton(onClick = { showErrorDetail = null }) { Text("Close") }
                },
                title = { Text("Error: ${log.operation}", style = MaterialTheme.typography.titleMedium) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            log.errorMessage ?: "Unknown error", 
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                log.stackTrace ?: "No stacktrace available",
                                modifier = Modifier.padding(8.dp),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun TriggerList(
    triggers: List<TriggerSummary>,
    onTriggerSelected: (String) -> Unit
) {
    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    if (triggers.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No sync logs found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(triggers) { trigger ->
                ListItem(
                    headlineContent = { 
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                sdf.format(Date(trigger.startTime)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                trigger.triggerType,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    trailingContent = { AppIcon(AppIcons.ChevronRight, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onTriggerSelected(trigger.triggerId) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
fun LogList(
    logs: List<SyncLog>,
    onShowError: (SyncLog) -> Unit
) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(logs) { log ->
            val isError = log.status == "FAILED"
            ListItem(
                headlineContent = { 
                    Text(
                        log.operation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal
                    ) 
                },
                supportingContent = { 
                    Column {
                        Text(
                            sdf.format(Date(log.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!log.params.isNullOrBlank()) {
                            Text(
                                log.params,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                trailingContent = {
                    if (isError) {
                        AppIcon(AppIcons.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    } else {
                        AppIcon(AppIcons.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                    }
                },
                modifier = if (isError) Modifier.clickable { onShowError(log) } else Modifier,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
