package com.jetbrains.kmpapp.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.jetbrains.kmpapp.data.widget.WidgetPinFeedback
import com.jetbrains.kmpapp.data.widget.consumePendingWidgetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Применение параметров, выбранных во внутреннем конфигураторе приложения
 * (см. SettingsScreen): лаунчеры не показывают системный конфигуратор при
 * добавлении виджета из приложения, поэтому выбор делается ДО закрепления,
 * а эта активность стартует success-callback'ом после подтверждения —
 * пишет выбор в Glance-состояние нового экземпляра и обновляет виджет.
 *
 * Не интерактивна: мелькает на долю секунды и закрывается — параметры
 * уже выбраны, переспрашивать нечего.
 */
class ScheduleWidgetApplyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Метка для экрана настроек: диалог закрепления не был подавлен,
        // и закрепление подтверждено (эта активность стартует только из
        // success-callback).
        WidgetPinFeedback.lastConfigureOpenedAtMillis = System.currentTimeMillis()

        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            // Источник — prefs (переживает перезапуск процесса), не RAM:
            // колбэк может сработать позже выбора, память уже могла
            // очиститься. Одноразовые: вручную добавленные позже виджеты
            // не должны получить чужой конфиг. Нет параметров — виджет
            // применит дефолт, просто закрываемся.
            val config = consumePendingWidgetConfig(this@ScheduleWidgetApplyActivity)
            if (config != null) {
                val manager = GlanceAppWidgetManager(this@ScheduleWidgetApplyActivity)
                val glanceId = runCatching { manager.getGlanceIdBy(appWidgetId) }.getOrNull()
                if (glanceId != null) {
                    runCatching {
                        updateAppWidgetState(
                            this@ScheduleWidgetApplyActivity,
                            glanceId
                        ) { prefs ->
                            prefs[WidgetTargetIdKey] = config.first
                            prefs[WidgetShortNamesKey] = config.second
                        }
                        ScheduleWidget().update(this@ScheduleWidgetApplyActivity, glanceId)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                if (!isFinishing) finish()
            }
        }
    }

    companion object {
        /** Имя для Intent'а success-callback'а (WidgetPinning.android). */
        const val INTENT_NAME = "com.jetbrains.kmpapp.widget.ScheduleWidgetApplyActivity"
    }
}
