package com.setto.sdk

/**
 * Setto SDK 에러 코드
 */
enum class SettoErrorCode(val code: String) {
    // 사용자 액션
    USER_CANCELLED("USER_CANCELLED"),

    // 결제 실패
    PAYMENT_FAILED("PAYMENT_FAILED"),
    INSUFFICIENT_BALANCE("INSUFFICIENT_BALANCE"),
    TRANSACTION_REJECTED("TRANSACTION_REJECTED"),

    // 네트워크/시스템
    NETWORK_ERROR("NETWORK_ERROR"),
    SESSION_EXPIRED("SESSION_EXPIRED"),

    // 파라미터
    INVALID_PARAMS("INVALID_PARAMS"),
    INVALID_MERCHANT("INVALID_MERCHANT");

    companion object {
        fun fromString(code: String?): SettoErrorCode {
            return entries.find { it.code == code } ?: PAYMENT_FAILED
        }
    }
}

/**
 * Setto SDK 에러
 */
class SettoException(
    val errorCode: SettoErrorCode,
    override val message: String? = null
) : Exception(message ?: errorCode.code) {

    companion object {
        /**
         * Deep Link error 파라미터로부터 에러 생성
         */
        fun fromErrorCode(code: String?): SettoException {
            val errorCode = SettoErrorCode.fromString(code)
            val message = when (errorCode) {
                SettoErrorCode.USER_CANCELLED -> "사용자가 결제를 취소했습니다."
                SettoErrorCode.INSUFFICIENT_BALANCE -> "잔액이 부족합니다."
                SettoErrorCode.TRANSACTION_REJECTED -> "트랜잭션이 거부되었습니다."
                SettoErrorCode.NETWORK_ERROR -> "네트워크 오류가 발생했습니다."
                SettoErrorCode.SESSION_EXPIRED -> "세션이 만료되었습니다."
                SettoErrorCode.INVALID_PARAMS -> "잘못된 파라미터입니다."
                SettoErrorCode.INVALID_MERCHANT -> "유효하지 않은 고객사입니다."
                else -> code
            }
            return SettoException(errorCode, message)
        }
    }
}
