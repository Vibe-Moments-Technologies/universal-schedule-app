package com.jetbrains.kmpapp.data.analytics

import com.jetbrains.kmpapp.data.storage.AndroidContextProvider
import io.sentry.android.core.SentryAndroid

/** Android-движок диагностики: sentry-android, только по явному включению. */
class AndroidDiagnostics : DiagnosticsEngine {
    override fun start(dsn: String) {
        val context = AndroidContextProvider.context ?: return
        SentryAndroid.init(context) { options ->
            options.dsn = dsn
        }
    }

    override fun stop() {
        io.sentry.Sentry.close()
    }

    override fun capture(message: String) {
        io.sentry.Sentry.captureMessage(message)
    }
}
