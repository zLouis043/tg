package com.example.tetrisgiu

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlin.random.Random

class GameView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    interface GameListener {
        fun onScoreUpdate(score: Int, topScore: Int)
        fun onLevelUpdate(level: Int, lines: Int)
        fun onNextPieceUpdate(nextType: TetrominoType)
        fun onStatsUpdate(stats: Map<TetrominoType, Int>)
        fun onGameOver()
    }

    var listener: GameListener? = null

    private val COLS = 10
    private val ROWS = 20
    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    var soundManager: SoundManager? = null

    private var blockBitmap: Bitmap? = null
    private val srcRect = Rect()
    private val dstRect = RectF()

    private val grid = Array(COLS) { IntArray(ROWS) { 0 } }

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    // --- ANIMAZIONE GAME OVER ---
    private var isAnimatingGameOver = false
    private var animRowIndex = ROWS - 1
    private val ANIM_DELAY = 100L // RALLENTATO (Era 50L) per rendere la tendina più drammatica

    var currentPiece: List<Point>? = null
    private var currentType: TetrominoType? = null
    private var currentX = 0
    private var currentY = 0
    private var rotationIndex = 0

    // --- LOGICA ANIMAZIONE LINEE ---
    private var isClearingLines = false
    private val rowsToClear = mutableListOf<Int>()
    private val CLEAR_ANIMATION_DELAY = 400L

    // --- LOGICA 7-BAG SYSTEM ---
    private val bag = mutableListOf<TetrominoType>()
    var nextType: TetrominoType = TetrominoType.I

    var score = 0
    var topScore = 0
    var lines = 0
    var level = 0
    var isGameOver = false
    var isPaused = false

    private var startingLevel = 0

    private val LOCK_DELAY_MS = 500L
    private var lockDelayRunnable: Runnable? = null

    var isGameRestored = false

    private val pieceStats = mutableMapOf<TetrominoType, Int>().apply {
        TetrominoType.values().forEach { put(it, 0) }
    }

    private val paints = mapOf(
        TetrominoType.I to Paint().apply { color = ContextCompat.getColor(context, R.color.color_I) },
        TetrominoType.J to Paint().apply { color = ContextCompat.getColor(context, R.color.color_J) },
        TetrominoType.L to Paint().apply { color = ContextCompat.getColor(context, R.color.color_L) },
        TetrominoType.O to Paint().apply { color = ContextCompat.getColor(context, R.color.color_O) },
        TetrominoType.S to Paint().apply { color = ContextCompat.getColor(context, R.color.color_S) },
        TetrominoType.T to Paint().apply { color = ContextCompat.getColor(context, R.color.color_T) },
        TetrominoType.Z to Paint().apply { color = ContextCompat.getColor(context, R.color.color_Z) },
        TetrominoType.F to Paint().apply { color = ContextCompat.getColor(context, R.color.color_dead) }
    )

    private val ghostPaint = Paint().apply { style = Paint.Style.FILL }

    private val flashPaint = Paint().apply {
        color = Color.WHITE
        alpha = 200
        style = Paint.Style.FILL
    }

    private val gridDotPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.white)
        alpha = 50
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val gameHandler = Handler(Looper.getMainLooper())
    private val gameRunnable = object : Runnable {
        override fun run() {
            // 1. CASO NORMALE: GIOCO ATTIVO
            if (!isGameOver && !isPaused && !isClearingLines && !isAnimatingGameOver) {
                if (!tryMove(0, 1)) {
                    startLockDelay()
                } else {
                    cancelLockDelay()
                }
                gameHandler.postDelayed(this, getSpeedForLevel(level))
            }

            // 2. CASO SPECIALE: ANIMAZIONE GAME OVER (RIEMPIMENTO)
            else if (isAnimatingGameOver) {
                fillRow(animRowIndex)
                animRowIndex--

                if (animRowIndex < 0) {
                    // Animazione finita -> Mostra Overlay
                    isAnimatingGameOver = false
                    // isGameOver è già true
                    listener?.onGameOver()
                } else {
                    invalidate()
                    gameHandler.postDelayed(this, ANIM_DELAY)
                }
            }
        }
    }

    init {
        try {
            blockBitmap = BitmapFactory.decodeResource(resources, R.drawable.block_base)
        } catch (e: Exception) { e.printStackTrace() }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate(duration: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrateImpact() { vibrate(50) }
    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrateSuccess() { vibrate(150) }
    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrateGameOver() { vibrate(500) }

    private fun getSpeedForLevel(l: Int): Long {
        return when (l) {
            0 -> 700L
            1 -> 600L
            2 -> 500L
            3 -> 400L
            4 -> 300L
            5 -> 250L
            6 -> 200L
            7 -> 150L
            8 -> 100L
            in 9..12 -> 80L
            in 13..15 -> 70L
            in 16..18 -> 50L
            in 19..28 -> 30L
            else -> 20L
        }
    }

    fun saveState(editor: android.content.SharedPreferences.Editor) {
        if (isGameOver) {
            editor.putBoolean("has_saved_game", false)
            return
        }

        // 1. Variabili base
        editor.putBoolean("has_saved_game", true)
        editor.putInt("save_score", score)
        editor.putInt("save_level", level)
        editor.putInt("save_lines", lines)

        editor.putInt("save_starting_level", startingLevel)

        // 2. Griglia (Convertiamo 200 numeri in una stringa separata da virgole)
        val gridString = StringBuilder()
        for (x in 0 until COLS) {
            for (y in 0 until ROWS) {
                gridString.append("${grid[x][y]},")
            }
        }
        editor.putString("save_grid", gridString.toString())

        // 3. Pezzo Corrente
        if (currentPiece != null) {
            editor.putString("save_cur_type", currentType?.name ?: "I")
            editor.putInt("save_cur_x", currentX)
            editor.putInt("save_cur_y", currentY)
            editor.putInt("save_cur_rot", rotationIndex)
        }

        // 4. Pezzo Successivo
        editor.putString("save_next_type", nextType.name)

        // 5. La Busta (7-Bag) - Importante per mantenere la sequenza
        val bagString = bag.joinToString(",") { it.name }
        editor.putString("save_bag", bagString)
    }

    fun restoreState(prefs: android.content.SharedPreferences): Boolean {
        if (!prefs.getBoolean("has_saved_game", false)) return false

        try {
            // 1. Variabili base
            score = prefs.getInt("save_score", 0)
            level = prefs.getInt("save_level", 0)
            lines = prefs.getInt("save_lines", 0)

            startingLevel = prefs.getInt("save_starting_level", 0)

            // 2. Griglia
            val gridString = prefs.getString("save_grid", "") ?: ""
            val values = gridString.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
            if (values.size == COLS * ROWS) {
                var index = 0
                for (x in 0 until COLS) {
                    for (y in 0 until ROWS) {
                        grid[x][y] = values[index++]
                    }
                }
            }

            // 3. Pezzo Corrente
            val curTypeName = prefs.getString("save_cur_type", "I")
            currentType = TetrominoType.valueOf(curTypeName!!)
            currentX = prefs.getInt("save_cur_x", 4)
            currentY = prefs.getInt("save_cur_y", 0)
            rotationIndex = prefs.getInt("save_cur_rot", 0)
            currentPiece = getRotatedPiece(currentType!!, rotationIndex)

            // 4. Pezzo Successivo
            val nextTypeName = prefs.getString("save_next_type", "I")
            nextType = TetrominoType.valueOf(nextTypeName!!)
            listener?.onNextPieceUpdate(nextType)

            // 5. La Busta
            val bagString = prefs.getString("save_bag", "") ?: ""
            bag.clear()
            if (bagString.isNotEmpty()) {
                bagString.split(",").forEach {
                    if (it.isNotEmpty()) bag.add(TetrominoType.valueOf(it))
                }
            }

            updateUI()
            isGameRestored = true
            return true

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun resumeSavedGame() {
        isGameOver = false
        isPaused = false
        isClearingLines = false
        isAnimatingGameOver = false
        gameLoop()
    }

    private fun fillRow(y: Int) {
        for (x in 0 until COLS) {
            // Effetto "morte": Riempiamo con blocchi grigi (F)
            grid[x][y] = TetrominoType.F.ordinal + 1
        }
    }

    private fun startLockDelay() {
        if (lockDelayRunnable == null) {
            lockDelayRunnable = Runnable {
                if (!tryMove(0, 1)) {
                    lockPiece()
                }
            }
            gameHandler.postDelayed(lockDelayRunnable!!, LOCK_DELAY_MS)
        }
    }

    private fun cancelLockDelay() {
        lockDelayRunnable?.let {
            gameHandler.removeCallbacks(it)
            lockDelayRunnable = null
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun hardDrop() {
        if (isGameOver || isPaused || isClearingLines || isAnimatingGameOver || currentPiece == null) return

        var cellsDropped = 0
        while (isValidPosition(currentX, currentY + 1, currentPiece!!)) {
            currentY++
            cellsDropped++
        }

        if (cellsDropped > 0) {
            addScore(cellsDropped * 2)
            vibrateImpact()
            invalidate()
        }
        lockPiece()
    }

    private fun pullNextPiece(): TetrominoType {
        if (bag.isEmpty()) {
            bag.addAll(TetrominoType.values().filter { it != TetrominoType.F })
            bag.shuffle()
        }
        return bag.removeAt(0)
    }

    fun startGame(startLevel: Int) {
        this.startingLevel = startLevel
        initGame()
        gameLoop()
    }

    private fun initGame() {
        for (x in 0 until COLS) {
            for (y in 0 until ROWS) {
                grid[x][y] = 0
            }
        }
        score = 0
        lines = 0
        level = startingLevel
        isGameOver = false
        isPaused = false
        isClearingLines = false
        isAnimatingGameOver = false
        rowsToClear.clear()

        pieceStats.keys.forEach { pieceStats[it] = 0 }

        bag.clear()
        nextType = pullNextPiece()

        spawnPiece()
        updateUI()
    }

    private fun gameLoop() {
        gameHandler.removeCallbacks(gameRunnable)
        gameHandler.post(gameRunnable)
    }

    fun setGamePaused(paused: Boolean) {
        if (isPaused == paused) return
        isPaused = paused
        if (isPaused) {
            gameHandler.removeCallbacks(gameRunnable)
        } else {
            if (!isGameOver) gameLoop()
        }
        invalidate()
    }

    fun restartGame() {
        gameHandler.removeCallbacks(gameRunnable)
        initGame()
        gameLoop()
    }

    private fun addScore(points: Int) {
        score += points
        if (score > topScore) {
            topScore = score
        }
        updateUI()
    }

    private fun spawnPiece() {
        currentType = nextType
        nextType = pullNextPiece()
        rotationIndex = 0

        currentType?.let {
            val count = pieceStats[it] ?: 0
            pieceStats[it] = count + 1
        }

        listener?.onNextPieceUpdate(nextType)
        listener?.onStatsUpdate(pieceStats)

        currentPiece = getRotatedPiece(currentType!!, rotationIndex)
        currentX = COLS / 2 - 2
        currentY = 0

        // GAME OVER CHECK
        // Se il pezzo spawna in una posizione non valida (sovrapposto)
        if (!isValidPosition(currentX, currentY, currentPiece!!)) {

            // 1. Ferma tutto immediatamente
            isGameOver = true // Blocca input
            gameHandler.removeCallbacks(gameRunnable) // Ferma gravità
            soundManager?.playGameOver()

            // 2. "Incolla" visivamente il pezzo che ha causato la morte sulla griglia
            // Così il giocatore lo vede lì, incastrato.
            currentPiece?.forEach { p ->
                val x = currentX + p.x
                val y = currentY + p.y
                if (x in 0 until COLS && y in 0 until ROWS) {
                    grid[x][y] = (currentType?.ordinal ?: 0) + 1
                }
            }
            currentPiece = null // Rimuoviamo lo sprite mobile, ora è nella griglia
            invalidate() // Disegna il disastro

            // 3. Aspetta 1 secondo (Momento di realizzazione)
            gameHandler.postDelayed({
                // 4. Avvia l'animazione della tendina
                isAnimatingGameOver = true
                animRowIndex = ROWS - 1
                gameHandler.post(gameRunnable) // Riavvia il loop per gestire l'animazione
            }, 1000L)
        }
    }

    private fun lockPiece() {
        cancelLockDelay()
        soundManager?.playDrop()

        currentPiece?.forEach { p ->
            val x = currentX + p.x
            val y = currentY + p.y
            if (x in 0 until COLS && y in 0 until ROWS) {
                grid[x][y] = (currentType?.ordinal ?: 0) + 1
            }
        }

        checkLines()

        if (!isClearingLines) {
            spawnPiece()
        }
        invalidate()
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun checkLines() {
        rowsToClear.clear()
        for (y in ROWS - 1 downTo 0) {
            var full = true
            for (x in 0 until COLS) {
                if (grid[x][y] == 0) {
                    full = false
                    break
                }
            }
            if (full) rowsToClear.add(y)
        }

        if (rowsToClear.isNotEmpty()) {
            isClearingLines = true

            if (rowsToClear.size >= 4) {
                vibrate(300)
            } else {
                soundManager?.playLineClear()
                vibrateSuccess()
            }

            val points = when (rowsToClear.size) {
                1 -> 40
                2 -> 100
                3 -> 300
                4 -> 1200
                else -> 0
            } * (level + 1)
            addScore(points)

            gameHandler.postDelayed({
                removeFilledRows()
            }, CLEAR_ANIMATION_DELAY)

            invalidate()
        }
    }

    private fun removeFilledRows() {
        rowsToClear.sorted().forEach { rowToRemove ->
            for (k in rowToRemove downTo 1) {
                for (x in 0 until COLS) {
                    grid[x][k] = grid[x][k - 1]
                }
            }
            for (x in 0 until COLS) grid[x][0] = 0
        }

        lines += rowsToClear.size
        level = startingLevel + (lines / 10)
        updateUI()

        isClearingLines = false
        rowsToClear.clear()

        spawnPiece()
        gameLoop()
        invalidate()
    }

    private fun updateUI() {
        listener?.onScoreUpdate(score, topScore)
        listener?.onLevelUpdate(level, lines)
    }

    fun tryMove(dx: Int, dy: Int): Boolean {
        if (isValidPosition(currentX + dx, currentY + dy, currentPiece!!)) {
            currentX += dx
            currentY += dy
            invalidate()
            return true
        }
        return false
    }

    fun moveLeft() {
        if (!isGameOver && !isPaused && !isClearingLines && !isAnimatingGameOver && currentPiece != null) {
            if(tryMove(-1, 0)) {
                soundManager?.playMove()
                if (isValidPosition(currentX, currentY + 1, currentPiece!!)) cancelLockDelay()
                else { cancelLockDelay(); startLockDelay() }
            }
        }
    }

    fun moveRight() {
        if (!isGameOver && !isPaused && !isClearingLines && !isAnimatingGameOver && currentPiece != null) {
            if (tryMove(1, 0)) {
                soundManager?.playMove()
                if (isValidPosition(currentX, currentY + 1, currentPiece!!)) cancelLockDelay()
                else { cancelLockDelay(); startLockDelay() }
            }
        }
    }

    fun moveDown() {
        if (!isGameOver && !isPaused && !isClearingLines && !isAnimatingGameOver && currentPiece != null) {
            if (tryMove(0, 1)) {
                addScore(1)
            }
        }
    }

    fun rotateRight() {
        if (isGameOver || isPaused || isClearingLines || isAnimatingGameOver || currentPiece == null) return
        rotatePiece(1)
    }

    fun rotateLeft() {
        if (isGameOver || isPaused || isClearingLines || isAnimatingGameOver || currentPiece == null) return
        rotatePiece(3)
    }

    private fun rotatePiece(direction: Int) {
        val newRotation = (rotationIndex + direction) % 4
        val newPiece = getRotatedPiece(currentType!!, newRotation)

        val kicks = listOf(0, 1, -1, 2, -2)
        for (kick in kicks) {
            if (isValidPosition(currentX + kick, currentY, newPiece)) {
                currentX += kick
                rotationIndex = newRotation
                currentPiece = newPiece
                soundManager?.playRotate()
                if (!isValidPosition(currentX, currentY + 1, currentPiece!!)) {
                    cancelLockDelay(); startLockDelay()
                }
                invalidate()
                return
            }
        }
    }

    private fun getRotatedPiece(type: TetrominoType, rotation: Int): List<Point> {
        return when (type) {
            TetrominoType.I -> when (rotation) {
                0 -> listOf(Point(0, 1), Point(1, 1), Point(2, 1), Point(3, 1))
                1 -> listOf(Point(2, 0), Point(2, 1), Point(2, 2), Point(2, 3))
                2 -> listOf(Point(0, 2), Point(1, 2), Point(2, 2), Point(3, 2))
                3 -> listOf(Point(1, 0), Point(1, 1), Point(1, 2), Point(1, 3))
                else -> listOf()
            }
            TetrominoType.J -> when (rotation) {
                0 -> listOf(Point(0, 0), Point(0, 1), Point(1, 1), Point(2, 1))
                1 -> listOf(Point(1, 0), Point(2, 0), Point(1, 1), Point(1, 2))
                2 -> listOf(Point(0, 1), Point(1, 1), Point(2, 1), Point(2, 2))
                3 -> listOf(Point(1, 0), Point(1, 1), Point(0, 2), Point(1, 2))
                else -> listOf()
            }
            TetrominoType.L -> when (rotation) {
                0 -> listOf(Point(2, 0), Point(0, 1), Point(1, 1), Point(2, 1))
                1 -> listOf(Point(1, 0), Point(1, 1), Point(1, 2), Point(2, 2))
                2 -> listOf(Point(0, 1), Point(1, 1), Point(2, 1), Point(0, 2))
                3 -> listOf(Point(0, 0), Point(1, 0), Point(1, 1), Point(1, 2))
                else -> listOf()
            }
            TetrominoType.O -> listOf(Point(1, 0), Point(2, 0), Point(1, 1), Point(2, 1))
            TetrominoType.S -> when (rotation) {
                0 -> listOf(Point(1, 0), Point(2, 0), Point(0, 1), Point(1, 1))
                1 -> listOf(Point(1, 0), Point(1, 1), Point(2, 1), Point(2, 2))
                2 -> listOf(Point(1, 1), Point(2, 1), Point(0, 2), Point(1, 2))
                3 -> listOf(Point(0, 0), Point(0, 1), Point(1, 1), Point(1, 2))
                else -> listOf()
            }
            TetrominoType.T -> when (rotation) {
                0 -> listOf(Point(1, 0), Point(0, 1), Point(1, 1), Point(2, 1))
                1 -> listOf(Point(1, 0), Point(1, 1), Point(2, 1), Point(1, 2))
                2 -> listOf(Point(0, 1), Point(1, 1), Point(2, 1), Point(1, 2))
                3 -> listOf(Point(1, 0), Point(0, 1), Point(1, 1), Point(1, 2))
                else -> listOf()
            }
            TetrominoType.Z -> when (rotation) {
                0 -> listOf(Point(0, 0), Point(1, 0), Point(1, 1), Point(2, 1))
                1 -> listOf(Point(2, 0), Point(1, 1), Point(2, 1), Point(1, 2))
                2 -> listOf(Point(0, 1), Point(1, 1), Point(1, 2), Point(2, 2))
                3 -> listOf(Point(1, 0), Point(0, 1), Point(1, 1), Point(0, 2))
                else -> listOf()
            }
            else -> listOf()
        }
    }

    private fun isValidPosition(x: Int, y: Int, piece: List<Point>): Boolean {
        for (p in piece) {
            val newX = x + p.x
            val newY = y + p.y
            if (newX !in 0 until COLS || newY !in 0 until ROWS) return false
            if (grid[newX][newY] != 0) return false
        }
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cellH = h / ROWS.toFloat()
        val cellW = w / COLS.toFloat()
        cellSize = minOf(cellH, cellW)
        offsetX = (w - (cellSize * COLS)) / 2
        offsetY = h - (cellSize * ROWS)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Punti Griglia
        for (x in 0 until COLS) {
            for (y in 0 until ROWS) {
                if (grid[x][y] == 0) {
                    val cx = offsetX + x * cellSize + cellSize / 2
                    val cy = offsetY + y * cellSize + cellSize / 2
                    canvas.drawCircle(cx, cy, 2f, gridDotPaint)
                }
            }
        }

        // Helper Disegno Blocco
        fun drawBlock(x: Int, y: Int, type: TetrominoType, paintOverride: Paint? = null) {
            val left = offsetX + x * cellSize
            val top = offsetY + y * cellSize
            val right = left + cellSize
            val bottom = top + cellSize

            val paint = paintOverride ?: paints[type]!!
            canvas.drawRect(left, top, right, bottom, paint)

            if (paintOverride == null && blockBitmap != null && type != TetrominoType.F) {
                dstRect.set(left, top, right, bottom)
                canvas.drawBitmap(blockBitmap!!, null, dstRect, null)
            }
        }

        // 2. Blocchi Fissi
        for (x in 0 until COLS) {
            for (y in 0 until ROWS) {
                val valGrid = grid[x][y]
                if (valGrid > 0) {
                    val type = TetrominoType.values().getOrElse(valGrid - 1) { TetrominoType.F }
                    drawBlock(x, y, type)
                }
            }
        }

        // 3. ANIMAZIONE FLASH
        if (isClearingLines && rowsToClear.isNotEmpty()) {
            for (y in rowsToClear) {
                val left = offsetX
                val top = offsetY + y * cellSize
                val right = offsetX + COLS * cellSize
                val bottom = top + cellSize
                canvas.drawRect(left, top, right, bottom, flashPaint)
            }
        }

        // 4. Ghost & Pezzo Corrente
        if (currentPiece != null && !isGameOver && !isClearingLines && !isAnimatingGameOver) {
            var ghostY = currentY
            while (isValidPosition(currentX, ghostY + 1, currentPiece!!)) {
                ghostY++
            }
            val originalPaint = paints[currentType]!!
            ghostPaint.color = originalPaint.color
            ghostPaint.alpha = 50
            for (p in currentPiece!!) {
                drawBlock(currentX + p.x, ghostY + p.y, currentType!!, ghostPaint)
            }

            for (p in currentPiece!!) {
                drawBlock(currentX + p.x, currentY + p.y, currentType!!)
            }
        }
    }
}