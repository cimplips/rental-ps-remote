package com.rentalps.admin

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val executor =
        Executors.newSingleThreadExecutor()

    private lateinit var ipAddress: EditText

    private lateinit var titleInput: EditText
    private lateinit var messageInput: EditText
    private lateinit var billInput: EditText

    private lateinit var statusText: TextView
    private lateinit var sessionStatusText: TextView
    private lateinit var remainingTimeText: TextView
    private lateinit var sessionPriceText: TextView

    private var sessionTimer: CountDownTimer? = null

    private var remainingMillis = 0L

    private var sessionPrice = 0L

    /*
     * Waktu berakhir sesi yang diberikan TV.
     *
     * TV menjadi sumber waktu utama.
     */
    private var tvSessionEndTimeMillis = 0L

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        buildUi()
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

            requestTvStatus()
        }

        root.addView(
            connectionButton,
            matchParentButton()
        )

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
                "Mulai 1 Jam  •  Rp 10.000"
            )

        startButton.setOnClickListener {

            sessionPrice =
                10_000L

            sendCommand(
                "START:3600"
            )

            /*
             * Setelah START dikirim,
             * ambil status dari TV agar HP
             * memakai waktu akhir yang sama.
             */
            requestTvStatus()

            sessionStatusText.text =
                "● Mengaktifkan sesi"

            sessionPriceText.text =
                "Rp 10.000"
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

            sendCommand(
                "ADD:1800"
            )

            /*
             * Jangan menambah timer lokal
             * secara manual.
             *
             * Setelah TV menerima ADD,
             * baca kembali waktu akhir dari TV.
             */
            requestTvStatus()

            sessionPrice +=
                5_000L

            sessionPriceText.text =
                formatRupiah(
                    sessionPrice
                )

            sessionStatusText.text =
                "● Memperbarui sesi"
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

            tvSessionEndTimeMillis =
                0L

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

    /*
     * Memulai timer HP berdasarkan waktu akhir TV.
     */
    private fun startTimerFromTvEndTime(
        endTimeMillis: Long
    ) {

        val remaining =
            endTimeMillis -
                System.currentTimeMillis()

        if (remaining <= 0L) {

            stopLocalTimer()

            tvSessionEndTimeMillis =
                0L

            remainingTimeText.text =
                "00:00:00"

            sessionStatusText.text =
                "● Waktu habis"

            return
        }

        tvSessionEndTimeMillis =
            endTimeMillis

        sessionTimer?.cancel()

        sessionTimer =
            object : CountDownTimer(
                remaining,
                1000L
            ) {

                override fun onTick(
                    millisUntilFinished: Long
                ) {

                    /*
                     * Hitung ulang dari endTime TV,
                     * bukan dari akumulasi tick.
                     */
                    val currentRemaining =
                        tvSessionEndTimeMillis -
                            System.currentTimeMillis()

                    if (
                        currentRemaining <= 0L
                    ) {

                        remainingMillis =
                            0L

                        remainingTimeText.text =
                            "00:00:00"

                        return
                    }

                    remainingMillis =
                        currentRemaining

                    remainingTimeText.text =
                        formatTime(
                            currentRemaining
                        )

                    sessionStatusText.text =
                        "● Sesi aktif"
                }

                override fun onFinish() {

                    remainingMillis =
                        0L

                    remainingTimeText.text =
                        "00:00:00"

                    sessionStatusText.text =
                        "● Waktu habis"

                    sessionTimer = null

                    /*
                     * Cek ulang ke TV setelah timer
                     * selesai agar status HP benar-benar
                     * mengikuti TV.
                     */
                    requestTvStatus()
                }
            }.start()
    }

    private fun startLocalTimer(
        durationMillis: Long
    ) {

        val endTime =
            System.currentTimeMillis() +
                durationMillis

        startTimerFromTvEndTime(
            endTime
        )
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

    private fun formatTime(
        millis: Long
    ): String {

        val totalSeconds =
            millis.coerceAtLeast(0L) /
                1000L

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

    /*
     * Meminta STATUS langsung dari TV.
     *
     * Format balasan:
     *
     * STATUS|ACTIVE|<endTimeMillis>
     *
     * atau:
     *
     * STATUS|IDLE|0
     */
    private fun requestTvStatus() {

        val host =
            ipAddress.text
                .toString()
                .trim()

        if (
            host.isEmpty()
        ) {

            runOnUiThread {

                statusText.text =
                    "● Masukkan IP TV"
            }

            return
        }

        executor.execute {

            try {

                Socket(
                    host,
                    8787
                ).use { socket ->

                    socket.soTimeout =
                        5000

                    val writer =
                        PrintWriter(
                            socket.getOutputStream(),
                            true
                        )

                    val reader =
                        BufferedReader(
                            InputStreamReader(
                                socket.getInputStream()
                            )
                        )

                    writer.println(
                        "STATUS"
                    )

                    writer.flush()

                    val response =
                        reader.readLine()
                            ?.trim()
                            ?: ""

                    runOnUiThread {

                        handleTvStatusResponse(
                            response
                        )
                    }
                }

            } catch (_: Exception) {

                runOnUiThread {

                    statusText.text =
                        "● Gagal terhubung ke TV"
                }
            }
        }
    }

    private fun handleTvStatusResponse(
        response: String
    ) {

        if (
            response.isEmpty()
        ) {

            statusText.text =
                "● TV tidak memberi status"

            return
        }

        val parts =
            response.split(
                "|"
            )

        if (
            parts.size < 3 ||
            parts[0] != "STATUS"
        ) {

            statusText.text =
                "● Status TV tidak dikenali"

            return
        }

        val state =
            parts[1]

        val endTime =
            parts[2]
                .toLongOrNull()
                ?: 0L

        statusText.text =
            "● TV terhubung"

        when (state) {

            "ACTIVE" -> {

                if (
                    endTime > System.currentTimeMillis()
                ) {

                    startTimerFromTvEndTime(
                        endTime
                    )

                } else {

                    stopLocalTimer()

                    remainingTimeText.text =
                        "00:00:00"

                    sessionStatusText.text =
                        "● Waktu habis"
                }
            }

            "IDLE" -> {

                stopLocalTimer()

                tvSessionEndTimeMillis =
                    0L

                remainingTimeText.text =
                    "00:00:00"

                sessionStatusText.text =
                    "● Sesi selesai"

                sessionPriceText.text =
                    "Rp 0"

                sessionPrice =
                    0L
            }

            else -> {

                statusText.text =
                    "● Status TV tidak dikenali"
            }
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

    override fun onDestroy() {

        sessionTimer?.cancel()

        sessionTimer = null

        executor.shutdownNow()

        super.onDestroy()
    }
}
