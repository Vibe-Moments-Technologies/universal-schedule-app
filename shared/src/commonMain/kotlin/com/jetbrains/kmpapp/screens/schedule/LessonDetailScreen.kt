package com.jetbrains.kmpapp.screens.schedule
import com.jetbrains.kmpapp.theme.GlitchText

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.TaskRepository
import com.jetbrains.kmpapp.data.model.AssessmentType
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.DefaultSubjectColors
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.Subject
import com.jetbrains.kmpapp.data.model.SubjectImportance
import com.jetbrains.kmpapp.screens.components.PlatformBackHandler
import org.koin.compose.koinInject
import kotlin.time.Clock

@Composable
fun LessonDetailScreen(
    lesson: Lesson,
    targetId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlatformBackHandler(onBack = onBack)
    val taskRepository: TaskRepository = koinInject()
    val notesStorage: com.jetbrains.kmpapp.data.storage.LessonNotesStorage = koinInject()
    val subjects by taskRepository.subjects.collectAsState()
    val isAlreadyAdded = subjects.any { it.name.trim().equals(lesson.subject.trim(), ignoreCase = true) }

    // Персистентные заметки (R2): к паре и к предмету — единый шит
    // добавления/редактирования (решение владельца: вместо двух полей —
    // кнопка «Добавить заметку» / «Редактировать заметки»).
    // Состояние — TextFieldValue: позиция курсора живёт с текстом, иначе
    // первая буква «перепрыгивала» в начало строки (привет → риветп).
    val dateStr = lesson.date.toString()
    var lessonNoteText by remember(targetId, dateStr, lesson.bellNumber) {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                notesStorage.getLessonNote(targetId, dateStr, lesson.bellNumber)?.text ?: ""
            )
        )
    }
    var subjectNoteText by remember(targetId, lesson.subject) {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                notesStorage.getSubjectNote(targetId, lesson.subject)?.text ?: ""
            )
        )
    }
    var showNoteSheet by remember { mutableStateOf(false) }

    // rememberUpdatedState: onDispose видит актуальные тексты, а не из первого рендера
    val currentLessonNote by androidx.compose.runtime.rememberUpdatedState(lessonNoteText)
    val currentSubjectNote by androidx.compose.runtime.rememberUpdatedState(subjectNoteText)

    // Сохранение — при закрытии шита И при уходе с экрана (двойная
    // защита). Только-onDispose было ошибкой: закрыть шит → смахнуть
    // приложение (процесс убит, onDispose не вызван) → заметка не
    // записалась — «исчезает при перезапуске» (репорт владельца).
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    fun persistNotes() {
        if (currentLessonNote.text.isNotBlank()) {
            notesStorage.saveLessonNote(targetId, dateStr, lesson.bellNumber, currentLessonNote.text)
        } else {
            notesStorage.getLessonNote(targetId, dateStr, lesson.bellNumber)?.let {
                notesStorage.deleteNote(it.id)
            }
        }
        if (currentSubjectNote.text.isNotBlank()) {
            notesStorage.saveSubjectNote(targetId, lesson.subject, currentSubjectNote.text)
        } else {
            notesStorage.getSubjectNote(targetId, lesson.subject)?.let {
                notesStorage.deleteNote(it.id)
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            persistNotes()
        }
    }

    val (typeBg, typeTextColor) = getTypeBadgeColors(lesson.lessonType)

    val weekInfo = DateUtils.getWeekInfo(lesson.date)
    val parityStr = if (weekInfo.isEven) "Чётная неделя" else "Нечётная неделя"

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
                    text = "О занятии",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        modifier = modifier
            .fillMaxSize()
    ) { innerPadding ->
        // Тап вне полей ввода скрывает клавиатуру
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { focusManager.clearFocus() }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main card with subject and type
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(typeBg)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        GlitchText(
                            text = lesson.lessonType.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = typeTextColor
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    GlitchText(
                        text = lesson.subject,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Time & Date card
            DetailSectionCard(
                title = "Время и дата",
                icon = Icons.Default.AccessTime
            ) {
                DetailItem(
                    label = "Пара",
                    value = "${lesson.bellNumber} пара (${lesson.startTime} — ${lesson.endTime})"
                )
                Spacer(modifier = Modifier.height(10.dp))
                DetailItem(
                    label = "Дата",
                    value = "${lesson.date.day} ${DateUtils.formatMonthRu(lesson.date.month)} ${lesson.date.year} (${DateUtils.formatDayOfWeekShort(lesson.date.dayOfWeek)})"
                )
                Spacer(modifier = Modifier.height(10.dp))
                DetailItem(
                    label = "Неделя",
                    value = "${weekInfo.weekNumber} неделя • $parityStr"
                )
            }

            // Teachers card
            if (lesson.teachers.isNotEmpty()) {
                DetailSectionCard(
                    title = if (lesson.teachers.size > 1) "Преподаватели" else "Преподаватель",
                    icon = Icons.Default.Person
                ) {
                    lesson.teachers.forEachIndexed { index, teacher ->
                        if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                        GlitchText(
                            text = teacher,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Classrooms card / дистанционная пара
            if (lesson.isRemote) {
                // «СДО» из фида — сайт вуза (online-edu.mirea.ru), не корпус:
                // вместо фейковой аудитории — честная подпись и ссылка.
                DetailSectionCard(
                    title = "Формат",
                    icon = Icons.Default.Language
                ) {
                    GlitchText(
                        text = "Дистанционно",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GlitchText(
                        text = "Пара проходит в СДО вуза — среде дистанционного обучения.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    GlitchText(
                        text = "online-edu.mirea.ru",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { uriHandler.openUri("https://online-edu.mirea.ru/") }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    // Смешанный формат: есть и аудитории, и дистанционная
                    // строка (несколько аудиторий у пары) — показываем обе.
                    if (lesson.physicalClassrooms.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        GlitchText(
                            text = "Также аудитории: ${lesson.physicalClassrooms.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (lesson.classrooms.isNotEmpty()) {
                DetailSectionCard(
                    title = if (lesson.classrooms.size > 1) "Аудитории" else "Аудитория",
                    icon = Icons.Default.LocationOn
                ) {
                    lesson.classrooms.forEachIndexed { index, room ->
                        if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                        GlitchText(
                            text = room,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Groups card
            if (lesson.groups.isNotEmpty()) {
                DetailSectionCard(
                    title = if (lesson.groups.size > 1) "Группы" else "Группа",
                    icon = Icons.Default.Group
                ) {
                    GlitchText(
                        text = lesson.groups.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Панель инструментов: заметки + добавить в задачи
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Заметка — единая кнопка (решение владельца): нет заметок
                    // → «Добавить заметку», есть → «Редактировать заметки».
                    // Выбор формата и текст — в выдвигающемся снизу шите.
                    val hasNotes = lessonNoteText.text.isNotBlank() || subjectNoteText.text.isNotBlank()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showNoteSheet = true }
                            .background(
                                if (hasNotes) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = if (hasNotes) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        GlitchText(
                            text = if (hasNotes) "Редактировать заметки" else "Добавить заметку",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = if (hasNotes) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Добавить предмет в задачи — одна строка: плюсик + текст
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isAlreadyAdded) {
                                val words = lesson.subject.split(" ", "-", "_").filter { it.isNotBlank() }
                                val shortCode = if (words.size > 1) {
                                    words.mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(4).joinToString("")
                                } else {
                                    lesson.subject.take(3).uppercase()
                                }
                                val colorIdx = (lesson.subject.hashCode().and(0x7fffffff)) % DefaultSubjectColors.size
                                val newSubject = Subject(
                                    id = Clock.System.now().toEpochMilliseconds().toString(),
                                    name = lesson.subject.trim(),
                                    shortCode = shortCode,
                                    colorHex = DefaultSubjectColors[colorIdx],
                                    importance = SubjectImportance.MEDIUM,
                                    assessmentType = when (lesson.lessonType) {
                                        LessonType.EXAM -> AssessmentType.EXAM
                                        LessonType.CREDIT -> AssessmentType.TEST
                                        LessonType.COURSE_WORK -> AssessmentType.COURSE_WORK
                                        LessonType.LAB -> AssessmentType.CREDIT
                                        LessonType.PRACTICE -> AssessmentType.TEST
                                        else -> AssessmentType.EXAM
                                    },
                                    teacherName = lesson.teachers.joinToString(", "),
                                    roomOrLink = lesson.classrooms.joinToString(", "),
                                    notes = "Добавлено из расписания"
                                )
                                taskRepository.addSubject(newSubject)
                            }
                            .background(
                                if (isAlreadyAdded) MaterialTheme.colorScheme.surfaceContainerHigh
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isAlreadyAdded) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null,
                            tint = if (isAlreadyAdded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        GlitchText(
                            text = if (isAlreadyAdded) "Предмет уже в задачах" else "Добавить предмет в задачи",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = if (isAlreadyAdded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Шит заметок: выбор формата + текст (как AddScheduleBottomSheet).
            if (showNoteSheet) {
                NoteEditorSheet(
                    lessonNoteValue = lessonNoteText,
                    subjectNoteValue = subjectNoteText,
                    onLessonNoteChange = { lessonNoteText = it },
                    onSubjectNoteChange = { subjectNoteText = it },
                    onDismiss = {
                        // Сохраняем при закрытии шита (не только экрана):
                        // смахнутое приложение не зовёт onDispose.
                        persistNotes()
                        showNoteSheet = false
                    },
                    onKeyboardHide = {
                        keyboardController?.hide()
                    }
                )
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

/**
 * Выдвигающийся снизу шит заметок (стиль AddScheduleBottomSheet):
 * выбор формата чипами («К этой паре» / «К предмету») и одно поле
 * текста активного формата.
 *
 * Поле — TextFieldValue: курсор и выделение живут вместе с текстом.
 * Строковое значение (String) вместо этого пересоздавало курсор при
 * каждой рекомпозиции родителя — первая буква «перепрыгивала» в
 * начало строки (привет → риветп).
 *
 * Клавиатура: кнопка Done на самой клавиатуре убирает её (жест
 * свайпа клавиатуры вниз работает системно).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorSheet(
    lessonNoteValue: androidx.compose.ui.text.input.TextFieldValue,
    subjectNoteValue: androidx.compose.ui.text.input.TextFieldValue,
    onLessonNoteChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onSubjectNoteChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
    // Прямое скрытие системной клавиатуры: clearFocus один не сворачивает
    // клаву на всех платформах (репорт владельца: «галочка и жест не
    // сворачивают»); hide() гасит её принудительно.
    onKeyboardHide: () -> Unit
) {
    var format by remember { mutableStateOf(if (lessonNoteValue.text.isNotBlank()) 0 else 1) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = rememberCoroutineScope()
    // Диагноз по репорту («клавиатура уходит ТОЛЬКО при выходе из шита»):
    // на iOS CMP программные clearFocus/hide()/resignFirstResponder в
    // модальном шите не срабатывают — клавиатура гарантированно закрывается
    // только когда поле ПОКИДАЕТ композицию (выход из шита удаляет поле).
    // «Готово» воспроизводит ровно этот механизм: поле временно убирается
    // из дерева (клавиатура сворачивается), затем возвращается.
    var fieldVisible by remember { mutableStateOf(true) }
    fun hideKeyboard() {
        if (!fieldVisible) return
        fieldVisible = false
        focusManager.clearFocus()
        onKeyboardHide()
        com.jetbrains.kmpapp.screens.components.forceHideKeyboard()
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = {
            hideKeyboard()
            onDismiss()
        },
        sheetState = sheetState,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Весь контент шита — зона закрытия жестом (iOS-паттерн):
                // быстрый свайп вниз в ЛЮБОМ месте шторки (не только за
                // шапку-«ручку») закрывает её. Встроенный порог закрытия
                // ModalBottomSheet почти у низа экрана — привычный
                // «маленький» свайп пружинил обратно (репорт владельца).
                // Жест сработает раньше встроенного drag-детектора: наш
                // pointerInput стоит на Column, детектор шита — выше.
                .sheetDragDismiss(
                    onDismiss = {
                        hideKeyboard()
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDismiss()
                        }
                    }
                )
                .padding(horizontal = 20.dp)
                .imePadding()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlitchText(
                    text = "Заметка",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Формат: к паре или к предмету
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.FilterChip(
                    selected = format == 0,
                    onClick = { format = 0 },
                    label = { GlitchText("К этой паре", fontSize = 13.sp) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.FilterChip(
                    selected = format == 1,
                    onClick = { format = 1 },
                    label = { GlitchText("К предмету", fontSize = 13.sp) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (fieldVisible) {
                OutlinedTextField(
                    value = if (format == 0) lessonNoteValue else subjectNoteValue,
                    onValueChange = { newText ->
                        // iOS-ловушка многострочных полей: кнопка Done НЕ зовёт
                        // KeyboardActions, а вставляет «\n». Ловим перенос —
                        // это и есть нажатие Done: срезаем его и прячем клаву.
                        val pressedDone = newText.text.endsWith("\n")
                        if (pressedDone) {
                            val trimmed = newText.text.removeSuffix("\n")
                            val cleaned = androidx.compose.ui.text.input.TextFieldValue(
                                text = trimmed,
                                selection = newText.selection
                            )
                            if (format == 0) onLessonNoteChange(cleaned) else onSubjectNoteChange(cleaned)
                            hideKeyboard()
                        } else {
                            if (format == 0) onLessonNoteChange(newText) else onSubjectNoteChange(newText)
                        }
                    },
                    placeholder = {
                        GlitchText(
                            if (format == 0) "Например: принести отчёт…"
                            else "Например: всегда брать тетрадь…"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 5,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onAny = { hideKeyboard() }
                    )
                )
            } else {
                // Поле вне композиции → клавиатура закрыта. Карточка —
                // точная копия вида OutlinedTextField (рамка 1dp, та же
                // форма/высота), иначе после Done текст «терял оконтовку и
                // висел в пустоте», а высота прыгала (репорт владельца).
                // Тап возвращает поле редактирования.
                val text = if (format == 0) lessonNoteValue.text else subjectNoteValue.text
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { fieldVisible = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    GlitchText(
                        text = if (text.isBlank()) "Нажмите, чтобы редактировать…" else text,
                        fontSize = 16.sp,
                        color = if (text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            GlitchText(
                text = if (format == 0) "Показывается только у этой пары"
                else "Показывается у всех пар предмета",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    icon: ImageVector,
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
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                GlitchText(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        GlitchText(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        GlitchText(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Поле заметки с автосохранением при потере фокуса. */
@Composable
private fun NoteField(
    icon: ImageVector,
    title: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        GlitchText(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { GlitchText(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = false,
        maxLines = 3,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}

/**
 * Жест закрытия шторки по «ручке»-шапке (iOS-паттерн): вертикальная тяга
 * вниз от 90dp ИЛИ быстрый флик вниз закрывают шит. Встроенный порог
 * ModalBottomSheet почти у низа экрана — короткая тяга отскакивала
 * обратно, приходилось «сильно вниз» (репорт владельца). жест читается
 * на Main-пассе ДО собственного drag-детектора шита — конфликтов
 * скролла нет: шапка не скроллится.
 */
private fun Modifier.sheetDragDismiss(
    onDismiss: () -> Unit
): Modifier = pointerInput(Unit) {
    // PointerInputScope — Density: 90dp → px прямо тут.
    val thresholdPx = 90.dp.toPx()
    val velocityThresholdPx = 1600f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var totalDown = 0f
        var lastY = down.position.y
        var trackedTime = down.uptimeMillis
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.all { !it.pressed }) break
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val dy = change.position.y - lastY
            val dt = (change.uptimeMillis - trackedTime).coerceAtLeast(1L)
            totalDown += dy.coerceAtLeast(0f)
            val velocityY = dy / dt * 1000f
            lastY = change.position.y
            trackedTime = change.uptimeMillis
            // Тяга вниз за порог ИЛИ флик вниз с любой высоты.
            if (totalDown > thresholdPx || velocityY > velocityThresholdPx) {
                event.changes.forEach { it.consume() }
                onDismiss()
                break
            }
        }
    }
}
