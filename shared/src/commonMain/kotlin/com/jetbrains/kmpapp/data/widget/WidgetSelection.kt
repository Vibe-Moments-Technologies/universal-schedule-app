package com.jetbrains.kmpapp.data.widget

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Логика виджета расписания, общая для Android (Glance) и тестов.
 *
 * iOS-виджет использует зеркальный Swift-движок
 * (`iosApp/WidgetKitLogic/Sources/WidgetKitLogic/ScheduleSelectionEngine.swift`).
 * Этот файл повторяет его правила из ТЗ виджетов: активная пара, статусы,
 * следующие пары дня и ближайшие учебные дни.
 *
 * Время везде считается в зоне расписания (Москва), а не устройства.
 */
fun scheduleTimeZone(): TimeZone =
    runCatching { TimeZone.of("Europe/Moscow") }.getOrDefault(TimeZone.currentSystemDefault())

/** Причина пустого состояния (ТЗ п. 10.4: «нет данных» ≠ «нет занятий»). */
enum class WidgetEmptyReason {
    /** Приложение ещё не записало снапшот. */
    NO_DATA,

    /** Снапшот есть, но не читается. */
    BAD_DATA,

    /** Расписание не выбрано. */
    NO_TARGET,

    /** В загруженном расписании занятий нет. */
    NO_LESSONS
}

/** Все занятия одного учебного дня для секции Large/Extra Large. */
data class WidgetDay(
    /** Подпись «ПТ, 23 ОКТ». */
    val label: String,
    /** Дата «yyyy-MM-dd». */
    val date: String,
    val lessons: List<WidgetLesson>
)

/** Готовое к отображению состояние — одинаковое для всех размеров. */
data class WidgetState(
    val generatedAtMillis: Long,
    /** Возраст снапшота в часах, если данные устарели; иначе null. */
    val staleHours: Int?,
    val targetTitle: String?,
    /** Активная пара: текущая → ближайшая предстоящая → первая следующего дня. */
    val primary: WidgetLesson?,
    /** «СЕЙЧАС» / «СКОРО» / «ДАЛЕЕ». */
    val statusLabel: String?,
    val soonMinutes: Int?,
    val isCurrent: Boolean,
    /** Прогресс идущей пары 0..1; null, если пара не идёт. */
    val progress: Float?,
    val isToday: Boolean,
    /** «СЕГОДНЯ · СРЕДА» или «ПТ, 23 ОКТ». */
    val dayHeader: String?,
    /** Остальные пары того же учебного дня, кроме активной. */
    val upcoming: List<WidgetLesson>,
    /** До двух ближайших учебных дней после дня активной пары. */
    val nextDays: List<WidgetDay>,
    val emptyReason: WidgetEmptyReason?
) {
    val isEmpty: Boolean get() = primary == null

    /** Метка с обратным отсчётом там, где есть место. */
    val countdownLabel: String?
        get() {
            val minutes = soonMinutes ?: return statusLabel
            return if (minutes > 0) "ЧЕРЕЗ $minutes МИН" else statusLabel
        }

    /** Короткая метка для компактных размеров. */
    val compactStatusLabel: String
        get() = if (isToday || dayHeader == null) {
            statusLabel ?: dayHeader ?: "РАСПИСАНИЕ"
        } else {
            dayHeader
        }

    companion object {
        fun empty(
            reason: WidgetEmptyReason,
            generatedAtMillis: Long = Clock.System.now().toEpochMilliseconds(),
            targetTitle: String? = null
        ) = WidgetState(
            generatedAtMillis = generatedAtMillis,
            staleHours = null,
            targetTitle = targetTitle,
            primary = null,
            statusLabel = null,
            soonMinutes = null,
            isCurrent = false,
            progress = null,
            isToday = false,
            dayHeader = null,
            upcoming = emptyList(),
            nextDays = emptyList(),
            emptyReason = reason
        )
    }
}

/** Форматирование дат и подписей виджета (русская локаль, зона расписания). */
object WidgetLabels {
    private val weekdaysShort =
        listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
    private val weekdaysFull =
        listOf("ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА", "ВОСКРЕСЕНЬЕ")
    private val monthsShort =
        listOf("ЯНВ", "ФЕВ", "МАР", "АПР", "МАЙ", "ИЮН", "ИЮЛ", "АВГ", "СЕН", "ОКТ", "НОЯ", "ДЕК")

    /** `ПТ, 23 ОКТ` — единый формат будущего дня. */
    fun compactDate(date: LocalDate): String =
        "${weekdaysShort[date.dayOfWeek.ordinal]}, ${date.dayOfMonth} ${monthsShort[date.month.ordinal]}"

