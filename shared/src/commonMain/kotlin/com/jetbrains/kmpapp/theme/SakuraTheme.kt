package com.jetbrains.kmpapp.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val SakuraLightColors = lightColorScheme(
    primary = Color(0xFFE87A90),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDE6),
    onPrimaryContainer = Color(0xFF5B112B),
    secondary = Color(0xFFB57C8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDECEF),
    onSecondaryContainer = Color(0xFF3E1A23),
    background = Color(0xFFFFF7F9),
    onBackground = Color(0xFF281C1E),
    surface = Color(0xFFFFF7F9),
    onSurface = Color(0xFF281C1E),
    surfaceContainer = Color(0xFFFDF0F3),
    surfaceContainerHigh = Color(0xFFF9E4E8)
)

// Киберпанк: кислотный жёлтый как основной, циан и сигнальный красный как
// акценты. Раньше основным был маджента — и тема сближалась с Каваем, у
// которой розовый тоже был ведущим цветом. Жёлтый + циан — это узнаваемый
// неон-нуар, и он не пересекается ни с одной другой темой приложения.
val CyberpunkLightColors = lightColorScheme(
    primary = Color(0xFF6E6200),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCE300),
    onPrimaryContainer = Color(0xFF1F1B00),
    secondary = Color(0xFF006874),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF84F2FF),
    onSecondaryContainer = Color(0xFF001F24),
    tertiary = Color(0xFFD00036),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9DD),
    onTertiaryContainer = Color(0xFF40000B),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF12151A),
    surface = Color(0xFFF7F8FA),
    onSurface = Color(0xFF12151A),
    surfaceVariant = Color(0xFFDFE4EC),
    onSurfaceVariant = Color(0xFF404854),
    surfaceContainer = Color(0xFFECEEF2),
    surfaceContainerHigh = Color(0xFFE2E6EC),
    outline = Color(0xFF6E7887),
    outlineVariant = Color(0xFFBFC7D4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

val CyberpunkDarkColors = darkColorScheme(
    primary = Color(0xFFFCE300),
    onPrimary = Color(0xFF1F1B00),
    primaryContainer = Color(0xFF4C4400),
    onPrimaryContainer = Color(0xFFFFF275),
    secondary = Color(0xFF00F0FF),
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF004F56),
    onSecondaryContainer = Color(0xFF9CF8FF),
    tertiary = Color(0xFFFF003C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF680014),
    onTertiaryContainer = Color(0xFFFFD1D6),
    background = Color(0xFF08090C),
    onBackground = Color(0xFFEAEAF0),
    surface = Color(0xFF08090C),
    onSurface = Color(0xFFEAEAF0),
    surfaceVariant = Color(0xFF1A212B),
    onSurfaceVariant = Color(0xFF94B0C2),
    surfaceContainer = Color(0xFF10141B),
    surfaceContainerHigh = Color(0xFF181E29),
    outline = Color(0xFF007A85),
    outlineVariant = Color(0xFF1F3642),
    error = Color(0xFFFF5449),
    onError = Color(0xFF370001),
    errorContainer = Color(0xFF730006),
    onErrorContainer = Color(0xFFFFDAD6)
)

// «Классический» Киберпанк: прежняя палитра темы (маджента + циан), сохранённая
// как вариант. Основную тему перекрасили в жёлтый/циан/красный, чтобы развести
// её с Каваем, но часть пользователей привыкла к мадженте — обе палитры живут
// рядом, выбор хранится отдельно от текущего оверлея. Значения побайтово те,
// что были до перекраски (git show aa62a6e~1).
val CyberpunkClassicLightColors = lightColorScheme(
    primary = Color(0xFFB000B5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFC6F8),
    onPrimaryContainer = Color(0xFF3B003D),
    secondary = Color(0xFF007F86),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF8FF),
    onSecondaryContainer = Color(0xFF002022),
    background = Color(0xFFFFF7FF),
    onBackground = Color(0xFF211A22),
    surface = Color(0xFFFFF7FF),
    onSurface = Color(0xFF211A22),
    surfaceContainer = Color(0xFFF8EAF8),
    surfaceContainerHigh = Color(0xFFF0D6F0)
)

