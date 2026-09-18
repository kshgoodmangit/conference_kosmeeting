# 관리자 화면 디자인 및 구현 가이드

관리자 화면을 추가하거나 수정할 때 사용하는 프로젝트 UI 기준이다. 등록 버튼과 모달처럼 같은 역할을 하는 요소는 화면이 달라도 같은 모습과 동작을 유지한다.

이 문서는 현재 React·Tailwind 관리자 화면의 디자인 규칙이다. 별도의 공통 컴포넌트 라이브러리가 완성되어 있다는 뜻은 아니다. 공개 홈페이지는 별도의 레이아웃을 사용하므로 이 관리자 스타일을 그대로 적용하지 않는다.

## 1. 작업 전 확인

1. 이 문서를 읽고, 아래 참조 화면 중 작업과 가장 가까운 화면을 확인한다.
2. 페이지 헤더, 버튼, 모달, 입력란, 목록의 기본 스타일을 먼저 맞춘다.
3. 공통 확인 다이얼로그·토스트·엑셀 다운로드 기능은 기존 구현을 재사용한다.
4. 화면별로 필요한 필드와 업무 동작을 추가한다.
5. 라이트·다크 모드, 좁은 화면, 로딩·빈 목록·오류 상태를 확인한다.

| 대상 | 참조 구현 |
|---|---|
| 일반 관리 목록·등록 버튼 | `frontend/src/components/SponsorPage.tsx`, `AdminPage.tsx`, `MemberPage.tsx` |
| 검색 영역 HTML 구조·필드 간격·초기화/조회 버튼 | `frontend/src/components/PreRegistrationManagementPage.tsx` |
| 기본 등록·수정 모달과 프로필 이미지 입력 | `frontend/src/components/SpeakerEditModal.tsx`, `SponsorPage.tsx` |
| 복잡한 편집 화면 | `frontend/src/components/BoardManagementPage.tsx` |
| 확인·삭제 다이얼로그 | `frontend/src/components/ConfirmDialog.tsx`, `confirmDialogContext.ts` |
| 알림 | `frontend/src/components/NotificationToast.tsx` |
| 엑셀 다운로드 사유 | `frontend/src/components/ExcelDownloadReasonDialog.tsx`, `excelDownloadDialogContext.ts`, `frontend/src/excelDownload.ts` |
| 엑셀 일괄등록·일괄배정 버튼과 모달 | `frontend/src/components/FreeRecipientPage.tsx`, `AbstractPage.tsx`, `ReviewerAssignmentBulkImportModal.tsx` 및 아래 전용 UI 기준 |
| 메뉴 아이콘·라우팅 | `frontend/src/menuIcons.ts`, `frontend/src/App.tsx` |

참조 화면에 과거 스타일이나 개별 구현이 남아 있어도 그대로 복사하지 않는다. 새로 작성하거나 수정하는 부분에는 이 문서와 `AGENTS.md`의 규칙을 적용한다. 특히 본문 오류 박스와 토스트의 중복 표시는 따라 하지 않는다.

사용자가 명시한 디자인 요구사항이 우선한다. 편집기·시간표 등 업무 특성에 필요한 폭과 배치 조정은 가능하지만, 등록·저장·삭제 버튼의 역할별 색상과 공통 UI 동작은 유지한다. 다른 화면 전체를 일괄 변경하는 작업은 별도 범위로 다룬다.

## 2. 기본 스타일

- Tailwind 클래스와 Lucide React 아이콘을 사용한다. 화면마다 CSS 모듈이나 별도 색상 체계를 만들지 않는다.
- 색상·배경·테두리·hover 상태에는 `dark:` 대응을 함께 지정한다. 같은 색을 유지하는 경우도 명시한다.
- 모바일 우선으로 작성한다. 작은 화면에서는 세로 배치하고 `sm:`, `md:`, `lg:`에서 확장한다.
- 기존 화면과 같은 역할의 요소에 임의로 큰 제목, 넓은 여백, 다른 모서리나 그림자를 적용하지 않는다.

