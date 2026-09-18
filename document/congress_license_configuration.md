# 학회 추가 허용 설정

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
    congress-creation-enabled: false
```

- `true`: `/admin/congress` 상단과 빈 목록의 학회 추가 버튼 표시, 학회 생성 API 허용.
- `false` 또는 설정 누락: 추가 버튼 숨김, `POST /api/admin/congress-settings` 요청은 HTTP 403으로 거부.
- 기존 학회 조회·수정과 등록 옵션 관리는 그대로 사용할 수 있습니다.
- 변경 후 백엔드 서버를 재시작하고 관리자 화면을 새로고침합니다.
- 프론트엔드는 관리자 전용 `GET /api/admin/congress-settings/capabilities`로 허용 여부를 조회합니다. 조회 중이거나 실패한 경우 추가 버튼은 숨깁니다.

이 설정은 운영자가 배포 환경에서 관리하는 기능 허용 플래그입니다. 라이선스 키 발급·서명 검증·만료일 검증은 포함하지 않습니다.

## 행사 현황판 노출 설정

`src/main/resources/application.yaml`의 `app.license.event-dashboard-enabled`로 관리합니다.

- `true`: `menu_settings`의 사용 여부와 사용 기간이 유효하면 `dashboard-board` 메뉴와 `/admin/dashboard/board` 화면을 표시합니다.
- `false`: 메뉴를 숨기고 `/admin/dashboard/board` 및 이전 주소 `/admin/dashboard2`의 직접 접근을 사용 가능한 첫 관리자 메뉴로 전환합니다.
- 관리자 capabilities 조회가 실패하거나 응답 필드가 없으면 행사 현황판만 숨기고 다른 관리자 메뉴는 유지합니다.
- 메뉴 테이블의 행은 삭제하지 않으므로 라이선스를 다시 활성화하면 기존 메뉴 구성을 사용합니다.
- 현재 기본 환경변수 값은 `${EVENT_DASHBOARD_ENABLED:true}`이므로 미설정 환경에서도 활성화됩니다. 비활성 환경에서는 `EVENT_DASHBOARD_ENABLED=false`를 명시합니다.
