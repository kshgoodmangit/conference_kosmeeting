# 대한안과학회 요구사항 대비 추가 구현 목록

검토일: 2026년 9월 9일  
대상: `bjworld21_cms_congress` 현재 작업 폴더, 기준 커밋 `8d3ee64`  
요구사항 기준: [기능 요구사항 목록](D:/dev/java/intellij/bjworld21_cms_congress/document/kosmeeting_requirements_inventory.md)

현재 프로젝트에는 관리자 초록 관리, 심사위원 배정과 평가, 채택 처리, 프로그램 편성, 사전등록 관리, CMS, 주소록, 메일·문자 발송 준비 등이 구현되어 있다. 추가 개발의 중심은 **사용자 직접 신청 흐름, 실제 결제·환불과 발송 연동, 발표파일 관리, 회차 분리, 세부 권한·감사로그**다.

특히 메일·문자는 실제 전송 전 준비 단계이며, 사전등록 결제 취소는 PG 환불 없이 DB 상태를 변경한다. 메뉴명이나 상태값이 존재하는 것과 해당 업무가 완료되는 것은 구분하여 판단했다.

## 1 검토 범위와 판정 기준

React 관리자 화면, 공개 홈페이지의 Thymeleaf 템플릿·JavaScript, 컨트롤러·서비스·리포지토리·DTO, 스키마, 관련 테스트 코드와 설계 문서를 대조했다. AGENTS.md에 남아 있는 ‘백엔드 클래스 1개’ 등의 설명은 현재 소스와 다르므로 판정 근거로 사용하지 않았다.

이번 검토는 **정적 소스 분석**이다. 서버 실행, 브라우저 기능 검수, 테스트 재실행, 운영 DB 조회, 외부 PG·메일·문자 전송은 수행하지 않았다. DB에 저장된 실제 메뉴 콘텐츠, 과거 업체 시스템, 운영 인프라와 계약 산출물은 별도 확인이 필요하다. ‘코드 확인’은 운영 검수 완료를 의미하지 않는다.

| 판정 | 의미 |
|---|---|
| 미구현 | 현재 소스에서 해당 업무를 수행하는 화면·API·서비스 연결을 확인하지 못함 |
| 부분 구현 | 재사용 가능한 구현이 있으나 원문 세부 조건 또는 업무 완료 흐름이 부족함 |
| 코드 확인 | 해당 요구의 주요 구현을 확인하여 이번 추가 구현 목록에서 제외. 실행 검수는 별도 |
| 확인 필요 | 운영 환경·실제 콘텐츠·외부 시스템·합의된 기준을 확인해야 판정 가능 |

아래 표의 요구 ID는 기존 목록과 동일하다. 여러 행에 같은 기반 기능이 언급되어도 각각을 독립적인 신규 개발로 중복 산정하지 않는다. 예를 들어 실제 메일 발송 엔진은 접수확인·결과통지·독촉·초대메일에 공통 사용한다.

| 구분 | 미구현 | 부분 구현 | 확인 필요 | 코드 확인 | 합계 |
|---|---:|---:|---:|---:|---:|
| 시스템 기능 | 23 | 56 | 2 | 3 | 84 |
| 운영·제작·검수 | 0 | 2 | 12 | 0 | 14 |
| 전체 | 23 | 58 | 14 | 3 | 98 |

시스템 기능 중 추가 개발 대상은 **79개 요구 ID**에 걸쳐 있다. 이는 작업 티켓 79개나 구현률을 뜻하지 않는다. 하나의 요구 ID에 여러 세부 조건이 묶여 있어 주요 기능이 있어도 일부 조건이 부족하면 ‘부분 구현’으로 분류했다.

## 2 우선 검토할 핵심 차이

| 순서 | 핵심 차이 | 현재 상태와 추가 작업 |
|---|---|---|
| 1 | 사용자 직접 접수·등록 경로 | 공개 사이트는 메뉴 HTML을 렌더링한다. 회원 초록 생성·수정 서비스는 있으나 이를 호출하는 사용자 API가 없고, 사전등록은 관리자 기능 중심이다. 사용자 인증·본인 내역·접수·등록·결제를 연결해야 한다. |
| 2 | 실제 결제·환불 | PG 연동 구조와 결제 테스트가 있다. 등록 결제 확정·원장 연결, 실제 취소·부분환불·결과 검증은 추가해야 한다. 현재 `cancelPayment`는 DB를 `REFUNDED`로 변경한다. |
| 3 | 실제 메일·문자 전송 | 수신자 선택·중복 제거·예약시각·작업 저장은 있다. 전송 작업자와 예약 실행, 공급자 연동, 테스트발송·재발송·결과 관리가 남아 있다. |
| 4 | 발표파일 제출 | 초록 첨부 조회는 있으나 발표파일 업로드·재제출·버전·최종본·마감·동의 통제는 별도 구현이 필요하다. |
| 5 | 등록 옵션과 무료등록 | 등록비·회원별 요금 확인은 있다. 옵션 상품·수량·재고, 변경 차액, 무료 한도·초대대상 인증·진행 추적은 없다. |
| 6 | 다회차와 학회 인증 | 학회 설정 이력은 있으나 최신 설정 사용 방식이고 초록·등록 등의 회차 분리가 부족하다. 회차 복제·이관 조회·국영문·종료 자료 회원 인증을 설계해야 한다. |
| 7 | 기존 정책과 원문 충돌 | 회원 제출 초록의 관리자 수정을 명시적으로 금지하고 있다. 과업지시서는 관리자 수정을 요구하므로 권한·사유·변경이력과 함께 정책을 수정해야 한다. |
| 8 | 초록 변경과 프로그램 동기화 | 프로그램 배정 시 제목·발표자를 복사한다. 초록 수정 시 프로그램 값을 자동 갱신하는 연결은 확인되지 않아 일괄 연동 정책이 필요하다. |

