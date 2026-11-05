import Foundation

// MARK: - Состояние презентации

/// Причина пустого состояния. По ТЗ (п. 10.4) «нет занятий» и «нет данных» —
/// разные вещи, показывать их одинаково запрещено.
enum EmptyReason: Equatable {
    /// Занятий нет в загруженном расписании.
    case noLessons
    /// Группа не выбрана вовсе.
    case noTarget
    /// Данных в контейнере ещё нет — приложение не писало снапшот.
    case noData
    /// Данные есть, но не читаются.
    case badData
    /// Нет App Group в подписи — переподпись не добавила контейнер.
    case noAppGroup
}

/// Свежесть данных: устаревший кэш можно показывать, но с честной пометкой.
enum DataFreshness: Equatable {
    case fresh
    /// Возраст снапшота в часах.
    case stale(hours: Int)

    var isStale: Bool {
        if case .stale = self { return true }
        return false
    }
}

/// Учебный день в готовом виде: подпись и занятия.
struct WidgetDay: Equatable {
    /// Подпись для заголовка: «ПТ, 23 ОКТ».
    let label: String
    /// Занятия дня в хронологическом порядке.
    let lessons: [WLesson]
    /// Дата дня («yyyy-MM-dd») — для заголовков и будущих deep links.
    let date: String
}

/// Готовое к отображению состояние.
///
/// Все четыре виджета получают его из одного движка — это требование ТЗ
/// (п. 11): выбором активной пары не должна заниматься вёрстка.
struct WidgetPresentationState: Equatable {
    let generatedAt: Date
    let freshness: DataFreshness
    /// Название выбранного расписания (для пустых состояний).
    let targetTitle: String?

    /// Активная пара: текущая, иначе ближайшая предстоящая, иначе первая
    /// пара ближайшего учебного дня (ТЗ п. 1.1).
    let primaryLesson: WLesson?
    let primaryStatus: LessonStatus?
    /// Прогресс идущей пары: 0 — только началась, 1 — заканчивается.
    /// nil, если пара не идёт сейчас. Считается здесь, а не во вью: там нет
    /// текущего времени, а состояние должно соответствовать дате записи
    /// Timeline, а не моменту отрисовки.
    let primaryProgress: Double?
    /// День активной пары.
    let primaryDay: DayContext
    /// Заголовок выбранного учебного дня: «СЕГОДНЯ · СРЕДА».
    let primaryDayLabel: String?

    /// Остальные занятия того же учебного дня (активная исключена).
    let upcomingLessons: [WLesson]

    /// Ближайшие следующие учебные дни с занятиями, по порядку.
    /// Первый — ближайший; второй добавляется, только если он есть.
    let nextDays: [WidgetDay]

    /// Заполнено, только если показывать нечего.
    let emptyReason: EmptyReason?

    static func empty(reason: EmptyReason, generatedAt: Date = Date()) -> WidgetPresentationState {
        WidgetPresentationState(
            generatedAt: generatedAt,
            freshness: .fresh,
            targetTitle: nil,
            primaryLesson: nil,
            primaryStatus: nil,
            primaryProgress: nil,
            primaryDay: .none,
            primaryDayLabel: nil,
            upcomingLessons: [],
            nextDays: [],
            emptyReason: reason
        )
    }

    var isEmpty: Bool { primaryLesson == nil }

    /// Заголовок дня для шапок Large/Extra Large.
    ///
    /// Сегодня — «СЕГОДНЯ · СРЕДА», будущий день — компактная дата
    /// «ПТ, 23 ОКТ»: единый формат для всех размеров и всех будущих дней.
    var headerTitle: String {
        primaryDayLabel ?? primaryDay.label ?? "СЕГОДНЯ"
    }
}

// MARK: - Движок

/// Выбор активной пары и подготовка состояния для вью.
///
/// Чистая логика: время передаётся параметром, зависимости — только календарь
/// и зона расписания. Это позволяет прогнать все сценарии ТЗ (п. 18) на
/// заранее подготовленных датах без опоры на системные часы.
struct ScheduleSelectionEngine {
    /// Порог статуса «СКОРО» (ТЗ п. 12.3): 30 минут до начала, включительно.
    static let soonThresholdMinutes = 30

    let calendar: Calendar
    let timeZone: TimeZone

    init(
        timeZone: TimeZone = ScheduleClock.timeZone,
        calendar: Calendar? = nil
    ) {
        self.timeZone = timeZone
        self.calendar = calendar ?? ScheduleClock.calendar(timeZone: timeZone)
    }

