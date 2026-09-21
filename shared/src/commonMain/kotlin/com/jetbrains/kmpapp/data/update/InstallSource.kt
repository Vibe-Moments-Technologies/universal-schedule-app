package com.jetbrains.kmpapp.data.update

/**
 * Источник установки приложения. Определяется один раз при старте.
 *
 * Логика обновлений:
 *  - GITHUB / GBOX / UNKNOWN → наша система (скачивание APK / переход на релиз)
 *  - RUSTORE → ссылка на страницу приложения в RuStore
 *  - GOOGLE_PLAY → ссылка на страницу в Google Play
 *  - APP_STORE → ссылка на страницу в App Store
 */
enum class InstallSource {
    GITHUB,
    GBOX,
    RUSTORE,
    GOOGLE_PLAY,
    APP_STORE,
    UNKNOWN
}

expect fun detectInstallSource(): InstallSource
