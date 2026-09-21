package com.jetbrains.kmpapp.screens.schedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.data.model.Lesson
import com.jetbrains.kmpapp.data.model.ScheduleSlot
import com.jetbrains.kmpapp.data.model.ScheduleTargetType
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
internal fun DaySchedulePage(
    date: LocalDate,
    slots: List<ScheduleSlot>,
    listState: LazyListState,
    errorMessage: String?,
    currentMinutesState: State<Int>?,
    showLessonProgress: Boolean,
    showEmptyLessonProgress: Boolean,
    showBreakProgress: Boolean,
    showAbbreviatedNames: Boolean,
    scheduleTargetType: ScheduleTargetType,
    autoScrollToCurrentLesson: Boolean,
    canAutoScroll: (LocalDate) -> Boolean,
    markAutoScrolled: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onLessonClick: (Lesson) -> Unit,
    modifier: Modifier = Modifier
) {
    // Ключи — только дата и настройка: список slots пересоздаётся при каждой
    // пересборке (в т.ч. когда поверх открывается подстраница), и по нему в
    // ключах эффект перезапускался, подбрасывая день к первой паре.
    // Один автоскролл на дату уже гарантирует canAutoScroll/markAutoScrolled.
    LaunchedEffect(date, autoScrollToCurrentLesson) {
        if (date != com.jetbrains.kmpapp.data.model.DateUtils.today() ||
            !autoScrollToCurrentLesson || !canAutoScroll(date) || slots.isEmpty()
        ) return@LaunchedEffect

        data class DisplayItem(
            val index: Int,
            val isBreak: Boolean,
            val slot: ScheduleSlot?,
            val start: Int,
            val end: Int,
            val nextIsActive: Boolean
        )

        val displayItems = mutableListOf<DisplayItem>()
        slots.forEachIndexed { index, slot ->
            if (index > 0) {
                val previous = slots[index - 1]
                val breakMinutes = com.jetbrains.kmpapp.data.model.calculateBreakMinutes(previous.endTime, slot.startTime)
                if (breakMinutes > 0) {
                    displayItems += DisplayItem(
                        index = displayItems.size,
                        isBreak = true,
                        slot = null,
                        start = com.jetbrains.kmpapp.data.model.DateUtils.parseTimeToMinutes(previous.endTime) ?: 0,
                        end = com.jetbrains.kmpapp.data.model.DateUtils.parseTimeToMinutes(slot.startTime) ?: 0,
                        nextIsActive = slot is ScheduleSlot.Active
                    )
                }
            }
            displayItems += DisplayItem(
                index = displayItems.size,
                isBreak = false,
                slot = slot,
                start = com.jetbrains.kmpapp.data.model.DateUtils.parseTimeToMinutes(slot.startTime) ?: 0,
                end = com.jetbrains.kmpapp.data.model.DateUtils.parseTimeToMinutes(slot.endTime) ?: 0,
                nextIsActive = false
            )
        }

        val now = com.jetbrains.kmpapp.data.model.DateUtils.currentTimeMinutes()
        val active = displayItems.firstOrNull {
            !it.isBreak && it.slot is ScheduleSlot.Active && now in it.start until it.end
        }
        val ongoingBreak = displayItems.firstOrNull {
            it.isBreak && it.nextIsActive && now in it.start until it.end
        }
        val upcoming = displayItems.firstOrNull {
            !it.isBreak && it.slot is ScheduleSlot.Active && now < it.start
        }
        val fallback = displayItems.firstOrNull {
            !it.isBreak && (now in it.start until it.end || now < it.start)
        }
        val target = active ?: ongoingBreak ?: upcoming?.let {
            val previous = if (it.index > 0) displayItems[it.index - 1] else null
            if (previous?.isBreak == true && now >= previous.start) previous else it
        } ?: fallback

        if (target == null || target.index == 0) {
            if (target != null) markAutoScrolled(date)
            return@LaunchedEffect
        }

        var scrolled = false
        repeat(4) {
            if (!scrolled) {
                val layoutReady = withTimeoutOrNull(800) {
                    snapshotFlow { listState.layoutInfo.totalItemsCount }
                        .filter { it > target.index }
                        .first()
                    true
                } == true
                if (layoutReady) {
                    kotlinx.coroutines.delay(100)
                    listState.animateScrollToItem(target.index)
                    scrolled = true
                } else {
                    kotlinx.coroutines.delay(100)
                }
            }
        }
        if (scrolled) markAutoScrolled(date)
    }

    if (slots.isEmpty()) {
        // Тап по хлопушке — конфетти, как в Telegram.
        var burst by remember { mutableIntStateOf(0) }
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "🎉",
                    fontSize = 48.sp,
                    // Без ripple: квадратная анимация нажатия портила эмодзи.
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { burst++ }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("На этот день пар нет", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Отличный повод отдохнуть!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, textAlign = TextAlign.Center)
                    IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = "Повторить") }
                }
            }
            ConfettiBurst(trigger = burst)
        }
        return
    }

    LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)) {
        slots.forEachIndexed { index, slot ->
            if (index > 0) {
                val previous = slots[index - 1]
                val breakMinutes = com.jetbrains.kmpapp.data.model.calculateBreakMinutes(previous.endTime, slot.startTime)
                if (breakMinutes > 0) {
                    item(key = "break_${previous.bellNumber}_${slot.bellNumber}") {
                        LessonBreakIndicator(
                            breakMinutes = breakMinutes,
                            breakStartTime = previous.endTime,
                            breakEndTime = slot.startTime,
                            isToday = date == com.jetbrains.kmpapp.data.model.DateUtils.today(),
                            currentMinutesState = currentMinutesState,
                            showBreakProgress = showBreakProgress
                        )
                    }
                }
            }
            val slotKey = when (slot) {
                is ScheduleSlot.Active -> "active_${slot.bellNumber}_${slot.lessons.firstOrNull()?.id}"
                is ScheduleSlot.Empty -> "empty_${slot.bellNumber}"
            }
            item(key = slotKey) {
                ScheduleSlotCard(
                    slot = slot,
                    onLessonClick = onLessonClick,
                    isToday = date == com.jetbrains.kmpapp.data.model.DateUtils.today(),
                    currentMinutesState = currentMinutesState,
                    showLessonProgress = showLessonProgress,
                    showEmptyLessonProgress = showEmptyLessonProgress,
                    showAbbreviatedNames = showAbbreviatedNames,
                    scheduleTargetType = scheduleTargetType
                )
            }
        }
    }
}

