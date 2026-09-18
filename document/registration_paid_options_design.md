# 등록 유료 옵션 구현 설계

작성일: 2026년 9월 9일  
대상: bjworld21_cms_congress  
상태: 구현 전 설계안. 이 문서에서 제안한 테이블·API·정책은 아직 적용하지 않았다.

기본 등록비에 식사·연회·워크숍·동반자·자료 등의 선택 항목을 추가한다. **기존 `pre_registrations`를 신청의 중심으로 유지하고, 옵션 카탈로그·신청 변경본·결제 및 환불 원장을 분리**한다. 옵션 선택부터 차액 정산까지 같은 도메인 서비스를 사용하며 관리자와 참가자 화면은 해당 서비스를 서로 다른 권한으로 호출한다.

단순히 등록 테이블에 연회비·식사비 컬럼을 추가하는 방식으로는 회차별 옵션 종류, 복수 수량, 재고, 추가결제와 부분환불을 처리하기 어렵다. 선택한 옵션을 별도 행으로 저장하고 신청 당시 명칭·단가·면제액을 보존하는 방식을 사용한다.

## 1 범위와 현재 코드의 연결 지점

| 범위 | 관련 요구 ID | 설계에 포함하는 내용 |
|---|---|---|
| 옵션 상품 | REG-02 | 유료·무료, 수량·정원, 판매기간, 중복선택 제약 |
| 신청 후 변경 | PAY-04~05 | 등록 유지, 옵션 추가·삭제·수량 변경, 차액 결제·부분환불 |
| 무료 대상 | FREE-01~04 | 동일 선택 화면, 무료 수량·금액 한도, 초과분 결제와 자격 연계 |
| 결제·출력 | PAY-01~03·06~07, REG-08 | 서버 결제확정, 거래 연결, 최종 금액·환불 이력, 옵션 통계·증빙 |
| 운영 | COM-05, SEC-01·03 | 변경 사유·행위자·이력, 관리자 권한, 중복 처리 방지 |

기존 학회 회원 로그인 연동, 초대메일 발송 엔진, 전체 회차 데이터 이관은 별도 과제다. 이 설계는 이들이 연결할 계약과 의존관계를 정의한다. 이들 없이 관리자 기능부터 개발할 수 있지만 참가자 셀프서비스 완료로 표시해서는 안 된다.

| 현재 소스 | 현재 동작 | 변경 방향 |
|---|---|---|
| [PreRegistrationAdminService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/PreRegistrationAdminService.java) | 기본요금·등록구분 수정, DB 상태 변경형 결제취소 | 금액 변경은 공통 변경 서비스로 위임. 단순 메모 수정만 별도 유지 |
| [PreRegistrationRepository.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/repository/PreRegistrationRepository.java) | 등록 한 건에 단일 결제 정보 | 변경본·복수 결제·환불 집계로 확장 |
| [PreRegistrationManagementPage.tsx](D:/dev/java/intellij/bjworld21_cms_congress/frontend/src/components/PreRegistrationManagementPage.tsx) | 관리자 목록·상세·등록 | 옵션 요약, 변경 모달, 결제·환불 이력 탭 추가 |
| [RegistrationFeeService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/RegistrationFeeService.java) | 등록구분별 기본요금 | 기본요금 계산에 재사용. 옵션 가격은 별도 카탈로그에서 계산 |
| [SocietyMemberService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/SocietyMemberService.java) | 로컬 회원 명단·등록비 매핑 | 무료 자격 확인의 한 입력으로 사용. 회원 여부만으로 모든 옵션 무료 처리 금지 |
| [PaymentGatewayAdapter.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/payment/PaymentGatewayAdapter.java) | checkout 요청 생성 계약 | 서버 검증·거래 조회·환불 능력과 실행 계약 확장 |
| [PaymentGatewayService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/payment/PaymentGatewayService.java) | 현재 선택 PG로 checkout 준비 | 최초 결제는 설정 PG, 조회·환불은 원거래에 기록된 PG·상점 설정으로 실행 |
| [paymentGatewayClient.ts](D:/dev/java/intellij/bjworld21_cms_congress/frontend/src/payment/paymentGatewayClient.ts) | PG 브라우저 클라이언트 | 서버가 발행한 주문 세션으로 결제창 실행. 금액 확정 권한 없음 |

## 2 설계 기본값

다음은 원문에 수치·세부 규칙이 없는 부분에 대한 **제안 기본값**이다. 정책으로 저장하여 회차별 변경이 가능하도록 한다.

| 항목 | 제안 |
|---|---|
| 회차 | 옵션·요금·등록에 `congressSeq` 명시. 현재의 ‘최신 학회’ 조회를 결제·변경 판단에 사용하지 않음 |
| 옵션 최소 단위 | ‘9월 10일 점심’, ‘9월 11일 점심’, ‘연회’, ‘워크숍 A’를 각각 별도 옵션으로 등록 |
| 통화 | 한 등록·변경·원장은 한 통화. KRW와 USD를 합산·자동 환전하지 않음 |
| 견적 유효시간 | 5분. 견적 조회만으로 좌석을 잡지 않음 |
| 결제 대기 정원 확보 | 변경 제출 시 15분 확보. 서버 결제 조회 결과와 조합하여 만료 처리 |
| 변경 동시성 | 등록 한 건에 활성 변경 한 건만 허용. 미확정 결제·환불이 있으면 다음 금액 변경 대기 |
| 가격 변경 | 기존 구매 수량은 구매 당시 단가 유지. 추가 수량은 변경 견적 당시 단가 |
| 수량 감소 | 동일 옵션의 가장 최근 구매 수량부터 차감. 차감 대상 단가·할인도 그 구매분 기준 |
| 무료 옵션 | 가격이 0이어도 기간·정원·중복선택 규칙 동일 적용 |
| 옵션 취소 | 기본은 변경 가능 기간 내 미사용 항목의 전액 반환. 이미 사용했거나 마감된 옵션은 일반 사용자 변경 불가 |
| 관리자의 예외 | 별도 권한·사유·감사기록으로 기간 제한 예외 허용. 정원은 임의 우회하지 않고 정원 변경을 먼저 수행 |
| 삭제 | 참조 이력이 있는 상품·신청·거래는 물리 삭제하지 않음. 판매중지·논리 취소로 관리 |

