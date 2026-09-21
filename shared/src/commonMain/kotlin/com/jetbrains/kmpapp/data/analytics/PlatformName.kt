package com.jetbrains.kmpapp.data.analytics

/**
 * Имя платформы для аналитики: AppMetrica разделяет платформы и сам, но
 * явный параметр позволяет резать срез «версия × платформа» одним запросом.
 */
expect fun platformName(): String
