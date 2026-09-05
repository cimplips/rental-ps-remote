package com.rentalps.admin

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.content.SharedPreferences
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var preferences: SharedPreferences

    private val executor =
        Executors.newSingleThreadExecutor()

    private lateinit var ipAddress: EditText

    private lateinit var titleInput: EditText
    private lateinit var messageInput: EditText
    private lateinit var billInput: EditText

    private lateinit var psTypeSpinner: Spinner
    private lateinit var psTypeInput: EditText
    private lateinit var durationInput: EditText
    private lateinit var priceInput: EditText
    private lateinit var addDurationInput: EditText
    private lateinit var addPriceInput: EditText

    private lateinit var statusText: TextView
    private lateinit var sessionStatusText: TextView
    private lateinit var remainingTimeText: TextView
    private lateinit var sessionPriceText: TextView

    private var sessionTimer: CountDownTimer? = null

    private var remainingMillis = 0L

    private var sessionPrice = 0L

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        preferences =
            getSharedPreferences(
                "rental_ps_admin",
                MODE_PRIVATE
            )

        buildUi()

        restoreSavedSession()
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }

    private fun buildUi() {

        val scrollView =
            ScrollView(this).apply {

                setBackgroundColor(
                    Color.rgb(
                        245,
                        247,
                        250
                    )

                )

                isFillViewport = true
            }

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(18),
                    dp(24),
                    dp(18),
                    dp(32)
                )
            }

        scrollView.addView(root)

        /*
         * HEADER
         */

        val title =
            TextView(this).apply {

                text = "Rental PS"

                textSize = 30f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    Color.rgb(
                        35,
                        42,
                        52
                    )
                )
            }

        root.addView(
            title,
            matchParentWrapContent()
        )

        val subtitle =
            TextView(this).apply {

                text =
                    "Kelola sesi PlayStation & Android TV"

                textSize = 14f

                setTextColor(
                    Color.rgb(
                        110,
                        118,
                        130
                    )
                )

                setPadding(
                    0,
                    dp(5),
                    0,
                    dp(20)
                )
            }

        root.addView(
            subtitle,
            matchParentWrapContent()
        )

        /*
         * KONEKSI TV
         */

        addSectionTitle(
            root,
            "Koneksi TV"
        )

        ipAddress =
            EditText(this).apply {

                hint =
                    "IP TV, contoh 192.168.1.20"

                setSingleLine(true)

                textSize = 15f

                setTextColor(
                    Color.rgb(
                        45,
                        52,
                        64
                    )
                )

                setHintTextColor(
                    Color.rgb(
                        125,
                        132,
                        143
                    )
                )

                setPadding(
                    dp(16),
                    dp(10),
                    dp(16),
                    dp(10)
                )

                setBackgroundColor(
                    Color.WHITE
                )

                minHeight =
                    dp(50)
            }

        ipAddress.setText(
            preferences.getString(
                "tv_ip",
                ""
            ) ?: ""
        )

        root.addView(
            ipAddress,
            matchParentWrapContent()
        )

        statusText =
            TextView(this).apply {

                text =
                    "● Belum terhubung"

                textSize = 13f

                setTextColor(
                    Color.rgb(
                        105,
                        113,
                        125
                    )
                )

                setPadding(
                    dp(4),
                    dp(8),
                    dp(4),
                    dp(8)
                )
            }

        root.addView(
            statusText,
            matchParentWrapContent()
        )

        val connectionButton =
            createSoftButton(
                "Tes Koneksi"
            )

        connectionButton.setOnClickListener {

            sendCommand(
                "PING"
            )
        }

        root.addView(
            connectionButton,
            matchParentButton()
        )

        /*
         * PENGATURAN SESI
         */

        addSectionTitle(
            root,
            "Pengaturan Sesi"
        )

        psTypeSpinner =
            Spinner(this).apply {

                val adapter =
                    ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        arrayOf(
                            "PS3",
                            "PS4",
                            "PS5"
                        )
                    )

                this.adapter = adapter

                val savedType =
                    preferences.getString(
                        "ps_type",
                        "PS5"
                    ) ?: "PS5"

                val index =
                    when (savedType.uppercase()) {
                        "PS3" -> 0
                        "PS4" -> 1
                        else -> 2
                    }

                setSelection(index)
            }

        root.addView(
            psTypeSpinner,
            matchParentWrapContent()
        )

        psTypeInput =
            createInput(
                "Nama jenis PS, contoh PS5"
            )

        psTypeInput.setText(
            preferences.getString(
                "ps3_name",
                "PS3"
            ) ?: "PS3"
        )

        root.addView(
            psTypeInput,
            matchParentWrapContent()
        )

        durationInput =
            createInput(
                "Durasi sesi jenis PS ini dalam menit"
            )

        durationInput.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER

        durationInput.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER

        durationInput.setText(
            preferences.getInt(
                "ps5_duration_minutes",
                60
            ).toString()
        )

        root.addView(
            durationInput,
            matchParentWrapContent()
        )

        priceInput =
            createInput(
                "Tarif sesi jenis PS ini, contoh 10000"
            )

        priceInput.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER

        priceInput.setText(
            preferences.getLong(
                "ps5_price",
                10_000L
            ).toString()
        )

        root.addView(
            priceInput,
            matchParentWrapContent()
        )

        addDurationInput =
            createInput(
                "Tambah waktu dalam menit"
            )

        addDurationInput.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER

        addDurationInput.setText(
            preferences.getInt(
                "add_duration_minutes",
                30
            ).toString()
        )

        root.addView(
            addDurationInput,
            matchParentWrapContent()
        )

        addPriceInput =
            createInput(
                "Tarif tambah waktu, contoh 5000"
            )

        addPriceInput.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER

        addPriceInput.setText(
            preferences.getLong(
                "add_price",
                5_000L
            ).toString()
        )

        root.addView(
            addPriceInput,
            matchParentWrapContent()
        )

        val saveSessionSettingsButton =
            createSoftButton(
                "Simpan Pengaturan Sesi"
            )

        saveSessionSettingsButton.setOnClickListener {

            saveSessionSettings()
        }

        root.addView(
            saveSessionSettingsButton,
            matchParentButton()
        )

        psTypeSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    loadSelectedPsSettings()
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) {
                }
            }

        /*
         * SESSION
         */

        addSectionTitle(
            root,
            "Sesi Aktif"
        )

        val sessionCard =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(18),
                    dp(18),
                    dp(18),
                    dp(18)
                )

                setBackgroundColor(
                    Color.WHITE
                )
            }

        val psName =
            TextView(this).apply {

                text = "PS 01"

                textSize = 20f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    Color.rgb(
                        38,
                        45,
                        55
                    )
                )
            }

        sessionCard.addView(
            psName,
            matchParentWrapContent()
        )

        sessionStatusText =
            TextView(this).apply {

                text =
                    "● Sesi selesai"

                textSize = 13f

                setTextColor(
                    Color.rgb(
                        105,
                        113,
                        125
                    )
                )

                setPadding(
                    0,
                    dp(4),
                    0,
                    dp(8)
                )
            }

        sessionCard.addView(
            sessionStatusText,
            matchParentWrapContent()
        )

        remainingTimeText =
            TextView(this).apply {

                text = "00:00:00"

                textSize = 38f

                gravity =
                    Gravity.CENTER

                typeface =
                    Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.BOLD
                    )

                setTextColor(
                    Color.rgb(
                        45,
                        52,
                        64
                    )
                )

                setPadding(
                    0,
                    dp(8),
                    0,
                    dp(4)
                )
            }

        sessionCard.addView(
            remainingTimeText,
            matchParentWrapContent()
        )

        val remainingLabel =
            TextView(this).apply {

                text =
                    "Waktu tersisa"

                textSize = 12f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    Color.rgb(
                        130,
                        138,
                        150
                    )
                )
            }

        sessionCard.addView(
            remainingLabel,
            matchParentWrapContent()
        )

        sessionPriceText =
            TextView(this).apply {

                text =
                    "Rp 0"

                textSize = 16f

                gravity =
                    Gravity.CENTER

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    Color.rgb(
                        70,
                        78,
                        90
                    )
                )

                setPadding(
                    0,
                    dp(10),
                    0,
                    dp(8)
                )
            }

        sessionCard.addView(
            sessionPriceText,
            matchParentWrapContent()
        )

        /*
         * MULAI
         */

        val startButton =
            createPrimaryButton(
                "Mulai Sesi"
            )

        startButton.setOnClickListener {

            val durationMinutes =
                durationInput.text
                    .toString()
                    .trim()
                    .toLongOrNull()
                    ?.coerceAtLeast(1L)
                    ?: 60L

            val price =
                priceInput.text
                    .toString()
                    .replace(".", "")
                    .replace(",", "")
                    .trim()
                    .toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 10_000L

            val durationSeconds =
                durationMinutes * 60L

            sessionPrice =
                price

            saveSessionSettings()

            saveSessionEndTime(
                System.currentTimeMillis() +
                    durationMinutes * 60_000L
            )

            sendCommand(
                "START:$durationSeconds"
            )

            startLocalTimer(
                durationMinutes * 60_000L
            )

            sessionStatusText.text =
                "● Sesi aktif"

            sessionPriceText.text =
                formatRupiah(
                    sessionPrice
                )
        }

        sessionCard.addView(
            startButton,
            matchParentButton()
        )

        /*
         * TAMBAH
         */

        val addButton =
            createSoftButton(
                "+ Tambah 30 Menit  •  Rp 5.000"
            )

        addButton.setOnClickListener {

            val currentEndTime =
                getSavedSessionEndTime()

            val baseEndTime =
                if (
                    currentEndTime > System.currentTimeMillis()
                ) {
                    currentEndTime
                } else {
                    System.currentTimeMillis()
                }

            val addMinutes =
                addDurationInput.text
                    .toString()
                    .trim()
                    .toLongOrNull()
                    ?.coerceAtLeast(1L)
                    ?: 30L

            val addPrice =
                addPriceInput.text
                    .toString()
                    .replace(".", "")
                    .replace(",", "")
                    .trim()
                    .toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 5_000L

            val newEndTime =
                baseEndTime +
                    addMinutes * 60_000L

            saveSessionSettings()

            saveSessionEndTime(
                newEndTime
            )

            sendCommand(
                "ADD:${addMinutes * 60L}"
            )

            startTimerUntil(
                newEndTime
            )

            sessionPrice +=
                addPrice

            sessionPriceText.text =
                formatRupiah(
                    sessionPrice
                )

            sessionStatusText.text =
                "● Sesi aktif"
        }

        sessionCard.addView(
            addButton,
            matchParentButton()
        )

        /*
         * AKHIRI
         */

        val stopButton =
            createDangerButton(
                "Akhiri Sesi"
            )

        stopButton.setOnClickListener {

            sendCommand(
                "STOP"
            )

            stopLocalTimer()

            sessionStatusText.text =
                "● Sesi selesai"

            remainingTimeText.text =
                "00:00:00"

            sessionPriceText.text =
                "Rp 0"

            sessionPrice =
                0L
        }

        sessionCard.addView(
            stopButton,
            matchParentButton()
        )

        root.addView(
            sessionCard,
            matchParentWrapContent()
        )

        /*
         * TAMPILAN WAKTU HABIS
         */

        addSectionTitle(
            root,
            "Tampilan Saat Waktu Habis"
        )

        titleInput =
            createInput(
                "Judul"
            )

        titleInput.setText(
            "WAKTU HABIS"
        )

        root.addView(
            titleInput,
            matchParentWrapContent()
        )

        messageInput =
            createInput(
                "Pesan"
            )

        messageInput.setText(
            "Silakan ke kasir"
        )

        messageInput.minLines =
            2

        root.addView(
            messageInput,
            matchParentWrapContent()
        )

        billInput =
            createInput(
                "Tagihan, contoh Rp 25.000"
            )

        root.addView(
            billInput,
            matchParentWrapContent()
        )

        /*
         * SIMPAN
         */

        val saveDisplayButton =
            createSoftButton(
                "Simpan Tampilan ke TV"
            )

        saveDisplayButton.setOnClickListener {

            sendDisplaySettings()
        }

        root.addView(
            saveDisplayButton,
            matchParentButton()
        )

        /*
         * HAPUS TAGIHAN
         */

        val clearBillButton =
            createSoftButton(
                "Hapus Tagihan di TV"
            )

        clearBillButton.setOnClickListener {

            sendCommand(
                "CLEAR_BILL"
            )
        }

        root.addView(
            clearBillButton,
            matchParentButton()
        )

        /*
         * INFO
         */

        val infoText =
            TextView(this).apply {

                text =
                    """
                    TV akan tetap menyala selama sesi.

                    Saat waktu habis, TV menampilkan
                    blank screen dan informasi tagihan.

                    Pengaturan QRIS dan gambar akan
                    kita tambahkan pada tahap berikutnya.
                    """.trimIndent()

                textSize = 13f

                setTextColor(
                    Color.rgb(
                        110,
                        118,
                        130
                    )
                )

                setPadding(
                    dp(4),
                    dp(20),
                    dp(4),
                    dp(8)
                )
            }

        root.addView(
            infoText,
            matchParentWrapContent()
        )

        setContentView(
            scrollView
        )
    }

    private fun createInput(
        hintText: String
    ): EditText {

        return EditText(this).apply {

            hint =
                hintText

            textSize = 15f

            setTextColor(
                Color.rgb(
                    45,
                    52,
                    64
                )
            )

            setHintTextColor(
                Color.rgb(
                    125,
                    132,
                    143
                )
            )

            setPadding(
                dp(16),
                dp(10),
                dp(16),
                dp(10)
            )

            setBackgroundColor(
                Color.WHITE
            )

            minHeight =
                dp(50)
        }
    }

    private fun createSoftButton(
        textValue: String
    ): Button {

        return Button(this).apply {

            text =
                textValue

            textSize = 14f

            gravity =
                Gravity.CENTER

            setTextColor(
                Color.rgb(
                    55,
                    63,
                    75
                )
            )

            setBackgroundColor(
                Color.rgb(
                    232,
                    236,
                    241
                )
            )

            isAllCaps = false

            minHeight =
                dp(52)

            minimumHeight =
                dp(52)

            setPadding(
                dp(12),
                dp(6),
                dp(12),
                dp(6)
            )

            includeFontPadding = true
        }
    }

    private fun createPrimaryButton(
        textValue: String
    ): Button {

        return Button(this).apply {

            text =
                textValue

            textSize = 14f

            gravity =
                Gravity.CENTER

            setTextColor(
                Color.WHITE
            )

            setBackgroundColor(
                Color.rgb(
                    70,
                    78,
                    92
                )
            )

            isAllCaps = false

            minHeight =
                dp(52)

            minimumHeight =
                dp(52)

            setPadding(
                dp(12),
                dp(6),
                dp(12),
                dp(6)
            )

            includeFontPadding = true
        }
    }

    private fun createDangerButton(
        textValue: String
    ): Button {

        return Button(this).apply {

            text =
                textValue

            textSize = 14f

            gravity =
                Gravity.CENTER

            setTextColor(
                Color.rgb(
                    90,
                    70,
                    70
                )
            )

            setBackgroundColor(
                Color.rgb(
                    242,
                    232,
                    232
                )
            )

            isAllCaps = false

            minHeight =
                dp(52)

            minimumHeight =
                dp(52)

            setPadding(
                dp(12),
                dp(6),
                dp(12),
                dp(6)
            )

            includeFontPadding = true
        }
    }

    private fun startLocalTimer(
        durationMillis: Long
    ) {

        val endTime =
            System.currentTimeMillis() +
                durationMillis

        saveSessionEndTime(
            endTime
        )

        startTimerUntil(
            endTime
        )
    }

    private fun startTimerUntil(
        endTimeMillis: Long
    ) {

        sessionTimer?.cancel()

        val remaining =
            endTimeMillis -
                System.currentTimeMillis()

        if (remaining <= 0L) {

            remainingMillis =
                0L

            remainingTimeText.text =
                "00:00:00"

            sessionStatusText.text =
                "● Waktu habis"

            clearSavedSession()

            sessionTimer = null

            return
        }

        remainingMillis =
            remaining

        updateSessionTimeAppearance(
            remainingMillis
        )

        sessionTimer =
            object : CountDownTimer(
                remaining,
                1000L
            ) {

                override fun onTick(
                    millisUntilFinished: Long
                ) {

                    val currentRemaining =
                        endTimeMillis -
                            System.currentTimeMillis()

                    remainingMillis =
                        currentRemaining.coerceAtLeast(
                            0L
                        )

                    remainingTimeText.text =
                        formatTime(
                            remainingMillis
                        )

                    updateSessionTimeAppearance(
                        remainingMillis
                    )
                }

                override fun onFinish() {

                    remainingMillis =
                        0L

                    remainingTimeText.text =
                        "00:00:00"

                    updateSessionTimeAppearance(
                        0L
                    )

                    clearSavedSession()

                    sessionTimer = null
                }
            }.start()
    }

    private fun restartLocalTimer(
        durationMillis: Long
    ) {

        startLocalTimer(
            durationMillis
        )
    }

    private fun stopLocalTimer() {

        sessionTimer?.cancel()

        sessionTimer = null

        remainingMillis =
            0L
    }

    private fun saveSessionEndTime(
        endTimeMillis: Long
    ) {

        preferences.edit()
            .putLong(
                "session_end_time",
                endTimeMillis
            )
            .apply()
    }

    private fun getSavedSessionEndTime(): Long {

        return preferences.getLong(
            "session_end_time",
            0L
        )
    }

    private fun clearSavedSession() {

        preferences.edit()
            .remove(
                "session_end_time"
            )
            .apply()
    }

    private fun restoreSavedSession() {

        val savedEndTime =
            getSavedSessionEndTime()

        if (
            savedEndTime <= 0L
        ) {
            return
        }

        if (
            savedEndTime >
            System.currentTimeMillis()
        ) {

            sessionStatusText.text =
                "● Sesi aktif"

            startTimerUntil(
                savedEndTime
            )

        } else {

            clearSavedSession()

            remainingTimeText.text =
                "00:00:00"

            sessionStatusText.text =
                "● Waktu habis"
        }
    }

    private fun updateSessionTimeAppearance(
        remaining: Long
    ) {

        if (remaining <= 0L) {
            remainingTimeText.setTextColor(
                Color.rgb(
                    90,
                    70,
                    70
                )
            )

            sessionStatusText.text =
                "● Waktu habis"

            return
        }

        if (remaining <= 300_000L) {
            remainingTimeText.setTextColor(
                Color.rgb(
                    110,
                    92,
                    92
                )
            )

            sessionStatusText.text =
                "● Sisa waktu kurang dari 5 menit"

        } else {
            remainingTimeText.setTextColor(
                Color.rgb(
                    45,
                    52,
                    64
                )
            )

            sessionStatusText.text =
                "● Sesi aktif"
        }
    }

    private fun selectedPsKey(): String {

        return when (
            psTypeSpinner.selectedItem?.toString()?.uppercase()
        ) {
            "PS3" -> "ps3"
            "PS4" -> "ps4"
            else -> "ps5"
        }
    }

    private fun loadSelectedPsSettings() {

        val key =
            selectedPsKey()

        val defaultName =
            when (key) {
                "ps3" -> "PS3"
                "ps4" -> "PS4"
                else -> "PS5"
            }

        val defaultDuration =
            preferences.getInt(
                "${key}_duration_minutes",
                60
            )

        val defaultPrice =
            preferences.getLong(
                "${key}_price",
                when (key) {
                    "ps3" -> 8_000L
                    "ps4" -> 10_000L
                    else -> 15_000L
                }
            )

        psTypeInput.setText(
            preferences.getString(
                "${key}_name",
                defaultName
            ) ?: defaultName
        )

        durationInput.setText(
            defaultDuration.toString()
        )

        priceInput.setText(
            defaultPrice.toString()
        )

        addDurationInput.setText(
            preferences.getInt(
                "${key}_add_duration_minutes",
                30
            ).toString()
        )

        addPriceInput.setText(
            preferences.getLong(
                "${key}_add_price",
                5_000L
            ).toString()
        )
    }

    private fun saveSessionSettings() {

        val key =
            selectedPsKey()

        val psType =
            psTypeInput.text
                .toString()
                .trim()
                .ifEmpty {
                    key.uppercase()
                }

        val durationMinutes =
            durationInput.text
                .toString()
                .trim()
                .toLongOrNull()
                ?.coerceAtLeast(1L)
                ?: 60L

        val price =
            priceInput.text
                .toString()
                .replace(".", "")
                .replace(",", "")
                .trim()
                .toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 10_000L

        val addMinutes =
            addDurationInput.text
                .toString()
                .trim()
                .toLongOrNull()
                ?.coerceAtLeast(1L)
                ?: 30L

        val addPrice =
            addPriceInput.text
                .toString()
                .replace(".", "")
                .replace(",", "")
                .trim()
                .toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 5_000L

        preferences.edit()
            .putString(
                "${key}_name",
                psType
            )
            .putInt(
                "${key}_duration_minutes",
                durationMinutes.toInt()
            )
            .putLong(
                "${key}_price",
                price
            )
            .putInt(
                "${key}_add_duration_minutes",
                addMinutes.toInt()
            )
            .putLong(
                "${key}_add_price",
                addPrice
            )
            .putString(
                "ps_type",
                psType
            )
            .putInt(
                "session_duration_minutes",
                durationMinutes.toInt()
            )
            .putLong(
                "session_price",
                price
            )
            .putInt(
                "add_duration_minutes",
                addMinutes.toInt()
            )
            .putLong(
                "add_price",
                addPrice
            )
            .apply()

        statusText.text =
            "● Pengaturan ${key.uppercase()} tersimpan"
    }

    private fun formatTime(
        millis: Long
    ): String {

        val totalSeconds =
            millis / 1000L

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

    private fun formatRupiah(
        value: Long
    ): String {

        return String.format(
            "Rp %,d",
            value
        ).replace(
            ",",
            "."
        )
    }

    private fun sendDisplaySettings() {

        val title =
            titleInput.text
                .toString()
                .trim()

        val message =
            messageInput.text
                .toString()
                .trim()

        val bill =
            billInput.text
                .toString()
                .trim()

        if (
            title.isNotEmpty()
        ) {

            sendCommand(
                "SET_TITLE:$title"
            )
        }

        if (
            message.isNotEmpty()
        ) {

            sendCommand(
                "SET_MESSAGE:$message"
            )
        }

        if (
            bill.isNotEmpty()
        ) {

            sendCommand(
                "SET_BILL:$bill"
            )

        } else {

            sendCommand(
                "CLEAR_BILL"
            )
        }
    }

    private fun sendCommand(
        command: String
    ) {

        val host =
            ipAddress.text
                .toString()
                .trim()

        if (
            host.isEmpty()
        ) {

            statusText.text =
                "● Masukkan IP TV"

            return
        }

        statusText.text =
            "● Mengirim perintah..."

        executor.execute {

            try {

                Socket(
                    host,
                    8787
                ).use { socket ->

                    val writer =
                        PrintWriter(
                            socket.getOutputStream(),
                            true
                        )

                    writer.println(
                        command
                    )

                    writer.flush()
                }

                runOnUiThread {

                    statusText.text =
                        "● TV terhubung"
                }

            } catch (_: Exception) {

                runOnUiThread {

                    statusText.text =
                        "● Gagal terhubung ke TV"
                }
            }
        }
    }

    private fun addSectionTitle(
        root: LinearLayout,
        textValue: String
    ) {

        val sectionTitle =
            TextView(this).apply {

                text =
                    textValue

                textSize = 17f

                typeface =
                    Typeface.DEFAULT_BOLD

                setTextColor(
                    Color.rgb(
                        50,
                        58,
                        70
                    )
                )

                setPadding(
                    dp(2),
                    dp(16),
                    dp(2),
                    dp(9)
                )
            }

        root.addView(
            sectionTitle,
            matchParentWrapContent()
        )
    }

    private fun matchParentWrapContent():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun matchParentButton():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(58)
        ).apply {

            topMargin =
                dp(8)
        }
    }

    override fun onPause() {

        preferences.edit()
            .putString(
                "tv_ip",
                ipAddress.text
                    .toString()
                    .trim()
            )
            .apply()

        super.onPause()
    }

    override fun onDestroy() {

        sessionTimer?.cancel()

        sessionTimer = null

        executor.shutdownNow()

        super.onDestroy()
    }
}
