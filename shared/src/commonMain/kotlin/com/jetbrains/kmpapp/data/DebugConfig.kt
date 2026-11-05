package com.jetbrains.kmpapp.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Отладочные тумблеры, переживающие пересоздание экрана отладки.
 * Живут только в памяти: рестарт приложения возвращает дефолты.
 */
object DebugConfig {

    /** Тестовый фид версионирования (меню отладки): проверка поведения
     *  обновлений с разными версиями/сборками/критичностями. */
    private val _isTestVersionFeed = MutableStateFlow(false)
    val isTestVersionFeed: StateFlow<Boolean> = _isTestVersionFeed.asStateFlow()

    fun setTestVersionFeed(enabled: Boolean) {
        _isTestVersionFeed.value = enabled
    }
}