    /** `СЕГОДНЯ · СРЕДА` — заголовок сегодняшнего дня. */
    fun todayHeader(date: LocalDate): String =
        "СЕГОДНЯ · ${weekdaysFull[date.dayOfWeek.ordinal]}"

    /** `14:20–15:50` с типографским тире. */
    fun timeRange(lesson: WidgetLesson): String = "${lesson.start}–${lesson.end}"

    /**
     * Название дисциплины с учётом сокращения.
     *
     * Правило — дословно из кода экрана расписания
     * (`abbreviateSubjectName`): для названий из 3+ слов от каждого слова
     * берётся первая буква — слова длиннее двух букв дают заглавный
     * инициал, слова в 1–2 буквы остаются строчными («Базы данных и
     * управление ими» → «БДиУИ»: «и» строчная, «ими» заглавная).
     * Виджеты (Android Glance и iOS Swift-зеркало) наследуют правило,
     * а не задают своё.
     */
    fun subjectDisplay(lesson: WidgetLesson, abbreviated: Boolean): String =
        if (!abbreviated) lesson.subject else abbreviateSubject(lesson.subject)

    /** Первые буквы слов для названий из 3+ слов; иначе — как есть. */
    fun abbreviateSubject(subject: String): String {
        val words = subject.split(' ', '-').filter { it.isNotBlank() }
        if (words.size <= 2) return subject
        return words.joinToString("") { word ->
            if (word.length > 2) word.first().uppercaseChar().toString()
            else word.first().toString()
        }
    }

    /** Человекочитаемое место занятия. */
    fun roomDisplay(lesson: WidgetLesson): String {
        val trimmed = lesson.room.trim()
        if (trimmed.isEmpty()) return "Аудитория уточняется"
        val lower = trimmed.lowercase()
        if (lower.contains("сдо") || lower.contains("дистант") || lower.contains("онлайн")) {
            return "Онлайн"
        }
        return trimmed
    }

    /** Имя преподавателя; null — не резервировать пустую строку. */
    fun teacherDisplay(lesson: WidgetLesson): String? =
        lesson.teacher.trim().ifEmpty { null }

    /** Количество уникальных групп (ТЗ п. 5.5). */
    fun groupCount(lesson: WidgetLesson): Int = lesson.groups.toSet().size

    /** «2 группы» / «3 группы» / «5 групп». */
    fun groupsLabel(count: Int): String = when {
        count % 100 in 11..14 -> "$count групп"
        count % 10 == 1 -> "$count группа"
        count % 10 in 2..4 -> "$count группы"
        else -> "$count групп"
    }

    /** Разбивает «341 (С-20)» на номер и корпус для двухцветной подачи. */
    fun splitClassroom(display: String): Pair<String, String?> {
        val open = display.indexOf('(')
        if (open <= 0) return display to null
        val close = display.lastIndexOf(')')
        if (close <= open) return display to null
        val number = display.substring(0, open).trim()
        val building = display.substring(open + 1, close).trim()
        if (number.isEmpty() || building.isEmpty()) return display to null
        return number to building
    }
}

