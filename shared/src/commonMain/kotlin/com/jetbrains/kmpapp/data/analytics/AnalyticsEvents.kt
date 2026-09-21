package com.jetbrains.kmpapp.data.analytics

/**
 * Единый реестр событий аналитики.
 *
 * Структура имени: `<область>_<действие>` (snake_case).
 * Параметры: плоский Map<String, String>, ключи в snake_case.
 *
 * Правила:
 *  - Никакого пользовательского контента (тексты заметок, имена групп,
 *    преподавателей, предметы) — только технические идентификаторы.
 *  - Никаких PII (email, телефон, имя устройства).
 *  - IP-адрес виден серверу AppMetrica в любом случае (естественный сбор).
 *
 * Категории:
 *  - session_*   — жизненный цикл сессии
 *  - nav_*       — навигация (вкладки, экраны)
 *  - schedule_*  — работа с расписанием
 *  - settings_*  — изменение настроек
 *  - feature_*   — использование фич
 *  - error_*     — ошибки (отдельно от крашей SDK)
 */
object AnalyticsEvents {

    // ── Сессия ────────────────────────────────────────────────
    const val SESSION_OPEN = "session_open"
    const val SESSION_DOCK_CONFIG = "session_dock_config"

    // ── Навигация ─────────────────────────────────────────────
    const val NAV_TAB_OPEN = "nav_tab_open"
    const val NAV_SCREEN_VIEW = "nav_screen_view"
    const val NAV_SERVICE_OPEN = "nav_service_open"
    const val NAV_SOCIAL_OPEN = "nav_social_open"

    // ── Расписание ────────────────────────────────────────────
    const val SCHEDULE_TARGET_ADDED = "schedule_target_added"
    const val SCHEDULE_TARGET_REMOVED = "schedule_target_removed"
    const val SCHEDULE_TARGET_TYPE = "schedule_target_type"
    const val SCHEDULE_REFRESH = "schedule_refresh"

    // ── Настройки ─────────────────────────────────────────────
    const val SETTINGS_THEME_SET = "settings_theme_set"
    const val SETTINGS_THEME_OVERLAY_SET = "settings_theme_overlay_set"
    const val SETTINGS_APP_ICON_CHANGED = "settings_app_icon_changed"
    const val SETTINGS_NOTIFICATIONS_CHANGED = "settings_notifications_changed"
    const val SETTINGS_ANALYTICS_CHANGED = "settings_analytics_changed"

    // ── Фичи ──────────────────────────────────────────────────
    const val FEATURE_NOTE_ADDED = "feature_note_added"
    const val FEATURE_NOTE_REMOVED = "feature_note_removed"
    const val FEATURE_UPDATE_SHOWN = "feature_update_shown"
    const val FEATURE_VPN_BANNER_SHOWN = "feature_vpn_banner_shown"
    const val FEATURE_LESSON_DETAIL = "feature_lesson_detail"

    // ── Ошибки (не краши — краши собирает SDK автоматически) ─
    const val ERROR_SCHEDULE_LOAD = "error_schedule_load"
    const val ERROR_UPDATE_CHECK = "error_update_check"
}
