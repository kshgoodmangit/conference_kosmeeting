# 사용자 메인 팝업

## 동작

- `/`의 Thymeleaf 메인화면에서 현재 공개 학회(`getLatestConferenceSeq()`)의 팝업을 표시한다.
- `conference_settings.popupLayoutNo`의 1~8번을 각각 좌측 상단, 중앙, 우측 상단, 카드 덱, 3장 나란히, 3장 오버레이, 커버플로, 우측 드로어에 연결한다.
- 사용 중이고 한국 시간 기준 시작일·종료일 범위 안인 항목만 조회한다. 양 끝 날짜는 포함하며, 입력하지 않은 날짜는 제한하지 않는다. 등록일·번호 내림차순으로 정렬한다.
- CKEditor 본문 전체를 다시 정제해 표시한다. 본문이 비어 있으면 기존 첨부 이미지를 사용하며 둘 다 없으면 제외한다. 본문의 CSS 클래스는 홈페이지 스타일과 충돌하지 않도록 제거한다.
- 홈페이지의 글꼴과 APDRC 색상 변수를 사용한다. 모바일에서는 한 장씩 표시하며, 긴 내용은 카드 내부에서 스크롤한다.
- 5번 나란히형, 6번 오버레이, 7번 커버플로는 바깥 프레임 없이 카드만 오버레이 위에 배치한다. 상단 숨김·닫기와 하단 이동·일시정지 버튼은 투명 영역에 배치하고 밝은 글자와 반투명 버튼 배경으로 대비를 확보한다. 오버레이의 카드 겹침과 커버플로의 입체 배치는 유지한다.
- 배경 오버레이를 클릭해도 닫히지 않는다. X 버튼 또는 Escape로 닫으며, 오늘 하루 보지 않기는 서버 저장 성공 후 닫는다.
- 4번 중앙 카드덱도 같은 프레임 없는 디자인을 사용한다. 뒤쪽 카드는 아래로 12px·24px 드러나며 전환 영역을 자르지 않는다. 이동 버튼은 겹친 카드 아래에 배치하고 배경 스크롤과 키보드 포커스를 팝업 안으로 제한한다.
- 1건은 전환 버튼 없이 표시한다. 여러 건은 자동 전환하되 카드 덱은 수동으로만 전환한다. 마우스·키보드 조작, 비활성 탭, 움직임 축소 설정에는 자동 전환을 멈춘다.
- 기존 쿠키 선택 배너가 열려 있으면 선택을 마친 뒤 팝업을 연다. 팝업 조회 실패는 로그에 기록하고 메인화면은 정상 제공한다.

## 오늘 하루 보지 않기

사용자가 선택한 정책은 **현재 브라우저에서 해당 학회의 전체 팝업을 한국 시간 자정까지 숨기는 것**이다. 숨긴 뒤 새로 등록되는 팝업도 포함한다.

1. 브라우저가 `/api/security/csrf-token`에서 토큰을 얻는다.
2. CSRF 토큰을 포함한 `POST /api/popups/dismiss-today`를 호출한다.
3. 서버가 `conference_popups_hidden_{학회번호}=yyyy-MM-dd` 쿠키를 발급한다. `Path=/`, `HttpOnly`, `SameSite=Lax`이며 HTTPS에서는 `Secure`를 적용한다. 유효 기간은 서버 시각 기준 다음 한국 시간 자정까지이다.
4. 저장 성공 응답을 받은 뒤 현재 팝업을 닫는다. 실패하면 오류 안내와 재시도·일반 닫기를 제공한다.
5. 다음 메인 요청에서는 **쿠키 확인을 가장 먼저 하여 팝업 목록·레이아웃 DB 조회를 건너뛴다.** 모델에 팝업 내용을 넣지 않으며 팝업 본문 HTML은 생성하지 않는다. 퀵메뉴에서 수동으로 다시 열 수 있도록 CSS·JS와 POPUP 버튼은 유지한다.
6. 서버가 쿠키 날짜를 매번 검증하므로 지난 날짜·잘못된 값·다른 학회의 쿠키는 적용하지 않는다. 메인 응답은 `private, no-store`와 `Vary: Cookie`를 설정한다. 브라우저 뒤로가기 캐시 복원 시 서버에 다시 요청한다.

일반 `Close`는 쿠키를 저장하지 않는다. 이미 열려 있는 다른 탭은 다음 서버 요청부터 숨김이 적용된다. 브라우저가 사이트 쿠키 저장을 차단하거나 사용자가 쿠키를 삭제하면 숨김을 유지할 수 없다.