이 순서는 의존관계를 고려한 검토 제안이며, 계약상 중요도나 확정 개발 일정은 아니다.

## 3 공통 기능과 홈페이지

| 요구 ID | 판정 | 현재 확인한 구현 | 추가 구현 또는 확인할 내용 | 근거 |
|---|---|---|---|---|
| COM-01 | 부분 구현 | 학회 설정 등록·수정·목록, 공개 홈페이지 | 회차별 데이터·홈페이지 선택, 춘계·추계 분리, 국영문 콘텐츠 및 언어 전환 | E01, E02 |
| COM-02 | 미구현 | 설정 추가는 가능 | 과거 회차 전체 복제, 날짜·명칭·URL·연동값 일괄 치환, 잔존값 검사 | E01 |
| COM-03 | 부분 구현 | 주요 관리 목록의 검색·필터·페이징·엑셀 | 모든 목록의 정렬·선택 일괄처리·내보내기 일관화. 예: 초록 API는 정렬·선택 ID 입력이 없고 주소록은 목록 전체 반환 | E03, E08, E13 |
| COM-04 | 부분 구현 | 행사기간·공통코드·메뉴 HTML, 메일 본문 편집 | 주요 정책·문구·필수항목 설정 확대, 재사용 가능한 메일·SMS 템플릿 관리 | E01, E04, E11 |
| COM-05 | 부분 구현 | 접속로그·엑셀 다운로드 감사, 결정자·결정사유 | 전체 CRUD·발송·권한 변경의 행위자·일시·변경 전후 값 기록 | E06, E14 |
| COM-06 | 확인 필요 | 현행 데이터 조회는 가능 | 과거 회차 식별·분리 및 이관 자료 조회 체계. 원본 자료와 매핑·이관 검증은 별도 | E01, E18 |
| WEB-01 | 부분 구현 | 행사명·일시·장소·메뉴 바로가기 | 실제 마감일 연결, 공지·팝업·후원사 메인 연동, 뉴스레터·전차대회·언어 전환. 현재 마감일 영역은 고정 안내문 | E02 |
| WEB-02 | 확인 필요 | 메뉴별 HTML 콘텐츠 편집·공개 | 인사말·위원회·숙박·비자·교통 등 대한안과학회 필수 콘텐츠의 실제 등록 여부 및 회차별 제작 | E02 |
| WEB-03 | 부분 구현 | 관리자 일정·룸·세션·연자 편성 | 일반 방문자용 Program at a Glance, 일자·룸·분과·연자 검색, 발표일정 검색·PDF 출력 | E02, E07 |
| WEB-04 | 부분 구현 | 관리자 초록 화면, 회원용 서비스 메서드 일부 | 사용자 접수·확인·수정·채택결과·발표파일 API 및 공개 화면 연결. 안내 콘텐츠는 DB 확인 필요 | E02, E03, E04 |
| WEB-05 | 부분 구현 | 등록비와 관리자 사전등록 관리 | 사용자 온라인 등록·본인 내역 변경·결제·영수증. 국내외 구분을 실제 신청 흐름에 적용 | E08, E09 |
| WEB-06 | 부분 구현 | 후원사와 전시·후원 신청 관리자 CRUD | 업체 로그인, 업체 본인 신청·조회 흐름, 공개 안내·로고·링크 연결 확인 및 보완 | E02, E17 |
| WEB-08 | 미구현 | 로컬 학회회원 명단·요금 확인은 있음 | 학회 회원 로그인 연동, 종료 회차 자료 회원 전용 검색·열람, 비회원·검색엔진 차단. 로컬 회원 요금 조회는 SSO가 아님 | E02, E10, E14 |
| WEB-09 | 미구현 | 관리자 접근 통제 일부 | 자료별 공개대상·다운로드 허용 정책, 서버 조회·다운로드에서 정책 검사 | E02, E14 |

`WEB-07` 공지·팝업 관리자 작성·변경은 구현 근거를 확인했으므로 10절에 별도 기재했다.

## 4 초록 접수와 입력 품질

