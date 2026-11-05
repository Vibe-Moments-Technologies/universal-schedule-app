package com.jetbrains.kmpapp.data.widget

import com.jetbrains.kmpapp.data.storage.AndroidContextProvider

/** Android-движок: пакет, версия, подписи. */
class AndroidAppRuntimeInfo : AppRuntimeInfo.Engine {
    override fun debugInfo(): String {
        val ctx = AndroidContextProvider.context ?: return "контекст недоступен"
        val pm = ctx.packageManager
        val pkg = ctx.packageName
        val info = pm.getPackageInfo(pkg, 0)
        val isDebug = (info.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        val hasInstaller = runCatching {
            pm.getInstallSourceInfo(pkg).installingPackageName
        }.getOrNull()
        return buildString {
            appendLine("пакет: $pkg")
            appendLine("versionName: ${info.versionName}")
            appendLine("versionCode: ${info.longVersionCode}")
            appendLine("debuggable: $isDebug")
            appendLine("установщик: ${hasInstaller ?: "системный/неизвестен"}")
            appendLine("App Group: на Android не используется (виджеты через Glance)")
        }
    }
}
