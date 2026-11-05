package com.jetbrains.kmpapp.screens.games

import kotlin.math.max
import kotlin.random.Random

/**
 * 7 классических типов тетрамино: I, O, T, S, Z, J, L.
 * Каждый тип имеет цвет (в формате ARGB 0xAARRGGBB), имя и матрицы смещений для 4 поворотов.
 */
enum class TetrominoType(
    val colorArgb: Long,
    val title: String
) {
    I(0xFF00E5FF, "I"),      // Бирюзовый
    O(0xFFFFD600, "O"),      // Жёлтый
    T(0xFFAA00FF, "T"),      // Фиолетовый
    S(0xFF00E676, "S"),      // Зелёный
    Z(0xFFFF1744, "Z"),      // Красный
    J(0xFF2979FF, "J"),      // Синий
    L(0xFFFF9100, "L");      // Оранжевый

    /**
     * Смещения 4 блоков тетрамино для заданного угла поворота [rotation] (0, 1, 2, 3).
     */
    fun getBlocks(rotation: Int): List<BlockOffset> {
        val rot = (rotation % 4 + 4) % 4
        return OFFSETS[this]!![rot]
    }

    companion object {
        private val OFFSETS: Map<TetrominoType, List<List<BlockOffset>>> = mapOf(
            I to listOf(
                listOf(BlockOffset(0, 1), BlockOffset(1, 1), BlockOffset(2, 1), BlockOffset(3, 1)),
                listOf(BlockOffset(2, 0), BlockOffset(2, 1), BlockOffset(2, 2), BlockOffset(2, 3)),
                listOf(BlockOffset(0, 2), BlockOffset(1, 2), BlockOffset(2, 2), BlockOffset(3, 2)),
                listOf(BlockOffset(1, 0), BlockOffset(1, 1), BlockOffset(1, 2), BlockOffset(1, 3))
            ),
            O to listOf(
                listOf(BlockOffset(0, 0), BlockOffset(1, 0), BlockOffset(0, 1), BlockOffset(1, 1)),
                listOf(BlockOffset(0, 0), BlockOffset(1, 0), BlockOffset(0, 1), BlockOffset(1, 1)),
                listOf(BlockOffset(0, 0), BlockOffset(1, 0), BlockOffset(0, 1), BlockOffset(1, 1)),
                listOf(BlockOffset(0, 0), BlockOffset(1, 0), BlockOffset(0, 1), BlockOffset(1, 1))
            ),
            T to listOf(
                listOf(BlockOffset(1, 0), BlockOffset(0, 1), BlockOffset(1, 1), BlockOffset(2, 1)),
                listOf(BlockOffset(1, 0), BlockOffset(1, 1), BlockOffset(2, 1), BlockOffset(1, 2)),
                listOf(BlockOffset(0, 1), BlockOffset(1, 1), BlockOffset(2, 1), BlockOffset(1, 2)),
                listOf(BlockOffset(1, 0), BlockOffset(0, 1), BlockOffset(1, 1), BlockOffset(1, 2))
            ),
            S to listOf(
                listOf(BlockOffset(1, 0), BlockOffset(2, 0), BlockOffset(0, 1), BlockOffset(1, 1)),
                listOf(BlockOffset(1, 0), BlockOffset(1, 1), BlockOffset(2, 1), BlockOffset(2, 2)),
                listOf(BlockOffset(1, 1), BlockOffset(2, 1), BlockOffset(0, 2), BlockOffset(1, 2)),
                listOf(BlockOffset(0, 0), BlockOffset(0, 1), BlockOffset(1, 1), BlockOffset(1, 2))
            ),
            Z to listOf(
                listOf(BlockOffset(0, 0), BlockOffset(1, 0), BlockOffset(1, 1), BlockOffset(2, 1)),
                listOf(BlockOffset(2, 0), BlockOffset(1, 1), BlockOffset(2, 1), BlockOffset(1, 2)),
                listOf(BlockOffset(0, 1), BlockOffset(1, 1), BlockOffset(1, 2), BlockOffset(2, 2)),
                listOf(BlockOffset(1, 0), BlockOffset(0, 1), BlockOffset(1, 1), BlockOffset(0, 2))
            ),
            J to listOf(
                listOf(BlockOffset(0, 0), BlockOffset(0, 1), BlockOffset(1, 1), BlockOffset(2, 1)),
                listOf(BlockOffset(1, 0), BlockOffset(2, 0), BlockOffset(1, 1), BlockOffset(1, 2)),
                listOf(BlockOffset(0, 1), BlockOffset(1, 1), BlockOffset(2, 1), BlockOffset(2, 2)),
                listOf(BlockOffset(1, 0), BlockOffset(1, 1), BlockOffset(0, 2), BlockOffset(1, 2))
            ),
            L to listOf(
                listOf(BlockOffset(2, 0), BlockOffset(0, 1), BlockOffset(1, 1), BlockOffset(2, 1)),
                listOf(BlockOffset(1, 0), BlockOffset(1, 1), BlockOffset(1, 2), BlockOffset(2, 2)),
                listOf(BlockOffset(0, 1), BlockOffset(1, 1), BlockOffset(2, 1), BlockOffset(0, 2)),
                listOf(BlockOffset(0, 0), BlockOffset(1, 0), BlockOffset(1, 1), BlockOffset(1, 2))
            )
        )
    }
}