| 요구 ID | 판정 | 현재 확인한 구현 | 추가 구현 또는 확인할 내용 | 근거 |
|---|---|---|---|---|
| SUB-01 | 부분 구현 | 접수 시작·종료일, 발표형식·분과 코드, 고정 검증 | 회차·언어·증례 및 원저별 접수 설정, 연장 정책, 관리자 필수항목·길이 설정 및 실제 접수 시 기간 검사 | E01, E04 |
| SUB-02 | 부분 구현 | 제목·항목별 본문·저자·소속·연락처, AI·표절 관련 확인 | 접수항목의 관리자 동적 설정, 이해관계·개인정보 동의 등 학회 필수항목과 정책 적용 | E04 |
| SUB-03 | 부분 구현 | 접수번호 자동 생성 | 발표형식별 접수 조건, 회차별 번호 규칙. 현재 접수번호의 APDRC8 고정 접두사도 변경 대상 | E04 |
| SUB-04 | 부분 구현 | 저장 후 응답, 메일 캠페인의 발신자·회신처 필드 | 사용자 접수 완료 화면, 접수 이벤트와 자동 확인메일 연결, 재발송 | E03, E04, E11 |
| SUB-05 | 부분 구현 | 저자·소속을 별도 행으로 저장 | 저자당 단일 `institutionNo` 선택을 복수 소속 체크 매핑으로 확장 | E04, E05 |
| SUB-06 | 부분 구현 | 소속 번호와 저자 순서 데이터 | 소속 중복 제거, 윗첨자 자동 표기, 동일 소속 번호 생략 등 학회별 표기 설정 | E04, E05 |
| SUB-07 | 부분 구현 | 저자 순서 값, 발표자·교신저자 지정 | 저자 드래그 정렬, 국영문 이름 검증, 외국인 발표자 예외 정책 | E05 |
| SUB-08 | 부분 구현 | 일반 입력란·textarea로 입력, 문자열 trim | 강제 줄바꿈·허용 기호·제어문자 정규화와 서버 저장 검증. 일반 입력란만으로 원문 전체 정규화 조건을 충족하지는 않음 | E04, E05, E19 |
| SUB-09 | 부분 구현 | 목적·방법·결과·결론 분리, 초록당 엑셀 한 행 생성 | 허용되지 않는 제어문자의 안전 처리. XLSX 작성기는 XML 기호 이스케이프를 하지만 제어문자 제거는 확인되지 않음 | E04, E19 |
| SUB-10 | 미구현 | 저장된 초록 상세보기 | 저장 전 홈페이지·심사·출판 표기 동시 미리보기, 오류 위치 안내 | E05 |
| SUB-11 | 미구현 | 기관 번호 중복 검사는 있음 | 초록 제목 정규화·동일 및 유사 제목 검색, 제출자·관리자 경고. 기관 번호 검사는 제목 중복 검사와 별개 | E04, E05 |

## 5 초록 관리 심사와 출판

| 요구 ID | 판정 | 현재 확인한 구현 | 추가 구현 또는 확인할 내용 | 근거 |
|---|---|---|---|---|
| ABS-01 | 부분 구현 | 키워드·발표형식·승인형식·분과·상태 필터 및 페이징 | 접수·수정일 조건과 요구된 상세 검색항목·정렬 확장 | E03 |
| ABS-02 | 부분 구현 | 관리자 신규·상세·수정·삭제 | 복제·철회·복구, 세부 삭제권한. 회원 제출분 관리자 수정 금지 정책을 원문에 맞게 변경 | E03, E04 |
| ABS-04 | 부분 구현 | DB 수정 후 목록 재조회, 프로그램 최초 배정 시 값 복사 | 프로그램 제목·발표자 자동 갱신, 심사 중 변경의 처리 정책과 연동 검증 | E04, E07 |
| ABS-05 | 미구현 | 공통 메일 준비 기반 | 초록 확인메일 개별·선택·전체 재발송, 초록별 수신주소·성공 및 실패 이력 | E03, E11 |
| ABS-06 | 부분 구현 | 검색조건 기반 XLSX, 개별 첨부파일 조회 | 선택 초록 다운로드, Word·인쇄용 출력, 첨부파일 일괄 압축 다운로드 | E03, E04 |
| ABS-07 | 부분 구현 | 최종 채택 결정 사유 저장 | 초록 일반 수정·변경 사유 메모와 누적 이력. 결정 사유만으로 대체 불가 | E05, E06 |
| ABS-08 | 부분 구현 | 분과·상태·일별 대시보드와 표·그래프 | 전체 요구 차원별 필터·엑셀, 전 회차 비교 | E15 |
| REV-01 | 부분 구현 | 심사위원 계정·프로필, 초록별 배정·엑셀 일괄배정, 마감일 | 분과 단위 배정 흐름, 시작일 포함 심사기간 정책, 심사 안내메일 설정·연결 | E06 |
| REV-03 | 부분 구현 | 심사 완료·미완료 상태와 배정 현황 | 미심사자 독촉 대상 추출과 실제 독촉 발송 | E06, E11 |
| REV-04 | 부분 구현 | 심사위원별·항목별·전체 평균 및 제출 현황 | 총점, 최고·최저점 제외 규칙, 동점·결측 확인, 심사결과 전용 엑셀 | E06 |
| REV-05 | 부분 구현 | 개별 승인·반려, 승인 발표형식 지정 | 보완 상태, 발표번호, 일괄결정, 결과통지, 결정 시 분과 변경 흐름 | E06 |
| REV-06 | 미구현 | 일반 메일 HTML 저장 기반 | 결과·발표형식·언어별 템플릿과 초록 정보 치환, 개별·선택·전체 결과·발표 안내 발송 및 재발송 | E06, E11 |
| REV-07 | 미구현 | 회원 초록은 심사 시작 후 수정 금지 | 채택 후 수정기간·허용 필드·변경 비교·승인. 현재 금지 정책과 충돌 | E04 |
| PRG-01 | 부분 구현 | 일자·룸·계층형 프로그램·시간·순서·좌장·연자·초록 연결 | 프로그램 항목의 분과 분류·변경 지원 보완. 초록 후보의 분과 필터는 있으나 프로그램 항목 자체에는 분과 필드가 없음 | E07 |
| PRG-02 | 부분 구현 | 채택 초록의 프로그램 항목 배정·해제 | 출판리스트·출판용 정렬, 프로그램북 Word·Excel 출력 | E07 |

