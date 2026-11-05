package com.jetbrains.kmpapp.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Активна ли сейчас хэллоуинская тема (автоматически в сезон 28 октября —
 * 3 ноября, либо вручную тумблером). Позволяет любым экранам включать
 * тематические иконки/эффекты, не зная о дате.
 */
val LocalHalloweenEnabled = compositionLocalOf { false }
