package com.rentalps.admin

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
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
            isFillViewport = true
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

    private fun buildHomeScreen() {
        screen = Screen.HOME
        sessionTimer?.cancel()
        homeTimerHandler.removeCallbacks(homeTimerRunnable)
        homeTimerViews.clear()

        buildBase(
            "Rental PS",
            "Kelola meja, sesi PlayStation & Android TV"
        )

        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        summary.addView(
            createSummaryCard(
                "Total Meja",
                TABLE_COUNT.toString(),
                Color.rgb(90, 98, 112)
            ),
            LinearLayout.LayoutParams(0, dp(92), 1f).apply {
                rightMargin = dp(6)
            }
        )

        summary.addView(
            createSummaryCard(
                "Aktif",
                countActiveTables().toString(),
                Color.rgb(55, 125, 88)
            ),
            LinearLayout.LayoutParams(0, dp(92), 1f).apply {
                leftMargin = dp(6)
            }
        )

        root.addView(summary, matchParentWrapContent())

        addSectionTitle(root, "Meja")

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
                    LinearLayout.LayoutParams(0, dp(142), 1f).apply {
                        if (column == 0) rightMargin = dp(6)
                        else leftMargin = dp(6)
                        bottomMargin = dp(12)
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

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setBackgroundColor(
                if (active) Color.rgb(237, 248, 241) else Color.WHITE
            )
            setOnClickListener {
                selectedTable = tableNumber
                screen = Screen.TABLE
                restoreTableSession(tableNumber)
                buildTableScreen()
            }
        }

        val connectionDot = TextView(this).apply {
            text = "●"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(
                when (getTvConnectionState(tableNumber)) {
                    TvConnectionState.CONNECTED -> Color.rgb(55, 170, 95)
                    TvConnectionState.DISCONNECTED -> Color.rgb(205, 105, 105)
                    TvConnectionState.UNCHECKED -> Color.rgb(185, 190, 198)
                }
            )
        }
        card.addView(
            connectionDot,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(24)
            )
        )

        card.addView(TextView(this).apply {
            text = String.format(Locale.US, "%02d", tableNumber)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(
                if (active) Color.rgb(35, 115, 72)
                else Color.rgb(35, 42, 52)
            )
        }, matchParentWrapContent())

        card.addView(TextView(this).apply {
            text = psType
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(80, 88, 100))
        }, matchParentWrapContent())

        card.addView(TextView(this).apply {
            text = when (getTvConnectionState(tableNumber)) {
                TvConnectionState.CONNECTED -> "TV Terhubung"
                TvConnectionState.DISCONNECTED -> "TV Tidak Terhubung"
                TvConnectionState.UNCHECKED -> "TV Belum Dicek"
            }
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(
                when (getTvConnectionState(tableNumber)) {
                    TvConnectionState.CONNECTED -> Color.rgb(55, 140, 88)
                    TvConnectionState.DISCONNECTED -> Color.rgb(170, 90, 90)
                    TvConnectionState.UNCHECKED -> Color.rgb(145, 150, 158)
                }
            )
        }, matchParentWrapContent())

        card.addView(TextView(this).apply {
            text = when {
                paused -> "● Pause"
                active -> "● Aktif"
                else -> "○ Kosong"
            }
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(
                when {
                    paused -> Color.rgb(150, 112, 55)
                    active -> Color.rgb(55, 125, 88)
                    else -> Color.rgb(125, 132, 143)
                }
            )
        }, matchParentWrapContent())

        if (active || paused) {
            val timeText = TextView(this).apply {
                text = formatTime(remaining)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(70, 78, 90))
                setPadding(0, dp(4), 0, 0)
            }
            card.addView(timeText, matchParentWrapContent())

            if (active && !paused) {
                homeTimerViews[tableNumber] = timeText
            }
        }

        return card
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
                finishTableSession()
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
                expireTableSession(tableNumber)
            }
        }.start()
    }

    private fun expireTableSession(tableNumber: Int) {
        sessionTimer?.cancel()
        sessionTimer = null

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
            buildTableScreen()
        } else {
            buildHomeScreen()
        }
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
            expireTableSession(tableNumber)
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
        val billInput = createInput("Tagihan")

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
        val host = getTableIp(tableNumber)
        val tableLabel = String.format(Locale.US, "%02d", tableNumber)

        if (host.isBlank()) {
            tvConnectionStatus[tableNumber] = TvConnectionState.DISCONNECTED
            runOnUiThread {
                when (screen) {
                    Screen.HOME -> buildHomeScreen()
                    Screen.TABLE -> buildTableScreen()
                }
            }
            showToast("IP TV Meja $tableLabel belum diatur")
            return
        }

        executor.execute {
            try {
                Socket(host, 8787).use { socket ->
                    socket.soTimeout = 2500
                    PrintWriter(socket.getOutputStream(), true).use { writer ->
                        writer.println(command)
                        writer.flush()
                    }
                }

                runOnUiThread {
                    tvConnectionStatus[tableNumber] = TvConnectionState.CONNECTED
                    when (screen) {
                        Screen.HOME -> buildHomeScreen()
                        Screen.TABLE -> buildTableScreen()
                    }
                }

                // Setelah perintah dikirim, baca kembali STATUS TV agar
                // tampilan HP segera mengikuti kondisi TV yang sebenarnya.
                Thread.sleep(250L)
                syncTableStatus(tableNumber, rebuildWhenChanged = true)
            } catch (_: Exception) {
                runOnUiThread {
                    tvConnectionStatus[tableNumber] = TvConnectionState.DISCONNECTED
                    when (screen) {
                        Screen.HOME -> buildHomeScreen()
                        Screen.TABLE -> buildTableScreen()
                    }
                    showToast("Gagal terhubung ke TV meja $tableLabel")
                }
            }
        }
    }

    private fun syncTableStatus(
        tableNumber: Int,
        rebuildWhenChanged: Boolean = false
    ) {
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
                            val connected = response.startsWith("STATUS|", ignoreCase = true)
                            tvConnectionStatus[tableNumber] = if (connected) TvConnectionState.CONNECTED else TvConnectionState.DISCONNECTED
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
}
