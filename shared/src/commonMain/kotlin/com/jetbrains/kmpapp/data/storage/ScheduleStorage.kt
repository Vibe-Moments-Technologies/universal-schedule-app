package com.jetbrains.kmpapp.data.storage

import com.jetbrains.kmpapp.data.analytics.AnalyticsEvents
import com.jetbrains.kmpapp.data.analytics.AppAnalytics
import com.jetbrains.kmpapp.data.appicon.AppIconManager
import com.jetbrains.kmpapp.data.notifications.NotificationsManager
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.ScheduleTarget
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.screens.components.AppTab
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ScheduleStorage(
    private val platformStorage: PlatformStorage
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _savedTargets = MutableStateFlow<List<ScheduleTarget>>(emptyList())
    val savedTargets: StateFlow<List<ScheduleTarget>> = _savedTargets.asStateFlow()

    private val _selectedTarget = MutableStateFlow<ScheduleTarget?>(null)
    val selectedTarget: StateFlow<ScheduleTarget?> = _selectedTarget.asStateFlow()

    private val _cachedLessons = MutableStateFlow<Map<Int, List<Lesson>>>(emptyMap())
    val cachedLessons: StateFlow<Map<Int, List<Lesson>>> = _cachedLessons.asStateFlow()

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

    private val _dockTabs = MutableStateFlow<List<AppTab>>(DEFAULT_DOCK_TABS)
    val dockTabs: StateFlow<List<AppTab>> = _dockTabs.asStateFlow()

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

    private val _betaChannel = MutableStateFlow(false)
    val betaChannel: StateFlow<Boolean> = _betaChannel.asStateFlow()

    private val _analyticsEnabled = MutableStateFlow(true)
    val analyticsEnabled: StateFlow<Boolean> = _analyticsEnabled.asStateFlow()

    // null = согласие ещё не спрашивали: первый вход ИЛИ обновление со старой версии
    private val _analyticsConsent = MutableStateFlow<Boolean?>(null)
    val analyticsConsent: StateFlow<Boolean?> = _analyticsConsent.asStateFlow()

    private val _appIcon = MutableStateFlow(AppIconManager.ICON_DEFAULT)
    val appIcon: StateFlow<String> = _appIcon.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(false)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _notificationsTargetId = MutableStateFlow<Int?>(null)
    val notificationsTargetId: StateFlow<Int?> = _notificationsTargetId.asStateFlow()

    private val _vpnWarningEnabled = MutableStateFlow(true)
    val vpnWarningEnabled: StateFlow<Boolean> = _vpnWarningEnabled.asStateFlow()

    private val _notifyMinutesBefore = MutableStateFlow(15)
    val notifyMinutesBefore: StateFlow<Int> = _notifyMinutesBefore.asStateFlow()

    private val lastSyncTimes = mutableMapOf<Int, Long>()

    init {
        loadPersistedState()
    }

    private fun loadPersistedState() {
        try {
            loadPreferenceFlags()
            loadDockTabsSetting()
            restoreScheduleData()
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
        // До первого ответа на диалог согласия ничего не отправляем.
        AppAnalytics.setEventsEnabled(_analyticsEnabled.value && _analyticsConsent.value != null)
        // Миграция иконки: прошлые «Новая светлая/тёмная» и «Старая»
        // (AppIconClassic) слились в дефолт; неизвестные значения → дефолт.
        _appIcon.value = when (val saved = platformStorage.getString(KEY_APP_ICON)) {
            null -> AppIconManager.ICON_DEFAULT
            "AppIconNewLight", "AppIconNewDark", "AppIconClassic" -> AppIconManager.ICON_DEFAULT
            AppIconManager.ICON_DEFAULT, AppIconManager.ICON_ALT -> saved
            else -> AppIconManager.ICON_DEFAULT
        }
        _notificationsEnabled.value = loadBooleanFlag(KEY_NOTIFICATIONS_ENABLED, false)
        _notificationsTargetId.value = platformStorage.getString(KEY_NOTIFICATIONS_TARGET_ID)?.toIntOrNull()
        _vpnWarningEnabled.value = loadBooleanFlag(KEY_VPN_WARNING_ENABLED, true)
        _notifyMinutesBefore.value =
            platformStorage.getString(KEY_NOTIFY_MINUTES_BEFORE)?.toIntOrNull() ?: 15
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

    private fun loadDockTabsSetting() {
        try {
            val dockTabsStr = platformStorage.getString(KEY_DOCK_TABS)
            if (!dockTabsStr.isNullOrBlank()) {
                val loaded = dockTabsStr.split(",").mapNotNull { name ->
                    try { AppTab.valueOf(name.trim()) } catch (_: Throwable) { null }
                }
                // ponytail: раньше совпадение со старыми дефолтами принудительно
                // сбрасывалось на новый дефолт — это стирало живой выбор
                // (дока без «Аудиторий» == старый дефолт). Сохранённое доверяем.
                _dockTabs.value = sanitizeDockTabs(loaded)
            } else {
                _dockTabs.value = DEFAULT_DOCK_TABS
            }
        } catch (_: Throwable) {
            _dockTabs.value = DEFAULT_DOCK_TABS
        }
    }

    /** Цели, кэш уроков и выбранная цель. Порядок важен: кэш ДО выбранной цели. */
    private fun restoreScheduleData() {
        // Restore saved targets
        val targets: List<ScheduleTarget> = try {
            val targetsJson = platformStorage.getString(KEY_SAVED_TARGETS)
            if (!targetsJson.isNullOrBlank()) {
                try { json.decodeFromString(targetsJson) } catch (_: Throwable) { emptyList() }
            } else {
                emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
        _savedTargets.value = targets

        // IMPORTANT: Restore cached lessons for all targets BEFORE setting selected target!
        val loadedCache = mutableMapOf<Int, List<Lesson>>()
        for (target in targets) {
            try {
                val lessonsJson = platformStorage.getString(KEY_LESSONS_PREFIX + target.id)
                if (!lessonsJson.isNullOrBlank()) {
                    try {
                        val lessons: List<Lesson> = json.decodeFromString(lessonsJson)
                        loadedCache[target.id] = lessons
                    } catch (_: Throwable) {}
                }
                val syncTimeStr = platformStorage.getString(KEY_LAST_SYNC_PREFIX + target.id)
                syncTimeStr?.toLongOrNull()?.let { lastSyncTimes[target.id] = it }
            } catch (_: Throwable) {}
        }
        _cachedLessons.value = loadedCache

        // Now that cached lessons and timestamps are ready, restore selected target!
        try {
            val activeIdStr = platformStorage.getString(KEY_SELECTED_TARGET_ID)
            val activeId = activeIdStr?.toIntOrNull()
            val selected = targets.firstOrNull { it.id == activeId } ?: targets.firstOrNull()
            _selectedTarget.value = selected
            selected?.let {
                com.jetbrains.kmpapp.data.model.SemesterWeeks.set(loadWeekMarkers(it.id))
            }
        } catch (_: Throwable) {}
    }

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
        // Ручное включение тумблера = согласие; до ответа на диалог ничего не уходит
        if (enabled) _analyticsConsent.value = _analyticsConsent.value ?: true
        AppAnalytics.setEventsEnabled(enabled && _analyticsConsent.value != null)
        scope.launch { platformStorage.saveString(KEY_ANALYTICS_ENABLED, enabled.toString()) }
    }

    /** Ответ на диалог первого запуска: сразу задаёт и согласие, и тумблер. */
    fun setAnalyticsConsent(accepted: Boolean) {
        _analyticsConsent.value = accepted
        _analyticsEnabled.value = accepted
        AppAnalytics.setEventsEnabled(accepted)
        scope.launch {
            platformStorage.saveString(KEY_ANALYTICS_CONSENT, accepted.toString())
            platformStorage.saveString(KEY_ANALYTICS_ENABLED, accepted.toString())
        }
    }

    /** Выбор иконки приложения; применяется немедленно (iOS), хранится для UI. */
    fun setAppIcon(name: String) {
        val changed = _appIcon.value != name
        _appIcon.value = name
        AppIconManager.apply(name)
        scope.launch { platformStorage.saveString(KEY_APP_ICON, name) }
        if (changed) {
            AppAnalytics.logEvent(AnalyticsEvents.SETTINGS_APP_ICON_CHANGED, mapOf("icon" to name))
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        val changed = _notificationsEnabled.value != enabled
        _notificationsEnabled.value = enabled
        if (enabled) {
            if (_notificationsTargetId.value == null) {
                _notificationsTargetId.value = _selectedTarget.value?.id
                persistNotificationsTargetId(_notificationsTargetId.value)
            }
            NotificationsManager.requestAuthorization()
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

    fun setNotificationsTargetId(targetId: Int?) {
        _notificationsTargetId.value = targetId
        persistNotificationsTargetId(targetId)
    }

    fun setVpnWarningEnabled(enabled: Boolean) {
        _vpnWarningEnabled.value = enabled
        scope.launch { platformStorage.saveString(KEY_VPN_WARNING_ENABLED, enabled.toString()) }
    }

    private fun persistNotificationsTargetId(targetId: Int?) {
        scope.launch {
            if (targetId == null) platformStorage.remove(KEY_NOTIFICATIONS_TARGET_ID)
            else platformStorage.saveString(KEY_NOTIFICATIONS_TARGET_ID, targetId.toString())
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

    fun setDockTabs(tabs: List<AppTab>) {
        val sanitized = sanitizeDockTabs(tabs)
        _dockTabs.value = sanitized
        scope.launch {
            try {
                platformStorage.saveString(KEY_DOCK_TABS, sanitized.joinToString(",") { it.name })
            } catch (e: Exception) {
                println("Failed to persist dock tabs: ${e.message}")
            }
        }
    }

    private fun sanitizeDockTabs(tabs: List<AppTab>): List<AppTab> {
        return try {
            val middle = tabs.filter { !it.isFixed }.distinct().take(3)
            listOf(AppTab.SCHEDULE) + middle + listOf(AppTab.OTHER)
        } catch (_: Throwable) {
            DEFAULT_DOCK_TABS
        }
    }



    fun addTarget(target: ScheduleTarget) {
        val wasNew = _savedTargets.value.none { it.id == target.id }
        _savedTargets.update { list ->
            if (list.any { it.id == target.id }) list
            else list + target
        }
        selectTarget(target)
        if (_notificationsEnabled.value && _notificationsTargetId.value == null) {
            _notificationsTargetId.value = target.id
            persistNotificationsTargetId(target.id)
        }
        persistTargets()
        // Аналитика: только тип (GROUP/TEACHER/AUDITORIUM) и количество —
        // ни id, ни название группы/преподавателя наружу не уходят.
        if (wasNew) {
            AppAnalytics.logEvent(
                AnalyticsEvents.SCHEDULE_TARGET_ADDED,
                mapOf(
                    "type" to target.type.name,
                    "count" to _savedTargets.value.size.toString()
                )
            )
        }
    }

    fun removeTarget(targetId: Int) {
        val removed = _savedTargets.value.firstOrNull { it.id == targetId }
        _savedTargets.update { list -> list.filter { it.id != targetId } }
        if (_selectedTarget.value?.id == targetId) {
            _selectedTarget.value = _savedTargets.value.firstOrNull()
            persistSelectedTargetId(_selectedTarget.value?.id)
        }
        _cachedLessons.update { map -> map - targetId }
        if (_notificationsTargetId.value == targetId) {
            val fallback = _savedTargets.value.firstOrNull()?.id
            _notificationsTargetId.value = fallback
            persistNotificationsTargetId(fallback)
        }
        lastSyncTimes.remove(targetId)
        platformStorage.remove(KEY_LESSONS_PREFIX + targetId)
        platformStorage.remove(KEY_LAST_SYNC_PREFIX + targetId)
        platformStorage.remove(KEY_WEEK_MARKERS_PREFIX + targetId)
        _selectedTarget.value?.let {
            com.jetbrains.kmpapp.data.model.SemesterWeeks.set(loadWeekMarkers(it.id))
        }
        persistTargets()
        if (removed != null) {
            AppAnalytics.logEvent(
                AnalyticsEvents.SCHEDULE_TARGET_REMOVED,
                mapOf(
                    "type" to removed.type.name,
                    "count" to _savedTargets.value.size.toString()
                )
            )
        }
    }

    fun selectTarget(target: ScheduleTarget?) {
        _selectedTarget.value = target
        persistSelectedTargetId(target?.id)
        com.jetbrains.kmpapp.data.model.SemesterWeeks.set(
            target?.let { loadWeekMarkers(it.id) } ?: emptyList()
        )
    }

    fun selectTargetById(targetId: Int) {
        val target = _savedTargets.value.firstOrNull { it.id == targetId }
        if (target != null) {
            selectTarget(target)
        }
    }

    fun saveLessons(targetId: Int, lessons: List<Lesson>) {
        _cachedLessons.update { map ->
            map + (targetId to lessons)
        }
        scope.launch {
            try {
                platformStorage.saveString(KEY_LESSONS_PREFIX + targetId, json.encodeToString(lessons))
            } catch (e: Exception) {
                println("Failed to persist lessons for $targetId: ${e.message}")
            }
        }
    }

    /** Маркеры недель из iCal-фида: активная цель определяет нумерацию в UI. */
    fun saveWeekMarkers(targetId: Int, markers: List<com.jetbrains.kmpapp.data.model.WeekMarker>) {
        if (markers.isEmpty()) return
        scope.launch {
            try {
                platformStorage.saveString(KEY_WEEK_MARKERS_PREFIX + targetId, json.encodeToString(markers))
            } catch (e: Exception) {
                println("Failed to persist week markers for $targetId: ${e.message}")
            }
        }
        if (_selectedTarget.value?.id == targetId) {
            com.jetbrains.kmpapp.data.model.SemesterWeeks.set(markers)
        }
    }

    fun loadWeekMarkers(targetId: Int): List<com.jetbrains.kmpapp.data.model.WeekMarker> = try {
        val s = platformStorage.getString(KEY_WEEK_MARKERS_PREFIX + targetId)
        if (s.isNullOrBlank()) emptyList()
        else try { json.decodeFromString(s) } catch (_: Throwable) { emptyList() }
    } catch (_: Throwable) {
        emptyList()
    }

    fun getLessons(targetId: Int): List<Lesson>? {
        return _cachedLessons.value[targetId]
    }

    fun getLastSyncTime(targetId: Int): Long {
        val cached = lastSyncTimes[targetId]
        if (cached != null) return cached
        val str = platformStorage.getString(KEY_LAST_SYNC_PREFIX + targetId)
        val time = str?.toLongOrNull() ?: 0L
        lastSyncTimes[targetId] = time
        return time
    }

    fun setLastSyncTime(targetId: Int, time: Long) {
        lastSyncTimes[targetId] = time
        scope.launch {
            try {
                platformStorage.saveString(KEY_LAST_SYNC_PREFIX + targetId, time.toString())
            } catch (e: Exception) {
                println("Failed to persist lastSyncTime for $targetId: ${e.message}")
            }
        }
    }

    fun clearCache() {
        _cachedLessons.value = emptyMap()
        lastSyncTimes.clear()
        for (target in _savedTargets.value) {
            platformStorage.remove(KEY_LESSONS_PREFIX + target.id)
            platformStorage.remove(KEY_LAST_SYNC_PREFIX + target.id)
            platformStorage.remove(KEY_WEEK_MARKERS_PREFIX + target.id)
        }
        com.jetbrains.kmpapp.data.model.SemesterWeeks.set(emptyList())
    }

    fun resetAllData() {
        val cheatsAgreedBefore = _cheatsAgreed.value
        val cheatsBlockedBefore = _cheatsBlocked.value
        val betaChannelBefore = _betaChannel.value
        val analyticsEnabledBefore = _analyticsEnabled.value
        val analyticsConsentBefore = _analyticsConsent.value
        val notificationsEnabledBefore = _notificationsEnabled.value
        val notifyMinutesBeforeBefore = _notifyMinutesBefore.value
        platformStorage.clearAll()
        _savedTargets.value = emptyList()
        _selectedTarget.value = null
        _cachedLessons.value = emptyMap()
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
        _dockTabs.value = DEFAULT_DOCK_TABS
        _themeOverlay.value = ThemeOverlay.NONE
        _cheatsAgreed.value = cheatsAgreedBefore
        _cheatsBlocked.value = cheatsBlockedBefore
        _betaChannel.value = betaChannelBefore
        _analyticsEnabled.value = analyticsEnabledBefore
        _analyticsConsent.value = analyticsConsentBefore
        _notificationsEnabled.value = notificationsEnabledBefore
        _notificationsTargetId.value = null
        _vpnWarningEnabled.value = true
        _notifyMinutesBefore.value = notifyMinutesBeforeBefore
        lastSyncTimes.clear()
        com.jetbrains.kmpapp.data.model.SemesterWeeks.set(emptyList())
        scope.launch {
            if (cheatsAgreedBefore == null) platformStorage.remove(KEY_CHEATS_AGREED)
            else platformStorage.saveString(KEY_CHEATS_AGREED, cheatsAgreedBefore.toString())
            platformStorage.saveString(KEY_CHEATS_BLOCKED, cheatsBlockedBefore.toString())
            platformStorage.saveString(KEY_BETA_CHANNEL, betaChannelBefore.toString())
            platformStorage.saveString(KEY_ANALYTICS_ENABLED, analyticsEnabledBefore.toString())
            if (analyticsConsentBefore == null) platformStorage.remove(KEY_ANALYTICS_CONSENT)
            else platformStorage.saveString(KEY_ANALYTICS_CONSENT, analyticsConsentBefore.toString())
            platformStorage.saveString(KEY_NOTIFICATIONS_ENABLED, notificationsEnabledBefore.toString())
            platformStorage.remove(KEY_NOTIFICATIONS_TARGET_ID)
            platformStorage.saveString(KEY_VPN_WARNING_ENABLED, true.toString())
            platformStorage.saveString(KEY_NOTIFY_MINUTES_BEFORE, notifyMinutesBeforeBefore.toString())
        }
    }

    fun getStorageStats(): com.jetbrains.kmpapp.data.model.StorageStats {
        return try {
            var schedulesBytes = 0L
            var totalLessons = 0
            for ((_, lessons) in _cachedLessons.value) {
                totalLessons += lessons.size
            }
            for (target in _savedTargets.value) {
                val str = platformStorage.getString(KEY_LESSONS_PREFIX + target.id)
                if (str != null) {
                    schedulesBytes += str.encodeToByteArray().size
                }
            }

            val targetsStr = platformStorage.getString(KEY_SAVED_TARGETS)
            val targetsBytes = targetsStr?.encodeToByteArray()?.size?.toLong() ?: 0L

            var settingsBytes = 0L
            platformStorage.getString(KEY_SELECTED_TARGET_ID)?.let { settingsBytes += it.encodeToByteArray().size }
            platformStorage.getString(KEY_SHOW_EMPTY_LESSONS)?.let { settingsBytes += it.encodeToByteArray().size }
            platformStorage.getString(KEY_APP_THEME)?.let { settingsBytes += it.encodeToByteArray().size }

            val total = schedulesBytes + targetsBytes + settingsBytes

            com.jetbrains.kmpapp.data.model.StorageStats(
                schedulesSizeBytes = schedulesBytes,
                schedulesCount = _cachedLessons.value.size,
                lessonsCount = totalLessons,
                targetsSizeBytes = targetsBytes,
                targetsCount = _savedTargets.value.size,
                settingsSizeBytes = settingsBytes,
                totalSizeBytes = total
            )
        } catch (_: Throwable) {
            com.jetbrains.kmpapp.data.model.StorageStats()
        }
    }

    private fun persistTargets() {
        scope.launch {
            try {
                platformStorage.saveString(KEY_SAVED_TARGETS, json.encodeToString(_savedTargets.value))
            } catch (e: Exception) {
                println("Failed to persist targets: ${e.message}")
            }
        }
    }

    private fun persistSelectedTargetId(id: Int?) {
        scope.launch {
            if (id != null) {
                platformStorage.saveString(KEY_SELECTED_TARGET_ID, id.toString())
            } else {
                platformStorage.remove(KEY_SELECTED_TARGET_ID)
            }
        }
    }

    companion object {
        private const val KEY_SAVED_TARGETS = "krasava_saved_targets"
        private const val KEY_SELECTED_TARGET_ID = "krasava_selected_target_id"
        private const val KEY_LESSONS_PREFIX = "krasava_lessons_"
        private const val KEY_WEEK_MARKERS_PREFIX = "krasava_week_markers_"
        private const val KEY_LAST_SYNC_PREFIX = "krasava_last_sync_"
        private const val KEY_SHOW_EMPTY_LESSONS = "krasava_show_empty_lessons"
        private const val KEY_SHOW_LESSON_PROGRESS = "krasava_show_lesson_progress"
        private const val KEY_SHOW_EMPTY_LESSON_PROGRESS = "krasava_show_empty_lesson_progress"
        private const val KEY_SHOW_BREAK_PROGRESS = "krasava_show_break_progress"
        private const val KEY_CALENDAR_COLLAPSED = "krasava_calendar_collapsed"
        private const val KEY_CALENDAR_SWIPE_COLLAPSE = "krasava_calendar_swipe_collapse"
        private const val KEY_HIDE_ADDITIONAL_LESSONS = "krasava_hide_additional_lessons"
        private const val KEY_AUTO_SCROLL_CURRENT_LESSON = "krasava_auto_scroll_current_lesson"
        private const val KEY_SHOW_ABBREVIATED_NAMES = "krasava_show_abbreviated_names"
        private const val KEY_APP_THEME = "krasava_app_theme"
        private const val KEY_DOCK_TABS = "krasava_dock_tabs_order"
        private const val KEY_SAKURA_THEME = "krasava_sakura_theme_secret"
        private const val KEY_CYBERPUNK_THEME = "krasava_cyberpunk_theme_secret"
        private const val KEY_MATRIX_THEME = "krasava_matrix_theme_secret"
        private const val KEY_THEME_OVERLAY = "krasava_theme_overlay"
        private const val KEY_CHEATS_AGREED = "krasava_cheats_agreed"
        private const val KEY_CHEATS_BLOCKED = "krasava_cheats_blocked"
        private const val KEY_BETA_CHANNEL = "krasava_beta_channel"
        private const val KEY_ANALYTICS_ENABLED = "krasava_analytics_enabled"
        private const val KEY_ANALYTICS_CONSENT = "krasava_analytics_consent"
        private const val KEY_APP_ICON = "krasava_app_icon"
        private const val KEY_NOTIFICATIONS_ENABLED = "krasava_notifications_enabled"
        private const val KEY_NOTIFICATIONS_TARGET_ID = "krasava_notifications_target_id"
        private const val KEY_VPN_WARNING_ENABLED = "krasava_vpn_warning_enabled"
        private const val KEY_NOTIFY_MINUTES_BEFORE = "krasava_notify_minutes_before"
        // Дефолт дока для НОВЫХ установок: расписание, задачи, другое.
        val DEFAULT_DOCK_TABS = listOf(
            AppTab.SCHEDULE,
            AppTab.TASKS,
            AppTab.OTHER
        )
    }
}


