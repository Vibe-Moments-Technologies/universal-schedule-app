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
    val changelog: String? = null,
    @SerialName("download_url")
    val downloadUrl: String? = null,
    @SerialName("apk_url")
    val apkUrl: String? = null,
    @SerialName("ipa_url")
    val ipaUrl: String? = null,
    val channel: String = "stable",
    val prerelease: Boolean = false
)

@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = ""
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url")
    val htmlUrl: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

data class UpdateCheckResult(
    val urgency: UpdateUrgency,
    val latestVersion: String,
    val latestBuild: Int,
    val currentVersion: String = AppVersion.VERSION_NAME,
    val currentBuild: Int = AppVersion.BUILD_NUMBER,
    val isCritical: Boolean = false,
    val changelog: String? = null,
    val downloadUrl: String,
    val releaseUrl: String,
    val apkUrl: String? = null,
    val channel: String = "stable",
    val isPrerelease: Boolean = false,
    /** URL страницы в маркете, если приложение установлено оттуда. */
    val storeUrl: String? = null
) {
    val hasUpdate: Boolean get() = urgency != UpdateUrgency.UP_TO_DATE

    /**
     * URL для кнопки «Обновить»: маркет → страница в маркете,
     * иначе → прямое скачивание / страница релиза.
     */
    val actionUrl: String get() = storeUrl ?: downloadUrl
}

