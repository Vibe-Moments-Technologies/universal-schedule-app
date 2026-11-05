import SwiftUI
import WidgetKit
import AppIntents

// MARK: - Таймлайн (ТЗ п. 12)
//
// Запись содержит состояние, рассчитанное для её момента: система показывает
// готовый снимок и не пересчитывает выбор пары. Переходы планируются на
// значимых границах — начало и конец пары, момент «СКОРО», полночь, — поэтому
// виджет обновляется сам, без таймеров и ежеминутных перезагрузок.

struct ScheduleEntry: TimelineEntry {
    let date: Date
    /// Готовое состояние на момент `date`. nil, если данных нет вовсе.
    let state: WidgetPresentationState?
    /// Причина отсутствия данных (когда `state` == nil).
    let emptyReason: EmptyReason?
    let targetId: Int?
    /// Переопределённые пользователем цвета типов занятий из снапшота.
    /// Пусто — палитра рисует собственные цвета типов. Значение по умолчанию
    /// есть специально: placeholder'ы и Preview не обязаны их знать.
    var typeColorOverrides: [String: WTypeColor] = [:]
    /// Сокращать названия дисциплин (параметр «Изменить виджет»).
    /// Значение по умолчанию — для placeholder'ов и Preview.
    var shortSubjectNames: Bool = false
}

// MARK: - Построение таймлайна

/// Собирает записи на несколько дней вперёд.
///
/// Расписание предсказуемо, поэтому шкала рассчитывается заранее (ТЗ п. 12.3,
/// 12.4) — это рекомендованный Apple подход. Система может показать запись
/// позже указанного времени, поэтому состояние считается для момента записи,
/// а не «сейчас».
enum WidgetTimelineBuilder {
    /// Записей хватает с запасом: границы пар, шаги прогресса и четыре
    /// полуночи. При изменении расписания приложение само попросит
    /// перезагрузку, поэтому больше не нужно.
    static let maxEntries = 64

    /// Шаг полосы прогресса: каждые 15 минут идущей пары. Полоса рисуется из
    /// состояния записи, поэтому без промежуточных точек она стояла бы на
    /// месте до конца пары — владелец это и заметил.
    static let progressStepMinutes = 15

    static func entries(
        targetId: Int?,
        now: Date,
        shortSubjectNames: Bool = false,
        calendar: Calendar = ScheduleClock.calendar()
    ) -> [ScheduleEntry] {
        var dates: [Date] = [now]

        switch WidgetData.load() {
        case .ok(let payload):
            let target = WidgetData.resolveTarget(in: payload, selectedId: targetId)
            if let target {
                for lesson in target.lessons {
                    guard let start = lesson.startDate(), let end = lesson.endDate() else { continue }
                    if start > now { dates.append(start) }
                    if end > now { dates.append(end) }
                    // Переход в статус «СКОРО» за порог до начала: подпись
                    // меняется, значит нужна отдельная запись (ТЗ п. 12.3).
                    let soon = start.addingTimeInterval(
                        -Double(ScheduleSelectionEngine.soonThresholdMinutes) * 60
                    )
                    if soon > now { dates.append(soon) }

                    // Шаги прогресса: для пар, которые идут или начнутся
                    // в ближайшие 12 часов, добавляем точку каждые 15 минут
                    // внутри окна пары. Полоса двигается сама, без кнопки
                    // и без запуска приложения. Шаги выровнены по сетке,
                    // чтобы записи не дрейфовали от перезагрузки к перезагрузке.
                    guard end > now,
                          start < now.addingTimeInterval(12 * 3600) else { continue }
                    let step = Double(Self.progressStepMinutes) * 60
                    var moment = max(now, start)
                    // Выравнивание на сетку шагов от начала пары.
                    let elapsed = moment.timeIntervalSince(start)
                    moment = moment.addingTimeInterval(step - elapsed.truncatingRemainder(dividingBy: step))
                    while moment < end {
                        dates.append(moment)
                        moment = moment.addingTimeInterval(step)
                    }
                }
            }
        default:
            break
        }

        // Полночь: меняется подпись дня («СЕГОДНЯ» → «ЗАВТРА» → дата)
        // и, возможно, активная пара.
        for offset in 1...4 {
            if let midnight = calendar.date(
                byAdding: .day, value: offset,
                to: calendar.startOfDay(for: now)
            ) {
                dates.append(midnight)
            }
        }

        // Близкие события объединяются: WidgetKit не любит записи вплотную
        // друг к другу, а разница меньше минуты для виду несущественна.
        let sorted = Array(Set(dates.map { $0.timeIntervalSince1970.rounded() }))
            .sorted()
            .map { Date(timeIntervalSince1970: $0) }

        var result: [Date] = []
        for moment in sorted {
            if let last = result.last, moment.timeIntervalSince(last) < 30 { continue }
            result.append(moment)
        }

        return result.prefix(maxEntries).map { moment in
            let (context, reason) = WidgetData.context(selectedId: targetId, now: moment)
            return ScheduleEntry(
                date: moment,
                state: context?.state,
                emptyReason: reason,
                targetId: targetId,
                typeColorOverrides: context?.payload.typeColorOverrides ?? [:],
                shortSubjectNames: shortSubjectNames
            )
        }
    }
}

