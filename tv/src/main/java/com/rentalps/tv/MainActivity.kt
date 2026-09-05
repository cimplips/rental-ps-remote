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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        hideSystemBars()

        createScreen()
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

        root.addView(timerText, timerParams)

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

        root.addView(expiredText, expiredParams)

        setContentView(root)
    }

    private fun startTimer(durationMillis: Long) {

        countDownTimer?.cancel()

        expiredText.visibility = View.GONE

        countDownTimer = object : CountDownTimer(
            durationMillis,
            1000
        ) {

            override fun onTick(millisUntilFinished: Long) {

                val totalSeconds =
                    millisUntilFinished / 1000

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

            override fun onFinish() {

                timerText.visibility = View.GONE

                expiredText.visibility = View.VISIBLE

                root.setBackgroundColor(Color.BLACK)
            }

        }.start()
    }

    override fun onDestroy() {

        countDownTimer?.cancel()

        super.onDestroy()
    }
}
