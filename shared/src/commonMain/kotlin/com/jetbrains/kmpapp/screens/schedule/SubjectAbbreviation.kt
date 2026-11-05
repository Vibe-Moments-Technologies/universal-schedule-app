package com.jetbrains.kmpapp.screens.schedule

import com.jetbrains.kmpapp.data.widget.WidgetLabels

/**
 * Сокращение названия дисциплины для экрана расписания.
 *
 * Логика живёт в [WidgetLabels.abbreviateSubject] — один источник истины:
 * виджетам (Android Glance и iOS Swift-зеркало) и экрану нужно одно и то же
 * поведение, иначе сокращённое название в виджете не совпадёт с приложением.
 */
internal fun abbreviateSubjectName(subject: String): String =
    WidgetLabels.abbreviateSubject(subject)
