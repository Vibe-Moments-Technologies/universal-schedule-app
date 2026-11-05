package com.jetbrains.kmpapp.data.widget

/**
 * Сведения о среде запуска приложения для меню отладки: bundle id,
 * подпись, App Group (группы профиля, доступность контейнеров).
 *
 * Android — заполняет actual (данные PackageManager).
 * iOS — actual делегирует Swift-движку (мост регистрируется в iOSApp),
 * который читает embedded.mobileprovision и контейнеры.
 */
object AppRuntimeInfo {
    interface Engine {
        fun debugInfo(): String
    }

    private var engine: Engine? = null

    fun setEngine(newEngine: Engine) {
        engine = newEngine
    }

    /** Многострочный отчёт; без движка — «движок не зарегистрирован». */
    fun debugInfo(): String = engine?.debugInfo() ?: "движок не зарегистрирован (не iOS?)"
}
