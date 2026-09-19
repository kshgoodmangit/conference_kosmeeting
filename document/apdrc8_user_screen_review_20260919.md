# APDRC8 사용자 화면 검토 — 2026-09-19

## 후속 수정 결과

사용자의 수정 요청에 따라 아래 내용을 반영했다. 이후의 검토 표는 수정 전 브라우저에서 관찰한 기록이다.

| 항목 | 처리 상태 |
|---|---|
| 등록 화면 진입 시 잘못된 성공 배너 | 코드 수정 완료. 성공 응답의 빈 안내문은 그대로 유지한다. 명시적인 메시지 코드와 오류 응답의 번역 처리는 유지한다. |
| 경로 안내의 상위 폴더가 빈 화면으로 이동 | 코드 수정 완료. 폴더는 클릭 불가 텍스트로 표시하며, 일반 페이지인 상위 경로의 링크는 유지한다. |
| 본문의 과거 경로로 인한 404 | `migrate_apdrc8_content_links.sql` 작성 완료, DB 적용 대기. 프로그램·초록 안내·수상 안내의 본문 및 번역·이력 9개 행을 수정한다. |
| 등록 안내의 잘못된 버튼 | 같은 SQL에 포함, DB 적용 대기. 본문 및 번역·이력 3개 행의 `Abstract submission` / `#` 버튼을 `Online Registration` / `/online-registration`으로 수정한다. |

### 적용 방법과 검증 범위

- Java 17로 `PublicPreRegistrationControllerTest`, `PublicTemplateRenderTest`, `PublicApiIsolationTest`의 25개 테스트가 통과했다. 최초 등록 조회에서 빈 `message`가 유지되는 것과 폴더/일반 페이지의 경로 안내 차이를 회귀 테스트로 확인했다.
- 링크 이관 도구의 Python 테스트 17개가 통과했다. 현재 DB의 공개 콘텐츠 117개 컬럼을 읽기 전용으로 내보내어 실제 남아 있는 링크를 확인했다.
- DB는 프로젝트의 읽기 전용 조회 규칙에 따라 직접 변경하지 않았다. `migrate_apdrc8_menu_routes.sql` 적용이 완료된 DB에서 새 `migrate_apdrc8_content_links.sql` 전체를 같은 연결로 실행한다.
- 마지막 결과가 `expectedColumns=12`, `changedColumns=12`일 때 같은 연결에서 `COMMIT;`한다. 결과가 다르면 `ROLLBACK;`하고 변경된 본문을 기준으로 SQL을 다시 생성한다. 자동 COMMIT은 포함하지 않았다.
- 변경 대상과 전후 주소는 `migrate_apdrc8_content_links_report.json`에 기록했다. 원문 해시 조건이 조회 이후 편집된 본문을 덮어쓰지 않도록 보호한다.
- Java 변경을 적용하려면 실행 중인 IntelliJ 서버를 다시 시작해야 한다. 이번 수정 후 실행 서버를 재시작하거나 SQL을 적용하지 않았으므로, 수정된 실행 화면의 재검증은 아직 남아 있다.

### 자료 또는 운영 결정이 필요한 항목

- Accommodation의 Reserve: 사용할 호텔 예약 URL이 필요하다.
- Sponsorship의 CONTACT · COMING SOON: 확정된 후원 문의 URL 또는 이메일이 필요하다.
- SAMPLE NAME, [Name], [Month DD, YYYY] 등: 확정된 인사말·이름·날짜가 필요하다. Contact Us 본문과 푸터의 전화번호 중 사용할 번호도 확인해야 한다.
- 영문 초록 폼의 한국어 발표형식: `common_codes.codeName`을 그대로 조회한다. 이 테이블의 이름은 관리자와 다른 조회에서도 공유하고 언어별 이름 컬럼이 없으므로, 공통 이름을 영문으로 운영할지 언어별 표시를 추가할지 결정이 필요하다. 공통 데이터를 임의로 바꾸지 않았다.
- 초록 저장·제출, 등록·결제·영수증은 최초 검토와 동일하게 검증하지 않았다.

## 검토 범위와 방법

- 대상: http://localhost:8080/ / APDRC8 / 단일 학회·영문 사이트.
- Chrome에서 상단 메뉴 24개를 순차 클릭하고, 본문 버튼·공지·FAQ·등록비 계산·로그인·마이페이지를 확인했다.
- 기존 로그인 계정이 제공된 테스트 계정임을 확인했다. 이후 로그아웃, 비로그인 등록 접근, 제공 계정 재로그인을 직접 검증했다.
- 화면 진입과 조회, 저장하지 않는 폼 조작만 수행했다. 초록 제출·임시저장, 등록 신청·결제, 개인정보 저장, 비밀번호 변경, 회원가입, 인증메일 발송은 실행하지 않았다.
- 외부 사이트의 업무 흐름, 모바일 반응형, 모든 첨부파일 다운로드는 이번 검토 범위에 포함하지 않았다.
- 아래의 정상은 확인한 범위에서의 정상이며, 실제 저장·결제까지 검증했다는 뜻은 아니다.

