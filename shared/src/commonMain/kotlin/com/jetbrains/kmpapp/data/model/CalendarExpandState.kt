package com.jetbrains.kmpapp.data.model

/**
 * Ступень раскрытия ленты календаря на экране расписания (issue #3).
 *
 * Порядок задан именно так — свайп вниз по календарику поднимается по
 * списку, свайп вверх опускается. MONTH — не вёрстка в ленте, а открытие
 * существующего месячного оверлея ([com.jetbrains.kmpapp.screens.components.MonthPickerOverlay]):
 * он уже показывает точки пар по всему месяцу, дублировать его внутри ленты
 * незачем.
 */
enum class CalendarExpandState {
    /** Одна неделя — обычный вид ленты. */
    WEEK,

    /** Две недели: чётная и нечётная, одна под другой. */
    TWO_WEEKS,

    /** Месяц: лента раскрывается в месячный оверлей. */
    MONTH;

    val isExpanded: Boolean get() = this != WEEK

    fun onSwipeDown(): CalendarExpandState = when (this) {
        WEEK -> TWO_WEEKS
        TWO_WEEKS -> MONTH
        MONTH -> MONTH
    }

    fun onSwipeUp(): CalendarExpandState = when (this) {
        WEEK -> WEEK
        TWO_WEEKS -> WEEK
        MONTH -> TWO_WEEKS
    }

    companion object {
        /** Разбор сохранённого имени; неизвестное/отсутствующее → WEEK. */
        fun fromName(name: String?): CalendarExpandState =
            entries.firstOrNull { it.name == name } ?: WEEK
    }
}
