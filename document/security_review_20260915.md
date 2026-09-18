# 현재 구현 기준 보안 점검 보고서

점검일: 2026-09-15

## 1. 종합 판단

인증·접근 제어·CSRF·HTML 정화·개인정보 암호화·다운로드 감사 등 기본 보호 장치는 상당 부분 구현되어 있습니다. 다만 비밀값 관리, 계정 권한 회수, 로그인 시도 제한, 운영용 테스트 기능 분리에는 우선 보완이 필요합니다.

이번 결과는 현재 작업 트리의 소스를 읽고 기존 테스트를 실행한 결과입니다. 운영 서버 침투 테스트, DB 내용 조회, 실제 자격증명 유효성 검사, 실제 결제 또는 메일 발송은 수행하지 않았습니다. 배포환경의 환경변수·프록시·방화벽이 소스 기본값을 보완하는지는 확인하지 않았습니다.

AGENTS.md에는 백엔드가 초기 상태라고 적힌 부분이 있으나, 실제 구현에는 다수의 컨트롤러·서비스·저장소가 존재하므로 소스를 우선했습니다.

우선순위:

- **P1**: 운영 중 우선 조치하거나 해당 기능의 정식 운영 전에 해결.
- **P2**: 다음 보안 개선 작업에서 수정.
- **운영 확인**: 소스만으로 실효성을 확정할 수 없는 항목.

## 2. 구현된 보호 장치

| 영역 | 확인한 구현 | 평가 및 범위 |
|---|---|---|
| 비밀번호 저장 | [SecurityConfig](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/config/SecurityConfig.java:23)의 BCryptPasswordEncoder, 관리자·회원 저장 시 encode 사용 | 비밀번호를 평문이나 복호화 가능한 값으로 저장하지 않음. 암호화된 개인정보와 비밀번호 해시는 별개임 |
| CSRF | [SecurityConfig](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/config/SecurityConfig.java:28)의 세션 기반 토큰과 X-CSRF-TOKEN 헤더, [adminSession.ts](D:/dev/java/intellij/bjworld21_cms_congress/frontend/src/adminSession.ts:60)의 자동 전송 | 세션 쿠키를 사용하는 변경 요청을 보호. 메일 webhook만 명시적 예외 |
| 로그인 세션 고정 방어 | [AdminController](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/AdminController.java:74), [PublicMemberAuthController](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/PublicMemberAuthController.java:70)의 로그인 시 세션 ID 변경 | 기존 세션 ID를 로그인 후에도 계속 사용하는 문제를 줄임 |
| 역할과 소유권 검사 | 관리자·심사자 인터셉터, [AbstractSubmissionService](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AbstractSubmissionService.java:247), [ReviewerReviewService](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/ReviewerReviewService.java:281) | 회원 본인의 초록인지, 심사자에게 배정된 건인지 서버에서 검사. 회원 학회 정보도 세션에서 확인 |
| 관리자 IP 제한 | [AdminIpAccessInterceptor](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/config/AdminIpAccessInterceptor.java:37), [ClientIpResolver](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/security/ClientIpResolver.java:23) | 허용목록에 없는 주소 차단. 신뢰한 프록시에서 온 경우에만 X-Forwarded-For 사용. 단, 만료 처리 문제는 아래 참조 |
| 개인정보 암호화 및 SQL 바인딩 | [MemberRepository](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/repository/MemberRepository.java:93) 등에서 AES_ENCRYPT/AES_DECRYPT와 MyBatis 바인딩 사용 | 회원·관리자·연락처 등의 주요 필드가 암호화 대상. 조사한 repository Java에서 MyBatis 문자열 치환 ${...}는 발견하지 않음. 모든 개인정보·로그·파일이 암호화된다는 뜻은 아님 |
| HTML 정화와 미리보기 격리 | [CmsHtmlSanitizer](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/CmsHtmlSanitizer.java:52), [MailHtmlSanitizer](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/MailHtmlSanitizer.java:37), [publicMenuContent.ts](D:/dev/java/intellij/bjworld21_cms_congress/frontend/src/publicMenuContent.ts:18) | 허용 태그·속성·프로토콜·CSS만 보존. 공개 게시물·메뉴는 출력 시에도 정화. 관리자 미리보기에 iframe sandbox와 CSP 적용 |
| 위험 링크 검사 | [PopupService](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/PopupService.java:67), [WebRiskLinkValidator](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/WebRiskLinkValidator.java:26) | 팝업 외부 링크를 Web Risk로 검사하고 위험 링크 저장 거부. 검사 실패도 예외로 처리. 다른 모든 링크나 업로드 파일 검사까지 제공하는 기능은 아님 |
| 업로드 경로 보호 | [UploadStorage](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/UploadStorage.java:52) | 메뉴 허용목록, UUID 저장명, 월별 상대 경로, 절대 경로·상위 경로 이탈 차단 |
| 본문 이미지 검증 | [BoardContentImageService](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/BoardContentImageService.java:117) 및 메일·팝업 이미지 서비스 | ImageIO로 이미지 종류를 식별하고 가로·세로 및 총 픽셀 제한. 확장자만 확인하는 것보다 강화됨 |
| 엑셀 반출 감사 | [ExcelDownloadAuditService](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/ExcelDownloadAuditService.java:42) | 사유, 관리자, 검색조건, IP, 성공·실패 기록. 실행 시 계정 활성 상태를 다시 확인. 감사 시작/성공 기록이 실패하면 정상 파일 응답을 반환하지 않는 구조 |
| 결제 요청 무결성 일부 | [PublicPreRegistrationService](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/PublicPreRegistrationService.java:265) | 회원 소유권, 등록기간, 납부 상태 확인. 금액을 서버 DB에서 구성하고 통화·금액 일치 여부 검사. 브라우저 콜백만으로 PAID 처리하지 않음 |

