package com.jetbrains.kmpapp.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «Тема Error»: тёмно-красный сбой, включается для варианта, решённого
 * с одного нажатия (рекорд 0 с). В отличие от Сакуры/Матрицы/Киберпанка
 * это не выбор пользователя, а «сломанное» состояние приложения — поэтому
 * глобальная палитра и моноширинная типографика приходят издалека, как
 * оверлей поверх любой другой темы.
 */
val ErrorColors = darkColorScheme(
    primary = Color(0xFFFF5252),
    onPrimary = Color(0xFF2A0000),
    primaryContainer = Color(0xFF7A0E0E),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFFB4554F),
    onSecondary = Color(0xFF2A0B0B),
    secondaryContainer = Color(0xFF5A1A1A),
    onSecondaryContainer = Color(0xFFFFDAD4),
    background = Color(0xFF150505),
    onBackground = Color(0xFFFFE5E2),
    surface = Color(0xFF150505),
    onSurface = Color(0xFFFFE5E2),
    surfaceContainer = Color(0xFF2A0B0B),
    surfaceContainerHigh = Color(0xFF3A1010),
    onSurfaceVariant = Color(0xFFE57373),
    error = Color(0xFFFF3B30),
    errorContainer = Color(0xFF8B0000),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Color(0xFF7A3A38),
    outlineVariant = Color(0xFF5A2626),
    inverseSurface = Color(0xFFFFE5E2),
    inverseOnSurface = Color(0xFF3A0A0A)
)

/** Та же типографика, что и по умолчанию, но моноширинная — как в «Сапере». */
val ErrorTypography: Typography = Typography().run {
    val family = FontFamily.Monospace
    Typography(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family)
    )
}

/**
 * Моноширинный заголовок «в духе ERROR»: рядом с темой всегда заметен.
 * При [glitch] рисуется объёмным наложением цветов — белая надпись с
 * циановым и красным «двойниками» со смещением, как заголовок ERROR.
 */
@Composable
fun MonoTitle(
    text: String,
    style: TextStyle,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    glitch: Boolean = false
) {
    val base = style.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black
    )
    if (glitch) {
        Box(modifier = modifier) {
            Text(text, style = base, color = Color(0x6600E5FF), modifier = Modifier.offset((-2).dp, 1.dp))
            Text(text, style = base, color = Color(0x66FF3B30), modifier = Modifier.offset(2.dp, (-1).dp))
            Text(text, style = base, color = color)
        }
    } else {
        Text(text, style = base, color = color, modifier = modifier)
    }
}

/** Заголовок с эффектом глюка: красная надпись с цветными «двойниками». */
@Composable
fun GlitchTitle(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val base = style.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        letterSpacing = 3.sp
    )
    Box(modifier = modifier) {
        Text(text, style = base, color = Color(0x4DFFB400), modifier = Modifier.offset(2.dp, 2.dp))
        Text(text, style = base, color = Color(0x6600E5FF), modifier = Modifier.offset((-2).dp, (-1).dp))
        Text(text, style = base, color = Color(0xFFFF3B30))
    }
}