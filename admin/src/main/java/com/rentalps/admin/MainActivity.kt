package com.rentalps.admin

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var ip: EditText
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildUi() }
    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,32,28,24); setBackgroundColor(Color.rgb(247,249,252)) }
        fun tv(text:String,size:Float=16f)=TextView(this).apply { this.text=text; textSize=size; setTextColor(Color.rgb(30,38,50)); setPadding(0,8,0,8) }
        root.addView(tv("Rental PS",30f)); root.addView(tv("Remote Android TV",14f))
        ip = EditText(this).apply { hint="IP TV, contoh 192.168.1.20"; setSingleLine(true); setText("") }
        root.addView(ip, LinearLayout.LayoutParams(-1,-2))
        status=tv("● Belum terhubung"); root.addView(status)
        val test=Button(this).apply { text="Tes Koneksi"; setOnClickListener { request("/") } }; root.addView(test)
        root.addView(tv("PS 01",22f))
        val start=Button(this).apply { text="Mulai 1 Jam — Rp 10.000"; setOnClickListener { request("/api/start?minutes=60&price=10000") } }; root.addView(start)
        val add=Button(this).apply { text="Tambah 30 Menit — Rp 5.000"; setOnClickListener { request("/api/add?minutes=30&price=5000") } }; root.addView(add)
        val stop=Button(this).apply { text="Akhiri Sesi"; setOnClickListener { request("/api/stop") } }; root.addView(stop)
        root.addView(tv("Catatan: TV tidak dimatikan. Saat 5 menit terakhir, timer kecil transparan muncul di kanan bawah. Saat waktu habis, layar berubah menjadi blank screen/tagihan.",14f))
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun request(path:String) { val host=ip.text.toString().trim(); if(host.isEmpty()){status.text="● Masukkan IP TV";return}; status.text="● Menghubungkan..."; executor.execute { try { val c=URL("http://$host:8787$path").openConnection() as HttpURLConnection; c.connectTimeout=3000;c.readTimeout=3000; val code=c.responseCode; runOnUiThread{status.text=if(code==200)"● TV terhubung" else "● TV error: $code"};c.disconnect()}catch(e:Exception){runOnUiThread{status.text="● Gagal: ${e.message}"}} } }
    override fun onDestroy(){executor.shutdownNow();super.onDestroy()}
}
