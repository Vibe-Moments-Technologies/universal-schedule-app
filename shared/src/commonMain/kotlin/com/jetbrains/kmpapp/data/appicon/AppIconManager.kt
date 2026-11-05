package com.jetbrains.kmpapp.data.appicon

/**
 * Мост к системной смене иконки. Android-движок не регистрируется вовсе:
 * там иконка одна (новая) и автоподстраивается под тему через alias'ы
 * (ThemeIconSwitcher), выбора нет — строка настроек скрыта (SettingsScreen).
 * iOS хранит выбранную иконку сам (setAlternateIconName переживает
 * перезапуски), поэтому при старте ничего переустанавливать не нужно —
 * применяем только в момент выбора пользователем.
 */
object AppIconManager {
    /** Значения совпадают с именами appiconset'ов в Assets.xcassets (asset catalog). */
    const val ICON_DEFAULT = "default"
    const val ICON_ALT = "AppIconAlt"

    interface IconEngine {
        fun applyIcon(name: String)
    }

    var engine: IconEngine? = null
        private set

    /** true только там, где платформа умеет менять иконку (сейчас iOS). */
    val supportsSwitching: Boolean get() = engine != null

    fun setEngine(newEngine: IconEngine) {
        engine = newEngine
    }

    fun apply(name: String) {
        engine?.applyIcon(name)
    }
}
