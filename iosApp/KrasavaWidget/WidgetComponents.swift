import SwiftUI

// MARK: - Тип занятия в строке названия

/// Тип занятия перед названием дисциплины: `ПР · Базы данных`.
///
/// Просьба владельца: метка типа должна стоять перед названием одной строкой
/// во всех виджетах, в том числе у неактивных пар. Отдельный бейдж в шапке
/// (только у активной пары) оставляем для главной пары — там он работает как
/// акцент, — а в списках тип идёт в начале названия, потому что справа уже
/// занято аудиторией.
struct SubjectWithType: View {
    let lesson: WLesson
    let palette: WidgetPalette
    let size: CGFloat
    var weight: Font.Weight = .medium
    var lines: Int = 2
    /// Показывать ли тип. Если типа нет в модели, он просто не выводится.
    var showsType: Bool = true
    /// Сокращать название дисциплины (настройка «Изменить виджет»):
    /// инициалы слов для названий длиннее двух слов.
    var abbreviatedSubject: Bool = false

    var body: some View {
        content
            .lineLimit(lines)
            .minimumScaleFactor(0.85)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityLabel(accessibility)
    }

    /// Тип и название складываются оператором `+` над `Text`: результат уже
    /// `Text`, поэтому блок измеряется как целое и перенос строки не отрывает
    /// метку от начала дисциплины.
    private var content: Text {
        guard showsType, let type = lesson.type.nilIfBlank else {
            return styledSubject
        }
        return Text("\(type) · ")
            .font(.system(size: size - 1, weight: .bold))
            .foregroundColor(palette.typeColor(type))
            + styledSubject
    }

    private var styledSubject: Text {
        Text(lesson.subjectDisplay(abbreviated: abbreviatedSubject))
            .font(.system(size: size, weight: weight))
            .foregroundColor(palette.subjectText)
    }

    private var accessibility: String {
        // VoiceOver всегда читает полное название: сокращение — про экономию
        // места на экране, а не про то, что слышит пользователь.
        guard showsType, let type = lesson.type.nilIfBlank else {
            return lesson.subject
        }
        return "Тип занятия \(type), \(lesson.subject)"
    }
}

// MARK: - Общие компоненты виджетов (ТЗ п. 8)
//
// Один набор деталей на все четыре размера: аудитория, время, название,
// бейдж типа, статус всегда выглядят и усекаются одинаково. Представления
// не решают, что важнее — это решено здесь и в токенах.

// MARK: - Аудитория (ТЗ п. 3)

/// Неразрывное обозначение аудитории с корпусом: `265 (С-20)`.
///
/// Главное требование (ТЗ п. 3.1, 3.2): аудитория и корпус — единый блок,
/// который никогда не переносится и не получает многоточие. Поэтому собирается
/// один `Text` с двумя фрагментами разного веса: измеряется как целое,
/// усечение невозможно, визуальная иерархия сохраняется.
struct ClassroomLabel: View {
    let lesson: WLesson
    let palette: WidgetPalette
    /// Размер номера аудитории в пунктах.
    let size: CGFloat
    /// Начертание номера (ТЗ п. 2.2: `.bold` или `.semibold`).
    var weight: Font.Weight = .bold

    var body: some View {
        content
            // Одна строка и никакого многоточия: при нехватке ширины
            // срабатывает усадка, а не обрезка (ТЗ п. 3.2).
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .accessibilityLabel(accessibility)
    }

    @ViewBuilder
    private var content: some View {
        if let room = normalizedRoom, let campus = normalizedCampus {
            // Номер ярче, корпус спокойнее — но одной текстовой строкой.
            Text(room)
                .font(.system(size: size, weight: weight))
                .foregroundColor(palette.primaryText)
            + Text(" (\(campus))")
                .font(.system(size: campusSize(roomSize: size), weight: .regular))
                .foregroundColor(palette.secondaryText)
        } else if let room = normalizedRoom {
            // Корпус неизвестен — только аудитория, без пустых скобок.
            Text(room)
                .font(.system(size: size, weight: weight))
                .foregroundColor(palette.primaryText)
        } else if let campus = normalizedCampus {
            // Аудитория неизвестна, но корпус есть: обозначение места
            // без «()» (ТЗ п. 3.3).
            Text("Корпус \(campus)")
                .font(.system(size: size * 0.7, weight: .semibold))
                .foregroundColor(palette.secondaryText)
        } else {
            // Ни того, ни другого: короткое сообщение вместо пустоты.
            Text(lesson.isOnline ? "Онлайн" : "Место уточняется")
                .font(.system(size: size * 0.62, weight: .semibold))
                .foregroundColor(palette.secondaryText)
        }
    }

