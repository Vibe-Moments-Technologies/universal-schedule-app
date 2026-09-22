package com.jetbrains.kmpapp.screens.other

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.data.notifications.NotificationsManager
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler

@Composable
fun SettingsScreen(
    viewModel: OtherViewModel,
    onBack: () -> Unit,
    onOpenTaskSettings: () -> Unit,
    onOpenScheduleDisplay: () -> Unit = {},
    onOpenScheduleProgress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    PlatformBackHandler(onBack = onBack)

    val showEmptyLessons by viewModel.showEmptyLessons.collectAsState()
    val showLessonProgress by viewModel.showLessonProgress.collectAsState()
    val showEmptyLessonProgress by viewModel.showEmptyLessonProgress.collectAsState()
    val showBreakProgress by viewModel.showBreakProgress.collectAsState()
    val autoScrollToCurrentLesson by viewModel.autoScrollToCurrentLesson.collectAsState()
    val showAbbreviatedNames by viewModel.showAbbreviatedNames.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isSakuraTheme by viewModel.isSakuraTheme.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val notifyMinutesBefore by viewModel.notifyMinutesBefore.collectAsState()
    val tasksEnabled by viewModel.tasksEnabled.collectAsState()
    val analyticsEnabled by viewModel.analyticsEnabled.collectAsState()

    var sakuraTapCount by remember { mutableIntStateOf(0) }
    var lastSakuraTapMark by remember { mutableStateOf<kotlin.time.TimeMark?>(null) }
    var showSakuraDialog by remember { mutableStateOf(false) }
    var showCustomMinutesDialog by remember { mutableStateOf(false) }
    var customMinutesDraft by remember { mutableStateOf("") }

    // Скролл живёт в ViewModel: LayeredNavHost пересоздаёт этот экран в
    // другом слое при переходе в подраздел — общий ScrollState переживает
    // пересоздание, позиция не сбрасывается (как список расписания).
    val scrollState = viewModel.settingsScrollState

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Настройки",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        modifier = modifier
            .fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: Appearance
            SettingsSectionCard(
                title = "Внешний вид",
                icon = Icons.Default.Palette,
                onIconClick = {
                    val mark = lastSakuraTapMark
                    if (mark != null && mark.elapsedNow().inWholeMilliseconds < 1500L) {
                        sakuraTapCount++
                    } else {
                        sakuraTapCount = 1
                    }
                    lastSakuraTapMark = kotlin.time.TimeSource.Monotonic.markNow()
                    if (sakuraTapCount >= 8) {
                        sakuraTapCount = 0
                        showSakuraDialog = true
                    }
                }
            ) {
                Text(
                    text = "Тема приложения",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Выберите желаемый стиль интерфейса",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    text = mode.displayName,
                                    fontSize = 13.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section: Разделы приложения
            SettingsSectionCard(
                title = "Разделы",
                icon = Icons.Default.Tune
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Задачи",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Показывать раздел «Задачи» в нижней панели",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = tasksEnabled,
                        onCheckedChange = { viewModel.setTasksEnabled(it) }
                    )
                }
            }

            // Section: Schedule — ссылки на подстраницы
            SettingsSectionCard(
                title = "Расписание",
                icon = Icons.Default.CalendarMonth
            ) {
                SettingsNavigationRow(
                    title = "Отображение",
                    subtitle = "Пустые пары, сокращения, авто-скролл",
                    onClick = onOpenScheduleDisplay
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                SettingsNavigationRow(
                    title = "Прогресс и индикаторы",
                    subtitle = "Полоски времени, прогресс перемены",
                    onClick = onOpenScheduleProgress
                )
            }

            // Section: Lesson notifications (платформенный движок)
            if (NotificationsManager.supportsNotifications) {
                SettingsSectionCard(
                    title = "Уведомления",
                    icon = Icons.Default.Notifications
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Напоминать о занятиях",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Локальное напоминание до начала пары. Работает без интернета, прямо на устройстве",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                        )
                    }

                    if (notificationsEnabled) {
                        val presets = listOf(5, 10, 15)
                        val isCustom = notifyMinutesBefore !in presets

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "За сколько минут до пары",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { minutes ->
                                FilterChip(
                                    selected = notifyMinutesBefore == minutes,
                                    onClick = { viewModel.setNotifyMinutesBefore(minutes) },
                                    label = {
                                        Text(
                                            text = "$minutes мин",
                                            fontSize = 13.sp,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            FilterChip(
                                selected = isCustom,
                                onClick = {
                                    customMinutesDraft = if (isCustom) notifyMinutesBefore.toString() else "20"
                                    showCustomMinutesDialog = true
                                },
                                label = {
                                    Text(
                                        text = "Своё",
                                        fontSize = 13.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (isCustom) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Своё: $notifyMinutesBefore мин",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = {
                                        customMinutesDraft = notifyMinutesBefore.toString()
                                        showCustomMinutesDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Изменить время")
                                }
                            }
                        }
                    }
                }
            }

            // Section: Статистика (AppMetrica)
            SettingsSectionCard(
                title = "Статистика",
                icon = Icons.Default.Analytics
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Отправлять анонимную статистику",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Помогает находить падения и понимать, какие разделы чаще используются. Анонимно, без личных данных и содержимого расписания",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = analyticsEnabled,
                        onCheckedChange = { viewModel.setAnalyticsEnabled(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showCustomMinutesDialog) {
        AlertDialog(
            onDismissRequest = { showCustomMinutesDialog = false },
            title = { Text("Своё время") },
            text = {
                OutlinedTextField(
                    value = customMinutesDraft,
                    onValueChange = { text ->
                        customMinutesDraft = text.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("Минут до пары (1–120)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        customMinutesDraft.toIntOrNull()
                            ?.takeIf { it in 1..120 }
                            ?.let(viewModel::setNotifyMinutesBefore)
                        showCustomMinutesDialog = false
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomMinutesDialog = false }) {
                    Text("Отменить")
                }
            }
        )
    }

    if (showSakuraDialog) {
        AlertDialog(
            onDismissRequest = { showSakuraDialog = false },
            title = { Text("Сакура 🌸", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Очередной тыкальщик? 😏\n\nРаз уж ты нашёл этот секрет — держи эксклюзивную тему «Сакура» в нежных пастельно-розовых тонах!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Сакура",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        androidx.compose.material3.Switch(
                            checked = isSakuraTheme,
                            onCheckedChange = { viewModel.setSakuraTheme(it) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSakuraDialog = false }) {
                    Text("Готово")
                }
            }
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Открыть",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    onIconClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(22.dp)
                        .then(
                            if (onIconClick != null) {
                                // Без ripple: визуальная анимация нажатия выдавала скрытую кнопку
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onIconClick
                                )
                            } else Modifier
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
