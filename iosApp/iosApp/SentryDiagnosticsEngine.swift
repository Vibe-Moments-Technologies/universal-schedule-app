import Shared
import Sentry

/// iOS-движок диагностики: sentry-cocoa, только по явному включению
/// (тумблер в скрытом меню отладки). Выключено — SDK не запущен, данные не идут.
final class SentryDiagnosticsEngine: DiagnosticsEngine {
    func start(dsn: String) {
        SentrySDK.start { options in
            options.dsn = dsn
        }
    }

    func stop() {
        SentrySDK.close()
    }

    func capture(message: String) {
        SentrySDK.capture(message: message)
    }
}