`ABS-03` 수정과 발송 분리, `REV-02` 심사위원 로그인·평가·임시저장·제출은 10절에 기재했다.

## 6 발표파일

| 요구 ID | 판정 | 현재 확인한 구현 | 추가 구현 또는 확인할 내용 | 근거 |
|---|---|---|---|---|
| FIL-01 | 미구현 | 초록 첨부 메타데이터·다운로드 기반 | 본인 인증과 연계한 사용자 발표파일 자체 업로드. 관리자 초록 첨부 조회와 별도 업무 | E03, E18 |
| FIL-02 | 미구현 | AI·표절 확인 항목은 있음 | 상업적 이해관계·게재 동의 확인과 발표파일 업로드 차단·오류 안내 | E04 |
| FIL-03 | 미구현 | 첨부파일 테이블 | 재제출 버전·최종본 구분, 제출 이력, 마감일까지 수정 | E18 |
| FIL-04 | 미구현 | 공통 업로드 저장 경로 | 발표형식별 확장자·용량·파일명·제출기간 관리자 설정 및 검사 | E18 |
| FIL-05 | 미구현 | 일반 초록 현황 | 발표파일 제출·미제출·재제출·최종 제출일시 현황 및 엑셀 | E03, E18 |

## 7 등록 결제와 무료등록

| 요구 ID | 판정 | 현재 확인한 구현 | 추가 구현 또는 확인할 내용 | 근거 |
|---|---|---|---|---|
| REG-01 | 부분 구현 | Early Bird·Regular 기간, 등록구분별 요금, 학회회원 요금 매핑 | 회차별 현장등록 기간, 회원·직종·국내외·참가유형 조합 정책을 명시적으로 적용 | E01, E08, E10 |
| REG-02 | 미구현 | 기본 등록비 | 식사·연회·워크숍·교육·동반자·추가자료 옵션, 수량·마감·중복선택 규칙 | E08, E18 |
| REG-03 | 부분 구현 | PG 설정 구조·통화·등록비 | 관리자 결제수단·계좌·세금·영수증·취소환불 규칙·동의 문구 설정 | E08, E09 |
| REG-04 | 부분 구현 | 키워드·등록구분·기간·신청 및 결제 상태·신청일 필터 | 옵션·금액·회원번호·면허번호 등 요구 검색항목 확장과 정렬 | E08 |
| REG-05 | 부분 구현 | 관리자 등록·조회·수정·메모·취소 상태, 동일 회원 중복 신청 차단 | 실제 중복등록 병합, 병합 이력. 취소·복구 시 결제 및 관련 데이터까지 정합성 확인 | E08 |
| REG-06 | 미구현 | 등록자 전체 수신자 선택 기반 | 등록확인 메일·문자 개별·선택·전체 발송·재발송과 등록별 이력 | E08, E11, E12 |
| REG-07 | 부분 구현 | 검색조건 반영 XLSX | 다운로드 필드 선택 | E08 |
| REG-08 | 미구현 | 등록 정보 조회 | 명찰·영수증·확인서·비자레터·참가증 출력 템플릿과 발급 | E08 |
| REG-09 | 부분 구현 | 관리자 신규등록·정보 수정 기반 | 현장 구분과 최종 연회비·등록비·옵션 입력, 사전등록 통합·대사. 별도 최종 내역 구조 필요 | E08, E18 |
| REG-10 | 미구현 | 관리자 상세조회 | 현장 등록자 본인 인증·내역 조회·증빙 출력 | E08, E14 |
| PAY-01 | 부분 구현 | 결제수단·거래번호·금액·일시 필드, PG 테스트 요청 | 실제 등록 건의 승인·입금·결제 확정, 승인번호와 결제 내역 수집·검증 | E08, E09 |
| PAY-02 | 부분 구현 | PAID를 REFUNDED로 바꾸는 관리 기능 | PG 실제 전액·부분취소, 입금확인·면제·환불예정·완료 관리, 사유·상태 이력 | E08, E09 |
| PAY-03 | 미구현 | 등록금액·결제 상태 데이터 | 최종 등록·추가결제·환불을 반영한 영수증·거래명세·등록확인서 | E08 |
| PAY-04 | 미구현 | 등록구분·기본 요금 수정 | 등록 유지 상태에서 옵션 추가·삭제·수량 변경 | E08, E18 |
| PAY-05 | 미구현 | 등록 건의 단일 결제 정보 | 차액 계산·추가결제·부분환불, 원결제와 변경결제 연결 원장 | E08, E09, E18 |
| PAY-06 | 부분 구현 | 등록구분·기간·일별 인원 및 금액, 기본 XLSX | 직종·소속·국내외·옵션·환불별 다차원 통계 및 내보내기 | E08, E15 |
| PAY-07 | 부분 구현 | 결제금액 합계·현황 | 옵션 매출·미수금·취소환불 상세 원장과 대사 가능한 정산자료 | E08, E15 |
| FREE-01 | 미구현 | 관리자 0원 등록 가능 기반 | 무료대상자 본인의 일반 등록 화면에서 참가일·식사·옵션 선택 및 접수 | E08, E10 |
| FREE-02 | 부분 구현 | 면허번호·성명을 통한 학회회원 요금 확인 | 무료대상 인증 수단 선택, 초대코드·이메일·휴대전화 인증과 자격 관리 | E10 |
| FREE-03 | 미구현 | 0원 기본요금 표현 가능 | 무료 한도·옵션별 면제와 초과액 계산, 초과분 본인 결제 | E08, E18 |
| FREE-04 | 부분 구현 | 학회회원 명단 엑셀 업로드·공통 수신자 선택 | 무료초대 대상 명단·초대메일 및 SMS·대상자별 등록 여부 추적 | E10, E11, E12 |

