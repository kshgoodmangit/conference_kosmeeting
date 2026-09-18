# 메뉴 HTML 이력 및 복원

- 대상: `/admin/menu`. DB의 `menu_html_histories`와 `menu_settings.htmlRevisionNo`를 수동 적용한 뒤 사용한다. 시작 시 스키마 변경이나 데이터 이관을 실행하지 않는다.
- 기존 84개 메뉴의 최초 이관 완료를 읽기 전용으로 확인했다. 이관 데이터는 다시 생성하지 않는다.
- 신규 메뉴는 빈 HTML도 INITIAL v1으로 기록한다. 수정 시 기존/새 HTML에 기존 trim·빈 값→NULL 규칙을 동일하게 적용하여 Java 문자열로 비교한다. 대소문자와 본문 내부 공백을 구분한다. 같은 내용은 이력·버전 번호를 변경하지 않는다.
- CKEditor를 열기만 해도 발생하는 HTML 포맷 변경은 사용자 변경으로 취급하지 않는다. 프런트의 `menuHtmlChanged=false` 요청에서는 잠근 메뉴의 최신 HTML을 유지한다. 실제 변경 요청은 서버에서도 내용을 비교한다.
- 저장과 복원은 트랜잭션 안에서 메뉴 행을 `FOR UPDATE`로 잠그고 버전 번호를 증가시킨다. 이 잠금은 중복 버전 방지용이며, rowVersion/낙관적 충돌 감지는 없다. 실제 HTML 수정 요청끼리는 마지막 저장이 반영된다.
- 복원은 HTML만 갱신하고 RESTORE 이력을 추가한다. 원본 이력을 수정·삭제하지 않는다. 현재 HTML과 같은 내용이면 쓰지 않는다. 이름·경로·노출·정렬은 복원하지 않는다.
- 관리자 정보는 로그인 세션 번호로 서버에서 조회한다. 이력은 menuSeq로 조회하되 먼저 메뉴의 학술대회 범위를 확인한다. 관리자 공통 메뉴는 menuScope=admin, conferenceSeq=NULL만 허용한다.
- 메뉴 HTML 이력이 있으면 물리 삭제 대신 비활성화를 안내한다. 기존 외래키 RESTRICT 정책을 유지한다.
- 이력 목록은 20개씩 메타데이터만 조회한다. 상세에는 원문과 현재 저장본을 텍스트로 제공하고, 미리보기용 HTML은 CmsHtmlSanitizer로 정제한다. 미리보기는 sandbox iframe과 현재 사용자 CSS를 사용한다. 리소스 파일이나 과거 CSS는 보관·복원하지 않는다.
- 미저장 HTML/메뉴 설정/정렬이 있으면 이력 열람은 가능하지만 복원은 먼저 저장하도록 안내한다.
- 소스 비교는 jsdiff로 내용에 맞는 줄을 정렬하는 읽기 전용 좌우 비교이다. 선택 이력에서 현재 저장본으로의 추가(초록/+), 삭제(빨강/−), 수정(파랑/~)을 표시하고, 수정된 글자는 각 측에서 더 진하게 강조한다. 양쪽 줄 번호와 빈 대응 행을 유지하고 하나의 스크롤 영역에서 함께 이동한다. 부분 적용·병합 기능은 제공하지 않는다.
- 비교 시 원문 HTML을 파싱·재저장하지 않고 CRLF/CR 줄바꿈만 LF로 통일한다. 공백·속성·대소문자는 비교 대상이며 HTML은 실행하지 않고 텍스트로 표시한다. 긴 변경 없는 구간은 앞뒤 3줄을 남겨 접고 펼칠 수 있으며 이전/다음 변경 이동을 지원한다.
- 비교 화면은 refractor의 HTML 문법만 불러와 태그명·속성명·속성값·주석·엔티티에 색상을 적용한다. 원문 전체의 문맥으로 토큰을 나누고 diff 강조 범위와 합쳐, 여러 줄에 걸친 태그와 변경 강조를 함께 유지한다. 출력은 React 텍스트이며 HTML 삽입이나 DOM 자동 강조를 사용하지 않는다. 20만 문자 초과 시 색상만 생략하고 기존 비교는 유지한다. 검증: `node --test scripts/html-syntax.test.mjs scripts/menu-html-diff.test.mjs`.
- 브라우저 정지 방지를 위해 합계 100만 문자/12,000줄을 초과하거나 비교 계산 한도를 넘으면 상세 비교 대신 안내한다. 긴 수정 줄은 글자 단위 강조를 생략하고 줄 단위로 표시한다. 자동 검증: frontend에서 `node --test scripts/menu-html-diff.test.mjs`.

