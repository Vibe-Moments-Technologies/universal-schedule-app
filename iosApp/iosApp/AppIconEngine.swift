import UIKit
import UserNotifications
import CFNetwork
import Shared

// Оба платформенных движка живут в одном файле: один Swift-файл в фазе
// Sources (NotificationsEngine.swift) детерминированно выпадал из плана
// сборки Xcode при корректном pbxproj — здесь компиляция гарантирована.
final class AppIconEngine: AppIconManagerIconEngine {
    func applyIcon(name: String) {
        // "default" -> nil = вернуть первичную иконку из asset catalog.
        // Литерал, а не AppIconManager.ICON_DEFAULT: const val не экспортируется
        // в Swift (inline на стороне Kotlin), значение зафиксировано в common-коде.
        let iconName: String? = name == "default" ? nil : name
        // Ошибки (например, PNG с альфа-каналом iOS отвергает) логируем,
        // чтобы сбой смены иконки не был немым.
        UIApplication.shared.setAlternateIconName(iconName) { error in
            if let error = error {
                print("AppIconEngine: setAlternateIconName failed: \(error.localizedDescription)")
            }
        }
    }
}

/// iOS-движок локальных напоминаний о занятиях (UNUserNotificationCenter).
/// Разрешение запрашивается только в момент включения тумблера в настройках.
final class NotificationsEngine: NotificationsManagerNotificationEngine {
    func requestAuthorization() {
        // Без .badge: красный кружок с цифрой на иконке не используем,
        // просить на него разрешение — лишняя галочка в системном окне.
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    /// Идентификаторы, поставленные этой партией занятий. Храним их в
    /// UserDefaults, чтобы после перезапуска можно было синхронно снять
    /// старую партию без гонки с новой постановкой.
    private var scheduledLessonIds: Set<String>
    private let scheduledIdsKey = "krasava_scheduled_lesson_ids"
    private let queue = DispatchQueue(label: "ru.vibemoments.krasava.notifications")

    init() {
        scheduledLessonIds = Set(UserDefaults.standard.stringArray(forKey: scheduledIdsKey) ?? [])
    }

    func schedule(id: String, title: String, body: String, dateEpochMillis: Int64) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        // Ближние уведомления (тест из отладки) — интервальный триггер:
        // календарный округляет до минут и «съедает» секунды. Минимум 1 c:
        // на нулевом интервале iOS молча отбрасывает запрос.
        let interval = TimeInterval(dateEpochMillis) / 1000 - Date().timeIntervalSince1970
        let trigger: UNNotificationTrigger
        if interval <= 90 {
            trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, interval), repeats: false)
        } else {
            let date = Date(timeIntervalSince1970: TimeInterval(dateEpochMillis) / 1000)
            let components = Calendar.current.dateComponents(
                [.year, .month, .day, .hour, .minute], from: date)
            trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        }
        queue.sync {
            if id.hasPrefix("lesson-") {
                scheduledLessonIds.insert(id)
                UserDefaults.standard.set(Array(scheduledLessonIds), forKey: scheduledIdsKey)
            }
            UNUserNotificationCenter.current().add(
                UNNotificationRequest(identifier: id, content: content, trigger: trigger))
        }
    }

    func cancelAll() {
        // Синхронно: снимаем запомненную партию занятий ПЕРЕД постановкой
        // новой (reschedule зовёт cancelAll, затем schedule). Асинхронный
        // getPendingNotificationRequests давал гонку — отмена приходила
        // после новой партии и сносила её: напоминания переставали приходить.
        // Тестовые уведомления (test-now/test-delayed) не в этом списке
        // и потому отменой не затрагиваются.
        queue.sync {
            let ids = Array(scheduledLessonIds)
            scheduledLessonIds.removeAll()
            UserDefaults.standard.removeObject(forKey: scheduledIdsKey)
            if !ids.isEmpty {
                UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ids)
            }
        }
    }

    /// Разовая уборка при старте: снимает партию занятий, оставшуюся с
    /// прошлого запуска (её id в памяти не сохранились). Вызывается до
    /// первого планирования, поэтому гонки с новой партией не создаёт.
    func sweepStaleLessonReminders() {
        let center = UNUserNotificationCenter.current()
        queue.sync {
            let stale = Array(scheduledLessonIds)
            scheduledLessonIds.removeAll()
            UserDefaults.standard.removeObject(forKey: scheduledIdsKey)
            if !stale.isEmpty {
                center.removePendingNotificationRequests(withIdentifiers: stale)
            }
        }
        center.getPendingNotificationRequests { [weak self] requests in
            guard let self = self else { return }
            self.queue.sync {
                let activeIds = self.scheduledLessonIds
                let stale = requests.map(\.identifier).filter {
                    $0.hasPrefix("lesson-") && !activeIds.contains($0)
                }
                if !stale.isEmpty {
                    center.removePendingNotificationRequests(withIdentifiers: stale)
                }
            }
        }
    }
}

/// Показ уведомлений, когда приложение открыто: без делегата iOS молча
/// гасит баннер в форграунде (тест из отладки «не приходил», хотя
/// напоминания о парах в фоне доставлялись).
final class NotificationPresenter: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationPresenter()

    func attach() {
        UNUserNotificationCenter.current().delegate = self
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }
}

/// iOS-детектор VPN: публичного API «VPN включён» нет, используем
/// канонический «AppsFlyer-style» разбор системных настроек прокси —
/// ключ __SCOPED__ содержит интерфейсы активных туннелей
/// (WireGuard, OpenVPN, корпоративные NEPacketTunnelProvider).
final class VpnEngine: VpnStatusEngine {
    func isVpnActive() -> Bool {
        guard let settings = CFNetworkCopySystemProxySettings()?.takeRetainedValue() as? [String: Any],
              let scoped = settings["__SCOPED__"] as? [String: Any] else { return false }
        for key in scoped.keys {
            let k = key.lowercased()
            if k.contains("tap") || k.contains("tun") || k.contains("ppp")
                || k.contains("ipsec") || k.contains("utun") {
                return true
            }
        }
        return false
    }
}
