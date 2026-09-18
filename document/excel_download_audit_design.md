# 엑셀 다운로드 사유 및 이력 관리 설계

## 1. 목표와 적용 범위

관리자 화면에서 조회 결과를 엑셀로 반출할 때 다운로드 사유를 필수로 입력받고, 누가 언제 어떤 조건의 데이터를 내려받았는지 감사 이력으로 남긴다.

### 1차 적용 대상

현재 서버가 조회 결과를 동적으로 생성하는 다음 4개 메뉴부터 적용한다.

| 내보내기 유형 | 메뉴 키 | 화면 | 현재 API |
|---|---|---|---|
| `ADMIN_ACCOUNTS` | `admin` | 관리자 계정 관리 | `GET /api/admin/accounts/excel` |
| `MEMBERS` | `members` | 회원 관리 | `GET /api/members/excel` |
| `ABSTRACTS` | `abstracts` | 초록 관리 | `GET /api/abstracts/export` |
| `PRE_REGISTRATIONS` | `pre-registrations` | 사전등록관리 | `GET /api/admin/pre-registrations/excel` |

`frontend/public/templates`의 관리자 일괄등록 양식, 심사자 일괄배정 양식처럼 개인정보나 조회 결과가 들어 있지 않은 정적 서식 다운로드는 1차 적용 대상에서 제외한다.

향후 다른 메뉴에 동적 엑셀 다운로드가 추가되면 공통 모달, 공통 클라이언트 함수, 공통 감사 서비스를 그대로 사용하고 내보내기 유형만 등록한다.

## 2. 사용자 흐름

1. 관리자가 화면에서 검색 조건을 적용한다.
2. `엑셀 다운로드` 버튼을 누른다.
3. 공통 `엑셀 다운로드 사유` 모달이 열린다.
4. 모달에 메뉴명과 현재 적용된 검색 조건을 표시한다.
5. 관리자가 사유를 입력하고 `다운로드`를 누른다.
6. 프론트엔드는 사유와 현재 적용된 필터를 JSON 요청 본문으로 전송한다.
7. 서버는 세션 사용자와 권한, 사유를 검증한 뒤 감사 이력을 `PROCESSING`으로 먼저 생성한다.
8. 서버가 엑셀 생성에 성공하면 행 수, 파일명, 파일 크기와 함께 이력을 `SUCCESS`로 변경하고 파일을 응답한다.
9. 생성에 실패하면 실패 내용을 이력에 남기고 오류 응답을 반환한다.

사용자가 사유 모달에서 취소한 경우 서버 요청과 이력은 생성하지 않는다. `SUCCESS`는 서버가 다운로드 응답을 정상 생성했다는 뜻이다. 브라우저에서 사용자가 저장 대화상자를 취소했는지까지는 서버가 확인할 수 없다.

## 3. 화면 설계

### 공통 모달

별도 화면별 모달을 만들지 않고 `ExcelDownloadReasonDialog`와 Context/Provider를 공통으로 사용한다. 기존 `ConfirmDialog`는 문자열 입력을 반환하지 않으므로 확장하지 않는다.

- 제목: `엑셀 다운로드 사유 입력`
- 안내: `개인정보가 포함될 수 있습니다. 업무상 필요한 다운로드 사유를 입력해 주세요.`
- 대상 메뉴: 서버의 내보내기 유형과 연결된 고정 메뉴명 표시
- 적용 조건: 검색어, 상태, 구분, 기간 등 현재 실제 조회에 적용된 값만 요약 표시
- 사유: 여러 줄 입력, 필수, 앞뒤 공백 제거 후 5~500자
- 글자 수 표시: `현재 글자 수 / 500`
- 버튼: `취소`, `다운로드`
- 처리 중에는 입력과 버튼을 비활성화하고 중복 요청을 막는다.
- 오류가 발생하면 모달을 닫지 않고 사유를 유지해 재시도할 수 있게 한다.
- Escape 닫기, 포커스 트랩, 모바일 대응, Tailwind 다크모드를 적용한다.

프론트엔드 검증은 사용성을 위한 것이며 동일한 5~500자 검증을 서버에서 반드시 다시 수행한다.

### 이력 조회 화면

이력을 운영에서 확인할 수 있도록 `엑셀 다운로드 이력` 관리자 메뉴를 제공하는 것을 권장한다. 이 화면은 감사 데이터이므로 수정/삭제 기능을 제공하지 않는다.

