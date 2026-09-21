package com.jetbrains.kmpapp.screens.components

import androidx.compose.runtime.Composable

/**
 * На iOS системной кнопки «назад» нет: возврат обеспечивают свайп-жест
 * LayeredNavHost и стрелка на иконке дока, поэтому actual пустой.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no-op
}