| 요소 | 기본 Tailwind 기준 |
|---|---|
| 페이지 카드 | `rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-950` |
| 주요 본문 | `text-slate-900 dark:text-slate-50` |
| 폼 라벨 | `text-sm font-semibold text-slate-700 dark:text-slate-200` |
| 보조 설명 | `text-xs text-slate-400 dark:text-slate-400` |
| 페이지 제목 | `text-sm font-semibold md:text-base` |
| 모달 제목 | `text-base font-bold` |
| 카드 여백 | `p-4 md:p-5` |
| 폼 필드 간격 | `gap-4`, 섹션 간격 `space-y-5` |
| 버튼 내부 아이콘 | `h-4 w-4` |
| 모달 닫기 아이콘 | `h-5 w-5` |

제목 태그는 문서 계층에 맞게 선택하되 글자 크기는 위 스타일을 따른다.

## 3. 페이지 헤더와 도구 영역

- 왼쪽은 **작은 메뉴 아이콘과 제목 행 → 설명 행**으로 구성한다. 설명은 메뉴명이 아닌 아이콘의 왼쪽 시작점에 정렬한다. `/admin/promotional-mail`, `/admin/society-members`의 배치를 참조한다.
- 등록 버튼은 헤더 오른쪽에 배치한다. 좁은 화면에서는 아래로 자연스럽게 내려온다.
- 검색어·필터·초기화·조회는 별도 검색 영역으로 묶는다. 페이지 헤더에 별도의 새로고침 버튼을 추가하지 않는다.
- 검색창에는 검색 가능한 항목을 안내하고, 입력·선택 요소마다 label 또는 `aria-label`을 제공한다.
- 검색 조건을 바꾸면 첫 페이지부터 조회한다.
- 일반 CRUD 관리 목록은 **페이지 헤더 → 요약 통계 → 검색 조건 → 목록 → 페이지 이동**을 기본 구성으로 한다. 검색 영역이나 요약 통계를 빠뜨리지 않는다. 단일 설정 폼처럼 목록이 아닌 화면에는 해당하지 않는다.

### 검색 조건 영역

- **사전등록관리의 검색 form HTML 구조와 클래스를 기준으로 작성한다.** 별도의 `검색 조건` 제목 행이나 통계 설명 행은 추가하지 않는다.
- form은 `border-b border-slate-200 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-900/40 md:p-5`를 사용한다.
- form의 첫 자식은 필드 grid, 다음 자식은 하단 버튼 행이다. 입력 필드 옆에 조회 버튼을 끼워 넣지 않는다.
- 필드 grid는 `grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4`, 검색어 label은 `md:col-span-2`를 사용한다.
- 각 label 안에 `span`과 입력 요소를 배치한다. 항목명 span에는 `mb-1 block text-[11px] font-semibold text-slate-500 dark:text-slate-400`을 적용한다. inline span에 `space-y-*`만 의존하여 간격을 만들지 않는다.
- 검색 입력·select는 `w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:border-blue-500 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50`로 통일한다. 모달 입력란의 `py-2.5`와 구분한다.
- 검색어 입력은 `relative` div로 감싸 왼쪽에 Search 아이콘을 배치하고 입력란에 `pl-9`를 추가한다.
- 하단 버튼 행은 `mt-3 flex flex-col justify-end gap-2 sm:flex-row`로 만들고, **초기화 → 조회** 순서로 배치한다. 초기화는 FilterX 아이콘·테두리형·`px-4 py-2`, 조회는 Search 아이콘·파란색 채움·`px-5 py-2`이며 둘 다 `text-xs font-semibold`이다.
- Enter로도 조회할 수 있는 form 구조를 사용한다.
- 검색 버튼이 있는 화면은 입력 중인 조건과 적용된 조건을 구분한다. 버튼 또는 Enter를 누르면 조건 전체를 함께 적용하고 첫 페이지로 이동한다.
- 초기화는 검색어와 필터를 기본값으로 돌리고 첫 페이지를 조회한다.
- 참조 화면처럼 즉시 검색을 사용하는 경우 입력과 필터를 모두 같은 방식으로 적용한다. 일부 필터만 즉시 검색되는 혼합 동작을 피한다.

### 요약 통계 영역

