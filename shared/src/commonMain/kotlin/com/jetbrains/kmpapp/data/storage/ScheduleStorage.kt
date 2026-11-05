package com.jetbrains.kmpapp.data.storage

import com.jetbrains.kmpapp.data.analytics.AnalyticsEvents
import com.jetbrains.kmpapp.data.analytics.AppAnalytics
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.SemesterConfig
import com.jetbrains.kmpapp.data.model.ThemeMode
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
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
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

    /** Ступень раскрытия ленты календаря: неделя → две недели → месяц. */
    private val _calendarExpandState = MutableStateFlow(
        com.jetbrains.kmpapp.data.model.CalendarExpandState.WEEK
    )
    val calendarExpandState: StateFlow<com.jetbrains.kmpapp.data.model.CalendarExpandState> =
        _calendarExpandState.asStateFlow()

    private val _themeMode = MutableStateFlow<ThemeMode>(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Вкладка «Задачи» в доке: настраиваемая, по умолчанию выключена. */
    private val _tasksEnabled = MutableStateFlow(false)
    val tasksEnabled: StateFlow<Boolean> = _tasksEnabled.asStateFlow()

    /** Вкладка «Игры» в доке: Сапёр и Тетрис. По умолчанию включена. */
    private val _gamesEnabled = MutableStateFlow(true)
    val gamesEnabled: StateFlow<Boolean> = _gamesEnabled.asStateFlow()

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
     * Вариант палитры темы «Киберпанк»: новая (жёлтый/циан) или старая
     * (маджента/циан). Хранится отдельно от [themeOverlay] нарочно:
     * это свойство самой темы, а не состояния «включена сейчас».
     */
    private val _cyberpunkVariant = MutableStateFlow(CyberpunkVariant.DEFAULT)
    val cyberpunkVariant: StateFlow<CyberpunkVariant> = _cyberpunkVariant.asStateFlow()

    /**
     * Год сезона, от которого пользователь отказался вручную.
     *
     * Хэллоуин включается автоматически 28 октября — 3 ноября, и одного
     * выключения оверлея мало: правило включило бы его снова при следующей
     * рекомпозиции. Поэтому «выключить» = запомнить отказ на текущий сезон.
     */
    private val _halloweenDismissedYear = MutableStateFlow<Int?>(null)

    /**
     * Текущая дата как поток: сезонные правила должны переключаться сами,
     * даже если приложение не закрывали на ночь. Тик раз в 15 минут;
     * distinctUntilChanged гасит повторы, дальше уходит только смена суток.
     */
    private val todayFlow: StateFlow<LocalDate> = flow {
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
     * обратно нельзя. Сезон кончился — правило гасит и тему, и строку.
     */
    val isHalloweenRowVisible: StateFlow<Boolean> =
        combine(themeOverlay, todayFlow) { overlay, today ->
            overlay == ThemeOverlay.HALLOWEEN || DateUtils.isHalloweenSeason(today)
        }.stateIn(scope, SharingStarted.Eagerly, false)

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
        _calendarExpandState.value = com.jetbrains.kmpapp.data.model.CalendarExpandState.fromName(
            platformStorage.getString(KEY_CALENDAR_EXPAND_STATE)
        )
        _themeOverlay.value = loadThemeOverlay()
        _unlockedThemes.value = loadUnlockedThemes()
        _cyberpunkVariant.value =
            CyberpunkVariant.fromName(platformStorage.getString(KEY_CYBERPUNK_VARIANT))
        _halloweenDismissedYear.value =
            platformStorage.getString(KEY_HALLOWEEN_DISMISSED_YEAR)?.toIntOrNull()
        _cheatsAgreed.value = nullableFlag(KEY_CHEATS_AGREED)
        _cheatsBlocked.value = loadBooleanFlag(KEY_CHEATS_BLOCKED, false)
        _betaChannel.value = loadBooleanFlag(KEY_BETA_CHANNEL, false)
        _analyticsEnabled.value = loadBooleanFlag(KEY_ANALYTICS_ENABLED, true)
        // Отправка включена по умолчанию: приветственный диалог согласия
        // скрыт, согласие считаем данным; тумблер в настройках выключает.
        _analyticsConsent.value = nullableFlag(KEY_ANALYTICS_CONSENT) ?: true
        AppAnalytics.setEventsEnabled(_analyticsEnabled.value)
        _tasksEnabled.value = loadBooleanFlag(KEY_TASKS_ENABLED, false)
        _gamesEnabled.value = loadBooleanFlag(KEY_GAMES_ENABLED, true)
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

    /** Ступень раскрытия календаря: пишется всегда, переживает рестарт. */
    fun setCalendarExpandState(state: com.jetbrains.kmpapp.data.model.CalendarExpandState) {
        _calendarExpandState.value = state
        scope.launch {
            try {
                platformStorage.saveString(KEY_CALENDAR_EXPAND_STATE, state.name)
            } catch (e: Exception) {
                println("Failed to persist calendar expand state: ${e.message}")
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
        val changed = _themeOverlay.value != overlay
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
            } catch (e: Exception) {
                println("Failed to persist theme overlay: ${e.message}")
            }
        }
        if (changed) AppAnalytics.logEvent(AnalyticsEvents.SETTINGS_THEME_OVERLAY_SET, mapOf("overlay" to overlay.name))
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
        setThemeOverlay(ThemeOverlay.NONE)
        setThemeUnlocked(ThemeOverlay.MATRIX, false)
    }

    private fun unlockKey(overlay: ThemeOverlay): String? = when (overlay) {
        ThemeOverlay.KAWAII -> KEY_UNLOCKED_KAWAII
        ThemeOverlay.CYBERPUNK -> KEY_UNLOCKED_CYBERPUNK
        ThemeOverlay.MATRIX -> KEY_UNLOCKED_MATRIX
        else -> null
    }

    /** Найденные темы: сохранённые флаги разблокировок. */
    private fun loadUnlockedThemes(): Set<ThemeOverlay> {
        val set = mutableSetOf<ThemeOverlay>()
        if (loadBooleanFlag(KEY_UNLOCKED_KAWAII, false)) set += ThemeOverlay.KAWAII
        if (loadBooleanFlag(KEY_UNLOCKED_CYBERPUNK, false)) set += ThemeOverlay.CYBERPUNK
        if (loadBooleanFlag(KEY_UNLOCKED_MATRIX, false)) set += ThemeOverlay.MATRIX
        return set
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

    fun setGamesEnabled(enabled: Boolean) = persistFlag(_gamesEnabled, KEY_GAMES_ENABLED, enabled)

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
        val gamesEnabledBefore = _gamesEnabled.value
        val unlockedThemesBefore = _unlockedThemes.value
        val cyberpunkVariantBefore = _cyberpunkVariant.value
        platformStorage.clearAll()
        applySemester(null)
        _showEmptyLessons.value = true
        _showLessonProgress.value = true
        _showEmptyLessonProgress.value = true
        _showBreakProgress.value = true
        _autoScrollToCurrentLesson.value = true
        _showAbbreviatedNames.value = false
        _calendarExpandState.value = com.jetbrains.kmpapp.data.model.CalendarExpandState.WEEK
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
        _gamesEnabled.value = gamesEnabledBefore
        _skippedUpdateVersion.value = null
        // Найденные темы — это прогресс пользователя, а не настройки:
        // сброс данных не отнимает квесты. Палитра Киберпанка тоже выбор,
        // а не временного состояние.
        _unlockedThemes.value = unlockedThemesBefore
        _cyberpunkVariant.value = cyberpunkVariantBefore
        _halloweenDismissedYear.value = null
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
            platformStorage.saveString(KEY_GAMES_ENABLED, gamesEnabledBefore.toString())
            for (overlay in unlockedThemesBefore) {
                unlockKey(overlay)?.let { platformStorage.saveString(it, "true") }
            }
            platformStorage.saveString(KEY_CYBERPUNK_VARIANT, cyberpunkVariantBefore.name)
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
        private const val KEY_CALENDAR_EXPAND_STATE = "uschedule_calendar_expand_state"
        private const val KEY_APP_THEME = "uschedule_app_theme"
        private const val KEY_TASKS_ENABLED = "uschedule_tasks_enabled"
        private const val KEY_GAMES_ENABLED = "uschedule_games_enabled"
        private const val KEY_THEME_OVERLAY = "uschedule_theme_overlay"
        private const val KEY_CHEATS_AGREED = "uschedule_cheats_agreed"
        private const val KEY_CHEATS_BLOCKED = "uschedule_cheats_blocked"
        private const val KEY_BETA_CHANNEL = "uschedule_beta_channel"
        private const val KEY_ANALYTICS_ENABLED = "uschedule_analytics_enabled"
        private const val KEY_ANALYTICS_CONSENT = "uschedule_analytics_consent"
        private const val KEY_NOTIFICATIONS_ENABLED = "uschedule_notifications_enabled"
        private const val KEY_NOTIFY_MINUTES_BEFORE = "uschedule_notify_minutes_before"
        private const val KEY_SKIPPED_UPDATE_VERSION = "uschedule_skipped_update_version"
        private const val KEY_UNLOCKED_KAWAII = "uschedule_unlocked_kawaii"
        private const val KEY_UNLOCKED_CYBERPUNK = "uschedule_unlocked_cyberpunk"
        private const val KEY_UNLOCKED_MATRIX = "uschedule_unlocked_matrix"
        private const val KEY_CYBERPUNK_VARIANT = "uschedule_cyberpunk_variant"
        private const val KEY_HALLOWEEN_DISMISSED_YEAR = "uschedule_halloween_dismissed_year"
    }
}