private class ConfettiParticle(
    val vx: Float,
    val vy: Float,
    val color: Color,
    val sizePx: Float,
    val rotation: Float,
    val spin: Float
)

private val CONFETTI_COLORS = listOf(
    Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFFFDE047),
    Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFFEC4899), Color(0xFF8B5CF6)
)

/**
 * Взрыв конфетти по тапу на эмодзи — как реакция в Telegram: частицы
 * разлетаются из центра во все стороны, тормозятся гравитацией и гаснут.
 * Каждый тап (`trigger++`) — новый взрыв с новой раскладкой частиц.
 */
@Composable
private fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    // Частицы фиксируются на тап — в кадре меняется только прогресс.
    val particles = remember(trigger) {
        if (trigger == 0) emptyList()
        else List(64) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 0.30f + Random.nextFloat() * 0.65f
            ConfettiParticle(
                vx = cos(angle) * speed,
                vy = sin(angle) * speed * 0.75f - 0.45f,
                color = CONFETTI_COLORS[Random.nextInt(CONFETTI_COLORS.size)],
                sizePx = 4f + Random.nextFloat() * 5f,
                rotation = Random.nextFloat() * 360f,
                spin = (Random.nextFloat() - 0.5f) * 640f
            )
        }
    }
    val progress = remember(trigger) { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(1100, easing = LinearEasing))
        }
    }
    if (trigger == 0) return
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            val px = w / 2f + p.vx * w * t
            // Баллистика: равномерный разлёт + гравитационный прогиб.
            val py = h / 2f + p.vy * h * 0.55f * t + 1.2f * t * t * h * 0.28f
            drawContext.canvas.save()
            // Пивот — через translate: у Canvas в этой версии rotate
            // принимает только угол, а вариант с опорной точкой не
            // переносим между версиями библиотеки.
            drawContext.canvas.translate(px, py)
            drawContext.canvas.rotate(p.rotation + p.spin * t)
            drawRect(
                color = p.color,
                topLeft = Offset(-p.sizePx / 2f, -p.sizePx * 0.31f),
                size = Size(p.sizePx, p.sizePx * 0.62f),
                alpha = (1f - t * t).coerceIn(0f, 1f)
            )
            drawContext.canvas.restore()
        }
    }
}