class AppUpdateChecker(
    private val client: HttpClient,
    private val syncManager: com.jetbrains.kmpapp.data.sync.UnifiedSyncManager
) {
    companion object {
        private const val GITHUB_REPO = AppVersion.GITHUB_REPO
        /** Страница приложения в маркете — подставляется при установке оттуда. */
        // Временно: GitHub-релизы. Заменить на реальные URL после публикации.
        const val RUSTORE_URL = "https://github.com/Vibe-Moments-Technologies/universal-schedule-app/releases/latest"
        const val GOOGLE_PLAY_URL = "https://github.com/Vibe-Moments-Technologies/universal-schedule-app/releases/latest"
        const val APP_STORE_URL = "https://github.com/Vibe-Moments-Technologies/universal-schedule-app/releases/latest"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun checkForUpdates(includeBeta: Boolean = false): UpdateCheckResult? = withContext(Dispatchers.IO) {
        // Google Play / App Store обновляют нативно — наша система не нужна.
        // RuStore: пока ведём на страницу в маркете; TODO: подключить RuStore SDK.
        val source = detectInstallSource()
        if (source == InstallSource.GOOGLE_PLAY || source == InstallSource.APP_STORE) {
            return@withContext null
        }

        // Бета-канал проверяется только если пользователь явно включил тумблер.
        // Раньше тестовые сборки форсировали проверку беты — из-за этого бета
        // предлагалась даже с выключенным тумблером.
        val stableResult = fetchFeedResult(AppVersion.UPDATE_FEED_URL, channel = "stable", isPrerelease = false)
        val betaResult = if (includeBeta) {
            fetchFeedResult(AppVersion.BETA_FEED_URL, channel = "beta", isPrerelease = true)
        } else {
            null
        }

        val result = pickBestResult(betaResult, stableResult) ?: fetchLatestReleaseResult()
        // Роутинг обновлений: RuStore-установка ведёт на страницу в маркете.
        result?.let {
            val storeUrl = if (source == InstallSource.RUSTORE) RUSTORE_URL else null
            if (storeUrl != null) it.copy(storeUrl = storeUrl) else it
        }
    }

    private suspend fun fetchFeedResult(url: String, channel: String, isPrerelease: Boolean): UpdateCheckResult? {
        return try {
            val response = client.get(url) {
                header("User-Agent", "universal-schedule-app")
            }
            if (response.status.value !in 200..299) return null
            val feed = json.decodeFromString<VersionFeed>(response.body<String>())
            if (feed.version.isBlank()) return null

            val hasNewerVersion = VersionComparator.compare(feed.version, AppVersion.VERSION_NAME) > 0
            val hasNewerBuild = feed.build > AppVersion.BUILD_NUMBER
            val isUnderMinSupported = AppVersion.BUILD_NUMBER < feed.minSupportedBuild
            val isCritical = !isPrerelease && (isUnderMinSupported || (feed.critical && (hasNewerVersion || hasNewerBuild)))

            // NEW_VERSION требует и новую версию, и новую сборку: легаси-релизы (26.9.x)
            // численно больше всей линии 26.0.0, но их build (79) меньше любого epoch —
            // без этой проверки dev/beta-сборкам предлагался бы даунгрейд до 26.9.1
            val urgency = when {
                isCritical -> UpdateUrgency.CRITICAL
                hasNewerVersion && hasNewerBuild -> UpdateUrgency.NEW_VERSION
                hasNewerBuild -> UpdateUrgency.MINOR_BUILD
                else -> UpdateUrgency.UP_TO_DATE
            }

            val releaseUrl = if (isPrerelease) {
                "https://github.com/$GITHUB_REPO/releases/tag/preview"
            } else {
                "https://github.com/$GITHUB_REPO/releases/latest"
            }
            val downloadUrl = feed.downloadUrl ?: feed.apkUrl ?: releaseUrl

            UpdateCheckResult(
                urgency = urgency,
                latestVersion = feed.version,
                latestBuild = feed.build,
                currentVersion = AppVersion.VERSION_NAME,
                currentBuild = AppVersion.BUILD_NUMBER,
                isCritical = isCritical,
                changelog = feed.changelog,
                downloadUrl = downloadUrl,
                releaseUrl = releaseUrl,
                apkUrl = feed.apkUrl ?: feed.downloadUrl,
                channel = feed.channel.ifBlank { channel },
                isPrerelease = isPrerelease
            )
        } catch (e: Throwable) {
            println("Feed check error ($url): ${e.message}")
            null
        }
    }

    /** Из двух кандидатов выбирает более свежую версию; при равенстве выигрывает стабильная. */
    private fun pickBestResult(preview: UpdateCheckResult?, stable: UpdateCheckResult?): UpdateCheckResult? {
        if (preview == null) return stable
        if (stable == null) return preview
        // Кандидат с доступным обновлением приоритетнее «актуального»:
        // иначе легаси-стабильный (26.9.x, «актуально» после guard'а сборок)
        // строково обыгрывал бы новую бету (26.0.0-beta.1)
        if (preview.hasUpdate != stable.hasUpdate) {
            return if (preview.hasUpdate) preview else stable
        }
        val c = VersionComparator.compare(preview.latestVersion, stable.latestVersion)
        return if (c > 0) preview else stable
    }

    private suspend fun fetchLatestReleaseResult(): UpdateCheckResult? {
        return try {
            val response = client.get("https://api.github.com/repos/$GITHUB_REPO/releases/latest") {
                header("User-Agent", "universal-schedule-app")
            }
            if (response.status.value !in 200..299) return null
            val release = response.body<GitHubRelease>()
            if (release.tagName.isBlank()) return null

            val latestTag = release.tagName.trimStart('v', 'V')
            val isNewerVersion = VersionComparator.compare(latestTag, AppVersion.VERSION_NAME) > 0

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            val downloadUrl = apkAsset?.browserDownloadUrl ?: release.htmlUrl

            UpdateCheckResult(
                urgency = if (isNewerVersion) UpdateUrgency.NEW_VERSION else UpdateUrgency.UP_TO_DATE,
                latestVersion = release.tagName,
                latestBuild = AppVersion.BUILD_NUMBER,
                currentVersion = AppVersion.VERSION_NAME,
                currentBuild = AppVersion.BUILD_NUMBER,
                isCritical = false,
                changelog = release.body,
                downloadUrl = downloadUrl,
                releaseUrl = release.htmlUrl,
                apkUrl = apkAsset?.browserDownloadUrl,
                channel = "stable",
                isPrerelease = false
            )
        } catch (t: Throwable) {
            println("GitHub API update check error: ${t.message}")
            null
        }
    }
}
