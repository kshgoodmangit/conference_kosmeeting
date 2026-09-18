# 개인정보 보호 적용 현황 및 제안발표 문안

확인일: 2026-09-10

현재 작업 폴더의 백엔드·프론트엔드 소스, 설정, 관련 테스트 코드와 개발 DB 스키마를 확인한 결과입니다. 이름·연락처·이메일을 보관하는 25개 업무 테이블의 68개 컬럼에 저장 암호화와 업무 조회 시 복호화를 적용했고, 개발 DB의 컬럼 변경과 기존 데이터 일괄 암호화는 수동 실행을 완료했습니다. 운영 반영은 아직 하지 않았습니다. 운영 서버, 방화벽, 인증서, 백업 및 조직의 관리 절차도 별도 점검이 필요합니다. 아래의 ‘적용’은 소스와 개발 DB 기준이며 운영 검증 완료나 법령 준수 인증을 의미하지 않습니다.

## 1. 발표에 사용할 핵심 문안

### 슬라이드 제목

개인정보 저장 암호화와 접근·반출 통제

### 슬라이드 본문

- **계정정보 보호**: 회원 및 관리자 비밀번호를 BCrypt 기반 단방향 해시로 저장
- **업무 개인정보 저장 보호**: 회원·관리자·심사자·초록 저자·연자·후원 담당자·메일 및 문자 수신자 등의 이름·이메일·연락처를 DB 저장 시 AES 암호화하고 업무 조회 시에만 복호화
- **업무별 접근 통제**: 관리자·심사자 권한 분리 및 심사자 본인에게 배정된 심사만 접근 허용
- **관리 영역 접근 제한**: 허용 IP 기반 접근 검사 및 비허용 네트워크 요청 차단
- **접속·반출 이력 관리**: 관리자 로그인 성공 이력과 주요 개인정보 관리 메뉴의 엑셀 다운로드 사유·요청자·처리 결과 기록
- **세션 및 웹 입력 보호**: 로그인 시 세션 ID 갱신, 세션 만료, CSRF 토큰 검증 및 게시물·메일 본문 HTML 정제
- **첨부파일 저장 보호**: UUID 파일명과 공통 저장 경로 검증으로 파일명 노출 및 경로 이탈 위험 감소

### 발표 멘트

“개인정보 보호를 위해 계정 인증, 중요정보 저장 암호화, 업무별 접근 권한, 데이터 반출 기록을 함께 관리하도록 구현했습니다. 회원과 관리자 비밀번호는 단방향 해시로 저장하고, 회원·관리자·심사자·초록 저자·연자·후원 담당자와 메일·문자 수신자 등 업무 테이블의 이름·이메일·연락처 68개 컬럼은 데이터베이스에 AES 암호문으로 저장합니다. 애플리케이션은 업무 처리 시에만 설정된 키로 복호화하며, 관리자와 심사자의 접근 권한을 구분하고 허용 IP를 검사합니다. 회원·초록·사전등록·관리자 계정의 엑셀 다운로드에는 업무상 사유를 입력하도록 해 요청자와 조회 조건, 추출 건수, 파일 생성 결과를 추적할 수 있도록 했습니다. 또한 세션 보호, 요청 위조 방지, HTML 입력 정제와 첨부파일 경로 검증을 적용했습니다.”

추가 대책을 함께 설명할 때는 다음 문장을 별도로 사용합니다.

“운영 전 점검 및 추가 보완 과제로는 HTTPS와 서버 접근 정책 확인, 암호화 키의 안전한 보관과 백업 암호화, 관리자 다중요소 인증, 개인정보 화면 마스킹, 동의 이력 및 보유기간에 따른 파기, 감사기록 보호와 이상행위 모니터링을 제안합니다.”

## 2. 현재 구현이 확인된 대책