// MARK: - Провайдер (iOS 17+, с выбором группы)

@available(iOS 17.0, *)
struct ScheduleWidgetProvider: AppIntentTimelineProvider {
    typealias Entry = ScheduleEntry
    typealias Intent = KrasavaWidgetIntent

    func placeholder(in context: Context) -> ScheduleEntry {
        ScheduleEntry(
            date: Date(),
            state: WidgetPreviewFixtures.current.state,
            emptyReason: nil,
            targetId: WidgetPreviewFixtures.current.targetId
        )
    }

    func snapshot(for configuration: KrasavaWidgetIntent, in context: Context) async -> ScheduleEntry {
        let targetId = configuration.selectedTargetId
        let now = Date()
        let (ctx, reason) = WidgetData.context(selectedId: targetId, now: now)
        return ScheduleEntry(
            date: now,
            state: ctx?.state,
            emptyReason: reason,
            targetId: targetId,
            shortSubjectNames: configuration.shortSubjectNames
        )
    }

    func timeline(for configuration: KrasavaWidgetIntent, in context: Context) async -> Timeline<ScheduleEntry> {
        let targetId = configuration.selectedTargetId
        let entries = WidgetTimelineBuilder.entries(
            targetId: targetId,
            now: Date(),
            shortSubjectNames: configuration.shortSubjectNames
        )
        // .atEnd: система вернётся за новой шкалой после последней записи.
        return Timeline(entries: entries, policy: .atEnd)
    }
}

// MARK: - Провайдер для iOS 15–16 (без конфигурации)

/// Совместимость: на этих версиях интерактивной настройки виджета нет вовсе,
/// поэтому расписание берётся из группы уведомлений.
struct LegacyScheduleWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> ScheduleEntry {
        ScheduleEntry(
            date: Date(),
            state: WidgetPreviewFixtures.current.state,
            emptyReason: nil,
            targetId: nil
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (ScheduleEntry) -> Void) {
        let now = Date()
        let (ctx, reason) = WidgetData.context(selectedId: nil, now: now)
        completion(ScheduleEntry(
            date: now, state: ctx?.state, emptyReason: reason, targetId: nil
        ))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ScheduleEntry>) -> Void) {
        let entries = WidgetTimelineBuilder.entries(targetId: nil, now: Date())
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

// MARK: - Виджет

/// Один виджет в галерее. Выбор конфигурации — в обычной функции, а не в
/// result builder: `@WidgetBundleBuilder` не поддерживает ветвление по версии
/// ОС, а показывать интерактивный и legacy-виджет одновременно нельзя.
struct ScheduleWidget: Widget {
    let kind = "KrasavaWidget"

    var body: some WidgetConfiguration {
        configuration()
    }

    private func configuration() -> some WidgetConfiguration {
        if #available(iOS 17.0, *) {
            return AppIntentConfiguration(
                kind: kind,
                intent: KrasavaWidgetIntent.self,
                provider: ScheduleWidgetProvider()
            ) { entry in
                ScheduleWidgetEntryView(entry: entry)
            }
            .configurationDisplayName("Красава!")
            .description("Ближайшие пары: аудитория, время и дисциплина")
            .supportedFamilies(Self.families)
        } else {
            return StaticConfiguration(
                kind: kind,
                provider: LegacyScheduleWidgetProvider()
            ) { entry in
                ScheduleWidgetEntryView(entry: entry)
            }
            .configurationDisplayName("Красава!")
            .description("Ближайшие пары: аудитория, время и дисциплина")
            .supportedFamilies(Self.families)
        }
    }

    /// Поддерживаемые семейства.
    ///
    /// `systemExtraLargePortrait` в SDK iOS объявлен как
    /// `@available(iOS, unavailable)` — на iPhone такого размера нет.
    /// Регистрируется `systemExtraLarge` (iPad): для него по ТЗ п. 1.2
    /// предусмотрена горизонтальная компоновка.
    static var families: [WidgetFamily] {
        [.systemSmall, .systemMedium, .systemLarge, .systemExtraLarge]
    }
}

