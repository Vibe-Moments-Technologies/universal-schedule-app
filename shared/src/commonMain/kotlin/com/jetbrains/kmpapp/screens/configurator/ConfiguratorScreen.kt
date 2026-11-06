package com.jetbrains.kmpapp.screens.configurator

import com.jetbrains.kmpapp.data.model.LessonBells
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.ScheduleEntry
import com.jetbrains.kmpapp.data.model.SemesterConfig
import com.jetbrains.kmpapp.data.model.WeekParity
import com.jetbrains.kmpapp.theme.GlitchText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler
import kotlinx.datetime.LocalDate

/**
 * Экран конфигуратора семестра: полный редактор [SemesterConfig].
 *
 * Три секции — заголовок (вуз/группа/курс/семестр/даты/недели), звонки и
 * недельный шаблон занятий. Валидация — [SemesterConfig.validate],
 * сохранение — [ConfiguratorViewModel.save] в репозиторий. Всё хранится
 * локально, без сети.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguratorScreen(
    viewModel: ConfiguratorViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit = {}
) {
    PlatformBackHandler(onBack = onBack)
    val state by viewModel.state.collectAsState()
    val config = state.config

    var showExitDialog by remember { mutableStateOf(false) }
    var showSaveErrorDialog by remember { mutableStateOf(false) }

    // Закрыть экран после успешного сохранения.
    if (state.saved) {
        LaunchedEffect(state.saved) {
            viewModel.reset()
            onSaved()
        }
    }

    if (state.saveError != null && !showSaveErrorDialog) {
        showSaveErrorDialog = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showExitDialog = true }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Spacer(modifier = Modifier.width(4.dp))
                GlitchText(
                    text = "Конфигуратор семестра",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlitchText(
                    "Соберите семестр: вуз, группа, звонки и недельный шаблон занятий. Всё хранится на устройстве.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { HeaderSection(viewModel, config) }
            item { BellsSection(viewModel, config) }
            item { EntriesSection(viewModel, config) }
            if (state.validationError != null) {
                item {
                    GlitchText(
                        state.validationError ?: "",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            item {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSaving
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    GlitchText(
                        if (state.isSaving) "Сохраняем…" else "Сохранить семестр",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { GlitchText("Выйти без сохранения?") },
            text = { GlitchText("Несохранённые изменения будут потеряны.") },
            confirmButton = {
                Button(onClick = {
                    showExitDialog = false
                    onBack()
                }) { GlitchText("Выйти") }
            },
            dismissButton = {
                Button(onClick = { showExitDialog = false }) { GlitchText("Отмена") }
            }
        )
    }

    if (showSaveErrorDialog) {
        AlertDialog(
            onDismissRequest = { showSaveErrorDialog = false },
            title = { GlitchText("Не удалось сохранить") },
            text = { GlitchText(state.saveError ?: "Неизвестная ошибка") },
            confirmButton = {
                Button(onClick = { showSaveErrorDialog = false }) { GlitchText("Понятно") }
            }
        )
    }
}

/** Сворачиваемая секция-карточка с заголовком и стрелкой. */
@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    iconContent: @Composable () -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                iconContent()
                Column(Modifier.weight(1f)) {
                    GlitchText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    GlitchText(
                        subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    content()
                }
            }
        }
    }
}