    /// Главная точка входа.
    ///
    /// - Parameters:
    ///   - target: выбранное расписание (nil — группа не выбрана).
    ///   - now: текущий момент (в тестах — заранее заданный).
    ///   - generatedAt: когда снапшот был записан приложением.
    ///   - staleAfterHours: с какого возраста данные считаются устаревшими.
    func makeState(
        target: WTarget?,
        now: Date,
        generatedAt: Date,
        staleAfterHours: Int = 36
    ) -> WidgetPresentationState {
        guard let target else {
            return .empty(reason: .noTarget, generatedAt: generatedAt)
        }

        let lessons = ordered(target.lessons)
        let fresh = freshness(generatedAt: generatedAt, now: now, staleAfterHours: staleAfterHours)

        guard !lessons.isEmpty else {
            return WidgetPresentationState(
                generatedAt: generatedAt,
                freshness: fresh,
                targetTitle: target.title,
                primaryLesson: nil,
                primaryStatus: nil,
                primaryProgress: nil,
                primaryDay: .none,
                primaryDayLabel: nil,
                upcomingLessons: [],
                nextDays: [],
                emptyReason: .noLessons
            )
        }

        // 1. Активная пара: текущая → ближайшая предстоящая.
        let current = lessons.first { isOngoing($0, at: now) }
        let upcoming = lessons.first { lesson in
            guard let start = lesson.startDate(timeZone: timeZone) else { return false }
            return start > now
        }
        let primary = current ?? upcoming

        guard let primary else {
            // Все пары уже прошли: показывать нечего.
            return WidgetPresentationState(
                generatedAt: generatedAt,
                freshness: fresh,
                targetTitle: target.title,
                primaryLesson: nil,
                primaryStatus: nil,
                primaryProgress: nil,
                primaryDay: .none,
                primaryDayLabel: nil,
                upcomingLessons: [],
                nextDays: [],
                emptyReason: .noLessons
            )
        }

        let primaryDayStart = dayStart(primary)
        let isToday = calendar.isDate(primaryDayStart, inSameDayAs: now)

        let status = status(for: primary, current: current, now: now)
        let dayContext = context(for: primaryDayStart, now: now)

        // 2. Остальные занятия того же учебного дня, кроме активной.
        let sameDayLessons = lessons.filter {
            calendar.isDate(dayStart($0), inSameDayAs: primaryDayStart)
        }
        let upcomingSameDay = sameDayLessons.filter { lesson in
            lesson.id != primary.id && (lesson.startDate(timeZone: timeZone) ?? .distantPast) > now
        }

        // 3. Следующие учебные дни. По ТЗ (п. 11.3) перебираем календарные
        //    даты через Calendar, а не прибавлением 86400 секунд.
        let futureDays = nextStudyDays(after: primaryDayStart, in: lessons, now: now, limit: 2)

        return WidgetPresentationState(
            generatedAt: generatedAt,
            freshness: fresh,
            targetTitle: target.title,
            primaryLesson: primary,
            primaryStatus: status,
            primaryProgress: progress(for: primary, now: now),
            primaryDay: dayContext,
            primaryDayLabel: isToday ? todayHeader(primaryDayStart) : dayContext.label,
            upcomingLessons: upcomingSameDay,
            nextDays: futureDays,
            emptyReason: nil
        )
    }

    // MARK: Примитивы

    /// Пара идёт сейчас: `start <= now < end` (ТЗ п. 11.1).
    ///
    /// Верхняя граница строгая: в момент окончания пара уже завершена,
    /// поэтому во время перемены активной становится следующая.
    func isOngoing(_ lesson: WLesson, at now: Date) -> Bool {
        guard let s = lesson.startDate(timeZone: timeZone),
              let e = lesson.endDate(timeZone: timeZone) else { return false }
        return now >= s && now < e
    }

    /// Порядок: по дате, затем по времени начала, затем по номеру пары.
    /// `id` в конце — чтобы порядок был детерминированным при полном совпадении.
    func ordered(_ lessons: [WLesson]) -> [WLesson] {
        lessons.sorted { a, b in
            if a.date != b.date { return a.date < b.date }
            if a.start != b.start { return a.start < b.start }
            if a.bell != b.bell { return a.bell < b.bell }
            return a.id < b.id
        }
    }

    func dayStart(_ lesson: WLesson) -> Date {
        ScheduleClock.parseDay(lesson.date, calendar: calendar) ?? .distantPast
    }

    private func status(for primary: WLesson, current: WLesson?, now: Date) -> LessonStatus {
        if current != nil { return .current }
        guard let start = primary.startDate(timeZone: timeZone) else { return .upcoming }
        let minutes = Int(start.timeIntervalSince(now) / 60)
        // Порог включительный: «30 минут до начала» — это уже «СКОРО».
        return (0...Self.soonThresholdMinutes).contains(minutes)
            ? .soon(minutesUntil: minutes)
            : .upcoming
    }

    /// Прогресс идущей пары: от 0 (начало) до 1 (окончание).
    ///
    /// nil для всего, что не идёт прямо сейчас: для будущей пары полоса
    /// прогресса бессмысленна, а пустая полоса занимала бы место у P0-данных.
    /// Значение считается по текущему моменту из Timeline, поэтому в записи
    /// шкалы оно соответствует своему времени, а не моменту отрисовки.
    func progress(for lesson: WLesson, now: Date) -> Double? {
        guard let s = lesson.startDate(timeZone: timeZone),
              let e = lesson.endDate(timeZone: timeZone),
              now >= s, now < e else { return nil }
        let total = e.timeIntervalSince(s)
        guard total > 0 else { return nil }
        return min(1, max(0, now.timeIntervalSince(s) / total))
    }

