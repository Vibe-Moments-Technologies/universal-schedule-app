package com.jetbrains.kmpapp.screens.components

/**
 * Принудительное скрытие системной клавиатуры, минуя Compose-фокус.
 *
 * Compose clearFocus/hide в модальных шитах iOS срабатывает не всегда
 * (скрытый UITextView теряет связь с деревом). Платформенная реализация
 * работает в обход: Android — стандартный hide, iOS — UIKit
 * sendAction("resignFirstResponder") без адресата: система сама
 * находит активный FirstResponder.
 */
expect fun forceHideKeyboard()