/** Секция заголовка семестра: вуз, группа, курс, семестр, даты, недели. */
@Composable
private fun HeaderSection(viewModel: ConfiguratorViewModel, config: SemesterConfig) {
    var expanded by remember { mutableStateOf(true) }
    SectionCard(
        title = "Семестр",
        subtitle = listOfNotNull(
            config.university.ifBlank { null },
            config.group.ifBlank { null }
        ).joinToString(" · ").ifBlank { "Учебное заведение и группа" },
        iconContent = { Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        expanded = expanded,
        onToggle = { expanded = !expanded }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = config.university,
                onValueChange = viewModel::setUniversity,
                label = { GlitchText("Учебное заведение *") },
                placeholder = { GlitchText("Например: МГУ") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = config.group,
                onValueChange = viewModel::setGroup,
                label = { GlitchText("Группа *") },
                placeholder = { GlitchText("Например: ИТ-21") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = config.course.toString(),
                    onValueChange = { viewModel.setCourse(it.toIntOrNull() ?: 1) },
                    label = { GlitchText("Курс *") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = config.semesterNumber.toString(),
                    onValueChange = { viewModel.setSemesterNumber(it.toIntOrNull() ?: 1) },
                    label = { GlitchText("Семестр *") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            OutlinedTextField(
                value = config.semesterTitle,
                onValueChange = viewModel::setSemesterTitle,
                label = { GlitchText("Подпись (опционально)") },
                placeholder = { GlitchText("Например: Осень 2026") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = config.startDate.toString(),
                onValueChange = { text ->
                    parseLocalDateOrNull(text)?.let { viewModel.setStartDate(it) }
                },
                label = { GlitchText("Начало семестра, гггг-мм-дд *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = config.weeksCount.toString(),
                onValueChange = { viewModel.setWeeksCount(it.toIntOrNull() ?: 17) },
                label = { GlitchText("Число недель *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

/** Секция звонков. */
@Composable
private fun BellsSection(viewModel: ConfiguratorViewModel, config: SemesterConfig) {
    var expanded by remember { mutableStateOf(true) }
    SectionCard(
        title = "Звонки",
        subtitle = "${config.bells.size} ${pluralBells(config.bells.size)}",
        iconContent = { Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        expanded = expanded,
        onToggle = { expanded = !expanded }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            config.bells.forEachIndexed { index, bell ->
                BellEditorRow(viewModel, index, bell)
            }
            Button(
                onClick = { viewModel.addBell() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                GlitchText("Добавить звонок", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Секция записей недельного шаблона. */
@Composable
private fun EntriesSection(viewModel: ConfiguratorViewModel, config: SemesterConfig) {
    var expanded by remember { mutableStateOf(true) }
    SectionCard(
        title = "Занятия",
        subtitle = "${config.entries.size} ${pluralEntries(config.entries.size)}",
        iconContent = { Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        expanded = expanded,
        onToggle = { expanded = !expanded }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (config.entries.isEmpty()) {
                GlitchText(
                    "Занятий пока нет. Добавьте первую пару — день, звонок и предмет.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            config.entries.forEachIndexed { index, entry ->
                EntryEditorRow(viewModel, index, entry)
            }
            Button(
                onClick = { viewModel.addEntry() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                GlitchText("Добавить занятие", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun pluralBells(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "звонок"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "звонка"
    else -> "звонков"
}

private fun pluralEntries(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "занятие"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "занятия"
    else -> "занятий"
}

/** Строка редактора одного звонка. */
@Composable
private fun BellEditorRow(viewModel: ConfiguratorViewModel, index: Int, bell: LessonBells) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = bell.number.toString(),
                onValueChange = { viewModel.updateBell(index, it.toIntOrNull() ?: 1, bell.startTime, bell.endTime) },
                label = { GlitchText("№") },
                modifier = Modifier.weight(0.25f),
                singleLine = true
            )
            OutlinedTextField(
                value = bell.startTime,
                onValueChange = { viewModel.updateBell(index, bell.number, it, bell.endTime) },
                label = { GlitchText("Начало") },
                modifier = Modifier.weight(0.35f),
                singleLine = true
            )
            OutlinedTextField(
                value = bell.endTime,
                onValueChange = { viewModel.updateBell(index, bell.number, bell.startTime, it) },
                label = { GlitchText("Конец") },
                modifier = Modifier.weight(0.35f),
                singleLine = true
            )
            IconButton(onClick = { viewModel.removeBell(index) }) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить звонок", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Строка редактора одной записи шаблона. */
@Composable
private fun EntryEditorRow(
    viewModel: ConfiguratorViewModel,
    index: Int,
    entry: ScheduleEntry
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = entry.subject,
                    onValueChange = { viewModel.updateEntry(
                        index, entry.id, entry.dayOfWeek, entry.bellNumber, it,
                        entry.lessonType, entry.teacher, entry.classroom, entry.subgroup,
                        entry.parity, entry.firstWeek, entry.lastWeek
                    ) },
                    label = { GlitchText("Предмет *") },
                    placeholder = { GlitchText("Например: Математический анализ") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(onClick = { viewModel.removeEntry(index) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить занятие", tint = MaterialTheme.colorScheme.error)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = entry.dayOfWeek.toString(),
                    onValueChange = { viewModel.updateEntry(
                        index, entry.id, it.toIntOrNull() ?: 1, entry.bellNumber, entry.subject,
                        entry.lessonType, entry.teacher, entry.classroom, entry.subgroup,
                        entry.parity, entry.firstWeek, entry.lastWeek
                    ) },
                    label = { GlitchText("День (1=Пн)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = entry.bellNumber.toString(),
                    onValueChange = { viewModel.updateEntry(
                        index, entry.id, entry.dayOfWeek, it.toIntOrNull() ?: 1, entry.subject,
                        entry.lessonType, entry.teacher, entry.classroom, entry.subgroup,
                        entry.parity, entry.firstWeek, entry.lastWeek
                    ) },
                    label = { GlitchText("Звонок №") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = entry.teacher,
                onValueChange = { viewModel.updateEntry(
                    index, entry.id, entry.dayOfWeek, entry.bellNumber, entry.subject,
                    entry.lessonType, it, entry.classroom, entry.subgroup,
                    entry.parity, entry.firstWeek, entry.lastWeek
                ) },
                label = { GlitchText("Преподаватель") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = entry.classroom,
                onValueChange = { viewModel.updateEntry(
                    index, entry.id, entry.dayOfWeek, entry.bellNumber, entry.subject,
                    entry.lessonType, entry.teacher, it, entry.subgroup,
                    entry.parity, entry.firstWeek, entry.lastWeek
                ) },
                label = { GlitchText("Аудитория") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = entry.subgroup,
                onValueChange = { viewModel.updateEntry(
                    index, entry.id, entry.dayOfWeek, entry.bellNumber, entry.subject,
                    entry.lessonType, entry.teacher, entry.classroom, it,
                    entry.parity, entry.firstWeek, entry.lastWeek
                ) },
                label = { GlitchText("Подгруппа") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = entry.firstWeek.toString(),
                    onValueChange = { viewModel.updateEntry(
                        index, entry.id, entry.dayOfWeek, entry.bellNumber, entry.subject,
                        entry.lessonType, entry.teacher, entry.classroom, entry.subgroup,
                        entry.parity, it.toIntOrNull() ?: 1, entry.lastWeek
                    ) },
                    label = { GlitchText("Первая неделя") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = entry.lastWeek.toString(),
                    onValueChange = { viewModel.updateEntry(
                        index, entry.id, entry.dayOfWeek, entry.bellNumber, entry.subject,
                        entry.lessonType, entry.teacher, entry.classroom, entry.subgroup,
                        entry.parity, entry.firstWeek, it.toIntOrNull() ?: 0
                    ) },
                    label = { GlitchText("Последняя (0 — до конца)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            GlitchText("Чётность", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeekParity.entries.forEach { parity ->
                    FilterChip(
                        selected = entry.parity == parity,
                        onClick = { viewModel.updateEntry(
                            index, entry.id, entry.dayOfWeek, entry.bellNumber, entry.subject,
                            entry.lessonType, entry.teacher, entry.classroom, entry.subgroup,
                            parity, entry.firstWeek, entry.lastWeek
                        ) },
                        label = { GlitchText(parity.displayName, fontSize = 11.5.sp) }
                    )
                }
            }

            GlitchText("Тип занятия", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            FlowChips(
                items = LessonType.entries,
                selected = entry.lessonType
            ) { type ->
                viewModel.updateEntry(
                    index, entry.id, entry.dayOfWeek, entry.bellNumber, entry.subject,
                    type, entry.teacher, entry.classroom, entry.subgroup,
                    entry.parity, entry.firstWeek, entry.lastWeek
                )
            }
        }
    }
}

/** Раскладка чипов по строкам с переносом. */
@Composable
private fun <T> FlowChips(
    items: List<T>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var rows = mutableListOf<List<T>>()
        var currentRow = mutableListOf<T>()
        items.forEach { item ->
            currentRow.add(item)
            if (currentRow.size == 3) {
                rows.add(currentRow)
                currentRow = mutableListOf()
            }
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    FilterChip(
                        selected = item == selected,
                        onClick = { onSelected(item) },
                        label = { GlitchText(item.toString(), fontSize = 11.5.sp) }
                    )
                }
            }
        }
    }
}

/** «2026-09-01» → LocalDate; null, если формат не совпал. */
private fun parseLocalDateOrNull(text: String): LocalDate? {
    val parts = text.trim().split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (year !in 2000..2100 || month !in 1..12 || day !in 1..31) return null
    return try {
        kotlinx.datetime.LocalDate(year, month, day)
    } catch (e: IllegalArgumentException) {
        null
    }
}