- 검색 조건: 기간, 메뉴, 관리자 이름/이메일, 처리 상태, 사유 검색
- 목록 컬럼: 요청일시, 메뉴, 관리자, 사유, 적용 필터, 결과 행 수, 파일명/크기, IP, 상태
- 실패 행: 실패 사유를 상세 팝오버나 상세 모달에서 확인
- 기본 정렬: 최신 요청순
- 접근 권한: 일반 관리자만 허용하고 심사자 역할에는 노출하지 않음

이력 조회 UI가 1차 일정에 포함되지 않더라도 DB 저장과 조회 API까지 먼저 만들어 운영 검증이 가능하게 한다.

## 4. API 설계

사유가 URL, 웹서버 접근 로그, 브라우저 기록에 남지 않도록 기존 `GET` 다운로드를 `POST`와 JSON 요청 본문으로 변경한다. 기존 `GET`을 유지하면 사유 입력을 우회할 수 있으므로 프론트엔드 전환과 함께 제거하거나 `405 Method Not Allowed`가 되도록 한다.

### 메뉴별 다운로드 API

| 화면 | 변경 API | 요청 DTO |
|---|---|---|
| 관리자 계정 | `POST /api/admin/accounts/excel` | `AdminAccountExcelDownloadRequest` |
| 회원 | `POST /api/members/excel` | `MemberExcelDownloadRequest` |
| 초록 | `POST /api/abstracts/export` | `AbstractExcelDownloadRequest` |
| 사전등록 | `POST /api/admin/pre-registrations/excel` | `PreRegistrationExcelDownloadRequest` |

각 요청 DTO에 해당 화면 필터와 `reason`을 둔다. 클라이언트가 `menuKey`, `menuName`, `adminSeq`를 보내게 하지 않는다. 이 값들은 엔드포인트의 고정 `ExcelExportType`과 서버 세션으로 결정해 위변조를 막는다.

예시:

```json
{
  "reason": "등록 현황 확인 후 운영위원회 주간 보고 자료 작성",
  "keyword": "",
  "memberType": "domestic",
  "hasPreRegistration": true,
  "hasAbstractSubmission": null
}
```

성공 응답은 기존과 같이 XLSX 바이너리와 다음 헤더를 반환한다.

```text
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename*=UTF-8''members-2026-08-27.xlsx
X-Excel-Download-Log-Id: 12345
```

`X-Excel-Download-Log-Id`는 장애 문의 시 서버 이력을 찾기 위한 상관관계 ID이다.

### 이력 조회 API

```text
GET /api/admin/excel-download-logs
    ?page=1
    &size=20
    &dateFrom=2026-08-01
    &dateTo=2026-08-31
    &exportType=MEMBERS
    &adminKeyword=
    &status=SUCCESS
    &reasonKeyword=

GET /api/admin/excel-download-logs/{seq}
```

목록 응답에서는 긴 `filterJson`, `userAgent`, 실패 상세를 축약하고 상세 API에서 전체 값을 제공한다.

### 오류 규칙

- `400 Bad Request`: 사유 누락/길이 위반, 필터 형식 오류
- `401 Unauthorized`: 관리자 세션 없음
- `403 Forbidden`: 해당 다운로드 또는 이력 조회 권한 없음
- `409 Conflict`: 동일 요청이 이미 처리 중인 경우(향후 요청 ID 중복 방지를 도입할 때)
- `500 Internal Server Error`: 감사 이력 저장 실패 또는 엑셀 생성 실패

감사 이력을 남길 수 없는 상태에서는 파일 다운로드도 허용하지 않는 fail-closed 정책을 적용한다.

## 5. 백엔드 공통 구조

### 고정 내보내기 유형

```java
public enum ExcelExportType {
    ADMIN_ACCOUNTS("admin", "관리자 계정 관리"),
    MEMBERS("members", "회원 관리"),
    ABSTRACTS("abstracts", "초록 관리"),
    PRE_REGISTRATIONS("pre-registrations", "사전등록관리");
}
```

화면에서 메뉴명이 변경되어도 과거 기록을 보존할 수 있도록 이력에는 `menuKey`와 당시 `menuName`을 모두 스냅샷으로 저장한다.

### 공통 결과 모델

기존 서비스가 `byte[]`만 반환하면 정확한 결과 행 수를 감사 이력에 남기기 어렵다. 각 엑셀 생성 서비스가 다음 공통 결과를 반환하도록 변경한다.

```java
public record ExcelExportResult(
        byte[] content,
        int rowCount,
        String filename
) {}
```

### 감사 실행 서비스

컨트롤러별로 로그 저장 코드를 복제하지 않고 `ExcelDownloadAuditService`가 다음 순서를 보장한다.

