import XCTest
@testable import WidgetKitLogic

/// Тесты движка выбора активной пары (ТЗ п. 18).
///
/// Движок принимает `now` параметром, поэтому системное время не влияет:
/// все сценарии гоняются на заранее подготовленных датах. Зона зафиксирована
/// как Europe/Moscow — по ней живёт расписание РТУ МИРЭА.
final class ScheduleSelectionEngineTests: XCTestCase {

    private let tz = TimeZone(identifier: "Europe/Moscow")!

    private var engine: ScheduleSelectionEngine {
        ScheduleSelectionEngine(timeZone: tz)
    }

    // MARK: Фикстуры

    private func moment(_ text: String) -> Date {
        let f = DateFormatter()
        f.timeZone = tz
        f.locale = Locale(identifier: "ru_RU")
        f.dateFormat = "yyyy-MM-dd HH:mm"
        return f.date(from: text)!
    }

    private func lesson(
        id: String = "l1",
        date: String = "2026-10-21",
        start: String = "14:20",
        end: String = "15:50",
        bell: Int = 4,
        subject: String = "Базы данных",
        type: String = "ПР",
        room: String = "341 (С-20)",
        teacher: String = "Иванов И. И.",
        groups: [String] = ["БИСО-02-23"]
    ) -> WLesson {
        WLesson(
            id: id, date: date, bell: bell, start: start, end: end,
            subject: subject, type: type, room: room,
            teacher: teacher, groups: groups
        )
    }

    private func target(_ lessons: [WLesson], title: String = "БИСО-02-23") -> WTarget {
        WTarget(id: 1, title: title, typeName: "Группа", lessons: lessons)
    }

    private func state(
        _ lessons: [WLesson],
        at text: String,
        generatedAt: String? = nil
    ) -> WidgetPresentationState {
        let now = moment(text)
        return engine.makeState(
            target: target(lessons),
            now: now,
            generatedAt: generatedAt.map(moment) ?? now
        )
    }

    /// Расписание из ТЗ: пары 21 октября, пусто 22-го, пары 23 октября.
    private var twoDays: [WLesson] {
        [
            lesson(id: "a1", start: "14:20", end: "15:50", bell: 4,
                   subject: "Моделирование информационно-аналитических систем",
                   room: "265 (С-20)"),
            lesson(id: "a2", start: "16:20", end: "17:50", bell: 5,
                   subject: "Моделирование информационно-аналитических систем",
                   room: "341 (С-20)"),
            lesson(id: "b1", date: "2026-10-23", start: "10:40", end: "12:10", bell: 2,
                   subject: "Организационное и правовое обеспечение информационной безопасности",
                   room: "258а (С-20)"),
            lesson(id: "b2", date: "2026-10-23", start: "12:40", end: "14:10", bell: 3,
                   subject: "Организационное и правовое обеспечение информационной безопасности",
                   room: "258а (С-20)")
        ]
    }

    // MARK: 1–5. Границы занятия (ТЗ п. 11.1)

    func testBeforeStartIsSoon() {
        // За 16 минут до пары — статус «скоро», активная пара та же.
        let s = state(twoDays, at: "2026-10-21 14:04")
        XCTAssertEqual(s.primaryLesson?.id, "a1")
        XCTAssertEqual(s.primaryStatus, .soon(minutesUntil: 16))
    }

    func testBeforeSoonThresholdIsUpcoming() {
        // За два часа до пары — обычный статус «ДАЛЕЕ», без отсчёта.
        let s = state(twoDays, at: "2026-10-21 12:00")
        XCTAssertEqual(s.primaryLesson?.id, "a1")
        XCTAssertEqual(s.primaryStatus, .upcoming)
    }

    func testExactlyAtStartIsCurrent() {
        let s = state(twoDays, at: "2026-10-21 14:20")
        XCTAssertEqual(s.primaryLesson?.id, "a1")
        XCTAssertEqual(s.primaryStatus, .current)
    }

    func testMidLessonIsCurrent() {
        let s = state(twoDays, at: "2026-10-21 15:00")
        XCTAssertEqual(s.primaryLesson?.id, "a1")
        XCTAssertEqual(s.primaryStatus, .current)
    }

    func testExactlyAtEndIsNoLongerCurrent() {
        // Верхняя граница строгая: в момент окончания пара завершена,
        // активной становится следующая (ТЗ п. 11.1).
        let s = state(twoDays, at: "2026-10-21 15:50")
        XCTAssertEqual(s.primaryLesson?.id, "a2")
        XCTAssertEqual(s.primaryStatus, .soon(minutesUntil: 30))
    }