    /// Номер аудитории. Пустое значение → nil, чтобы не рисовать «()».
    private var normalizedRoom: String? {
        lesson.isOnline ? nil : lesson.roomNumber.nilIfBlank
    }

    /// Код кампуса.
    private var normalizedCampus: String? {
        lesson.buildingLabel.nilIfBlank
    }

    /// Корпус мельче номера примерно на треть: блок читается как единое
    /// целое, но номер остаётся главным (ТЗ п. 3.1, 4.2).
    private func campusSize(roomSize: CGFloat) -> CGFloat {
        max(roomSize * 0.62, 10)
    }

    private var accessibility: String {
        let room = normalizedRoom
        let campus = normalizedCampus
        if lesson.isOnline { return "Аудитория: онлайн" }
        if let room, let campus { return "Аудитория \(room), корпус \(campus)" }
        if let room { return "Аудитория \(room)" }
        if let campus { return "Корпус \(campus)" }
        return "Место уточняется"
    }
}

// MARK: - Время (ТЗ п. 8.2)

/// Интервал занятия одной строкой: `14:20–15:50`.
///
/// Формат единый для всех занятий: типографское тире, моноширинные цифры,
/// запрет переноса. Время — P0: не разрывается и не скрывается.
struct LessonTimeLabel: View {
    let lesson: WLesson
    let palette: WidgetPalette
    let size: CGFloat
    var weight: Font.Weight = .semibold
    var color: Color?

    var body: some View {
        Text(lesson.timeRange)
            .font(.system(size: size, weight: weight))
            .monospacedDigit()
            .foregroundColor(color ?? palette.primaryText)
            .lineLimit(1)
            // Время не ужимается вместе с соседями: фиксируем по ширине
            // содержимого, иначе SwiftUI отдаст место названию.
            .fixedSize(horizontal: true, vertical: false)
            .accessibilityLabel(lesson.timeRangeAccessible)
    }
}

// MARK: - Прогресс пары

/// Полоса прогресса идущей пары.
///
/// Показывается только когда пара действительно идёт (`progress != nil`):
/// для будущей пары полоса была бы украшением без смысла. Толщина 3 pt —
/// заметно, но не отнимает места у названия. Цвет акцента совпадает с меткой
/// «СЕЙЧАС», поэтому состояние читается и в тонированном режиме, где цвет
/// становится монохромным, — там работает сама длина полосы.
struct LessonProgressBar: View {
    let progress: Double
    let palette: WidgetPalette
    var height: CGFloat = 3

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(palette.divider.opacity(0.5))
                Capsule()
                    .fill(palette.activeAccent)
                    .frame(width: max(height, geo.size.width * progress))
            }
        }
        .frame(height: height)
        .accessibilityElement()
        .accessibilityLabel("Прошло \(Int(progress * 100)) процентов пары")
        .accessibilityValue("\(Int(progress * 100))%")
    }
}

// MARK: - Статус (ТЗ п. 8.3)

/// Короткая цветовая метка статуса: `СЕЙЧАС`, `ДАЛЕЕ`, `СКОРО`.
///
/// Статус приходит из бизнес-логики, вью его не вычисляет. В тонированном
/// режиме цвет не несёт смысла, поэтому метка всегда ещё и текстовая.
struct LessonStatusLabel: View {
    let status: LessonStatus
    let palette: WidgetPalette
    let size: CGFloat
    /// Обратный отсчёт допустим только там, где есть место (Medium+).
    var allowCountdown: Bool = false
    /// Скрывать ли «ДАЛЕЕ».
    ///
    /// В Large и Extra Large над активной парой уже стоит заголовок дня,
    /// поэтому «ДАЛЕЕ» под ним ничего не добавляет — владелец попросил убрать
    /// эту избыточную подпись. «СЕЙЧАС» и «ЧЕРЕЗ N МИН» остаются: они несут
    /// новую информацию.
    var hidesUpcoming: Bool = false

