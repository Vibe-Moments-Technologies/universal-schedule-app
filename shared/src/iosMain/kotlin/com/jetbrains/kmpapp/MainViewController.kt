package com.jetbrains.kmpapp

import androidx.compose.ui.window.ComposeUIViewController
import com.jetbrains.kmpapp.screens.components.forceHideKeyboard
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UISwipeGestureRecognizer
import platform.UIKit.UISwipeGestureRecognizerDirectionDown
import platform.UIKit.UIViewController
import platform.objc.sel_registerName
import platform.darwin.NSObject

/**
 * Нативный свайп вниз по экрану закрывает клавиатуру в обход Compose:
 * Compose на iOS рендерится в Skia-canvas, а ввод идёт через скрытый
 * UITextView, который иногда теряет связь с Compose-деревом (кнопки
 * «жмякаются вхолостую»). UIKit ловит жест раньше Skia и зовёт
 * sendAction("resignFirstResponder") (общий ForceHideKeyboard).
 * cancelsTouchesInView = false + делегат — скроллы и клики Compose
 * продолжают работать одновременно с жестом.
 */
@OptIn(ExperimentalForeignApi::class)
private class KeyboardDismissHandler : NSObject(), UIGestureRecognizerDelegateProtocol {
    @ObjCAction
    fun handleGesture(sender: UIGestureRecognizer) {
        forceHideKeyboard()
    }

    override fun gestureRecognizer(
        gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer
    ): Boolean = true
}

// Держим ссылку: иначе ARC/GC соберёт хелпер, и жест умрёт молча.
private val dismissHandler = KeyboardDismissHandler()

@OptIn(ExperimentalForeignApi::class)
fun MainViewController(): UIViewController {
    val controller = ComposeUIViewController { App() }

    val swipeDown = UISwipeGestureRecognizer(
        target = dismissHandler,
        action = sel_registerName("handleGesture:")
    ).apply {
        direction = UISwipeGestureRecognizerDirectionDown
        // КРИТИЧНО: не блокировать тачи Compose — скролл и кнопки живут.
        cancelsTouchesInView = false
        delegate = dismissHandler
    }
    controller.view.addGestureRecognizer(swipeDown)

    return controller
}
