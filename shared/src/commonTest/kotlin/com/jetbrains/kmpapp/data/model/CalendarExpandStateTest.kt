package com.jetbrains.kmpapp.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Цепочка раскрытия ленты календаря (issue #3):
 * неделя → две недели → месяц, свайпом вверх — обратно.
 *
 * Это чистая логика переходов: UI-часть (жест, вёрстка второй строки)
 * в тестах не участвует, а вот порядок ступеней — контракт, на который
 * опираются и жест, и экран расписания (MONTH открывает оверлей).
 */
class CalendarExpandStateTest {

    @Test
    fun swipeDownWalksUpTheChain() {
        assertEquals(CalendarExpandState.TWO_WEEKS, CalendarExpandState.WEEK.onSwipeDown())
        assertEquals(CalendarExpandState.MONTH, CalendarExpandState.TWO_WEEKS.onSwipeDown())
        // Месяц — потолок: дальше раскрыться нельзя.
        assertEquals(CalendarExpandState.MONTH, CalendarExpandState.MONTH.onSwipeDown())
    }

    @Test
    fun swipeUpWalksDownTheChain() {
        assertEquals(CalendarExpandState.TWO_WEEKS, CalendarExpandState.MONTH.onSwipeUp())
        assertEquals(CalendarExpandState.WEEK, CalendarExpandState.TWO_WEEKS.onSwipeUp())
        // Неделя — пол: ниже не падаем (сворачивание ленты — отдельная ось).
        assertEquals(CalendarExpandState.WEEK, CalendarExpandState.WEEK.onSwipeUp())
    }

    @Test
    fun roundTripRestoresOriginalState() {
        // Раскрыл до конца, свернул до конца — вернулся к неделе.
        val walkedUp = CalendarExpandState.WEEK.onSwipeDown().onSwipeDown()
        assertEquals(CalendarExpandState.MONTH, walkedUp)
        val walkedBack = walkedUp.onSwipeUp().onSwipeUp()
        assertEquals(CalendarExpandState.WEEK, walkedBack)
    }

    @Test
    fun isExpandedMarksNonDefaultSteps() {
        assertFalse(CalendarExpandState.WEEK.isExpanded)
        assertTrue(CalendarExpandState.TWO_WEEKS.isExpanded)
        assertTrue(CalendarExpandState.MONTH.isExpanded)
    }

    @Test
    fun unknownNameFallsBackToWeek() {
        // Ключа нет (свежая установка, сброс) или значение от старой версии.
        assertEquals(CalendarExpandState.WEEK, CalendarExpandState.fromName(null))
        assertEquals(CalendarExpandState.WEEK, CalendarExpandState.fromName("NO_SUCH_STATE"))
        assertEquals(CalendarExpandState.WEEK, CalendarExpandState.fromName(""))
    }

    @Test
    fun namesRoundTrip() {
        // Сериализация в хранилище: имя ↔ значение без потерь.
        CalendarExpandState.entries.forEach { state ->
            assertEquals(state, CalendarExpandState.fromName(state.name))
        }
    }

    @Test
    fun distinctStatesHaveDistinctNames() {
        // Хранение строкой: одинаковые имена значили бы потерю ступени.
        val names = CalendarExpandState.entries.map { it.name }
        assertEquals(names.size, names.distinct().size)
        assertNotEquals(
            CalendarExpandState.WEEK.name,
            CalendarExpandState.TWO_WEEKS.name
        )
    }
}
