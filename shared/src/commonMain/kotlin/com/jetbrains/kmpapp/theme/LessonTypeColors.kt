package com.jetbrains.kmpapp.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.jetbrains.kmpapp.data.model.LessonColor
import com.jetbrains.kmpapp.data.model.LessonType

/**
 * Пользовательские переопределения цветов типов занятий (только изменённые
 * типы; пустая карта — все цвета стандартные). Предоставляется из [App] на
 * основе настроек, чтобы карточки и календарь перерисовывались при смене цвета.
 */
val LocalLessonTypeOverrides = staticCompositionLocalOf { emptyMap<LessonType, LessonColor>() }
