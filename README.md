# Setto SDK for Android

Setto Android SDK - Chrome Custom Tabs 기반 결제 연동 SDK

## 요구사항

- Android API 21+ (Android 5.0 Lollipop)
- Kotlin 1.8+

## 설치

### Gradle (Maven Central)

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.setto:sdk:0.1.0")
}
```

### JitPack

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.settopay-app:setto-android-sdk:0.1.0")
}
```

## 설정

### AndroidManifest.xml - Deep Link Activity 등록

```xml
<activity
    android:name="com.setto.sdk.SettoDeepLinkActivity"
    android:exported="true"
    android:launchMode="singleTask">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="mygame" />  <!-- 고객사 Scheme -->
    </intent-filter>
</activity>
```

### Cold Start 처리 (MainActivity)

앱이 종료된 상태에서 Deep Link로 실행되면, MainActivity에서 결과를 처리해야 합니다.

```kotlin
// MainActivity.kt
import com.setto.sdk.SettoSDK

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Cold Start 결제 결과 처리
        SettoSDK.extractResultFromIntent(intent)?.let { result ->
            handlePaymentResult(result)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        SettoSDK.extractResultFromIntent(intent)?.let { result ->
            handlePaymentResult(result)
        }
    }

    private fun handlePaymentResult(result: PaymentResult) {
        when (result.status) {
            PaymentStatus.SUCCESS -> {
                // 결제 성공 처리
                // 서버에서 결제 검증 필수!
            }
            PaymentStatus.CANCELLED -> {
                // 결제 취소 처리
            }
            PaymentStatus.FAILED -> {
                // 결제 실패 처리
            }
        }
    }
}
```

## 사용법

### SDK 초기화

```kotlin
import com.setto.sdk.SettoSDK
import com.setto.sdk.SettoEnvironment

class MyApplication : Application() {
    lateinit var settoSDK: SettoSDK

    override fun onCreate() {
        super.onCreate()

        settoSDK = SettoSDK(this)
        settoSDK.initialize(
            merchantId = "your-merchant-id",
            environment = SettoEnvironment.PRODUCTION,
            returnScheme = "mygame"
        )
    }
}
```

### 결제 요청

```kotlin
import com.setto.sdk.*
import java.math.BigDecimal

fun handlePayment() {
    val params = PaymentParams(
        orderId = "order-123",
        amount = BigDecimal("100.00"),
        currency = "USD"  // 선택
    )

    settoSDK.openPayment(params) { result ->
        when (result.status) {
            PaymentStatus.SUCCESS -> {
                Log.d("Payment", "결제 성공! TX ID: ${result.txId}")
                // 서버에서 결제 검증 필수!
            }
            PaymentStatus.CANCELLED -> {
                Log.d("Payment", "사용자가 결제를 취소했습니다.")
            }
            PaymentStatus.FAILED -> {
                Log.e("Payment", "결제 실패: ${result.error}")
            }
        }
    }
}
```

## API

### SettoSDK

#### `initialize(merchantId, environment, returnScheme)`

SDK를 초기화합니다.

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `merchantId` | `String` | 고객사 ID |
| `environment` | `SettoEnvironment` | `DEVELOPMENT` 또는 `PRODUCTION` |
| `returnScheme` | `String` | Custom URL Scheme |

#### `openPayment(params, onComplete)`

결제 창을 열고 결제를 진행합니다.

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `params` | `PaymentParams` | 결제 파라미터 |
| `onComplete` | `(PaymentResult) -> Unit` | 결제 완료 콜백 |

#### `handleResult(status, txId, paymentId, error): Boolean`

Deep Link 결과를 처리합니다. SettoDeepLinkActivity에서 내부적으로 호출됩니다.

#### `extractResultFromIntent(intent): PaymentResult?`

Intent에서 결제 결과를 추출합니다. Cold Start 시 MainActivity에서 사용합니다.

### PaymentParams

| 속성 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `orderId` | `String` | ✅ | 주문 ID |
| `amount` | `BigDecimal` | ✅ | 결제 금액 |
| `currency` | `String?` | | 통화 (기본: USD) |

### PaymentResult

| 속성 | 타입 | 설명 |
|------|------|------|
| `status` | `PaymentStatus` | `SUCCESS`, `FAILED`, `CANCELLED` |
| `txId` | `String?` | 블록체인 트랜잭션 해시 |
| `paymentId` | `String?` | Setto 결제 ID |
| `error` | `String?` | 에러 메시지 |

### SettoErrorCode

| 값 | 설명 |
|----|------|
| `USER_CANCELLED` | 사용자 취소 |
| `PAYMENT_FAILED` | 결제 실패 |
| `INSUFFICIENT_BALANCE` | 잔액 부족 |
| `TRANSACTION_REJECTED` | 트랜잭션 거부 |
| `NETWORK_ERROR` | 네트워크 오류 |
| `SESSION_EXPIRED` | 세션 만료 |
| `INVALID_PARAMS` | 잘못된 파라미터 |
| `INVALID_MERCHANT` | 유효하지 않은 고객사 |

## 보안 참고사항

1. **결제 결과는 서버에서 검증 필수**: SDK에서 반환하는 결과는 UX 피드백용입니다. 실제 결제 완료 여부는 고객사 서버에서 Setto API를 통해 검증해야 합니다.

2. **Custom URL Scheme 보안**: 다른 앱이 동일한 Scheme을 등록할 수 있으므로, 결제 결과는 반드시 서버에서 검증하세요.

## License

MIT