```text
validate reason and session
  -> insert PROCESSING log
  -> generate ExcelExportResult
  -> update SUCCESS metadata
  -> return result with log seq

generation exception
  -> update FAILED metadata
  -> rethrow mapped exception
```

감사 서비스 입력값:

- 서버 고정 `ExcelExportType`
- 세션의 `adminSeq`
- 정규화된 사유
- 서버가 실제 엑셀 조회에 사용한 필터 객체
- `HttpServletRequest`의 접속 IP와 User-Agent
- 엑셀 생성 함수

감사 INSERT/UPDATE는 `ExcelDownloadAuditLogWriter` 같은 별도 빈에서 `REQUIRES_NEW` 트랜잭션으로 처리한다. 엑셀 생성 전체를 하나의 트랜잭션으로 감싼 뒤 예외를 다시 던지면 `FAILED` 갱신까지 롤백되어 실패 이력이 사라질 수 있기 때문이다. 권장 트랜잭션 경계는 다음과 같다.

```text
REQUIRES_NEW: PROCESSING insert
no transaction or read-only transaction: data query and workbook generation
REQUIRES_NEW: SUCCESS or FAILED update
```

`PROCESSING` INSERT가 실패하면 생성 함수를 실행하지 않는다. 파일 생성 후 `SUCCESS` 갱신이 실패해도 파일을 응답하지 않는다. `FAILED` 갱신 자체가 실패하면 최초 `PROCESSING` 행은 남기고 서버 오류 로그로 알리며, 운영 화면에서 장시간 `PROCESSING` 상태를 장애로 식별한다.

관리자 이름, 이메일, 역할은 요청값이 아니라 `adminSeq`로 DB에서 조회해 스냅샷을 저장한다. 접속 IP는 현재 로그인 로그와 같은 기준을 사용하되 프록시 환경에서는 신뢰 프록시 설정을 통과한 주소만 사용하고 임의의 `X-Forwarded-For` 값을 그대로 신뢰하지 않는다.

필터는 Jackson으로 JSON 직렬화하되, 각 DTO에서 허용된 조회 필드만 저장한다. 비밀번호, 토큰, 세션 ID 같은 값은 포함하지 않는다.

## 6. 데이터베이스 설계

프로젝트 규칙에 맞춰 테이블명은 snake_case, 컬럼명은 camelCase, PK는 `seq`로 한다.

```sql
CREATE TABLE IF NOT EXISTS excel_download_logs (
    seq BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '엑셀 다운로드 이력 ID',
    exportType VARCHAR(50) NOT NULL COMMENT '엑셀 내보내기 유형',
    menuKey VARCHAR(100) NOT NULL COMMENT '관리자 메뉴 키 스냅샷',
    menuName VARCHAR(100) NOT NULL COMMENT '관리자 메뉴명 스냅샷',
    reason VARCHAR(500) NOT NULL COMMENT '다운로드 사유',
    filterJson JSON COMMENT '적용된 조회 조건 JSON',
    adminSeq BIGINT COMMENT '요청 관리자 ID',
    adminEmail VARCHAR(255) NOT NULL COMMENT '요청 관리자 이메일 스냅샷',
    adminName VARCHAR(255) NOT NULL COMMENT '요청 관리자 이름 스냅샷',
    adminRole VARCHAR(50) NOT NULL COMMENT '요청 관리자 권한 스냅샷',
    ipAddress VARCHAR(45) COMMENT '요청 IP 주소',
    userAgent VARCHAR(500) COMMENT '요청 User-Agent',
    status VARCHAR(20) NOT NULL COMMENT '처리 상태 (PROCESSING/SUCCESS/FAILED)',
    rowCount INT UNSIGNED COMMENT '내보낸 데이터 행 수',
    fileName VARCHAR(255) COMMENT '생성 파일명',
    fileSize BIGINT UNSIGNED COMMENT '생성 파일 크기(Byte)',
    failureCode VARCHAR(50) COMMENT '실패 코드',
    failureMessage VARCHAR(500) COMMENT '실패 메시지',
    requestedAt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '다운로드 요청일시',
    completedAt DATETIME(6) COMMENT '처리 완료일시',
    INDEX idx_excel_download_logs_requestedAt (requestedAt),
    INDEX idx_excel_download_logs_menu_date (menuKey, requestedAt),
    INDEX idx_excel_download_logs_admin_date (adminSeq, requestedAt),
    INDEX idx_excel_download_logs_status_date (status, requestedAt),
    CONSTRAINT fk_excel_download_logs_admin
        FOREIGN KEY (adminSeq) REFERENCES admin_accounts(seq)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_excel_download_logs_status
        CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED'))
) COMMENT='관리자 엑셀 다운로드 감사 이력';
```

