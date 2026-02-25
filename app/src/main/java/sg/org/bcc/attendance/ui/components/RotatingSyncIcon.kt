package sg.org.bcc.attendance.ui.components

import androidx.compose.animation.core.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun RotatingSyncIcon(
    resourceId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shouldRotate: Boolean = false,
    tint: Color = LocalContentColor.current
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SyncRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    AppIcon(
        resourceId = resourceId,
        contentDescription = contentDescription,
        modifier = modifier.then(
            if (shouldRotate) Modifier.graphicsLayer { rotationZ = rotation } else Modifier
        ),
        tint = tint
    )
}
