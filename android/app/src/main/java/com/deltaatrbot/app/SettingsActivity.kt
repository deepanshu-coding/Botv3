package com.deltaatrbot.app

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var inputBackendUrl: EditText
    private lateinit var inputDeltaBaseUrl: EditText
    private lateinit var inputApiKey: EditText
    private lateinit var inputApiSecret: EditText
    private lateinit var inputSymbol: EditText
    private lateinit var inputProductId: EditText
    private lateinit var inputLot: EditText
    private lateinit var inputLeverage: EditText
    private lateinit var statusText: TextView

    private var secretVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings_title)
        prefs = Prefs(this)

        inputBackendUrl = findViewById(R.id.inputBackendUrl)
        inputDeltaBaseUrl = findViewById(R.id.inputDeltaBaseUrl)
        inputApiKey = findViewById(R.id.inputApiKey)
        inputApiSecret = findViewById(R.id.inputApiSecret)
        inputSymbol = findViewById(R.id.inputSymbol)
        inputProductId = findViewById(R.id.inputProductId)
        inputLot = findViewById(R.id.inputLot)
        inputLeverage = findViewById(R.id.inputLeverage)
        statusText = findViewById(R.id.settingsStatusText)

        loadFromPrefs()

        findViewById<ImageButton>(R.id.toggleSecretVisibility).setOnClickListener {
            secretVisible = !secretVisible
            inputApiSecret.inputType = if (secretVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            inputApiSecret.setSelection(inputApiSecret.text.length)
        }

        findViewById<MaterialButton>(R.id.resolveSymbolButton).setOnClickListener { resolveSymbol() }
        findViewById<MaterialButton>(R.id.testKeysButton).setOnClickListener { testApiKeys() }
        findViewById<MaterialButton>(R.id.applyLeverageButton).setOnClickListener { applyLeverage() }
        findViewById<MaterialButton>(R.id.saveButton).setOnClickListener { saveSettings() }
    }

    private fun loadFromPrefs() {
        inputBackendUrl.setText(prefs.backendUrl)
        inputDeltaBaseUrl.setText(prefs.deltaBaseUrl.ifBlank { Prefs.DEFAULT_DELTA_BASE_URL })
        inputApiKey.setText(prefs.apiKey)
        inputApiSecret.setText(prefs.apiSecret)
        inputSymbol.setText(prefs.symbol)
        if (prefs.productId > 0) inputProductId.setText(prefs.productId.toString())
        inputLot.setText(prefs.lot.toString())
        inputLeverage.setText(prefs.leverage.toString())
    }

    private fun currentDeltaClient(): DeltaApiClient {
        val base = inputDeltaBaseUrl.text.toString().ifBlank { Prefs.DEFAULT_DELTA_BASE_URL }
        return DeltaApiClient(base, inputApiKey.text.toString().trim(), inputApiSecret.text.toString().trim())
    }

    private fun setStatus(msg: String, isError: Boolean) {
        statusText.text = msg
        statusText.setTextColor(
            ContextCompat.getColor(this, if (isError) R.color.carbon_red else R.color.carbon_green)
        )
    }

    private fun resolveSymbol() {
        val symbol = inputSymbol.text.toString().trim().uppercase()
        if (symbol.isBlank()) {
            setStatus("Enter a symbol first (e.g. BTCUSD)", true)
            return
        }
        setStatus("Resolving $symbol…", false)
        lifecycleScope.launch {
            val client = currentDeltaClient()
            val result = withContext(Dispatchers.IO) { client.getProductBySymbol(symbol) }
            if (result.success && result.body != null) {
                val product = result.body.optJSONObject("result")
                val id = product?.optInt("id", 0) ?: 0
                val notional = product?.optString("notional_type", "")
                inputProductId.setText(id.toString())
                setStatus("Resolved: $symbol -> product_id $id (notional_type=$notional)", false)
            } else {
                setStatus("Could not resolve $symbol: ${result.error ?: "unknown error"}", true)
            }
        }
    }

    private fun testApiKeys() {
        val key = inputApiKey.text.toString().trim()
        val secret = inputApiSecret.text.toString().trim()
        if (key.isBlank() || secret.isBlank()) {
            setStatus("Enter API key and secret first", true)
            return
        }
        setStatus("Testing API keys…", false)
        lifecycleScope.launch {
            val client = currentDeltaClient()
            val result = withContext(Dispatchers.IO) { client.getWalletBalances() }
            if (result.success) {
                val count = result.body?.optJSONArray("result")?.length() ?: 0
                setStatus("API keys OK — wallet has $count asset balance(s)", false)
            } else {
                setStatus("API key test failed: ${result.error ?: "unknown error"} (check IP whitelist too)", true)
            }
        }
    }

    private fun applyLeverage() {
        val productId = inputProductId.text.toString().toIntOrNull()
        val leverage = inputLeverage.text.toString().toIntOrNull()
        val key = inputApiKey.text.toString().trim()
        val secret = inputApiSecret.text.toString().trim()
        if (productId == null || productId <= 0) {
            setStatus("Resolve the symbol first to get a product ID", true)
            return
        }
        if (leverage == null || leverage <= 0) {
            setStatus("Enter a valid leverage value", true)
            return
        }
        if (key.isBlank() || secret.isBlank()) {
            setStatus("Enter API key and secret first", true)
            return
        }
        setStatus("Applying ${leverage}x leverage on product $productId…", false)
        lifecycleScope.launch {
            val client = currentDeltaClient()
            val result = withContext(Dispatchers.IO) { client.setLeverage(productId, leverage) }
            if (result.success) {
                setStatus("Leverage set to ${leverage}x for product $productId", false)
            } else {
                setStatus("Failed to set leverage: ${result.error ?: "unknown error"}", true)
            }
        }
    }

    private fun saveSettings() {
        val backendUrl = inputBackendUrl.text.toString().trim()
        val deltaBaseUrl = inputDeltaBaseUrl.text.toString().trim().ifBlank { Prefs.DEFAULT_DELTA_BASE_URL }
        val apiKey = inputApiKey.text.toString().trim()
        val apiSecret = inputApiSecret.text.toString().trim()
        val symbol = inputSymbol.text.toString().trim().uppercase()
        val productId = inputProductId.text.toString().toIntOrNull() ?: 0
        val lot = inputLot.text.toString().toIntOrNull() ?: 1
        val leverage = inputLeverage.text.toString().toIntOrNull() ?: 10

        if (backendUrl.isBlank()) {
            setStatus("Backend URL is required", true)
            return
        }
        if (symbol.isBlank()) {
            setStatus("Product symbol is required", true)
            return
        }

        prefs.backendUrl = backendUrl
        prefs.deltaBaseUrl = deltaBaseUrl
        prefs.apiKey = apiKey
        prefs.apiSecret = apiSecret
        prefs.symbol = symbol
        prefs.productId = productId
        prefs.lot = lot
        prefs.leverage = leverage

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        setStatus("Saved locally. Pushing trading params to backend…", false)

        lifecycleScope.launch {
            val client = BackendApiClient(backendUrl)
            val pushed = withContext(Dispatchers.IO) { client.pushSettings(symbol, productId, lot, leverage) }
            if (pushed) {
                setStatus("Saved and applied on backend.", false)
            } else {
                setStatus(
                    "Saved locally. Backend didn't accept the update — it needs an /api/settings " +
                        "endpoint added, or edit config.json + restart the bot for now.",
                    true
                )
            }
        }
    }
}
