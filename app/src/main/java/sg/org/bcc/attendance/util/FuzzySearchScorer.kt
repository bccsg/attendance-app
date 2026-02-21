package sg.org.bcc.attendance.util

import sg.org.bcc.attendance.data.local.entities.Attendee

object FuzzySearchScorer {
    
    fun score(attendee: Attendee, query: String): Int {
        if (query.isBlank()) return 0
        
        val q = query.trim().lowercase()
        val sn = attendee.shortName?.lowercase()
        val fn = attendee.fullName.lowercase()
        
        var score = 0
        
        // 1. Exact Short Name Match (Highest)
        if (sn != null && sn == q) {
            score += 1000
        }
        
        // 2. Starts-with Short Name
        if (sn != null && sn.startsWith(q)) {
            score += 500
        }
        
        // 3. Contains Short Name
        if (sn != null && sn.contains(q)) {
            score += 250
        }
        
        // 4. Full Name Matches
        if (fn == q) {
            score += 100
        } else if (fn.startsWith(q)) {
            score += 50
        } else if (fn.contains(q)) {
            score += 25
        }
        
        return score
    }

    fun sort(attendees: List<Attendee>, query: String): List<Attendee> {
        if (query.isBlank()) return attendees
        
        return attendees
            .map { it to score(it, query) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
