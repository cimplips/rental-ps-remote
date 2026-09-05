package com.rentalps.tv

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
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

    private var overlayView: View? = null

    private var titleText = "WAKTU HABIS"
    private var messageText = "Silakan ke kasir"
    private var billText = ""

    private val timerHandler =
        Handler(Looper.getMainLooper())

    private val hideTimerRunnable =
        Runnable {
            if (
                countDownTimer != null &&
                timerText.visibility == View.VISIBLE
            ) {
                timerText.visibility =
                    View.GONE
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(
            Window.FEATURE_NO_TITLE
        )

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
                    110,
                    255,
                    255,
                    255
                )
            )

            gravity = Gravity.CENTER

            visibility = View.GONE

            setPadding(
                10,
                5,
                10,
                5
            )
        }

        val timerParams =
            FrameLayout.LayoutParams(
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

        expiredText.text =
            buildExpiredText()
    }

    private fun buildExpiredText(): String {

        val parts =
            mutableListOf<String>()

        if (titleText.isNotBlank()) {
            parts.add(titleText)
        }

        if (messageText.isNotBlank()) {
            parts.add(messageText)
        }

        if (billText.isNotBlank()) {
            parts.add(
                "Tagihan: $billText"
            )
        }

        return parts.joinToString(
            "\n\n"
        )
    }

    private fun createControlPanel() {

        controlPanel = LinearLayout(this).apply {

            orientation =
                LinearLayout.HORIZONTAL

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
            )

            visibility = View.VISIBLE
        }

        val settingsButton =
            Button(this).apply {

                text = "⚙"

                textSize = 18f

                setOnClickListener {

                    openOverlaySettings()
                }
            }

        val minimizeButton =
            Button(this).apply {

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
                    command
                        .substringAfter(
                            "SET_TITLE:"
                        )
                        .trim()

                saveSettings()

                updateExpiredText()

                updateOverlayText()
            }

            command.startsWith("SET_MESSAGE:") -> {

                messageText =
                    command
                        .substringAfter(
                            "SET_MESSAGE:"
                        )
                        .trim()

                saveSettings()

                updateExpiredText()

                updateOverlayText()
            }

            command.startsWith("SET_BILL:") -> {

                billText =
                    command
                        .substringAfter(
                            "SET_BILL:"
                        )
                        .trim()

                saveSettings()

                updateExpiredText()

                updateOverlayText()
            }

            command == "CLEAR_BILL" -> {

                billText = ""

                saveSettings()

                updateExpiredText()

                updateOverlayText()
            }

            command.startsWith("START:") -> {

                val seconds =
                    command
                        .substringAfter(
                            "START:"
                        )
                        .toLongOrNull()

                if (
                    seconds != null &&
                    seconds > 0
                ) {

                    removeBlankOverlay()

                    startTimer(
                        seconds * 1000L
                    )
                }
            }

            command.startsWith("ADD:") -> {

                val seconds =
                    command
                        .substringAfter(
                            "ADD:"
                        )
                        .toLongOrNull()

                if (
                    seconds != null &&
                    seconds > 0
                ) {

                    removeBlankOverlay()

                    addTime(
                        seconds * 1000L
                    )
                }
            }

            command == "STOP" -> {

                stopTimer()

                showBlankOverlay()
            }
        }
    }

    private fun startTimer(
        durationMillis: Long
    ) {

        countDownTimer?.cancel()

        timerHandler.removeCallbacks(
            hideTimerRunnable
        )

        removeBlankOverlay()

        expiredText.visibility =
            View.GONE

        root.setBackgroundColor(
            Color.BLACK
        )

        /*
         * Tampilkan timer SEGERA
         * ketika sesi dimulai.
         */
        timerText.visibility =
            View.VISIBLE

        /*
         * Sembunyikan timer setelah
         * 10 detik.
         */
        timerHandler.postDelayed(
            hideTimerRunnable,
            10_000L
        )

        countDownTimer =
            object : CountDownTimer(
                durationMillis,
                1000L
            ) {

                override fun onTick(
                    millisUntilFinished: Long
                ) {

                    val totalSeconds =
                        millisUntilFinished / 1000L

                    updateTimerText(
                        totalSeconds
                    )

                    /*
                     * Lima menit terakhir:
                     * timer harus selalu tampil.
                     */
                    if (
                        totalSeconds <= 300L
                    ) {

                        timerHandler.removeCallbacks(
                            hideTimerRunnable
                        )

                        timerText.visibility =
                            View.VISIBLE
                    }
                }

                override fun onFinish() {

                    timerHandler.removeCallbacks(
                        hideTimerRunnable
                    )

                    timerText.visibility =
                        View.GONE

                    expiredText.visibility =
                        View.VISIBLE

                    root.setBackgroundColor(
                        Color.BLACK
                    )

                    updateExpiredText()

                    showBlankOverlay()
                }
            }.start()
    }

    private fun updateTimerText(
        totalSeconds: Long
    ) {

        val hours =
            totalSeconds / 3600L

        val minutes =
            (totalSeconds % 3600L) / 60L

        val seconds =
            totalSeconds % 60L

        timerText.text =
            String.format(
                "%02d:%02d:%02d",
                hours,
                minutes,
                seconds
            )
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
            parts[0].toLongOrNull() ?: 0L

        val minutes =
            parts[1].toLongOrNull() ?: 0L

        val seconds =
            parts[2].toLongOrNull() ?: 0L

        val currentMillis =
            (
                hours * 3600L +
                    minutes * 60L +
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

        timerHandler.removeCallbacks(
            hideTimerRunnable
        )

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

    private fun showBlankOverlay() {

        if (!Settings.canDrawOverlays(this)) {
            return
        }

        if (overlayView != null) {

            updateOverlayText()

            return
        }

        val overlayRoot =
            FrameLayout(this).apply {

                setBackgroundColor(
                    Color.BLACK
                )

                isFocusable = false
                isClickable = false
            }

        val overlayText =
            TextView(this).apply {

                tag =
                    "expired_overlay_text"

                textSize = 24f

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    40,
                    40,
                    40,
                    40
                )

                text =
                    buildExpiredText()
            }

        overlayRoot.addView(
            overlayText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {

                gravity =
                    Gravity.TOP or Gravity.START

                screenOrientation =
                    android.content.pm.ActivityInfo
                        .SCREEN_ORIENTATION_LANDSCAPE
            }

        try {

            windowManager.addView(
                overlayRoot,
                params
            )

            overlayView =
                overlayRoot

        } catch (_: Exception) {
        }
    }

    private fun updateOverlayText() {

        val view =
            overlayView ?: return

        val textView =
            view.findViewWithTag<TextView>(
                "expired_overlay_text"
            )

        textView?.text =
            buildExpiredText()
    }

    private fun removeBlankOverlay() {

        val view =
            overlayView ?: return

        val windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        try {

            windowManager.removeView(
                view
            )

        } catch (_: Exception) {
        }

        overlayView = null
    }

    override fun onDestroy() {

        countDownTimer?.cancel()

        timerHandler.removeCallbacks(
            hideTimerRunnable
        )

        removeBlankOverlay()

        tvServer?.stop()

        tvServer = null

        super.onDestroy()
    }
}
