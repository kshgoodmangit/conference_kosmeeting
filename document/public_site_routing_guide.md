# 사용자 사이트의 학회·언어·메뉴 운영

## 운영 설정과 URL

학회 모드는 서버 설정으로, 지원 언어와 기본 언어는 각 학회 설정에서 관리한다. 학회 개수가 한 개라고 해서 `multi`를 자동으로 `single`로 바꾸지는 않는다.

```yaml
app:
  public-site:
    conference-mode: multi
    default-conference-seq: 1
```

- `conference-mode`: `single` 또는 `multi`. 기본값은 `multi`이며 환경변수 `PUBLIC_SITE_CONFERENCE_MODE`로 변경할 수 있다.
- `default-conference-seq`: `single` 모드에서 사용할 학회 번호. 환경변수는 `PUBLIC_SITE_DEFAULT_CONFERENCE_SEQ`이다. 최신 학회 번호를 자동 선택하지 않는다.
- 학회별 `sitePath`, `defaultLanguage`, 지원 언어 목록은 관리자 학회 설정에서 저장한다. 기본 언어는 반드시 지원 언어에 포함되어야 한다.
- `sitePath`에는 영문 소문자, 숫자와 구분자 `-`, `_`를 사용할 수 있다. 예를 들어 `2026_136` 학회가 국문·영문을 지원하고 기본 언어가 영문이면 `/2026_136/`은 `/2026_136/en/`으로 이동한다.
- `conference_languages`의 등록된 행이 지원 언어다. 언어가 한 개이면 언어 경로를 생략하고, 두 개 이상이면 포함한다.

| 학회 모드 | 지원 언어 | 메인 | 메뉴 페이지 예시 |
|---|---|---|---|
| single | ko | `/` | `/welcome-message` |
| single | en | `/` | `/welcome-message` |
| single | ko, en | `/ko/`, `/en/` | `/ko/welcome-message` |
| multi | ko | `/apdrc8/` | `/apdrc8/welcome-message` |
| multi | en | `/apdrc8/` | `/apdrc8/welcome-message` |
| multi | ko, en | `/apdrc8/ko/`, `/apdrc8/en/` | `/apdrc8/ko/welcome-message` |

다국어 학회에서 언어 없는 주소를 열면 기본 언어 주소로 이동한다. 단일 언어 학회에서 지원 언어가 붙은 주소를 열면 언어 없는 주소로 정규화한다. 없는 학회·언어·페이지는 다른 학회의 콘텐츠로 대체하지 않는다.

`multi` 모드의 `/`는 공개된 학회 중 `seq`가 가장 큰 학회의 기본 언어 메인으로 임시 이동(302)한다. 공개된 학회가 없으면 빈 학회 안내 화면을 표시한다. 언어 전환은 현재 페이지를 유지하며 `seq`, `page`, `category` 등 허용된 조회값만 유지한다. 비밀번호 재설정 토큰 화면에서는 언어 전환을 숨기고, 토큰을 다른 언어 링크에 복사하지 않는다.

## 학회 홈페이지 공개 여부

- `conference_settings.published`는 홈페이지 공개 여부이며 `1`은 공개, `0`은 비공개다.
- 관리자 학회 설정의 ‘홈페이지 공개’로 변경한다. 관리자 목록에는 비공개 학회도 표시되며 편집할 수 있다.
- 신규 등록·복사한 학회는 기본 비공개다. 복사 원본의 공개 여부는 승계하지 않는다.
- 비공개 학회는 루트 자동 이동 대상·학회 안내 목록·사이트맵에서 제외한다. 직접 페이지 주소와 `/api/public/{conferenceSeq}/...` 접근도 `single`, `multi` 모두 404로 응답한다.
- 공개 설정을 생략한 기존 수정 요청은 현재 상태를 유지한다.
- 배포 전에 MariaDB 10.6용 `document/add_conference_published.sql`을 한 번 실행한다. 기존 학회는 공개 상태를 유지하고 신규 행의 DB 기본값은 비공개로 바뀐다. 애플리케이션은 스키마를 자동 변경하지 않는다.

## 메뉴와 번역 저장