## 3. 우선 보완 항목

### F01. P1 — 비밀값이 Git 추적 설정 파일에 포함됨

**확인:** [application.yaml](D:/dev/java/intellij/bjworld21_cms_congress/src/main/resources/application.yaml:7)에 DB 비밀번호가 리터럴로 들어 있고, 36행 개인정보 암호화 키도 리터럴입니다. 29행 Web Risk API 키와 85행 SMTP 비밀번호는 환경변수 미설정 시 사용하는 비어 있지 않은 기본값이 존재합니다. 이 파일은 Git 추적 대상입니다.

**영향:** 저장소나 빌드 산출물 접근자가 자격증명을 얻을 수 있습니다. DB 데이터와 암호화 키가 함께 노출되면 컬럼 암호화의 보호 효과가 줄어듭니다. 실제 운영에서 유효한 값인지는 검사하지 않았습니다.

**조치:** 비밀값과 비밀값 기본값을 제거하고 환경별 비밀 저장소에서 주입합니다. 실제 사용한 값은 교체합니다. 개인정보 키 변경은 기존 데이터 복호화·재암호화·복구 계획과 함께 수행해야 하며 단순 설정값 교체로 끝내면 안 됩니다. Git 과거 이력 및 이미 만들어진 배포물도 노출 범위를 점검합니다.

### F02. P1 — 관리자 정지·삭제·권한 변경이 기존 세션에 즉시 반영되지 않음

**확인:** [AdminSessionInterceptor](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/config/AdminSessionInterceptor.java:20)는 세션의 adminSeq/adminRole만 확인합니다. [AdminService](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AdminService.java:413)의 삭제·비밀번호 재설정은 계정 데이터를 바꾸지만 계정의 전체 세션을 폐기하지 않습니다.

[AdminController](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/AdminController.java:147)의 /session 호출은 DB 상태를 재검사합니다. 하지만 호출자가 이 API를 건너뛰고 업무 API를 직접 호출할 수 있어 요청마다 적용되는 보호는 아닙니다. [AdminSelfController](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/AdminSelfController.java:34)는 본인 비밀번호 변경 시 현재 세션은 폐기하지만 다른 브라우저의 세션까지 폐기하지 않습니다. 엑셀 다운로드는 별도 활성 상태 재검사라는 예외가 있습니다.

**영향:** 정지된 계정이나 비밀번호를 재설정한 계정의 기존 세션이 일부 관리 기능을 계속 실행할 수 있습니다. 60분은 유휴 만료 시간이라 계속 요청하는 세션의 최대 수명을 보장하지 않습니다.

**조치:** 계정별 세션 버전 또는 세션 저장소를 사용해 정지·삭제·권한 변경·비밀번호 변경 시 해당 계정의 세션을 일괄 무효화합니다. 각 인증 요청에서 현재 유효 상태를 강제 확인합니다.

