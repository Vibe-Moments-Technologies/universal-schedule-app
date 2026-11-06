package com.jetbrains.kmpapp.data.files

/**
 * Платформенный мост файлового обмена: экспорт семестра в файл
 * и импорт JSON-файла обратно.
 *
 * Android реализует мост напрямую (см. SemesterFileExchange.android.kt);
 * iOS работает через Swift-движок (FileExchangeEngine в iosApp),
 * регистрируемый в iOSApp.swift — вся UIKit-логика живёт в Swift.
 */
expect object SemesterFileExchange {
    fun shareFile(fileName: String, content: String)
    fun pickJsonFile(onResult: (String?) -> Unit)
}
