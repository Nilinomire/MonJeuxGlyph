package com.example.glyphgame

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastAcceleration = 0f
    private var currentAcceleration = SensorManager.GRAVITY_EARTH

    private var isRunning = false
    private var isAirborne = false
    private var score = 0
    private var obstaclePos = 4

    private var gameJob: Job? = null
    private var jumpJob: Job? = null
    private var lastJumpTimeMs = 0L

    private lateinit var statusText: TextView
    private lateinit var scoreText: TextView
    private lateinit var actionBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
        }

        statusText = TextView(this).apply {
            text = "GLYPH RUNNER 1D\n\nSecoue le téléphone pour sauter !"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
        }

        scoreText = TextView(this).apply {
            text = "Score : 0"
            setTextColor(0xFFFF0000.toInt())
            textSize = 28f
            setPadding(0, 32, 0, 32)
        }

        actionBtn = Button(this).apply {
            text = "DÉMARRER LE JEU"
            setOnClickListener {
                if (!isRunning) startGame() else triggerJump()
            }
        }

        layout.addView(statusText)
        layout.addView(scoreText)
        layout.addView(actionBtn)
        setContentView(layout)

        initSensors()
    }

    private fun initSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopGame()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        lastAcceleration = currentAcceleration
        val gTotal = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        currentAcceleration = gTotal
        val delta = abs(currentAcceleration - lastAcceleration)
        val normalizedG = gTotal / SensorManager.GRAVITY_EARTH

        if (delta > 8.5f || normalizedG > 1.95f) {
            val now = System.currentTimeMillis()
            if (now - lastJumpTimeMs > 600L) {
                lastJumpTimeMs = now
                if (isRunning) triggerJump() else startGame()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerJump() {
        if (isAirborne || !isRunning) return
        isAirborne = true
        vibratePhone(50L)
        statusText.text = "SAUT EN COURS !"

        jumpJob?.cancel()
        jumpJob = CoroutineScope(Dispatchers.Main).launch {
            delay(700L)
            isAirborne = false
            if (isRunning) statusText.text = "En course..."
        }
    }

    private fun startGame() {
        isRunning = true
        score = 0
        obstaclePos = 4
        isAirborne = false
        scoreText.text = "Score : 0"
        actionBtn.text = "SAUTER !"
        statusText.text = "En course..."
        vibratePhone(60L)

        gameJob?.cancel()
        gameJob = CoroutineScope(Dispatchers.Main).launch {
            var tickDelay = 550L
            while (isActive && isRunning) {
                delay(tickDelay)
                if (obstaclePos > 0) {
                    obstaclePos -= 1
                } else {
                    if (isAirborne) {
                        score++
                        scoreText.text = "Score : $score"
                        vibratePhone(40L)
                        tickDelay = (550L - (score * 15L)).coerceAtLeast(280L)
                        obstaclePos = 4
                    } else {
                        handleGameOver()
                        break
                    }
                }
            }
        }
    }

    private fun handleGameOver() {
        isRunning = false
        vibratePhone(400L)
        statusText.text = "GAME OVER !\nScore final : $score"
        actionBtn.text = "RECOMMENCER"
    }

    private fun stopGame() {
        isRunning = false
        gameJob?.cancel()
        jumpJob?.cancel()
    }

    private fun vibratePhone(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(durationMs)
            }
        } catch (ignored: Exception) {}
    }
}
