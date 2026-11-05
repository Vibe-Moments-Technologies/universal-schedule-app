package com.jetbrains.kmpapp.screens.components

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.objc.sel_registerName

/**
 * iOS: sendAction("resignFirstResponder") без адресата — UIKit сам
 * находит активный FirstResponder (в любом окне/слое) и закрывает
 * клавиатуру. Работает на iOS 15–18; keyWindow тут ненадёжен.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun forceHideKeyboard() {
    UIApplication.sharedApplication.sendAction(
        action = sel_registerName("resignFirstResponder"),
        to = null,
        from = null,
        forEvent = null
    )
}
