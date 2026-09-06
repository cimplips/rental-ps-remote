package com.rentalps.admin

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
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
            isFillViewport = false
            isSmoothScrollingEnabled = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = false
            setPadding(0, 0, 0, dp(24))
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
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

        // Logo CimpliPS ditampilkan di dashboard sebagai identitas aplikasi.
        val logoView = ImageView(this).apply {
            setImageBitmap(loadCimpliPsLogo())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Logo CimpliPS"
        }
        root.addView(
            logoView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(112)).apply {
                bottomMargin = dp(4)
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
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34))
        )

        root.addView(
            topBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)).apply {
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
                    LinearLayout.LayoutParams(0, dp(176), 1f).apply {
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

        val psSettingsButton = createSoftButton("Pengaturan PS")
        psSettingsButton.setOnClickListener {
            screen = Screen.PS_SETTINGS
            buildPsSettingsScreen()
        }
        root.addView(psSettingsButton, matchParentButton())

        val tableSettingsButton = createSoftButton("Pengaturan Meja")
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
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedTable = tableNumber
                screen = Screen.TABLE
                restoreTableSession(tableNumber)
                buildTableScreen()
            }
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
                TvConnectionState.CONNECTED -> "Terhubung"
                TvConnectionState.DISCONNECTED -> "Tidak terhubung"
                TvConnectionState.UNCHECKED -> "Belum dicek"
            }
            textSize = 8.5f
            setTextColor(Color.rgb(120, 126, 137))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(18)))

        card.addView(
            connectionRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(20))
        )

        // Nomor meja dibuat besar dan rata tengah.
        card.addView(TextView(this).apply {
            text = String.format(Locale.US, "%02d", tableNumber)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(40, 47, 58))
            setPadding(0, dp(1), 0, 0)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))

        // Jenis PS tetap mengikuti pengaturan meja.
        card.addView(TextView(this).apply {
            text = psType
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(90, 97, 108))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(25)))

        val timerText = TextView(this).apply {
            text = when {
                active && !paused -> formatTime(remaining)
                paused -> "PAUSE  •  ${formatTime(remaining)}"
                else -> "--:--:--"
            }
            textSize = if (active || paused) 22f else 16f
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
            val startButton = createPrimaryButton("▶  MULAI").apply {
                setOnClickListener {
                    selectedTable = tableNumber
                    restoreTableSession(tableNumber)
                    showStartDurationDialog()
                }
            }
            card.addView(
                startButton,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)).apply {
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

            actionRow.addView(addButton, LinearLayout.LayoutParams(0, dp(36), 1f).apply { rightMargin = dp(3) })
            actionRow.addView(pauseButton, LinearLayout.LayoutParams(0, dp(36), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
            actionRow.addView(finishButton, LinearLayout.LayoutParams(0, dp(36), 1f).apply { leftMargin = dp(3) })

            card.addView(
                actionRow,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38))
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
            val startButton = createPrimaryButton("▶  MULAI")
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
        buildBase("Pengaturan PS", "Atur nama, durasi dasar dan harga setiap jenis PS")

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            PS_TYPES
        )

        root.addView(spinner, matchParentWrapContent())

        val nameInput = createInput("Nama")
        val durationInput = createNumberInput("Durasi dalam menit")
        val priceInput = createNumberInput("Harga dalam rupiah")
        attachNominalFormatter(priceInput)

        root.addView(nameInput, matchParentWrapContent())
        root.addView(durationInput, matchParentWrapContent())
        root.addView(priceInput, matchParentWrapContent())

        fun load() {
            val type = PS_TYPES[spinner.selectedItemPosition]
            nameInput.setText(getPsName(type))
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
                .putString(psKey(type, "name"), nameInput.text.toString().trim().ifEmpty { type })
                .putInt(psKey(type, "duration"), duration.toInt())
                .putLong(psKey(type, "price"), price)
                .apply()

            showToast("Pengaturan $type tersimpan")
        }
        root.addView(save, matchParentButton())
    }

    private fun buildTableSettingsScreen() {
        buildBase("Pengaturan Meja", "Tentukan PS dan IP Android TV untuk setiap meja")

        val tableSpinner = Spinner(this)
        tableSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            (1..TABLE_COUNT).map { String.format(Locale.US, "Meja %02d", it) }
        )
        root.addView(tableSpinner, matchParentWrapContent())

        val psSpinner = Spinner(this)
        psSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            PS_TYPES
        )
        root.addView(psSpinner, matchParentWrapContent())

        val ipInput = createInput("IP Android TV, contoh 192.168.1.20")
        root.addView(ipInput, matchParentWrapContent())

        val connectionStatus = TextView(this).apply {
            textSize = 13f
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        root.addView(connectionStatus, matchParentWrapContent())

        fun updateConnectionStatus(table: Int) {
            if (getTableIp(table).isBlank()) {
                connectionStatus.text = "● TV belum diatur"
                connectionStatus.setTextColor(Color.rgb(145, 150, 158))
            } else {
                when (getTvConnectionState(table)) {
                    TvConnectionState.CONNECTED -> {
                        connectionStatus.text = "● TV terhubung"
                        connectionStatus.setTextColor(Color.rgb(55, 170, 95))
                    }
                    TvConnectionState.DISCONNECTED -> {
                        connectionStatus.text = "● TV tidak terhubung"
                        connectionStatus.setTextColor(Color.rgb(190, 90, 90))
                    }
                    TvConnectionState.UNCHECKED -> {
                        connectionStatus.text = "● Belum dicek"
                        connectionStatus.setTextColor(Color.rgb(145, 150, 158))
                    }
                }
            }
        }

        fun load() {
            val table = tableSpinner.selectedItemPosition + 1
            val ps = getTablePsType(table)
            psSpinner.setSelection(PS_TYPES.indexOf(ps).coerceAtLeast(0))
            ipInput.setText(getTableIp(table))
            updateConnectionStatus(table)
        }

        load()

        tableSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
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
            val table = tableSpinner.selectedItemPosition + 1
            val ps = PS_TYPES[psSpinner.selectedItemPosition]
            val ip = ipInput.text.toString().trim()

            if (ip.isBlank()) {
                showToast("IP Android TV belum diisi")
                return@setOnClickListener
            }

            val oldIp = getTableIp(table)

            preferences.edit()
                .putString(tableKey(table, "ps_type"), ps)
                .putString(tableKey(table, "tv_ip"), ip)
                .apply()

            if (oldIp != ip) {
                tvConnectionStatus[table] = TvConnectionState.UNCHECKED
            }
            updateConnectionStatus(table)
            showToast(String.format(Locale.US, "Meja %02d tersimpan", table))
        }
        root.addView(save, matchParentButton())

        val testConnection = createSoftButton("TEST KONEKSI TV")
        testConnection.setOnClickListener {
            val table = tableSpinner.selectedItemPosition + 1
            val ip = ipInput.text.toString().trim()

            if (ip.isBlank()) {
                showToast("IP Android TV belum diisi")
                return@setOnClickListener
            }

            testConnection.isEnabled = false
            testConnection.text = "MENGECEK..."

            executor.execute {
                var success = false
                try {
                    Socket(ip, 8787).use { socket ->
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

                            success = response.startsWith("STATUS|", ignoreCase = true)
                        }
                    }
                } catch (_: Exception) {
                    success = false
                }

                runOnUiThread {
                    tvConnectionStatus[table] = if (success) TvConnectionState.CONNECTED else TvConnectionState.DISCONNECTED
                    testConnection.isEnabled = true
                    testConnection.text = "TEST KONEKSI TV"
                    updateConnectionStatus(table)
                    if (screen == Screen.HOME) {
                        scheduleHomeRefresh()
                    }
                    if (success) {
                        showToast(String.format(Locale.US, "TV Meja %02d terhubung", table))
                    } else {
                        showToast(String.format(Locale.US, "TV Meja %02d tidak dapat dihubungi", table))
                    }
                }
            }
        }
        root.addView(testConnection, matchParentButton())
    }

    private fun buildTvSettingsScreen() {
        buildBase("Pengaturan Tampilan TV", "Pesan yang ditampilkan saat waktu sesi habis")

        val tableSpinner = Spinner(this)
        tableSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            (1..TABLE_COUNT).map { String.format(Locale.US, "Meja %02d", it) }
        )
        root.addView(tableSpinner, matchParentWrapContent())

        val titleInput = createInput("Judul, contoh WAKTU HABIS")
        val messageInput = createInput("Pesan, contoh Silakan ke kasir")
        val billInput = createNumberInput("Tagihan")
        attachNominalFormatter(billInput)

        root.addView(titleInput, matchParentWrapContent())
        root.addView(messageInput, matchParentWrapContent())
        root.addView(billInput, matchParentWrapContent())

        val save = createPrimaryButton("SIMPAN KE TV")
        save.setOnClickListener {
            val table = tableSpinner.selectedItemPosition + 1
            val title = titleInput.text.toString().trim()
            val message = messageInput.text.toString().trim()
            val bill = billInput.text.toString().trim()

            if (title.isNotEmpty()) sendCommandToTable(table, "SET_TITLE:$title")
            if (message.isNotEmpty()) sendCommandToTable(table, "SET_MESSAGE:$message")
            if (bill.isNotEmpty()) sendCommandToTable(table, "SET_BILL:$bill")
            else sendCommandToTable(table, "CLEAR_BILL")

            showToast("Tampilan TV meja ${String.format(Locale.US, "%02d", table)} dikirim")
        }
        root.addView(save, matchParentButton())
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
        private val PS_TYPES = arrayOf("PS3", "PS4", "PS5")
    }
    private companion object {
        const val CIMPLI_PS_LOGO_BASE64 = "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"
    }

}
