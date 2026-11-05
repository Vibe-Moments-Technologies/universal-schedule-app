package com.jetbrains.kmpapp.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jetbrains.kmpapp.MainActivity
import com.jetbrains.kmpapp.data.widget.WidgetEmptyReason
import com.jetbrains.kmpapp.data.widget.WidgetLabels
import com.jetbrains.kmpapp.data.widget.WidgetLesson
import com.jetbrains.kmpapp.data.widget.WidgetPayload
import com.jetbrains.kmpapp.data.widget.WidgetPayloadCodec
import com.jetbrains.kmpapp.data.widget.WidgetPinFeedback
import com.jetbrains.kmpapp.data.widget.WidgetSelectionEngine
import com.jetbrains.kmpapp.data.widget.WidgetState
import com.jetbrains.kmpapp.data.widget.WidgetThemeCodec
import com.jetbrains.kmpapp.data.widget.WidgetTypeColor
import com.jetbrains.kmpapp.data.widget.consumePendingWidgetConfig

/**
 * Android-виджет расписания на Glance. Повторяет структуру и правила
 * iOS-виджетов: размеры Small/Medium/Large подбираются по фактической площади,
 * данные — из того же JSON-снапшота, что пишет приложение.
 *
 * Виджеты на Android произвольно меняют размер, поэтому вместо четырёх
 * фиксированных семейств используются три режима компоновки.
 */
class ScheduleWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val raw = ScheduleWidgetStore.read(context)
        val payload: WidgetPayload? = WidgetPayloadCodec.decode(raw)
        // Null-ключ = экземпляр ещё не настроен (важно: Pair(null, false)
        // от getAppWidgetState — валидный объект, «нет конфига» — это
        // именно null-ключ, а не null пары; из-за путаницы с этим
        // внутренний конфигуратор не применялся вовсе — все виджеты
        // жили на дефолте, и нельзя было сделать виджеты двух групп).
        var storedConfig = runCatching {
            val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
            Pair(prefs[WidgetTargetIdKey], prefs[WidgetShortNamesKey] ?: false)
        }.getOrNull()

        // Внутренний конфигуратор (добавление из приложения): незакрытый
        // экземпляр (null-ключ) + персистентный pendingConfig = только
        // что закреплённый. Лаунчер может забиндить виджет ДО или ВМЕСТО
        // success-callback-активности (на некоторых OEM она вовсе не
        // стартует), поэтому применение дублируется здесь — на первом
        // же рендере виджета. Prefs переживают смерть процесса.
        // Одноразовые: следующий виджет из системного флоу не должен
        // получить чужой конфиг.
        if (storedConfig?.first == null) {
            consumePendingWidgetConfig(context)?.let { pending ->
                runCatching {
                    updateAppWidgetState(context, id) { prefs ->
                        prefs[WidgetTargetIdKey] = pending.first
                        prefs[WidgetShortNamesKey] = pending.second
                    }
                }
                storedConfig = pending
                // Для экрана настроек: применение считаем подтверждением —
                // диалог закрепления не был подавлен.
                WidgetPinFeedback.lastConfigureOpenedAtMillis =
                    System.currentTimeMillis()
            }
        }
        // id -1 — опция «По умолчанию» из конфигуратора: движку отдаем null,
        // он сам возьмёт расписание уведомлений (тот же фолбэк, что у iOS).
        val storedTargetId = storedConfig?.first?.takeIf { it != DEFAULT_TARGET_ID }
        val shortNames = storedConfig?.second ?: false
        val state = when {
            raw == null -> WidgetState.empty(WidgetEmptyReason.NO_DATA)
            payload == null -> WidgetState.empty(WidgetEmptyReason.BAD_DATA)
            else -> WidgetSelectionEngine().makeState(payload, storedTargetId)
        }
        val theme = WidgetThemeCodec.decode(ScheduleWidgetStore.readTheme(context))
        val palette = theme?.toWidgetPalette()
            ?: if (context.isNightMode()) DarkWidgetPalette else LightWidgetPalette
        // Пользовательские цвета типов занятий из снапшота: тот же контракт,
        // что у iOS-виджета (ключ — короткий бейдж типа, ARGB обеих схем).
        // Пустая карта — стандартные цвета палитры.
        val typeColors = payload?.typeColorOverrides ?: emptyMap()
        val isDark = context.isNightMode()
        provideContent {
            WidgetRoot(
                state = state,
                palette = palette,
                typeColors = typeColors,
                isDark = isDark,
                shortNames = shortNames
            )
        }
    }
}

