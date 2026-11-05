package com.jetbrains.kmpapp.data.widget

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class WidgetSelectionEngineTest {

    private val engine = WidgetSelectionEngine(timeZone = TimeZone.of("Europe/Moscow"))

    // 23.10.2026, 10:00 по Москве (UTC+3).
    private val now = Instant.parse("2026-10-23T07:00:00Z")

    private fun lesson(
        id: String,
        date: String = "2026-10-23",
        start: String = "09:00",
        end: String = "10:30",
        bell: Int = 1,
        subject: String = "Математика",
        room: String = "341 (С-20)",
        teacher: String = "Иванов И.И.",
        type: String = "ЛК",
        groups: List<String> = listOf("g1")
    ) = WidgetLesson(
        id = id,
        date = date,
        bell = bell,
        start = start,
        end = end,
        subject = subject,
        type = type,
        room = room,
        teacher = teacher,
        groups = groups
    )

    private fun payload(
        vararg lessons: WidgetLesson,
        defaultTargetId: Int? = 1,
        generatedAt: Long = now.toEpochMilliseconds()
    ) = WidgetPayload(
        generatedAt = generatedAt,
        defaultTargetId = defaultTargetId,
        targets = listOf(WidgetTarget(1, "ИС-21", "Группа", lessons.toList()))
    )

    @Test
    fun nullPayloadIsNoData() {
        val state = engine.makeState(null, now = now)
        assertEquals(WidgetEmptyReason.NO_DATA, state.emptyReason)
        assertTrue(state.isEmpty)
    }

    @Test
    fun noTargetsIsNoTarget() {
        val state = engine.makeState(WidgetPayload(now.toEpochMilliseconds()), now = now)
        assertEquals(WidgetEmptyReason.NO_TARGET, state.emptyReason)
    }

    @Test
    fun emptyLessonsIsNoLessons() {
        val state = engine.makeState(payload(), now = now)
        assertEquals(WidgetEmptyReason.NO_LESSONS, state.emptyReason)
        assertEquals("ИС-21", state.targetTitle)
    }

    @Test
    fun ongoingLessonIsPrimaryWithProgress() {
        val state = engine.makeState(payload(lesson("a")), now = now)
        assertNotNull(state.primary)
        assertEquals("СЕЙЧАС", state.statusLabel)
        assertTrue(state.isCurrent)
        assertEquals(2f / 3f, state.progress!!, 0.001f)
        assertEquals("СЕГОДНЯ · ПЯТНИЦА", state.dayHeader)
    }

    @Test
    fun soonLessonCountsDown() {
        val soon = lesson("b", start = "10:20", end = "11:50", bell = 2)
        val state = engine.makeState(payload(soon), now = now)
        assertEquals("СКОРО", state.statusLabel)
        assertEquals(20, state.soonMinutes)
        assertEquals("ЧЕРЕЗ 20 МИН", state.countdownLabel)
        assertNull(state.progress)
    }

    @Test
    fun laterLessonIsNext() {
        val later = lesson("c", start = "12:40", end = "14:10", bell = 3)
        val state = engine.makeState(payload(later), now = now)
        assertEquals("ДАЛЕЕ", state.statusLabel)
        assertNull(state.soonMinutes)
    }

    @Test
    fun fallbackToFirstLessonOfNextDay() {
        val monday = lesson("d", date = "2026-10-26", start = "09:00", end = "10:30")
        val state = engine.makeState(payload(monday), now = now)
        assertNotNull(state.primary)
        assertEquals("ПН, 26 ОКТ", state.dayHeader)
        assertEquals(false, state.isToday)
        assertEquals("ДАЛЕЕ", state.statusLabel)
    }

    @Test
    fun upcomingAndNextDaysAreFilled() {
        val current = lesson("a", start = "09:00", end = "10:30")
        val nextSameDay = lesson("e", start = "10:40", end = "12:10", bell = 2)
        val tomorrow = lesson("f", date = "2026-10-24", start = "09:00", end = "10:30")
        val state = engine.makeState(payload(current, nextSameDay, tomorrow), now = now)
        assertEquals(listOf("e"), state.upcoming.map { it.id })
        assertEquals(listOf("СБ, 24 ОКТ"), state.nextDays.take(1).map { it.label })
        assertEquals(listOf("f"), state.nextDays.first().lessons.map { it.id })
    }

    @Test
    fun staleSnapshotIsReported() {
        val generatedAt = now.toEpochMilliseconds() - 40L * 3_600_000L
        val state = engine.makeState(payload(lesson("a"), generatedAt = generatedAt), now = now)
        assertEquals(40, state.staleHours)
    }

    @Test
    fun preferredTargetWinsOverDefault() {
        val payload = WidgetPayload(
            generatedAt = now.toEpochMilliseconds(),
            defaultTargetId = 1,
            targets = listOf(
                WidgetTarget(1, "ИС-21", "Группа", listOf(lesson("a"))),
                WidgetTarget(2, "Петров", "Преподаватель", listOf(lesson("b", start = "10:20")))
            )
        )
        val state = engine.makeState(payload, preferredTargetId = 2, now = now)
        assertEquals("Петров", state.targetTitle)
        assertEquals("b", state.primary?.id)
    }

    @Test
    fun codecRoundTrip() {
        val original = payload(lesson("a"), lesson("b", date = "2026-10-24"))
        val restored = WidgetPayloadCodec.decode(WidgetPayloadCodec.encode(original))
        assertEquals(original, restored)
    }

    @Test
    fun codecIgnoresUnknownKeysAndBadJson() {
        val json = """{"generatedAt":1,"defaultTargetId":1,"targets":[],"futureField":true}"""
        val decoded = WidgetPayloadCodec.decode(json)
        assertEquals(1L, decoded?.generatedAt)
        assertNull(WidgetPayloadCodec.decode("not-json"))
        assertNull(WidgetPayloadCodec.decode(null))
    }

    @Test
    fun groupsPluralization() {
        assertEquals("1 группа", WidgetLabels.groupsLabel(1))
        assertEquals("2 группы", WidgetLabels.groupsLabel(2))
        assertEquals("5 групп", WidgetLabels.groupsLabel(5))
        assertEquals("11 групп", WidgetLabels.groupsLabel(11))
        assertEquals("21 группа", WidgetLabels.groupsLabel(21))
    }

    @Test
    fun roomDisplayAndSplit() {
        assertEquals("Онлайн", WidgetLabels.roomDisplay(lesson("x", room = "СДО")))
        assertEquals("Аудитория уточняется", WidgetLabels.roomDisplay(lesson("x", room = " ")))
        assertEquals("341" to "С-20", WidgetLabels.splitClassroom("341 (С-20)"))
    }

    // MARK: Сокращение названий (параметр виджета Android / iOS)

    @Test
    fun subjectDisplayKeepsFullTitleWhenAbbreviationOff() {
        val lesson = lesson("x", subject = "Базы данных и управление ими")
        assertEquals(
            "Базы данных и управление ими",
            WidgetLabels.subjectDisplay(lesson, abbreviated = false)
        )
    }

    @Test
    fun subjectDisplayAbbreviatesLongTitles() {
        // Правило экрана расписания дословно: слово длиннее двух букв —
        // заглавный инициал, короче — строчная первая буква. «Базы данных
        // и управление ими» → «БДиУИ» («и» строчная, «ими» заглавная).
        val lesson = lesson("x", subject = "Базы данных и управление ими")
        assertEquals("БДиУИ", WidgetLabels.subjectDisplay(lesson, abbreviated = true))
    }

    @Test
    fun subjectDisplayLeavesShortTitlesAlone() {
        assertEquals("Физика", WidgetLabels.abbreviateSubject("Физика"))
        assertEquals("Базы данных", WidgetLabels.abbreviateSubject("Базы данных"))
    }

    @Test
    fun subjectDisplayHyphenIsWordBoundary() {
        // «Проектирование-и-разработка» → «ПиР»: дефис делит слова, союз
        // «и» остаётся строчным.
        assertEquals("ПиР", WidgetLabels.abbreviateSubject("Проектирование-и-разработка"))
    }
}
