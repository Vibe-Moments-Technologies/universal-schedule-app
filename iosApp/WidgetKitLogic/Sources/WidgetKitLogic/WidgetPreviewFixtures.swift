import Foundation

// MARK: - Фикстуры для Preview и placeholder
//
// ТЗ п. 17 задаёт обязательные наборы данных, п. 19 — обязательные состояния.
// Фикстуры строятся тем же движком, что и реальные данные, поэтому Preview
// показывает настоящую логику, а не нарисованную вручную разметку.
//
// Файл без SwiftUI: участвует в unit-тестах и в placeholder.

/// Готовый набор для Preview: состояние + подпись сценария.
struct WidgetPreviewFixture {
    let name: String
    let targetId: Int?
    let state: WidgetPresentationState
}

enum WidgetPreviewFixtures {

    // MARK: Данные из ТЗ п. 17

    /// Набор A / C: дисциплины из макетов.
    static let modelingSubject = "Моделирование информационно-аналитических систем"
    static let longSubject = "Организационное и правовое обеспечение информационной безопасности"
    static let longerSubject = "Информационно-аналитические технологии поиска угроз информационной безопасности"

    /// Дата сценариев: среда, 21 октября 2026.
    static let scenarioDate = "2026-10-21"
    /// Следующий учебный день из набора D: пятница, 23 октября.
    static let nextDayDate = "2026-10-23"

    // MARK: Время

    /// Момент времени в зоне расписания.
    static func moment(_ text: String) -> Date {
        let f = DateFormatter()
        f.timeZone = ScheduleClock.timeZone
        f.locale = Locale(identifier: "ru_RU")
        f.dateFormat = "yyyy-MM-dd HH:mm"
        return f.date(from: text) ?? Date()
    }

    private static var engine: ScheduleSelectionEngine {
        ScheduleSelectionEngine()
    }

    // MARK: Сборка пар

    static func lesson(
        id: String,
        date: String,
        start: String,
        end: String,
        bell: Int,
        subject: String,
        type: String,
        room: String,
        teacher: String = "Иванов И. И.",
        groups: [String] = ["БИСО-02-23"]
    ) -> WLesson {
        WLesson(
            id: id, date: date, bell: bell, start: start, end: end,
            subject: subject, type: type, room: room, teacher: teacher, groups: groups
        )
    }

    /// Набор A: активная пара — 14:20–15:50, 265 (С-20), ПР, две группы.
    static func primaryPair(_ date: String) -> WLesson {
        lesson(
            id: "\(date)-a", date: date, start: "14:20", end: "15:50", bell: 4,
            subject: modelingSubject, type: "ПР", room: "265 (С-20)",
            groups: ["БИСО-02-23", "БИСО-02-24"]
        )
    }

    /// Набор B: следующая пара — 16:20–17:50, 341 (С-20).
    static func nextPair(_ date: String) -> WLesson {
        lesson(
            id: "\(date)-b", date: date, start: "16:20", end: "17:50", bell: 5,
            subject: modelingSubject, type: "ПР", room: "341 (С-20)"
        )
    }

    /// Набор C: длинное название, аудитория 389 (С-20).
    static func longPair(_ date: String) -> WLesson {
        lesson(
            id: "\(date)-c", date: date, start: "18:00", end: "19:30", bell: 6,
            subject: longerSubject, type: "ЛК", room: "389 (С-20)"
        )
    }

    /// Набор D: будущий учебный день — две пары по одной дисциплине подряд
    /// в одной аудитории (проверка п. 6.9: объединять интервалы нельзя).
    static func futureDay(_ date: String) -> [WLesson] {
        [
            lesson(
                id: "\(date)-1", date: date, start: "10:40", end: "12:10", bell: 2,
                subject: longSubject, type: "ПР", room: "258а (С-20)",
                groups: ["БИСО-02-23", "БИСО-02-24", "БИСО-02-25"]
            ),
            lesson(
                id: "\(date)-2", date: date, start: "12:40", end: "14:10", bell: 3,
                subject: longSubject, type: "ПР", room: "258а (С-20)",
                groups: ["БИСО-02-23", "БИСО-02-24", "БИСО-02-25"]
            )
        ]
    }

    /// Полный учебный день: активная пара, следующая и длинная.
    static func scenarioDay() -> [WLesson] {
        [
            lesson(
                id: "\(scenarioDate)-0", date: scenarioDate, start: "09:00", end: "10:30", bell: 1,
                subject: "Базы данных", type: "ЛК", room: "341 (С-20)"
            ),
            lesson(
                id: "\(scenarioDate)-1", date: scenarioDate, start: "10:40", end: "12:10", bell: 2,
                subject: longSubject, type: "ПР", room: "258а (С-20)"
            ),
            lesson(
                id: "\(scenarioDate)-2", date: scenarioDate, start: "12:40", end: "14:10", bell: 3,
                subject: "Теория вероятностей", type: "ЛАБ", room: "412 (В-78)"
            ),
            primaryPair(scenarioDate),
            nextPair(scenarioDate),
            longPair(scenarioDate)
        ]
    }

    static func target(_ lessons: [WLesson]) -> WTarget {
        WTarget(id: 1, title: "БИСО-02-23", typeName: "Группа", lessons: lessons)
    }

    // MARK: Сценарии (ТЗ п. 19)

    private static func state(_ lessons: [WLesson], at text: String) -> WidgetPresentationState {
        let now = moment(text)
        return engine.makeState(target: target(lessons), now: now, generatedAt: now)
    }

