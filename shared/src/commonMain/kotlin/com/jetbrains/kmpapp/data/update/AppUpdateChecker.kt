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
    val body: String? = null,
    @SerialName("html_url")
    val htmlUrl: String = ""
)

data class UpdateCheckResult(
    val urgency: UpdateUrgency,
    val latestVersion: String,
    val changelog: String? = null,
    val releaseUrl: String
) {
    val hasUpdate: Boolean get() = urgency != UpdateUrgency.UP_TO_DATE
}

/**
 * Проверка обновлений: только stable-канал, только «есть ли версия новее».
 * Скачивание и запуск установщика отключены — «Обновить» ведёт в браузер
 * на страницу релизов GitHub. Бета-система скрыта (код канала сохранён).
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
        fetchFeedResult(AppVersion.UPDATE_FEED_URL) ?: fetchLatestReleaseResult()
    }

    private suspend fun fetchFeedResult(url: String): UpdateCheckResult? {
        return try {
            val response = client.get(url) {
                header("User-Agent", "universal-schedule-app")
            }
            if (response.status.value !in 200..299) return null
            val feed = json.decodeFromString<VersionFeed>(response.body<String>())
            if (feed.version.isBlank()) return null

            val hasNewerVersion = VersionComparator.compare(feed.version, AppVersion.VERSION_NAME) > 0
            val hasNewerBuild = feed.build > AppVersion.BUILD_NUMBER
            val isCritical = AppVersion.BUILD_NUMBER < feed.minSupportedBuild ||
                (feed.critical && (hasNewerVersion || hasNewerBuild))

            val urgency = when {
                isCritical -> UpdateUrgency.CRITICAL
                hasNewerVersion && hasNewerBuild -> UpdateUrgency.NEW_VERSION
                hasNewerBuild -> UpdateUrgency.MINOR_BUILD
                else -> UpdateUrgency.UP_TO_DATE
            }

            UpdateCheckResult(
                urgency = urgency,
                latestVersion = feed.version,
                changelog = feed.changelog,
                releaseUrl = "https://github.com/$GITHUB_REPO/releases/latest"
            )
        } catch (e: Throwable) {
            println("Feed check error ($url): ${e.message}")
            null
        }
    }

    private suspend fun fetchLatestReleaseResult(): UpdateCheckResult? {
        return try {
            val response = client.get("https://api.github.com/repos/$GITHUB_REPO/releases/latest") {
                header("User-Agent", "universal-schedule-app")
            }
            if (response.status.value !in 200..299) return null
            val release = json.decodeFromString<GitHubRelease>(response.body<String>())
            if (release.tagName.isBlank()) return null

            val latestTag = release.tagName.trimStart('v', 'V')
            val isNewerVersion = VersionComparator.compare(latestTag, AppVersion.VERSION_NAME) > 0

            UpdateCheckResult(
                urgency = if (isNewerVersion) UpdateUrgency.NEW_VERSION else UpdateUrgency.UP_TO_DATE,
                latestVersion = latestTag,
                changelog = release.body,
                releaseUrl = release.htmlUrl.ifBlank { "https://github.com/$GITHUB_REPO/releases/latest" }
            )
        } catch (t: Throwable) {
            println("GitHub API update check error: ${t.message}")
            null
        }
    }
}
