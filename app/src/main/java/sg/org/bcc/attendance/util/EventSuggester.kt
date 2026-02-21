package sg.org.bcc.attendance.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object EventSuggester {
    fun suggestNextEventTitle(): String {
        // TODO: Revert to kotlinx-datetime once build environment issue is resolved
        val today = LocalDate.now()
        val nextSunday = if (today.dayOfWeek == DayOfWeek.SUNDAY) {
            today
        } else {
            today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
        }
        
        val datePart = nextSunday.format(DateTimeFormatter.ofPattern("yyMMdd"))
        val timePart = "1030"
        val namePart = "sunday service"
        
        return "$datePart $timePart $namePart"
    }
}
