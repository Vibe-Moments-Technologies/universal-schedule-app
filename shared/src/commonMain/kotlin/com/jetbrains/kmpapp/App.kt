package com.jetbrains.kmpapp

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
import com.jetbrains.kmpapp.data.analytics.AnalyticsEvents
import com.jetbrains.kmpapp.data.analytics.AppAnalytics
import com.jetbrains.kmpapp.data.analytics.platformName
import com.jetbrains.kmpapp.data.model.AppVersion
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.screens.components.AppTab
import com.jetbrains.kmpapp.screens.components.FloatingDock
import com.jetbrains.kmpapp.screens.components.GESTURE_BLOCKER_HEIGHT
import com.jetbrains.kmpapp.screens.components.systemGestureBlocker
import com.jetbrains.kmpapp.screens.other.OtherScreen
import com.jetbrains.kmpapp.screens.other.OtherSubScreen
import com.jetbrains.kmpapp.screens.other.OtherViewModel
import com.jetbrains.kmpapp.screens.schedule.ScheduleScreen
import com.jetbrains.kmpapp.screens.schedule.ScheduleViewModel
import com.jetbrains.kmpapp.screens.tasks.TasksScreen
import com.jetbrains.kmpapp.screens.tasks.TasksViewModel
import com.jetbrains.kmpapp.theme.HalloweenDarkColors
import com.jetbrains.kmpapp.theme.KawaiiDarkColors
import com.jetbrains.kmpapp.theme.KawaiiLightColors
import com.jetbrains.kmpapp.theme.MatrixDarkColors
import com.jetbrains.kmpapp.theme.MatrixLightColors
import com.jetbrains.kmpapp.theme.SakuraDarkColors
import com.jetbrains.kmpapp.theme.SakuraLightColors
import com.jetbrains.kmpapp.theme.ThemeOverlay
import com.jetbrains.kmpapp.theme.cyberpunkColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jetbrains.kmpapp.data.update.UpdateUrgency
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

    val scheduleViewModel: ScheduleViewModel = koinViewModel()
    val otherViewModel: OtherViewModel = koinViewModel()
    val tasksViewModel: TasksViewModel = koinViewModel()
    val gamesViewModel: com.jetbrains.kmpapp.screens.games.GamesViewModel = koinViewModel()

    // Аналитика включена по умолчанию; выключается тумблером в настройках.
    LaunchedEffect(Unit) {
        val params = mutableMapOf<String, String>()
        val channel = AppVersion.BUILD_CHANNEL
        if (channel == "stable" || channel == "beta" || channel == "rc") {
            params["version"] = AppVersion.VERSION_NAME
            params["platform"] = platformName()
        }
        AppAnalytics.logEvent(AnalyticsEvents.SESSION_OPEN, params)
    }

    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Хэллоуин: автоматическая, только тёмная, перекрывает любую другую тему.
    // Сезон — за 3 дня до 31 октября и через 3 дня после, то есть 28 октября —
    // 3 ноября включительно, по локальной дате устройства. Правила живут в
    // хранилище, чтобы тумблер в списке тем и оформление считались одинаково:
    // сезон + отсутствие отказа либо ручное включение.
    val halloweenActive by repository.isHalloweenActive.collectAsState()
    // Вариант палитры «Киберпанка» хранится отдельно от оверлея: тема может
    // быть выключена, но выбранный вариант переживает переключения тем.
    val cyberpunkVariant by repository.cyberpunkVariant.collectAsState()

    // Тема Error включается теми же правилами, что и в «Сапере» (сломанный
    // рекорд 0 с), но распространяется на всё приложение: пока существует
    // сломанный рекорд, вся схема и типографика заменяются на красный глюк.
    val errorActive by gamesViewModel.errorActive.collectAsState()

    val colors = if (halloweenActive) {
        HalloweenDarkColors
    } else if (errorActive) {
        com.jetbrains.kmpapp.theme.ErrorColors
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
        typography = if (errorActive) com.jetbrains.kmpapp.theme.ErrorTypography else MaterialTheme.typography
    ) {
        // На Хэллоуин глитч-надписи включаются как «страшный» эффект;
        // в теме Error — все светлые надписи рисуются глитч-двойниками.
        androidx.compose.runtime.CompositionLocalProvider(
            com.jetbrains.kmpapp.theme.LocalGlitchTextEnabled provides (halloweenActive || errorActive),
            com.jetbrains.kmpapp.theme.LocalHalloweenEnabled provides halloweenActive
        ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // «Блики» темы Error на весь экран: те же скан-строки и полосы
            // порчи, что на поле «Сапёра», но на всю площадь приложения.
            com.jetbrains.kmpapp.theme.ErrorScanlines(enabled = errorActive)

            var currentTab by remember { mutableStateOf(AppTab.SCHEDULE) }

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

            val updateResult by otherViewModel.updateResult.collectAsState()
            val skippedVersion by repository.skippedUpdateVersion.collectAsState()

            UpdateDialog(
                updateResult = updateResult,
                skippedVersion = skippedVersion,
                onSkip = { version -> otherViewModel.skipUpdate(version) },
                onDismiss = { otherViewModel.dismissUpdateDialog() }
            )

            // «Задачи» выключены тумблером — вкладка недоступна нигде.
            LaunchedEffect(dockTabs, currentTab) {
                if (currentTab !in dockTabs) currentTab = AppTab.SCHEDULE
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Crossfade(targetState = currentTab) { tab ->
                    when (tab) {
                        AppTab.SCHEDULE -> {
                            ScheduleScreen(
                                viewModel = scheduleViewModel,
                                onOpenConfigurator = {
                                    otherViewModel.navigateToSubScreen(OtherSubScreen.CONFIGURATOR)
                                    currentTab = AppTab.OTHER
                                }
                            )
                        }
                        AppTab.TASKS -> {
                            TasksScreen(viewModel = tasksViewModel)
                        }
                        AppTab.GAMES -> {
                            com.jetbrains.kmpapp.screens.games.GamesScreen(viewModel = gamesViewModel)
                        }
                        AppTab.OTHER -> {
                            OtherScreen(viewModel = otherViewModel)
                        }
                    }
                }

                val density = LocalDensity.current
                val isImeVisible = WindowInsets.ime.getBottom(density) > 0

                // Невидимый блок системных жестов (только iOS, решение
                // владельца): от низа экрана до середины дока. Свайп системной
                // шторки больше не перехватывается приложением (расписание
                // не уезжает); сам жест распознаёт система по edge-зоне.
                // Слой над контентом, ПОД доком: клики дока работают.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(GESTURE_BLOCKER_HEIGHT)
                        .systemGestureBlocker()
                )

                if (!isImeVisible) {
                    // Идёт партия — иконка «Игр» в доке превращается в «назад»:
                    // тап возвращает к меню выбора игры (см. backModeTab).
                    val activeGame by gamesViewModel.activeGame.collectAsState()
                    val gamesBackMode = if (currentTab == AppTab.GAMES && activeGame != null) AppTab.GAMES else null
                    FloatingDock(
                        currentTab = currentTab,
                        backModeTab = gamesBackMode,
                        onTabSelected = {
                            currentTab = it
                            AppAnalytics.logEvent(AnalyticsEvents.NAV_TAB_OPEN, mapOf("tab" to it.name))
                        },
                        onTabReselected = { tab ->
                            when (tab) {
                                AppTab.SCHEDULE -> {
                                    scheduleViewModel.selectLessonForDetail(null)
                                }
                                AppTab.TASKS -> {}
                                AppTab.GAMES -> {
                                    gamesViewModel.closeGame()
                                }
                                AppTab.OTHER -> {
                                    otherViewModel.resetToRoot()
                                }
                            }
                        },
                        tabs = dockTabs,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
        }
    }
}

/**
 * Предупреждение об обновлении: без скачивания и установки — «Обновить»
 * открывает страницу релизов GitHub и закрывает диалог (без пропуска версии —
 * при следующей проверке он покажется снова), «Пропустить» гасит предложение
 * для этой версии (для следующей более новой — покажется снова).
 */
@Composable
private fun UpdateDialog(
    updateResult: com.jetbrains.kmpapp.data.update.UpdateCheckResult?,
    skippedVersion: String?,
    onSkip: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val activeUpdate = updateResult ?: return
    if (!activeUpdate.hasUpdate) return
    if (activeUpdate.urgency != UpdateUrgency.NEW_VERSION && activeUpdate.urgency != UpdateUrgency.CRITICAL) return
    if (skippedVersion == activeUpdate.latestVersion) return

    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = "Доступна новая версия", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Вышла версия ${activeUpdate.latestVersion}. Обновление устанавливается вручную — со страницы релизов на GitHub.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!activeUpdate.changelog.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activeUpdate.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                uriHandler.openUri(AppVersion.GITHUB_REPO_URL + "/releases/latest")
                onDismiss()
            }) {
                Text("Обновить")
            }
        },
        dismissButton = {
            TextButton(onClick = { onSkip(activeUpdate.latestVersion) }) {
                Text("Пропустить")
            }
        }
    )
}
