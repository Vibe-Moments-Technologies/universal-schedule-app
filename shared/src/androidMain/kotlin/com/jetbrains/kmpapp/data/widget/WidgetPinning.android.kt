package com.jetbrains.kmpapp.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import com.jetbrains.kmpapp.data.storage.AndroidContextProvider

/**
 * Классы receiver'а и конфигуратора живут в модуле `androidApp`, поэтому
 * ссылаемся по имени: у shared нет и не должно быть обратной зависимости
 * от приложения.
 */
private const val WIDGET_RECEIVER_CLASS = "com.jetbrains.kmpapp.widget.ScheduleWidgetReceiver"
private const val WIDGET_APPLY_CLASS = "com.jetbrains.kmpapp.widget.ScheduleWidgetApplyActivity"

actual fun isScheduleWidgetPinningSupported(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val context = AndroidContextProvider.context ?: return false
    return runCatching {
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }.getOrDefault(false)
}

actual fun requestScheduleWidgetPin(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val context = AndroidContextProvider.context ?: return false
    return runCatching {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(context.packageName, WIDGET_RECEIVER_CLASS)
        // Выбор из внутреннего конфигуратора — персистим сразу (переживает
        // смерть процесса): лаунчер может забиндить виджет раньше или без
        // success-callback-активности, виджет применит параметры сам на
        // первом рендере.
        WidgetPinFeedback.pendingConfig?.let { (targetId, shortNames) ->
            context.getSharedPreferences(WIDGET_PREFS, android.content.Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_PENDING_TARGET, targetId)
                .putBoolean(KEY_PENDING_SHORT, shortNames)
                .commit()
        }
        // Success-callback: срабатывает после подтверждения пользователем.
        // Из приложения параметры выбираются ЗАРАНЕЕ — внутренний
        // конфигуратор (решение владельца: лаунчеры системный
        // конфигуратор при добавлении из приложения всё равно не
        // показывают). Колбэк ведёт в неинтерактивную
        // ScheduleWidgetApplyActivity: она пишет выбор в Glance-состояние
        // нового экземпляра (нет параметров — просто закрывается) и
        // помечает «диалог не был подавлен» для экрана настроек.
        val configIntent = Intent().apply {
            setClassName(context.packageName, WIDGET_APPLY_CLASS)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val configPendingIntent = PendingIntent.getActivity(
            context,
            0,
            configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.requestPinAppWidget(provider, null, configPendingIntent)
    }.getOrDefault(false)
}

/** Тот же файл prefs, что у ScheduleWidgetStore (общий контейнер). */
internal const val WIDGET_PREFS = "krasava_widget"
internal const val KEY_PENDING_TARGET = "pending_widget_target"
internal const val KEY_PENDING_SHORT = "pending_widget_short_names"

/**
 * Прочитать и снять персистентные параметры внутреннего конфигуратора
 * (вызов из androidApp — виджетом на первом рендере или ApplyActivity).
 * null — их нет (виджет добавлен через системный флоу).
 */
fun consumePendingWidgetConfig(context: android.content.Context): Pair<Int, Boolean>? {
    val prefs = context.getSharedPreferences(WIDGET_PREFS, android.content.Context.MODE_PRIVATE)
    if (!prefs.contains(KEY_PENDING_TARGET)) return null
    val config = Pair(
        prefs.getInt(KEY_PENDING_TARGET, -1),
        prefs.getBoolean(KEY_PENDING_SHORT, false)
    )
    prefs.edit().remove(KEY_PENDING_TARGET).remove(KEY_PENDING_SHORT).commit()
    return config
}

