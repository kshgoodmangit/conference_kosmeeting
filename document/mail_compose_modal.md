# 공통 안내메일 작성 및 이력 저장

`MailComposeModal.tsx`는 목록에서 선택한 수신자의 안내메일을 작성·미리보기하고 이력을 저장한다. 실제 전송, 예약 전송, 자동 발송은 구현하지 않는다.

## 적용 화면

| 화면 | sourceMenu | 서버 확인 수신자 |
|---|---|---|
| /admin/pre-registrations | pre-registrations | 등록 회원 이름·이메일·소속 |
| /admin/abstracts | abstracts | 접수 회원 이름·이메일·소속 |
| /admin/abstracts/oral | oral-accepted-abstracts | 접수 회원 이름·이메일·소속 |
| /admin/abstracts/poster | poster-accepted-abstracts | 접수 회원 이름·이메일·소속 |
| /admin/speakers | speakers | 연자명·contactEmail·affiliation |
| /admin/sponsorship | sponsorship | 담당자명·contactPersonEmail·회사명 |

초록 공동저자·교신저자와 후원 세금계산서 이메일은 자동 포함하지 않는다. 구두·포스터 원본 연결 유형도 ABSTRACT이다.

## 재사용 계약

공통 ConfirmDialogProvider 안에서 sourceMenu, isOpen, recipients, onClose, onNotify를 전달한다. recipients의 각 항목은 고유 id, 원본 PK sourceSeq, name, 선택적 email·affiliation을 가진다.

```tsx
<MailComposeModal
    sourceMenu="speakers"
    isOpen={recipients !== null}
    recipients={recipients ?? []}
    onClose={() => setRecipients(null)}
    onNotify={onNotify}
/>
// 수신자 예: { id: `speaker-${speaker.seq}`, sourceSeq: speaker.seq,
// name: speaker.displayName, email: speaker.contactEmail, affiliation: speaker.affiliation }
```

- 표시용 이름·이메일을 서버에 신뢰시키지 않는다. 서버는 메뉴와 남은 sourceSeq 목록으로 선택 학회의 최신 원본을 조회한다.
- sourceMenu 확장은 프론트 타입, 서버 허용 목록, 원본 조회를 함께 변경한다.
- 선택은 페이지 이동 시 유지하고 검색 적용·필터 변경·초기화 시 해제한다. 전체 선택은 현재 페이지에 적용한다.
- 모달은 처음 전달된 배열을 복사한다. 내부 수신자 제외는 목록 체크박스와 원본을 변경하지 않는다.
- useMailSelection과 MailSelectionControls가 공통 선택 상태와 UI를 담당한다. 수정 응답은 선택 정보를 동기화하고 삭제 성공 시 선택에서 제외한다.
- 초록 세 메뉴 전환 시 선택·작성 상태를 새로 시작한다.

## 작성·저장

1. 수신자 검색·제외·복원, 제목·본문 작성, 첨부파일 선택을 제공한다.
2. 유효 이메일·제목·본문을 확인한 뒤 미리보기 또는 이력 저장을 실행한다. 미리보기만으로는 저장하지 않는다.
3. POST /api/admin/mail-history로 request JSON과 files를 하나의 multipart 요청에 보낸다.
4. 서버에서 이메일 정규화·중복 제거·수신 거부 검사·본문 정화를 하고 원본 연결과 첨부를 함께 저장한다.
5. 성공 시 미발송임을 토스트로 알리고 닫는다. 실패 시 작성 내용·선택 파일을 유지한다.

- 제목 최대 500자, 본문 최대 1,000,000자, 원본 항목 최대 1,000개.
- 첨부 최대 5개·합계 10MB, 빈 파일 불가. PDF, DOC/DOCX, XLS/XLSX, PPT/PPTX, ZIP, PNG/JPG/JPEG/GIF/WEBP, TXT 허용.
- 본문은 공통 CKEditor로 편집하며 저장 직전에 HTML 소스의 최신 내용을 읽는다. 본문 이미지 업로드는 미연결.
- 같은 내용의 실패 요청 재시도는 동일 UUID를 사용한다. 저장 중 편집·중복 저장·닫기·Escape를 막는다.
- 미리보기 sandbox iframe, 닫기 확인, 드래그, 배경 스크롤 잠금, Tab 이동, 종료 후 포커스 복원을 유지한다.

DB 구조·API·배포 순서는 mail_history.md, 적용 SQL은 mail_history_migration.sql을 참고한다.
