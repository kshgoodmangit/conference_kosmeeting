# 사용자 페이지 기능 변경 요약

작성일: 2026-09-14  
범위: 공개 사용자 페이지, 사용자 전용 API, 사용자 접수 기간 설정 및 관련 테스트

## 한눈에 보는 변경 내용

| 영역 | 변경 내용 |
|---|---|
| 회원 | 국내·국외 회원가입, 로그인, 로그아웃, 프로필 수정 기능 연결 |
| 세션 | 로그인한 회원과 학회 식별자를 함께 저장하고 최신 학회 변경 시 기존 세션 만료 |
| 마이페이지 | 회원 정보, 초록 목록, 등록 현황, 증명서 메뉴 및 전용 화면 추가 |
| 초록 | 사용자 전용 작성·임시저장·제출·수정·조회·임시저장 삭제 API/UI 추가 |
| 발표자료 | 채택된 본인 초록의 발표자료 업로드·다운로드·삭제 기능 추가 |
| 온라인등록 | 등록비·옵션 조회, 신청 저장, 결제창 호출, 미결제 신청 취소 기능 추가 |
| 접수 기간 | 개발 설정에서는 사용자 접수 제한만 우회하고 운영에서는 실제 기간 적용 |
| 홈 화면 | 등록/초록 CTA와 `OPEN`, `CLOSED`, D-day를 실제 날짜 기준으로 표시 |
| 공통 UI | 로그인 상태별 헤더·모바일 메뉴, Select2, 회원/접수 화면 스타일 및 반응형 보완 |
| 증명서 | 로그인 회원이 현재 정적 `certificate.png`를 다운로드하는 기능 추가 |
| 테스트 | 공개 회원·초록·등록·증명서·기간 제어 및 템플릿 테스트 추가 |

## 상세 변경 사항

### 1. 회원가입·로그인·세션

- 국내 회원과 국외 회원 가입 화면을 분리하고 `/api/public/members/register`에 연결했습니다.
- 회원 유형, 국가, 이메일, 비밀번호 확인, 휴대전화 형식, 개인정보 동의를 서버에서 검증합니다.
- 로그인 시 이메일을 정규화하고 비밀번호를 확인한 뒤 세션 ID를 교체합니다.
- 세션에 `memberSeq`와 `memberConferenceSeq`를 함께 저장합니다.
- 이후 사용자 전용 요청마다 저장된 학회와 현재 최신 학회를 비교합니다.
- 최신 학회가 변경됐거나 세션 값이 불완전하면 기존 회원 세션을 무효화하고 재로그인을 요구합니다.
- 로그아웃 시 일부 속성만 지우지 않고 세션 전체를 무효화한 뒤 홈으로 이동합니다.
- 로그인 상태에 따라 데스크톱·모바일 헤더가 다음처럼 표시됩니다.
  - 비로그인: `LOGIN`, `SIGN UP`
  - 로그인: `LOGOUT`, `MY PAGE`
- 모바일 최상위 메뉴는 실제 하위 메뉴가 있을 때만 기본 이동을 막도록 수정했습니다.

### 2. 마이페이지와 공개 페이지 라우팅

- CMS 사용자 메뉴의 `menuKey`를 이용해 회원 전용 템플릿을 연결했습니다.
- 마이페이지 하위 경로는 부모 메뉴의 인증 요구사항도 상속하도록 보호했습니다.
- 별도 CMS 메뉴 행이 없는 초록 작성·검토 화면은 `mypage-abstract` 메뉴를 재사용합니다.
- 마이페이지에 회원 기본 정보, 초록 목록, 초록 마감 D-day를 표시합니다.
- 현재 날짜가 Early-bird 또는 Regular 기간인지 판단해 해당 등록 단계와 남은 날짜를 표시합니다.
- 로그인 페이지가 자기 자신으로 다시 리다이렉트되지 않도록 일반 공개 페이지 렌더링 흐름에 통합했습니다.
- 접수 기간이 닫힌 사용자 페이지에는 공개 안내 화면을 반환합니다.
- 사용하지 않던 홈의 `quickMenus` 모델 값과 관련 테스트 의존성을 제거했습니다.

