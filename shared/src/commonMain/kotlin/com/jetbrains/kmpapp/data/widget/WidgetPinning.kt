package com.jetbrains.kmpapp.data.widget

/**
 * Поддержка системного закрепления виджета на главном экране.
 *
 * Android 8+ умеет показывать системный диалог «Добавить виджет?» по запросу
 * приложения (`AppWidgetManager.requestPinAppWidget`). На iOS закрепление
 * виджетов из приложения недоступно — там пользователь добавляет их вручную.
 */
expect fun isScheduleWidgetPinningSupported(): Boolean

/**
 * Просит систему показать диалог добавления виджета расписания.
 *
 * @return true, если запрос принят (диалог показан); false — платформа не
 * поддерживает закрепление или запрос не удался.
 */
expect fun requestScheduleWidgetPin(): Boolean
