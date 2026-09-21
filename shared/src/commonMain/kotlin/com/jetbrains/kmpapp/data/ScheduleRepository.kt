package com.jetbrains.kmpapp.data

import com.jetbrains.kmpapp.data.analytics.AppAnalytics
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.ScheduleExport
import com.jetbrains.kmpapp.data.model.SemesterConfig
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.data.notifications.NotificationsManager
import com.jetbrains.kmpapp.data.storage.LessonNotesStorage
import com.jetbrains.kmpapp.data.storage.ScheduleStorage
import com.jetbrains.kmpapp.screens.components.AppTab
import com.jetbrains.kmpapp.theme.ThemeOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Репозиторий локального расписания: единственный источник — собранный
 * пользователем семестр ([SemesterConfig]). Никаких серверов: занятия
 * генерируются из недельного шаблона, уведомления планируются по ним же.
 */
class ScheduleRepository(
    private val storage: ScheduleStorage,
    private val powerManager: com.jetbrains.kmpapp.data.power.PlatformPowerManager,
    private val lessonNotesStorage: LessonNotesStorage
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val notificationRescheduleMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    val isLowPowerMode: StateFlow<Boolean> = powerManager.isLowPowerMode

    /** Активный семестр (единственный; null — расписание не собрано). */
    val semester: StateFlow<SemesterConfig?> = storage.semesterConfig

    val currentLessons: StateFlow<List<Lesson>> = combine(
        storage.semesterLessons,
        storage.hideAdditionalLessons
    ) { lessons, hideAdditional ->
        if (hideAdditional) lessons.filter { it.lessonType != LessonType.ADDITIONAL } else lessons
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val showEmptyLessons: StateFlow<Boolean> = storage.showEmptyLessons
    val themeMode: StateFlow<ThemeMode> = storage.themeMode

    /** Состав дока: «Задачи» — единственная скрываемая вкладка. */
    val dockTabs: StateFlow<List<AppTab>> = storage.tasksEnabled
        .map { enabled ->
            listOf(AppTab.SCHEDULE) +
                (if (enabled) listOf(AppTab.TASKS) else emptyList()) +
                listOf(AppTab.OTHER)
        }
        .stateIn(scope, SharingStarted.Eagerly, listOf(AppTab.SCHEDULE, AppTab.TASKS, AppTab.OTHER))

    val tasksEnabled: StateFlow<Boolean> = storage.tasksEnabled
    fun setTasksEnabled(enabled: Boolean) = storage.setTasksEnabled(enabled)

    fun getStorageStats(): com.jetbrains.kmpapp.data.model.StorageStats = storage.getStorageStats()

    fun setShowEmptyLessons(enabled: Boolean) {
        storage.setShowEmptyLessons(enabled)
    }

    val showAbbreviatedNames: StateFlow<Boolean> = storage.showAbbreviatedNames

    fun setShowAbbreviatedNames(enabled: Boolean) {
        storage.setShowAbbreviatedNames(enabled)
    }

    val showLessonProgress: StateFlow<Boolean> = storage.showLessonProgress

    fun setShowLessonProgress(enabled: Boolean) {
        storage.setShowLessonProgress(enabled)
    }

    val showEmptyLessonProgress: StateFlow<Boolean> = storage.showEmptyLessonProgress

    fun setShowEmptyLessonProgress(enabled: Boolean) {
        storage.setShowEmptyLessonProgress(enabled)
    }

    val showBreakProgress: StateFlow<Boolean> = storage.showBreakProgress

    fun setShowBreakProgress(enabled: Boolean) {
        storage.setShowBreakProgress(enabled)
    }

    val calendarCollapsed: StateFlow<Boolean> = storage.calendarCollapsed

    fun setCalendarCollapsed(collapsed: Boolean) {
        storage.setCalendarCollapsed(collapsed)
    }

    val calendarSwipeCollapse: StateFlow<Boolean> = storage.calendarSwipeCollapse

    fun setCalendarSwipeCollapse(enabled: Boolean) {
        storage.setCalendarSwipeCollapse(enabled)
    }

    val hideAdditionalLessons: StateFlow<Boolean> = storage.hideAdditionalLessons

    fun setHideAdditionalLessons(enabled: Boolean) {
        storage.setHideAdditionalLessons(enabled)
    }

    val autoScrollToCurrentLesson: StateFlow<Boolean> = storage.autoScrollToCurrentLesson

    fun setAutoScrollToCurrentLesson(enabled: Boolean) {
        storage.setAutoScrollToCurrentLesson(enabled)
    }

    fun setThemeMode(mode: ThemeMode) {
        storage.setThemeMode(mode)
    }

    val themeOverlay: StateFlow<ThemeOverlay> = storage.themeOverlay
    val isSakuraTheme: StateFlow<Boolean> = storage.isSakuraTheme
    val isCyberpunkTheme: StateFlow<Boolean> = storage.isCyberpunkTheme
    val isMatrixTheme: StateFlow<Boolean> = storage.isMatrixTheme
    val cheatsAgreed: StateFlow<Boolean?> = storage.cheatsAgreed
    val cheatsBlocked: StateFlow<Boolean> = storage.cheatsBlocked
    val betaChannel: StateFlow<Boolean> = storage.betaChannel
    val analyticsEnabled: StateFlow<Boolean> = storage.analyticsEnabled
    val analyticsConsent: StateFlow<Boolean?> = storage.analyticsConsent
    val notificationsEnabled: StateFlow<Boolean> = storage.notificationsEnabled
    val notifyMinutesBefore: StateFlow<Int> = storage.notifyMinutesBefore
    val skippedUpdateVersion: StateFlow<String?> = storage.skippedUpdateVersion

    init {
        // Единая точка перепланирования напоминаний: семестр, тумблер или
        // минуты — любое изменение пересчитывает партию уведомлений.
        scope.launch {
            val notifSettings = combine(
                storage.notificationsEnabled,
                storage.notifyMinutesBefore,
                storage.hideAdditionalLessons
            ) { enabled, minutes, hideAdditional -> Triple(enabled, minutes, hideAdditional) }
            combine(storage.semesterLessons, notifSettings) { lessons, (enabled, minutes, hideAdditional) ->
                Pair(lessons, Triple(enabled, minutes, hideAdditional))
            }.collect { (lessons, settings) ->
                notificationRescheduleMutex.withLock {
                    val (enabled, minutes, hideAdditional) = settings
                    val targetLessons = if (hideAdditional) {
                        lessons.filter { it.lessonType != LessonType.ADDITIONAL }
                    } else lessons
                    if (!enabled || targetLessons.isEmpty()) {
                        NotificationsManager.reschedule(emptyList(), minutes) { "" }
                    } else {
                        NotificationsManager.reschedule(targetLessons, minutes) { lesson ->
                            val room = lesson.classrooms.firstOrNull()?.let { ", ауд. $it" } ?: ""
                            "Через ${minutes} мин: ${lesson.subject}$room"
                        }
                    }
                }
            }
        }
    }

    fun setThemeOverlay(overlay: ThemeOverlay) = storage.setThemeOverlay(overlay)
    fun setMatrixTheme(enabled: Boolean) = storage.setMatrixTheme(enabled)
    fun setCheatsAgreed(agreed: Boolean?) = storage.setCheatsAgreed(agreed)
    fun setCheatsBlocked(blocked: Boolean) = storage.setCheatsBlocked(blocked)
    fun setBetaChannel(enabled: Boolean) = storage.setBetaChannel(enabled)
    fun setAnalyticsEnabled(enabled: Boolean) = storage.setAnalyticsEnabled(enabled)
    fun setAnalyticsConsent(accepted: Boolean) = storage.setAnalyticsConsent(accepted)
    fun setNotificationsEnabled(enabled: Boolean) = storage.setNotificationsEnabled(enabled)
    fun setNotifyMinutesBefore(minutes: Int) = storage.setNotifyMinutesBefore(minutes)

    fun setSakuraTheme(enabled: Boolean) {
        storage.setSakuraThemeExclusive(enabled)
    }

    fun setCyberpunkTheme(enabled: Boolean) {
        storage.setCyberpunkTheme(enabled)
    }

    fun setSkippedUpdateVersion(version: String?) = storage.setSkippedUpdateVersion(version)

    // ── Семестр: сохранение, удаление, экспорт/импорт ───────────────

    /** Текст ошибки валидации или null — тогда семестр сохранён. */
    fun saveSemester(config: SemesterConfig): String? {
        val error = config.validate()
        if (error != null) return error
        storage.saveSemester(config)
        AppAnalytics.logEvent(
            com.jetbrains.kmpapp.data.analytics.AnalyticsEvents.SCHEDULE_TARGET_ADDED,
            mapOf("type" to "local", "count" to "1")
        )
        return null
    }

    /** Удалить семестр и заметки к его парам. Задачи остаются (стираются вручную). */
    fun deleteSemester() {
        storage.deleteSemester()
        lessonNotesStorage.removeNotesForTarget(SemesterConfig.TARGET_ID)
    }

    fun exportSemesterJson(): String? = storage.exportSemesterJson()

    /** Импорт JSON (ScheduleExport или голый SemesterConfig). Текст ошибки или null. */
    fun importSemesterJson(text: String): String? {
        val config = try {
            runCatching { json.decodeFromString<ScheduleExport>(text).semester }
                .getOrElse { json.decodeFromString<SemesterConfig>(text) }
        } catch (t: Throwable) {
            return "Не удалось разобрать файл расписания"
        }
        return saveSemester(config)
    }

    fun resetAllData() {
        storage.resetAllData()
        lessonNotesStorage.removeNotesForTarget(SemesterConfig.TARGET_ID)
    }
}
