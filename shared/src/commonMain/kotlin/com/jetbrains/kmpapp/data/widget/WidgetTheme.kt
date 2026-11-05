package com.jetbrains.kmpapp.data.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.jetbrains.kmpapp.DarkColors
import com.jetbrains.kmpapp.LightColors
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.theme.HalloweenDarkColors
import com.jetbrains.kmpapp.theme.KawaiiDarkColors
import com.jetbrains.kmpapp.theme.KawaiiLightColors
import com.jetbrains.kmpapp.theme.MatrixDarkColors
import com.jetbrains.kmpapp.theme.MatrixLightColors
import com.jetbrains.kmpapp.theme.SakuraDarkColors
import com.jetbrains.kmpapp.theme.SakuraLightColors
import com.jetbrains.kmpapp.theme.ThemeOverlay
import com.jetbrains.kmpapp.theme.CyberpunkVariant
import com.jetbrains.kmpapp.theme.cyberpunkColors
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Цвета виджета, снятые с активной темы приложения.
 *
 * Виджет живёт вне Compose-дерева и не может прочитать `MaterialTheme`,
 * поэтому приложение при публикации снапшота кладёт сюда ARGB-значения
 * семантических ролей текущей схемы. Одинаковая структура у Android и iOS.
 */
@Serializable
data class WidgetThemeColors(
    val background: Int,
    val primaryText: Int,
    val subjectText: Int,
    val secondaryText: Int,
    val mutedText: Int,
    val accent: Int,
    val practice: Int,
    val divider: Int,
    val subtleSurface: Int,
    val badgeBackground: Int,
    val badgeText: Int
)

/** Выбирает ту же схему, что и `App()`, и переводит её в цвета виджета. */
object WidgetThemeResolver {
    fun resolve(
        themeMode: ThemeMode,
        overlay: ThemeOverlay,
        systemDark: Boolean,
        halloweenActive: Boolean,
        /** Вариант палитры «Киберпанка» (новая/старая) — как в App(). */
        cyberpunkVariant: CyberpunkVariant = CyberpunkVariant.DEFAULT
    ): WidgetThemeColors {
        val isDark = when (themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val scheme: ColorScheme = if (halloweenActive) {
            HalloweenDarkColors
        } else {
            when (overlay) {
                ThemeOverlay.SAKURA -> if (isDark) SakuraDarkColors else SakuraLightColors
                ThemeOverlay.KAWAII -> if (isDark) KawaiiDarkColors else KawaiiLightColors
                // Вариант палитры Киберпанка выбирается той же функцией,
                // что и в App(): «Старая» (маджента) обязана попасть и в
                // виджет, иначе владелец увидит другой цвет вне приложения.
                ThemeOverlay.CYBERPUNK -> cyberpunkColors(cyberpunkVariant, isDark)
                ThemeOverlay.MATRIX -> if (isDark) MatrixDarkColors else MatrixLightColors
                ThemeOverlay.HALLOWEEN -> HalloweenDarkColors
                ThemeOverlay.NONE -> if (isDark) DarkColors else LightColors
            }
        }
        return WidgetThemeColors(
            background = scheme.background.toArgb(),
            primaryText = scheme.onBackground.toArgb(),
            subjectText = scheme.onSurface.toArgb(),
            secondaryText = scheme.onSurfaceVariant.toArgb(),
            mutedText = scheme.outline.toArgb(),
            accent = scheme.primary.toArgb(),
            practice = scheme.tertiary.toArgb(),
            divider = scheme.outlineVariant.toArgb(),
            subtleSurface = scheme.surfaceVariant.toArgb(),
            badgeBackground = scheme.secondaryContainer.toArgb(),
            badgeText = scheme.onSecondaryContainer.toArgb()
        )
    }
}

object WidgetThemeCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun decode(raw: String?): WidgetThemeColors? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<WidgetThemeColors>(raw) }.getOrNull()
    }

    fun encode(colors: WidgetThemeColors): String = json.encodeToString(colors)
}
