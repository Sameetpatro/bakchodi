package com.example.shhhhhhmita

import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.shhhhhhmita.R
import com.example.shhhhhhmita.Direction
import com.example.shhhhhhmita.GameView

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var scoreText: TextView
    private lateinit var pauseBtn: Button
    private lateinit var btnUp: ImageButton
    private lateinit var btnDown: ImageButton
    private lateinit var btnLeft: ImageButton
    private lateinit var btnRight: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gameView = findViewById(R.id.gameView)
        scoreText = findViewById(R.id.scoreText)
        pauseBtn = findViewById(R.id.pauseBtn)
        btnUp = findViewById(R.id.btnUp)
        btnDown = findViewById(R.id.btnDown)
        btnLeft = findViewById(R.id.btnLeft)
        btnRight = findViewById(R.id.btnRight)

        // Hook direction buttons
        btnUp.setOnClickListener { gameView.setDirection(Direction.UP) }
        btnDown.setOnClickListener { gameView.setDirection(Direction.DOWN) }
        btnLeft.setOnClickListener { gameView.setDirection(Direction.LEFT) }
        btnRight.setOnClickListener { gameView.setDirection(Direction.RIGHT) }

        pauseBtn.setOnClickListener {
            if (gameView.isPaused()) {
                gameView.resumeGame()
                pauseBtn.text = "Pause"
                playMusic()
            } else {
                gameView.pauseGame()
                pauseBtn.text = "Resume"
                pauseMusic()
            }
        }

        // Score update listener - use callback
        gameView.onScoreChange = { score ->
            runOnUiThread {
                scoreText.text = "Score: $score"
            }
        }

        // Play background music
        initMusic()
    }

    override fun onResume() {
        super.onResume()
        gameView.resumeGame()
        playMusic()
    }

    override fun onPause() {
        super.onPause()
        gameView.pauseGame()
        pauseMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun initMusic() {
        // Put your music file in res/raw/background_music.mp3
        // If you changed the name, update R.raw.background_music
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.bg_music_snake_game)
            mediaPlayer?.isLooping = true
            mediaPlayer?.setVolume(0.6f, 0.6f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playMusic() {
        try {
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pauseMusic() {
        try {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
