package com.jetbrains.kmpapp.data.model

object AppVersion {
    /**
     * Базовая версия в формате YY.X.Z. Новое приложение — старт с 26.0.0.
     * CI подставляет полный VERSION_NAME по каналу:
     *  - тег v26.0.0 / v26.0.1       → stable
     *  - тег v26.0.0-beta.N / -rc.N   → prerelease (бета-система скрыта)
     *  - push в main                  → 26.X-dev.N (rolling preview)
     */
    const val RELEASE_VERSION = "26.0.0"
    const val VERSION_NAME = RELEASE_VERSION

    /** stable | beta | rc | dev | contrib — подставляет CI через tools/versioning.py */
    const val BUILD_CHANNEL = "stable"

    /**
     * Числовой код сборки. CI вычисляет ОДИН раз на запуск в resolve-джобе
     * (epoch-секунды) и раздаёт всем джобам через --build-id: монотонно во
     * всех каналах, влезает в Int32 / Android versionCode (max 2147483647).
     */
    const val BUILD_NUMBER = 1
    const val COMMIT_SHA = "local"

    /** Стабильный канал обновлений (обновляется только стабильными релизами). */
    const val UPDATE_FEED_URL = "https://raw.githubusercontent.com/Vibe-Moments-Technologies/universal-schedule-app/gh-pages/version.json"

    /** Канал бета-версий: система скрыта, фид не проверяется. */
    const val BETA_FEED_URL = "https://raw.githubusercontent.com/Vibe-Moments-Technologies/universal-schedule-app/gh-pages/beta.json"

    const val IS_CRITICAL = false
    const val MIN_SUPPORTED_BUILD = 1
    const val CHANGELOG = "Первый релиз «Расписания»: универсальный автономный конфигуратор семестра (вуз, группа, курс, звонки, недельный шаблон занятий) вместо загрузки с сервера. Подходит любому учебному заведению, даже если расписание — только табличка в PDF. Экспорт и импорт в открытом JSON-формате universal-schedule. Напоминания о занятиях и задачи работают полностью офлайн."

    const val DISPLAY_VERSION = "Версия $VERSION_NAME (сборка $BUILD_NUMBER)"
    const val GITHUB_REPO = "Vibe-Moments-Technologies/universal-schedule-app"
    const val GITHUB_REPO_URL = "https://github.com/Vibe-Moments-Technologies/universal-schedule-app"
    const val GITHUB_ISSUES_URL = "https://github.com/Vibe-Moments-Technologies/universal-schedule-app/issues"
}
