package com.rentalps.tv

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var gameView: TextView
    private lateinit var overlay: TextView
    private var endAt = 0L
    private var totalPrice = 0
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = android.os.Handler(mainLooper)
    private var server: ServerSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        gameView = TextView(this).apply {
            text = "GAME SCREEN\n\nRental PS TV siap terhubung"
            textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        overlay = TextView(this).apply {
            textSize = 14f; setTextColor(Color.argb(155,255,255,255)); setPadding(12,8,12,8); visibility = View.GONE
        }
        root.addView(gameView, FrameLayout.LayoutParams(-1,-1))
        val lp = FrameLayout.LayoutParams(-2,-2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0,0,18,18) }
        root.addView(overlay, lp)
        setContentView(root)
        startServer()
        tick()
    }

    private fun tick() {
        val remaining = endAt - System.currentTimeMillis()
        if (endAt > 0 && remaining <= 0) showExpired() else if (endAt > 0) {
            val sec = remaining / 1000
            if (sec <= 300) { overlay.visibility = View.VISIBLE; overlay.text = "◷ ${format(sec)}" } else overlay.visibility = View.GONE
        }
        handler.postDelayed({ tick() }, 1000)
    }

    private fun showExpired() = runOnUiThread {
        overlay.visibility = View.GONE
        gameView.text = "WAKTU HABIS!\n\nPS 01\n\nTotal Tagihan\nRp ${money(totalPrice)}\n\nSilakan ke kasir untuk melanjutkan."
        gameView.textSize = 28f
        gameView.setTextColor(Color.WHITE)
        gameView.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.rgb(16,20,25))
        endAt = 0
    }

    private fun startSession(minutes: Long, price: Int) = runOnUiThread {
        endAt = System.currentTimeMillis() + minutes * 60_000
        totalPrice = price
        root.setBackgroundColor(Color.BLACK)
        gameView.text = "GAME SCREEN"
        gameView.textSize = 30f
        gameView.setTextColor(Color.TRANSPARENT)
        overlay.visibility = View.GONE
    }

    private fun stopSession() = runOnUiThread {
        endAt = 0; overlay.visibility = View.GONE; root.setBackgroundColor(Color.BLACK)
        gameView.setTextColor(Color.WHITE); gameView.text = "GAME SCREEN"
    }

    private fun addTime(minutes: Long, price: Int) { if (endAt == 0L) startSession(minutes, price) else { endAt += minutes*60_000; totalPrice += price } }

    private fun startServer() { executor.execute {
        try {
            server = ServerSocket(8787)
            while (!server!!.isClosed) handle(server!!.accept())
        } catch (_: Exception) {}
    }}

    private fun handle(socket: java.net.Socket) {
        socket.use { s ->
            val input = s.getInputStream().bufferedReader()
            val request = input.readLine() ?: return
            while (input.readLine()?.isNotEmpty() == true) {}
            val parts = request.split(" ")
            val path = parts.getOrNull(1) ?: "/"
            val query = path.substringAfter('?', "")
            val params = query.split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }
            when (path.substringBefore('?')) {
                "/api/start" -> startSession((params["minutes"] ?: "60").toLong(), (params["price"] ?: "10000").toInt())
                "/api/add" -> addTime((params["minutes"] ?: "30").toLong(), (params["price"] ?: "5000").toInt())
                "/api/stop" -> stopSession()
            }
            val body = "{\"ok\":true,\"ip\":\"${localIp()}\"}"
            val out = s.getOutputStream().bufferedWriter()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body")
            out.flush()
        }
    }

    private fun localIp(): String = try { Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { Collections.list(it.inetAddresses) }.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }?.hostAddress ?: "0.0.0.0" } catch (_: Exception) { "0.0.0.0" }
    private fun format(s: Long) = "%02d:%02d".format(max(0,s)/60, max(0,s)%60)
    private fun money(v: Int) = "%,d".format(v).replace(',', '.')
    override fun onDestroy() { server?.close(); executor.shutdownNow(); super.onDestroy() }
}