- 현재 검색 결과 건수와 업무상 주요 상태별 건수를 기본 3개 내외로 제공한다. 예: 초청연자는 `검색 결과`, `공개 연자`, `주요 연자`.
- `grid grid-cols-1 sm:grid-cols-3` 형태로 배치하고 카드 사이에 구분선을 둔다. 내부 여백은 `p-4 md:p-5`, 라벨은 `text-xs font-medium`, 숫자는 `mt-1 text-xl font-bold`를 사용한다.
- 기본 건수는 slate, 정상·공개 상태는 emerald, 주요 분류는 blue 계열을 사용하고 다크모드 색상도 지정한다.
- 집계는 서버에서 검색어·구분·상태 필터를 동일하게 적용한 **전체 검색 결과**를 기준으로 계산한다. 현재 페이지의 배열 길이로 통계를 만들지 않는다.
- 목록과 통계는 같은 조회 응답에서 받고 검색·초기화·등록·수정·삭제 후 함께 갱신한다.
- 집계 기준은 구현 문서에 명시한다. 기존 화면에 없는 통계 설명 행을 검색 영역에 추가하지 않는다. 화면상 구분이 필요하면 통계 항목명으로 표현한다.
- 로딩에는 `집계 중...`, 실패에는 `-`, 정상 조회 결과가 없을 때만 `0`을 표시한다. 실패를 0건으로 오인하게 하지 않는다.

헤더 기본 배치:

```tsx
<div className="flex flex-col justify-between gap-4 border-b border-slate-200 p-4 dark:border-slate-800 sm:flex-row sm:items-center md:p-5">
    <div>
        <div className="flex items-center gap-2">
            {/* 메뉴 아이콘: h-4 w-4 shrink-0 */}
            <h1 className="text-sm font-semibold md:text-base">메뉴명 관리</h1>
        </div>
        <p className="mt-1 text-xs text-slate-400 dark:text-slate-400">관리하는 내용을 설명합니다.</p>
    </div>
    {/* 초록색 등록 버튼 */}
</div>
```

## 4. 버튼의 역할과 스타일

| 역할 | 색상·형태 | 크기·문구 |
|---|---|---|
| 목록에서 신규 등록·추가 | 초록색 채움 `emerald-600`, hover `emerald-700` | `px-3 py-2 text-xs font-semibold`, Plus 아이콘 + `연자 등록`처럼 대상 명시 |
| 모달에서 저장·확정 | 파란색 채움 `blue-600`, hover `blue-700` | `px-4 py-2 text-sm font-semibold`, `저장` |
| 검색 영역 조회 | 파란색 채움 `blue-600`, hover `blue-700` | `px-5 py-2 text-xs font-semibold`, Search 아이콘 + `조회` |
| 검색 영역 초기화 | slate 테두리형 | `px-4 py-2 text-xs font-semibold`, FilterX 아이콘 + `초기화` |
| 헤더 보조 동작 | slate 테두리형 | `px-3 py-2 text-xs font-semibold` |
| 모달 취소 | slate 테두리형 | `px-4 py-2 text-sm font-semibold`, `취소` |
| 행 수정·삭제 | 테두리형 아이콘 버튼 | `p-2`, 수정은 blue hover, 삭제는 rose hover |
| 삭제 최종 확인 | 공통 확인 다이얼로그의 danger 스타일 | `tone: 'danger'`, `confirmText: '삭제'` |

등록 버튼을 일반 검색·취소 버튼과 같은 테두리형으로 만들지 않는다. 모든 버튼은 `rounded-lg`를 사용하고, 폼 제출 외 버튼에는 `type="button"`을 지정한다.

버튼 커서는 `frontend/src/index.css`의 공통 base 스타일에서 관리한다. 활성 버튼에는 `cursor-pointer`, 비활성 버튼에는 `cursor-not-allowed`를 적용하므로 화면마다 반복 지정하지 않는다. 실제 비활성화는 `disabled`와 이벤트 처리로 구현하며 커서 변경만으로 대신하지 않는다.

등록 버튼 클래스:

```text
inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-emerald-600 dark:text-white dark:hover:bg-emerald-700
```

보조 버튼 클래스:

```text
inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900
```

아이콘만 있는 버튼은 `aria-label="연자명 수정"`처럼 대상과 동작을 명시한다. 처리 중 버튼은 비활성화하고 저장 버튼에 `저장 중...`을 표시한다. 중복 저장·삭제 요청도 핸들러에서 방지한다.

## 5. 등록·수정 모달

