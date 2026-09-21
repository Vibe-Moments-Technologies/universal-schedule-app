package com.jetbrains.kmpapp.screens.components

import androidx.compose.runtime.Composable

/**
 * Файловый обмен расписанием: экспорт сохраняет JSON-файл, импорт читает
 * выбранный пользователем файл (null = отмена или ошибка чтения).
 * Android — SAF (CreateDocument/OpenDocument), iOS — share sheet (экспорт)
 * и UIDocumentPickerViewController (импорт).
 */
class ScheduleFilePicker(
    val export: (fileName: String, content: String) -> Unit,
    val import: (onResult: (String?) -> Unit) -> Unit
)

@Composable
expect fun rememberScheduleFilePicker(): ScheduleFilePicker
