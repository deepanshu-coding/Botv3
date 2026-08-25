package com.deltaatrbot.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal signed REST client for the Delta Exchange v2 API, called directly
 * from the phone (independent of the Go backend) so Settings can test API
 * keys, resolve a symbol to its product_id, and apply leverage immediately.
 *
 * Signing follows the documented scheme exactly:
 *   signature = hex(HMAC_SHA256(secret, method + timestamp + requestPath + queryString + body))
 * where timestamp is unix seconds as a string, generated fresh per request
 * (signatures are only valid ~5 seconds).
 */
class DeltaApiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val apiSecret: String
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    data class ApiResult(val success: Boolean, val statusCode: Int, val body: JSONObject?, val error: String?)

    private fun sign(method: String, path: String, queryString: String, body: String): Pair<String, String> {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val prehash = method + timestamp + path + queryString + body
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(apiSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val sigBytes = mac.doFinal(prehash.toByteArray(Charsets.UTF_8))
        val hex = sigBytes.joinToString("") { "%02x".format(it) }
        return hex to timestamp
    }

    /** GET request. queryString must already include the leading "?" if non-empty, e.g. "?symbol=BTCUSD". */
    fun get(path: String, queryString: String = "", authed: Boolean = true): ApiResult {
        return execute("GET", path, queryString, "", authed)
    }

    fun post(path: String, jsonBody: String, authed: Boolean = true): ApiResult {
        return execute("POST", path, "", jsonBody, authed)
    }

    private fun execute(method: String, path: String, queryString: String, body: String, authed: Boolean): ApiResult {
        return try {
            val url = baseUrl.trimEnd('/') + path + queryString
            val builder = Request.Builder().url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "delta-atr-bot-android/1.0")

            if (authed) {
                val (signature, timestamp) = sign(method, path, queryString, body)
                builder.addHeader("api-key", apiKey)
                builder.addHeader("signature", signature)
                builder.addHeader("timestamp", timestamp)
            }

            when (method) {
                "GET" -> builder.get()
                "POST" -> builder.post(body.toRequestBody(jsonMedia))
                "DELETE" -> if (body.isNotEmpty()) builder.delete(body.toRequestBody(jsonMedia)) else builder.delete()
                else -> throw IllegalArgumentException("unsupported method $method")
            }

            http.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val json = try { JSONObject(text) } catch (e: Exception) { null }
                val success = json?.optBoolean("success", false) ?: false
                val errCode = json?.optJSONObject("error")?.optString("code")
                ApiResult(success = success, statusCode = resp.code, body = json, error = if (!success) (errCode ?: "http_${resp.code}") else null)
            }
        } catch (e: Exception) {
            ApiResult(success = false, statusCode = 0, body = null, error = e.message ?: "network error")
        }
    }

    // --- convenience wrappers for endpoints this app needs ---

    /** Public, unauthenticated. Resolves symbol -> product metadata (incl. numeric id). */
    fun getProductBySymbol(symbol: String): ApiResult = get("/v2/products/$symbol", authed = false)

    /** Auth required. Confirms API key/secret work end-to-end. */
    fun getWalletBalances(): ApiResult = get("/v2/wallet/balances")

    /** Auth required. POST /v2/products/{product_id}/orders/leverage with {"leverage": "N"} */
    fun setLeverage(productId: Int, leverage: Int): ApiResult {
        val body = JSONObject().put("leverage", leverage.toString()).toString()
        return post("/v2/products/$productId/orders/leverage", body)
    }
}