/** Смещение отдельного блока внутри сетки фигуры. */
data class BlockOffset(val x: Int, val y: Int)

/**
 * Текущая активная фигура на доске.
 */
data class ActivePiece(
    val type: TetrominoType,
    val x: Int,
    val y: Int,
    val rotation: Int = 0
) {
    /** Список абсолютных координат (x, y) 4 блоков фигуры на доске. */
    fun absoluteBlocks(): List<Pair<Int, Int>> {
        return type.getBlocks(rotation).map { offset ->
            Pair(x + offset.x, y + offset.y)
        }
    }
}

/**
 * Неизменяемое состояние игрового поля Тетриса.
 * Сетка: [BOARD_HEIGHT] строк по [BOARD_WIDTH] столбцов.
 * Каждая ячейка хранит null (пусто) или [TetrominoType] упавшего блока.
 */
data class TetrisBoard(
    val grid: List<List<TetrominoType?>> = emptyGrid()
) {
    operator fun get(x: Int, y: Int): TetrominoType? {
        if (x !in 0 until TetrisEngine.BOARD_WIDTH || y !in 0 until TetrisEngine.BOARD_HEIGHT) return null
        return grid[y][x]
    }

    companion object {
        fun emptyGrid(): List<List<TetrominoType?>> =
            List(TetrisEngine.BOARD_HEIGHT) { List(TetrisEngine.BOARD_WIDTH) { null } }
    }
}

/** Статус игры. */
enum class TetrisStatus {
    READY,
    PLAYING,
    PAUSED,
    GAME_OVER
}

/**
 * Вариант расположения экранных кнопок управления Тетрисом.
 */
enum class TetrisControlVariant(
    val id: Int,
    val title: String,
    val description: String
) {
    TYPE_1(
        id = 1,
        title = "Управление 1",
        description = "Ряд 1: Влево, Вправо, Hold, Поворот\nРяд 2: Вниз, Сброс"
    ),
    TYPE_2(
        id = 2,
        title = "Управление 2",
        description = "Ряд 1: Влево, Вращать, Вправо\nРяд 2: Ниже, Hold, Упасть"
    );

    companion object {
        fun fromId(id: Int?): TetrisControlVariant = entries.firstOrNull { it.id == id } ?: TYPE_1
    }
}

/**
 * Сложность игры в Тетрис, определяющая темп нарастания скорости падения блоков.
 */
enum class TetrisDifficulty(
    val id: String,
    val title: String,
    val description: String,
    val linesPerLevel: Int,
    val speedStepMs: Long
) {
    EASY(
        id = "easy",
        title = "Легкая",
        description = "Стандартное плавное ускорение (уровень повышается каждые 10 линий)",
        linesPerLevel = 10,
        speedStepMs = 65L
    ),
    MEDIUM(
        id = "medium",
        title = "Средняя",
        description = "Умеренно быстрое ускорение (уровень повышается каждые 6 линий)",
        linesPerLevel = 6,
        speedStepMs = 85L
    ),
    HARD(
        id = "hard",
        title = "Сложная",
        description = "Быстрое ускорение (уровень повышается каждые 3 линии)",
        linesPerLevel = 3,
        speedStepMs = 110L
    );

    companion object {
        fun fromId(id: String?): TetrisDifficulty = entries.firstOrNull { it.id == id } ?: EASY
    }
}

/** Полный снимок состояния игры в Тетрис. */
data class TetrisState(
    val board: TetrisBoard = TetrisBoard(),
    val currentPiece: ActivePiece? = null,
    val nextPieces: List<TetrominoType> = emptyList(),
    val holdPiece: TetrominoType? = null,
    val canHold: Boolean = true,
    val score: Int = 0,
    val lines: Int = 0,
    val level: Int = 1,
    val status: TetrisStatus = TetrisStatus.READY,
    val ghostY: Int = 0
)

