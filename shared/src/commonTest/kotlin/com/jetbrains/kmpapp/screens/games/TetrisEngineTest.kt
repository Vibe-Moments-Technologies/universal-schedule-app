package com.jetbrains.kmpapp.screens.games

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TetrisEngineTest {

    @Test
    fun sevenBagContainsAllUniqueTetrominoes() {
        repeat(20) { seed ->
            val bag = TetrisEngine.generate7Bag(Random(seed))
            assertEquals(7, bag.size)
            assertEquals(7, bag.toSet().size, "7-bag must contain each piece exactly once")
            assertTrue(bag.containsAll(TetrominoType.entries))
        }
    }

    @Test
    fun newGameInitializesCorrectly() {
        val (state, queue) = TetrisEngine.startNewGame(Random(42))
        assertEquals(TetrisStatus.PLAYING, state.status)
        assertEquals(0, state.score)
        assertEquals(0, state.lines)
        assertEquals(1, state.level)
        assertNotNull(state.currentPiece)
        assertNull(state.holdPiece)
        assertTrue(state.canHold)
        assertEquals(3, state.nextPieces.size)
        assertTrue(queue.size >= 7)
    }

    @Test
    fun movementLeftAndRightRespectsBoundaries() {
        val (initialState, _) = TetrisEngine.startNewGame(Random(1))
        var state = initialState

        // Try moving left many times
        repeat(10) {
            state = TetrisEngine.moveLeft(state)
        }
        val pieceLeft = state.currentPiece!!
        assertTrue(pieceLeft.x >= 0, "Piece X should not be negative: ${pieceLeft.x}")

        // Move right across the board
        repeat(15) {
            state = TetrisEngine.moveRight(state)
        }
        val pieceRight = checkNotNull(state.currentPiece)
        val blocks = pieceRight.type.getBlocks(pieceRight.rotation)
        assertTrue(blocks.all { pieceRight.x + it.x < TetrisEngine.BOARD_WIDTH }, "Piece must remain in bounds")
    }

    @Test
    fun rotationCyclesThroughFourStates() {
        val (initialState, _) = TetrisEngine.startNewGame(Random(2))
        var state = initialState
        val initialRot = state.currentPiece!!.rotation

        state = TetrisEngine.rotateClockwise(state)
        assertEquals((initialRot + 1) % 4, state.currentPiece!!.rotation)

        state = TetrisEngine.rotateClockwise(state)
        assertEquals((initialRot + 2) % 4, state.currentPiece!!.rotation)

        state = TetrisEngine.rotateClockwise(state)
        assertEquals((initialRot + 3) % 4, state.currentPiece!!.rotation)

        state = TetrisEngine.rotateClockwise(state)
        assertEquals(initialRot, state.currentPiece!!.rotation)
    }

    @Test
    fun ghostYIsCalculatedCorrectly() {
        val (state, _) = TetrisEngine.startNewGame(Random(5))
        val ghost = state.ghostY
        val piece = state.currentPiece!!
        assertTrue(ghost >= piece.y)

        // Drop to ghost position should be valid
        assertTrue(TetrisEngine.isValidPosition(state.board, piece.type, piece.x, ghost, piece.rotation))
        // One step further should be invalid (out of bounds or hitting bottom)
        assertFalse(TetrisEngine.isValidPosition(state.board, piece.type, piece.x, ghost + 1, piece.rotation))
    }

    @Test
    fun hardDropInstantlyLocksAndGivesPoints() {
        val (initialState, queue) = TetrisEngine.startNewGame(Random(3))
        val queueMutable = queue.toMutableList()
        val dropDist = initialState.ghostY - initialState.currentPiece!!.y

        val afterDrop = TetrisEngine.hardDrop(initialState, queueMutable, Random(3))
        assertEquals(dropDist * 2, afterDrop.score)
        assertNotNull(afterDrop.currentPiece)
        // Board now has 4 blocks filled
        val filledBlocks = afterDrop.board.grid.sumOf { row -> row.count { it != null } }
        assertEquals(4, filledBlocks)
    }

    @Test
    fun lineClearingCalculatesCorrectScoreAndRemovesLines() {
        // Prepare a board with 19 full lines except the top 1
        val grid = List(TetrisEngine.BOARD_HEIGHT) { y ->
            if (y == 19) {
                // Line 19 is missing one block at x=3 for an I piece
                List(TetrisEngine.BOARD_WIDTH) { x -> if (x != 3 && x != 4 && x != 5 && x != 6) TetrominoType.T else null }
            } else {
                List(TetrisEngine.BOARD_WIDTH) { null }
            }
        }
        val board = TetrisBoard(grid)
        val piece = ActivePiece(TetrominoType.I, x = 3, y = 18, rotation = 0)
        val state = TetrisState(
            board = board,
            currentPiece = piece,
            status = TetrisStatus.PLAYING,
            level = 2
        )
        val queue = mutableListOf(TetrominoType.O, TetrominoType.T, TetrominoType.S)

        val nextState = TetrisEngine.lockPiece(state, queue, Random(1))
        // Line 19 should have been filled and cleared!
        assertEquals(1, nextState.lines)
        // Single line cleared at level 2 gives 100 * 2 = 200 points
        assertEquals(200, nextState.score)
    }

    @Test
    fun tetrisFourLinesClearingGives800TimesLevelPoints() {
        // 4 lines (16, 17, 18, 19) filled except for column 3
        val grid = List(TetrisEngine.BOARD_HEIGHT) { y ->
            if (y >= 16) {
                List(TetrisEngine.BOARD_WIDTH) { x -> if (x == 3) null else TetrominoType.J }
            } else {
                List(TetrisEngine.BOARD_WIDTH) { null }
            }
        }
        val board = TetrisBoard(grid)
        // Drop vertical I piece into column 3
        // For I in rotation 1, x=1 -> column 1 + 2 = 3
        val piece = ActivePiece(TetrominoType.I, x = 1, y = 16, rotation = 1)
        val state = TetrisState(
            board = board,
            currentPiece = piece,
            status = TetrisStatus.PLAYING,
            level = 3
        )
        val queue = mutableListOf(TetrominoType.O, TetrominoType.T)

        val nextState = TetrisEngine.lockPiece(state, queue, Random(1))
        assertEquals(4, nextState.lines)
        // 4 lines at level 3 = 800 * 3 = 2400 points
        assertEquals(2400, nextState.score)
        // Board bottom rows are now cleared
        assertTrue(nextState.board.grid[19].all { it == null })
    }

    @Test
    fun holdSwapsPieceAndDisallowsDoubleHoldUntilLock() {
        val (state1, queue) = TetrisEngine.startNewGame(Random(10))
        val queueMutable = queue.toMutableList()
        val initialType = state1.currentPiece!!.type

        // First hold
        val state2 = TetrisEngine.hold(state1, queueMutable, Random(10))
        assertEquals(initialType, state2.holdPiece)
        assertFalse(state2.canHold)

        // Attempting to hold again immediately should do nothing
        val state3 = TetrisEngine.hold(state2, queueMutable, Random(10))
        assertEquals(state2, state3)

        // Locking piece restores canHold
        val lockedState = TetrisEngine.hardDrop(state3, queueMutable, Random(10))
        assertTrue(lockedState.canHold)
    }

    @Test
    fun gameOverTriggersWhenSpawnCollides() {
        // Заполняем верхние ряды ТАК, чтобы они не были полными: полностью
        // заполненный ряд очищается при любом lockPiece (стандартное
        // правило), и искусственно «полные» ряды в фикстуре исчезли бы до
        // проверки спауна. Оставляем колонку 0 пустой — ряды не завершены,
        // но клетки спауна O (колонки 4–5) заняты.
        val grid = List(TetrisEngine.BOARD_HEIGHT) { y ->
            if (y <= 2) {
                List(TetrisEngine.BOARD_WIDTH) { x -> if (x == 0) null else TetrominoType.O }
            } else {
                List(TetrisEngine.BOARD_WIDTH) { null }
            }
        }
        val board = TetrisBoard(grid)
        val piece = ActivePiece(TetrominoType.O, x = 4, y = 15)
        val state = TetrisState(
            board = board,
            currentPiece = piece,
            status = TetrisStatus.PLAYING
        )
        val queue = mutableListOf(TetrominoType.O)

        val nextState = TetrisEngine.lockPiece(state, queue, Random(1))
        assertEquals(TetrisStatus.GAME_OVER, nextState.status)
        assertNull(nextState.currentPiece)
    }

    @Test
    fun gravityIntervalDecreasesWithLevel() {
        val lvl1 = TetrisEngine.gravityIntervalMs(1)
        val lvl5 = TetrisEngine.gravityIntervalMs(5)
        val lvl10 = TetrisEngine.gravityIntervalMs(10)
        assertTrue(lvl1 > lvl5)
        assertTrue(lvl5 > lvl10)
        assertTrue(TetrisEngine.gravityIntervalMs(20) >= 90L)
    }

    @Test
    fun softDropIncreasesScoreByOnePerStep() {
        val (state, queue) = TetrisEngine.startNewGame(Random(7))
        val queueMutable = queue.toMutableList()
        val initialY = state.currentPiece!!.y
        val initialScore = state.score

        val (droppedState, locked) = TetrisEngine.softDrop(state, queueMutable, Random(7))
        assertFalse(locked)
        assertEquals(initialY + 1, droppedState.currentPiece!!.y)
        assertEquals(initialScore + 1, droppedState.score)
    }

    @Test
    fun tetrisControlVariantFromIdDefaultsToType1() {
        assertEquals(TetrisControlVariant.TYPE_1, TetrisControlVariant.fromId(null))
        assertEquals(TetrisControlVariant.TYPE_1, TetrisControlVariant.fromId(1))
        assertEquals(TetrisControlVariant.TYPE_2, TetrisControlVariant.fromId(2))
        assertEquals(TetrisControlVariant.TYPE_1, TetrisControlVariant.fromId(999))
    }

    @Test
    fun tetrisDifficultyFromIdDefaultsToEasy() {
        assertEquals(TetrisDifficulty.EASY, TetrisDifficulty.fromId(null))
        assertEquals(TetrisDifficulty.EASY, TetrisDifficulty.fromId("easy"))
        assertEquals(TetrisDifficulty.MEDIUM, TetrisDifficulty.fromId("medium"))
        assertEquals(TetrisDifficulty.HARD, TetrisDifficulty.fromId("hard"))
        assertEquals(TetrisDifficulty.EASY, TetrisDifficulty.fromId("unknown"))
    }

    @Test
    fun difficultyAffectsSpeedAcceleration() {
        val easyLvl5 = TetrisEngine.gravityIntervalMs(5, TetrisDifficulty.EASY)
        val medLvl5 = TetrisEngine.gravityIntervalMs(5, TetrisDifficulty.MEDIUM)
        val hardLvl5 = TetrisEngine.gravityIntervalMs(5, TetrisDifficulty.HARD)

        // Hard should drop faster (smaller interval) than Medium, and Medium faster than Easy
        assertTrue(easyLvl5 > medLvl5, "Medium speed interval ($medLvl5) must be faster than Easy ($easyLvl5)")
        assertTrue(medLvl5 > hardLvl5, "Hard speed interval ($hardLvl5) must be faster than Medium ($medLvl5)")
    }

    @Test
    fun difficultyAffectsLinesPerLevelProgression() {
        // Prepare board with row 19 full except x=0
        val grid = TetrisBoard.emptyGrid().map { it.toMutableList() }.toMutableList()
        for (x in 1 until TetrisEngine.BOARD_WIDTH) {
            grid[19][x] = TetrominoType.I
        }
        val board = TetrisBoard(grid)
        val piece = ActivePiece(TetrominoType.I, x = 0, y = 18, rotation = 0)

        // For EASY (10 lines per level): 5 lines + 1 cleared line = 6 lines -> level 1 + 6/10 = 1
        val stateEasy = TetrisState(board = board, currentPiece = piece, lines = 5, level = 1, status = TetrisStatus.PLAYING)
        val lockedEasy = TetrisEngine.lockPiece(stateEasy, mutableListOf(TetrominoType.O), Random(1), TetrisDifficulty.EASY)
        assertEquals(6, lockedEasy.lines)
        assertEquals(1, lockedEasy.level)

        // For MEDIUM (6 lines per level): 5 lines + 1 cleared line = 6 lines -> level 1 + 6/6 = 2
        val stateMed = TetrisState(board = board, currentPiece = piece, lines = 5, level = 1, status = TetrisStatus.PLAYING)
        val lockedMed = TetrisEngine.lockPiece(stateMed, mutableListOf(TetrominoType.O), Random(1), TetrisDifficulty.MEDIUM)
        assertEquals(6, lockedMed.lines)
        assertEquals(2, lockedMed.level)

        // For HARD (3 lines per level): 5 lines + 1 cleared line = 6 lines -> level 1 + 6/3 = 3
        val stateHard = TetrisState(board = board, currentPiece = piece, lines = 5, level = 1, status = TetrisStatus.PLAYING)
        val lockedHard = TetrisEngine.lockPiece(stateHard, mutableListOf(TetrominoType.O), Random(1), TetrisDifficulty.HARD)
        assertEquals(6, lockedHard.lines)
        assertEquals(3, lockedHard.level)
    }
}
