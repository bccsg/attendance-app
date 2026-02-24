package sg.org.bcc.attendance.util

import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.Group
import sg.org.bcc.attendance.data.local.entities.AttendeeGroupMapping

object DemoData {
    val groups = listOf(
        Group("MICKEY", "Mickey & Friends"),
        Group("PRINCESS", "Disney Princesses"),
        Group("FROZEN", "Frozen"),
        Group("LIONKING", "The Lion King"),
        Group("TOYSTORY", "Toy Story"),
        Group("CARS", "Cars"),
        Group("INSIDEOUT", "Inside Out"),
        Group("HERO6", "Big Hero 6"),
        Group("STITCH", "Lilo & Stitch"),
        Group("PETERPAN", "Peter Pan"),
        Group("ROYALTY", "Royalty"),
        Group("VILLAINS", "Villains")
    )

    val disneyCharacters = listOf(
        Attendee("D01", "Mickey Mouse", "Mickey"),
        Attendee("D02", "Minnie Mouse", "Minnie"),
        Attendee("D03", "Donald Duck", "Donald"),
        Attendee("D04", "Daisy Duck", "Daisy"),
        Attendee("D05", "Goofy Goof", "Goofy"),
        Attendee("D06", "Pluto Dog", "Pluto"),
        Attendee("D07", "Chipmunk Chip", "Chip"),
        Attendee("D08", "Chipmunk Dale", "Dale"),
        Attendee("D09", "Snow White", "Snow"),
        Attendee("D10", "Cinderella Tremaine", "Cinderella"),
        Attendee("D11", "Aurora Rose", "Aurora"),
        Attendee("D12", "Ariel Triton", "Ariel"),
        Attendee("D13", "Belle Maurice", "Belle"),
        Attendee("D14", "Jasmine Sultan", "Jasmine"),
        Attendee("D15", "Pocahontas Powhatan", "Pocahontas"),
        Attendee("D16", "Fa Mulan", "Mulan"),
        Attendee("D17", "Tiana Rogers", "Tiana"),
        Attendee("D18", "Rapunzel Corona", "Rapunzel"),
        Attendee("D19", "Merida DunBroch", "Merida"),
        Attendee("D20", "Elsa Arendelle", "Elsa"),
        Attendee("D21", "Anna Arendelle", "Anna"),
        Attendee("D22", "Moana Waialiki", "Moana"),
        Attendee("D23", "Simba Lion", "Simba"),
        Attendee("D24", "Nala Lion", "Nala"),
        Attendee("D25", "Timon Meerkat", "Timon"),
        Attendee("D26", "Pumbaa Warthog", "Pumbaa"),
        Attendee("D27", "Mufasa Lion", "Mufasa"),
        Attendee("D28", "Scar Lion", "Scar"),
        Attendee("D29", "Woody Pride", "Woody"),
        Attendee("D30", "Buzz Lightyear", "Buzz"),
        Attendee("D31", "Jessie Cowgirl", "Jessie"),
        Attendee("D32", "Rex Dinosaur", "Rex"),
        Attendee("D33", "Hamm Pig", "Hamm"),
        Attendee("D34", "Slinky Dog", "Slinky"),
        Attendee("D35", "Lightning McQueen", "Lightning"),
        Attendee("D36", "Mater Tow", "Mater"),
        Attendee("D37", "Sally Carrera", "Sally"),
        Attendee("D38", "Doc Hudson", "Doc"),
        Attendee("D39", "Joy Emotion", "Joy"),
        Attendee("D40", "Sadness Emotion", "Sadness"),
        Attendee("D41", "Anger Emotion", "Anger"),
        Attendee("D42", "Fear Emotion", "Fear"),
        Attendee("D43", "Disgust Emotion", "Disgust"),
        Attendee("D44", "Hiro Hamada", "Hiro"),
        Attendee("D45", "Baymax Robot", "Baymax"),
        Attendee("D46", "Stitch Experiment", "Stitch"),
        Attendee("D47", "Lilo Pelekai", "Lilo"),
        Attendee("D48", "Peter Pan", "Peter"),
        Attendee("D49", "Wendy Darling", "Wendy"),
        Attendee("D50", "Captain Hook", "Hook")
    )