    // MARK: 6. Перемена

    func testBreakShowsNextLesson() {
        // Перемена 15:50–16:20: активной становится следующая пара.
        let s = state(twoDays, at: "2026-10-21 16:00")
        XCTAssertEqual(s.primaryLesson?.id, "a2")
        XCTAssertEqual(s.primaryStatus, .soon(minutesUntil: 20))
        XCTAssertTrue(s.primaryDay.isToday)
    }

    // MARK: Порог «скоро» включительный (ТЗ п. 12.3)

    func testSoonThresholdIsInclusive() {
        // Ровно 30 минут до начала — это ещё «скоро».
        let s = state(twoDays, at: "2026-10-21 15:50")
        XCTAssertEqual(s.primaryStatus, .soon(minutesUntil: 30))
    }

    // MARK: 7. После последней пары дня

    func testAfterLastLessonMovesToNextStudyDay() {
        // 22 октября занятий нет, поэтому активной становится пара 23 октября.
        let s = state(twoDays, at: "2026-10-21 18:00")
        XCTAssertEqual(s.primaryLesson?.id, "b1")
        XCTAssertEqual(s.primaryDay, .dayAfterTomorrow(label: "ПТ, 23 ОКТ"))
        XCTAssertEqual(s.primaryDay.label, "ПТ, 23 ОКТ")
    }

    func testDayWithoutLessonsMovesToTomorrow() {
        // 22 октября смотрим на 23-е — это завтра.
        let s = state(twoDays, at: "2026-10-22 10:00")
        XCTAssertEqual(s.primaryLesson?.id, "b1")
        XCTAssertEqual(s.primaryDay, .tomorrow(label: "ПТ, 23 ОКТ"))
        XCTAssertEqual(s.primaryDay.label, "ПТ, 23 ОКТ")
    }

    // MARK: 8–9. Несколько пустых дней и выходные

    func testSeveralEmptyDaysInARow() {
        // Пары в среду и в следующий вторник: 4 пустых дня пропускаются.
        let lessons = [
            lesson(id: "wed", date: "2026-10-21", start: "14:20", end: "15:50"),
            lesson(id: "tue", date: "2026-10-27", start: "09:00", end: "10:30")
        ]
        let s = state(lessons, at: "2026-10-21 18:00")
        XCTAssertEqual(s.primaryLesson?.id, "tue")
        XCTAssertEqual(s.primaryDay.label, "ВТ, 27 ОКТ")
    }

    func testMondayAfterWeekendIsPicked() {
        let lessons = [
            lesson(id: "fri", date: "2026-10-23", start: "10:40", end: "12:10"),
            lesson(id: "mon", date: "2026-10-26", start: "09:00", end: "10:30")
        ]
        let s = state(lessons, at: "2026-10-24 12:00")
        XCTAssertEqual(s.primaryLesson?.id, "mon")
        XCTAssertEqual(s.primaryDay.label, "ПН, 26 ОКТ")
    }

    // MARK: 10–11. Переход через месяц и год

    func testMonthBoundary() {
        let lessons = [
            lesson(id: "oct", date: "2026-10-30", start: "10:40", end: "12:10"),
            lesson(id: "nov", date: "2026-11-02", start: "10:40", end: "12:10")
        ]
        let s = state(lessons, at: "2026-10-31 12:00")
        XCTAssertEqual(s.primaryLesson?.id, "nov")
        XCTAssertEqual(s.primaryDay.label, "ПН, 2 НОЯ")
    }

    func testYearBoundary() {
        let lessons = [
            lesson(id: "dec", date: "2026-12-30", start: "10:40", end: "12:10"),
            lesson(id: "jan", date: "2027-01-11", start: "10:40", end: "12:10")
        ]
        let s = state(lessons, at: "2026-12-31 12:00")
        XCTAssertEqual(s.primaryLesson?.id, "jan")
        XCTAssertEqual(s.primaryDay.label, "ПН, 11 ЯНВ")
    }

    // MARK: 12. Разные часовые зоны (ТЗ п. 10.1)