### 3. 홈 CTA와 날짜 표시

- 홈에서 빠져 있던 `registrationOpened`, `abstractOpened` 모델 값을 제공합니다.
- 온라인등록 CTA는 실제 Early-bird 또는 Regular 등록 기간에만 표시합니다.
- 초록접수 CTA는 실제 초록 접수 기간에만 표시합니다.
- 등록 시작일은 무조건 Regular 날짜를 사용하지 않고 현재·다음 Early-bird/Regular 일정을 기준으로 선택합니다.
- 등록 마감 후 `OPEN`이 남는 문제를 수정해 `CLOSED`로 표시합니다.
- `Register Now`, `OPEN/CLOSED`, D-day는 개발/운영 설정과 관계없이 실제 학회 날짜대로 표시합니다.
- 개발 환경의 기간 검사 해제는 화면 표시가 아니라 실제 접수 차단에만 적용합니다.

### 4. 사용자 초록 접수

- `/api/public/abstracts` 사용자 전용 API를 추가했습니다.
  - 메타데이터 조회
  - 본인 초록 목록·상세 조회
  - 초록 작성·임시저장·제출·수정
  - 본인 임시저장 초록 삭제
- 초록 입력 화면에 다음 항목을 연결했습니다.
  - AI 사용 여부, 사용 도구·범위 및 기타 항목
  - 기관 목록과 저자 목록, 순서 변경, 발표자·교신저자 지정
  - 발표 유형과 초록 카테고리
  - Objective, Methods, Results, Conclusion 구조화 본문
- 브라우저와 서버 양쪽에서 초록 본문 300단어 제한을 검사합니다.
- 사용자 초록은 `draft` 또는 `submitted` 상태로만 저장할 수 있습니다.
- 기존 임시저장은 제출완료로 전환할 수 있고, 심사가 시작된 초록은 수정할 수 없습니다.
- 초록 조회·수정·삭제 시 로그인 회원의 소유권과 `submissionSource='member'`를 확인합니다.
- 임시저장 삭제 시 대상 행을 잠근 뒤 상태와 소유권을 다시 확인합니다.
- 관리자용 기존 초록 등록·수정·삭제 흐름은 유지하고 사용자 전용 메서드를 별도로 추가했습니다.

### 5. 발표자료 등록

- 채택된 본인 초록에 한해 발표자료를 업로드·다운로드·삭제할 수 있습니다.
- 기존 파일 검증 규칙을 그대로 재사용합니다.
  - PDF, PPT, PPTX
  - 파일당 최대 100MB
  - 초록당 최대 5개
- 사용자 업로드는 발표자료 등록 기간과 소유권을 검사합니다.
- 개발 설정에서 기간 검사를 끄더라도 채택 상태와 본인 소유권 검사는 유지합니다.
- 사용자 삭제도 본인 초록과 첨부파일 관계를 확인하며, 관리자용 메서드는 변경하지 않았습니다.

### 6. 온라인 사전등록과 결제 시작

- `/api/public/pre-registrations` 사용자 전용 API를 추가했습니다.
  - 등록 폼 데이터 조회
  - 사전등록 생성
  - 결제 준비
  - 미결제 등록 취소
- 회원 유형에 따라 국내 회원은 KRW, 국외 회원은 USD 등록비를 사용합니다.
- 활성 등록 카테고리와 현재 통화·등록 단계에 맞는 금액만 표시합니다.
- 추가 옵션은 사용 여부, 판매 기간, 가격, 최대 수량, 전체 잔여 수량을 검사합니다.
- 등록비와 옵션 금액은 브라우저 값을 신뢰하지 않고 서버에서 다시 계산합니다.
- 개인정보 및 등록 약관 동의를 서버에서 필수로 확인합니다.
- 회원 행과 기존 등록 건을 잠가 동일 회원의 중복 제출을 방지합니다.
- 저장 후 `PR-{연도}-{6자리 번호}` 형식의 등록번호를 발급합니다.
- 총액이 0원이면 `FREE/PAID`, 유료이면 `UNPAID` 상태로 생성합니다.
- 결제 준비 전에 저장된 등록 금액·통화와 PG 준비 결과를 다시 비교합니다.
- `UNPAID` 또는 `FAILED` 신청만 사용자가 취소할 수 있고 결제 완료 건은 취소하지 못하게 했습니다.

