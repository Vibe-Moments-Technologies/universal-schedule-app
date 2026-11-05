package com.jetbrains.kmpapp.screens.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * iOS: невидимый потребитель касаний в нижней зоне экрана.
 *
 * awaitPointerEvent в Main-проходе «забирает» события до того, как их
 * увидят скроллы контента: расписание не уезжает при свайпе системной
 * шторки. UIKit сам распознаёт edge-свайп домашнего индикатора — он
 * идёт в обход Compose-иерархии, поэтому шторка продолжает работать.
 */
actual fun Modifier.systemGestureBlocker(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            // Поглощаем поток событий, не давая его скроллам контента.
            // Ничего не рисуем и не анимируем: зона полностью невидима.
            awaitFirstDown(pass = PointerEventPass.Main)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.changes.none { it.pressed }) break
            }
        }
    }
