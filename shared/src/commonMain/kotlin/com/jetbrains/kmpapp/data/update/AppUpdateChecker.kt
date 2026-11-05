package com.jetbrains.kmpapp.data.update

import com.jetbrains.kmpapp.data.model.AppVersion
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class UpdateUrgency {
    UP_TO_DATE,
    MINOR_BUILD,
    NEW_VERSION,
    CRITICAL
}

@Serializable
data class VersionFeed(
    val version: String = "",
    val build: Int = 0,
    val critical: Boolean = false,
    @SerialName("min_supported_build")
    val minSupportedBuild: Int = 0,
    val changelog: String? = null
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String = "",
    val body: String? = null
)

/**
 * Результат проверки: только факт «есть версия новее» + метаданные для
 * информационного бейджа. Никаких ссылок и установщиков: приложение
 * распространяется через маркеты (они обновляют сами), sideload-пользователи
 * обновляются вручную со страницы релизов.
 */
data class UpdateCheckResult(
    val urgency: UpdateUrgency,
    val latestVersion: String,
    val latestBuild: Int,
    val currentVersion: String = AppVersion.VERSION_NAME,
    val currentBuild: Int = AppVersion.BUILD_NUMBER,
    val isCritical: Boolean = false,
    val changelog: String? = null
) {
    val hasUpdate: Boolean get() = urgency != UpdateUrgency.UP_TO_DATE
}

/**
 * Проверка стабильного канала: version.json на gh-pages, при его
 * недоступности — latest release через GitHub API. Проверяется для всех
 * источников установки: маркеты обновляют сами, но знать о новой версии
 * полезно (например, если автообновление маркета выключено).
 *
 * Бета-канал из приложения убран: в RuStore/Google Play/App Store бета
 * ведётся средствами самого стора.
 */
class AppUpdateChecker(
    private val client: HttpClient
) {
    companion object {
        private const val GITHUB_REPO = AppVersion.GITHUB_REPO
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun checkForUpdates(): UpdateCheckResult? = withContext(Dispatchers.IO) {
        // Тестовый фид (меню отладки): файл version-test.json на gh-pages —
        // версии крутятся как угодно для проверки поведения приложения.
        // Тумблер доступен только на dev/beta/contrib; DebugConfig сам
        // принудительно гасит флаг на стабильном релизе.
        val testFeed = com.jetbrains.kmpapp.data.DebugConfig.isTestVersionFeed.value
        if (testFeed) {
            fetchFeedResult(AppVersion.TEST_UPDATE_FEED_URL)
        } else {
            fetchFeedResult() ?: fetchLatestReleaseResult()
        }
    }

    private suspend fun fetchFeedResult(
        feedUrl: String = AppVersion.UPDATE_FEED_URL
    ): UpdateCheckResult? {
        return try {
            val response = client.get(feedUrl) {
                header("User-Agent", "Krasava-App")
                // raw.githubusercontent отдаёт max-age=300: NSURLSession (iOS)
                // и OkHttp-кеш могли отдавать СТАРЫЙ JSON из локального кеша
                // до 5 минут после правки фида — «тестовый фид не работает»
                // (репорт владельца). no-cache заставляет кеш свериться
                // с сервером: файл правки виден сразу.
                header("Cache-Control", "no-cache")
            }
            if (response.status.value !in 200..299) return null
            val feed = json.decodeFromString<VersionFeed>(response.body<String>())
            if (feed.version.isBlank()) return null
            // Диагностика (в т.ч. тестового фида): что реально прочитано.
            println("UpdateFeed $feedUrl -> ${feed.version} build=${feed.build}")

            val hasNewerVersion = VersionComparator.compare(feed.version, AppVersion.VERSION_NAME) > 0
            val hasNewerBuild = feed.build > AppVersion.BUILD_NUMBER
            val isUnderMinSupported = AppVersion.BUILD_NUMBER < feed.minSupportedBuild
            val isCritical = isUnderMinSupported || (feed.critical && (hasNewerVersion || hasNewerBuild))

            // NEW_VERSION требует и новую версию, и новую сборку: легаси-релизы (26.9.x)
            // численно больше всей линии 26.0.0, но их build (79) меньше любого epoch —
            // без этой проверки dev-сборкам предлагался бы даунгрейд до 26.9.1
            val urgency = when {
                isCritical -> UpdateUrgency.CRITICAL
                hasNewerVersion && hasNewerBuild -> UpdateUrgency.NEW_VERSION
                hasNewerBuild -> UpdateUrgency.MINOR_BUILD
                else -> UpdateUrgency.UP_TO_DATE
            }

            UpdateCheckResult(
                urgency = urgency,
                latestVersion = feed.version,
                latestBuild = feed.build,
                isCritical = isCritical,
                changelog = feed.changelog
            )
        } catch (e: Throwable) {
            println("Feed check error: ${e.message}")
            null
        }
    }

    private suspend fun fetchLatestReleaseResult(): UpdateCheckResult? {
        return try {
            val response = client.get("https://api.github.com/repos/$GITHUB_REPO/releases/latest") {
                header("User-Agent", "Krasava-App")
            }
            if (response.status.value !in 200..299) return null
            val release = response.body<GitHubRelease>()
            if (release.tagName.isBlank()) return null

            val latestTag = release.tagName.trimStart('v', 'V')
            val isNewerVersion = VersionComparator.compare(latestTag, AppVersion.VERSION_NAME) > 0

            UpdateCheckResult(
                urgency = if (isNewerVersion) UpdateUrgency.NEW_VERSION else UpdateUrgency.UP_TO_DATE,
                latestVersion = latestTag,
                latestBuild = AppVersion.BUILD_NUMBER,
                isCritical = false,
                changelog = release.body
            )
        } catch (t: Throwable) {
            println("GitHub API update check error: ${t.message}")
            null
        }
    }
}
