import SwiftUI
import AppMetricaCore
import Shared

@main
struct iOSApp: App {
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
        VpnStatus.shared.setEngine(newEngine: VpnEngine())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
