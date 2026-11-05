package com.jetbrains.kmpapp.data.analytics

import com.jetbrains.kmpapp.data.storage.AndroidContextProvider
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

/**
 * Android-движок AppMetrica. Активируется при создании (context уже
 * установлен ScheduleApp'ом), дальше все вызовы статические.
 */
class AndroidAnalytics : AnalyticsEngine {

    init {
        // SDK активирован всегда: краши собираются независимо от согласия.
        // Опциональная аналитика (события) фильтруется на уровне AppAnalytics.
        AndroidContextProvider.context?.let { context ->
            AppMetrica.activate(
                context,
                AppMetricaConfig.newConfigBuilder(AppAnalytics.API_KEY).build()
            )
        }
    }

    override fun logEvent(name: String, params: Map<String, String>) {
        AppMetrica.reportEvent(name, params)
    }
}