`menu_settings`는 메뉴 계층·유형·노출·권한·공통 페이지 경로를 관리한다. 내부 메뉴의 `routePath`에는 `/welcome-message`, `/mypage-abstract`처럼 학회와 언어를 제외한 경로를 저장한다. 메뉴 계층은 `parentKey`로 관리하며 상위 메뉴명을 URL 폴더로 붙이지 않는다.

사용자 메뉴 조회는 저장된 `routePath`를 그대로 사용한다. 메뉴 링크 생성과 컨트롤러의 메뉴 검색에서 `menuPath`로 대체하지 않는다. 기존 폴더형 경로는 코드 배포 전에 DB에서 단일 페이지 경로로 이관해야 한다. APDRC8(seq=1)의 경로 이관은 `migrate_apdrc8_menu_routes.sql`을 사용한다. 중복 경로는 설정 오류로 처리하며, 이전 폴더 URL의 리다이렉트나 HTML 본문 링크의 자동 변환은 제공하지 않는다.

`menu_translations`는 `(menuSeq, languageCode)`별 메뉴명·HTML을 저장한다. 관리자 메뉴관리에서 사용자 메뉴를 선택하고 언어를 전환하여 해당 언어 내용을 저장한다. 번역이 없는 경우 다른 언어의 본문을 자동 노출하지 않고 준비 중 상태로 표시한다. HTML 편집 이력도 언어별로 구분한다.

- 일반 안내 페이지의 메뉴명과 HTML을 저장하면 재배포 없이 다음 조회에 반영된다.
- 로그인·초록·등록 등 기능 화면은 공통 템플릿과 API를 사용한다. 구조나 업무 기능의 변경은 코드 배포가 필요하다.
- 시스템 문구는 `public-ui.properties`(영문), `public-ui_ko.properties`(국문), API 메시지 리소스에서 관리한다.
- 브라우저에서 표시하는 검증·진행 문구는 `static/public/js/public-messages.js`의 번역을 사용한다. 회원이 입력한 이름·초록과 관리자가 작성한 게시글·프로그램 데이터 자체를 자동 번역하지 않는다.
- 새 언어 행을 추가하는 것과 새 언어의 콘텐츠를 완성하는 것은 별개다. 새 언어로 운영하기 전에 메뉴 번역, 시스템 문구와 필요한 업무 콘텐츠를 준비해야 한다.

외부 링크는 그대로 사용한다. 내부 HTML 링크에는 현재 학회·언어 경로만 붙인다. 사이트 내 링크는 `/welcome-message`, `/abstract-write?seq=123`처럼 학회·언어를 제외한 최종 페이지 경로로 작성한다. 이전 `/program/program-at-a-glance`, `/mypage/abstract/write` 등의 폴더 경로를 새 경로로 바꾸는 런타임 호환 처리는 제공하지 않는다. 공통 CSS·JS·아이콘, 기존 본문 이미지 URL은 공통 경로를 유지한다.

학회명·일정·장소 텍스트는 선택한 학회의 설정을 사용한다. 현재 템플릿의 로고 이미지, 주최기관·연락처와 지도는 공통 디자인 자산이며 학회별 설정 항목이 없다. 이 요소를 학회마다 다르게 운영하려면 별도의 설정·자산 관리 기능을 추가해야 한다.

## 화면 및 API 코드 작성

컨트롤러는 공통 요청 해석기가 확정한 `PublicSiteContext`의 학회와 언어를 사용한다. 세션의 마지막 방문 학회나 가장 최근에 생성한 학회로 대체해서는 안 된다.

```html
<body th:attr="data-site-base=${siteBasePath},data-api-base=${apiBasePath},data-language=${language},data-conference-seq=${conferenceSeq}">
<a th:href="${siteContext.pageUrl('/abstract-write')}">초록 작성</a>
<form th:action="${siteContext.apiUrl('/members/login')}" method="post">
```

사용자 API는 모든 운영 모드에서 `/api/public/{conferenceSeq}/...`를 사용한다. 요청 언어는 `lang`으로 전달한다.

```javascript
fetch(window.PublicSite.apiUrl('/abstracts'));
// /api/public/8/abstracts?lang=ko

window.location.assign(window.PublicSite.pageUrl('/mypage-abstract'));
// /apdrc8/ko/mypage-abstract
```