### F03. P1 — 테스트 데이터 생성이 운영 프로필에서 차단되지 않음

**확인:** [DailyDashboardTestDataScheduler](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/config/DailyDashboardTestDataScheduler.java:29)는 매일 00:01 테스트 데이터를 생성하며 프로필/활성화 조건이 없습니다. [DailyDashboardTestDataService](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/DailyDashboardTestDataService.java:97)는 회원·사전등록·초록을 생성하고 회원에 공통 고정 비밀번호를 사용합니다. 대상 학회 기본값은 1입니다. [TestDataController](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/TestDataController.java:15) 등의 수동 생성 API에도 운영 프로필 차단은 없습니다.

**영향:** 대상 학회 등 실행 조건이 충족된 운영환경에서는 가상 계정과 업무 데이터가 생성될 수 있습니다. 고정 비밀번호로 생성된 회원은 별도 테스트 계정 로그인 차단이 확인되지 않습니다. 이는 실제 데이터 오염 및 예측 가능한 계정 생성 위험입니다.

**조치:** 테스트 서비스·스케줄러·API를 개발/테스트 프로필로 제한하고 기본 비활성화합니다. 운영에 이미 생성됐는지 먼저 조회한 뒤 삭제 대상을 식별합니다. 이번 점검에서 운영 데이터 생성이나 삭제는 하지 않았습니다.

### F04. P1 — 로그인 시도 제한 및 관리자 추가 인증이 없음

**후속 구현 (2026-09-15):** 관리자 로그인과 캐시 복구 인증에 계정별 비밀번호 5회 연속 실패 잠금을 추가했습니다. 다른 관리자의 비밀번호 초기화 전까지 잠금을 유지합니다. 적용에는 `admin_login_lockout_migration.sql`의 선행 실행이 필요하며, 이번 코드 작업에서 대상 DB에 SQL을 실행하지 않았습니다. 동작과 적용 절차는 [관리자 로그인 잠금](admin_login_lockout.md)을 참조합니다. 아래는 최초 점검 당시 기록이며, IP별 제한·MFA·실패 이벤트 기록과 일반 회원 로그인 제한은 추가 과제입니다.

**확인:** [AdminService](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AdminService.java:61)와 [PublicMemberAuthController](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/PublicMemberAuthController.java:47)에서 계정/IP별 실패 횟수 제한·점진적 지연·잠금·MFA 처리를 찾지 못했습니다.

특히 [AdminCacheRecoveryController](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/AdminCacheRecoveryController.java:37)의 /api/admin/cache/reload는 IP 제한 예외이며 관리자 ID/비밀번호로 인증합니다. 따라서 일반 관리자 로그인에 IP 제한이 있어도 외부에서 관리자 자격증명을 반복 검증할 수 있는 별도 경로가 남습니다. CSRF 토큰은 정상 방문자도 발급받을 수 있어 로그인 시도 제한을 대신하지 않습니다.

관리자 로그인 오류는 미등록 계정·비활성 계정·비밀번호 오류를 구분합니다. 일반 회원 로그인은 공통 오류 문구를 사용합니다.

**조치:** 공개 로그인과 복구 API에 계정/IP별 요청 제한, 실패 이벤트 기록, 관리자 MFA를 적용합니다. 관리자 오류 응답을 통일하고, 복구 API에는 별도 네트워크 경계나 짧은 수명의 복구 자격증명을 적용합니다. 인증 정책 방향은 [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)를 참고합니다. 외부 WAF의 제한 여부는 별도 확인이 필요합니다.

### F05. P2 — 일반 회원 비밀번호 변경에 재인증이 없음

**확인:** [PublicMemberRegistrationController](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/PublicMemberRegistrationController.java:105)의 /profile은 로그인 세션과 새 비밀번호 확인값만으로 변경합니다. 현재 비밀번호 검증이 없고, [MemberService](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/MemberService.java:148)에서 변경 후 현재/다른 세션 폐기도 없습니다.

**영향:** 탈취한 세션이나 공유 PC의 열린 로그인 상태만으로 비밀번호를 변경해 계정 접근을 유지할 수 있습니다.

