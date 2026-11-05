package com.jetbrains.kmpapp.screens.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetbrains.kmpapp.data.model.DateUtils
import com.jetbrains.kmpapp.data.storage.GameRecord
import com.jetbrains.kmpapp.data.storage.GamesStorage
import com.jetbrains.kmpapp.data.storage.SavedMinesweeperCell
import com.jetbrains.kmpapp.data.storage.SavedMinesweeperGame
import com.jetbrains.kmpapp.data.storage.TetrisRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

/**
 * Раздел «Игры»: меню выбора игры и состояние партии в «Сапере».
 * Вся логика правил — в [MinesweeperEngine], здесь только хранение состояния,
 * таймер и рекорды.
 */
class GamesViewModel(private val gamesStorage: GamesStorage) : ViewModel() {

    /** Открытая игра (null — корневое меню «Игры» со списком игр). */
    enum class Game {
        /** Меню вариантов «Сапера». */
        SAPER_MENU,

        /** Партия в «Сапере». */
        MINESWEEPER,

        /** Партия в «Тетрис». */
        TETRIS;

        /** Слой «под» текущим: поле под собой держит меню вариантов «Сапера». */
        val parent: Game?
            get() = when (this) {
                MINESWEEPER -> SAPER_MENU
                SAPER_MENU -> null
                TETRIS -> null
            }
    }

    private val _activeGame = MutableStateFlow<Game?>(null)
    val activeGame: StateFlow<Game?> = _activeGame.asStateFlow()

    private val _difficulty = MutableStateFlow(MinesweeperDifficulty.STANDARD)
    val difficulty: StateFlow<MinesweeperDifficulty> = _difficulty.asStateFlow()

    /** null, пока не сделан первый ход: мины раскладываются в момент клика. */
    private val _board = MutableStateFlow<MinesweeperBoard?>(null)
    val board: StateFlow<MinesweeperBoard?> = _board.asStateFlow()

    private val _status = MutableStateFlow(MinesweeperStatus.READY)
    val status: StateFlow<MinesweeperStatus> = _status.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    /** Режим флага: тап по клетке ставит флаг, а не открывает её. */
    private val _flagMode = MutableStateFlow(false)
    val flagMode: StateFlow<Boolean> = _flagMode.asStateFlow()

    // Состояние Тетриса
    private val _tetrisState = MutableStateFlow(TetrisState())
    val tetrisState: StateFlow<TetrisState> = _tetrisState.asStateFlow()

    val tetrisRecord: StateFlow<TetrisRecord?> = gamesStorage.tetrisRecord

    private var tetrisQueue: MutableList<TetrominoType> = mutableListOf()
    private var tetrisJob: Job? = null

    /** Настройки «Сапера» из хранилища: доступны и с экрана настроек. */
    val boardVertical: StateFlow<Boolean> = gamesStorage.boardVertical
    val smallCellHintEnabled: StateFlow<Boolean> = gamesStorage.smallCellHintEnabled

    fun setBoardVertical(enabled: Boolean) = gamesStorage.setBoardVertical(enabled)
    fun setSmallCellHintEnabled(enabled: Boolean) = gamesStorage.setSmallCellHintEnabled(enabled)

    /** Настройки «Тетриса» из хранилища: вариант управления и сложность. */
    val tetrisControlVariant: StateFlow<TetrisControlVariant> = gamesStorage.tetrisControlVariant
    val tetrisDifficulty: StateFlow<TetrisDifficulty> = gamesStorage.tetrisDifficulty

    fun setTetrisControlVariant(variant: TetrisControlVariant) = gamesStorage.setTetrisControlVariant(variant)
    fun setTetrisDifficulty(difficulty: TetrisDifficulty) = gamesStorage.setTetrisDifficulty(difficulty)

