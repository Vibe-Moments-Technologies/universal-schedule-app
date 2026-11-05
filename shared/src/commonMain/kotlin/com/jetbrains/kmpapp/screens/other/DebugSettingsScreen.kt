package com.jetbrains.kmpapp.screens.other
import com.jetbrains.kmpapp.theme.GlitchText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jetbrains.kmpapp.data.DebugConfig
import com.jetbrains.kmpapp.data.analytics.AppDiagnostics
import com.jetbrains.kmpapp.data.model.AppVersion
import com.jetbrains.kmpapp.data.notifications.NotificationsManager
import com.jetbrains.kmpapp.data.widget.AppRuntimeInfo
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler

@Composable
fun DebugSettingsScreen(
    viewModel: OtherViewModel,
    onBack: () -> Unit,
    onOpenExperimentalSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlatformBackHandler(onBack = onBack)
    val simulateOffline by DebugConfig.isOfflineSimulated.collectAsState()
    val mapCoordinatePlane by DebugConfig.isMapCoordinatePlaneEnabled.collectAsState()
    val testVersionFeed by DebugConfig.isTestVersionFeed.collectAsState()
    val diagnosticsEnabled by viewModel.diagnosticsEnabled.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var testEventSent by remember { mutableStateOf(false) }
    var themesExpanded by remember { mutableStateOf(false) }
    // Не сырой флаг оверлея: в сезон тема включена автоматически, хотя
    // сохранённого оверлея HALLOWEEN может и не быть. Тумблер должен
    // показывать факт, иначе он выглядит выключенным при включённой теме.
    val isHalloweenTheme by viewModel.isHalloweenActive.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshStorageStats()
    }

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Spacer(modifier = Modifier.width(4.dp))
                GlitchText(
                    text = "Отладка и эксперименты",
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
                .verticalScroll(viewModel.scrollState("debug"))
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Режимы")
            DebugSwitchCard(
                title = "Имитировать оффлайн",
                subtitle = "Использовать сохранённые данные без сети",
                checked = simulateOffline,
                onCheckedChange = DebugConfig::setOfflineSimulated
            )
            DebugSwitchCard(
                title = "Координатная плоскость на картах",
                subtitle = "Сетка координат поверх карт для отладки геометрии",
                checked = mapCoordinatePlane,
                onCheckedChange = DebugConfig::setMapCoordinatePlaneEnabled
            )
            // Карты МП-1 (репорт владельца): по умолчанию — НОВАЯ съёмка
            // (инверсия исходного тумблера); тумблер возвращает СТАРУЮ.
            // Персистентен; обе версии карт лежат в ресурсах.
            val oldMp1Map by DebugConfig.isOldMp1MapEnabled.collectAsState()
            DebugSwitchCard(
                title = "Старая съёмка МП-1",
                subtitle = "Прежние карты Малой Пироговской (подвал -1). Выключено — новая съёмка (этажи 0–5, «0» — бывший подвал).",
                checked = oldMp1Map,
                onCheckedChange = DebugConfig::setOldMp1MapEnabled
            )

            // Тестовый фид версионирования (репорт владельца): проверка
            // обновлений читает version-test.json на gh-pages — версии
            // меняются в файле как угодно, смотрим поведение приложения
            // (бейдж, срочность, новая версия). Жёсткое правило: только
            // dev/beta/contrib; стабильный релиз всегда на официальном
            // version.json — тумблер недоступен и флаг погашен.
            SectionTitle("Версионирование")
            DebugSwitchCard(
                title = "Тестовый фид версий",
                subtitle = if (AppVersion.BUILD_CHANNEL in setOf("stable", "rc")) {
                    "Недоступно в стабильном релизе: приложение всегда проверяет официальный version.json."
                } else {
                    "Проверка обновлений читает version-test.json на gh-pages " +
                        "(меняйте версии в файле как угодно). Перезапуск не нужен: " +
                        "выключите — вернётся официальный фид."
                },
                checked = testVersionFeed,
                enabled = AppVersion.BUILD_CHANNEL !in setOf("stable", "rc"),
                onCheckedChange = DebugConfig::setTestVersionFeed
            )
            // Подавление диалога «Вышла новая версия» сбрасывается тапом по
            // оранжевому/красному мини-виджету в шапке «Другого» (репорт
            // владельца): диалог открывается даже при подавлении, галочка
            // в нём управляет показом. Отдельная кнопка сброса убрана.

            // Диагностика (Sentry): opt-in для тестировщиков. Выключена —
            // наружу не уходит ничего вообще. На тестовых каналах (dev/beta/
            // contrib) сбор обязателен: тумблер заблокирован во включённом состоянии.
            SectionTitle("Диагностика")
            DebugSwitchCard(
                title = "Отправка диагностики",
                subtitle = if (AppDiagnostics.isForced) {
                    "Тестовая сборка (канал ${AppVersion.BUILD_CHANNEL}): диагностика включена всегда."
                } else {
                    "Краши и ошибки уходят в Sentry. Только для тестировщиков: " +
                        "включайте на время отладки, данные покидают устройство."
                },
                checked = diagnosticsEnabled || AppDiagnostics.isForced,
                enabled = !AppDiagnostics.isForced,
                onCheckedChange = viewModel::setDiagnosticsEnabled
            )
            DebugCard {
                GlitchText(
                    "Сведения о приложении",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                GlitchText(
                    "bundle id, профиль подписи, App Group виджета и доступность контейнеров.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GlitchText(
                    AppRuntimeInfo.debugInfo(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DebugCard {
                GlitchText(
                    "Проверка связи",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                GlitchText(
                    "Отправляет тестовое событие в Sentry. Через минуту оно появится в проекте — " +
                        "значит, канал диагностики живой.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        AppDiagnostics.sendTestEvent()
                        testEventSent = true
                    },
                    enabled = !testEventSent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GlitchText(if (testEventSent) "Отправлено ✓" else "Отправить тестовое событие")
                }
            }

            SectionTitle("Хранилище")
            DebugCard {
                GlitchText(
                    "Расписаний: ${storageStats.schedulesCount} · пар: ${storageStats.lessonsCount} · размер: ${storageStats.formatBytes(storageStats.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showClearCacheDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GlitchText("Очистить кеш расписаний")
                }
                GlitchText(
                    "Сохранённые группы и настройки останутся.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (NotificationsManager.supportsNotifications) {
                SectionTitle("Уведомления")
                DebugCard {
                    GlitchText(
                        "Мгновенная доставка или будильник через минуту.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { NotificationsManager.sendTest(1_500L) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GlitchText("Отправить сейчас")
                    }
                    OutlinedButton(
                        onClick = { NotificationsManager.sendTest(60_000L) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GlitchText("Запланировать через 1 минуту")
                    }
                }
            }

            SectionTitle("Эксперименты")
            DebugCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenExperimentalSettings),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GlitchText(
                            text = "Экспериментальные параметры",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        GlitchText(
                            text = "Скрытые возможности и секреты",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Открыть",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Раскрываемый раздел «Темы»: пока одна тема — хэллоуинская.
                // Тумблер нужен, чтобы проверить праздничное оформление вне
                // сезона (сама тема включается по дате автоматически:
                // 28 октября — 3 ноября).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { themesExpanded = !themesExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlitchText(
                        text = "Темы",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (themesExpanded) "Свернуть" else "Развернуть",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(if (themesExpanded) 90f else 0f)
                    )
                }
                if (themesExpanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            GlitchText(
                                text = "Хэллоуинская тема",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(2.dp))
                            GlitchText(
                                text = "Тыквы, паутина и кровавый глитч. " +
                                    "С 28 октября по 3 ноября включается сама. " +
                                    "Выключение в сезон запоминается до его конца.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isHalloweenTheme,
                            onCheckedChange = viewModel::setHalloweenTheme
                        )
                    }
                }
            }

            SectionTitle("Опасная зона")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(10.dp))
                        GlitchText("Полный сброс", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    GlitchText(
                        "Удалит расписания, задачи, кеши и все настройки. Приложение вернётся к состоянию после установки.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(onClick = { showResetDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        GlitchText("Сбросить всё")
                    }
                }
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { GlitchText("Очистить кеш расписаний?") },
            text = { GlitchText("Сохранённые расписания будут загружены заново при следующем обновлении.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearCache()
                    showClearCacheDialog = false
                }) { GlitchText("Очистить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearCacheDialog = false }) { GlitchText("Отмена") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { GlitchText("Сбросить все данные?") },
            text = { GlitchText("Это удалит все сохранённые данные и настройки без возможности восстановления.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.resetAllData()
                    showResetDialog = false
                    onBack()
                }) { GlitchText("Сбросить всё") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetDialog = false }) { GlitchText("Отмена") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    GlitchText(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun DebugCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun DebugSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    DebugCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                GlitchText(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                GlitchText(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}
