package com.jetbrains.kmpapp.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetbrains.kmpapp.data.ScheduleRepository
import com.jetbrains.kmpapp.data.analytics.AnalyticsEvents
import com.jetbrains.kmpapp.data.analytics.AppAnalytics
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.ScheduleSlot
import com.jetbrains.kmpapp.data.model.SemesterConfig
import com.jetbrains.kmpapp.data.model.defaultBells
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class ScheduleViewModel(
    private val repository: ScheduleRepository
) : ViewModel() {

    /** Активный семестр; null — расписание не собрано (пустое состояние). */
    val semester: StateFlow<SemesterConfig?> = repository.semester
    val selectedTargetId: Int get() = SemesterConfig.TARGET_ID
    val showLessonProgress: StateFlow<Boolean> = repository.showLessonProgress
    val showEmptyLessonProgress: StateFlow<Boolean> = repository.showEmptyLessonProgress
    val showBreakProgress: StateFlow<Boolean> = repository.showBreakProgress
    val calendarCollapsed: StateFlow<Boolean> = repository.calendarCollapsed
    val autoScrollToCurrentLesson: StateFlow<Boolean> = repository.autoScrollToCurrentLesson
    val showAbbreviatedNames: StateFlow<Boolean> = repository.showAbbreviatedNames

    private var lastAutoScrolledDate: LocalDate? = null
    private var lastAutoScrolledTargetId: Int? = null

    /**
     * Позиция скролла дня по дате: живёт в ViewModel, а не в композиции.
     * Страница пейджера и весь экран расписания пересоздаются (в т.ч. когда
     * сверху открывается карточка пары), и позиция дня «прыгала» к первой
     * паре. Здесь она переживает любые пересборки.
     */
    private val dayScrollPositions = mutableMapOf<LocalDate, Int>()

    fun scrollPositionFor(date: LocalDate): Int = dayScrollPositions[date] ?: 0

    fun saveScrollPosition(date: LocalDate, index: Int) {
        dayScrollPositions[date] = index
    }

    fun canAutoScroll(date: LocalDate, targetId: Int?): Boolean {
        // Автоскролл только один раз на дату: повторные пересборки не должны
        // заново подбрасывать день к текущей паре.
        return lastAutoScrolledDate != date || lastAutoScrolledTargetId != targetId
    }

    fun markAutoScrolled(date: LocalDate, targetId: Int?) {
        lastAutoScrolledDate = date
        lastAutoScrolledTargetId = targetId
    }

    fun resetAutoScroll() {
        lastAutoScrolledDate = null
        lastAutoScrolledTargetId = null
    }

    private val _currentMinutes = MutableStateFlow(DateUtils.currentTimeMinutes())
    val currentMinutes: StateFlow<Int> = _currentMinutes.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                // Power-saving tick: pause or sleep longer when in background
                val lowPower = repository.isLowPowerMode.value
                _currentMinutes.value = DateUtils.currentTimeMinutes()
                val sleepTime = if (lowPower) 60_000L else 30_000L
                kotlinx.coroutines.delay(sleepTime)
            }
        }
    }

    private val _selectedDate = MutableStateFlow(DateUtils.today())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _selectedLessonForDetail = MutableStateFlow<Lesson?>(null)
    val selectedLessonForDetail: StateFlow<Lesson?> = _selectedLessonForDetail.asStateFlow()

    val datesWithLessons: StateFlow<Set<LocalDate>> = repository.currentLessons
        .combine(MutableStateFlow(Unit)) { lessons, _ ->
            lessons.map { it.date }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val dayLessonSummaries: StateFlow<Map<LocalDate, DayLessonSummary>> = repository.currentLessons
        .combine(MutableStateFlow(Unit)) { lessons, _ ->
            lessons.groupBy { it.date }.mapValues { (_, dayLessons) ->
                val orderedTypes = dayLessons.groupBy { it.bellNumber }
                    .entries.sortedBy { it.key }
                    .map { (_, slotLessons) -> slotLessons.first().lessonType }
                DayLessonSummary(lessonTypes = orderedTypes)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val daySlots: StateFlow<List<ScheduleSlot>> = combine(
        repository.currentLessons,
        _selectedDate,
        repository.showEmptyLessons
    ) { lessons, date, showEmpty -> slotsForDate(lessons, date, showEmpty) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentLessons: StateFlow<List<Lesson>> = repository.currentLessons
    val showEmptyLessons: StateFlow<Boolean> = repository.showEmptyLessons

    /** Звонки активного семестра (или дефолт, если семестр не собран). */
    private val bells get() = semester.value?.bells ?: defaultBells

    fun slotsForDate(
        date: LocalDate,
        lessons: List<Lesson> = currentLessons.value,
        showEmpty: Boolean = showEmptyLessons.value
    ): List<ScheduleSlot> = slotsForDate(lessons, date, showEmpty)

    private fun slotsForDate(
        lessons: List<Lesson>,
        date: LocalDate,
        showEmpty: Boolean
    ): List<ScheduleSlot> {
        val bells = bells
        val forDay = lessons.filter { it.date == date }
        if (forDay.isEmpty()) {
            return if (showEmpty && date.dayOfWeek != DayOfWeek.SUNDAY) {
                bells.map { bell ->
                    ScheduleSlot.Empty(bell.number, bell.startTime, bell.endTime)
                }
            } else emptyList()
        }

        val bellMap = forDay.groupBy { it.bellNumber }
        if (!showEmpty) {
            return bellMap.entries.sortedBy { it.key }.map { (bell, items) ->
                val first = items.first()
                ScheduleSlot.Active(bell, first.startTime, first.endTime, items)
            }
        }

        val result = mutableListOf<ScheduleSlot>()
        val upperBell = maxOf(forDay.maxOfOrNull { it.bellNumber } ?: 0, bells.maxOfOrNull { it.number } ?: 7)
        for (bell in 1..upperBell) {
            val items = bellMap[bell]
            if (!items.isNullOrEmpty()) {
                val first = items.first()
                result += ScheduleSlot.Active(bell, first.startTime, first.endTime, items)
            } else {
                val bellInfo = bells.firstOrNull { it.number == bell }
                result += ScheduleSlot.Empty(bell, bellInfo?.startTime ?: "—", bellInfo?.endTime ?: "—")
            }
        }
        return result
    }

    fun selectDate(date: LocalDate) {
        if (_selectedDate.value != date) {
            _selectedDate.value = date
            resetAutoScroll()
        }
    }

    fun setCalendarCollapsed(collapsed: Boolean) {
        repository.setCalendarCollapsed(collapsed)
    }

    fun nextDay() {
        selectDate(_selectedDate.value.plus(DatePeriod(days = 1)))
    }

    fun previousDay() {
        selectDate(_selectedDate.value.minus(DatePeriod(days = 1)))
    }

    fun selectLessonForDetail(lesson: Lesson?) {
        _selectedLessonForDetail.value = lesson
        if (lesson != null) {
            AppAnalytics.logEvent(AnalyticsEvents.FEATURE_LESSON_DETAIL, mapOf("screen" to "lesson_detail"))
        }
    }
}

data class DayLessonSummary(
    val lessonTypes: List<com.jetbrains.kmpapp.data.model.LessonType> = emptyList()
) {
    val hasLessons: Boolean get() = lessonTypes.isNotEmpty()
}
