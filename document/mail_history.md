# 메일발송이력 — 저장 기능

## 배포 순서

1. MariaDB 10.6 대상 DB에 `document/mail_history_migration.sql`을 1회 적용한다.
2. 서버와 프론트엔드를 배포하고 관리자 메뉴를 다시 조회한다.
3. 시스템관리 > 메일발송이력(`/admin/mail-history`)에서 선택 학회의 저장 이력을 확인한다.

애플리케이션 시작 시 스키마나 메뉴를 자동 변경하지 않는다. 로컬 DB 조회 도구로 쓰기 SQL을 실행하지 않는다. SQL 적용 전에는 새 이력 API와 변경된 홍보메일 목록 API를 사용할 수 없다.

## 상태와 범위

- 공통 MailComposeModal을 사용하는 사전등록·초록 접수·구두·포스터·초청연자·후원 신청을 지원한다.
- 저장 버튼을 누른 때부터 이력이 생성된다. 작성 중·미리보기만 한 내용은 저장하지 않는다.
- mail_campaigns.status, mail_send_jobs.status 및 유효 수신자 상태는 SAVED, 화면 표시는 `저장됨 · 미발송`이다.
- jobType은 HISTORY, provider는 none이다. 발송 시각과 전송 성공 결과를 생성하지 않는다.
- 발신 계정이 아직 없으므로 발신자명·이메일은 빈 값이다. createdBy와 저장 당시 관리자명은 별도로 기록한다.
- 이 저장 건은 기존 홍보메일 편집 목록에 중복 노출하지 않는다. 기존 홍보메일 발송 준비 건, 인증·비밀번호 재설정 메일 등은 아직 이 메뉴의 수집 대상에 포함하지 않는다.
- 기존 캠페인 수정·삭제 API는 DRAFT만 허용하므로 SAVED 본문·첨부는 변경할 수 없다. 이력 수정·삭제 API도 제공하지 않는다.
- 추후 전송 구현 시 저장 이력을 자동 발송하면 안 된다. 발신 계정 설정, 최신 수신 거부 재검사 및 명시적인 발송 요청 후 큐로 전환해야 한다.

## 테이블

| 테이블 | 역할 |
|---|---|
| mail_campaigns | 제목·정화된 HTML/텍스트 본문·저장 관리자 |
| mail_send_jobs | 미발송 작업과 수신자 집계 |
| mail_campaign_recipients | 이메일 중복 제거 후 저장 당시 수신자 스냅샷 |
| mail_send_results | SAVED 또는 EXCLUDED, 향후 공급자 결과 |
| mail_campaign_attachments | mail/yyyyMM/UUID.ext 파일 및 원본 파일명 |
| mail_history_contexts (신규) | 학회·발송 메뉴·요청 키·요청 지문·집계·저장 관리자명 |
| mail_recipient_origins (신규) | 수신자와 선택 업무 항목의 다대일 연결 |

원본은 PRE_REGISTRATION / ABSTRACT / SPEAKER / SPONSORSHIP + sourceSeq로 연결한다. 원본이 삭제되거나 이메일이 변경돼도 스냅샷과 연결값은 보존한다. 이메일은 PK가 아니며 같은 이메일의 여러 발송 건을 독립적으로 보관한다. 이름·이메일·관리자명은 기존 DB AES HEX 방식으로 암호화한다.

## 집계

### 보기 기준

- `메일별 보기`: 기존처럼 저장한 메일 한 건을 한 행으로 표시한다.
- `이메일별 보기`: 저장 당시 normalizedEmail을 기준으로 같은 주소의 수신자 기록을 묶는다. 대소문자·앞뒤 공백 차이는 저장 시 정규화되므로 같은 주소로 집계된다. 다른 학회의 이력은 합치지 않는다.
- 이메일별 목록은 이메일, 검색 결과 중 최근 수신자명, 메일 이력 수, SAVED 수, EXCLUDED 수, 최근 저장일을 표시한다. 이름이 달라도 같은 주소면 한 그룹이며, 각 이력의 당시 이름은 하위 목록에 남는다. 이메일이 없는 오류 기록은 `이메일 없음` 그룹으로 확인할 수 있다.
- 이메일을 선택하면 해당 주소의 메일 이력 목록으로 이동한다. 적용된 검색 조건을 유지하고, 메일 상세에서는 선택한 수신자 기록을 먼저 보여 준다. `전체 수신자 보기`로 같은 메일의 나머지 수신자도 확인할 수 있다.
- 이메일 보기의 상태 조건은 메일 전체가 아닌 수신자별 상태에 적용한다. 검색어도 해당 수신자의 이름·이메일 또는 메일 제목·저장 관리자에 적용하며, 같은 메일의 다른 수신자가 검색어에 맞는다는 이유로 포함하지 않는다. 보기 기준을 전환할 때 상태 조건은 초기화한다.
- 이메일별 상단 통계는 검색 조건 전체의 주소 수, 수신자별 메일 이력 수, 제외 이력 수다. 주소 선택 후에는 해당 주소의 메일 이력 수, 미발송 수, 제외 수다. 제외 기록도 메일 이력 수에 포함하므로 실제 발송 횟수와 같지 않다.
- 목록·집계·페이지 나누기는 서버에서 처리한다. 새 테이블이나 추가 SQL 적용은 필요하지 않으며 최초 이력 기능용 마이그레이션이 적용되어 있어야 한다.

