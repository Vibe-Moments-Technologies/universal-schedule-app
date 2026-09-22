package com.jetbrains.kmpapp.screens.other

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jetbrains.kmpapp.screens.components.LayeredNavHost

@Composable
fun OtherScreen(
    viewModel: OtherViewModel,
    tasksViewModel: com.jetbrains.kmpapp.screens.tasks.TasksViewModel = org.koin.compose.viewmodel.koinViewModel(),
    modifier: Modifier = Modifier
) {
    val activeSubScreen by viewModel.activeSubScreen.collectAsState()

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
        rootContent = {
            OtherMainContent(
                viewModel = viewModel,
                onNavigate = { viewModel.navigateToSubScreen(it) }
            )
        },
        screenContent = { subScreen, back ->
            when (subScreen as OtherSubScreen) {
                OtherSubScreen.ROOT -> {}
                OtherSubScreen.CONFIGURATOR -> {
                    com.jetbrains.kmpapp.screens.configurator.ConfiguratorScreen(onBack = back)
                }
                OtherSubScreen.SETTINGS -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = back,
                        onOpenTaskSettings = { viewModel.navigateToSubScreen(OtherSubScreen.TASK_SETTINGS) },
                        onOpenScheduleDisplay = { viewModel.navigateToSubScreen(OtherSubScreen.SCHEDULE_DISPLAY) },
                        onOpenScheduleProgress = { viewModel.navigateToSubScreen(OtherSubScreen.SCHEDULE_PROGRESS) }
                    )
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
            }
        },
        modifier = modifier
    )
}

@Composable
private fun OtherMainContent(
    viewModel: OtherViewModel,
    onNavigate: (OtherSubScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val semester by viewModel.semester.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateResult by viewModel.updateResult.collectAsState()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Другое",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
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
            // 1. Конфигуратор расписания — главный инструмент приложения.
            OtherNavCard(
                title = "Конфигуратор расписания",
                subtitle = semester?.let {
                    "${it.university} · ${it.group} · ${it.weeksCount} нед."
                } ?: "Соберите семестр: вуз, группа, курс и звонки",
                icon = Icons.Default.Construction,
                onClick = { onNavigate(OtherSubScreen.CONFIGURATOR) }
            )

            // 2. Settings card
            OtherNavCard(
                title = "Настройки",
                subtitle = "Оформление, тема, разделы",
                icon = Icons.Default.Tune,
                onClick = { onNavigate(OtherSubScreen.SETTINGS) }
            )

            // 3. О программе — версия, команда, лицензии.
            OtherNavCard(
                title = "О программе",
                subtitle = "Версия, команда проекта, лицензии",
                icon = Icons.Default.Info,
                onClick = { onNavigate(OtherSubScreen.ABOUT) }
            )

            // 4. App Version / Auto-Update Card
            UpdateStatusCard(
                updateResult = updateResult,
                isCheckingUpdate = isCheckingUpdate,
                onCheckForUpdates = { viewModel.checkForUpdates() }
            )

            Spacer(modifier = Modifier.height(2.dp))
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
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
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
