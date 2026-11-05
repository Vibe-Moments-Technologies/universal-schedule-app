package com.jetbrains.kmpapp.screens.games

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Правила «Сапёра» проверяются без Compose: генерация мин, первый ход,
 * раскрытие области, флаги, победа и поражение.
 */
class MinesweeperEngineTest {

    private val standard = MinesweeperDifficulty.STANDARD

    @Test
    fun standardFieldHasExactlyConfiguredMines() {
        val board = MinesweeperEngine.generate(
            width = standard.width,
            height = standard.height,
            mines = standard.mines,
            random = Random(1)
        )
        assertEquals(standard.width * standard.height, board.cells.size)
        assertEquals(10, board.mineCount)
    }

    @Test
    fun difficultyOrderAndSizesMatchRequestedVariants() {
        // Порядок в меню — часть договорённости с игроком, как и размеры полей.
        assertEquals(
            listOf(
                Triple("KINDERGARTEN", 5, 5),
                Triple("SCHOOL", 7, 7),
                Triple("STANDARD", 9, 9),
                Triple("BACHELOR", 16, 16),
                Triple("SPECIALIST", 30, 16),
                Triple("MASTER", 25, 25),
                Triple("POSTGRAD", 50, 50),
                Triple("RETIREE", 64, 48)
            ),
            MinesweeperDifficulty.entries.map { Triple(it.name, it.width, it.height) }
        )
        assertEquals(
            listOf(3, 5, 10, 40, 99, 100, 250, 777),
            MinesweeperDifficulty.entries.map { it.mines }
        )
    }