    /**
     * «Тема Error». Активна, пока существует хоть один «сломанный» рекорд
     * (0 с) в любом из вариантов. Это не состояние партии, а глобальное
     * состояние приложения: вернувшись из игры в меню выбора сложности,
     * тема не сбрасывается, а остаётся на всём экране. Снимается только
     * кнопкой «Починить ошибку» (удаляет рекорды 0 с).
     */
    val errorActive: StateFlow<Boolean> = gamesStorage.records
        .map { records -> records.values.any { it.seconds == 0 } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Клетка, на которой подорвались: для каскадной анимации вскрытия мин. */
    private val _boomIndex = MutableStateFlow<Int?>(null)
    val boomIndex: StateFlow<Int?> = _boomIndex.asStateFlow()

    /**
     * Рекорды всех вариантов «Сапера»: ключ — [recordKey] варианта,
     * значение — время и дата постановки. Меню показывает рекорд каждой
     * строки сразу, поэтому все ключи прогружаются один раз при старте.
     */
    val records: StateFlow<Map<String, GameRecord>> = gamesStorage.records

    private var timerJob: Job? = null

    init {
        // Чтение с устройства не блокирует главный поток.
        viewModelScope.launch(Dispatchers.Default) {
            MinesweeperDifficulty.entries.forEach { gamesStorage.record(recordKey(it)) }
        }
    }

    // Навигация

    /** Эффективные размеры поля с учётом настройки «вертикальная доска». */
    private val boardW: Int
        get() = _board.value?.width ?: currentW

    private val boardH: Int
        get() = _board.value?.height ?: currentH

    private var currentW = MinesweeperDifficulty.STANDARD.width
    private var currentH = MinesweeperDifficulty.STANDARD.height

    private fun orientedWidth(value: MinesweeperDifficulty): Int =
        if (gamesStorage.boardVertical.value) value.height else value.width

    private fun orientedHeight(value: MinesweeperDifficulty): Int =
        if (gamesStorage.boardVertical.value) value.width else value.height

    /**
     * Открывает «Сапер» на выбранной сложности: поле и таймер сбрасываются.
     * Если под этот вариант сохранена незавершённая партия — она восстанавливается.
     * Тема Error активна глобально ([errorActive]) независимо от того, каким
     * путём открыта партия, — фон красится на уровне всего приложения.
     */
    fun openGame(value: MinesweeperDifficulty) {
        _difficulty.value = value
        currentW = orientedWidth(value)
        currentH = orientedHeight(value)
        resetGame()
        restoreSavedGame(value)
        _activeGame.value = Game.MINESWEEPER
    }

    /** Открывает меню вариантов «Сапера» из корневого меню «Игры». */
    fun openSaper() {
        _activeGame.value = Game.SAPER_MENU
    }

    /** Выход из «Сапера» в корневое меню «Игры»: незавершённая партия сохраняется. */
    fun closeGame() {
        finishFieldSession()
        _activeGame.value = null
    }

    /** Назад из партии в меню вариантов «Сапера»: незавершённая партия сохраняется. */
    fun backFromField() {
        finishFieldSession()
        _activeGame.value = Game.SAPER_MENU
    }

    private fun finishFieldSession() {
        stopTimer()
        persistGame()
        resetGame()
    }

    /** Новая партия на том же размере поля: сохранённая партия стирается. */
    fun restart() {
        gamesStorage.clearSavedGame()
        resetGame()
    }

    /**
     * «Починить ошибку»: стирает «сломанные» рекорды (0 с), из-за которых
     * включилась тема Error, и закрывает партию, если открыта. После этого
     * тема и тумблер «Починить ошибку» исчезают — режим снова нормальный.
     */
    fun fixError() {
        closeGame()
        gamesStorage.clearBrokenRecords()
    }

    private fun resetGame() {
        stopTimer()
        _board.value = null
        _status.value = MinesweeperStatus.READY
        _elapsedSeconds.value = 0
        _boomIndex.value = null
    }

    fun toggleFlagMode() {
        _flagMode.value = !_flagMode.value
    }

    // Ходы

    /** Тап по клетке: раскрывает её, либо ставит флаг в режиме флага. */
    fun onCellClick(index: Int) {
        val x = index % boardW
        val y = index / boardW
        if (_flagMode.value) {
            onCellLongClick(index)
            return
        }
        onCellClick(x, y)
    }

    /**
     * Тап по клетке: если это флаг — просто снимает его, не открывая мину под
     * ним. Если вокруг раскрытой цифры помечены все соседние мины — раскрываются
     * непомеченные соседи (chord). В противном случае клетка раскрывается
     * (или ставит флаг в режиме флага через [onCellClick]).
     */
    fun onCellClick(x: Int, y: Int) {
        val current = _board.value ?: return onFirstClick(x, y)
        if (_status.value == MinesweeperStatus.WON || _status.value == MinesweeperStatus.LOST) return
        // Тап по флагу не открывает клетку — флаг просто убирается.
        if (current[x, y].isFlagged) {
            toggleFlagAt(x, y)
            return
        }
        if (tryChord(current, x, y)) return
        revealAt(current, x, y)
    }

    /** Долгое нажатие: поставить или снять флаг. */
    fun onCellLongClick(index: Int) {
        toggleFlagAt(index % boardW, index / boardW)
    }

    /** Поставить или снять флаг на клетке; незавершённая партия сохраняется. */
    private fun toggleFlagAt(x: Int, y: Int) {
        val current = _board.value ?: return
        if (_status.value == MinesweeperStatus.WON || _status.value == MinesweeperStatus.LOST) return
        _board.value = MinesweeperEngine.toggleFlag(current, x, y)
        if (_status.value == MinesweeperStatus.PLAYING) persistGame()
    }

    /** Первый ход: мины раскладываются так, чтобы клик и его соседи были чистыми. */
    private fun onFirstClick(x: Int, y: Int) {
        if (x !in 0 until boardW || y !in 0 until boardH) return
        val difficulty = _difficulty.value
        val fresh = MinesweeperEngine.generate(
            width = boardW,
            height = boardH,
            mines = difficulty.mines,
            safeX = x,
            safeY = y,
            random = Random.Default
        )
        _board.value = fresh
        revealAt(fresh, x, y)
    }

    private fun revealAt(board: MinesweeperBoard, x: Int, y: Int) {
        val updated = MinesweeperEngine.reveal(board, x, y)
        _board.value = updated
        val cell = updated[x, y]
        _status.value = when {
            cell.isMine -> {
                _boomIndex.value = updated.index(x, y)
                MinesweeperStatus.LOST
            }
            updated.isWon -> MinesweeperStatus.WON
            else -> MinesweeperStatus.PLAYING
        }
        when (_status.value) {
            MinesweeperStatus.PLAYING -> {
                startTimer()
                persistGame()
            }
            MinesweeperStatus.WON -> {
                _board.value = MinesweeperEngine.flagRemainingMines(updated)
                stopTimer()
                saveResultIfBest()
                gamesStorage.clearSavedGame()
            }
            MinesweeperStatus.LOST -> {
                stopTimer()
                gamesStorage.clearSavedGame()
            }
            MinesweeperStatus.READY -> stopTimer()
        }
    }

    /** Двойной тап по числу: тот же раскрытие области, что и одинарный. */
    fun onCellDoubleClick(index: Int) {
        onCellClick(index % boardW, index / boardW)
    }

    /** Раскрытие вокруг числа: все флаги вокруг уже расставлены — открываем остальное. */
    private fun tryChord(board: MinesweeperBoard, x: Int, y: Int): Boolean {
        val cell = board[x, y]
        if (!cell.isRevealed || cell.adjacentMines == 0) return false
        val neighbours = ArrayList<MinesweeperCell>(8)
        forEachNeighbour(board, x, y) { neighbours.add(it) }
        if (neighbours.count { it.isFlagged } != cell.adjacentMines) return false
        neighbours.forEach { neighbour ->
            // Партия могла закончиться на предыдущем соседе — дальше не идём,
            // иначе статус вернулся бы из LOST/WON в PLAYING.
            val status = _status.value
            if (status == MinesweeperStatus.WON || status == MinesweeperStatus.LOST) return@forEach
            if (!neighbour.isRevealed && !neighbour.isFlagged) {
                revealAt(_board.value ?: board, neighbour.x, neighbour.y)
            }
        }
        return true
    }

    private inline fun forEachNeighbour(
        board: MinesweeperBoard,
        x: Int,
        y: Int,
        action: (MinesweeperCell) -> Unit
    ) {
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (board.inBounds(nx, ny)) action(board[nx, ny])
            }
        }
    }

    private fun saveResultIfBest() {
        val difficulty = _difficulty.value
        val seconds = _elapsedSeconds.value
        // «0 с» — тоже рекорд: поле, решённое с первого же нажатия, «ломает»
        // режим и превращает его кнопку в меню в «ERROR».
        gamesStorage.submitResult(
            key = recordKey(difficulty),
            seconds = seconds,
            achievedAtMillis = Clock.System.now().toEpochMilliseconds()
        )
    }

    // Сохранение партии

    /**
     * Пишет текущую партию в хранилище. Вызывается после каждого хода и при
     * выходе с экрана, пока партия не закончена (не победа и не поражение).
     */
    private fun persistGame() {
        val board = _board.value ?: return
        if (_status.value != MinesweeperStatus.PLAYING) return
        gamesStorage.saveGame(
            SavedMinesweeperGame(
                difficultyName = _difficulty.value.name,
                width = board.width,
                height = board.height,
                elapsedSeconds = _elapsedSeconds.value,
                statusName = MinesweeperStatus.PLAYING.name,
                boomIndex = _boomIndex.value,
                cells = board.cells.map { cell ->
                    SavedMinesweeperCell(
                        x = cell.x,
                        y = cell.y,
                        isMine = cell.isMine,
                        adjacentMines = cell.adjacentMines,
                        isRevealed = cell.isRevealed,
                        isFlagged = cell.isFlagged
                    )
                }
            )
        )
    }

    /**
     * Восстанавливает сохранённую партию этого варианта. Игнорирует чужие
     * варианты и «битые» снимки: в этом случае партия начинается заново.
     */
    private fun restoreSavedGame(value: MinesweeperDifficulty) {
        val saved = gamesStorage.loadSavedGame() ?: return
        if (saved.difficultyName != value.name) return
        if (saved.statusName != MinesweeperStatus.PLAYING.name) return
        val total = saved.width * saved.height
        if (total <= 0 || total > 64 * 48) return
        if (saved.cells.size != total) return
        val cells = arrayOfNulls<MinesweeperCell>(total)
        saved.cells.forEach { cell ->
            val index = cell.y * saved.width + cell.x
            if (index in cells.indices) {
                cells[index] = MinesweeperCell(
                    x = cell.x,
                    y = cell.y,
                    isMine = cell.isMine,
                    adjacentMines = cell.adjacentMines,
                    isRevealed = cell.isRevealed,
                    isFlagged = cell.isFlagged
                )
            }
        }
        if (cells.any { it == null }) return
        currentW = saved.width
        currentH = saved.height
        _board.value = MinesweeperBoard(saved.width, saved.height, cells.map { it!! })
        _status.value = MinesweeperStatus.PLAYING
        _elapsedSeconds.value = saved.elapsedSeconds
        _boomIndex.value = saved.boomIndex
        startTimer()
    }

    // Таймер

    private fun startTimer() {
        if (timerJob != null) return
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _elapsedSeconds.value += 1
            }
        }
    }

    //
    // Тетрис
    //

    /** Открывает Тетрис из меню «Игры». Если игра не начата — запускает новую партию. */
    fun openTetris() {
        if (_tetrisState.value.status != TetrisStatus.PLAYING && _tetrisState.value.status != TetrisStatus.PAUSED) {
            startNewTetris()
        }
        _activeGame.value = Game.TETRIS
    }

    /** Новая партия в Тетрис: очищает поле, начинает генерацию из 7-bag. */
    fun startNewTetris() {
        tetrisJob?.cancel()
        val (state, queue) = TetrisEngine.startNewGame()
        tetrisQueue = queue.toMutableList()
        _tetrisState.value = state
        startTetrisLoop()
    }

    /** Ставит Тетрис на паузу. */
    fun pauseTetris() {
        if (_tetrisState.value.status == TetrisStatus.PLAYING) {
            tetrisJob?.cancel()
            _tetrisState.value = _tetrisState.value.copy(status = TetrisStatus.PAUSED)
        }
    }

    /** Снимает Тетрис с паузы. */
    fun resumeTetris() {
        if (_tetrisState.value.status == TetrisStatus.PAUSED) {
            _tetrisState.value = _tetrisState.value.copy(status = TetrisStatus.PLAYING)
            startTetrisLoop()
        }
    }

    /** Выход из Тетриса в меню «Игры». */
    fun closeTetris() {
        pauseTetris()
        _activeGame.value = null
    }

    /** Сдвиг падающей фигуры влево. */
    fun moveTetrisLeft() {
        _tetrisState.value = TetrisEngine.moveLeft(_tetrisState.value)
    }

    /** Сдвиг падающей фигуры вправо. */
    fun moveTetrisRight() {
        _tetrisState.value = TetrisEngine.moveRight(_tetrisState.value)
    }

    /** Поворот по часовой стрелке с настенными отскоками (wall kicks). */
    fun rotateTetris() {
        _tetrisState.value = TetrisEngine.rotateClockwise(_tetrisState.value)
    }

    /** Мягкое опускание (Soft Drop): ускорение падения вниз (+1 очко за клетку). */
    fun softDropTetris() {
        val difficulty = gamesStorage.tetrisDifficulty.value
        val (newState, locked) = TetrisEngine.softDrop(_tetrisState.value, tetrisQueue, difficulty = difficulty)
        _tetrisState.value = newState
        if (locked && newState.status == TetrisStatus.GAME_OVER) {
            onTetrisGameOver(newState)
        }
    }

    /** Жёсткое падение (Hard Drop): мгновенный сброс вниз и фиксация (+2 очка за клетку). */
    fun hardDropTetris() {
        val difficulty = gamesStorage.tetrisDifficulty.value
        val newState = TetrisEngine.hardDrop(_tetrisState.value, tetrisQueue, difficulty = difficulty)
        _tetrisState.value = newState
        if (newState.status == TetrisStatus.GAME_OVER) {
            onTetrisGameOver(newState)
        }
    }

    /** Удержание (Hold): откладывает текущую фигуру или меняет её с уже отложенной. */
    fun holdTetris() {
        val newState = TetrisEngine.hold(_tetrisState.value, tetrisQueue)
        _tetrisState.value = newState
        if (newState.status == TetrisStatus.GAME_OVER) {
            onTetrisGameOver(newState)
        }
    }

    private fun startTetrisLoop() {
        tetrisJob?.cancel()
        tetrisJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive && _tetrisState.value.status == TetrisStatus.PLAYING) {
                val difficulty = gamesStorage.tetrisDifficulty.value
                val delayMs = TetrisEngine.gravityIntervalMs(_tetrisState.value.level, difficulty)
                delay(delayMs)
                if (!isActive || _tetrisState.value.status != TetrisStatus.PLAYING) break
                val updated = TetrisEngine.gravityTick(_tetrisState.value, tetrisQueue, difficulty = difficulty)
                _tetrisState.value = updated
                if (updated.status == TetrisStatus.GAME_OVER) {
                    onTetrisGameOver(updated)
                    break
                }
            }
        }
    }

    private fun onTetrisGameOver(finalState: TetrisState) {
        tetrisJob?.cancel()
        gamesStorage.submitTetrisResult(
            score = finalState.score,
            lines = finalState.lines,
            level = finalState.level,
            achievedAtMillis = Clock.System.now().toEpochMilliseconds()
        )
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        stopTimer()
        tetrisJob?.cancel()
        persistGame()
        super.onCleared()
    }

    companion object {
        private const val GAME = "minesweeper"

        fun recordKey(difficulty: MinesweeperDifficulty): String = "$GAME${difficulty.name}"

        /** «35 с · 06.10.2026 14:23» для меню. */
        fun formatRecord(record: GameRecord): String =
            "${record.seconds} с · ${DateUtils.formatDateTime(record.achievedAtMillis)}"
    }
}