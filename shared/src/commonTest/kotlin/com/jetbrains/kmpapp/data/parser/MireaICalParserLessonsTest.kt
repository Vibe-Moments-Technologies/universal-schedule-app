package com.jetbrains.kmpapp.data.parser

import com.jetbrains.kmpapp.data.model.LessonType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * Типы пар, номера пар, RRULE/EXDATE — ядро парсера.
 * Ломается здесь → расписание показывает мусор у всех пользователей.
 */
class MireaICalParserLessonsTest {

    private fun event(
        type: String,
        start: String = "20260915T090000",
        end: String = "20260915T103000",
        rrule: String? = null,
        exdate: String? = null,
        transp: String = "OPAQUE"
    ): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VEVENT")
        sb.appendLine("DTSTART;TZID=Europe/Moscow:$start")
        sb.appendLine("DTEND;TZID=Europe/Moscow:$end")
        sb.appendLine("SUMMARY:Тестовый предмет")
        sb.appendLine("X-META-LESSON_TYPE:$type")
        sb.appendLine("TRANSP:$transp")
        sb.appendLine("UID:test-uid")
        if (rrule != null) sb.appendLine("RRULE:$rrule")
        if (exdate != null) sb.appendLine("EXDATE;TZID=Europe/Moscow:$exdate")
        sb.appendLine("END:VEVENT")
        return sb.toString()
    }

    private fun parseSingle(ical: String) = MireaICalParser.parse(ical).single()

    // ── Типы пар ──────────────────────────────────────────────

    @Test
    fun additionalTypeDetectedBeforePractice() {
        // «доп. практика» → ADDITIONAL, не PRACTICE (порядок веток критичен)
        assertEquals(LessonType.ADDITIONAL, parseSingle(event("Доп. практика")).lessonType)
        assertEquals(LessonType.ADDITIONAL, parseSingle(event("ДОП")).lessonType)
        assertEquals(LessonType.ADDITIONAL, parseSingle(event("дополнительное занятие")).lessonType)
    }

    @Test
    fun standardTypes() {
        assertEquals(LessonType.LECTURE, parseSingle(event("ЛК")).lessonType)
        assertEquals(LessonType.LECTURE, parseSingle(event("Лекция")).lessonType)
        assertEquals(LessonType.PRACTICE, parseSingle(event("ПР")).lessonType)
        assertEquals(LessonType.PRACTICE, parseSingle(event("Практические занятия")).lessonType)
        assertEquals(LessonType.LAB, parseSingle(event("ЛАБ")).lessonType)
        assertEquals(LessonType.LAB, parseSingle(event("Лабораторная работа")).lessonType)
        assertEquals(LessonType.OTHER, parseSingle(event("Экзамен")).lessonType)
        assertEquals(LessonType.OTHER, parseSingle(event("")).lessonType)
    }

    // ── Номера пар по времени ─────────────────────────────────

    @Test
    fun bellNumbers() {
        assertEquals(1, parseSingle(event("ЛК", start = "20260915T090000")).bellNumber)
        assertEquals(2, parseSingle(event("ЛК", start = "20260915T104000")).bellNumber)
        assertEquals(3, parseSingle(event("ЛК", start = "20260915T124000")).bellNumber)
        assertEquals(4, parseSingle(event("ЛК", start = "20260915T142000")).bellNumber)
        assertEquals(5, parseSingle(event("ЛК", start = "20260915T160000")).bellNumber)
        assertEquals(6, parseSingle(event("ЛК", start = "20260915T180000")).bellNumber)
        assertEquals(7, parseSingle(event("ЛК", start = "20260915T194000")).bellNumber)
    }

    // ── Transparent события пропускаются ──────────────────────

    @Test
    fun transparentEventsSkipped() {
        assertTrue(MireaICalParser.parse(event("ЛК", transp = "TRANSPARENT")).isEmpty())
    }

    // ── RRULE: еженедельное повторение ────────────────────────

    @Test
    fun weeklyRruleExpandsDates() {
        val ical = event("ЛК", rrule = "FREQ=WEEKLY;UNTIL=20261013T235959")
        val lessons = MireaICalParser.parse(ical)
        assertEquals(5, lessons.size) // 15, 22, 29 сент + 6, 13 окт
        assertEquals(LocalDate(2026, 9, 15), lessons.first().date)
        assertEquals(LocalDate(2026, 10, 13), lessons.last().date)
    }

    @Test
    fun biweeklyRrule() {
        val ical = event("ЛК", rrule = "FREQ=WEEKLY;INTERVAL=2;UNTIL=20261027T235959")
        val lessons = MireaICalParser.parse(ical)
        assertEquals(4, lessons.size) // 15, 29 сент + 13, 27 окт
        assertEquals(LocalDate(2026, 9, 29), lessons[1].date)
    }

    // ── EXDATE: исключения ────────────────────────────────────

    @Test
    fun exdateRemovesDates() {
        val ical = event(
            "ЛК",
            rrule = "FREQ=WEEKLY;UNTIL=20261013T235959",
            exdate = "20260922T090000"
        )
        val lessons = MireaICalParser.parse(ical)
        assertEquals(4, lessons.size)
        assertTrue(lessons.none { it.date == LocalDate(2026, 9, 22) })
    }

    @Test
    fun exdateWithoutRrule() {
        val ical = event("ЛК", exdate = "20260915T090000")
        assertTrue(MireaICalParser.parse(ical).isEmpty())
    }

    // ── Пустой фид ────────────────────────────────────────────

    @Test
    fun emptyFeedGivesNoLessons() {
        assertTrue(MireaICalParser.parse("BEGIN:VCALENDAR\nEND:VCALENDAR").isEmpty())
    }
}