    @Test
    fun everyDifficultyGeneratesExactlyItsConfiguredField() {
        MinesweeperDifficulty.entries.forEach { option ->
            val safeX = option.width / 2
            val safeY = option.height / 2
            val board = MinesweeperEngine.generate(
                width = option.width,
                height = option.height,
                mines = option.mines,
                safeX = safeX,
                safeY = safeY,
                random = Random(option.ordinal + 1)
            )
            assertEquals(
                option.width * option.height,
                board.cells.size,
                "Число клеток: ${option.name}"
            )
            assertEquals(option.mines, board.mineCount, "Число мин: ${option.name}")
            assertFalse(
                board[safeX, safeY].isMine,
                "Первый клик попал на мину: ${option.name}"
            )
            // Вокруг первого клика мины стоять не могут — иначе не откроется область.
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val nx = safeX + dx
                    val ny = safeY + dy
                    if (nx in 0 until option.width && ny in 0 until option.height) {
                        assertFalse(
                            board[nx, ny].isMine,
                            "Мина в защищённой зоне ${option.name}: $nx,$ny"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun everyDifficultyKeepsItsOwnRecordKey() {
        val keys = MinesweeperDifficulty.entries.map { GamesViewModel.recordKey(it) }
        assertEquals(keys.size, keys.toSet().size, "Ключи рекордов совпадают")
        assertEquals(
            "minesweeperSTANDARD",
            GamesViewModel.recordKey(MinesweeperDifficulty.STANDARD)
        )
    }

    @Test
    fun generationIsRandom() {
        val layouts = buildSet {
            repeat(40) { seed ->
                val board = MinesweeperEngine.generate(9, 9, 10, random = Random(seed))
                add(board.cells.filter { it.isMine }.map { it.y * 9 + it.x }.toSet())
            }
        }
        // Случайная расстановка обязана давать разные поля, а не одно и то же.
        assertTrue(layouts.size > 1, "Все партии оказались одинаковыми")
    }

    @Test
    fun firstClickAreaIsAlwaysMineFree() {
        repeat(81) { index ->
            val x = index % 9
            val y = index / 9
            val board = MinesweeperEngine.generate(9, 9, 10, safeX = x, safeY = y, random = Random(index))
            assertEquals(10, board.mineCount)
            assertFalse(board[x, y].isMine, "Первый клик попал на мину: $x,$y")
            // Вокруг первого клика тоже без мин — открывается область.
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0..8 && ny in 0..8) {
                        assertFalse(
                            board[nx, ny].isMine,
                            "Мина в защищённой зоне первого клика: $nx,$ny"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun adjacentCountsAreCorrect() {
        val board = MinesweeperEngine.generate(9, 9, 10, safeX = 4, safeY = 4, random = Random(7))
        board.cells.filter { !it.isMine }.forEach { cell ->
            val expected = board.cells.count { other ->
                other.isMine && other != cell &&
                    kotlin.math.abs(other.x - cell.x) <= 1 &&
                    kotlin.math.abs(other.y - cell.y) <= 1
            }
            assertEquals(expected, cell.adjacentMines, "Неверный счётчик у ${cell.x},${cell.y}")
        }
    }

    @Test
    fun revealOfZeroOpensWholeArea() {
        // Поле с одной миной в углу: с нуля должна раскрыться большая область.
        val board = MinesweeperEngine.generate(9, 9, 1, random = Random(3))
        val zero = board.cells.first { !it.isMine && it.adjacentMines == 0 }
        val opened = MinesweeperEngine.reveal(board, zero.x, zero.y)
        assertTrue(opened[zero.x, zero.y].isRevealed)
        // Все клетки без мин, кроме тех, что отрезаны ненулевыми числами.
        val revealedSafe = opened.revealedSafeCount
        assertTrue(revealedSafe > 1, "Область с нуля не раскрылась")
        assertTrue(opened.isWon || opened.revealedSafeCount > 1)
    }

    @Test
    fun revealOfMineLosesAndShowsAllMines() {
        val board = MinesweeperEngine.generate(9, 9, 10, random = Random(11))
        val mine = board.cells.first { it.isMine }
        val opened = MinesweeperEngine.reveal(board, mine.x, mine.y)
        assertTrue(opened[mine.x, mine.y].isMine)
        assertEquals(10, opened.cells.count { it.isMine && it.isRevealed })
        assertFalse(opened.isWon)
    }

    @Test
    fun revealOfFlaggedCellDoesNothing() {
        val board = MinesweeperEngine.generate(9, 9, 10, safeX = 4, safeY = 4, random = Random(5))
        val target = board.cells.first { !it.isMine }
        val flagged = MinesweeperEngine.toggleFlag(board, target.x, target.y)
        assertTrue(flagged[target.x, target.y].isFlagged)
        val opened = MinesweeperEngine.reveal(flagged, target.x, target.y)
        assertFalse(opened[target.x, target.y].isRevealed)
    }

    @Test
    fun flagTogglesAndCountsDownRemainingMines() {
        var board = MinesweeperEngine.generate(9, 9, 10, safeX = 4, safeY = 4, random = Random(2))
        assertEquals(10, board.remainingMines)
        val mine = board.cells.first { it.isMine }
        board = MinesweeperEngine.toggleFlag(board, mine.x, mine.y)
        assertEquals(9, board.remainingMines)
        board = MinesweeperEngine.toggleFlag(board, mine.x, mine.y)
        assertEquals(10, board.remainingMines)
    }

    @Test
    fun flagsAreNotAcceptedOnRevealedCells() {
        val board = MinesweeperEngine.generate(9, 9, 10, safeX = 0, safeY = 0, random = Random(9))
        val opened = MinesweeperEngine.reveal(board, 0, 0)
        val revealedCell = opened.cells.first { it.isRevealed }
        val afterFlag = MinesweeperEngine.toggleFlag(opened, revealedCell.x, revealedCell.y)
        assertFalse(afterFlag[revealedCell.x, revealedCell.y].isFlagged)
    }

    @Test
    fun openingEverySafeCellWins() {
        // 4×4 с 3 минами: открываем всё, кроме мин.
        var board = MinesweeperEngine.generate(4, 4, 3, random = Random(4))
        assertFalse(board.isWon)
        board.cells.filter { !it.isMine }.forEach { cell ->
            board = MinesweeperEngine.reveal(board, cell.x, cell.y)
        }
        assertTrue(board.isWon, "Победа не засчитана при открытии всех чистых клеток")
        assertEquals(board.safeCellCount, board.revealedSafeCount)
    }

    @Test
    fun flagRemainingMinesMarksMinesOnWin() {
        var board = MinesweeperEngine.generate(4, 4, 3, random = Random(4))
        board.cells.filter { !it.isMine }.forEach { cell ->
            board = MinesweeperEngine.reveal(board, cell.x, cell.y)
        }
        val finished = MinesweeperEngine.flagRemainingMines(board)
        assertEquals(3, finished.cells.count { it.isFlagged })
        assertTrue(finished.cells.filter { it.isMine }.all { it.isFlagged })
    }

    @Test
    fun outOfBoundsTapsAreIgnored() {
        val board = MinesweeperEngine.generate(9, 9, 10, random = Random(6))
        assertEquals(board, MinesweeperEngine.reveal(board, -1, 0))
        assertEquals(board, MinesweeperEngine.reveal(board, 0, 9))
        assertEquals(board, MinesweeperEngine.toggleFlag(board, 99, 99))
    }

    @Test
    fun generationNeverExceedsFieldCapacity() {
        // Запрос больше мин, чем помещается, не должен ронять игру.
        // Без безопасной зоны на 3×3 помещаются все 9 клеток.
        val full = MinesweeperEngine.generate(3, 3, 500, random = Random(1))
        assertEquals(9, full.cells.size)
        assertEquals(9, full.mineCount)
        // Защищённая зона 3×3 вокруг центра закрывает всё поле — мин будет 0.
        val covered = MinesweeperEngine.generate(3, 3, 500, safeX = 1, safeY = 1, random = Random(1))
        assertEquals(9, covered.cells.size)
        assertEquals(0, covered.mineCount)
        assertFalse(covered[1, 1].isMine)
        // Обычное поле всегда получает ровно нужное число мин.
        repeat(50) { seed ->
            val board = MinesweeperEngine.generate(9, 9, 10, safeX = 4, safeY = 4, random = Random(seed))
            assertEquals(81, board.cells.size)
            assertEquals(10, board.mineCount)
        }
    }

    @Test
    fun adjacentCountsAreCorrectOnEveryGeneratedBoard() {
        // Счётчики проверяются на многих расстановках сразу: ошибка в подсчёте
        // соседей сдвигает всю игру, даже если на одном поле повезло.
        repeat(100) { seed ->
            val board = MinesweeperEngine.generate(9, 9, 10, safeX = 4, safeY = 4, random = Random(seed))
            board.cells.forEach { cell ->
                val expected = if (cell.isMine) {
                    0
                } else {
                    board.cells.count { other ->
                        other.isMine &&
                            kotlin.math.abs(other.x - cell.x) <= 1 &&
                            kotlin.math.abs(other.y - cell.y) <= 1
                    }
                }
                assertEquals(expected, cell.adjacentMines, "Счётчик у ${cell.x},${cell.y}, seed $seed")
            }
        }
    }

    @Test
    fun flagsNeverLandOnNonMinesWhenPlacedByCount() {
        // Имитация игры: бот ставит флаги только по правилу «число мин равно
        // числу неизвестных соседей» и открывает только доказанно чистые клетки.
        // Все флаги обязаны оказаться на минах.
        repeat(200) { seed ->
            val random = Random(seed * 31)
            val width = 9
            val height = 9
            val startX = random.nextInt(width)
            val startY = random.nextInt(height)
            val initial = MinesweeperEngine.generate(width, height, 10, startX, startY, random)
            val mineSet = initial.cells.filter { it.isMine }.map { it.y * width + it.x }.toSet()
            var board = MinesweeperEngine.reveal(initial, startX, startY)
            assertFalse(board[startX, startY].isMine, "Первый клик небезопасен, seed $seed")
            var guard = 0
            while (!board.isWon && guard < 400) {
                guard++
                var moved = false
                for (cell in board.cells.filter { it.isRevealed }) {
                    val unknown = mutableListOf<MinesweeperCell>()
                    var flaggedAround = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val nx = cell.x + dx
                            val ny = cell.y + dy
                            if (!board.inBounds(nx, ny)) continue
                            val neighbour = board[nx, ny]
                            when {
                                neighbour.isFlagged -> flaggedAround++
                                !neighbour.isRevealed -> unknown.add(neighbour)
                            }
                        }
                    }
                    if (unknown.isEmpty()) continue
                    val remaining = cell.adjacentMines - flaggedAround
                    when (remaining) {
                        0 -> unknown.forEach {
                            board = MinesweeperEngine.reveal(board, it.x, it.y)
                            moved = true
                        }
                        unknown.size -> unknown.forEach {
                            board = MinesweeperEngine.toggleFlag(board, it.x, it.y)
                            moved = true
                        }
                    }
                }
                if (!moved) {
                    // Правил не хватило: случайный ход, партия может проиграть.
                    val target = board.cells.filter { !it.isRevealed && !it.isFlagged }
                    if (target.isEmpty()) break
                    val guess = target[random.nextInt(target.size)]
                    board = MinesweeperEngine.reveal(board, guess.x, guess.y)
                }
                // Вскрытая мина — конец партии: дальше выводы бота
                // некорректны, потому что вскрытые мины не считаются ни
                // флагами, ни неизвестными, и правило «осталось == неизвестных»
                // начало бы ставить флаги на чистые клетки. Движок при этом
                // работает правильно — ошибка была в цикле бота.
                if (board.cells.any { it.isRevealed && it.isMine }) break
            }
            val wrongFlags = board.cells.filter { it.isFlagged && (it.y * width + it.x) !in mineSet }
            assertTrue(wrongFlags.isEmpty(), "Флаг на чистой клетке, seed $seed")
            assertEquals(81, board.cells.size)
            assertEquals(10, board.mineCount)
            // Партия обязана закончиться либо победой, либо вскрытием всех мин.
            val exploded = board.cells.any { it.isRevealed && it.isMine }
            assertTrue(board.isWon || exploded, "Партия не завершилась, seed $seed")
            if (exploded) {
                assertEquals(10, board.cells.count { it.isRevealed && it.isMine })
            }
        }
    }
}