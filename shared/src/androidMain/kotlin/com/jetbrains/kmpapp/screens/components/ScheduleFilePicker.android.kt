package com.jetbrains.kmpapp.screens.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberScheduleFilePicker(): ScheduleFilePicker {
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<((String?) -> Unit)?>(null) }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val content = pendingExport
        pendingExport = null
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.encodeToByteArray())
                }
            }
        }
    }

    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val callback = pendingImport
        pendingImport = null
        val text = uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    stream.readBytes().decodeToString()
                }
            }.getOrNull()
        }
        callback?.invoke(text)
    }

    return remember(createDoc, openDoc) {
        ScheduleFilePicker(
            export = { fileName, content ->
                pendingExport = content
                createDoc.launch(fileName)
            },
            import = { onResult ->
                pendingImport = onResult
                openDoc.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
            }
        )
    }
}