/**
 * Чистый движок Тетриса (Guideline-совместимый):
 * - Поле 10 × 20
 * - 7-Bag Randomizer (гарантирует честную генерацию всех 7 фигур)
 * - Повороты с настенными отскоками (wall kicks)
 * - Призрачная фигура (ghost piece / проекция падения)
 * - Очистка линий, подсчёт очков и уровней
 * - Механика удержания (Hold)
 */
object TetrisEngine {
    const val BOARD_WIDTH = 10
    const val BOARD_HEIGHT = 20

    // Смещения для настенных отскоков при повороте (wall kicks)
    private val ROTATION_KICKS = listOf(
        Pair(0, 0),   // Без сдвига
        Pair(-1, 0),  // 1 влево
        Pair(1, 0),   // 1 вправо
        Pair(-2, 0),  // 2 влево (для I)
        Pair(2, 0),   // 2 вправо (для I)
        Pair(0, -1)   // 1 вверх (отскок от пола/блока)
    )

    /** Создаёт новую партию с начальной генерацией фигур из 7-bag. */
    fun startNewGame(random: Random = Random.Default): Pair<TetrisState, List<TetrominoType>> {
        val bag = generate7Bag(random)
        val firstPieceType = bag.first()
        val remainingQueue = bag.drop(1).toMutableList()

        if (remainingQueue.size < 7) {
            remainingQueue.addAll(generate7Bag(random))
        }

        val spawnX = spawnXFor(firstPieceType)
        val spawnY = spawnYFor(firstPieceType)
        val initialPiece = ActivePiece(firstPieceType, spawnX, spawnY, rotation = 0)
        val board = TetrisBoard()
        val ghost = calculateGhostY(board, initialPiece)

        val state = TetrisState(
            board = board,
            currentPiece = initialPiece,
            nextPieces = remainingQueue.take(3),
            holdPiece = null,
            canHold = true,
            score = 0,
            lines = 0,
            level = 1,
            status = TetrisStatus.PLAYING,
            ghostY = ghost
        )

        return Pair(state, remainingQueue)
    }

    /** 7-bag рандомизатор: перестановка всех 7 типов. */
    fun generate7Bag(random: Random = Random.Default): List<TetrominoType> {
        return TetrominoType.entries.shuffled(random)
    }

    fun spawnXFor(type: TetrominoType): Int = when (type) {
        TetrominoType.O -> 4
        TetrominoType.I -> 3
        else -> 3
    }

    fun spawnYFor(type: TetrominoType): Int = 0

    /** Проверяет, валидна ли позиция фигуры на доске (в пределах границ и без коллизий с установленными блоками). */
    fun isValidPosition(
        board: TetrisBoard,
        type: TetrominoType,
        x: Int,
        y: Int,
        rotation: Int
    ): Boolean {
        val blocks = type.getBlocks(rotation)
        for (b in blocks) {
            val bx = x + b.x
            val by = y + b.y
            if (bx !in 0 until BOARD_WIDTH) return false
            if (by >= BOARD_HEIGHT) return false
            if (by >= 0) {
                if (board[bx, by] != null) return false
            }
        }
        return true
    }

    /** Рассчитывает координату Y для призрачной фигуры (ghost piece). */
    fun calculateGhostY(board: TetrisBoard, piece: ActivePiece): Int {
        var testY = piece.y
        while (isValidPosition(board, piece.type, piece.x, testY + 1, piece.rotation)) {
            testY++
        }
        return testY
    }

    /** Сдвиг влево. */
    fun moveLeft(state: TetrisState): TetrisState {
        val piece = state.currentPiece ?: return state
        if (state.status != TetrisStatus.PLAYING) return state
        if (isValidPosition(state.board, piece.type, piece.x - 1, piece.y, piece.rotation)) {
            val moved = piece.copy(x = piece.x - 1)
            val ghost = calculateGhostY(state.board, moved)
            return state.copy(currentPiece = moved, ghostY = ghost)
        }
        return state
    }

    /** Сдвиг вправо. */
    fun moveRight(state: TetrisState): TetrisState {
        val piece = state.currentPiece ?: return state
        if (state.status != TetrisStatus.PLAYING) return state
        if (isValidPosition(state.board, piece.type, piece.x + 1, piece.y, piece.rotation)) {
            val moved = piece.copy(x = piece.x + 1)
            val ghost = calculateGhostY(state.board, moved)
            return state.copy(currentPiece = moved, ghostY = ghost)
        }
        return state
    }

