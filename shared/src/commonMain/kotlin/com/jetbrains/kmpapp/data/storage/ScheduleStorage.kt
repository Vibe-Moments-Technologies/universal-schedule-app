package com.jetbrains.kmpapp.data.storage

import com.jetbrains.kmpapp.data.analytics.AnalyticsEvents
import com.jetbrains.kmpapp.data.analytics.AppAnalytics
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.SemesterConfig
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.theme.ThemeOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Локальное хранилище: один активный семестр ([SemesterConfig]) и настройки.
 * Занятия генерируются из конфигурации детерминированно и в память, и в
 * [SemesterWeeks]-маркеры — сервера и кэша загрузки больше нет.
 */
class ScheduleStorage(
    private val platformStorage: PlatformStorage
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _semesterConfig = MutableStateFlow<SemesterConfig?>(null)
    val semesterConfig: StateFlow<SemesterConfig?> = _semesterConfig.asStateFlow()

    /** Развёртка активного семестра в занятия по датам. */
    private val _semesterLessons = MutableStateFlow<List<Lesson>>(emptyList())
    val semesterLessons: StateFlow<List<Lesson>> = _semesterLessons.asStateFlow()

    private val _showEmptyLessons = MutableStateFlow<Boolean>(true)
    val showEmptyLessons: StateFlow<Boolean> = _showEmptyLessons.asStateFlow()

    private val _showLessonProgress = MutableStateFlow<Boolean>(true)
    val showLessonProgress: StateFlow<Boolean> = _showLessonProgress.asStateFlow()

    private val _showEmptyLessonProgress = MutableStateFlow<Boolean>(true)
    val showEmptyLessonProgress: StateFlow<Boolean> = _showEmptyLessonProgress.asStateFlow()

    private val _showBreakProgress = MutableStateFlow<Boolean>(true)
    val showBreakProgress: StateFlow<Boolean> = _showBreakProgress.asStateFlow()

    private val _calendarCollapsed = MutableStateFlow(false)
    val calendarCollapsed: StateFlow<Boolean> = _calendarCollapsed.asStateFlow()

    private val _calendarSwipeCollapse = MutableStateFlow(false)
    val calendarSwipeCollapse: StateFlow<Boolean> = _calendarSwipeCollapse.asStateFlow()

    private val _hideAdditionalLessons = MutableStateFlow(false)
    val hideAdditionalLessons: StateFlow<Boolean> = _hideAdditionalLessons.asStateFlow()

    private val _autoScrollToCurrentLesson = MutableStateFlow<Boolean>(true)
    val autoScrollToCurrentLesson: StateFlow<Boolean> = _autoScrollToCurrentLesson.asStateFlow()

    private val _showAbbreviatedNames = MutableStateFlow<Boolean>(false)
    val showAbbreviatedNames: StateFlow<Boolean> = _showAbbreviatedNames.asStateFlow()

    private val _themeMode = MutableStateFlow<ThemeMode>(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Вкладка «Задачи» в доке: единственная настраиваемая вкладка. */
    private val _tasksEnabled = MutableStateFlow(true)
    val tasksEnabled: StateFlow<Boolean> = _tasksEnabled.asStateFlow()

    private val _themeOverlay = MutableStateFlow(ThemeOverlay.NONE)
    val themeOverlay: StateFlow<ThemeOverlay> = _themeOverlay.asStateFlow()

    val isSakuraTheme: StateFlow<Boolean> = themeOverlay.map { it == ThemeOverlay.SAKURA }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val isCyberpunkTheme: StateFlow<Boolean> = themeOverlay.map { it == ThemeOverlay.CYBERPUNK }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val isMatrixTheme: StateFlow<Boolean> = themeOverlay.map { it == ThemeOverlay.MATRIX }
        .stateIn(scope, SharingStarted.Eagerly, false)
    private val _cheatsAgreed = MutableStateFlow<Boolean?>(null)
    val cheatsAgreed: StateFlow<Boolean?> = _cheatsAgreed.asStateFlow()
    private val _cheatsBlocked = MutableStateFlow(false)
    val cheatsBlocked: StateFlow<Boolean> = _cheatsBlocked.asStateFlow()

    // Скрытые системы (бета-канал, аналитика): ключи живут, UI выключен.
    private val _betaChannel = MutableStateFlow(false)
    val betaChannel: StateFlow<Boolean> = _betaChannel.asStateFlow()

    private val _analyticsEnabled = MutableStateFlow(true)
    val analyticsEnabled: StateFlow<Boolean> = _analyticsEnabled.asStateFlow()

    private val _analyticsConsent = MutableStateFlow<Boolean?>(null)
    val analyticsConsent: StateFlow<Boolean?> = _analyticsConsent.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(false)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _notifyMinutesBefore = MutableStateFlow(15)
    val notifyMinutesBefore: StateFlow<Int> = _notifyMinutesBefore.asStateFlow()

    /** Версия обновления, которую пользователь попросил больше не предлагать. */
    private val _skippedUpdateVersion = MutableStateFlow<String?>(null)
    val skippedUpdateVersion: StateFlow<String?> = _skippedUpdateVersion.asStateFlow()

    init {
        loadPersistedState()
    }

    private fun loadPersistedState() {
        try {
            loadPreferenceFlags()
            loadSemester()
        } catch (t: Throwable) {
            println("ScheduleStorage: failed to load persisted state: ${t.message}")
        }
    }

    /** Тумблеры и скалярные настройки; отсутствующий или битый ключ = дефолт. */
    private fun loadPreferenceFlags() {
        _themeMode.value = loadThemeModeSetting()
        _showEmptyLessons.value = loadBooleanFlag(KEY_SHOW_EMPTY_LESSONS, true)
        _showLessonProgress.value = loadBooleanFlag(KEY_SHOW_LESSON_PROGRESS, true)
        _showEmptyLessonProgress.value = loadBooleanFlag(KEY_SHOW_EMPTY_LESSON_PROGRESS, true)
        _showBreakProgress.value = loadBooleanFlag(KEY_SHOW_BREAK_PROGRESS, true)
        _calendarCollapsed.value = loadBooleanFlag(KEY_CALENDAR_COLLAPSED, false)
        _calendarSwipeCollapse.value = loadBooleanFlag(KEY_CALENDAR_SWIPE_COLLAPSE, false)
        _hideAdditionalLessons.value = loadBooleanFlag(KEY_HIDE_ADDITIONAL_LESSONS, false)
        _autoScrollToCurrentLesson.value = loadBooleanFlag(KEY_AUTO_SCROLL_CURRENT_LESSON, true)
        _showAbbreviatedNames.value = loadBooleanFlag(KEY_SHOW_ABBREVIATED_NAMES, false)
        _themeOverlay.value = loadThemeOverlay()
        _cheatsAgreed.value = nullableFlag(KEY_CHEATS_AGREED)
        _cheatsBlocked.value = loadBooleanFlag(KEY_CHEATS_BLOCKED, false)
        _betaChannel.value = loadBooleanFlag(KEY_BETA_CHANNEL, false)
        _analyticsEnabled.value = loadBooleanFlag(KEY_ANALYTICS_ENABLED, true)
        _analyticsConsent.value = nullableFlag(KEY_ANALYTICS_CONSENT)
        // Приветственный гейт согласия скрыт: без явного согласия ничего не уходит.
        AppAnalytics.setEventsEnabled(_analyticsEnabled.value && _analyticsConsent.value != null)
        _tasksEnabled.value = loadBooleanFlag(KEY_TASKS_ENABLED, true)
        _notificationsEnabled.value = loadBooleanFlag(KEY_NOTIFICATIONS_ENABLED, false)
        _notifyMinutesBefore.value =
            platformStorage.getString(KEY_NOTIFY_MINUTES_BEFORE)?.toIntOrNull() ?: 15
        _skippedUpdateVersion.value = platformStorage.getString(KEY_SKIPPED_UPDATE_VERSION)
    }

    private fun loadBooleanFlag(key: String, default: Boolean): Boolean = try {
        val s = platformStorage.getString(key)
        if (s.isNullOrBlank()) default else s.toBooleanStrictOrNull() ?: default
    } catch (_: Throwable) {
        default
    }

    /** null = ключа нет (решение ещё не принимали); битое значение тоже null. */
    private fun nullableFlag(key: String): Boolean? = try {
        platformStorage.getString(key)?.toBooleanStrictOrNull()
    } catch (_: Throwable) {
        null
    }

    private fun loadThemeModeSetting(): ThemeMode = try {
        val s = platformStorage.getString(KEY_APP_THEME)
        if (s.isNullOrBlank()) ThemeMode.SYSTEM else try {
            ThemeMode.valueOf(s)
        } catch (_: Throwable) {
            ThemeMode.SYSTEM
        }
    } catch (_: Throwable) {
        ThemeMode.SYSTEM
    }

    private fun loadSemester() {
        val config = try {
            val s = platformStorage.getString(KEY_SEMESTER_CONFIG)
            if (s.isNullOrBlank()) null
            else try { json.decodeFromString<SemesterConfig>(s) } catch (_: Throwable) { null }
        } catch (_: Throwable) {
            null
        }
        applySemester(config)
    }

    /** Единая точка применения семестра: config → занятия + маркеры недель. */
    private fun applySemester(config: SemesterConfig?) {
        _semesterConfig.value = config
        _semesterLessons.value = config?.generateLessons() ?: emptyList()
        com.jetbrains.kmpapp.data.model.SemesterWeeks.set(config?.weekMarkers() ?: emptyList())
    }

    fun saveSemester(config: SemesterConfig) {
        applySemester(config)
        scope.launch {
            try {
                platformStorage.saveString(KEY_SEMESTER_CONFIG, json.encodeToString(config))
            } catch (e: Exception) {
                println("Failed to persist semester config: ${e.message}")
            }
        }
    }

    /** Удаление семестра. Задачи НЕ трогаем — они стираются только вручную. */
    fun deleteSemester() {
        applySemester(null)
        scope.launch {
            try {
                platformStorage.remove(KEY_SEMESTER_CONFIG)
            } catch (e: Exception) {
                println("Failed to remove semester config: ${e.message}")
            }
        }
    }

    fun exportSemesterJson(): String? =
        _semesterConfig.value?.let { json.encodeToString(it) }

    fun setThemeMode(mode: ThemeMode) {
        val changed = _themeMode.value != mode
        _themeMode.value = mode
        scope.launch {
            try {
                platformStorage.saveString(KEY_APP_THEME, mode.name)
            } catch (e: Exception) {
                println("Failed to persist themeMode: ${e.message}")
            }
        }
        if (changed) AppAnalytics.logEvent(AnalyticsEvents.SETTINGS_THEME_SET, mapOf("mode" to mode.name))
    }

    fun setShowEmptyLessons(enabled: Boolean) {
        _showEmptyLessons.value = enabled
        scope.launch {
            try {
                platformStorage.saveString(KEY_SHOW_EMPTY_LESSONS, enabled.toString())
            } catch (e: Exception) {
                println("Failed to persist showEmptyLessons: ${e.message}")
            }
        }
    }

    fun setShowLessonProgress(enabled: Boolean) {
        _showLessonProgress.value = enabled
        scope.launch {
            try {
                platformStorage.saveString(KEY_SHOW_LESSON_PROGRESS, enabled.toString())
            } catch (e: Exception) {
                println("Failed to persist showLessonProgress: ${e.message}")
            }
        }
    }

    fun setShowEmptyLessonProgress(enabled: Boolean) {
        _showEmptyLessonProgress.value = enabled
        scope.launch {
            try {
                platformStorage.saveString(KEY_SHOW_EMPTY_LESSON_PROGRESS, enabled.toString())
            } catch (e: Exception) {
                println("Failed to persist showEmptyLessonProgress: ${e.message}")
            }
        }
    }

    fun setShowBreakProgress(enabled: Boolean) {
        _showBreakProgress.value = enabled
        scope.launch {
            try {
                platformStorage.saveString(KEY_SHOW_BREAK_PROGRESS, enabled.toString())
            } catch (e: Exception) {
                println("Failed to persist showBreakProgress: ${e.message}")
            }
        }
    }

    /** Свёрнута ли лента календаря на экране расписания. */
    fun setCalendarCollapsed(collapsed: Boolean) {
        _calendarCollapsed.value = collapsed
        scope.launch {
            try {
                platformStorage.saveString(KEY_CALENDAR_COLLAPSED, collapsed.toString())
            } catch (e: Exception) {
                println("Failed to persist calendarCollapsed: ${e.message}")
            }
        }
    }

    /** Разрешено ли сворачивать ленту календаря свайпом вверх по разделителю. */
    fun setCalendarSwipeCollapse(enabled: Boolean) {
        _calendarSwipeCollapse.value = enabled
        scope.launch {
            try {
                platformStorage.saveString(KEY_CALENDAR_SWIPE_COLLAPSE, enabled.toString())
            } catch (e: Exception) {
                println("Failed to persist calendarSwipeCollapse: ${e.message}")
            }
        }
    }

    /** Скрывать ли доп. занятия (ДОП) в расписании и уведомлениях. */
    fun setHideAdditionalLessons(enabled: Boolean) {
        _hideAdditionalLessons.value = enabled
        scope.launch {
            try {
                platformStorage.saveString(KEY_HIDE_ADDITIONAL_LESSONS, enabled.toString())
            } catch (e: Exception) {
                println("Failed to persist hideAdditionalLessons: ${e.message}")
            }
        }
    }

    fun setAutoScrollToCurrentLesson(enabled: Boolean) {
        _autoScrollToCurrentLesson.value = enabled
        scope.launch {
            try {
                platformStorage.saveString(KEY_AUTO_SCROLL_CURRENT_LESSON, enabled.toString())
            } catch (e: Exception) {
                println("Failed to persist autoScrollToCurrentLesson: ${e.message}")
            }
        }
    }

    fun setShowAbbreviatedNames(enabled: Boolean) {
        _showAbbreviatedNames.value = enabled
        scope.launch {
            try {
                platformStorage.saveString(KEY_SHOW_ABBREVIATED_NAMES, enabled.toString())
            } catch (e: Exception) {
                println("Failed to persist showAbbreviatedNames: ${e.message}")
            }
        }
    }

    fun setThemeOverlay(overlay: ThemeOverlay) {
        val changed = _themeOverlay.value != overlay
        _themeOverlay.value = overlay
        scope.launch {
            try {
                platformStorage.saveString(KEY_THEME_OVERLAY, overlay.name)
                platformStorage.saveString(KEY_SAKURA_THEME, (overlay == ThemeOverlay.SAKURA).toString())
                platformStorage.saveString(KEY_CYBERPUNK_THEME, (overlay == ThemeOverlay.CYBERPUNK).toString())
                platformStorage.saveString(KEY_MATRIX_THEME, (overlay == ThemeOverlay.MATRIX).toString())
            } catch (e: Exception) {
                println("Failed to persist theme overlay: ${e.message}")
            }
        }
        if (changed) AppAnalytics.logEvent(AnalyticsEvents.SETTINGS_THEME_OVERLAY_SET, mapOf("overlay" to overlay.name))
    }

    fun setCyberpunkTheme(enabled: Boolean) {
        setThemeOverlay(if (enabled) ThemeOverlay.CYBERPUNK else ThemeOverlay.NONE)
    }

    fun setMatrixTheme(enabled: Boolean) {
        setThemeOverlay(if (enabled) ThemeOverlay.MATRIX else ThemeOverlay.NONE)
    }

    fun setCheatsAgreed(agreed: Boolean?) {
        _cheatsAgreed.value = agreed
        scope.launch {
            if (agreed == null) platformStorage.remove(KEY_CHEATS_AGREED)
            else platformStorage.saveString(KEY_CHEATS_AGREED, agreed.toString())
        }
    }

    fun setCheatsBlocked(blocked: Boolean) {
        _cheatsBlocked.value = blocked
        scope.launch { platformStorage.saveString(KEY_CHEATS_BLOCKED, blocked.toString()) }
    }

    fun setBetaChannel(enabled: Boolean) {
        _betaChannel.value = enabled
        scope.launch { platformStorage.saveString(KEY_BETA_CHANNEL, enabled.toString()) }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        _analyticsEnabled.value = enabled
        if (enabled) _analyticsConsent.value = _analyticsConsent.value ?: true
        AppAnalytics.setEventsEnabled(enabled && _analyticsConsent.value != null)
        scope.launch { platformStorage.saveString(KEY_ANALYTICS_ENABLED, enabled.toString()) }
    }

    fun setAnalyticsConsent(accepted: Boolean) {
        _analyticsConsent.value = accepted
        _analyticsEnabled.value = accepted
        AppAnalytics.setEventsEnabled(accepted)
        scope.launch {
            platformStorage.saveString(KEY_ANALYTICS_CONSENT, accepted.toString())
            platformStorage.saveString(KEY_ANALYTICS_ENABLED, accepted.toString())
        }
    }

    fun setTasksEnabled(enabled: Boolean) {
        _tasksEnabled.value = enabled
        scope.launch { platformStorage.saveString(KEY_TASKS_ENABLED, enabled.toString()) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        val changed = _notificationsEnabled.value != enabled
        _notificationsEnabled.value = enabled
        if (enabled) {
            com.jetbrains.kmpapp.data.notifications.NotificationsManager.requestAuthorization()
        }
        scope.launch { platformStorage.saveString(KEY_NOTIFICATIONS_ENABLED, enabled.toString()) }
        if (changed) {
            AppAnalytics.logEvent(AnalyticsEvents.SETTINGS_NOTIFICATIONS_CHANGED, mapOf(
                "enabled" to enabled.toString(),
                "minutes_before" to _notifyMinutesBefore.value.toString()
            ))
        }
    }

    fun setNotifyMinutesBefore(minutes: Int) {
        _notifyMinutesBefore.value = minutes.coerceIn(1, 120)
        scope.launch { platformStorage.saveString(KEY_NOTIFY_MINUTES_BEFORE, _notifyMinutesBefore.value.toString()) }
    }

    /** «Пропустить» в диалоге обновления: эта версия больше не предлагается. */
    fun setSkippedUpdateVersion(version: String?) {
        _skippedUpdateVersion.value = version
        scope.launch {
            if (version == null) platformStorage.remove(KEY_SKIPPED_UPDATE_VERSION)
            else platformStorage.saveString(KEY_SKIPPED_UPDATE_VERSION, version)
        }
    }

    fun setSakuraThemeExclusive(enabled: Boolean) {
        setThemeOverlay(if (enabled) ThemeOverlay.SAKURA else ThemeOverlay.NONE)
    }

    private fun loadThemeOverlay(): ThemeOverlay {
        val stored = platformStorage.getString(KEY_THEME_OVERLAY)
            ?.let { runCatching { ThemeOverlay.valueOf(it) }.getOrNull() }
        if (stored != null) return stored
        return when {
            platformStorage.getString(KEY_MATRIX_THEME)?.toBooleanStrictOrNull() == true -> ThemeOverlay.MATRIX
            platformStorage.getString(KEY_CYBERPUNK_THEME)?.toBooleanStrictOrNull() == true -> ThemeOverlay.CYBERPUNK
            platformStorage.getString(KEY_SAKURA_THEME)?.toBooleanStrictOrNull() == true -> ThemeOverlay.SAKURA
            else -> ThemeOverlay.NONE
        }
    }

    fun resetAllData() {
        val cheatsAgreedBefore = _cheatsAgreed.value
        val cheatsBlockedBefore = _cheatsBlocked.value
        val betaChannelBefore = _betaChannel.value
        val analyticsEnabledBefore = _analyticsEnabled.value
        val analyticsConsentBefore = _analyticsConsent.value
        val notificationsEnabledBefore = _notificationsEnabled.value
        val notifyMinutesBeforeBefore = _notifyMinutesBefore.value
        val tasksEnabledBefore = _tasksEnabled.value
        platformStorage.clearAll()
        applySemester(null)
        _showEmptyLessons.value = true
        _showLessonProgress.value = true
        _showEmptyLessonProgress.value = true
        _showBreakProgress.value = true
        _calendarCollapsed.value = false
        _calendarSwipeCollapse.value = false
        _hideAdditionalLessons.value = false
        _autoScrollToCurrentLesson.value = true
        _showAbbreviatedNames.value = false
        _themeMode.value = ThemeMode.SYSTEM
        _themeOverlay.value = ThemeOverlay.NONE
        _cheatsAgreed.value = cheatsAgreedBefore
        _cheatsBlocked.value = cheatsBlockedBefore
        _betaChannel.value = betaChannelBefore
        _analyticsEnabled.value = analyticsEnabledBefore
        _analyticsConsent.value = analyticsConsentBefore
        _notificationsEnabled.value = notificationsEnabledBefore
        _notifyMinutesBefore.value = notifyMinutesBeforeBefore
        _tasksEnabled.value = tasksEnabledBefore
        _skippedUpdateVersion.value = null
        scope.launch {
            if (cheatsAgreedBefore == null) platformStorage.remove(KEY_CHEATS_AGREED)
            else platformStorage.saveString(KEY_CHEATS_AGREED, cheatsAgreedBefore.toString())
            platformStorage.saveString(KEY_CHEATS_BLOCKED, cheatsBlockedBefore.toString())
            platformStorage.saveString(KEY_BETA_CHANNEL, betaChannelBefore.toString())
            platformStorage.saveString(KEY_ANALYTICS_ENABLED, analyticsEnabledBefore.toString())
            if (analyticsConsentBefore == null) platformStorage.remove(KEY_ANALYTICS_CONSENT)
            else platformStorage.saveString(KEY_ANALYTICS_CONSENT, analyticsConsentBefore.toString())
            platformStorage.saveString(KEY_NOTIFICATIONS_ENABLED, notificationsEnabledBefore.toString())
            platformStorage.saveString(KEY_NOTIFY_MINUTES_BEFORE, notifyMinutesBeforeBefore.toString())
            platformStorage.saveString(KEY_TASKS_ENABLED, tasksEnabledBefore.toString())
        }
    }

    fun getStorageStats(): com.jetbrains.kmpapp.data.model.StorageStats {
        return try {
            val configStr = platformStorage.getString(KEY_SEMESTER_CONFIG)
            val semesterBytes = configStr?.encodeToByteArray()?.size?.toLong() ?: 0L

            var settingsBytes = 0L
            platformStorage.getString(KEY_SHOW_EMPTY_LESSONS)?.let { settingsBytes += it.encodeToByteArray().size }
            platformStorage.getString(KEY_APP_THEME)?.let { settingsBytes += it.encodeToByteArray().size }

            com.jetbrains.kmpapp.data.model.StorageStats(
                schedulesSizeBytes = semesterBytes,
                schedulesCount = if (_semesterConfig.value != null) 1 else 0,
                lessonsCount = _semesterLessons.value.size,
                targetsSizeBytes = 0L,
                targetsCount = 0,
                settingsSizeBytes = settingsBytes,
                totalSizeBytes = semesterBytes + settingsBytes
            )
        } catch (_: Throwable) {
            com.jetbrains.kmpapp.data.model.StorageStats()
        }
    }

    companion object {
        private const val KEY_SEMESTER_CONFIG = "uschedule_semester_config"
        private const val KEY_SHOW_EMPTY_LESSONS = "uschedule_show_empty_lessons"
        private const val KEY_SHOW_LESSON_PROGRESS = "uschedule_show_lesson_progress"
        private const val KEY_SHOW_EMPTY_LESSON_PROGRESS = "uschedule_show_empty_lesson_progress"
        private const val KEY_SHOW_BREAK_PROGRESS = "uschedule_show_break_progress"
        private const val KEY_CALENDAR_COLLAPSED = "uschedule_calendar_collapsed"
        private const val KEY_CALENDAR_SWIPE_COLLAPSE = "uschedule_calendar_swipecollapse"
        private const val KEY_HIDE_ADDITIONAL_LESSONS = "uschedule_hide_additional_lessons"
        private const val KEY_AUTO_SCROLL_CURRENT_LESSON = "uschedule_auto_scroll_current_lesson"
        private const val KEY_SHOW_ABBREVIATED_NAMES = "uschedule_show_abbreviated_names"
        private const val KEY_APP_THEME = "uschedule_app_theme"
        private const val KEY_TASKS_ENABLED = "uschedule_tasks_enabled"
        private const val KEY_SAKURA_THEME = "uschedule_sakura_theme_secret"
        private const val KEY_CYBERPUNK_THEME = "uschedule_cyberpunk_theme_secret"
        private const val KEY_MATRIX_THEME = "uschedule_matrix_theme_secret"
        private const val KEY_THEME_OVERLAY = "uschedule_theme_overlay"
        private const val KEY_CHEATS_AGREED = "uschedule_cheats_agreed"
        private const val KEY_CHEATS_BLOCKED = "uschedule_cheats_blocked"
        private const val KEY_BETA_CHANNEL = "uschedule_beta_channel"
        private const val KEY_ANALYTICS_ENABLED = "uschedule_analytics_enabled"
        private const val KEY_ANALYTICS_CONSENT = "uschedule_analytics_consent"
        private const val KEY_NOTIFICATIONS_ENABLED = "uschedule_notifications_enabled"
        private const val KEY_NOTIFY_MINUTES_BEFORE = "uschedule_notify_minutes_before"
        private const val KEY_SKIPPED_UPDATE_VERSION = "uschedule_skipped_update_version"
    }
}
