package com.jetbrains.kmpapp.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DebugConfig {
    private val _isOfflineSimulated = MutableStateFlow(false)
    val isOfflineSimulated: StateFlow<Boolean> = _isOfflineSimulated.asStateFlow()

    fun setOfflineSimulated(enabled: Boolean) {
        _isOfflineSimulated.value = enabled
    }

    private val _isMapCoordinatePlaneEnabled = MutableStateFlow(false)
    val isMapCoordinatePlaneEnabled: StateFlow<Boolean> = _isMapCoordinatePlaneEnabled.asStateFlow()

    fun setMapCoordinatePlaneEnabled(enabled: Boolean) {
        _isMapCoordinatePlaneEnabled.value = enabled
    }

    /**
     * Тестовый фид версионирования (репорт владельца): проверка обновлений
     * читает version-test.json на gh-pages вместо официального version.json.
     *
     * Персистентен (репорт: «после перезапуска сбрасывается»): флаг
     * хранится в PlatformStorage, при старте читается. Правило владельца
     * не меняется: работает ТОЛЬКО на dev/beta/contrib-сборках; в
     * стабильном релизе (stable/rc) чтение гасит флаг и запись
     * игнорируется — официальный фид защищён от подмены.
     */
    private const val KEY_TEST_VERSION_FEED = "krasava_debug_test_version_feed"

    // Стабильный релиз: тестовый фид недопустим (правило владельца).
    private val testFeedAllowed: Boolean =
        com.jetbrains.kmpapp.data.model.AppVersion.BUILD_CHANNEL !in setOf("stable", "rc")

    private val storage by lazy {
        com.jetbrains.kmpapp.data.storage.PlatformStorage()
    }

    private val _isTestVersionFeed = MutableStateFlow(
        // Персистентность: стартовое значение — из хранилища (и только
        // на каналах, где тестовый фид разрешён).
        testFeedAllowed &&
            runCatching { storage.getString(KEY_TEST_VERSION_FEED) == "1" }.getOrDefault(false)
    )
    val isTestVersionFeed: StateFlow<Boolean> = _isTestVersionFeed.asStateFlow()

    fun setTestVersionFeed(enabled: Boolean) {
        val allowed = enabled && testFeedAllowed
        _isTestVersionFeed.value = allowed
        if (testFeedAllowed) {
            runCatching { storage.saveString(KEY_TEST_VERSION_FEED, if (allowed) "1" else "0") }
        }
    }

    fun reset() {
        _isOfflineSimulated.value = false
        _isTestVersionFeed.value = false
        runCatching { storage.remove(KEY_TEST_VERSION_FEED) }
    }

    /**
     * Карты Малой Пироговской (репорт владельца): ПО УМОЛЧАНИЮ — новая
     * съёмка mp-1-new (инверсия исходного тумблера); старые карты mp-1
     * включаются тумблером «Старая съёмка МП-1» в «Отладке и
     * экспериментах». Персистентен (как тестовый фид).
     */
    private const val KEY_OLD_MP1_MAP = "krasava_debug_old_mp1_map"

    private val _isOldMp1MapEnabled = MutableStateFlow(
        runCatching { storage.getString(KEY_OLD_MP1_MAP) == "1" }.getOrDefault(false)
    )
    val isOldMp1MapEnabled: StateFlow<Boolean> = _isOldMp1MapEnabled.asStateFlow()

    fun setOldMp1MapEnabled(enabled: Boolean) {
        _isOldMp1MapEnabled.value = enabled
        runCatching { storage.saveString(KEY_OLD_MP1_MAP, if (enabled) "1" else "0") }
    }
}
