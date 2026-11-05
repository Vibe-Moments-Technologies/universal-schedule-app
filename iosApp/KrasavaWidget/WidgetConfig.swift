import AppIntents
import WidgetKit

// MARK: - Конфигурация виджета
//
// Параметры: расписание (группа/преподаватель/аудитория) и сокращение
// названий дисциплин. Виджет работает по умолчанию (берёт расписание,
// выбранное для уведомлений), но конкретному виджету можно назначить другое
// через «Изменить виджет».

/// Расписание как вариант выбора в «Изменить виджет» → «Расписание».
/// Требует iOS 17: AppIntent-конфигурация в WidgetKit появилась там же.
///
/// В entity лежат и title, и typeName — не только id. Это осознанно:
/// система сериализует выбранную сущность и кэширует её. Если поля
/// displayRepresentation вычислять динамически по id, структура остаётся
/// неизменной при смене названия группы в приложении — и «Изменить виджет»
/// показывает устаревшую строку, а выбор может не применяться. Данные в
/// самой сущности заставляют equality видеть разницу и сбрасывать кэш.
@available(iOS 17.0, *)
struct WidgetTargetEntity: AppEntity, Identifiable {
    let id: Int
    let title: String
    let typeName: String

    static var typeDisplayRepresentation: TypeDisplayRepresentation {
        TypeDisplayRepresentation(name: "Расписание")
    }

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(
            // Названия групп и типов приходят из данных приложения, а не из
            // каталога локализации — подставляем как есть, без строковых ключей.
            title: LocalizedStringResource(stringLiteral: title),
            subtitle: LocalizedStringResource(stringLiteral: typeName)
        )
    }

    static var defaultQuery = WidgetTargetQuery()

    init(id: Int, title: String, typeName: String) {
        self.id = id
        self.title = title
        self.typeName = typeName
    }

    init(_ target: WTarget) {
        self.init(id: target.id, title: target.title, typeName: target.typeName)
    }
}

/// Источник вариантов для списка выбора. Читает тот же общий контейнер,
/// что и виджет, — сеть и Koin не нужны.
@available(iOS 17.0, *)
struct WidgetTargetQuery: EntityQuery {
    func entities(for identifiers: [Int]) async throws -> [WidgetTargetEntity] {
        let all = WidgetData.availableTargets().map(WidgetTargetEntity.init)
        // Каждый запрошенный id должен резолвиться в свою сущность — система
        // сопоставляет результат с выбором пользователя один к одному.
        // Виртуальная опция «По умолчанию» (id -1) в списке расписаний не
        // лежит, поэтому резолвится отдельно.
        var resolved = identifiers.compactMap { id in all.first { $0.id == id } }
        if identifiers.contains(WidgetTargetEntity.defaultOptionId) {
            resolved.append(WidgetTargetEntity.defaultOption)
        }
        // Реальных расписаний из запроса нет (удалены в приложении) — не
        // подсовываем весь список под видом выбранного: система сама
        // предложит выбрать заново, а виджет тем временем работает по
        // фолбэку из WidgetData.
        return resolved
    }

    func suggestedEntities() async throws -> [WidgetTargetEntity] {
        // Специальная первая строка «По умолчанию» — чтобы выбор был виден:
        // иначе при nil-параметре строка выглядит пустой и «кривой», как
        // будто виджет ничего не запомнил.
        [WidgetTargetEntity.defaultOption]
            + WidgetData.availableTargets().map(WidgetTargetEntity.init)
    }

    func defaultResult() async -> WidgetTargetEntity? {
        // «По умолчанию»: виджет берёт расписание уведомлений (решение
        // владельца). Строка выбора при этом показывает явную опцию,
        // а не пустоту.
        WidgetTargetEntity.defaultOption
    }
}

@available(iOS 17.0, *)
private extension WidgetTargetEntity {
    /// Пункт «По умолчанию (уведомления)»: id −1 вне диапазона реальных id.
    static var defaultOption: WidgetTargetEntity {
        WidgetTargetEntity(
            id: WidgetTargetEntity.defaultOptionId,
            title: "По умолчанию",
            typeName: "Расписание уведомлений"
        )
    }

    /// Соглашение об id опции «по умолчанию»: расписания нумеруются с 1
    /// приложением, отрицательных id не бывает.
    static let defaultOptionId: Int = -1
}

/// Параметры виджета: расписание и сокращение названий дисциплин.
@available(iOS 17.0, *)
struct KrasavaWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource { "Расписание" }
    static var description: IntentDescription {
        IntentDescription("Выберите группу, преподавателя или аудиторию для виджета.")
    }

    @Parameter(title: "Расписание")
    var target: WidgetTargetEntity?

    /// Сокращённые названия дисциплин («Базы данных» → «БД» для длинных).
    /// Сокращение — не обрезка: строится из инициалов слов там, где название
    /// длиннее двух слов. На тесных размерах (Small) экономит важные строки.
    @Parameter(title: "Сокращать названия", default: false)
    var shortSubjectNames: Bool

    /// id выбранного расписания с учётом опции «По умолчанию» (nil → nil,
    /// фолбэком займётся WidgetData: расписание уведомлений).
    var selectedTargetId: Int? {
        guard let target, target.id != WidgetTargetEntity.defaultOptionId else { return nil }
        return target.id
    }
}

// MARK: - Кнопка «Обновить»

/// Пересчёт виджета по нажатию кнопки внутри него.
///
/// Что реально делает: перечитывает снапшот из общего контейнера и строит
/// новый Timeline от текущего момента. Поэтому статус («СЕЙЧАС»/«ДАЛЕЕ») и
/// полоса прогресса становятся актуальными без запуска приложения — именно
/// этого просил владелец, заметив, что прогресс обновлялся только при
/// открытии приложения.
///
/// Чего не делает: не ходит в сеть за новым расписанием. Расширение виджета
/// сознательно не тянет данные из интернета (память, время, батарея);
/// расписание обновляет приложение, а кнопка пересчитывает то, что уже
/// сохранено. Это честно отражено в названии и описании.
@available(iOS 17.0, *)
struct RefreshWidgetIntent: AppIntent {
    static var title: LocalizedStringResource = "Обновить виджет"
    static var description: IntentDescription = IntentDescription(
        "Пересчитать виджет из сохранённого расписания: статус и прогресс пары станут актуальными."
    )

    /// Не показывать в приложении «Команды»: это служебное действие виджета.
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        WidgetCenter.shared.reloadTimelines(ofKind: "KrasavaWidget")
        return .result()
    }
}
