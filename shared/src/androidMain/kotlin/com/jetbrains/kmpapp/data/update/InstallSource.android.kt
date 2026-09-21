package com.jetbrains.kmpapp.data.update

import com.jetbrains.kmpapp.data.storage.AndroidContextProvider

/**
 * Android: определяем источник по installer package name.
 *
 * PackageManager.getInstallerPackageName возвращает:
 *  - "com.android.vending" → Google Play
 *  - "ru.vk.store" → RuStore
 *  - null → sideload (APK напрямую, GBox, GitHub)
 *
 * На Android 11+ (API 30+) getInstallerPackageName deprecated,
 * но работает. Альтернатива — InstallSourceInfo (API 30+).
 */
actual fun detectInstallSource(): InstallSource {
    val context = AndroidContextProvider.context ?: return InstallSource.UNKNOWN
    return try {
        val pm = context.packageManager
        val installer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
        when {
            installer == "com.android.vending" -> InstallSource.GOOGLE_PLAY
            installer == "ru.vk.store" -> InstallSource.RUSTORE
            installer == null -> InstallSource.GITHUB // sideload
            else -> InstallSource.UNKNOWN
        }
    } catch (_: Exception) {
        InstallSource.UNKNOWN
    }
}