| 영역 | 기본 기준 |
|---|---|
| 배경 | `fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 dark:bg-slate-950/60` |
| 모달 본체 | `flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl dark:bg-slate-950` |
| 헤더 | `shrink-0`, `px-5 py-4`, 아래 구분선 `border-slate-200 dark:border-slate-800` |
| 제목·설명 | `text-base font-bold` 제목 + `mt-1 text-xs` 설명 |
| 닫기 | 테두리 없는 X 아이콘 버튼, `rounded-lg p-2`, slate hover 배경 |
| 본문 | 스크롤 가능한 `overflow-y-auto p-5`, 폼 구간 `space-y-5` |
| 하단 동작 | `mt-5 flex justify-end gap-2 border-t pt-5`, 취소 다음 저장 |

- 기본 폭은 `max-w-2xl`이다. 일반 입력 폼을 이유 없이 `max-w-3xl` 이상으로 넓히지 않는다.
- 표·리치 텍스트 편집기 등 넓은 공간이 필요한 화면은 참조 화면에 맞춰 확장할 수 있다.
- 필드는 `grid gap-4 sm:grid-cols-2`를 기본으로 하고, 약력·본문·파일 영역은 전체 폭을 사용한다.
- `role="dialog"`, `aria-modal="true"`, 제목을 가리키는 `aria-labelledby`를 제공한다.
- 열릴 때 모달에 포커스를 이동하고, Tab 이동을 모달 안에 유지하며, 닫을 때 호출 버튼으로 복원한다.
- Escape로 닫을 수 있게 하고 배경 스크롤을 잠근다. 저장 중에는 닫기·취소·Escape로 중단되지 않도록 한다.
- 긴 폼은 본문을 스크롤해 모든 필드와 저장 버튼에 접근할 수 있어야 한다.
- 모든 관리자 모달은 헤더 드래그 이동을 지원한다. 모달 본체는 `DraggableModal.tsx`의 `DraggableModal`로, form 자체가 본체이면 `DraggableModalForm`으로 감싼다. 기존 className, ref, 접근성 속성, 키보드 및 폼 이벤트는 그대로 전달한다.
- 헤더에는 `data-modal-drag-handle`과 `cursor-move select-none touch-none`을 지정한다. 본문에는 `select-none`을 적용하지 않는다. 별도 헤더가 없는 간단한 확인창은 제목을 드래그 핸들로 사용한다.
- 공통 구현이 최신 좌표, 프레임 단위 갱신, 화면 경계 및 크기 변경 보정, 포인터 취소·포커스 이탈·닫기 정리를 담당한다. 화면마다 이동 이벤트나 좌표 저장 로직을 복제하지 않는다.
- 이미 `useModalDrag`를 직접 사용하는 모달은 기존 중앙 정렬 방식과 ref 연결을 유지할 수 있다. 프로그램·학회회원 등 공통 모달을 사용하는 화면은 공통 모달에 한 번만 적용한다. CKEditor 자체 다이얼로그는 에디터의 기본 드래그를 사용한다.

<a id="excel-bulk-import-ui"></a>

### 엑셀 일괄등록·일괄배정 UI

관리자 엑셀 일괄등록·일괄배정 화면을 추가하거나 수정할 때 적용한다. `/admin/free-recipients`의 일괄등록과 `/admin/abstracts`의 심사자 일괄배정을 기준으로 한다. **이 동작의 진입·실행 버튼에는 보라색을 사용한다.** 일반 개별 등록의 초록색, 일반 저장의 파란색, 엑셀 다운로드의 보조 버튼 스타일은 유지한다.

#### 목록에서 모달을 여는 버튼

- `FileSpreadsheet` 아이콘(`h-4 w-4`) + `엑셀 일괄등록`, `심사자 일괄배정` 등 업무에 맞는 문구로 구성한다.
- 연한 보라색 배경·테두리·글자색을 사용하며 아래 크기와 hover·다크모드를 적용한다.

```text
inline-flex items-center justify-center gap-2 rounded-lg border border-violet-200 bg-violet-50 px-3 py-2 text-xs font-semibold text-violet-700 hover:bg-violet-100 disabled:opacity-50 dark:border-violet-900/60 dark:bg-violet-950/30 dark:text-violet-300 dark:hover:bg-violet-950/50
```

#### 모달 헤더와 본문