## 8 메일 문자와 주소록

| 요구 ID | 판정 | 현재 확인한 구현 | 추가 구현 또는 확인할 내용 | 근거 |
|---|---|---|---|---|
| MAIL-01 | 부분 구현 | 개인·주소록·전체그룹 대상 구성, 작업·예약시각 저장 | 실제 즉시·예약 전송 작업자와 공급자 연동. QUEUED·SCHEDULED 저장을 전송 완료로 판단하면 안 됨 | E11 |
| MAIL-02 | 부분 구현 | HTML 편집·이미지·첨부, 발신자·회신처 | 국영문 재사용 템플릿 저장·복사, 성명·접수번호·등록번호·발표정보의 실제 변수 치환 | E11 |
| MAIL-03 | 부분 구현 | 직접 입력·주소록·회원·등록자·제출자·채택자, 중복·수신거부 제외 | 심사·연자 데이터의 전용 대상 조회·조건 선택 확대. 관리자 검색으로 일부 심사자를 선택할 수 있으나 심사·연자 목록 연계와는 구분 | E11, E13 |
| MAIL-04 | 부분 구현 | 대상자 수 미리보기 | 테스트메일 실제 전송, 준비 이후 예약 수정·취소. 현재 작성 상태만 변경 가능 | E11 |
| MAIL-05 | 부분 구현 | 발송결과 저장 구조, 수신거부·공급자 이벤트 처리 기반 | 실제 공급자 이벤트 어댑터와 전송 결과 연결, 성공·실패·반송 이력 운영, 재발송·결과 엑셀 | E11 |
| MAIL-06 | 부분 구현 | 주소록 연락처를 개별 검색·추가 가능 | 선택한 1명에게 실제 발송. 대상 선택 UI를 새로 만들 필요는 없으며 공통 전송 엔진 연결이 핵심 | E11, E13 |
| SMS-01 | 부분 구현 | SMS·LMS·AUTO 작성, 그룹·주소록·개인, 번호 정규화·중복 제외 | 공급자 실제 전송, MMS 이미지·첨부 및 유형 지원. 현재 DTO에서 MMS를 허용하지 않음 | E12 |
| SMS-02 | 부분 구현 | 희망 발송시각 저장 | 예약 스케줄러와 실제 발송 실행. PREPARED 상태는 미발송 | E12 |
| ADR-01 | 부분 구현 | 그룹·연락처 CRUD, XLSX 업로드, 동일 이메일 upsert·중복 감지 | 주소록 엑셀 다운로드, 중복 연락처의 명시적 병합과 필드 충돌 처리 | E13 |
| ADR-02 | 부분 구현 | 발송 준비 때 회원·등록자·제출자·채택자 최신 조회 | 심사위원·연자·좌장·위원회·업체까지 동기화 범위 확장. 주소록으로의 동기화와 발송 시 조회 범위도 정의 | E13 |

## 9 보안 통계와 운영

