package com.jetbrains.kmpapp

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jetbrains.kmpapp.data.ScheduleRepository
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.screens.components.AppTab
import com.jetbrains.kmpapp.screens.components.FloatingDock
import com.jetbrains.kmpapp.screens.components.GESTURE_BLOCKER_HEIGHT
import com.jetbrains.kmpapp.screens.components.LayeredNavHost
import com.jetbrains.kmpapp.screens.components.systemGestureBlocker
import com.jetbrains.kmpapp.screens.compare.CompareScheduleScreen
import com.jetbrains.kmpapp.screens.compare.CompareScheduleViewModel
import com.jetbrains.kmpapp.screens.map.MapScreen
import com.jetbrains.kmpapp.screens.notes.NotesScreen
import com.jetbrains.kmpapp.screens.notes.NotesViewModel
import com.jetbrains.kmpapp.screens.other.OtherScreen
import com.jetbrains.kmpapp.screens.other.OtherViewModel
import com.jetbrains.kmpapp.screens.rooms.FreeRoomsScreen
import com.jetbrains.kmpapp.screens.rooms.FreeRoomsViewModel
import com.jetbrains.kmpapp.screens.schedule.ScheduleScreen
import com.jetbrains.kmpapp.screens.schedule.ScheduleViewModel
import com.jetbrains.kmpapp.screens.services.ServicesScreen
import com.jetbrains.kmpapp.screens.games.GamesScreen
import com.jetbrains.kmpapp.screens.games.GamesViewModel
import com.jetbrains.kmpapp.screens.services.ServicesViewModel
import com.jetbrains.kmpapp.screens.other.isServiceScreen
import com.jetbrains.kmpapp.screens.tasks.TasksScreen
import com.jetbrains.kmpapp.screens.tasks.TasksViewModel
import com.jetbrains.kmpapp.theme.cyberpunkColors
import com.jetbrains.kmpapp.theme.ErrorColors
import com.jetbrains.kmpapp.theme.ErrorScanlines
import com.jetbrains.kmpapp.theme.ErrorTypography
import com.jetbrains.kmpapp.theme.HalloweenDarkColors
import com.jetbrains.kmpapp.theme.KawaiiDarkColors
import com.jetbrains.kmpapp.theme.KawaiiLightColors
import com.jetbrains.kmpapp.theme.LocalGlitchTextEnabled
import com.jetbrains.kmpapp.theme.LocalLessonTypeOverrides
import com.jetbrains.kmpapp.theme.MatrixDarkColors
import com.jetbrains.kmpapp.theme.MatrixLightColors
import com.jetbrains.kmpapp.theme.SakuraDarkColors
import com.jetbrains.kmpapp.theme.SakuraLightColors
import com.jetbrains.kmpapp.theme.ThemeOverlay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

internal val LightColors = lightColorScheme(
    primary = Color(0xFF1E5BB0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF555F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF121C2B),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceContainer = Color(0xFFF0F3F9),
    surfaceContainerHigh = Color(0xFFE8EDF5)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468A),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF3D4758),
    onSecondaryContainer = Color(0xFFD9E3F8),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF282A30)
)

