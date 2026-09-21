package com.jetbrains.kmpapp.data.update

/**
 * iOS: источник установки.
 *
 * Без приватного API невозможно надёжно определить App Store vs TestFlight
 * vs sideload. Эвристика:
 *  - receipt содержит "sandboxReceipt" → TestFlight
 *  - receipt существует → App Store
 *  - нет receipt → sideload (GBox, AltStore, unsigned IPA)
 */
actual fun detectInstallSource(): InstallSource {
    // На iOS без приватного API точный детект невозможен.
    // GBox-сборки unsigned → receipt отсутствует → UNKNOWN → наша система.
    return InstallSource.UNKNOWN
}
