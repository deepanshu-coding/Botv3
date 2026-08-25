package com.deltaatrbot.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for the Go backend's local dashboard API (see internal/api/server.go
 * in the backend project): /api/status, /api/wallet, /api/positions,
 * /api/orders, /api/logs. All unauthenticated - the backend is expected to
 * run on localhost or the user's own trusted local network.
 */
class BackendApiClient(private val baseUrl: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    private fun getRaw(path: String): String? {
        return try {
            val req = Request.Builder().url(baseUrl.trimEnd('/') + path).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getStatus(): JSONObject? = getRaw("/api/status")?.let { runCatching { JSONObject(it) }.getOrNull() }
    fun getWallet(): JSONArray? = getRaw("/api/wallet")?.let { runCatching { JSONArray(it) }.getOrNull() }
    fun getPositions(): JSONArray? = getRaw("/api/positions")?.let { runCatching { JSONArray(it) }.getOrNull() }
    fun getOrders(): JSONArray? = getRaw("/api/orders")?.let { runCatching { JSONArray(it) }.getOrNull() }
    fun getLogs(): JSONArray? = getRaw("/api/logs")?.let { runCatching { JSONArray(it) }.getOrNull() }

    /**
     * Best-effort push of trading settings to the backend so the running bot
     * picks them up without a restart. This expects an `/api/settings` POST
     * endpoint on the backend (symbol, product_id, lot, leverage as JSON) -
     * that endpoint isn't part of the base backend yet, so this call may
     * fail/404. Callers should treat a false return as "saved locally only",
     * not a hard error.
     */
    fun pushSettings(symbol: String, productId: Int, lot: Int, leverage: Int): Boolean {
        return try {
            val body = JSONObject()
                .put("product_symbol", symbol)
                .put("product_id", productId)
                .put("order_size_contracts", lot)
                .put("leverage", leverage)
                .toString()
            val req = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/api/settings")
                .post(body.toRequestBody(jsonMedia))
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