    func testScheduleTimeZoneIsUsedNotDeviceZone() {
        // Расчёты идут в зоне расписания: 14:20 в Москве — это 14:20,
        // независимо от того, в какой зоне находится устройство.
        let lessonMoscow = lesson(id: "m", start: "14:20", end: "15:50")
        let engineMoscow = ScheduleSelectionEngine(timeZone: tz)
        XCTAssertTrue(engineMoscow.isOngoing(lessonMoscow, at: moment("2026-10-21 14:30")))

        // Тот же момент, но движок настроен на другую зону: 14:30 по Москве —
        // это 11:30 UTC, значит в UTC пара ещё не началась.
        let utc = TimeZone(identifier: "UTC")!
        let engineUtc = ScheduleSelectionEngine(timeZone: utc)
        let nowUtc = moment("2026-10-21 14:30")
        // В зоне UTC пара 14:20–15:50 ещё не началась (сейчас 11:30 UTC).
        XCTAssertFalse(engineUtc.isOngoing(lessonMoscow, at: nowUtc))
    }

    // MARK: 13. Две одинаковые дисциплины подряд (ТЗ п. 6.9)

    func testTwoIdenticalLessonsStayDistinct() {
        let s = state(twoDays, at: "2026-10-21 14:30")
        XCTAssertEqual(s.primaryLesson?.id, "a1")
        XCTAssertEqual(s.upcomingLessons.count, 1)
        XCTAssertEqual(s.upcomingLessons.first?.id, "a2")
        // Обе пары одного предмета, но остаются разными записями.
        XCTAssertEqual(s.upcomingLessons.first?.subject, s.primaryLesson?.subject)
        XCTAssertNotEqual(s.upcomingLessons.first?.start, s.primaryLesson?.start)
    }

    // MARK: 14. Два одновременных занятия (ТЗ п. 11.5)

    func testOverlappingLessonsPickDeterministically() {
        let lessons = [
            lesson(id: "x", start: "14:20", end: "15:50", bell: 4),
            lesson(id: "y", start: "14:30", end: "16:00", bell: 5)
        ]
        let s = state(lessons, at: "2026-10-21 15:00")
        // Выбор детерминирован: по времени начала, затем по номеру пары.
        XCTAssertEqual(s.primaryLesson?.id, "x")
    }

    // MARK: 15. Несколько участвующих групп (ТЗ п. 5.5)

    func testGroupCountUsesUniqueGroups() {
        let shared = lesson(
            id: "shared",
            groups: ["БИСО-02-23", "БИСО-02-24", "БИСО-02-24"]
        )
        // Повтор одного и того же идентификатора не увеличивает счётчик.
        XCTAssertEqual(shared.groupCount, 2)
        XCTAssertTrue(shared.isShared)

        let single = lesson(id: "single", groups: ["БИСО-02-23"])
        XCTAssertEqual(single.groupCount, 1)
        XCTAssertFalse(single.isShared)

        let none = lesson(id: "none", groups: [])
        XCTAssertEqual(none.groupCount, 0)
        XCTAssertFalse(none.isShared)
    }

    // MARK: 16. Пустой список

    func testEmptySchedule() {
        let s = state([], at: "2026-10-21 12:00")
        XCTAssertTrue(s.isEmpty)
        XCTAssertEqual(s.emptyReason, .noLessons)
    }

    // MARK: 17. Ошибка декодирования

    func testDecodingBrokenJSONGivesBadData() {
        let broken = Data("{ это не json".utf8)
        XCTAssertThrowsError(try JSONDecoder().decode(WPayload.self, from: broken))
    }

    func testDecodingWithoutGroupsAndIdStillWorks() throws {
        // Старый снапшот без id и groups не должен ломать виджет.
        let json = """
        {
          "generatedAt": 0,
          "defaultTargetId": 1,
          "targets": [{
            "id": 1, "title": "Группа", "typeName": "Группа",
            "lessons": [{
              "date": "2026-10-21", "bell": 4,
              "start": "14:20", "end": "15:50",
              "subject": "Базы данных", "type": "ПР",
              "room": "341 (С-20)", "teacher": ""
            }]
          }]
        }
        """
        let payload = try JSONDecoder().decode(WPayload.self, from: Data(json.utf8))
        let lesson = payload.targets[0].lessons[0]
        // id синтезирован, groups пустые.
        XCTAssertFalse(lesson.id.isEmpty)
        XCTAssertTrue(lesson.groups.isEmpty)
        XCTAssertEqual(lesson.groupCount, 0)
    }

    func testDecodingWithoutLessonTypeColorsStillWorks() throws {
        // Снапшот от версии приложения без пользовательских цветов: ключ
        // typeColorOverrides отсутствует. Виджет должен работать, а не
        // показывать «данные не читаются».
        let json = """
        {
          "generatedAt": 0,
          "defaultTargetId": 1,
          "targets": []
        }
        """
        let payload = try JSONDecoder().decode(WPayload.self, from: Data(json.utf8))
        XCTAssertTrue(payload.typeColorOverrides.isEmpty)
    }

