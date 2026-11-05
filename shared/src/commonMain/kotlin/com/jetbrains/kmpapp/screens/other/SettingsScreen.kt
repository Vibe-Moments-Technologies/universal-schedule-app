package com.jetbrains.kmpapp.screens.other
import com.jetbrains.kmpapp.theme.GlitchText

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.appicon.AppIconManager
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.data.notifications.NotificationsManager
import com.jetbrains.kmpapp.data.widget.WidgetPinFeedback
import com.jetbrains.kmpapp.data.widget.isScheduleWidgetPinningSupported
import com.jetbrains.kmpapp.data.widget.requestScheduleWidgetPin
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Ждать открытия конфигуратора виджета после запроса закрепления, прежде
 * чем решить, что системный диалог был подавлен (OEM-запрет). 5 секунд
 * хватает на подтверждение в системном окне.
 */
private const val WIDGET_PIN_DIALOG_TIMEOUT_MS = 5_000L

/** «По умолчанию» в конфигураторах виджета: цель уведомлений (id < 0). */
private const val DEFAULT_WIDGET_TARGET_ID = -1

@Composable
fun SettingsScreen(
    viewModel: OtherViewModel,
    onBack: () -> Unit,
    onOpenDataAndCache: () -> Unit,
    onOpenDockSettings: () -> Unit,
    onOpenTaskSettings: () -> Unit,
    onOpenIconPicker: () -> Unit = {},
    onOpenScheduleDisplay: () -> Unit = {},
    onOpenScheduleProgress: () -> Unit = {},
    onOpenScheduleCalendar: () -> Unit = {},
    onOpenServiceSettings: () -> Unit = {},
    onOpenSaperSettings: () -> Unit = {},
    onOpenThemedThemes: () -> Unit = {},
    onOpenLessonColors: () -> Unit = {},
    onOpenTetrisSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    PlatformBackHandler(onBack = onBack)

    val showEmptyLessons by viewModel.showEmptyLessons.collectAsState()
    val hideAdditionalLessons by viewModel.hideAdditionalLessons.collectAsState()
    val showLessonProgress by viewModel.showLessonProgress.collectAsState()
    val showEmptyLessonProgress by viewModel.showEmptyLessonProgress.collectAsState()
    val showBreakProgress by viewModel.showBreakProgress.collectAsState()
    val calendarSwipeCollapse by viewModel.calendarSwipeCollapse.collectAsState()
    val autoScrollToCurrentLesson by viewModel.autoScrollToCurrentLesson.collectAsState()
    val showAbbreviatedNames by viewModel.showAbbreviatedNames.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isKawaiiTheme by viewModel.isKawaiiTheme.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val notifyMinutesBefore by viewModel.notifyMinutesBefore.collectAsState()
    val notificationsTargetId by viewModel.notificationsTargetId.collectAsState()
    val vpnWarningEnabled by viewModel.vpnWarningEnabled.collectAsState()
    val savedTargets by viewModel.savedTargets.collectAsState()
    val askBeforeNoteDelete by viewModel.askBeforeNoteDelete.collectAsState()
    val dockHidden by viewModel.dockHidden.collectAsState()

    var sakuraTapCount by remember { mutableIntStateOf(0) }
    var lastSakuraTapMark by remember { mutableStateOf<kotlin.time.TimeMark?>(null) }
    var showKawaiiDialog by remember { mutableStateOf(false) }
    var showCustomMinutesDialog by remember { mutableStateOf(false) }
    var customMinutesDraft by remember { mutableStateOf("") }
    var showNotificationsTargetDialog by remember { mutableStateOf(false) }
    var showWidgetPinFailedDialog by remember { mutableStateOf(false) }
    // Предупреждения — диалогами поверх экрана (решение владельца):
    // снекбар внизу не виден за навигационной панелью.
    var showNotificationsDeniedDialog by remember { mutableStateOf(false) }
    // Внутренний конфигуратор виджета (добавление из приложения).
    var showWidgetConfigDialog by remember { mutableStateOf(false) }
    var widgetConfigTargetId by remember { mutableStateOf<Int?>(null) }
    var widgetConfigShortNames by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Пользователь мог выдать разрешение в системном диалоге и вернуться:
    // откатываем «включено без разрешения» (старые установки/перенос),
    // чтобы переключатель не врал. false НЕ запрашивает разрешение —
    // иначе системный диалог (resumed-activity) ронял приложение
    // при первом входе на экран.
    LaunchedEffect(Unit) {
        snapshotFlow { notificationsEnabled }
            .collect { enabled ->
                if (enabled && !NotificationsManager.isAuthorized()) {
                    viewModel.setNotificationsEnabled(false)
                }
            }
    }

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
                GlitchText(
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
                        // Квест из восьми быстрых тычков по иконке раздела
                        // теперь выдаёт «Кавай»: Сакура доступна по умолчанию
                        // и своего квента больше не имеет.
                        showKawaiiDialog = true
                    }
                }
            ) {
                GlitchText(
                    text = "Тема приложения",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                GlitchText(
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
                                GlitchText(
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

                // App icon picker: only where the platform supports it (iOS)
                if (AppIconManager.supportsSwitching) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenIconPicker)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            GlitchText(
                                text = "Иконка приложения",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            GlitchText(
                                text = "Новая или старая · тема — автоматически",
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

                // Тематические темы: сакура по умолчанию плюс найденные
                // квестами. Отдельная подстраница с тумблерами.
                Spacer(modifier = Modifier.height(12.dp))
                SettingsNavigationRow(
                    title = "Тематические темы",
                    subtitle = "Сакура, кавай и найденные квестами",
                    onClick = onOpenThemedThemes
                )

                // Цвета типов занятий: бейджи карточек и точки календаря
                Spacer(modifier = Modifier.height(12.dp))
                SettingsNavigationRow(
                    title = "Цвета занятий",
                    subtitle = "Акценты для лекций, практик, лабораторных",
                    onClick = onOpenLessonColors
                )
            }

            // Section: Navigation & Dock
            SettingsSectionCard(
                title = "Интерфейс и навигация",
                icon = Icons.Default.Tune
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenDockSettings)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GlitchText(
                            text = "Настройка нижней панели",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        GlitchText(
                            text = "Порядок и состав страниц на панели",
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
                // Скрыть док целиком: «Другое» становится подстраницей
                // расписания (шестерёнка в топбаре), сервисы — блоками в нём.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GlitchText(
                            text = "Скрывать панель навигации",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        GlitchText(
                            text = "«Другое» откроется шестерёнкой на странице расписания, сервисы — блоками внутри него",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = dockHidden, onCheckedChange = viewModel::setDockHidden)
                }
            }

            // Section: Tasks Settings (temporarily hidden)
            /*
            SettingsSectionCard(
                title = "Задачи и дедлайны",
                icon = Icons.Default.TaskAlt
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenTaskSettings)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GlitchText(
                            text = "Настройки задач",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        GlitchText(
                            text = "Генератор лабораторных, приоритеты, очистка базы",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Открыть",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            */

            // Section: Schedule — ссылки на подстраницы
            SettingsSectionCard(
                title = "Расписание",
                icon = Icons.Default.CalendarMonth
            ) {
                SettingsNavigationRow(
                    title = "Отображение",
                    subtitle = "Пустые пары, ДОП-занятия, сокращения, авто-скролл",
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
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                SettingsNavigationRow(
                    title = "Календарь",
                    subtitle = "Сворачивание свайпом",
                    onClick = onOpenScheduleCalendar
                )
            }

            // Section: Widget — закрепление на главном экране
            if (isScheduleWidgetPinningSupported()) {
                SettingsSectionCard(
                    title = "Виджет",
                    icon = Icons.Default.Widgets
                ) {
                    SettingsActionRow(
                        title = "Добавить на главный экран",
                        subtitle = "Выберите расписание — система предложит разместить виджет. Убрать — долгим нажатием → «Удалить»",
                        icon = Icons.Default.Add,
                        onClick = {
                            // Внутренний конфигуратор (решение владельца):
                            // лаунчеры не показывают системный при добавлении
                            // из приложения, поэтому параметры выбираем здесь.
                            showWidgetConfigDialog = true
                        }
                    )
                }
            }

            // Section: Service settings
            SettingsSectionCard(
                title = "Параметры сервисов",
                icon = Icons.Default.EditNote
            ) {
                SettingsNavigationRow(
                    title = "Конспекты",
                    subtitle = "Настройки блокнота и заметок",
                    onClick = onOpenServiceSettings
                )
            }

            // Section: Games — настройки «Сапера» и «Тетриса»
            SettingsSectionCard(
                title = "Игры",
                icon = Icons.Default.Bolt
            ) {
                SettingsNavigationRow(
                    title = "Сапер",
                    subtitle = "Ориентация доски, предупреждение о мелких клетках",
                    onClick = onOpenSaperSettings
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                SettingsNavigationRow(
                    title = "Тетрис",
                    subtitle = "Вариант управления, сложность и скорость падения",
                    onClick = onOpenTetrisSettings
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
                            GlitchText(
                                text = "Напоминать о занятиях",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            GlitchText(
                                text = "Локальное напоминание до начала пары выбранного расписания. Работает без интернета, прямо на устройстве",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { wanted ->
                                // Статус читается в момент клика (без remember-кэша):
                                // после выдачи прав в системном окне повторное
                                // включение проходит сразу, без перезапуска
                                // (репорт тестера).
                                if (wanted && !NotificationsManager.isAuthorized()) {
                                    // Первая установка: явно просим системное
                                    // разрешение (тумблер остаётся «выкл» —
                                    // включится после выдачи). Системный диалог
                                    // асинхронен; если выдали — повторное
                                    // включение пройдёт мгновенно; отказ дважды
                                    // (ОС прячет диалог навсегда) — предупреждение
                                    // поверх экрана объясняет путь через настройки.
                                    NotificationsManager.requestAuthorization()
                                    showNotificationsDeniedDialog = true
                                } else {
                                    viewModel.setNotificationsEnabled(wanted)
                                }
                            }
                        )
                    }

                    // Выбор цели — всегда доступен (репорт тестера): цель
                    // уведомлений — ещё и дефолт для виджетов («По умолчанию»
                    // в их конфигураторах). Прятать её за включённый тумблер
                    // значило «без уведомлений нельзя менять группу виджета».
                    val notificationTarget = savedTargets.firstOrNull { it.id == notificationsTargetId }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showNotificationsTargetDialog = true }
                            .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                GlitchText(
                                    text = "Расписание для уведомлений",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                GlitchText(
                                    text = notificationTarget?.fullTitle ?: "Выберите расписание",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Выбрать расписание",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val presets = listOf(5, 10, 15)
                        val isCustom = notifyMinutesBefore !in presets

                        Spacer(modifier = Modifier.height(12.dp))
                        GlitchText(
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
                                        GlitchText(
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
                                    GlitchText(
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
                                GlitchText(
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

            // Section: Additional features (объединённый блок)
            SettingsSectionCard(
                title = "Дополнительный функционал",
                icon = Icons.Default.Tune
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GlitchText(
                            text = "Предупреждения о VPN",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        GlitchText(
                            text = "Показывать предупреждение, если VPN может помешать обновлению расписания",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = vpnWarningEnabled,
                        onCheckedChange = { viewModel.setVpnWarningEnabled(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showWidgetPinFailedDialog) {
        AlertDialog(
            onDismissRequest = { showWidgetPinFailedDialog = false },
            title = { GlitchText("Не удалось добавить виджет") },
            text = {
                GlitchText(
                    "Добавьте его вручную: долгое нажатие на рабочем столе → «Виджеты» → «Красава» → «Расписание»."
                )
            },
            confirmButton = {
                TextButton(onClick = { showWidgetPinFailedDialog = false }) {
                    GlitchText("Понятно")
                }
            }
        )
    }

    if (showWidgetConfigDialog) {
        // Внутренний конфигуратор виджета (решение владельца): лаунчеры
        // не показывают системный конфигуратор при добавлении из
        // приложения — выбираем параметры здесь, закрепление после
        // подтверждения применит их к новому экземпляру молча
        // (ScheduleWidgetApplyActivity из success-callback).
        AlertDialog(
            onDismissRequest = { showWidgetConfigDialog = false },
            title = { GlitchText("Виджет расписания") },
            text = {
                if (savedTargets.isEmpty()) {
                    GlitchText("Сначала добавьте хотя бы одно расписание.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GlitchText(
                            text = "Расписание",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        // «По умолчанию» — цель уведомлений, как в системном
                        // конфигураторе: тот же фолбэк движка виджета.
                        val configRows = savedTargets
                        val effectiveId = widgetConfigTargetId
                            ?: notificationsTargetId
                            ?: configRows.firstOrNull()?.id
                        configRows.forEach { target ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { widgetConfigTargetId = target.id }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    GlitchText(
                                        text = target.fullTitle,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                RadioButton(
                                    selected = target.id == effectiveId,
                                    onClick = { widgetConfigTargetId = target.id }
                                )
                            }
                        }
                        GlitchText(
                            text = "«По умолчанию» = расписание уведомлений. Виджеты без своего выбора берут его же.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { widgetConfigShortNames = !widgetConfigShortNames }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                GlitchText(
                                    text = "Сокращать длинные названия",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                GlitchText(
                                    text = "Инициалы слов: «Базы данных и управление ими» → «БДиУИ»",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(
                                checked = widgetConfigShortNames,
                                onCheckedChange = { widgetConfigShortNames = it }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWidgetConfigDialog = false
                        if (savedTargets.isEmpty()) return@TextButton
                        // Конкретная цель либо «По умолчанию» (-1 = цель
                        // уведомлений) — тот же контракт, что у системного
                        // конфигуратора и ApplyActivity.
                        val chosen = widgetConfigTargetId
                            ?: notificationsTargetId
                            ?: savedTargets.firstOrNull()?.id
                        val targetId = chosen ?: DEFAULT_WIDGET_TARGET_ID
                        WidgetPinFeedback.setPendingConfig(targetId, widgetConfigShortNames)
                        if (requestScheduleWidgetPin()) {
                            // OEM-запрет может подавить диалог молча: ждём
                            // метку «закрепление подтверждено» (её ставит
                            // ScheduleWidgetApplyActivity), её нет —
                            // предупреждение поверх экрана.
                            WidgetPinFeedback.markPinRequested()
                            val requestedAt = WidgetPinFeedback.lastPinRequestedAtMillis
                            scope.launch {
                                delay(WIDGET_PIN_DIALOG_TIMEOUT_MS)
                                if (WidgetPinFeedback.lastConfigureOpenedAtMillis < requestedAt) {
                                    showWidgetPinFailedDialog = true
                                }
                            }
                        } else {
                            showWidgetPinFailedDialog = true
                        }
                    }
                ) {
                    GlitchText("Добавить виджет")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWidgetConfigDialog = false }) {
                    GlitchText("Отмена")
                }
            }
        )
    }

    if (showNotificationsDeniedDialog) {
        // Диалог поверх экрана (решение владельца: снекбар внизу не виден).
        // Тумблер уже вернулся в «выкл»: объясняем, что делать дальше.
        AlertDialog(
            onDismissRequest = { showNotificationsDeniedDialog = false },
            title = { GlitchText("Уведомления не разрешены") },
            text = {
                GlitchText(
                    "Напоминания включатся, только когда система разрешит приложению показывать уведомления. " +
                        "Разрешите их для «Красавы» в настройках телефона и вернитесь включить напоминания."
                )
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDeniedDialog = false }) {
                    GlitchText("Понятно")
                }
            }
        )
    }

    if (showNotificationsTargetDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsTargetDialog = false },
            title = { GlitchText("Расписание для уведомлений") },
            text = {
                if (savedTargets.isEmpty()) {
                    GlitchText("Сначала добавьте хотя бы одно расписание.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        savedTargets.forEach { target ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setNotificationsTargetId(target.id)
                                        showNotificationsTargetDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    GlitchText(
                                        text = target.fullTitle,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    GlitchText(
                                        text = target.type.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (target.id == notificationsTargetId) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Выбрано",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsTargetDialog = false }) {
                    GlitchText("Закрыть")
                }
            }
        )
    }

    if (showCustomMinutesDialog) {
        AlertDialog(
            onDismissRequest = { showCustomMinutesDialog = false },
            title = { GlitchText("Своё время") },
            text = {
                OutlinedTextField(
                    value = customMinutesDraft,
                    onValueChange = { text ->
                        customMinutesDraft = text.filter { it.isDigit() }.take(3)
                    },
                    label = { GlitchText("Минут до пары (1–120)") },
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
                    GlitchText("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomMinutesDialog = false }) {
                    GlitchText("Отменить")
                }
            }
        )
    }

    if (showKawaiiDialog) {
        AlertDialog(
            onDismissRequest = { showKawaiiDialog = false },
            title = { GlitchText("Кавай 🎀", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    GlitchText(
                        text = "Очередной тыкальщик? 😏\n\nРаз уж ты нашёл этот секрет — держи эксклюзивную тему «Кавай»: полностью анимешная, милая, в бело-розовых тонах!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlitchText(
                            text = "Кавай",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        androidx.compose.material3.Switch(
                            checked = isKawaiiTheme,
                            onCheckedChange = { viewModel.setKawaiiTheme(it) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showKawaiiDialog = false }) {
                    GlitchText("Готово")
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
            GlitchText(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            GlitchText(
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
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
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
            GlitchText(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            GlitchText(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary
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
                GlitchText(
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
