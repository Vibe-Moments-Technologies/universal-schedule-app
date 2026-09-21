import SwiftUI
import AppMetricaCore
import Shared

@main
struct iOSApp: App {
    init() {
        KoinKt.doInitKoin()
        if let configuration = AppMetricaConfiguration(apiKey: "fc0cde08-05c5-4718-96ee-e9674b8c33e7") {
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