| 대책 | 확인된 구현 | 발표 시 표현 범위 |
|---|---|---|
| 비밀번호 보호 | 공통 BCrypt 인코더를 사용하여 회원·관리자 비밀번호 저장 및 일치 여부 검증 | ‘비밀번호 단방향 해시 저장’ |
| 업무 개인정보 암호화 | 회원, 관리자, 접속·다운로드 이력, 심사자, 초록 저자, 연자, 후원 신청, 학회 회원, 프로그램 인물, 메일·문자 발송자와 수신자, 무료 대상자의 이름·이메일·연락처 등 25개 테이블 68개 컬럼을 MariaDB AES 함수와 외부 환경변수 키로 암호화하여 저장 | ‘업무 DB의 이름·이메일·연락처 저장 암호화’. 첨부파일·백업·네트워크 구간까지 암호화되었다는 표현은 보류 |
| 암호화 데이터 일관성 | 등록·수정·발송 대상 스냅샷 저장 경로는 암호화하고 조회·검색·로그인·엑셀 경로는 복호화 또는 동일 암호문 비교를 사용함. 평문 호환 분기와 런타임 인터셉터 없이 각 MyBatis 쿼리가 키를 명시적으로 전달 | ‘대상 컬럼은 암호문 저장을 전제로 처리’. 운영 반영 완료나 키 교체 자동화를 의미하지 않음 |
| 역할별 접근 통제 | `/api/admin/**` 관리자 세션·역할 검사, `/api/reviewer/**` 심사자 역할 검사, 본인 계정 관리 경로 별도 검사 | ‘관리자·심사자 권한 분리’. 관리자 내부의 메뉴별 최소권한까지 완비했다는 표현은 보류 |
| 심사 데이터 접근 제한 | 로그인한 심사자 식별값과 심사 배정 번호를 함께 검증 | ‘본인에게 배정된 심사 접근 제한’. 익명심사·저자정보 비공개를 의미하지 않음 |
| IP 접근 제한 | 기본 설정에서 IP 검사 활성화. 관리자 화면 및 API 대상 검사, 허용 목록과 CIDR 대역 지원. 신뢰 프록시 여부에 따라 전달 IP 처리 | ‘허용 IP 기반 접근 통제 기능 적용’. 운영 허용 목록과 예외 경로는 추가 검증 필요 |
| 로그인 세션 보호 | 로그인 시 유효한 기존 세션 ID 교체, 로그아웃 시 세션 무효화, 기본 비활성 세션 만료 60분 | ‘세션 갱신 및 만료 처리’. 화면에서 정확히 60분 후 항상 자동 로그아웃된다는 표현은 피함 |
| 관리자 접속 이력 | 로그인 성공 시 계정 식별정보·역할·IP·User-Agent 기록 | ‘로그인 성공 이력 기록’. 로그인 실패 및 개인정보 조회·수정·삭제 전 행위 기록은 아님 |
| 엑셀 반출 감사 | 회원·초록·사전등록·관리자 계정 4개 유형. 서버에서 사유 5~500자 검증 및 활성 관리자 재확인. 요청자·조회 조건·IP·처리 상태·추출 건수·파일명·크기 기록 | ‘주요 개인정보 관리 메뉴의 엑셀 반출 사유·이력 관리’. 사전 승인제·파일 암호화·유출 차단 기능을 뜻하지 않음 |
| 이력 없는 엑셀 응답 방지 | 이력 생성과 성공 기록을 별도 트랜잭션으로 저장. 시작 또는 성공 기록에 실패하면 정상 파일 응답을 반환하지 않는 구조 | ‘감사기록 저장과 연계한 엑셀 제공’. 성공 상태는 서버 파일 생성 성공이며 사용자 PC의 최종 저장 완료를 증명하지 않음 |
| CSRF 방어 | 서버 세션 기반 CSRF 토큰과 요청 헤더 검증, 프론트엔드 공통 요청 처리. 메일 웹훅에는 별도 예외 존재 | ‘요청 위조 방지 토큰 검증’. 모든 API에 예외 없이 적용된다는 표현은 피함 |
| HTML 입력 정제 | 게시물·메일 HTML의 허용 태그·속성·URL 정제. CMS iframe의 허용 영상 주소 제한 | ‘주요 HTML 입력 영역의 악성 스크립트 위험 감소’. 모든 XSS 가능성이 제거되었다는 표현은 피함 |
| 첨부파일 경로 보호 | 공통 저장소에서 절대경로·상위 경로 이탈 거부. 메뉴·월별 폴더와 UUID 파일명 사용. 발표자료는 확장자·선언된 Content-Type·용량·개수 검사 | ‘첨부파일 저장 경로 및 업로드 형식 검증’. 파일 암호화·백신 검사·파일 내용 기반 진위 검증과는 구분 |
| 회원 응답 정보 분리 | 회원 목록·상세 응답 DTO에 비밀번호 필드를 포함하지 않음 | ‘회원 조회 응답의 비밀번호 제외’. 연락처 마스킹까지 적용된 것은 아님 |