### 7. 사용자 접수 기간 적용

- 개발·운영 환경 모두 관리자에 설정된 실제 학회 접수 기간을 적용합니다.
- 적용 범위는 사용자 기능으로 제한했습니다.
  - 초록 접수 페이지 진입 및 작성·수정·임시저장 삭제
  - 채택 초록 발표자료 업로드
  - 온라인등록 페이지 진입, 신청, 옵션 판매 기간 및 결제 시작
- 기존 `--app.test-date` 방식과 관련 설정은 제거했습니다.
- 홈과 마이페이지의 날짜·D-day도 같은 기준일로 계산합니다.

### 8. 날짜 기준 통일

- 애플리케이션 공용 `Clock` Bean을 추가하고 기준 시간대를 `Asia/Seoul`로 고정했습니다.
- 사용자 접수 기간, 등록 단계, D-day, 등록번호 연도 계산에서 같은 Clock을 사용합니다.
- 테스트에서는 고정 Clock을 주입할 수 있어 날짜 경계 테스트가 가능해졌습니다.

### 9. 증명서 다운로드

- 마이페이지 Certificate 화면에 미리보기와 다운로드 링크를 추가했습니다.
- `/api/public/members/certificate`에서 로그인 및 현재 학회 세션을 확인합니다.
- 현재는 `/public/img/certificate.png`를 `attachment` 방식으로 다운로드합니다.
- 응답은 `no-store`로 설정했습니다.
- 나중에 이름·날짜를 합성할 때도 화면 링크는 유지하고 이 API 내부의 이미지 생성 부분만 교체할 수 있습니다.

### 10. 정적 UI와 개발 환경

- 회원가입·로그인·마이페이지·초록·등록·확인 대화상자 스타일을 `user.css`에 추가했습니다.
- 모바일·태블릿 화면 대응 스타일을 `responsive.css`에 추가했습니다.
- 국가·카테고리·등록 단계 선택 UI를 위해 Select2 정적 파일을 연결했습니다.
- 회원 및 폼 화면에서 사용하는 IcoMoon 폰트와 이미지 자산을 추가했습니다.

## 관리자 기능 영향

- 관리자 React 소스와 관리자 컨트롤러 API는 수정하지 않았습니다.
- 사용자 API는 `/api/public/**` 경로로 분리했습니다.
- 공유 서비스에는 관리자 메서드를 변경하는 대신 `ByMember` 사용자 전용 메서드를 추가했습니다.
- 사용자 기간 설정은 사용자 페이지 진입과 사용자 전용 서비스 호출에만 적용합니다.
- 관리자에서 초록과 발표자료를 처리하는 기존 경로는 사용자 접수 기간과 관계없이 유지됩니다.
- 저장소에는 사용자 전용 조회·잠금·저장 쿼리를 추가했으며 기존 관리자 쿼리는 유지했습니다.

## 커밋 전 반드시 확인할 파일

| 파일 | 확인 이유 | 권장 처리 |
|---|---|---|
| `static/public/css/*.css`, `static/public/js/user.js` | 일부가 이미 스테이징된 `MM` 상태 | 최종 내용 전체를 올리려면 다시 스테이징 |

## 변경 파일 목록

### 백엔드

