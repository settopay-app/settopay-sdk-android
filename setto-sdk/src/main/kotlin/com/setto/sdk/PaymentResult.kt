package com.setto.sdk

/**
 * 결제 상태
 */
enum class PaymentStatus {
    SUCCESS,
    FAILED,
    CANCELLED;

    companion object {
        fun fromString(value: String?): PaymentStatus = when (value) {
            "success" -> SUCCESS
            "cancelled" -> CANCELLED
            else -> FAILED
        }
    }
}

/**
 * 결제 결과
 */
data class PaymentResult(
    /** 결제 상태 */
    val status: PaymentStatus,

    /** 블록체인 트랜잭션 해시 (성공 시) */
    val txId: String? = null,

    /** Setto 결제 ID */
    val paymentId: String? = null,

    /** 에러 메시지 (실패 시) */
    val error: String? = null
)

/**
 * 결제 요청 파라미터
 */
data class PaymentParams(
    /** 주문 ID */
    val orderId: String,

    /** 결제 금액 */
    val amount: java.math.BigDecimal,

    /** 통화 (기본: USD) */
    val currency: String? = null
)