취소 수수료나 사용 후 부분 반환 정책이 필요한 회차는 별도 정책 버전을 추가한다. 최초 구현에 임의 비율을 하드코딩하지 않는다. 아래 차액 예시는 취소수수료 0인 기본 정책에 해당한다.

## 3 데이터 모델

다음 내용은 논리 설계다. 실제 구현 시 MariaDB 10.6 기준으로 테이블은 snake_case, 컬럼은 camelCase, 모든 PK는 `seq BIGINT AUTO_INCREMENT`를 적용한다. 모든 테이블·컬럼에 COMMENT와 생성·수정시각을 둔다. 금액은 `DECIMAL(12,2)`·Java `BigDecimal`을 사용하고 합계의 범위 초과도 검사한다.

### 3.1 기존 테이블 보완

| 테이블 | 추가·변경 필드 | 의미 |
|---|---|---|
| `pre_registrations` | `congressSeq`, `versionNo`, `currentRevisionSeq`, `activeRevisionSeq`, `registrationChannel`, `financialMigrationStatus` | 회차, 낙관적 잠금, 확정 내역과 처리 중 내역 분리, ONLINE·ADMIN·ONSITE 구분, 이관 신뢰 상태 |
| `registration_categories` | `congressSeq` | 회차별 등록구분. 코드 유일성을 `(congressSeq, categoryCode)`로 변경 |
| `registration_fee_rates` | 기존 category FK 유지 | 회차가 지정된 등록구분을 통해 회차별 요금 조회 |
| `pre_registrations` 기존 금액 필드 | `feeAmount`는 기본 등록비 의미 유지 | 옵션 포함 총액으로 조용히 의미 변경하지 않음. 새 응답에는 아래 명시적 금액 필드 제공 |

`congressSeq`는 현재 관리자가 생성·수정하는 `congress_settings.seq`를 등록 영역의 회차 식별자로 사용한다. 과거 설정 행이 동일 행사의 버전인지 별도 회차인지 먼저 분류한다. 기존 데이터에 최신 seq를 일괄 지정하지 않는다. 추후 전사 회차 모델로 옮겨도 명시적 FK를 통해 전환할 수 있게 한다.

조회 응답의 금액은 `baseAmount`, `optionsGrossAmount`, `discountAmount`, `payableAmount`, `capturedAmount`, `refundedAmount`, `netPaidAmount`, `outstandingAmount`, `refundDueAmount`로 구분한다. 가급적 원장에서 집계하고, 캐시는 같은 트랜잭션에서 갱신하며 대사 작업으로 검증한다.

### 3.2 옵션 카탈로그

| 신규 테이블 | 주요 컬럼 | 책임 |
|---|---|---|
| `registration_options` | `congressSeq`, `optionCode`, `nameKo`, `nameEn`, `descriptionKo`, `descriptionEn`, `optionType`, `eventDate`, `startsAt`, `endsAt`, `saleStartsAt`, `saleEndsAt`, `changeEndsAt`, `minQuantity`, `maxQuantity`, `capacity`, `enabled`, `exclusiveGroupCode`, `versionNo`, `sortOrder` | 상품·기간·정원·선택 규칙 |
| `registration_option_prices` | `optionSeq`, `currency`, `unitAmount`, `effectiveFrom`, `effectiveTo`, `versionNo` | 통화별 버전 가격. 판매 이후 가격 행을 덮어쓰지 않고 새 버전 생성 |
| `registration_option_eligibilities` | `optionSeq`, `categorySeq` | 등록구분별 선택 가능 조건. 행이 없으면 해당 회차 전체 구분에 허용 |

`capacity = NULL`은 무제한, 0은 판매 가능한 자리가 0개라는 의미다. 인원 단위 상품은 한 수량을 한 좌석으로 정의한다. 동일 `exclusiveGroupCode` 안에서는 양수 수량 옵션을 최대 하나만 허용한다. 시간 중첩이 금지된 워크숍은 관리자가 같은 그룹으로 묶는다. 복잡한 다중 시간 충돌은 별도 확장 과제다.

가격 적용 구간은 `[effectiveFrom, effectiveTo)`로 해석하고 같은 옵션·통화의 기간이 겹치지 않게 옵션 행 잠금 안에서 검증한다. 시간은 UTC 저장·행사 시간대 표시를 원칙으로 하고, 기존 날짜형 기간과의 경계 변환을 서비스에서 명시한다.

### 3.3 신청 변경본과 정원

| 신규 테이블 | 주요 컬럼 | 책임 |
|---|---|---|
| `registration_revisions` | `registrationSeq`, `revisionNo`, `previousRevisionSeq`, `baseVersionNo`, `state`, `currency`, `baseAmount`, `optionsGrossAmount`, `discountAmount`, `payableAmount`, `deltaAmount`, `policySnapshotJson`, `actorType`, `actorSeq`, `reason`, `requestKey`, `requestHash`, `expiresAt`, `appliedAt` | 최초 신청 또는 변경 요청 한 건과 계산 결과 |
| `registration_revision_items` | `revisionSeq`, `optionSeq`, `purchaseLotKey`, `priceSeq`, `nameKoSnapshot`, `nameEnSnapshot`, `eventDateSnapshot`, `unitAmount`, `quantity`, `grossAmount`, `discountAmount`, `netAmount`, `benefitSnapshotJson` | 해당 변경본이 목표로 하는 전체 옵션 내역. 가격이 다른 구매분은 같은 옵션이어도 별도 행 |
| `registration_option_reservations` | `optionSeq`, `registrationSeq`, `revisionSeq`, `quantity`, `state`, `expiresAt` | HELD·CONFIRMED·RELEASED·EXPIRED 정원 사용 및 확보 기록 |

