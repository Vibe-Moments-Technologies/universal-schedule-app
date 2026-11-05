import SwiftUI

// MARK: - Small: одна активная пара (ТЗ п. 4)
//
// Назначение: только активная пара. Никаких следующих занятий, преподавателя,
// списков дней и информационных блоков за пределами главной пары.
//
// Структура строго в таком порядке (ТЗ п. 4.2):
//   A. Header (статус или дата + бейдж типа)
//   B. Classroom (основной визуальный акцент, 20–26 pt)
//   C. LessonTime (одной строкой, 14–16 pt)
//   D. Subject (максимально полно, свободная высота)

struct SmallScheduleWidgetView: View {
    let state: WidgetPresentationState
    let palette: WidgetPalette
    /// Сокращать названия дисциплин (параметр «Изменить виджет»).
    var abbreviatedSubject: Bool = false

    var body: some View {
        GeometryReader { geo in
            let density = WidgetDensity.forHeight(geo.size.height)
            let typography = WidgetTypography(density: density)
            content(density: density, typography: typography, height: geo.size.height)
                .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
        }
    }

    @ViewBuilder
    private func content(
        density: WidgetDensity,
        typography: WidgetTypography,
        height: CGFloat
    ) -> some View {
        if let lesson = state.primaryLesson, let status = state.primaryStatus {
            VStack(alignment: .leading, spacing: 0) {
                header(lesson: lesson, status: status, density: density, typography: typography)

                // Отступы из ТЗ п. 4.3: 8 после Header, 6 после Classroom,
                // 6 после Time. При нехватке высоты сокращаются первыми.
                Spacer().frame(height: density >= .regular ? Space.s8 : Space.s6)

                ClassroomLabel(
                    lesson: lesson,
                    palette: palette,
                    size: typography.room(20...26),
                    weight: .bold
                )
                .layoutPriority(3)

                Spacer().frame(height: Space.s6)

                LessonTimeLabel(
                    lesson: lesson,
                    palette: palette,
                    size: typography.primaryTime(14...16),
                    weight: .semibold
                )
                .layoutPriority(2)

                Spacer().frame(height: Space.s6)

                // Пункт D: название занимает оставшуюся высоту, но не жадно:
                // `maxHeight: .infinity` здесь съедал место полосы прогресса,
                // и она не отображалась. Теперь остаток забирает Spacer, а
                // прогресс прижат к низу — как просил владелец.
                SubjectWithType(
                    lesson: lesson,
                    palette: palette,
                    size: typography.subject(11...13),
                    lines: subjectLines(density: density, typography: typography, height: height),
                    abbreviatedSubject: abbreviatedSubject
                )
                .layoutPriority(1)

                Spacer(minLength: 0)

                // Полоса прогресса идущей пары внизу: 3 pt, только пока пара
                // идёт. Для будущей пары прогресса нет — полосы не будет.
                if let progress = state.primaryProgress {
                    Spacer().frame(height: Space.s4)
                    LessonProgressBar(progress: progress, palette: palette)
                }
            }
        } else {
            WidgetEmptyStateView(
                reason: state.emptyReason,
                targetTitle: state.targetTitle,
                palette: palette,
                compact: true
            )
        }
    }

    /// Сколько строк дать названию на Small.
    ///
    /// Считается по остатку высоты: шапка ~14, отступы 6+6+6, аудитория 26,
    /// время 16, полоса прогресса 7 (когда пара идёт). Всё, что осталось,
    /// делится на высоту строки названия. Минимум две строки, максимум
    /// четыре — дальше текст становится плотным.
    private func subjectLines(
        density: WidgetDensity,
        typography: WidgetTypography,
        height: CGFloat
    ) -> Int {
        let progressSpace: CGFloat = state.primaryProgress != nil ? 7 : 0
        let chrome: CGFloat = 14 + Space.s6 * 3 + typography.room(20...26)
            + typography.primaryTime(14...16) + progressSpace
        let lineHeight = typography.subject(11...13) * 1.25
        let available = max(0, height - chrome)
        let fits = Int(available / lineHeight)
        return min(4, max(2, fits))
    }

