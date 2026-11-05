package com.jetbrains.kmpapp.data.model

object AppVersion {
    /**
     * Базовая версия текущей линии разработки в формате YY.X.Z (старт: 26.0.0).
     * CI подставляет полный VERSION_NAME по каналу:
     *  - тег v26.0.0 / v26.0.1      → stable
     *  - тег v26.0.0-beta.3 / -rc.1  → beta / rc (prerelease)
     *  - push в main                 → 26.X-dev.N (rolling preview)
     *  - contributor build           → 26.X-contrib.N
     * v26.1.0 выпущен (сравнение расписаний, согласие, аналитика). v26.2.0 —
     * конспекты, сервисы, навигация, иконка, напоминания, фиксы недель и
     * обновлений. v26.3.0 выпущен: ребрендинг «Красава!», раздел «Ресурсы»,
     * заметки к парам, группировка настроек, информационные обновления.
     * Текущая линия: 26.4.0.
     */
    const val RELEASE_VERSION = "26.4.0"
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
    const val UPDATE_FEED_URL = "https://raw.githubusercontent.com/Vibe-Moments-Technologies/krasava-app/gh-pages/version.json"

    /**
     * Тестовый фид версионирования (меню отладки): отдельный файл на
     * gh-pages, редактируется вручную как угодно — проверять, как
     * приложение ведёт себя с разными версиями/сборками/критичностями.
     * Формат идентичен version.json. Читается только на dev/beta/contrib:
     * стабильный релиз жёстко остаётся на официальном фиде (правило
     * владельца).
     */
    const val TEST_UPDATE_FEED_URL = "https://raw.githubusercontent.com/Vibe-Moments-Technologies/krasava-app/gh-pages/version-test.json"

    const val IS_CRITICAL = false
    const val MIN_SUPPORTED_BUILD = 1
    const val CHANGELOG = "Диагностика: Sentry вместо AppMetrica — отправка только по желанию (тумблер в скрытом меню отладки), на тестовых сборках включена всегда. Приватность: расписания, заметки и настройки остаются на устройстве; пользовательское соглашение и политика конфиденциальности — на странице проекта. Раздел «Ресурсы»: сервисы университета, сообщества институтов и студенческих организаций — в одном месте, с папками и свайпом-назад на любой глубине. Скрытие панели навигации: тумблер в настройках — «Другое» открывается шестерёнкой на странице расписания, все сервисы становятся блоками внутри него. Новые типы занятий: экзамены, зачёты, курсовые, консультации, самост. работа — свои бейджи и цвета в расписании и календаре. Тип занятия определяется по полному названию из фида — филиальные аббревиатуры (ЛЕК, ЗД, ДЗ) распознаются корректно."

    const val DISPLAY_VERSION = "Версия $VERSION_NAME (сборка $BUILD_NUMBER)"
    const val GITHUB_REPO = "Vibe-Moments-Technologies/krasava-app"
    const val GITHUB_REPO_URL = "https://github.com/Vibe-Moments-Technologies/krasava-app"
    const val GITHUB_ISSUES_URL = "https://github.com/Vibe-Moments-Technologies/krasava-app/issues"
    const val DEVELOPER_NAME = "l1ratch"

}
