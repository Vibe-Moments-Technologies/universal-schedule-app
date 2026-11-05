package com.jetbrains.kmpapp.screens.schedule
import com.jetbrains.kmpapp.theme.GlitchText

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.data.model.ScheduleSlot
import com.jetbrains.kmpapp.data.model.ScheduleTargetType
import com.jetbrains.kmpapp.data.model.defaultLessonColor
import com.jetbrains.kmpapp.data.storage.LessonNotesStorage
import com.jetbrains.kmpapp.theme.LocalLessonTypeOverrides
import org.koin.compose.koinInject

@Composable
fun ScheduleSlotCard(
    slot: ScheduleSlot,
    onLessonClick: (Lesson) -> Unit,
    isToday: Boolean = false,
    currentMinutesState: State<Int>? = null,
    showLessonProgress: Boolean = true,
    showEmptyLessonProgress: Boolean = true,
    showBreakProgress: Boolean = true,
    showAbbreviatedNames: Boolean = false,
    scheduleTargetType: ScheduleTargetType = ScheduleTargetType.GROUP,
    noteTargetId: Int = -1,
    modifier: Modifier = Modifier
) {
    when (slot) {
        is ScheduleSlot.Active -> {
            if (slot.lessons.size == 1) {
                LessonCard(
                    lesson = slot.lessons.first(),
                    onClick = { onLessonClick(slot.lessons.first()) },
                    isToday = isToday,
                    currentMinutesState = currentMinutesState,
                    showLessonProgress = showLessonProgress,
                    showAbbreviatedNames = showAbbreviatedNames,
                    scheduleTargetType = scheduleTargetType,
                    noteTargetId = noteTargetId,
                    modifier = modifier
                )
            } else {
                MultiLessonCard(
                    bellNumber = slot.bellNumber,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    lessons = slot.lessons,
                    onLessonClick = onLessonClick,
                    isToday = isToday,
                    currentMinutesState = currentMinutesState,
                    showLessonProgress = showLessonProgress,
                    showAbbreviatedNames = showAbbreviatedNames,
                    scheduleTargetType = scheduleTargetType,
                    noteTargetId = noteTargetId,
                    modifier = modifier
                )
            }
        }
        is ScheduleSlot.Empty -> {
            EmptyLessonCard(
                bellNumber = slot.bellNumber,
                startTime = slot.startTime,
                endTime = slot.endTime,
                isToday = isToday,
                currentMinutesState = currentMinutesState,
                showEmptyLessonProgress = showEmptyLessonProgress,
                modifier = modifier
            )
        }
    }
}

