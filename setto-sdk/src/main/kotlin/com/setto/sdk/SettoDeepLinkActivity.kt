package com.setto.sdk

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Setto Deep Link 수신 Activity
 *
 * 결제 완료 후 Deep Link를 수신하여 결과를 처리합니다.
 *
 * ## AndroidManifest.xml 설정
 * ```xml
 * <activity
 *     android:name="com.setto.sdk.SettoDeepLinkActivity"
 *     android:exported="true"
 *     android:launchMode="singleTask">
 *     <intent-filter>
 *         <action android:name="android.intent.action.VIEW" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *         <category android:name="android.intent.category.BROWSABLE" />
 *         <data android:scheme="mygame" />  <!-- 고객사 Scheme -->
 *     </intent-filter>
 * </activity>
 * ```
 *
 * ## Cold Start 처리
 *
 * 앱이 종료된 상태에서 Deep Link로 실행되면 콜백이 등록되어 있지 않습니다.
 * 이 경우 MainActivity로 Intent를 포워딩합니다.
 *
 * ```kotlin
 * // MainActivity.kt
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *
 *     // Cold Start 결제 결과 처리
 *     SettoSDK.extractResultFromIntent(intent)?.let { result ->
 *         handlePaymentResult(result)
 *     }
 * }
 * ```
 */
open class SettoDeepLinkActivity : Activity() {

    /**
     * MainActivity 클래스를 반환합니다.
     * 서브클래스에서 오버라이드하여 고객사의 MainActivity를 지정합니다.
     *
     * 기본값은 null이며, 이 경우 Cold Start 시 패키지의 런처 Activity를 찾습니다.
     */
    protected open val mainActivityClass: Class<out Activity>?
        get() = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            val status = uri.getQueryParameter("status")
            val txId = uri.getQueryParameter("txId")
            val paymentId = uri.getQueryParameter("paymentId")
            val error = uri.getQueryParameter("error")

            // 콜백 시도
            val handled = SettoSDK.handleResult(status, txId, paymentId, error)

            if (!handled) {
                // Cold Start: MainActivity로 Intent 포워딩
                forwardToMainActivity(status, txId, paymentId, error)
            }
        }

        finish()
    }

    private fun forwardToMainActivity(
        status: String?,
        txId: String?,
        paymentId: String?,
        error: String?
    ) {
        val targetClass = mainActivityClass ?: findLauncherActivity() ?: return

        val mainIntent = Intent(this, targetClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("setto_payment_status", status)
            putExtra("setto_payment_txId", txId)
            putExtra("setto_payment_paymentId", paymentId)
            putExtra("setto_payment_error", error)
        }
        startActivity(mainIntent)
    }

    private fun findLauncherActivity(): Class<out Activity>? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        val componentName = intent.component ?: return null

        return try {
            @Suppress("UNCHECKED_CAST")
            Class.forName(componentName.className) as? Class<out Activity>
        } catch (e: ClassNotFoundException) {
            null
        }
    }
}
