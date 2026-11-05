package com.jetbrains.kmpapp.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * «Блики» темы Error на весь экран: те же скан-строка и полосы порчи, что
 * рисуются по полю «Сапёра», но на всю площадь приложения. Поэтому однотонные
 * поверхности (фон, карточки, контейнеры) во время темы «тлеют» тем же глюком,
 * что и игровое поле. Формулы совпадают с полевыми, время — кадры композиции.
 * Canvas без pointerInput не перехватывает тапы и не мешает управлению.
 */
@Composable
fun ErrorScanlines(enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now = it }
        }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val ms = now / 1_000_000L
        val scanH = (h + 60f).toInt().coerceAtLeast(1)

        // Яркая скан-строка, как на поле.
        val sy = (ms / 8 % scanH).toFloat() - 30f
        drawRect(
            color = Color(0x59FF3B30),
            topLeft = Offset(0f, sy),
            size = Size(w, 2f)
        )

        // Полосы «порчи» — те же четыре, что бегают по полю.
        for (i in 0 until 4) {
            val yy = (((ms / 3) * (16L * i + 11) + 41L * i) % scanH).toFloat() - 30f
            drawRect(
                color = Color(0x2EFF2D55),
                topLeft = Offset(0f, yy),
                size = Size(w, 6f + (i % 3) * 4f)
            )
        }

        // Циановая пара — вторая половина палитры ERROR (как GlitchText).
        for (i in 0 until 2) {
            val cy = (((ms / 5) * (23L * i + 7) + 19L * i) % scanH).toFloat() - 30f
            drawRect(
                color = Color(0x3800E5FF),
                topLeft = Offset(0f, cy),
                size = Size(w, 3f + i * 4f)
            )
        }
    }
}