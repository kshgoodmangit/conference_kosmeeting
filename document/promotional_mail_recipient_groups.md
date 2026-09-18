# 메일 전체 대상 선택

메일 작성·수정 모달에서 전체 대상 그룹과 기존 주소록·개별 수신자를 함께 선택한다.

| 저장 코드 | 화면 이름 | 조회 기준 |
|---|---|---|
| ALL_MEMBERS | 회원 전체 | members 전체 |
| ALL_REGISTRANTS | 사전등록자 전체 | applicationStatus = SUBMITTED인 사전등록 보유 회원. 결제 상태 무관 |
| ALL_SUBMITTERS | 초록 접수자 전체 | submitted, under_review, approved, rejected 초록 보유 회원. draft 제외 |
| ALL_ACCEPTED | 초록 채택자 전체 | approved 초록을 1건 이상 보유한 회원 |

초록은 접수 회원의 이메일을 사용한다. 공동저자·교신저자 전체를 별도로 포함하지 않는다.
EXISTS 조회로 한 회원이 여러 신청·초록을 보유해도 그룹 안에서는 한 번 조회한다.

## 저장과 집계

- `MailCampaignRequest`와 상세 응답에 `recipientGroups` 배열을 추가한다. 기존 요청은 빈 그룹으로 처리한다.
- 기존 `mail_campaign_recipient_sources.sourceType`에 위 코드를 저장한다. 그룹 행의 이메일·주소록 ID는 NULL이다.
- 네 코드 모두 기존 VARCHAR(20)에 들어가며 추가 테이블·컬럼이나 운영 DB 마이그레이션은 필요 없다.
- 개별 수신자 조회는 DIRECT, INTERNAL_MEMBER, INTERNAL_ADMIN, ADDRESS_BOOK_CONTACT만 조회하여 그룹 행과 구분한다.
- `POST /api/admin/mail/campaigns/recipient-preview`는 recipientGroups, addressBookSeqs, directRecipients를 받아 그룹별 발송 가능 인원과 통합 includedCount, duplicateCount, suppressionCount, invalidCount를 반환한다. 메일 제목·본문 저장은 필요 없다.
- 서버의 그룹 인원과 통합 집계에는 전체 조회 결과의 이메일 공백 제거·소문자 정규화·중복 제거·수신 거부·이메일 유효성 검사를 적용한다.
- 모달에서는 처음 열 때 빈 선택 조건으로 그룹별 인원을 한 번만 조회한다. 그룹·주소록·개별 수신자 선택 변경으로 추가 조회하지 않으며 수동 인원 새로고침 버튼은 제공하지 않는다.
- 화면의 선택 합계는 처음 조회한 그룹 인원, 주소록 등록 인원, 개별 수신자 수를 클라이언트에서 더한 값이다. 대상 간 중복 및 주소록·개별 수신자의 제외 대상이 포함될 수 있으며 정확한 최종 인원은 발송 준비 시 확정한다.
- 중복은 제거된 후보 건수, 이메일 오류는 유효하지 않은 후보 건수, 수신 거부는 중복 제거 후 이메일 수다.
- 조회 중에는 집계 중, 조회 실패에는 -를 표시한다. 재조회하려면 모달을 닫았다가 다시 연다. 닫힌 모달의 요청은 취소하고 이전 응답은 무시한다.

## 발송 준비

작성 중에는 선택 조건만 저장한다. 발송 준비 시 저장된 그룹과 주소록의 최신 대상을 다시 조회하며, 그룹 인원 집계와 같은 서비스를 사용한다.
최종 수신자는 기존 mail_campaign_recipients에 고정하며 예약 메일도 준비 시 확정한 명단을 사용한다.
유효한 이메일의 수신 거부는 EXCLUDED/SUPPRESSED로 기록한다. 누락·형식 오류 주소는 수신자 레코드 생성 없이 invalidCount로 반환한다.
발송 가능한 인원이 0명이면 작업 생성을 거부한다. 기존 중복 요청 키와 캠페인 상태 전환을 유지한다.

실제 공급자 전송 구현은 이 변경 범위에 포함하지 않으며 기존 발송 준비 동작을 유지한다.
