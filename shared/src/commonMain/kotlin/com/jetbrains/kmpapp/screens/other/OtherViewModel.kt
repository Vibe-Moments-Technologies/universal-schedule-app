package com.jetbrains.kmpapp.screens.other

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetbrains.kmpapp.data.ScheduleRepository
import com.jetbrains.kmpapp.data.TaskRepository
import com.jetbrains.kmpapp.data.DebugConfig
import com.jetbrains.kmpapp.data.model.LessonColor
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.ScheduleTarget
import com.jetbrains.kmpapp.data.model.ScheduleTargetType
import com.jetbrains.kmpapp.data.model.StorageStats
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.theme.CyberpunkVariant
import com.jetbrains.kmpapp.theme.ThemeOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

import com.jetbrains.kmpapp.data.update.AppUpdateChecker
import com.jetbrains.kmpapp.data.update.UpdateCheckResult
import com.jetbrains.kmpapp.screens.components.AppTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

enum class TargetSortOrder(val displayName: String) {
    TITLE_ASC("По названию (А → Я / 0 → 9)"),
    TITLE_DESC("По названию (Я → А / 9 → 0)"),
    RECENT("Сначала новые"),
    OLDEST("Сначала старые")
}

enum class OtherSubScreen(val depth: Int) {
    ROOT(0),
    MANAGE_SCHEDULES(1),
    SETTINGS(1),
    DATA_AND_CACHE(2),
    DOCK_SETTINGS(2),
    TASK_SETTINGS(2),
    ICON_PICKER(2),
    // Подстраницы настроек расписания
    SCHEDULE_DISPLAY(2),
    SCHEDULE_PROGRESS(2),
    SCHEDULE_CALENDAR(2),
    // Тематические темы: найденные квестами надстройки тем
    THEMED_THEMES(2),
    // Пользовательские цвета типов занятий
    LESSON_COLORS(2),
    // Параметры сервисов
    SERVICE_NOTES_SETTINGS(2),
    // Настройки игр: «Сапер» и «Тетрис»
    SAPER_SETTINGS(2),
    TETRIS_SETTINGS(2),
    RESOURCES(1),
    ABOUT(1),
    TEAM(2),
    LICENSES(2),
    DEBUG_SETTINGS(2),
    EXPERIMENTAL_SETTINGS(3),
    // Сервисы, открытые через блок «Сервисы» на главной «Другого».
    SERVICE_ROOMS(1),
    SERVICE_TASKS(1),
    SERVICE_MAP(1),
    SERVICE_NOTES(1),
    SERVICE_COMPARE(1),
    SERVICE_GAMES(1)
}

/** Родитель подстраницы для послойной навигации (null = корень). */
private val SUB_SCREEN_PARENT = mapOf(
    OtherSubScreen.MANAGE_SCHEDULES to OtherSubScreen.ROOT,
    OtherSubScreen.SETTINGS to OtherSubScreen.ROOT,
    OtherSubScreen.DATA_AND_CACHE to OtherSubScreen.SETTINGS,
    OtherSubScreen.DOCK_SETTINGS to OtherSubScreen.SETTINGS,
    OtherSubScreen.TASK_SETTINGS to OtherSubScreen.SETTINGS,
    OtherSubScreen.ICON_PICKER to OtherSubScreen.SETTINGS,
    OtherSubScreen.SCHEDULE_DISPLAY to OtherSubScreen.SETTINGS,
    OtherSubScreen.SCHEDULE_PROGRESS to OtherSubScreen.SETTINGS,
    OtherSubScreen.SCHEDULE_CALENDAR to OtherSubScreen.SETTINGS,
    OtherSubScreen.THEMED_THEMES to OtherSubScreen.SETTINGS,
    OtherSubScreen.LESSON_COLORS to OtherSubScreen.SETTINGS,
    OtherSubScreen.SERVICE_NOTES_SETTINGS to OtherSubScreen.SETTINGS,
    OtherSubScreen.SAPER_SETTINGS to OtherSubScreen.SETTINGS,
    OtherSubScreen.TETRIS_SETTINGS to OtherSubScreen.SETTINGS,
    OtherSubScreen.RESOURCES to OtherSubScreen.ROOT,
    OtherSubScreen.ABOUT to OtherSubScreen.ROOT,
    OtherSubScreen.TEAM to OtherSubScreen.ABOUT,
    OtherSubScreen.LICENSES to OtherSubScreen.ABOUT,
    OtherSubScreen.DEBUG_SETTINGS to OtherSubScreen.ABOUT,
    OtherSubScreen.EXPERIMENTAL_SETTINGS to OtherSubScreen.DEBUG_SETTINGS,
    OtherSubScreen.SERVICE_ROOMS to OtherSubScreen.ROOT,
    OtherSubScreen.SERVICE_TASKS to OtherSubScreen.ROOT,
    OtherSubScreen.SERVICE_MAP to OtherSubScreen.ROOT,
    OtherSubScreen.SERVICE_NOTES to OtherSubScreen.ROOT,
    OtherSubScreen.SERVICE_COMPARE to OtherSubScreen.ROOT,
    OtherSubScreen.SERVICE_GAMES to OtherSubScreen.ROOT
)

