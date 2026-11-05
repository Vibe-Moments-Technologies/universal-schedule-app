import Foundation
import SwiftUI
import WidgetKit

// MARK: - Чтение общего контейнера и доступ к движку
//
// Модели и расчёт актуальной пары живут в SwiftPM-пакете WidgetKitLogic
// (модуль подключается к таргету по абсолютному пути в pbxproj) — там же
// прогоняются unit-тесты. Здесь только то, что требует WidgetKit и SwiftUI.

/// Расписание, доступное виджету: снапшот из общего контейнера, выбранная
/// цель и готовое состояние презентации.
struct WidgetContext {
    let payload: WPayload
    let target: WTarget?
    let state: WidgetPresentationState
}

enum WidgetData {
    /// Ключ снапшота. Старый "widget_snapshot" (одно активное расписание)
    /// не читаем: контракт изменился на список всех сохранённых расписаний.
    static let key = "widget_payload"

    /// Причина, по которой данных нет. Отдельный тип, а не Bool: по ТЗ (п. 27)
    /// «нет занятий» и «нет данных» — разные состояния, показывать их
    /// одинаково запрещено.
    enum LoadResult {
        /// Нет App Group в подписи — переподпись не добавила контейнер.
        case noAppGroup
        /// Группа есть, но приложение ещё ничего не писало.
        case noData
        /// Данные есть, но не читаются.
        case badData
        case ok(WPayload)
    }

    static func load() -> LoadResult {
        // Группа определяется из профиля подписи (см. AppGroupLocator):
        // приложение может быть переподписано любым инструментом.
        guard let suite = AppGroupLocator.availableSuiteName() else { return .noAppGroup }
        guard let defaults = UserDefaults(suiteName: suite),
              let json = defaults.string(forKey: key) else { return .noData }
        guard let payload = try? JSONDecoder().decode(WPayload.self, from: Data(json.utf8)) else {
            return .badData
        }
        return .ok(payload)
    }

    /// Список расписаний для экрана «Изменить виджет»: пустой массив, если
    /// данных ещё нет (система покажет «Нет групп» вместо падения).
    static func availableTargets() -> [WTarget] {
        if case .ok(let payload) = load() { return payload.targets }
        return []
    }

    /// Готовит состояние для вью.
    ///
    /// - Parameters:
    ///   - selectedId: группа, выбранная в настройках конкретного виджета.
    ///     nil — виджет добавлен без выбора, берём группу уведомлений
    ///     (согласовано с владельцем: обратная совместимость и «работает
    ///     по умолчанию» из ТЗ).
    ///   - now: момент отрисовки. Передаётся явно, чтобы Timeline мог
    ///     рассчитать состояние для будущего момента, а не только для «сейчас».
    static func context(selectedId: Int?, now: Date) -> (WidgetContext?, EmptyReason?) {
        switch load() {
        case .noAppGroup:
            return (nil, .noAppGroup)
        case .noData:
            return (nil, .noData)
        case .badData:
            return (nil, .badData)
        case .ok(let payload):
            let target = resolveTarget(in: payload, selectedId: selectedId)
            let state = ScheduleSelectionEngine().makeState(
                target: target,
                now: now,
                generatedAt: Date(timeIntervalSince1970: Double(payload.generatedAt) / 1000)
            )
            return (WidgetContext(payload: payload, target: target, state: state), nil)
        }
    }

    /// Выбор расписания: явный выбор в настройках виджета, затем группа
    /// уведомлений, затем первое сохранённое.
    static func resolveTarget(in payload: WPayload, selectedId: Int?) -> WTarget? {
        if let selectedId,
           let exact = payload.targets.first(where: { $0.id == selectedId }) {
            return exact
        }
        if let fallback = payload.defaultTargetId,
           let match = payload.targets.first(where: { $0.id == fallback }) {
            return match
        }
        return payload.targets.first
    }
}