package com.rentalps.tv

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var timerText: TextView
    private lateinit var expiredText: TextView
    private lateinit var controlPanel: LinearLayout

    private lateinit var preferences: SharedPreferences

    private var countDownTimer: CountDownTimer? = null
    private var tvServer: TvServer? = null

    private var titleText = "WAKTU HABIS"
    private var messageText = "Silakan ke kasir"
    private var billText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        preferences = getSharedPreferences(
            "rental_ps_tv",
            MODE_PRIVATE
        )

        loadSettings()

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

    private fun loadSettings() {

        titleText = preferences.getString(
            "title",
            "WAKTU HABIS"
        ) ?: "WAKTU HABIS"

        messageText = preferences.getString(
            "message",
            "Silakan ke kasir"
        ) ?: "Silakan ke kasir"

        billText = preferences.getString(
            "bill",
            ""
        ) ?: ""
    }

    private fun saveSettings() {

        preferences.edit()
            .putString(
                "title",
                titleText
            )
            .putString(
                "message",
                messageText
            )
            .putString(
                "bill",
                billText
            )
            .apply()
    }

    private fun createScreen() {

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        timerText = TextView(this).apply {

            text = ""

            textSize = 14f

            setTextColor(
                Color.argb(
                    90,
                    255,
                    255,
                    255
                )
            )

            gravity = Gravity.CENTER

            visibility = View.GONE
        }

        val timerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {

            gravity =
                Gravity.BOTTOM or Gravity.END

            setMargins(
                0,
                0,
                24,
                18
            )
        }

        root.addView(
            timerText,
            timerParams
        )

        expiredText = TextView(this).apply {

            textSize = 24f

            setTextColor(Color.WHITE)

            gravity = Gravity.CENTER

            visibility = View.VISIBLE

            setPadding(
                40,
                40,
                40,
                40
            )
        }

        val expiredParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

        root.addView(
            expiredText,
            expiredParams
        )

        updateExpiredText()

        createControlPanel()

        setContentView(root)
    }

    private fun updateExpiredText() {

        val parts = mutableListOf<String>()

        if (titleText.isNotBlank()) {
            parts.add(titleText)
        }

        if (messageText.isNotBlank()) {
            parts.add(messageText)
        }

        if (billText.isNotBlank()) {
            parts.add("Tagihan: $billText")
        }

        expiredText.text =
            parts.joinToString("\n\n")
    }

    private fun createControlPanel() {

        controlPanel = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            gravity = Gravity.CENTER

            setPadding(
                8,
                8,
                8,
                8
            )

            setBackgroundColor(
                Color.argb(
                    150,
                    20,
                    20,
                    20
                )

            visibility = View.VISIBLE
        }

        val settingsButton = Button(this).apply {

            text = "⚙"

            textSize = 18f

            setOnClickListener {

                openOverlaySettings()
            }
        }

        val minimizeButton = Button(this).apply {

            text = "−"

            textSize = 22f

            setOnClickListener {

                minimizeApp()
            }
        }

        controlPanel.addView(
            settingsButton,
            LinearLayout.LayoutParams(
                64,
                56
            )
        )

        controlPanel.addView(
            minimizeButton,
            LinearLayout.LayoutParams(
                64,
                56
            )
        )

        val panelParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {

                gravity =
                    Gravity.TOP or Gravity.END

                setMargins(
                    0,
                    12,
                    12,
                    0
                )
            }

        root.addView(
            controlPanel,
            panelParams
        )
    }

    private fun openOverlaySettings() {

        try {

            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse(
                    "package:$packageName"
                )
            )

            startActivity(intent)

        } catch (_: Exception) {

            try {

                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                )

                startActivity(intent)

            } catch (_: Exception) {
            }
        }
    }

    private fun minimizeApp() {

        val intent = Intent(
            Intent.ACTION_MAIN
        ).apply {

            addCategory(
                Intent.CATEGORY_HOME
            )

            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK
        }

        startActivity(intent)
    }

    private fun startTvServer() {

        tvServer = TvServer(
            port = 8787
        ) { command ->

            handleCommand(command)
        }

        tvServer?.start()
    }

    private fun handleCommand(
        command: String
    ) {

        when {

            command == "PING" -> {
                // Tes koneksi.
            }

            command.startsWith("SET_TITLE:") -> {

                titleText =
                    command.substringAfter(
                        "SET_TITLE:"
                    ).trim()

                saveSettings()

                updateExpiredText()
            }

            command.startsWith("SET_MESSAGE:") -> {

                messageText =
                    command.substringAfter(
                        "SET_MESSAGE:"
                    ).trim()

                saveSettings()

                updateExpiredText()
            }

            command.startsWith("SET_BILL:") -> {

                billText =
                    command.substringAfter(
                        "SET_BILL:"
                    ).trim()

                saveSettings()

                updateExpiredText()
            }

            command == "CLEAR_BILL" -> {

                billText = ""

                saveSettings()

                updateExpiredText()
            }

            command.startsWith("START:") -> {

                val seconds =
                    command
                        .substringAfter("START:")
                        .toLongOrNull()

                if (
                    seconds != null &&
                    seconds > 0
                ) {

                    startTimer(
                        seconds * 1000L
                    )
                }
            }

            command.startsWith("ADD:") -> {

                val seconds =
                    command
                        .substringAfter("ADD:")
                        .toLongOrNull()

                if (
                    seconds != null &&
                    seconds > 0
                ) {

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

    private fun startTimer(
        durationMillis: Long
    ) {

        countDownTimer?.cancel()

        expiredText.visibility =
            View.GONE

        root.setBackgroundColor(
            Color.BLACK
        )

        countDownTimer =
            object : CountDownTimer(
                durationMillis,
                1000
            ) {

                override fun onTick(
                    millisUntilFinished: Long
                ) {

                    val totalSeconds =
                        millisUntilFinished / 1000

                    val hours =
                        totalSeconds / 3600

                    val minutes =
                        (totalSeconds % 3600) / 60

                    val seconds =
                        totalSeconds % 60

                    timerText.text =
                        String.format(
                            "%02d:%02d:%02d",
                            hours,
                            minutes,
                            seconds
                        )

                    timerText.visibility =
                        if (
                            totalSeconds <= 300
                        ) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                }

                override fun onFinish() {

                    timerText.visibility =
                        View.GONE

                    expiredText.visibility =
                        View.VISIBLE

                    root.setBackgroundColor(
                        Color.BLACK
                    )

                    updateExpiredText()
                }
            }.start()
    }

    private fun addTime(
        additionalMillis: Long
    ) {

        val currentText =
            timerText.text.toString()

        if (currentText.isEmpty()) {

            startTimer(
                additionalMillis
            )

            return
        }

        val parts =
            currentText.split(":")

        if (parts.size != 3) {

            startTimer(
                additionalMillis
            )

            return
        }

        val hours =
            parts[0].toLongOrNull() ?: 0

        val minutes =
            parts[1].toLongOrNull() ?: 0

        val seconds =
            parts[2].toLongOrNull() ?: 0

        val currentMillis =
            (
                hours * 3600 +
                minutes * 60 +
                seconds
            ) * 1000L

        startTimer(
            currentMillis +
                additionalMillis
        )
    }

    private fun stopTimer() {

        countDownTimer?.cancel()

        countDownTimer = null

        timerText.text = ""

        timerText.visibility =
            View.GONE

        expiredText.visibility =
            View.VISIBLE

        root.setBackgroundColor(
            Color.BLACK
        )

        updateExpiredText()
    }

    override fun onDestroy() {

        countDownTimer?.cancel()

        tvServer?.stop()

        tvServer = null

        super.onDestroy()
    }
}