    /** Поворот по часовой стрелке с настенными отскоками. */
    fun rotateClockwise(state: TetrisState): TetrisState {
        val piece = state.currentPiece ?: return state
        if (state.status != TetrisStatus.PLAYING) return state
        val targetRotation = (piece.rotation + 1) % 4

        for (kick in ROTATION_KICKS) {
            val testX = piece.x + kick.first
            val testY = piece.y + kick.second
            if (isValidPosition(state.board, piece.type, testX, testY, targetRotation)) {
                val rotated = piece.copy(x = testX, y = testY, rotation = targetRotation)
                val ghost = calculateGhostY(state.board, rotated)
                return state.copy(currentPiece = rotated, ghostY = ghost)
            }
        }
        return state
    }

    /**
     * Мягкое опускание (Soft Drop) на одну клетку вниз.
     * Возвращает обновлённое состояние и флаг, была ли фигура заблокирована на дне.
     */
    fun softDrop(
        state: TetrisState,
        queue: MutableList<TetrominoType>,
        random: Random = Random.Default,
        difficulty: TetrisDifficulty = TetrisDifficulty.EASY
    ): Pair<TetrisState, Boolean> {
        val piece = state.currentPiece ?: return Pair(state, false)
        if (state.status != TetrisStatus.PLAYING) return Pair(state, false)

        if (isValidPosition(state.board, piece.type, piece.x, piece.y + 1, piece.rotation)) {
            val moved = piece.copy(y = piece.y + 1)
            return Pair(
                state.copy(
                    currentPiece = moved,
                    score = state.score + 1,
                    ghostY = calculateGhostY(state.board, moved)
                ),
                false
            )
        } else {
            // Фигура достигла дна/препятствия — фиксируем её
            val locked = lockPiece(state, queue, random, difficulty)
            return Pair(locked, true)
        }
    }

    /** Шаг гравитации (автоматическое падение). Очки за soft drop не начисляются. */
    fun gravityTick(
        state: TetrisState,
        queue: MutableList<TetrominoType>,
        random: Random = Random.Default,
        difficulty: TetrisDifficulty = TetrisDifficulty.EASY
    ): TetrisState {
        val piece = state.currentPiece ?: return state
        if (state.status != TetrisStatus.PLAYING) return state

        if (isValidPosition(state.board, piece.type, piece.x, piece.y + 1, piece.rotation)) {
            val moved = piece.copy(y = piece.y + 1)
            return state.copy(
                currentPiece = moved,
                ghostY = calculateGhostY(state.board, moved)
            )
        } else {
            return lockPiece(state, queue, random, difficulty)
        }
    }

    /**
     * Жёсткое падение (Hard Drop): мгновенно опускает фигуру на ghostY,
     * начисляет +2 очка за каждую пройденную клетку и фиксирует фигуру.
     */
    fun hardDrop(
        state: TetrisState,
        queue: MutableList<TetrominoType>,
        random: Random = Random.Default,
        difficulty: TetrisDifficulty = TetrisDifficulty.EASY
    ): TetrisState {
        val piece = state.currentPiece ?: return state
        if (state.status != TetrisStatus.PLAYING) return state

        val targetY = state.ghostY
        val dropDistance = max(0, targetY - piece.y)
        val droppedPiece = piece.copy(y = targetY)
        val stateWithDropped = state.copy(
            currentPiece = droppedPiece,
            score = state.score + dropDistance * 2
        )
        return lockPiece(stateWithDropped, queue, random, difficulty)
    }

