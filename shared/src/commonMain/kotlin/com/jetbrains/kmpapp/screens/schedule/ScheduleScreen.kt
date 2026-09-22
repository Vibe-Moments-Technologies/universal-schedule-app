package com.jetbrains.kmpapp.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.screens.components.LayeredNavHost
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onOpenConfigurator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selectedLessonForDetail by viewModel.selectedLessonForDetail.collectAsState()

    LayeredNavHost(
        screen = selectedLessonForDetail,
        parentScreen = null,
        onBackToParent = { viewModel.selectLessonForDetail(null) },
        // Возврат из другой вкладки с открытым детальным экраном — без въезда.
        initiallyRevealed = remember { selectedLessonForDetail != null },
        rootContent = { ScheduleMainContent(viewModel = viewModel, onOpenConfigurator = onOpenConfigurator) },
        screenContent = { detailLesson, back ->
            LessonDetailScreen(
                lesson = detailLesson as Lesson,
                targetId = viewModel.selectedTargetId,
                onBack = back
            )
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleMainContent(
    viewModel: ScheduleViewModel,
    onOpenConfigurator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semester by viewModel.semester.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val currentLessons by viewModel.currentLessons.collectAsState()
    val showEmptyLessons by viewModel.showEmptyLessons.collectAsState()
    val dayLessonSummaries by viewModel.dayLessonSummaries.collectAsState()
    val showLessonProgress by viewModel.showLessonProgress.collectAsState()
    val showEmptyLessonProgress by viewModel.showEmptyLessonProgress.collectAsState()
    val showBreakProgress by viewModel.showBreakProgress.collectAsState()
    val calendarCollapsed by viewModel.calendarCollapsed.collectAsState()
    val autoScrollToCurrentLesson by viewModel.autoScrollToCurrentLesson.collectAsState()
    val showAbbreviatedNames by viewModel.showAbbreviatedNames.collectAsState()
    // Значение НЕ читаем здесь: тик раз в 30 секунд не должен пересобирать
    // всё дерево расписания. State уходит вниз и читается только в карточках
    // «сегодня» (см. LessonCard).
    val currentMinutesState = viewModel.currentMinutes.collectAsState()

    // Месячный календарь: по тапу на «Сентябрь 2026 • N неделя» или долгому
    // нажатию на кружок дня в топбаре (когда лента свёрнута).
    var showMonthPicker by remember { mutableStateOf(false) }

    val today = com.jetbrains.kmpapp.data.model.DateUtils.today()
    val basePage = 1000
    val selectedPage = basePage + today.daysUntil(selectedDate)
    val pagerState = rememberPagerState(initialPage = selectedPage, pageCount = { 2001 })

    LaunchedEffect(selectedDate) {
        val targetPage = basePage + today.daysUntil(selectedDate)
        if (pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
            // Дальние дни — мгновенно (иначе долгий «полёт» через страницы),
            // соседний день — обычная анимация.
            if (abs(targetPage - pagerState.currentPage) > 1) {
                pagerState.scrollToPage(targetPage)
            } else {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    LaunchedEffect(pagerState) {
        // currentPage, а не settledPage: дата обновляется в момент переворота
        // страницы (пока палец ещё на экране), лента календаря реагирует сразу.
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val date = today.plus(DatePeriod(days = page - basePage))
            if (date != selectedDate) viewModel.selectDate(date)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (semester == null) {
                    // Локальное расписание ещё не собрано — зовём в конфигуратор.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Construction,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Расписание не собрано",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Соберите семестр в конфигураторе: это работает без интернета и подходит любому учебному заведению",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onOpenConfigurator,
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                            ) {
                                Text("Открыть конфигуратор", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Week calendar strip with navigation bar
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !calendarCollapsed,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                    ) {
                        WeekCalendarStrip(
                            selectedDate = selectedDate,
                            onDateSelected = { viewModel.selectDate(it) },
                            lessonSummaries = dayLessonSummaries,
                            onTitleClick = { showMonthPicker = true },
                            onCollapse = { viewModel.setCalendarCollapsed(true) },
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(top = 4.dp, bottom = 2.dp)
                        )
                    }

                    // Календарь свёрнут: компактный кружок текущего дня вместо
                    // шапки (тап — развернуть ленту, долгое нажатие — месяц).
                    androidx.compose.animation.AnimatedVisibility(
                        visible = calendarCollapsed,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { viewModel.setCalendarCollapsed(false) },
                                            onLongPress = { showMonthPicker = true }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = selectedDate.day.toString(),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val pageDate = today.plus(DatePeriod(days = page - basePage))
                        val pageSlots = viewModel.slotsForDate(pageDate, currentLessons, showEmptyLessons)
                        key(pageDate) {
                            // Стартуем с сохранённой позиции дня и пишем её
                            // обратно: открытие подстраницы больше не сбрасывает
                            // день к первой паре.
                            val pageListState = remember(pageDate) {
                                LazyListState(
                                    firstVisibleItemIndex = viewModel.scrollPositionFor(pageDate)
                                )
                            }
                            LaunchedEffect(pageListState) {
                                snapshotFlow { pageListState.firstVisibleItemIndex }
                                    .collect { viewModel.saveScrollPosition(pageDate, it) }
                            }
                            DaySchedulePage(
                                date = pageDate,
                                slots = pageSlots,
                                listState = pageListState,
                                currentMinutesState = currentMinutesState,
                                showLessonProgress = showLessonProgress,
                                showEmptyLessonProgress = showEmptyLessonProgress,
                                showBreakProgress = showBreakProgress,
                                showAbbreviatedNames = showAbbreviatedNames,
                                autoScrollToCurrentLesson = autoScrollToCurrentLesson,
                                canAutoScroll = { viewModel.canAutoScroll(it, viewModel.selectedTargetId) },
                                markAutoScrolled = { viewModel.markAutoScrolled(it, viewModel.selectedTargetId) },
                                onLessonClick = { viewModel.selectLessonForDetail(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Месячный календарь — оверлей поверх всего экрана (НЕ Dialog:
            // диалоговые окна Compose на iOS падали при смене месяца).
            if (showMonthPicker) {
                com.jetbrains.kmpapp.screens.components.MonthPickerOverlay(
                    initialDate = selectedDate,
                    lessonSummaries = dayLessonSummaries,
                    onDatePicked = { viewModel.selectDate(it) },
                    onDismiss = { showMonthPicker = false }
                )
            }
        }
    }
}
