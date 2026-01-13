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
    val environment: SettoEnvironment,
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
    /** 결제자 지갑 주소 (서버에서 반환) */
    val fromAddress: String? = null,
    /** 결산 수신자 주소 (pool이 아닌 최종 수신자, 서버에서 반환) */
    val toAddress: String? = null,
    /** 결제 금액 (USD, 예: "10.00", 서버에서 반환) */
    val amount: String? = null,
    /** 체인 ID (예: 8453, 56, 900001, 서버에서 반환) */
    val chainId: Int? = null,
    /** 토큰 심볼 (예: "USDC", "USDT", 서버에서 반환) */
    val tokenSymbol: String? = null,
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
        debugLog("Initialized with environment: ${config.environment}")
    }

    /**
     * 결제 요청
     *
     * 항상 PaymentToken을 발급받아서 결제 페이지로 전달합니다.
     * - IdP Token 없음: Setto 로그인 필요
     * - IdP Token 있음: 자동로그인
     *
     * @param context Android Context
     * @param merchantId 머천트 ID
     * @param amount 결제 금액
     * @param idpToken IdP 토큰 (선택, 있으면 자동로그인)
     * @param callback 결제 결과 콜백
     */
    fun openPayment(
        context: Context,
        merchantId: String,
        amount: String,
        idpToken: String? = null,
        callback: (PaymentResult) -> Unit
    ) {
        val cfg = config ?: run {
            callback(PaymentResult(PaymentStatus.FAILED, error = "SDK not initialized"))
            return
        }

        debugLog("Requesting PaymentToken...")
        requestPaymentToken(context, merchantId, amount, idpToken, cfg, callback)
    }

    private fun requestPaymentToken(
        context: Context,
        merchantId: String,
        amount: String,
        idpToken: String?,
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
                    put("merchant_id", merchantId)
                    put("amount", amount)
                    if (idpToken != null) {
                        put("idp_token", idpToken)
                    }
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
                val paymentToken = json.getString("payment_token")

                // Fragment로 전달 (보안: 서버 로그에 남지 않음)
                val encodedToken = URLEncoder.encode(paymentToken, "UTF-8")
                val uri = Uri.parse("${cfg.environment.baseURL}/pay/wallet#pt=$encodedToken")

                debugLog("Opening payment page")
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
    suspend fun getPaymentInfo(merchantId: String, paymentId: String): Result<PaymentInfo> = withContext(Dispatchers.IO) {
        val cfg = config ?: return@withContext Result.failure(Exception("SDK not initialized"))

        try {
            val url = URL("${cfg.environment.baseURL}/api/external/payment/$paymentId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("X-Merchant-ID", merchantId)

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
        val fromAddress = uri.getQueryParameter("from_address")
        val toAddress = uri.getQueryParameter("to_address")
        val amount = uri.getQueryParameter("amount")
        val chainIdStr = uri.getQueryParameter("chain_id")
        val chainId = chainIdStr?.toIntOrNull()
        val tokenSymbol = uri.getQueryParameter("token_symbol")
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
            fromAddress = fromAddress,
            toAddress = toAddress,
            amount = amount,
            chainId = chainId,
            tokenSymbol = tokenSymbol,
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
