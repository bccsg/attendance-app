package sg.org.bcc.attendance.unit

import io.kotest.matchers.shouldBe
import org.junit.Test
import sg.org.bcc.attendance.util.EventSuggester
import java.time.LocalDate

class EventSuggesterTest {

    @Test
    fun `suggestEventTitle on Sunday should default to today`() {
        val today = LocalDate.of(2026, 2, 22) // Feb 22, 2026 is Sunday
        val title = EventSuggester.suggestEventTitle(today)
        title shouldBe "2602221030:sunday service"
    }

    @Test
    fun `suggestEventTitle on Friday should default to coming Sunday`() {
        val today = LocalDate.of(2026, 2, 20) // Feb 20, 2026 is Friday
        val title = EventSuggester.suggestEventTitle(today)
        title shouldBe "2602221030:sunday service"
    }

    @Test
    fun `suggestEventTitle on Monday should default to next Sunday`() {
        val today = LocalDate.of(2026, 2, 23) // Feb 23, 2026 is Monday
        val title = EventSuggester.suggestEventTitle(today)
        title shouldBe "2603011030:sunday service"
    }
}
