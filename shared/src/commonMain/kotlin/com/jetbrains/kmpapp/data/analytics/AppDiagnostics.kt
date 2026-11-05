package com.jetbrains.kmpapp.data.analytics

import com.jetbrains.kmpapp.data.model.AppVersion

/**
 * Диагностика сбоев (Sentry) — opt-in: включается тумблером в скрытом меню
 * отладки, по умолчанию ВЫКЛЮЧЕНА. Пока SDK не запущен, наружу не уходит
 * ничего: Sentry не пишет и не шлёт события до init.
 *
 * Тестовые каналы (dev / beta / contrib) — исключение: сбор принудительно
 * включён всегда, тумблер на них не даёт выключить (нам нужны краши тестовых
 * сборок). На stable и rc — только по желанию пользователя.
 *
 * Движок подставляет платформа на старте (паттерн как у прежней аналитики):
 *  - Android: ScheduleApp.onCreate → AndroidDiagnostics (sentry-android)
 *  - iOS: iOSApp.init → SentryDiagnosticsEngine (sentry-cocoa)
 */
interface DiagnosticsEngine {
    fun start(dsn: String)
    fun stop()
    fun capture(message: String)
}

object AppDiagnostics {
    /** DSN проекта sentry.io. Пусто = диагностика не запускается даже с тумблера. */
    const val DSN = "https://d7e91a261452273e3edf89df0a5f947d@o4512147900137472.ingest.de.sentry.io/4512147904659536"

    /** Тестовые каналы: сбор диагностики обязателен, тумблер заблокирован. */
    val isForced: Boolean = AppVersion.BUILD_CHANNEL in setOf("dev", "beta", "contrib")

    private var engine: DiagnosticsEngine? = null
    private var enabled = false
    private var active = false

    fun setEngine(engine: DiagnosticsEngine) {
        this.engine = engine
        apply()
    }

    /** Тумблер диагностики / восстановление флага при старте. */
    fun setEnabled(value: Boolean) {
        enabled = value
        apply()
    }

    /** Кнопка «тест» в меню отладки: событие-маркер, что канал живой. */
    fun sendTestEvent() {
        if (active) engine?.capture("Тест диагностики: ${AppVersion.DISPLAY_VERSION}, канал ${AppVersion.BUILD_CHANNEL}")
    }

    private fun apply() {
        active = isForced || enabled
        if (active) {
            if (DSN.isNotBlank()) engine?.start(DSN)
        } else {
            engine?.stop()
        }
    }
}
