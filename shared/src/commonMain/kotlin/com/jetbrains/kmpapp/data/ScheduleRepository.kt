package com.jetbrains.kmpapp.data

import com.jetbrains.kmpapp.data.model.CalendarExpandState
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonColor
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.ScheduleExport
import com.jetbrains.kmpapp.data.model.SemesterConfig
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.data.notifications.NotificationsManager
import com.jetbrains.kmpapp.data.storage.ScheduleStorage
import com.jetbrains.kmpapp.theme.CyberpunkVariant
import com.jetbrains.kmpapp.theme.ThemeOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Репозиторий локального расписания: единственный источник — собранный
 * пользователем семестр ([SemesterConfig]). Никаких серверов: занятия
 * генерируются из недельного шаблона детерминированно, уведомления
 * планируются по ним же.
 */
class ScheduleRepository(
    private val storage: ScheduleStorage,
    private val powerManager: com.jetbrains.kmpapp.data.power.PlatformPowerManager
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

    /** Развёртка активного семестра в занятия по датам. */
    val semesterLessons: StateFlow<List<Lesson>> = storage.semesterLessons

    val showEmptyLessons: StateFlow<Boolean> = storage.showEmptyLessons
    val themeMode: StateFlow<ThemeMode> = storage.themeMode
    val dockTabs: StateFlow<List<com.jetbrains.kmpapp.screens.components.AppTab>> = storage.dockTabs
    val dockHidden: StateFlow<Boolean> = storage.dockHidden
    val notePages: StateFlow<List<com.jetbrains.kmpapp.data.model.NotePage>> = storage.notePages

    fun updateNotePages(pages: List<com.jetbrains.kmpapp.data.model.NotePage>) {
        storage.saveNotePages(pages)
    }

    fun setDockTabs(tabs: List<com.jetbrains.kmpapp.screens.components.AppTab>) {
        storage.setDockTabs(tabs)
    }

    fun setDockHidden(hidden: Boolean) {
        storage.setDockHidden(hidden)
    }

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

    val calendarExpandState: StateFlow<CalendarExpandState> = storage.calendarExpandState

    fun setCalendarExpandState(state: CalendarExpandState) =
        storage.setCalendarExpandState(state)

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
    val isKawaiiTheme: StateFlow<Boolean> = storage.isKawaiiTheme
    val isCyberpunkTheme: StateFlow<Boolean> = storage.isCyberpunkTheme
    val isMatrixTheme: StateFlow<Boolean> = storage.isMatrixTheme
    val isHalloweenTheme: StateFlow<Boolean> = storage.isHalloweenTheme

    val isHalloweenActive: StateFlow<Boolean> = storage.isHalloweenActive

    val isHalloweenRowVisible: StateFlow<Boolean> = storage.isHalloweenRowVisible
    val unlockedThemes: StateFlow<Set<ThemeOverlay>> = storage.unlockedThemes

    val lessonTypeColors: StateFlow<Map<LessonType, LessonColor>> = storage.lessonTypeColors

    val cyberpunkVariant: StateFlow<CyberpunkVariant> = storage.cyberpunkVariant
    val cheatsAgreed: StateFlow<Boolean?> = storage.cheatsAgreed
    val cheatsBlocked: StateFlow<Boolean> = storage.cheatsBlocked
    val analyticsEnabled: StateFlow<Boolean> = storage.analyticsEnabled
    val appIcon: StateFlow<String> = storage.appIcon
    val notificationsEnabled: StateFlow<Boolean> = storage.notificationsEnabled
    val notifyMinutesBefore: StateFlow<Int> = storage.notifyMinutesBefore
    val vpnWarningEnabled: StateFlow<Boolean> = storage.vpnWarningEnabled
    val askBeforeNoteDelete: StateFlow<Boolean> = storage.askBeforeNoteDelete

    init {
        // Единая точка перепланирования напоминаний: семестр, тумблер или
        // минуты — любое изменение пересчитывает партию уведомлений.
        scope.launch {
            val notifSettings = combine(
                storage.notificationsEnabled,
                storage.notifyMinutesBefore
            ) { enabled, minutes -> Pair(enabled, minutes) }
            combine(storage.semesterLessons, notifSettings) { lessons, (enabled, minutes) ->
                Triple(lessons, enabled, minutes)
            }.collect { (lessons, enabled, minutes) ->
                notificationRescheduleMutex.withLock {
                    if (!enabled || lessons.isEmpty()) {
                        NotificationsManager.reschedule(emptyList(), minutes) { "" }
                    } else {
                        NotificationsManager.reschedule(lessons, minutes) { lesson ->
                            // «Через N мин» из текста убрано (репорт владельца):
                            // относительное время показывает сама система в
                            // углу баннера — текст не дублирует. Тип пары
                            // тоже не пишем: он виден цветом карточки.
                            val room = lesson.classrooms.firstOrNull()
                                ?.let { ", ауд. $it" } ?: ""
                            "${lesson.subject} в ${lesson.startTime}$room"
                        }
                    }
                }
            }
        }
    }

    fun setThemeOverlay(overlay: ThemeOverlay) = storage.setThemeOverlay(overlay)
    fun setMatrixTheme(enabled: Boolean) = storage.setMatrixTheme(enabled)
    fun setHalloweenTheme(enabled: Boolean) = storage.setHalloweenTheme(enabled)
    fun dismissHalloweenSeason() = storage.dismissHalloweenSeason()
    fun clearHalloweenSeasonDismissal() = storage.clearHalloweenSeasonDismissal()
    fun setCheatsAgreed(agreed: Boolean?) = storage.setCheatsAgreed(agreed)
    fun setCheatsBlocked(blocked: Boolean) = storage.setCheatsBlocked(blocked)
    fun setAnalyticsEnabled(enabled: Boolean) = storage.setAnalyticsEnabled(enabled)
    fun setAppIcon(name: String) = storage.setAppIcon(name)
    fun setNotificationsEnabled(enabled: Boolean) = storage.setNotificationsEnabled(enabled)
    fun setNotifyMinutesBefore(minutes: Int) = storage.setNotifyMinutesBefore(minutes)
    fun setVpnWarningEnabled(enabled: Boolean) = storage.setVpnWarningEnabled(enabled)
    fun setAskBeforeNoteDelete(ask: Boolean) = storage.setAskBeforeNoteDelete(ask)
    fun setLessonTypeColor(type: LessonType, color: LessonColor) =
        storage.setLessonTypeColor(type, color)

    fun resetLessonTypeColors() = storage.resetLessonTypeColors()
    fun setCyberpunkVariant(variant: CyberpunkVariant) = storage.setCyberpunkVariant(variant)

    fun setSakuraTheme(enabled: Boolean) {
        storage.setSakuraThemeExclusive(enabled)
    }

    fun setKawaiiTheme(enabled: Boolean) {
        storage.setKawaiiThemeExclusive(enabled)
    }

    fun setCyberpunkTheme(enabled: Boolean) {
        storage.setCyberpunkTheme(enabled)
    }

    fun revokeMatrixUnlock() {
        storage.revokeMatrixUnlock()
    }

    // ── Семестр: сохранение, удаление, экспорт/импорт ───────────────

    /** Текст ошибки валидации или null — тогда семестр сохранён. */
    fun saveSemester(config: SemesterConfig): String? {
        val error = config.validate()
        if (error != null) return error
        storage.saveSemester(config)
        return null
    }

    /** Удалить семестр. */
    fun deleteSemester() {
        storage.deleteSemester()
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

    fun clearCache() {
        // Расписание локальное: серверного кэша нет, метод оставлен для
        // совместимости экрана «Данные и память» — чистит пересчёт семестра.
        storage.reloadSemester()
    }

    fun resetAllData() {
        storage.resetAllData()
    }
}