@Composable
fun App() {
    val repository: ScheduleRepository = koinInject()
    val themeMode by repository.themeMode.collectAsState()
    val themeOverlay by repository.themeOverlay.collectAsState()
    val dockTabs by repository.dockTabs.collectAsState()
    val dockHidden by repository.dockHidden.collectAsState()
    // Пользовательские цвета типов занятий: пробрасываются вниз через
    // LocalLessonTypeOverrides, чтобы бейджи и точки календаря перерисовывались
    // сразу после выбора цвета.
    val lessonTypeColors by repository.lessonTypeColors.collectAsState()
    // Вариант палитры «Киберпанка» хранится отдельно от оверлея: тема может
    // быть выключена, но выбранный вариант переживает переключения тем.
    val cyberpunkVariant by repository.cyberpunkVariant.collectAsState()

    val scheduleViewModel: ScheduleViewModel = koinViewModel()
    val otherViewModel: OtherViewModel = koinViewModel()
    val freeRoomsViewModel: FreeRoomsViewModel = koinViewModel()
    val tasksViewModel: TasksViewModel = koinViewModel()
    val compareViewModel: CompareScheduleViewModel = koinViewModel()
    val notesViewModel: NotesViewModel = koinViewModel()
    val servicesViewModel: ServicesViewModel = koinViewModel()
    val gamesViewModel: GamesViewModel = koinViewModel()

    // Тема Error включается теми же правилами, что и в «Сапере» (сломанный
    // рекорд 0 с), но распространяется на всё приложение: пока существует
    // сломанный рекорд, вся схема и типографика заменяются на красный глюк —
    // как это делают оверлеи Сакуры/Матрицы/Киберпанка. Тема не сбрасывается
    // при возврате из партии в меню, а держится до нажатия «Починить ошибку».
    val errorActive by gamesViewModel.errorActive.collectAsState()

    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Хэллоуин: автоматическая, только тёмная, перекрывает любую другую тему
    // (включая Error). Сезон — за 3 дня до 31 октября и через 3 дня после,
    // то есть 28 октября — 3 ноября включительно, по локальной дате устройства.
    // Правила живут в хранилище, чтобы тумблер в списке тем и оформление
    // считались одинаково: сезон + отсутствие отказа либо ручное включение.
    val halloweenActive by repository.isHalloweenActive.collectAsState()

    val colors = if (halloweenActive) {
        HalloweenDarkColors
    } else if (errorActive) {
        ErrorColors
    } else {
        when (themeOverlay) {
            ThemeOverlay.SAKURA -> if (isDark) SakuraDarkColors else SakuraLightColors
            // Кавай — пастель из трёх акцентов (голубой, розовый, золотой) на
            // кремово-сиреневых поверхностях; намеренно не похожа ни на
            // тёплую розовую Сакуру, ни на жёлто-циановый Киберпанк.
            ThemeOverlay.KAWAII -> if (isDark) KawaiiDarkColors else KawaiiLightColors
            ThemeOverlay.CYBERPUNK -> cyberpunkColors(cyberpunkVariant, isDark)
            ThemeOverlay.MATRIX -> if (isDark) MatrixDarkColors else MatrixLightColors
            // Перекрыт проверкой halloweenActive выше; ветка нужна для
            // исчерпывающего when.
            ThemeOverlay.HALLOWEEN -> HalloweenDarkColors
            ThemeOverlay.NONE -> if (isDark) DarkColors else LightColors
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = if (errorActive) ErrorTypography else MaterialTheme.typography
    ) {
        // Пока активна тема Error, все светлые (белые) надписи приложения —
        // расписание, сервисы, настройки — рисуются через GlitchText: те
        // Cyan/Red «двойники», что у заголовка ERROR. Тёмные и цветные
        // подписи остаются обычными. На Хэллоуин глитч включается тоже —
        // как «страшный» эффект надписей.
        CompositionLocalProvider(
            LocalGlitchTextEnabled provides (errorActive || halloweenActive),
            com.jetbrains.kmpapp.theme.LocalHalloweenEnabled provides halloweenActive,
            LocalLessonTypeOverrides provides lessonTypeColors
        ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            var currentTab by remember { mutableStateOf(AppTab.SCHEDULE) }

            // Предупреждение о новой версии по экрану (репорт владельца:
            // «нет никаких предупреждений о том, что вышла новая версия»):
            // автопроверка на старте нашла NEW_VERSION/CRITICAL →
            // AlertDialog поверх всего, один раз на запуск. MINOR_BUILD
            // (новая сборка того же канала) молчит — это шум для dev.
            // Чекбокс «Не показывать для этой версии» (репорт владельца):
            // подавление пишется в PlatformStorage и живёт до СЛЕДУЮЩЕЙ
            // новой версии — версия в фиде станет больше подавленной, и
            // диалог вернётся.
            // Ручной показ (репорт: «красный виджет не кликается»): тап по
            // оранжевому/красному мини-виджету в шапке «Другого» открывает
            // диалог ДАЖЕ при подавлении — в нём галочку можно снять.
            val updateSuppressedVersion by com.jetbrains.kmpapp.data.update.UpdateDialogPrefs.suppressedVersion.collectAsState()
            var updateDialogShownFor by remember { mutableStateOf<String?>(null) }
            // Чекбокс отражает ТЕКУЩЕЕ состояние (репорт владельца:
            // «при открытии с подавленной версией галочка уже снята»):
            // при каждом открытии диалога синхронизируется с фактом
            // подавления — подавлена → стоит; нет → снята. «Понятно»
            // сохраняет то, что на галочке сейчас.
            var suppressThisVersion by remember { mutableStateOf(false) }
            // Ручной показ: request — счётчик тапов; последний обработанный
            // номер храним тут. НЕТ consume-вызовов из композиции: смена
            // состояния внутри композиции убивала диалог мгновенно
            // (репорт владельца: «открывается и сразу закрывается»).
            var updateDialogRequestHandled by remember { mutableStateOf(0) }
            val updateResult by otherViewModel.updateResult.collectAsState()
            val updateDialogRequest by otherViewModel.showUpdateDialogRequest.collectAsState()
            // Подавление активно, только пока фид показывает ту же версию
            // (или старее): вышла более новая — снова показываем.
            val suppressedNow = updateSuppressedVersion?.let { sup ->
                updateResult?.let { res ->
                    com.jetbrains.kmpapp.data.update.VersionComparator.compare(
                        res.latestVersion, sup
                    ) <= 0
                } == true
            } == true
            val manualRequested = updateDialogRequest > updateDialogRequestHandled
            val showUpdateDialog = updateResult?.let { res ->
                val autoShow = res.hasUpdate &&
                    res.urgency != com.jetbrains.kmpapp.data.update.UpdateUrgency.MINOR_BUILD &&
                    updateDialogShownFor != res.latestVersion + "_" + res.latestBuild &&
                    !suppressedNow
                // Ручной тап по виджету: есть обновление — показать всегда
                // (даже при подавлении — в диалоге галочку можно снять).
                (autoShow) || (manualRequested && res.hasUpdate)
            } == true
            if (showUpdateDialog) {
                val res = updateResult!!
                val isCritical = res.urgency == com.jetbrains.kmpapp.data.update.UpdateUrgency.CRITICAL
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {
                        updateDialogRequestHandled = updateDialogRequest
                        updateDialogShownFor = res.latestVersion + "_" + res.latestBuild
                    },
                    title = {
                        com.jetbrains.kmpapp.theme.GlitchText(
                            text = if (isCritical) "Важное обновление!" else "Вышла новая версия!",
                            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    },
                    text = {
                        androidx.compose.foundation.layout.Column {
                            com.jetbrains.kmpapp.theme.GlitchText(
                                text = buildString {
                                    append("Доступна версия ${res.latestVersion}")
                                    if (isCritical) append(" — содержит важные исправления")
                                    append(".")
                                    res.changelog?.takeIf { it.isNotBlank() }?.let { ch ->
                                        append("\n\n")
                                        append(ch)
                                    }
                                    // Пометка владельца: если приложение ставили из
                                    // стора — новая версия появляется там позже
                                    // (модерация платформы); GitHub-сборка уже вышла.
                                    append("\n\nЕсли вы устанавливали приложение из магазина приложений, подождите: новая версия появится в нём после модерации платформы. Прямая сборка уже доступна на GitHub.")
                                },
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                            )
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = suppressThisVersion,
                                    onCheckedChange = { suppressThisVersion = it }
                                )
                                com.jetbrains.kmpapp.theme.GlitchText(
                                    text = "Не показывать для этой версии",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.Button(onClick = {
                            updateDialogRequestHandled = updateDialogRequest
                            if (suppressThisVersion) {
                                com.jetbrains.kmpapp.data.update.UpdateDialogPrefs.suppress(res.latestVersion)
                            } else {
                                // Галочка снята: подавление сбрасывается (открыли
                                // вручную при подавлении и передумали) — диалог
                                // снова показывается при следующих запусках.
                                com.jetbrains.kmpapp.data.update.UpdateDialogPrefs.reset()
                            }
                            updateDialogShownFor = res.latestVersion + "_" + res.latestBuild
                        }) {
                            com.jetbrains.kmpapp.theme.GlitchText("Понятно")
                        }
                    }
                )
            }

            // Синхронизация чекбокса с фактом подавления при каждом открытии
            // диалога (репорт владельца: «при открытии галочка уже снята» —
            // должна стоять, если версия подавлена). LaunchedEffect, не
            // композиция: запись состояния в композиции закрывала диалог.
            androidx.compose.runtime.LaunchedEffect(
                updateDialogRequest,
                updateDialogShownFor,
                updateSuppressedVersion
            ) {
                // Диалог вот-вот откроется или открыт: галочка = факт подавления.
                if (updateResult?.hasUpdate == true) {
                    suppressThisVersion = suppressedNow
                }
            }

            LaunchedEffect(Unit) {
                // Мост для виджетов: платформенный код забирает снапшот через него.
                com.jetbrains.kmpapp.data.widget.WidgetBridge.attach(repository)
                kotlinx.coroutines.delay(2000)
                try {
                    if (!repository.isLowPowerMode.value) {
                        otherViewModel.checkForUpdates()
                    }
                } catch (_: Throwable) {}
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (dockHidden) {
                    // Док скрыт: корень — расписание, «Другое» — его дочерний
                    // слой (шестерёнка в топбаре, назад — стрелка/свайп/системная).
                    // Единый источник истины — currentTab: при переключении
                    // тумблера из «Другого» нет кадра-вспышки другой страницы.
                    LayeredNavHost(
                        screen = if (currentTab == AppTab.OTHER) AppTab.OTHER else null,
                        parentScreen = null,
                        onBackToParent = { currentTab = AppTab.SCHEDULE },
                        // Только на момент создания ветки: если док скрыли,
                        // уже находясь в «Другом», — показать сразу, без въезда.
                        initiallyRevealed = remember { currentTab == AppTab.OTHER },
                        rootContent = {
                            ScheduleScreen(
                                viewModel = scheduleViewModel,
                                onOpenOther = { currentTab = AppTab.OTHER }
                            )
                        },
                        screenContent = { _, back ->
                            OtherScreen(
                                viewModel = otherViewModel,
                                gamesViewModel = gamesViewModel,
                                onNavigateToTab = { currentTab = it },
                                onBack = back
                            )
                        }
                    )
                } else {
                Crossfade(targetState = currentTab) { tab ->
                    when (tab) {
                        AppTab.SCHEDULE -> {
                            ScheduleScreen(viewModel = scheduleViewModel)
                        }
                        AppTab.FREE_ROOMS -> {
                            FreeRoomsScreen(viewModel = freeRoomsViewModel)
                        }
                        AppTab.TASKS -> {
                            TasksScreen(viewModel = tasksViewModel)
                        }
                        AppTab.MAP -> {
                            MapScreen()
                        }
                        AppTab.NOTES -> {
                            NotesScreen(viewModel = notesViewModel)
                        }
                        AppTab.COMPARE -> {
                            CompareScheduleScreen(viewModel = compareViewModel)
                        }
                        AppTab.GAMES -> {
                            GamesScreen(viewModel = gamesViewModel)
                        }
                        AppTab.SERVICES -> {
                            ServicesScreen(
                                viewModel = servicesViewModel,
                                gamesViewModel = gamesViewModel,
                                dockTabs = dockTabs
                            )
                        }
                        AppTab.OTHER -> {
                            OtherScreen(
                                viewModel = otherViewModel,
                                gamesViewModel = gamesViewModel,
                                onNavigateToTab = { currentTab = it }
                            )
                        }
                    }
                }

                val density = LocalDensity.current
                val isImeVisible = WindowInsets.ime.getBottom(density) > 0

                // Невидимый блокер системных жестов (только iOS, решение
                // владельца): от низа экрана до середины дока. Свайп системной
                // шторки больше не перехватывается приложением (расписание
                // не уезжает); сам жест распознаёт система по edge-зоне.
                // Слой над контентом, ПОД доком: клики дока работают.
                // Визуально ничего не меняет — ничего не рисует.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(GESTURE_BLOCKER_HEIGHT)
                        .systemGestureBlocker()
                )

                if (!isImeVisible) {
                    // Стрелка «назад» в доке: у вкладки с открытым сервисом
                    // иконка раздела меняется на «назад» (тап = возврат).
                    val servicesActiveService by servicesViewModel.activeService.collectAsState()
                    val otherSubScreen by otherViewModel.activeSubScreen.collectAsState()
                    val activeGame by gamesViewModel.activeGame.collectAsState()
                    val backModeTab = when {
                        currentTab == AppTab.SERVICES && servicesActiveService != null -> AppTab.SERVICES
                        currentTab == AppTab.OTHER && otherSubScreen.isServiceScreen -> AppTab.OTHER
                        currentTab == AppTab.GAMES && activeGame != null -> AppTab.GAMES
                        else -> null
                    }
                    FloatingDock(
                        currentTab = currentTab,
                        onTabSelected = {
                            currentTab = it
                        },
                        onTabReselected = { tab ->
                            when (tab) {
                                AppTab.SCHEDULE -> {
                                    scheduleViewModel.selectLessonForDetail(null)
                                }
                                AppTab.FREE_ROOMS -> {
                                    freeRoomsViewModel.selectRoomForDetail(null)
                                }
                                AppTab.TASKS -> {}
                                AppTab.MAP -> {}
                                AppTab.NOTES -> {}
                                AppTab.COMPARE -> {}
                                AppTab.GAMES -> {
                                    gamesViewModel.closeGame()
                                }
                                AppTab.SERVICES -> {
                                    servicesViewModel.closeService()
                                }
                                AppTab.OTHER -> {
                                    otherViewModel.resetToRoot()
                                }
                            }
                        },
                        tabs = dockTabs,
                        backModeTab = backModeTab,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
                }

                // Однотонные поверхности приложения во время темы Error дают
                // те же «глюковатые полосы», что и поле «Сапёра»: скан-строка
                // и полосы порчи проезжают по всему экрану поверх контента.
                ErrorScanlines(enabled = errorActive)
            }
        }
        }
    }
}