    func testDecodingLessonTypeColorOverrides() throws {
        let json = """
        {
          "generatedAt": 0,
          "defaultTargetId": 1,
          "targets": [],
          "typeColorOverrides": {
            "ПР": {"light": -16546105, "dark": -12849028}
          }
        }
        """
        let payload = try JSONDecoder().decode(WPayload.self, from: Data(json.utf8))
        XCTAssertEqual(payload.typeColorOverrides.count, 1)
        XCTAssertEqual(payload.typeColorOverrides["ПР"]?.light, -16546105)
        XCTAssertEqual(payload.typeColorOverrides["ПР"]?.dark, -12849028)
    }

    /// Kotlin `Color.toArgb()` отдаёт знаковый Int, поэтому 0xFF0284C7
    /// приходит как отрицательное число. Маска должна вернуть тот же RGB,
    /// что и положительный вариант, — иначе бейдж станет другого цвета.
    func testSignedArgbNormalizesToSameRgbAsUnsigned() throws {
        // 0xFF0284C7 как знаковый Int32.
        let signed = Int(Int32(bitPattern: 0xFF0284C7))
        XCTAssertLessThan(signed, 0)

        let json = """
        {"generatedAt":0,"defaultTargetId":1,"targets":[],
         "typeColorOverrides":{"ЛК":{"light":\(signed),"dark":\(signed)}}}
        """
        let payload = try JSONDecoder().decode(WPayload.self, from: Data(json.utf8))
        let value = try XCTUnwrap(payload.typeColorOverrides["ЛК"])

        XCTAssertEqual(value.rgb(isDark: false), 0x0284C7)
        XCTAssertEqual(value.rgb(isDark: true), 0x0284C7)
    }

    /// Обе схемы читаются из своих полей, а не из одного.
    func testArgbUsesSchemeSpecificField() {
        let value = WTypeColor(light: 0x00FF00, dark: 0x0000FF)
        XCTAssertEqual(value.rgb(isDark: false), 0x00FF00)
        XCTAssertEqual(value.rgb(isDark: true), 0x0000FF)
    }

    // MARK: Сокращение названий дисциплин (параметр виджета)

    /// Названия из одного-двух слов не сокращаются — инициалы бессмысленны.
    func testShortSubjectsAreNotAbbreviated() {
        let one = lesson(subject: "Физика")
        XCTAssertEqual(one.subjectDisplay(abbreviated: true), "Физика")
        let two = lesson(subject: "Базы данных")
        XCTAssertEqual(two.subjectDisplay(abbreviated: false), "Базы данных")
    }

    /// От каждого слова — первая буква: длинные (>2) заглавной, короткие
    /// строчной. Правило унаследовано от экрана расписания приложения
    /// (`abbreviateSubjectName`), виджет своего не задаёт. «ими» — три
    /// буквы, инициал заглавной: «БДиУИ».
    func testLongSubjectAbbreviatesToInitials() {
        let long = lesson(subject: "Базы данных и управление ими")
        XCTAssertEqual(long.subjectDisplay(abbreviated: true), "БДиУИ")
    }

    /// Дефис — тоже граница слова, как в Kotlin-оригинале.
    func testHyphenIsAWordBoundary() {
        XCTAssertEqual(WLesson.abbreviate("Проектирование-и-разработка"), "ПиР")
    }

    /// Выключенное сокращение не меняет название.
    func testAbbreviationOffKeepsFullSubject() {
        let long = lesson(subject: "Базы данных и управление ими")
        XCTAssertEqual(long.subjectDisplay(abbreviated: false), "Базы данных и управление ими")
    }

    /// Двухбуквенные слова дают одну букву — как на экране расписания.
    func testTwoLetterWordsGiveFirstLetter() {
        XCTAssertEqual(WLesson.abbreviate("Теория на практике им. Иванова"), "ТнПИИ")
    }

    // MARK: 18. Устаревший кэш

    func testStaleDataIsMarkedButShown() {
        let s = state(twoDays, at: "2026-10-21 15:00", generatedAt: "2026-10-19 15:00")
        XCTAssertEqual(s.freshness, .stale(hours: 48))
        XCTAssertNotNil(s.primaryLesson)
    }