추가로 광고성 메일의 수신 거부 링크 검사와 발송 대상의 수신 거부 목록 제외 로직이 있습니다. 다만 외부 사용자가 실제 수신 거부를 완료하는 전체 흐름은 운영 IP 정책, CSRF 및 외부 발송 연동을 포함해 검증해야 하므로 발표 핵심 완료 항목에는 넣지 않는 편이 정확합니다.

사전등록에는 개인정보·등록 규정 동의 여부와 일시를 조회하는 구조가 있지만, 관리자 대리 등록은 동의 여부를 FALSE로 저장합니다. 회원 등록 API에서 동의 문구 버전·동의 일시를 수집하고 검증하는 흐름은 확인되지 않았으므로 ‘개인정보 동의 관리 완료’로 분류하지 않았습니다.

## 3. 추가 조치와 우선순위

| 우선순위 | 조치 | 프로젝트에 필요한 구체적인 보완 |
|---|---|---|
| 운영 전 우선 | 접속 비밀정보 분리 | `application.yaml`에 DB 계정·비밀번호 직접 지정이 확인됨. 환경변수 또는 비밀관리 저장소로 이전. 실제 운영값이었다면 저장소·배포물 노출 범위 확인 후 교체. 문서·발표자료에 값 자체를 기재하지 않음 |
| 운영 전 우선 | HTTPS 및 세션 쿠키 검증 | 외부 HTTPS 적용과 HTTP 전환 정책 확인. `Secure`, `HttpOnly`, 적절한 `SameSite` 및 보안 헤더의 실제 응답 검증. 운영 DB URL의 `useSSL=false`는 브라우저 HTTPS와 별개이므로 DB 통신 경로와 TLS 필요성도 확인 |
| 운영 전 우선 | 접근 권한 누락 방지 | Spring Security는 `/api/**`를 허용하고 MVC 인터셉터에서 역할을 검사하는 구조. 이를 무인증 공개로 단정할 수는 없지만, 새로운 경로나 예외의 검사 누락을 막기 위해 기본 거부·명시적 공개 정책을 검토하고 API별 권한 테스트 수행 |
| 운영 전 우선 | 계정 중지·권한 변경 즉시 반영 | 일반 관리자 인터셉터는 세션 속성만 검사. 계정 상태는 로그인·세션 조회 및 엑셀 반출에서 재확인. 계정 중지·삭제·권한 변경 시 기존 세션도 즉시 회수하도록 보완 |
| 높음 | 관리자 로그인 강화 | MFA 도입, 계정·IP별 시도 제한, 로그인 실패 기록·탐지, 오류 메시지 통일. 현재는 미등록·비활성·비밀번호 불일치가 구분되어 계정 상태가 추측될 수 있음. 임시 비밀번호의 유효기간·최초 변경 요구도 보완 |
| 높음 | 개인정보 표시·저장 보호 확대 | 대상 DB 컬럼의 저장 암호화는 구현됨. 화면·API 마스킹, 원문 조회 권한과 조회 사유 기록, 첨부파일·임시파일·백업 암호화를 위험도에 따라 확대 |
| 운영 전 우선 | 암호화 키 관리 | 개발·운영 환경마다 `DB_ENC_STRING`을 비밀관리 수단으로 주입하고 소스·로그·배포 문서에 기록하지 않음. 키 유실 방지, 접근권한, 백업, 교체 시 전체 재암호화 절차를 마련. 기존 암호문을 재암호화하지 않은 채 키를 바꾸면 복호화할 수 없음 |
| 높음 | 동의와 수집 항목 관리 | 필수·선택 항목과 목적 정리, 동의가 필요한 처리의 문구 버전·일시·처리 근거 기록, 선택적 홍보 수신 동의 분리, 관리자 대리 등록의 적정한 수집 근거 관리 |
| 높음 | 보유기간·파기 절차 | 회원정보뿐 아니라 초록 저자, 등록정보, 발송 대상 스냅샷, 업로드, 다운로드 파일, 백업의 보유기간과 삭제 범위를 정함. 파기 작업·결과 기록 및 열람·정정·삭제 요청 처리 절차 마련. 일반 삭제 API가 있다는 사실만으로 파기 정책 완료를 의미하지 않음 |
| 높음 | 감사기록 확대·보호 | 개인정보 원문 조회·변경·삭제, 권한 변경, 로그인 실패를 기록. 위변조 방지와 열람 권한 분리, 보관 정책 및 정기 점검 마련. 현재 로그인 기록 실패는 로그인 자체를 중단하지 않으므로 실패 알림·재시도 등 운영 방안 필요 |
| 높음 | 대량 반출 통제 | 다운로드 권한 분리, 필요 열만 추출, 대량 추출 시 추가 인증·승인, 비정상 반출 알림 검토. 엑셀 반출 기록은 반출 이후 복제·재전달을 차단하지 않으므로 조직의 파일 보관·전송·삭제 절차 병행 |
| 운영 확인 | 서버·DB·파일·백업 보안 | DB 계정 최소권한, DB 및 업로드 디렉터리 접근 제한, 백업 암호화와 복구 훈련, 업로드 악성코드 검사, 보안 패치·취약점 점검 수행. 개발용 테스트 계정·데이터 생성 기능의 운영 차단 여부 확인 |
| 운영 확인 | 개인정보 처리 업무 관리 | 개인정보 담당자와 취급자 지정, 권한 정기 점검·교육, 위탁 서비스 및 데이터 전달 항목 검토, 사고 대응 절차 마련. 메일·문자·결제 등 외부 연동도 포함 |

