package com.jetbrains.kmpapp.screens.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jetbrains.kmpapp.theme.GlitchText
import com.jetbrains.kmpapp.theme.MonoTitle
import kotlin.math.abs

/**
 * Экран игры «Тетрис».
 *
 * Особенности:
 * - Поле 10 × 20 с поддержкой призрачной фигуры (ghost piece).
 * - Счётчик очков, уровней, очищенных линий и лучший рекорд.
 * - Миниатюры следующей фигуры (Next) и отложенной (Hold).
 * - Сенсорное управление: свайпы влево/вправо/вниз, тап для поворота.
 * - Эргономичная экранная панель управления для комфортной игры одной рукой.
 * - Диалог с полным описанием правил и начислением очков.
 * - Оверлеи паузы и окончания игры (Game Over).
 */
@Composable
fun TetrisScreen(
    viewModel: GamesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.tetrisState.collectAsState()
    val bestRecord by viewModel.tetrisRecord.collectAsState()
    val errorTheme by viewModel.errorActive.collectAsState()
    val controlVariant by viewModel.tetrisControlVariant.collectAsState()

    var showRulesDialog by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 110.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Шапка с кнопками управления (центрированный верхний бар)
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "В меню игр"
                )
            }

            MonoTitle(
                text = "Тетрис",
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                glitch = errorTheme
            )

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showRulesDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Правила игры",
                        tint = colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        if (state.status == TetrisStatus.PLAYING) {
                            viewModel.pauseTetris()
                        } else if (state.status == TetrisStatus.PAUSED) {
                            viewModel.resumeTetris()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (state.status == TetrisStatus.PLAYING) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = if (state.status == TetrisStatus.PLAYING) "Пауза" else "Продолжить",
                        tint = colorScheme.primary
                    )
                }

                IconButton(onClick = { viewModel.startNewTetris() }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Заново",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Информационная панель (HUD): Hold / Очки / Next
        HudPanel(
            state = state,
            bestRecord = bestRecord?.score,
            onHoldClick = { viewModel.holdTetris() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Игровое поле и экранные кнопки
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val availableHeight = (maxHeight - 110.dp).coerceAtLeast(180.dp)
            val availableWidth = maxWidth - 16.dp

            val cellFromWidth = availableWidth / TetrisEngine.BOARD_WIDTH.toFloat()
            val cellFromHeight = availableHeight / TetrisEngine.BOARD_HEIGHT.toFloat()
            val cellSize = minOf(cellFromWidth, cellFromHeight).coerceIn(12.dp, 26.dp)

            val boardW = cellSize * TetrisEngine.BOARD_WIDTH
            val boardH = cellSize * TetrisEngine.BOARD_HEIGHT

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .size(width = boardW, height = boardH)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceContainerLowest)
                        .border(
                            width = 1.5.dp,
                            color = colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    TetrisBoardCanvas(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        onMoveLeft = { viewModel.moveTetrisLeft() },
                        onMoveRight = { viewModel.moveTetrisRight() },
                        onSoftDrop = { viewModel.softDropTetris() },
                        onRotate = { viewModel.rotateTetris() }
                    )

                    // Оверлей паузы
                    if (state.status == TetrisStatus.PAUSED) {
                        TetrisPauseOverlay(
                            onResume = { viewModel.resumeTetris() },
                            onRestart = { viewModel.startNewTetris() }
                        )
                    }

                    // Оверлей окончания игры
                    if (state.status == TetrisStatus.GAME_OVER) {
                        TetrisGameOverOverlay(
                            state = state,
                            isNewRecord = bestRecord?.score?.let { state.score >= it && state.score > 0 } ?: (state.score > 0),
                            onRestart = { viewModel.startNewTetris() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Экранные кнопки управления
                TetrisControls(
                    canHold = state.canHold && state.status == TetrisStatus.PLAYING,
                    isPlaying = state.status == TetrisStatus.PLAYING,
                    controlVariant = controlVariant,
                    onMoveLeft = { viewModel.moveTetrisLeft() },
                    onMoveRight = { viewModel.moveTetrisRight() },
                    onSoftDrop = { viewModel.softDropTetris() },
                    onHardDrop = { viewModel.hardDropTetris() },
                    onRotate = { viewModel.rotateTetris() },
                    onHold = { viewModel.holdTetris() }
                )
            }
        }
    }

    // Диалог с правилами игры
    if (showRulesDialog) {
        TetrisRulesDialog(onDismiss = { showRulesDialog = false })
    }
}

/** Верхняя информационная панель HUD: Hold (слева), счёт/уровень/линии (по центру) и Next (справа). */
@Composable
private fun HudPanel(
    state: TetrisState,
    bestRecord: Int?,
    onHoldClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            // Левый блок: Hold (левее к краю)
            MiniPieceCard(
                title = "HOLD",
                piece = state.holdPiece,
                enabled = state.canHold && state.status == TetrisStatus.PLAYING,
                onClick = onHoldClick,
                modifier = Modifier.align(Alignment.CenterStart)
            )

            // Центральный блок: Счёт, уровень и линии (строго по центру)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = "${state.score}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "УР. ${state.level}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "${state.lines} ЛИН.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (bestRecord != null && bestRecord > 0) {
                    Text(
                        text = "Рекорд: $bestRecord",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Правый блок: Next (правее к краю)
            MiniPieceCard(
                title = "NEXT",
                piece = state.nextPieces.firstOrNull(),
                enabled = false,
                onClick = null,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

/** Карточка предварительного просмотра фигуры (Hold или Next). */
@Composable
private fun MiniPieceCard(
    title: String,
    piece: TetrominoType?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled || onClick == null) 1f else 0.4f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (enabled || onClick == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier.size(width = 38.dp, height = 26.dp),
            contentAlignment = Alignment.Center
        ) {
            if (piece != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val blocks = piece.getBlocks(0)
                    val minX = blocks.minOf { it.x }
                    val maxX = blocks.maxOf { it.x }
                    val minY = blocks.minOf { it.y }
                    val maxY = blocks.maxOf { it.y }

                    val pieceWidth = maxX - minX + 1
                    val pieceHeight = maxY - minY + 1

                    val miniCellSize = minOf(size.width / 4.2f, size.height / 3.2f)
                    val startX = (size.width - pieceWidth * miniCellSize) / 2f
                    val startY = (size.height - pieceHeight * miniCellSize) / 2f

                    val color = Color(piece.colorArgb)
                    blocks.forEach { b ->
                        val bx = startX + (b.x - minX) * miniCellSize
                        val by = startY + (b.y - minY) * miniCellSize
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(bx, by),
                            size = Size(miniCellSize - 1.5f, miniCellSize - 1.5f),
                            cornerRadius = CornerRadius(2.5f, 2.5f)
                        )
                    }
                }
            } else {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

/**
 * Отрисовка игрового поля Тетриса через Canvas:
 * - Сетка ячеек
 * - Зафиксированные блоки
 * - Призрачная фигура (проекция падения)
 * - Текущая активная фигура
 * - Обработка жестов тапа и свайпа
 */
@Composable
private fun TetrisBoardCanvas(
    state: TetrisState,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onSoftDrop: () -> Unit,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }

    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)

    Canvas(
        modifier = modifier
            .pointerInput(state.status) {
                if (state.status == TetrisStatus.PLAYING) {
                    detectTapGestures(
                        onTap = { onRotate() }
                    )
                }
            }
            .pointerInput(state.status) {
                if (state.status == TetrisStatus.PLAYING) {
                    detectDragGestures(
                        onDragStart = {
                            accumulatedDragX = 0f
                            accumulatedDragY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDragX += dragAmount.x
                            accumulatedDragY += dragAmount.y

                            val thresholdX = size.width / TetrisEngine.BOARD_WIDTH.toFloat() * 0.8f
                            val thresholdY = size.height / TetrisEngine.BOARD_HEIGHT.toFloat() * 0.7f

                            if (accumulatedDragX > thresholdX) {
                                onMoveRight()
                                accumulatedDragX = 0f
                            } else if (accumulatedDragX < -thresholdX) {
                                onMoveLeft()
                                accumulatedDragX = 0f
                            }

                            if (accumulatedDragY > thresholdY) {
                                onSoftDrop()
                                accumulatedDragY = 0f
                            }
                        }
                    )
                }
            }
    ) {
        val cellW = size.width / TetrisEngine.BOARD_WIDTH.toFloat()
        val cellH = size.height / TetrisEngine.BOARD_HEIGHT.toFloat()

        // Сетка
        for (x in 0..TetrisEngine.BOARD_WIDTH) {
            drawLine(
                color = outlineColor,
                start = Offset(x * cellW, 0f),
                end = Offset(x * cellW, size.height),
                strokeWidth = 0.8f
            )
        }
        for (y in 0..TetrisEngine.BOARD_HEIGHT) {
            drawLine(
                color = outlineColor,
                start = Offset(0f, y * cellH),
                end = Offset(size.width, y * cellH),
                strokeWidth = 0.8f
            )
        }

        val padding = 1.2f
        val corner = CornerRadius(cellW * 0.18f, cellH * 0.18f)

        // 1. Отрисовка установленных блоков
        for (y in 0 until TetrisEngine.BOARD_HEIGHT) {
            for (x in 0 until TetrisEngine.BOARD_WIDTH) {
                val block = state.board[x, y]
                if (block != null) {
                    drawTetrisBlock(
                        color = Color(block.colorArgb),
                        topLeft = Offset(x * cellW + padding, y * cellH + padding),
                        size = Size(cellW - padding * 2, cellH - padding * 2),
                        corner = corner
                    )
                }
            }
        }

        // 2. Отрисовка призрачной фигуры (Ghost piece)
        val active = state.currentPiece
        if (active != null && state.status == TetrisStatus.PLAYING) {
            val ghostBlocks = active.type.getBlocks(active.rotation)
            val ghostColor = Color(active.type.colorArgb).copy(alpha = 0.3f)
            val strokeColor = Color(active.type.colorArgb).copy(alpha = 0.65f)

            ghostBlocks.forEach { b ->
                val gx = active.x + b.x
                val gy = state.ghostY + b.y
                if (gx in 0 until TetrisEngine.BOARD_WIDTH && gy in 0 until TetrisEngine.BOARD_HEIGHT) {
                    val rectOffset = Offset(gx * cellW + padding, gy * cellH + padding)
                    val rectSize = Size(cellW - padding * 2, cellH - padding * 2)

                    drawRoundRect(
                        color = ghostColor,
                        topLeft = rectOffset,
                        size = rectSize,
                        cornerRadius = corner
                    )
                    drawRoundRect(
                        color = strokeColor,
                        topLeft = rectOffset,
                        size = rectSize,
                        cornerRadius = corner,
                        style = Stroke(width = 1.2f)
                    )
                }
            }

            // 3. Отрисовка активной падающей фигуры
            val activeColor = Color(active.type.colorArgb)
            activeBlocksLoop@ for (b in active.type.getBlocks(active.rotation)) {
                val ax = active.x + b.x
                val ay = active.y + b.y
                if (ax in 0 until TetrisEngine.BOARD_WIDTH && ay in 0 until TetrisEngine.BOARD_HEIGHT) {
                    drawTetrisBlock(
                        color = activeColor,
                        topLeft = Offset(ax * cellW + padding, ay * cellH + padding),
                        size = Size(cellW - padding * 2, cellH - padding * 2),
                        corner = corner
                    )
                }
            }
        }
    }
}

/** Красивый блок с эффектом глубины и блика. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTetrisBlock(
    color: Color,
    topLeft: Offset,
    size: Size,
    corner: CornerRadius
) {
    // Основное тело блока
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = corner
    )
    // Верхний блик (светлая окантовка)
    drawRoundRect(
        color = Color.White.copy(alpha = 0.35f),
        topLeft = topLeft,
        size = Size(size.width, size.height * 0.35f),
        cornerRadius = corner
    )
    // Тонкий внутренний контур
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.2f),
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
        style = Stroke(width = 1f)
    )
}

/** Нижняя панель физических сенсорных кнопок для удобного управления. */
@Composable
private fun TetrisControls(
    canHold: Boolean,
    isPlaying: Boolean,
    controlVariant: TetrisControlVariant = TetrisControlVariant.TYPE_1,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onSoftDrop: () -> Unit,
    onHardDrop: () -> Unit,
    onRotate: () -> Unit,
    onHold: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (controlVariant == TetrisControlVariant.TYPE_2) {
            // Управление 2:
            // 1 ряд: лево, вращать, право
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onMoveLeft,
                    enabled = isPlaying,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Влево"
                    )
                }

                Button(
                    onClick = onRotate,
                    enabled = isPlaying,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ВРАЩАТЬ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onMoveRight,
                    enabled = isPlaying,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Вправо"
                    )
                }
            }

            // 2 ряд: ниже, Hold, упасть
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onSoftDrop,
                    enabled = isPlaying,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "НИЖЕ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onHold,
                    enabled = canHold && isPlaying,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "HOLD",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = onHardDrop,
                    enabled = isPlaying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardDoubleArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "УПАСТЬ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            // Управление 1 (по умолчанию):
            // 1 ряд: лево, вправо, Hold, перевернуть
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onMoveLeft,
                    enabled = isPlaying,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Влево"
                    )
                }

                FilledTonalButton(
                    onClick = onMoveRight,
                    enabled = isPlaying,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Вправо"
                    )
                }

                FilledTonalButton(
                    onClick = onHold,
                    enabled = canHold && isPlaying,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "HOLD",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = onRotate,
                    enabled = isPlaying,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = "Перевернуть"
                    )
                }
            }

            // 2 ряд: вниз, сброс
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onSoftDrop,
                    enabled = isPlaying,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ВНИЗ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Button(
                    onClick = onHardDrop,
                    enabled = isPlaying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardDoubleArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "СБРОС",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/** Оверлей паузы с кнопками возобновления и перезапуска. */
@Composable
private fun TetrisPauseOverlay(
    onResume: () -> Unit,
    onRestart: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.padding(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "ПАУЗА",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Продолжить")
                }

                FilledTonalButton(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Начать заново")
                }
            }
        }
    }
}

