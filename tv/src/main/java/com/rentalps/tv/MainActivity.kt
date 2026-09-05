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

    private var timerOverlayView: View? = null
    private var blankOverlayView: View? = null

    private var titleText = "WAKTU HABIS"
    private var messageText = "Silakan ke kasir"
    private var billText = ""

    private val handler =
        Handler(Looper.getMainLooper())

    /*
     * Waktu berakhir sesi.
     *
     * Ini menjadi sumber waktu utama.
     */
    private var sessionEndTimeMillis = 0L

    private var remainingMillis = 0L

    private val hideTimerOverlayRunnable =
        Runnable {
            hideTimerOverlay()
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(
            Window.FEATURE_NO_TITLE
        )

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        preferences =
            getSharedPreferences(
                "rental_ps_tv",
                MODE_PRIVATE
            )

        loadSettings()

        hideSystemBars()
        createScreen()
        startTvServer()

        /*
         * Setelah UI dan server siap,
         * cek apakah masih ada sesi aktif
         * yang tersimpan.
         */
        restoreSavedSession()
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

        titleText =
            preferences.getString(
                "title",
                "WAKTU HABIS"
            ) ?: "WAKTU HABIS"

        messageText =
            preferences.getString(
                "message",
                "Silakan ke kasir"
            ) ?: "Silakan ke kasir"

        billText =
            preferences.getString(
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

    /*
     * Simpan waktu berakhir sesi.
     */
    private fun saveSessionEndTime(
        endTimeMillis: Long
    ) {

        sessionEndTimeMillis =
            endTimeMillis

        preferences.edit()
            .putLong(
                "session_end_time",
                endTimeMillis
            )
            .apply()
    }

    /*
     * Hapus sesi tersimpan.
     */
    private fun clearSavedSession() {

        sessionEndTimeMillis = 0L

        preferences.edit()
            .remove(
                "session_end_time"
            )
            .apply()
    }

    /*
     * Ambil sesi yang tersimpan setelah
     * aplikasi TV dibuka kembali.
     */
    private fun restoreSavedSession() {

        val savedEndTime =
            preferences.getLong(
                "session_end_time",
                0L
            )

        if (
            savedEndTime <= 0L
        ) {
            return
        }

        val currentTime =
            System.currentTimeMillis()

        val remaining =
            savedEndTime -
                currentTime

        if (remaining > 0L) {

            sessionEndTimeMillis =
                savedEndTime

            startTimer(
                remaining,
                false
            )

        } else {

            clearSavedSession()

            expiredText.visibility =
                View.VISIBLE

            root.setBackgroundColor(
                Color.BLACK
            )

            updateExpiredText()

            showBlankOverlay()
        }
    }

    private fun createScreen() {

        root =
            FrameLayout(this).apply {

                setBackgroundColor(
                    Color.BLACK
                )
            }

        timerText =
            TextView(this).apply {

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

                gravity =
                    Gravity.CENTER

                visibility =
                    View.GONE

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
                    Gravity.BOTTOM or
                        Gravity.END

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

        expiredText =
            TextView(this).apply {

                textSize = 24f

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER

                visibility =
                    View.VISIBLE

                setPadding(
                    40,
                    40,
                    40,
                    40
                )
            }

        root.addView(
            expiredText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
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

        controlPanel =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER

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

                visibility =
                    View.VISIBLE
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
                    Gravity.TOP or
                        Gravity.END

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

            val intent =
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse(
                        "package:$packageName"
                    )
                )

            startActivity(intent)

        } catch (_: Exception) {

            try {

                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                    )
                )

            } catch (_: Exception) {
            }
        }
    }

    private fun minimizeApp() {

        val intent =
            Intent(
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

        tvServer =
            TvServer(
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

            command.startsWith(
                "SET_TITLE:"
            ) -> {

                titleText =
                    command
                        .substringAfter(
                            "SET_TITLE:"
                        )
                        .trim()

                saveSettings()

                updateExpiredText()

                updateBlankOverlayText()
            }

            command.startsWith(
                "SET_MESSAGE:"
            ) -> {

                messageText =
                    command
                        .substringAfter(
                            "SET_MESSAGE:"
                        )
                        .trim()

                saveSettings()

                updateExpiredText()

                updateBlankOverlayText()
            }

            command.startsWith(
                "SET_BILL:"
            ) -> {

                billText =
                    command
                        .substringAfter(
                            "SET_BILL:"
                        )
                        .trim()

                saveSettings()

                updateExpiredText()

                updateBlankOverlayText()
            }

            command == "CLEAR_BILL" -> {

                billText = ""

                saveSettings()

                updateExpiredText()

                updateBlankOverlayText()
            }

            command.startsWith(
                "START:"
            ) -> {

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
                        seconds * 1000L,
                        true
                    )
                }
            }

            command.startsWith(
                "ADD:"
            ) -> {

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

    /*
     * Memulai countdown berdasarkan
     * waktu absolut sesi.
     */
    private fun startTimer(
        durationMillis: Long,
        saveSession: Boolean
    ) {

        countDownTimer?.cancel()

        handler.removeCallbacks(
            hideTimerOverlayRunnable
        )

        val safeDuration =
            durationMillis.coerceAtLeast(
                1000L
            )

        if (saveSession) {

            saveSessionEndTime(
                System.currentTimeMillis() +
                    safeDuration
            )
        }

        remainingMillis =
            safeDuration

        expiredText.visibility =
            View.GONE

        root.setBackgroundColor(
            Color.BLACK
        )

        showTimerOverlay(
            remainingMillis
        )

        countDownTimer =
            object : CountDownTimer(
                safeDuration,
                1000L
            ) {

                override fun onTick(
                    millisUntilFinished: Long
                ) {

                    remainingMillis =
                        millisUntilFinished

                    val totalSeconds =
                        millisUntilFinished /
                            1000L

                    updateTimerOverlayText(
                        totalSeconds
                    )

                    if (
                        totalSeconds <= 300L
                    ) {

                        handler.removeCallbacks(
                            hideTimerOverlayRunnable
                        )

                        showTimerOverlay(
                            millisUntilFinished
                        )
                    }
                }

                override fun onFinish() {

                    remainingMillis = 0L

                    clearSavedSession()

                    handler.removeCallbacks(
                        hideTimerOverlayRunnable
                    )

                    hideTimerOverlay()

                    timerText.text = ""

                    expiredText.visibility =
                        View.VISIBLE

                    root.setBackgroundColor(
                        Color.BLACK
                    )

                    updateExpiredText()

                    showBlankOverlay()
                }
            }.start()

        /*
         * Timer hanya tampil 10 detik,
         * kecuali sudah masuk 5 menit terakhir.
         */
        handler.postDelayed(
            hideTimerOverlayRunnable,
            10_000L
        )
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

    private fun showTimerOverlay(
        millis: Long
    ) {

        if (
            !Settings.canDrawOverlays(this)
        ) {

            timerText.visibility =
                View.VISIBLE

            updateTimerText(
                millis / 1000L
            )

            return
        }

        val totalSeconds =
            millis / 1000L

        val windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager

        if (
            timerOverlayView == null
        ) {

            val overlay =
                TextView(this).apply {

                    tag =
                        "timer_overlay_text"

                    textSize = 14f

                    setTextColor(
                        Color.argb(
                            110,
                            255,
                            255,
                            255
                        )
                    )

                    gravity =
                        Gravity.CENTER

                    setPadding(
                        10,
                        5,
                        10,
                        5
                    )

                    setBackgroundColor(
                        Color.argb(
                            35,
                            255,
                            255,
                            255
                        )
                    )

                    isFocusable = false

                    isClickable = false

                    text =
                        formatTime(
                            totalSeconds
                        )
                }

            val params =
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {

                    gravity =
                        Gravity.BOTTOM or
                            Gravity.END

                    x = 24

                    y = 18
                }

            try {

                windowManager.addView(
                    overlay,
                    params
                )

                timerOverlayView =
                    overlay

            } catch (_: Exception) {

                timerText.visibility =
                    View.VISIBLE

                updateTimerText(
                    totalSeconds
                )

                return
            }

        } else {

            updateTimerOverlayText(
                totalSeconds
            )
        }
    }

    private fun updateTimerOverlayText(
        totalSeconds: Long
    ) {

        val view =
            timerOverlayView ?: return

        val textView =
            view as? TextView

        textView?.text =
            formatTime(
                totalSeconds
            )
    }

    private fun formatTime(
        totalSeconds: Long
    ): String {

        val hours =
            totalSeconds / 3600L

        val minutes =
            (totalSeconds % 3600L) / 60L

        val seconds =
            totalSeconds % 60L

        return String.format(
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    }

    private fun hideTimerOverlay() {

        val view =
            timerOverlayView

        if (view != null) {

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

            timerOverlayView = null
        }

        timerText.visibility =
            View.GONE
    }

    private fun addTime(
        additionalMillis: Long
    ) {

        val currentMillis =
            remainingMillis

        if (
            currentMillis <= 0L
        ) {

            startTimer(
                additionalMillis,
                true
            )

            return
        }

        /*
         * Karena sessionEndTimeMillis
         * adalah sumber waktu utama,
         * tambahkan waktu ke waktu akhir.
         */
        val currentEndTime =
            if (
                sessionEndTimeMillis > 0L
            ) {
                sessionEndTimeMillis
            } else {
                System.currentTimeMillis() +
                    currentMillis
            }

        val newEndTime =
            currentEndTime +
                additionalMillis

        val newDuration =
            newEndTime -
                System.currentTimeMillis()

        saveSessionEndTime(
            newEndTime
        )

        startTimer(
            newDuration,
            false
        )
    }

    private fun stopTimer() {

        countDownTimer?.cancel()

        countDownTimer = null

        remainingMillis = 0L

        clearSavedSession()

        handler.removeCallbacks(
            hideTimerOverlayRunnable
        )

        hideTimerOverlay()

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

        if (
            !Settings.canDrawOverlays(this)
        ) {
            return
        }

        if (
            blankOverlayView != null
        ) {

            updateBlankOverlayText()

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
                    Gravity.TOP or
                        Gravity.START

                screenOrientation =
                    android.content.pm.ActivityInfo
                        .SCREEN_ORIENTATION_LANDSCAPE
            }

        try {

            windowManager.addView(
                overlayRoot,
                params
            )

            blankOverlayView =
                overlayRoot

        } catch (_: Exception) {
        }
    }

    private fun updateBlankOverlayText() {

        val view =
            blankOverlayView ?: return

        val textView =
            view.findViewWithTag<TextView>(
                "expired_overlay_text"
            )

        textView?.text =
            buildExpiredText()
    }

    private fun removeBlankOverlay() {

        val view =
            blankOverlayView ?: return

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

        blankOverlayView = null
    }

    override fun onDestroy() {

        countDownTimer?.cancel()

        countDownTimer = null

        handler.removeCallbacksAndMessages(
            null
        )

        hideTimerOverlay()

        removeBlankOverlay()

        tvServer?.stop()

        tvServer = null

        super.onDestroy()
    }
}