    /// Блок A.
    ///
    /// Для пары другого дня показывается ТОЛЬКО дата (`ПТ, 23 ОКТ`) — без
    /// статуса «ДАЛЕЕ»: оба элемента в узкой шапке не помещаются, а дата
    /// информативнее (ТЗ разд. 1: «предпочтительно один заголовок, без
    /// дополнительной подписи ДАЛЕЕ»).
    /// Для сегодняшней пары — статус. Справа в обоих случаях — кнопка
    /// обновления значком: на Small места на надпись нет (просьба владельца).
    @ViewBuilder
    private func header(
        lesson: WLesson,
        status: LessonStatus,
        density: WidgetDensity,
        typography: WidgetTypography
    ) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Space.s4) {
            if state.primaryDay.isFuture, let dayLabel = state.primaryDay.label {
                // Усадка вместо обрезки: «ПОСЛЕЗАВТРА» больше не выводится,
                // но длина строки всё равно контролируется (дефект из ТЗ п. 4.7).
                Text(dayLabel)
                    .font(.system(size: typography.status(10...11), weight: .semibold))
                    .foregroundColor(palette.activeAccent)
                    .tracking(0.4)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            } else {
                // На сегодняшней паре остаётся только статус: тип идёт перед
                // названием строкой (единый вид во всех виджетах, просьба
                // владельца). Бейдж в шапке дублировал бы его в одном блоке.
                LessonStatusLabel(
                    status: status,
                    palette: palette,
                    size: typography.status(10...11),
                    allowCountdown: false
                )
            }

            Spacer(minLength: Space.s4)

            WidgetRefreshButton(palette: palette, showsTitle: false)
        }
    }
}

// MARK: - Medium: активная сверху, следующие снизу (ТЗ п. 5)
//
// Компоновка ПЕРЕРАБОТАНА по замечанию владельца: горизонтальные зоны давали
// правой колонке слишком мало ширины (аудитория усекалась), а снизу оставалось
// много пустого места. Вертикальная раскладка решает оба дефекта:
//
//   ┌─────────────────────────────────────┐
//   │ СЕЙЧАС                          ПР  │  ← шапка активной пары
//   │ 265 (С-20)          14:20–15:50     │  ← аудитория и время
//   │ Моделирование информационно-…       │  ← название (2–3 строки)
//   │ Иванов И. И.  👥 2 группы           │  ← метаданные
//   │ ────────────────────────────────────│
//   │ ДАЛЕЕ                               │
//   │ 16:20–17:50           341 (С-20)    │  ← следующая пара
//   │ Моделирование ИАС                   │
//   └─────────────────────────────────────┘
//
// Ширина у всех блоков полная, поэтому аудитория не может усечься до «258…»,
// а высота используется целиком: главная пара занимает столько, сколько нужно
// её содержимому, остаток уходит следующей.

struct MediumScheduleWidgetView: View {
    let state: WidgetPresentationState
    let palette: WidgetPalette
    /// Сокращать названия дисциплин (параметр «Изменить виджет»).
    var abbreviatedSubject: Bool = false

    var body: some View {
        GeometryReader { geo in
            let density = WidgetDensity.forHeight(geo.size.height)
            let typography = WidgetTypography(density: density)
            content(density: density, typography: typography, size: geo.size)
                .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
        }
    }

    @ViewBuilder
    private func content(
        density: WidgetDensity,
        typography: WidgetTypography,
        size: CGSize
    ) -> some View {
        if let lesson = state.primaryLesson, let status = state.primaryStatus {
            let rows = upcomingRows(density: density, size: size)
            VStack(alignment: .leading, spacing: 0) {
                primary(
                    lesson: lesson,
                    status: status,
                    density: density,
                    typography: typography,
                    showsMetadata: rows == 0,
                    subjectLines: rows == 0 ? 3 : 2
                )

                if rows > 0 {
                    // Разделитель без отдельного заголовка секции: на Medium
                    // заголовок «ДАЛЕЕ» съедает строку, нужную второй паре.
                    // Подпись идёт инлайном в первой строке следующей пары.
                    Spacer().frame(height: Space.s8)
                    WidgetDivider(palette: palette)
                    Spacer().frame(height: Space.s8)

                    upcoming(
                        lessons: Array(state.upcomingLessons.prefix(rows)),
                        density: density,
                        typography: typography
                    )
                }

                Spacer(minLength: 0)
            }
        } else {
            WidgetEmptyStateView(
                reason: state.emptyReason,
                targetTitle: state.targetTitle,
                palette: palette
            )
        }
    }

