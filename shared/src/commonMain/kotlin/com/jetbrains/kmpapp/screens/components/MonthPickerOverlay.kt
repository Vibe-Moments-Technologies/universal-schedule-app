package com.jetbrains.kmpapp.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.model.LessonType
import com.jetbrains.kmpapp.screens.schedule.DayLessonSummary
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus

/**
 * Полноценный месячный календарь: листание месяцев/лет, подсветка сегодня
 * и выбранного дня, цветные точки пар под числом (легенда внизу), «Сегодня».
 * Общий для расписания и свободных аудиторий.
 *
 * Намеренно НЕ Dialog/AlertDialog, а оверлей в окне экрана: на iOS любое
 * диалоговое окно (AlertDialog и даже голый Dialog) роняло приложение при
 * второй смене состояния контента — необработанное исключение в корутине
 * кадра Compose (FlushCoroutineDispatcher → terminateWithUnhandledException).
 * Оверлей использует тот же механизм отрисовки, что баннеры/бейджи/детальный
 * экран пары, которые на iOS не падают. Вызывать из корневого Box экрана.
 */
@Composable
fun MonthPickerOverlay(
    initialDate: LocalDate,
    onDatePicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    lessonSummaries: Map<LocalDate, DayLessonSummary> = emptyMap()
) {
    val today = remember { DateUtils.today() }
    var displayedYear by remember { mutableStateOf(initialDate.year) }
    var displayedMonth by remember { mutableStateOf(initialDate.month.ordinal + 1) }
    // «Сегодня» отмечает текущий день, но НЕ выбирает его: выбор — только тап.
    var highlighted by remember { mutableStateOf(initialDate) }

    val daysInMonth = remember(displayedYear, displayedMonth) {
        val next = if (displayedMonth == 12) {
            LocalDate(displayedYear + 1, 1, 1)
        } else {
            LocalDate(displayedYear, displayedMonth + 1, 1)
        }
        next.minus(DatePeriod(days = 1)).dayOfMonth
    }
    val leadingEmptyDays = remember(displayedYear, displayedMonth) {
        LocalDate(displayedYear, displayedMonth, 1).dayOfWeek.ordinal // Пн = 0
    }
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(320.dp)
                // Тап по карточке не должен закрывать календарь.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // Заголовок: стрелки + «Месяц Год»
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (displayedMonth == 1) {
                                displayedMonth = 12
                                displayedYear -= 1
                            } else {
                                displayedMonth -= 1
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Предыдущий месяц"
                        )
                    }

                    Text(
                        text = "${DateUtils.formatMonthTitle(Month.entries[displayedMonth - 1])} $displayedYear",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            if (displayedMonth == 12) {
                                displayedMonth = 1
                                displayedYear += 1
                            } else {
                                displayedMonth += 1
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Следующий месяц"
                        )
                    }
                }

                // Дни недели
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEachIndexed { idx, dayName ->
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (idx >= 5) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Сетка: всегда 6 строк по 7 ячеек-Box (статичное дерево,
                // постоянная высота).
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (row in 0 until 6) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            for (col in 0 until 7) {
                                val dayNumber = row * 7 + col - leadingEmptyDays + 1
                                val cellDate = if (dayNumber in 1..daysInMonth) {
                                    LocalDate(displayedYear, displayedMonth, dayNumber)
                                } else null

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(1.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (cellDate != null && cellDate == highlighted) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            } else Color.Transparent
                                        )
                                        .then(
                                            if (cellDate == today) {
                                                Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            } else Modifier
                                        )
                                        .then(
                                            if (cellDate != null) {
                                                Modifier.clickable {
                                                    onDatePicked(cellDate)
                                                    onDismiss()
                                                }
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (cellDate != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = cellDate.dayOfMonth.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (cellDate == highlighted || cellDate == today) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    cellDate == highlighted -> MaterialTheme.colorScheme.primary
                                                    col == 6 -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            // Точки пар в две строки (до 5 в строке), как в ленточном календаре.
                                            val lessonTypes = lessonSummaries[cellDate]?.lessonTypes ?: emptyList()
                                            if (lessonTypes.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    listOf(lessonTypes.take(5), lessonTypes.drop(5).take(5))
                                                        .filter { it.isNotEmpty() }
                                                        .forEach { rowTypes ->
                                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                rowTypes.forEach { type ->
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(3.5.dp)
                                                                            .clip(CircleShape)
                                                                            .background(getLessonDotColor(type, isDark))
                                                                    )
                                                                }
                                                            }
                                                        }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Легенда типов пар в одну строку — короткие названия, как на
                // карточках пар (полные не влезают в ширину карточки).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LessonType.entries.forEach { type ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(getLessonDotColor(type, isDark))
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = type.shortName,
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        displayedYear = today.year
                        displayedMonth = today.month.ordinal + 1
                        highlighted = today
                    }) {
                        Text("Сегодня", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                }
            }
        }
    }
}

/** Цвета точек пар — единый источник для ленточного и месячного календарей. */
internal fun getLessonDotColor(type: LessonType, isDark: Boolean): Color = when (type) {
    LessonType.LECTURE -> if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    LessonType.PRACTICE -> if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
    LessonType.LAB -> if (isDark) Color(0xFFFB923C) else Color(0xFFEA580C)
    LessonType.OTHER -> if (isDark) Color(0xFFC084FC) else Color(0xFF9333EA)
}
