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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlatformBackHandler(onBack = onBack)
    val taskRepository: TaskRepository = koinInject()
    val subjects by taskRepository.subjects.collectAsState()
    val isAlreadyAdded = subjects.any { it.name.trim().equals(lesson.subject.trim(), ignoreCase = true) }

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

            // Панель инструментов: добавить предмет в задачи
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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


            Spacer(modifier = Modifier.height(72.dp))
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

