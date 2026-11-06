package com.jetbrains.kmpapp.data.model

object AppVersion {
    /**
     * Базовая версия текущей линии разработки в формате YY.X.Z (старт: 26.0.0).
     * CI подставляет полный VERSION_NAME по каналу:
     *  - тег v26.0.0 / v26.0.1      → stable
     *  - тег v26.0.0-beta.3 / -rc.1  → beta / rc (prerelease)
     *  - push в main                 → 26.X-dev.N (rolling preview)
     *  - contributor build           → 26.X-contrib.N
     * База перезаписана; линия разработки продолжается с 26.1.0.
     */
    const val RELEASE_VERSION = "26.1.0"
    const val VERSION_NAME = RELEASE_VERSION

    /** stable | beta | rc | dev | contrib — подставляет CI через tools/versioning.py */
    const val BUILD_CHANNEL = "stable"

    /**
     * Числовой код сборки. CI вычисляет ОДИН раз на запуск в resolve-джобе
     * (epoch-секунды) и раздаёт всем джобам через --build-id: монотонно во
     * всех каналах, влезает в Int32 / Android versionCode (max 2147483647).
     * github.run_id и github.run_started_at НЕ подходят.
     */
    const val BUILD_NUMBER = 32
    const val COMMIT_SHA = "local"

    /** Стабильный канал обновлений (обновляется только стабильными релизами). */
    const val UPDATE_FEED_URL = "https://raw.githubusercontent.com/Vibe-Moments-Technologies/universal-schedule-app/gh-pages/version.json"

    /**
     * Тестовый фид версионирования (меню отладки): отдельный файл на
     * gh-pages, редактируется вручную как угодно — проверять, как
     * приложение ведёт себя с разными версиями/сборками/критичностями.
     * Формат идентичен version.json. Читается только на dev/beta/contrib:
     * стабильный релиз жёстко остаётся на официальном фиде.
     */
    const val TEST_UPDATE_FEED_URL = "https://raw.githubusercontent.com/Vibe-Moments-Technologies/universal-schedule-app/gh-pages/version-test.json"

    const val IS_CRITICAL = false
    const val MIN_SUPPORTED_BUILD = 1
    const val CHANGELOG = "Перезапуск на новой базе: универсальное расписание без привязки к вузу. Конфигуратор семестра — вуз, группа, звонки и недельный шаблон занятий; всё хранится на устройстве. Виджеты, игры, темы и задачи сохранены."

    const val DISPLAY_VERSION = "Версия $VERSION_NAME (сборка $BUILD_NUMBER)"
    const val GITHUB_REPO = "Vibe-Moments-Technologies/universal-schedule-app"
    const val GITHUB_REPO_URL = "https://github.com/Vibe-Moments-Technologies/universal-schedule-app"
    const val GITHUB_ISSUES_URL = "https://github.com/Vibe-Moments-Technologies/universal-schedule-app/issues"
    const val DEVELOPER_NAME = "l1ratch"

}
