package com.jetbrains.kmpapp.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.jetbrains.kmpapp.data.widget.WidgetThemeColors

/**
 * Семантические токены виджета (ТЗ, п. 2.1). Цвета берутся из активной темы
 * приложения; статические палитры ниже — запасной вариант, пока тема ещё
 * не опубликована.
 */
data class WidgetPalette(
    val background: ColorProvider,
    val primaryText: ColorProvider,
    val subjectText: ColorProvider,
    val secondaryText: ColorProvider,
    val mutedText: ColorProvider,
    val accent: ColorProvider,
    val practice: ColorProvider,
    val divider: ColorProvider,
    val subtleSurface: ColorProvider,
    val badgeBackground: ColorProvider,
    val badgeText: ColorProvider
)

private fun color(value: Long) = ColorProvider(Color(value))

val DarkWidgetPalette = WidgetPalette(
    background = color(0xFF292C31),
    primaryText = color(0xFFF5F5F7),
    subjectText = color(0xFFE5E7EC),
    secondaryText = color(0xFFB6BAC4),
    mutedText = color(0xFF969BA6),
    accent = color(0xFF65B5FF),
    practice = color(0xFF78D99D),
    divider = color(0xFF51545D),
    subtleSurface = color(0xFF353940),
    badgeBackground = color(0xFF3E434B),
    badgeText = color(0xFF78D99D)
)

val LightWidgetPalette = WidgetPalette(
    background = color(0xFFFFFFFF),
    primaryText = color(0xFF1C1C1E),
    subjectText = color(0xFF2C2C2E),
    secondaryText = color(0xFF5B5F66),
    mutedText = color(0xFF8A8F98),
    accent = color(0xFF0A7AFF),
    practice = color(0xFF1D9A52),
    divider = color(0xFFD9DCE1),
    subtleSurface = color(0xFFF2F3F7),
    badgeBackground = color(0xFFEAF7EF),
    badgeText = color(0xFF1D8F4C)
)

/** Переводит цвета активной темы приложения в палитру виджета. */
fun WidgetThemeColors.toWidgetPalette(): WidgetPalette = WidgetPalette(
    background = ColorProvider(Color(background)),
    primaryText = ColorProvider(Color(primaryText)),
    subjectText = ColorProvider(Color(subjectText)),
    secondaryText = ColorProvider(Color(secondaryText)),
    mutedText = ColorProvider(Color(mutedText)),
    accent = ColorProvider(Color(accent)),
    practice = ColorProvider(Color(practice)),
    divider = ColorProvider(Color(divider)),
    subtleSurface = ColorProvider(Color(subtleSurface)),
    badgeBackground = ColorProvider(Color(badgeBackground)),
    badgeText = ColorProvider(Color(badgeText))
)