    val mappings = listOf(
        // Mickey & Friends
        AttendeeGroupMapping("D01", "MICKEY"),
        AttendeeGroupMapping("D02", "MICKEY"),
        AttendeeGroupMapping("D03", "MICKEY"),
        AttendeeGroupMapping("D04", "MICKEY"),
        AttendeeGroupMapping("D05", "MICKEY"),
        AttendeeGroupMapping("D06", "MICKEY"),
        AttendeeGroupMapping("D07", "MICKEY"),
        AttendeeGroupMapping("D08", "MICKEY"),

        // Princesses
        AttendeeGroupMapping("D09", "PRINCESS"), AttendeeGroupMapping("D09", "ROYALTY"),
        AttendeeGroupMapping("D10", "PRINCESS"), AttendeeGroupMapping("D10", "ROYALTY"),
        AttendeeGroupMapping("D11", "PRINCESS"), AttendeeGroupMapping("D11", "ROYALTY"),
        AttendeeGroupMapping("D12", "PRINCESS"), AttendeeGroupMapping("D12", "ROYALTY"),
        AttendeeGroupMapping("D13", "PRINCESS"), AttendeeGroupMapping("D13", "ROYALTY"),
        AttendeeGroupMapping("D14", "PRINCESS"), AttendeeGroupMapping("D14", "ROYALTY"),
        AttendeeGroupMapping("D15", "PRINCESS"),
        AttendeeGroupMapping("D16", "PRINCESS"),
        AttendeeGroupMapping("D17", "PRINCESS"), AttendeeGroupMapping("D17", "ROYALTY"),
        AttendeeGroupMapping("D18", "PRINCESS"), AttendeeGroupMapping("D18", "ROYALTY"),
        AttendeeGroupMapping("D19", "PRINCESS"), AttendeeGroupMapping("D19", "ROYALTY"),
        AttendeeGroupMapping("D22", "PRINCESS"),

        // Frozen
        AttendeeGroupMapping("D20", "FROZEN"), AttendeeGroupMapping("D20", "ROYALTY"),
        AttendeeGroupMapping("D21", "FROZEN"), AttendeeGroupMapping("D21", "ROYALTY"),

        // Lion King
        AttendeeGroupMapping("D23", "LIONKING"), AttendeeGroupMapping("D23", "ROYALTY"),
        AttendeeGroupMapping("D24", "LIONKING"), AttendeeGroupMapping("D24", "ROYALTY"),
        AttendeeGroupMapping("D25", "LIONKING"),
        AttendeeGroupMapping("D26", "LIONKING"),
        AttendeeGroupMapping("D27", "LIONKING"), AttendeeGroupMapping("D27", "ROYALTY"),
        AttendeeGroupMapping("D28", "LIONKING"), AttendeeGroupMapping("D28", "ROYALTY"), AttendeeGroupMapping("D28", "VILLAINS"),

        // Toy Story
        AttendeeGroupMapping("D29", "TOYSTORY"),
        AttendeeGroupMapping("D30", "TOYSTORY"),
        AttendeeGroupMapping("D31", "TOYSTORY"),
        AttendeeGroupMapping("D32", "TOYSTORY"),
        AttendeeGroupMapping("D33", "TOYSTORY"),
        AttendeeGroupMapping("D34", "TOYSTORY"),

        // Cars
        AttendeeGroupMapping("D35", "CARS"),
        AttendeeGroupMapping("D36", "CARS"),
        AttendeeGroupMapping("D37", "CARS"),
        AttendeeGroupMapping("D38", "CARS"),

        // Inside Out
        AttendeeGroupMapping("D39", "INSIDEOUT"),
        AttendeeGroupMapping("D40", "INSIDEOUT"),
        AttendeeGroupMapping("D41", "INSIDEOUT"),
        AttendeeGroupMapping("D42", "INSIDEOUT"),
        AttendeeGroupMapping("D43", "INSIDEOUT"),

        // Hero 6
        AttendeeGroupMapping("D44", "HERO6"),
        AttendeeGroupMapping("D45", "HERO6"),

        // Stitch
        AttendeeGroupMapping("D46", "STITCH"),
        AttendeeGroupMapping("D47", "STITCH"),

        // Peter Pan
        AttendeeGroupMapping("D48", "PETERPAN"),
        AttendeeGroupMapping("D49", "PETERPAN"),
        AttendeeGroupMapping("D50", "PETERPAN"), AttendeeGroupMapping("D50", "VILLAINS")
    )
    
    fun generateRecentEvents(days: Int): List<sg.org.bcc.attendance.data.local.entities.Event> {
        val today = java.time.LocalDate.now()
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyMMdd")
        val eventNames = listOf(
            "Mickey's Celebration",
            "Magic Kingdom Gathering",
            "Princess Royal Ball",
            "Frozen Adventure",
            "Lion King Pride Meeting",
            "Toy Story Playtime"
        )

        return (0..days step 7).mapIndexed { index, daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val dateStr = date.format(dateFormatter)
            val name = eventNames[index % eventNames.size]
            sg.org.bcc.attendance.data.local.entities.Event(
                id = "demo-event-$daysAgo",
                title = "$dateStr 1030 $name",
                date = date.toString(),
                time = "1030",
                cloudEventId = (100000000 + index * 12345).toString()
            )
        }
    }
}
