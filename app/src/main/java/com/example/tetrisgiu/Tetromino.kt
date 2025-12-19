package com.example.tetrisgiu

import android.content.Context
import androidx.core.content.ContextCompat

enum class TetrominoType { I, O, T, S, Z, J, L, F }

data class Point(var x: Int, var y: Int)

fun getColorForType(context: Context, type: TetrominoType): Int {
    return when(type) {
        TetrominoType.I -> ContextCompat.getColor(context, R.color.color_I)
        TetrominoType.O -> ContextCompat.getColor(context, R.color.color_O)
        TetrominoType.T -> ContextCompat.getColor(context, R.color.color_T)
        TetrominoType.S -> ContextCompat.getColor(context, R.color.color_S)
        TetrominoType.Z -> ContextCompat.getColor(context, R.color.color_Z)
        TetrominoType.J -> ContextCompat.getColor(context, R.color.color_J)
        TetrominoType.L -> ContextCompat.getColor(context, R.color.color_L)
        TetrominoType.F -> ContextCompat.getColor(context, R.color.color_dead)
    }
}

class Tetromino(val type: TetrominoType) {
    var x = 3
    var y = 0

    var shape: MutableList<Point> = getShapeForType(type)

    private fun getShapeForType(type: TetrominoType): MutableList<Point> {
        return when (type) {
            TetrominoType.I -> mutableListOf(Point(0,1), Point(0,0), Point(0,-1), Point(0,-2))
            TetrominoType.O -> mutableListOf(Point(0,0), Point(1,0), Point(0,1), Point(1,1))
            TetrominoType.T -> mutableListOf(Point(0,0), Point(-1,0), Point(1,0), Point(0,-1))
            TetrominoType.S -> mutableListOf(Point(0,0), Point(1,0), Point(0,1), Point(-1,1))
            TetrominoType.Z -> mutableListOf(Point(0,0), Point(-1,0), Point(0,1), Point(1,1))
            TetrominoType.J -> mutableListOf(Point(0,0), Point(-1,0), Point(1,0), Point(-1,-1))
            TetrominoType.L -> mutableListOf(Point(0,0), Point(-1,0), Point(1,0), Point(1,-1))
            TetrominoType.F -> mutableListOf(Point(0,0))
        }
    }

    fun rotate() {
        if (type == TetrominoType.O) return

        shape.forEach { point ->
            val oldX = point.x
            point.x = -point.y
            point.y = oldX
        }
    }

}