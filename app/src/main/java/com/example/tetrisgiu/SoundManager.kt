package com.example.tetrisgiu

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

class SoundManager(context: Context) {

    private var soundPool: SoundPool? = null

    // ID dei suoni
    private var sfxMove = 0
    private var sfxRotate = 0
    private var sfxDrop = 0
    private var sfxLine = 0
    private var sfxGameOver = 0

    private var isLoaded = false

    // Volume Master (quello della SeekBar)
    var masterVolume: Float = 0.5f

    // --- MOLTIPLICATORI DI MIXAGGIO (Regola questi se un suono è troppo alto/basso) ---
    private val VOL_MULT_MOVE = 0.05f   // Abbassato molto perché si ripete spesso
    private val VOL_MULT_ROTATE = 0.3f // Medio
    private val VOL_MULT_DROP = 0.8f   // Abbastanza forte (impatto)
    private val VOL_MULT_LINE = 1.0f   // Forte (premio)
    private val VOL_MULT_GAMEOVER = 1.0f

    init {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build()

            sfxMove = loadSound(context, "sfx_move", R.raw.sfx_move)
            sfxRotate = loadSound(context, "sfx_rotate", R.raw.sfx_rotate)
            sfxDrop = loadSound(context, "sfx_drop", R.raw.sfx_drop)
            sfxLine = loadSound(context, "sfx_line", R.raw.sfx_line)
            sfxGameOver = loadSound(context, "sfx_gameover", R.raw.sfx_gameover)

            isLoaded = true
        } catch (e: Exception) {
            Log.e("SoundManager", "Errore init audio", e)
            isLoaded = false
        }
    }

    private fun loadSound(context: Context, name: String, resId: Int): Int {
        return try {
            soundPool?.load(context, resId, 1) ?: 0
        } catch (e: Exception) { 0 }
    }

    // Quando suoniamo, moltiplichiamo il Master Volume per il Moltiplicatore specifico
    fun playMove() { safePlay(sfxMove, VOL_MULT_MOVE) }
    fun playRotate() { safePlay(sfxRotate, VOL_MULT_ROTATE) }
    fun playDrop() { safePlay(sfxDrop, VOL_MULT_DROP) }
    fun playLineClear() { safePlay(sfxLine, VOL_MULT_LINE) }
    fun playGameOver() { safePlay(sfxGameOver, VOL_MULT_GAMEOVER) }

    private fun safePlay(soundId: Int, multiplier: Float) {
        if (isLoaded && soundId != 0) {
            try {
                // Calcolo volume finale: Master (es. 0.5) * Moltiplicatore (es. 0.3) = 0.15
                val finalVol = masterVolume * multiplier
                soundPool?.play(soundId, finalVol, finalVol, 0, 0, 1f)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}