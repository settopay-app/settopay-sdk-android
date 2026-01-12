package com.setto.sdk

/**
 * Setto SDK 환경 설정
 */
enum class SettoEnvironment(val baseUrl: String) {
    /** 개발 환경 */
    DEVELOPMENT("https://dev-wallet.settopay.com"),

    /** 프로덕션 환경 */
    PRODUCTION("https://wallet.settopay.com")
}
