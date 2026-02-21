package sg.org.bcc.attendance.unit

import io.kotest.matchers.shouldBe
import org.junit.Test
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.util.FuzzySearchScorer

class FuzzySearchScorerTest {

    @Test
    fun `scoring should prioritize short names`() {
        val johnDoe = Attendee("1", "John Doe", "John")
        val jonathan = Attendee("2", "Jonathan Smith", "Jon")
        
        // Exact short name should have higher score than starting-with short name
        (FuzzySearchScorer.score(johnDoe, "John") > FuzzySearchScorer.score(jonathan, "Jo")) shouldBe true
        
        // Starts with short name should have higher score than starting-with full name only
        val matthew = Attendee("3", "Matthew Chng", "Matt")
        val matthewNoShort = Attendee("4", "Matthew Tan")
        (FuzzySearchScorer.score(matthew, "Mat") > FuzzySearchScorer.score(matthewNoShort, "Mat")) shouldBe true
    }

    @Test
    fun `sort should return correctly ordered list`() {
        val attendees = listOf(
            Attendee("1", "Jonathan Smith", "Jon"),
            Attendee("2", "John Doe", "John"),
            Attendee("3", "Jonny Quest", "Jonny")
        )
        
        val results = FuzzySearchScorer.sort(attendees, "John")
        
        results.size shouldBe 1
        results[0].shortName shouldBe "John"
    }
}