## 확인된 문제

### 1. 안내 본문의 과거 링크 4개에서 404 발생

| 출발 화면 | 클릭한 버튼 | 실제 연결 | 필요한 연결 |
|---|---|---|---|
| `/program-at-a-glance` | VIEW INVITED SPEAKERS → | `/program/invited-speakers` | `/invited-speakers` |
| `/program-at-a-glance` | VIEW SCIENTIFIC PROGRAM → | `/program/scientific-program` | `/scientific-program` |
| `/submission-guideline` | Abstract submission | `/abstract/abstract-submission` | `/abstract-submission` |
| `/award` | Submit an Abstract | `/abstract/abstract-submission` | `/abstract-submission` |

네 버튼을 모두 실제 클릭하여 Whitelabel Error Page / status=404를 확인했다.
상단 메뉴와 우측 빠른 메뉴는 현재 경로를 사용하므로 정상이다. `routePath` 이관은 본문 HTML의 `href`를 변경하지 않기 때문에 남은 문제다.
해결 대상은 영문 메뉴 본문과 관련 원본/이력에 저장된 과거 링크다. 런타임의 경로 대체 로직을 되살리는 방식은 필요하지 않다.
기존 `scripts/migrate_public_content_links.py` 및 `public_site_routing_guide.md`의 본문 링크 이관 절차를 사용할 수 있다.

### 2. 온라인 등록 화면에서 수행하지 않은 작업의 성공 문구 표시

- `/online-registration`에 진입만 해도 `Completed successfully.` 배너가 표시된다.
- 등록 구분을 선택하거나 저장·결제하지 않은 상태에서도 표시되는 것을 스크린샷으로 확인했다.
- `PublicPreRegistrationService.form()`은 별도 안내가 없을 때 빈 문자열 `message`를 반환한다.
- `PublicApiAdvice.beforeBodyWrite()`는 null이 아닌 빈 `message`도 번역 대상으로 처리한다.
- `PublicApiMessages.translate()`는 빈 문자열과 HTTP 200 조합을 `SUCCESS` 문구로 바꾸며, `user.js`가 이를 상태 배너에 표시한다.
- 빈 안내문은 빈 값으로 유지하고, 실제 성공한 작업에만 성공 문구를 표시해야 한다.
- 처음에는 금액도 비어 있었지만 이는 등록 구분을 아직 선택하지 않았기 때문이었다. PI / Ph.D. 선택 후 ₩460,000, Gala Ticket 1개 추가 후 ₩555,000으로 정상 계산됐다. 금액 계산 오류로 분류하지 않는다.

### 3. 목적지가 지정되지 않은 본문 버튼

| 화면 | 버튼 | 현재 동작 |
|---|---|---|
| `/registration-guideline` | Abstract submission | `#` 링크로 다른 화면에 이동하지 않음. 등록 안내에 초록 제출 라벨이 있는 점도 확인 필요 |
| `/accommodation` | Reserve | `#` 링크로 예약 화면에 이동하지 않음 |
| `/sponsorship` | CONTACT · COMING SOON | `#` 링크. 준비 중 상태이며 문의 기능은 미연결 |

세 버튼 모두 클릭 확인했다. 예약/문의 기능이 준비 전이라면 실행 가능한 버튼으로 오해하지 않도록 표시를 정리할 필요가 있다.

### 4. 경로 표시의 상위 폴더 링크가 빈 안내 화면으로 이동

- Sponsorship 화면의 `HOME > SPONSORS > Sponsorship`에서 `SPONSORS`를 클릭하면 `/sponsors`로 이동한다.
- 이 화면은 `Content is being prepared.`와 `Add content in Admin → Menu Management to display it here.`를 보여준다.
- 상단 SPONSORS 메뉴는 첫 하위 메뉴 `/sponsorship`으로 연결하므로 두 위치의 동작이 다르다.
- `templates/public/page.html`은 상위 경로 표시에서 폴더의 `routePath`를 직접 사용하고, `templates/public/fragments.html`의 상단 메뉴는 첫 번째 표시 가능한 하위 메뉴를 사용한다.
- 폴더의 경로 표시는 클릭 불가 텍스트로 표시하거나 상단 메뉴와 같은 하위 목적지를 사용하는 방식을 선택할 수 있다.

### 5. 운영 콘텐츠 보완 사항

- Welcome Message에 SAMPLE NAME, [Name] 등의 임시 문구가 남아 있다.
- 초록 안내·수상 안내·등록 안내·취소 정책에 `[Month DD, YYYY]` 등 날짜 자리표시자가 남아 있다. 메인의 실제 일정과 별도로 저장된 안내 본문이다.
- Contact Us 본문의 전화번호와 공통 푸터의 전화번호가 다르다. 어떤 번호가 맞는지는 검증하지 않았다.
- 영문 초록 작성 화면의 발표형식 선택지는 `구두 발표`, `포스터 발표`로 표시된다. 코드 선택 자체는 가능하며, 영문 운영에 맞는 표시 데이터 검토가 필요하다.
- 이 항목들은 테스트 사이트의 콘텐츠 준비 상태로 분류하며, 페이지 진입 실패와 구분한다.