## 구성과 변경 범위

- `PublicPopupService`: 쿠키 선확인, 공개 데이터 조회·본문 정제, 장애 격리.
- `PublicPopupPreferences`: 한국 시간 기준 쿠키 판정·발급.
- `PublicPopupController`: CSRF 보호를 유지하는 비회원용 숨김 요청.
- `public/popups.html`, `public/css/popups.css`, `public/js/popups.js`: 사용자 전용 표시 및 조작.
- 기존 이미지 GET 2개에 `IpAccessExempt`를 적용해 외부 방문자도 이미지를 볼 수 있도록 한다. 관리자 등록·수정·이미지 업로드의 IP·세션 제한은 유지한다.
- DB 스키마 변경 없음. 기존 업로드 상대 경로와 `UploadStorage`를 유지한다.
- 관리자 미리보기는 기존 샘플을 유지하며, 공개 화면은 현재 홈페이지 디자인에 맞춘 별도 렌더러를 사용한다.

## 검증

```powershell
.\gradlew17.bat test --tests '*PublicPopup*' --tests '*PublicPageControllerTest' --tests '*PublicTemplateRenderTest' --tests '*PopupServiceTest' -x npmInstallTask -x npmBuildTask
node --test frontend/scripts/public-popups.test.cjs
```

백엔드 테스트는 숨김 시 조회 생략, 한국 시간 자정, 본문 정제, 공개 API CSRF, 캐시 정책 및 템플릿 미생성을 검증한다. JS 테스트는 8개 레이아웃별 0~4개 데이터, 모바일, 포커스·일시 정지, 중복 저장 방지, 저장 실패 및 CSRF 재시도를 검증한다. 템플릿 테스트에서 `build/popup-preview/layout-1.html`부터 `layout-8.html`까지 시각 검토용 결과를 생성한다. 실제 DB 데이터를 수정하지 않는 샘플이다.

## 퀵메뉴 POPUP 버튼

- 사용자 메인화면과 서브페이지의 View Program 아래에 POPUP 버튼을 표시한다. 기존 아이콘에 맞춰 가는 남색 윤곽선, 파란 창, 청록색 알림 종으로 제작한 `src/main/resources/static/public/img/quick-popup.png`를 사용한다.
- 서브페이지는 팝업 CSS·JS만 로드하고 팝업 본문을 미리 생성하지 않는다. 자동 표시는 메인화면에서만 유지하고, 서브페이지에서는 버튼을 눌렀을 때 팝업을 조회해 연다.
- 일반 닫기 또는 오늘 하루 보지 않기로 닫은 팝업은 버튼으로 다시 열 수 있다. 닫을 때 중지한 임베드 영상도 다시 복원한다.
- 숨김 쿠키가 적용된 메인화면이나 서브페이지처럼 본문이 없는 경우에는 버튼을 눌렀을 때 `GET /popups/display`로 현재 공개 팝업을 가져온다. 자동 표시와 같은 공개 기간·사용 여부·본문 정제 기준을 적용한다.
- 수동 열기는 숨김 쿠키를 삭제하거나 만료 시각을 연장하지 않는다. 다시 메인에 접속하면 기존 오늘 하루 보지 않기가 계속 적용된다.
- 중복 클릭을 막고, 팝업이 없거나 조회하지 못한 경우 메뉴에 안내를 표시한다.
- `PublicPopupDisplayController`가 수동 열기용 HTML fragment를 제공한다. 응답은 `private, no-store`이며 쿠키를 설정하지 않는다.

### 아이콘 생성 기록

내장 image_gen 도구로 생성 후 기존 아이콘과 비교하여 윤곽선을 최초 두께의 약 1/4로 조정했다. 최종 배경은 기존 원형 배경에 어울리는 연한 파랑으로 정리했다.

최종 편집 프롬프트:

> Change ONLY the background outside this thin-line popup window icon. Replace ALL gray checkerboard pattern and folds with perfectly flat solid pale blue #EEF5FA, no texture, no shadows, no checkerboard, no transparency. Keep icon exactly same thin fine navy outlines, white windows, royal blue headers, teal bell, lower-right blue circle arrow. Square canvas. The backdrop must be uniformly #EEF5FA edge to edge everywhere outside the icon. Preserve icon shape, fine stroke weight and composition.