### 저장 건수

- selectedCount: 중복 원본 PK를 제거한 선택 항목 수.
- duplicateCount: 유효 이메일의 공백·대소문자 정규화 후 중복 원본 항목 수.
- invalidCount: 이메일 누락·형식 오류 원본 항목 수.
- suppressionCount: 정규화한 유효 이메일 중 수신 거부 이메일 수.
- recipientCount: 수신자 스냅샷 수. 오류 이메일도 같은 주소면 합치고 모든 원본을 연결한다. 여러 빈 이메일은 하나의 오류 수신자와 여러 원본으로 남는다.
- excludedCount: 수신자 스냅샷 중 이메일 오류 또는 수신 거부로 제외된 수.
- 유효·비거부 수신자가 0명이면 저장을 거부한다. 일부 오류가 있는 경우 정상 대상과 제외 대상을 함께 기록한다.
- 목록 상단은 동일 검색 조건 전체 결과의 이력 건수, SAVED 건수, 수신자 스냅샷 수 합계를 서버 집계한다. 서로 다른 이력에 같은 이메일이 있으면 각각 센다.

## 일관성과 재시도

하나의 트랜잭션으로 context 예약, 원본 확인, 캠페인, 작업, 수신자, 원본 연결, 첨부 메타데이터를 기록한다. DB 커밋 실패·롤백 시 이번 요청에서 만든 파일을 정리한다. 파일은 월별 UUID 상대 경로로 저장하며 조회는 UploadStorage.resolve를 사용한다.

conferenceSeq + createdBy + requestKey 유일 키와 FOR UPDATE 잠금으로 동시 요청을 직렬화한다. 같은 요청 키·지문은 기존 이력을 반환한다. 제목·정화 본문·메뉴·원본 ID·첨부 이름/크기/내용 해시로 만든 지문이 다르면 409를 반환한다. 원본 조회보다 재시도 확인을 먼저 하므로 저장 후 원본이 변경돼도 같은 이력을 반환한다.

## API

- POST /api/admin/mail-history: multipart request JSON(requestKey, sourceMenu, subject, htmlContent, sourceSeqs) 및 files. 원본은 서버에서 선택 학회로 한정하여 조회한다.
- GET /api/admin/mail-history: page, size, keyword(제목/수신자 이름/이메일/관리자명), sourceMenu, status, dateFrom, dateTo. 원본별 연동에는 sourceType + sourceSeq도 지원한다.
- GET /api/admin/mail-history/{seq}: 본문·수신자별 상태·원본 연결·첨부.
- GET /api/admin/mail-history/{seq}/attachments/{attachmentSeq}: 학회·이력·첨부 소속 검증 후 다운로드.
- GET /api/admin/mail-history/emails: 이메일별 집계와 목록. page, size, keyword, sourceMenu, status(수신자 상태), dateFrom, dateTo.
- GET /api/admin/mail-history/emails/{recipientSeq}: 동일 학회에 속한 수신자 번호로 저장 당시 정규화 이메일을 조회한 뒤, 그 주소의 이력을 정확히 일치시켜 조회한다. 검색 조건은 이메일별 목록과 동일하다. 이메일 원문을 경로에 전달하지 않는다.

기존 /api/admin/** 관리자 인터셉터와 CSRF 정책을 따른다. X-Conference-Seq로 범위를 고정하고 저장자는 세션에서 얻는다. 첨부 다운로드도 공통 fetch를 사용하여 학회 헤더를 유지한다.

## 검증

MailHistoryServiceTest는 미발송 상태, HTML 정화, 원본 연결, 수신 거부·오류, 재시도, 학회 분리, 첨부 월별 저장·롤백, 검색 범위를 확인한다. MailHistoryRepositoryTest는 MyBatis 문장 파싱과 메뉴별 SQL의 학회·날짜 조건을 확인한다. MailHistoryControllerTest는 관리자 권한, multipart 검증, 세션 저장자, 다른 학회 첨부 접근 방지를 확인한다.
