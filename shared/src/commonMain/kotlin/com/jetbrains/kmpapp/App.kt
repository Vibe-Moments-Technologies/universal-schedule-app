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
import com.jetbrains.kmpapp.screens.other.OtherScreen
import com.jetbrains.kmpapp.screens.other.OtherSubScreen
import com.jetbrains.kmpapp.screens.other.OtherViewModel
import com.jetbrains.kmpapp.screens.schedule.ScheduleScreen
import com.jetbrains.kmpapp.screens.schedule.ScheduleViewModel
import com.jetbrains.kmpapp.screens.tasks.TasksScreen
import com.jetbrains.kmpapp.screens.tasks.TasksViewModel
import com.jetbrains.kmpapp.theme.CyberpunkDarkColors
import com.jetbrains.kmpapp.theme.CyberpunkLightColors
import com.jetbrains.kmpapp.theme.MatrixDarkColors
import com.jetbrains.kmpapp.theme.MatrixLightColors
import com.jetbrains.kmpapp.theme.SakuraDarkColors
import com.jetbrains.kmpapp.theme.SakuraLightColors
import com.jetbrains.kmpapp.theme.ThemeOverlay
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

private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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

    // Аналитика отключена до явного согласия (гейт скрыт) — события не уходят.
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

    val colors = when (themeOverlay) {
        ThemeOverlay.SAKURA -> if (isDark) SakuraDarkColors else SakuraLightColors
        ThemeOverlay.CYBERPUNK -> if (isDark) CyberpunkDarkColors else CyberpunkLightColors
        ThemeOverlay.MATRIX -> if (isDark) MatrixDarkColors else MatrixLightColors
        ThemeOverlay.NONE -> if (isDark) DarkColors else LightColors
    }

    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            var currentTab by remember { mutableStateOf(AppTab.SCHEDULE) }

            LaunchedEffect(Unit) {
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
                onSkip = { version -> otherViewModel.skipUpdate(version) }
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
                        AppTab.OTHER -> {
                            OtherScreen(viewModel = otherViewModel)
                        }
                    }
                }

                val density = LocalDensity.current
                val isImeVisible = WindowInsets.ime.getBottom(density) > 0

                if (!isImeVisible) {
                    FloatingDock(
                        currentTab = currentTab,
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

/**
 * Предупреждение об обновлении: без скачивания и установки — «Обновить»
 * открывает страницу релизов GitHub, «Пропустить» гасит предложение для
 * этой версии (для следующей более новой — покажется снова).
 */
@Composable
private fun UpdateDialog(
    updateResult: com.jetbrains.kmpapp.data.update.UpdateCheckResult?,
    skippedVersion: String?,
    onSkip: (String) -> Unit
) {
    val activeUpdate = updateResult ?: return
    if (!activeUpdate.hasUpdate) return
    if (activeUpdate.urgency != UpdateUrgency.NEW_VERSION && activeUpdate.urgency != UpdateUrgency.CRITICAL) return
    if (skippedVersion == activeUpdate.latestVersion) return

    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = { },
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
            Button(onClick = { uriHandler.openUri(AppVersion.GITHUB_REPO_URL + "/releases/latest") }) {
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
