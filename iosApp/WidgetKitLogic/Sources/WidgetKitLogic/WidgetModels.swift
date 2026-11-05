import Foundation

// MARK: - Модели виджета (зеркало WidgetPayload из shared)
//
// Файл намеренно зависит только от Foundation: те же исходники подключены
// к SwiftPM-пакету WidgetKitLogic, где на них гоняются unit-тесты. Всё, что
// тянет SwiftUI или WidgetKit, живёт в других файлах.

/// Пара расписания.
///
/// `id` приходит из shared (`Lesson.id` — стабильный идентификатор фида) и
/// нужен для deep links и для различения двух одинаковых пар подряд: по ТЗ
/// (п. 5) такие пары остаются разными записями, объединять их по названию
/// дисциплины запрещено.
struct WLesson: Decodable, Identifiable, Equatable {
    let id: String
    let date: String      // yyyy-MM-dd
    let bell: Int
    let start: String     // HH:mm
    let end: String
    let subject: String
    let type: String      // ЛК/ПР/ЛАБ/ДР/ДОП
    let room: String
    let teacher: String
    /// Учебные группы, участвующие в паре. По ним считается индикатор
    /// совместного занятия: ТЗ п. 5.5 запрещает выводить его из числа
    /// преподавателей или повторов пар.
    let groups: [String]

    init(
        id: String,
        date: String,
        bell: Int,
        start: String,
        end: String,
        subject: String,
        type: String,
        room: String,
        teacher: String,
        groups: [String] = []
    ) {
        self.id = id
        self.date = date
        self.bell = bell
        self.start = start
        self.end = end
        self.subject = subject
        self.type = type
        self.room = room
        self.teacher = teacher
        self.groups = groups
    }

    /// Поле `id` в JSON может отсутствовать (старый снапшот в контейнере) —
    /// тогда синтезируем ключ из даты, времени и названия, чтобы виджет
    /// не падал на декодировании.
    enum CodingKeys: String, CodingKey {
        case id, date, bell, start, end, subject, type, room, teacher, groups
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        date = try c.decode(String.self, forKey: .date)
        bell = try c.decode(Int.self, forKey: .bell)
        start = try c.decode(String.self, forKey: .start)
        end = try c.decode(String.self, forKey: .end)
        subject = try c.decode(String.self, forKey: .subject)
        type = try c.decode(String.self, forKey: .type)
        room = try c.decode(String.self, forKey: .room)
        teacher = try c.decode(String.self, forKey: .teacher)
        groups = (try? c.decode([String].self, forKey: .groups)) ?? []
        id = (try? c.decode(String.self, forKey: .id))
            ?? "\(date)-\(start)-\(subject)"
    }

    /// Количество уникальных учебных групп в паре (ТЗ п. 5.5, 10.3).
    var groupCount: Int { Set(groups).count }

    /// Показывать ли индикатор совместного занятия: только если групп
    /// действительно больше одной.
    var isShared: Bool { groupCount > 1 }
}

/// Пользовательский цвет типа занятия: ARGB обеих схем.
///
/// Цвета приходят числами из shared, а не именами: виджету не нужен дубль
/// палитры приложения, и он не сломается, если вариант цвета переименуют.
/// Ключ карты — короткий бейдж типа (`ЛК`/`ПР`/`ЛАБ`), тот же, что в
/// `WLesson.type`.
struct WTypeColor: Decodable, Equatable {
    let light: Int
    let dark: Int

    /// Цвет выбранной схемы в виде 0xRRGGBB, готовый для `Color(hex:)`.
    ///
    /// Kotlin `Color.toArgb()` отдаёт знаковый Int, поэтому значения вроде
    /// `0xFF0284C7` приходят в JSON отрицательными. Маска отбрасывает знаковый
    /// бит и альфу: альфа не нужна, потому что виджет сам управляет
    /// прозрачностью бейджа (`WidgetPalette.typeBackground`), а цвета в
    /// палитре приложения непрозрачные.
    func rgb(isDark: Bool) -> UInt32 {
        let value = isDark ? dark : light
        return UInt32(bitPattern: Int32(truncatingIfNeeded: value)) & 0xFFFFFF
    }
}

/// Расписание, сохранённое в приложении (группа, преподаватель или аудитория).
struct WTarget: Decodable, Equatable {
    let id: Int
    let title: String
    let typeName: String  // «Группа» / «Преподаватель» / «Аудитория»
    let lessons: [WLesson]
}

/// Полный снапшот в общем контейнере.
struct WPayload: Decodable, Equatable {
    let generatedAt: Int64
    let defaultTargetId: Int?
    let targets: [WTarget]
    /// Переопределённые пользователем цвета типов занятий (только изменённые).
    /// Пусто — виджет рисует свою палитру типов.
    let typeColorOverrides: [String: WTypeColor]

    enum CodingKeys: String, CodingKey {
        case generatedAt, defaultTargetId, targets, typeColorOverrides
    }

