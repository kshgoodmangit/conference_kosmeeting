# 학회 생성 및 기능 라이선스 설정

## 사용자 접속 현황판 라이선스

`app.license.user-analytics-dashboard-enabled`는 행사 현황판 라이선스와 독립적으로 관리합니다.

```yaml
app:
  license:
    user-analytics-dashboard-enabled: ${USER_ANALYTICS_DASHBOARD_ENABLED:true}
```

- `true`: 사용자 접속 현황판 메뉴, `/admin/dashboard/user-analytics`, 전용 API `/api/admin/analytics/dashboard`를 허용합니다.
- `false`: 메뉴와 현황판 이동 링크를 숨기고 직접 URL 접근은 사용 가능한 관리자 화면으로 이동합니다. 전용 API는 집계 조회 전에 HTTP 403을 반환합니다.
- capabilities의 `userAnalyticsDashboardEnabled`가 누락되거나 조회에 실패하면 화면 접근을 허용하지 않습니다.
- 일반 사용자 접속 통계, 이벤트 수집 및 테스트 데이터 생성은 유지합니다. 테스트 생성 완료 링크는 일반 통계로 연결합니다.
- 배포 설정의 기본값은 `true`이며, 비활성화하려면 `USER_ANALYTICS_DASHBOARD_ENABLED=false`로 설정합니다. Java 설정 객체는 설정 누락 시 `false`입니다.
- 변경 후 백엔드를 재시작하고 관리자 화면을 새로고침합니다.

## 학회 추가 설정

`src/main/resources/application.yaml`에서 관리합니다.

```yaml
app:
  license:
    conference-creation-enabled: false
```

- 정확한 설정 키는 `app.license.conference-creation-enabled`입니다. `congress-creation-enabled`는 사용하지 않습니다.
- `true`이면서 `app.public-site.conference-mode: multi`일 때 학회 추가·복사 버튼을 표시하고 해당 API를 허용합니다.
- `false`이거나 학회 모드가 `single`이면 추가·복사를 차단합니다. 학회 생성 API인 `POST /api/admin/conference-settings`와 `POST /api/admin/conference-settings/save-all`도 HTTP 403으로 거부합니다.
- Java 설정 객체는 설정 누락 시 `false`입니다. 다만 현재 공통 `application.yaml`에는 `true`가 명시되어 있으므로, 생성 기능이 없는 배포 환경에서는 `false`를 명시합니다.
- 기존 학회 조회·수정과 등록 옵션 관리는 그대로 사용할 수 있습니다.
- 변경 후 백엔드 서버를 재시작하고 관리자 화면을 새로고침합니다.
- 프론트엔드는 관리자 전용 `GET /api/admin/conference-settings/capabilities`의 `conferenceCreationEnabled`, `conferenceCopyEnabled`로 허용 여부를 조회합니다. 조회 중이거나 실패한 경우 추가·복사 버튼은 숨깁니다.

이 설정은 운영자가 배포 환경에서 관리하는 기능 허용 플래그입니다. 라이선스 키 발급·서명 검증·만료일 검증은 포함하지 않습니다.

## 생성 라이선스 없이 단일 학회·단일 언어 운영

학술대회 한 개를 한국어 또는 영어 사이트로 운영할 때는 서버의 학회 모드와 관리자 화면의 지원 언어를 각각 설정합니다. 학회가 한 개뿐이어도 `multi`가 자동으로 `single`로 전환되지는 않습니다.

기존 `application.yaml`의 `app` 아래 항목을 수정하거나, 실제 사용하는 `application-{profile}.yaml`에서 다음 값을 지정합니다. 별도의 `app` 항목을 중복 추가하지 않습니다.

```yaml
app:
  public-site:
    conference-mode: single
    default-conference-seq: 1 # DB에 존재하는 실제 운영 학회의 seq로 변경
  license:
    conference-creation-enabled: false
```

- `default-conference-seq`에는 기존 운영 학회의 번호를 지정해야 합니다. 최신 학회를 자동 선택하거나 학회를 새로 생성하는 설정이 아닙니다. 최초 배포라면 운영할 학회 데이터가 먼저 준비되어 있어야 합니다.
- 공통 설정의 환경변수 방식을 유지한다면 `PUBLIC_SITE_CONFERENCE_MODE=single`, `PUBLIC_SITE_DEFAULT_CONFERENCE_SEQ=운영 학회 번호`로 지정할 수 있습니다.
- 활성 프로필이나 실행 환경에서 다른 값으로 덮어쓰는 설정이 있는지 함께 확인합니다.

관리자 학회 수정 화면에서는 지원 언어와 기본 언어를 다음과 같이 저장합니다.

| 운영 방식 | 지원 언어 | 기본 언어 |
|---|---|---|
| 한국어만 운영 | 한국어(`ko`)만 선택 | 한국어(`ko`) |
| 영어만 운영 | 영어(`en`)만 선택 | 영어(`en`) |

단일 학회·단일 언어이면 메인은 `/`, 메뉴는 `/welcome-message`처럼 학회·언어 접두사 없이 제공됩니다. 기본 언어만 바꾸고 지원 언어를 두 개 유지하면 `/ko/`, `/en/` 경로를 사용하는 다국어 사이트로 동작합니다. 이 설정은 사이트 언어와 학회 선택 방식에 관한 것이며, 회원의 국가를 제한하는 설정은 아닙니다.

서버 설정 변경 후 백엔드를 재시작하고 관리자 화면을 새로고침합니다. 사용자 사이트 `/`에서 지정한 학회와 언어가 표시되는지 확인합니다. 상세 URL 규칙은 [사용자 사이트 운영 가이드](public_site_routing_guide.md)를 참고합니다.

## 행사 현황판 노출 설정

`src/main/resources/application.yaml`의 `app.license.event-dashboard-enabled`로 관리합니다.

- `true`: `menu_settings`의 사용 여부와 사용 기간이 유효하면 `dashboard-board` 메뉴와 `/admin/dashboard/board` 화면을 표시합니다.
- `false`: 메뉴를 숨기고 `/admin/dashboard/board` 및 이전 주소 `/admin/dashboard2`의 직접 접근을 사용 가능한 첫 관리자 메뉴로 전환합니다.
- 관리자 capabilities 조회가 실패하거나 응답 필드가 없으면 행사 현황판만 숨기고 다른 관리자 메뉴는 유지합니다.
- 메뉴 테이블의 행은 삭제하지 않으므로 라이선스를 다시 활성화하면 기존 메뉴 구성을 사용합니다.
- 현재 기본 환경변수 값은 `${EVENT_DASHBOARD_ENABLED:true}`이므로 미설정 환경에서도 활성화됩니다. 비활성 환경에서는 `EVENT_DASHBOARD_ENABLED=false`를 명시합니다.
