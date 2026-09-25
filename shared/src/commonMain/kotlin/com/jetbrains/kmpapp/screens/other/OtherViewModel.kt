package com.jetbrains.kmpapp.screens.other

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetbrains.kmpapp.data.ScheduleRepository
import com.jetbrains.kmpapp.data.TaskRepository
import com.jetbrains.kmpapp.data.analytics.AnalyticsEvents
import com.jetbrains.kmpapp.data.analytics.AppAnalytics
import com.jetbrains.kmpapp.data.model.SemesterConfig
import com.jetbrains.kmpapp.data.model.StorageStats
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.theme.ThemeOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.jetbrains.kmpapp.data.update.AppUpdateChecker
import com.jetbrains.kmpapp.data.update.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

enum class OtherSubScreen(val depth: Int) {
    ROOT(0),
    CONFIGURATOR(1),
    SETTINGS(1),
    TASK_SETTINGS(2),
    // Подстраницы настроек расписания
    SCHEDULE_DISPLAY(2),
    SCHEDULE_PROGRESS(2),
    ABOUT(1),
    TEAM(2),
    LICENSES(2),
    DEBUG_SETTINGS(2),
    EXPERIMENTAL_SETTINGS(3)
}

/** Родитель подстраницы для послойной навигации (null = корень). */
private val SUB_SCREEN_PARENT = mapOf(
    OtherSubScreen.CONFIGURATOR to OtherSubScreen.ROOT,
    OtherSubScreen.SETTINGS to OtherSubScreen.ROOT,
    OtherSubScreen.TASK_SETTINGS to OtherSubScreen.SETTINGS,
    OtherSubScreen.SCHEDULE_DISPLAY to OtherSubScreen.SETTINGS,
    OtherSubScreen.SCHEDULE_PROGRESS to OtherSubScreen.SETTINGS,
    OtherSubScreen.ABOUT to OtherSubScreen.ROOT,
    OtherSubScreen.TEAM to OtherSubScreen.ABOUT,
    OtherSubScreen.LICENSES to OtherSubScreen.ABOUT,
    OtherSubScreen.DEBUG_SETTINGS to OtherSubScreen.ABOUT,
    OtherSubScreen.EXPERIMENTAL_SETTINGS to OtherSubScreen.DEBUG_SETTINGS
)

fun OtherSubScreen.parent(): OtherSubScreen? =
    if (this == OtherSubScreen.ROOT) null else SUB_SCREEN_PARENT[this]

class OtherViewModel(
    private val repository: ScheduleRepository,
    private val updateChecker: AppUpdateChecker,
    private val taskRepository: TaskRepository
) : ViewModel() {

    val semester: StateFlow<SemesterConfig?> = repository.semester
    val showEmptyLessons: StateFlow<Boolean> = repository.showEmptyLessons
    val showLessonProgress: StateFlow<Boolean> = repository.showLessonProgress
    val showEmptyLessonProgress: StateFlow<Boolean> = repository.showEmptyLessonProgress
    val showBreakProgress: StateFlow<Boolean> = repository.showBreakProgress
    val autoScrollToCurrentLesson: StateFlow<Boolean> = repository.autoScrollToCurrentLesson
    val showAbbreviatedNames: StateFlow<Boolean> = repository.showAbbreviatedNames
    val themeMode: StateFlow<ThemeMode> = repository.themeMode
    val themeOverlay: StateFlow<ThemeOverlay> = repository.themeOverlay
    val isSakuraTheme: StateFlow<Boolean> = repository.isSakuraTheme
    val isMatrixTheme: StateFlow<Boolean> = repository.isMatrixTheme
    val cheatsAgreed: StateFlow<Boolean?> = repository.cheatsAgreed
    val cheatsBlocked: StateFlow<Boolean> = repository.cheatsBlocked
    val tasksEnabled: StateFlow<Boolean> = repository.tasksEnabled
    val analyticsEnabled: StateFlow<Boolean> = repository.analyticsEnabled
    val notificationsEnabled: StateFlow<Boolean> = repository.notificationsEnabled
    val notifyMinutesBefore: StateFlow<Int> = repository.notifyMinutesBefore
    val skippedUpdateVersion: StateFlow<String?> = repository.skippedUpdateVersion

    fun setShowLessonProgress(enabled: Boolean) {
        repository.setShowLessonProgress(enabled)
    }

    fun setShowEmptyLessonProgress(enabled: Boolean) {
        repository.setShowEmptyLessonProgress(enabled)
    }

    fun setShowBreakProgress(enabled: Boolean) {
        repository.setShowBreakProgress(enabled)
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

    fun setMatrixTheme(enabled: Boolean) = repository.setMatrixTheme(enabled)
    fun setCheatsAgreed(agreed: Boolean?) = repository.setCheatsAgreed(agreed)
    fun setCheatsBlocked(blocked: Boolean) = repository.setCheatsBlocked(blocked)
    fun setTasksEnabled(enabled: Boolean) = repository.setTasksEnabled(enabled)
    fun setAnalyticsEnabled(enabled: Boolean) = repository.setAnalyticsEnabled(enabled)
    fun setNotificationsEnabled(enabled: Boolean) = repository.setNotificationsEnabled(enabled)
    fun setNotifyMinutesBefore(minutes: Int) = repository.setNotifyMinutesBefore(minutes)

    fun setShowEmptyLessons(enabled: Boolean) {
        repository.setShowEmptyLessons(enabled)
    }

    fun setShowAbbreviatedNames(enabled: Boolean) {
        repository.setShowAbbreviatedNames(enabled)
    }

    fun setThemeMode(mode: ThemeMode) {
        repository.setThemeMode(mode)
    }

    /** «Пропустить» в диалоге обновления: версия больше не предлагается. */
    fun skipUpdate(version: String) {
        repository.setSkippedUpdateVersion(version)
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
        if (subScreen != OtherSubScreen.ROOT) {
            AppAnalytics.logEvent(AnalyticsEvents.NAV_SCREEN_VIEW, mapOf("screen" to subScreen.name))
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

    private val _updateResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateResult: StateFlow<UpdateCheckResult?> = _updateResult.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
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

    /** Закрыть диалог обновления без пропуска версии: при следующей проверке покажется снова. */
    fun dismissUpdateDialog() {
        _updateResult.value = null
    }

    /** Полный ручной сброс: семестр, задачи и настройки. */
    fun resetAllData() {
        taskRepository.clearAllData()
        repository.resetAllData()
        _storageStats.value = repository.getStorageStats()
    }
}
