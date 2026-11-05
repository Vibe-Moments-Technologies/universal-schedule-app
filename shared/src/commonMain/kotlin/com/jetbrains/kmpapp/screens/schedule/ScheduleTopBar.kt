package com.jetbrains.kmpapp.screens.schedule
import com.jetbrains.kmpapp.theme.GlitchText

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.ScheduleDiff
import com.jetbrains.kmpapp.data.model.ScheduleTarget

@Composable
fun ScheduleTopBar(
    selectedTarget: ScheduleTarget?,
    savedTargets: List<ScheduleTarget>,
    isLoading: Boolean,
    activeDiff: ScheduleDiff?,
    onSelectTarget: (ScheduleTarget) -> Unit,
    onDiffClick: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Видна, когда лента календаря свёрнута: число текущего дня расписания.
    // Тап — развернуть ленту, долгое нажатие — месячный календарь.
    calendarBadgeDay: Int? = null,
    onCalendarBadgeClick: () -> Unit = {},
    onCalendarBadgeLongClick: () -> Unit = {},
    // Видна при скрытом доке: открывает «Другое» подстраницей расписания.
    onOpenOtherClick: (() -> Unit)? = null
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Wide dropdown selector and Diff button
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                // Ширина капсулы группы измеряется живьём (onSizeChanged):
                // список выбора открывается РОВНО по ширине блока группы
                // (репорт владельца: «список теперь больше самого блока»).
                var capsuleWidth by remember { mutableStateOf(IntSize.Zero) }
                Row(
                    modifier = Modifier
                        .onSizeChanged { capsuleWidth = it }
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { dropdownExpanded = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlitchText(
                        text = selectedTarget?.targetTitle ?: "Выберите расписание",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Выбрать расписание",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    // Репорт владельца: «уголочки вылезающие от квадрата» —
                    // modifier-clip/background рисуются ПОВЕРХ внутренней
                    // Surface меню: её квадратные углы торчали из-под
                    // скруглённой подложки. Скругляем сам попап — параметры
                    // shape/containerColor достаются внутренней Surface.
                    // Ширина — ровно ширина капсулы (меряется живьём).
                    // Страховочный widthIn(200dp) убран: он ПОБЕЖДАЛ width
                    // при коротком названии и раздувал список шире блока —
                    // «большой пробел между группой и галочкой».
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .then(
                            if (capsuleWidth.width > 0) {
                                Modifier.width(with(LocalDensity.current) { capsuleWidth.width.toDp() })
                            } else Modifier
                        )
                ) {
                    if (savedTargets.isEmpty()) {
                        DropdownMenuItem(
                            text = { GlitchText("Нет сохранённых групп") },
                            onClick = {
                                dropdownExpanded = false
                                onAddClick()
                            }
                        )
                    } else {
                        savedTargets.forEach { target ->
                            val isSelected = target.id == selectedTarget?.id
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            GlitchText(
                                                text = target.targetTitle,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            GlitchText(
                                                text = target.type.displayName,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Выбрано",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onSelectTarget(target)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (activeDiff != null && activeDiff.items.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onDiffClick)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlitchText(
                        text = "Diff (${activeDiff.items.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Правая группа: кружок текущего дня (когда календарь свёрнут) + Add (+)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (calendarBadgeDay != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onCalendarBadgeClick() },
                                onLongPress = { onCalendarBadgeLongClick() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    GlitchText(
                        text = calendarBadgeDay.toString(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            FilledIconButton(
                onClick = onAddClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить расписание",
                    modifier = Modifier.size(22.dp)
                )
            }

            if (onOpenOtherClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onOpenOtherClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Другое",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
