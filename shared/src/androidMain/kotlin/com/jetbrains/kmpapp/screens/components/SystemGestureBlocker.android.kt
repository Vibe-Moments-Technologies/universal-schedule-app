package com.jetbrains.kmpapp.screens.components

import androidx.compose.ui.Modifier

/** Android: блокер не нужен — системные жесты обрабатываются до приложения. */
actual fun Modifier.systemGestureBlocker(): Modifier = this