변경본 항목은 **변경 차이만이 아닌 전체 목표 장바구니**를 저장한다. `currentRevisionSeq`가 가리키는 APPLIED 변경본이 현재 유효 신청 내역이다. 승인된 변경본·항목은 불변이며 다음 변경본으로 교체한다. 상품 이름·가격을 바꾸어도 과거 증빙은 바뀌지 않는다.

예: 같은 연회 1장을 50,000원에 산 뒤 가격 인상 후 1장을 추가하면 `(50,000원 × 1)`과 `(60,000원 × 1)` 두 구매분으로 저장한다. 화면에는 수량 2, 합계 110,000원과 상세 단가를 표시한다.

정원 계산은 `CONFIRMED 수량 + 유효 HELD 수량`이다. 이력을 남기는 EXPIRED·RELEASED 행은 합산하지 않는다. 수량 감소 시에는 해당 등록의 CONFIRMED 예약 수량을 줄이고, 증가 시 HELD를 거쳐 CONFIRMED로 바꾼다. 변경 이력의 원본은 불변 변경본이 담당하며 예약 테이블은 운영 상태다.

### 3.4 결제 원장

| 신규 테이블 | 주요 컬럼 | 책임 |
|---|---|---|
| `registration_payments` | `registrationSeq`, `revisionSeq`, `orderNo`, `provider`, `merchantConfigRef`, `paymentMethod`, `route`, `currency`, `requestedAmount`, `capturedAmount`, `state`, `providerTransactionId`, `verifiedAt`, `requestKey`, `retryOfSeq` | 결제 시도별 주문·검증 결과. 재시도와 원주문 연결 |
| `registration_refunds` | `registrationSeq`, `revisionSeq`, `paymentSeq`, `amount`, `currency`, `state`, `providerRefundId`, `requestKey`, `reason`, `actorSeq`, `verifiedAt` | 특정 원결제에 대한 환불 한 건. 복수 결제에서 환불하면 여러 행 |
| `registration_money_allocations` | `registrationSeq`, `revisionSeq`, `paymentSeq`, `refundSeq`, `revisionItemSeq`, `componentType`, `entryType`, `amount`, `eventKey` | 수납·반환·항목 간 재배분의 불변 명세. 기본요금과 옵션별 정산 근거 |
| `registration_financial_events` | `provider`, `merchantConfigRef`, `providerEventId`, `paymentSeq`, `refundSeq`, `eventType`, `verificationState`, `processingState`, `payloadDigest`, `receivedAt` | 중복·역순 콜백 수신함과 처리 결과. 민감 원문 무제한 저장 금지 |

`registration_payments`와 `registration_refunds`의 검증된 성공 금액이 현금 원장이다. `money_allocations`는 항목별 귀속을 표현한다. CAPTURE·REFUND 배분 합계는 각각 성공 거래금액과 일치해야 한다. 같은 금액 옵션 교환 시 RECLASSIFY_OUT·RECLASSIFY_IN으로 귀속만 바꾸고 두 합계는 상쇄한다. 배분 조정으로 실제 수납액을 증가시키지 않는다.

결제는 옵션 항목 하나에 종속시키지 않고 등록 전체에 귀속한다. 따라서 옵션 교체 시 차액만 결제할 수 있다. 부분환불은 원결제별 잔여 환불 가능액 내에서 최신 결제부터 배분하는 기본 정책을 사용하고, 그 결과를 배분 명세에 남긴다. 환불 대기액도 원결제 한도에서 예약해 중복 환불을 방지한다.

### 3.5 무료 자격과 작업 기록

| 신규 테이블 | 주요 컬럼 | 책임 |
|---|---|---|
| `registration_benefit_policies` | `congressSeq`, `policyCode`, `versionNo`, `baseFeeWaived`, `optionBudgetAmount`, `currency`, `enabled` | 기본요금 면제와 옵션 금액 한도 정책 |
| `registration_benefit_rules` | `policySeq`, `optionSeq`, `freeQuantity`, `budgetEligible`, `priority` | 옵션별 무료 수량과 한도 적용 순서 |
| `registration_benefit_grants` | `congressSeq`, `policySeq`, `memberSeq`, `registrationSeq`, `verificationMethod`, `verificationState`, `verifiedAt`, `expiresAt`, `invitationRef` | 확인된 대상자에게 부여된 자격과 사용 등록 연결 |
| `registration_change_events` | `registrationSeq`, `revisionSeq`, `actorType`, `actorSeq`, `eventType`, `beforeJson`, `afterJson`, `reason`, `createdAt` | 가격·신청·자격·수동 처리의 감사기록 |
| `registration_outbox` | `aggregateType`, `aggregateSeq`, `eventType`, `dedupeKey`, `state`, `attemptCount`, `nextAttemptAt` | 결제 준비·환불·안내·재조회의 재시도 가능한 작업 |

초대 대상 파일·인증코드 발행·메일 발송은 별도 FREE-04 및 공통 발송 과제에서 구현하고 `benefit_grants`로 연결한다. 자격 없이 0원으로 보내는 클라이언트 요청은 인정하지 않는다. 기본 구현은 등록당 활성 정책 하나이며 여러 정책 중복 적용은 지원하지 않는다.

### 3.6 제약과 인덱스

