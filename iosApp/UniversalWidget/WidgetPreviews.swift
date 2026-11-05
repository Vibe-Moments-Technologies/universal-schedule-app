import SwiftUI
import WidgetKit

// MARK: - Preview (ТЗ п. 19)
//
// Для каждого размера — все обязательные состояния: идёт занятие, перемена,
// завтра, через несколько дней, одна пара, две, много, длинная дисциплина,
// без аудитории, без преподавателя, без данных, устаревшие данные.
//
// Preview использует те же вью и тот же движок, что настоящий виджет, поэтому
// показывает фактическую компоновку, а не нарисованный макет. Размер
// задаётся параметром familyOverride: ключ `widgetFamily` в environment
// доступен только для чтения.

/// Запись шкалы из фикстуры.
private func entry(_ fixture: WidgetPreviewFixture) -> ScheduleEntry {
    ScheduleEntry(
        date: fixture.state.generatedAt,
        state: fixture.state,
        emptyReason: fixture.state.emptyReason,
        targetId: fixture.targetId
    )
}

/// Галерея сценариев для одного размера.
private struct FixtureGallery: View {
    let family: WidgetFamily
    let fixtures: [WidgetPreviewFixture]

    init(family: WidgetFamily, fixtures: [WidgetPreviewFixture] = WidgetPreviewFixtures.all) {
        self.family = family
        self.fixtures = fixtures
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .trailing, spacing: Space.s12) {
                ForEach(fixtures, id: \.name) { fixture in
                    VStack(alignment: .leading, spacing: Space.s4) {
                        Text(fixture.name)
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(.secondary)
                        ScheduleWidgetEntryView(
                            entry: entry(fixture),
                            familyOverride: family
                        )
                        .frame(width: width, height: height)
                        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                    }
                }
            }
            .padding()
        }
    }

    /// Фактические размеры семейств на iPhone (ТЗ п. 19.2: проверять
    /// в реальном масштабе, а не только увеличенным).
    private var width: CGFloat {
        family == .systemSmall ? 170 : 364
    }

    private var height: CGFloat {
        switch family {
        case .systemSmall, .systemMedium: return 170
        default: return 382
        }
    }
}

// MARK: - Все состояния по размерам

#Preview("Small — все состояния") {
    FixtureGallery(family: .systemSmall)
}

#Preview("Medium — все состояния") {
    FixtureGallery(family: .systemMedium)
}

#Preview("Large — все состояния") {
    FixtureGallery(family: .systemLarge)
}

// MARK: - Темы (ТЗ п. 19: Light, Dark)

#Preview("Small — светлая тема") {
    FixtureGallery(family: .systemSmall)
        .preferredColorScheme(.light)
}

#Preview("Medium — светлая тема") {
    FixtureGallery(family: .systemMedium)
        .preferredColorScheme(.light)
}

#Preview("Large — светлая тема") {
    FixtureGallery(family: .systemLarge)
        .preferredColorScheme(.light)
}

#Preview("Medium — тёмная тема") {
    FixtureGallery(family: .systemMedium)
        .preferredColorScheme(.dark)
}

#Preview("Large — тёмная тема") {
    FixtureGallery(family: .systemLarge)
        .preferredColorScheme(.dark)
}

// MARK: - Отдельные обязательные сценарии крупно

#Preview("Small — идёт занятие") {
    ScheduleWidgetEntryView(entry: entry(WidgetPreviewFixtures.current), familyOverride: .systemSmall)
        .frame(width: 170, height: 170)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding()
}

#Preview("Small — длинная дисциплина") {
    ScheduleWidgetEntryView(entry: entry(WidgetPreviewFixtures.longTitle), familyOverride: .systemSmall)
        .frame(width: 170, height: 170)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding()
}

#Preview("Small — завтра") {
    ScheduleWidgetEntryView(entry: entry(WidgetPreviewFixtures.tomorrow), familyOverride: .systemSmall)
        .frame(width: 170, height: 170)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding()
}

#Preview("Medium 1 — преподаватель и две группы") {
    ScheduleWidgetEntryView(entry: entry(WidgetPreviewFixtures.teacherAndGroups), familyOverride: .systemMedium)
        .frame(width: 364, height: 170)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding()
}

#Preview("Medium 2 — активная и одна следующая") {
    ScheduleWidgetEntryView(entry: entry(WidgetPreviewFixtures.primaryPlusOne), familyOverride: .systemMedium)
        .frame(width: 364, height: 170)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding()
}

#Preview("Medium 3 — активная и две следующие") {
    ScheduleWidgetEntryView(entry: entry(WidgetPreviewFixtures.manyLessons), familyOverride: .systemMedium)
        .frame(width: 364, height: 170)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding()
}

#Preview("Medium — две следующие пары (компактно)") {
    ScheduleWidgetEntryView(entry: entry(WidgetPreviewFixtures.twoLessons), familyOverride: .systemMedium)
        .frame(width: 364, height: 170)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding()
}

#Preview("Medium — одна пара (Mode 0)") {
    ScheduleWidgetEntryView(entry: entry(WidgetPreviewFixtures.oneLesson), familyOverride: .systemMedium)
        .frame(width: 364, height: 170)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding()
}

#Preview("Large — следующий учебный день") {
    ScheduleWidgetEntryView(entry: entry(WidgetPreviewFixtures.dayOver), familyOverride: .systemLarge)
        .frame(width: 364, height: 382)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding()
}

#Preview("Large — много пар") {
    ScheduleWidgetEntryView(entry: entry(WidgetPreviewFixtures.manyLessons), familyOverride: .systemLarge)
        .frame(width: 364, height: 382)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding()
}

#Preview("Large — нет аудитории") {
    ScheduleWidgetEntryView(entry: entry(WidgetPreviewFixtures.noRoom), familyOverride: .systemLarge)
        .frame(width: 364, height: 382)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding()
}

#Preview("Large — нет данных") {
    ScheduleWidgetEntryView(entry: entry(WidgetPreviewFixtures.noSchedule), familyOverride: .systemLarge)
        .frame(width: 364, height: 382)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding()
}

// MARK: - iPad (горизонтальная панель)

#Preview("Extra Large — iPad") {
    ScheduleWidgetEntryView(entry: entry(WidgetPreviewFixtures.manyLessons), familyOverride: .systemExtraLarge)
        .frame(width: 628, height: 382)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding()
}
