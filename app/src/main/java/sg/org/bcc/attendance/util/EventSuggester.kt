package sg.org.bcc.attendance.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object EventSuggester {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyMMdd")

    fun suggestNextEventTitle(today: LocalDate = LocalDate.now()): String {
        val nextSunday = suggestNextEventDate(today)
        
        val datePart = nextSunday.format(dateFormatter)
        val timePart = suggestNextEventTime()
        val namePart = "Sunday Service"
        
        return "$datePart $timePart $namePart"
    }

    fun suggestNextEventDate(today: LocalDate = LocalDate.now()): LocalDate {
        return if (today.dayOfWeek == DayOfWeek.SUNDAY) {
            today
        } else {
            today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
        }
    }

    fun suggestNextEventTime(): String = "1030"

    fun parseDate(title: String): LocalDate? {
        return try {
            val datePart = title.substringBefore(" ")
            LocalDate.parse(datePart, dateFormatter)
        } catch (e: Exception) {
            null
        }
    }

    fun parseDateTime(title: String): java.time.LocalDateTime? {
        return try {
            val parts = title.split(" ")
            if (parts.size < 2) return null
            val datePart = parts[0]
            val timePart = parts[1]
            val date = LocalDate.parse(datePart, dateFormatter)
            val time = LocalTime.of(timePart.take(2).toInt(), timePart.takeLast(2).toInt())
            date.atTime(time)
        } catch (e: Exception) {
            null
        }
    }
}
