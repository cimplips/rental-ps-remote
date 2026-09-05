package com.rentalps.admin

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
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
                Color.rgb(247, 249, 252)
            )
        }

        val title = TextView(this).apply {

            text = "Rental PS"

            textSize = 30f

            setTextColor(
                Color.rgb(30, 38, 50)
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
                Color.rgb(100, 108, 120)
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
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        statusText = TextView(this).apply {

            text = "● Belum terhubung"

            textSize = 15f

            setTextColor(
                Color.rgb(100, 108, 120)
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

        val psTitle = TextView(this).apply {

            text = "PS 01"

            textSize = 22f

            setTextColor(
                Color.rgb(30, 38, 50)
            )

            setPadding(
                0,
                28,
                0,
                8
            )
        }

        root.addView(psTitle)

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

        val info = TextView(this).apply {

            text =
                """
                
                Kontrol TV:
                
                • TV tidak dimatikan.
                • Countdown muncul pada 5 menit terakhir.
                • Saat waktu habis, layar TV menjadi blank.
                • Perintah dikirim melalui jaringan Wi-Fi yang sama.
                """.trimIndent()

            textSize = 14f

            setTextColor(
                Color.rgb(90, 98, 110)
            )

            setPadding(
                0,
                20,
                0,
                20
            )
        }

        root.addView(info)

        val scrollView = ScrollView(this).apply {

            addView(root)
        }

        setContentView(scrollView)
    }

    private fun sendCommand(command: String) {

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
            "● Menghubungkan..."

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

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "● Gagal terhubung ke TV"
                }
            }
        }
    }

    override fun onDestroy() {

        executor.shutdownNow()

        super.onDestroy()
    }
}
