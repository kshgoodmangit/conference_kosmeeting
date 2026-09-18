# 카드 결제 PG사 추가 구현 가이드

## 1. 목적

이 문서는 학회별로 서로 다른 카드 결제 PG사를 연결하면서도 공통 결제 흐름을 재사용하기 위한 구현 기준이다.

PG사를 추가하거나 기존 카드 결제 코드를 변경할 때는 이 문서를 먼저 읽고 아래 구조를 유지한다. 학회가 달라져도 같은 PG사를 사용하는 경우에는 소스를 복사하지 않고 MID, 인증키, URL 등 설정값만 교체하여 재사용한다.

## 2. 핵심 원칙

1. `PaymentGatewayProperties`에는 모든 PG사에 공통인 설정만 둔다.
2. PG사 전용 설정은 `payment/{provider}/` 패키지의 별도 `@ConfigurationProperties` 클래스로 만든다.
3. PG사별 요청 생성과 응답 해석은 `PaymentGatewayAdapter` 구현체로 분리한다.
4. `PaymentGatewayRegistry`를 직접 수정해 분기문을 추가하지 않는다. Spring Bean으로 등록된 어댑터가 자동 수집되게 한다.
5. 브라우저 SDK가 필요한 PG사는 프론트엔드 클라이언트를 provider 코드로 등록한다.
6. Secret Key, 서명키, API 비밀번호는 프론트엔드 응답에 포함하지 않는다.
7. 운영 결제 성공은 브라우저 콜백만으로 확정하지 않는다. PG 서버 통지, 승인 조회 또는 서명 검증 결과를 서버에서 확인한다.
8. 주문번호, 결제금액, 통화, 회원 및 사전등록 대상은 서버의 원본 데이터와 대조한다.
9. 결제 결과 저장은 같은 거래가 여러 번 통지되어도 한 번만 반영되도록 멱등성을 보장한다.

## 3. 현재 결제 구조

### 공통 백엔드

```text
src/main/java/com/bjworld21/congress/
├── config/
│   └── PaymentGatewayProperties.java
├── payment/
│   ├── PaymentGatewayAdapter.java
│   ├── PaymentGatewayRegistry.java
│   ├── PaymentGatewayService.java
│   ├── PaymentRoute.java
│   └── paygate/
│       ├── PayGateProperties.java
│       └── PayGatePaymentGatewayAdapter.java
└── controller/
    └── PaymentTestController.java
```

- `PaymentGatewayProperties`: `enabled`, `provider` 공통 설정
- `PaymentGatewayAdapter`: PG사 구현체의 공통 계약
- `PaymentGatewayRegistry`: `providerCode()` 기준으로 구현체 검색
- `PaymentGatewayService`: YAML에 선택된 provider의 어댑터 실행
- `PaymentRoute`: 국내 카드와 해외 카드 등 공통 결제 경로

### 프론트엔드

```text
frontend/src/
├── components/PaymentTestPage.tsx
└── payment/paymentGatewayClient.ts
```

- 백엔드에서 내려준 `provider`에 맞는 브라우저 클라이언트를 선택한다.
- PG 스크립트 로드, 결제창 호출, 브라우저 콜백 수집만 담당한다.
- Secret Key를 이용한 서명이나 최종 승인 확정은 백엔드에서 처리한다.

## 4. provider 코드 규칙

provider 코드는 YAML, 백엔드, 프론트엔드에서 동일해야 한다.

```text
YAML app.payment.provider
    = PaymentGatewayAdapter.providerCode()
    = 프론트엔드 PaymentGatewayClient.providerCode
```

코드는 소문자 영문과 숫자, 하이픈을 사용한다.

```text
paygate
nicepay
inicis
tosspayments
```

`PaymentGatewayRegistry`는 대소문자를 정규화하지만 처음부터 소문자로 작성한다.

## 5. 새 PG사 추가 절차

아래 예시는 provider 코드가 `newpg`인 경우다.

### 5.1 YAML 설정 추가

`src/main/resources/application.yaml`의 `app.payment.providers` 아래에 PG사 전용 설정을 추가한다.

