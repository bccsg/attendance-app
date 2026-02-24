package sg.org.bcc.attendance.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A modifier that detects pinch gestures to switch between normal and large text scaling.
 * It is designed to work with scrollable lists by only consuming multi-finger gestures.
 * 
 * @param textScale Current scale factor
 * @param onScaleChange Callback when scale factor should change (e.g., toggle 1.0f vs 1.5f)
 */
fun Modifier.pinchToScale(
    textScale: Float,
    onScaleChange: (Float) -> Unit
): Modifier = this.pointerInput(textScale) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var totalZoom = 1f
        var isPinching = false
        
        do {
            val event = awaitPointerEvent()
            
            // Only consider it a pinch if we have more than one finger
            if (event.changes.size > 1) {
                val zoomChange = event.calculateZoom()
                totalZoom *= zoomChange
                
                // If we've zoomed significantly, mark as pinching and consume
                if (!isPinching && (totalZoom > 1.05f || totalZoom < 0.95f)) {
                    isPinching = true
                }
                
                if (isPinching) {
                    // Consume the event changes so they don't trigger other gestures (like scroll)
                    event.changes.forEach { it.consume() }
                    
                    if (totalZoom > 1.25f && textScale < 1.1f) {
                        onScaleChange(1.5f)
                        // Reset zoom once triggered to prevent multiple triggers
                        totalZoom = 1f 
                    } else if (totalZoom < 0.75f && textScale > 1.1f) {
                        onScaleChange(1.0f)
                        // Reset zoom once triggered
                        totalZoom = 1f
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