| 요구 ID | 판정 | 현재 확인한 구현 | 추가 구현 또는 확인할 내용 | 근거 |
|---|---|---|---|---|
| SEC-01 | 부분 구현 | admin·reviewer 역할 구분, 세션·IP 접근 통제 | 관리자별 메뉴·조회·수정·삭제·다운로드·발송 권한을 각각 저장·검사 | E14 |
| SEC-02 | 부분 구현 | 비밀번호 길이·암호화·변경, 수동 비활성화, 접속기록 | 장기 미사용 자동 잠금 및 합의된 비밀번호 정책 확대 | E14 |
| SEC-03 | 부분 구현 | 공통 확인 다이얼로그, 엑셀 사유·로그, 일부 중요 작업 확인 | 환불·삭제·대량발송 전체의 추가 확인과 행위별 감사 이력 일관화 | E08, E11, E14 |
| SEC-04 | 부분 구현 | 비밀번호 해시·접속기록·역할 통제 | 개인정보 보유기간·파기 기능, 수집·암호화 대상과 권한 정책 확정. DB·디스크 암호화 등 인프라 설정은 별도 확인 | E14, E18 |
| STA-01 | 미구현 | 접수·등록 업무 대시보드 | 방문자·페이지·유입·기기·오류 이벤트 수집, 접수·등록 전환 퍼널과 기간 비교. 관리자 접속기록은 방문 분석을 대체하지 않음 | E02, E15 |
| OPS-01 | 확인 필요 | 현재 소스의 기능은 검토 | 기존 업체 시스템 실사·전체 산출물 목록과 동등성 검수 | 외부 시스템 필요 |
| OPS-02 | 확인 필요 | 개별 엑셀 업로드는 있음 | 과거 회차·첨부·발송·결제·등록·초록 일괄 이관 도구, 원본 대사·조회 검증 | E18, 원본 자료 필요 |
| OPS-03 | 확인 필요 | 관리자 반응형 클래스, 공개 사이트 모바일 메뉴·CSS | 모든 화면과 표·팝업·결제의 기기별 실제 검수. 화면이 없는 사용자 기능은 구현 후 검수 | E02, E05 |
| OPS-04 | 확인 필요 | 공개 HTML의 접근성 속성 일부 | 접근성·성능 기준 합의와 측정·수정. 현재 소스만으로 합격 판정 불가 | E02 |
| OPS-05 | 확인 필요 | 앱 저장소에서 운영 백업 체계 확인 못함 | 외부 백업 설정 확인, DB·파일·서버 백업 및 복구 테스트 증빙 | 인프라 확인 필요 |
| OPS-06 | 확인 필요 | 업무 대시보드 | 장애·용량·트래픽·전송실패 알림, 집중기간 모니터링·대응 체계. 외부 관제 여부 확인 | E15, 인프라 확인 필요 |
| OPS-07 | 확인 필요 | 일부 보안 제어 | 보안점검 수행 증빙, 개인정보 파기 절차·실행 기록. 기능 부분은 SEC-04와 공통 | E14, 운영 증빙 필요 |
| OPS-08 | 확인 필요 | 프로젝트별 설계·연동 가이드 일부 | 발주처 요구에 맞춘 최종 운영·복구·인수인계 문서와 전달 여부 | document 폴더 |
| DEL-01 | 확인 필요 | 학회 설정 기능 | 대한안과학회 회차별 정책·메뉴·공동학회 확정 산출물 | 계약·검수 자료 필요 |
| DEL-02 | 확인 필요 | 현재 공개 홈페이지 디자인 | 2~3개 실질적 대안과 시안별 의도·장단점 제출·승인 | 디자인 산출물 필요 |
| DEL-03 | 확인 필요 | 관리자 UI 가이드·공개 CSS | 대한안과학회 키비주얼·디자인 시스템·메뉴별 최종 콘텐츠 | 디자인·콘텐츠 자료 필요 |
| DEL-04 | 부분 구현 | 공개 콘텐츠는 비로그인 렌더링, 관리자·심사는 인증 필요 | 모든 검수 대상 페이지를 볼 수 있는 분리된 테스트버전과 검수 데이터 구성 | E02, E14 |
| DEL-05 | 부분 구현 | 행사기간 선후 검증 | 날짜·요일·시간·행사명·링크·전 회차 잔존값 자동점검 및 수동 체크리스트 | E01 |
| DEL-06 | 확인 필요 | 코드로 이행 여부 판정 불가 | 추가 요구 접수·협의·대안·반영 기록 관리 | 운영·계약 자료 필요 |

## 10 이번 추가 구현 목록에서 제외한 항목

| 요구 ID | 판정 | 확인 근거와 한계 |
|---|---|---|
| WEB-07 | 코드 확인 | 게시판·팝업 관리자 CRUD, 본문·이미지 편집 구현 확인(E16). 공개 메인 연결의 부족은 WEB-01에서 관리한다. |
| ABS-03 | 코드 확인 | 초록 수정 서비스가 메일 발송을 호출하지 않아 변경과 발송은 분리되어 있다(E04). 실제 메일 전송 자체는 별도 미완성이다. |
| REV-02 | 코드 확인 | 심사위원 세션, 본인 배정 조회, 평가항목·점수·의견, 임시저장·제출 경로 확인(E06). 실제 운영 검수는 별도다. |

그 밖의 ‘부분 구현’ 항목 안에 있는 기존 기능도 재사용 대상이다. 예를 들어 프로그램 편성 전체, 저자·소속 전체, 주소록 전체를 신규 개발로 산정해서는 안 된다.