- 유일키: 옵션 `(congressSeq, optionCode)`, 변경본 `(registrationSeq, revisionNo)`, 변경 요청 `(registrationSeq, requestKey)`, 주문 `orderNo`, 환불 요청 `requestKey`, 외부 거래 `(provider, merchantConfigRef, providerTransactionId)`.
- 공급자 이벤트 키도 `(provider, merchantConfigRef, providerEventId)`로 유일하게 둔다. 공급자가 안정적 이벤트 ID를 주지 않으면 어댑터에서 문서화된 결정적 키를 만든다.
- 자격의 사용 등록을 고정하고 같은 초대 자격이 복수 등록에서 중복 사용되지 않게 유일키·행 잠금으로 보호한다.
- 검색 인덱스: 옵션 `(congressSeq, enabled, sortOrder)`, 변경 `(registrationSeq, state)`, 예약 `(optionSeq, state, expiresAt)`, 미처리 환불 `(state, createdAt)`, outbox `(state, nextAttemptAt)`.
- 수량 양수, 금액 음수 금지, 할인액 ≤ 총액, 기간 선후 조건을 DB CHECK와 서비스에서 검증한다. 배분 이벤트 금액은 양수로 저장하고 방향을 `entryType`으로 표현한다.
- 회차·통화·옵션·등록 소속 일치는 서비스에서 반드시 검사한다. 삭제 FK는 원장·변경본 보존을 위해 RESTRICT를 기본으로 한다.
- `activeRevisionSeq`는 등록 행 잠금 안에서만 지정·해제한다. 등록별 활성 변경 하나를 애플리케이션의 검사만으로 경쟁 상태에 맡기지 않는다.

## 4 계산 규칙

```text
기본 등록비 = 최초 등록 시 서버가 확정한 등록구분별 요금
옵션 총액 = Σ 구매분 단가 × 수량
면제액 = 기본요금 면제액 + 옵션별 무료수량 금액 + 옵션 금액한도 적용액
목표 납부액 = 기본 등록비 + 옵션 총액 - 면제액
실납부액 = 검증된 수납 합계 - 검증된 환불 합계
미수금 = max(현재 확정 납부액 - 실납부액, 0)
반환할 금액 = max(실납부액 - 현재 확정 납부액, 0)
```

변경 전 정산이 완료되어 있고 다른 결제·환불이 없을 때만 `차액 = 목표 납부액 - 현재 확정 납부액`을 사용한다. **환불 대기나 미확인 거래가 있는 상태에서 단순 총액 차이를 계산하여 다음 변경을 받지 않는다.** 최초 신청은 이전 확정 납부액 0에서 시작한다.

무료 자격은 먼저 옵션별 무료 수량을 적용하고, 남은 유료 부분에 정책 priority·optionSeq·구매분 순서로 금액 한도를 배분한다. 기본요금 면제와 옵션 무료는 별개다. 무료 수량의 금액을 다시 금액 한도에서 중복 차감하지 않는다. 금액 한도는 등록별 현재 장바구니 한도이며 취소 시 복원한다.

현재 장바구니가 바뀌면 같은 정책 버전으로 면제 배분을 다시 계산하므로, 다른 항목으로 혜택이 이동할 수 있다. 변경 미리보기에 이 차이를 보여주고 이전·이후 배분을 모두 저장한다. 정책 수정은 신규 자격부터 적용하고 기존 자격은 이전 정책 버전을 유지한다.

기존 옵션을 유지하는 수량은 과거 단가, 신규·증가 구매분만 최신 단가를 적용한다. 0원 옵션도 스냅샷을 저장한다. 통화별 소수 자릿수를 검증하고 반올림으로 가격 오류를 숨기지 않는다. USD 센트 단위·KRW 정수 원 단위를 기본 지원하되 실제 PG 지원과 대조한다.

| 예시 | 이전 | 변경 | 처리 |
|---|---:|---:|---|
| 기본 100,000원에 연회 추가 | 100,000 | 150,000 | 50,000원 추가결제 후 옵션 확정 |
| 결제한 연회 삭제 | 150,000 | 100,000 | 연회 해제·50,000원 환불 요청 |
| 50,000원 옵션을 70,000원 옵션으로 교환 | 150,000 | 170,000 | 20,000원 결제 후 교환. 신규 좌석을 그동안 확보 |
| 같은 가격 옵션 교환 | 150,000 | 150,000 | 결제 없이 좌석과 항목별 금액 귀속 변경 |
| 기본요금 면제, 연회 유료 | 0 | 50,000 | 연회비만 결제 |
| 옵션 한도 30,000원, 연회 50,000원 | 0 | 20,000 | 한도 적용 후 20,000원 결제 |

금액은 예시이며 실제 학회 요금이 아니다.

## 5 상태와 처리 흐름

### 5.1 상태를 분리한다

| 대상 | 상태 |
|---|---|
| 신청 변경본 | QUOTED → PAYMENT_PENDING 또는 APPLIED, EXPIRED, CANCELLED, RECONCILIATION_REQUIRED |
| 결제 | CREATED → PENDING → CAPTURED 또는 FAILED·CANCELLED, 결과 불명은 UNKNOWN |
| 환불 | REQUESTED → PROCESSING → SUCCEEDED 또는 FAILED, 결과 불명은 UNKNOWN |
| 정원 확보 | HELD → CONFIRMED 또는 EXPIRED·RELEASED |

변경본 APPLIED는 신청 내역 반영을 뜻한다. 환불 성공을 뜻하지 않는다. 감소 변경은 신청 내역이 APPLIED이고 환불은 PROCESSING일 수 있으므로 화면에 ‘옵션 취소 완료·환불 처리 중’을 각각 표시한다. 기존 `paymentStatus` 하나로 이를 모두 표현하지 않는다.

### 5.2 공통 견적과 변경 제출

1. 서버가 인증 사용자·등록 소유자·회차·버전·현재 정산 상태를 검사한다.
2. 목표 옵션과 수량, 기존 구매분, 가격·자격·마감·중복 규칙을 검증한다. 서버가 견적 변경본 QUOTED를 생성한다.
3. 사용자는 이전·이후 내역, 면제액, 추가 납부 또는 반환액을 확인한다.
4. 제출 시 등록 행과 관련 옵션 행을 잠근다. `baseVersionNo`, 자격·카탈로그 버전, 견적 만료를 재확인한다. 값이 달라지면 재견적을 요구하고 금액을 조용히 바꾸지 않는다.
5. 증가 수량만 HELD로 확보하고 `activeRevisionSeq`를 지정한다. 요청 키·payload hash가 동일하면 기존 결과 반환, 같은 키로 다른 내용을 보내면 409 반환한다.

