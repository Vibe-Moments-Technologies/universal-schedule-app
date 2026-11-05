package com.jetbrains.kmpapp.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class MireaICalParserWeeksTest {

    private val sample = """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        DTSTART;VALUE=DATE:20260901
        DTEND;VALUE=DATE:20260907
        SUMMARY:1 неделя
        TRANSP:TRANSPARENT
        UID:w1
        END:VEVENT
        BEGIN:VEVENT
        DTSTART;VALUE=DATE:20260907
        DTEND;VALUE=DATE:20260914
        SUMMARY:2 неделя
        TRANSP:TRANSPARENT
        UID:w2
        END:VEVENT
        BEGIN:VEVENT
        DTSTART;TZID=Europe/Moscow:20260914T124000
        DTEND;TZID=Europe/Moscow:20260914T141000
        SUMMARY:ПР Методы и средства сборки
        TRANSP:OPAQUE
        UID:l1
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun parsesWeekMarkersOnlyFromTransparentEvents() {
        val markers = MireaICalParser.parseWeekMarkers(sample)
        assertEquals(2, markers.size)
        assertEquals(1, markers[0].number)
        assertEquals(LocalDate(2026, 9, 1), markers[0].monday)
        assertEquals(2, markers[1].number)
        assertEquals(LocalDate(2026, 9, 7), markers[1].monday)
    }

    @Test
    fun emptyFeedGivesNoMarkers() {
        assertEquals(0, MireaICalParser.parseWeekMarkers("BEGIN:VCALENDAR\nEND:VCALENDAR").size)
    }
}
