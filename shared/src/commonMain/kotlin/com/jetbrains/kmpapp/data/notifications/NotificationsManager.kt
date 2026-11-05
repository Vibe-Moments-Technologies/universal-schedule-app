package com.jetbrains.kmpapp.data.notifications

import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonType
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Локальные напоминания о ближайшем занятии активного семестра.
 * Бронью: все пары на 7 дней вперёд (потолок 60 из 64 запросов iOS;
 * фактически ~35) — телефон, пролежавший запертым неделю, всё равно
 * получает напоминания. Перепланируется при любом изменении семестра/настроек.
 */
object NotificationsManager {
    /** iOS-движок: UNUserNotificationCenter. Идентификаторы "lesson-<date>-<bell>". */
    interface NotificationEngine {
        /** Запросить системное разрешение (вызывается при включении тумблера). */
        fun requestAuthorization()
        /** Выдано ли разрешение прямо сейчас. Синхронно: тумблер в настройках
         *  сворачивает состояние без гонок. */
        fun isAuthorized(): Boolean
        /**
         * Запланировать уведомление с идентификатором; dateEpochMillis —
         * время показа; subtitle — мелкий подзаголовок: iOS рендерит его
         * строкой под заголовком. Пустая строка — без субтитла.
         * Android-движок параметр игнорирует.
         */
        fun schedule(
            id: String,
            title: String,
            subtitle: String,
            body: String,
            dateEpochMillis: Long
        )
        /** Снять все запланированные (в т.ч. предыдущую партию). */
        fun cancelAll()
    }

    /** Максимальный горизонт планирования. */
    const val DAYS_AHEAD = 7

    var engine: NotificationEngine? = null
        private set

    /** true только там, где платформа умеет показывать локальные уведомления. */
    val supportsNotifications: Boolean get() = engine != null

    fun setEngine(newEngine: NotificationEngine) {
        engine = newEngine
    }

    fun requestAuthorization() {
        engine?.requestAuthorization()
    }

    /**
     * Разрешение выдано. false, если движка нет (платформа не умеет) или
     * система ещё не выдала разрешение.
     */
    fun isAuthorized(): Boolean = engine?.isAuthorized() ?: false

    /**
     * Отладка: тестовое уведомление через [delayMillis] (сейчас ≈ 1.5 с).
     * Фиксированные id — повторные тесты заменяют предыдущие, партию
     * напоминаний о занятиях не трогают.
     *
     * Вид — СТРУКТУРНО как боевое напоминание (репорт владельца: «отправлял
     * не свой тип, а такой же, как для расписания, — тестить вид не ждя
     * пары»): фиктивная пара + заголовок + текст в формате
     * ScheduleRepository (предмет, время, аудитория).
     */
    fun sendTest(delayMillis: Long, minutesBefore: Int = 5) {
        val eng = engine ?: return
        requestAuthorization()

        // Фиктивная пара «сейчас + N минут»: время начала подогнано, чтобы
        // текст был правдоподобным относительно настройки напоминаний.
        val nowLocal = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val lessonStart = nowLocal.time.let { LocalTime(it.hour, it.minute) }
        val fakeLesson = Lesson(
            id = "test-lesson",
            subject = "Тестовое напоминание",
            lessonType = LessonType.PRACTICE,
            teachers = emptyList(),
            classrooms = listOf("353"),
            bellNumber = 3,
            startTime = "${lessonStart.hour.toString().padStart(2, '0')}:${lessonStart.minute.toString().padStart(2, '0')}",
            endTime = "00:00",
            date = nowLocal.date
        )
        val body = formatLessonBody(fakeLesson)

        eng.schedule(
            id = if (delayMillis <= 2_000L) "test-now" else "test-delayed",
            title = "Скоро занятие",
            subtitle = "",
            body = body,
            dateEpochMillis = Clock.System.now().toEpochMilliseconds() + delayMillis
        )
    }

    /** Текст напоминания о паре — общий формат для боевых и тестовых увед. */
    private fun formatLessonBody(lesson: Lesson): String {
        val room = if (lesson.classrooms.isNotEmpty()) {
            ", ауд. ${lesson.classrooms.joinToString(", ")}"
        } else ""
        return "${lesson.subject} в ${lesson.startTime}$room"
    }

    /**
     * Перепланировать напоминания: снять прошлую партию и забронировать все
     * занятия [lessons] на следующие [DAYS_AHEAD] дней, у которых время
     * показа (минус [minutesBefore]) ещё не наступило.
     */
    fun reschedule(lessons: List<Lesson>, minutesBefore: Int, formatter: (Lesson) -> String) {
        val eng = engine ?: return
        eng.cancelAll()
        if (minutesBefore <= 0) return

        val nowLocal = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val horizonDate = nowLocal.date.plus(DatePeriod(days = DAYS_AHEAD))

        var scheduled = 0
        lessons.asSequence()
            .filter { it.date >= nowLocal.date && it.date <= horizonDate }
            .sortedWith(compareBy({ it.date }, { it.bellNumber }))
            .forEach { lesson ->
                if (scheduled >= 60) return@forEach // ponytail: потолок 64 iOS-запросов
                val fireAt = lessonStartMinus(lesson, minutesBefore) ?: return@forEach
                if (fireAt <= nowLocal) return@forEach // время показа уже прошло
                eng.schedule(
                    id = "lesson-${lesson.date}-${lesson.bellNumber}",
                    title = "Скоро занятие",
                    // Субтитл не задействован (репорт владельца krasava):
                    // iOS рисует его строкой ПОД заголовком, а не в углу;
                    // относительное время система и так показывает сама.
                    subtitle = "",
                    body = formatter(lesson),
                    dateEpochMillis = fireAt.toInstant(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds()
                )
                scheduled++
            }
    }

    /** Время показа уведомления = начало пары минус [minusMinutes]. */
    private fun lessonStartMinus(lesson: Lesson, minusMinutes: Int): LocalDateTime? {
        val parts = lesson.startTime.split(':')
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val total = hour * 60 + minute - minusMinutes
        if (total < 0) return null // уведомление должно было уйти до полуночи
        return LocalDateTime(lesson.date, LocalTime(total / 60, total % 60))
    }
}
