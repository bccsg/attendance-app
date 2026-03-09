package sg.org.bcc.attendance.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.util.EventSuggester
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EventSummary(
    event: Event,
    modifier: Modifier = Modifier,
    textScale: Float = 1.0f,
    contentColor: Color = Color.Unspecified,
    secondaryContentColor: Color = Color.Unspecified,
    isSelected: Boolean = false,
    showCloudStatus: Boolean = false,
    isDemoMode: Boolean = false
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

    val finalSecondaryColor = secondaryContentColor.takeOrElse { 
        contentColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }.copy(alpha = 0.6f) 
    }

    Row(
        modifier = modifier.fillMaxWidth(),
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
                ),
                color = contentColor.takeOrElse { Color.Unspecified }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * textScale
                    ),
                    color = finalSecondaryColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = event.cloudEventId ?: event.id.take(8),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * textScale
                    ),
                    color = finalSecondaryColor.copy(alpha = 0.5f)
                )
            }
        }

        if (showCloudStatus) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    AppIcon(
                        resourceId = AppIcons.Check, 
                        contentDescription = "Selected", 
                        tint = contentColor.takeOrElse { MaterialTheme.colorScheme.primary },
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
                        tint = if (isDemoMode) finalSecondaryColor
                               else contentColor.takeOrElse { MaterialTheme.colorScheme.primary },
                        modifier = Modifier.size(24.dp * textScale)
                    )
                }
            }
        }
    }
}
