package com.jetbrains.kmpapp.data.config

import com.jetbrains.kmpapp.data.storage.PlatformStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Загружает config.json с gh-pages при старте, кэширует локально.
 * При ошибке сети — использует кэш или дефолты.
 */
class RemoteConfigLoader(
    private val client: HttpClient,
    private val platformStorage: PlatformStorage
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _config = MutableStateFlow(RemoteConfig())
    val config: StateFlow<RemoteConfig> = _config.asStateFlow()

    init {
        // Восстанавливаем кэш
        try {
            val cached = platformStorage.getString(KEY_CONFIG)
            if (!cached.isNullOrBlank()) {
                _config.value = json.decodeFromString(cached)
            }
        } catch (_: Throwable) {}

        // Обновляем из сети
        refresh()
    }

    fun refresh() {
        scope.launch {
            try {
                val response = client.get(CONFIG_URL) {
                    header("User-Agent", "Krasava-App")
                }
                if (response.status.value in 200..299) {
                    val remote = json.decodeFromString<RemoteConfig>(response.body<String>())
                    _config.value = remote
                    platformStorage.saveString(KEY_CONFIG, response.body())
                }
            } catch (t: Throwable) {
                println("RemoteConfigLoader: refresh failed: ${t.message}")
            }
        }
    }

    companion object {
        private const val CONFIG_URL =
            "https://raw.githubusercontent.com/Vibe-Moments-Technologies/krasava-app/gh-pages/config.json"
        private const val KEY_CONFIG = "krasava_remote_config"
    }
}
