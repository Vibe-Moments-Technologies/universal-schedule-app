package com.jetbrains.kmpapp.data.update

import com.jetbrains.kmpapp.data.storage.PlatformStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Подавление диалога «Вышла новая версия» (репорт владельца):
 * чекбокс «Не показывать для этой версии» + «Понятно» → диалог молчит,
 * ПОКА фид показывает ту же (или более старую) версию. Вышла следующая —
 * сравнение через VersionComparator открывает диалог снова.
 *
 * Сброс — кнопкой в меню отладки («Отладка и эксперименты»).
 */
object UpdateDialogPrefs {
    private const val KEY_SUPPRESSED_VERSION = "krasava_update_dialog_suppressed_version"

    private val storage by lazy { PlatformStorage() }

    private val _suppressedVersion = MutableStateFlow(
        runCatching { storage.getString(KEY_SUPPRESSED_VERSION) }.getOrNull()
    )
    val suppressedVersion: StateFlow<String?> = _suppressedVersion.asStateFlow()

    /** Запомнить «не показывать для этой версии». */
    fun suppress(version: String) {
        _suppressedVersion.value = version
        runCatching { storage.saveString(KEY_SUPPRESSED_VERSION, version) }
    }

    /** Сброс (меню отладки): диалог снова показывается для текущей версии. */
    fun reset() {
        _suppressedVersion.value = null
        runCatching { storage.remove(KEY_SUPPRESSED_VERSION) }
    }
}
