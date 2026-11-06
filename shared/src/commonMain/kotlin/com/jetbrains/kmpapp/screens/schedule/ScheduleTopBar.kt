package com.jetbrains.kmpapp.screens.schedule
import com.jetbrains.kmpapp.theme.GlitchText

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Шапка экрана расписания: кружок текущего дня при свёрнутой ленте,
 * шестерёнка «Другое» при скрытом доке.
 *
 * Капсула с названием семестра убрана (решение владельца): расписание
 * одно, имя не несёт информации, а место под ленту дороже.
 */
@Composable
fun ScheduleTopBar(
    modifier: Modifier = Modifier,
    // Видна, когда лента календаря свёрнута: число текущего дня расписания.
    // Тап — развернуть ленту, долгое нажатие — месячный календарь.
    calendarBadgeDay: Int? = null,
    onCalendarBadgeClick: () -> Unit = {},
    onCalendarBadgeLongClick: () -> Unit = {},
    // Видна при скрытом доке: открывает «Другое» подстраницей расписания.
    onOpenOtherClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
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

            if (onOpenOtherClick != null) {
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
