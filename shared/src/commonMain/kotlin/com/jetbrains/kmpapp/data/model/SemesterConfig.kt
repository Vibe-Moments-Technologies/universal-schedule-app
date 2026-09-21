package com.jetbrains.kmpapp.data.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * Универсальный локальный формат расписания (universal-schedule v1).
 *
 * Семестр = заголовок (вуз, группа, курс, семестр, дата начала, число недель,
 * звонки) + список повторяющихся занятий [ScheduleEntry]. Конкретные даты
 * генерируются из недельного шаблона — формат не привязан ни к какому вузу
 * и не требует сервера: студент сам собирает семестр (хоть из PDF-таблички)
 * и получает календарь, прогресс пар и уведомления.
 */

/** Чётность недели для занятия. */
@Serializable
enum class WeekParity(val displayName: String) {
    ALL("Каждую неделю"),
    ODD("Нечётные недели"),
    EVEN("Чётные недели")
}

/** Одно повторяющееся занятие недельного шаблона. */
@Serializable
data class ScheduleEntry(
    val id: String,
    /** День недели по ISO: 1 = понедельник … 7 = воскресенье. */
    val dayOfWeek: Int,
    val bellNumber: Int,
    val subject: String,
    val lessonType: LessonType = LessonType.OTHER,
    val teacher: String = "",
    val classroom: String = "",
    val subgroup: String = "",
    val parity: WeekParity = WeekParity.ALL,
    /** Диапазон недель семестра; lastWeek = 0 означает «до конца семестра». */
    val firstWeek: Int = 1,
    val lastWeek: Int = 0
) {
    fun occursOnWeek(week: Int, weeksCount: Int): Boolean {
        if (week !in firstWeek..(if (lastWeek > 0) lastWeek else weeksCount)) return false
        return when (parity) {
            WeekParity.ALL -> true
            WeekParity.ODD -> week % 2 == 1
            WeekParity.EVEN -> week % 2 == 0
        }
    }
}

/** Конфигурация одного активного семестра. */
@Serializable
data class SemesterConfig(
    val university: String = "",
    val group: String = "",
    val course: Int = 1,
    /** Номер семестра (1–8). */
    val semesterNumber: Int = 1,
    /** Подпись семестра, например «Осень 2026». */
    val semesterTitle: String = "",
    /** Понедельник первой недели. */
    val startDate: LocalDate,
    val weeksCount: Int = 17,
    val bells: List<LessonBells> = defaultBells,
    val entries: List<ScheduleEntry> = emptyList()
) {
    companion object {
        /** Единый id «цели» локального расписания (заметки к парам и т.п.). */
        const val TARGET_ID = 1
    }

    val endDate: LocalDate get() = startDate.plus(DatePeriod(days = weeksCount * 7 - 1))

    val displayTitle: String
        get() = group.ifBlank { "Расписание" }

    val fullTitle: String
        get() = listOf(university, group).filter { it.isNotBlank() }.joinToString(" · ")

    /** Текст ошибки валидации или null — вуз, группа, курс и семестр обязательны. */
    fun validate(): String? = when {
        university.isBlank() -> "Укажите учебное заведение"
        group.isBlank() -> "Укажите группу"
        course < 1 -> "Укажите курс"
        semesterNumber < 1 -> "Укажите семестр"
        weeksCount < 1 -> "Число недель должно быть больше нуля"
        else -> null
    }

    /** Развёртка недельного шаблона в конкретные занятия по датам. */
    fun generateLessons(): List<Lesson> {
        val out = mutableListOf<Lesson>()
        for (week in 1..weeksCount) {
            val monday = startDate.plus(DatePeriod(days = (week - 1) * 7))
            for (entry in entries) {
                if (!entry.occursOnWeek(week, weeksCount)) continue
                val bell = bells.firstOrNull { it.number == entry.bellNumber } ?: continue
                val date = monday.plus(DatePeriod(days = entry.dayOfWeek - 1))
                out += Lesson(
                    id = "${entry.id}_w$week",
                    subject = entry.subject,
                    lessonType = entry.lessonType,
                    teachers = listOf(entry.teacher).filter { it.isNotBlank() },
                    classrooms = listOf(entry.classroom).filter { it.isNotBlank() },
                    bellNumber = entry.bellNumber,
                    startTime = bell.startTime,
                    endTime = bell.endTime,
                    date = date,
                    groups = listOf(entry.subgroup).filter { it.isNotBlank() }
                )
            }
        }
        return out.sortedWith(compareBy({ it.date }, { it.bellNumber }))
    }

    /** Маркеры недель для нумерации/чётности в календаре. */
    fun weekMarkers(): List<WeekMarker> =
        (1..weeksCount).map { week ->
            WeekMarker(week, startDate.plus(DatePeriod(days = (week - 1) * 7)))
        }
}

/** Обёрка экспорта/импорта: формат + версия + сам семестр. */
@Serializable
data class ScheduleExport(
    val format: String = FORMAT,
    val formatVersion: Int = VERSION,
    val semester: SemesterConfig
) {
    companion object {
        const val FORMAT = "universal-schedule"
        const val VERSION = 1
    }
}