    var body: some View {
        if hidesUpcoming, case .upcoming = status {
            EmptyView()
        } else {
            Text(allowCountdown ? status.countdownLabel : status.shortLabel)
                .font(.system(size: size, weight: .semibold))
                .foregroundColor(palette.activeAccent)
                // Разрядка: короткий статус читается как маркер, а не как слово.
                .tracking(0.4)
                .lineLimit(1)
                .accessibilityLabel(accessibility)
        }
    }

    private var accessibility: String {
        switch status {
        case .current: return "Идёт сейчас"
        case .upcoming: return "Следующая пара"
        case .soon(let minutes): return "Начнётся через \(minutes) минут"
        }
    }
}

// MARK: - Тип занятия

/// Компактный бейдж типа: `ПР`, `ЛК`, `ЛБ`.
///
/// Больше нигде не выводится: по просьбе владельца тип стоит перед названием
/// одной строкой во всех виджетах (`SubjectWithType`). Оставлен как готовый
/// компонент на случай возврата акцентного бейджа в шапку — удалять рабочий
/// элемент с accessibility-подписями ради «чистоты» не стоит.
struct LessonTypeBadge: View {
    let lesson: WLesson
    let palette: WidgetPalette
    let size: CGFloat

    var body: some View {
        if let type = lesson.type.nilIfBlank {
            Text(type)
                .font(.system(size: size, weight: .semibold))
                .foregroundColor(palette.typeColor(type))
                .padding(.horizontal, Space.s4)
                .padding(.vertical, 1)
                .background(palette.typeBackground(type))
                .clipShape(RoundedRectangle(cornerRadius: 3, style: .continuous))
                .lineLimit(1)
                .accessibilityLabel("Тип занятия: \(type)")
        }
    }
}

// MARK: - Преподаватель (ТЗ п. 8.5)

/// Имя преподавателя. Сокращение делает модель, а не вью: ТЗ п. 5.4
/// запрещает неустойчивые эвристики для иностранных и составных имён.
struct TeacherLabel: View {
    let lesson: WLesson
    let palette: WidgetPalette
    let size: CGFloat

    var body: some View {
        if let teacher = lesson.teacherDisplay {
            Text(teacher)
                .font(.system(size: size, weight: .regular))
                .foregroundColor(palette.secondaryText)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
        }
    }
}

// MARK: - Несколько групп (ТЗ п. 8.6)

/// Индикатор совместного занятия: `person.2.fill` + «2 группы».
///
/// Появляется только если в модели известно больше одной уникальной группы
/// (ТЗ п. 5.5). Для тесной компоновки остаётся иконка с числом.
struct MultiGroupIndicator: View {
    let lesson: WLesson
    let palette: WidgetPalette
    let size: CGFloat
    /// Показывать ли слово «группы» или оставить только число.
    var showWord: Bool = true

