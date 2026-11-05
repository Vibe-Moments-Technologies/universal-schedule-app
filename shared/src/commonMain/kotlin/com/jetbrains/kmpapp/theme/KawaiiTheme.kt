package com.jetbrains.kmpapp.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Тема «Кавай»: пастельная аниме-палитра из трёх акцентов — голубой, розовый
 * и золотой — на кремово-сиреневых поверхностях.
 *
 * История: сперва она была бело-розовой и читалась как «Сакура номер два».
 * Затем ушла в холодную лаванду с мятой — и стала похожа уже на Киберпанк,
 * потому что у обеих оказался один и тот же рецепт «неоновый акцент на почти
 * чёрном индиго»: поверхности различались на 4–5 единиц на канал.
 *
 * Сейчас различие держится на двух уровнях сразу:
 * - акценты: голубой + розовый + золотой (у Киберпанка — жёлтый + циан +
 *   красный, у Сакуры — только розовый);
 * - поверхности: тёплый кремово-сиреневый днём и ночной индиго ночью, тогда
 *   как у Киберпанка это почти чёрный графит.
 *
 * Тёмный вариант есть с самого начала: ночью светлая пастель слепит.
 *
 * Даётся квестом: восемь быстрых taps по иконке раздела «Внешний вид»
 * (раньше этот квест выдавал Сакуру — она теперь доступна по умолчанию).
 */
val KawaiiLightColors = lightColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8ECFF),
    onPrimaryContainer = Color(0xFF0D326E),
    secondary = Color(0xFFFF66A3),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE0EE),
    onSecondaryContainer = Color(0xFF681239),
    tertiary = Color(0xFFD99B00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFF0B8),
    onTertiaryContainer = Color(0xFF4D3400),
    background = Color(0xFFFFFDF9),
    onBackground = Color(0xFF332940),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF332940),
    surfaceVariant = Color(0xFFF3EEFF),
    onSurfaceVariant = Color(0xFF6B5E82),
    surfaceContainer = Color(0xFFFAF5FF),
    surfaceContainerHigh = Color(0xFFF2EAFE),
    outline = Color(0xFFC9BBE6),
    outlineVariant = Color(0xFFE5DCFA),
    error = Color(0xFFFF4D6D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE0E6),
    onErrorContainer = Color(0xFF690B22)
)

val KawaiiDarkColors = darkColorScheme(
    primary = Color(0xFF94C8FF),
    onPrimary = Color(0xFF0A2E5C),
    primaryContainer = Color(0xFF234E8A),
    onPrimaryContainer = Color(0xFFE0F0FF),
    secondary = Color(0xFFFF9EC7),
    onSecondary = Color(0xFF591134),
    secondaryContainer = Color(0xFF7D244F),
    onSecondaryContainer = Color(0xFFFFE3EE),
    tertiary = Color(0xFFFFE07D),
    onTertiary = Color(0xFF473200),
    tertiaryContainer = Color(0xFF6B4D00),
    onTertiaryContainer = Color(0xFFFFF3C4),
    background = Color(0xFF1A162B),
    onBackground = Color(0xFFF7F2FF),
    surface = Color(0xFF1A162B),
    onSurface = Color(0xFFF7F2FF),
    surfaceVariant = Color(0xFF322A4D),
    onSurfaceVariant = Color(0xFFCFC3EB),
    surfaceContainer = Color(0xFF241F3B),
    surfaceContainerHigh = Color(0xFF2F284A),
    outline = Color(0xFF7E70A6),
    outlineVariant = Color(0xFF463C66),
    error = Color(0xFFFF8FA3),
    onError = Color(0xFF54081B),
    errorContainer = Color(0xFF7D1930),
    onErrorContainer = Color(0xFFFFDCE2)
)