로그인 및 엑셀 감사 서비스는 IP를 `request.getRemoteAddr()`로 기록하는 반면 접근 제한은 `ClientIpResolver`를 사용합니다. 프록시 운영 시 감사기록에 실제 접속자 IP가 남는지 확인하고 IP 판별 기준을 일치시킬 필요가 있습니다.

## 4. 발표 표현의 구분

| 사용할 수 있는 표현 | 현재 근거로는 보류할 표현 |
|---|---|
| 비밀번호를 단방향 해시로 저장합니다. | 모든 개인정보를 암호화합니다. |
| 업무 DB의 이름·이메일·연락처 대상 컬럼을 암호화하여 저장합니다. | 애플리케이션의 모든 개인정보·첨부파일·백업을 암호화했습니다. |
| 관리자·심사자의 역할과 배정 범위에 따라 접근을 제한합니다. | 모든 업무에 세분화된 최소권한이 완비되어 있습니다. |
| 주요 개인정보 메뉴의 엑셀 다운로드 사유와 처리 이력을 기록합니다. | 모든 개인정보 이용을 추적하고 외부 유출을 원천 차단합니다. |
| 관리자 로그인 성공 이력을 기록합니다. | 개인정보 접속기록 관련 모든 요구사항을 충족했습니다. |
| HTTPS·백업 보안 등은 운영 환경에서 확인합니다. | 전 구간 암호화와 백업 암호화가 모두 적용되어 있습니다. |
| 보유기간·파기·동의 이력 관리를 추가 보완합니다. | 개인정보 생애주기 관리가 완성되어 있습니다. |

## 5. 주요 코드 근거

아래 경로는 프로젝트 루트 기준입니다.

