package com.jetbrains.kmpapp.screens.games
import com.jetbrains.kmpapp.theme.GlitchText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.theme.GlitchTitle
import com.jetbrains.kmpapp.theme.MonoTitle
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

/**
 * Игра «Сапер». Поле рисуется одним Canvas: тап открывает клетку (а по
 * раскрытой цифре, вокруг которой собраны все флаги, — открывает остаток
 * области), долгое нажатие ставит флаг сразу при удержании. Щипок
 * масштабирует поле, перетаскивание двигает его по экрану; кнопка в шапке
 * возвращает исходный размер.
 */
@Composable
fun MinesweeperScreen(
    viewModel: GamesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val board by viewModel.board.collectAsState()
    val status by viewModel.status.collectAsState()
    val elapsed by viewModel.elapsedSeconds.collectAsState()
    val flagMode by viewModel.flagMode.collectAsState()
    val difficulty by viewModel.difficulty.collectAsState()
    val boomIndex by viewModel.boomIndex.collectAsState()
    val errorTheme by viewModel.errorActive.collectAsState()
    val verticalBoard by viewModel.boardVertical.collectAsState()
    val smallCellHintEnabled by viewModel.smallCellHintEnabled.collectAsState()

    // Эффективные размеры поля с учётом настройки «вертикальная доска».
    // До первого хода доски нет — берём размеры варианта; после — фактические
    // ширину/высоту доски (у сохранённой партии свои).
    val fieldWidth = board?.width ?: if (verticalBoard) difficulty.height else difficulty.width
    val fieldHeight = board?.height ?: if (verticalBoard) difficulty.width else difficulty.height

    // Отдельный тик для возврата к исходному масштабу: кнопка живёт в шапке
    // (слева от перезапуска), а состояние масштаба — внутри поля.
    var zoomResetTick by remember { mutableStateOf(0L) }

    // Тема Error глобальна: когда вариант «сломан» (рекорд 0 с), App.kt
    // подставляет ErrorColors и моноширинную типографику во всё приложение.
    // Здесь кастомная схема не нужна — берём текущую из MaterialTheme.
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
            // Свободное место под плавающую панель страниц.
            .padding(bottom = 110.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "В меню игр"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                if (errorTheme) {
                    GlitchTitle(
                        text = "ERROR",
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    MonoTitle(
                        text = "Сапер",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        glitch = errorTheme
                    )
                }
                GlitchText(
                    text = "$fieldWidth×$fieldHeight · ${difficulty.mines} мин",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { zoomResetTick++ }) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = "Вернуть исходный размер"
                    )
                }
                IconButton(onClick = { viewModel.restart() }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Новая партия"
                    )
                }
            }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "Мины",
                value = (board?.remainingMines ?: difficulty.mines).toString(),
                scheme = scheme,
                glitch = errorTheme,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Время",
                value = formatSeconds(elapsed),
                scheme = scheme,
                glitch = errorTheme,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            MinesweeperField(
                width = fieldWidth,
                height = fieldHeight,
                board = board,
                status = status,
                boomIndex = boomIndex,
                scheme = scheme,
                errorTheme = errorTheme,
                zoomResetRequest = zoomResetTick,
                smallCellHintEnabled = smallCellHintEnabled,
                onCellClick = { viewModel.onCellClick(it) },
                onCellLongClick = { viewModel.onCellLongClick(it) },
                onCellDoubleClick = { viewModel.onCellDoubleClick(it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (status) {
            MinesweeperStatus.WON -> ResultCard(
                title = "Победа!",
                subtitle = "Все мины найдены за ${formatSeconds(elapsed)}.",
                primaryLabel = "Ещё партия",
                onPrimary = { viewModel.restart() },
                onMenu = onBack,
                scheme = scheme,
                glitch = errorTheme
            )
            MinesweeperStatus.LOST -> ResultCard(
                title = "Мина!",
                subtitle = "Раскрыто за ${formatSeconds(elapsed)}. Попробуй ещё раз.",
                primaryLabel = "Ещё партия",
                onPrimary = { viewModel.restart() },
                onMenu = onBack,
                scheme = scheme,
                glitch = errorTheme
            )
            else -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilterChip(
                    selected = flagMode,
                    onClick = { viewModel.toggleFlagMode() },
                    label = { GlitchText(if (flagMode) "Режим флага" else "Обычный режим") }
                )
                GlitchText(
                    text = "Долгое нажатие — флаг",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    scheme: ColorScheme,
    glitch: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GlitchText(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                // Зазор до значения: глитч-«двойники» не наползают на подпись.
                modifier = Modifier.padding(end = 8.dp)
            )
            MonoTitle(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                glitch = glitch
            )
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onMenu: () -> Unit,
    scheme: ColorScheme,
    glitch: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            MonoTitle(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                glitch = glitch
            )
            GlitchText(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrimary) { GlitchText(primaryLabel) }
                Button(onClick = onMenu) { GlitchText("В меню") }
            }
        }
    }
}

/** Поле: клетки одинаковые, размер подбирается под свободное место. */
private val MinCellSize = 20.dp

/** Потолок масштаба: клетку крупнее этого размера считать незачем. */
private val MaxCellSize = 72.dp

/** Клетки мельче этого размера — повод показать подсказку «приблизьте поле». */
private val MinTapCellSize = 14.dp

private const val RevealAnimMillis = 180f
private const val FlagAnimMillis = 260f
private const val DoubleTapMillis = 320L

/** Зона у левого края, где живёт свайп «назад»: там поле не перетаскиваем. */
private val EdgeSwipeZonePx = 200f

private val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private enum class GestureMode { Tap, Pan, Pinch, Cancelled }

/**
 * Поле «Сапёра» одним Canvas: клетки, сетка, превью нажатия и анимации
 * рисуются здесь, поэтому большое поле не создаёт тысячи нод и не
 * тормозит. Масштаб и сдвиг — матрица трансформации в момент отрисовки,
 * поэтому щипок и возврат масштаба не пересоздают композицию.
 */
@Composable
private fun MinesweeperField(
    width: Int,
    height: Int,
    board: MinesweeperBoard?,
    status: MinesweeperStatus,
    boomIndex: Int?,
    scheme: ColorScheme,
    errorTheme: Boolean,
    zoomResetRequest: Long = 0,
    smallCellHintEnabled: Boolean = false,
    onCellClick: (Int) -> Unit,
    onCellLongClick: (Int) -> Unit,
    onCellDoubleClick: (Int) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()

    // Порог долгого нажатия один и тот же для жеста и для «удержания»: берём
    // из системной конфигурации, а не заводим вручную.
    val longPressMs = LocalViewConfiguration.current.longPressTimeoutMillis

    // Масштаб — обычное состояние, а не Animatable: щипок обрабатывается
    // внутри awaitEachGesture, где suspend-вызовы (snapTo/animateTo) запрещены
    // restricted-scope. Плавность кнопкам даёт animate() в отдельной корутине.
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var framesNeeded by remember { mutableStateOf(false) }
    val animClock = remember { mutableLongStateOf(0L) }

    // Метки времени раскрытия и установки флага: по ним рисуются анимации.
    var revealAt by remember { mutableStateOf(LongArray(0)) }
    var flagAt by remember { mutableStateOf(LongArray(0)) }
    var prevRevealed = remember { BooleanArray(0) }
    var prevFlagged = remember { BooleanArray(0) }
    var lastActionIndex by remember { mutableStateOf(-1) }

    var pressedCell by remember { mutableStateOf(-1) }
    var pressedSince by remember { mutableStateOf<TimeMark?>(null) }

    // Клетка, по которой уже сработало долгое нажатие «на удержании»: чтобы
    // не ставить флаг дважды за одно нажатие. Сбрасывается при каждом касании.
    var longFiredCell by remember { mutableStateOf(-1) }

    var lastTapCell by remember { mutableStateOf(-1) }
    var lastTapTime by remember { mutableStateOf(0L) }

    var hintVisible by remember { mutableStateOf(false) }
    LaunchedEffect(hintVisible) {
        if (hintVisible) {
            delay(2500)
            hintVisible = false
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportW = maxWidth
        val viewportH = maxHeight
        val fitCell = minOf(viewportW / width, viewportH / height)
        val baseCell = maxOf(fitCell, MinCellSize)
        val zoomMin = (fitCell / baseCell).coerceAtMost(1f)
        val zoomMax = maxOf(MaxCellSize / baseCell, 2f)

        // Dp → пиксели считаем один раз здесь: внутри LaunchedEffect и локальных
        // функций нет receiver'а Density, а BoxWithConstraintsScope (Compose 1.12)
        // Density больше не наследует — поэтому плотность берём явно.
        val density = LocalDensity.current
        val basePx = with(density) { baseCell.toPx() }
        val viewportWPx = with(density) { viewportW.toPx() }
        val viewportHPx = with(density) { viewportH.toPx() }

        val zoom = zoomLevel

        // Глифы измеряются заранее: measure — suspend-функция, а фаза
        // отрисовки не умеет ждать. Размер шрифта зависит только от клетки.
        var glyphLayouts by remember { mutableStateOf<Map<String, TextLayoutResult>>(emptyMap()) }
        LaunchedEffect(baseCell, scheme) {
            val fontSize = minOf(baseCell.value * 0.5f, 24f).sp
            glyphLayouts = buildMap {
                for (n in 1..8) {
                    put(
                        n.toString(),
                        textMeasurer.measure(
                            n.toString(),
                            TextStyle(
                                fontSize = fontSize,
                                fontWeight = FontWeight.Bold,
                                color = numberColor(n, scheme)
                            )
                        )
                    )
                }
                put("✖", textMeasurer.measure("✖", TextStyle(fontSize = fontSize, color = scheme.onErrorContainer)))
                put("⚑", textMeasurer.measure("⚑", TextStyle(fontSize = fontSize, color = scheme.error)))
                put(
                    "ERROR",
                    textMeasurer.measure(
                        "ERROR",
                        TextStyle(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 4.sp
                        )
                    )
                )
            }
        }

        LaunchedEffect(zoom, viewportWPx, viewportHPx) {
            offset = clampOffset(
                offset = offset,
                zoom = zoom,
                basePx = basePx,
                width = width,
                height = height,
                viewportWPx = viewportWPx,
                viewportHPx = viewportHPx
            )
        }

        LaunchedEffect(board, status) {
            val b = board ?: return@LaunchedEffect
            val total = b.width * b.height
            if (prevRevealed.size != total) {
                prevRevealed = BooleanArray(total)
                prevFlagged = BooleanArray(total)
                revealAt = LongArray(total) { Long.MIN_VALUE }
                flagAt = LongArray(total) { Long.MIN_VALUE }
            }
            val now = withFrameNanos { it }
            val loss = status == MinesweeperStatus.LOST
            val win = status == MinesweeperStatus.WON
            for (i in 0 until total) {
                val cell = b.cells[i]
                if (cell.isRevealed && !prevRevealed[i]) {
                    // Каскад раскрытия идёт от клетки хода; при проигрыше —
                    // от подорванной мины, при победе флаги встают волной.
                    val delay = when {
                        loss && cell.isMine && boomIndex != null ->
                            minOf(chebyshev(boomIndex, i, b.width) * 25L, 500L)
                        win -> minOf((cell.x + cell.y) * 6L, 400L)
                        else -> if (lastActionIndex in 0 until total) {
                            minOf(chebyshev(lastActionIndex, i, b.width) * 12L, 300L)
                        } else {
                            0L
                        }
                    }
                    revealAt[i] = now + delay
                }
                if (cell.isFlagged && !prevFlagged[i]) flagAt[i] = now
                prevRevealed[i] = cell.isRevealed
                prevFlagged[i] = cell.isFlagged
            }
            framesNeeded = true
        }

        LaunchedEffect(framesNeeded) {
            if (!framesNeeded) return@LaunchedEffect
            while (framesNeeded) {
                withFrameNanos { animClock.longValue = it }
                // Долгое нажатие срабатывает на удержании, не дожидаясь
                // отпускания: флаг встаёт под пальцем по проходе тайм-аута.
                val pressed = pressedCell
                val since = pressedSince
                if (pressed >= 0 && since != null && longFiredCell != pressed &&
                    since.elapsedNow().inWholeMilliseconds >= longPressMs
                ) {
                    longFiredCell = pressed
                    lastActionIndex = pressed
                    onCellLongClick(pressed)
                }
                framesNeeded = hasActiveAnimations(animClock.longValue, revealAt, flagAt, pressedCell)
            }
        }

        // Масштаб от центра вьюпорта: точка в центре остаётся на месте.
        var zoomJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        fun zoomBy(factor: Float) {
            val current = zoomLevel
            val target = (current * factor).coerceIn(zoomMin, zoomMax)
            if (target == current) return
            val ratio = target / current
            val centerX = viewportWPx / 2f
            val centerY = viewportHPx / 2f
            offset = clampOffset(
                offset = Offset(
                    centerX - (centerX - offset.x) * ratio,
                    centerY - (centerY - offset.y) * ratio
                ),
                zoom = target,
                basePx = basePx,
                width = width,
                height = height,
                viewportWPx = viewportWPx,
                viewportHPx = viewportHPx
            )
            // Плавность кнопкам: щипок пишет значение напрямую, кнопки
            // доезжают анимацией; прошлую анимацию отменяем.
            zoomJob?.cancel()
            zoomJob = scope.launch {
                animate(zoomLevel, target, animationSpec = tween(220)) { v, _ -> zoomLevel = v }
            }
        }

        // Кнопка «Вернуть исходный размер» в шапке передаёт тик: возвращаем
        // масштаб 1f (поле «по месту») тем же путём, что zoomBy.
        LaunchedEffect(zoomResetRequest) {
            if (zoomResetRequest > 0L) zoomBy(1f / zoomLevel)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(width, height, zoomMin, zoomMax, baseCell) {
                    val hintCellPx = MinTapCellSize.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var mode = GestureMode.Tap
                        var prevPos = down.position
                        var prevCentroid = Offset.Zero
                        var prevDist = 0f
                        val downCell = cellAt(down.position, offset, zoomLevel, baseCell.toPx(), width, height)
                        if (downCell != null) {
                            pressedCell = downCell
                            pressedSince = TimeSource.Monotonic.markNow()
                            longFiredCell = -1
                            framesNeeded = true
                        }
                        var upPos = down.position
                        var upTime = down.uptimeMillis
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                released = true
                                upPos = event.changes.firstOrNull()?.position ?: upPos
                                upTime = event.changes.firstOrNull()?.uptimeMillis ?: upTime
                                break
                            }
                            if (pressed.size >= 2) {
                                if (mode != GestureMode.Pinch) {
                                    mode = GestureMode.Pinch
                                    pressedCell = -1
                                    prevCentroid = centroid(pressed)
                                    prevDist = 0f
                                }
                                val c = centroid(pressed)
                                val d = distance(pressed)
                                if (prevDist > 0f && d > 0f) {
                                    val current = zoomLevel
                                    val newZoom = (current * d / prevDist).coerceIn(zoomMin, zoomMax)
                                    if (newZoom != current) {
                                        val ratio = newZoom / current
                                        // Точка под центром пальцев остаётся на месте.
                                        val newOffset = c - (prevCentroid - offset) * ratio
                                        // Щипок пишет напрямую: snapTo — suspend, а
                                        // awaitEachGesture работает в restricted scope.
                                        zoomJob?.cancel()
                                        zoomLevel = newZoom
                                        offset = clampOffset(
                                            offset = newOffset,
                                            zoom = newZoom,
                                            basePx = baseCell.toPx(),
                                            width = width,
                                            height = height,
                                            viewportWPx = viewportW.toPx(),
                                            viewportHPx = viewportH.toPx()
                                        )
                                        pressed.forEach { it.consume() }
                                    }
                                }
                                prevCentroid = c
                                prevDist = d
                            } else {
                                val change = pressed[0]
                                if (mode == GestureMode.Pan && change.isConsumed) {
                                    // Свайп «назад» у края экрана перехватил жест.
                                    mode = GestureMode.Cancelled
                                }
                                val moved = (change.position - down.position).getDistance()
                                if (mode == GestureMode.Tap && moved > viewConfiguration.touchSlop) {
                                    mode = if (down.position.x <= EdgeSwipeZonePx) {
                                        GestureMode.Cancelled
                                    } else {
                                        GestureMode.Pan
                                    }
                                    pressedCell = -1
                                }
                                if (mode == GestureMode.Pan) {
                                    val newOffset = clampOffset(
                                        offset = offset + (change.position - prevPos),
                                        zoom = zoomLevel,
                                        basePx = baseCell.toPx(),
                                        width = width,
                                        height = height,
                                        viewportWPx = viewportW.toPx(),
                                        viewportHPx = viewportH.toPx()
                                    )
                                    if (newOffset != offset) {
                                        offset = newOffset
                                        change.consume()
                                    }
                                }
                                prevPos = change.position
                            }
                        }
                        pressedCell = -1
                        pressedSince = null
                        if (mode == GestureMode.Tap) {
                            val cellSizePx = baseCell.toPx() * zoomLevel
                            val upCell = cellAt(upPos, offset, zoomLevel, baseCell.toPx(), width, height)
                            // Подсказка показывается только если включена в настройках:
                            // по умолчанию клетки нажимаются при любом размере.
                            if (downCell != null && cellSizePx < hintCellPx && smallCellHintEnabled) {
                                hintVisible = true
                            }
                            // Долгое нажатие уже сработало «на удержании» — второй
                            // ход по отпусканию не нужен.
                            if (downCell != null && upCell == downCell && longFiredCell != downCell) {
                                if (lastTapCell == downCell && upTime - lastTapTime < DoubleTapMillis) {
                                    lastTapCell = -1
                                    lastActionIndex = downCell
                                    onCellDoubleClick(downCell)
                                } else {
                                    lastTapCell = downCell
                                    lastTapTime = upTime
                                    lastActionIndex = downCell
                                    onCellClick(downCell)
                                }
                            }
                        }
                        longFiredCell = -1
                    }
                }
        ) {
            val now = animClock.longValue
            val basePx = baseCell.toPx()
            val cellPx = basePx * zoom
            val boardW = cellPx * width
            val boardH = cellPx * height

            drawRoundRect(
                color = scheme.surfaceContainer,
                topLeft = offset,
                size = Size(boardW, boardH),
                cornerRadius = CornerRadius(8.dp.toPx())
            )

            withTransform({
                translate(offset.x, offset.y)
                scale(zoom, zoom, Offset.Zero)
            }) {
                if (errorTheme) {
                    glyphLayouts["ERROR"]?.let { g ->
                        drawText(
                            g,
                            topLeft = Offset(
                                (width * basePx - g.size.width) / 2f,
                                (height * basePx - g.size.height) / 2f
                            ),
                            color = Color(0x26FF3B30)
                        )
                    }
                }
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val index = y * width + x
                        drawCell(
                            cell = board?.cells?.getOrNull(index),
                            x = x * basePx,
                            y = y * basePx,
                            size = basePx,
                            revealStart = revealAt.getOrElse(index) { Long.MIN_VALUE },
                            flagStart = flagAt.getOrElse(index) { Long.MIN_VALUE },
                            now = now,
                            scheme = scheme,
                            errorTheme = errorTheme,
                            glyphs = glyphLayouts
                        )
                    }
                }
                if (pressedCell >= 0) {
                    val px = pressedCell % width
                    val py = pressedCell / width
                    val pressedElapsedMs = (pressedSince?.elapsedNow()?.inWholeMilliseconds ?: 0L).coerceAtLeast(0L)
                    val alpha = (pressedElapsedMs / 120f).coerceIn(0f, 1f) * 0.22f
                    drawRoundRect(
                        color = scheme.primary.copy(alpha = alpha),
                        topLeft = Offset(px * basePx + 1f, py * basePx + 1f),
                        size = Size(basePx - 2f, basePx - 2f),
                        cornerRadius = CornerRadius(4f)
                    )
                }
            }

            val line = scheme.outlineVariant.copy(alpha = 0.6f)
            val lineWidth = 0.5.dp.toPx().coerceAtLeast(1f)
            for (i in 0..width) {
                val lx = offset.x + i * cellPx
                drawLine(line, Offset(lx, offset.y), Offset(lx, offset.y + boardH), strokeWidth = lineWidth)
            }
            for (j in 0..height) {
                val ly = offset.y + j * cellPx
                drawLine(line, Offset(offset.x, ly), Offset(offset.x + boardW, ly), strokeWidth = lineWidth)
            }

            drawRoundRect(
                color = scheme.outlineVariant,
                topLeft = offset,
                size = Size(boardW, boardH),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // Тема Error: скан-строка, полосы «порчи» и пульсирующая рамка.
            // Всё детерминировано по времени композиции, никаких состояний.
            if (errorTheme) {
                val ms = now / 1_000_000L
                val scanH = (boardH + 60f).toInt().coerceAtLeast(1)
                val sy = (ms / 8 % scanH).toFloat() - 30f
                drawRect(
                    color = Color(0x59FF3B30),
                    topLeft = Offset(offset.x, offset.y + sy),
                    size = Size(boardW, 2f)
                )
                for (i in 0 until 4) {
                    val yy = (((ms / 3) * (16L * i + 11) + 41L * i) % scanH).toFloat() - 30f
                    drawRect(
                        color = Color(0x2EFF2D55),
                        topLeft = Offset(offset.x, offset.y + yy),
                        size = Size(boardW, 6f + (i % 3) * 4f)
                    )
                }
                val pulse = ((ms / 45) % 90) / 90f
                drawRoundRect(
                    color = Color(0xFFFF3B30).copy(alpha = 0.3f + 0.3f * pulse),
                    topLeft = offset,
                    size = Size(boardW, boardH),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        AnimatedVisibility(
            visible = hintVisible,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = scheme.inverseSurface,
                tonalElevation = 4.dp
            ) {
                GlitchText(
                    text = "Клетки слишком мелкие — приближите поле",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/** Клетка под точкой экрана (null — точка вне поля). */
private fun cellAt(
    pos: Offset,
    offset: Offset,
    zoom: Float,
    basePx: Float,
    width: Int,
    height: Int
): Int? {
    val bx = floor((pos.x - offset.x) / zoom / basePx).toInt()
    val by = floor((pos.y - offset.y) / zoom / basePx).toInt()
    return if (bx in 0 until width && by in 0 until height) by * width + bx else null
}

/**
 * Сдвиг поля так, чтобы оно не уезжало за вьюпорт: если поле меньше
 * вьюпорта — центрируем, иначе не даём отодвинуть край дальше края.
 */
internal fun clampOffset(
    offset: Offset,
    zoom: Float,
    basePx: Float,
    width: Int,
    height: Int,
    viewportWPx: Float,
    viewportHPx: Float
): Offset {
    val boardW = basePx * zoom * width
    val boardH = basePx * zoom * height
    val ox = if (boardW <= viewportWPx) {
        (viewportWPx - boardW) / 2f
    } else {
        offset.x.coerceIn(viewportWPx - boardW, 0f)
    }
    val oy = if (boardH <= viewportHPx) {
        (viewportHPx - boardH) / 2f
    } else {
        offset.y.coerceIn(viewportHPx - boardH, 0f)
    }
    return Offset(ox, oy)
}

private fun hasActiveAnimations(
    now: Long,
    revealAt: LongArray,
    flagAt: LongArray,
    pressedCell: Int
): Boolean {
    if (pressedCell >= 0) return true
    for (i in revealAt.indices) {
        val s = revealAt[i]
        if (s != Long.MIN_VALUE && now - s < RevealAnimMillis) return true
    }
    for (i in flagAt.indices) {
        val s = flagAt[i]
        if (s != Long.MIN_VALUE && now - s < FlagAnimMillis) return true
    }
    return false
}

/** Расстояние Чебышёва между клетками — как распространяется волна раскрытия. */
private fun chebyshev(a: Int, b: Int, width: Int): Int {
    val ax = a % width
    val ay = a / width
    val bx = b % width
    val by = b / width
    return maxOf(abs(ax - bx), abs(ay - by))
}

private fun centroid(changes: List<PointerInputChange>): Offset {
    var x = 0f
    var y = 0f
    changes.forEach { x += it.position.x; y += it.position.y }
    return Offset(x / changes.size, y / changes.size)
}

private fun distance(changes: List<PointerInputChange>): Float {
    if (changes.size < 2) return 0f
    val a = changes[0].position
    val b = changes[1].position
    return (a - b).getDistance()
}

private fun revealProgress(start: Long, now: Long): Float =
    if (start == Long.MIN_VALUE) 1f
    else ((now - start).coerceAtLeast(0L) / RevealAnimMillis).coerceIn(0f, 1f)

private fun flagProgress(start: Long, now: Long): Float =
    if (start == Long.MIN_VALUE) 1f
    else ((now - start).coerceAtLeast(0L) / FlagAnimMillis).coerceIn(0f, 1f)

private fun DrawScope.drawCell(
    cell: MinesweeperCell?,
    x: Float,
    y: Float,
    size: Float,
    revealStart: Long,
    flagStart: Long,
    now: Long,
    scheme: ColorScheme,
    errorTheme: Boolean,
    glyphs: Map<String, TextLayoutResult>
) {
    val revealed = cell?.isRevealed == true
    val isMine = cell?.isMine == true
    val flagged = cell?.isFlagged == true

    // Полупиксель с каждой стороны: соседние клетки не оставляют швов.
    // В режиме Error нераскрытые клетки слегка «мерцают» шашечкой.
    val cellX = (x / size).toInt()
    val cellY = (y / size).toInt()
    val baseFill = if (errorTheme && !revealed && (cellX + cellY) % 2 == 1) {
        scheme.surfaceContainerHigh.copy(alpha = 0.82f)
    } else {
        scheme.surfaceContainerHigh
    }
    drawRect(
        color = baseFill,
        topLeft = Offset(x - 0.25f, y - 0.25f),
        size = Size(size + 0.5f, size + 0.5f)
    )

    if (revealed) {
        val p = revealProgress(revealStart, now)
        val ease = FastOutSlowInEasing.transform(p)
        val bg = if (isMine) scheme.errorContainer else scheme.surface
        val revealScale = 0.6f + 0.4f * ease
        withTransform({
            translate(x + size / 2f, y + size / 2f)
            scale(revealScale, revealScale)
            translate(-x - size / 2f, -y - size / 2f)
        }) {
            drawRect(
                color = bg.copy(alpha = ease),
                topLeft = Offset(x - 0.25f, y - 0.25f),
                size = Size(size + 0.5f, size + 0.5f)
            )
            when {
                isMine -> glyphs["✖"]?.let { g ->
                    val top = textTopLeft(g, x, y, size)
                    if (errorTheme) drawGhostLayer(g, top, ease)
                    drawText(
                        g,
                        topLeft = top,
                        color = if (errorTheme) Color(0xFFFF8A80) else scheme.onErrorContainer,
                        alpha = ease
                    )
                }
                flagged -> glyphs["⚑"]?.let { g ->
                    val top = textTopLeft(g, x, y, size)
                    if (errorTheme) drawGhostLayer(g, top, ease)
                    drawText(
                        g,
                        topLeft = top,
                        color = if (errorTheme) Color(0xFFFF3B30) else scheme.error,
                        alpha = ease
                    )
                }
                (cell?.adjacentMines ?: 0) > 0 -> glyphs[cell!!.adjacentMines.toString()]?.let { g ->
                    val top = textTopLeft(g, x, y, size)
                    if (errorTheme) {
                        val jitter = if ((cellX * 7 + cellY * 13) % 5 == 0) 2.4f else 1.5f
                        drawText(g, topLeft = top + Offset(-jitter, 0f), color = Color(0x6600E5FF), alpha = ease)
                        drawText(g, topLeft = top + Offset(jitter, 0f), color = Color(0x66FF0033), alpha = ease)
                    }
                    drawText(
                        g,
                        topLeft = top,
                        color = if (errorTheme) Color(0xFFFFE8E6) else Color.Unspecified,
                        alpha = ease
                    )
                }
            }
        }
    } else if (flagged) {
        val pop = EaseOutBack.transform(flagProgress(flagStart, now))
        drawCircle(
            color = scheme.primary,
            radius = size * 0.2f * pop,
            center = Offset(x + size / 2f, y + size / 2f)
        )
    }
}

/** Хроматические «двойники» глифа: циан слева, красный справа. Только Error. */
private fun DrawScope.drawGhostLayer(glyph: TextLayoutResult, top: Offset, ease: Float) {
    drawText(glyph, topLeft = top + Offset(-1.8f, 0f), color = Color(0x5500E5FF), alpha = ease)
    drawText(glyph, topLeft = top + Offset(1.8f, 0f), color = Color(0x55FF002E), alpha = ease)
}

private fun textTopLeft(layout: TextLayoutResult, x: Float, y: Float, size: Float): Offset =
    Offset(x + (size - layout.size.width) / 2f, y + (size - layout.size.height) / 2f)

/** Классические цвета цифр: 1 — синий, 2 — зелёный и так далее. */
private fun numberColor(value: Int, scheme: ColorScheme): Color = when (value) {
    1 -> Color(0xFF1976D2)
    2 -> Color(0xFF388E3C)
    3 -> Color(0xFFD32F2F)
    4 -> Color(0xFF512DA8)
    5 -> Color(0xFF8D6E63)
    6 -> Color(0xFF0097A7)
    7 -> Color(0xFF303F9F)
    else -> scheme.onSurfaceVariant
}

private fun formatSeconds(total: Int): String {
    val minutes = total / 60
    val seconds = total % 60
    return if (minutes > 0) {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    } else {
        seconds.toString()
    }
}