/** Выбранное пользователем расписание для конкретного экземпляра виджета. */
internal val WidgetTargetIdKey = intPreferencesKey("target_id")

/** Сокращать названия дисциплин в этом экземпляре виджета. */
internal val WidgetShortNamesKey = booleanPreferencesKey("short_names")

/** id опции «По умолчанию» в конфигураторе (реальных id < 0 не бывает). */
internal const val DEFAULT_TARGET_ID = -1

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()
}

internal fun Context.isNightMode(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

@androidx.compose.runtime.Composable
private fun WidgetRoot(
    state: WidgetState,
    palette: WidgetPalette,
    typeColors: Map<String, WidgetTypeColor> = emptyMap(),
    isDark: Boolean = false,
    shortNames: Boolean = false
) {
    val size = LocalSize.current
    val widthDp = size.width.value
    val heightDp = size.height.value

    val compact = widthDp < 200f || (widthDp < 180f && heightDp < 180f)
    val wideShort = !compact && heightDp < 185f
    val extra = widthDp >= 560f || heightDp >= 400f

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 10.dp else 12.dp
            )
    ) {
        if (state.primary == null) {
            EmptyContent(state, palette)
        } else if (compact) {
            SmallContent(state, palette, typeColors, isDark, shortNames, widthDp, heightDp)
        } else if (wideShort) {
            MediumContent(state, palette, typeColors, isDark, shortNames, heightDp)
        } else {
            LargeContent(state, palette, typeColors, isDark, shortNames, widthDp, heightDp, extra)
        }
    }
}

// ── Small: только активная пара ──────────────────────────────

@androidx.compose.runtime.Composable
private fun SmallContent(
    state: WidgetState,
    palette: WidgetPalette,
    typeColors: Map<String, WidgetTypeColor>,
    isDark: Boolean,
    shortNames: Boolean,
    widthDp: Float,
    heightDp: Float
) {
    val tight = heightDp < 145f
    val primary = state.primary ?: return
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        StatusRow(
            label = state.compactStatusLabel,
            type = primary.type,
            palette = palette,
            typeColors = typeColors,
            isDark = isDark,
            sizeSp = 9
        )
        Spacer(modifier = GlanceModifier.height(if (tight) 4.dp else 8.dp))
        ClassroomLabel(primary, palette, if (tight) 20 else 22)
        Spacer(modifier = GlanceModifier.height(if (tight) 2.dp else 5.dp))
        Label(WidgetLabels.timeRange(primary), palette.primaryText, 13, FontWeight.Medium)
        Spacer(modifier = GlanceModifier.height(if (tight) 3.dp else 6.dp))
        Label(
            text = WidgetLabels.subjectDisplay(primary, shortNames),
            color = palette.subjectText,
            sizeSp = if (tight) 11 else 12,
            weight = FontWeight.Medium,
            maxLines = if (tight) 2 else 3
        )
        val progress = state.progress
        if (state.isCurrent && progress != null && !tight) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            ProgressBar(progress, palette, widthDp - 20f)
        }
    }
}

// ── Medium: активная + 1–2 следующие ────────────────────────

