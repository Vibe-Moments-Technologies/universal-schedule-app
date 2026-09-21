package com.jetbrains.kmpapp.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class DateUtilsTest {

    // Осенний семестр 2026: 1 сентября — вторник. Первая (неполная) неделя
    // всё равно должна считаться первой, а текущая — третьей.
    @Test
    fun weekNumberCountsPartialFirstWeek() {
        assertEquals(1, DateUtils.getWeekInfo(LocalDate(2026, 9, 1)).weekNumber)
        assertEquals(2, DateUtils.getWeekInfo(LocalDate(2026, 9, 7)).weekNumber)
        assertEquals(3, DateUtils.getWeekInfo(LocalDate(2026, 9, 16)).weekNumber)
    }

    @Test
    fun springSemesterStartsOnMonday() {
        assertEquals(1, DateUtils.getWeekInfo(LocalDate(2026, 2, 9)).weekNumber)
        assertEquals(2, DateUtils.getWeekInfo(LocalDate(2026, 2, 16)).weekNumber)
    }

    @Test
    fun parityFollowsWeekNumber() {
        assertTrue(DateUtils.getWeekInfo(LocalDate(2026, 9, 7)).isEven)
        assertFalse(DateUtils.getWeekInfo(LocalDate(2026, 9, 16)).isEven)
    }

    @Test
    fun feedMarkersTakePrecedenceOverCalculation() {
        SemesterWeeks.set(listOf(WeekMarker(5, LocalDate(2026, 9, 14))))
        try {
            assertEquals(5, DateUtils.getWeekInfo(LocalDate(2026, 9, 16)).weekNumber)
        } finally {
            SemesterWeeks.set(emptyList())
        }
        // фолбэк снова работает
        assertEquals(3, DateUtils.getWeekInfo(LocalDate(2026, 9, 16)).weekNumber)
    }

    // Осень 2026: маркер «1 неделя» в фиде начинается со вторника 01.09,
    // но понедельник 31.08 той же недели тоже должен давать неделю 1.
    @Test
    fun markerStartingMidWeekCoversWholeWeek() {
        SemesterWeeks.set(
            listOf(
                WeekMarker(1, LocalDate(2026, 9, 1)),
                WeekMarker(2, LocalDate(2026, 9, 7))
            )
        )
        try {
            assertEquals(1, DateUtils.getWeekInfo(LocalDate(2026, 8, 31)).weekNumber)
            assertEquals(1, DateUtils.getWeekInfo(LocalDate(2026, 9, 1)).weekNumber)
            assertEquals(2, DateUtils.getWeekInfo(LocalDate(2026, 9, 7)).weekNumber)
        } finally {
            SemesterWeeks.set(emptyList())
        }
    }
}
