package com.deltaatrbot.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Plain SharedPreferences-backed settings store.
 *
 * NOTE: API key/secret are stored here in plaintext-on-disk SharedPreferences
 * (standard Android app-private storage, not world-readable, but not
 * encrypted-at-rest either). For production use, consider swapping this for
 * androidx.security's EncryptedSharedPreferences.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("delta_atr_bot_prefs", Context.MODE_PRIVATE)

    var backendUrl: String
        get() = sp.getString(KEY_BACKEND_URL, "").orEmpty()
        set(value) = sp.edit().putString(KEY_BACKEND_URL, value.trimEnd('/')).apply()

    var deltaBaseUrl: String
        get() = sp.getString(KEY_DELTA_BASE_URL, DEFAULT_DELTA_BASE_URL).orEmpty()
        set(value) = sp.edit().putString(KEY_DELTA_BASE_URL, value.trimEnd('/')).apply()

    var apiKey: String
        get() = sp.getString(KEY_API_KEY, "").orEmpty()
        set(value) = sp.edit().putString(KEY_API_KEY, value.trim()).apply()

    var apiSecret: String
        get() = sp.getString(KEY_API_SECRET, "").orEmpty()
        set(value) = sp.edit().putString(KEY_API_SECRET, value.trim()).apply()

    var symbol: String
        get() = sp.getString(KEY_SYMBOL, "BTCUSD").orEmpty()
        set(value) = sp.edit().putString(KEY_SYMBOL, value.trim().uppercase()).apply()

    var productId: Int
        get() = sp.getInt(KEY_PRODUCT_ID, 0)
        set(value) = sp.edit().putInt(KEY_PRODUCT_ID, value).apply()

    var lot: Int
        get() = sp.getInt(KEY_LOT, 1)
        set(value) = sp.edit().putInt(KEY_LOT, value).apply()

    var leverage: Int
        get() = sp.getInt(KEY_LEVERAGE, 10)
        set(value) = sp.edit().putInt(KEY_LEVERAGE, value).apply()

    fun isConfigured(): Boolean = backendUrl.isNotBlank()

    companion object {
        const val DEFAULT_DELTA_BASE_URL = "https://api.india.delta.exchange"

        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_DELTA_BASE_URL = "delta_base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_SYMBOL = "symbol"
        private const val KEY_PRODUCT_ID = "product_id"
        private const val KEY_LOT = "lot"
        private const val KEY_LEVERAGE = "leverage"
    }
}
