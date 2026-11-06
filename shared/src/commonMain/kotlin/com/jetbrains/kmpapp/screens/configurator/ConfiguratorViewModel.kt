package com.jetbrains.kmpapp.screens.configurator

import androidx.lifecycle.ViewModel
import com.jetbrains.kmpapp.data.ScheduleRepository
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.LessonBells
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.ScheduleEntry
import com.jetbrains.kmpapp.data.model.SemesterConfig
import com.jetbrains.kmpapp.data.model.WeekParity
import com.jetbrains.kmpapp.data.model.defaultBells
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Состояние редактирования конфигуратора: черновик SemesterConfig + ошибка валидации. */
data class ConfiguratorState(
    val config: SemesterConfig,
    val validationError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val saved: Boolean = false
)

class ConfiguratorViewModel(private val repository: ScheduleRepository) : ViewModel() {

    private val _state = MutableStateFlow(ConfiguratorState(config = defaultSemesterConfig()))
    val state: StateFlow<ConfiguratorState> = _state.asStateFlow()

    // ── Поля заголовка семестра ──────────────────────────────────────

    fun setUniversity(value: String) {
        val current = _state.value
        _state.value = current.copy(config = current.config.copy(university = value))
    }

    fun setGroup(value: String) {
        val current = _state.value
        _state.value = current.copy(config = current.config.copy(group = value))
    }

    fun setCourse(value: Int) {
        val current = _state.value
        _state.value = current.copy(config = current.config.copy(course = value.coerceIn(1, 6)))
    }

    fun setSemesterNumber(value: Int) {
        val current = _state.value
        _state.value = current.copy(config = current.config.copy(semesterNumber = value.coerceIn(1, 8)))
    }

    fun setSemesterTitle(value: String) {
        val current = _state.value
        _state.value = current.copy(config = current.config.copy(semesterTitle = value))
    }

    fun setStartDate(value: LocalDate) {
        val current = _state.value
        _state.value = current.copy(config = current.config.copy(startDate = value))
    }

    fun setWeeksCount(value: Int) {
        val current = _state.value
        _state.value = current.copy(config = current.config.copy(weeksCount = value.coerceIn(1, 52)))
    }

    // ── Звонки ───────────────────────────────────────────────────────

    fun addBell(number: Int = -1, startTime: String = "", endTime: String = "") {
        val current = _state.value
        val nextNumber = if (number == -1) nextBellNumber(current.config.bells) else number
        val bells = current.config.bells + LessonBells(nextNumber, startTime, endTime)
        _state.value = current.copy(config = current.config.copy(bells = bells.sortedBy { it.number }))
    }

    fun updateBell(index: Int, number: Int, startTime: String, endTime: String) {
        val current = _state.value
        val bells = current.config.bells.toMutableList()
        bells[index] = LessonBells(number, startTime, endTime)
        _state.value = current.copy(config = current.config.copy(bells = bells.sortedBy { it.number }))
    }

    fun removeBell(index: Int) {
        val current = _state.value
        val bells = current.config.bells.toMutableList()
        bells.removeAt(index)
        _state.value = current.copy(config = current.config.copy(bells = bells))
    }

    private fun nextBellNumber(currentBells: List<LessonBells>): Int =
        (currentBells.maxOfOrNull { it.number } ?: 0) + 1

    // ── Записи недели (entries) ─────────────────────────────────────

    fun addEntry(dayOfWeek: Int = DateUtils.today().dayOfWeek.ordinal + 1) {
        val current = _state.value
        val entries = current.config.entries +
            ScheduleEntry(
                id = nextEntryId(current.config.entries),
                dayOfWeek = dayOfWeek.coerceIn(1, 7),
                bellNumber = 1,
                subject = "",
                lessonType = LessonType.OTHER
            )
        _state.value = current.copy(config = current.config.copy(entries = entries))
    }

    fun updateEntry(
        index: Int,
        id: String,
        dayOfWeek: Int,
        bellNumber: Int,
        subject: String,
        lessonType: LessonType,
        teacher: String,
        classroom: String,
        subgroup: String,
        parity: WeekParity,
        firstWeek: Int,
        lastWeek: Int
    ) {
        val current = _state.value
        val entries = current.config.entries.toMutableList()
        entries[index] = ScheduleEntry(
            id = id,
            dayOfWeek = dayOfWeek.coerceIn(1, 7),
            bellNumber = bellNumber.coerceIn(1, 20),
            subject = subject,
            lessonType = lessonType,
            teacher = teacher,
            classroom = classroom,
            subgroup = subgroup,
            parity = parity,
            firstWeek = firstWeek.coerceIn(1, 52),
            lastWeek = lastWeek.coerceIn(0, 52)
        )
        _state.value = current.copy(config = current.config.copy(entries = entries))
    }

    fun removeEntry(index: Int) {
        val current = _state.value
        val entries = current.config.entries.toMutableList()
        entries.removeAt(index)
        _state.value = current.copy(config = current.config.copy(entries = entries))
    }

    private fun nextEntryId(currentEntries: List<ScheduleEntry>): String {
        val maxNum = currentEntries
            .mapNotNull { it.id.removePrefix("e").toIntOrNull() }
            .maxOrNull() ?: 0
        return "e${maxNum + 1}"
    }

    // ── Валидация и сохранение ───────────────────────────────────────

    fun validate() {
        val current = _state.value
        val error = current.config.validate()
        _state.value = current.copy(validationError = error, saveError = null)
    }

    fun save() {
        val current = _state.value
        val error = current.config.validate()
        if (error != null) {
            _state.value = current.copy(validationError = error)
            return
        }

        _state.value = current.copy(isSaving = true, saveError = null, saved = false)

        try {
            repository.saveSemester(_state.value.config)
            _state.value = _state.value.copy(isSaving = false, saved = true, validationError = null)
        } catch (e: Exception) {
            _state.value = _state.value.copy(isSaving = false, saveError = e.message ?: "Не удалось сохранить")
        }
    }

    fun reset() {
        val current = _state.value
        _state.value = current.copy(saved = false, saveError = null)
    }

    companion object {
        /** Понедельник недели, в которую попадает 1 сентября текущего года. */
        private fun septemberMonday(): LocalDate {
            val now = DateUtils.today()
            val september1 = LocalDate(now.year, 9, 1)
            // MONDAY.ordinal == 0 в kotlinx.datetime.
            return september1.minus(DatePeriod(days = september1.dayOfWeek.ordinal))
        }

        /** Семестр по умолчанию: осень текущего года, 17 недель, стандартные звонки. */
        fun defaultSemesterConfig(): SemesterConfig = SemesterConfig(
            university = "",
            group = "",
            course = 1,
            semesterNumber = 1,
            semesterTitle = "",
            startDate = septemberMonday(),
            weeksCount = 17,
            bells = defaultBells,
            entries = emptyList()
        )
    }
}
