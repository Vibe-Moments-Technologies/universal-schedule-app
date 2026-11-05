package com.jetbrains.kmpapp.data.storage

import android.content.Context
import java.io.File

actual open class NotesFileStorage {
    private val dir: File?
        get() = AndroidContextProvider.context?.filesDir?.let { File(it, "notes") }

    private fun file(): File? =
        dir?.apply { mkdirs() }?.let { File(it, FILE_NAME) }

    actual open fun read(): String? = try {
        file()?.takeIf { it.exists() }?.readText()
    } catch (_: Exception) {
        null
    }

    actual open fun write(content: String): Boolean = try {
        val f = file() ?: return false
        // Атомарно: временный файл + переименование. Обрыв питания посреди
        // записи не оставляет наполовину стёртые заметки.
        val tmp = File(f.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(content)
        tmp.renameTo(f)
    } catch (_: Exception) {
        false
    }

    actual open fun delete(): Boolean = try {
        file()?.delete() ?: false
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val FILE_NAME = "lesson_notes.json"
    }
}
