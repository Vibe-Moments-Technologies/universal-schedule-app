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
     * обновлений. Текущая линия: 26.3.0 (ребрендинг «Красава!», переезд в
     * Vibe Moments Technologies, ДОП-пары, unified GBox feed).
     */
    const val RELEASE_VERSION = "26.3.0"
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

    /** Канал бета-версий (beta/rc): проверяется только если включён «Бета-канал» в настройках. */
    const val BETA_FEED_URL = "https://raw.githubusercontent.com/Vibe-Moments-Technologies/universal-schedule-app/gh-pages/beta.json"

    const val IS_CRITICAL = false
    const val MIN_SUPPORTED_BUILD = 1
    const val CHANGELOG = "Ребрендинг: приложение теперь называется «Красава!» и переехало в организацию Vibe Moments Technologies. Новый ключ подписи — все сборки подписываются безопасно. Поддержка ДОП-пар: розовый бейдж «ДОП» и тумблер «Скрывать доп. занятия» в настройках. Единый GBox-источник: все каналы (стабильный, бета, dev) в одном списке. Переработан раздел «О программе»: команда с контактами и ссылками, блок «Контакты» с почтой и Issue. Календарь над расписанием: месячный календарь с точками пар и легендой, сворачивание свайпом (в настройках). Прогресс в расписании: полоска времени и подсветка перемены. Карты корпусов: подписи крупнее, цвета следуют теме. Исправлен вылет месячного календаря на iOS."

    val isTestBuild: Boolean get() = BUILD_CHANNEL != "stable"

    const val DISPLAY_VERSION = "Версия $VERSION_NAME (сборка $BUILD_NUMBER)"
    const val GITHUB_REPO = "Vibe-Moments-Technologies/universal-schedule-app"
    const val GITHUB_REPO_URL = "https://github.com/Vibe-Moments-Technologies/universal-schedule-app"
    const val GITHUB_ISSUES_URL = "https://github.com/Vibe-Moments-Technologies/universal-schedule-app/issues"
    const val DEVELOPER_NAME = "l1ratch"

}
