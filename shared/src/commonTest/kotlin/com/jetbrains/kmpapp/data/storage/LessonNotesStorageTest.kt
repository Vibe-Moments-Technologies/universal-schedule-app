package com.jetbrains.kmpapp.data.storage

import com.jetbrains.kmpapp.data.model.LessonNote
import com.jetbrains.kmpapp.data.model.NoteScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Заметки живут файлом, а не в PlatformStorage: они обязаны переживать
 * clearAll() («сброс данных»), очистку кеша и любые перезаписи расписания.
 * Раньше ключ лежал в общем контейнере и терялся при сбросе — баг
 * «расписание обновилось, заметки пропали».
 */
class LessonNotesStorageTest {

    private lateinit var platform: FakePlatformStorage
    private lateinit var file: FakeFileStorage
    private lateinit var storage: LessonNotesStorage

    @BeforeTest
    fun setUp() {
        platform = FakePlatformStorage()
        file = FakeFileStorage()
        storage = LessonNotesStorage(platform, file)
    }

    @AfterTest
    fun tearDown() {
        // Каждое LessonNotesStorage имеет свой scope; GC заботится,
        // тесты короткие — утечек нет.
    }

    @Test
    fun savesAndReadsBack() {
        storage.saveLessonNote(1, "2026-10-28", 3, "Принести методичку")
        assertEquals("Принести методичку", storage.getLessonNote(1, "2026-10-28", 3)?.text)
        storage.persistNow()
        assertTrue(file.content.orEmpty().contains("Принести методичку"))
    }

    @Test
    fun survivesPlatformClearAll() {
        storage.saveSubjectNote(1, "Базы данных", "Конспект в ноутбуке")
        storage.persistNow()
        platform.clearAll()
        // Новый экземпляр читает файл — заметка на месте.
        val reloaded = LessonNotesStorage(platform, FakeFileStorage(file.content))
        assertEquals("Конспект в ноутбуке", reloaded.getSubjectNote(1, "Базы данных")?.text)
    }

    @Test
    fun migratesFromLegacyPrefsKey() {
        val legacyNote = LessonNote(
            id = "1",
            targetId = 7,
            scope = NoteScope.SUBJECT,
            noteKey = "7_физика",
            text = "Старая заметка из prefs",
            createdAt = 1L,
            updatedAt = 1L
        )
        val json = Json { ignoreUnknownKeys = true }
        platform.saved["krasava_lesson_notes"] = json.encodeToString(listOf(legacyNote))

        val migrated = LessonNotesStorage(platform, file)
        assertEquals("Старая заметка из prefs", migrated.getSubjectNote(7, "физика")?.text)
        // Ключ снят, файл стал источником истины.
        assertNull(platform.saved["krasava_lesson_notes"])
        assertTrue(file.content.orEmpty().contains("Старая заметка из prefs"))
    }

    @Test
    fun fileWinsWhenBothExist() {
        // Файл уже есть (прошлая миграция), а в prefs кто-то снова положил
        // старый ключ: файл приоритетен, данные не откатываются.
        val json = Json { ignoreUnknownKeys = true }
        val fileNote = LessonNote(
            id = "2", targetId = 1, scope = NoteScope.LESSON,
            noteKey = "1_2026-10-28_2", text = "Из файла",
            createdAt = 2L, updatedAt = 2L
        )
        file.content = json.encodeToString(listOf(fileNote))
        platform.saved["krasava_lesson_notes"] = json.encodeToString(listOf(fileNote.copy(text = "Из prefs")))

        val loaded = LessonNotesStorage(platform, file)
        assertEquals("Из файла", loaded.getLessonNote(1, "2026-10-28", 2)?.text)
    }

    @Test
    fun remapMovesNoteWhenLessonShifts() {
        // Баг владельца: «обновил расписание — заметки стёрлись».
        // Пару сдвинули (дата+звонок), заметка должна переехать за ней.
        storage.saveLessonNote(1, "2026-10-28", 3, "Принести методичку")

        val old = listOf(lesson(date = "2026-10-28", bell = 3, subject = "Базы данных"))
        // Пара та же (предмет/тип), но 5-я пара на другой дате.
        val new = listOf(lesson(date = "2026-11-04", bell = 5, subject = "Базы данных"))

        storage.remapLessonNotes(1, old, new)
        assertEquals("Принести методичку", storage.getLessonNote(1, "2026-11-04", 5)?.text)
        assertNull(storage.getLessonNote(1, "2026-10-28", 3))
    }

    @Test
    fun remapKeepsNoteWhenLessonStays() {
        storage.saveLessonNote(1, "2026-10-28", 3, "Принести методичку")
        val old = listOf(lesson(date = "2026-10-28", bell = 3, subject = "Базы данных"))
        storage.remapLessonNotes(1, old, old)
        assertEquals("Принести методичку", storage.getLessonNote(1, "2026-10-28", 3)?.text)
    }

    @Test
    fun remapKeepsWeeklyLessonNoteOnItsDate() {
        // Еженедельная пара живёт на нескольких датах: заметка к одной
        // из них не должна «переезжать» на другую при обновлении.
        storage.saveLessonNote(1, "2026-10-28", 3, "К этой среде")
        val old = listOf(
            lesson(date = "2026-10-21", bell = 3, subject = "Базы данных"),
            lesson(date = "2026-10-28", bell = 3, subject = "Базы данных")
        )
        val new = listOf(
            lesson(date = "2026-10-21", bell = 3, subject = "Базы данных"),
            lesson(date = "2026-10-28", bell = 3, subject = "Базы данных"),
            lesson(date = "2026-11-04", bell = 3, subject = "Базы данных")
        )
        storage.remapLessonNotes(1, old, new)
        assertEquals("К этой среде", storage.getLessonNote(1, "2026-10-28", 3)?.text)
    }

    private fun lesson(date: String, bell: Int, subject: String) =
        com.jetbrains.kmpapp.data.model.Lesson(
            id = "x_${date}_$bell",
            subject = subject,
            lessonType = com.jetbrains.kmpapp.data.model.LessonType.PRACTICE,
            teachers = emptyList(),
            classrooms = emptyList(),
            bellNumber = bell,
            startTime = "12:00",
            endTime = "13:30",
            date = kotlinx.datetime.LocalDate.parse(date),
            groups = listOf("БИСО-02-23")
        )

    private class FakePlatformStorage : KeyValueStore {
        val saved = mutableMapOf<String, String>()
        override fun saveString(key: String, value: String) { saved[key] = value }
        override fun getString(key: String): String? = saved[key]
        override fun remove(key: String) { saved.remove(key) }
        override fun clearAll() { saved.clear() }
    }

    private class FakeFileStorage(var content: String? = null) : NotesFileStorage() {
        override fun read(): String? = content
        override fun write(content: String): Boolean {
            this.content = content
            return true
        }
        override fun delete(): Boolean {
            content = null
            return true
        }
    }
}
