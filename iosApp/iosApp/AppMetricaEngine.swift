import AppMetricaCore
import Shared

/// iOS-движок анонимной аналитики. AppMetrica активирован всегда (краши
/// собираются независимо от согласия); сюда приходят только события.
/// Опциональная аналитика фильтруется на уровне AppAnalytics (common).
final class AppMetricaEngine: AnalyticsEngine {
    func logEvent(name: String, params: [String : String]) {
        AppMetrica.reportEvent(name: name, parameters: params, onFailure: nil)
    }
}
