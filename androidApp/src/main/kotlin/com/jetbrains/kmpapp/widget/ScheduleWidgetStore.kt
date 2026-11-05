package com.jetbrains.kmpapp.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.jetbrains.kmpapp.data.widget.WidgetBridge

/**
 * Мост между приложением и виджетом на Android.
 *
 * iOS пишет снапшот в App Group (`WidgetSync.push()` в `iOSApp.swift`).
 * Android-аналог общего контейнера — приватный `SharedPreferences`-файл
 * приложения: его читает и приложение, и процесс виджета.
 */
object ScheduleWidgetStore {
    private const val PREFS = "krasava_widget"
    private const val KEY_PAYLOAD = "widget_payload"
    private const val KEY_THEME = "widget_theme"

    fun read(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PAYLOAD, null)

    fun readTheme(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, null)

    /**
     * Забирает свежий снапшот и цвета активной темы из общего `WidgetBridge`
     * и, если что-то изменилось, сохраняет их и обновляет все экземпляры.
     *
     * Вызывается из `MainActivity` при появлении/уходе приложения в фон —
     * так же, как iOS вызывает `WidgetSync.push()` по `scenePhase`.
     */
    suspend fun publish(context: Context) {
        val appContext = context.applicationContext
        val json = runCatching { WidgetBridge.payloadJson() }.getOrNull()
        val themeJson = runCatching {
            WidgetBridge.themeJson(appContext.isNightMode())
        }.getOrNull()
        if (json == null && themeJson == null) return
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var changed = false
        if (json != null && prefs.getString(KEY_PAYLOAD, null) != json) {
            editor.putString(KEY_PAYLOAD, json)
            changed = true
        }
        if (themeJson != null && prefs.getString(KEY_THEME, null) != themeJson) {
            editor.putString(KEY_THEME, themeJson)
            changed = true
        }
        if (!changed) return
        editor.commit()
        runCatching { ScheduleWidget().updateAll(appContext) }
    }
}
