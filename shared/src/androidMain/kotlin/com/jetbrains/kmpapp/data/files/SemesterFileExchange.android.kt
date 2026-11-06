package com.jetbrains.kmpapp.data.files

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.jetbrains.kmpapp.data.storage.AndroidContextProvider

/**
 * Android-мост файлового обмена.
 *
 * Экспорт — share-интент: текст пишется во временный файл (FileProvider
 * объявлен в манифесте), система предлагает «Сохранить в файлы»,
 * мессенджеры и т.д.
 *
 * Импорт — системный выбор документа. Лаунчер регистрируется в
 * MainActivity (см. FilePickersBridge) и живёт со активностью;
 * результат уходит в колбэк вызова.
 */
actual object SemesterFileExchange {

    actual fun shareFile(fileName: String, content: String) {
        val context = AndroidContextProvider.context ?: return
        FilePickersBridge.shareJson(context, fileName, content)
    }

    actual fun pickJsonFile(onResult: (String?) -> Unit) {
        FilePickersBridge.pickJson(onResult)
    }
}

/** Лаунчер, зарегистрированный MainActivity; null до старта активности. */
object FilePickersBridge {
    var openDocument: ActivityResultLauncher<Array<String>>? = null

    fun shareJson(context: android.content.Context, fileName: String, content: String) {
        val dir = java.io.File(context.cacheDir, "exchange").apply { mkdirs() }
        val file = java.io.File(dir, fileName)
        file.writeText(content)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    fun pickJson(onResult: (String?) -> Unit) {
        val launcher = openDocument
            ?: run { onResult(null); return }
        PendingPick.holder = onResult
        launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }
}

/** Одноразовый колбэк последнего выбора файла. */
object PendingPick {
    var holder: ((String?) -> Unit)? = null
}