**조치:** 비밀번호 변경을 별도 흐름으로 분리해 현재 비밀번호 또는 최근 재인증을 요구하고, 변경 시 세션 ID 갱신 및 다른 세션 폐기, 변경 알림을 적용합니다.

### F06. P2 — 초록 상세 화면에 저장형 XSS 경로가 있음

**확인:** [user.js](D:/dev/java/intellij/bjworld21_cms_congress/src/main/resources/static/public/js/user.js:866)의 fillRows는 셀 값을 innerHTML로 넣습니다. [호출 부분](D:/dev/java/intellij/bjworld21_cms_congress/src/main/resources/static/public/js/user.js:1069)은 저자 이름·기관명·부서·이메일을 전달합니다. [AbstractSubmissionService](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AbstractSubmissionService.java:568)의 입력 검사는 이 값들을 HTML 이스케이프하거나 제거하지 않고 저장 경로로 넘깁니다.

**영향:** 해당 필드에 HTML 이벤트 속성 등이 저장되면 상세 화면이 이를 실행 가능한 DOM으로 해석할 수 있습니다. 다른 CMS HTML 경로의 sanitizer는 이 표 출력 경로에 적용되지 않습니다.

**범위:** 확인한 공개 상세 API는 본인 초록만 조회합니다. 따라서 이를 곧바로 다른 회원·관리자에게 전파되는 XSS라고 판단하지 않습니다. 관리자 작성·가져오기 등 다른 경로로 해당 필드가 유입되는 경우의 영향은 추가 검증이 필요합니다. 실제 브라우저 공격 실행은 수행하지 않았습니다.

**조치:** 텍스트 셀은 textContent를 사용하고, Presenting/Corresponding 배지는 DOM 요소로 별도 생성합니다. 사용자 문자열을 HTML 배지와 같은 출력 방식으로 처리하지 않습니다.

### F07. P2 — IP 허용기간 만료가 캐시에 반영되지 않음

**확인:** [AdminIpAllowlistRepository](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/repository/AdminIpAllowlistRepository.java:33)는 조회 시점의 날짜로 시작일·종료일을 검사합니다. [AdminIpAccessCache](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AdminIpAccessCache.java:42)는 이 결과를 IP 범위만 저장하고, 요청 시에는 날짜를 검사하지 않습니다. 갱신은 시작 시·CRUD 이벤트·수동 재적재 때 이루어지며 날짜 변경을 위한 자동 갱신은 찾지 못했습니다.

**영향:** 어제까지 허용된 IP가 오늘도 계속 허용될 수 있습니다. 반대로 오늘부터 허용된 IP가 갱신 전까지 차단될 수 있습니다.

**조치:** 캐시에 유효기간을 포함해 매 요청 검사하거나 날짜 변경/짧은 TTL에 맞춰 갱신합니다. 서버가 여러 대이면 변경 전파 및 갱신 실패 시 정책도 정의합니다.

### F08. P2 — DB 연결에서 TLS가 비활성화됨

**확인:** 기본 설정과 prod, apdrc8, apdrc8-oper 프로필의 JDBC URL에 useSSL=false가 있습니다. [운영 프로필](D:/dev/java/intellij/bjworld21_cms_congress/src/main/resources/application-prod.yaml:5)

**영향:** 실제 DB 접속이 네트워크를 통과하면 SQL 파라미터로 전달되는 개인정보·암호화 키 등의 전송 보호를 기대하기 어렵습니다. 컬럼 암호화가 DB 연결의 암호화를 대신하지 않습니다. 서버 로컬 접속·터널·외부 설정 덮어쓰기 여부에 따라 실제 노출 범위는 달라집니다.

**조치:** 운영 연결 경로를 확인하고 네트워크 접속에는 서버 인증서를 검증하는 TLS를 적용합니다. 적용 후 드라이버 옵션과 실제 협상된 암호화 연결을 확인합니다.

### F09. P1(실결제 운영 전) — 결제 승인 검증·확정·재처리 단계가 미구현

**확인:** [PayGatePaymentGatewayAdapter](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/payment/paygate/PayGatePaymentGatewayAdapter.java:84)는 결제창 요청을 준비합니다. [사용자 결제 코드](D:/dev/java/intellij/bjworld21_cms_congress/src/main/resources/static/public/js/user.js:1402)에도 브라우저 콜백을 최종 승인으로 처리하지 않는다고 명시되어 있습니다. 결제 패키지/컨트롤러 조사에서 PG 서버 승인 조회·서명 검증·결과 영속화·중복 통지 방지 구현을 찾지 못했습니다.

