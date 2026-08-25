package com.deltaatrbot.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var subtitleText: TextView
    private lateinit var statusPill: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var uPnlValue: TextView
    private lateinit var rPnlValue: TextView
    private lateinit var lastPriceValue: TextView
    private lateinit var trailStopValue: TextView
    private lateinit var walletTableBody: LinearLayout
    private lateinit var positionTableBody: LinearLayout
    private lateinit var ordersTableBody: LinearLayout
    private lateinit var logsContainer: LinearLayout

    private var pollingActive = true
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        subtitleText = findViewById(R.id.subtitleText)
        statusPill = findViewById(R.id.statusPill)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        uPnlValue = findViewById(R.id.uPnlValue)
        rPnlValue = findViewById(R.id.rPnlValue)
        lastPriceValue = findViewById(R.id.lastPriceValue)
        trailStopValue = findViewById(R.id.trailStopValue)
        walletTableBody = findViewById(R.id.walletTableBody)
        positionTableBody = findViewById(R.id.positionTableBody)
        ordersTableBody = findViewById(R.id.ordersTableBody)
        logsContainer = findViewById(R.id.logsContainer)

        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        swipeRefresh.setOnRefreshListener { refreshOnce() }

        buildWalletHeader()
        buildPositionHeader()
        buildOrdersHeader()
    }

    override fun onResume() {
        super.onResume()
        pollingActive = true
        if (!prefs.isConfigured()) {
            subtitleText.text = "Not configured - tap the gear icon to set your Backend URL"
            setStatusPill("Not configured", R.drawable.bg_pill_gray, R.color.carbon_text_muted)
            // Auto-open Settings only once, the very first time the app is
            // ever opened. Auto-launching on every resume caused a loop:
            // pressing Back from Settings without saving would immediately
            // bounce you right back into Settings, which looks like a crash.
            if (!prefs.hasPromptedSetup) {
                prefs.hasPromptedSetup = true
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            return
        }
        startPolling()
    }

    override fun onPause() {
        super.onPause()
        pollingActive = false
    }

    private fun startPolling() {
        lifecycleScope.launch {
            while (pollingActive) {
                refreshOnce()
                kotlinx.coroutines.delay(1500L)
            }
        }
    }

    private fun refreshOnce() {
        val backendUrl = prefs.backendUrl
        if (backendUrl.isBlank()) {
            swipeRefresh.isRefreshing = false
            return
        }
        lifecycleScope.launch {
            val client = BackendApiClient(backendUrl)
            val status = withContext(Dispatchers.IO) { client.getStatus() }
            val wallet = withContext(Dispatchers.IO) { client.getWallet() }
            val positions = withContext(Dispatchers.IO) { client.getPositions() }
            val orders = withContext(Dispatchers.IO) { client.getOrders() }
            val logs = withContext(Dispatchers.IO) { client.getLogs() }

            if (status == null) {
                setStatusPill("Unreachable", R.drawable.bg_pill_red, R.color.carbon_red)
                subtitleText.text = "Could not reach $backendUrl"
            } else {
                renderStatus(status)
            }
            wallet?.let { renderWallet(it) }
            positions?.let { renderPositions(it) }
            orders?.let { renderOrders(it) }
            logs?.let { renderLogs(it) }

            swipeRefresh.isRefreshing = false
        }
    }

    private fun setStatusPill(text: String, bgRes: Int, colorRes: Int) {
        statusPill.text = text
        statusPill.setBackgroundResource(bgRes)
        statusPill.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun renderStatus(s: JSONObject) {
        val running = s.optBoolean("running", false)
        val dryRun = s.optBoolean("dry_run", true)
        when {
            running && dryRun -> setStatusPill("Dry-Run", R.drawable.bg_pill_blue, R.color.carbon_blue)
            running -> setStatusPill("Live", R.drawable.bg_pill_green, R.color.carbon_green)
            else -> setStatusPill("Stopped", R.drawable.bg_pill_gray, R.color.carbon_text_muted)
        }

        val symbol = s.optString("product_symbol", "—")
        val productId = s.optInt("product_id", 0)
        subtitleText.text = "$symbol · product_id $productId"

        val uPnl = s.optDouble("unrealized_pnl", Double.NaN)
        val rPnl = s.optDouble("realized_pnl", Double.NaN)
        uPnlValue.text = fmtSigned(uPnl)
        uPnlValue.setTextColor(pnlColor(uPnl))
        rPnlValue.text = fmtSigned(rPnl)
        rPnlValue.setTextColor(pnlColor(rPnl))

        lastPriceValue.text = fmt(s.optDouble("last_price", Double.NaN))
        val atr = s.optDouble("last_atr", Double.NaN)
        val stop = s.optDouble("last_trail_stop", Double.NaN)
        trailStopValue.text = "${fmt(stop)}  (ATR ${fmt(atr)})"
    }

    private fun pnlColor(v: Double): Int {
        val res = when {
            v.isNaN() -> R.color.carbon_text_primary
            v > 0 -> R.color.carbon_green
            v < 0 -> R.color.carbon_red
            else -> R.color.carbon_text_primary
        }
        return ContextCompat.getColor(this, res)
    }

    private fun fmt(v: Double): String {
        if (v.isNaN()) return "—"
        return String.format(Locale.getDefault(), "%,.2f", v)
    }

    private fun fmtSigned(v: Double): String {
        if (v.isNaN()) return "—"
        val sign = if (v >= 0) "+" else ""
        return sign + fmt(v)
    }

    // ---------- Wallet table ----------

    private val walletWidths = listOf(80, 90, 90, 90, 90)

    private fun buildWalletHeader() {
        addRow(walletTableBody, listOf("Asset", "Balance", "Available", "Pos. Margin", "Order Margin"), walletWidths, isHeader = true)
    }

    private fun renderWallet(arr: JSONArray) {
        // keep header (index 0), clear rest
        while (walletTableBody.childCount > 1) walletTableBody.removeViewAt(1)
        if (arr.length() == 0) {
            addEmptyRow(walletTableBody, getString(R.string.empty_wallet))
            return
        }
        for (i in 0 until arr.length()) {
            val b = arr.optJSONObject(i) ?: continue
            addRow(
                walletTableBody,
                listOf(
                    b.optString("asset_symbol", ""),
                    fmtStr(b.optString("balance", "")),
                    fmtStr(b.optString("available_balance", "")),
                    fmtStr(b.optString("position_margin", "")),
                    fmtStr(b.optString("order_margin", ""))
                ),
                walletWidths
            )
        }
    }

    // ---------- Position table ----------

    private val positionWidths = listOf(80, 60, 90, 90, 90, 100)

    private fun buildPositionHeader() {
        addRow(positionTableBody, listOf("Symbol", "Size", "Entry", "Mark", "Liq.", "Unreal. PnL"), positionWidths, isHeader = true)
    }

    private fun renderPositions(arr: JSONArray) {
        while (positionTableBody.childCount > 1) positionTableBody.removeViewAt(1)
        val open = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { it.optDouble("size", 0.0) != 0.0 }
        if (open.isEmpty()) {
            addEmptyRow(positionTableBody, getString(R.string.empty_position))
            return
        }
        for (p in open) {
            val size = p.optDouble("size", 0.0)
            val pnl = p.optDouble("unrealized_pnl", 0.0)
            val cells = listOf(
                Cell(p.optString("product_symbol", "")),
                Cell(fmt(size), if (size > 0) R.color.carbon_green else R.color.carbon_red),
                Cell(fmt(p.optDouble("entry_price", 0.0))),
                Cell(fmt(p.optDouble("mark_price", 0.0))),
                Cell(fmt(p.optDouble("liquidation_price", 0.0))),
                Cell(fmtSigned(pnl), if (pnl > 0) R.color.carbon_green else if (pnl < 0) R.color.carbon_red else null)
            )
            addColoredRow(positionTableBody, cells, positionWidths)
        }
    }

    // ---------- Orders table ----------

    private val orderWidths = listOf(70, 80, 60, 90, 60, 70, 70)

    private fun buildOrdersHeader() {
        addRow(ordersTableBody, listOf("ID", "Symbol", "Side", "Type", "Size", "Unfilled", "State"), orderWidths, isHeader = true)
    }

    private fun renderOrders(arr: JSONArray) {
        while (ordersTableBody.childCount > 1) ordersTableBody.removeViewAt(1)
        if (arr.length() == 0) {
            addEmptyRow(ordersTableBody, getString(R.string.empty_orders))
            return
        }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val side = o.optString("side", "")
            val cells = listOf(
                Cell(o.optLong("id", 0).toString()),
                Cell(o.optString("product_symbol", "")),
                Cell(side.uppercase(), if (side == "buy") R.color.carbon_green else R.color.carbon_red),
                Cell(o.optString("order_type", "")),
                Cell(fmt(o.optDouble("size", 0.0))),
                Cell(fmt(o.optDouble("unfilled_size", 0.0))),
                Cell(o.optString("state", ""))
            )
            addColoredRow(ordersTableBody, cells, orderWidths)
        }
    }

    // ---------- Logs ----------

    private fun renderLogs(arr: JSONArray) {
        logsContainer.removeAllViews()
        if (arr.length() == 0) {
            val tv = TextView(this)
            tv.text = getString(R.string.empty_logs)
            tv.setTextColor(ContextCompat.getColor(this, R.color.carbon_text_muted))
            tv.textSize = 12f
            tv.setPadding(dp(8), dp(8), dp(8), dp(8))
            logsContainer.addView(tv)
            return
        }
        // newest first
        for (i in arr.length() - 1 downTo 0) {
            val l = arr.optJSONObject(i) ?: continue
            val level = l.optString("level", "info")
            val msg = l.optString("message", "")
            val timeRaw = l.optString("time", "")
            val timeStr = formatLogTime(timeRaw)

            val row = TextView(this)
            row.textSize = 11.5f
            row.typeface = android.graphics.Typeface.MONOSPACE
            row.setPadding(dp(8), dp(6), dp(8), dp(6))
            row.setTextColor(ContextCompat.getColor(this, levelColor(level)))
            row.text = "$timeStr  [${level.uppercase()}]  $msg"
            logsContainer.addView(row)

            val divider = View(this)
            divider.setBackgroundColor(Color.parseColor("#2a2a2a"))
            logsContainer.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }
    }

    private fun formatLogTime(raw: String): String {
        return try {
            // backend sends RFC3339; fall back to raw string if parsing fails
            val parsed = java.time.OffsetDateTime.parse(raw)
            timeFmt.format(Date.from(parsed.toInstant()))
        } catch (e: Exception) {
            raw.take(8)
        }
    }

    private fun levelColor(level: String): Int = when (level) {
        "signal" -> R.color.carbon_blue
        "order" -> R.color.carbon_green
        "error" -> R.color.carbon_red
        else -> R.color.carbon_text_secondary
    }

    // ---------- generic table row helpers ----------

    private data class Cell(val text: String, val colorRes: Int? = null)

    private fun fmtStr(s: String): String {
        val d = s.toDoubleOrNull() ?: return if (s.isBlank()) "—" else s
        return fmt(d)
    }

    private fun addEmptyRow(container: LinearLayout, message: String) {
        val tv = TextView(this)
        tv.text = message
        tv.setTextColor(ContextCompat.getColor(this, R.color.carbon_text_muted))
        tv.textSize = 12f
        tv.setPadding(dp(10), dp(12), dp(10), dp(12))
        container.addView(tv)
    }

    private fun addRow(container: LinearLayout, cells: List<String>, widths: List<Int>, isHeader: Boolean = false) {
        addColoredRow(container, cells.map { Cell(it) }, widths, isHeader)
    }

    private fun addColoredRow(container: LinearLayout, cells: List<Cell>, widths: List<Int>, isHeader: Boolean = false) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(8), dp(8), dp(8), dp(8))
        if (!isHeader) {
            val divider = View(this)
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.carbon_border))
            container.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }
        cells.forEachIndexed { idx, cell ->
            val tv = TextView(this)
            tv.text = cell.text
            tv.textSize = if (isHeader) 10f else 12f
            tv.typeface = if (isHeader) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.MONOSPACE
            val color = cell.colorRes?.let { ContextCompat.getColor(this, it) }
                ?: ContextCompat.getColor(this, if (isHeader) R.color.carbon_text_muted else R.color.carbon_text_primary)
            tv.setTextColor(color)
            tv.gravity = Gravity.START
            val width = widths.getOrElse(idx) { 80 }
            tv.layoutParams = LinearLayout.LayoutParams(dp(width), LinearLayout.LayoutParams.WRAP_CONTENT)
            row.addView(tv)
        }
        container.addView(row)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