## 11 개발 작업 묶음과 완료 기준 제안

이 표는 요구사항을 실행 가능한 작업 묶음으로 정리한 제안이다. 원문에 없는 별도 계약 요구를 추가한 것은 아니다.

| 작업 묶음 | 주요 요구 | 완료를 확인할 대표 시나리오 |
|---|---|---|
| 회차·인증 기반 | COM-01~02, WEB-08~09 | 새 회차 생성 후 초록·등록·콘텐츠가 구분되고, 종료 자료는 허용 회원만 검색·다운로드 |
| 사용자 접수·등록 | WEB-04~05, SUB-01~04, REG-10 | 본인 로그인 → 초록 또는 등록 제출 → 본인 내역 조회·수정 → 완료 안내 |
| 초록 품질·변경 | SUB-05~11, ABS-02·04·07, REV-07 | 복수 소속·미리보기·중복 제목 경고, 관리자 수정 사유와 이력, 승인 후 프로그램 반영 |
| 심사·편성·출판 확장 | REV-01·03~06, PRG-01~02, ABS-06·08 | 배정 → 심사 집계 규칙 → 일괄결정 → 번호 부여 → 프로그램북 출력 |
| 발표파일 | FIL-01~05 | 필수 동의 → 업로드 → 재제출 → 최종본 확인 → 미제출자 현황·엑셀 |
| 옵션·무료·현장등록 | REG-02·09, FREE-01~04 | 초대대상 인증 → 옵션 선택 → 무료 초과분 산정, 현장 최종 내역 통합 |
| 실결제·환불·증빙 | PAY-01~07, REG-03·08 | 결제 확정 → 옵션 변경 차액 결제·부분환불 → 원결제 이력 → 최종 영수증·정산 대사 |
| 공통 발송 | MAIL-01~06, SMS-01~02, SUB-04, ABS-05, REV-03·06, REG-06 | 테스트 → 즉시·예약 전송 → 실패·반송 반영 → 재발송, 1명 선택 발송과 업무별 이력 |
| 주소록·목록 공통 | ADR-01~02, COM-03 | 내부 대상 동기화·중복 병합·엑셀, 검색조건·정렬·선택작업 유지 |
| 보안·감사·운영 | COM-05, SEC-01~04, STA-01, OPS | 권한별 작업 허용·거부, 변경 전후 감사, 보유기간 파기, 통계 수집, 백업 복구 검수 |

## 12 소스 근거

근거 번호는 표에서 반복되는 소스를 묶기 위한 것이다. ‘미구현’ 판단은 해당 파일 하나에 기능이 없다는 이유만으로 내리지 않고 관련 화면·컨트롤러·서비스·데이터 구조를 함께 검색했다.

