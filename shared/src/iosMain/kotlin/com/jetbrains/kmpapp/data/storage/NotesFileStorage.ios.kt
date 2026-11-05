package com.jetbrains.kmpapp.data.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual open class NotesFileStorage {
    /**
     * Library/Application Support/Krasava — переживёт установку
     * обновления (в т.ч. GBox-переподпись): Documents-контейнер
     * пересоздавался при обновлении — заметки исчезали (репорт
     * владельца). Папка не видна в «Файлах» — надёжность важнее.
     * Одноразовая миграция подхватывает файл из старого Documents/Krasava.
     */
    private fun filePath(): String? {
        val support = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: return null
        val dir = "$support/Krasava"
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir, withIntermediateDirectories = true, attributes = null, error = null
        )
        // Миграция из Documents/Krasava (хранилище прошлой версии).
        val docs = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String
        if (docs != null) {
            val legacy = "$docs/Krasava/$FILE_NAME"
            val target = "$dir/$FILE_NAME"
            if (!NSFileManager.defaultManager.fileExistsAtPath(target) &&
                NSFileManager.defaultManager.fileExistsAtPath(legacy)
            ) {
                NSFileManager.defaultManager.moveItemAtPath(legacy, toPath = target, error = null)
            }
        }
        return "$dir/$FILE_NAME"
    }

    @OptIn(ExperimentalForeignApi::class)
    actual open fun read(): String? = try {
        val path = filePath() ?: return null
        val data = NSFileManager.defaultManager.contentsAtPath(path) as? NSData
        if (data == null) return null
        val size = data.length.toInt()
        if (size == 0) return ""
        val bytes = ByteArray(size)
        bytes.usePinned { pinned ->
            data.bytes?.let { src -> memcpy(pinned.addressOf(0), src, size.toULong()) }
        }
        bytes.decodeToString()
    } catch (_: Throwable) {
        null
    }

    @OptIn(ExperimentalForeignApi::class)
    actual open fun write(content: String): Boolean = try {
        val path = filePath() ?: return false
        // Прошлая схема createFileAtPath(tmp) + moveItemAtPath(tmp → path)
        // тихо падала при повторной записи: moveItemAtPath НЕ перезаписывает
        // существующий адресат (writeToFile:atomically: в Kotlin/Native не
        // экспортирован). Поэтому: tmp → remove старого → move. Окно риска
        // (между remove и move) — миллисекунды; при обрыве контент остаётся
        // в RAM хранилища и записывается следующим сохранением.
        val tmp = "$path.tmp"
        val bytes = content.encodeToByteArray()
        val data: NSData = bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
        val fm = NSFileManager.defaultManager
        if (!fm.createFileAtPath(tmp, contents = data, attributes = null)) return false
        if (fm.fileExistsAtPath(path)) {
            fm.removeItemAtPath(path, error = null)
        }
        fm.moveItemAtPath(tmp, toPath = path, error = null)
    } catch (_: Throwable) {
        false
    }

    actual open fun delete(): Boolean = try {
        val path = filePath() ?: return false
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    } catch (_: Throwable) {
        false
    }
}

private const val FILE_NAME = "lesson_notes.json"