    /**
     * Удержание (Hold): откладывает текущую фигуру или меняет её на отложенную.
     * Доступно один раз за ход до фиксации фигуры.
     */
    fun hold(
        state: TetrisState,
        queue: MutableList<TetrominoType>,
        random: Random = Random.Default
    ): TetrisState {
        val piece = state.currentPiece ?: return state
        if (state.status != TetrisStatus.PLAYING || !state.canHold) return state

        val currentType = piece.type
        val heldType = state.holdPiece

        if (heldType == null) {
            // Первая задержка: берём следующую фигуру из очереди
            if (queue.size < 7) {
                queue.addAll(generate7Bag(random))
            }
            val nextType = queue.removeAt(0)
            val spawnX = spawnXFor(nextType)
            val spawnY = spawnYFor(nextType)
            val newPiece = ActivePiece(nextType, spawnX, spawnY)

            val gameOver = !isValidPosition(state.board, nextType, spawnX, spawnY, 0)

            return state.copy(
                currentPiece = if (gameOver) null else newPiece,
                holdPiece = currentType,
                canHold = false,
                nextPieces = queue.take(3),
                status = if (gameOver) TetrisStatus.GAME_OVER else TetrisStatus.PLAYING,
                ghostY = if (gameOver) 0 else calculateGhostY(state.board, newPiece)
            )
        } else {
            // Обмен с текущей удержанной
            val spawnX = spawnXFor(heldType)
            val spawnY = spawnYFor(heldType)
            val newPiece = ActivePiece(heldType, spawnX, spawnY)
            val gameOver = !isValidPosition(state.board, heldType, spawnX, spawnY, 0)

            return state.copy(
                currentPiece = if (gameOver) null else newPiece,
                holdPiece = currentType,
                canHold = false,
                status = if (gameOver) TetrisStatus.GAME_OVER else TetrisStatus.PLAYING,
                ghostY = if (gameOver) 0 else calculateGhostY(state.board, newPiece)
            )
        }
    }

    /**
     * Фиксирует фигуру в сетке доски, проверяет заполненные линии,
     * начисляет очки и спавнит следующую фигуру.
     */
    fun lockPiece(
        state: TetrisState,
        queue: MutableList<TetrominoType>,
        random: Random = Random.Default,
        difficulty: TetrisDifficulty = TetrisDifficulty.EASY
    ): TetrisState {
        val piece = state.currentPiece ?: return state

        // 1. Запись блоков в сетку
        val mutableGrid = state.board.grid.map { it.toMutableList() }.toMutableList()
        for (b in piece.type.getBlocks(piece.rotation)) {
            val bx = piece.x + b.x
            val by = piece.y + b.y
            if (bx in 0 until BOARD_WIDTH && by in 0 until BOARD_HEIGHT) {
                mutableGrid[by][bx] = piece.type
            }
        }

        // 2. Поиск и удаление заполненных линий
        val remainingRows = mutableGrid.filter { row -> row.any { it == null } }
        val clearedCount = BOARD_HEIGHT - remainingRows.size

        val newGrid = if (clearedCount > 0) {
            val emptyRows = List(clearedCount) { List(BOARD_WIDTH) { null } }
            emptyRows + remainingRows
        } else {
            mutableGrid
        }

        val newBoard = TetrisBoard(newGrid)

        // 3. Расчёт очков (Guideline)
        val lineScore = when (clearedCount) {
            1 -> 100 * state.level
            2 -> 300 * state.level
            3 -> 500 * state.level
            4 -> 800 * state.level
            else -> 0
        }

        val totalLines = state.lines + clearedCount
        val newLevel = 1 + totalLines / difficulty.linesPerLevel
        val totalScore = state.score + lineScore

        // 4. Добор очереди фигур
        if (queue.size < 7) {
            queue.addAll(generate7Bag(random))
        }
        val nextType = queue.removeAt(0)
        val spawnX = spawnXFor(nextType)
        val spawnY = spawnYFor(nextType)
        val nextPiece = ActivePiece(nextType, spawnX, spawnY)

        // 5. Проверка на Game Over
        val isGameOver = !isValidPosition(newBoard, nextType, spawnX, spawnY, 0)

        return state.copy(
            board = newBoard,
            currentPiece = if (isGameOver) null else nextPiece,
            nextPieces = queue.take(3),
            canHold = true,
            score = totalScore,
            lines = totalLines,
            level = newLevel,
            status = if (isGameOver) TetrisStatus.GAME_OVER else TetrisStatus.PLAYING,
            ghostY = if (isGameOver) 0 else calculateGhostY(newBoard, nextPiece)
        )
    }

    /**
     * Скорость падения (в миллисекундах) в зависимости от уровня и выбранной сложности.
     *
     * Пол — 90 мс, а не 80: при EASY-шаге 65 мс формула без пола уходит
     * глубоко в минус уже к 12-му уровню (750 − 11×65 = 35), и различия
     * сложностей ниже ~10-го уровня стираются. 90 мс — соглашение из
     * `gravityIntervalDecreasesWithLevel`: игра остаётся проходимой
     * на высоких уровнях, но заметно быстрее, чем на первом.
     */
    fun gravityIntervalMs(
        level: Int,
        difficulty: TetrisDifficulty = TetrisDifficulty.EASY
    ): Long {
        return max(90L, 750L - (level - 1) * difficulty.speedStepMs)
    }
}