    /// Идёт занятие: смотрим в 15:00, пара 14:20–15:50.
    static let current = WidgetPreviewFixture(
        name: "CURRENT",
        targetId: 1,
        state: state(scenarioDay(), at: "2026-10-21 15:00")
    )

    /// Перемена: 15:50–16:20, активной становится следующая пара.
    static let breakBetween = WidgetPreviewFixture(
        name: "BREAK",
        targetId: 1,
        state: state(scenarioDay(), at: "2026-10-21 16:00")
    )

    /// Ближайшая пара завтра.
    static let tomorrow = WidgetPreviewFixture(
        name: "TOMORROW",
        targetId: 1,
        state: state(futureDay(nextDayDate), at: "2026-10-22 18:00")
    )

    /// Ближайшая пара через несколько дней.
    static let laterDay = WidgetPreviewFixture(
        name: "LATER_DAY",
        targetId: 1,
        state: state(futureDay("2026-10-27"), at: "2026-10-21 20:00")
    )

    /// День закончился: активной становится пара ближайшего учебного дня,
    /// а его соседние пары попадают в «далее».
    static let dayOver = WidgetPreviewFixture(
        name: "DAY_OVER",
        targetId: 1,
        state: state(scenarioDay() + futureDay(nextDayDate), at: "2026-10-21 20:00")
    )

    /// Одна пара за день: правой зоны в Medium не будет (Mode 0).
    static let oneLesson = WidgetPreviewFixture(
        name: "ONE_LESSON",
        targetId: 1,
        state: state([primaryPair(scenarioDate)], at: "2026-10-21 15:00")
    )

    /// Активная пара с преподавателем и индикатором двух групп, следующих нет.
    /// Обязательный сценарий проверки Medium (преподаватель + группы).
    static let teacherAndGroups = WidgetPreviewFixture(
        name: "TEACHER_GROUPS",
        targetId: 1,
        state: state([primaryPair(scenarioDate)], at: "2026-10-21 15:00")
    )

    /// Активная пара и одна следующая — должна читаться без сжатия.
    static let primaryPlusOne = WidgetPreviewFixture(
        name: "PRIMARY_PLUS_ONE",
        targetId: 1,
        state: state([primaryPair(scenarioDate), nextPair(scenarioDate)], at: "2026-10-21 15:00")
    )

    /// Две пары: Mode 1.
    static let twoLessons = WidgetPreviewFixture(
        name: "TWO_LESSONS",
        targetId: 1,
        state: state([primaryPair(scenarioDate), nextPair(scenarioDate)], at: "2026-10-21 15:00")
    )

    /// Много пар: Mode 2 и проверка, что список не переполняет виджет.
    static let manyLessons = WidgetPreviewFixture(
        name: "MANY_LESSONS",
        targetId: 1,
        state: state(scenarioDay() + futureDay(nextDayDate), at: "2026-10-21 15:00")
    )

    /// Длинная дисциплина (набор C).
    static let longTitle = WidgetPreviewFixture(
        name: "LONG_TITLE",
        targetId: 1,
        state: state([longPair(scenarioDate)], at: "2026-10-21 18:30")
    )

    /// Нет аудитории, нет корпуса, дистант (набор G).
    static let noRoom = WidgetPreviewFixture(
        name: "NO_ROOM",
        targetId: 1,
        state: state(
            [
                lesson(id: "g1", date: scenarioDate, start: "14:20", end: "15:50", bell: 4,
                       subject: modelingSubject, type: "ПР", room: ""),
                lesson(id: "g2", date: scenarioDate, start: "16:20", end: "17:50", bell: 5,
                       subject: "Физика", type: "ЛАБ", room: "СДО")
            ],
            at: "2026-10-21 15:00"
        )
    )

    /// Пара без преподавателя: место под него не резервируется.
    static let noTeacher = WidgetPreviewFixture(
        name: "NO_TEACHER",
        targetId: 1,
        state: state(
            [
                lesson(id: "nt", date: scenarioDate, start: "14:20", end: "15:50", bell: 4,
                       subject: modelingSubject, type: "ПР", room: "265 (С-20)", teacher: "")
            ],
            at: "2026-10-21 15:00"
        )
    )

    /// Занятий нет в известном расписании.
    static let noSchedule = WidgetPreviewFixture(
        name: "NO_SCHEDULE",
        targetId: 1,
        state: state([], at: "2026-10-21 12:00")
    )

    /// Группа не выбрана.
    static let noTarget = WidgetPreviewFixture(
        name: "NO_TARGET",
        targetId: nil,
        state: engine.makeState(
            target: nil,
            now: moment("2026-10-21 12:00"),
            generatedAt: moment("2026-10-21 12:00")
        )
    )

    /// Данные устарели: показываются, но с пометкой.
    static let stale = WidgetPreviewFixture(
        name: "STALE",
        targetId: 1,
        state: {
            let now = moment("2026-10-21 15:00")
            return engine.makeState(
                target: target(scenarioDay()),
                now: now,
                generatedAt: moment("2026-10-19 15:00")
            )
        }()
    )

    /// Все сценарии — для перебора в Preview.
    static let all: [WidgetPreviewFixture] = [
        current, breakBetween, tomorrow, laterDay, dayOver,
        oneLesson, teacherAndGroups, primaryPlusOne, twoLessons, manyLessons,
        longTitle, noRoom, noTeacher, noSchedule, noTarget, stale
    ]
}
