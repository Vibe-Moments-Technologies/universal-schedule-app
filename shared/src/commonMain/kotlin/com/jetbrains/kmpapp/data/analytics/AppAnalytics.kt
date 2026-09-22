package com.jetbrains.kmpapp.data.analytics

/**
 * Анонимная аналитика (AppMetrica), разделённая на два уровня:
 *
 * 1. **Обязательная диагностика** (краши, ошибки) — SDK активирован всегда,
 *    `setDataSendingEnabled` не трогаем: краши собираются независимо от
 *    согласия пользователя. Это нужно для стабильности приложения.
 *
 * 2. **Опциональная аналитика** (события использования) — шлётся только
 *    если пользователь дал согласие (consent-диалог при первом запуске).
 *    Управляется тумблером в настройках (`uschedule_analytics_enabled`).
 *
 * Движок подставляет платформа на старте приложения:
 *  - Android: ScheduleApp.onCreate → AndroidAnalytics (shared/androidMain)
 *  - iOS: iOSApp.init → Swift-класс AppMetricaEngine (iosApp)
 */
interface AnalyticsEngine {
    fun logEvent(name: String, params: Map<String, String>)
}

object AppAnalytics {
    /** Публичный ключ AppMetrica — по дизайну системы шьётся в приложение.
     *  ВАЖНО: в iOSApp.swift ключ продублирован (const val не экспортируется
     *  в Swift) — менять только синхронно в обоих местах. */
    const val API_KEY = "388946ca-aae3-4c1c-9bce-9f390bc0f5ca"

    private var engine: AnalyticsEngine? = null

    /** Опциональная аналитика: true после согласия, false если отказался. */
    private var eventsEnabled = true

    fun setEngine(engine: AnalyticsEngine) {
        this.engine = engine
    }

    /** Управление опциональной аналитикой (события). Краши не затрагиваются. */
    fun setEventsEnabled(value: Boolean) {
        eventsEnabled = value
    }

    /** Событие использования. Шлётся только при включённой опциональной аналитике. */
    fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        if (eventsEnabled) engine?.logEvent(name, params)
    }
}
