package com.example.tetrisgiu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class NextBlockView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var nextType: TetrominoType? = null
    private val paints = mutableMapOf<TetrominoType, Paint>()
    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    // Bitmap per l'effetto "texture" (lo stesso della GameView)
    private var blockBitmap: Bitmap? = null
    private val srcRect = Rect()
    private val dstRect = RectF()

    init {
        // Inizializza i pennelli con i colori centralizzati
        TetrominoType.values().forEach { type ->
            if (type != TetrominoType.F) { // Ignora il blocco grigio
                val color = getColorForType(context, type)
                paints[type] = Paint().apply {
                    this.color = color
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
            }
        }

        try {
            blockBitmap = BitmapFactory.decodeResource(resources, R.drawable.block_base)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setNextPiece(type: TetrominoType) {
        this.nextType = type
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Calcola una dimensione cella che stia bene nel riquadro (assumiamo max 4x4 blocchi)
        val sizeByW = w / 5f
        val sizeByH = h / 5f
        cellSize = minOf(sizeByW, sizeByH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (nextType == null) return

        // Otteniamo la forma "statica" dal file Tetromino.kt o la definiamo qui al volo per l'anteprima
        // Usiamo una definizione locale centrata per l'anteprima estetica
        val shape = getPreviewShape(nextType!!)
        val paint = paints[nextType!!] ?: return

        // Calcoliamo l'offset per centrare il pezzo nel riquadro
        // La larghezza del pezzo in celle
        var minX = 100; var maxX = -100
        var minY = 100; var maxY = -100

        shape.forEach { p ->
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }

        val pieceWidth = (maxX - minX + 1) * cellSize
        val pieceHeight = (maxY - minY + 1) * cellSize

        offsetX = (width - pieceWidth) / 2f - (minX * cellSize)
        offsetY = (height - pieceHeight) / 2f - (minY * cellSize)

        // Disegna
        shape.forEach { p ->
            val left = offsetX + p.x * cellSize
            val top = offsetY + p.y * cellSize
            val right = left + cellSize
            val bottom = top + cellSize

            // 1. Colore base
            canvas.drawRect(left, top, right, bottom, paint)

            // 2. Texture Bitmap (Opzionale, ma coerente con GameView)
            if (blockBitmap != null) {
                dstRect.set(left, top, right, bottom)
                canvas.drawBitmap(blockBitmap!!, null, dstRect, null)
            }
        }
    }

    // Definizioni statiche per l'anteprima (coordinate relative 0,0)
    private fun getPreviewShape(type: TetrominoType): List<Point> {
        return when (type) {
            TetrominoType.I -> listOf(Point(0, 0), Point(1, 0), Point(2, 0), Point(3, 0)) // Orizzontale
            TetrominoType.O -> listOf(Point(0, 0), Point(1, 0), Point(0, 1), Point(1, 1))
            TetrominoType.T -> listOf(Point(1, 0), Point(0, 1), Point(1, 1), Point(2, 1))
            TetrominoType.S -> listOf(Point(1, 0), Point(2, 0), Point(0, 1), Point(1, 1))
            TetrominoType.Z -> listOf(Point(0, 0), Point(1, 0), Point(1, 1), Point(2, 1))
            TetrominoType.J -> listOf(Point(0, 0), Point(0, 1), Point(1, 1), Point(2, 1))
            TetrominoType.L -> listOf(Point(2, 0), Point(0, 1), Point(1, 1), Point(2, 1))
            else -> listOf()
        }
    }
}