### 5.3 차액이 양수인 변경

1. 이전 APPLIED 내역과 CONFIRMED 좌석은 유지한다. 신규 증가분만 HELD다.
2. 서버 원본 견적 금액으로 주문번호를 발행하고 결제 준비 작업을 저장한다.
3. DB 트랜잭션 종료 후 PG checkout을 준비하고 결제창을 연다. PG 통신 중 DB 잠금을 유지하지 않는다.
4. 서명·승인 조회 등 PG의 검증 수단으로 결제 결과를 확인한다. 금액·통화·주문·상점·사용자 및 등록 연결을 대조한다.
5. 잠금 안에서 결제 반영·금액 배분·변경본 APPLIED·현재 변경본 교체·정원 전환·버전 증가·감사·알림 outbox를 한 번만 기록한다.
6. 결제 실패 또는 사용자가 결제창을 닫은 경우, 서버에서 실제 성공 여부를 확인하기 전 실패 확정하지 않는다. 확정 실패면 HELD만 해제하며 이전 신청은 보존한다.

한 변경본의 동시에 유효한 결제 시도도 하나로 제한한다. 기존 주문이 UNKNOWN이면 새 주문을 발행하지 않는다. 서로 다른 시도가 실제로 모두 승인되는 예외에서는 모든 승인액을 원장에 기록하되 변경본은 한 번만 적용하고 초과 승인분은 별도 보상환불한다. 이미 APPLIED라는 이유로 추가 승인 통지를 버리지 않는다.

### 5.4 차액 0 또는 음수인 변경

- 0원: 같은 트랜잭션에서 목표 옵션의 정원·자격을 검증하고 즉시 APPLIED 처리한다. 무료 신규 등록도 동일하다.
- 음수: 반환 대상 원결제와 환불 가능한 잔액을 먼저 확인한다. 새 옵션이 포함되면 해당 정원도 확보한다. 목표 내역 적용·삭제 옵션 정원 반환·환불 REQUESTED·outbox를 하나의 DB 트랜잭션으로 확정한다.
- 감소 변경이 적용되면 옵션 이용 권리는 종료된다. 환불 지연 때문에 삭제된 좌석을 계속 점유하지 않는다. 환불 실패를 이유로 이미 해제한 옵션을 자동 복구하지 않는다.
- 환불이 모두 SUCCEEDED 되거나 명시적으로 정산 해결될 때까지 `activeRevisionSeq`를 유지하여 다음 금액 변경을 막는다.
- 원거래에 부분취소가 없으면 자동 환불 가능으로 표시하지 않는다. 지원되는 별도 반환 수단을 관리자가 선택하고 외부 지급이 확인된 뒤 성공 기록한다. 지원 방식을 확정할 수 없으면 환불 요청 단계에서 사유를 안내한다.

환불 FAILED는 확인된 실패, UNKNOWN은 성공 여부를 아직 모르는 상태다. UNKNOWN 재조회와 FAILED 재시도를 분리하며, 결과가 미확정인 원환불을 남겨둔 채 새로운 환불키로 지급하지 않는다. 환불 처리 도중 진행 변경 취소 요청도 내역만 역으로 덮어쓰지 않고 정산 완료 후 새 변경으로 처리한다.

### 5.5 장시간 미응답과 늦은 결제

만료 작업은 PG에서 취소·미결제를 확정할 수 있으면 HELD를 해제한다. 조회가 불가능해 UNKNOWN인 동안은 즉시 신규 주문을 허용하지 않고 정원 점유·확인 필요 상태를 노출한다. 운영상 정원 확보 상한을 두고 해제하는 경우 늦은 성공은 다음처럼 처리한다.

1. 만료된 주문의 결제도 검증된 수납으로 반드시 기록한다. 웹훅을 무시하지 않는다.
2. 기존 신청 버전이 같고 정원·기한·자격 조건이 유효하며 재확보 가능할 때만 변경을 적용한다.
3. 그 외에는 변경 미적용을 유지하고 보상 전액환불 요청을 만든다. RECONCILIATION_REQUIRED 상태에서 관리자에게 알리고 다음 변경을 막는다.
4. 공급자 중복 통지, outbox 재시작, 결제창 재시도에도 수납·정원·환불이 중복 반영되지 않아야 한다.

## 6 정원과 동시성

정원은 화면에서 보여주는 잔여 수량으로 판정하지 않는다. 제출·결제 확정·만료·옵션 정원 수정이 **모두 같은 옵션 행을 잠그는 규약**을 따른다.

```text
트랜잭션 시작
  등록 행 잠금
  관련 옵션 행을 optionSeq 오름차순으로 잠금
  유효한 CONFIRMED + HELD를 DB에서 현재 읽기로 집계
  증가 수량만큼 수용 가능한지 검사
  변경본 활성화와 HELD 저장
커밋
```

MariaDB 트랜잭션에서 오래된 스냅샷 집계를 읽지 않도록 이 경로는 READ_COMMITTED 또는 명시적 현재 읽기를 사용한다. 실제 구현에서 선택한 격리수준으로 경쟁 테스트한다. 만료·관리자 정원 수정 등 모든 쓰기 경로가 옵션 잠금을 거쳐야 이 규약이 성립한다.

정원이 1 남은 옵션에 두 명이 동시에 요청하면 한 명만 확보한다. 기존 정원보다 작은 capacity 변경은 거부한다. 무제한 옵션도 신청당 최대 수량·기간을 검사한다. 신규 옵션이 포함된 교환은 신규 정원을 얻지 못하면 전체 변경을 적용하지 않는다.

## 7 PG와 내부 원장 연결

[PG 연동 가이드](D:/dev/java/intellij/bjworld21_cms_congress/document/payment_gateway_integration_guide.md)를 따른다. 현재 어댑터는 checkout 준비만 제공하므로 실제 정산 기능은 추가 구현이 필요하다. PayGate의 특정 취소 API·부분취소 지원을 이 설계에서 가정하지 않는다. 구현 착수 시 해당 계약·경로의 공식 규격으로 확인한다.