@androidx.compose.runtime.Composable
private fun MediumContent(
    state: WidgetState,
    palette: WidgetPalette,
    typeColors: Map<String, WidgetTypeColor>,
    isDark: Boolean,
    shortNames: Boolean,
    heightDp: Float
) {
    val primary = state.primary ?: return
    val compact = heightDp < 155f
    val maxRows = if (heightDp < 145f) 1 else 2
    val showMeta = !compact && primary.teacher.isNotBlank()

    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            StatusRow(
                label = state.compactStatusLabel,
                type = primary.type,
                palette = palette,
                typeColors = typeColors,
                isDark = isDark,
                sizeSp = 9
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            ClassroomLabel(primary, palette, if (compact) 16 else 18)
            Spacer(modifier = GlanceModifier.height(2.dp))
            Label(
                WidgetLabels.timeRange(primary),
                palette.primaryText,
                if (compact) 11 else 12,
                FontWeight.Medium
            )
            Spacer(modifier = GlanceModifier.height(3.dp))
            Label(
                text = WidgetLabels.subjectDisplay(primary, shortNames),
                color = palette.subjectText,
                sizeSp = if (compact) 10 else 11,
                weight = FontWeight.Medium,
                maxLines = if (compact) 1 else 2
            )
            if (showMeta) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                MetaRow(primary, palette, 9)
            }
        }

        if (state.upcoming.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.width(10.dp))
            VerticalDivider(palette)
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Label("ДАЛЕЕ", palette.mutedText, 9, FontWeight.Medium)
                Spacer(modifier = GlanceModifier.height(4.dp))
                state.upcoming.take(maxRows).forEachIndexed { index, lesson ->
                    if (index > 0) Spacer(modifier = GlanceModifier.height(6.dp))
                    UpcomingRow(lesson, palette, shortNames, compact = true)
                }
            }
        }
    }
}

// ── Large: активная + остаток дня + следующий день ──────────

@androidx.compose.runtime.Composable
private fun LargeContent(
    state: WidgetState,
    palette: WidgetPalette,
    typeColors: Map<String, WidgetTypeColor>,
    isDark: Boolean,
    shortNames: Boolean,
    widthDp: Float,
    heightDp: Float,
    extra: Boolean
) {
    val primary = state.primary ?: return
    val budget = when {
        extra -> 9
        heightDp >= 320f -> 6
        heightDp >= 260f -> 5
        heightDp >= 220f -> 4
        else -> 2
    }

    var remaining = budget
    val todayRows = state.upcoming.take(remaining)
    remaining -= todayRows.size
    val firstNextDay = state.nextDays.firstOrNull()
    val firstNextRows = if (firstNextDay != null && remaining > 0) {
        firstNextDay.lessons.take(minOf(remaining, if (extra) 4 else 3))
    } else {
        emptyList()
    }
    remaining -= firstNextRows.size
    val secondNextDay = state.nextDays.getOrNull(1)
    val secondNextRows = if (
        secondNextDay != null &&
        firstNextRows.size == firstNextDay?.lessons?.size &&
        remaining > 0
    ) {
        secondNextDay.lessons.take(minOf(remaining, 2))
    } else {
        emptyList()
    }

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Label(
            text = state.dayHeader.orEmpty(),
            color = palette.mutedText,
            sizeSp = 10,
            weight = FontWeight.Medium
        )
        Spacer(modifier = GlanceModifier.height(6.dp))

        StatusRow(
            label = state.countdownLabel ?: state.statusLabel.orEmpty(),
            type = primary.type,
            palette = palette,
            typeColors = typeColors,
            isDark = isDark,
            sizeSp = 10
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        ClassroomLabel(primary, palette, 24)
        Spacer(modifier = GlanceModifier.height(2.dp))
        Label(WidgetLabels.timeRange(primary), palette.primaryText, 15, FontWeight.Medium)
        Spacer(modifier = GlanceModifier.height(3.dp))
        Label(
            text = WidgetLabels.subjectDisplay(primary, shortNames),
            color = palette.subjectText,
            sizeSp = 13,
            weight = FontWeight.Medium,
            maxLines = 3
        )
        if (primary.teacher.isNotBlank() || WidgetLabels.groupCount(primary) > 1) {
            Spacer(modifier = GlanceModifier.height(3.dp))
            MetaRow(primary, palette, 10)
        }
        val progress = state.progress
        if (state.isCurrent && progress != null) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            ProgressBar(progress, palette, widthDp - 28f)
        }

        if (todayRows.isNotEmpty() || firstNextRows.isNotEmpty() || secondNextRows.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(9.dp))
            HorizontalDivider(palette)
            Spacer(modifier = GlanceModifier.height(7.dp))
        }

        if (todayRows.isNotEmpty()) {
            Label(
                text = if (state.isToday) "ДАЛЕЕ СЕГОДНЯ" else "ДАЛЕЕ",
                color = palette.mutedText,
                sizeSp = 9,
                weight = FontWeight.Medium
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            todayRows.forEachIndexed { index, lesson ->
                if (index > 0) Spacer(modifier = GlanceModifier.height(6.dp))
                UpcomingRow(lesson, palette, shortNames, compact = false)
            }
        }

        if (firstNextDay != null && firstNextRows.isNotEmpty()) {
            if (todayRows.isNotEmpty()) Spacer(modifier = GlanceModifier.height(8.dp))
            Label(firstNextDay.label, palette.mutedText, 9, FontWeight.Medium)
            Spacer(modifier = GlanceModifier.height(4.dp))
            firstNextRows.forEachIndexed { index, lesson ->
                if (index > 0) Spacer(modifier = GlanceModifier.height(6.dp))
                UpcomingRow(lesson, palette, shortNames, compact = false)
            }
        }

        if (secondNextDay != null && secondNextRows.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(8.dp))
            Label(secondNextDay.label, palette.mutedText, 9, FontWeight.Medium)
            Spacer(modifier = GlanceModifier.height(4.dp))
            secondNextRows.forEachIndexed { index, lesson ->
                if (index > 0) Spacer(modifier = GlanceModifier.height(6.dp))
                UpcomingRow(lesson, palette, shortNames, compact = false)
            }
        }

        val staleHours = state.staleHours
        if (staleHours != null) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Label("Данные устарели · $staleHours ч", palette.mutedText, 9)
        }
    }
}

