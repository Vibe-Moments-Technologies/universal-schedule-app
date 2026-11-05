package com.jetbrains.kmpapp.data.widget

import com.jetbrains.kmpapp.data.ScheduleRepository
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.SemesterConfig
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Контракт данных для виджетов: компактный JSON со всеми парами активного
 * семестра на неделю вперёд. Приложение пишет его в общий контейнер
 * (iOS — App Group), виджет только читает и никогда не ходит в сеть.
 *
 * Универсальное приложение: расписание одно (локальный семестр), поэтому и
 * цель в снапшоте одна — [SemesterConfig.TARGET_ID]. Структура с массивом
 * targets сохранена дословно из контракта krasava, чтобы iOS-виджет
 * (Swift-зеркало движка) и Android Glance читали одинаковый JSON без
 * ветвлений: количество целей — деталь этого приложения, а не формата.
 */

@Serializable
data class WidgetLesson(
    /**
     * Стабильный идентификатор пары. Нужен виджету, чтобы различать
     * две одинаковые пары подряд и для будущих deep links.
     */
    val id: String,
    val date: String,   // yyyy-MM-dd
    val bell: Int,
    val start: String,  // "09:00" (локальное время)
    val end: String,
    val subject: String,
    val type: String,   // короткий бейдж: ЛК/ПР/ЛАБ/ДР/ДОП
    val room: String,
    val teacher: String,
    /** Учебные группы, участвующие в паре: индикатор «2 группы». */
    val groups: List<String> = emptyList()
)

/** Одно расписание: у нас — единственный локальный семестр. */
@Serializable
data class WidgetTarget(
    val id: Int,
    val title: String,
    /** Человекочитаемый тип для списка выбора: «Семестр». */
    val typeName: String,
    val lessons: List<WidgetLesson> = emptyList()
)

@Serializable
data class WidgetPayload(
    val generatedAt: Long,
    /** Семестр по умолчанию: один и тот же для всех размеров виджета. */
    val defaultTargetId: Int? = null,
    val targets: List<WidgetTarget> = emptyList()
)

object WidgetSnapshotBuilder {
    /** На сколько дней вперёд пишем пары. Неделя — с запасом на «Только сегодня». */
    const val DAYS_AHEAD = 7

    /** null — семестра нет, писать нечего. */
    fun build(
        config: SemesterConfig?,
        lessons: List<Lesson>
    ): String? {
        if (config == null) return null
        val today = kotlin.time.Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val lastDay = today + DatePeriod(days = DAYS_AHEAD - 1)

        val payload = WidgetPayload(
            generatedAt = kotlin.time.Clock.System.now().toEpochMilliseconds(),
            defaultTargetId = SemesterConfig.TARGET_ID,
            targets = listOf(
                WidgetTarget(
                    id = SemesterConfig.TARGET_ID,
                    title = "${config.group} · ${config.semesterNumber} сем.",
                    typeName = "Семестр",
                    lessons = lessons
                        .filter { it.date >= today && it.date <= lastDay }
                        .sortedWith(compareBy({ it.date }, { it.bellNumber }, { it.startTime }))
                        .map { it.toWidgetLesson() }
                )
            )
        )
        return Json.encodeToString(payload)
    }

    private fun Lesson.toWidgetLesson() = WidgetLesson(
        id = id,
        date = date.toString(),
        bell = bellNumber,
        start = startTime,
        end = endTime,
        subject = subject,
        type = lessonType.shortName,
        room = classrooms.joinToString(", "),
        teacher = teachers.joinToString(", "),
        groups = groups
    )
}

/**
 * Точка входа для Swift-стороны: `WidgetBridge.shared.payloadJson()`.
 * Ссылка на репозиторий ставится один раз из общего `App()` — платформенный
 * код не лезет внутрь Koin.
 */
object WidgetBridge {
    private var repository: ScheduleRepository? = null

    fun attach(repository: ScheduleRepository) {
        this.repository = repository
    }

    fun payloadJson(): String? {
        val repo = repository ?: return null
        return try {
            WidgetSnapshotBuilder.build(
                config = repo.semester.value,
                lessons = repo.currentLessons.value
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun themeJson(systemDark: Boolean): String? {
        val repo = repository ?: return null
        return try {
            WidgetThemeCodec.encode(
                WidgetThemeResolver.resolve(
                    themeMode = repo.themeMode.value,
                    overlay = repo.themeOverlay.value,
                    systemDark = systemDark,
                    halloweenActive = repo.isHalloweenActive.value,
                    // Вариант палитры Киберпанка пробрасывается в виджет:
                    // «Старая» маджента должна жить и вне приложения.
                    cyberpunkVariant = repo.cyberpunkVariant.value
                )
            )
        } catch (_: Throwable) {
            null
        }
    }
}