**평가:** 브라우저 결과만으로 PAID로 바꾸지 않는 점은 바람직합니다. 그러나 승인 검증 체계가 완성된 결제 시스템으로 평가할 수는 없습니다. 현재 소스만으로 무결제 PAID 조작이 가능하다고 단정하는 항목은 아닙니다.

**조치:** PG가 제공하는 서버 검증 절차에 따라 주문번호·등록건·금액·통화·가맹점·거래번호를 대조하고, 중복 통지 및 상태 경쟁을 처리하는 승인 이력과 트랜잭션을 구현합니다. 실제 PG 계약/문서와 대조한 별도 구현 검증이 필요합니다.

### F10. P2 — API 권한 정책이 URL별 MVC 인터셉터에 의존

**확인:** [SecurityConfig](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/config/SecurityConfig.java:54)는 /api/**를 permitAll로 설정하며 [ConferenceAdminWebMvcConfig](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/config/ConferenceAdminWebMvcConfig.java:30)의 인터셉터와 공개 컨트롤러별 검사가 실제 인증 경계를 구성합니다.

**평가:** 현재 관리자 API가 전부 익명 공개라는 뜻은 아닙니다. 확인한 관리자 경로는 인터셉터가 보호합니다. 다만 새로운 경로를 보호 패턴 밖에 만들거나 수동 검사를 누락할 때 보호가 빠질 수 있는 구조입니다. [Spring 공식 문서](https://docs.spring.io/spring-framework/reference/7.1/web/webmvc/mvc-config/interceptors.html)도 인터셉터를 주 보안 계층으로 사용하는 방식의 한계를 설명합니다.

**조치:** 인증 정보를 Spring Security의 인증 객체로 연결하고 공개 API만 명시적으로 허용하는 기본 차단 정책으로 전환합니다. permitAll만 제거하면 현재 수동 세션 로그인이 Spring Security 인증으로 인식되지 않으므로 인증 흐름도 함께 변경해야 합니다.

## 4. 추가 강화 및 운영 확인

1. **최소 권한:** [AdminRolePolicy](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/config/AdminRolePolicy.java:8)는 maintenance를 admin과 동일한 전체 관리자 권한으로 취급합니다. 유지보수 계정이 회원정보·관리자 계정·설정에 전부 접근하는 것이 업무 정책상 필요한지 확인하고, 아니라면 메뉴 및 행위별 권한을 나눠야 합니다. 현재 정책과의 불일치를 확인한 것은 아닙니다.
2. **개인정보 암호화 방식:** 현재 SQL은 같은 키로 동일 값을 동일 암호문으로 만들어 조회하는 방식이며 키 버전·인증 태그·키 순환 구조가 확인되지 않습니다. SHA2(..., 512)는 키를 만드는 표현이지 AES-512라는 의미가 아닙니다. [MariaDB AES_ENCRYPT 문서](https://mariadb.com/docs/server/reference/sql-functions/secondary-functions/encryption-hashing-and-compression-functions/aes_encrypt)와 [11.2 변경 안내](https://mariadb.com/resources/blog/announcing-mariadb-community-server-11-1-ga-and-11-2-rc/)에 비추어, MariaDB 10.6에 새 버전의 IV/모드 옵션을 그대로 적용하면 안 됩니다. 장기적으로 애플리케이션 계층의 인증 암호화, 검색용 별도 HMAC, 키 버전을 검토합니다.
3. **세션 쿠키·HTTPS·CSP:** application 프로필에서 세션 쿠키 Secure/SameSite 및 HTTPS 강제 설정이 명시되지 않았습니다. 미리보기 전용 CSP는 있으나 애플리케이션 전체 CSP는 찾지 못했습니다. Spring Security 기본 헤더까지 없다고 판단한 것은 아닙니다. 프록시 포함 실제 응답의 Secure·HttpOnly·SameSite, HSTS, CSP를 확인해야 합니다.
4. **문서 업로드 검사:** [AbstractPresentationAttachmentService](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AbstractPresentationAttachmentService.java:175)는 크기·확장자·클라이언트 MIME을 검사하지만 이미지 업로드처럼 실제 파일 구조를 검사하지는 않습니다. 악성코드 검사·격리·문서 무해화 연계도 찾지 못했습니다. 승인된 초록 소유자만 업로드할 수 있다는 접근 제한은 존재합니다.
5. **감사 범위:** 성공 로그인과 엑셀 반출 감사는 있으나 로그인 실패·권한 변경·IP 정책 변경·비밀번호 초기화 등에 대한 통합 보안 이벤트 기록 및 경보를 보완할 필요가 있습니다. IP 정책의 작성자/수정자 필드와 차단 서버 로그는 존재합니다. 현재 로그인/엑셀 로그의 IP는 getRemoteAddr()라 프록시 운영 시 실제 사용자 IP 대신 프록시가 기록될 수 있습니다.
6. **메일 webhook:** [MailWebhookController](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/MailWebhookController.java:37)는 adapter.verify 성공 후에만 이벤트를 처리합니다. 하지만 실제 MailWebhookAdapter 구현체는 찾지 못했으므로 특정 공급자의 검증이 완성됐다고 평가하지 않습니다.
7. **공개 가입 검증:** 이메일 형식·중복 및 개인정보 동의를 검사하지만 이메일 소유 확인 절차는 찾지 못했습니다. 잘못된 주소 또는 타인 주소로 가입하는 것을 막아야 하는 운영 정책이면 이메일 인증을 추가합니다.
8. **공급망·운영 보호:** 라이브러리 최신 CVE 전수 조사, 자동 의존성 취약점 검사, 운영 DB 최소 권한, 백업 암호화·복구, 비밀값 저장소, 로그 접근 제한, WAF 설정은 이번 범위에서 확인하지 않았습니다. 버전 번호만으로 안전/취약을 단정하지 않습니다.

## 5. 권장 적용 순서

1. 실제 사용한 비밀값 교체와 설정 외부화. 개인정보 키 교체는 데이터 이관 계획 선행.
2. 운영 테스트 스케줄러/API 비활성화와 기존 테스트 계정 현황 확인.
3. 관리자 세션 강제 회수 및 로그인/복구 API 시도 제한.
4. 회원 비밀번호 재인증, 초록 표의 안전한 텍스트 출력, IP 허용기간 재검사.
5. DB TLS 및 배포 응답 헤더 검증.
6. 실결제 운영 전 서버 승인 검증 체계 완성.
7. Spring Security 중심의 기본 차단 정책, 최소 권한, 감사·파일 검사 강화.

## 6. 검증 결과와 제한

프로젝트 전용 Java 17 실행기 gradlew17.bat으로 기존 테스트 **19개 클래스, 75개 테스트**를 두 번에 나누어 실행했고 모두 통과했습니다. 실패·오류·건너뜀은 없었습니다.

- 1차 66개: 관리자/역할/IP 인터셉터, IP 캐시, 관리자 자격증명, CIDR, 업로드 경로, CMS/메일 HTML 정화, 엑셀 감사, 공개 회원 로그인/가입, 공개 초록, 공개 사전등록 서비스, 심사자 서비스.
- 2차 9개: FreeRecipientControllerTest, SocietyMemberControllerTest, AbstractSimilarityReviewControllerTest, PublicPopupControllerTest. Spring Security 필터와 CSRF를 포함한 기존 접근 제어 테스트.
- 실행 형태: gradlew17.bat test -x processResources --tests '선택한 테스트 클래스'. 프론트엔드 빌드를 제외했고 애플리케이션을 운영 설정으로 기동하지 않았습니다.
- 네트워크 제한으로 첫 Gradle 다운로드 시도가 실패했으나, 허용된 확장 실행에서 테스트가 정상 완료됐습니다.
- 테스트 통과는 해당 테스트가 다루는 동작만 입증합니다. 이번에 발견한 모든 결함에 대한 재현 테스트를 새로 작성한 것은 아닙니다.
- 브라우저 실행 기반 XSS 재현, 실제 HTTP 헤더/쿠키, 계정 정지 후 운영 API 재호출, 자정 이후 실제 IP 만료, PG 승인 통신은 소스 분석 결과와 구분해 후속 검증해야 합니다.
- 애플리케이션 소스와 운영 데이터는 수정하지 않았습니다. 이 점검 문서만 추가했습니다.