    var body: some View {
        if lesson.isShared {
            HStack(spacing: Space.s2) {
                Image(systemName: "person.2.fill")
                    .font(.system(size: size))
                    .foregroundColor(palette.mutedText)
                Text(label)
                    .font(.system(size: size, weight: .regular))
                    .foregroundColor(palette.mutedText)
                    .lineLimit(1)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Совместное занятие, групп: \(lesson.groupCount)")
        }
    }

    /// «2 группы» / «5 групп» — по правилам русского счёта.
    private var label: String {
        let count = lesson.groupCount
        guard showWord else { return "\(count)" }
        let noun: String
        switch count % 10 {
        case 1 where count % 100 != 11: noun = "группа"
        case 2...4 where !(11...14).contains(count % 100): noun = "группы"
        default: noun = "групп"
        }
        return "\(count) \(noun)"
    }
}

// MARK: - Строка следующей пары (ТЗ п. 8.8)

/// Компактная запись следующей пары.
///
/// Две строки: сверху время и аудитория (в одинаковых колонках у всех записей
/// списка — ТЗ п. 2.4), снизу тип и название во всю ширину. Однострочная
/// компоновка оставляла названию ~30 символов, и длинные дисциплины
/// обрезались; здесь оно получает всю ширину при том же вертикальном размере.
struct UpcomingLessonRow: View {
    let lesson: WLesson
    let palette: WidgetPalette
    let density: WidgetDensity
    /// Диапазоны шрифтов из таблицы ТЗ п. 2.2.
    let typography: WidgetTypography
    /// Подпись перед временем первой строки: «ДАЛЕЕ» в Medium, где отдельный
    /// заголовок секции не помещается по высоте. nil — без подписи.
    var inlineLabel: String? = nil
    /// Сокращать название дисциплины (параметр «Изменить виджет»).
    var abbreviatedSubject: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: density.lineSpacing) {
            HStack(alignment: .firstTextBaseline, spacing: Space.s6) {
                if let inlineLabel {
                    Text(inlineLabel)
                        .font(.system(size: typography.rowTime(11...14) - 1, weight: .semibold))
                        .foregroundColor(palette.mutedText)
                        .tracking(0.3)
                        .lineLimit(1)
                }
                LessonTimeLabel(
                    lesson: lesson,
                    palette: palette,
                    size: typography.rowTime(11...14),
                    weight: .medium,
                    color: palette.secondaryText
                )
                // Аудитория прижата к правому краю: у всех записей списка
                // она в одной колонке (ТЗ п. 2.4).
                Spacer(minLength: Space.s4)
                ClassroomLabel(
                    lesson: lesson,
                    palette: palette,
                    size: typography.rowRoom(11...15),
                    weight: .semibold
                )
            }

            // Тип перед названием одной строкой — всегда и во всех виджетах
            // (просьба владельца). Раньше метка зависела от плотности и
            // пропадала у первых следующих пар, поэтому условие убрано.
            SubjectWithType(
                lesson: lesson,
                palette: palette,
                size: typography.rowSubject(11...14),
                lines: density.rowSubjectLines,
                abbreviatedSubject: abbreviatedSubject
            )
            .layoutPriority(1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "\(lesson.timeRangeAccessible), \(lesson.subject), \(lesson.roomDisplay)"
        )
    }
}

// MARK: - Кнопка обновления

/// Кнопка принудительного пересчёта виджета.
///
/// Интерактивные виджеты появились в iOS 17, поэтому на 15–16 кнопка не
/// рисуется вовсе: мёртвая кнопка хуже, чем её отсутствие. Действие
/// пересобирает Timeline из сохранённого расписания — статус и полоса
/// прогресса становятся актуальными без запуска приложения.
struct WidgetRefreshButton: View {
    let palette: WidgetPalette
    /// С надписью «Обновить» (Medium и крупнее) или только значок (Small).
    var showsTitle: Bool = true

    var body: some View {
        if #available(iOS 17.0, *) {
            Button(intent: RefreshWidgetIntent()) {
                if showsTitle {
                    HStack(spacing: 3) {
                        Image(systemName: "arrow.clockwise")
                        Text("Обновить")
                    }
                    .font(.system(size: 10, weight: .medium))
                } else {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 11, weight: .semibold))
                }
            }
            .buttonStyle(.plain)
            .foregroundColor(palette.mutedText)
            .accessibilityLabel("Обновить виджет")
        }
    }
}

// MARK: - Разделитель и заголовки

/// Тонкий разделитель между самостоятельными блоками.
struct WidgetDivider: View {
    let palette: WidgetPalette

    var body: some View {
        Rectangle()
            .fill(palette.divider)
            .frame(height: 1)
    }
}

/// Заголовок учебного дня или секции: `СЕГОДНЯ · СРЕДА`, `ДАЛЕЕ СЕГОДНЯ`.
struct WidgetSectionTitle: View {
    let text: String
    let palette: WidgetPalette
    let size: CGFloat
    /// Заголовок дня — чуть заметнее служебной подписи секции.
    var isDayHeader: Bool = false

    var body: some View {
        Text(text.uppercased())
            .font(.system(size: size, weight: isDayHeader ? .semibold : .medium))
            .foregroundColor(isDayHeader ? palette.secondaryText : palette.mutedText)
            .tracking(0.3)
            .lineLimit(1)
            .accessibilityAddTraits(.isHeader)
    }
}

// MARK: - Хелперы

extension String {
    /// Строка, в которой нет ничего кроме пробелов, — это отсутствие значения.
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