## 상단 메뉴별 결과

| 그룹 | 화면 | 화면 진입·표시 | 추가 확인 |
|---|---|---|---|
| APDRC8 | Welcome Message | 정상 | 임시 인사말 콘텐츠 |
| APDRC8 | Overview | 정상 | 행사 정보 표시 |
| APDRC8 | Committee | 정상 | 위원 목록 표시 |
| APDRC8 | About APDRC8 | 정상 | 소개 본문 표시 |
| APDRC8 | Contact Us | 정상 | 연락처 본문/푸터 불일치 |
| PROGRAM | Program at a Glance | 정상 | 본문 이동 버튼 2개 404 |
| PROGRAM | Scientific Program | 정상 | DAY 1~4 모두 클릭, 해당 날짜 표 표시 |
| PROGRAM | Invited Speakers | 정상 | 연자 구분·목록 표시 |
| ABSTRACT | Submission Guideline | 정상 | 본문 초록 제출 버튼 404 |
| ABSTRACT | Abstract Submission | 정상 | 로그인 상태에서 입력 폼 표시 |
| ABSTRACT | Award | 정상 | 본문 초록 제출 버튼 404 |
| ABSTRACT | Presentation Guidelines | 정상 | 안내 본문 및 My Page 링크 존재 |
| REGISTRATION | Registration Guideline | 정상 | 본문 버튼 `#` |
| REGISTRATION | Online Registration | 정상 | 등록비/옵션 계산 정상, 잘못된 성공 배너 |
| REGISTRATION | Accommodation | 정상 | 지도 로딩 확인, 예약 버튼 `#` |
| REGISTRATION | Visa | 정상 | 안내·외부 링크 표시, 외부 신청은 미검증 |
| REGISTRATION | Cancellation Policy | 정상 | 날짜 자리표시자 존재 |
| INFORMATION | Venue | 정상 | 장소·외부 안내 링크 표시 |
| INFORMATION | Transportation | 정상 | 교통 안내 본문 표시 |
| INFORMATION | About Seoul & Korea | 정상 | 지역 소개 본문 표시 |
| INFORMATION | Notice | 정상 | 2페이지 이동, 게시글 seq=6 상세 조회 정상 |
| INFORMATION | FAQ | 정상 | 답변 펼침 및 결제·환불 분류 필터 정상 |
| SPONSORS | Sponsorship | 정상 | 문의 버튼 준비 중, 상위 폴더 경로 표시 문제 |
| SPONSORS | Sponsor | 정상 | 후원 등급 제목 표시 |

## 추가 기능별 결과

| 기능 | 확인 결과 |
|---|---|
| 메인 | 행사명·일정·연자·프로그램·공지 표시, 메뉴 경로 정상 |
| 팝업 | Open popups 클릭 후 이미지 팝업 표시, 닫기 정상 |
| Cookie Settings | 설정 다이얼로그 열기·닫기 정상. 선호값 저장은 실행하지 않음 |
| Privacy Policy | 본문 진입 정상 |
| 로그아웃 | 메인으로 이동하고 LOGIN/SIGN UP 표시 |
| 비로그인 등록 접근 | Register Now 클릭 시 로그인 화면으로 이동 |
| 로그인 | 제공 계정으로 로그인 성공, 메인과 MY PAGE 표시 |
| 비밀번호 찾기 | 입력 화면 표시. 메일 발송은 실행하지 않음 |
| 회원가입 | 가입 유형 선택 및 국내·국외 입력 화면 표시. 가입/인증은 실행하지 않음 |
| My Page | 로그인 사용자 정보와 초록 목록 영역 표시 |
| 내 초록 | 목록 조회 정상, 해당 계정의 제출 내역 없음 |
| 초록 작성 `/abstract-write` | 입력 폼 및 발표형식 선택지 로딩 확인. 저장·제출은 실행하지 않음 |
| 내 등록 | 등록 폼 표시. 결제/영수증은 미검증 |
| 회원정보 수정 | 기존 정보 입력 화면 표시. 저장하지 않음 |
| 비밀번호 변경 | 입력 화면 표시. 비밀번호는 변경하지 않음 |

## 권장 처리 순서

1. 404를 일으키는 본문 링크 이관.
2. 빈 API 안내문이 성공 문구로 변환되는 문제 수정.
3. `#` 버튼과 상위 폴더 경로 표시 동작 정리.
4. 안내 날짜·이름·연락처·언어 등 콘텐츠 확정.
5. 별도 테스트 데이터와 결제 환경을 정한 뒤 실제 저장·제출·결제·영수증 흐름을 검증.

최초 화면 검토에서는 코드와 DB를 수정하지 않았다. 이후 요청에 따른 코드 수정 및 적용 대기 SQL은 문서 상단의 후속 수정 결과에 기록했다.
