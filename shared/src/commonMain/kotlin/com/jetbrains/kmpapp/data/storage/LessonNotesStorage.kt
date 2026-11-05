package com.jetbrains.kmpapp.data.storage

import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonNote
import com.jetbrains.kmpapp.data.model.NoteScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Хранилище заметок, привязанных к расписанию (R2).
 *
 * Ключи:
 *  - LESSON:  "{targetId}_{date}_{bellNumber}" — заметка к конкретной паре
 *  - SUBJECT: "{targetId}_{subject.lowercase()}" — заметка к предмету
 *
 * Персистентность: файл lesson_notes.json в documents/files-папке
 * ([NotesFileStorage]) — заметки это пользовательский контент, не кеш:
 * они переживают «очистить кеш», «сбросить данные» и пересоздание
 * расписания. Раньше жили в PlatformStorage и терялись при clearAll().
 *
 * Миграция: при первом чтении заметки из старого ключа prefs
 * (krasava_lesson_notes) переезжают в файл, ключ снимается.
 */
class LessonNotesStorage(
    private val legacyStore: KeyValueStore,
    private val fileStorage: NotesFileStorage
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _notes = MutableStateFlow<List<LessonNote>>(emptyList())
    val notes: StateFlow<List<LessonNote>> = _notes.asStateFlow()

    init {
        load()
    }

    private fun load() {
        try {
            val fromFile = fileStorage.read()
            if (!fromFile.isNullOrBlank()) {
                _notes.value = json.decodeFromString(fromFile)
                return
            }
            // Миграция со старого ключа prefs: заметки, записанные до
            // перевода на файл, не должны потеряться.
            val legacy = legacyStore.getString(KEY_LEGACY)
            if (!legacy.isNullOrBlank()) {
                val parsed = json.decodeFromString<List<LessonNote>>(legacy)
                _notes.value = parsed
                fileStorage.write(json.encodeToString(parsed))
                legacyStore.remove(KEY_LEGACY)
            }
        } catch (t: Throwable) {
            println("LessonNotesStorage: failed to load: ${t.message}")
        }
    }

    /**
     * Запись файла — синхронная. Асинхронной она была зря: JSON заметок —
     * килобайты, запись мгновенная, а «смахнутое сразу после шита»
     * приложение убивало корутину до записи — заметки терялись при
     * перезапуске (репорт владельца: «в приложении есть, после
     * перезапуска — нет»). Гарантия «закрыл шит = записано» важнее
     * экономии миллисекунды.
     */
    private fun persist() {
        try {
            fileStorage.write(json.encodeToString(_notes.value))
        } catch (e: Exception) {
            println("LessonNotesStorage: failed to persist: ${e.message}")
        }
    }

    /** Синхронная запись — см. persist(): он теперь тоже синхронный. */
    internal fun persistNow() {
        persist()
    }

    // ── Ключи ─────────────────────────────────────────────────

    fun lessonKey(targetId: Int, date: String, bellNumber: Int): String =
        "${targetId}_${date}_$bellNumber"

    fun subjectKey(targetId: Int, subject: String): String =
        "${targetId}_${subject.trim().lowercase()}"

    // ── Чтение ────────────────────────────────────────────────

    fun getLessonNote(targetId: Int, date: String, bellNumber: Int): LessonNote? =
        _notes.value.firstOrNull { it.noteKey == lessonKey(targetId, date, bellNumber) }

    fun getSubjectNote(targetId: Int, subject: String): LessonNote? =
        _notes.value.firstOrNull { it.noteKey == subjectKey(targetId, subject) }

    fun getNotesForTarget(targetId: Int): List<LessonNote> =
        _notes.value.filter { it.targetId == targetId }

    // ── Запись ────────────────────────────────────────────────

    fun saveLessonNote(targetId: Int, date: String, bellNumber: Int, text: String) {
        val key = lessonKey(targetId, date, bellNumber)
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = _notes.value.firstOrNull { it.noteKey == key }
        val note = LessonNote(
            id = existing?.id ?: now.toString(),
            targetId = targetId,
            scope = NoteScope.LESSON,
            noteKey = key,
            text = text,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        _notes.update { list ->
            list.filter { it.noteKey != key } + note
        }
        persist()
    }

    fun saveSubjectNote(targetId: Int, subject: String, text: String) {
        val key = subjectKey(targetId, subject)
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = _notes.value.firstOrNull { it.noteKey == key }
        val note = LessonNote(
            id = existing?.id ?: now.toString(),
            targetId = targetId,
            scope = NoteScope.SUBJECT,
            noteKey = key,
            text = text,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        _notes.update { list ->
            list.filter { it.noteKey != key } + note
        }
        persist()
    }

    fun deleteNote(noteId: String) {
        _notes.update { list -> list.filter { it.id != noteId } }
        persist()
    }

    // ── Каскадное удаление ────────────────────────────────────

    /**
     * Удаляет все заметки расписания — только явное удаление расписания
     * пользователем (removeTarget): заметки переживают обновления,
     * сброс данных и очистку кеша.
     */
    fun removeNotesForTarget(targetId: Int) {
        _notes.update { list -> list.filter { it.targetId != targetId } }
        persist()
    }

    /**
     * Перепривязка LESSON-заметок после обновления расписания: ключ
     * заметки — targetId_дата_звонок, и при сдвиге пары (дату/звонок
     * поменяли) заметка «отклеивалась»: не удалялась, но больше не
     * показывалась — выглядело как «обновил расписание — заметки
     * стёрлись» (репорт владельца).
     *
     * Сопоставление пар: (предмет, тип) — пара та же, когда совпадают
     * они; звонок и дата у «той же» пары вуз может поменять. Заметка
     * пары, ушедшей со своей даты, переезжает на её новое место.
     * Спорные случаи (несколько одноимённых пар, пара на нескольких
     * датах) — заметка остаётся на месте: перестраховка против ложных
     * переносов.
     */
    fun remapLessonNotes(
        targetId: Int,
        oldLessons: List<Lesson>,
        newLessons: List<Lesson>
    ) {
        if (oldLessons.isEmpty() || newLessons.isEmpty()) return

        // (дата, звонок) жив в новом расписании — заметки этого слота
        // не трогаем вовсе (еженедельные пары в т.ч.).
        val newSlots: Set<Pair<String, Int>> =
            newLessons.map { it.date.toString() to it.bellNumber }.toSet()

        // Признак пары без даты/звонка.
        data class PairId(val subject: String, val type: String)
        fun Lesson.pairId() = PairId(subject.trim().lowercase(), lessonType.name)

        // Однозначность обязательна: (subject, type) один раз в старом и
        // один раз в новом. Дубликаты (та же пара на нескольких датах) —
        // идентичны по pairId и потому «один признак, много пар» —
        // переносим только если старый слот умер и новый ровно один.
        val oldBySlot: Map<Pair<String, Int>, List<PairId>> =
            oldLessons.groupBy({ it.date.toString() to it.bellNumber }, { it.pairId() })

        _notes.update { list ->
            val updated = mutableListOf<LessonNote>()
            for (note in list) {
                if (note.targetId != targetId || note.scope != NoteScope.LESSON) {
                    updated.add(note); continue
                }
                val parts = note.noteKey.split("_")
                if (parts.size != 3) { updated.add(note); continue }
                val noteDate = parts[1]
                val noteBell = parts[2].toIntOrNull() ?: run { updated.add(note); continue }
                val slot = noteDate to noteBell

                // Слот жив в новом расписании — заметка на месте.
                if (slot in newSlots) { updated.add(note); continue }

                // Слот умер: какая пара на нём была? (однозначность)
                val pairIds = oldBySlot[slot] ?: listOf()
                if (pairIds.size != 1) { updated.add(note); continue }
                val pairId = pairIds.first()

                // Новое место той же пары; переносим, если оно одно и
                // не занято другой заметкой.
                val relocated = newLessons.filter { it.pairId() == pairId }
                if (relocated.size != 1) { updated.add(note); continue }
                val newSlot = relocated.first().date.toString() to relocated.first().bellNumber
                if (newSlot in newSlots && newSlot != slot) {
                    // Здесь заметка переехавшей пары: переносим, если на
                    // новом слоте ещё нет другой заметки.
                    val occupied = list.any {
                        it.scope == NoteScope.LESSON && it.targetId == targetId &&
                            it.noteKey == lessonKey(targetId, newSlot.first, newSlot.second)
                    }
                    if (!occupied) {
                        updated.add(
                            note.copy(
                                noteKey = lessonKey(targetId, newSlot.first, newSlot.second),
                                updatedAt = Clock.System.now().toEpochMilliseconds()
                            )
                        )
                        continue
                    }
                }
                updated.add(note)
            }
            updated
        }
        persist()
    }

    /** Удаляет заметки к прошедшим парам (старше 90 дней). */
    fun cleanOldLessonNotes(beforeEpochMs: Long): Int {
        val old = _notes.value.filter {
            it.scope == NoteScope.LESSON && it.updatedAt < beforeEpochMs
        }
        if (old.isNotEmpty()) {
            _notes.update { list -> list.filter { it !in old } }
            persist()
        }
        return old.size
    }

    /** Размер данных в байтах (для DataAndCacheScreen). */
    fun getStorageSizeBytes(): Long =
        fileStorage.read()?.encodeToByteArray()?.size?.toLong() ?: 0L

    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
        else -> "${(bytes * 10 / (1024 * 1024)) / 10.0} МБ"
    }

    companion object {
        /** Старый ключ PlatformStorage — для одноразовой миграции в файл. */
        internal const val KEY_LEGACY = "krasava_lesson_notes"
    }
}