    private func freshness(generatedAt: Date, now: Date, staleAfterHours: Int) -> DataFreshness {
        let hours = Int(now.timeIntervalSince(generatedAt) / 3600)
        return hours >= staleAfterHours ? .stale(hours: hours) : .fresh
    }

    /// Ближайшие учебные дни после указанного, в порядке следования.
    ///
    /// Пустые календарные дни пропускаются: виджет переходит к ближайшему
    /// дню, где занятия действительно есть (ТЗ п. 1.1, 11.3).
    func nextStudyDays(
        after day: Date,
        in lessons: [WLesson],
        now: Date,
        limit: Int
    ) -> [WidgetDay] {
        var dayStarts: [Date] = []
        for lesson in lessons {
            let start = dayStart(lesson)
            guard start > day else { continue }
            if !dayStarts.contains(where: { calendar.isDate($0, inSameDayAs: start) }) {
                dayStarts.append(start)
            }
        }

        return dayStarts.sorted().prefix(limit).map { startOfDay in
            let dayLessons = lessons.filter {
                calendar.isDate(self.dayStart($0), inSameDayAs: startOfDay)
            }
            return WidgetDay(
                label: compactDateLabel(startOfDay),
                lessons: dayLessons,
                date: dateString(startOfDay)
            )
        }
    }

    /// Контекст дня относительно `now`.
    ///
    /// Подпись единая для всех будущих дней — `ПТ, 23 ОКТ`. «Завтра/послезавтра»
    /// отдельными словами не выводятся: ТЗ требует конкретную дату, а не
    /// словесную метку, и дублировать её в двух формах нельзя.
    func context(for day: Date, now: Date) -> DayContext {
        let today = calendar.startOfDay(for: now)
        let diff = calendar.dateComponents([.day], from: today, to: day).day ?? 0
        switch diff {
        case 0:
            return .today
        case 1:
            return .tomorrow(label: compactDateLabel(day))
        case 2:
            return .dayAfterTomorrow(label: compactDateLabel(day))
        default:
            return .date(label: compactDateLabel(day))
        }
    }

    // MARK: Форматирование дат

    /// Форматтеры создаются один раз: `DateFormatter` дорог в создании,
    /// а виджет строит десятки подписей за один проход Timeline.
    private static let weekdayShortFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.timeZone = ScheduleClock.timeZone
        f.dateFormat = "E"
        return f
    }()

    private static let dayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.timeZone = ScheduleClock.timeZone
        f.dateFormat = "d"
        return f
    }()

    private static let monthShortFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.timeZone = ScheduleClock.timeZone
        f.dateFormat = "MMM"
        return f
    }()

    private static let weekdayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ru_RU")
        f.timeZone = ScheduleClock.timeZone
        f.dateFormat = "EEEE"
        return f
    }()

    /// `ПТ, 23 ОКТ` — единый компактный формат будущего учебного дня.
    ///
    /// Собирается из трёх `DateFormatter` с русской локалью и зоной расписания.
    /// Месяц приводится к трём буквам: CLDR отдаёт «окт.», «нояб.», «сент.» —
    /// разной длины и с точкой, а ТЗ требует единого стиля `ПН, 26 ОКТ`.
    /// Обрезка до трёх символов даёт ровно `янв, фев, мар, апр, май, июн, июл,
    /// авг, сен, окт, ноя, дек` — без таблиц строк в коде.
    func compactDateLabel(_ day: Date) -> String {
        let weekday = Self.weekdayShortFormatter.string(from: day)
        let number = Self.dayFormatter.string(from: day)
        let month = Self.monthShortFormatter.string(from: day)
        return "\(weekday), \(number) \(shortMonth(month))".uppercased()
    }

    /// Три буквы месяца без точки. CLDR для русского даёт от трёх до четырёх
    /// букв («май» / «нояб.»), поэтому длина нормализуется.
    private func shortMonth(_ raw: String) -> String {
        let trimmed = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ".", with: "")
        guard trimmed.count > 3 else { return trimmed }
        return String(trimmed.prefix(3))
    }

    /// `СРЕДА` — полное название дня недели для заголовка сегодняшнего дня.
    func weekdayName(_ day: Date) -> String {
        Self.weekdayFormatter.string(from: day).uppercased()
    }

    /// `СЕГОДНЯ · СРЕДА` — заголовок для сегодняшнего дня.
    func todayHeader(_ day: Date) -> String {
        "СЕГОДНЯ · \(weekdayName(day))"
    }

    /// Дата в формате снапшота «yyyy-MM-dd».
    func dateString(_ day: Date) -> String {
        let comps = calendar.dateComponents([.year, .month, .day], from: day)
        return String(
            format: "%04d-%02d-%02d",
            comps.year ?? 1970, comps.month ?? 1, comps.day ?? 1
        )
    }
}
