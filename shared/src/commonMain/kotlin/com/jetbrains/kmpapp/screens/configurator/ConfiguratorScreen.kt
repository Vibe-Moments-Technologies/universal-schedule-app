package com.jetbrains.kmpapp.screens.configurator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jetbrains.kmpapp.data.ScheduleRepository
import com.jetbrains.kmpapp.data.model.LessonBells
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.ScheduleEntry
import com.jetbrains.kmpapp.data.model.SemesterConfig
import com.jetbrains.kmpapp.data.model.WeekParity
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.random.Random
import kotlin.time.Clock

private val DAY_NAMES = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

/** «930» → «9:30» → «09:30»: маска времени по мере ввода цифр. */
private fun formatTimeInput(text: String): String {
    val d = text.filter(Char::isDigit).take(4)
    return if (d.length == 4) "${d.take(2)}:${d.drop(2)}" else d
}

private fun newEntryId(): String =
    "e${Clock.System.now().toEpochMilliseconds()}${Random.nextInt(1000, 9999)}"

/** Понедельник текущей недели — дефолт начала семестра. */
private fun currentMonday(): LocalDate {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
}

private fun formatDate(date: LocalDate?): String {
    if (date == null) return "Выберите дату"
    return "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
}

private fun millisToDate(millis: Long?): LocalDate? =
    millis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }

