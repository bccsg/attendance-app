package sg.org.bcc.attendance.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.ui.theme.DeepGreen
import sg.org.bcc.attendance.ui.theme.PastelGreen
import sg.org.bcc.attendance.ui.theme.Purple40

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttendeeListItem(
    attendee: Attendee,
    isPresent: Boolean = false,
    isQueued: Boolean = false,
    isGrouped: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isLater: Boolean = false,
    searchQuery: String = "",
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    textScale: Float = 1.0f,
    alpha: Float = 1.0f,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onAvatarClick: () -> Unit = onClick,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val avatarSize = 40.dp * textScale

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        color = when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            isSelectionMode -> Color(0xFFF2F0F7)
            else -> backgroundColor
        }
    ) {
        ListItem(
            modifier = Modifier.combinedClickable(
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
                    if (isGrouped) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AppIcon(
                            resourceId = AppIcons.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp * textScale),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            supportingContent = {
                Column {
                    val supportingText = if (attendee.shortName != null) {
                        getHighlightedText("${attendee.fullName} • ${attendee.id}", searchQuery)
                    } else {
                        getHighlightedText(attendee.id, searchQuery)
                    }
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * textScale
                        )
                    )
                }
            },
            trailingContent = trailingContent ?: {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPresent && isSelectionMode) {
                        AppIcon(
                            resourceId = AppIcons.PersonCheck,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp * textScale),
                            tint = DeepGreen.copy(alpha = 0.6f)
                        )
                    }
                    if (isQueued) {
                        if (isPresent && isSelectionMode) Spacer(modifier = Modifier.width(8.dp))
                        AppIcon(
                            resourceId = AppIcons.BookmarkAdded,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp * textScale),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                    if (isLater) {
                        if ((isPresent && isSelectionMode) || isQueued) Spacer(modifier = Modifier.width(8.dp))
                        AppIcon(
                            resourceId = AppIcons.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp * textScale),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            },
            leadingContent = {
                Surface(
                    shape = CircleShape,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isPresent && !isSelectionMode -> PastelGreen
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .clickable { onAvatarClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSelected) {
                            AppIcon(
                                resourceId = AppIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(avatarSize * 0.6f)
                            )
                        } else if (isPresent && !isSelectionMode) {
                            AppIcon(
                                resourceId = AppIcons.PersonCheck,
                                contentDescription = null,
                                tint = DeepGreen,
                                modifier = Modifier.size(avatarSize * 0.6f)
                            )
                        } else {
                            Text(
                                text = (attendee.shortName ?: attendee.fullName).take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize * textScale
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
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