    /// Сколько следующих пар реально помещается.
    ///
    /// Владелец просил уместить активную и следующую: бюджет считается от
    /// фактической высоты контента (~123 pt на iPhone) с учётом полосы
    /// прогресса, которая стоит в главной паре. Первая следующая пара
    /// приоритетна; вторая добавляется только если после неё остаётся место.
    private func upcomingRows(density: WidgetDensity, size: CGSize) -> Int {
        let available = state.upcomingLessons.count
        guard available > 0 else { return 0 }

        // Главная пара + полоса прогресса (7 pt), когда пара идёт.
        let primaryHeight = density.primaryBlockHeight
            + (state.primaryProgress != nil ? 7 : 0)
        // Разделитель с отступами; заголовок секции в Medium инлайный.
        let sectionChrome: CGFloat = Space.s8 * 2 + 1
        var budget = size.height - primaryHeight - sectionChrome

        var rows = 0
        while rows < min(available, 2) {
            let cost = density.rowHeight + (rows > 0 ? Space.s6 : 0)
            if budget < cost { break }
            budget -= cost
            rows += 1
        }
        return rows
    }

    // MARK: Главная пара

    @ViewBuilder
    private func primary(
        lesson: WLesson,
        status: LessonStatus,
        density: WidgetDensity,
        typography: WidgetTypography,
        showsMetadata: Bool,
        subjectLines: Int
    ) -> some View {
        VStack(alignment: .leading, spacing: density.lineSpacing) {
            // Строка 1: статус или дата слева, кнопка обновления справа —
            // то самое пустое место вверху, о котором просил владелец.
            HStack(alignment: .firstTextBaseline, spacing: Space.s6) {
                if state.primaryDay.isFuture, let dayLabel = state.primaryDay.label {
                    // Пара не сегодня: дата важнее статуса (ТЗ разд. 1).
                    Text(dayLabel)
                        .font(.system(size: typography.status(10...11), weight: .semibold))
                        .foregroundColor(palette.activeAccent)
                        .tracking(0.4)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                } else {
                    // На Medium отсчёт уместен: место есть, и он не отнимает
                    // у P0-данных.
                    LessonStatusLabel(
                        status: status,
                        palette: palette,
                        size: typography.status(10...11),
                        allowCountdown: true
                    )
                }

                Spacer(minLength: Space.s4)

                WidgetRefreshButton(palette: palette, showsTitle: true)
            }

            // Аудитория и время в одну строку: аудитория слева (главный
            // акцент), время прижато вправо. Полная ширина виджета — поэтому
            // «258а (С-20)» помещается целиком.
            HStack(alignment: .firstTextBaseline, spacing: Space.s8) {
                ClassroomLabel(
                    lesson: lesson,
                    palette: palette,
                    size: typography.room(16...20),
                    weight: .bold
                )
                .layoutPriority(2)
                Spacer(minLength: Space.s4)
                LessonTimeLabel(
                    lesson: lesson,
                    palette: palette,
                    size: typography.primaryTime(12...15),
                    weight: .semibold
                )
            }
            .padding(.top, Space.s2)

            // Название с типом в начале строки.
            SubjectWithType(
                lesson: lesson,
                palette: palette,
                size: typography.subject(11...13),
                lines: subjectLines,
                abbreviatedSubject: abbreviatedSubject
            )
            .padding(.top, Space.s2)
            .layoutPriority(1)

            // Метаданные: преподаватель и индикатор групп. Показываются, когда
            // следующей пары нет и место свободно: две пары важнее
            // преподавателя (приоритет скрытия из ТЗ п. 5.6).
            if showsMetadata {
                metadata(lesson: lesson, density: density, typography: typography)
            }

            // Полоса прогресса идущей пары.
            if let progress = state.primaryProgress {
                Spacer().frame(height: Space.s4)
                LessonProgressBar(progress: progress, palette: palette)
            }
        }
    }

