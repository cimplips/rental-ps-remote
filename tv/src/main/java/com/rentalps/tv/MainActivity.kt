package com.rentalps.tv

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
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
    private lateinit var gameView: TextView

    private var timerOverlay: TextView? = null
    private var blankOverlayView: View? = null

    private lateinit var preferences: SharedPreferences

    private var tvServer: TvServer? = null
    private var sessionTimer: CountDownTimer? = null

    @Volatile
    private var sessionEndTimeMillis = 0L

    @Volatile
    private var pausedRemainingMillis = 0L

    @Volatile
    private var sessionPaused = false

    private var title = "WAKTU HABIS"
    private var message = "Silakan ke kasir"
    private var bill = ""

    private var timerOverlayUntilMillis = 0L

    private val windowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        preferences =
            getSharedPreferences(
                "rental_ps_tv",
                MODE_PRIVATE
            )

        loadSavedSettings()
        buildUi()
        restoreSavedSession()
        startTvServer()
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    private fun buildUi() {

        root =
            FrameLayout(this).apply {
                setBackgroundColor(
                    Color.BLACK
                )
            }

        gameView =
            TextView(this).apply {
                text =
                    "Rental PS TV\n\nMenunggu koneksi HP"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }

        root.addView(
            gameView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val controlPanel =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(
                    dp(20),
                    dp(20),
                    dp(20),
                    dp(20)
                )
                setBackgroundColor(
                    Color.argb(
                        210,
                        18,
                        22,
                        28
                    )
                )
            }

        val statusText =
            TextView(this).apply {
                text =
                    "TV siap digunakan"
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(
                    0,
                    0,
                    0,
                    dp(10)
                )
            }

        val overlayButton =
            createButton(
                "Tampilkan di aplikasi lain"
            )

        overlayButton.setOnClickListener {
            openOverlayPermission()
        }

        val minimizeButton =
            createButton(
                "Minimize"
            )

        minimizeButton.setOnClickListener {
            moveTaskToBack(true)
        }

        controlPanel.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        controlPanel.addView(
            overlayButton,
            buttonParams()
        )

        controlPanel.addView(
            minimizeButton,
            buttonParams()
        )

        val panelParams =
            FrameLayout.LayoutParams(
                dp(280),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                setMargins(
                    0,
                    dp(20),
                    dp(20),
                    0
                )
            }

        root.addView(
            controlPanel,
            panelParams
        )

        setContentView(root)
    }

    private fun createButton(
        textValue: String
    ): Button {
        return Button(this).apply {
            text = textValue
            textSize = 13f
            isAllCaps = false
            setTextColor(
                Color.rgb(
                    45,
                    52,
                    64
                )
            )
            setBackgroundColor(
                Color.rgb(
                    235,
                    238,
                    242
                )
            )
            minHeight = dp(48)
        }
    }

    private fun buttonParams():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(50)
        ).apply {
            topMargin = dp(8)
        }
    }

    private fun loadSavedSettings() {

        title =
            preferences.getString(
                "title",
                "WAKTU HABIS"
            ) ?: "WAKTU HABIS"

        message =
            preferences.getString(
                "message",
                "Silakan ke kasir"
            ) ?: "Silakan ke kasir"

        bill =
            preferences.getString(
                "bill",
                ""
            ) ?: ""
    }

    private fun startTvServer() {

        tvServer =
            TvServer(
                port = 8787,
                onCommand = {
                    command ->
                    handleCommand(command)
                },
                onStatusRequest = {
                    val now =
                        System.currentTimeMillis()

                    if (sessionPaused) {
                        "STATUS|PAUSED|$pausedRemainingMillis"
                    } else if (
                        sessionEndTimeMillis > now
                    ) {
                        "STATUS|ACTIVE|$sessionEndTimeMillis"
                    } else {
                        "STATUS|IDLE|0"
                    }
                }
            )

        tvServer?.start()
    }

    private fun handleCommand(
        command: String
    ) {

        when {
            command.startsWith("START:") -> {

                val seconds =
                    command
                        .substringAfter("START:")
                        .toLongOrNull()
                        ?: 0L

                if (seconds > 0L) {
                    startSession(
                        seconds * 1000L
                    )
                }
            }

            command.startsWith("ADD:") -> {

                val seconds =
                    command
                        .substringAfter("ADD:")
                        .toLongOrNull()
                        ?: 0L

                if (seconds > 0L) {
                    addSessionTime(
                        seconds * 1000L
                    )
                }
            }

            command == "PAUSE" -> {
                pauseSession()
            }

            command == "RESUME" -> {
                resumeSession()
            }

            command == "STOP" -> {
                stopSession()
            }

            command.startsWith("SET_TITLE:") -> {

                title =
                    command
                        .substringAfter("SET_TITLE:")
                        .trim()

                saveDisplaySettings()
            }

            command.startsWith("SET_MESSAGE:") -> {

                message =
                    command
                        .substringAfter("SET_MESSAGE:")
                        .trim()

                saveDisplaySettings()
            }

            command.startsWith("SET_BILL:") -> {

                bill =
                    command
                        .substringAfter("SET_BILL:")
                        .trim()

                saveDisplaySettings()
            }

            command == "CLEAR_BILL" -> {

                bill = ""
                saveDisplaySettings()
            }
        }
    }

    private fun startSession(
        durationMillis: Long
    ) {

        runOnUiThread {

            removeBlankOverlay()

            sessionPaused = false
            pausedRemainingMillis = 0L

            sessionEndTimeMillis =
                System.currentTimeMillis() +
                    durationMillis

            saveSession()

            showTimerOverlayTemporarily()

            gameView.text =
                "Sesi aktif"

            gameView.setTextColor(
                Color.TRANSPARENT
            )

            startSessionTimer()
        }
    }

    private fun addSessionTime(
        durationMillis: Long
    ) {

        runOnUiThread {

            if (sessionPaused) {

                pausedRemainingMillis +=
                    durationMillis

                saveSession()

                showTimerOverlayTemporarily()

                return@runOnUiThread
            }

            val now =
                System.currentTimeMillis()

            val base =
                if (
                    sessionEndTimeMillis > now
                ) {
                    sessionEndTimeMillis
                } else {
                    now
                }

            sessionEndTimeMillis =
                base + durationMillis

            saveSession()

            showTimerOverlayTemporarily()

            removeBlankOverlay()

            startSessionTimer()
        }
    }

    private fun pauseSession() {

        runOnUiThread {

            if (sessionPaused) {
                return@runOnUiThread
            }

            val now =
                System.currentTimeMillis()

            val remaining =
                sessionEndTimeMillis - now

            if (remaining <= 0L) {
                showExpired()
                return@runOnUiThread
            }

            pausedRemainingMillis =
                remaining

            sessionEndTimeMillis = 0L
            sessionPaused = true

            sessionTimer?.cancel()
            sessionTimer = null

            saveSession()

            hideTimerOverlay()

            gameView.text =
                "SESI DI-PAUSE"

            gameView.setTextColor(
                Color.WHITE
            )
        }
    }

    private fun resumeSession() {

        runOnUiThread {

            if (!sessionPaused) {
                return@runOnUiThread
            }

            if (pausedRemainingMillis <= 0L) {
                showExpired()
                return@runOnUiThread
            }

            sessionEndTimeMillis =
                System.currentTimeMillis() +
                    pausedRemainingMillis

            pausedRemainingMillis = 0L
            sessionPaused = false

            saveSession()

            showTimerOverlayTemporarily()

            gameView.text =
                "Sesi aktif"

            gameView.setTextColor(
                Color.TRANSPARENT
            )

            removeBlankOverlay()

            startSessionTimer()
        }
    }

    private fun stopSession() {

        runOnUiThread {

            sessionTimer?.cancel()
            sessionTimer = null

            sessionEndTimeMillis = 0L
            pausedRemainingMillis = 0L
            sessionPaused = false

            clearSavedSession()
            hideTimerOverlay()

            showBlankOverlay()
        }
    }

    private fun startSessionTimer() {

        sessionTimer?.cancel()

        val endTime =
            sessionEndTimeMillis

        if (endTime <= 0L) {
            return
        }

        val remaining =
            endTime -
                System.currentTimeMillis()

        if (remaining <= 0L) {
            showExpired()
            return
        }

        sessionTimer =
            object : CountDownTimer(
                remaining,
                1000L
            ) {

                override fun onTick(
                    millisUntilFinished: Long
                ) {

                    val currentRemaining =
                        (
                            endTime -
                                System.currentTimeMillis()
                            ).coerceAtLeast(0L)

                    updateTimerOverlay(
                        currentRemaining
                    )
                }

                override fun onFinish() {

                    sessionTimer = null

                    showExpired()
                }
            }.start()
    }

    private fun updateTimerOverlay(
        remainingMillis: Long
    ) {

        if (
            remainingMillis <= 0L
        ) {
            hideTimerOverlay()
            return
        }

        if (
            remainingMillis <= 300_000L
        ) {
            showTimerOverlay()

            timerOverlay?.text =
                "◷ ${formatTime(remainingMillis)}"

            return
        }

        if (
            System.currentTimeMillis() >
                timerOverlayUntilMillis
        ) {
            hideTimerOverlay()
        } else {
            showTimerOverlay()

            timerOverlay?.text =
                "◷ ${formatTime(remainingMillis)}"
        }
    }

    private fun showTimerOverlayTemporarily() {

        timerOverlayUntilMillis =
            System.currentTimeMillis() +
                10_000L

        val remaining =
            if (sessionPaused) {
                pausedRemainingMillis
            } else {
                (
                    sessionEndTimeMillis -
                        System.currentTimeMillis()
                    ).coerceAtLeast(0L)
            }

        if (remaining > 0L) {
            showTimerOverlay()

            timerOverlay?.text =
                "◷ ${formatTime(remaining)}"
        }
    }

    private fun showTimerOverlay() {

        runOnUiThread {

            if (timerOverlay == null) {
                timerOverlay =
                    TextView(this).apply {
                        textSize = 13f
                        setTextColor(
                            Color.argb(
                                210,
                                255,
                                255,
                                255
                            )
                        )
                        setBackgroundColor(
                            Color.argb(
                                55,
                                0,
                                0,
                                0
                            )
                        )
                        setPadding(
                            dp(10),
                            dp(6),
                            dp(10),
                            dp(6)
                        )
                    }
            }

            if (
                timerOverlay?.parent == null
            ) {

                val params =
                    WindowManager.LayoutParams(
                        dp(110),
                        dp(42),
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity =
                            Gravity.BOTTOM or
                                Gravity.END
                        x = dp(18)
                        y = dp(18)
                    }

                if (
                    Settings.canDrawOverlays(this)
                ) {
                    try {
                        windowManager.addView(
                            timerOverlay,
                            params
                        )
                    } catch (_: Exception) {
                    }
                } else {
                    timerOverlay?.let {
                        if (it.parent == null) {
                            root.addView(
                                it,
                                FrameLayout.LayoutParams(
                                    dp(110),
                                    dp(42),
                                    Gravity.BOTTOM or
                                        Gravity.END
                                ).apply {
                                    setMargins(
                                        0,
                                        0,
                                        dp(18),
                                        dp(18)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            timerOverlay?.visibility =
                View.VISIBLE
        }
    }

    private fun hideTimerOverlay() {

        runOnUiThread {

            timerOverlay?.visibility =
                View.GONE
        }
    }

    private fun showExpired() {

        sessionTimer?.cancel()
        sessionTimer = null

        sessionEndTimeMillis = 0L
        pausedRemainingMillis = 0L
        sessionPaused = false

        clearSavedSession()
        hideTimerOverlay()

        showBlankOverlay()
    }

    private fun showBlankOverlay() {

        runOnUiThread {

            gameView.setTextColor(
                Color.TRANSPARENT
            )

            gameView.text = ""

            if (
                blankOverlayView != null
            ) {
                return@runOnUiThread
            }

            val content =
                LinearLayout(this).apply {
                    orientation =
                        LinearLayout.VERTICAL

                    gravity =
                        Gravity.CENTER

                    setPadding(
                        dp(32),
                        dp(32),
                        dp(32),
                        dp(32)
                    )

                    setBackgroundColor(
                        Color.BLACK
                    )
                }

            val titleText =
                TextView(this).apply {
                    text = title
                    textSize = 30f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                }

            val messageText =
                TextView(this).apply {
                    text = message
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTextColor(
                        Color.rgb(
                            215,
                            218,
                            224
                        )
                    )
                    setPadding(
                        0,
                        dp(18),
                        0,
                        dp(12)
                    )
                }

            val billText =
                TextView(this).apply {
                    text =
                        if (bill.isEmpty()) {
                            ""
                        } else {
                            bill
                        }

                    textSize = 24f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                }

            content.addView(
                titleText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            content.addView(
                messageText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            content.addView(
                billText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            if (
                Settings.canDrawOverlays(this)
            ) {

                val params =
                    WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.OPAQUE
                    ).apply {
                        gravity = Gravity.CENTER
                    }

                try {
                    windowManager.addView(
                        content,
                        params
                    )

                    blankOverlayView =
                        content

                } catch (_: Exception) {
                    root.addView(
                        content,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )

                    blankOverlayView =
                        content
                }

            } else {

                root.addView(
                    content,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )

                blankOverlayView =
                    content
            }
        }
    }

    private fun removeBlankOverlay() {

        runOnUiThread {

            val view =
                blankOverlayView
                    ?: return@runOnUiThread

            try {
                if (view.parent != null) {
                    if (view.parent === root) {
                        root.removeView(view)
                    } else {
                        windowManager.removeView(view)
                    }
                }
            } catch (_: Exception) {
                try {
                    root.removeView(view)
                } catch (_: Exception) {
                    try {
                        windowManager.removeView(view)
                    } catch (_: Exception) {
                    }
                }
            }

            blankOverlayView = null
        }
    }

    private fun openOverlayPermission() {

        if (
            Settings.canDrawOverlays(this)
        ) {
            return
        }

        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse(
                        "package:$packageName"
                    )
                )
            )
        } catch (_: Exception) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                )
            )
        }
    }

    private fun saveDisplaySettings() {

        preferences.edit()
            .putString(
                "title",
                title
            )
            .putString(
                "message",
                message
            )
            .putString(
                "bill",
                bill
            )
            .apply()
    }

    private fun saveSession() {

        preferences.edit()
            .putLong(
                "session_end_time",
                sessionEndTimeMillis
            )
            .putLong(
                "paused_remaining_time",
                pausedRemainingMillis
            )
            .putBoolean(
                "session_paused",
                sessionPaused
            )
            .apply()
    }

    private fun restoreSavedSession() {

        sessionEndTimeMillis =
            preferences.getLong(
                "session_end_time",
                0L
            )

        pausedRemainingMillis =
            preferences.getLong(
                "paused_remaining_time",
                0L
            )

        sessionPaused =
            preferences.getBoolean(
                "session_paused",
                false
            )

        if (sessionPaused) {

            if (
                pausedRemainingMillis > 0L
            ) {
                gameView.text =
                    "SESI DI-PAUSE"

                gameView.setTextColor(
                    Color.WHITE
                )
            } else {
                clearSavedSession()
            }

            return
        }

        if (
            sessionEndTimeMillis >
                System.currentTimeMillis()
        ) {

            gameView.text =
                "Sesi aktif"

            gameView.setTextColor(
                Color.TRANSPARENT
            )

            startSessionTimer()

        } else if (
            sessionEndTimeMillis > 0L
        ) {

            showExpired()
        }
    }

    private fun clearSavedSession() {

        preferences.edit()
            .remove(
                "session_end_time"
            )
            .remove(
                "paused_remaining_time"
            )
            .remove(
                "session_paused"
            )
            .apply()
    }

    private fun formatTime(
        millis: Long
    ): String {

        val totalSeconds =
            millis.coerceAtLeast(0L) /
                1000L

        val hours =
            totalSeconds / 3600L

        val minutes =
            (
                totalSeconds % 3600L
                ) / 60L

        val seconds =
            totalSeconds % 60L

        return String.format(
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    }

    override fun onResume() {
        super.onResume()

        if (
            sessionPaused
        ) {
            return
        }

        if (
            sessionEndTimeMillis >
                System.currentTimeMillis()
        ) {
            startSessionTimer()
        }
    }

    override fun onDestroy() {

        sessionTimer?.cancel()
        sessionTimer = null

        tvServer?.stop()
        tvServer = null

        removeBlankOverlay()

        try {
            timerOverlay?.let {
                if (it.parent === root) {
                    root.removeView(it)
                } else if (it.parent != null) {
                    windowManager.removeView(it)
                }
            }
        } catch (_: Exception) {
        }

        timerOverlay = null

        super.onDestroy()
    }
}
