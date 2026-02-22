package sg.org.bcc.attendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DateIcon(date: LocalDate?, textScale: Float, modifier: Modifier = Modifier) {
    val month = date?.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))?.uppercase() ?: "???"
    val day = date?.dayOfMonth?.toString() ?: "??"

    Box(
        modifier = modifier
            .size(48.dp * textScale)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = month,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp * textScale,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = day,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp * textScale,
                    fontWeight = FontWeight.Black
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