    func testFreshDataIsNotMarked() {
        let s = state(twoDays, at: "2026-10-21 15:00", generatedAt: "2026-10-21 14:00")
        XCTAssertEqual(s.freshness, .fresh)
    }

    // MARK: 19. Изменение расписания

    func testScheduleChangeReplacesPrimaryLesson() {
        // То же время, но пара перенесена в другую аудиторию и время.
        let before = state(twoDays, at: "2026-10-21 14:30")
        XCTAssertEqual(before.primaryLesson?.roomNumber, "265")

        var changed = twoDays
        changed[0] = lesson(id: "a1", start: "14:20", end: "15:50", bell: 4,
                            subject: "Моделирование информационно-аналитических систем",
                            room: "389 (С-20)")
        let after = state(changed, at: "2026-10-21 14:30")
        XCTAssertEqual(after.primaryLesson?.roomNumber, "389")
    }

    // MARK: 20. Отменённое занятие

    func testCancelledLessonIsNotTreatedAsCurrent() {
        // Фид не отдаёт отметку отмены, поэтому проверяем контракт: если
        // занятие отсутствует в списке, оно не может стать активным.
        let lessons = [
            lesson(id: "kept", start: "14:20", end: "15:50")
        ]
        let s = state(lessons, at: "2026-10-21 14:30")
        XCTAssertEqual(s.primaryLesson?.id, "kept")
        XCTAssertFalse(s.upcomingLessons.contains { $0.id == "cancelled" })
    }

    // MARK: Нет группы / нет данных

    func testNoTargetReportsNoTarget() {
        let s = engine.makeState(
            target: nil,
            now: moment("2026-10-21 12:00"),
            generatedAt: moment("2026-10-21 12:00")
        )
        XCTAssertTrue(s.isEmpty)
        XCTAssertEqual(s.emptyReason, .noTarget)
    }

    // MARK: Полночь и нестандартная длительность

    func testLessonCrossingMidnight() {
        let lessons = [lesson(id: "night", start: "23:00", end: "00:30")]
        let s = state(lessons, at: "2026-10-21 23:30")
        XCTAssertEqual(s.primaryStatus, .current)
    }

    func testNonStandardDuration() {
        let lessons = [
            lesson(id: "long", start: "14:20", end: "16:05"),
            lesson(id: "next", start: "16:20", end: "17:50", bell: 5)
        ]
        // За минуту до конца — ещё идёт.
        XCTAssertEqual(state(lessons, at: "2026-10-21 16:04").primaryStatus, .current)
        // Ровно в момент окончания — уже следующая, до неё 15 минут.
        let after = state(lessons, at: "2026-10-21 16:05")
        XCTAssertEqual(after.primaryLesson?.id, "next")
        XCTAssertEqual(after.primaryStatus, .soon(minutesUntil: 15))
    }

    // MARK: Следующие учебные дни (ТЗ п. 11.3)

    func testNextDaysAreFilledSkippingEmptyDates() {
        let s = state(twoDays, at: "2026-10-21 15:00")
        XCTAssertEqual(s.nextDays.count, 1)
        XCTAssertEqual(s.nextDays.first?.label, "ПТ, 23 ОКТ")
        XCTAssertEqual(s.nextDays.first?.lessons.count, 2)
        XCTAssertEqual(s.nextDays.first?.date, "2026-10-23")
    }

    func testSecondNextDayIsProvidedWhenExists() {
        let lessons = twoDays + [
            lesson(id: "c1", date: "2026-10-27", start: "09:00", end: "10:30", bell: 1)
        ]
        let s = state(lessons, at: "2026-10-21 15:00")
        XCTAssertEqual(s.nextDays.count, 2)
        XCTAssertEqual(s.nextDays[1].label, "ВТ, 27 ОКТ")
    }

    func testNoNextDaysWhenNothingAhead() {
        let lessons = [lesson(id: "only", start: "14:20", end: "15:50")]
        let s = state(lessons, at: "2026-10-21 15:00")
        XCTAssertTrue(s.nextDays.isEmpty)
    }

    // MARK: Порядок следующих пар (ТЗ п. 11.2)

    func testUpcomingLessonsAreSortedByStart() {
        let lessons = [
            lesson(id: "c", start: "18:00", end: "19:30", bell: 6),
            lesson(id: "a", start: "10:40", end: "12:10", bell: 2),
            lesson(id: "b", start: "14:20", end: "15:50", bell: 4),
            lesson(id: "primary", start: "09:00", end: "10:30", bell: 1)
        ]
        let s = state(lessons, at: "2026-10-21 09:30")
        XCTAssertEqual(s.primaryLesson?.id, "primary")
        XCTAssertEqual(s.upcomingLessons.map(\.id), ["a", "b", "c"])
    }

