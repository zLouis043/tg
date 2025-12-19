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
import androidx.core.content.res.ResourcesCompat
import kotlin.math.min

class StatsView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val stats = mutableMapOf<TetrominoType, Int>().apply {
        TetrominoType.values().forEach { put(it, 0) }
    }

    private val paint = Paint()

    // Configurazione Testo (Opzionale: qui caricheremo il font NES più avanti)
    private val textPaint = Paint().apply {
        color = Color.parseColor("#D32F2F")
        textAlign = Paint.Align.RIGHT
        try {
            typeface = ResourcesCompat.getFont(context, R.font.press_start_2p_regular)
        } catch (e: Exception) {
            // Se fallisce, usa il default
        }
    }

    // --- NUOVO: Carichiamo la texture ---
    private val blockBitmap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.block_base)
    private val srcRect = Rect(0, 0, blockBitmap.width, blockBitmap.height)
    private val dstRect = RectF()

    fun updateStats(newStats: Map<TetrominoType, Int>) {
        stats.putAll(newStats)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val types = TetrominoType.values()
        val slotHeight = height / 7f

        // --- CALIBRAZIONE ---
        val targetTextWidth = width * 0.6f

        // Testo: Leggermente più piccolo per non rubare la scena
        val sizeByWidth = targetTextWidth / 2.8f
        val sizeByHeight = slotHeight * 0.22f
        textPaint.textSize = min(sizeByWidth, sizeByHeight)
        textPaint.textAlign = Paint.Align.CENTER

        // BLOCCHI: Aumentati dal 13% al 20% per renderli ben visibili
        val miniBlockSize = min(width * 0.20f, slotHeight * 0.20f)

        val centerX = width / 2f
        var currentSlotY = 0f

        for (type in types) {
            // Posizioni Y aggiornate per i blocchi più grandi
            val iconCenterY = currentSlotY + (slotHeight * 0.35f) // Icona al 35%
            val textCenterY = currentSlotY + (slotHeight * 0.82f) // Testo spinto giù all'82%

            // --- 1. PREPARAZIONE ---
            val dummy = Tetromino(type)
            if (type == TetrominoType.I) dummy.rotate()

            // --- 2. BOUNDING BOX ---
            var minX = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var minY = Int.MAX_VALUE
            var maxY = Int.MIN_VALUE

            for (p in dummy.shape) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }

            val offsetX = - ((minX + maxX + 1) * miniBlockSize) / 2f
            val offsetY = - ((minY + maxY + 1) * miniBlockSize) / 2f

            // --- 3. DISEGNO ---
            for (p in dummy.shape) {
                val bx = centerX + (p.x * miniBlockSize) + offsetX
                val by = iconCenterY + (p.y * miniBlockSize) + offsetY

                paint.style = Paint.Style.FILL
                paint.color = getColorForType(context, type)
                canvas.drawRect(bx, by, bx + miniBlockSize, by + miniBlockSize, paint)

                dstRect.set(bx, by, bx + miniBlockSize, by + miniBlockSize)
                canvas.drawBitmap(blockBitmap, srcRect, dstRect, null)
            }

            // --- 4. TESTO ---
            val count = stats[type] ?: 0
            val textOffset = (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(String.format("%03d", count), centerX, textCenterY - textOffset, textPaint)

            currentSlotY += slotHeight
        }
    }
}