## API

- `GET /api/admin/menu-settings/{seq}/html-histories?language=ko&page=0&size=20`
- `GET /api/admin/menu-settings/{seq}/html-histories/{historySeq}?language=ko`
- `POST /api/admin/menu-settings/{seq}/html-histories/{historySeq}/restore?language=ko`: `{ "changeMemo": "선택 입력" }`
- 기존 등록/수정 API: 선택 changeMemo(최대 500자), 수정 API의 menuHtmlChanged(기본 true) 추가.

모든 API는 기존 관리자 세션·권한과 X-Conference-Seq, 변경 요청의 CSRF 보호를 그대로 사용한다.

## 언어별 메뉴 편집

- 배포 전에 `migrate_public_site_languages.sql`을 운영자가 수동 적용한다. 기존 사용자 메뉴명·HTML·이력은 영문으로 유지되며, 국문 번역은 자동 생성하지 않는다.
- `/admin/menu`의 사용자 메뉴에서 편집 언어를 고른다. 지원 언어는 학회 설정의 `conference_languages`에 등록된 목록만 표시한다.
- `menu_translations`에 `(menuSeq, languageCode)`별 메뉴명·HTML·현재 버전을 저장한다. 경로·계층·순서·사용 기간·인증 및 노출은 `menu_settings`의 공통 설정이다.
- 미작성 언어에는 다른 언어 HTML을 대신 보여주지 않는다. 관리자 트리에 미작성 상태를 표시하고 해당 언어의 메뉴명과 본문을 직접 작성한다.
- 메뉴 트리 조회 `GET /api/admin/menu-settings/tree?language=ko`, 생성/수정의 폼 항목 `language=ko`, 정렬의 `?language=ko`를 사용한다. 관리자 메뉴에는 번역을 적용하지 않는다.
- HTML 이력은 `(menuSeq, languageCode, revisionNo)`로 구분한다. 다른 언어의 이력 번호를 전달해도 조회·복원할 수 없다. 선택한 언어 HTML만 복원하고 다른 언어의 본문·메뉴명은 유지한다.
- 언어별 메뉴명만 바꾸면 HTML 이력은 늘리지 않는다. 최초 작성은 빈 HTML이어도 해당 언어의 INITIAL v1을 생성한다.
- 편집 언어·메뉴 변경 시 미저장 메뉴명·HTML·설정·정렬이 있으면 공통 확인창으로 변경사항 폐기 여부를 확인한다.
- 지원 언어를 해제해도 번역과 이력은 유지된다. 나중에 같은 언어를 다시 활성화하면 기존 콘텐츠를 편집할 수 있다.
- 하나라도 HTML 이력이 있는 메뉴는 삭제할 수 없다. 이력이 전혀 없는 메뉴를 삭제할 때만 번역 행을 함께 제거한다.
- 학회 복사 화면은 기존과 같이 미리보기다. 향후 실제 메뉴 복사에서는 메뉴 ID를 새 학회의 ID로 매핑한 뒤 `MenuTranslationService.copyTranslations`를 호출한다. 원본 이력 대신 새 메뉴별 INITIAL 이력이 생성된다.