- **E01 회차 설정**: [CongressSettingsService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/CongressSettingsService.java:21)의 최신 설정 조회, 생성·수정 및 날짜 검증. 설정 목록과 업무 데이터의 회차 분리는 별개다.
- **E02 공개 홈페이지**: [PublicPageController.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/PublicPageController.java:42)의 홈·메뉴 HTML 렌더링과 robots·sitemap, [home.html](D:/dev/java/intellij/bjworld21_cms_congress/src/main/resources/templates/public/home.html:1), [conference.js](D:/dev/java/intellij/bjworld21_cms_congress/src/main/resources/static/public/js/conference.js:1). 공개 페이지는 영문 템플릿이며 스크립트는 테마·모바일 메뉴 중심이다.
- **E03 초록 API**: [AbstractController.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/AbstractController.java:45). 관리자 경로의 검색·CRUD·XLSX·개별 첨부·심사·결정. 사용자 제출 API와 발송·Word·일괄 첨부 API는 확인되지 않는다.
- **E04 초록 저장·검증**: [AbstractSubmissionService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AbstractSubmissionService.java:242), [AbstractSubmissionRequest.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/dto/AbstractSubmissionRequest.java:1). 회원용 메서드는 있으나 컨트롤러 연결이 없다. `update`는 회원 제출분 관리자 수정 금지, `updateByMember`는 심사 시작 후 수정 금지다. [관련 테스트](D:/dev/java/intellij/bjworld21_cms_congress/src/test/java/com/bjworld21/congress/service/AbstractSubmissionServiceTest.java:125)도 이 정책을 명시한다.
- **E05 저자·소속 UI**: [AbstractRegisterModal.tsx](D:/dev/java/intellij/bjworld21_cms_congress/frontend/src/components/AbstractRegisterModal.tsx:702)의 단일 소속 select, [AbstractSubmissionAuthor.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/dto/AbstractSubmissionAuthor.java:1), [AbstractDetailModal.tsx](D:/dev/java/intellij/bjworld21_cms_congress/frontend/src/components/AbstractDetailModal.tsx:68).
- **E06 심사·채택**: [ReviewerReviewService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/ReviewerReviewService.java:50), [AbstractReviewAssignmentService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AbstractReviewAssignmentService.java:57), [AdminAbstractReviewResultService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AdminAbstractReviewResultService.java:30), [AbstractDecisionService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AbstractDecisionService.java:17). 결정은 approved·rejected 두 종류이며 개별 처리다.
- **E07 프로그램**: [ProgramService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/ProgramService.java:52), [AbstractProgramService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AbstractProgramService.java:63), [ProgramItem.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/entity/ProgramItem.java:1). 초록 배정 시 제목·발표자 복사와 원본 복구용 스냅샷을 저장한다.
- **E08 사전등록**: [PreRegistrationAdminService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/PreRegistrationAdminService.java:117), [PreRegistrationRepository.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/repository/PreRegistrationRepository.java:210), [PreRegistrationManagementPage.tsx](D:/dev/java/intellij/bjworld21_cms_congress/frontend/src/components/PreRegistrationManagementPage.tsx). 결제 취소 서비스는 repository만 호출하며 PG를 호출하지 않는다. 엑셀 필드는 고정이다.
- **E09 결제 연동 범위**: [PaymentGatewayService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/payment/PaymentGatewayService.java:33), [PaymentTestController.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/PaymentTestController.java:23). 현재 컨트롤러 사용처는 관리자 결제 테스트이며, 등록 원장과 결제 결과 확정 흐름은 확인되지 않는다.
- **E10 학회회원 요금**: [SocietyMemberService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/SocietyMemberService.java), [SocietyMemberBulkImportService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/SocietyMemberBulkImportService.java), [RegistrationFeeService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/RegistrationFeeService.java). 로컬 명단 및 요금 확인 기반이다.
- **E11 메일**: [MailCampaignService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/MailCampaignService.java:195), [PromotionalMailPage.tsx](D:/dev/java/intellij/bjworld21_cms_congress/frontend/src/components/PromotionalMailPage.tsx:458). UI도 실제 미발송을 명시한다. [MailProviderEventService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/MailProviderEventService.java), [MailWebhookRegistry.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/mail/MailWebhookRegistry.java)는 결과 수신 확장 기반이다.
- **E12 문자**: [SmsCampaignService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/SmsCampaignService.java), [SmsData.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/dto/SmsData.java:27), [SMS 연동 가이드](D:/dev/java/intellij/bjworld21_cms_congress/document/sms_integration_guide.md:27). 예약 스케줄러·자동 발송·실제 발송 API가 없음을 가이드와 코드에서 확인했다.
- **E13 수신자·주소록**: [MailRecipientSelectionService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/MailRecipientSelectionService.java), [RecipientGroups.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/RecipientGroups.java), [MailAddressBookService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/MailAddressBookService.java), [MailAddressBookController.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/MailAddressBookController.java). 개인 검색에 주소록 연락처가 포함되고 import는 있으나 export API는 없다.
- **E14 보안·감사**: [CongressAdminWebMvcConfig.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/config/CongressAdminWebMvcConfig.java), [AdminService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AdminService.java), [AdminAccessLogService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AdminAccessLogService.java), [ExcelDownloadAuditService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/ExcelDownloadAuditService.java). 개별 행위별 권한·전후 변경 감사와 개인정보 수명주기 기능은 별도 필요하다.
- **E15 업무 통계**: [OperationalDashboardService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/OperationalDashboardService.java:30), [AdminDashboardService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/AdminDashboardService.java:24). 접수·등록 업무 통계이며 웹 방문 분석과는 범위가 다르다.
- **E16 공지·팝업**: [BoardPostService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/BoardPostService.java), [PopupService.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/service/PopupService.java), 해당 관리자 React 화면과 컨트롤러.
- **E17 전시·후원**: [SponsorshipApplicationController.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/SponsorshipApplicationController.java), [SponsorController.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/controller/SponsorController.java). 관리 경로의 신청·수정·조회와 로고 제공을 확인했다.
- **E18 데이터 구조**: 초록, 저자·소속, 등록·프로그램·첨부 관련 엔티티와 리포지토리. 발표파일 버전·등록 옵션·연결 결제 원장·범용 변경 감사의 업무 구현은 확인되지 않는다.
- **E19 엑셀 문자열 처리**: [XlsxWorkbookWriter.java](D:/dev/java/intellij/bjworld21_cms_congress/src/main/java/com/bjworld21/congress/util/XlsxWorkbookWriter.java:188). XML 기호 치환과 셀 생성은 있으나 금지 제어문자 정규화는 추가 검증·보완 대상이다.

## 13 후속 사용 시 주의사항

1. 운영 환경이나 소스가 바뀌면 판정을 갱신한다. 이 문서는 검토 시점의 스냅샷이다.
2. ‘확인 필요’는 ‘미구현’으로 합산하지 않는다. 특히 콘텐츠·기존 데이터 이관·백업·접근성·디자인 산출물은 별도 증빙으로 판정한다.
3. 현재 구현과 원문의 차이를 정리한 것이며, 개발 변경·서비스 설정 변경·실제 발송·환불은 수행하지 않았다.
4. 구현 완료 판정 시에는 관련 단위 테스트뿐 아니라 사용자의 전체 업무 흐름과 출력물을 검수한다. 기존 테스트가 통과하더라도 원문과 다른 정책을 검증하고 있을 수 있다.
