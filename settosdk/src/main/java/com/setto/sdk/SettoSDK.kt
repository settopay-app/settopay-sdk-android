package com.setto.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// MARK: - Types

enum class SettoEnvironment(val baseURL: String) {
    DEV("https://dev-wallet.settopay.com"),
    PROD("https://wallet.settopay.com")
}

data class SettoConfig(
    val merchantId: String,
    val environment: SettoEnvironment,
    val idpToken: String? = null,  // IdP 토큰 (있으면 자동로그인)
    val debug: Boolean = false
)

enum class PaymentStatus {
    SUCCESS,
    FAILED,
    CANCELLED
}

data class PaymentResult(
    val status: PaymentStatus,
    val paymentId: String? = null,
    val txHash: String? = null,
    val error: String? = null
)

data class PaymentInfo(
    val paymentId: String,
    val status: String,
    val amount: String,
    val currency: String,
    val txHash: String? = null,
    val createdAt: Long,
    val completedAt: Long? = null
)

// MARK: - SDK

object SettoSDK {

    private var config: SettoConfig? = null
    private var pendingCallback: ((PaymentResult) -> Unit)? = null

    /**
     * SDK 초기화
     */
    fun initialize(config: SettoConfig) {
        this.config = config
        debugLog("Initialized with merchantId: ${config.merchantId}")
    }

    /**
     * 결제 요청
     *
     * IdP Token 유무에 따라 자동로그인 여부가 결정됩니다.
     * - IdP Token 없음: Setto 로그인 필요
     * - IdP Token 있음: PaymentToken 발급 후 자동로그인
     */
    fun openPayment(
        context: Context,
        amount: String,
        orderId: String? = null,
        callback: (PaymentResult) -> Unit
    ) {
        val cfg = config ?: run {
            callback(PaymentResult(PaymentStatus.FAILED, error = "SDK not initialized"))
            return
        }

        if (cfg.idpToken != null) {
            // IdP Token 있음 → PaymentToken 발급 → Fragment로 전달
            debugLog("Requesting PaymentToken...")
            requestPaymentToken(context, amount, orderId, cfg, callback)
        } else {
            // IdP Token 없음 → Query param으로 직접 전달
            val uri = Uri.parse("${cfg.environment.baseURL}/pay/wallet").buildUpon()
                .appendQueryParameter("merchant_id", cfg.merchantId)
                .appendQueryParameter("amount", amount)
                .apply { orderId?.let { appendQueryParameter("order_id", it) } }
                .build()

            debugLog("Opening payment with Setto login: $uri")
            openCustomTabs(context, uri, callback)
        }
    }

    private fun requestPaymentToken(
        context: Context,
        amount: String,
        orderId: String?,
        cfg: SettoConfig,
        callback: (PaymentResult) -> Unit
    ) {
        Thread {
            try {
                val url = URL("${cfg.environment.baseURL}/api/external/payment/token")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val body = JSONObject().apply {
                    put("merchantId", cfg.merchantId)
                    put("amount", amount)
                    orderId?.let { put("orderId", it) }
                    put("idpToken", cfg.idpToken)
                }

                connection.outputStream.bufferedWriter().use { it.write(body.toString()) }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    debugLog("PaymentToken request failed: $responseCode")
                    android.os.Handler(context.mainLooper).post {
                        callback(PaymentResult(PaymentStatus.FAILED, error = "Token request failed: $responseCode"))
                    }
                    return@Thread
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val paymentToken = json.getString("paymentToken")

                // Fragment로 전달 (보안: 서버 로그에 남지 않음)
                val encodedToken = URLEncoder.encode(paymentToken, "UTF-8")
                val uri = Uri.parse("${cfg.environment.baseURL}/pay/wallet#pt=$encodedToken")

                debugLog("Opening payment with auto-login")
                android.os.Handler(context.mainLooper).post {
                    openCustomTabs(context, uri, callback)
                }
            } catch (e: Exception) {
                debugLog("PaymentToken request error: ${e.message}")
                android.os.Handler(context.mainLooper).post {
                    callback(PaymentResult(PaymentStatus.FAILED, error = "Network error"))
                }
            }
        }.start()
    }

    /**
     * 결제 상태 조회
     */
    suspend fun getPaymentInfo(paymentId: String): Result<PaymentInfo> = withContext(Dispatchers.IO) {
        val cfg = config ?: return@withContext Result.failure(Exception("SDK not initialized"))

        try {
            val url = URL("${cfg.environment.baseURL}/api/external/payment/$paymentId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-Merchant-ID", cfg.merchantId)

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP error: $responseCode"))
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            val info = PaymentInfo(
                paymentId = json.getString("payment_id"),
                status = json.getString("status"),
                amount = json.getString("amount"),
                currency = json.getString("currency"),
                txHash = json.optString("tx_hash", null),
                createdAt = json.getLong("created_at"),
                completedAt = if (json.has("completed_at")) json.getLong("completed_at") else null
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * URL Scheme 콜백 처리
     * Activity에서 onNewIntent 시 호출
     */
    fun handleCallback(intent: Intent): Boolean {
        val uri = intent.data ?: return false

        // setto-{merchantId}://callback?status=success&payment_id=xxx&tx_hash=xxx
        if (!uri.scheme.orEmpty().startsWith("setto-") || uri.host != "callback") {
            return false
        }

        val statusString = uri.getQueryParameter("status") ?: ""
        val paymentId = uri.getQueryParameter("payment_id")
        val txHash = uri.getQueryParameter("tx_hash")
        val errorMsg = uri.getQueryParameter("error")

        val status = when (statusString) {
            "success" -> PaymentStatus.SUCCESS
            "failed" -> PaymentStatus.FAILED
            else -> PaymentStatus.CANCELLED
        }

        val result = PaymentResult(
            status = status,
            paymentId = paymentId,
            txHash = txHash,
            error = errorMsg
        )

        pendingCallback?.invoke(result)
        pendingCallback = null

        debugLog("Callback received: $statusString")
        return true
    }

    /**
     * 초기화 여부 확인
     */
    val isInitialized: Boolean
        get() = config != null

    // MARK: - Private Methods

    private fun openCustomTabs(
        context: Context,
        uri: Uri,
        callback: (PaymentResult) -> Unit
    ) {
        pendingCallback = callback

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.launchUrl(context, uri)
    }

    private fun debugLog(message: String) {
        if (config?.debug == true) {
            println("[SettoSDK] $message")
        }
    }
}
