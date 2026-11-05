package com.jetbrains.kmpapp.screens.other
import com.jetbrains.kmpapp.theme.GlitchText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.jetbrains.kmpapp.screens.components.AppTab
import com.jetbrains.kmpapp.screens.components.LayeredNavHost
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler
import com.jetbrains.kmpapp.screens.components.isService
import com.jetbrains.kmpapp.screens.games.GamesScreen
import com.jetbrains.kmpapp.screens.games.GamesViewModel

@Composable
fun OtherScreen(
    viewModel: OtherViewModel,
    tasksViewModel: com.jetbrains.kmpapp.screens.tasks.TasksViewModel = org.koin.compose.viewmodel.koinViewModel(),
    freeRoomsViewModel: com.jetbrains.kmpapp.screens.rooms.FreeRoomsViewModel = org.koin.compose.viewmodel.koinViewModel(),
    compareViewModel: com.jetbrains.kmpapp.screens.compare.CompareScheduleViewModel = org.koin.compose.viewmodel.koinViewModel(),
    notesViewModel: com.jetbrains.kmpapp.screens.notes.NotesViewModel = org.koin.compose.viewmodel.koinViewModel(),
    gamesViewModel: GamesViewModel = org.koin.compose.viewmodel.koinViewModel(),
    onNavigateToTab: (AppTab) -> Unit = {},
    // Не null при скрытом доке: «Другое» — подстраница расписания,
    // стрелка/свайп/системный «назад» возвращают к расписанию.
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val activeSubScreen by viewModel.activeSubScreen.collectAsState()
    val updateResult by viewModel.updateResult.collectAsState()
    val uriHandler = LocalUriHandler.current

    if (onBack != null) {
        com.jetbrains.kmpapp.screens.components.PlatformBackHandler(onBack = onBack)
    }

    val childScreen = activeSubScreen.takeIf { it != OtherSubScreen.ROOT }
    LayeredNavHost(
        // ROOT в слоте родителя хост понимает как «подложка — корень»
        // (иначе под подстраницами первого уровня был бы пустой однотон).
        screen = childScreen,
        parentScreen = childScreen?.parent()?.takeIf { it != OtherSubScreen.ROOT },
        onBackToParent = {
            viewModel.navigateToSubScreen(activeSubScreen.parent() ?: OtherSubScreen.ROOT)
        },
        // Возврат из другой вкладки с открытой подстраницей — показать сразу.
        initiallyRevealed = remember { activeSubScreen != OtherSubScreen.ROOT },
        // Карта управляет горизонтальными жестами сама — свайп-назад не вешаем.
        swipeGestureEnabled = { it != OtherSubScreen.SERVICE_MAP },
        rootContent = {
            OtherMainContent(
                viewModel = viewModel,
                onNavigate = { viewModel.navigateToSubScreen(it) },
                onNavigateToTab = onNavigateToTab,
                onBack = onBack
            )
        },
        screenContent = { subScreen, back ->
            when (subScreen as OtherSubScreen) {
                OtherSubScreen.ROOT -> {}
                OtherSubScreen.MANAGE_SCHEDULES -> {
                    ManageSchedulesScreen(viewModel = viewModel, onBack = back)
                }
                OtherSubScreen.SETTINGS -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = back,
                        onOpenDataAndCache = { viewModel.navigateToSubScreen(OtherSubScreen.DATA_AND_CACHE) },
                        onOpenDockSettings = { viewModel.navigateToSubScreen(OtherSubScreen.DOCK_SETTINGS) },
                        onOpenTaskSettings = { viewModel.navigateToSubScreen(OtherSubScreen.TASK_SETTINGS) },
                        onOpenIconPicker = { viewModel.navigateToSubScreen(OtherSubScreen.ICON_PICKER) },
                        onOpenScheduleDisplay = { viewModel.navigateToSubScreen(OtherSubScreen.SCHEDULE_DISPLAY) },
                        onOpenScheduleProgress = { viewModel.navigateToSubScreen(OtherSubScreen.SCHEDULE_PROGRESS) },
                        onOpenScheduleCalendar = { viewModel.navigateToSubScreen(OtherSubScreen.SCHEDULE_CALENDAR) },
                        onOpenServiceSettings = { viewModel.navigateToSubScreen(OtherSubScreen.SERVICE_NOTES_SETTINGS) },
                        onOpenSaperSettings = { viewModel.navigateToSubScreen(OtherSubScreen.SAPER_SETTINGS) },
                        onOpenThemedThemes = { viewModel.navigateToSubScreen(OtherSubScreen.THEMED_THEMES) },
                        onOpenLessonColors = { viewModel.navigateToSubScreen(OtherSubScreen.LESSON_COLORS) },
                        onOpenTetrisSettings = { viewModel.navigateToSubScreen(OtherSubScreen.TETRIS_SETTINGS) }
                    )
                }
                OtherSubScreen.DATA_AND_CACHE -> {
                    DataAndCacheScreen(viewModel = viewModel, onBack = back)
                }
                OtherSubScreen.DOCK_SETTINGS -> {
                    DockSettingsScreen(viewModel = viewModel, onBack = back)
                }
                OtherSubScreen.ICON_PICKER -> {
                    IconPickerScreen(viewModel = viewModel, onBack = back)
                }
                OtherSubScreen.TASK_SETTINGS -> {
                    TaskSettingsScreen(tasksViewModel = tasksViewModel, onBack = back)
                }
                OtherSubScreen.SCHEDULE_DISPLAY -> {
                    ScheduleDisplaySettingsScreen(viewModel = viewModel, onBack = back)
                }
                OtherSubScreen.SCHEDULE_PROGRESS -> {
                    ScheduleProgressSettingsScreen(viewModel = viewModel, onBack = back)
                }
                OtherSubScreen.SCHEDULE_CALENDAR -> {
                    ScheduleCalendarSettingsScreen(viewModel = viewModel, onBack = back)
                }
                OtherSubScreen.THEMED_THEMES -> {
                    ThemedThemesScreen(viewModel = viewModel, onBack = back)
                }
                OtherSubScreen.LESSON_COLORS -> {
                    LessonColorSettingsScreen(viewModel = viewModel, onBack = back)
                }
                OtherSubScreen.SERVICE_NOTES_SETTINGS -> {
                    ServiceNotesSettingsScreen(viewModel = viewModel, onBack = back)
                }
                OtherSubScreen.SAPER_SETTINGS -> {
                    SaperSettingsScreen(viewModel = gamesViewModel, onBack = back)
                }
                OtherSubScreen.TETRIS_SETTINGS -> {
                    TetrisSettingsScreen(viewModel = gamesViewModel, onBack = back)
                }
                OtherSubScreen.RESOURCES -> {
                    ResourcesScreen(onBack = back)
                }
                OtherSubScreen.ABOUT -> {
                    AboutScreen(
                        onBack = back,
                        onOpenDebugMenu = { viewModel.navigateToSubScreen(OtherSubScreen.DEBUG_SETTINGS) },
                        onOpenTeam = { viewModel.navigateToSubScreen(OtherSubScreen.TEAM) },
                        onOpenLicenses = { viewModel.navigateToSubScreen(OtherSubScreen.LICENSES) }
                    )
                }
                OtherSubScreen.TEAM -> {
                    TeamScreen(onBack = back)
                }
                OtherSubScreen.LICENSES -> {
                    LicensesScreen(onBack = back)
                }
                OtherSubScreen.DEBUG_SETTINGS -> {
                    DebugSettingsScreen(
                        viewModel = viewModel,
                        onBack = back,
                        onOpenExperimentalSettings = { viewModel.navigateToSubScreen(OtherSubScreen.EXPERIMENTAL_SETTINGS) }
                    )
                }
                OtherSubScreen.EXPERIMENTAL_SETTINGS -> {
                    ExperimentalSettingsScreen(viewModel = viewModel, onBack = back)
                }
                // Сервисы из блока «Сервисы»: те же экраны, что и вкладками,
                // но подстраницами «Другого» — назад: свайп, система и
                // стрелка на иконке «Другое» в доке.
                OtherSubScreen.SERVICE_ROOMS -> {
                    com.jetbrains.kmpapp.screens.components.PlatformBackHandler(onBack = back)
                    com.jetbrains.kmpapp.screens.rooms.FreeRoomsScreen(viewModel = freeRoomsViewModel)
                }
                OtherSubScreen.SERVICE_TASKS -> {
                    com.jetbrains.kmpapp.screens.components.PlatformBackHandler(onBack = back)
                    com.jetbrains.kmpapp.screens.tasks.TasksScreen(viewModel = tasksViewModel)
                }
                OtherSubScreen.SERVICE_MAP -> {
                    com.jetbrains.kmpapp.screens.components.PlatformBackHandler(onBack = back)
                    com.jetbrains.kmpapp.screens.map.MapScreen(onBack = back)
                }
                OtherSubScreen.SERVICE_NOTES -> {
                    com.jetbrains.kmpapp.screens.components.PlatformBackHandler(onBack = back)
                    com.jetbrains.kmpapp.screens.notes.NotesScreen(viewModel = notesViewModel)
                }
                OtherSubScreen.SERVICE_COMPARE -> {
                    com.jetbrains.kmpapp.screens.components.PlatformBackHandler(onBack = back)
                    com.jetbrains.kmpapp.screens.compare.CompareScheduleScreen(viewModel = compareViewModel)
                }
                OtherSubScreen.SERVICE_GAMES -> {
                    PlatformBackHandler(onBack = back)
                    GamesScreen(viewModel = gamesViewModel)
                }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun OtherMainContent(
    viewModel: OtherViewModel,
    onNavigate: (OtherSubScreen) -> Unit,
    onNavigateToTab: (AppTab) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val savedTargets by viewModel.savedTargets.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateResult by viewModel.updateResult.collectAsState()
    val dockTabs by viewModel.dockTabs.collectAsState()
    val dockHidden by viewModel.dockHidden.collectAsState()
    val uriHandler = LocalUriHandler.current
    val hiddenTabs = remember(dockTabs, dockHidden) {
        when {
            // Док скрыт: все сервисы живут блоками здесь. «Расписание» и
            // «Другое» — фиксированные вкладки, не сервисы (isService),
            // контейнер «Сервисы» дублировал бы сам блок.
            dockHidden -> AppTab.entries.filter { it.isService }
            // Если раздел «Сервисы» в доке — блок в «Другом» скрыт целиком
            // (правило владельца), вне зависимости от остальных вкладок.
            dockTabs.take(5).contains(AppTab.SERVICES) -> emptyList()
            else -> AppTab.entries.filter { it.isService && it !in dockTabs.take(5) }
        }
    }
    // Сервис из блока открывается подстраницей «Другого» (назад — свайп и
    // стрелка на иконке дока); сам раздел «Сервисы» — вкладкой.
    val openHiddenTab: (AppTab) -> Unit = { tab ->
        if (tab == AppTab.SERVICES) {
            onNavigateToTab(tab)
        } else {
            tab.toServiceSubScreen()?.let { subScreen ->
                // То же событие, что и со страницы «Сервисы» — в панели
                // сценарии сходятся по одному service_open, различает source.
                onNavigate(subScreen)
            }
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад"
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    GlitchText(
                        text = "Другое",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                // «О программе» спущена кнопкой в ленту (репорт владельца);
                // в шапке её место занял мини-виджет версии/обновления.
                // Мини-виджет: капсула-овал, слева иконка статуса
                // (галочка/обновление/спиннер), справа версия — текущая
                // или новая, если вышла. Заливка — та же 3-ступенчатая
                // палитра бывшей большой карточки. Тап: нет обновления —
                // ручная проверка; есть — открыть предупреждение (в нём
                // чекбоксом можно снять подавление).
                UpdateMiniBadge(
                    updateResult = updateResult,
                    isCheckingUpdate = isCheckingUpdate,
                    onCheckForUpdates = { viewModel.checkForUpdates() },
                    onShowUpdateDialog = { viewModel.requestShowUpdateDialog() }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Adaptive Concentrator Block (only visible if any tabs are hidden from the dock)
            if (hiddenTabs.isNotEmpty()) {
                HiddenTabsCard(
                    hiddenTabs = hiddenTabs,
                    onNavigateToTab = openHiddenTab
                )
            }
            // 1. University resources card
            OtherNavCard(
                title = "Ресурсы университета",
                subtitle = "Личный кабинет, СДО, Пульс и сервисы",
                icon = Icons.Default.School,
                onClick = { onNavigate(OtherSubScreen.RESOURCES) }
            )

            // 2. My schedules card
            OtherNavCard(
                title = "Мои расписания",
                subtitle = if (savedTargets.isEmpty()) "Нет сохранённых расписаний"
                else "Сохранено: ${savedTargets.size}",
                icon = Icons.AutoMirrored.Filled.EventNote,
                onClick = { onNavigate(OtherSubScreen.MANAGE_SCHEDULES) }
            )

            // 3. Settings card
            OtherNavCard(
                title = "Настройки",
                subtitle = "Оформление, тема, навигация",
                icon = Icons.Default.Tune,
                onClick = { onNavigate(OtherSubScreen.SETTINGS) }
            )

            // 4. О программе — кнопкой в ленте (репорт владельца: раньше
            // иконка-Info в шапке). Версия — в мини-виджете шапки; тут —
            // короткое описание, как у соседних карточек.
            OtherNavCard(
                title = "О программе",
                subtitle = "Команда, ссылки и контакты",
                icon = Icons.Outlined.Info,
                onClick = { onNavigate(OtherSubScreen.ABOUT) }
            )

            // 5. App Version / Auto-Update Card — СКРЫТА (репорт владельца):
            // заменена мини-виджетом в шапке. Код оставлен «на всякий»:
            // вернуть — раскомментировать блок ниже.
            // UpdateStatusCard(
            //     updateResult = updateResult,
            //     isCheckingUpdate = isCheckingUpdate,
            //     onCheckForUpdates = { viewModel.checkForUpdates() }
            // )

            // 5. Ссылки на соцсети проекта — намеренно без рамок и фона.
            // «Есть проблема или идея?» живёт в «О программе» — дубль с
            // главной убран ради вертикального бюджета страницы.
            Spacer(modifier = Modifier.height(2.dp))
            ProjectSocialLinks()
        }
    }
}

@Composable
private fun OtherNavCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    GlitchText(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    GlitchText(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Открыть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