@Composable
fun LessonCard(
    lesson: Lesson,
    onClick: () -> Unit,
    isToday: Boolean = false,
    currentMinutesState: State<Int>? = null,
    showLessonProgress: Boolean = true,
    showAbbreviatedNames: Boolean = false,
    scheduleTargetType: ScheduleTargetType = ScheduleTargetType.GROUP,
    noteTargetId: Int = -1,
    modifier: Modifier = Modifier,
    pageIndicator: Pair<Int, Int>? = null,
    horizontalMargin: androidx.compose.ui.unit.Dp = 16.dp
) {
    val (typeBg, typeTextColor) = getTypeBadgeColors(lesson.lessonType)

    // Заметка к паре (R2): реактивное чтение — превью появляется сразу после
    // сохранения (StateFlow), а ключ точный по targetId+дата+пара: заметки
    // чужих расписаний в карточку не попадают.
    val notesStorage: LessonNotesStorage = koinInject()
    val allNotes by notesStorage.notes.collectAsState()
    val notePreview = if (noteTargetId >= 0) {
        val noteKey = notesStorage.lessonKey(noteTargetId, lesson.date.toString(), lesson.bellNumber)
        allNotes.firstOrNull { it.noteKey == noteKey }?.text
    } else {
        null
    }

    // ponytail: State протягивается вниз и читается ТОЛЬКО на сегодняшних
    // карточках: тик раз в 30 секунд пересобирает одну активную карточку,
    // а не всё дерево расписания посреди скролла.
    val progress = if (isToday && showLessonProgress) {
        val currentMinutes = currentMinutesState?.value
            ?: com.jetbrains.kmpapp.data.model.DateUtils.currentTimeMinutes()
        com.jetbrains.kmpapp.data.model.DateUtils.getLessonProgress(lesson.startTime, lesson.endTime, currentMinutes)
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header: Pair number, time, type badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            GlitchText(
                                text = "${lesson.bellNumber} пара",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        GlitchText(
                            text = "${lesson.startTime} — ${lesson.endTime}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(typeBg)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            GlitchText(
                                text = lesson.lessonType.displayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = typeTextColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Subject name
                GlitchText(
                    text = if (showAbbreviatedNames) abbreviateSubjectName(lesson.subject) else lesson.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Details: show the useful entity for the selected schedule target.
                val groupsText = lesson.groups.joinToString(", ")
                val showGroupsAsTeacherReplacement = scheduleTargetType == ScheduleTargetType.TEACHER && lesson.groups.isNotEmpty()

                if (showGroupsAsTeacherReplacement) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Группы",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        GlitchText(
                            text = groupsText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (lesson.teachers.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Преподаватель",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        GlitchText(
                            text = lesson.teachers.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (lesson.classrooms.isNotEmpty() || lesson.groups.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (scheduleTargetType == ScheduleTargetType.AUDITORIUM && lesson.groups.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "Группы",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            GlitchText(
                                text = groupsText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else if (lesson.isRemote) {
                            // «СДО» из фида — это сайт вуза
                            // (online-edu.mirea.ru), не корпус и не аудитория:
                            // рисуем подпись дистанционной пары, а не строку
                            // с иконкой локации.
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Дистанционно",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            GlitchText(
                                text = "Дистанционно · СДО",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        } else if (lesson.physicalClassrooms.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Аудитория",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            GlitchText(
                                text = lesson.physicalClassrooms.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }

                        if (scheduleTargetType == ScheduleTargetType.GROUP && lesson.groups.size > 1) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "Группы",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            GlitchText(
                                text = groupsText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Заметка к паре (R2): ≤2 строк с … на переполнении
                if (!notePreview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        GlitchText(
                            text = notePreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                pageIndicator?.let { (total, current) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        GlitchText(
                            text = "$total ${if (total in 2..4) "пары" else "пар"} в это время",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        repeat(total) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == current) 6.dp else 4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == current) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }
                }
            }

            if (progress != null) {
                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = progress,
                    animationSpec = androidx.compose.animation.core.tween(500)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = if (animatedProgress >= 0.98f) 20.dp else 0.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
fun MultiLessonCard(
    bellNumber: Int,
    startTime: String,
    endTime: String,
    lessons: List<Lesson>,
    onLessonClick: (Lesson) -> Unit,
    isToday: Boolean = false,
    currentMinutesState: State<Int>? = null,
    showLessonProgress: Boolean = true,
    showAbbreviatedNames: Boolean = false,
    scheduleTargetType: ScheduleTargetType = ScheduleTargetType.GROUP,
    noteTargetId: Int = -1,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { lessons.size })

    // Gallery of full lesson blocks: each pair is its own card, the next peeks from the right
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 4.dp,
        beyondViewportPageCount = 1
    ) { page ->
        LessonCard(
            lesson = lessons[page],
            onClick = { onLessonClick(lessons[page]) },
            isToday = isToday,
            currentMinutesState = currentMinutesState,
            showLessonProgress = showLessonProgress,
            showAbbreviatedNames = showAbbreviatedNames,
            scheduleTargetType = scheduleTargetType,
            noteTargetId = noteTargetId,
            pageIndicator = lessons.size to pagerState.currentPage,
            horizontalMargin = 0.dp
        )
    }
}

@Composable
fun EmptyLessonCard(
    bellNumber: Int,
    startTime: String,
    endTime: String,
    isToday: Boolean = false,
    currentMinutesState: State<Int>? = null,
    showEmptyLessonProgress: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Прогресс — та же формула, что у обычной пары: 0..1 внутри [start, end).
    val progress = if (isToday && showEmptyLessonProgress) {
        val currentMinutes = currentMinutesState?.value
            ?: com.jetbrains.kmpapp.data.model.DateUtils.currentTimeMinutes()
        com.jetbrains.kmpapp.data.model.DateUtils.getLessonProgress(startTime, endTime, currentMinutes)
    } else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlitchText(
                    text = "$bellNumber пара • $startTime — $endTime",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
                GlitchText(
                    text = "Нет пары",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                )
            }
        }
        if (progress != null) {
            // Полоса в самом низу карточки, как у обычной пары; размер блока не меняется.
            val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                targetValue = progress,
                animationSpec = androidx.compose.animation.core.tween(500)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(2.5.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun LessonBreakIndicator(
    breakMinutes: Int,
    breakStartTime: String = "",
    breakEndTime: String = "",
    isToday: Boolean = false,
    currentMinutesState: State<Int>? = null,
    showBreakProgress: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (breakMinutes <= 0) return
    val breakText = "••• перемена $breakMinutes мин •••"
    val progress = if (isToday && showBreakProgress) {
        val currentMinutes = currentMinutesState?.value
            ?: com.jetbrains.kmpapp.data.model.DateUtils.currentTimeMinutes()
        com.jetbrains.kmpapp.data.model.DateUtils
            .getLessonProgress(breakStartTime, breakEndTime, currentMinutes)
    } else null

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Внутренний Box — по размеру текста: клип прогресса считается от ширины
        // самой надписи, а не от ширины строки (иначе первая половина перемены
        // красила бы пустое поле слева и подсветка «не появлялась»).
        Box {
            GlitchText(
                text = breakText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 0.2.sp
            )
            if (progress != null) {
                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = progress,
                    animationSpec = androidx.compose.animation.core.tween(500)
                )
                // Копия надписи цветом primary, обрезанная клипом по пройденной
                // части: тот же текст/стиль, поэтому буквы совпадают пиксель в пиксель.
                GlitchText(
                    text = breakText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.2.sp,
                    modifier = Modifier.drawWithContent {
                        clipRect(right = size.width * animatedProgress) {
                            this@drawWithContent.drawContent()
                        }
                    }
                )
            }
        }
    }
}


/** Пары (фон, текст) для каждого типа: [0] = тёмная тема, [1] = светлая. */
private val TYPE_BADGE_COLORS = mapOf(
    LessonType.LECTURE to listOf(
        Color(0xFF0C4A6E) to Color(0xFFBAE6FD),
        Color(0xFFBAE6FD) to Color(0xFF0369A1)
    ),
    LessonType.PRACTICE to listOf(
        Color(0xFF14532D) to Color(0xFFBBF7D0),
        Color(0xFFBBF7D0) to Color(0xFF15803D)
    ),
    LessonType.LAB to listOf(
        Color(0xFF7C2D12) to Color(0xFFFED7AA),
        Color(0xFFFED7AA) to Color(0xFFC2410C)
    ),
    LessonType.EXAM to listOf(
        Color(0xFF7F1D1D) to Color(0xFFFECACA),
        Color(0xFFFECACA) to Color(0xFFB91C1C)
    ),
    LessonType.CREDIT to listOf(
        Color(0xFF134E4A) to Color(0xFF99F6E4),
        Color(0xFF99F6E4) to Color(0xFF0F766E)
    ),
    LessonType.COURSE_WORK to listOf(
        Color(0xFF713F12) to Color(0xFFFDE68A),
        Color(0xFFFDE68A) to Color(0xFFA16207)
    ),
    LessonType.CONSULTATION to listOf(
        Color(0xFF312E81) to Color(0xFFC7D2FE),
        Color(0xFFC7D2FE) to Color(0xFF4338CA)
    ),
    LessonType.INDIVIDUAL_WORK to listOf(
        Color(0xFF334155) to Color(0xFFCBD5E1),
        Color(0xFFCBD5E1) to Color(0xFF475569)
    ),
    LessonType.OTHER to listOf(
        Color(0xFF581C87) to Color(0xFFE9D5FF),
        Color(0xFFE9D5FF) to Color(0xFF7E22CE)
    ),
    LessonType.ADDITIONAL to listOf(
        Color(0xFF831843) to Color(0xFFFBCFE8),
        Color(0xFFFCE7F3) to Color(0xFFBE185D)
    )
)

@Composable
internal fun getTypeBadgeColors(lessonType: LessonType): Pair<Color, Color> {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Пользовательский цвет: если он отличается от стандартного для этого типа,
    // рисуем бейдж как полупрозрачную плашку с акцентом. Если выбран «родной»
    // цвет типа — оставляем привычный контрастный бейдж из TYPE_BADGE_COLORS.
    val override = LocalLessonTypeOverrides.current[lessonType]
    if (override != null && override != defaultLessonColor[lessonType]) {
        val accent = override.color(isDark)
        val background = accent.copy(alpha = if (isDark) 0.22f else 0.16f)
        return background to accent
    }
    // Фолбэк вместо getValue: новый тип занятия без своей пары цветов не должен
    // ронять экран расписания — берём нейтральный серый.
    val colors = TYPE_BADGE_COLORS[lessonType]
    if (colors == null) {
        return if (isDark) Color(0xFF334155) to Color(0xFFCBD5E1)
        else Color(0xFFCBD5E1) to Color(0xFF475569)
    }
    return if (isDark) colors[0] else colors[1]
}