val CyberpunkClassicDarkColors = darkColorScheme(
    primary = Color(0xFFFF5CE1),
    onPrimary = Color(0xFF4A004B),
    primaryContainer = Color(0xFF780078),
    onPrimaryContainer = Color(0xFFFFD7F5),
    secondary = Color(0xFF00F0FF),
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF00565C),
    onSecondaryContainer = Color(0xFF9CF8FF),
    background = Color(0xFF090B18),
    onBackground = Color(0xFFEFE5F5),
    surface = Color(0xFF090B18),
    onSurface = Color(0xFFEFE5F5),
    surfaceContainer = Color(0xFF17142B),
    surfaceContainerHigh = Color(0xFF241D3D)
)

/**
 * Палитра «Киберпанка» по выбранному варианту и схеме.
 *
 * Одна точка выбора вместо четырёх веток в вызывающем коде: и [App], и
 * предпросмотр в «Тематических темах» берут цвета одинаково.
 */
fun cyberpunkColors(variant: CyberpunkVariant, isDark: Boolean): ColorScheme = when (variant) {
    CyberpunkVariant.CLASSIC ->
        if (isDark) CyberpunkClassicDarkColors else CyberpunkClassicLightColors
    CyberpunkVariant.NEON ->
        if (isDark) CyberpunkDarkColors else CyberpunkLightColors
}

val SakuraDarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C3), onPrimary = Color(0xFF5B112B),
    primaryContainer = Color(0xFF7E2442), onPrimaryContainer = Color(0xFFFFDDE6),
    secondary = Color(0xFFE3BDC7), onSecondary = Color(0xFF422830),
    secondaryContainer = Color(0xFF5B3D46), onSecondaryContainer = Color(0xFFFFDDE6),
    background = Color(0xFF1B1114), onBackground = Color(0xFFF0DFE2),
    surface = Color(0xFF1B1114), onSurface = Color(0xFFF0DFE2),
    surfaceContainer = Color(0xFF281C20), surfaceContainerHigh = Color(0xFF33242A)
)

val MatrixLightColors = lightColorScheme(
    primary = Color(0xFF006E2E), onPrimary = Color.White,
    primaryContainer = Color(0xFF9FF2AC), onPrimaryContainer = Color(0xFF003914),
    secondary = Color(0xFF276F57), onSecondary = Color.White,
    secondaryContainer = Color(0xFFABF2D7), onSecondaryContainer = Color(0xFF002117),
    background = Color(0xFFF7FCF3), onBackground = Color(0xFF161E18),
    surface = Color(0xFFF7FCF3), onSurface = Color(0xFF161E18),
    surfaceContainer = Color(0xFFEBF4E7), surfaceContainerHigh = Color(0xFFE2EDDF)
)

val MatrixDarkColors = darkColorScheme(
    primary = Color(0xFF00E676), onPrimary = Color(0xFF003A00),
    primaryContainer = Color(0xFF00521D), onPrimaryContainer = Color(0xFF8DFFAC),
    secondary = Color(0xFF2EB88F), onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF00513F), onSecondaryContainer = Color(0xFFA8F5DE),
    background = Color(0xFF030505), onBackground = Color(0xFFD8F5E0),
    surface = Color(0xFF030505), onSurface = Color(0xFFD8F5E0),
    surfaceContainer = Color(0xFF0E1612), surfaceContainerHigh = Color(0xFF18241D)
)

val HalloweenDarkColors = darkColorScheme(
    primary = Color(0xFFFF5722), onPrimary = Color(0xFF1A0A00),
    primaryContainer = Color(0xFF7A1A00), onPrimaryContainer = Color(0xFFFFD1C1),
    secondary = Color(0xFFFF3B3B), onSecondary = Color(0xFF220000),
    secondaryContainer = Color(0xFF4A0000), onSecondaryContainer = Color(0xFFFFB3B3),
    tertiary = Color(0xFF9C27B0), onTertiary = Color(0xFF1A0023),
    tertiaryContainer = Color(0xFF4A0055), onTertiaryContainer = Color(0xFFE1B3FF),
    background = Color(0xFF050505), onBackground = Color(0xFFF0E8E8),
    surface = Color(0xFF050505), onSurface = Color(0xFFF0E8E8),
    surfaceContainer = Color(0xFF150A0A), surfaceContainerHigh = Color(0xFF201010),
    error = Color(0xFFFF6B6B), onError = Color(0xFF1A0000)
)