// ── Пустое состояние ────────────────────────────────────────

@androidx.compose.runtime.Composable
private fun EmptyContent(state: WidgetState, palette: WidgetPalette) {
    val title: String
    val subtitle: String
    when (state.emptyReason) {
        WidgetEmptyReason.NO_DATA -> {
            title = "Нет данных"
            subtitle = "Откройте приложение, чтобы загрузить расписание"
        }
        WidgetEmptyReason.BAD_DATA -> {
            title = "Данные не читаются"
            subtitle = "Откройте приложение и обновите расписание"
        }
        WidgetEmptyReason.NO_TARGET -> {
            title = "Группа не выбрана"
            subtitle = "Сохраните расписание в приложении"
        }
        else -> {
            title = "Занятий нет"
            subtitle = state.targetTitle ?: "В расписании пока пусто"
        }
    }
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Label(
                text = title,
                color = palette.primaryText,
                sizeSp = 13,
                weight = FontWeight.Medium,
                maxLines = 2,
                align = TextAlign.Center,
                modifier = GlanceModifier.fillMaxWidth()
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Label(
                text = subtitle,
                color = palette.mutedText,
                sizeSp = 10,
                maxLines = 3,
                align = TextAlign.Center,
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
    }
}

// ── Общие элементы ──────────────────────────────────────────

@androidx.compose.runtime.Composable
private fun StatusRow(
    label: String,
    type: String,
    palette: WidgetPalette,
    typeColors: Map<String, WidgetTypeColor>,
    isDark: Boolean,
    sizeSp: Int
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Label(
            text = label,
            color = palette.accent,
            sizeSp = sizeSp,
            weight = FontWeight.Medium,
            modifier = GlanceModifier.defaultWeight()
        )
        if (type.isNotBlank()) {
            TypeBadge(type, palette, typeColors, isDark)
        }
    }
}