fun OtherSubScreen.parent(): OtherSubScreen? =
    if (this == OtherSubScreen.ROOT) null else SUB_SCREEN_PARENT[this]

/** Сервисная подстраница для вкладки дока (null — не сервис). */
fun AppTab.toServiceSubScreen(): OtherSubScreen? = when (this) {
    AppTab.FREE_ROOMS -> OtherSubScreen.SERVICE_ROOMS
    AppTab.TASKS -> OtherSubScreen.SERVICE_TASKS
    AppTab.MAP -> OtherSubScreen.SERVICE_MAP
    AppTab.NOTES -> OtherSubScreen.SERVICE_NOTES
    AppTab.COMPARE -> OtherSubScreen.SERVICE_COMPARE
    AppTab.GAMES -> OtherSubScreen.SERVICE_GAMES
    else -> null
}

/** Открыт ли в «Другом» сервис (для стрелки «назад» в доке). */
val OtherSubScreen.isServiceScreen: Boolean
    get() = this in setOf(
        OtherSubScreen.SERVICE_ROOMS,
        OtherSubScreen.SERVICE_TASKS,
        OtherSubScreen.SERVICE_MAP,
        OtherSubScreen.SERVICE_NOTES,
        OtherSubScreen.SERVICE_COMPARE,
        OtherSubScreen.SERVICE_GAMES
    )

