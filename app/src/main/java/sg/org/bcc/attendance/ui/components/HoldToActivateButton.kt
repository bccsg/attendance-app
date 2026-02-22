package sg.org.bcc.attendance.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun HoldToActivateButton(
    iconResId: Int,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = 64.dp,
    holdDurationMs: Long = 1000L,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    content: @Composable (Color, Float) -> Unit = { tint, alpha ->
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(height * 0.5f).alpha(alpha)
        )
    }
) {
    val haptic = LocalHapticFeedback.current
    var isPressing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    
    // Track previous enabled state to detect transitions
    var prevEnabled by remember { mutableStateOf(enabled) }
    val isEnablingDisabling = enabled != prevEnabled
    LaunchedEffect(enabled) {
        prevEnabled = enabled
        if (!enabled) {
            isPressing = false
            progress = 0f
        }
    }

    val elevation by animateDpAsState(
        targetValue = if (!enabled) 0.dp else if (isPressing) 0.dp else 2.dp,
        animationSpec = if (isEnablingDisabling) snap() else tween(100),
        label = "Elevation"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isPressing && enabled) 0.95f else 1f,
        animationSpec = if (isEnablingDisabling) snap() else tween(100),
        label = "Scale"
    )

    val buttonColor by animateColorAsState(
        targetValue = if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        animationSpec = if (isEnablingDisabling) snap() else tween(200),
        label = "ButtonColor"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = if (enabled) progress else 0f,
        animationSpec = if (enabled && isPressing) {
            tween(durationMillis = holdDurationMs.toInt())
        } else {
            snap()
        },
        label = "HoldProgress"
    )

    LaunchedEffect(isPressing, enabled) {
        if (isPressing && enabled) {
            progress = 1f
            delay(holdDurationMs)
            if (isPressing && enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onActivate()
                isPressing = false
                progress = 0f
            }
        } else {
            progress = 0f
        }
    }

    val boxModifier = if (width.isSpecified) {
        modifier.size(width = width + 12.dp, height = height + 12.dp)
    } else {
        modifier.fillMaxWidth().height(height + 12.dp)
    }

    Box(
        modifier = boxModifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressing = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressing = false
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val backgroundArcColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.3f else 0.1f)
        val strokeWidth = 4.dp
        
        val canvasModifier = if (width.isSpecified) {
            Modifier.size(width = width + 8.dp, height = height + 8.dp)
        } else {
            Modifier.fillMaxWidth().height(height + 8.dp)
        }

        Canvas(modifier = canvasModifier) {
            val sw = strokeWidth.toPx()
            
            // Adjust size to account for padding/margin
            val drawSize = if (width.isSpecified) size else size.copy(width = size.width - 12.dp.toPx())
            val offset = if (width.isSpecified) Offset.Zero else Offset(6.dp.toPx(), 0f)
            val cornerRadius = drawSize.height / 2f

            // Background track
            drawRoundRect(
                color = backgroundArcColor,
                topLeft = offset,
                size = drawSize,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = sw)
            )
            
            if (animatedProgress > 0f) {
                val path = Path().apply {
                    val centerX = offset.x + drawSize.width / 2f
                    val top = offset.y
                    val right = offset.x + drawSize.width
                    val bottom = offset.y + drawSize.height
                    val left = offset.x
                    val r = cornerRadius

                    moveTo(centerX, top)
                    lineTo(right - r, top)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(right - 2 * r, top, right, top + 2 * r),
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(right, bottom - r)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(right - 2 * r, bottom - 2 * r, right, bottom),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(left + r, bottom)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(left, bottom - 2 * r, left + 2 * r, bottom),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(left, top + r)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(left, top, left + 2 * r, top + 2 * r),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    close()
                }
                val pathMeasure = PathMeasure()
                pathMeasure.setPath(path, false)
                val stopDistance = pathMeasure.length * animatedProgress
                
                val drawPath = Path()
                pathMeasure.getSegment(0f, stopDistance, drawPath, true)
                
                drawPath(
                    path = drawPath,
                    color = color,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
        }

        val surfaceModifier = if (width.isSpecified) {
            Modifier.size(width = width, height = height)
        } else {
            Modifier.fillMaxWidth().height(height).padding(horizontal = 6.dp)
        }

        Surface(
            modifier = surfaceModifier
                .scale(scale)
                .shadow(elevation, RoundedCornerShape(height / 2))
                .clip(RoundedCornerShape(height / 2)),
            color = buttonColor,
            tonalElevation = elevation
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val contentAlpha = if (enabled) 1f else 0.38f
                val tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                
                content(tint, contentAlpha)
                
                if (iconResId == AppIcons.PersonCheck) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Present",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = tint,
                        modifier = Modifier.alpha(contentAlpha)
                    )
                }
            }
        }
    }
}