```yaml
app:
  payment:
    enabled: ${PAYMENT_ENABLED:false}
    provider: ${PAYMENT_PROVIDER:newpg}
    providers:
      newpg:
        api-url: ${NEWPG_API_URL:https://api.example.com}
        client-id: ${NEWPG_CLIENT_ID:}
        secret-key: ${NEWPG_SECRET_KEY:}
        return-url: ${NEWPG_RETURN_URL:}
        notify-url: ${NEWPG_NOTIFY_URL:}
        routes:
          domestic-card:
            merchant-id: ${NEWPG_DOMESTIC_MID:}
            currency: KRW
          international-card:
            merchant-id: ${NEWPG_INTERNATIONAL_MID:}
            currency: USD
```

설정 기준:

- 비밀값에는 실제 값을 기본값으로 넣지 않는다.
- 테스트 URL과 운영 URL을 분리한다.
- 브라우저 이동용 `return-url`과 서버 통지용 `notify-url`을 구분한다.
- 학회별 차이는 우선 환경변수나 프로필 YAML에서 관리한다.
- `parameters` 같은 자유 형식 Map은 단순 요청 옵션에만 사용하고 인증 관련 값은 명시적인 필드로 선언한다.

### 5.2 PG사 전용 설정 클래스 생성

`payment/newpg/NewPgProperties.java`를 생성한다.

```java
package com.bjworld21.congress.payment.newpg;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.payment.providers.newpg")
public class NewPgProperties {
    private String apiUrl = "";
    private String clientId = "";
    private String secretKey = "";
    private String returnUrl = "";
    private String notifyUrl = "";
}
```

PG사 전용 필드를 `PaymentGatewayProperties`에 추가하지 않는다.

### 5.3 백엔드 어댑터 구현

`payment/newpg/NewPgPaymentGatewayAdapter.java`를 생성하고 `PaymentGatewayAdapter`를 구현한다.

```java
@Component
public class NewPgPaymentGatewayAdapter implements PaymentGatewayAdapter {
    private final PaymentGatewayProperties commonProperties;
    private final NewPgProperties newPgProperties;

    public NewPgPaymentGatewayAdapter(
            PaymentGatewayProperties commonProperties,
            NewPgProperties newPgProperties
    ) {
        this.commonProperties = commonProperties;
        this.newPgProperties = newPgProperties;
    }

    @Override
    public String providerCode() {
        return "newpg";
    }

    @Override
    public PaymentGatewaySummaryResponse summary() {
        // 활성화 상태와 국내·국외 설정 완료 여부를 반환한다.
    }

    @Override
    public PaymentCheckoutResponse prepareCheckout(PaymentCheckoutRequest request) {
        // 서버 원본 주문 검증 후 PG 요청에 필요한 공개 값만 반환한다.
    }
}
```

어댑터 구현 시 확인사항:

1. 전체 결제 활성화 여부는 공통 `app.payment.enabled`로만 제어한다.
2. PG사가 현재 제공하는 API URL을 설정에서 읽고 소스에 하드코딩하지 않는다.
3. 국내·국외 경로와 통화를 명시적으로 검증한다.
4. 원화 금액의 소수점 허용 여부 등 PG사 규칙을 검증한다.
5. MID 전체 값과 비밀값을 설정 요약 API에 노출하지 않는다.
6. 클라이언트가 보낸 금액을 그대로 신뢰하지 않는다. 실제 운영 API에서는 등록비 원본을 서버에서 조회한다.

`@Component`로 등록하면 `PaymentGatewayRegistry`가 자동으로 수집한다. 새 PG사 추가를 위해 Registry에 `if`, `switch` 또는 직접 생성 코드를 추가하지 않는다.

### 5.4 프론트엔드 클라이언트 추가

PG사가 JavaScript SDK나 결제창 호출을 요구하는 경우 `frontend/src/payment/paymentGatewayClient.ts`에 클라이언트를 등록한다.

```ts
const newPgClient: PaymentGatewayClient = {
    providerCode: 'newpg',
    async start(session, form, screen) {
        // 공개 SDK 로드 및 결제창 호출
        // 결제창의 브라우저 결과를 PaymentResult로 변환
    }
};

const clients = new Map<string, PaymentGatewayClient>([
    [payGateClient.providerCode, payGateClient],
    [newPgClient.providerCode, newPgClient]
]);
```

