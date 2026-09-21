package com.jetbrains.kmpapp.screens.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

@Composable
actual fun rememberScheduleFilePicker(): ScheduleFilePicker {
    return remember {
        ScheduleFilePicker(
            export = { fileName, content -> exportViaShareSheet(fileName, content) },
            import = { onResult -> importViaDocumentPicker(onResult) }
        )
    }
}

/** Корневой VC ключевого окна: из него показываем системные контроллеры. */
@Suppress("DEPRECATION")
private fun activeRootViewController(): UIViewController? =
    UIApplication.sharedApplication.keyWindow?.rootViewController

@OptIn(ExperimentalForeignApi::class)
private fun exportViaShareSheet(fileName: String, content: String) {
    val root = activeRootViewController() ?: return
    // Файл во временную директорию — share sheet сам предложит «Сохранить
    // в Файлы», AirDrop, почту и т.д.
    val path = NSTemporaryDirectory() + fileName
    val ok = NSString.create(string = content)
        .writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    if (!ok) return
    val url = NSURL.fileURLWithPath(path)
    val activity = UIActivityViewController(
        activityItems = listOf<Any>(url),
        applicationActivities = null
    )
    // iPad: обязателен якорь поповера, иначе краш.
    val anchor: UIView? = root.view
    activity.popoverPresentationController?.let { popover ->
        popover.sourceView = anchor
        if (anchor != null) {
            popover.sourceRect = anchor.bounds.useContents {
                CGRectMake(size.width / 2.0, size.height / 2.0, 1.0, 1.0)
            }
        }
    }
    root.presentViewController(activity, animated = true, completion = null)
}

/** Сильная ссылка на делегат, пока системный пикер открыт. */
private var activeImportDelegate: ImportDelegate? = null

private class ImportDelegate(
    private val onResult: (String?) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        // asCopy = true: файл уже скопирован системой во временное хранилище.
        val text = url?.let {
            NSString.create(contentsOfURL = it, encoding = NSUTF8StringEncoding, error = null)?.toString()
        }
        activeImportDelegate = null
        onResult(text)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        activeImportDelegate = null
        onResult(null)
    }
}

private fun importViaDocumentPicker(onResult: (String?) -> Unit) {
    val root = activeRootViewController() ?: run { onResult(null); return }
    val picker = UIDocumentPickerViewController(
        forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText),
        asCopy = true
    )
    val delegate = ImportDelegate(onResult)
    activeImportDelegate = delegate
    picker.delegate = delegate
    root.presentViewController(picker, animated = true, completion = null)
}