공통 계약에 다음 역할을 추가한다. 처음에는 기본 구현이 ‘지원 안 함’을 반환하게 하여 기존 결제 테스트 동작을 유지한다.

```java
GatewayCapabilities capabilities(); // 거래 조회·부분취소·전액취소 등
VerifiedPayment verifyPayment(PaymentVerificationCommand command);
VerifiedPayment queryPayment(PaymentLookupCommand command);
RefundResult refund(RefundCommand command);
RefundResult queryRefund(RefundLookupCommand command);
```

- 구현체는 계속 `payment/{provider}/`에 두고 Registry가 자동 수집한다. 공통 설정 클래스에 PG별 비밀값을 넣지 않는다.
- 주문번호는 등록 결제 서비스가 DB에 먼저 발행한다. 운영 checkout 어댑터가 주문번호를 임의로 새로 만들지 않도록 요청 계약을 보완한다.
- 원거래에 provider·route·통화·merchantConfigRef를 남긴다. 설정 PG를 바꾸어도 과거 거래 조회·환불은 원래 상점 설정으로 수행한다. 설정 참조는 비밀값 자체가 아니다.
- 브라우저 callback은 결과 화면 이동·상태 조회용이다. 서버 검증 전 성공 표시·정원 확정·영수증 발급 금지.
- 외부 호출 타임아웃은 UNKNOWN으로 기록하고 원 주문번호·환불키로 조회한다. 응답이 없다고 같은 금액의 새 결제나 환불을 바로 전송하지 않는다.
- 다수 원결제에 나눠 환불할 때 일부 성공·일부 실패를 허용하는 상태 모델을 사용한다. 성공분을 다시 실행하지 않는다.
- 계좌 입금과 수동 반환도 동일 원장에 별도 paymentMethod로 기록하며 확인 권한·증빙 참조·일시를 남긴다. PG 결과를 수동으로 위조하지 않는다.
- notify 경로만 공급자 검증 후 처리하도록 설계한다. 서버 간 통지에 필요한 CSRF 예외는 해당 경로에만 적용하고 일반 신청 API의 CSRF는 유지한다.

## 8 서비스와 API

### 8.1 서비스 책임

| 서비스 제안명 | 책임 |
|---|---|
| `RegistrationOptionService` | 카탈로그·가격 버전·기간·통화·정원 정책 관리 |
| `RegistrationBenefitService` | 자격 확인·정책 버전 고정·무료 한도 배분 |
| `RegistrationQuoteService` | 서버 가격 계산, 기존 구매분 보존, 견적 생성 |
| `RegistrationRevisionService` | 신청별 단일 변경, 버전·잠금·적용·취소 |
| `RegistrationCapacityService` | 정원 확보·확정·해제·만료 |
| `RegistrationPaymentService` | 주문 발행·검증·배분·상태 반영 |
| `RegistrationRefundService` | 원결제별 환불 한도 확보·요청·결과 반영 |
| `RegistrationSettlementService` | 원장 집계·미수·반환금·정합성 대사·증빙 데이터 |

컨트롤러가 여러 서비스를 개별 호출하여 절반만 저장하지 않도록 변경 적용은 `RegistrationRevisionService`의 명시적 트랜잭션 경계로 묶는다. 외부 통신 실행은 outbox 작업자가 담당하고 결과를 다시 트랜잭션으로 반영한다.

### 8.2 관리자 API

아래 경로는 신규 제안이다. 목록은 검색·정렬·페이징·동일 조건 전체 통계를 함께 반환한다.

| 메서드·경로 | 역할 |
|---|---|
| `GET /api/admin/registration-options?congressSeq=...` | 옵션 목록·통계 |
| `POST /api/admin/registration-options` | 옵션 생성 |
| `PUT /api/admin/registration-options/{seq}` | 버전 포함 정책 수정·판매중지 |
| `POST /api/admin/registration-options/{seq}/prices` | 새 가격 버전 생성 |
| `GET /api/admin/pre-registrations/{seq}/options` | 확정 내역·정산·활성 변경 조회 |
| `POST /api/admin/pre-registrations/{seq}/option-quotes` | 목표 옵션 전체 목록으로 견적 |
| `POST /api/admin/pre-registrations/{seq}/option-changes` | quoteSeq·versionNo·reason으로 제출 |
| `GET /api/admin/pre-registrations/{seq}/financial-history` | 변경·결제·환불·배분 이력 |
| `POST /api/admin/registration-refunds/{seq}/retry` | 확정 실패만 권한·사유 확인 후 재시도 |
| `POST /api/admin/registration-options/export` | 필터 반영 XLSX, 개인정보 포함 시 사유·감사 |

진행 중 주문 취소는 별도 `POST .../option-changes/{changeSeq}/cancel`로 제안한다. 서버 검증 결과 이미 결제됐다면 단순 취소가 아니라 환불 흐름으로 전환한다.

### 8.3 참가자 API

```text
GET  /api/me/registrations/{seq}/options
POST /api/me/registrations/{seq}/option-quotes
POST /api/me/registrations/{seq}/option-changes
GET  /api/me/registrations/{seq}/option-changes/{changeSeq}
POST /api/me/registrations/{seq}/payments/{paymentSeq}/checkout
GET  /api/me/registrations/{seq}/financial-history
```

`/api/me/**` 전용 회원 세션 검사와 등록 소유권 검사를 추가한다. 현재 관리자·심사위원 interceptor를 사용자 인증으로 대체 사용하지 않는다. 모든 child ID가 URL의 등록에 속하는지 검사하고 반환할 PG 정보에서도 비밀값을 제거한다.

최초 등록 API도 `categorySeq`, 옵션·수량, 동의 정보를 받아 같은 계산 서비스를 이용한다. 클라이언트의 `feeAmount`·단가·면제액은 운영 결제의 입력으로 사용하지 않는다. 기존 관리자 수동 금액 조정은 별도 권한과 조정 사유로 분리한다.

### 8.4 요청과 응답 예시

