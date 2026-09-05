package com.rentalps.tv

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class TvServer(
    private val port: Int = 8787,
    private val onCommand: (String) -> Unit,
    private val onStatusRequest: () -> String = {
        "STATUS|IDLE|0"
    }
) {

    private var serverSocket: ServerSocket? = null

    private val executor =
        Executors.newCachedThreadPool()

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    @Volatile
    private var running = false

    fun start() {

        if (running) {
            return
        }

        running = true

        executor.execute {

            try {

                serverSocket =
                    ServerSocket(port)

                while (running) {

                    val socket =
                        serverSocket?.accept()

                    if (socket != null) {

                        handleClient(
                            socket
                        )
                    }
                }

            } catch (_: Exception) {
                // Server dihentikan atau socket gagal.
            }
        }
    }

    private fun handleClient(
        socket: Socket
    ) {

        executor.execute {

            socket.use {

                try {

                    val reader =
                        BufferedReader(
                            InputStreamReader(
                                socket.getInputStream()
                            )
                        )

                    val writer =
                        PrintWriter(
                            socket.getOutputStream(),
                            true
                        )

                    val command =
                        reader
                            .readLine()
                            ?.trim()
                            ?.uppercase()

                    if (
                        command.isNullOrEmpty()
                    ) {
                        return@use
                    }

                    /*
                     * STATUS adalah request khusus.
                     *
                     * HP mengirim:
                     *
                     * STATUS
                     *
                     * TV membalas:
                     *
                     * STATUS|ACTIVE|<waktu_berakhir>
                     *
                     * atau:
                     *
                     * STATUS|IDLE|0
                     */
                    if (
                        command == "STATUS"
                    ) {

                        val response =
                            try {

                                onStatusRequest()

                            } catch (_: Exception) {

                                "STATUS|IDLE|0"
                            }

                        writer.println(
                            response
                        )

                        writer.flush()

                        return@use
                    }

                    /*
                     * Perintah biasa tetap memakai
                     * mekanisme yang sudah berjalan.
                     */
                    mainHandler.post {

                        onCommand(
                            command
                        )
                    }

                } catch (_: Exception) {
                    // Client terputus.
                }
            }
        }
    }

    fun stop() {

        running = false

        try {

            serverSocket?.close()

        } catch (_: Exception) {
        }

        serverSocket = null
    }
}
