package com.jetbrains.kmpapp.data.storage

/**
 * Минимальный key-value контракт, который нужен заметкам: сохранённое
 * значение по строковому ключу. [PlatformStorage] реализует его в
 * продакшене; тесты подставляют in-memory фейк — заметки обязаны
 * переживать clearAll(), и это удобно проверять без платформы.
 */
interface KeyValueStore {
    fun saveString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)
    fun clearAll()
}