class OtherViewModel(
    private val repository: ScheduleRepository,
    private val updateChecker: AppUpdateChecker,
    private val taskRepository: TaskRepository
) : ViewModel() {

    val savedTargets: StateFlow<List<ScheduleTarget>> = repository.savedTargets
    val selectedTarget: StateFlow<ScheduleTarget?> = repository.selectedTarget
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val showEmptyLessons: StateFlow<Boolean> = repository.showEmptyLessons
    val showLessonProgress: StateFlow<Boolean> = repository.showLessonProgress
    val showEmptyLessonProgress: StateFlow<Boolean> = repository.showEmptyLessonProgress
    val showBreakProgress: StateFlow<Boolean> = repository.showBreakProgress
    val calendarSwipeCollapse: StateFlow<Boolean> = repository.calendarSwipeCollapse
    val hideAdditionalLessons: StateFlow<Boolean> = repository.hideAdditionalLessons
    val autoScrollToCurrentLesson: StateFlow<Boolean> = repository.autoScrollToCurrentLesson
    val showAbbreviatedNames: StateFlow<Boolean> = repository.showAbbreviatedNames
    val themeMode: StateFlow<ThemeMode> = repository.themeMode
    val themeOverlay: StateFlow<ThemeOverlay> = repository.themeOverlay
    val isSakuraTheme: StateFlow<Boolean> = repository.isSakuraTheme
    val isKawaiiTheme: StateFlow<Boolean> = repository.isKawaiiTheme
    val isCyberpunkTheme: StateFlow<Boolean> = repository.isCyberpunkTheme
    val isMatrixTheme: StateFlow<Boolean> = repository.isMatrixTheme
    val isHalloweenTheme: StateFlow<Boolean> = repository.isHalloweenTheme
    /**
     * Хэллоуин действует прямо сейчас: сезон без отказа или включён вручную.
     * Пока это true, строка есть в «Тематических темах» — иначе сезонную тему
     * нельзя было бы выключить. Как только правило её гасит, строка исчезает.
     */
    val isHalloweenActive: StateFlow<Boolean> = repository.isHalloweenActive
    /** Строка Хэллоуина в списке тем: сезон идёт или тема включена вручную. */
    val isHalloweenRowVisible: StateFlow<Boolean> = repository.isHalloweenRowVisible
    /** Темы, найденные квестами: только они плюс Сакура видны в списке. */
    val unlockedThemes: StateFlow<Set<ThemeOverlay>> = repository.unlockedThemes
    /** Пользовательские цвета типов занятий (только переопределённые). */
    val lessonTypeColors: StateFlow<Map<LessonType, LessonColor>> = repository.lessonTypeColors

    /**
     * Типы занятий, реально присутствующие в ДОБАВЛЕННЫХ расписаниях.
     * Больше не используется меню цветов (запрос тестеров: показывать все
     * типы сразу) — оставлено как источник истины «что есть в расписаниях».
     */
    val presentLessonTypes: StateFlow<List<LessonType>> = repository.cachedLessons
        .map { cache ->
            val present = cache.values
                .asSequence()
                .flatMap { lessons -> lessons.asSequence().map { it.lessonType } }
                .toSet()
            if (present.isEmpty()) LessonType.entries.toList()
            else LessonType.entries.filter { it in present }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LessonType.entries.toList())
    /** Вариант палитры «Киберпанка»: NEON (новая) или CLASSIC (старая). */
    val cyberpunkVariant: StateFlow<CyberpunkVariant> = repository.cyberpunkVariant
    val cheatsAgreed: StateFlow<Boolean?> = repository.cheatsAgreed
    val cheatsBlocked: StateFlow<Boolean> = repository.cheatsBlocked
    val diagnosticsEnabled: StateFlow<Boolean> = repository.diagnosticsEnabled

    // Скролл подэкранов «Другое» живёт в VM: LayeredNavHost пересоздаёт экран
    // в другом слое при переходе родитель↔дочка, remember-состояния гибнут.
    // Один общий реестр по ключу вместо простыни отдельных полей.
    private val scrollStates = mutableMapOf<String, androidx.compose.foundation.ScrollState>()
    fun scrollState(key: String): androidx.compose.foundation.ScrollState =
        scrollStates.getOrPut(key) { androidx.compose.foundation.ScrollState(0) }
    val dockTabs: StateFlow<List<AppTab>> = repository.dockTabs
    val dockHidden: StateFlow<Boolean> = repository.dockHidden
    val appIcon: StateFlow<String> = repository.appIcon
    val notificationsEnabled: StateFlow<Boolean> = repository.notificationsEnabled
    val notifyMinutesBefore: StateFlow<Int> = repository.notifyMinutesBefore
    val notificationsTargetId: StateFlow<Int?> = repository.notificationsTargetId
    val vpnWarningEnabled: StateFlow<Boolean> = repository.vpnWarningEnabled
    val askBeforeNoteDelete: StateFlow<Boolean> = repository.askBeforeNoteDelete

    fun setShowLessonProgress(enabled: Boolean) {
        repository.setShowLessonProgress(enabled)
    }

    fun setShowEmptyLessonProgress(enabled: Boolean) {
        repository.setShowEmptyLessonProgress(enabled)
    }

    fun setShowBreakProgress(enabled: Boolean) {
        repository.setShowBreakProgress(enabled)
    }

    fun setCalendarSwipeCollapse(enabled: Boolean) {
        repository.setCalendarSwipeCollapse(enabled)
    }

    fun setHideAdditionalLessons(enabled: Boolean) {
        repository.setHideAdditionalLessons(enabled)
    }

    fun setAutoScrollToCurrentLesson(enabled: Boolean) {
        repository.setAutoScrollToCurrentLesson(enabled)
    }

    fun setThemeOverlay(overlay: ThemeOverlay) {
        repository.setThemeOverlay(overlay)
    }

    fun setSakuraTheme(enabled: Boolean) {
        repository.setSakuraTheme(enabled)
    }

    fun setKawaiiTheme(enabled: Boolean) {
        repository.setKawaiiTheme(enabled)
    }

    fun setCyberpunkTheme(enabled: Boolean) {
        repository.setCyberpunkTheme(enabled)
    }

    fun setMatrixTheme(enabled: Boolean) = repository.setMatrixTheme(enabled)
    fun setHalloweenTheme(enabled: Boolean) = repository.setHalloweenTheme(enabled)

    fun setLessonTypeColor(type: LessonType, color: LessonColor) =
        repository.setLessonTypeColor(type, color)
    fun resetLessonTypeColors() = repository.resetLessonTypeColors()
    fun setCyberpunkVariant(variant: CyberpunkVariant) = repository.setCyberpunkVariant(variant)

    /** Матрица потеряна: выключается и исчезает из списка найденных тем. */
    fun revokeMatrixUnlock() = repository.revokeMatrixUnlock()
    fun setCheatsAgreed(agreed: Boolean?) = repository.setCheatsAgreed(agreed)
    fun setCheatsBlocked(blocked: Boolean) = repository.setCheatsBlocked(blocked)
    fun setDiagnosticsEnabled(enabled: Boolean) = repository.setDiagnosticsEnabled(enabled)
    fun setAppIcon(name: String) = repository.setAppIcon(name)
    fun setNotificationsEnabled(enabled: Boolean) = repository.setNotificationsEnabled(enabled)
    fun setNotifyMinutesBefore(minutes: Int) = repository.setNotifyMinutesBefore(minutes)
    fun setNotificationsTargetId(targetId: Int?) = repository.setNotificationsTargetId(targetId)
    fun setVpnWarningEnabled(enabled: Boolean) = repository.setVpnWarningEnabled(enabled)
    fun setAskBeforeNoteDelete(ask: Boolean) = repository.setAskBeforeNoteDelete(ask)

    fun setDockTabs(tabs: List<AppTab>) {
        repository.setDockTabs(tabs)
    }

    fun setDockHidden(hidden: Boolean) {
        repository.setDockHidden(hidden)
    }

    private val _activeSubScreen = MutableStateFlow(OtherSubScreen.ROOT)
    val activeSubScreen: StateFlow<OtherSubScreen> = _activeSubScreen.asStateFlow()

    // Скролл корневого экрана настроек живёт в VM: LayeredNavHost пересоздаёт
    // SettingsScreen в другом слое при переходе в подраздел (родитель под
    // дочерним), remember-состояния там не выживают. Общий ScrollState
    // переживает пересоздание экземпляров без save/restore.
    val settingsScrollState = androidx.compose.foundation.ScrollState(0)

    fun navigateToSubScreen(subScreen: OtherSubScreen) {
        _activeSubScreen.value = subScreen
        // Сервисные подстраницы пишут свой service_open (с источником) —
        // здесь их не дублируем, иначе в панели двойной счёт.
        if (subScreen != OtherSubScreen.ROOT && !subScreen.isServiceScreen) {
        }
    }

    fun resetToRoot() {
        _activeSubScreen.value = OtherSubScreen.ROOT
    }

    private val _storageStats = MutableStateFlow(repository.getStorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    fun refreshStorageStats() {
        _storageStats.value = repository.getStorageStats()
    }

    // Команда проекта показывается из захардкоженного списка TeamMembers
    // (аватарки — в ресурсах); подгрузка контрибьюторов из GitHub удалена,
    // чтобы экран не зависел от сети.

    private val _updateResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateResult: StateFlow<UpdateCheckResult?> = _updateResult.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    // Запрос показать диалог «Вышла новая версия» вручную (тап по
    // оранжевому/красному мини-виджету, репорт владельца): открывается
    // ДАЖЕ если подавлен чекбоксом — в диалоге галочку можно снять.
    private val _showUpdateDialogRequest = MutableStateFlow(0)
    val showUpdateDialogRequest: StateFlow<Int> = _showUpdateDialogRequest.asStateFlow()

    fun requestShowUpdateDialog() {
        _showUpdateDialogRequest.value += 1
    }

    fun checkForUpdates() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _isCheckingUpdate.value = true
                _updateResult.value = updateChecker.checkForUpdates()
            } catch (t: Throwable) {
                println("checkForUpdates caught throwable: ${t.message}")
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    // Search, filter, and sort state for ManageSchedulesScreen
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow<ScheduleTargetType?>(null)
    val filterType: StateFlow<ScheduleTargetType?> = _filterType.asStateFlow()

    private val _sortOrder = MutableStateFlow(TargetSortOrder.TITLE_ASC)
    val sortOrder: StateFlow<TargetSortOrder> = _sortOrder.asStateFlow()

    val filteredSavedTargets: StateFlow<List<ScheduleTarget>> = combine(
        repository.savedTargets,
        _searchQuery,
        _filterType,
        _sortOrder
    ) { list, query, filter, sort ->
        var result = list
        if (filter != null) {
            result = result.filter { it.type == filter }
        }
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            result = result.filter {
                it.targetTitle.contains(trimmed, ignoreCase = true) ||
                it.fullTitle.contains(trimmed, ignoreCase = true)
            }
        }
        when (sort) {
            TargetSortOrder.TITLE_ASC -> result.sortedBy { it.targetTitle.lowercase() }
            TargetSortOrder.TITLE_DESC -> result.sortedByDescending { it.targetTitle.lowercase() }
            TargetSortOrder.RECENT -> result
            TargetSortOrder.OLDEST -> result.reversed()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: ScheduleTargetType?) {
        _filterType.value = type
    }

    fun setSortOrder(order: TargetSortOrder) {
        _sortOrder.value = order
    }

    fun toggleSortDirection() {
        _sortOrder.value = when (_sortOrder.value) {
            TargetSortOrder.TITLE_ASC -> TargetSortOrder.TITLE_DESC
            TargetSortOrder.TITLE_DESC -> TargetSortOrder.TITLE_ASC
            TargetSortOrder.RECENT -> TargetSortOrder.OLDEST
            TargetSortOrder.OLDEST -> TargetSortOrder.RECENT
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        repository.setThemeMode(mode)
    }

    fun setShowEmptyLessons(enabled: Boolean) {
        repository.setShowEmptyLessons(enabled)
    }

    fun setShowAbbreviatedNames(enabled: Boolean) {
        repository.setShowAbbreviatedNames(enabled)
    }

    fun selectTarget(target: ScheduleTarget) {
        repository.selectTarget(target)
    }

    fun removeTarget(targetId: Int) {
        repository.removeTarget(targetId)
    }

    fun refreshSchedule() {
        repository.refreshCurrentSchedule()
    }

    fun clearCache() {
        repository.clearCache()
        _storageStats.value = repository.getStorageStats()
    }

    fun resetAllData() {
        taskRepository.clearAllData()
        repository.resetAllData()
        DebugConfig.reset()
        _storageStats.value = repository.getStorageStats()
    }

    suspend fun search(query: String): List<ScheduleTarget> {
        return repository.search(query)
    }

    fun addAndSelectTarget(target: ScheduleTarget) {
        repository.addAndSelectTarget(target)
    }
}