- `src/main/java/com/bjworld21/congress/Bjworld21CmsConferenceApplication.java`
- `src/main/java/com/bjworld21/congress/controller/PublicPageController.java`
- `src/main/java/com/bjworld21/congress/controller/PublicAbstractController.java`
- `src/main/java/com/bjworld21/congress/controller/PublicCertificateController.java`
- `src/main/java/com/bjworld21/congress/controller/PublicMemberAuthController.java`
- `src/main/java/com/bjworld21/congress/controller/PublicMemberRegistrationController.java`
- `src/main/java/com/bjworld21/congress/controller/PublicMemberSession.java`
- `src/main/java/com/bjworld21/congress/controller/PublicPreRegistrationController.java`
- `src/main/java/com/bjworld21/congress/dto/PublicPreRegistrationData.java`
- `src/main/java/com/bjworld21/congress/repository/AbstractSubmissionRepository.java`
- `src/main/java/com/bjworld21/congress/repository/PreRegistrationRepository.java`
- `src/main/java/com/bjworld21/congress/service/AbstractPresentationAttachmentService.java`
- `src/main/java/com/bjworld21/congress/service/AbstractSubmissionService.java`
- `src/main/java/com/bjworld21/congress/service/PublicPreRegistrationService.java`

### 공개 템플릿

- `src/main/resources/templates/public/fragments.html`
- `src/main/resources/templates/public/home.html`
- `src/main/resources/templates/public/page.html`
- `src/main/resources/templates/public/access-notice.html`
- `src/main/resources/templates/public/pages/abstract-submission.html`
- `src/main/resources/templates/public/pages/online-registration.html`
- `src/main/resources/templates/public/member/login.html`
- `src/main/resources/templates/public/member/join.html`
- `src/main/resources/templates/public/member/join-domestic.html`
- `src/main/resources/templates/public/member/join-international.html`
- `src/main/resources/templates/public/member/mypage.html`
- `src/main/resources/templates/public/member/mypage-profile.html`
- `src/main/resources/templates/public/member/mypage-abstract.html`
- `src/main/resources/templates/public/member/mypage-abstract-write.html`
- `src/main/resources/templates/public/member/mypage-abstract-review.html`
- `src/main/resources/templates/public/member/mypage-registration.html`
- `src/main/resources/templates/public/member/mypage-certificate.html`

### 공개 정적 자원

- `src/main/resources/static/public/js/user.js`
- `src/main/resources/static/public/js/select2/**`
- `src/main/resources/static/public/css/user.css`
- `src/main/resources/static/public/css/responsive.css`
- `src/main/resources/static/public/css/icomoon/**`
- `src/main/resources/static/public/img/certificate.png`
- `src/main/resources/static/public/img/confirm-icon.png`
- `src/main/resources/static/public/img/pdf-down.png`
- `src/main/resources/static/public/img/pdf-icon.png`
- `src/main/resources/static/public/img/member/**`

### 환경 설정

- `src/main/resources/application.yaml`
- `src/main/resources/application-apdrc8.yaml`
- `src/main/resources/application-apdrc8-oper.yaml`
- `src/main/resources/rebel.xml`
- `gradle.properties`

### 테스트와 문서

- `src/test/java/com/bjworld21/congress/controller/PublicAbstractControllerTest.java`
- `src/test/java/com/bjworld21/congress/controller/PublicCertificateControllerTest.java`
- `src/test/java/com/bjworld21/congress/controller/PublicMemberAuthControllerTest.java`
- `src/test/java/com/bjworld21/congress/controller/PublicMemberRegistrationControllerTest.java`
- `src/test/java/com/bjworld21/congress/controller/PublicPageControllerTest.java`
- `src/test/java/com/bjworld21/congress/controller/PublicPreRegistrationControllerTest.java`
- `src/test/java/com/bjworld21/congress/controller/PublicTemplateRenderTest.java`
- `src/test/java/com/bjworld21/congress/service/AbstractPresentationAttachmentServiceTest.java`
- `src/test/java/com/bjworld21/congress/service/AbstractSubmissionServiceTest.java`
- `src/test/java/com/bjworld21/congress/service/PublicPreRegistrationServiceTest.java`
- `document/public_user_page_missing_features.md`
- `document/public_user_feature_change_summary.md`
