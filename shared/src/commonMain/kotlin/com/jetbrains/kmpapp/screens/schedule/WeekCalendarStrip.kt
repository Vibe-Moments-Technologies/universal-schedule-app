package com.jetbrains.kmpapp.screens.schedule
import com.jetbrains.kmpapp.theme.GlitchText

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.CalendarExpandState
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.screens.components.getLessonDotColor
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape

private const val BASE_PAGE = 1000

/** Порог вертикального свайпа-ступени, dp→px (issue #3: жест раскрытия). */
private const val EXPAND_SWIPE_THRESHOLD_PX = 44f

@Composable
fun WeekCalendarStrip(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    lessonSummaries: Map<LocalDate, DayLessonSummary> = emptyMap(),
    onTitleClick: () -> Unit = {},
    onCollapse: () -> Unit = {},
    swipeCollapseEnabled: Boolean = false,
    expandState: CalendarExpandState = CalendarExpandState.WEEK,
    onExpandStateChange: (CalendarExpandState) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val today = DateUtils.today()
    val baseMonday = DateUtils.getWeekDates(today).first()

    val selectedMonday = DateUtils.getWeekDates(selectedDate).first()
    val weeksOffset = (baseMonday.daysUntil(selectedMonday) / 7)
    val targetPage = BASE_PAGE + weeksOffset

    val pagerState = rememberPagerState(
        initialPage = targetPage,
        pageCount = { 2000 }
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedMonday) {
        val currentMonday = baseMonday.plus(DatePeriod(days = (pagerState.currentPage - BASE_PAGE) * 7))
        if (currentMonday != selectedMonday && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(
                targetPage,
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            )
        }
    }

    // Active displayed week Monday
    val currentWeekMonday = baseMonday.plus(DatePeriod(days = (pagerState.currentPage - BASE_PAGE) * 7))
    val currentWeekEnd = currentWeekMonday.plus(DatePeriod(days = 7))
    val todayInCurrentWeek = today >= currentWeekMonday && today < currentWeekEnd
    val weekInfo = DateUtils.getWeekInfo(currentWeekMonday)
    // Месяц в заголовке — от ВЫБРАННОГО дня, не от понедельника недели
    // (репорт владельца: пограничная неделя 27 окт–1 ноя показывала
    // «Октябрь», хотя пользователь на Вс=1 ноября). Выбран день другой
    // недели (листнули, но не тапнули) — берём середину видимой недели,
    // чтобы заголовок следовал за листанием.
    val headerDate = if (selectedDate >= currentWeekMonday && selectedDate < currentWeekEnd) {
        selectedDate
    } else {
        currentWeekMonday.plus(DatePeriod(days = 3))
    }
    val monthTitle = DateUtils.formatMonthTitle(headerDate.month)
    val year = headerDate.year

    // Состояние и колбэки жеста обязаны читаться через rememberUpdatedState:
    // pointerInput запускается ОДИН раз, а состояние меняется (неделя → две
    // недели). Без этого жест навсегда запоминает значения первой композиции —
    // после раскрытия пересчитывает те же ступени и «перестаёт реагировать»
    // (баг с устройства владельца: оживает только после пересоздания экрана).
    // Лямбды ниже читают эти делегаты, поэтому pointerInput всегда видит
    // актуальные значения, хотя сам блок перезапускаться не должен.
    val currentOnCollapse by rememberUpdatedState(onCollapse)
    val currentSwipeCollapseEnabled by rememberUpdatedState(swipeCollapseEnabled)
    val currentExpandState by rememberUpdatedState(expandState)
    val currentOnExpandStateChange by rememberUpdatedState(onExpandStateChange)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Жест раскрытия/сворачивания живёт на всей области календарика
            // (issue #3, решение владельца). Полоса-делитель 14dp удалена:
            // она занимала место ради одного жеста, теперь он распределён
            // по всей ленте.
            //
            // Условия бесконтекстности (требование владельца: «всё остальное
            // нужно пропускать»):
            // — тапы не затрагиваются: жест реагирует только на смещение
            //   больше порога, короткое нажатие до порога — не наш случай,
            //   событие уходит кликабельным детям;
            // — горизонтальный пейджер не затрагивается: порог направления
            //   требует вертикального доминирования (|dy| > 1.5|dx|), иначе
            //   жест отменяется и события не потребляются вовсе;
            // — событие потребляется только когда жест признан нашим, после
            //   чего клики/пейджер его уже не увидят.
            .calendarExpandGesture(
                onSwipeDown = {
                    val next = currentExpandState.onSwipeDown()
                    if (next != currentExpandState) currentOnExpandStateChange(next)
                },
                onSwipeUp = {
                    val next = currentExpandState.onSwipeUp()
                    if (next != currentExpandState) {
                        currentOnExpandStateChange(next)
                    } else if (currentExpandState == CalendarExpandState.WEEK && currentSwipeCollapseEnabled) {
                        // Дальше сворачивать некуда: в WEEK свайп вверх
                        // сворачивает ленту целиком — как раньше делала
                        // 14dp-полоса, только с большей области захвата.
                        currentOnCollapse()
                    }
                }
            )
    ) {
        // Month, Year, Week bar with navigation arrows & Сегодня button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        if (pagerState.currentPage > 0) {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Предыдущая неделя",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                GlitchText(
                    text = "$monthTitle $year • ${weekInfo.weekNumber} неделя",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onTitleClick() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )

                if (!todayInCurrentWeek) {
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable {
                            scope.launch {
                                pagerState.animateScrollToPage(BASE_PAGE)
                            }
                            onDateSelected(today)
                        }
                    ) {
                        GlitchText(
                            text = "Сегодня",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    scope.launch {
                        if (pagerState.currentPage < 1999) {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Следующая неделя",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Week Days Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val pageMonday = baseMonday.plus(DatePeriod(days = (page - BASE_PAGE) * 7))
            val weekDates = (0..6).map { pageMonday.plus(DatePeriod(days = it)) }

            // Раскрытие в две недели (issue #3): автору важно видеть чётную и
            // нечётную недели рядом. Вторая строка — соседняя неделя, чтобы
            // чётная/нечётная всегда были в паре.
            val secondRowDates = if (expandState == CalendarExpandState.TWO_WEEKS) {
                val secondMonday = pageMonday.plus(DatePeriod(days = 7))
                (0..6).map { secondMonday.plus(DatePeriod(days = it)) }
            } else {
                null
            }

            Column {
                WeekRow(
                    weekDates = weekDates,
                    selectedDate = selectedDate,
                    today = today,
                    lessonSummaries = lessonSummaries,
                    onDateSelected = onDateSelected
                )

                if (secondRowDates != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    WeekRow(
                        weekDates = secondRowDates,
                        selectedDate = selectedDate,
                        today = today,
                        lessonSummaries = lessonSummaries,
                        onDateSelected = onDateSelected,
                        weekParityLabel = parityLabel(secondRowDates.first())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Разделитель — просто разделитель, без зоны жеста.
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            thickness = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    }
}

/** Подпись чётности недели для второй строки двухнедельного режима. */
private fun parityLabel(monday: LocalDate): String {
    val info = DateUtils.getWeekInfo(monday)
    return if (info.isEven) "чётная" else "нечётная"
}

/** Одна строка дней недели (общая для одно- и двухнедельного режимов). */
@Composable
private fun WeekRow(
    weekDates: List<LocalDate>,
    selectedDate: LocalDate,
    today: LocalDate,
    lessonSummaries: Map<LocalDate, DayLessonSummary>,
    onDateSelected: (LocalDate) -> Unit,
    weekParityLabel: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()

        weekDates.forEach { date ->
            val isSelected = date == selectedDate
            val isToday = date == today
            val summary = lessonSummaries[date]
            val lessonTypes = summary?.lessonTypes ?: emptyList()
            val row1 = lessonTypes.take(5)
            val row2 = lessonTypes.drop(5).take(5)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDateSelected(date) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                GlitchText(
                    text = DateUtils.formatDayOfWeekShort(date.dayOfWeek),
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Medium,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(modifier = Modifier.height(3.dp))

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .then(
                            when {
                                isSelected -> Modifier.background(MaterialTheme.colorScheme.primary)
                                isToday -> Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else -> Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    GlitchText(
                        text = date.day.toString(),
                        fontSize = 14.5.sp,
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.SemiBold,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Colored indicator dots matching lesson cards (1 dot per lesson)
                // Centered horizontally, wrap to row 2 if > 5 dots
                Column(
                    modifier = Modifier.height(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (row1.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.5.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row1.forEach { lessonType ->
                                Box(
                                    modifier = Modifier
                                        .size(4.5.dp)
                                        .clip(CircleShape)
                                        .background(getLessonDotColor(lessonType, isDark))
                                )
                            }
                        }
                    }
                    if (row2.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.5.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.5.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row2.forEach { lessonType ->
                                Box(
                                    modifier = Modifier
                                        .size(4.5.dp)
                                        .clip(CircleShape)
                                        .background(getLessonDotColor(lessonType, isDark))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Модификатор жеста раскрытия/сворачивания календарика (issue #3).
 *
 * Требование владельца: область захвата — весь календарик, сама область
 * не красится (модификатор не рисует ничего), а все чужие клики и жесты
 * должны продолжать работать.
 *
 * Как достигается:
 * 1. Первое нажатие наблюдается, но НЕ потребляется: клики по дням,
 *    «Сегодня», стрелкам и заголовку работают как раньше.
 * 2. Дальше отслеживается движение. Жест признаётся нашим только если
 *    вертикальное смещение превысило порог И вертикаль доминирует над
 *    горизонталью (иначе это горизонтальный свайп пейджера недели — не ours).
 * 3. До признания события не потребляются вообще: пейджер скроллится,
 *    тап-детекторы ждут своего. После признания — последующие события
 *    потребляются, чтобы список под лентой не дёрнулся.
 * 4. Один жест — одна ступень: порог сбрасывается после срабатывания,
 *    продолжение движения без отрыва пальца ничего не меняет.
 */
private fun Modifier.calendarExpandGesture(
    onSwipeDown: () -> Unit,
    onSwipeUp: () -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var handled = false
        var done = false
        var dy = 0f
        var dx = 0f
        while (!done) {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isConsumed }) {
                // Чужой жест уже в работе (например, пейджер недели
                // перехватил горизонтальный свайп): выходим, не мешаем.
                done = true
                continue
            }
            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
            dx += change.positionChange().x
            dy += change.positionChange().y
            if (!handled) {
                val verticalDominates = kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f
                if (verticalDominates && kotlin.math.abs(dy) > EXPAND_SWIPE_THRESHOLD_PX) {
                    // Ось Y в Compose растёт вниз: dy > 0 — свайп вниз
                    // (раскрытие), dy < 0 — вверх (сворачивание).
                    if (dy > 0) onSwipeDown() else onSwipeUp()
                    handled = true
                }
            }
            if (handled) {
                // Наш жест: после срабатывания потребляем перемещения,
                // чтобы ниже лежащие слои (список пар) не скроллились.
                event.changes.forEach { it.consume() }
            }
            if (event.changes.all { !it.pressed }) done = true
        }
    }
}