기본 API 경로에 직접 문자열을 이어 붙이면 기존 `?lang=` 뒤에 자원 번호가 붙을 수 있다. `PublicSite.apiUrl('/abstracts/' + seq)`처럼 자원 경로 전체를 전달한다. 서버에서 렌더링된 form action은 그대로 사용한다.

국가 목록과 CSRF 토큰은 공통 API를 사용한다. 학회 데이터·팝업·통계·개별 첨부파일 조회는 학회가 포함된 API를 사용하며, 서버가 학회와 로그인 회원·대상 데이터의 소속을 검증한다. 브라우저에서 학회 번호를 내려받았다는 사실만으로 권한이 보장되지는 않는다.

오류 응답은 `code`, `message`를 포함한다. 클라이언트는 번역된 `message`를 표시하며, 분기 처리가 필요하면 `code`를 사용한다. 기존 문자열 응답을 처리하는 공통 도우미는 `PublicSite.responseMessage(response)`이다.

## 기존 데이터 이관과 배포

실행 파일은 `document/migrate_public_site_languages.sql`이다. 대상 DB는 MariaDB 10.6이며, 서버 시작 시 자동 실행하지 않는다. 운영자가 백업과 기존 경로 검토 후 SQL 주석의 순서대로 적용해야 한다.

1. 모든 학회에 고유하고 유효한 `sitePath`가 있는지 확인한다. NULL·빈 값·중복·예약 경로를 해결한다.
2. 학회 언어, 메뉴 번역 및 언어별 HTML 이력 스키마를 적용한다.
3. 기존 메뉴명·HTML·이력은 **영문**으로 보존한다. 초기 지원 언어는 국문·영문, 기본 언어는 영문이다. 국문 본문은 관리자가 작성한다.
4. 사용자 메뉴 경로를 바꾸기 전에 학회별 기존 경로 → 새 경로 매핑을 파일로 보관한다. 사용자 메뉴 경로를 평탄하게 변환한다. 변환 후 같은 학회 내 경로 중복이 있으면 중단되므로 충돌을 해결한 후 진행한다.
5. 저장된 본문의 내부 링크도 배포 전에 이관한다. SQL 정규식이나 문자열 전체 치환으로 HTML을 바꾸지 않는다. 읽기 전용으로 내보낸 데이터를 HTML 파서로 검사하고 검토 가능한 수정 SQL을 생성한다. `menu_settings.menuHtml`, 모든 언어의 `menu_translations.menuHtml`, 사용자 메뉴의 모든 `menu_html_histories.menuHtml`, `board_posts.content`, `popups.content`를 포함한다. 이력 본문을 빠뜨리면 과거 버전 복원 시 폐기한 주소가 다시 나타난다. 사용자 메뉴와 팝업의 `linkUrl`도 같은 기준으로 확인한다.
6. 본문 링크 이관은 앵커의 실제 `href`와 별도 링크 컬럼에서 확인된 이전 내부 주소만 대상으로 한다. 외부 URL·프로토콜 상대 URL·정적 파일 주소·다른 HTML 속성·본문 텍스트는 보존한다. 경로 뒤의 조회값·앵커는 유지하고, `/information/noticedetail/123`은 `/notice-detail?seq=123`으로 옮긴다. 메뉴에 등록되지 않은 초록 작성·심사 등의 기능 주소도 포함한다. 수정 SQL은 내보낸 원본이 현재 데이터와 일치할 때만 갱신하도록 검증해야 한다.
7. 새 애플리케이션을 배포하고 서버 운영 모드와 학회 지원 언어를 확인한다. 이전 폴더 주소의 자동 리다이렉트에 의존하지 않는다. 외부에 이미 배포한 링크가 있다면 게시 위치에서 새 주소로 변경한다.

DDL은 암시적 커밋을 포함하므로 전체 파일을 무작정 재실행하지 않는다. 중간 실패 시 적용된 스키마·데이터를 확인하고 다음 단계부터 진행한다. 이 작업의 자동화 도구는 운영 DB에 쓰기 SQL을 실행하지 않는다.

