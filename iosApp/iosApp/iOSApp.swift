import SwiftUI
import AppMetricaCore
import Shared
import WidgetKit

/// Движок для общего AppRuntimeInfo (карточка «Сведения о приложении»
/// в меню отладки). Живёт здесь, а не в AppGroupLocator.swift: локатор
/// компилируется и в таргет виджета, где фреймворк Shared не линкуется.
final class AppGroupInfoEngine: AppRuntimeInfoEngine {
    func debugInfo() -> String {
        AppGroupLocator.debugInfo()
    }
}

/// Запись снапшота расписания в App Group для виджета.
/// Группа определяется в рантайме из профиля, которым подписали приложение
/// (GBox/esign/дистрибутив) — см. AppGroupLocator. Нет контейнера — молча
/// пропускаем: UserDefaults(suiteName:) без entitlements не падает, а пишет
/// в изолированную песочницу — проверяем контейнер явно.
enum WidgetSync {
    /// Ключ снапшота; должен совпадать с WidgetData.key в таргете виджета.
    static let key = "widget_payload"

    static func push() {
        guard let suite = AppGroupLocator.availableSuiteName(),
              let json = WidgetBridge.shared.payloadJson(),
              let defaults = UserDefaults(suiteName: suite) else { return }
        defaults.set(json, forKey: key)
        WidgetCenter.shared.reloadAllTimelines()
    }
}

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        KoinKt.doInitKoin()
        // Ключ синхронизирован с AppAnalytics.API_KEY (shared) — менять вместе.
        if let configuration = AppMetricaConfiguration(apiKey: "388946ca-aae3-4c1c-9bce-9f390bc0f5ca") {
            AppMetrica.activate(with: configuration)
        }
        AppAnalytics.shared.setEngine(engine: AppMetricaEngine())
        AppIconManager.shared.setEngine(newEngine: AppIconEngine())
        // Держим ссылку: движок нужен и после регистрации (уборка с прошлого
        // запуска) — до первого планирования, чтобы не гонять с ним гонку.
        let notifications = NotificationsEngine()
        NotificationsManager.shared.setEngine(newEngine: notifications)
        notifications.sweepStaleLessonReminders()
        NotificationPresenter.shared.attach()
        com.jetbrains.kmpapp.data.files.FileExchangeEngineHolder.shared.engine =
            FileExchangeEngine()
        AppRuntimeInfo.shared.setEngine(newEngine: AppGroupInfoEngine())
        AppRuntimeInfo.shared.setEngine(newEngine: AppGroupInfoEngine())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear { WidgetSync.push() }
        }
        .onChange(of: scenePhase) { phase in
            // Перед уходом в фон и по возврате — виджет всегда со свежим снимком.
            if phase == .background || phase == .active {
                WidgetSync.push()
            }
        }
    }
}