```json
{
  "versionNo": 4,
  "items": [
    {"optionSeq": 101, "quantity": 1},
    {"optionSeq": 102, "quantity": 2}
  ]
}
```

배열은 최종 목표 선택이다. 빠진 옵션은 삭제 요청으로 해석하고 UI에 이를 분명히 표시한다. 이전 구매분 차감은 서버가 결정한다. 빈 배열은 전체 옵션 삭제다.

견적 응답에는 `quoteSeq`, `expiresAt`, `baseVersionNo`, `before`, `after`, `deltaAmount`, `nextAction`, 품목별 증감·면제·가격 변동 안내를 포함한다. 금액은 JSON 문자열로 반환하고 JavaScript 실수 합산으로 최종액을 정하지 않는다. `nextAction`은 PAY·APPLY·REFUND 중 하나다.

제출은 `Idempotency-Key` 헤더와 quoteSeq를 받는다. 중복 키 충돌·버전 변경·정원 부족은 409, 기한·선택 규칙 위반은 422, 소유권 위반은 403 또는 일관된 404로 응답한다. PG 비동기 처리 중에는 202와 조회 주소를 반환한다. 오류 코드는 안정적으로 유지하고 화면 설명은 공통 토스트로 보여준다.

## 9 관리자와 참가자 화면

### 9.1 신규 관리자 메뉴

‘등록관리’ 하위에 **등록 옵션 관리**를 추가한다. `App.tsx` 지원 메뉴·렌더링과 `menuIcons.ts`를 함께 연결한다.

- 순서: 페이지 헤더 → 요약 통계 → 검색 조건 → 목록 → 페이지 이동.
- 조건: 회차, 옵션명·코드, 유형, 사용 여부, 판매기간.
- 통계: 검색 결과 옵션 수·판매 중 옵션 수·마감 옵션 수. 금액은 통화별로 분리한다.
- 목록: 옵션명·일시·금액·정원·확정 수량·결제 대기 수량·잔여·판매 상태.
- 편집: 기본정보, 기간, 금액, 수량·정원, 등록구분 제한, 중복선택 그룹. 실제 선택자가 있으면 물리 삭제 대신 판매중지.

### 9.2 기존 사전등록 상세 확장

‘기본정보 / 옵션 / 결제·환불 이력 / 변경이력’으로 나눈다. 옵션 탭에는 현재 확정 옵션과 진행 중 변경을 구분해 표시한다.

변경 모달은 ‘현재 수량 → 변경 수량’, 단가·면제액·변경 후 총액·추가 납부 또는 반환액을 보여준다. 성공 시 목록·상세·통계를 함께 갱신한다. 환불이 비동기면 ‘환불 요청 완료’로 안내하고 완료라고 표시하지 않는다.

관리자도 같은 가격·정원 검사에 따른다. 참가자의 카드 결제를 대신 성공으로 처리하는 버튼을 두지 않는다. 관리자 추가결제 요청은 참가자가 본인 페이지에서 결제하도록 하고, 계좌 입금은 별도 확인 절차를 사용한다.

### 9.3 참가자 화면

최초 등록과 옵션 변경에서 같은 옵션 선택 UI·금액 요약을 재사용한다. 무료 대상자에게도 동일 상품을 보여주고 ‘무료 적용 1개’, ‘한도 적용 30,000원’, ‘추가 납부 20,000원’처럼 실제 부담액을 표시한다.

품절·기간 종료·선택 불가 이유를 표시한다. 결제 중 이탈 후 재방문하면 새로운 주문을 즉시 만들지 않고 진행 중 주문 상태를 보여준다. 관리자 React 스타일을 공개 홈페이지에 그대로 이식하지 않고 공개 페이지 레이아웃과 인증 구조에 맞춘다.

관리자 화면은 [UI 가이드](D:/dev/java/intellij/bjworld21_cms_congress/document/admin_ui_design_guide.md)를 적용한다. 등록 emerald·저장 blue, 공통 `useConfirm`, `onNotify`, 개인정보 엑셀 사유·감사, 다크모드·모바일·드래그 모달을 재사용한다.

## 10 통계 증빙과 감사

- 옵션별 신청 **인원**은 등록 distinct 수, **수량**은 항목 수량 합으로 구분한다. 동반자 옵션 수량 3을 신청자 3명으로 계산하지 않는다.
- 확정 내역은 currentRevision만 집계한다. 과거 변경본 전체를 합산하면 매출·인원이 중복된다.
- 상품액·면제액·청구액·수납액·환불액·미수·반환 대기는 별도 지표다. 카드 결제 대기 옵션을 확정 신청에 포함하지 않는다.
- 매출·수납·환불의 일자를 구분한다. 통화별 합계와 원결제별 배분 명세를 내려받을 수 있게 한다.
- 영수증은 검증된 실제 수납·환불, 등록확인서는 현재 확정 옵션과 납부 상황을 사용한다. 처리 중 변경은 별도 안내로 표시한다.
- 감사는 수정 전후 내용·단가/면제 정책·행위자·사유·거래 참조를 포함한다. 임의 금액 조정·수동 입금·외부 반환 확인에는 구체적 권한을 요구한다.
- 주기적 대사: 확정 내역 합계, 거래별 배분 합계, PG 조회 결과, 정원 합계를 비교하고 차이가 있으면 자동으로 금액을 덮어쓰지 않고 확인 대상으로 표시한다.

## 11 기존 데이터와 API 전환

