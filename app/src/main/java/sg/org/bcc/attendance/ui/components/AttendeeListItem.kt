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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttendeeListItem(
    attendee: Attendee,
    modifier: Modifier = Modifier,
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
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onAvatarClick: () -> Unit = onClick,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val avatarSize = 40.dp * textScale

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            isSelectionMode -> Color(0xFFF2F0F7)
            else -> backgroundColor
        }
    ) {
        Box(modifier = Modifier.alpha(alpha)) {
            ListItem(
                modifier = if (enabled) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else Modifier,
                headlineContent = {
                    val matchColor = MaterialTheme.colorScheme.primary
                    val unmatchedColor = if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else Color.Unspecified
                    
                    Text(
                        text = getHighlightedText(attendee.shortName ?: attendee.fullName, searchQuery, matchColor, unmatchedColor),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize * textScale
                        )
                    )
                },
                supportingContent = {
                    val matchColor = MaterialTheme.colorScheme.primary
                    val unmatchedColor = if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else Color.Unspecified
                    
                    Column {
                        val supportingText = if (attendee.shortName != null) {
                            getHighlightedText("${attendee.fullName} • ${attendee.id}", searchQuery, matchColor, unmatchedColor)
                        } else {
                            getHighlightedText(attendee.id, searchQuery, matchColor, unmatchedColor)
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
                                    if (isPresent && (isSelectionMode || isSelected)) {
                                        AppIcon(
                                            resourceId = AppIcons.PersonCheck,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp * 1.25f),
                                            tint = DeepGreen.copy(alpha = 0.6f)
                                        )
                                    }
                                    if (isQueued) {
                                        if (isPresent && (isSelectionMode || isSelected)) Spacer(modifier = Modifier.width(8.dp))
                                        AppIcon(
                                            resourceId = AppIcons.Bookmark,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp * 1.25f),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        )
                                    }
                                    if (isGrouped) {
                                        if ((isPresent && (isSelectionMode || isSelected)) || isQueued) Spacer(modifier = Modifier.width(8.dp))
                                        AppIcon(
                                            resourceId = AppIcons.Groups,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp * 1.25f),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        )
                                    }
                                    if (isLater) {
                                        if ((isPresent && (isSelectionMode || isSelected)) || isQueued || isGrouped) Spacer(modifier = Modifier.width(8.dp))
                                        AppIcon(
                                            resourceId = AppIcons.PushPin,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp * 1.25f),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            },                leadingContent = {
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
                            .then(if (enabled) Modifier.clickable { onAvatarClick() } else Modifier)
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
}

private fun getHighlightedText(
    fullText: String, 
    query: String, 
    matchColor: Color, 
    unmatchedColor: Color
): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(fullText)
    
    val matches = Regex.escape(query).toRegex(RegexOption.IGNORE_CASE).findAll(fullText).toList()
    if (matches.isEmpty()) return buildAnnotatedString {
        if (unmatchedColor != Color.Unspecified) {
            pushStyle(SpanStyle(color = unmatchedColor))
        }
        append(fullText)
        if (unmatchedColor != Color.Unspecified) {
            pop()
        }
    }

    return buildAnnotatedString {
        var lastIndex = 0
        matches.forEach { match ->
            if (match.range.first > lastIndex) {
                if (unmatchedColor != Color.Unspecified) {
                    pushStyle(SpanStyle(color = unmatchedColor))
                }
                append(fullText.substring(lastIndex, match.range.first))
                if (unmatchedColor != Color.Unspecified) {
                    pop()
                }
            }
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = matchColor))
            append(match.value)
            pop()
            lastIndex = match.range.last + 1
        }
        if (lastIndex < fullText.length) {
            if (unmatchedColor != Color.Unspecified) {
                pushStyle(SpanStyle(color = unmatchedColor))
            }
            append(fullText.substring(lastIndex))
            if (unmatchedColor != Color.Unspecified) {
                pop()
            }
        }
    }
}