`adminSeq`는 계정 삭제 후에도 감사 이력을 유지하기 위해 nullable과 `ON DELETE SET NULL`을 사용하고, 관리자 식별 정보는 별도 스냅샷 컬럼으로 보존한다. 실패 메시지는 내부 SQL이나 경로를 그대로 저장하지 않고 운영자가 이해할 수 있는 정제된 메시지만 저장한다.

감사 이력은 일반 기능 데이터처럼 수정하거나 삭제하지 않는다. 보존 기간은 개인정보 처리방침과 내부 규정에 맞춰 별도로 확정하며, 확정 전에는 자동 삭제 배치를 두지 않는다.

## 7. 프론트엔드 공통 구조

권장 구성:

```text
frontend/src/components/ExcelDownloadReasonDialog.tsx
frontend/src/components/excelDownloadDialogContext.ts
frontend/src/excelDownload.ts
```

`excelDownload.ts`는 다음을 공통 처리한다.

- 사유 모달 호출
- JSON `POST` 요청
- 서버 오류 문자열 처리
- `Content-Disposition`의 UTF-8 파일명 추출
- Blob URL 생성/해제와 브라우저 다운로드
- 한 요청 내 중복 제출 방지

각 화면은 엔드포인트와 현재 적용 필터만 전달한다. 검색 폼에 입력했지만 아직 조회 버튼을 누르지 않은 draft 값이 아니라, 현재 목록을 조회한 확정 필터를 보내야 화면 결과와 엑셀/이력이 일치한다.

## 8. 보안과 운영 원칙

- 다운로드 권한과 감사 이력 조회 권한은 프론트엔드 메뉴 노출이 아니라 서버에서 검사한다.
- 사유, 검색어, 개인정보를 애플리케이션 일반 로그에 출력하지 않는다.
- 사유는 SQL 파라미터 바인딩으로 저장하고 화면 출력 시 React 기본 이스케이프를 유지한다.
- 다운로드 이력 API에는 페이지 크기 상한을 둔다.
- 감사 테이블 INSERT가 실패하면 다운로드를 중단한다.
- 엑셀 생성 실패도 `FAILED`로 기록하되 내부 예외 전문이나 스택 트레이스는 DB에 저장하지 않는다.
- `PROCESSING` 상태가 일정 시간 이상 남으면 장애로 식별할 수 있도록 운영 조회 조건을 제공한다.
- 대용량 엑셀로 전환할 경우에도 성공 갱신은 파일 생성 완료 뒤에 수행한다.

## 9. 테스트 기준

### 백엔드

- 공백 사유, 5자 미만, 500자 초과 요청은 `400`이며 엑셀을 생성하지 않는다.
- 세션이 없으면 `401`, 권한이 없으면 `403`이다.
- 성공 시 사용자 스냅샷, 메뉴, 사유, 정규화 필터, 행 수, 파일명이 정확히 저장된다.
- 엑셀 생성 예외 시 같은 이력이 `FAILED`로 변경된다.
- 감사 이력 INSERT 실패 시 엑셀 생성 함수를 실행하지 않고 파일도 응답하지 않는다.
- 한글 사유와 다국어 검색 조건이 UTF-8로 손상 없이 저장된다.
- 네 메뉴 모두 화면 목록과 동일한 확정 필터를 사용한다.
- 기존 `GET` API로 사유 없이 다운로드할 수 없다.

### 프론트엔드

- 다운로드 버튼을 누르면 파일 요청 전에 사유 모달이 열린다.
- 취소하면 네트워크 요청이 발생하지 않는다.
- 처리 중 중복 클릭이 차단된다.
- 서버 오류 시 입력한 사유가 유지된다.
- 성공 시 서버 파일명으로 다운로드하고 Blob URL을 해제한다.
- 키보드 탐색, Escape, 포커스 복귀, 다크모드가 정상 동작한다.

## 10. 구현 순서

1. `excel_download_logs` 스키마, Entity, Repository, 감사 서비스와 테스트 추가
2. 공통 요청 검증, `ExcelExportType`, `ExcelExportResult` 추가
3. 4개 컨트롤러를 JSON `POST` 방식으로 전환하고 기존 `GET` 우회 차단
4. 공통 프론트엔드 사유 모달과 다운로드 유틸리티 추가
5. 관리자 계정, 회원, 초록, 사전등록 화면 연결
6. 이력 조회 API와 관리자 이력 화면 추가
7. 전체 Java 17 Gradle 테스트 및 프론트엔드 빌드로 검증