    /// Явный декодер ради обратной совместимости: в контейнере может лежать
    /// снапшот от предыдущей версии приложения, где `typeColorOverrides` ещё
    /// нет. Синтезированный конструктор потребовал бы ключ и весь payload
    /// не декодировался — виджет показал бы «данные не читаются» вместо
    /// расписания.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        generatedAt = try c.decode(Int64.self, forKey: .generatedAt)
        defaultTargetId = try c.decodeIfPresent(Int.self, forKey: .defaultTargetId)
        targets = try c.decode([WTarget].self, forKey: .targets)
        typeColorOverrides =
            (try? c.decodeIfPresent([String: WTypeColor].self, forKey: .typeColorOverrides)) ?? [:]
    }

    init(
        generatedAt: Int64,
        defaultTargetId: Int?,
        targets: [WTarget],
        typeColorOverrides: [String: WTypeColor] = [:]
    ) {
        self.generatedAt = generatedAt
        self.defaultTargetId = defaultTargetId
        self.targets = targets
        self.typeColorOverrides = typeColorOverrides
    }
}

// MARK: - Статус и контекст дня

/// Статус пары. Формируется бизнес-логикой, а не View: ТЗ п. 8.3 запрещает
/// независимый вызов текущего времени внутри представления.
enum LessonStatus: Equatable {
    /// Пара идёт прямо сейчас.
    case current
    /// Ближайшая пара впереди (дальше порога «скоро»).
    case upcoming
    /// До начала меньше порога — с обратным отсчётом.
    case soon(minutesUntil: Int)

    var isCurrent: Bool {
        if case .current = self { return true }
        return false
    }

    /// Короткая метка без обратного отсчёта. Её видят все размеры.
    var shortLabel: String {
        switch self {
        case .current: return "СЕЙЧАС"
        case .upcoming: return "ДАЛЕЕ"
        case .soon: return "СКОРО"
        }
    }

    /// Метка с обратным отсчётом — только там, где есть место (Medium и
    /// крупнее, дополнение владельца: на Small место дороже точного времени).
    var countdownLabel: String {
        guard case .soon(let minutes) = self, minutes > 0 else { return shortLabel }
        return minutes == 1 ? "ЧЕРЕЗ 1 МИН" : "ЧЕРЕЗ \(minutes) МИН"
    }
}

/// День, к которому относится активная пара.
///
/// Формат подписи единый для всех размеров и всех будущих дней:
/// `ПТ, 23 ОКТ`. Сегодня дата не подписывается — вместо неё статус
/// (`СЕЙЧАС`/`ДАЛЕЕ`), потому что дублировать дату запрещено.
enum DayContext: Equatable {
    case today
    /// Завтра: та же компактная подпись, что и у остальных дней.
    case tomorrow(label: String)
    /// Послезавтра.
    case dayAfterTomorrow(label: String)
    /// Дальше.
    case date(label: String)
    case none

    /// Подпись с датой: `ПТ, 23 ОКТ`. nil — пара сегодня.
    var label: String? {
        switch self {
        case .today, .none: return nil
        case .tomorrow(let label), .dayAfterTomorrow(let label), .date(let label):
            return label
        }
    }

    var isFuture: Bool {
        switch self {
        case .today, .none: return false
        case .tomorrow, .dayAfterTomorrow, .date: return true
        }
    }

    var isToday: Bool {
        if case .today = self { return true }
        return false
    }
}

// MARK: - Время

/// Временная зона учебного расписания.
///
/// По ТЗ (п. 23) расчёты идут в зоне расписания, а не устройства: иначе
/// учебное `14:20` превращалось бы в другое время у студента в другой зоне.
enum ScheduleClock {
    /// Расписание РТУ МИРЭА живёт по московскому времени.
    static let timeZone = TimeZone(identifier: "Europe/Moscow") ?? .current

    /// Календарь расписания: зона зафиксирована, локаль — русская (интерфейс
    /// приложения русскоязычный, подписи дат должны совпадать с ним).
    static func calendar(timeZone: TimeZone = ScheduleClock.timeZone) -> Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        cal.locale = Locale(identifier: "ru_RU")
        return cal
    }

    /// Разбор «yyyy-MM-dd HH:mm» строго в зоне расписания.
    static func parse(date: String, time: String, timeZone: TimeZone = ScheduleClock.timeZone) -> Date? {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = timeZone
        let parts = time.split(separator: ":")
        guard parts.count == 2,
              let hour = Int(parts[0]),
              let minute = Int(parts[1]),
              let day = parseDay(date, calendar: cal) else { return nil }
        return cal.date(
            bySettingHour: hour, minute: minute, second: 0, of: day
        )
    }

    /// Начало суток для «yyyy-MM-dd».
    static func parseDay(_ text: String, calendar: Calendar) -> Date? {
        let parts = text.split(separator: "-")
        guard parts.count == 3,
              let year = Int(parts[0]),
              let month = Int(parts[1]),
              let day = Int(parts[2]) else { return nil }
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = day
        return calendar.date(from: comps)
    }
}