PG사 코드가 길어지면 다음 구조로 분리하고 Registry 파일에서 조합한다.

```text
frontend/src/payment/
├── paymentGatewayClient.ts
├── paygateClient.ts
└── newPgClient.ts
```

서버 API 방식만 사용하는 PG사는 불필요한 브라우저 SDK를 추가하지 않는다.

### 5.5 결과 수신 API 구현

운영 결제에는 PG사 규격에 맞는 결과 처리 엔드포인트가 필요하다.

```text
GET 또는 POST /api/payments/{provider}/return
POST        /api/payments/{provider}/notify
```

- `return`: 결제 완료 후 사용자의 브라우저가 돌아오는 화면용
- `notify`: PG 서버가 결제 결과를 전달하는 서버 간 통신용

결과 처리 순서:

1. PG사 서명, 해시 또는 인증값 검증
2. PG 거래번호와 상점 주문번호 확인
3. DB의 주문금액, 결제통화, MID와 대조
4. PG 승인 조회 API가 있으면 서버에서 최종 상태 재확인
5. 이미 처리한 거래인지 확인
6. 결제 상태와 원본 응답을 트랜잭션으로 저장
7. PG사가 요구하는 성공 응답 형식 반환

브라우저의 성공 응답만으로 `pre_registrations` 등의 결제 상태를 변경하지 않는다.

## 6. 학회별 설정 재사용

동일한 PG사를 사용하는 학회는 소스를 복사하지 않고 설정만 교체한다.

```powershell
java `
  -DPAYMENT_ENABLED=true `
  -DPAYMENT_PROVIDER=paygate `
  -DPAYGATE_DOMESTIC_MID=학회별국내MID `
  -DPAYGATE_INTERNATIONAL_MID=학회별해외MID `
  -jar congress.jar
```

설정이 많으면 `application-{학회}.yaml` 프로필 파일을 사용하고 Secret Key만 환경변수로 주입한다.

## 7. 필수 테스트

PG사 추가 시 최소한 다음 테스트를 작성한다.

1. `@ConfigurationProperties` 바인딩 테스트
2. PG사 API URL 설정 바인딩 테스트
3. 국내 카드 요청값과 통화 테스트
4. 해외 카드 요청값과 통화 테스트
5. 필수 MID 또는 인증값 누락 테스트
6. 금액 형식과 허용 통화 검증 테스트
7. 설정 요약에서 MID와 Secret이 노출되지 않는지 확인
8. 결과 서명 검증 성공 및 실패 테스트
9. 동일한 결과 통지를 여러 번 받아도 중복 처리되지 않는 테스트
10. 컨트롤러 요청 검증 및 오류 응답 테스트

검증 명령은 프로젝트 전용 Java 17 실행기를 사용한다.

```powershell
.\gradlew17.bat test
```

프론트엔드 클라이언트를 변경했으면 다음도 확인한다.

```powershell
cd frontend
npm run build
npx eslint src/payment src/components/PaymentTestPage.tsx
```

실제 PG 테스트 MID가 준비되어 있으면 테스트 카드 또는 PG사가 제공한 테스트 수단으로 브라우저 결제창, return URL, 서버 notify까지 확인한다. 운영 MID로 자동화 테스트를 실행하지 않는다.

## 8. 완료 체크리스트

- [ ] 공통 설정 클래스에 PG 전용 필드를 추가하지 않았다.
- [ ] provider 전용 Properties와 Adapter를 별도 패키지에 만들었다.
- [ ] YAML, 백엔드, 프론트엔드 provider 코드가 일치한다.
- [ ] 테스트와 운영 URL 및 인증값이 분리되어 있다.
- [ ] 비밀값이 API 응답, 로그, 프론트엔드 번들에 포함되지 않는다.
- [ ] 국내·국외 카드와 통화 경로가 검증된다.
- [ ] return URL과 서버 notify의 역할이 구분되어 있다.
- [ ] 서버에서 거래번호, 주문번호, 금액, 통화, 서명을 검증한다.
- [ ] 결제 결과 처리가 멱등성을 가진다.
- [ ] 설정 바인딩, 어댑터, 결과 처리 테스트가 통과한다.
- [ ] `application.yaml`과 운영 배포 설정 예시를 갱신했다.
