package com.jetbrains.kmpapp.data.model

import kotlin.concurrent.Volatile
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * Маркер недели из iCal-фида МИРЭА: универ сам публикует события
 * «N неделя» (TRANSP:TRANSPARENT, VALUE=DATE) — это источник истины
 * для нумерации недель вместо вычисления по захардкоженным датам семестра.
 */
@Serializable
data class WeekMarker(
    val number: Int,
    val monday: LocalDate
)

object SemesterWeeks {

    @Volatile
    private var markers: List<WeekMarker> = emptyList()

    fun set(list: List<WeekMarker>) {
        // Фид может начинать неделю не с понедельника («1 неделя» осени 2026 —
        // вторник 01.09), а UI спрашивает номер по понедельнику. Приводим к
        // понедельнику недели, иначе день до старта маркера улетает в фолбэк.
        markers = list
            .map { it.copy(monday = it.monday.minus(DatePeriod(days = it.monday.dayOfWeek.ordinal))) }
            .distinctBy { it.monday }
            .sortedBy { it.monday }
    }

    fun get(): List<WeekMarker> = markers

    /** Номер недели из фида или null, если дата не покрыта маркерами (→ фолбэк). */
    fun weekNumberFor(date: LocalDate): Int? {
        // ponytail: линейный скан ~18 элементов — бинарный поиск не нужен
        val marker = markers.lastOrNull {
            date >= it.monday && date < it.monday.plus(DatePeriod(days = 7))
        }
        return marker?.number
    }
}