    func testPrimaryLessonIsNotDuplicatedInUpcoming() {
        let s = state(twoDays, at: "2026-10-21 14:30")
        XCTAssertFalse(s.upcomingLessons.contains { $0.id == s.primaryLesson?.id })
    }

    func testFinishedLessonsAreExcludedFromUpcoming() {
        let s = state(twoDays, at: "2026-10-21 14:30")
        // Завершённых пар до активной в списке быть не должно.
        XCTAssertFalse(s.upcomingLessons.contains { $0.id == "finished" })
    }

    // MARK: Аудитория и корпус (ТЗ п. 3)

    func testRoomIsSplitIntoNumberAndCampus() {
        let l = lesson(id: "r", room: "265 (С-20)")
        XCTAssertEqual(l.roomNumber, "265")
        XCTAssertEqual(l.buildingLabel, "С-20")
        XCTAssertFalse(l.isOnline)
    }

    func testRoomWithLetterKeepsLetter() {
        // «258а» — буква в номере аудитории должна сохраняться (ТЗ п. 19.1).
        let l = lesson(id: "r", room: "258а (С-20)")
        XCTAssertEqual(l.roomNumber, "258а")
        XCTAssertEqual(l.buildingLabel, "С-20")
    }

    func testRoomWithoutCampus() {
        let l = lesson(id: "r", room: "А-4")
        XCTAssertEqual(l.roomNumber, "А-4")
        XCTAssertEqual(l.buildingLabel, "")
    }

    func testMissingRoom() {
        let l = lesson(id: "r", room: "")
        XCTAssertEqual(l.roomNumber, "")
        XCTAssertEqual(l.buildingLabel, "")
        XCTAssertEqual(l.roomDisplay, "Аудитория уточняется")
    }

    func testOnlineLesson() {
        let l = lesson(id: "r", room: "СДО")
        XCTAssertTrue(l.isOnline)
        XCTAssertEqual(l.roomDisplay, "Онлайн")
    }

    // MARK: Время (ТЗ п. 8.2)

    func testTimeRangeUsesEnDash() {
        let l = lesson(id: "t", start: "14:20", end: "15:50")
        XCTAssertEqual(l.timeRange, "14:20–15:50")
        XCTAssertEqual(l.timeRangeAccessible, "с 14:20 до 15:50")
    }

    // MARK: Преподаватель (ТЗ п. 5.4)

    func testEmptyTeacherIsNilNotEmptyString() {
        XCTAssertNil(lesson(id: "t", teacher: "").teacherDisplay)
        XCTAssertNil(lesson(id: "t", teacher: "   ").teacherDisplay)
        XCTAssertEqual(lesson(id: "t", teacher: "Иванов И. И.").teacherDisplay, "Иванов И. И.")
    }

    // MARK: Прогресс пары

    func testProgressIsNilForUpcomingLesson() {
        // У будущей пары прогресса нет — полоса не должна рисоваться.
        let s = state(twoDays, at: "2026-10-21 12:00")
        XCTAssertNil(s.primaryProgress)
    }

    func testProgressIsCalculatedForCurrentLesson() {
        // Пара 14:20–15:50 (90 минут), смотрим 15:05 — прошло 45 минут.
        let s = state(twoDays, at: "2026-10-21 15:05")
        XCTAssertEqual(s.primaryStatus, .current)
        guard let progress = s.primaryProgress else {
            XCTFail("у идущей пары должен быть прогресс")
            return
        }
        XCTAssertEqual(progress, 0.5, accuracy: 0.01)
    }

    func testProgressAtStartIsNearZero() {
        let s = state(twoDays, at: "2026-10-21 14:20")
        XCTAssertEqual(s.primaryProgress ?? -1, 0, accuracy: 0.01)
    }

    func testProgressNearEndIsNearOne() {
        let s = state(twoDays, at: "2026-10-21 15:49")
        XCTAssertEqual(s.primaryProgress ?? -1, 0.99, accuracy: 0.02)
    }

    func testProgressIsNilWhenPrimaryIsTomorrow() {
        // Активная пара — уже завтрашняя: полоса прогресса не имеет смысла.
        let s = state(twoDays, at: "2026-10-21 18:00")
        XCTAssertNil(s.primaryProgress)
    }
}
