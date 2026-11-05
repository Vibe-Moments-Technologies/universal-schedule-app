package com.jetbrains.kmpapp.screens.components

import com.jetbrains.kmpapp.data.storage.AndroidContextProvider
import android.view.inputmethod.InputMethodManager

/** Android: скрыть клавиатуру через InputMethodManager. */
actual fun forceHideKeyboard() {
    val ctx = AndroidContextProvider.context ?: return
    val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
        as? InputMethodManager ?: return
    val windowToken = (ctx as? android.app.Activity)?.window?.decorView?.windowToken
    if (windowToken != null) {
        imm.hideSoftInputFromWindow(windowToken, 0)
    } else {
        imm.toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY)
    }
}
