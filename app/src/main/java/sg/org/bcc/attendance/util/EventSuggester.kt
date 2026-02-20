package sg.org.bcc.attendance.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object EventSuggester {
    private const val DEFAULT_TIME = "1030"
    private const val DEFAULT_NAME = "sunday service"
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd")

    fun suggestEventTitle(today: LocalDate = LocalDate.now()): String {
        val daysUntilSunday = (DayOfWeek.SUNDAY.value - today.dayOfWeek.value + 7) % 7
        val defaultDate = if (daysUntilSunday == 0) today else today.plusDays(daysUntilSunday.toLong())
        
        val datePart = defaultDate.format(DATE_FORMATTER)
        return "$datePart$DEFAULT_TIME:$DEFAULT_NAME"
    }
}
