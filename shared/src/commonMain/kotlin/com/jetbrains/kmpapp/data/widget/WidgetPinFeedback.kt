package com.jetbrains.kmpapp.data.widget

import kotlin.time.Clock

/**
 * Обратная связь закрепления виджета.
 *
 * Конфигуратор виджета открывается success-callback'ом запроса
 * закрепления (см. WidgetPinning): если пользователь подтвердил
 * добавление — конфигуратор стартует и отмечает здесь момент открытия
 * (androidApp пишет метку, shared читает). Экран настроек после
 * запроса ждёт ограниченное время: метка не обновилась — системный
 * диалог был подавлен (OEM-запрет, «нет прав»), и вместо молчания
 * показывается предупреждение-диалог поверх экрана.
 */
object WidgetPinFeedback {
    /** Момент (epoch-мс) последнего запроса закрепления виджета. */
    var lastPinRequestedAtMillis: Long = 0L
    /** Момент (epoch-мс) последнего открытия конфигуратора виджета. */
    var lastConfigureOpenedAtMillis: Long = 0L

    /**
     * Параметры, выбранные во внутреннем конфигураторе приложения перед
     * закреплением (решение владельца): лаунчеры всё равно не показывают
     * системный конфигуратор при добавлении из приложения, поэтому выбор
     * делается заранее, а после подтверждения применяется к созданному
     * экземпляру. null — параметров нет (добавление через системный флоу).
     */
    var pendingConfig: Pair<Int, Boolean>? = null

    /** Момент установки [pendingConfig]: защита от «протухших» параметров. */
    var pendingConfigAtMillis: Long = 0L

    /** Запомнить выбор из внутреннего конфигуратора. */
    fun setPendingConfig(targetId: Int, shortNames: Boolean) {
        pendingConfig = Pair(targetId, shortNames)
        pendingConfigAtMillis = Clock.System.now().toEpochMilliseconds()
    }

    /** Отметить запрос закрепления (звонится экраном настроек). */
    fun markPinRequested() {
        lastPinRequestedAtMillis = Clock.System.now().toEpochMilliseconds()
    }
}
