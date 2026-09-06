package com.rentalps.admin

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.net.Socket
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var preferences: SharedPreferences

    private val executor = Executors.newCachedThreadPool()

    private var sessionTimer: CountDownTimer? = null

    private val statusHandler =
        Handler(Looper.getMainLooper())

    private val homeTimerHandler =
        Handler(Looper.getMainLooper())

    private val homeTimerViews =
        mutableMapOf<Int, TextView>()

    private enum class TvConnectionState {
        UNCHECKED,
        CONNECTED,
        DISCONNECTED
    }

    private val tvConnectionStatus =
        mutableMapOf<Int, TvConnectionState>()

    // Nomor generasi mencegah hasil STATUS lama menimpa hasil perintah terbaru.
    private val statusRequestGeneration =
        mutableMapOf<Int, Long>()

    // Satu command untuk satu meja diproses berurutan agar START/ADD/PAUSE/RESUME/STOP
    // tidak saling bertabrakan ketika tombol ditekan cepat atau polling berjalan bersamaan.
    private val tableCommandLocks = Array(11) { Any() }

    private val homeTimerRunnable =
        object : Runnable {
            override fun run() {
                if (screen != Screen.HOME) return

                val now = System.currentTimeMillis()
                homeTimerViews.forEach { (tableNumber, textView) ->
                    val endTime = preferences.getLong(
                        tableKey(tableNumber, "session_end_time"),
                        0L
                    )
                    val active = preferences.getBoolean(
                        tableKey(tableNumber, "active"),
                        false
                    ) && !preferences.getBoolean(
                        tableKey(tableNumber, "paused"),
                        false
                    )

                    if (active && endTime > now) {
                        textView.text = formatTime(endTime - now)
                    } else if (active && endTime > 0L) {
                        textView.text = "00:00:00"
                    }
                }

                homeTimerHandler.postDelayed(this, 1_000L)
            }
        }

    private val statusPollRunnable =
        object : Runnable {
            override fun run() {
                when (screen) {
                    Screen.TABLE -> {
                        syncTableStatus(
                            selectedTable,
                            rebuildWhenChanged = true
                        )
                        statusHandler.postDelayed(this, 3_000L)
                    }

                    Screen.HOME -> {
                        for (tableNumber in 1..TABLE_COUNT) {
                            syncTableStatus(
                                tableNumber,
                                rebuildWhenChanged = true
                            )
                        }
                        statusHandler.postDelayed(this, 5_000L)
                    }

                    else -> Unit
                }
            }
        }

    private var homeRefreshScheduled = false
    private var selectedTable = 1
    private var screen = Screen.HOME

    private var qrisPickerTable = 1
    private var pendingQrisBase64 = ""

    private var sessionPrice = 0L
    private var pausedRemainingMillis = 0L
    private var isPaused = false

    private lateinit var root: LinearLayout

    private enum class Screen {
        HOME,
        TABLE,
        PS_SETTINGS,
        TABLE_SETTINGS,
        TV_SETTINGS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences(
            "rental_ps_admin",
            MODE_PRIVATE
        )

        buildHomeScreen()
        requestInitialTvRecovery()
    }

    override fun onResume() {
        super.onResume()
        if (screen == Screen.TABLE) {
            restoreTableSession(selectedTable)
            syncTableStatus(selectedTable, rebuildWhenChanged = true)
        } else {
            buildHomeScreen()
            requestInitialTvRecovery()
        }
    }

    private fun requestInitialTvRecovery() {
        // SharedPreferences tetap menjadi sumber data lokal saat aplikasi dibuka.
        // Status TV kemudian dipakai untuk mengoreksi ACTIVE/PAUSED/IDLE.
        statusHandler.post {
            if (screen != Screen.HOME) return@post
            for (tableNumber in 1..TABLE_COUNT) {
                syncTableStatus(
                    tableNumber,
                    rebuildWhenChanged = true
                )
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun buildBase(titleText: String, subtitleText: String? = null) {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(245, 247, 250))
            isFillViewport = true
            isSmoothScrollingEnabled = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
            clipToPadding = false
            setPadding(0, 0, 0, dp(24))
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(32))
        }

        scroll.addView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        if (screen != Screen.HOME) {
            val back = Button(this).apply {
                text = "‹"
                textSize = 28f
                setTextColor(Color.rgb(55, 63, 75))
                setBackgroundColor(Color.TRANSPARENT)
                minWidth = dp(44)
                minHeight = dp(48)
                setOnClickListener {
                    sessionTimer?.cancel()
                    stopStatusPolling()
                    screen = Screen.HOME
                    buildHomeScreen()
                }
            }
            header.addView(back, LinearLayout.LayoutParams(dp(50), dp(52)))
        }

        val title = TextView(this).apply {
            text = titleText
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(35, 42, 52))
        }

        header.addView(title, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(header, matchParentWrapContent())

        if (!subtitleText.isNullOrBlank()) {
            val subtitle = TextView(this).apply {
                text = subtitleText
                textSize = 13f
                setTextColor(Color.rgb(110, 118, 130))
                setPadding(0, 0, 0, dp(14))
            }
            root.addView(subtitle, matchParentWrapContent())
        }

        setContentView(scroll)
    }

    private fun loadCimpliPsLogo(): android.graphics.Bitmap? {
        return try {
            val data = Base64.decode(CIMPLI_PS_LOGO_BASE64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildHomeScreen() {
        screen = Screen.HOME
        sessionTimer?.cancel()
        homeTimerHandler.removeCallbacks(homeTimerRunnable)
        homeTimerViews.clear()

        buildBase(
            "Rental PS",
            "Kelola meja dan sesi PlayStation"
        )

        // Logo CimpliPS dibuat compact agar dashboard tetap sederhana dan ruang scroll maksimal.
        val logoView = ImageView(this).apply {
            setImageBitmap(loadCimpliPsLogo())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Logo CimpliPS"
        }
        root.addView(
            logoView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)).apply {
                bottomMargin = dp(2)
            }
        )

        val activeSessionCount = (1..TABLE_COUNT).count { isTableActive(it) && !isTablePaused(it) }
        val pausedSessionCount = (1..TABLE_COUNT).count { isTablePaused(it) }

        // Kontrol utama dashboard sengaja dibuat kecil dan diletakkan di kanan atas.
        // Jika semua sesi aktif sudah di-pause, tombol berubah menjadi RESUME ALL.
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        val pauseAllButton = createSmallDashboardButton(
            if (activeSessionCount > 0) "Ⅱ PAUSE ALL" else "▶ RESUME ALL"
        ).apply {
            contentDescription = if (activeSessionCount > 0) "Pause semua sesi" else "Resume semua sesi"
            isEnabled = activeSessionCount > 0 || pausedSessionCount > 0
            alpha = if (isEnabled) 1f else 0.55f
            setOnClickListener {
                if (activeSessionCount > 0) {
                    showPauseAllConfirmation()
                } else if (pausedSessionCount > 0) {
                    showResumeAllConfirmation()
                }
            }
        }

        topBar.addView(
            pauseAllButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32))
        )

        root.addView(
            topBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply {
                topMargin = dp(2)
                bottomMargin = dp(8)
            }
        )

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (row in 0 until 5) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            for (column in 0 until 2) {
                val tableNumber = row * 2 + column + 1
                rowLayout.addView(
                    createTableCard(tableNumber),
                    LinearLayout.LayoutParams(0, dp(166), 1f).apply {
                        if (column == 0) rightMargin = dp(5)
                        else leftMargin = dp(5)
                        bottomMargin = dp(10)
                    }
                )
            }

            grid.addView(rowLayout, matchParentWrapContent())
        }

        root.addView(grid, matchParentWrapContent())

        addSectionTitle(root, "Pengaturan")

        val psSettingsButton = createSoftButton("Harga & Durasi PS")
        psSettingsButton.setOnClickListener {
            screen = Screen.PS_SETTINGS
            buildPsSettingsScreen()
        }
        root.addView(psSettingsButton, matchParentButton())

        val tableSettingsButton = createSoftButton("Pengaturan Meja • PS & IP TV")
        tableSettingsButton.setOnClickListener {
            screen = Screen.TABLE_SETTINGS
            buildTableSettingsScreen()
        }
        root.addView(tableSettingsButton, matchParentButton())

        val tvSettingsButton = createSoftButton("Pengaturan Tampilan TV")
        tvSettingsButton.setOnClickListener {
            screen = Screen.TV_SETTINGS
            buildTvSettingsScreen()
        }
        root.addView(tvSettingsButton, matchParentButton())

        startStatusPolling()
        homeTimerHandler.post(homeTimerRunnable)
    }

    private fun createSummaryCard(
        label: String,
        value: String,
        valueColor: Int
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(Color.WHITE)

            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 12f
                setTextColor(Color.rgb(125, 132, 143))
            }, matchParentWrapContent())

            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 25f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(valueColor)
            }, matchParentWrapContent())
        }
    }

    private fun createTableCard(tableNumber: Int): LinearLayout {
        val active = isTableActive(tableNumber)
        val paused = isTablePaused(tableNumber)
        val psType = getTablePsType(tableNumber)
        val remaining = getTableRemaining(tableNumber)
        val connectionState = getTvConnectionState(tableNumber)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            setBackgroundColor(
                when {
                    active && !paused -> Color.rgb(237, 248, 241)
                    paused -> Color.rgb(250, 247, 239)
                    else -> Color.WHITE
                }
            )
            // Kartu meja hanya sebagai tampilan/status.
            // Detail meja tidak lagi dibuka dengan mengetuk kartu.
            // Pengaturan PS dan IP TV dilakukan melalui tombol Pengaturan Meja di bagian bawah dashboard.
            isClickable = false
            isFocusable = false
        }

        // Indikator koneksi tetap kecil di kanan atas kartu.
        val connectionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        connectionRow.addView(TextView(this).apply {
            text = "●"
            textSize = 10f
            setTextColor(
                when (connectionState) {
                    TvConnectionState.CONNECTED -> Color.rgb(55, 170, 95)
                    TvConnectionState.DISCONNECTED -> Color.rgb(205, 105, 105)
                    TvConnectionState.UNCHECKED -> Color.rgb(185, 190, 198)
                }
            )
        }, LinearLayout.LayoutParams(dp(12), dp(18)))

        connectionRow.addView(TextView(this).apply {
            text = when (connectionState) {
                TvConnectionState.CONNECTED -> "CONNECT"
                TvConnectionState.DISCONNECTED -> "OFFLINE"
                TvConnectionState.UNCHECKED -> "CONNECT"
            }
            textSize = 8f
            setTextColor(Color.rgb(120, 126, 137))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(18)))

        card.addView(
            connectionRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(18))
        )

        // Nomor meja dibuat besar dan rata tengah.
        card.addView(TextView(this).apply {
            text = String.format(Locale.US, "%02d", tableNumber)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(40, 47, 58))
            setPadding(0, dp(1), 0, 0)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)))

        // Jenis PS tetap mengikuti pengaturan meja.
        card.addView(TextView(this).apply {
            text = psType
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(90, 97, 108))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(22)))

        val timerText = TextView(this).apply {
            text = when {
                active && !paused -> formatTime(remaining)
                paused -> "PAUSE  •  ${formatTime(remaining)}"
                else -> "--:--:--"
            }
            textSize = if (active || paused) 21f else 15f
            typeface = if (active || paused) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            gravity = Gravity.CENTER
            setTextColor(
                when {
                    paused -> Color.rgb(145, 112, 60)
                    active -> Color.rgb(55, 125, 88)
                    else -> Color.rgb(150, 155, 164)
                }
            )
            setPadding(0, dp(2), 0, dp(4))
        }
        card.addView(
            timerText,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38))
        )

        if (active && !paused) {
            homeTimerViews[tableNumber] = timerText
        }

        if (!active && !paused) {
            val startButton = createSmallDashboardButton("▶ MULAI").apply {
                setOnClickListener {
                    selectedTable = tableNumber
                    restoreTableSession(tableNumber)
                    showStartDurationDialog()
                }
            }
            card.addView(
                startButton,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)).apply {
                    topMargin = dp(2)
                }
            )
        } else {
            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val addButton = createSmallDashboardButton("+ JAM").apply {
                setOnClickListener {
                    selectedTable = tableNumber
                    restoreTableSession(tableNumber)
                    addOneHourToTable(tableNumber)
                }
            }

            val pauseButton = createSmallDashboardButton(
                if (paused) "▶ LANJUT" else "Ⅱ PAUSE"
            ).apply {
                setOnClickListener {
                    selectedTable = tableNumber
                    restoreTableSession(tableNumber)
                    if (paused) resumeTable() else pauseTable()
                }
            }

            val finishButton = createSmallDashboardButton("SELESAI").apply {
                setOnClickListener {
                    selectedTable = tableNumber
                    restoreTableSession(tableNumber)
                    showFinishSessionConfirmation()
                }
            }

            actionRow.addView(addButton, LinearLayout.LayoutParams(0, dp(34), 1f).apply { rightMargin = dp(3) })
            actionRow.addView(pauseButton, LinearLayout.LayoutParams(0, dp(34), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
            actionRow.addView(finishButton, LinearLayout.LayoutParams(0, dp(34), 1f).apply { leftMargin = dp(3) })

            card.addView(
                actionRow,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34))
            )
        }

        return card
    }

    private fun addOneHourToTable(tableNumber: Int) {
        selectedTable = tableNumber
        restoreTableSession(tableNumber)
        addOneHour()
        screen = Screen.HOME
        buildHomeScreen()
    }

    private fun showDashboardAddHourChooser() {
        val activeTables = (1..TABLE_COUNT)
            .filter { isTableActive(it) && !isTablePaused(it) }

        if (activeTables.isEmpty()) {
            showToast("Tidak ada sesi aktif yang bisa ditambah jam")
            return
        }

        val tableNames = activeTables.map {
            String.format(Locale.US, "Meja %02d", it)
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Tambah 1 Jam")
            .setItems(tableNames) { _, which ->
                val tableNumber = activeTables[which]
                selectedTable = tableNumber
                addOneHourToTable(tableNumber)
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    private fun buildTableScreen() {
        sessionTimer?.cancel()
        restoreTableSession(selectedTable)

        val psType = getTablePsType(selectedTable)
        val pricePerHour = getPsPrice(psType)

        buildBase(
            String.format(Locale.US, "Meja %02d", selectedTable),
            "${getPsName(psType)}  •  ${formatRupiah(pricePerHour)} / jam"
        )

        syncTableStatus(selectedTable, rebuildWhenChanged = false)
        startStatusPolling()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(Color.WHITE)
        }

        card.addView(TextView(this).apply {
            text = getPsName(psType)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(45, 52, 64))
        }, matchParentWrapContent())

        val remainingText = TextView(this).apply {
            text = formatTime(currentTableRemaining())
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(55, 63, 75))
            setPadding(0, dp(20), 0, dp(2))
        }
        card.addView(remainingText, matchParentWrapContent())

        val statusText = TextView(this).apply {
            text = when {
                isPaused -> "● Sesi dijeda"
                isCurrentTableActive() -> "● Sesi aktif"
                else -> "○ Meja kosong"
            }
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(
                if (isPaused) Color.rgb(150, 112, 55)
                else Color.rgb(105, 113, 125)
            )
            setPadding(0, 0, 0, dp(14))
        }
        card.addView(statusText, matchParentWrapContent())

        if (!isCurrentTableActive() && !isPaused) {
            val startButton = createSmallDashboardButton("▶ MULAI")
            startButton.setOnClickListener {
                showStartDurationDialog()
            }
            card.addView(startButton, matchParentButton())
        } else {
            val firstRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val addButton = createSoftButton("+ JAM")
            addButton.setOnClickListener {
                addOneHour()
            }

            val pauseButton = createSoftButton(
                if (isPaused) "▶ LANJUT" else "Ⅱ PAUSE"
            )
            pauseButton.setOnClickListener {
                if (isPaused) resumeTable() else pauseTable()
            }

            firstRow.addView(
                addButton,
                LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                    rightMargin = dp(5)
                }
            )
            firstRow.addView(
                pauseButton,
                LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                    leftMargin = dp(5)
                }
            )

            card.addView(firstRow, matchParentWrapContent())

            val finishButton = createDangerButton("■  SELESAI")
            finishButton.setOnClickListener {
                showFinishSessionConfirmation()
            }
            card.addView(finishButton, matchParentButton())
        }

        val billCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(18), dp(14), dp(18))
            setBackgroundColor(Color.rgb(248, 249, 251))
        }

        billCard.addView(TextView(this@MainActivity).apply {
            text = "Tagihan"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(110, 118, 130))
        }, matchParentWrapContent())

        billCard.addView(TextView(this@MainActivity).apply {
            text = formatRupiah(sessionPrice)
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(70, 78, 92))
        }, matchParentWrapContent())

        card.addView(billCard, matchParentWrapContent())
        root.addView(card, matchParentWrapContent())

        if (isCurrentTableActive() && !isPaused) {
            startTableTimer(selectedTable, remainingText, statusText)
        }
    }

    private fun showStartDurationDialog() {
        val psType = getTablePsType(selectedTable)
        val baseDuration = getPsDuration(psType)
        val baseHours = (baseDuration / 60).coerceAtLeast(1)

        val options = arrayOf(
            "1 Jam",
            "2 Jam",
            "3 Jam",
            "4 Jam"
        )

        AlertDialog.Builder(this)
            .setTitle("Mulai Sesi")
            .setMessage("Durasi dasar ${baseDuration} menit • ${formatRupiah(getPsPrice(psType))} / jam")
            .setItems(options) { _, which ->
                val hours = which + 1
                startTableSession(hours)
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    private fun showPauseAllConfirmation() {
        val activeTables = (1..TABLE_COUNT)
            .filter { isTableActive(it) }

        if (activeTables.isEmpty()) {
            showToast("Tidak ada sesi aktif yang perlu di-pause")
            return
        }

        val tableNames = activeTables.joinToString(", ") {
            String.format(Locale.US, "Meja %02d", it)
        }

        AlertDialog.Builder(this)
            .setTitle("Pause Semua Sesi?")
            .setMessage(
                "Semua sesi aktif akan dijeda sementara. " +
                    "Timer di HP dan TV akan berhenti.\n\n" +
                    tableNames
            )
            .setNegativeButton("BATAL", null)
            .setPositiveButton("PAUSE SEMUA") { _, _ ->
                pauseAllActiveSessions(activeTables)
            }
            .show()
    }

    private fun showStopAllConfirmation() {
        val tables = (1..TABLE_COUNT)
            .filter { isTableActive(it) || isTablePaused(it) }

        if (tables.isEmpty()) {
            showToast("Tidak ada sesi yang perlu diakhiri")
            return
        }

        val tableNames = tables.joinToString(", ") {
            String.format(Locale.US, "Meja %02d", it)
        }

        AlertDialog.Builder(this)
            .setTitle("Akhiri Semua Sesi?")
            .setMessage(
                "Semua sesi yang aktif maupun pause akan diakhiri dan tidak bisa dilanjutkan lagi.\n\n" +
                    tableNames
            )
            .setNegativeButton("BATAL", null)
            .setPositiveButton("AKHIRI SEMUA") { _, _ ->
                stopAllSessions(tables)
            }
            .show()
    }

    private fun stopAllSessions(tables: List<Int>) {
        var stoppedCount = 0

        for (tableNumber in tables) {
            sendCommandToTable(tableNumber, "STOP")

            preferences.edit()
                .remove(tableKey(tableNumber, "session_end_time"))
                .remove(tableKey(tableNumber, "session_price"))
                .remove(tableKey(tableNumber, "paused_remaining"))
                .putBoolean(tableKey(tableNumber, "active"), false)
                .putBoolean(tableKey(tableNumber, "paused"), false)
                .apply()

            if (screen == Screen.TABLE && selectedTable == tableNumber) {
                sessionPrice = 0L
                pausedRemainingMillis = 0L
                isPaused = false
            }

            stoppedCount++
        }

        showToast("$stoppedCount sesi berhasil diakhiri")

        if (screen == Screen.HOME) {
            buildHomeScreen()
        } else if (screen == Screen.TABLE) {
            buildTableScreen()
        }
    }

    private fun showResumeAllConfirmation() {
        val pausedTables = (1..TABLE_COUNT)
            .filter { isTablePaused(it) }

        if (pausedTables.isEmpty()) {
            showToast("Tidak ada sesi pause yang perlu dilanjutkan")
            return
        }

        val tableNames = pausedTables.joinToString(", ") {
            String.format(Locale.US, "Meja %02d", it)
        }

        AlertDialog.Builder(this)
            .setTitle("Lanjutkan Semua Sesi?")
            .setMessage(
                "Semua sesi yang sedang pause akan dilanjutkan. " +
                    "Pastikan TV dan listrik sudah siap.\n\n" +
                    tableNames
            )
            .setNegativeButton("BATAL", null)
            .setPositiveButton("LANJUT SEMUA") { _, _ ->
                resumeAllPausedSessions(pausedTables)
            }
            .show()
    }

    private fun resumeAllPausedSessions(pausedTables: List<Int>) {
        var resumedCount = 0

        for (tableNumber in pausedTables) {
            val remaining = preferences.getLong(
                tableKey(tableNumber, "paused_remaining"),
                0L
            )

            if (remaining <= 0L) {
                continue
            }

            val newEnd = System.currentTimeMillis() + remaining

            preferences.edit()
                .putBoolean(tableKey(tableNumber, "active"), true)
                .putBoolean(tableKey(tableNumber, "paused"), false)
                .putLong(tableKey(tableNumber, "session_end_time"), newEnd)
                .putLong(tableKey(tableNumber, "paused_remaining"), 0L)
                .apply()

            sendCommandToTable(
                tableNumber,
                "START:${remaining / 1000L}"
            )
            resumedCount++
        }

        if (resumedCount > 0) {
            showToast("$resumedCount sesi berhasil dilanjutkan")
        } else {
            showToast("Tidak ada sesi pause yang bisa dilanjutkan")
        }

        if (screen == Screen.HOME) {
            buildHomeScreen()
        }
    }

    private fun pauseAllActiveSessions(activeTables: List<Int>) {
        val now = System.currentTimeMillis()
        var pausedCount = 0

        for (tableNumber in activeTables) {
            val endTime = preferences.getLong(
                tableKey(tableNumber, "session_end_time"),
                0L
            )
            val remaining = maxOf(0L, endTime - now)

            if (remaining <= 0L) {
                continue
            }

            preferences.edit()
                .putBoolean(tableKey(tableNumber, "active"), false)
                .putBoolean(tableKey(tableNumber, "paused"), true)
                .putLong(tableKey(tableNumber, "session_end_time"), 0L)
                .putLong(tableKey(tableNumber, "paused_remaining"), remaining)
                .apply()

            sendCommandToTable(tableNumber, "PAUSE")
            pausedCount++
        }

        if (pausedCount > 0) {
            showToast("$pausedCount sesi berhasil di-pause")
        } else {
            showToast("Tidak ada sesi aktif yang bisa di-pause")
        }

        if (screen == Screen.HOME) {
            buildHomeScreen()
        }
    }

    private fun startTableSession(hours: Int) {
        val psType = getTablePsType(selectedTable)
        val durationMinutes = getPsDuration(psType) * hours
        val price = getPsPrice(psType) * hours
        val endTime = System.currentTimeMillis() + durationMinutes * 60_000L

        sessionPrice = price
        pausedRemainingMillis = 0L
        isPaused = false

        preferences.edit()
            .putLong(tableKey(selectedTable, "session_end_time"), endTime)
            .putLong(tableKey(selectedTable, "session_price"), price)
            .putBoolean(tableKey(selectedTable, "active"), true)
            .putBoolean(tableKey(selectedTable, "paused"), false)
            .putLong(tableKey(selectedTable, "paused_remaining"), 0L)
            .apply()

        sendCommandToTable(selectedTable, "START:${durationMinutes * 60L}")
        buildTableScreen()
    }

    private fun addOneHour() {
        val psType = getTablePsType(selectedTable)
        val addedMillis = 3_600_000L
        val addedPrice = getPsPrice(psType)

        if (isPaused || preferences.getBoolean(tableKey(selectedTable, "paused"), false)) {
            val currentPaused = maxOf(
                pausedRemainingMillis,
                preferences.getLong(
                    tableKey(selectedTable, "paused_remaining"),
                    0L
                )
            )

            if (currentPaused <= 0L) return

            sessionPrice += addedPrice
            pausedRemainingMillis = currentPaused + addedMillis
            isPaused = true

            preferences.edit()
                .putLong(tableKey(selectedTable, "session_price"), sessionPrice)
                .putBoolean(tableKey(selectedTable, "active"), false)
                .putBoolean(tableKey(selectedTable, "paused"), true)
                .putLong(
                    tableKey(selectedTable, "paused_remaining"),
                    pausedRemainingMillis
                )
                .putLong(tableKey(selectedTable, "session_end_time"), 0L)
                .apply()

            sendCommandToTable(selectedTable, "ADD:3600")
            buildTableScreen()
            return
        }

        val currentEnd = preferences.getLong(
            tableKey(selectedTable, "session_end_time"),
            System.currentTimeMillis()
        )

        val baseEnd = maxOf(currentEnd, System.currentTimeMillis())
        val newEnd = baseEnd + addedMillis
        sessionPrice += addedPrice

        preferences.edit()
            .putLong(tableKey(selectedTable, "session_end_time"), newEnd)
            .putLong(tableKey(selectedTable, "session_price"), sessionPrice)
            .putBoolean(tableKey(selectedTable, "active"), true)
            .putBoolean(tableKey(selectedTable, "paused"), false)
            .putLong(tableKey(selectedTable, "paused_remaining"), 0L)
            .apply()

        isPaused = false
        pausedRemainingMillis = 0L

        sendCommandToTable(selectedTable, "ADD:3600")
        buildTableScreen()
    }

    private fun pauseTable() {
        val remaining = currentTableRemaining()
        if (remaining <= 0L) return

        pausedRemainingMillis = remaining
        isPaused = true
        sessionTimer?.cancel()
        sessionTimer = null

        preferences.edit()
            .putBoolean(tableKey(selectedTable, "paused"), true)
            .putLong(tableKey(selectedTable, "paused_remaining"), remaining)
            .putLong(tableKey(selectedTable, "session_end_time"), 0L)
            .apply()

        sendCommandToTable(selectedTable, "PAUSE")
        buildTableScreen()
    }

    private fun resumeTable() {
        if (pausedRemainingMillis <= 0L) return

        val newEnd = System.currentTimeMillis() + pausedRemainingMillis
        isPaused = false

        preferences.edit()
            .putBoolean(tableKey(selectedTable, "paused"), false)
            .putBoolean(tableKey(selectedTable, "active"), true)
            .putLong(tableKey(selectedTable, "session_end_time"), newEnd)
            .putLong(tableKey(selectedTable, "paused_remaining"), 0L)
            .apply()

        sendCommandToTable(
            selectedTable,
            "START:${pausedRemainingMillis / 1000L}"
        )

        pausedRemainingMillis = 0L
        buildTableScreen()
    }

    private fun showFinishSessionConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Akhiri Sesi?")
            .setMessage(
                String.format(
                    Locale.US,
                    "Sesi Meja %02d akan diakhiri dan tidak bisa dilanjutkan lagi.",
                    selectedTable
                )
            )
            .setNegativeButton("BATAL", null)
            .setPositiveButton("AKHIRI SESI") { _, _ ->
                finishTableSession()
            }
            .show()
    }

    private fun finishTableSession() {
        sessionTimer?.cancel()
        sessionTimer = null

        sendCommandToTable(selectedTable, "STOP")

        preferences.edit()
            .remove(tableKey(selectedTable, "session_end_time"))
            .remove(tableKey(selectedTable, "session_price"))
            .remove(tableKey(selectedTable, "paused_remaining"))
            .putBoolean(tableKey(selectedTable, "active"), false)
            .putBoolean(tableKey(selectedTable, "paused"), false)
            .apply()

        sessionPrice = 0L
        pausedRemainingMillis = 0L
        isPaused = false

        buildTableScreen()
    }

    private fun startTableTimer(
        tableNumber: Int,
        remainingText: TextView,
        statusText: TextView
    ) {
        val endTime = preferences.getLong(
            tableKey(tableNumber, "session_end_time"),
            0L
        )

        val remaining = endTime - System.currentTimeMillis()
        if (remaining <= 0L) {
            expireTableSession(tableNumber)
            return
        }

        sessionTimer?.cancel()
        sessionTimer = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val current = maxOf(
                    0L,
                    endTime - System.currentTimeMillis()
                )
                remainingText.text = formatTime(current)
                updateTimerAppearance(remainingText, statusText, current)
            }

            override fun onFinish() {
                remainingText.text = "00:00:00"
                verifyAndExpireTableSession(tableNumber)
            }
        }.start()
    }

    private fun verifyAndExpireTableSession(tableNumber: Int) {
        // Jangan langsung mengirim STOP hanya karena timer lokal HP habis.
        // TV adalah sumber kebenaran; cek STATUS terlebih dahulu agar koneksi
        // yang sempat putus tidak membuat sesi TV terhenti secara keliru.
        val requestGeneration = invalidateStatusRequests(tableNumber)
        val host = getTableIp(tableNumber)

        if (host.isBlank()) {
            // Tidak ada IP TV. Hanya bersihkan sesi lokal karena tidak ada
            // perangkat yang bisa dikirimi STOP.
            expireTableSessionLocally(tableNumber, sendStop = false)
            return
        }

        executor.execute {
            try {
                Socket(host, 8787).use { socket ->
                    socket.soTimeout = 2500

                    PrintWriter(socket.getOutputStream(), true).use { writer ->
                        writer.println("STATUS")
                        writer.flush()

                        val response =
                            socket.getInputStream()
                                .bufferedReader()
                                .readLine()
                                ?.trim()
                                .orEmpty()

                        runOnUiThread {
                            if (getStatusRequestGeneration(tableNumber) != requestGeneration) {
                                return@runOnUiThread
                            }

                            val validStatus = isValidTvStatusResponse(response)
                            tvConnectionStatus[tableNumber] =
                                if (validStatus) {
                                    TvConnectionState.CONNECTED
                                } else {
                                    TvConnectionState.DISCONNECTED
                                }

                            if (!validStatus) {
                                if (screen == Screen.TABLE && selectedTable == tableNumber) {
                                    scheduleTableRecoveryRefresh()
                                } else if (screen == Screen.HOME) {
                                    scheduleHomeRefresh()
                                }
                                return@runOnUiThread
                            }

                            val parts = response.split("|")
                            val status = parts.getOrNull(1)?.uppercase(Locale.US).orEmpty()
                            val value = parts.getOrNull(2)?.toLongOrNull() ?: 0L

                            when (status) {
                                "ACTIVE" -> {
                                    if (value > System.currentTimeMillis()) {
                                        // TV masih aktif. Pulihkan timer HP dari
                                        // waktu TV dan jangan kirim STOP.
                                        applyTvStatus(
                                            tableNumber,
                                            response,
                                            rebuildWhenChanged = true
                                        )
                                    } else {
                                        expireTableSessionLocally(tableNumber, sendStop = true)
                                    }
                                }

                                "PAUSED" -> {
                                    if (value > 0L) {
                                        // TV ternyata sedang pause. Pulihkan
                                        // kondisi pause HP tanpa menghentikan TV.
                                        applyTvStatus(
                                            tableNumber,
                                            response,
                                            rebuildWhenChanged = true
                                        )
                                    } else {
                                        expireTableSessionLocally(tableNumber, sendStop = true)
                                    }
                                }

                                "IDLE" -> {
                                    // TV sudah kosong, jadi sesi lokal memang
                                    // boleh dibersihkan tanpa mengirim STOP lagi.
                                    expireTableSessionLocally(tableNumber, sendStop = false)
                                }

                                else -> {
                                    // Response tidak valid dianggap belum aman
                                    // untuk mengakhiri sesi. Biarkan polling
                                    // berikutnya melakukan recovery.
                                    tvConnectionStatus[tableNumber] =
                                        TvConnectionState.DISCONNECTED
                                    if (screen == Screen.TABLE && selectedTable == tableNumber) {
                                        scheduleTableRecoveryRefresh()
                                    } else if (screen == Screen.HOME) {
                                        scheduleHomeRefresh()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (getStatusRequestGeneration(tableNumber) != requestGeneration) {
                        return@runOnUiThread
                    }

                    // TV tidak dapat dihubungi. Jangan kirim STOP dan jangan
                    // menghapus sesi lokal; polling berikutnya akan mencoba
                    // recovery ketika TV kembali terhubung.
                    tvConnectionStatus[tableNumber] = TvConnectionState.DISCONNECTED
                    if (screen == Screen.TABLE && selectedTable == tableNumber) {
                        scheduleTableRecoveryRefresh()
                    } else if (screen == Screen.HOME) {
                        scheduleHomeRefresh()
                    }
                }
            }
        }
    }

    private fun expireTableSession(tableNumber: Int) {
        // Dipertahankan untuk alur yang memang sudah memastikan sesi harus
        // berakhir. Untuk timer normal, gunakan verifyAndExpireTableSession().
        expireTableSessionLocally(tableNumber, sendStop = true)
    }

    private fun expireTableSessionLocally(
        tableNumber: Int,
        sendStop: Boolean
    ) {
        sessionTimer?.cancel()
        sessionTimer = null

        if (sendStop) {
            sendCommandToTable(tableNumber, "STOP")
        }

        preferences.edit()
            .remove(tableKey(tableNumber, "session_end_time"))
            .remove(tableKey(tableNumber, "session_price"))
            .remove(tableKey(tableNumber, "paused_remaining"))
            .putBoolean(tableKey(tableNumber, "active"), false)
            .putBoolean(tableKey(tableNumber, "paused"), false)
            .apply()

        if (screen == Screen.TABLE && selectedTable == tableNumber) {
            sessionPrice = 0L
            pausedRemainingMillis = 0L
            isPaused = false
            buildTableScreen()
        } else if (screen == Screen.HOME) {
            buildHomeScreen()
        }
    }

    private fun scheduleTableRecoveryRefresh() {
        if (screen != Screen.TABLE) return
        statusHandler.removeCallbacks(statusPollRunnable)
        statusHandler.postDelayed(statusPollRunnable, 3_000L)
    }

    private fun updateTimerAppearance(
        remainingText: TextView,
        statusText: TextView,
        remaining: Long
    ) {
        if (remaining <= 300_000L) {
            remainingText.setTextColor(Color.rgb(135, 92, 92))
            statusText.text = "● Sisa waktu kurang dari 5 menit"
        } else {
            remainingText.setTextColor(Color.rgb(55, 63, 75))
            statusText.text = "● Sesi aktif"
        }
    }

    private fun restoreTableSession(tableNumber: Int) {
        sessionPrice = preferences.getLong(
            tableKey(tableNumber, "session_price"),
            0L
        )
        isPaused = preferences.getBoolean(
            tableKey(tableNumber, "paused"),
            false
        )
        pausedRemainingMillis = preferences.getLong(
            tableKey(tableNumber, "paused_remaining"),
            0L
        )

        if (isPaused) return

        val endTime = preferences.getLong(
            tableKey(tableNumber, "session_end_time"),
            0L
        )

        if (endTime > 0L && endTime <= System.currentTimeMillis()) {
            verifyAndExpireTableSession(tableNumber)
        }
    }

    private fun isCurrentTableActive(): Boolean =
        preferences.getBoolean(
            tableKey(selectedTable, "active"),
            false
        ) && !isPaused

    private fun currentTableRemaining(): Long {
        return if (isPaused) {
            pausedRemainingMillis
        } else {
            maxOf(
                0L,
                preferences.getLong(
                    tableKey(selectedTable, "session_end_time"),
                    0L
                ) - System.currentTimeMillis()
            )
        }
    }

    private fun isTableActive(tableNumber: Int): Boolean {
        if (preferences.getBoolean(tableKey(tableNumber, "paused"), false)) {
            return false
        }
        return preferences.getBoolean(
            tableKey(tableNumber, "active"),
            false
        ) && preferences.getLong(
            tableKey(tableNumber, "session_end_time"),
            0L
        ) > System.currentTimeMillis()
    }

    private fun isTablePaused(tableNumber: Int): Boolean =
        preferences.getBoolean(tableKey(tableNumber, "paused"), false)

    private fun getTableRemaining(tableNumber: Int): Long {
        return if (isTablePaused(tableNumber)) {
            preferences.getLong(
                tableKey(tableNumber, "paused_remaining"),
                0L
            )
        } else {
            maxOf(
                0L,
                preferences.getLong(
                    tableKey(tableNumber, "session_end_time"),
                    0L
                ) - System.currentTimeMillis()
            )
        }
    }

    private fun countActiveTables(): Int {
        var count = 0
        for (table in 1..TABLE_COUNT) {
            if (isTableActive(table) || isTablePaused(table)) count++
        }
        return count
    }

    private fun buildPsSettingsScreen() {
        buildBase("Harga & Durasi PS", "Atur durasi dan harga dasar PS3, PS4, dan PS5")

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            PS_TYPES
        )

        root.addView(spinner, matchParentWrapContent())

        val durationInput = createNumberInput("Durasi dalam menit")
        val priceInput = createNumberInput("Harga dalam rupiah")
        attachNominalFormatter(priceInput)

        root.addView(durationInput, matchParentWrapContent())
        root.addView(priceInput, matchParentWrapContent())

        fun load() {
            val type = PS_TYPES[spinner.selectedItemPosition]
            durationInput.setText(getPsDuration(type).toString())
            priceInput.setText(getPsPrice(type).toString())
        }

        spinner.setSelection(0)
        load()

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                load()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        val save = createPrimaryButton("SIMPAN")
        save.setOnClickListener {
            val type = PS_TYPES[spinner.selectedItemPosition]
            val duration = durationInput.text.toString()
                .trim().toLongOrNull()?.coerceAtLeast(1L) ?: 60L
            val price = parseNominal(priceInput.text.toString())

            preferences.edit()
                .putInt(psKey(type, "duration"), duration.toInt())
                .putLong(psKey(type, "price"), price)
                .apply()

            showToast("Pengaturan $type tersimpan")
        }
        root.addView(save, matchParentButton())
    }

    private fun buildTableSettingsScreen() {
        buildBase(
            "Pengaturan Meja",
            "Atur jenis PS dan IP Android TV setiap meja"
        )

        val scrollInfo = TextView(this).apply {
            text = "Pilih meja untuk mengubah PS, IP TV, dan tes koneksi."
            textSize = 13f
            setTextColor(Color.rgb(120, 125, 135))
            setPadding(dp(2), dp(0), dp(2), dp(10))
        }
        root.addView(scrollInfo, matchParentWrapContent())

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (tableNumber in 1..TABLE_COUNT) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(Color.WHITE)
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val number = TextView(this).apply {
                text = String.format(Locale.US, "%02d", tableNumber)
                textSize = 24f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(45, 48, 55))
                gravity = Gravity.CENTER
            }
            header.addView(
                number,
                LinearLayout.LayoutParams(dp(48), dp(42))
            )

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(8), 0)
            }

            val psText = TextView(this@MainActivity).apply {
                text = getTablePsType(tableNumber)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(55, 58, 66))
            }

            val ipText = TextView(this@MainActivity).apply {
                text = if (getTableIp(tableNumber).isBlank()) {
                    "IP TV belum diatur"
                } else {
                    getTableIp(tableNumber)
                }
                textSize = 12f
                setTextColor(Color.rgb(125, 130, 140))
            }

            val statusText = TextView(this@MainActivity).apply {
                textSize = 11f
                setPadding(0, dp(2), 0, 0)
            }

            fun refreshStatus() {
                if (getTableIp(tableNumber).isBlank()) {
                    statusText.text = "● BELUM DIATUR"
                    statusText.setTextColor(Color.rgb(145, 150, 158))
                } else {
                    when (getTvConnectionState(tableNumber)) {
                        TvConnectionState.CONNECTED -> {
                            statusText.text = "● CONNECT"
                            statusText.setTextColor(Color.rgb(55, 170, 95))
                        }
                        TvConnectionState.DISCONNECTED -> {
                            statusText.text = "● OFFLINE"
                            statusText.setTextColor(Color.rgb(190, 90, 90))
                        }
                        TvConnectionState.UNCHECKED -> {
                            statusText.text = "● BELUM DICEK"
                            statusText.setTextColor(Color.rgb(145, 150, 158))
                        }
                    }
                }
            }

            info.addView(psText, matchParentWrapContent())
            info.addView(ipText, matchParentWrapContent())
            info.addView(statusText, matchParentWrapContent())

            header.addView(
                info,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            val editButton = createSmallDashboardButton("EDIT")
            editButton.setOnClickListener {
                showTableEditDialog(
                    tableNumber = tableNumber,
                    onSaved = {
                        psText.text = getTablePsType(tableNumber)
                        ipText.text = if (getTableIp(tableNumber).isBlank()) {
                            "IP TV belum diatur"
                        } else {
                            getTableIp(tableNumber)
                        }
                        refreshStatus()
                    }
                )
            }
            header.addView(
                editButton,
                LinearLayout.LayoutParams(dp(64), dp(32))
            )

            card.addView(header, matchParentWrapContent())
            list.addView(
                card,
                matchParentWrapContent().apply {
                    bottomMargin = dp(8)
                }
            )

            refreshStatus()
        }

        root.addView(list, matchParentWrapContent())

        val back = createSoftButton("KEMBALI")
        back.setOnClickListener {
            screen = Screen.HOME
            buildHomeScreen()
        }
        root.addView(back, matchParentButton())
    }

    private fun showTableEditDialog(
        tableNumber: Int,
        onSaved: () -> Unit
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(4))
        }

        val psSpinner = Spinner(this)
        psSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            PS_TYPES
        )
        psSpinner.setSelection(
            PS_TYPES.indexOf(getTablePsType(tableNumber)).coerceAtLeast(0)
        )
        container.addView(psSpinner, matchParentWrapContent())

        val ipInput = createInput("IP Android TV, contoh 192.168.1.20")
        ipInput.setText(getTableIp(tableNumber))
        container.addView(ipInput, matchParentWrapContent())

        val dialog = AlertDialog.Builder(this)
            .setTitle(String.format(Locale.US, "Pengaturan %02d", tableNumber))
            .setView(container)
            .setNegativeButton("BATAL", null)
            .setPositiveButton("SIMPAN", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ps = PS_TYPES[psSpinner.selectedItemPosition]
                val ip = ipInput.text.toString().trim()

                if (ip.isBlank()) {
                    ipInput.error = "IP Android TV wajib diisi"
                    return@setOnClickListener
                }

                val oldIp = getTableIp(tableNumber)

                preferences.edit()
                    .putString(tableKey(tableNumber, "ps_type"), ps)
                    .putString(tableKey(tableNumber, "tv_ip"), ip)
                    .apply()

                if (oldIp != ip) {
                    tvConnectionStatus[tableNumber] = TvConnectionState.UNCHECKED
                }

                onSaved()
                showToast(String.format(Locale.US, "Pengaturan %02d tersimpan", tableNumber))
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun buildTvSettingsScreen() {
        buildBase("Pengaturan Tampilan TV", "Atur tampilan yang muncul saat sesi berakhir")

        val tableLabel = createSectionLabel("Pilih TV")
        root.addView(tableLabel, matchParentWrapContent())

        val tableSpinner = Spinner(this)
        tableSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            (1..TABLE_COUNT).map { String.format(Locale.US, "%02d", it) }
        )
        root.addView(tableSpinner, matchParentWrapContent())

        val titleLabel = createSectionLabel("Judul waktu habis")
        root.addView(titleLabel, matchParentWrapContent())
        val titleInput = createInput("Contoh: WAKTU HABIS")
        root.addView(titleInput, matchParentWrapContent())

        val messageLabel = createSectionLabel("Pesan")
        root.addView(messageLabel, matchParentWrapContent())
        val messageInput = createInput("Contoh: Silakan ke kasir")
        root.addView(messageInput, matchParentWrapContent())

        val billLabel = createSectionLabel("Tagihan")
        root.addView(billLabel, matchParentWrapContent())
        val billInput = createNumberInput("Contoh: Rp10.000")
        attachNominalFormatter(billInput)
        root.addView(billInput, matchParentWrapContent())

        val qrisLabel = createSectionLabel("QRIS")
        root.addView(qrisLabel, matchParentWrapContent())

        val qrisPreview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setBackgroundColor(Color.rgb(245, 246, 248))
            contentDescription = "Preview QRIS"
        }
        root.addView(
            qrisPreview,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(220)
            ).apply {
                setMargins(dp(4), dp(4), dp(4), dp(8))
            }
        )

        val qrisStatus = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(110, 118, 130))
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        root.addView(qrisStatus, matchParentWrapContent())

        qrisPreviewForCurrentScreen = qrisPreview
        qrisStatusForCurrentScreen = qrisStatus

        fun loadQrisPreview(table: Int) {
            val base64 = preferences.getString(
                tableKey(table, "qris_image_base64"),
                ""
            ).orEmpty()
            pendingQrisBase64 = base64
            qrisPickerTable = table

            if (base64.isBlank()) {
                qrisPreview.setImageDrawable(null)
                qrisStatus.text = "Belum ada gambar QRIS"
                return
            }

            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    qrisPreview.setImageBitmap(bitmap)
                    qrisStatus.text = "QRIS tersimpan di HP"
                } else {
                    qrisPreview.setImageDrawable(null)
                    qrisStatus.text = "Gambar QRIS tidak valid"
                }
            } catch (_: Exception) {
                qrisPreview.setImageDrawable(null)
                qrisStatus.text = "Gambar QRIS tidak valid"
            }
        }

        tableSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                loadQrisPreview(position + 1)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        val chooseQris = createSoftButton("PILIH GAMBAR QRIS")
        chooseQris.setOnClickListener {
            qrisPickerTable = tableSpinner.selectedItemPosition + 1
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, REQUEST_QRIS_IMAGE)
        }
        root.addView(chooseQris, matchParentButton())

        val save = createPrimaryButton("SIMPAN KE TV")
        save.setOnClickListener {
            val table = tableSpinner.selectedItemPosition + 1
            val title = titleInput.text.toString().trim()
            val message = messageInput.text.toString().trim()
            val bill = billInput.text.toString().trim()

            if (title.isNotEmpty()) {
                sendCommandToTable(table, "SET_TITLE:$title")
            }
            if (message.isNotEmpty()) {
                sendCommandToTable(table, "SET_MESSAGE:$message")
            }
            if (bill.isNotEmpty()) {
                sendCommandToTable(table, "SET_BILL:$bill")
            } else {
                sendCommandToTable(table, "CLEAR_BILL")
            }

            if (pendingQrisBase64.isNotBlank()) {
                sendCommandToTable(table, "SET_IMAGE:$pendingQrisBase64")
            } else {
                sendCommandToTable(table, "CLEAR_IMAGE")
            }

            showToast("Tampilan TV ${String.format(Locale.US, "%02d", table)} disimpan")
        }
        root.addView(save, matchParentButton())

        val clearQris = createSoftButton("HAPUS QRIS DI TV")
        clearQris.setOnClickListener {
            val table = tableSpinner.selectedItemPosition + 1
            preferences.edit()
                .remove(tableKey(table, "qris_image_base64"))
                .apply()
            pendingQrisBase64 = ""
            qrisPreview.setImageDrawable(null)
            qrisStatus.text = "Belum ada gambar QRIS"
            sendCommandToTable(table, "CLEAR_IMAGE")
            showToast("QRIS TV ${String.format(Locale.US, "%02d", table)} dihapus")
        }
        root.addView(clearQris, matchParentButton())

        loadQrisPreview(1)
    }

    private fun encodeQrisImage(uri: android.net.Uri): String? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            input.use { stream ->
                val source = BitmapFactory.decodeStream(stream) ?: return null
                val maxSize = 700
                val scale = minOf(1f, maxSize.toFloat() / maxOf(source.width, source.height).toFloat())
                val bitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        source,
                        (source.width * scale).toInt().coerceAtLeast(1),
                        (source.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    source
                }

                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.WEBP, 70, output)

                if (bitmap !== source) {
                    bitmap.recycle()
                }
                source.recycle()

                Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_QRIS_IMAGE || resultCode != RESULT_OK) {
            return
        }

        val uri = data?.data ?: return
        val table = qrisPickerTable

        executor.execute {
            val encoded = encodeQrisImage(uri)

            runOnUiThread {
                if (encoded.isNullOrBlank()) {
                    showToast("Gagal membaca gambar QRIS")
                    return@runOnUiThread
                }

                pendingQrisBase64 = encoded
                preferences.edit()
                    .putString(tableKey(table, "qris_image_base64"), encoded)
                    .apply()

                if (table == qrisPickerTable) {
                    try {
                        val bytes = Base64.decode(encoded, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        qrisPreviewForCurrentScreen?.setImageBitmap(bitmap)
                    } catch (_: Exception) {
                    }
                }

                qrisStatusForCurrentScreen?.text = "QRIS siap disimpan ke TV"
                showToast("QRIS TV ${String.format(Locale.US, "%02d", table)} siap")
            }
        }
    }

    private var qrisPreviewForCurrentScreen: ImageView? = null
    private var qrisStatusForCurrentScreen: TextView? = null

    private fun createSectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(4), dp(10), dp(4), dp(4))
        }
    }

    private fun scheduleHomeRefresh() {
        if (homeRefreshScheduled) return
        homeRefreshScheduled = true

        statusHandler.postDelayed({
            homeRefreshScheduled = false
            if (screen == Screen.HOME) {
                buildHomeScreen()
            }
        }, 150L)
    }

    private fun startStatusPolling() {
        statusHandler.removeCallbacks(statusPollRunnable)

        if (screen == Screen.TABLE || screen == Screen.HOME) {
            statusHandler.post(statusPollRunnable)
        }
    }

    private fun stopStatusPolling() {
        statusHandler.removeCallbacks(statusPollRunnable)
    }

    private fun stopHomeTimer() {
        homeTimerHandler.removeCallbacks(homeTimerRunnable)
        homeTimerViews.clear()
    }

    private fun sendCommandToTable(tableNumber: Int, command: String) {
        // Batalkan secara logis request STATUS lama agar response yang terlambat
        // tidak mengembalikan UI ke kondisi sebelum perintah terbaru.
        val commandGeneration = invalidateStatusRequests(tableNumber)
        val host = getTableIp(tableNumber)
        val tableLabel = String.format(Locale.US, "%02d", tableNumber)

        if (host.isBlank()) {
            tvConnectionStatus[tableNumber] = TvConnectionState.DISCONNECTED
            runOnUiThread {
                when (screen) {
                    Screen.HOME -> buildHomeScreen()
                    Screen.TABLE -> buildTableScreen()
                    else -> Unit
                }
            }
            showToast("IP TV Meja $tableLabel belum diatur")
            return
        }

        executor.execute {
            synchronized(tableCommandLocks[tableNumber.coerceIn(0, tableCommandLocks.lastIndex)]) {
                try {
                Socket(host, 8787).use { socket ->
                    socket.soTimeout = 2500
                    PrintWriter(socket.getOutputStream(), true).use { writer ->
                        writer.println(command)
                        writer.flush()
                    }
                }

                // Jangan langsung menganggap TV benar-benar terhubung hanya karena
                // socket berhasil menerima perintah. Konfirmasi koneksi dilakukan
                // melalui STATUS setelah command selesai dikirim. Ini mencegah titik
                // hijau muncul ketika TV menerima koneksi tetapi server belum siap
                // memberikan status yang valid.
                runOnUiThread {
                    if (getStatusRequestGeneration(tableNumber) == commandGeneration) {
                        when (screen) {
                            Screen.HOME -> buildHomeScreen()
                            Screen.TABLE -> buildTableScreen()
                            else -> Unit
                        }
                    }
                }

                // Setelah perintah dikirim, baca kembali STATUS TV agar
                // tampilan HP segera mengikuti kondisi TV yang sebenarnya.
                Thread.sleep(250L)
                if (getStatusRequestGeneration(tableNumber) == commandGeneration) {
                    syncTableStatus(tableNumber, rebuildWhenChanged = true)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    tvConnectionStatus[tableNumber] = TvConnectionState.DISCONNECTED
                    when (screen) {
                        Screen.HOME -> buildHomeScreen()
                        Screen.TABLE -> buildTableScreen()
                        else -> Unit
                    }
                    showToast("Gagal terhubung ke TV meja $tableLabel")
                }
            }
            }
        }
    }

    private fun syncTableStatus(
        tableNumber: Int,
        rebuildWhenChanged: Boolean = false
    ) {
        // Setiap STATUS request mendapat generation baru. Dengan begitu dua
        // polling yang berjalan bersamaan tidak boleh saling menimpa hasil.
        // Hanya response dari request STATUS terbaru yang boleh memperbarui
        // status sesi dan indikator koneksi TV.
        val requestGeneration = invalidateStatusRequests(tableNumber)
        val host = getTableIp(tableNumber)

        if (host.isBlank()) {
            tvConnectionStatus[tableNumber] = TvConnectionState.DISCONNECTED
            if (screen == Screen.HOME) {
                scheduleHomeRefresh()
            }
            return
        }

        executor.execute {
            try {
                Socket(host, 8787).use { socket ->
                    socket.soTimeout = 2500

                    PrintWriter(socket.getOutputStream(), true).use { writer ->
                        writer.println("STATUS")
                        writer.flush()

                        val response =
                            socket.getInputStream()
                                .bufferedReader()
                                .readLine()
                                ?.trim()
                                .orEmpty()

                        runOnUiThread {
                            // Abaikan response lama jika ada perintah baru yang
                            // sudah dikirim setelah request STATUS ini dimulai.
                            if (getStatusRequestGeneration(tableNumber) != requestGeneration) {
                                return@runOnUiThread
                            }

                            val connected = isValidTvStatusResponse(response)
                            tvConnectionStatus[tableNumber] =
                                if (connected) {
                                    TvConnectionState.CONNECTED
                                } else {
                                    TvConnectionState.DISCONNECTED
                                }
                            if (!connected) {
                                if (rebuildWhenChanged && screen == Screen.HOME) {
                                    scheduleHomeRefresh()
                                }
                                return@runOnUiThread
                            }
                            applyTvStatus(
                                tableNumber = tableNumber,
                                response = response,
                                rebuildWhenChanged = rebuildWhenChanged
                            )
                            if (rebuildWhenChanged && screen == Screen.HOME) {
                                scheduleHomeRefresh()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    tvConnectionStatus[tableNumber] = TvConnectionState.DISCONNECTED
                    if (rebuildWhenChanged && screen == Screen.HOME) {
                        scheduleHomeRefresh()
                    }
                }
            }
        }
    }

    private fun invalidateStatusRequests(tableNumber: Int): Long {
        val next = getStatusRequestGeneration(tableNumber) + 1L
        statusRequestGeneration[tableNumber] = next
        return next
    }

    private fun getStatusRequestGeneration(tableNumber: Int): Long =
        statusRequestGeneration[tableNumber] ?: 0L

    private fun isValidTvStatusResponse(response: String): Boolean {
        val parts = response.split("|")
        if (parts.size != 3) return false
        if (!parts[0].equals("STATUS", ignoreCase = true)) return false

        val status = parts[1].uppercase(Locale.US)
        if (status !in setOf("ACTIVE", "PAUSED", "IDLE")) return false

        return parts[2].toLongOrNull() != null
    }

    private fun applyTvStatus(
        tableNumber: Int,
        response: String,
        rebuildWhenChanged: Boolean
    ) {
        val parts = response.split("|")
        if (parts.size < 3 || parts[0].uppercase(Locale.US) != "STATUS") {
            return
        }

        val status = parts[1].uppercase(Locale.US)
        val value = parts[2].toLongOrNull() ?: 0L

        val activeKey = tableKey(tableNumber, "active")
        val pausedKey = tableKey(tableNumber, "paused")
        val endKey = tableKey(tableNumber, "session_end_time")
        val pausedRemainingKey = tableKey(tableNumber, "paused_remaining")

        val oldActive =
            preferences.getBoolean(activeKey, false)
        val oldPaused =
            preferences.getBoolean(pausedKey, false)
        val oldEnd =
            preferences.getLong(endKey, 0L)
        val oldPausedRemaining =
            preferences.getLong(pausedRemainingKey, 0L)

        var changed = false

        when (status) {
            "ACTIVE" -> {
                if (value <= System.currentTimeMillis()) {
                    changed =
                        oldActive ||
                            oldPaused ||
                            oldEnd != 0L ||
                            oldPausedRemaining != 0L

                    preferences.edit()
                        .putBoolean(activeKey, false)
                        .putBoolean(pausedKey, false)
                        .remove(endKey)
                        .remove(pausedRemainingKey)
                        .apply()
                } else {
                    changed =
                        !oldActive ||
                            oldPaused ||
                            oldEnd != value ||
                            oldPausedRemaining != 0L

                    preferences.edit()
                        .putBoolean(activeKey, true)
                        .putBoolean(pausedKey, false)
                        .putLong(endKey, value)
                        .putLong(pausedRemainingKey, 0L)
                        .apply()
                }
            }

            "PAUSED" -> {
                if (value > 0L) {
                    changed =
                        !oldActive ||
                            !oldPaused ||
                            oldEnd != 0L ||
                            oldPausedRemaining != value

                    preferences.edit()
                        .putBoolean(activeKey, true)
                        .putBoolean(pausedKey, true)
                        .putLong(endKey, 0L)
                        .putLong(pausedRemainingKey, value)
                        .apply()
                }
            }

            "IDLE" -> {
                changed =
                    oldActive ||
                        oldPaused ||
                        oldEnd != 0L ||
                        oldPausedRemaining != 0L

                preferences.edit()
                    .putBoolean(activeKey, false)
                    .putBoolean(pausedKey, false)
                    .remove(endKey)
                    .remove(pausedRemainingKey)
                    .apply()
            }

            else -> return
        }

        if (rebuildWhenChanged && changed) {
            when {
                screen == Screen.TABLE && selectedTable == tableNumber -> {
                    restoreTableSession(tableNumber)
                    buildTableScreen()
                }

                screen == Screen.HOME -> scheduleHomeRefresh()
            }
        }
    }

    private fun getTablePsType(tableNumber: Int): String =
        preferences.getString(
            tableKey(tableNumber, "ps_type"),
            "PS3"
        ) ?: "PS3"

    private fun getTvConnectionState(tableNumber: Int): TvConnectionState =
        tvConnectionStatus[tableNumber] ?: TvConnectionState.UNCHECKED

    private fun isTvConnected(tableNumber: Int): Boolean =
        getTvConnectionState(tableNumber) == TvConnectionState.CONNECTED &&
            getTableIp(tableNumber).isNotBlank()

    private fun getTableIp(tableNumber: Int): String =
        preferences.getString(
            tableKey(tableNumber, "tv_ip"),
            ""
        ) ?: ""

    private fun getPsName(type: String): String =
        preferences.getString(
            psKey(type, "name"),
            type
        ) ?: type

    private fun getPsDuration(type: String): Long =
        preferences.getInt(
            psKey(type, "duration"),
            60
        ).toLong().coerceAtLeast(1L)

    private fun getPsPrice(type: String): Long {
        val default = when (type) {
            "PS3" -> 4_000L
            "PS4" -> 5_000L
            else -> 8_000L
        }
        return preferences.getLong(
            psKey(type, "price"),
            default
        ).coerceAtLeast(0L)
    }

    private fun psKey(type: String, field: String): String =
        "ps_${type.lowercase(Locale.US)}_$field"

    private fun tableKey(tableNumber: Int, field: String): String =
        "table_${tableNumber}_$field"

    private fun parseNominal(value: String): Long =
        value.replace(".", "")
            .replace(",", "")
            .replace("Rp", "", ignoreCase = true)
            .trim()
            .toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: 0L

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    }

    private fun formatRupiah(value: Long): String {
        return String.format(
            Locale.US,
            "Rp %,d",
            value
        ).replace(",", ".")
    }

    private fun createInput(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 15f
            setSingleLine(true)
            setTextColor(Color.rgb(45, 52, 64))
            setHintTextColor(Color.rgb(125, 132, 143))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(Color.WHITE)
            minHeight = dp(52)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
            ).apply {
                topMargin = dp(7)
                bottomMargin = dp(7)
            }
        }
    }

    private fun createNumberInput(hintText: String): EditText =
        createInput(hintText).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }

    private fun attachNominalFormatter(input: EditText) {
        var formatting = false

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (formatting) return

                val raw = s?.toString().orEmpty()
                    .replace(".", "")
                    .replace(",", "")
                    .filter { it.isDigit() }

                if (raw.isEmpty()) return

                val number = raw.toLongOrNull() ?: return
                val formatted = formatRupiah(number)

                if (formatted == s.toString()) return

                formatting = true
                input.setText(formatted)
                input.setSelection(formatted.length)
                formatting = false
            }
        })
    }

    private fun createSmallDashboardButton(textValue: String): Button {
        return Button(this).apply {
            text = textValue
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(80, 88, 100))
            setBackgroundColor(Color.rgb(238, 240, 244))
            isAllCaps = false
            minHeight = dp(40)
            minimumHeight = dp(40)
            setPadding(dp(10), 0, dp(10), 0)
            includeFontPadding = true
        }
    }

    private fun createSoftButton(textValue: String): Button {
        return Button(this).apply {
            text = textValue
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(55, 63, 75))
            setBackgroundColor(Color.rgb(232, 236, 241))
            isAllCaps = false
            minHeight = dp(52)
            minimumHeight = dp(52)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            includeFontPadding = true
        }
    }

    private fun createPrimaryButton(textValue: String): Button {
        return Button(this).apply {
            text = textValue
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(70, 78, 92))
            isAllCaps = false
            minHeight = dp(58)
            minimumHeight = dp(58)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
    }

    private fun createDangerButton(textValue: String): Button {
        return Button(this).apply {
            text = textValue
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(90, 70, 70))
            setBackgroundColor(Color.rgb(242, 232, 232))
            isAllCaps = false
            minHeight = dp(58)
            minimumHeight = dp(58)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
    }

    private fun addSectionTitle(root: LinearLayout, textValue: String) {
        root.addView(TextView(this).apply {
            text = textValue
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(50, 58, 70))
            setPadding(dp(2), dp(18), dp(2), dp(8))
        }, matchParentWrapContent())
    }

    private fun matchParentWrapContent(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

    private fun matchParentButton(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(58)
        ).apply {
            topMargin = dp(8)
        }

    private fun showToast(message: String) {
        runOnUiThread {
            android.widget.Toast.makeText(
                this,
                message,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        sessionTimer?.cancel()
        sessionTimer = null
        stopStatusPolling()
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val TABLE_COUNT = 10
        private const val REQUEST_QRIS_IMAGE = 4101
        private val PS_TYPES = arrayOf("PS3", "PS4", "PS5")
        const val CIMPLI_PS_LOGO_BASE64 = "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"
    }


}