/**
 * Конфигуратор семестра: единственный источник расписания в приложении.
 * Форма (вуз, группа, курс, семестр, даты, звонки) + недельный шаблон занятий.
 * Всё локально: экспорт/импорт — JSON формата universal-schedule v1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguratorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository: ScheduleRepository = koinInject()
    val savedConfig by repository.semester.collectAsState()

    var config by remember(savedConfig) {
        mutableStateOf(savedConfig ?: SemesterConfig(startDate = currentMonday()))
    }
    var selectedDay by remember { mutableStateOf(1) }
    var showSaveError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showBellsSheet by remember { mutableStateOf(false) }
    var showDataSheet by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<ScheduleEntry?>(null) }
    var showEntrySheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    PlatformBackHandler(onBack = onBack)

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
                Text(
                    text = "Конфигуратор расписания",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    val error = repository.saveSemester(config)
                    if (error != null) showSaveError.value = error else onBack()
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Сохранить", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Основное ────────────────────────────────────────────
            SectionCard(title = "Основное") {
                OutlinedTextField(
                    value = config.university,
                    onValueChange = { config = config.copy(university = it) },
                    label = { Text("Учебное заведение *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = config.group,
                    onValueChange = { config = config.copy(group = it) },
                    label = { Text("Группа *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = config.course.toString(),
                        onValueChange = { text ->
                            config = config.copy(course = text.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0)
                        },
                        label = { Text("Курс *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = config.semesterNumber.toString(),
                        onValueChange = { text ->
                            config = config.copy(semesterNumber = text.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0)
                        },
                        label = { Text("Семестр *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = config.semesterTitle,
                    onValueChange = { config = config.copy(semesterTitle = it) },
                    label = { Text("Название семестра (например «Осень 2026»)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Начало семестра (понедельник 1-й недели): ${formatDate(config.startDate)}")
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = config.weeksCount.toString(),
                    onValueChange = { text ->
                        config = config.copy(weeksCount = text.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0)
                    },
                    label = { Text("Количество недель *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Звонки ──────────────────────────────────────────────
            SectionCard(title = "Звонки (${config.bells.size})") {
                Text(
                    text = "Время пар используется в карточках, прогрессе и уведомлениях.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showBellsSheet = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Настроить время пар")
                }
            }

            // ── Недельный шаблон ────────────────────────────────────
            SectionCard(title = "Неделя") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DAY_NAMES.size) { index ->
                        val day = index + 1
                        val count = config.entries.count { it.dayOfWeek == day }
                        FilterChip(
                            selected = selectedDay == day,
                            onClick = { selectedDay = day },
                            label = { Text(if (count > 0) "${DAY_NAMES[index]} · $count" else DAY_NAMES[index]) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                val dayEntries = config.entries
                    .filter { it.dayOfWeek == selectedDay }
                    .sortedWith(compareBy({ it.bellNumber }, { it.id }))
                if (dayEntries.isEmpty()) {
                    Text(
                        text = "В этот день занятий нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                dayEntries.forEach { entry ->
                    val bell = config.bells.firstOrNull { it.number == entry.bellNumber }
                    EntryRow(
                        entry = entry,
                        bellText = bell?.let { "${it.startTime}–${it.endTime}" } ?: "звонок не задан",
                        onEdit = {
                            editingEntry.value = entry
                            showEntrySheet = true
                        },
                        onDelete = {
                            config = config.copy(entries = config.entries.filter { it.id != entry.id })
                        }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        editingEntry.value = null
                        showEntrySheet = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Добавить занятие")
                }
            }

            // ── Данные ──────────────────────────────────────────────
            SectionCard(title = "Данные") {
                OutlinedButton(
                    onClick = { showDataSheet = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Экспорт / импорт (JSON)")
                }
                if (savedConfig != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Удалить семестр", color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        text = "Задачи не удаляются вместе с семестром — только вручную.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // Диалог ошибки сохранения
    showSaveError.value?.let { error ->
        AlertDialog(
            onDismissRequest = { showSaveError.value = null },
            title = { Text("Не хватает данных") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { showSaveError.value = null }) { Text("ОК") }
            }
        )
    }

    // Дата начала семестра
    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = config.startDate
                .toEpochDays().toLong().let { days -> days * 86_400_000L }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    millisToDate(dateState.selectedDateMillis)?.let { picked ->
                        // Недели всегда начинаются в понедельник.
                        config = config.copy(startDate = picked.minus(DatePeriod(days = picked.dayOfWeek.ordinal)))
                    }
                    showDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    // Редактор звонков
    if (showBellsSheet) {
        BellsSheet(
            bells = config.bells,
            onDismiss = { showBellsSheet = false },
            onSave = { bells ->
                // Занятия на несуществующие звонки удаляем.
                val numbers = bells.map { it.number }.toSet()
                config = config.copy(
                    bells = bells,
                    entries = config.entries.filter { it.bellNumber in numbers }
                )
                showBellsSheet = false
            }
        )
    }

    // Экспорт / импорт
    if (showDataSheet) {
        DataSheet(
            repository = repository,
            onDismiss = { showDataSheet = false },
            onImported = { error ->
                showDataSheet = false
                if (error != null) {
                    showSaveError.value = error
                } else {
                    onBack()
                }
            }
        )
    }

    // Редактор занятия
    if (showEntrySheet) {
        EntrySheet(
            entry = editingEntry.value,
            dayOfWeek = selectedDay,
            bells = config.bells,
            weeksCount = config.weeksCount,
            onDismiss = { showEntrySheet = false },
            onSave = { entry ->
                val existing = config.entries.any { it.id == entry.id }
                config = config.copy(
                    entries = if (existing) {
                        config.entries.map { if (it.id == entry.id) entry else it }
                    } else {
                        config.entries + entry
                    }
                )
                showEntrySheet = false
            }
        )
    }

    // Удаление семестра
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить семестр?") },
            text = { Text("Расписание и заметки к парам будут удалены. Задачи останутся — их можно удалить вручную.") },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteSemester()
                    showDeleteConfirm = false
                    onBack()
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun EntryRow(
    entry: ScheduleEntry,
    bellText: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${entry.bellNumber} пара · $bellText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.lessonType.shortName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = entry.subject,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            val details = listOfNotNull(
                entry.teacher.takeIf { it.isNotBlank() },
                entry.classroom.takeIf { it.isNotBlank() }?.let { "ауд. $it" },
                entry.subgroup.takeIf { it.isNotBlank() },
                if (entry.parity != WeekParity.ALL) entry.parity.displayName else null
            )
            if (details.isNotEmpty() || entry.firstWeek > 1 || entry.lastWeek > 0) {
                val weeks = if (entry.firstWeek > 1 || entry.lastWeek > 0) {
                    "нед. ${entry.firstWeek}–${if (entry.lastWeek > 0) entry.lastWeek.toString() else "конец"}"
                } else null
                Text(
                    text = (details + listOfNotNull(weeks)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Изменить")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}

/** Редактор времени пар. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BellsSheet(
    bells: List<LessonBells>,
    onDismiss: () -> Unit,
    onSave: (List<LessonBells>) -> Unit
) {
    var draft by remember { mutableStateOf(bells) }
    var error by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Время пар", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Формат ЧЧ:ММ. Номер пары — порядок в списке.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            draft.forEachIndexed { index, bell ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                    OutlinedTextField(
                        value = bell.startTime,
                        onValueChange = { text ->
                            val formatted = formatTimeInput(text)
                            draft = draft.toMutableList().also { list -> list[index] = bell.copy(startTime = formatted) }
                        },
                        label = { Text("Начало") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = bell.endTime,
                        onValueChange = { text ->
                            val formatted = formatTimeInput(text)
                            draft = draft.toMutableList().also { list -> list[index] = bell.copy(endTime = formatted) }
                        },
                        label = { Text("Конец") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { draft = draft.filterIndexed { i, _ -> i != index } }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить звонок", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val nextNumber = (draft.maxOfOrNull { it.number } ?: 0) + 1
                    draft = draft + LessonBells(nextNumber, "09:00", "10:30")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Добавить пару")
            }
            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (draft.isEmpty()) {
                        error = "Должна быть хотя бы одна пара"
                        return@Button
                    }
                    val badTime = draft.any { !TIME_REGEX.matches(it.startTime) || !TIME_REGEX.matches(it.endTime) }
                    if (badTime) {
                        error = "Проверьте время: формат ЧЧ:ММ"
                        return@Button
                    }
                    // Перенумеровываем по порядку списка.
                    onSave(draft.mapIndexed { index, bell -> bell.copy(number = index + 1) })
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить звонки")
            }
        }
    }
}

/** Редактор одного занятия недельного шаблона. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(
    entry: ScheduleEntry?,
    dayOfWeek: Int,
    bells: List<LessonBells>,
    weeksCount: Int,
    onDismiss: () -> Unit,
    onSave: (ScheduleEntry) -> Unit
) {
    var subject by remember { mutableStateOf(entry?.subject ?: "") }
    var bellNumber by remember { mutableStateOf(entry?.bellNumber ?: bells.firstOrNull()?.number ?: 1) }
    var lessonType by remember { mutableStateOf(entry?.lessonType ?: LessonType.OTHER) }
    var teacher by remember { mutableStateOf(entry?.teacher ?: "") }
    var classroom by remember { mutableStateOf(entry?.classroom ?: "") }
    var subgroup by remember { mutableStateOf(entry?.subgroup ?: "") }
    var parity by remember { mutableStateOf(entry?.parity ?: WeekParity.ALL) }
    var firstWeek by remember { mutableStateOf((entry?.firstWeek ?: 1).toString()) }
    var lastWeek by remember { mutableStateOf((entry?.lastWeek ?: 0).let { if (it == 0) "" else it.toString() }) }
    var error by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = if (entry == null) "Новое занятие · ${DAY_NAMES[dayOfWeek - 1]}" else "Занятие · ${DAY_NAMES[dayOfWeek - 1]}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("Пара", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(bells.size) { index ->
                    val bell = bells[index]
                    FilterChip(
                        selected = bellNumber == bell.number,
                        onClick = { bellNumber = bell.number },
                        label = { Text("${bell.number} · ${bell.startTime}") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Предмет *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text("Тип занятия", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(LessonType.entries.size) { index ->
                    val type = LessonType.entries[index]
                    FilterChip(
                        selected = lessonType == type,
                        onClick = { lessonType = type },
                        label = { Text(type.displayName) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = teacher,
                onValueChange = { teacher = it },
                label = { Text("Преподаватель") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = classroom,
                onValueChange = { classroom = it },
                label = { Text("Аудитория") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = subgroup,
                onValueChange = { subgroup = it },
                label = { Text("Подгруппа / примечание") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("Недели", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(WeekParity.entries.size) { index ->
                    val p = WeekParity.entries[index]
                    FilterChip(
                        selected = parity == p,
                        onClick = { parity = p },
                        label = { Text(p.displayName) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = firstWeek,
                    onValueChange = { firstWeek = it.filter(Char::isDigit).take(2) },
                    label = { Text("С недели") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lastWeek,
                    onValueChange = { lastWeek = it.filter(Char::isDigit).take(2) },
                    label = { Text("По неделю (пусто = до конца)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (subject.isBlank()) {
                        error = "Укажите предмет"
                        return@Button
                    }
                    val first = firstWeek.toIntOrNull() ?: 1
                    val last = lastWeek.toIntOrNull() ?: 0
                    if (first < 1 || (weeksCount > 0 && first > weeksCount)) {
                        error = "Неделя «с» вне диапазона семестра"
                        return@Button
                    }
                    if (last != 0 && last < first) {
                        error = "Неделя «по» раньше недели «с»"
                        return@Button
                    }
                    onSave(
                        ScheduleEntry(
                            id = entry?.id ?: newEntryId(),
                            dayOfWeek = entry?.dayOfWeek ?: dayOfWeek,
                            bellNumber = bellNumber,
                            subject = subject.trim(),
                            lessonType = lessonType,
                            teacher = teacher.trim(),
                            classroom = classroom.trim(),
                            subgroup = subgroup.trim(),
                            parity = parity,
                            firstWeek = first,
                            lastWeek = last
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить занятие")
            }
        }
    }
}

/** Экспорт в буфер обмена и импорт из вставленного JSON. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataSheet(
    repository: ScheduleRepository,
    onDismiss: () -> Unit,
    onImported: (String?) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val savedJson = remember { repository.exportSemesterJson() }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Экспорт и импорт", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Расписание хранится в открытом JSON-формате universal-schedule: его можно передать другому студенту или сохранить как резервную копию.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val json = repository.exportSemesterJson()
                    if (json == null) {
                        importError = "Семестр ещё не сохранён — сначала сохраните расписание"
                    } else {
                        clipboard.setText(AnnotatedString(json))
                        copied = true
                    }
                },
                enabled = savedJson != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (copied) "Скопировано в буфер обмена ✓" else "Скопировать расписание (JSON)")
            }
            if (savedJson == null) {
                Text(
                    text = "Экспорт доступен после сохранения семестра.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(16.dp))

            Text("Импорт", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                label = { Text("Вставьте JSON расписания") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
            importError?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (importText.isBlank()) {
                        importError = "Вставьте JSON расписания"
                        return@Button
                    }
                    val error = repository.importSemesterJson(importText)
                    onImported(error)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Импортировать и заменить текущий семестр")
            }
        }
    }
}