/** Оверлей окончания игры: итоговые показатели и кнопка реванша. */
@Composable
private fun TetrisGameOverOverlay(
    state: TetrisState,
    isNewRecord: Boolean,
    onRestart: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.padding(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ИГРА ОКОНЧЕНА",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )

                if (isNewRecord) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "🏆 НОВЫЙ РЕКОРД!",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Счёт: ${state.score}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Уровень: ${state.level}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Линий: ${state.lines}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Играть снова", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Диалоговое окно с классическими правилами Тетриса. */
@Composable
private fun TetrisRulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Правила Тетриса", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "🎯 Цель игры",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Заполняйте фигурами горизонтальные линии по 10 клеток. Полностью заполненная линия сгорает и приносит очки.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "🧩 Фигуры (7-Bag)",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Используются 7 классических типов тетрамино: I, O, T, S, Z, J, L. Они выпадают сериями из мешка (7-Bag), обеспечивая честный баланс фигур без длительных серий одинаковых блоков.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "🎮 Управление",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• Свайп влево / вправо или кнопки ◀ / ▶ — перемещение фигуры.\n" +
                            "• Тап по полю или кнопка ⟳ — поворот по часовой стрелке (с настенными отскоками wall kicks).\n" +
                            "• Свайп вниз или кнопка ▼ — мягкое опускание (Soft Drop, +1 очко за клетку).\n" +
                            "• Кнопка СБРОС — мгновенное падение (Hard Drop, +2 очка за клетку).\n" +
                            "• Кнопка HOLD — отложить фигуру в запас или поменять на отложенную (1 раз за ход).",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "🏆 Начисление очков",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• 1 линия: 100 × уровень\n" +
                            "• 2 линии: 300 × уровень\n" +
                            "• 3 линии: 500 × уровень\n" +
                            "• 4 линии (Тетрис!): 800 × уровень\n" +
                            "Каждые 10 очищенных линий уровень повышается, увеличивая скорость падения фигур.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "👻 Призрачная фигура",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Полупрозрачный силуэт на дне стакана показывает точное место, куда приземлится фигура.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Понятно", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