/** Отбор активной пары и подготовка состояния (ТЗ п. 1.1, 11). */
class WidgetSelectionEngine(
    private val timeZone: TimeZone = scheduleTimeZone(),
    private val soonThresholdMinutes: Int = 30
) {
    fun makeState(
        payload: WidgetPayload?,
        preferredTargetId: Int? = null,
        now: Instant = Clock.System.now(),
        staleAfterHours: Int = 36
    ): WidgetState {
        if (payload == null) return WidgetState.empty(WidgetEmptyReason.NO_DATA)

        val target = payload.targets.firstOrNull { it.id == preferredTargetId }
            ?: payload.targets.firstOrNull { it.id == payload.defaultTargetId }
            ?: payload.targets.firstOrNull()
            ?: return WidgetState.empty(WidgetEmptyReason.NO_TARGET, payload.generatedAt)

        val lessons = target.lessons.sortedWith(lessonOrder)
        val staleHours = staleHours(payload.generatedAt, now, staleAfterHours)

        if (lessons.isEmpty()) {
            return WidgetState.empty(WidgetEmptyReason.NO_LESSONS, payload.generatedAt, target.title)
                .copy(staleHours = staleHours)
        }

        val current = lessons.firstOrNull { isOngoing(it, now) }
        val upcoming = lessons.firstOrNull { lesson ->
            val start = startInstant(lesson) ?: return@firstOrNull false
            start > now
        }
        val primary = current ?: upcoming
            ?: return WidgetState.empty(WidgetEmptyReason.NO_LESSONS, payload.generatedAt, target.title)
                .copy(staleHours = staleHours)

        val primaryDay = parseDate(primary.date) ?: return WidgetState.empty(
            WidgetEmptyReason.BAD_DATA, payload.generatedAt, target.title
        )

        val today = now.toLocalDateTime(timeZone).date
        val isToday = primaryDay == today
        val status = status(primary, current != null, now)
        val sameDay = lessons.filter { parseDate(it.date) == primaryDay }
        val upcomingSameDay = sameDay.filter { lesson ->
            lesson.id != primary.id && (startInstant(lesson)?.let { it > now } ?: false)
        }

        return WidgetState(
            generatedAtMillis = payload.generatedAt,
            staleHours = staleHours,
            targetTitle = target.title,
            primary = primary,
            statusLabel = status.label,
            soonMinutes = status.soonMinutes,
            isCurrent = current != null,
            progress = progress(primary, now),
            isToday = isToday,
            dayHeader = if (isToday) WidgetLabels.todayHeader(primaryDay)
            else WidgetLabels.compactDate(primaryDay),
            upcoming = upcomingSameDay,
            nextDays = nextStudyDays(after = primaryDay, lessons = lessons, limit = 2),
            emptyReason = null
        )
    }

    private data class Status(val label: String, val soonMinutes: Int?)

    private fun status(primary: WidgetLesson, ongoing: Boolean, now: Instant): Status {
        if (ongoing) return Status("СЕЙЧАС", null)
        val start = startInstant(primary) ?: return Status("ДАЛЕЕ", null)
        val minutes = ((start.toEpochMilliseconds() - now.toEpochMilliseconds()) / 60_000L).toInt()
        return if (minutes in 0..soonThresholdMinutes) {
            Status("СКОРО", minutes)
        } else {
            Status("ДАЛЕЕ", null)
        }
    }

    private fun nextStudyDays(
        after: LocalDate,
        lessons: List<WidgetLesson>,
        limit: Int
    ): List<WidgetDay> {
        val dates = lessons.mapNotNull { parseDate(it.date) }
            .filter { it > after }
            .distinct()
            .sorted()
            .take(limit)
        return dates.map { date ->
            WidgetDay(
                label = WidgetLabels.compactDate(date),
                date = date.toString(),
                lessons = lessons.filter { parseDate(it.date) == date }
            )
        }
    }

    private fun progress(lesson: WidgetLesson, now: Instant): Float? {
        val start = startInstant(lesson) ?: return null
        val end = endInstant(lesson) ?: return null
        if (now < start || now >= end) return null
        val total = (end.toEpochMilliseconds() - start.toEpochMilliseconds()).toFloat()
        if (total <= 0f) return null
        val passed = (now.toEpochMilliseconds() - start.toEpochMilliseconds()).toFloat()
        return (passed / total).coerceIn(0f, 1f)
    }

    private fun staleHours(generatedAt: Long, now: Instant, staleAfterHours: Int): Int? {
        val hours = ((now.toEpochMilliseconds() - generatedAt) / 3_600_000L).toInt()
        return if (hours >= staleAfterHours) hours else null
    }

    private fun isOngoing(lesson: WidgetLesson, now: Instant): Boolean {
        val start = startInstant(lesson) ?: return false
        val end = endInstant(lesson) ?: return false
        return now >= start && now < end
    }

    private fun startInstant(lesson: WidgetLesson): Instant? {
        val date = parseDate(lesson.date) ?: return null
        val time = parseTime(lesson.start) ?: return null
        return runCatching { LocalDateTime(date, time).toInstant(timeZone) }.getOrNull()
    }

    private fun endInstant(lesson: WidgetLesson): Instant? {
        val date = parseDate(lesson.date) ?: return null
        val start = parseTime(lesson.start) ?: return null
        val end = parseTime(lesson.end) ?: return null
        val endDate = if (end < start) date.plus(DatePeriod(days = 1)) else date
        return runCatching { LocalDateTime(endDate, end).toInstant(timeZone) }.getOrNull()
    }

    private fun parseDate(text: String): LocalDate? =
        runCatching { LocalDate.parse(text) }.getOrNull()

    private fun parseTime(text: String): LocalTime? =
        runCatching { LocalTime.parse(text) }.getOrNull()

    private val lessonOrder = compareBy<WidgetLesson>(
        { parseDate(it.date) ?: LocalDate(1970, 1, 1) },
        { parseTime(it.start) ?: LocalTime(0, 0) },
        { it.bell },
        { it.id }
    )
}

/** Чтение/запись JSON-снапшота виджета (контракт общий с iOS). */
object WidgetPayloadCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun decode(raw: String?): WidgetPayload? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<WidgetPayload>(raw) }.getOrNull()
    }

    fun encode(payload: WidgetPayload): String = json.encodeToString(payload)
}
