package com.rentalps.admin

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()
    }

    private fun buildUi() {

        val root = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                28,
                32,
                28,
                24
            )

            setBackgroundColor(
                Color.rgb(
                    247,
                    249,
                    252
                )
            )
        }

        val title = TextView(this).apply {

            text = "Rental PS"

            textSize = 30f

            setTextColor(
                Color.rgb(
                    30,
                    38,
                    50
                )
            )

            setPadding(
                0,
                8,
                0,
                4
            )
        }

        root.addView(title)

        val subtitle = TextView(this).apply {

            text = "Remote Android TV"

            textSize = 14f

            setTextColor(
                Color.rgb(
                    100,
                    108,
                    120
                )
            )

            setPadding(
                0,
                0,
                0,
                20
            )
        }

        root.addView(subtitle)

        ipAddress = EditText(this).apply {

            hint = "IP TV, contoh 192.168.1.20"

            setSingleLine(true)

            textSize = 16f
        }

        root.addView(
            ipAddress,
            matchParentWrapContent()
        )

        statusText = TextView(this).apply {

            text = "● Belum terhubung"

            textSize = 15f

            setTextColor(
                Color.rgb(
                    100,
                    108,
                    120
                )
            )

            setPadding(
                0,
                12,
                0,
                20
            )
        }

        root.addView(statusText)

        val testButton = Button(this).apply {

            text = "Tes Koneksi"

            setOnClickListener {

                sendCommand("PING")
            }
        }

        root.addView(testButton)

        addSectionTitle(
            root,
            "PS 01"
        )

        val startButton = Button(this).apply {

            text = "Mulai 1 Jam — Rp 10.000"

            setOnClickListener {

                sendCommand(
                    "START:3600"
                )
            }
        }

        root.addView(startButton)

        val addButton = Button(this).apply {

            text = "Tambah 30 Menit — Rp 5.000"

            setOnClickListener {

                sendCommand(
                    "ADD:1800"
                )
            }
        }

        root.addView(addButton)

        val stopButton = Button(this).apply {

            text = "Akhiri Sesi"

            setOnClickListener {

                sendCommand(
                    "STOP"
                )
            }
        }

        root.addView(stopButton)

        addSectionTitle(
            root,
            "Tampilan Saat Waktu Habis"
        )

        titleInput = EditText(this).apply {

            hint = "Judul"

            setSingleLine(true)

            text = "WAKTU HABIS"

            textSize = 16f
        }

        root.addView(
            titleInput,
            matchParentWrapContent()
        )

        messageInput = EditText(this).apply {

            hint = "Pesan"

            setSingleLine(false)

            minLines = 2

            text = "Silakan ke kasir"

            textSize = 16f
        }

        root.addView(
            messageInput,
            matchParentWrapContent()
        )

        billInput = EditText(this).apply {

            hint = "Tagihan, contoh Rp 25.000"

            setSingleLine(true)

            textSize = 16f
        }

        root.addView(
            billInput,
            matchParentWrapContent()
        )

        val saveDisplayButton = Button(this).apply {

            text = "Simpan Tampilan ke TV"

            setOnClickListener {

                sendDisplaySettings()
            }
        }

        root.addView(saveDisplayButton)

        val clearBillButton = Button(this).apply {

            text = "Hapus Tagihan di TV"

            setOnClickListener {

                sendCommand(
                    "CLEAR_BILL"
                )
            }
        }

        root.addView(clearBillButton)

        val infoText = TextView(this).apply {

            text =
                """
                
                Pengaturan TV:
                
                • Judul dan pesan dapat diubah dari HP.
                • Tagihan dapat dikirim ke TV.
                • Pengaturan tersimpan di TV.
                • QRIS akan kita tambahkan pada tahap berikutnya.
                """.trimIndent()

            textSize = 14f

            setTextColor(
                Color.rgb(
                    90,
                    98,
                    110
                )
            )

            setPadding(
                0,
                20,
                0,
                20
            )
        }

        root.addView(infoText)

        val scrollView =
            ScrollView(this).apply {

                addView(root)
            }

        setContentView(scrollView)
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

        if (title.isNotEmpty()) {

            sendCommand(
                "SET_TITLE:$title"
            )
        }

        if (message.isNotEmpty()) {

            sendCommand(
                "SET_MESSAGE:$message"
            )
        }

        if (bill.isNotEmpty()) {

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

        if (host.isEmpty()) {

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

                    writer.println(command)

                    writer.flush()
                }

                runOnUiThread {

                    statusText.text =
                        "● Perintah berhasil dikirim"
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
        text: String
    ) {

        val sectionTitle =
            TextView(this).apply {

                this.text = text

                textSize = 22f

                setTextColor(
                    Color.rgb(
                        30,
                        38,
                        50
                    )
                )

                setPadding(
                    0,
                    28,
                    0,
                    8
                )
            }

        root.addView(sectionTitle)
    }

    private fun matchParentWrapContent():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroy() {

        executor.shutdownNow()

        super.onDestroy()
    }
}