@androidx.compose.runtime.Composable
private fun TypeBadge(
    type: String,
    palette: WidgetPalette,
    typeColors: Map<String, WidgetTypeColor>,
    isDark: Boolean
) {
    val isPractice = type == "ПР" || type == "ЛАБ" || type == "ЛБ"
    // Пользовательское переопределение цвета типа (снапшот, тот же контракт,
    // что у iOS): ARGB знаковый — маска снимает знак, альфа берётся
    // полупрозрачной подложкой бейджа, как в карточках приложения.
    val overrideColor: Color? = typeColors[type]?.let { override ->
        val argb = (if (isDark) override.dark else override.light).toLong() and 0xFFFFFF
        Color(argb)
    }
    val textColor: ColorProvider = when {
        overrideColor != null -> ColorProvider(overrideColor)
        isPractice -> palette.badgeText
        else -> palette.accent
    }
    val badgeBackground: ColorProvider = when {
        overrideColor != null -> ColorProvider(
            overrideColor.copy(alpha = if (isDark) 0.22f else 0.16f)
        )
        else -> palette.badgeBackground
    }
    Box(
        modifier = GlanceModifier
            .background(badgeBackground)
            .cornerRadius(6.dp)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Label(
            text = type,
            color = textColor,
            sizeSp = 9,
            weight = FontWeight.Medium
        )
    }
}

@androidx.compose.runtime.Composable
private fun ClassroomLabel(lesson: WidgetLesson, palette: WidgetPalette, sizeSp: Int) {
    val display = WidgetLabels.roomDisplay(lesson)
    val (number, building) = WidgetLabels.splitClassroom(display)
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        Label(
            text = number,
            color = palette.primaryText,
            sizeSp = sizeSp,
            weight = FontWeight.Bold
        )
        if (building != null) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            Label(
                text = "($building)",
                color = palette.secondaryText,
                sizeSp = (sizeSp * 0.62f).toInt().coerceAtLeast(9),
                weight = FontWeight.Medium
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun MetaRow(lesson: WidgetLesson, palette: WidgetPalette, sizeSp: Int) {
    val teacher = WidgetLabels.teacherDisplay(lesson)
    val groups = WidgetLabels.groupCount(lesson)
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        if (teacher != null) {
            Label(
                text = teacher,
                color = palette.secondaryText,
                sizeSp = sizeSp,
                maxLines = 1
            )
        }
        if (teacher != null && groups > 1) {
            Spacer(modifier = GlanceModifier.width(6.dp))
        }
        if (groups > 1) {
            Label(WidgetLabels.groupsLabel(groups), palette.mutedText, sizeSp)
        }
    }
}

@androidx.compose.runtime.Composable
private fun UpcomingRow(
    lesson: WidgetLesson,
    palette: WidgetPalette,
    shortNames: Boolean,
    compact: Boolean
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Label(
                text = WidgetLabels.roomDisplay(lesson),
                color = palette.primaryText,
                sizeSp = if (compact) 10 else 11,
                weight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Label(
                text = WidgetLabels.timeRange(lesson),
                color = palette.secondaryText,
                sizeSp = if (compact) 9 else 10,
                weight = FontWeight.Medium
            )
        }
        Label(
            text = WidgetLabels.subjectDisplay(lesson, shortNames),
            color = palette.subjectText,
            sizeSp = if (compact) 9 else 10,
            maxLines = 1
        )
    }
}

@androidx.compose.runtime.Composable
private fun ProgressBar(progress: Float, palette: WidgetPalette, widthDp: Float) {
    val filledDp = (widthDp.coerceAtLeast(0f) * progress.coerceIn(0f, 1f))
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(3.dp)
            .cornerRadius(2.dp)
            .background(palette.divider)
    ) {
        if (filledDp > 0f) {
            Box(
                modifier = GlanceModifier
                    .width(filledDp.dp)
                    .fillMaxHeight()
                    .cornerRadius(2.dp)
                    .background(palette.accent)
            ) {}
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
    }
}

@androidx.compose.runtime.Composable
private fun HorizontalDivider(palette: WidgetPalette) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.divider)
    ) {}
}

@androidx.compose.runtime.Composable
private fun VerticalDivider(palette: WidgetPalette) {
    Box(
        modifier = GlanceModifier
            .width(1.dp)
            .fillMaxHeight()
            .background(palette.divider)
    ) {}
}

@androidx.compose.runtime.Composable
private fun Label(
    text: String,
    color: ColorProvider,
    sizeSp: Int,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    modifier: GlanceModifier = GlanceModifier
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = color,
            fontSize = sizeSp.sp,
            fontWeight = weight,
            textAlign = align
        )
    )
}