- 헤더 왼쪽은 **아이콘과 제목 행 → 설명 행**으로 구성하고 닫기 버튼은 오른쪽 위에 둔다. 설명은 아이콘을 포함한 왼쪽 영역의 시작점에 정렬한다.
- 헤더: `flex shrink-0 items-start justify-between gap-4 border-b border-slate-200 px-5 py-4 dark:border-slate-800`.
- 제목: `flex items-center gap-2 text-base font-bold text-slate-900 dark:text-slate-50`.
- 제목 아이콘: `FileSpreadsheet`, `h-5 w-5 shrink-0 text-violet-600 dark:text-violet-400`.
- 설명: `mt-1 text-xs text-slate-500 dark:text-slate-400`. 기존 자료 유지·추가 등의 실제 업무 동작을 짧게 안내한다. 예: ‘엑셀 명단을 이용해 기존 대상자를 유지하면서 무료 대상자를 추가합니다.’
- 우측 X 버튼: `shrink-0 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 disabled:opacity-40 dark:text-slate-400 dark:hover:bg-slate-900`. 아이콘은 `h-5 w-5`, `aria-label="닫기"`를 제공한다.
- 최신 공통 `DraggableModal` / `DraggableModalForm`을 사용하고 헤더에 `data-modal-drag-handle` 및 `cursor-move select-none touch-none`을 지정한다. 기존 `useModalDrag` 직접 사용 화면은 5절의 예외를 따른다.
- 모달 폭·최대 높이·접근성은 5절을 따른다. 본문은 `overflow-y-auto p-5`를 사용한다. 파일 제한·양식 다운로드·파일 선택·추가 설정·결과 표는 각 업무에 맞게 유지한다.
- 헤더와 하단 동작을 본문과 구분한다. 별도 footer를 두는 경우 `flex shrink-0 justify-end gap-2 border-t border-slate-200 px-5 py-4 dark:border-slate-800`을 사용한다. 기존 폼 내부 footer라면 본문 여백과 중복되지 않게 `flex justify-end gap-2 border-t border-slate-200 pt-5 dark:border-slate-800`을 사용한다.

#### 하단 버튼

닫기 다음 실행 순서로 배치하며, 두 버튼 모두 `px-4 py-2 text-xs font-semibold` 크기를 사용한다.

닫기 버튼:

```text
rounded-lg border border-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40 dark:border-slate-800 dark:text-slate-300 dark:hover:bg-slate-900
```

일괄등록·일괄배정 실행 버튼:

```text
inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-xs font-semibold text-white hover:bg-violet-700 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-violet-600 dark:text-white dark:hover:bg-violet-700
```

- 평상시 `Upload` 아이콘(`h-4 w-4`)과 `일괄등록` / `일괄배정`을 표시한다.
- 처리 중에는 `LoaderCircle` 아이콘(`h-4 w-4 animate-spin`)과 `등록 중` / `배정 중`을 표시한다.
- 실행 중에는 중복 실행·파일 변경·닫기·Escape·배경 클릭에 의한 종료를 막는다. 파일 미선택, 검증 실패, 이미 처리된 결과 등 기존 비활성화 조건을 보존한다.
- 폼 제출은 `type="submit"`, 그 밖의 버튼은 `type="button"`으로 지정한다. 기존 공통 토스트·확인창과 포커스 이동·복원 동작을 유지한다.
- 결과 검토가 필요한 일괄등록 모달은 처리 후 자동으로 닫지 않고 성공·건너뜀·실패 건수와 행별 결과를 유지한다. 업무별 재선택·재시도 동작을 보존한다.
- 스타일 통일만으로 API·파일 제한·XLSX 헤더·중복 처리·DB 저장 규칙을 변경하지 않는다. 양식 자체를 수정할 때는 `excel_bulk_import_template_guide.md`도 확인한다.

## 6. 입력란·파일·상태 설정

폼 입력란 기본 클래스:

```text
w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm font-normal text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-50
```