1. **회차 매핑**: 기존 학회 설정·등록구분·신청을 실제 회차와 매핑한다. 알 수 없는 행은 미매핑으로 남기고 옵션 판매를 차단한다.
2. **확장 적용**: 신규 테이블과 nullable 참조를 먼저 추가한다. 기존 신청을 옵션 없는 초기 변경본으로 생성한다. `feeAmount`를 기본요금 스냅샷으로 보존한다.
3. **기존 결제 대사**: `PAID`·`REFUNDED`만 보고 PG 성공 거래를 만들어서는 안 된다. 특히 현행 취소는 DB 표시만 바꾸므로 실제 반환 여부를 별도로 확인한다.
4. **이관 상태**: VERIFIED·MANUAL_VERIFIED·RECONCILIATION_REQUIRED로 구분한다. 확인된 금액은 출처와 legacy 참조를 남기고 기초 원장으로 이관한다. 원거래 정보가 없으면 자동 PG 환불 대상에서 제외한다.
5. **쓰기 경로 교체**: 현행 금액·category·applicationStatus 변경과 `cancel-payment`를 새 도메인 서비스로 우회 없이 연결한다. 금액 이력이 있는 등록의 삭제 API도 보존 정책으로 전환한다.
6. **읽기 경로 교체**: 목록·대시보드·엑셀·증빙이 새 명시적 금액 필드를 사용하게 한다. 기존 `paidAmount`·단일 거래번호를 총 정산액의 근거로 사용하지 않는다.
7. **기존 미수 건**: 미납 최초 등록은 동일 revision 모델에서 최초 결제 주문을 만든다. 상태 불명 거래가 있으면 먼저 대사한다. 이미 취소된 등록은 옵션 변경 대상에서 제외한다.
8. **배포**: 회차 단위 기능 플래그로 관리자 검수 후 개방한다. 거래가 발생한 뒤 기능을 끄더라도 이미 열린 주문의 notify·조회·환불·대사는 계속 처리한다.

## 12 구현 순서와 완료 기준

| 단계 | 작업 | 단계 완료 기준 |
|---|---|---|
| 1 | 회차 연결·카탈로그·관리자 메뉴 | 상품·가격·기간·정원·유료무료 설정, 검색·통계·엑셀과 감사 |
| 2 | 변경본·견적·정원·무료 자격 계산 | 관리자에서 옵션 선택·변경 미리보기, 0원 적용·경쟁 정원 검증. 유료 건은 아직 결제 완료로 표시하지 않음 |
| 3 | 실제 결제·원장·환불·대사 | 서버 검증 결제, 부분환불·불명 상태·늦은 성공·재시도·장애 복구 |
| 4 | 참가자 인증·최초 등록·변경 UI | 본인만 조회·결제·변경, 무료 초과분 결제, 결제 재진입·상태 표시 |
| 5 | 초대대상·현장·출력·통계 | 자격 일괄 연계·등록 추적, 최종 증빙·옵션별 정산과 실제 운영 검수 |

원문 요구를 충족하려면 5단계까지 필요하다. 1~2단계만 완료한 상태를 ‘유료 옵션 구현 완료’로 보고하지 않는다. PG가 제공하지 않는 기능은 어댑터 능력과 화면 안내를 일치시키고 별도 운영 경로를 검수해야 한다.

## 13 필수 테스트

| 대상 | 검증 시나리오 |
|---|---|
| 가격 | 정상·0원·복수수량·한도 초과·통화 불일치·금액 범위·가격 변경 후 기존 구매분 유지 |
| 무료 혜택 | 기본요금만 면제, 무료수량 후 금액한도, 중복 할인 방지, 삭제 후 혜택 재배분, 자격 중복 사용 차단 |
| 변경 | 증가·감소·동액 교환, 비허용 구분·판매 마감·사용 후 취소, 동일 요청 재전송·다른 payload 키 재사용 |
| 동시성 | 정원 마지막 1개에 동시 신청, 만료와 결제확정 경쟁, 관리자의 정원 축소, 동일 등록 2개 탭 변경 |
| 결제 | 위변조 금액·통화·상점·주문 거부, 중복·역순 콜백, 결제창 닫힘 후 성공, 응답 유실 후 조회 |
| 환불 | 부분환불·복수 원결제 분할·일부 성공, 환불 한도 경쟁, UNKNOWN에서 중복 요청 금지, 공급자 변경 후 원거래 환불 |
| 늦은 성공 | 재고 재확보 성공, 재확보 불가 보상환불, 동일 보상 재실행 방지 |
| 원장 | 옵션별 배분 합계와 현금 거래 일치, 동액 교환의 배분 상쇄, 환불 대기와 실제 반환 분리 |
| 이관 | DB만 REFUNDED인 기존 건을 미확인으로 분류, 회차 미매핑 차단, 기초 원장 재이관 멱등성 |
| 권한 | 타인 등록·다른 회차 옵션·child ID 바꿔치기, 관리자 기능별 권한, CSRF, 비밀값 비노출 |
| 장애 | PG 호출 후 DB 반영 실패, outbox 중복 실행·프로세스 재시작, 만료 스케줄러 재실행 |
| UI·출력 | 좁은 화면·다크모드·입력 유지, 실패를 성공으로 표시하지 않음, 최종 증빙과 정산 합계 일치 |

동시성은 mock 서비스 테스트만으로 검증하지 않고 MariaDB 10.6 테스트 환경에서 수행한다. Java 작업은 `gradlew17.bat`을 사용한다. 프론트엔드 변경은 TypeScript와 변경 파일 lint, 실제 결제는 테스트 상점·공급자 테스트 수단으로 검수한다.

## 14 구현 착수 전 확정할 업무 정책

설계의 기본값으로 개발을 시작할 수 있지만 다음은 실제 판매 개방 전에 확정해야 한다.

1. 실제 옵션 종류·가격·통화·정원·신청 및 변경 마감, 이미 사용한 옵션의 판단 기준.
2. 무료 대상별 기본요금·무료수량·옵션 금액 한도와 적용 순서, 자격 인증 방식.
3. 선택 PG 계약에서 부분취소·상태 조회 지원 여부, 미지원 시 반환 수단과 담당자.
4. 취소 수수료·마감 후 예외 정책 및 관련 증빙 문구. 이 설계 기본은 기간 내 전액 반환이다.
5. 기존 결제 데이터의 실제 수납·반환 증빙과 회차 매핑.

이 정책은 운영 학회의 결정 사항이며, 코드에 임의 수치를 넣어 확정하지 않는다.
