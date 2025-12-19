package com.example.tetrisgiu

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), GameView.GameListener {

    private lateinit var gameView: GameView
    private lateinit var scoreText: TextView
    private lateinit var topScoreText: TextView
    private lateinit var levelText: TextView
    private lateinit var linesText: TextView
    private lateinit var nextBlockView: NextBlockView
    private lateinit var statsView: StatsView

    // OVERLAYS
    private lateinit var overlayPause: LinearLayout
    private lateinit var overlayGameOver: LinearLayout
    private lateinit var overlayTitle: LinearLayout
    private lateinit var overlayLevelSelect: LinearLayout

    // MENU VARIABILI
    private lateinit var txtStartLevel: TextView
    private var selectedStartLevel = 0

    // AUDIO
    private lateinit var soundManager: SoundManager
    private var bgMusic: MediaPlayer? = null

    private val musicPlaylist = listOf(
        R.raw.soundtrack_1, R.raw.soundtrack_2, R.raw.soundtrack_3,
        R.raw.soundtrack_4, R.raw.soundtrack_5, R.raw.soundtrack_6,
        R.raw.soundtrack_7, R.raw.soundtrack_8, R.raw.soundtrack_9, R.raw.soundtrack_10
    )
    private var currentTrackIndex = 0

    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private val initialDelay = 250L
    private val repeatDelay = 80L

    private var musicVolume = 0.5f
    private var sfxVolume = 0.5f

    private val PREFS_NAME = "TetrisPrefs"
    private val KEY_TOP_SCORE = "key_top_score"
    private val KEY_VOL_MUSIC = "key_vol_music"
    private val KEY_VOL_SFX = "key_vol_sfx"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { supportActionBar?.hide() } catch (e: Exception) {}
        setContentView(R.layout.activity_main)

        // 1. Viste
        gameView = findViewById(R.id.gameView)
        scoreText = findViewById(R.id.scoreText)
        topScoreText = findViewById(R.id.topScoreText)
        levelText = findViewById(R.id.levelText)
        linesText = findViewById(R.id.labelLines)
        nextBlockView = findViewById(R.id.nextPieceView)
        statsView = findViewById(R.id.statsView)

        overlayPause = findViewById(R.id.overlayPause)
        overlayGameOver = findViewById(R.id.overlayGameOver)
        overlayTitle = findViewById(R.id.overlayTitle)
        overlayLevelSelect = findViewById(R.id.overlayLevelSelect)

        loadData()

        // 2. Audio
        soundManager = SoundManager(this)
        soundManager.masterVolume = sfxVolume
        gameView.soundManager = soundManager

        val titleTextView = findViewById<TextView>(R.id.titleText)
        colorizeTitle(titleTextView)
        val mainTitleText = findViewById<TextView>(R.id.mainTitleText)
        colorizeTitle(mainTitleText)

        // 3. Setup Listener
        gameView.listener = this
        setupControls()
        setupPauseMenu()
        setupGameOverMenu()
        setupAudioControls()

        // 4. Ripristino Stato
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val hasSavedGame = gameView.restoreState(prefs)

        // Aggiorna subito la next view se ripristinata
        if (hasSavedGame) {
            nextBlockView.setNextPiece(gameView.nextType)
        }

        setupTitleMenu(hasSavedGame)

        topScoreText.text = String.format("%06d", gameView.topScore)
        playTrack(0)
    }

    private fun setupTitleMenu(hasSavedGame: Boolean) {
        val btnContinue = findViewById<TextView>(R.id.btnTitleContinue)
        val btnNewGame = findViewById<TextView>(R.id.btnTitleNewGame)

        // LOGICA CONTINUE
        if (hasSavedGame) {
            btnContinue.alpha = 1.0f
            btnContinue.isEnabled = true
            btnContinue.setOnClickListener {
                soundManager.playLineClear()
                overlayTitle.visibility = View.GONE
                gameView.resumeSavedGame()
            }
        } else {
            // Disabilitato graficamente
            btnContinue.alpha = 0.4f
            btnContinue.isEnabled = false
            btnContinue.setOnClickListener(null)
        }

        // LOGICA NEW GAME -> Apre selezione livello
        btnNewGame.setOnClickListener {
            soundManager.playMove()
            overlayTitle.visibility = View.GONE
            overlayLevelSelect.visibility = View.VISIBLE
            setupLevelSelectMenu()
        }
    }

    private fun setupLevelSelectMenu() {
        txtStartLevel = findViewById(R.id.txtStartLevel)
        val btnMinus = findViewById<View>(R.id.btnLevelMinus)
        val btnPlus = findViewById<View>(R.id.btnLevelPlus)
        val btnStart = findViewById<View>(R.id.btnStartGame)
        val btnBack = findViewById<View>(R.id.btnBackToTitle)

        updateLevelText()

        btnMinus.setOnClickListener {
            soundManager.playMove()
            if (selectedStartLevel > 0) {
                selectedStartLevel--
                updateLevelText()
            }
        }

        btnPlus.setOnClickListener {
            soundManager.playMove()
            if (selectedStartLevel < 9) {
                selectedStartLevel++
                updateLevelText()
            }
        }

        btnStart.setOnClickListener {
            soundManager.playLineClear()
            overlayLevelSelect.visibility = View.GONE
            // Nuova partita cancella il save precedente
            clearSavedGame()
            gameView.startGame(selectedStartLevel)
        }

        btnBack.setOnClickListener {
            soundManager.playMove()
            overlayLevelSelect.visibility = View.GONE
            overlayTitle.visibility = View.VISIBLE
        }
    }

    private fun updateLevelText() {
        txtStartLevel.text = String.format("%02d", selectedStartLevel)
    }

    // --- PAUSE MENU (CORRETTO) ---
    private fun setupPauseMenu() {
        // RESUME
        findViewById<View>(R.id.btnMenuResume).setOnClickListener {
            hidePauseMenu()
        }
        // RESTART (Stesso Livello)
        findViewById<View>(R.id.btnMenuRestart).setOnClickListener {
            overlayPause.visibility = View.GONE
            clearSavedGame() // Cancella save precedente
            gameView.restartGame()
            playTrack(0)
        }
        // MAIN MENU (CORREZIONE: btnMenuMain invece di btnMenuQuit)
        findViewById<View>(R.id.btnMenuMain).setOnClickListener {
            saveData() // Salva stato corrente
            overlayPause.visibility = View.GONE
            overlayTitle.visibility = View.VISIBLE

            // Ricarica menu per attivare "CONTINUE"
            setupTitleMenu(true)
            playTrack(0)
        }
    }

    // --- GAME OVER MENU ---
    private fun setupGameOverMenu() {
        findViewById<View>(R.id.btnGameOverRestart).setOnClickListener {
            clearSavedGame()
            overlayGameOver.visibility = View.GONE
            gameView.restartGame()
            playTrack(0)
        }
        // MAIN MENU anche per Game Over
        findViewById<View>(R.id.btnGameOverMain).setOnClickListener {
            clearSavedGame()
            overlayGameOver.visibility = View.GONE
            overlayTitle.visibility = View.VISIBLE
            setupTitleMenu(false) // No save game
            playTrack(0)
        }
    }

    private fun playTrack(index: Int) {
        bgMusic?.release()
        if (index >= musicPlaylist.size) currentTrackIndex = 0 else currentTrackIndex = index
        try {
            if (musicPlaylist.isEmpty()) return
            val resId = musicPlaylist[currentTrackIndex]
            bgMusic = MediaPlayer.create(this, resId)
            bgMusic?.setVolume(musicVolume, musicVolume)
            bgMusic?.setOnCompletionListener { playTrack(currentTrackIndex + 1) }
            bgMusic?.start()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveData() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putFloat(KEY_VOL_MUSIC, musicVolume)
        editor.putFloat(KEY_VOL_SFX, sfxVolume)
        editor.putInt(KEY_TOP_SCORE, gameView.topScore)

        // Salva stato SOLO se siamo in gioco (tutti gli overlay spenti)
        if (overlayTitle.visibility == View.GONE && overlayLevelSelect.visibility == View.GONE && !gameView.isGameOver) {
            gameView.saveState(editor)
        }
        editor.apply()
    }

    private fun clearSavedGame() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putBoolean("has_saved_game", false)
        editor.apply()
        gameView.isGameRestored = false
    }

    private fun loadData() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        musicVolume = prefs.getFloat(KEY_VOL_MUSIC, 0.5f)
        sfxVolume = prefs.getFloat(KEY_VOL_SFX, 0.5f)
        val savedTopScore = prefs.getInt(KEY_TOP_SCORE, 0)
        gameView.topScore = savedTopScore
    }

    private fun setupAudioControls() {
        val seekMusic = findViewById<SeekBar>(R.id.seekMusic)
        val seekSfx = findViewById<SeekBar>(R.id.seekSfx)
        seekMusic.progress = (musicVolume * 100).toInt()
        seekSfx.progress = (sfxVolume * 100).toInt()
        seekMusic.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicVolume = progress / 100f
                    bgMusic?.setVolume(musicVolume, musicVolume)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        seekSfx.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    sfxVolume = progress / 100f
                    soundManager.masterVolume = sfxVolume
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onPause() {
        super.onPause()
        saveData()
        if (overlayTitle.visibility == View.GONE && overlayLevelSelect.visibility == View.GONE && !gameView.isGameOver) {
            showPauseMenu()
        }
        if (bgMusic?.isPlaying == true) bgMusic?.pause()
    }

    override fun onResume() {
        super.onResume()
        val inMenu = overlayTitle.visibility == View.VISIBLE || overlayLevelSelect.visibility == View.VISIBLE
        val inGame = overlayPause.visibility == View.GONE && overlayGameOver.visibility == View.GONE && !gameView.isGameOver
        if (inGame || inMenu) bgMusic?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveData()
        soundManager.release()
        bgMusic?.stop()
        bgMusic?.release()
        bgMusic = null
    }

    private fun showPauseMenu() {
        gameView.setGamePaused(true)
        overlayPause.visibility = View.VISIBLE
        if (bgMusic?.isPlaying == true) bgMusic?.pause()
    }

    private fun hidePauseMenu() {
        gameView.setGamePaused(false)
        overlayPause.visibility = View.GONE
        bgMusic?.start()
    }

    private fun setupControls() {
        fun setupRepeatButton(viewId: Int, action: () -> Unit) {
            val button = findViewById<View>(viewId)
            button.setOnTouchListener { _, event ->
                if (overlayTitle.visibility == View.VISIBLE || overlayLevelSelect.visibility == View.VISIBLE || gameView.isPaused || gameView.isGameOver) return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        action()
                        button.isPressed = true
                        repeatRunnable = object : Runnable {
                            override fun run() {
                                action()
                                repeatHandler.postDelayed(this, repeatDelay)
                            }
                        }
                        repeatHandler.postDelayed(repeatRunnable!!, initialDelay)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        repeatRunnable?.let { repeatHandler.removeCallbacks(it) }
                        repeatRunnable = null
                        button.isPressed = false
                        true
                    }
                    else -> false
                }
            }
        }
        setupRepeatButton(R.id.btnLeft) { gameView.moveLeft() }
        setupRepeatButton(R.id.btnRight) { gameView.moveRight() }
        setupRepeatButton(R.id.btnDown) { gameView.moveDown() }
        findViewById<View>(R.id.btnRotateRight).setOnClickListener {
            if (overlayTitle.visibility == View.GONE && overlayLevelSelect.visibility == View.GONE && !gameView.isPaused && !gameView.isGameOver) gameView.rotateRight()
        }
        findViewById<View>(R.id.btnRotateLeft).setOnClickListener {
            if (overlayTitle.visibility == View.GONE && overlayLevelSelect.visibility == View.GONE && !gameView.isPaused && !gameView.isGameOver) gameView.rotateLeft()
        }
        findViewById<View>(R.id.btnUp).setOnClickListener {
            if (overlayTitle.visibility == View.GONE && overlayLevelSelect.visibility == View.GONE && !gameView.isPaused && !gameView.isGameOver) gameView.hardDrop()
        }
        findViewById<View>(R.id.btnHeaderPause).setOnClickListener {
            if (overlayTitle.visibility == View.GONE && overlayLevelSelect.visibility == View.GONE && !gameView.isGameOver) {
                if (overlayPause.visibility == View.VISIBLE) hidePauseMenu() else showPauseMenu()
            }
        }
    }

    private fun colorizeTitle(textView: TextView) {
        val text = "TETRIS"
        val colors = arrayOf("#FF0000", "#FF7F00", "#FFFF00", "#00FF00", "#00FFFF", "#800080")
        val builder = android.text.SpannableStringBuilder()
        for (i in text.indices) {
            val start = builder.length
            builder.append(text[i])
            builder.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor(colors[i % colors.size])),
                start, builder.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        textView.text = builder
    }

    override fun onScoreUpdate(score: Int, topScore: Int) {
        runOnUiThread {
            scoreText.text = String.format("%06d", score)
            topScoreText.text = String.format("%06d", topScore)
        }
    }

    override fun onLevelUpdate(level: Int, lines: Int) {
        runOnUiThread {
            levelText.text = String.format("%02d", level)
            linesText.text = String.format("%03d", lines)
        }
    }

    override fun onNextPieceUpdate(nextType: TetrominoType) {
        runOnUiThread { nextBlockView.setNextPiece(nextType) }
    }

    override fun onStatsUpdate(stats: Map<TetrominoType, Int>) {
        runOnUiThread { statsView.updateStats(stats) }
    }

    override fun onGameOver() {
        runOnUiThread {
            if (bgMusic?.isPlaying == true) bgMusic?.pause()
            overlayGameOver.visibility = View.VISIBLE
            clearSavedGame()
        }
    }
}