    /// Компактная строка метаданных: `Иванов И. И. · 👥 2 группы`.
    @ViewBuilder
    private func metadata(
        lesson: WLesson,
        density: WidgetDensity,
        typography: WidgetTypography
    ) -> some View {
        let hasTeacher = lesson.teacherDisplay != nil
        let hasGroups = lesson.isShared

        if hasTeacher || hasGroups {
            HStack(spacing: Space.s6) {
                if hasTeacher {
                    TeacherLabel(lesson: lesson, palette: palette, size: typography.teacher)
                }
                if hasTeacher && hasGroups {
                    Text("·")
                        .font(.system(size: typography.teacher))
                        .foregroundColor(palette.mutedText)
                }
                if hasGroups {
                    MultiGroupIndicator(
                        lesson: lesson,
                        palette: palette,
                        size: typography.teacher - 1,
                        showWord: true
                    )
                }
                Spacer(minLength: 0)
            }
            .padding(.top, Space.s2)
        }
    }

    // MARK: Следующие занятия

    @ViewBuilder
    private func upcoming(
        lessons: [WLesson],
        density: WidgetDensity,
        typography: WidgetTypography
    ) -> some View {
        // Заголовок секции не выводится отдельной строкой: на Medium он
        // съедает высоту, нужную второй паре. Подпись «ДАЛЕЕ» идёт инлайном
        // в первой строке первой следующей пары.
        VStack(alignment: .leading, spacing: density.rowSpacing) {
            ForEach(Array(lessons.enumerated()), id: \.element.id) { index, lesson in
                if index > 0 {
                    WidgetDivider(palette: palette)
                }
                UpcomingLessonRow(
                    lesson: lesson,
                    palette: palette,
                    density: density,
                    typography: typography,
                    inlineLabel: index == 0 ? "ДАЛЕЕ" : nil,
                    abbreviatedSubject: abbreviatedSubject
                )
            }
        }
    }
}

// MARK: - Пустые состояния (ТЗ п. 10.4)
//
// «Нет занятий» ≠ «нет данных» ≠ «нет группы». Технические тексты ошибок
// пользователю не показываются, но действие объясняется.

struct WidgetEmptyStateView: View {
    let reason: EmptyReason?
    let targetTitle: String?
    let palette: WidgetPalette
    var compact: Bool = false

    var body: some View {
        VStack(spacing: compact ? Space.s4 : Space.s6) {
            Image(systemName: icon)
                .font(.system(size: compact ? 20 : 24))
                .foregroundColor(iconTint)
            Text(title)
                .font(.system(size: compact ? 13 : 15, weight: .semibold))
                .foregroundColor(palette.subjectText)
                .multilineTextAlignment(.center)
            if let subtitle {
                Text(subtitle)
                    .font(.system(size: compact ? 11 : 12))
                    .foregroundColor(palette.mutedText)
                    .multilineTextAlignment(.center)
                    .lineLimit(compact ? 2 : 3)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title). \(subtitle ?? "")")
    }

    private var icon: String {
        switch reason {
        case .noLessons: return "calendar"
        case .noTarget: return "slider.horizontal.3"
        case .noData: return "calendar.badge.plus"
        case .badData: return "exclamationmark.triangle"
        case .noAppGroup: return "exclamationmark.shield"
        case nil: return "calendar"
        }
    }

    private var iconTint: Color {
        switch reason {
        case .badData, .noAppGroup: return palette.activeAccent
        default: return palette.mutedText
        }
    }

    private var title: String {
        switch reason {
        case .noLessons: return "Нет занятий"
        case .noTarget: return "Выберите группу"
        case .noData: return "Нет данных"
        case .badData: return "Данные не читаются"
        case .noAppGroup: return "Нет доступа к данным"
        case nil: return "Нет занятий"
        }
    }

    private var subtitle: String? {
        switch reason {
        case .noLessons:
            return targetTitle.map { "По расписанию «\($0)»" } ?? "В загруженном расписании"
        case .noTarget:
            return "Долгое нажатие → «Изменить виджет» → Группа"
        case .noData:
            return "Откройте приложение и добавьте расписание"
        case .badData:
            return "Откройте приложение, чтобы обновить данные"
        case .noAppGroup:
            return "Приложение нужно переустановить"
        case nil:
            return nil
        }
    }
}