- 라벨은 `space-y-1.5 text-sm font-semibold`를 사용하고 필수값은 rose 색상의 `*`로 표시한다.
- `input`, `select`, `textarea`의 테두리·배경·글꼴·포커스 스타일을 통일한다. placeholder만으로 라벨을 대체하지 않는다.
- 폼 값은 React state로 관리한다. 길이·필수 여부·숫자 범위·허용 파일 조건은 서버 검증과 일치시킨다.
- 검증 실패는 공통 오류 토스트로 전달하고 입력값을 유지한다. 필요한 경우 해당 필드로 포커스를 이동한다.
- 공개·사용 여부 같은 독립 설정은 테두리 카드 안에 라벨과 `h-5 w-5` 체크박스를 배치한다.
- 이미지 업로드는 미리보기, 파일명, 허용 형식·용량 안내, Upload 아이콘이 있는 선택·교체 버튼으로 구성한다. 브라우저 기본 파일 입력을 그대로 노출하지 않는다.
- 파일 제거와 교체는 사용자가 구분할 수 있게 표시하고, 임시 미리보기 URL은 해제한다.
- 드래그 안내는 실제 드래그 업로드를 지원할 때만 표시한다.
- 공개 여부 등을 편집하는 화면은 실제 구현된 동작만 설명한다. 내부 API·SQL 같은 구현 용어를 일반 사용자 안내에 넣지 않는다.

### 국가 선택

- 관리자 국가 입력에는 `frontend/src/components/CountrySelect.tsx`를 사용한다. 검색 UI와 키보드·다크모드·모달 드롭다운 처리는 내부의 `SearchableSelect.tsx`에서 공통 관리한다.
- 국가 목록은 페이지나 모달이 기존 API로 조회하여 `countries`로 전달한다. 반복되는 기관·저자·연자 행에서 각각 조회하지 않는다.
- `value`와 `onChange`는 문자열을 사용한다. `valueKey`는 회원·초록의 경우 기본값 `countryName`, 초청연자는 `isoAlpha2`, 프로그램은 `seq`로 지정하여 기존 API 저장 형식을 유지한다. 프로그램의 숫자 변환은 기존 제출 처리에서 수행한다.
- 영문·한글 국가명과 ISO 코드로 검색할 수 있다. `showDialCode`를 지정하면 전화 국가번호를 표시하고 검색한다. 회원의 `onChange`에는 기존 전화 국가번호 연동 핸들러를 연결한다.
- `ariaLabel`을 제공하고 반복 행에는 기관·저자 번호 등으로 구분한다. 필요에 따라 `required`, `loading`, `disabled`, `invalid`, `describedBy`를 지정한다. 저장된 값이 사용 국가 목록에서 제외되어도 기존 값으로 표시하며, 국가명이 별도로 있으면 `fallbackLabel`로 전달한다.
- 선택 목록의 검색어는 저장값과 별개다. 검색만 하거나 Escape·Tab으로 닫아도 기존 값은 유지하며, 선택 해제 시 빈 문자열을 반환한다.

## 7. 목록과 화면 상태

- 표는 `overflow-x-auto`로 감싸 좁은 화면에서도 페이지 전체가 가로로 밀리지 않게 한다.
- 표 헤더는 밝은 slate 배경, 본문은 구분선과 hover 배경을 사용한다. 셀 간격은 `p-4`, 기본 글자는 `text-xs md:text-sm`이다.
- 헤더 글자 크기는 table에 지정한 크기를 상속한다. `thead`에 `text-xs`를 따로 넣어 본문보다 작게 만들거나 `uppercase`로 강제 변환하지 않는다.
- 테이블 헤더(`thead th`)의 글자 굵기는 `frontend/src/index.css`의 공통 base 스타일에서 `font-semibold`로 적용한다. 화면별 헤더에 같은 클래스를 반복하거나 `font-bold`, `font-medium` 등 다른 굵기를 지정하지 않는다. 본문 셀의 강조 스타일은 용도에 따라 유지한다.
- 테이블 헤더(`thead`)의 글자색은 `frontend/src/index.css`의 공통 base 스타일에서 초청연자 관리 기준인 `text-slate-600 dark:text-slate-300`으로 적용한다. 페이지·모달의 헤더에 글자색 클래스를 별도로 반복하거나 다른 색으로 덮어쓰지 않는다. 새 테이블에도 자동 적용되며, 배경·테두리·정렬은 화면 구성에 맞게 지정한다.
- 이름과 주요 정보는 강조하고 보조 정보는 작고 옅게 표시한다. 작업 버튼은 행마다 같은 위치에 둔다.
- 사용·미사용·주요 상태는 문구로 명시한다. 색상만으로 구분하지 않는다.
- 긴 이름·이메일·URL은 줄바꿈 또는 말줄임으로 표 폭이 과도하게 늘어나지 않도록 한다. 전체 값은 상세·수정 화면에서 확인할 수 있게 한다.
- 로딩, 정상 데이터, 검색 결과 없음, 조회 실패를 구분한다. 실패를 `등록된 데이터 없음`으로 표시하지 않는다.
- 조회 실패의 구체적인 원인은 토스트 한 번으로 전달하고 본문은 재시도 가능한 상태 안내를 표시한다.
- 총 건수·현재 페이지·이전/다음 동작을 제공하고, 필터 변경이나 마지막 행 삭제 후 유효한 페이지로 보정한다.

