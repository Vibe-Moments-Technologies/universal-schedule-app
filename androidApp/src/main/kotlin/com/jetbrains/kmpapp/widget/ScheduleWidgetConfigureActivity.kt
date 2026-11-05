package com.jetbrains.kmpapp.widget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.jetbrains.kmpapp.data.widget.WidgetPayload
import com.jetbrains.kmpapp.data.widget.WidgetPayloadCodec
import com.jetbrains.kmpapp.data.widget.WidgetPinFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Настройка экземпляра виджета: выбор расписания и сокращение названий.
 *
 * Аналог iOS-конфигурации (`AppIntent` на iOS 17+): те же два параметра —
 * «Расписание» (с явной опцией «По умолчанию») и «Сокращать названия».
 * Хранится в состоянии Glance для конкретного `appWidgetId`, поэтому у
 * каждого виджета на экране свои настройки.
 */
class ScheduleWidgetConfigureActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        // Метка для экрана настроек: конфигуратор открылся — значит
        // системный диалог закрепления не был подавлен.
        WidgetPinFeedback.lastConfigureOpenedAtMillis = System.currentTimeMillis()

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val payload = WidgetPayloadCodec.decode(ScheduleWidgetStore.read(this@ScheduleWidgetConfigureActivity))
            val manager = GlanceAppWidgetManager(this@ScheduleWidgetConfigureActivity)
            val glanceId = runCatching { manager.getGlanceIdBy(appWidgetId) }.getOrNull()
            val stored = glanceId?.let { id -> readStoredConfig(id) }
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                if (payload == null) {
                    showMessage("Откройте приложение и обновите расписание, чтобы настроить виджет.")
                } else {
                    val targetId = stored?.first
                    val shortNames: Boolean = stored?.second ?: false
                    showTargetPicker(payload, targetId, shortNames, glanceId)
                }
            }
        }
    }

    /** Текущие настройки экземпляра: (targetId, shortNames). */
    private suspend fun readStoredConfig(
        glanceId: androidx.glance.GlanceId
    ): Pair<Int?, Boolean> = runCatching {
        val prefs = getAppWidgetState(
            this,
            PreferencesGlanceStateDefinition,
            glanceId
        )
        val target = prefs[WidgetTargetIdKey]
        // null = «По умолчанию» ещё не выбирали — чекбокс тоже не тронут.
        val short = prefs[WidgetShortNamesKey] ?: false
        Pair(target, short)
    }.getOrDefault(Pair(null, false))

    private fun showTargetPicker(
        payload: WidgetPayload,
        storedTargetId: Int?,
        storedShortNames: Boolean,
        glanceId: androidx.glance.GlanceId?
    ) {
        val targets = payload.targets
        if (targets.isEmpty()) {
            showMessage("В приложении пока нет сохранённых расписаний.")
            return
        }

        // Первая строка — «По умолчанию»: расписание, выбранное для
        // уведомлений. Явная опция вместо пустого выбора (тот же UX, что
        // у iOS-виджета): видно, что виджет работает по умолчанию, и можно
        // осознанно вернуться к этому после конкретной группы.
        val labels = arrayOf(DEFAULT_TARGET_LABEL) +
            targets.map { "${it.title} · ${it.typeName}" }.toTypedArray()

        // id -1 (опция «По умолчанию») в списке не лежит: резолвим позицию
        // по факту — сохранённый реальный id либо дефолтная строка.
        val initial = if (storedTargetId == null || storedTargetId == DEFAULT_TARGET_ID) {
            0
        } else {
            targets.indexOfFirst { it.id == storedTargetId }
                .takeIf { it >= 0 }?.plus(1) ?: 0
        }
        var selected = initial

        AlertDialog.Builder(this)
            .setTitle("Расписание для виджета")
            .setSingleChoiceItems(labels, initial) { _, which -> selected = which }
            .setPositiveButton("Далее") { _, _ ->
                val targetId = if (selected == 0) DEFAULT_TARGET_ID else targets[selected - 1].id
                showShortNamesToggle(targetId, storedShortNames, glanceId)
            }
            .setNegativeButton("Отмена") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showShortNamesToggle(
        targetId: Int,
        initialShortNames: Boolean,
        glanceId: androidx.glance.GlanceId?
    ) {
        // Отдельный диалог, а не второй внутри первого: системные
        // AlertDialog не складываются в стек, зато пошаговый выбор читается
        // легче, чем две группы контролов в одном окне.
        val checked = booleanArrayOf(initialShortNames)
        AlertDialog.Builder(this)
            .setTitle("Названия дисциплин")
            .setMultiChoiceItems(
                arrayOf("Сокращать длинные названия (инициалы слов)"),
                checked
            ) { _, _, isChecked -> checked[0] = isChecked }
            .setPositiveButton("Готово") { _, _ ->
                save(targetId, checked[0], glanceId)
            }
            .setNegativeButton("Назад") { _, _ ->
                // Возврат к выбору расписания с уже выбранным значением —
                // состояние не теряется, шаг можно перевыбрать.
                CoroutineScope(Dispatchers.IO).launch {
                    val payload = WidgetPayloadCodec.decode(
                        ScheduleWidgetStore.read(this@ScheduleWidgetConfigureActivity)
                    )
                    withContext(Dispatchers.Main) {
                        if (isFinishing || isDestroyed) return@withContext
                        if (payload != null) {
                            showTargetPicker(payload, targetId, checked[0], glanceId)
                        } else finish()
                    }
                }
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun save(targetId: Int, shortNames: Boolean, glanceId: androidx.glance.GlanceId?) {
        val id = appWidgetId
        if (glanceId == null) {
            finish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                updateAppWidgetState(
                    this@ScheduleWidgetConfigureActivity,
                    glanceId
                ) { prefs ->
                    prefs[WidgetTargetIdKey] = targetId
                    prefs[WidgetShortNamesKey] = shortNames
                }
                ScheduleWidget().update(this@ScheduleWidgetConfigureActivity, glanceId)
            }
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                setResult(
                    RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                )
                finish()
            }
        }
    }

    private fun showMessage(text: String) {
        AlertDialog.Builder(this)
            .setMessage(text)
            .setPositiveButton("Ок") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private companion object {
        /** Соглашение об id опции «По умолчанию»: реальных id < 0 не бывает. */
        const val DEFAULT_TARGET_ID = -1
        const val DEFAULT_TARGET_LABEL = "По умолчанию · расписание уведомлений"
    }
}
