package com.setto.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URLEncoder

/**
 * Setto Android SDK
 *
 * Chrome Custom Tabs를 사용하여 wallet.settopay.com과 연동합니다.
 *
 * ## 사용 예시
 * ```kotlin
 * // 초기화
 * val sdk = SettoSDK(context)
 * sdk.initialize(
 *     merchantId = "merchant-123",
 *     environment = SettoEnvironment.PRODUCTION,
 *     returnScheme = "mygame"
 * )
 *
 * // 결제 요청
 * sdk.openPayment(
 *     params = PaymentParams(orderId = "order-456", amount = BigDecimal("100.00"))
 * ) { result ->
 *     when (result.status) {
 *         PaymentStatus.SUCCESS -> println("결제 성공: ${result.txId}")
 *         PaymentStatus.CANCELLED -> println("결제 취소")
 *         PaymentStatus.FAILED -> println("결제 실패: ${result.error}")
 *     }
 * }
 * ```
 */
class SettoSDK(private val context: Context) {

    private var merchantId: String = ""
    private var returnScheme: String = ""
    private var environment: SettoEnvironment = SettoEnvironment.PRODUCTION

    companion object {
        private var onCompleteCallback: ((PaymentResult) -> Unit)? = null

        /**
         * Deep Link 결과 처리
         *
         * DeepLinkActivity에서 호출됩니다.
         * Cold Start 시에는 callback이 null이므로, Intent 포워딩이 필요합니다.
         *
         * @return true: 콜백 처리됨, false: Cold Start (Intent 포워딩 필요)
         */
        fun handleResult(status: String?, txId: String?, paymentId: String?, error: String?): Boolean {
            val result = PaymentResult(
                status = PaymentStatus.fromString(status),
                txId = txId,
                paymentId = paymentId,
                error = error
            )

            return if (onCompleteCallback != null) {
                onCompleteCallback?.invoke(result)
                onCompleteCallback = null
                true
            } else {
                false
            }
        }

        /**
         * Intent에서 결제 결과 추출
         *
         * Cold Start 시 MainActivity에서 사용
         */
        fun extractResultFromIntent(intent: Intent?): PaymentResult? {
            val status = intent?.getStringExtra("setto_payment_status") ?: return null
            return PaymentResult(
                status = PaymentStatus.fromString(status),
                txId = intent.getStringExtra("setto_payment_txId"),
                paymentId = intent.getStringExtra("setto_payment_paymentId"),
                error = intent.getStringExtra("setto_payment_error")
            )
        }
    }

    /**
     * SDK 초기화
     */
    fun initialize(
        merchantId: String,
        environment: SettoEnvironment,
        returnScheme: String
    ) {
        this.merchantId = merchantId
        this.environment = environment
        this.returnScheme = returnScheme
    }

    /**
     * 결제 창을 열고 결제를 진행합니다.
     */
    fun openPayment(
        params: PaymentParams,
        onComplete: (PaymentResult) -> Unit
    ) {
        onCompleteCallback = onComplete

        val url = buildPaymentUrl(params)

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()

        customTabsIntent.launchUrl(context, Uri.parse(url))
    }

    private fun buildPaymentUrl(params: PaymentParams): String {
        val encodedMerchantId = URLEncoder.encode(merchantId, "UTF-8")
        val encodedOrderId = URLEncoder.encode(params.orderId, "UTF-8")
        val encodedScheme = URLEncoder.encode(returnScheme, "UTF-8")

        var url = "${environment.baseUrl}/pay"
        url += "?merchantId=$encodedMerchantId"
        url += "&orderId=$encodedOrderId"
        url += "&amount=${params.amount}"
        url += "&returnScheme=$encodedScheme"

        params.currency?.let { currency ->
            val encodedCurrency = URLEncoder.encode(currency, "UTF-8")
            url += "&currency=$encodedCurrency"
        }

        return url
    }
}