## 8. 공통 동작 및 메뉴 연결

- 확인은 `useConfirm()`으로 호출한다. `window.confirm()`이나 화면별 삭제 확인 모달을 새로 만들지 않는다.
- 성공·오류·안내는 `onNotify('success' | 'error' | 'info', message)`를 사용한다. `window.alert()`나 개별 토스트를 추가하지 않는다.
- 모달에서 발생한 알림도 공통 토스트로 화면 오른쪽 상단에 표시한다. `NotificationToast`는 `document.body` 포털에 렌더링하여 모달의 위치·스크롤·잘림에 영향을 받지 않게 한다. 동일한 알림을 모달 본문 오류 박스로 중복 표시하지 않는다. 필드별 안내와 일괄등록 결과 표는 유지한다.
- API 실패 시 모달과 입력값을 유지한다. 성공한 뒤 모달을 닫고 목록을 갱신한다.
- 비동기 조회는 취소 또는 이전 응답 무시 처리를 통해 오래된 검색 결과가 최신 결과를 덮어쓰지 않게 한다.
- 개인정보가 포함된 엑셀 다운로드에는 기존 사유 입력·감사 이력 기능을 사용한다. 일괄등록 양식 작업은 `document/excel_bulk_import_template_guide.md`도 확인한다.
- 관리자 메뉴는 `menu_settings`의 키, `App.tsx`의 지원 메뉴 및 렌더링, `menuIcons.ts`의 아이콘을 함께 연결한다.
- 메뉴 노출만으로 접근을 제어하지 않는다. 관리자 API는 기존 관리자 세션·권한·CSRF 처리 구조를 따른다.

## 9. 완료 전 확인

- [ ] 개별 등록 버튼은 초록색, 일반 저장은 파란색, 취소·보조 버튼은 테두리형인가?
- [ ] 엑셀 일괄등록·일괄배정의 진입 버튼, 아이콘·제목·설명 헤더, 하단 닫기·보라색 실행 버튼이 전용 UI 기준과 일치하는가?
- [ ] 일반 관리 목록에 검색 조건 영역과 요약 통계가 있으며, 검색·초기화·등록·수정·삭제 후 목록과 통계가 함께 갱신되는가?
- [ ] 검색 form이 사전등록관리와 같은 필드 grid + 하단 초기화/조회 버튼 구조이며, 항목명·입력란 간격과 버튼 색상·순서가 일치하는가?
- [ ] 통계가 현재 페이지가 아닌 전체 검색 결과를 기준으로 집계되고, 로딩·오류가 0건과 구분되는가?
- [ ] 제목·아이콘 크기, 카드 모서리·여백, 모달 폭과 헤더가 참조 화면과 일치하는가?
- [ ] 입력란·필수 표시·파일 선택 버튼·상태 설정이 같은 스타일인가?
- [ ] 라이트·다크 모드와 모바일에서 잘림·가로 넘침 없이 사용할 수 있는가?
- [ ] 긴 폼 스크롤, 키보드 포커스, Escape, 저장 중 중복 조작을 처리했는가?
- [ ] 로딩·빈 목록·오류·저장 성공 상태와 공통 확인·토스트가 동작하는가?
- [ ] 필요한 메뉴·라우팅·아이콘을 연결했는가?
- [ ] 코드 변경 시 TypeScript 검사와 변경 파일 린트가 통과했는가?

화면 코드를 변경했다면 `frontend/`에서 `npm exec -- tsc -b`와 변경 파일을 지정한 ESLint를 실행한다. 스타일·배치 변경은 가능한 경우 브라우저에서도 참조 화면과 비교한다. 문서만 변경한 경우 빌드나 기능 테스트는 불필요하며, 실제로 확인하지 못한 항목은 완료 보고에서 구분한다.
