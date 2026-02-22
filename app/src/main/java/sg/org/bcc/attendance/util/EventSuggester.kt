package sg.org.bcc.attendance.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object EventSuggester {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyMMdd")

    fun suggestNextEventTitle(today: LocalDate = LocalDate.now()): String {
        val nextSunday = if (today.dayOfWeek == DayOfWeek.SUNDAY) {
            today
        } else {
            today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
        }
        
        val datePart = nextSunday.format(dateFormatter)
        val timePart = "1030"
        val namePart = "Sunday Service"
        
        return "$datePart $timePart $namePart"
    }

    fun parseDate(title: String): LocalDate? {
        return try {
            val datePart = title.substringBefore(" ")
            LocalDate.parse(datePart, dateFormatter)
        } catch (e: Exception) {
            null
        }
    }
}