- `src/main/java/com/bjworld21/congress/config/SecurityConfig.java:23`: BCrypt 인코더, CSRF 설정 및 API 허용 구조
- `src/main/java/com/bjworld21/congress/service/MemberService.java:87`: 회원 비밀번호 해시 저장
- `src/main/java/com/bjworld21/congress/config/PersonalDataProperties.java`: 환경변수 암호화 키 설정 바인딩과 필수값 검증
- `src/main/java/com/bjworld21/congress/repository/MemberRepository.java`: 회원 개인정보 암호화 저장·복호화 조회·검색
- `src/main/java/com/bjworld21/congress/repository/MemberPersonalDataSql.java`: 회원 개인정보 복호화 공통 SQL
- `src/main/java/com/bjworld21/congress/repository/AbstractSubmissionRepository.java`: 초록 저자 이름·이메일·전화번호 암복호화
- `src/main/java/com/bjworld21/congress/repository/AdminAccountRepository.java`: 관리자 이름·이메일 암복호화 및 암호문 로그인 조회
- `src/main/java/com/bjworld21/congress/repository/SponsorshipApplicationRepository.java`: 후원 신청 담당자·세금계산서 수신자 개인정보 암복호화
- `src/main/java/com/bjworld21/congress/repository/MailCampaignRepository.java`: 메일 발신자·수신자·수신 거부 개인정보 암복호화
- `src/main/java/com/bjworld21/congress/repository/SmsCampaignRepository.java`: 문자 발신번호·수신자 개인정보 암복호화
- `src/main/java/com/bjworld21/congress/repository/ProgramRepository.java`: 프로그램 담당자·발표자·좌장 이름 암복호화
- `src/main/java/com/bjworld21/congress/service/AdminService.java:56`: 관리자 로그인 검증 및 오류 구분
- `src/main/java/com/bjworld21/congress/config/CongressAdminWebMvcConfig.java:28`: IP·역할 인터셉터 적용 범위와 예외
- `src/main/java/com/bjworld21/congress/config/AdminSessionInterceptor.java`: 관리자 세션·역할 검사
- `src/main/java/com/bjworld21/congress/service/ReviewerReviewService.java:260`: 심사자 및 배정 소유권 검사
- `src/main/java/com/bjworld21/congress/config/AdminIpAccessInterceptor.java`: 허용 IP 검사 및 차단
- `src/main/java/com/bjworld21/congress/security/ClientIpResolver.java`: 신뢰 프록시 기반 IP 판별
- `src/main/resources/application.yaml:20`: 기본 세션 만료 60분
- `src/main/resources/application.yaml:32`: IP 접근 제한 기본 활성화
- `src/main/java/com/bjworld21/congress/controller/AdminController.java:71`: 로그인 시 세션 ID 변경
- `src/main/java/com/bjworld21/congress/service/AdminAccessLogService.java`: 로그인 성공 이력 저장
- `src/main/java/com/bjworld21/congress/service/ExcelDownloadAuditService.java:37`: 사유 검증, 요청자 재확인 및 감사기록 처리
- `src/main/java/com/bjworld21/congress/service/ExcelDownloadAuditLogWriter.java`: 감사기록 별도 트랜잭션과 저장 실패 처리
- `src/main/java/com/bjworld21/congress/service/ExcelExportType.java`: 적용된 엑셀 4개 유형
- `frontend/src/adminSession.ts`: 프론트엔드 CSRF 토큰 요청 처리
- `src/main/java/com/bjworld21/congress/service/CmsHtmlSanitizer.java`: HTML 정제
- `src/main/java/com/bjworld21/congress/service/BoardPostService.java:240`: 게시물 저장 시 HTML 정제 호출
- `src/main/java/com/bjworld21/congress/service/MailCampaignService.java:281`: 메일 저장 시 HTML 정제 호출
- `src/main/java/com/bjworld21/congress/service/UploadStorage.java`: 공통 파일 경로 검증
- `src/main/java/com/bjworld21/congress/service/AbstractPresentationAttachmentService.java`: 발표자료 UUID 저장 및 업로드 제한
- `src/main/java/com/bjworld21/congress/dto/MemberListResponse.java`: 회원 응답에서 비밀번호 제외, 연락처 원문 필드 존재
- `src/main/java/com/bjworld21/congress/repository/PreRegistrationRepository.java:244`: 관리자 등록 시 동의 값을 FALSE로 저장
- `src/main/java/com/bjworld21/congress/controller/MemberRegistrationController.java`: 회원 등록 요청 항목
- `src/main/java/com/bjworld21/congress/service/MailCampaignService.java:198`: 광고성 메일 수신 거부 링크 검사

암호화 변경은 Java 및 테스트 소스 컴파일, 전체 대상 Mapper의 MyBatis 키 파라미터 계약, 회원과 연계 업무의 복호화 SQL 테스트로 확인했습니다. 개발 DB에서 68개 대상 컬럼이 `VARBINARY` 또는 `LONGBLOB`으로 변경된 것도 확인했습니다. 운영 반영 전에는 동일 DDL과 일괄 암호화 절차를 백업·복구 계획과 함께 적용하고, 실제 운영 키로 로그인·조회·등록·메일·문자·엑셀 기능을 점검해야 합니다.

## 6. 추가 대책 참고자료

- 기본 거부 및 요청마다 권한 확인: [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)
- HTTPS·쿠키·세션 관리: [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- MFA·로그인 시도 제한·인증 오류 응답: [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- 저장 데이터 최소화와 암호화 설계: [OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)
- 비밀정보 관리: [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)

보유기간, 동의 방식, 처리위탁 등은 실제 처리 목적·항목·규모와 적용 규정에 맞춰 확정해야 합니다. 이 문서는 특정 보유기간이나 법적 의무의 충족 여부를 판정하지 않습니다.