### 저장된 본문 링크 이관

`scripts/migrate_public_content_links.py`는 Python 3.10 이상에서 표준 라이브러리만 사용하는 일회성 도구다. DB에 연결하지 않고 JSON 내보내기 파일 두 개를 읽어 검토용 SQL과 변경 보고서를 만든다. 애플리케이션 런타임에서는 사용하지 않는다.

먼저 **메뉴 경로를 바꾸기 전** 기존 경로 매핑을 저장한다. 아래 조회 도구는 비밀값을 출력하지 않고 읽기 전용 SELECT만 실행한다. `-MaxRows 0`으로 내보내기가 잘리지 않도록 한다.

```powershell
$routeSql = python scripts/migrate_public_content_links.py --print-route-query | Out-String
powershell -NoProfile -ExecutionPolicy Bypass -File .codex/db-query.local.ps1 -Sql $routeSql -MaxRows 0 -Out .codex/db-query-results/public-menu-routes.json | Out-Null
```

운영자가 기본 마이그레이션 SQL의 1~3단계를 완료한 뒤, 새 영문 번역을 포함한 본문을 다시 조회하고 수정안을 만든다. HEX로 내보내므로 HTML 안의 줄바꿈·역슬래시·국문이 TSV 변환 과정에서 훼손되지 않는다.

```powershell
$contentSql = python scripts/migrate_public_content_links.py --print-content-query | Out-String
powershell -NoProfile -ExecutionPolicy Bypass -File .codex/db-query.local.ps1 -Sql $contentSql -MaxRows 0 -Out .codex/db-query-results/public-content-links.json | Out-Null
python scripts/migrate_public_content_links.py --routes .codex/db-query-results/public-menu-routes.json --content .codex/db-query-results/public-content-links.json --output .codex/db-query-results/migrate-public-content-links.sql --report .codex/db-query-results/public-content-links-report.json
```

보고서의 `changedColumns`에서 행·컬럼별 변경 주소를 확인한다. `manualReview`가 있으면 종료 코드는 2이며, 생성 SQL을 곧바로 적용하지 않는다. 매핑에 없는 이전 경로, 따옴표 없는 `href`, 중복 `href`, 상세 주소와 조회값의 `seq` 충돌은 자동으로 추측하지 않으므로 원본과 매핑을 확인하여 해결한다. 변경된 `href` 값 이외의 HTML은 그대로 유지한다. 주석·스크립트·스타일·텍스트 영역의 예제 코드는 변경하지 않는다.

검토한 SQL은 운영자가 DB 클라이언트의 **한 세션**에서 실행한다. 각 갱신은 원본문 SHA2와 소속 학회 조건을 확인하고, 마지막에 `expectedColumns`와 `changedColumns`를 출력한다. 두 수가 같고 결과가 확인되면 그 세션에서 `COMMIT`, 다르거나 확인에 실패하면 `ROLLBACK`한다. 도구는 커밋을 자동 실행하지 않는다. 쓰기를 중단한 상태를 유지하여 중간 수정으로 인한 덮어쓰기를 피한다.

이미 메뉴 경로를 바꾸고 이전 매핑을 저장하지 않았다면 백업에서 기존 경로를 복원하여 매핑 파일을 준비해야 한다. 현재의 평탄한 주소만으로 이전 임의 폴더 이름을 추측하지 않는다. 내보내기 파일에는 관리자 작성 본문이 들어 있으므로 저장소에 커밋하지 않는다.

## 검증

```powershell
.\gradlew17.bat test
node --test src/test/js/public-site.test.cjs
python -B -m unittest discover -s scripts/tests -p 'test_migrate_public_content_links.py'
```

`PublicTemplateRenderTest`는 네 가지 URL 조합, 국문·영문 문구, 언어 전환, 미작성 HTML, 회원 폼, 게시판 페이지 이동과 첨부파일 경로를 확인한다. JavaScript 테스트는 페이지·API 경로, 학회별 저장소 키, 번역 메시지, JSON 오류 처리를 검증한다. 전체 테스트 후 실제 DB 이관 환경에서도 학회별 로그인, 국문·영문 메뉴 저장, 페이지 이동과 다운로드를 확인한다.
