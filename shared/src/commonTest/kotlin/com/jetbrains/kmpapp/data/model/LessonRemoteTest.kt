package com.jetbrains.kmpapp.data.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Дистанционные пары: фид кладёт «СДО» (сайт вуза online-edu.mirea.ru) в поле
 * аудиторий. Это не аудитория и не корпус — UI обязан это различать, иначе
 * карточка рисует «СДО» со значком локации, как будто это место в корпусе.
 */
class LessonRemoteTest {

    private fun lesson(classrooms: List<String>) = Lesson(
        id = "l1",
        subject = "Базы данных",
        lessonType = LessonType.PRACTICE,
        teachers = emptyList(),
        classrooms = classrooms,
        bellNumber = 3,
        startTime = "12:00",
        endTime = "13:30",
        date = LocalDate(2026, 10, 28),
        groups = listOf("БИСО-02-23")
    )

    @Test
    fun sdoMarksLessonAsRemote() {
        assertTrue(lesson(listOf("СДО")).isRemote)
        assertTrue(lesson(listOf("сдо")).isRemote, "регистр не важен: фид писал и так, и так")
    }

    @Test
    fun physicalRoomIsNotRemote() {
        assertFalse(lesson(listOf("341 (С-20)")).isRemote)
        assertFalse(lesson(emptyList()).isRemote)
    }

    @Test
    fun physicalClassroomsFilterOutSdo() {
        // Смешанный случай: пара прошла и в аудитории, и дистанционно.
        assertEquals(
            listOf("341 (С-20)"),
            lesson(listOf("341 (С-20)", "СДО")).physicalClassrooms
        )
        assertTrue(lesson(listOf("СДО")).physicalClassrooms.isEmpty())
    }

    @Test
    fun mixedLessonIsRemoteAndKeepsRooms() {
        val mixed = lesson(listOf("341 (С-20)", "СДО"))
        assertTrue(mixed.isRemote)
        assertEquals(1, mixed.physicalClassrooms.size)
    }
}
