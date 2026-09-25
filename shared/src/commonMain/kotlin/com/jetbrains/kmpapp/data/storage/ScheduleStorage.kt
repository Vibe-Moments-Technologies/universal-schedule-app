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

    private val _autoScrollToCurrentLesson = MutableStateFlow<Boolean>(true)
    val autoScrollToCurrentLesson: StateFlow<Boolean> = _autoScrollToCurrentLesson.asStateFlow()

    private val _showAbbreviatedNames = MutableStateFlow<Boolean>(false)
    val showAbbreviatedNames: StateFlow<Boolean> = _showAbbreviatedNames.asStateFlow()

    private val _themeMode = MutableStateFlow<ThemeMode>(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Вкладка «Задачи» в доке: единственная настраиваемая вкладка. По умолчанию выключена. */
    private val _tasksEnabled = MutableStateFlow(false)
    val tasksEnabled: StateFlow<Boolean> = _tasksEnabled.asStateFlow()

    private val _themeOverlay = MutableStateFlow(ThemeOverlay.NONE)
    val themeOverlay: StateFlow<ThemeOverlay> = _themeOverlay.asStateFlow()

    val isSakuraTheme: StateFlow<Boolean> = themeOverlay.map { it == ThemeOverlay.SAKURA }
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
        _autoScrollToCurrentLesson.value = loadBooleanFlag(KEY_AUTO_SCROLL_CURRENT_LESSON, true)
        _showAbbreviatedNames.value = loadBooleanFlag(KEY_SHOW_ABBREVIATED_NAMES, false)
        _themeOverlay.value = loadThemeOverlay()
        _cheatsAgreed.value = nullableFlag(KEY_CHEATS_AGREED)
        _cheatsBlocked.value = loadBooleanFlag(KEY_CHEATS_BLOCKED, false)
        _betaChannel.value = loadBooleanFlag(KEY_BETA_CHANNEL, false)
        _analyticsEnabled.value = loadBooleanFlag(KEY_ANALYTICS_ENABLED, true)
        // Отправка включена по умолчанию: приветственный диалог согласия
        // скрыт, согласие считаем данным; тумблер в настройках выключает.
        _analyticsConsent.value = nullableFlag(KEY_ANALYTICS_CONSENT) ?: true
        AppAnalytics.setEventsEnabled(_analyticsEnabled.value)
        _tasksEnabled.value = loadBooleanFlag(KEY_TASKS_ENABLED, false)
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

    /** Единая персистенция булева флага: память сразу, диск асинхронно. */
    private fun persistFlag(flow: MutableStateFlow<Boolean>, key: String, value: Boolean) {
        flow.value = value
        scope.launch {
            try {
                platformStorage.saveString(key, value.toString())
            } catch (e: Exception) {
                println("Failed to persist $key: ${e.message}")
            }
        }
    }

    fun setShowEmptyLessons(enabled: Boolean) = persistFlag(_showEmptyLessons, KEY_SHOW_EMPTY_LESSONS, enabled)

    fun setShowLessonProgress(enabled: Boolean) = persistFlag(_showLessonProgress, KEY_SHOW_LESSON_PROGRESS, enabled)

    fun setShowEmptyLessonProgress(enabled: Boolean) =
        persistFlag(_showEmptyLessonProgress, KEY_SHOW_EMPTY_LESSON_PROGRESS, enabled)

    fun setShowBreakProgress(enabled: Boolean) = persistFlag(_showBreakProgress, KEY_SHOW_BREAK_PROGRESS, enabled)

    fun setAutoScrollToCurrentLesson(enabled: Boolean) =
        persistFlag(_autoScrollToCurrentLesson, KEY_AUTO_SCROLL_CURRENT_LESSON, enabled)

    fun setShowAbbreviatedNames(enabled: Boolean) =
        persistFlag(_showAbbreviatedNames, KEY_SHOW_ABBREVIATED_NAMES, enabled)

    fun setThemeOverlay(overlay: ThemeOverlay) {
        val changed = _themeOverlay.value != overlay
        _themeOverlay.value = overlay
        scope.launch {
            try {
                platformStorage.saveString(KEY_THEME_OVERLAY, overlay.name)
            } catch (e: Exception) {
                println("Failed to persist theme overlay: ${e.message}")
            }
        }
        if (changed) AppAnalytics.logEvent(AnalyticsEvents.SETTINGS_THEME_OVERLAY_SET, mapOf("overlay" to overlay.name))
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

    fun setCheatsBlocked(blocked: Boolean) = persistFlag(_cheatsBlocked, KEY_CHEATS_BLOCKED, blocked)

    fun setBetaChannel(enabled: Boolean) = persistFlag(_betaChannel, KEY_BETA_CHANNEL, enabled)

    fun setAnalyticsEnabled(enabled: Boolean) {
        _analyticsEnabled.value = enabled
        _analyticsConsent.value = enabled
        AppAnalytics.setEventsEnabled(enabled)
        scope.launch {
            platformStorage.saveString(KEY_ANALYTICS_ENABLED, enabled.toString())
            platformStorage.saveString(KEY_ANALYTICS_CONSENT, enabled.toString())
        }
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

    fun setTasksEnabled(enabled: Boolean) = persistFlag(_tasksEnabled, KEY_TASKS_ENABLED, enabled)

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

    private fun loadThemeOverlay(): ThemeOverlay =
        platformStorage.getString(KEY_THEME_OVERLAY)
            ?.let { runCatching { ThemeOverlay.valueOf(it) }.getOrNull() }
            ?: ThemeOverlay.NONE

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

            com.jetbrains.kmpapp.data.model.StorageStats(
                schedulesCount = if (_semesterConfig.value != null) 1 else 0,
                lessonsCount = _semesterLessons.value.size,
                totalSizeBytes = semesterBytes
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
        private const val KEY_AUTO_SCROLL_CURRENT_LESSON = "uschedule_auto_scroll_current_lesson"
        private const val KEY_SHOW_ABBREVIATED_NAMES = "uschedule_show_abbreviated_names"
        private const val KEY_APP_THEME = "uschedule_app_theme"
        private const val KEY_TASKS_ENABLED = "uschedule_tasks_enabled"
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