extension WLesson {
    /// Момент начала пары в зоне расписания.
    func startDate(timeZone: TimeZone = ScheduleClock.timeZone) -> Date? {
        ScheduleClock.parse(date: date, time: start, timeZone: timeZone)
    }

    /// Момент окончания пары. Если пара заканчивается после полуночи
    /// (переход через полночь), конец переносится на следующие сутки.
    func endDate(timeZone: TimeZone = ScheduleClock.timeZone) -> Date? {
        guard let s = startDate(timeZone: timeZone),
              let e = ScheduleClock.parse(date: date, time: end, timeZone: timeZone) else {
            return nil
        }
        if e < s {
            return Calendar.current.date(byAdding: .day, value: 1, to: e)
        }
        return e
    }

    /// Аудитория с корпусом, например «341 (С-20)». Пусто — аудитория не
    /// указана (СДО, дистант): по ТЗ (п. 7) вместо пустоты показываем
    /// понятную подпись, а не «— (—)».
    var roomLabel: String {
        room.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Готовая подпись аудитории для интерфейса: либо номер, либо текст
    /// «Аудитория уточняется» / «Онлайн».
    var roomDisplay: String {
        let trimmed = roomLabel
        if trimmed.isEmpty { return "Аудитория уточняется" }
        // СДО — занятия в дистанционной среде; отдельного номера аудитории нет.
        let lower = trimmed.lowercased()
        if lower.contains("сдо") || lower.contains("дистант") || lower.contains("онлайн") {
            return "Онлайн"
        }
        return trimmed
    }

    /// Только номер аудитории без корпуса: «341 (С-20)» → «341».
    var roomNumber: String {
        let trimmed = roomLabel
        guard !trimmed.isEmpty else { return "" }
        if let paren = trimmed.firstIndex(of: "(") {
            return trimmed[trimmed.startIndex..<paren]
                .trimmingCharacters(in: .whitespaces)
        }
        return trimmed
    }

    /// Только корпус: «341 (С-20)» → «С-20».
    var buildingLabel: String {
        let trimmed = roomLabel
        guard let open = trimmed.firstIndex(of: "("),
              let close = trimmed.lastIndex(of: ")"),
              open < close else { return "" }
        return String(trimmed[trimmed.index(after: open)..<close])
            .trimmingCharacters(in: .whitespaces)
    }

    /// Дистанционное занятие: вместо номера аудитории показывается «Онлайн»
    /// (ТЗ п. 3.3). Определяется по данным фида, а не догадкой.
    var isOnline: Bool {
        let lower = roomLabel.lowercased()
        if lower.isEmpty { return false }
        return lower.contains("сдо") || lower.contains("дистант") || lower.contains("онлайн")
    }

    /// Интервал одной строкой с типографским тире (ТЗ п. 8.2).
    var timeRange: String {
        "\(start)–\(end)"
    }

    /// Тот же интервал в форме для VoiceOver: «с 14:20 до 15:50».
    var timeRangeAccessible: String {
        "с \(start) до \(end)"
    }

    /// Имя преподавателя для показа. Пустое значение → nil, чтобы вью не
    /// рисовала пустую строку и не резервировала под неё место.
    ///
    /// Сокращений здесь нет: ТЗ п. 5.4 запрещает неустойчивые эвристики,
    /// а фид уже отдаёт имена в пригодном виде.
    var teacherDisplay: String? {
        let trimmed = teacher.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    /// Короткое название дисциплины для компактных строк.
    ///
    /// Собственных сокращений не изобретаем (ТЗ п. 10.2): если фид не отдал
    /// короткое имя, используется полное с ограничением строк.
    var shortSubject: String { subject }

    /// Название для показа с учётом пользовательской настройки сокращения.
    ///
    /// Правило — ровно то, что в коде экрана расписания приложения
    /// (`abbreviateSubjectName`): от каждого слова берётся первая буква
    /// (для слов длиннее двух букв — заглавная, для коротких — как есть),
    /// названия из одного-двух слов не трогаются («Базы данных и
    /// управление ими» → «БДиУи»). Виджет наследует правило приложения,
    /// а не задаёт своё. Значение в снапшот не входит: сокращение —
    /// параметр самого виджета («Изменить виджет»), не глобальная
    /// настройка приложения.
    func subjectDisplay(abbreviated: Bool) -> String {
        guard abbreviated else { return subject }
        return Self.abbreviate(subject)
    }

    /// Первые буквы слов для названий длиннее двух слов; иначе — как есть.
    /// Разбивка по пробелам и дефисам, как в Kotlin-оригинале.
    static func abbreviate(_ subject: String) -> String {
        let words = subject
            .split(whereSeparator: { $0 == " " || $0 == "-" })
            .filter { !$0.isEmpty }
        guard words.count > 2 else { return subject }
        return words.map { word in
            word.count > 2
                ? String(word.first!).uppercased()
                : String(word.first!)
        }.joined()
    }
}