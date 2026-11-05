package com.jetbrains.kmpapp.data.widget

import androidx.compose.ui.graphics.toArgb
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.theme.CyberpunkDarkColors
import com.jetbrains.kmpapp.theme.CyberpunkLightColors
import com.jetbrains.kmpapp.theme.CyberpunkClassicLightColors
import com.jetbrains.kmpapp.theme.CyberpunkVariant
import com.jetbrains.kmpapp.theme.HalloweenDarkColors
import com.jetbrains.kmpapp.theme.SakuraDarkColors
import com.jetbrains.kmpapp.theme.SakuraLightColors
import com.jetbrains.kmpapp.theme.ThemeOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class WidgetThemeTest {

    @Test
    fun lightAndDarkFollowThemeMode() {
        val light = WidgetThemeResolver.resolve(
            themeMode = ThemeMode.LIGHT,
            overlay = ThemeOverlay.NONE,
            systemDark = true,
            halloweenActive = false
        )
        val dark = WidgetThemeResolver.resolve(
            themeMode = ThemeMode.DARK,
            overlay = ThemeOverlay.NONE,
            systemDark = false,
            halloweenActive = false
        )
        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.primaryText, dark.primaryText)
    }

    @Test
    fun systemModeUsesSystemDarkness() {
        val light = WidgetThemeResolver.resolve(
            themeMode = ThemeMode.SYSTEM,
            overlay = ThemeOverlay.SAKURA,
            systemDark = false,
            halloweenActive = false
        )
        val dark = WidgetThemeResolver.resolve(
            themeMode = ThemeMode.SYSTEM,
            overlay = ThemeOverlay.SAKURA,
            systemDark = true,
            halloweenActive = false
        )
        assertEquals(SakuraLightColors.background.toArgb(), light.background)
        assertEquals(SakuraDarkColors.background.toArgb(), dark.background)
    }

    @Test
    fun overlayIsApplied() {
        val sakura = WidgetThemeResolver.resolve(
            themeMode = ThemeMode.LIGHT,
            overlay = ThemeOverlay.SAKURA,
            systemDark = false,
            halloweenActive = false
        )
        val cyberpunk = WidgetThemeResolver.resolve(
            themeMode = ThemeMode.LIGHT,
            overlay = ThemeOverlay.CYBERPUNK,
            systemDark = false,
            halloweenActive = false
        )
        assertEquals(SakuraLightColors.primary.toArgb(), sakura.accent)
        assertEquals(CyberpunkLightColors.primary.toArgb(), cyberpunk.accent)
        assertNotEquals(sakura.background, cyberpunk.background)
    }

    @Test
    fun halloweenOverridesOverlayEvenInLightMode() {
        val colors = WidgetThemeResolver.resolve(
            themeMode = ThemeMode.LIGHT,
            overlay = ThemeOverlay.CYBERPUNK,
            systemDark = false,
            halloweenActive = true
        )
        assertEquals(HalloweenDarkColors.background.toArgb(), colors.background)
        assertEquals(HalloweenDarkColors.onSurface.toArgb(), colors.subjectText)
    }

    @Test
    fun darkCyberpunkIsAppliedForDarkMode() {
        val colors = WidgetThemeResolver.resolve(
            themeMode = ThemeMode.DARK,
            overlay = ThemeOverlay.CYBERPUNK,
            systemDark = false,
            halloweenActive = false
        )
        assertEquals(CyberpunkDarkColors.primary.toArgb(), colors.accent)
        assertEquals(CyberpunkDarkColors.secondaryContainer.toArgb(), colors.badgeBackground)
    }

    @Test
    fun cyberpunkVariantChangesWidgetColors() {
        // «Старая» палитра (маджента) должна попадать и в виджет — выбор
        // варианта действует вне приложения, как и смена самой темы.
        val neon = WidgetThemeResolver.resolve(
            themeMode = ThemeMode.LIGHT,
            overlay = ThemeOverlay.CYBERPUNK,
            systemDark = false,
            halloweenActive = false,
            cyberpunkVariant = CyberpunkVariant.NEON
        )
        val classic = WidgetThemeResolver.resolve(
            themeMode = ThemeMode.LIGHT,
            overlay = ThemeOverlay.CYBERPUNK,
            systemDark = false,
            halloweenActive = false,
            cyberpunkVariant = CyberpunkVariant.CLASSIC
        )
        assertEquals(CyberpunkLightColors.primary.toArgb(), neon.accent)
        assertEquals(CyberpunkClassicLightColors.primary.toArgb(), classic.accent)
        assertNotEquals(neon.accent, classic.accent)
    }

    @Test
    fun cyberpunkVariantDefaultsToNeonForOlderCallers() {
        // Вызов без параметра (сторонний код, старый тест) не должен
        // получить случайно «старую» палитру.
        val defaulted = WidgetThemeResolver.resolve(
            themeMode = ThemeMode.LIGHT,
            overlay = ThemeOverlay.CYBERPUNK,
            systemDark = false,
            halloweenActive = false
        )
        val explicit = WidgetThemeResolver.resolve(
            themeMode = ThemeMode.LIGHT,
            overlay = ThemeOverlay.CYBERPUNK,
            systemDark = false,
            halloweenActive = false,
            cyberpunkVariant = CyberpunkVariant.NEON
        )
        assertEquals(explicit.accent, defaulted.accent)
    }

    @Test
    fun codecRoundTripsAndIgnoresBrokenInput() {
        val colors = WidgetThemeResolver.resolve(
            themeMode = ThemeMode.DARK,
            overlay = ThemeOverlay.MATRIX,
            systemDark = false,
            halloweenActive = false
        )
        assertEquals(colors, WidgetThemeCodec.decode(WidgetThemeCodec.encode(colors)))
        assertNull(WidgetThemeCodec.decode(null))
        assertNull(WidgetThemeCodec.decode("not json"))
    }
}
