package com.jetbrains.kmpapp.data.storage

import com.jetbrains.kmpapp.data.analytics.AppDiagnostics
import com.jetbrains.kmpapp.data.appicon.AppIconManager
import com.jetbrains.kmpapp.data.notifications.NotificationsManager
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.CalendarExpandState
import com.jetbrains.kmpapp.data.model.LessonColor
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.ScheduleTarget
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.screens.components.AppTab
import com.jetbrains.kmpapp.theme.CyberpunkVariant
import com.jetbrains.kmpapp.theme.ThemeOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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

    /**
     * Ступень раскрытия ленты календаря (issue #3): неделя → две недели →
     * месяц. Свайп вниз по календарику раскрывает, вверх — сворачивает.
     *
     * Свёрнутость ([calendarCollapsed]) и раскрытие — независимые оси:
     * свернуть можно из любой ступени, а раскрывать — из свёрнутого тоже
     * (жест живёт на бейджe дня в шапке не для всех, поэтому разворот из
     * свёрнутого состояния возвращает ленту на последнюю ступень).
     */
    private val _calendarExpandState = MutableStateFlow(CalendarExpandState.WEEK)
    val calendarExpandState: StateFlow<CalendarExpandState> = _calendarExpandState.asStateFlow()

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

    /** Док скрыт целиком: «Другое» открывается шестерёнкой на странице расписания. */
    private val _dockHidden = MutableStateFlow(false)
    val dockHidden: StateFlow<Boolean> = _dockHidden.asStateFlow()

    private val _themeOverlay = MutableStateFlow(ThemeOverlay.NONE)
    val themeOverlay: StateFlow<ThemeOverlay> = _themeOverlay.asStateFlow()

    val isSakuraTheme: StateFlow<Boolean> = themeOverlay.map { it == ThemeOverlay.SAKURA }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val isKawaiiTheme: StateFlow<Boolean> = themeOverlay.map { it == ThemeOverlay.KAWAII }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val isCyberpunkTheme: StateFlow<Boolean> = themeOverlay.map { it == ThemeOverlay.CYBERPUNK }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val isMatrixTheme: StateFlow<Boolean> = themeOverlay.map { it == ThemeOverlay.MATRIX }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val isHalloweenTheme: StateFlow<Boolean> = themeOverlay.map { it == ThemeOverlay.HALLOWEEN }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Темы, найденные пользователем и поэтому доступные в списке
     * «Тематические темы». Сакура там всегда (доступна по умолчанию),
     * Хэллоуин — сезонный и управляется отдельно, поэтому в множестве
     * живут только квестовые: Кавай, Киберпанк, Матрица.
     *
     * Разблокировка сохраняется навсегда: повторно проходить квест не нужно.
     * Исключение — Матрица: при её потере (нарушение договора о читах)
     * тема исчезает и из списка.
     */
    private val _unlockedThemes = MutableStateFlow(setOf<ThemeOverlay>())
    val unlockedThemes: StateFlow<Set<ThemeOverlay>> = _unlockedThemes.asStateFlow()

    /**
     * Пользовательские цвета типов занятий: только переопределённые типы.
     * Пустая карта — все цвета стандартные. Одинаковые цвета для разных типов
     * разрешены: это осознанный выбор пользователя, а не ошибка.
     */
    private val _lessonTypeColors = MutableStateFlow(emptyMap<LessonType, LessonColor>())
    val lessonTypeColors: StateFlow<Map<LessonType, LessonColor>> = _lessonTypeColors.asStateFlow()

    /**
     * Вариант палитры темы «Киберпанк»: новая (жёлтый/циан) или старая
     * (маджента/циан).
     *
     * Хранится отдельно от [themeOverlay] нарочно: это свойство самой темы,
     * а не состояния «включена сейчас». Пользователь выбирает вариант один раз,
     * потом переключается на Матрицу и обратно — выбор остаётся его.
     */
    private val _cyberpunkVariant = MutableStateFlow(CyberpunkVariant.DEFAULT)
    val cyberpunkVariant: StateFlow<CyberpunkVariant> = _cyberpunkVariant.asStateFlow()

    /**
     * Год сезона, от которого пользователь отказался вручную.
     *
     * Хэллоуин включается автоматически 28 октября — 3 ноября, и одного
     * выключения оверлея мало: правило включило бы его снова при следующей
     * рекомпозиции. Поэтому «выключить» = запомнить отказ на текущий сезон.
     * Смена года сбрасывает отказ естественным образом, отдельной чистки не
     * нужно.
     */
    private val _halloweenDismissedYear = MutableStateFlow<Int?>(null)

    /**
     * Текущая дата как поток: сезонные правила должны переключаться сами,
     * даже если приложение не закрывали на ночь. Тик раз в 15 минут —
     * компромисс между точностью границы сезона и лишними пересчётами;
     * [kotlinx.coroutines.flow.distinctUntilChanged] гасит повторы, так что
     * дальше уходит только реальная смена суток.
     */
    private val todayFlow: StateFlow<kotlinx.datetime.LocalDate> =
        flow {
            while (true) {
                emit(DateUtils.today())
                delay(15 * 60 * 1000L)
            }
        }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, DateUtils.today())

    /**
     * Показывать/включать ли Хэллоуин прямо сейчас (оформление приложения).
     *
     * Сезон (28 октября — 3 ноября) включает тему сам, ручное включение вне
     * сезона — тоже. Отказ текущего сезона перебивает только автоматику:
     * осознанно включённый тумблер сильнее отказа (он и снимает отказ).
     */
    val isHalloweenActive: StateFlow<Boolean> =
        combine(themeOverlay, _halloweenDismissedYear, todayFlow) { overlay, dismissedYear, today ->
            when {
                overlay == ThemeOverlay.HALLOWEEN -> true
                DateUtils.isHalloweenSeason(today) && dismissedYear != today.year -> true
                else -> false
            }
        }.stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Есть ли строка Хэллоуина в «Тематических темах».
     *
     * Шире [isHalloweenActive] намеренно: пока сезон идёт, строка остаётся,
     * даже если тему уже выключили — иначе выключить было бы можно, а вернуть
     * обратно нельзя. Сезон кончился — правило гасит и тему, и строку, как и
     * просил владелец. Ручное включение вне сезона тоже держит строку
     * видимой, чтобы её было чем выключить.
     */
    val isHalloweenRowVisible: StateFlow<Boolean> =
        combine(themeOverlay, todayFlow) { overlay, today ->
            overlay == ThemeOverlay.HALLOWEEN || DateUtils.isHalloweenSeason(today)
        }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _cheatsAgreed = MutableStateFlow<Boolean?>(null)
    val cheatsAgreed: StateFlow<Boolean?> = _cheatsAgreed.asStateFlow()
    private val _cheatsBlocked = MutableStateFlow(false)
    val cheatsBlocked: StateFlow<Boolean> = _cheatsBlocked.asStateFlow()

    // Диагностика (Sentry) — opt-in из скрытого меню отладки, по умолчанию выключена.
    private val _diagnosticsEnabled = MutableStateFlow(false)
    val diagnosticsEnabled: StateFlow<Boolean> = _diagnosticsEnabled.asStateFlow()

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

    private val _askBeforeNoteDelete = MutableStateFlow(true)
    val askBeforeNoteDelete: StateFlow<Boolean> = _askBeforeNoteDelete.asStateFlow()

    private val _notePages = MutableStateFlow<List<com.jetbrains.kmpapp.data.model.NotePage>>(
        com.jetbrains.kmpapp.data.model.defaultNotePages()
    )
    val notePages: StateFlow<List<com.jetbrains.kmpapp.data.model.NotePage>> = _notePages.asStateFlow()

    private val lastSyncTimes = mutableMapOf<Int, Long>()

    init {
        loadPersistedState()
    }

    private fun loadPersistedState() {
        try {
            loadPreferenceFlags()
            loadDockTabsSetting()
            restoreScheduleData()
            loadNotePages()
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
        _calendarExpandState.value =
            CalendarExpandState.fromName(platformStorage.getString(KEY_CALENDAR_EXPAND_STATE))
        _hideAdditionalLessons.value = loadBooleanFlag(KEY_HIDE_ADDITIONAL_LESSONS, false)
        _autoScrollToCurrentLesson.value = loadBooleanFlag(KEY_AUTO_SCROLL_CURRENT_LESSON, true)
        _showAbbreviatedNames.value = loadBooleanFlag(KEY_SHOW_ABBREVIATED_NAMES, false)
        _themeOverlay.value = loadThemeOverlay()
        _unlockedThemes.value = loadUnlockedThemes()
        _lessonTypeColors.value = loadLessonTypeColors()
        _cyberpunkVariant.value =
            CyberpunkVariant.fromName(platformStorage.getString(KEY_CYBERPUNK_VARIANT))
        _halloweenDismissedYear.value =
            platformStorage.getString(KEY_HALLOWEEN_DISMISSED_YEAR)?.toIntOrNull()
        _cheatsAgreed.value = nullableFlag(KEY_CHEATS_AGREED)
        _cheatsBlocked.value = loadBooleanFlag(KEY_CHEATS_BLOCKED, false)
        _diagnosticsEnabled.value = loadBooleanFlag(KEY_DIAGNOSTICS_ENABLED, false)
        AppDiagnostics.setEnabled(_diagnosticsEnabled.value)
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
        _askBeforeNoteDelete.value = loadBooleanFlag(KEY_ASK_BEFORE_NOTE_DELETE, true)
        _dockHidden.value = loadBooleanFlag(KEY_DOCK_HIDDEN, false)
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

    private fun loadNotePages() {
        try {
            val pagesJson = platformStorage.getString(KEY_NOTES)
            val loaded: List<com.jetbrains.kmpapp.data.model.NotePage> = if (!pagesJson.isNullOrBlank()) {
                try {
                    json.decodeFromString(pagesJson)
                } catch (_: Throwable) {
                    com.jetbrains.kmpapp.data.model.defaultNotePages()
                }
            } else {
                com.jetbrains.kmpapp.data.model.defaultNotePages()
            }
            _notePages.value = loaded
        } catch (_: Throwable) {
            _notePages.value = com.jetbrains.kmpapp.data.model.defaultNotePages()
        }
    }

    /** Полная перезапись страниц конспектов; запись на диск с дебаунсом. */
    fun saveNotePages(pages: List<com.jetbrains.kmpapp.data.model.NotePage>) {
        _notePages.value = pages
        scope.launch {
            try {
                platformStorage.saveString(KEY_NOTES, json.encodeToString(pages))
            } catch (e: Exception) {
                println("Failed to persist note pages: ${e.message}")
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        scope.launch {
            try {
                platformStorage.saveString(KEY_APP_THEME, mode.name)
            } catch (e: Exception) {
                println("Failed to persist themeMode: ${e.message}")
            }
        }
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

    /**
     * Разрешено ли сворачивать ленту календаря свайпом. Жест теперь живёт
     * на всей области календарика (не на отдельной полосе), свайп вверх
     * в ступени WEEK сворачивает ленту — если это разрешено настройкой.
     */
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

    /** Установить ступень раскрытия ленты календаря. */
    fun setCalendarExpandState(state: CalendarExpandState) {
        _calendarExpandState.value = state
        scope.launch {
            try {
                platformStorage.saveString(KEY_CALENDAR_EXPAND_STATE, state.name)
            } catch (e: Exception) {
                println("Failed to persist calendarExpandState: ${e.message}")
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
        applyThemeOverlay(overlay, dismissSeason = true)
    }

    /**
     * @param dismissSeason явный выбор другой темы в сезон означает
     *   «мне не нужен Хэллоуин»: иначе сезонное правило перекрыло бы оверлей
     *   и тумблер Сакуры/Кавая в эти дни был бы мёртвым. Сброс настроек —
     *   исключение: он возвращает дефолт, а не выражает выбор пользователя,
     *   поэтому отказ там не пишется (см. [resetAllData]).
     */
    private fun applyThemeOverlay(overlay: ThemeOverlay, dismissSeason: Boolean) {
        _themeOverlay.value = overlay
        if (dismissSeason &&
            overlay != ThemeOverlay.HALLOWEEN &&
            DateUtils.isHalloweenSeason(DateUtils.today())
        ) {
            dismissHalloweenSeason()
        }
        scope.launch {
            try {
                platformStorage.saveString(KEY_THEME_OVERLAY, overlay.name)
                platformStorage.saveString(KEY_SAKURA_THEME, (overlay == ThemeOverlay.SAKURA).toString())
                platformStorage.saveString(KEY_KAWAII_THEME, (overlay == ThemeOverlay.KAWAII).toString())
                platformStorage.saveString(KEY_CYBERPUNK_THEME, (overlay == ThemeOverlay.CYBERPUNK).toString())
                platformStorage.saveString(KEY_MATRIX_THEME, (overlay == ThemeOverlay.MATRIX).toString())
                platformStorage.saveString(KEY_HALLOWEEN_THEME, (overlay == ThemeOverlay.HALLOWEEN).toString())
            } catch (e: Exception) {
                println("Failed to persist theme overlay: ${e.message}")
            }
        }
    }

    fun setCyberpunkTheme(enabled: Boolean) {
        // Активация = разблокировка: тема появляется в списке «Тематические
        // темы», чтобы квест не приходилось проходить повторно.
        if (enabled) setThemeUnlocked(ThemeOverlay.CYBERPUNK, true)
        setThemeOverlay(if (enabled) ThemeOverlay.CYBERPUNK else ThemeOverlay.NONE)
    }

    fun setMatrixTheme(enabled: Boolean) {
        if (enabled) setThemeUnlocked(ThemeOverlay.MATRIX, true)
        setThemeOverlay(if (enabled) ThemeOverlay.MATRIX else ThemeOverlay.NONE)
    }

    /** Хэллоуинская тема: в сезон (28 октября — 3 ноября) включается сама,
     *  тумблер — для проверки вне сезона.
     *
     *  Выключение внутри сезона запоминается как отказ от сезона: иначе
     *  автоматическое правило включило бы тему обратно на следующей
     *  рекомпозиции. Вне сезона отказ не пишется — иначе выключенный летом
     *  «на пробу» тумблер тихо отменил бы осенний показ. */
    fun setHalloweenTheme(enabled: Boolean) {
        if (enabled) {
            clearHalloweenSeasonDismissal()
            applyThemeOverlay(ThemeOverlay.HALLOWEEN, dismissSeason = false)
        } else {
            applyThemeOverlay(ThemeOverlay.NONE, dismissSeason = false)
            if (DateUtils.isHalloweenSeason(DateUtils.today())) dismissHalloweenSeason()
        }
    }

    /** «Больше не надо в этом сезоне»: запоминаем год, правило его уважает. */
    fun dismissHalloweenSeason() {
        val year = DateUtils.today().year
        _halloweenDismissedYear.value = year
        scope.launch {
            try {
                platformStorage.saveString(KEY_HALLOWEEN_DISMISSED_YEAR, year.toString())
            } catch (e: Exception) {
                println("Failed to persist halloween dismissal: ${e.message}")
            }
        }
    }

    /** Отказ отменён: тема снова включается по правилу (и сразу, если сезон). */
    fun clearHalloweenSeasonDismissal() {
        _halloweenDismissedYear.value = null
        scope.launch {
            try {
                platformStorage.remove(KEY_HALLOWEEN_DISMISSED_YEAR)
            } catch (e: Exception) {
                println("Failed to clear halloween dismissal: ${e.message}")
            }
        }
    }

    fun setSakuraThemeExclusive(enabled: Boolean) {
        setThemeOverlay(if (enabled) ThemeOverlay.SAKURA else ThemeOverlay.NONE)
    }

    fun setKawaiiThemeExclusive(enabled: Boolean) {
        if (enabled) setThemeUnlocked(ThemeOverlay.KAWAII, true)
        setThemeOverlay(if (enabled) ThemeOverlay.KAWAII else ThemeOverlay.NONE)
    }

    /** Назначает цвет типу занятия; одинаковые цвета для разных типов разрешены. */
    fun setLessonTypeColor(type: LessonType, color: LessonColor) {
        _lessonTypeColors.update { it + (type to color) }
        persistLessonTypeColors()
    }

    /**
     * Выбор палитры «Киберпанка». Пишется всегда, даже когда тема выключена:
     * это настройка темы, а не текущее состояние.
     */
    fun setCyberpunkVariant(variant: CyberpunkVariant) {
        _cyberpunkVariant.value = variant
        scope.launch {
            try {
                platformStorage.saveString(KEY_CYBERPUNK_VARIANT, variant.name)
            } catch (e: Exception) {
                println("Failed to persist cyberpunk variant: ${e.message}")
            }
        }
    }

    /** Возвращает все цвета типов к стандартным. */
    fun resetLessonTypeColors() {
        _lessonTypeColors.value = emptyMap()
        scope.launch {
            try {
                platformStorage.remove(KEY_LESSON_TYPE_COLORS)
            } catch (e: Exception) {
                println("Failed to reset lesson type colors: ${e.message}")
            }
        }
    }

    private fun persistLessonTypeColors() {
        val encoded = _lessonTypeColors.value.entries.joinToString(";") { (type, color) ->
            "${type.name}=${color.name}"
        }
        scope.launch {
            try {
                if (encoded.isEmpty()) platformStorage.remove(KEY_LESSON_TYPE_COLORS)
                else platformStorage.saveString(KEY_LESSON_TYPE_COLORS, encoded)
            } catch (e: Exception) {
                println("Failed to persist lesson type colors: ${e.message}")
            }
        }
    }

    /**
     * Читает переопределения цветов. Формат: "LECTURE=BLUE;LAB=ROSE".
     * Неизвестные имена типов и цветов (после удаления варианта из enum)
     * тихо пропускаются — лучше стандартный цвет, чем падение на старте.
     */
    private fun loadLessonTypeColors(): Map<LessonType, LessonColor> {
        val raw = platformStorage.getString(KEY_LESSON_TYPE_COLORS) ?: return emptyMap()
        val result = mutableMapOf<LessonType, LessonColor>()
        raw.split(';').forEach { entry ->
            val parts = entry.split('=')
            if (parts.size == 2) {
                val type = runCatching { LessonType.valueOf(parts[0]) }.getOrNull()
                val color = runCatching { LessonColor.valueOf(parts[1]) }.getOrNull()
                if (type != null && color != null) result[type] = color
            }
        }
        return result
    }

    /**
     * Разблокировка квестовой темы: добавляет или убирает её из списка
     * «Тематические темы». Сакура и Хэллоуин в списке не живут (первая
     * доступна всегда, второй — сезонный), поэтому их ключей нет.
     */
    fun setThemeUnlocked(overlay: ThemeOverlay, unlocked: Boolean) {
        val key = unlockKey(overlay) ?: return
        val current = _unlockedThemes.value
        _unlockedThemes.value = if (unlocked) current + overlay else current - overlay
        scope.launch {
            try {
                platformStorage.saveString(key, unlocked.toString())
            } catch (e: Exception) {
                println("Failed to persist theme unlock: ${e.message}")
            }
        }
    }

    /**
     * Потеря Матрицы: тема выключается и исчезает из списка найденных.
     * Вызывается при нарушении договора о читах — второго шанса нет,
     * поэтому разблокировка снимается навсегда.
     */
    fun revokeMatrixUnlock() {
        setMatrixThemeOff()
        setThemeUnlocked(ThemeOverlay.MATRIX, false)
    }

    /** Выключить Матрицу, не трогая разблокировку (для обычных тумблеров). */
    private fun setMatrixThemeOff() {
        setThemeOverlay(ThemeOverlay.NONE)
    }

    private fun unlockKey(overlay: ThemeOverlay): String? = when (overlay) {
        ThemeOverlay.KAWAII -> KEY_UNLOCKED_KAWAII
        ThemeOverlay.CYBERPUNK -> KEY_UNLOCKED_CYBERPUNK
        ThemeOverlay.MATRIX -> KEY_UNLOCKED_MATRIX
        else -> null
    }

    /**
     * Найденные темы: сохранённые флаги разблокировок плюс сидирование из
     * легаси-ключей активности. Сид нужен пользователям, которые нашли тему
     * до введения списка: у них флаг активности равен true, и терять
     * найденное из-за обновления несправедливо.
     */
    private fun loadUnlockedThemes(): Set<ThemeOverlay> {
        val set = mutableSetOf<ThemeOverlay>()
        if (loadBooleanFlag(KEY_UNLOCKED_KAWAII, false) ||
            platformStorage.getString(KEY_KAWAII_THEME)?.toBooleanStrictOrNull() == true
        ) {
            set += ThemeOverlay.KAWAII
        }
        if (loadBooleanFlag(KEY_UNLOCKED_CYBERPUNK, false) ||
            platformStorage.getString(KEY_CYBERPUNK_THEME)?.toBooleanStrictOrNull() == true
        ) {
            set += ThemeOverlay.CYBERPUNK
        }
        if (loadBooleanFlag(KEY_UNLOCKED_MATRIX, false) ||
            platformStorage.getString(KEY_MATRIX_THEME)?.toBooleanStrictOrNull() == true
        ) {
            set += ThemeOverlay.MATRIX
        }
        return set
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

    /** Диагностика (Sentry): включение запускает SDK, выключение — останавливает. */
    fun setDiagnosticsEnabled(enabled: Boolean) {
        _diagnosticsEnabled.value = enabled
        AppDiagnostics.setEnabled(enabled)
        scope.launch { platformStorage.saveString(KEY_DIAGNOSTICS_ENABLED, enabled.toString()) }
    }

    /** Выбор иконки приложения; применяется немедленно (iOS), хранится для UI. */
    fun setAppIcon(name: String) {
        val changed = _appIcon.value != name
        _appIcon.value = name
        AppIconManager.apply(name)
        scope.launch { platformStorage.saveString(KEY_APP_ICON, name) }
        if (changed) {
        }
    }

    /**
     * Включить напоминания. Тумблер не врёт (решение владельца): включённым
     * он становится только при реально выданном разрешении. Если система
     * его не выдала — запросим, но значение не включим: пользователь увидит
     * пояснение в снекбаре и переключатель вернётся в «выкл», пока не выдаст.
     *
     * Сценарий: отказ дважды → Android навсегда прячет диалог; без этой
     * проверки тумблер включался, будильники ставились, а уведомления
     * молча не показывались.
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        if (enabled && !NotificationsManager.isAuthorized()) {
            // Спрашиваем разрешение (только при ЯВНОЙ попытке включить —
            // параметр true): системный диалог требует resumed-activity,
            // откат из наблюдателя передаёт false и молчит.
            NotificationsManager.requestAuthorization()
            return
        }
        val changed = _notificationsEnabled.value != enabled
        _notificationsEnabled.value = enabled
        if (enabled) {
            if (_notificationsTargetId.value == null) {
                _notificationsTargetId.value = _selectedTarget.value?.id
                persistNotificationsTargetId(_notificationsTargetId.value)
            }
        }
        scope.launch { platformStorage.saveString(KEY_NOTIFICATIONS_ENABLED, enabled.toString()) }
        if (changed) {
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

    fun setAskBeforeNoteDelete(ask: Boolean) {
        _askBeforeNoteDelete.value = ask
        scope.launch { platformStorage.saveString(KEY_ASK_BEFORE_NOTE_DELETE, ask.toString()) }
    }

    private fun loadThemeOverlay(): ThemeOverlay {
        val stored = platformStorage.getString(KEY_THEME_OVERLAY)
            ?.let { runCatching { ThemeOverlay.valueOf(it) }.getOrNull() }
        if (stored != null) return stored
        val legacy = when {
            platformStorage.getString(KEY_MATRIX_THEME)?.toBooleanStrictOrNull() == true -> ThemeOverlay.MATRIX
            platformStorage.getString(KEY_HALLOWEEN_THEME)?.toBooleanStrictOrNull() == true -> ThemeOverlay.HALLOWEEN
            platformStorage.getString(KEY_CYBERPUNK_THEME)?.toBooleanStrictOrNull() == true -> ThemeOverlay.CYBERPUNK
            platformStorage.getString(KEY_KAWAII_THEME)?.toBooleanStrictOrNull() == true -> ThemeOverlay.KAWAII
            platformStorage.getString(KEY_SAKURA_THEME)?.toBooleanStrictOrNull() == true -> ThemeOverlay.SAKURA
            else -> null
        }
        if (legacy != null) return legacy
        // Свежая установка: ни одного тематического ключа нет вообще.
        // Розовая тема теперь включена сразу (решение владельца), поэтому
        // дефолт — Сакура. Если ключи есть, один из возвратов выше уже
        // сработал: осознанный выбор пользователя уважается.
        return ThemeOverlay.SAKURA
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

    fun setDockHidden(hidden: Boolean) {
        _dockHidden.value = hidden
        scope.launch {
            try {
                platformStorage.saveString(KEY_DOCK_HIDDEN, hidden.toString())
            } catch (e: Exception) {
                println("Failed to persist dock hidden flag: ${e.message}")
            }
        }
    }

    private fun sanitizeDockTabs(tabs: List<AppTab>): List<AppTab> {
        return try {
            var middle = tabs.filter { !it.isFixed }.distinct().take(3)
            // Правило владельца: док не может опустеть до двух базовых
            // разделов — «Сервисы» ставятся между ними и «блокируются»,
            // пока в доке не появится другой скрываемый раздел.
            if (middle.none { it != AppTab.SERVICES }) {
                middle = listOf(AppTab.SERVICES)
            }
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
        val diagnosticsEnabledBefore = _diagnosticsEnabled.value
        val notificationsEnabledBefore = _notificationsEnabled.value
        val notifyMinutesBeforeBefore = _notifyMinutesBefore.value
        val askBeforeNoteDeleteBefore = _askBeforeNoteDelete.value
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
        _calendarExpandState.value = CalendarExpandState.WEEK
        _hideAdditionalLessons.value = false
        _autoScrollToCurrentLesson.value = true
        _showAbbreviatedNames.value = false
        // Пользовательские цвета занятий — тоже настройка, сбрасывается
        // вместе с остальными (в исходной ветке этот ключ забывали).
        _lessonTypeColors.value = emptyMap()
        // Вариант палитры Киберпанка — тоже настройка: сброс возвращает новую
        // палитру, которая стоит по умолчанию.
        _cyberpunkVariant.value = CyberpunkVariant.DEFAULT
        _themeMode.value = ThemeMode.SYSTEM
        _dockTabs.value = DEFAULT_DOCK_TABS
        _dockHidden.value = false
        // Дефолт после сброса — розовая тема включена (решение владельца).
        // Отказ от сезона тоже стирается: сброс возвращает заводское
        // поведение, а не фиксирует прошлое решение пользователя.
        applyThemeOverlay(ThemeOverlay.SAKURA, dismissSeason = false)
        _halloweenDismissedYear.value = null
        _cheatsAgreed.value = cheatsAgreedBefore
        _cheatsBlocked.value = cheatsBlockedBefore
        _diagnosticsEnabled.value = diagnosticsEnabledBefore
        _notificationsEnabled.value = notificationsEnabledBefore
        _notificationsTargetId.value = null
        _vpnWarningEnabled.value = true
        _notifyMinutesBefore.value = notifyMinutesBeforeBefore
        _askBeforeNoteDelete.value = askBeforeNoteDeleteBefore
        _notePages.value = com.jetbrains.kmpapp.data.model.defaultNotePages()
        lastSyncTimes.clear()
        com.jetbrains.kmpapp.data.model.SemesterWeeks.set(emptyList())
        scope.launch {
            if (cheatsAgreedBefore == null) platformStorage.remove(KEY_CHEATS_AGREED)
            else platformStorage.saveString(KEY_CHEATS_AGREED, cheatsAgreedBefore.toString())
            platformStorage.saveString(KEY_CHEATS_BLOCKED, cheatsBlockedBefore.toString())
            platformStorage.saveString(KEY_DIAGNOSTICS_ENABLED, diagnosticsEnabledBefore.toString())
            platformStorage.saveString(KEY_NOTIFICATIONS_ENABLED, notificationsEnabledBefore.toString())
            platformStorage.remove(KEY_NOTIFICATIONS_TARGET_ID)
            platformStorage.remove(KEY_LESSON_TYPE_COLORS)
            platformStorage.remove(KEY_CYBERPUNK_VARIANT)
            platformStorage.saveString(KEY_VPN_WARNING_ENABLED, true.toString())
            platformStorage.saveString(KEY_NOTIFY_MINUTES_BEFORE, notifyMinutesBeforeBefore.toString())
            platformStorage.saveString(KEY_ASK_BEFORE_NOTE_DELETE, askBeforeNoteDeleteBefore.toString())
            platformStorage.saveString(KEY_DOCK_HIDDEN, false.toString())
            platformStorage.saveString(KEY_NOTES, json.encodeToString(_notePages.value))
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
        // Ступень раскрытия ленты: WEEK | TWO_WEEKS | MONTH (issue #3).
        private const val KEY_CALENDAR_EXPAND_STATE = "krasava_calendar_expand_state"
        private const val KEY_HIDE_ADDITIONAL_LESSONS = "krasava_hide_additional_lessons"
        private const val KEY_AUTO_SCROLL_CURRENT_LESSON = "krasava_auto_scroll_current_lesson"
        private const val KEY_SHOW_ABBREVIATED_NAMES = "krasava_show_abbreviated_names"
        private const val KEY_APP_THEME = "krasava_app_theme"
        private const val KEY_DOCK_TABS = "krasava_dock_tabs_order"
        private const val KEY_DOCK_HIDDEN = "krasava_dock_hidden"
        private const val KEY_SAKURA_THEME = "krasava_sakura_theme_secret"
        private const val KEY_KAWAII_THEME = "krasava_kawaii_theme_secret"
        private const val KEY_CYBERPUNK_THEME = "krasava_cyberpunk_theme_secret"
        private const val KEY_MATRIX_THEME = "krasava_matrix_theme_secret"
        private const val KEY_HALLOWEEN_THEME = "krasava_halloween_theme_secret"
        private const val KEY_HALLOWEEN_DISMISSED_YEAR = "krasava_halloween_dismissed_year"
        private const val KEY_THEME_OVERLAY = "krasava_theme_overlay"
        // Разблокировки квестовых тем для списка «Тематические темы».
        private const val KEY_UNLOCKED_KAWAII = "krasava_unlocked_kawaii"
        private const val KEY_UNLOCKED_CYBERPUNK = "krasava_unlocked_cyberpunk"
        private const val KEY_UNLOCKED_MATRIX = "krasava_unlocked_matrix"
        // Пользовательские цвета типов занятий: "LECTURE=BLUE;LAB=ROSE".
        private const val KEY_LESSON_TYPE_COLORS = "krasava_lesson_type_colors"
        // Вариант палитры Киберпанка: NEON (новая) | CLASSIC (старая).
        private const val KEY_CYBERPUNK_VARIANT = "krasava_cyberpunk_variant"
        private const val KEY_CHEATS_AGREED = "krasava_cheats_agreed"
        private const val KEY_CHEATS_BLOCKED = "krasava_cheats_blocked"
        private const val KEY_DIAGNOSTICS_ENABLED = "krasava_diagnostics_enabled"
        private const val KEY_APP_ICON = "krasava_app_icon"
        private const val KEY_NOTIFICATIONS_ENABLED = "krasava_notifications_enabled"
        private const val KEY_NOTIFICATIONS_TARGET_ID = "krasava_notifications_target_id"
        private const val KEY_VPN_WARNING_ENABLED = "krasava_vpn_warning_enabled"
        private const val KEY_NOTIFY_MINUTES_BEFORE = "krasava_notify_minutes_before"
        private const val KEY_ASK_BEFORE_NOTE_DELETE = "krasava_ask_before_note_delete"
        private const val KEY_NOTES = "krasava_notes_pages"
        // Дефолт дока для НОВЫХ установок (решение владельца): Существующие
        // пользователи не затрагиваются — их сохранённый док доверяется.
        val DEFAULT_DOCK_TABS = listOf(
            AppTab.SCHEDULE,
            AppTab.MAP,
            AppTab.SERVICES,
            AppTab.OTHER
        )
    }
}


