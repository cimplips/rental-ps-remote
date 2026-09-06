
package com.rentalps.admin

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.PrintWriter
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Cimpli PS - MainActivity
 *
 * UI dibangun ulang dari nol dengan konsep:
 * - Soft gray premium
 * - Emerald accent
 * - Dashboard rental PS
 * - F&B
 * - Timer sesi
 * - Kontrol Android TV melalui socket
 *
 * Tidak menggunakan layout XML.
 */
class MainActivity : Activity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("cimpli_ps", Context.MODE_PRIVATE)
    }

    private val executor = Executors.newCachedThreadPool()
    private val handler = Handler(Looper.getMainLooper())

    private val tables = (1..10).map { TableState(it) }.toMutableList()

    private val fnbProducts = mutableListOf(
        FnbProduct(1, "Kopi Susu", "Minuman", 12000),
        FnbProduct(2, "Es Teh", "Minuman", 7000),
        FnbProduct(3, "Air Mineral", "Minuman", 5000),
        FnbProduct(4, "Mie Goreng", "Makanan", 15000),
        FnbProduct(5, "Kentang Goreng", "Makanan", 12000),
        FnbProduct(6, "Nugget", "Makanan", 14000)
    )

    private val orders = mutableListOf<FnbOrder>()

    private val homeTimerViews = mutableMapOf<Int, TextView>()
    private var detailTimerView: TextView? = null

    private var selectedTable = 1
    private var currentPage = Page.HOME
    private var fnbFilter = "Semua"

    private lateinit var content: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var bottomNav: LinearLayout

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (currentPage == Page.HOME) {
                refreshTableCards()
            }
            handler.postDelayed(this, 1000L)
        }
    }

    private enum class Page {
        HOME, TABLE, FNB, TRANSACTIONS
    }

    private data class TableState(
        val number: Int,
        var psType: String = "PS5",
        var active: Boolean = false,
        var paused: Boolean = false,
        var endAt: Long = 0L,
        var pausedRemaining: Long = 0L,
        var tvIp: String = "",
        var bill: Long = 0L
    )

    private data class FnbProduct(
        val id: Int,
        val name: String,
        val category: String,
        val price: Long
    )

    private data class FnbOrder(
        val id: Long,
        val table: Int,
        val items: MutableMap<Int, Int>,
        val total: Long,
        val createdAt: Long = System.currentTimeMillis()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadTables()
        buildShell()
        showHome()

        handler.post(timerRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRunnable)
        executor.shutdownNow()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // SHELL
    // -------------------------------------------------------------------------

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C.BG)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(8))
            setBackgroundColor(C.BG)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        brand.addView(TextView(this@MainActivity).apply {
            text = "CIMPLI PS"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(C.TEXT)
        })

        brand.addView(TextView(this@MainActivity).apply {
            text = "Rental • TV • F&B"
            textSize = 10f
            setTextColor(C.MUTED)
            setPadding(0, dp(2), 0, 0)
        })

        topRow.addView(brand, LinearLayout.LayoutParams(0, dp(50), 1f))

        val settings = iconButton("⚙", 44)
        settings.setOnClickListener { showSettingsDialog() }
        topRow.addView(settings)

        top.addView(topRow)

        titleView = TextView(this).apply {
            textSize = 12f
            setTextColor(C.MUTED)
            setPadding(0, dp(3), 0, dp(4))
        }
        top.addView(titleView)

        subtitleView = TextView(this).apply {
            textSize = 10f
            setTextColor(C.LIGHT_MUTED)
        }
        top.addView(subtitleView)

        root.addView(top, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(24))
        }

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        bottomNav = buildBottomNav()
        root.addView(bottomNav, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(74)
        ))

        setContentView(root)
    }

    private fun buildBottomNav(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(10))
            background = rounded(C.WHITE, dp(24))

            addView(navItem("⌂", "Beranda", Page.HOME),
                LinearLayout.LayoutParams(0, dp(58), 1f))
            addView(navItem("▦", "Meja", Page.TABLE),
                LinearLayout.LayoutParams(0, dp(58), 1f))
            addView(navItem("☕", "F&B", Page.FNB),
                LinearLayout.LayoutParams(0, dp(58), 1f))
            addView(navItem("▤", "Transaksi", Page.TRANSACTIONS),
                LinearLayout.LayoutParams(0, dp(58), 1f))
        }
    }

    private fun navItem(icon: String, label: String, page: Page): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener {
                currentPage = page
                when (page) {
                    Page.HOME -> showHome()
                    Page.TABLE -> showTables()
                    Page.FNB -> showFnb()
                    Page.TRANSACTIONS -> showTransactions()
                }
            }

            val iconView = TextView(this@MainActivity).apply {
                text = icon
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(if (currentPage == page) C.GREEN else C.LIGHT_MUTED)
            }

            val textView = TextView(this@MainActivity).apply {
                text = label
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(if (currentPage == page) C.GREEN else C.MUTED)
                setPadding(0, dp(2), 0, 0)
            }

            addView(iconView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(27)
            ))
            addView(textView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(20)
            ))
        }
    }

    private fun refreshNav() {
        val parent = bottomNav
        for (i in 0 until parent.childCount) {
            val item = parent.getChildAt(i) as LinearLayout
            val selected = when (i) {
                0 -> currentPage == Page.HOME
                1 -> currentPage == Page.TABLE
                2 -> currentPage == Page.FNB
                else -> currentPage == Page.TRANSACTIONS
            }

            (item.getChildAt(0) as TextView).setTextColor(
                if (selected) C.GREEN else C.LIGHT_MUTED
            )
            (item.getChildAt(1) as TextView).setTextColor(
                if (selected) C.GREEN else C.MUTED
            )
        }
    }

    private fun setHeader(title: String, subtitle: String) {
        titleView.text = title
        subtitleView.text = subtitle
        refreshNav()
    }

    // -------------------------------------------------------------------------
    // HOME
    // -------------------------------------------------------------------------

    private fun showHome() {
        currentPage = Page.HOME
        homeTimerViews.clear()
        detailTimerView = null
        content.removeAllViews()
        setHeader(
            "Dashboard",
            SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID")).format(Date())
        )

        val hero = card(C.WHITE, 22)
        hero.setPadding(dp(18), dp(17), dp(18), dp(17))

        hero.addView(TextView(this).apply {
            text = "Selamat datang 👋"
            textSize = 12f
            setTextColor(C.MUTED)
        })

        hero.addView(TextView(this).apply {
            text = "Kelola rental & cafe lebih mudah"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(C.TEXT)
            setPadding(0, dp(4), 0, dp(3))
        })

        hero.addView(TextView(this).apply {
            text = "Pantau sesi aktif, waktu bermain, TV, dan pesanan F&B."
            textSize = 10f
            setTextColor(C.LIGHT_MUTED)
        })

        content.addView(hero, wrap().apply { bottomMargin = dp(12) })

        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        summary.addView(summaryCard(
            "Meja",
            tables.size.toString(),
            C.TEXT
        ), LinearLayout.LayoutParams(0, dp(92), 1f).apply {
            rightMargin = dp(4)
        })

        summary.addView(summaryCard(
            "Aktif",
            tables.count { it.active }.toString(),
            C.GREEN
        ), LinearLayout.LayoutParams(0, dp(92), 1f).apply {
            leftMargin = dp(4)
            rightMargin = dp(4)
        })

        summary.addView(summaryCard(
            "F&B",
            formatRupiah(todayFnb()),
            C.TEXT
        ), LinearLayout.LayoutParams(0, dp(92), 1f).apply {
            leftMargin = dp(4)
        })

        content.addView(summary, wrap().apply { bottomMargin = dp(18) })

        sectionTitle("Meja PlayStation")

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (r in 0 until 5) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            for (c in 0 until 2) {
                val number = r * 2 + c + 1
                row.addView(
                    tableCard(number),
                    LinearLayout.LayoutParams(0, dp(158), 1f).apply {
                        if (c == 0) rightMargin = dp(5) else leftMargin = dp(5)
                        bottomMargin = dp(10)
                    }
                )
            }
            grid.addView(row, wrap())
        }

        content.addView(grid)

        sectionTitle("F&B")

        val fnb = card(C.WHITE, 20)
        fnb.setPadding(dp(14), dp(14), dp(14), dp(14))

        val fnbHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fnbHeader.addView(TextView(this@MainActivity).apply {
            text = "Pesanan & Produk Cafe"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(C.TEXT)
        }, LinearLayout.LayoutParams(0, dp(42), 1f))

        val open = smallButton("Buka F&B")
        open.setOnClickListener {
            currentPage = Page.FNB
            showFnb()
        }
        fnbHeader.addView(open, LinearLayout.LayoutParams(dp(92), dp(40)))

        fnb.addView(fnbHeader)

        val fnbRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(7), 0, 0)
        }

        fnbRow.addView(fnbMini("☕", "Minuman", "${fnbProducts.count { it.category == "Minuman" }} menu"),
            LinearLayout.LayoutParams(0, dp(76), 1f).apply { rightMargin = dp(4) })

        fnbRow.addView(fnbMini("🍜", "Makanan", "${fnbProducts.count { it.category == "Makanan" }} menu"),
            LinearLayout.LayoutParams(0, dp(76), 1f).apply {
                leftMargin = dp(4); rightMargin = dp(4)
            })

        fnbRow.addView(fnbMini("🧾", "Pesanan", "${orders.size} order"),
            LinearLayout.LayoutParams(0, dp(76), 1f).apply { leftMargin = dp(4) })

        fnb.addView(fnbRow)

        content.addView(fnb, wrap())
    }

    private fun tableCard(number: Int): LinearLayout {
        val table = tables[number - 1]
        val active = table.active && remaining(table) > 0
        val paused = table.active && table.paused

        if (table.active && !paused && remaining(table) <= 0) {
            finishSession(table, false)
        }

        val background = when {
            paused -> C.PAUSE_BG
            active -> C.ACTIVE_BG
            else -> C.WHITE
        }

        val root = card(background, 19).apply {
            setPadding(dp(13), dp(11), dp(13), dp(10))
            isClickable = true
            setOnClickListener {
                selectedTable = number
                currentPage = Page.TABLE
                showTableDetail()
            }
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        top.addView(TextView(this@MainActivity).apply {
            text = "MEJA ${String.format("%02d", number)}"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(C.MUTED)
        }, LinearLayout.LayoutParams(0, dp(25), 1f))

        val status = when {
            paused -> "PAUSE"
            active -> "AKTIF"
            else -> "TERSEDIA"
        }

        val statusView = TextView(this).apply {
            text = status
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(
                when {
                    paused -> C.AMBER
                    active -> C.GREEN
                    else -> C.MUTED
                }
            )
            background = rounded(
                when {
                    paused -> C.PAUSE_CHIP
                    active -> C.GREEN_CHIP
                    else -> C.GRAY_CHIP
                },
                dp(9)
            )
            setPadding(dp(7), 0, dp(7), 0)
        }

        top.addView(statusView, LinearLayout.LayoutParams(dp(64), dp(25)))
        root.addView(top)

        root.addView(TextView(this).apply {
            text = table.psType
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(C.TEXT)
            setPadding(0, dp(5), 0, 0)
        }, wrap())

        root.addView(TextView(this).apply {
            text = if (table.tvIp.isBlank()) "TV belum diatur" else "TV siap"
            textSize = 8f
            setTextColor(C.LIGHT_MUTED)
            setPadding(0, dp(2), 0, 0)
        }, wrap())

        val timerView = TextView(this).apply {
            text = if (active || paused) formatTime(remaining(table))
            else "Siap digunakan"
            textSize = if (active || paused) 18f else 9f
            typeface = if (active || paused) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(if (active || paused) C.TEXT else C.LIGHT_MUTED)
            setPadding(0, dp(7), 0, 0)
        }
        root.addView(timerView, wrap())

        if (currentPage == Page.HOME && (active || paused)) {
            homeTimerViews[number] = timerView
        }

        return root
    }

    private fun refreshTableCards() {
        tables.forEach { table ->
            if (table.active && !table.paused && remaining(table) <= 0L) {
                finishSession(table, false)
            }
        }

        if (currentPage == Page.HOME) {
            homeTimerViews.forEach { (number, view) ->
                val table = tables[number - 1]
                view.text = if (table.active) {
                    formatTime(remaining(table))
                } else {
                    "Siap digunakan"
                }
            }
        } else if (currentPage == Page.TABLE) {
            detailTimerView?.let { view ->
                val table = tables[selectedTable - 1]
                view.text = if (table.active) {
                    formatTime(remaining(table))
                } else {
                    "00:00:00"
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // TABLES
    // -------------------------------------------------------------------------

    private fun showTables() {
        currentPage = Page.TABLE
        content.removeAllViews()
        setHeader("Meja", "Pilih meja untuk memulai atau mengelola sesi")

        val active = tables.count { it.active }
        val available = tables.count { !it.active }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        info.addView(summaryCard("Aktif", active.toString(), C.GREEN),
            LinearLayout.LayoutParams(0, dp(82), 1f).apply { rightMargin = dp(5) })
        info.addView(summaryCard("Tersedia", available.toString(), C.TEXT),
            LinearLayout.LayoutParams(0, dp(82), 1f).apply { leftMargin = dp(5) })

        content.addView(info, wrap().apply { bottomMargin = dp(18) })

        for (table in tables) {
            val row = card(
                if (table.active) C.ACTIVE_BG else C.WHITE,
                18
            ).apply {
                setPadding(dp(15), dp(12), dp(15), dp(12))
                setOnClickListener {
                    selectedTable = table.number
                    showTableDetail()
                }
            }

            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            line.addView(TextView(this@MainActivity).apply {
                text = "Meja ${String.format("%02d", table.number)}"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(C.TEXT)
            }, LinearLayout.LayoutParams(0, dp(46), 1f))

            line.addView(TextView(this@MainActivity).apply {
                text = if (table.active) formatTime(remaining(table)) else "Tersedia"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (table.active) C.GREEN else C.MUTED)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(105), dp(46)))

            row.addView(line)
            content.addView(row, wrap().apply { bottomMargin = dp(8) })
        }
    }

    private fun showTableDetail() {
        currentPage = Page.TABLE
        homeTimerViews.clear()
        detailTimerView = null
        content.removeAllViews()

        val table = tables[selectedTable - 1]

        setHeader(
            "Meja ${String.format("%02d", table.number)}",
            "${table.psType} • ${if (table.active) "Sesi aktif" else "Belum ada sesi"}"
        )

        val hero = card(
            if (table.active) C.ACTIVE_BG else C.WHITE,
            24
        ).apply {
            setPadding(dp(18), dp(17), dp(18), dp(17))
        }

        hero.addView(TextView(this).apply {
            text = if (table.active && table.paused) "SESI DIJEDA"
            else if (table.active) "SESI AKTIF"
            else "MEJA SIAP"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (table.active) C.GREEN else C.MUTED)
        })

        detailTimerView = TextView(this).apply {
            text = if (table.active) formatTime(remaining(table)) else "00:00:00"
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(C.TEXT)
            setPadding(0, dp(5), 0, dp(3))
        }
        hero.addView(detailTimerView)

        hero.addView(TextView(this).apply {
            text = if (table.active) {
                "Tarif ${formatRupiah(priceFor(table.psType))} / jam"
            } else {
                "Pilih durasi untuk memulai sesi"
            }
            textSize = 10f
            setTextColor(C.MUTED)
        })

        content.addView(hero, wrap().apply { bottomMargin = dp(12) })

        if (!table.active) {
            sectionTitle("Mulai Sesi")

            val durations = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            listOf(30L, 60L, 120L).forEachIndexed { index, min ->
                val b = softButton("${min} Menit")
                b.setOnClickListener { startSession(table, min) }
                durations.addView(
                    b,
                    LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                        if (index > 0) leftMargin = dp(4)
                        if (index < 2) rightMargin = dp(4)
                    }
                )
            }

            content.addView(durations, wrap().apply { bottomMargin = dp(9) })

            val custom = primaryButton("Mulai Durasi Custom")
            custom.setOnClickListener { customStartDialog(table) }
            content.addView(custom, fullButton())
        } else {
            val controls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val pause = softButton(if (table.paused) "Lanjut" else "Pause")
            pause.setOnClickListener {
                if (table.paused) resumeSession(table) else pauseSession(table)
                showTableDetail()
            }

            val add = softButton("+30 Menit")
            add.setOnClickListener {
                addTime(table, 30)
                showTableDetail()
            }

            controls.addView(pause, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(4) })
            controls.addView(add, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(4) })

            content.addView(controls, wrap().apply { bottomMargin = dp(8) })

            val fnbButton = primaryButton("Tambah Pesanan F&B")
            fnbButton.setOnClickListener {
                currentPage = Page.FNB
                showFnb()
            }
            content.addView(fnbButton, fullButton())

            val finish = dangerButton("Selesaikan Sesi")
            finish.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Selesaikan sesi?")
                    .setMessage("Sesi Meja ${String.format("%02d", table.number)} akan dihentikan.")
                    .setNegativeButton("BATAL", null)
                    .setPositiveButton("SELESAI") { _, _ ->
                        finishSession(table, true)
                        showTableDetail()
                    }
                    .show()
            }
            content.addView(finish, fullButton())
        }

        sectionTitle("Informasi Meja")

        val info = card(C.WHITE, 18)
        info.setPadding(dp(15), dp(12), dp(15), dp(12))

        infoRow(info, "PlayStation", table.psType)
        infoRow(info, "Tarif / jam", formatRupiah(priceFor(table.psType)))
        infoRow(info, "Tagihan PS", formatRupiah(table.bill))
        infoRow(info, "TV", if (table.tvIp.isBlank()) "Belum diatur" else table.tvIp)

        content.addView(info, wrap().apply { bottomMargin = dp(10) })

        val tv = softButton("Pengaturan TV Meja")
        tv.setOnClickListener { showTvDialog(table) }
        content.addView(tv, fullButton())

        val back = softButton("Kembali ke Daftar Meja")
        back.setOnClickListener { showTables() }
        content.addView(back, fullButton())
    }

    // -------------------------------------------------------------------------
    // F&B
    // -------------------------------------------------------------------------

    private fun showFnb() {
        currentPage = Page.FNB
        content.removeAllViews()
        setHeader("F&B", "Pesanan cafe terintegrasi dengan meja PlayStation")

        val selected = card(C.WHITE, 20)
        selected.setPadding(dp(15), dp(13), dp(15), dp(13))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(TextView(this).apply {
            text = "Pesanan untuk"
            textSize = 10f
            setTextColor(C.MUTED)
        }, LinearLayout.LayoutParams(0, dp(25), 1f))

        val tableButton = smallButton("Meja ${String.format("%02d", selectedTable)}")
        tableButton.setOnClickListener { chooseFnbTable() }
        row.addView(tableButton, LinearLayout.LayoutParams(dp(108), dp(40)))

        selected.addView(row)
        content.addView(selected, wrap().apply { bottomMargin = dp(12) })

        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        listOf("Semua", "Minuman", "Makanan").forEach { category ->
            val b = if (category == fnbFilter) primaryButton(category) else softButton(category)
            b.textSize = 10f
            b.setOnClickListener {
                fnbFilter = category
                showFnb()
            }
            filters.addView(
                b,
                LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    leftMargin = dp(3); rightMargin = dp(3)
                }
            )
        }

        content.addView(filters, wrap().apply { bottomMargin = dp(15) })

        val visibleProducts = fnbProducts.filter {
            fnbFilter == "Semua" || it.category == fnbFilter
        }

        visibleProducts.forEach { product ->
            val productCard = card(C.WHITE, 18)
            productCard.setPadding(dp(14), dp(11), dp(12), dp(11))

            val productRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val icon = if (product.category == "Minuman") "☕" else "🍜"

            productRow.addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = 23f
                gravity = Gravity.CENTER
                background = rounded(C.GRAY_BG, dp(14))
            }, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                rightMargin = dp(10)
            })

            val details = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }

            details.addView(TextView(this@MainActivity).apply {
                text = product.name
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(C.TEXT)
            })

            details.addView(TextView(this@MainActivity).apply {
                text = product.category
                textSize = 9f
                setTextColor(C.LIGHT_MUTED)
                setPadding(0, dp(2), 0, 0)
            })

            productRow.addView(details, LinearLayout.LayoutParams(0, dp(52), 1f))

            productRow.addView(TextView(this@MainActivity).apply {
                text = formatRupiah(product.price)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(C.GREEN)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(92), dp(52)))

            val plus = smallButton("+")
            plus.setOnClickListener {
                addFnbOrder(product, selectedTable)
            }
            productRow.addView(plus, LinearLayout.LayoutParams(dp(44), dp(44)))

            productCard.addView(productRow)
            content.addView(productCard, wrap().apply { bottomMargin = dp(8) })
        }

        sectionTitle("Ringkasan")

        val total = todayFnb()
        val summary = card(C.WHITE, 20)
        summary.setPadding(dp(15), dp(13), dp(15), dp(13))

        infoRow(summary, "Jumlah order", orders.size.toString())
        infoRow(summary, "Total F&B", formatRupiah(total))

        content.addView(summary)
    }

    private fun addFnbOrder(product: FnbProduct, table: Int) {
        val map = mutableMapOf(product.id to 1)
        orders.add(
            FnbOrder(
                id = System.currentTimeMillis(),
                table = table,
                items = map,
                total = product.price
            )
        )
        Toast.makeText(
            this,
            "${product.name} ditambahkan • Meja ${String.format("%02d", table)}",
            Toast.LENGTH_SHORT
        ).show()
        showFnb()
    }

    private fun chooseFnbTable() {
        val labels = tables.map {
            "Meja ${String.format("%02d", it.number)}" +
                if (it.active) " • ${it.psType}" else " • Tersedia"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pilih meja")
            .setSingleChoiceItems(labels, selectedTable - 1) { dialog, which ->
                selectedTable = which + 1
                dialog.dismiss()
                showFnb()
            }
            .show()
    }

    // -------------------------------------------------------------------------
    // TRANSACTIONS
    // -------------------------------------------------------------------------

    private fun showTransactions() {
        currentPage = Page.TRANSACTIONS
        content.removeAllViews()
        setHeader("Transaksi", "Ringkasan pendapatan rental & F&B")

        val psRevenue = tables.sumOf { it.bill }
        val fnbRevenue = todayFnb()

        val total = card(C.TEXT, 22)
        total.setPadding(dp(18), dp(16), dp(18), dp(16))

        total.addView(TextView(this).apply {
            text = "TOTAL PENDAPATAN"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(205, 214, 222))
        })

        total.addView(TextView(this).apply {
            text = formatRupiah(psRevenue + fnbRevenue)
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(5), 0, dp(2))
        })

        total.addView(TextView(this).apply {
            text = "Rental PS ${formatRupiah(psRevenue)} • F&B ${formatRupiah(fnbRevenue)}"
            textSize = 9f
            setTextColor(Color.rgb(183, 195, 205))
        })

        content.addView(total, wrap().apply { bottomMargin = dp(15) })

        sectionTitle("Aktivitas F&B")

        if (orders.isEmpty()) {
            val empty = card(C.WHITE, 18)
            empty.setPadding(dp(18), dp(20), dp(18), dp(20))
            empty.addView(TextView(this).apply {
                text = "Belum ada transaksi F&B"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(C.MUTED)
            })
            content.addView(empty)
        } else {
            orders.asReversed().forEach { order ->
                val item = card(C.WHITE, 18)
                item.setPadding(dp(14), dp(11), dp(14), dp(11))

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                row.addView(TextView(this@MainActivity).apply {
                    text = "Meja ${String.format("%02d", order.table)}"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(C.TEXT)
                }, LinearLayout.LayoutParams(0, dp(45), 1f))

                row.addView(TextView(this@MainActivity).apply {
                    text = formatRupiah(order.total)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(C.GREEN)
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(dp(100), dp(45)))

                item.addView(row)
                content.addView(item, wrap().apply { bottomMargin = dp(7) })
            }
        }
    }

    // -------------------------------------------------------------------------
    // SESSION / TV
    // -------------------------------------------------------------------------

    private fun startSession(table: TableState, minutes: Long) {
        table.active = true
        table.paused = false
        table.endAt = System.currentTimeMillis() + minutes * 60_000L
        table.pausedRemaining = 0L
        table.bill = max(table.bill, priceFor(table.psType) * minutes / 60L)

        saveTables()
        sendTv(table, "START:${minutes * 60L}")
        Toast.makeText(
            this,
            "Sesi Meja ${String.format("%02d", table.number)} dimulai",
            Toast.LENGTH_SHORT
        ).show()
        showTableDetail()
    }

    private fun customStartDialog(table: TableState) {
        val input = EditText(this).apply {
            hint = "Durasi dalam menit"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(input, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
            ))
        }

        AlertDialog.Builder(this)
            .setTitle("Durasi custom")
            .setView(box)
            .setNegativeButton("BATAL", null)
            .setPositiveButton("MULAI") { _, _ ->
                val min = input.text.toString().toLongOrNull()
                if (min != null && min > 0) startSession(table, min)
                else Toast.makeText(this, "Durasi tidak valid", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun pauseSession(table: TableState) {
        if (!table.active || table.paused) return
        table.pausedRemaining = remaining(table)
        table.paused = true
        table.endAt = 0L
        saveTables()
        sendTv(table, "PAUSE")
    }

    private fun resumeSession(table: TableState) {
        if (!table.active || !table.paused) return
        table.endAt = System.currentTimeMillis() + table.pausedRemaining
        table.pausedRemaining = 0L
        table.paused = false
        saveTables()
        sendTv(table, "RESUME")
    }

    private fun addTime(table: TableState, minutes: Long) {
        if (!table.active) return

        if (table.paused) {
            table.pausedRemaining += minutes * 60_000L
        } else {
            table.endAt += minutes * 60_000L
        }

        table.bill += priceFor(table.psType) * minutes / 60L
        saveTables()
        sendTv(table, "ADD:${minutes * 60L}")
    }

    private fun finishSession(table: TableState, send: Boolean) {
        table.active = false
        table.paused = false
        table.endAt = 0L
        table.pausedRemaining = 0L
        saveTables()

        if (send) sendTv(table, "STOP")
    }

    private fun remaining(table: TableState): Long {
        return if (!table.active) 0L
        else if (table.paused) table.pausedRemaining
        else max(0L, table.endAt - System.currentTimeMillis())
    }

    private fun showTvDialog(table: TableState) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), 0)
        }

        val ip = EditText(this).apply {
            hint = "IP Android TV, contoh 192.168.1.20"
            setSingleLine(true)
            setText(table.tvIp)
        }

        box.addView(ip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ))

        val bill = EditText(this).apply {
            hint = "Tagihan TV"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(if (table.bill == 0L) "" else table.bill.toString())
        }

        box.addView(bill, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ))

        AlertDialog.Builder(this)
            .setTitle("TV Meja ${String.format("%02d", table.number)}")
            .setView(box)
            .setNegativeButton("BATAL", null)
            .setNeutralButton("BLANK TV") { _, _ ->
                table.tvIp = ip.text.toString().trim()
                saveTables()
                sendTv(table, "BLANK")
            }
            .setPositiveButton("SIMPAN") { _, _ ->
                table.tvIp = ip.text.toString().trim()
                table.bill = parseNominal(bill.text.toString())
                saveTables()
                Toast.makeText(this, "Pengaturan TV disimpan", Toast.LENGTH_SHORT).show()
                showTableDetail()
            }
            .show()
    }

    private fun sendTv(table: TableState, command: String) {
        if (table.tvIp.isBlank()) return

        executor.execute {
            try {
                Socket(table.tvIp, 8787).use { socket ->
                    socket.soTimeout = 2500
                    PrintWriter(socket.getOutputStream(), true).use { writer ->
                        writer.println(command)
                        writer.flush()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "TV Meja ${String.format("%02d", table.number)} tidak dapat dihubungi",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // SETTINGS
    // -------------------------------------------------------------------------

    private fun showSettingsDialog() {
        val items = arrayOf(
            "Harga PS",
            "Pengaturan Meja",
            "Kelola F&B"
        )

        AlertDialog.Builder(this)
            .setTitle("Pengaturan")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> priceSettings()
                    1 -> tableSettings()
                    2 -> fnbSettings()
                }
            }
            .show()
    }

    private fun priceSettings() {
        val types = arrayOf("PS3", "PS4", "PS5")
        val values = types.map { prefs.getLong("price_$it", priceFor(it)) }.toLongArray()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }

        val inputs = mutableListOf<EditText>()

        types.forEachIndexed { index, type ->
            val input = EditText(this).apply {
                hint = "$type / jam"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setSingleLine(true)
                setText(values[index].toString())
            }
            inputs.add(input)
            box.addView(input, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
            ))
        }

        AlertDialog.Builder(this)
            .setTitle("Harga PS")
            .setView(box)
            .setNegativeButton("BATAL", null)
            .setPositiveButton("SIMPAN") { _, _ ->
                types.forEachIndexed { index, type ->
                    val price = parseNominal(inputs[index].text.toString())
                    if (price > 0) prefs.edit().putLong("price_$type", price).apply()
                }
                Toast.makeText(this, "Harga disimpan", Toast.LENGTH_SHORT).show()
                showHome()
            }
            .show()
    }

    private fun tableSettings() {
        val labels = tables.map {
            "Meja ${String.format("%02d", it.number)} • ${it.psType}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pilih meja")
            .setItems(labels) { _, which ->
                tableConfigDialog(tables[which])
            }
            .show()
    }

    private fun tableConfigDialog(table: TableState) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }

        val ps = Spinner(this)
        ps.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf("PS3", "PS4", "PS5")
        )
        ps.setSelection(arrayOf("PS3", "PS4", "PS5").indexOf(table.psType).coerceAtLeast(0))

        box.addView(ps, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        ))

        val ip = EditText(this).apply {
            hint = "IP Android TV"
            setSingleLine(true)
            setText(table.tvIp)
        }
        box.addView(ip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ))

        AlertDialog.Builder(this)
            .setTitle("Meja ${String.format("%02d", table.number)}")
            .setView(box)
            .setNegativeButton("BATAL", null)
            .setPositiveButton("SIMPAN") { _, _ ->
                table.psType = ps.selectedItem.toString()
                table.tvIp = ip.text.toString().trim()
                saveTables()
                showHome()
            }
            .show()
    }

    private fun fnbSettings() {
        val message = "Menu F&B saat ini:\n\n" +
            fnbProducts.joinToString("\n") {
                "• ${it.name} — ${formatRupiah(it.price)}"
            }

        AlertDialog.Builder(this)
            .setTitle("Kelola F&B")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // -------------------------------------------------------------------------
    // UI HELPERS
    // -------------------------------------------------------------------------

    private fun sectionTitle(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(C.TEXT)
            setPadding(dp(2), dp(2), dp(2), dp(9))
        }, wrap())
    }

    private fun summaryCard(label: String, value: String, color: Int): LinearLayout {
        return card(C.WHITE, 18).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))

            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 9f
                setTextColor(C.LIGHT_MUTED)
            })

            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color)
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun fnbMini(icon: String, title: String, subtitle: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            background = rounded(C.GRAY_BG, dp(15))

            addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = 17f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(25)
            ))

            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(C.TEXT)
            }, wrap())

            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 7f
                gravity = Gravity.CENTER
                setTextColor(C.LIGHT_MUTED)
            }, wrap())
        }
    }

    private fun infoRow(parent: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 10f
            setTextColor(C.MUTED)
        }, LinearLayout.LayoutParams(0, dp(38), 1f))

        row.addView(TextView(this@MainActivity).apply {
            text = value
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            setTextColor(C.TEXT)
        }, LinearLayout.LayoutParams(dp(150), dp(38)))

        parent.addView(row)
    }

    private fun card(color: Int, radius: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color, dp(radius))
            elevation = dp(1).toFloat()
        }
    }

    private fun smallButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 9f
            isAllCaps = false
            gravity = Gravity.CENTER
            setTextColor(C.GREEN)
            background = rounded(C.GREEN_CHIP, dp(12))
            minHeight = dp(40)
            minimumHeight = dp(40)
            setPadding(dp(7), 0, dp(7), 0)
        }
    }

    private fun softButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 12f
            isAllCaps = false
            gravity = Gravity.CENTER
            setTextColor(C.TEXT)
            background = rounded(C.GRAY_BG, dp(15))
            minHeight = dp(52)
            minimumHeight = dp(52)
            setPadding(dp(8), 0, dp(8), 0)
        }
    }

    private fun primaryButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 12f
            isAllCaps = false
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(C.GREEN, dp(15))
            minHeight = dp(52)
            minimumHeight = dp(52)
            setPadding(dp(8), 0, dp(8), 0)
        }
    }

    private fun dangerButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            textSize = 12f
            isAllCaps = false
            gravity = Gravity.CENTER
            setTextColor(C.RED)
            background = rounded(C.RED_BG, dp(15))
            minHeight = dp(52)
            minimumHeight = dp(52)
            setPadding(dp(8), 0, dp(8), 0)
        }
    }

    private fun iconButton(icon: String, size: Int): Button {
        return Button(this).apply {
            text = icon
            textSize = 18f
            isAllCaps = false
            gravity = Gravity.CENTER
            setTextColor(C.TEXT)
            background = rounded(C.WHITE, dp(15))
            minHeight = dp(size)
            minimumHeight = dp(size)
            setPadding(0, 0, 0, 0)
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            setStroke(dp(1), C.BORDER)
        }
    }

    private fun wrap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun fullButton(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54)
        ).apply {
            topMargin = dp(5)
            bottomMargin = dp(5)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    // -------------------------------------------------------------------------
    // DATA
    // -------------------------------------------------------------------------

    private fun priceFor(ps: String): Long {
        val default = when (ps) {
            "PS3" -> 4000L
            "PS4" -> 5000L
            else -> 8000L
        }
        return prefs.getLong("price_$ps", default)
    }

    private fun todayFnb(): Long {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return orders.filter {
            SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(it.createdAt)) == today
        }.sumOf { it.total }
    }

    private fun parseNominal(value: String): Long {
        return value
            .replace(".", "")
            .replace(",", "")
            .replace("Rp", "", true)
            .trim()
            .toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: 0L
    }

    private fun formatRupiah(value: Long): String {
        return String.format(Locale.US, "Rp %,d", value).replace(",", ".")
    }

    private fun formatTime(millis: Long): String {
        val total = max(0L, millis) / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    private fun saveTables() {
        val e = prefs.edit()
        tables.forEach { table ->
            val key = "table_${table.number}_"
            e.putString(key + "ps", table.psType)
            e.putBoolean(key + "active", table.active)
            e.putBoolean(key + "paused", table.paused)
            e.putLong(key + "end", table.endAt)
            e.putLong(key + "pausedRemaining", table.pausedRemaining)
            e.putString(key + "ip", table.tvIp)
            e.putLong(key + "bill", table.bill)
        }
        e.apply()
    }

    private fun loadTables() {
        tables.forEach { table ->
            val key = "table_${table.number}_"
            table.psType = prefs.getString(key + "ps", "PS5") ?: "PS5"
            table.active = prefs.getBoolean(key + "active", false)
            table.paused = prefs.getBoolean(key + "paused", false)
            table.endAt = prefs.getLong(key + "end", 0L)
            table.pausedRemaining = prefs.getLong(key + "pausedRemaining", 0L)
            table.tvIp = prefs.getString(key + "ip", "") ?: ""
            table.bill = prefs.getLong(key + "bill", 0L)

            if (table.active && !table.paused && table.endAt <= System.currentTimeMillis()) {
                table.active = false
                table.endAt = 0L
            }
        }
    }

    // -------------------------------------------------------------------------
    // COLORS
    // -------------------------------------------------------------------------

    private object C {
        val BG = Color.rgb(246, 248, 251)
        val WHITE = Color.WHITE
        val TEXT = Color.rgb(40, 48, 61)
        val MUTED = Color.rgb(103, 114, 130)
        val LIGHT_MUTED = Color.rgb(145, 154, 167)
        val BORDER = Color.rgb(232, 237, 242)

        val GRAY_BG = Color.rgb(239, 243, 247)
        val GRAY_CHIP = Color.rgb(242, 245, 248)

        val GREEN = Color.rgb(67, 157, 117)
        val GREEN_CHIP = Color.rgb(233, 246, 239)
        val ACTIVE_BG = Color.rgb(248, 252, 249)

        val AMBER = Color.rgb(178, 140, 79)
        val PAUSE_BG = Color.rgb(252, 250, 245)
        val PAUSE_CHIP = Color.rgb(248, 242, 230)

        val RED = Color.rgb(178, 82, 94)
        val RED_BG = Color.rgb(252, 238, 240)
    }
}