// MARK: - Режим рендеринга

/// Тонирует ли система виджет.
///
/// `widgetRenderingMode` появился в iOS 16, а минимальная версия проекта —
/// 15.3, поэтому читаем его под проверкой доступности через отдельный тип.
@available(iOS 16.0, *)
struct RenderingModeReader: View {
    let content: (Bool) -> AnyView

    @Environment(\.widgetRenderingMode) private var renderingMode

    var body: some View {
        content(isTinted)
    }

    private var isTinted: Bool {
        // В SDK iOS 26 у WidgetRenderingMode больше вариантов, чем
        // fullColor/accented/vibrant, и перечислять их по именам нельзя:
        // набор растёт от версии к версии. Всё, что не полный цвет, считаем
        // тонированием — это безопасно, смысл несут текстовые метки.
        if case .fullColor = renderingMode { return false }
        return true
    }
}

// MARK: - Точка входа вью

/// Выбор представления по семейству. Вью получают уже готовое состояние
/// (ТЗ п. 12.2) — никакой логики выбора пары здесь нет.
struct ScheduleWidgetEntryView: View {
    @Environment(\.widgetFamily) var family
    @Environment(\.colorScheme) var colorScheme
    let entry: ScheduleEntry
    /// Размер, заданный вручную для Preview. В реальном виджете не задаётся:
    /// семейство приходит из environment (ключ только для чтения).
    var familyOverride: WidgetFamily?

    init(entry: ScheduleEntry, familyOverride: WidgetFamily? = nil) {
        self.entry = entry
        self.familyOverride = familyOverride
    }

    var body: some View {
        Group {
            if #available(iOS 16.0, *) {
                RenderingModeReader { isTinted in
                    AnyView(content(palette: makePalette(isTinted: isTinted)))
                }
            } else {
                content(palette: makePalette(isTinted: false))
            }
        }
    }

    private func makePalette(isTinted: Bool) -> WidgetPalette {
        WidgetPalette(
            colorScheme: colorScheme,
            isTinted: isTinted,
            typeColorOverrides: entry.typeColorOverrides
        )
    }

    @ViewBuilder
    private func content(palette: WidgetPalette) -> some View {
        Group {
            if let state = entry.state {
                view(for: state, palette: palette)
            } else {
                // Данных нет вовсе: показываем пустое состояние с причиной,
                // а не пустой прямоугольник (ТЗ п. 10.4).
                WidgetEmptyStateView(
                    reason: entry.emptyReason,
                    targetTitle: nil,
                    palette: palette
                )
            }
        }
        // Фон задаётся через containerBackground: рисовать поверх системного
        // фона собственный прямоугольник со скруглением не нужно (ТЗ п. 15.1).
        .widgetContainerBackground(palette: palette)
    }

    @ViewBuilder
    private func view(for state: WidgetPresentationState, palette: WidgetPalette) -> some View {
        let shortNames = entry.shortSubjectNames
        switch familyOverride ?? family {
        case .systemMedium:
            MediumScheduleWidgetView(state: state, palette: palette, abbreviatedSubject: shortNames)
        case .systemLarge:
            LargeScheduleWidgetView(state: state, palette: palette, abbreviatedSubject: shortNames)
        case .systemExtraLarge:
            // iPad: горизонтальная панель (ТЗ п. 1.2). На iPhone этого
            // семейства нет, поэтому ветка срабатывает только на планшете.
            ExtraLargeScheduleWidgetView(state: state, palette: palette, abbreviatedSubject: shortNames)
        default:
            SmallScheduleWidgetView(state: state, palette: palette, abbreviatedSubject: shortNames)
        }
    }
}

// MARK: - Точка входа расширения

@main
struct KrasavaWidgetBundle: WidgetBundle {
    var body: some Widget {
        ScheduleWidget()
    }
}

// MARK: - Фон и совместимость

extension View {
    /// `containerBackground` обязателен с iOS 17: без него система не даёт
    /// виджету фон, и в режимах тонирования содержимое теряет читаемость
    /// (ТЗ п. 15.1, 15.3). На 15–16 фон системный по умолчанию.
    @ViewBuilder
    func widgetContainerBackground(palette: WidgetPalette) -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            self.containerBackground(for: .widget) { palette.surfaceBackground }
        } else {
            self
        }
    }
}
