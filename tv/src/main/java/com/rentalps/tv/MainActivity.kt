package com.rentalps.tv

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var timerText: TextView
    private lateinit var expiredText: TextView

    private var countDownTimer: CountDownTimer? = null
    private var tvServer: TvServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        hideSystemBars()
        createScreen()

        startTvServer()
    }

    private fun hideSystemBars() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun createScreen() {

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        timerText = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.argb(90, 255, 255, 255))
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        val timerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 24, 18)
        }

        root.addView(
            timerText,
            timerParams
        )

        expiredText = TextView(this).apply {
            text = "WAKTU HABIS\n\nSilakan ke kasir"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = View.VISIBLE
        }

        val expiredParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        root.addView(
            expiredText,
            expiredParams
        )

        setContentView(root)
    }

    private fun startTvServer() {

        tvServer = TvServer(
            port = 8787
        ) { command ->

            handleCommand(command)
        }

        tvServer?.start()
    }

    private fun handleCommand(command: String) {

        when {

            command == "PING" -> {
                // Digunakan HP untuk mengecek
                // apakah TV dapat dihubungi.
            }

            command.startsWith("START:") -> {

                val seconds = command
                    .removePrefix("START:")
                    .toLongOrNull()

                if (seconds != null && seconds > 0) {

                    startTimer(
                        seconds * 1000L
                    )
                }
            }

            command.startsWith("ADD:") -> {

                val seconds = command
                    .removePrefix("ADD:")
                    .toLongOrNull()

                if (seconds != null && seconds > 0) {

                    addTime(
                        seconds * 1000L
                    )
                }
            }

            command == "STOP" -> {

                stopTimer()
            }
        }
    }

    private fun startTimer(durationMillis: Long) {

        countDownTimer?.cancel()

        expiredText.visibility = View.GONE

        root.setBackgroundColor(Color.TRANSPARENT)

        countDownTimer = object : CountDownTimer(
            durationMillis,
            1000
        ) {

            override fun onTick(
                millisUntilFinished: Long
            ) {

                val totalSeconds =
                    millisUntilFinished / 1000

                updateTimerDisplay(
                    totalSeconds
                )
            }

            override fun onFinish() {

                timerText.visibility =
                    View.GONE

                showExpiredScreen()
            }

        }.start()
    }

    private fun addTime(
        additionalMillis: Long
    ) {

        val currentRemaining =
            currentRemainingMillis()

        startTimer(
            currentRemaining + additionalMillis
        )
    }

    private fun currentRemainingMillis(): Long {

        return 0L
    }

    private fun stopTimer() {

        countDownTimer?.cancel()

        timerText.visibility =
            View.GONE

        showExpiredScreen()
    }

    private fun updateTimerDisplay(
        totalSeconds: Long
    ) {

        val hours =
            totalSeconds / 3600

        val minutes =
            (totalSeconds % 3600) / 60

        val seconds =
            totalSeconds % 60

        timerText.text = String.format(
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )

        timerText.visibility =
            if (totalSeconds <= 300) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun showExpiredScreen() {

        timerText.visibility =
            View.GONE

        root.setBackgroundColor(
            Color.BLACK
        )

        expiredText.text =
            "WAKTU HABIS\n\nSilakan ke kasir"

        expiredText.visibility =
            View.VISIBLE
    }

    override fun onDestroy() {

        countDownTimer?.cancel()

        tvServer?.stop()

        tvServer = null

        super.onDestroy()
    }
}
