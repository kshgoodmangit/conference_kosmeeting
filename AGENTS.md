# AGENTS.md: bjworld21_cms_congress AI 에이전트 가이드

## 프로젝트 개요

**ICMS 2026** (국제학술대회관리시스템)는 학술대회 초록 및 논문 제출을 관리하는 **Spring Boot + React 풀스택 애플리케이션**입니다.

- **백엔드**: Spring Boot 3.5.14, Java 17, Spring Security, MyBatis, MariaDB 10.6
- **프론트엔드**: React 19, TypeScript, Vite, Tailwind CSS 다크모드 지원
- **빌드**: Gradle이 npm 파이프라인을 통합 관리

---

## 핵심 아키텍처: 통합 빌드 파이프라인

### 중요한 이유
프로젝트는 **Gradle로 전체 파이프라인을 관리**—프론트엔드와 백엔드를 하나의 단위로 빌드합니다. 이는 CI/CD 및 배포를 이해하는 데 필수적입니다.

**빌드 흐름** (`build.gradle` lines 42-71):
```
npmInstallTask → npmBuildTask → copyFrontendAssets → processResources
```

1. `npmInstallTask`: 프론트엔드 의존성 설치 (Node.js v20.11.0 자동 다운로드)
2. `npmBuildTask`: `frontend/` 디렉토리에서 `npm run build` 실행, `frontend/dist`로 출력
3. `copyFrontendAssets`: `frontend/dist` → `src/main/resources/static/` 복사 (Spring Boot가 제공)
4. `processResources`: JAR 패킹 전 최종 리소스 처리

**주요 파일**:
- `build.gradle` - 마스터 빌드 오케스트레이션
- `frontend/vite.config.ts` - 프론트엔드 빌드 출력 경로: `../src/main/resources/static`

### 개발 워크플로우
**개발 중에는 두 프로세스를 별도로 실행해야 합니다**:
```powershell
# 백엔드: Java 컴파일 + Spring Boot
.\gradlew17.bat bootRun
# 프론트엔드: 핫 리로드 dev 서버 (별도 터미널)
cd frontend
npm run dev
```

이유? 프론트엔드 dev 서버가 API 호출을 `http://localhost:8080`으로 프록시합니다 (`frontend/vite.config.ts` lines 15-19 참조).

---

## 프론트엔드 아키텍처: Tailwind + React Hooks

### 화면 추가·수정 시 필수 참조

- 관리자 페이지·모달·폼을 추가하거나 UI를 수정하기 전에 **`document/admin_ui_design_guide.md`를 반드시 읽고 적용합니다.**
- 이 문서를 관리자 UI의 기본 디자인 기준으로 사용하고, 문서에 지정된 유사 화면을 함께 확인합니다.
- 등록·저장·취소 버튼의 역할별 색상과 크기, 페이지 헤더, 모달 폭·제목·닫기 버튼, 입력란·파일 선택 스타일을 임의로 다르게 만들지 않습니다.
- 공통 확인 다이얼로그·토스트·엑셀 다운로드 구현을 재사용하고, 작업 완료 전 가이드의 체크리스트를 확인합니다.
- 일반 CRUD 관리 목록에는 검색 조건 영역과 요약 통계를 기본 포함합니다. 검색·초기화 동작을 제공하고 별도의 새로고침 버튼은 추가하지 않습니다. 통계는 서버에서 현재 검색 조건에 해당하는 전체 결과를 집계하여 목록과 함께 갱신합니다.
- 기존 화면의 일부 구현이 가이드와 다르면 새로 작성·수정하는 부분은 가이드를 따릅니다. 사용자가 명시한 UI 요구사항은 우선 적용합니다.

### 컴포넌트 패턴 (예: AbstractRegisterModal)
위치: `frontend/src/components/AbstractRegisterModal.tsx`

**주요 특성**:
- **제어 컴포넌트**: 모든 폼 입력이 React state (`useState`) 사용
- **드래그 기능**: GPU 가속 모달 드래그 with `transform` CSS (lines 42-66)
- **폼 제출**: `/api/abstracts`로 multipart 업로드를 위해 `FormData`로 변환
- **파일 검증**: 업로드 전 PDF/DOCX 확장자 검증
- **다크모드**: Tailwind의 `dark:` 접두사로 모든 스타일링 (CSS 변수 없음)

**새로운 컴포넌트의 예상 패턴**:
```tsx
// Props interface 먼저 정의
interface ComponentProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

// Hooks 상단, useEffect에 side effects
export const YourComponent: React.FC<ComponentProps> = ({ isOpen, onClose, onSuccess }) => {
  const [state, setState] = useState('');
  
  useEffect(() => {
    if (isOpen) {
      // 초기화 및 정리
      return () => { /* cleanup */ };
    }
  }, [isOpen]);
  
  // 폼 제출 패턴
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const formData = new FormData();
    formData.append('key', value.trim());
    const response = await fetch('/api/endpoint', { method: 'POST', body: formData });
    if (response.ok) onSuccess();
  };
};
```

### 스타일링 규칙
- **Tailwind 클래스만 사용** (CSS 모듈 없음)
- **다크모드**: 모든 색상/배경에 `dark:` 접두사 버전 필수
- **아이콘**: Lucide React (`lucide-react` 패키지) 사용
- **반응형**: 모바일 우선, `md:` 및 `sm:` 접두사 사용

### 확인 다이얼로그 규칙
- 브라우저 기본 `window.confirm()`은 사용하지 않습니다.
- 확인이 필요한 모든 기능은 `frontend/src/components/ConfirmDialog.tsx`와 `frontend/src/components/confirmDialogContext.ts`의 공통 구현을 사용합니다.
- 호출 컴포넌트에서는 `useConfirm()`을 가져오고, 이벤트 핸들러를 `async`로 선언한 뒤 결과를 `await`합니다.
- 삭제·제외 등 되돌리기 어려운 작업은 `tone: 'danger'`와 구체적인 `confirmText`를 지정합니다.
- 단순 확인은 기본 tone을 사용하며, 화면별 확인 모달을 별도로 중복 구현하지 않습니다.

```tsx
import { useConfirm } from './confirmDialogContext';

const confirm = useConfirm();

const handleDelete = async () => {
  const confirmed = await confirm({
    title: '항목 삭제',
    message: '선택한 항목을 삭제하시겠습니까?',
    confirmText: '삭제',
    tone: 'danger'
  });
  if (!confirmed) return;

  // 삭제 API 호출
};
```

### 토스트 알림 규칙

- 성공·오류·안내 메시지는 `frontend/src/components/NotificationToast.tsx`의 공통 토스트를 사용합니다.
- 페이지와 모달은 상위 컴포넌트에서 전달받은 `onNotify(type, message)`를 호출합니다. 비동기 콜백이나 `useEffect`에서 사용할 때는 필요에 따라 `useRef`로 최신 콜백을 유지합니다.
- 알림 유형은 `success`, `error`, `info` 중에서 선택합니다.
- 화면이나 모달마다 별도의 토스트 컴포넌트를 만들지 않습니다.
- 모달 내부 유효성 검사 실패처럼 스크롤 위치와 관계없이 사용자가 즉시 확인해야 하는 메시지도 공통 토스트의 `error` 유형으로 표시합니다.
- 동일한 오류를 본문 오류 박스와 공통 토스트에 중복 표시하지 않습니다. 필드별 안내가 필요한 경우에만 해당 입력란 주변의 인라인 오류를 함께 사용합니다.

```tsx
interface Props {
  onNotify: (type: NotificationType, message: string) => void;
}

onNotify('success', '저장했습니다.');
onNotify('error', '필수 입력사항을 확인해 주세요.');
```

---

## 백엔드: Spring Boot + MyBatis 통합

### 현재 상태
- **Java 클래스 1개만 존재**: `Bjworld21CmsCongressApplication.java` (Spring Boot 메인 진입점)
- **컨트롤러/서비스/저장소는 아직 없음** (추가해야 함)
- 설정: `src/main/resources/application.yaml` (최소한—Spring 기본값 사용)

### 엔드포인트 생성 시 예상 패턴

**예상되는 `/api/abstracts` POST 엔드포인트** (프론트엔드의 `AbstractRegisterModal` 필요):
```java
@PostMapping("/abstracts")
public ResponseEntity<?> createAbstract(
    @RequestParam String title,
    @RequestParam String author,
    @RequestParam String affiliation,
    @RequestParam(required = false) MultipartFile file
) {
    // FormData 처리
    // 유효성 검사: title, author, affiliation 필수
    // 파일이 제공되면 저장 (제공된 경우)
    // MyBatis를 통해 MariaDB에 메타데이터 저장
}
```

### 설정 참고사항
- **DB 서버**: 실제 사용하는 데이터베이스는 **MariaDB 10.6**입니다.
- **JDBC 드라이버**: 현재 `runtimeOnly 'com.mysql:mysql-connector-j'`를 사용합니다 (`application.yaml`에서 연결 파라미터). 드라이버 이름이나 JDBC URL만으로 DB 서버를 MySQL로 판단하지 않습니다.
- **보안**: Spring Security 포함; 필요에 따라 인증 구성
- **유효성 검사**: Spring Validation starter 포함 (`spring-boot-starter-validation`)
- **Lombok**: `@Getter`, `@Setter`, `@Data` 주석에 사용

---

## 파일 업로드 저장 규칙

### 공통 기준 경로

- 모든 영구 업로드 파일은 `application.yaml`의 `app.upload.base-directory`를 기준으로 저장합니다.
- 현재 운영 기준 경로는 `Z:/dev/upload/bjworld21/_cms/_congress`입니다.
- 설정 바인딩은 `config/UploadProperties.java`, 경로 생성과 검증은 `service/UploadStorage.java`가 담당합니다.
- 서비스에서 `Paths.get("uploads", ...)` 또는 메뉴별 절대 경로를 직접 만들지 않습니다.

```yaml
app:
  upload:
    base-directory: Z:/dev/upload/bjworld21/_cms/_congress
```

### 메뉴 및 월별 폴더 구조

업로드 파일은 반드시 `기준 경로/메뉴/yyyyMM/파일명` 구조로 저장합니다.

```text
Z:/dev/upload/bjworld21/_cms/_congress/
├── abstracts/yyyyMM/
├── boards/yyyyMM/
├── mail/yyyyMM/
├── popups/yyyyMM/
├── sponsors/yyyyMM/
└── sponsorships/yyyyMM/
```

메뉴 이름은 `UploadStorage`의 다음 상수를 사용합니다.

- `UploadStorage.ABSTRACTS`
- `UploadStorage.BOARDS`
- `UploadStorage.MAIL`
- `UploadStorage.POPUPS`
- `UploadStorage.SPONSORS`
- `UploadStorage.SPONSORSHIPS`

새 메뉴를 추가할 때는 `UploadStorage`에 메뉴 상수를 추가하고 `SUPPORTED_MENUS`에도 등록합니다.

### 저장 및 조회 구현 규칙

- 실제 저장 파일명은 UUID 기반으로 생성합니다.
- DB의 저장 파일명 컬럼에는 절대 경로가 아닌 `yyyyMM/UUID.ext` 형식의 메뉴 폴더 기준 상대 경로만 저장합니다.
- 월 폴더는 파일 저장 직전에 `Files.createDirectories(target.getParent())`로 생성합니다.
- 파일 조회와 삭제는 반드시 `UploadStorage.resolve(menu, savedPath)`를 사용합니다.
- `UploadStorage.resolve`의 기준 경로 이탈 방지 검사를 우회하거나 별도의 문자열 경로 결합을 구현하지 않습니다.
- 본문 이미지 URL은 `/api/boards/images/{yyyyMM}/{filename}` 또는 `/api/mail/images/{yyyyMM}/{filename}` 형식을 유지합니다.

```java
UploadStorage.StoredTarget storedTarget = uploadStorage.monthlyTarget(
        UploadStorage.BOARDS,
        UUID.randomUUID() + "." + extension
);

Files.createDirectories(storedTarget.path().getParent());
file.transferTo(storedTarget.path());

// DB에는 전체 경로가 아닌 상대 경로만 저장
attachment.setSavedFilename(storedTarget.relativePath());

// 조회/삭제 시에도 공통 경로 검증 사용
Path target = uploadStorage.resolve(
        UploadStorage.BOARDS,
        attachment.getSavedFilename()
);
```

### 업로드 기능 변경 시 확인사항

1. 메뉴별 `yyyyMM` 폴더에 실제 파일이 생성되는지 확인합니다.
2. DB에는 `yyyyMM/파일명` 상대 경로만 저장되는지 확인합니다.
3. 다운로드·본문 이미지 URL·삭제 기능이 월 폴더를 포함한 상대 경로를 정상 처리하는지 확인합니다.
4. `../` 및 절대 경로가 `UploadStorage`에서 거부되는지 확인합니다.
5. 테스트에서는 실제 Z 드라이브 대신 `@TempDir`와 `UploadProperties.setBaseDirectory(...)`를 사용합니다.
6. Windows 서비스로 실행하는 경우 실행 계정에서 Z 드라이브가 보이고 쓰기 권한이 있는지 확인합니다. 매핑 드라이브를 사용할 수 없으면 UNC 경로를 설정합니다.

---

## API 계약: 프론트엔드 ↔ 백엔드

### 구축된 엔드포인트
**`POST /api/abstracts`** (`AbstractRegisterModal.tsx` line 91)
- **요청**: `title`, `author`, `affiliation`, `file` 필드가 있는 FormData
- **예상 응답**: 성공 시 HTTP 200, 그 외 특정 에러 메시지
- **프론트엔드 동작**: 실패 시 alert 표시, `onSuccess()` 호출하여 데이터 새로고침

### 개발 팁
새 API 엔드포인트 생성 시 확인사항:
1. 경로는 `/api/`로 시작 (Vite 프록시 구성됨)
2. 의미 있는 에러 메시지 반환 (프론트엔드가 `response.text()` 표시)
3. 프론트엔드의 fetch 호출로 직접 테스트

---

## TypeScript 설정

### 주요 설정
- **대상**: ES2023 (현대 JavaScript)
- **JSX**: React 19 (JSX 전처리 활성화)
- **엄격 검사**: `noUnusedLocals: true`, `noUnusedParameters: true` (강제)
- **모듈 시스템**: ESNext (Vite가 트랜스파일)

### import 규칙
```tsx
// 'frontend/src' 루트에서의 절대 import는 구성되지 않음
// 상대 import 사용:
import { AbstractRegisterModal } from './components/AbstractRegisterModal';
import App from './App.tsx'; // TS 파일에는 .tsx 확장자 필수
```

---

## 빌드 명령어 참조

### 로컬 JDK
- Office PC: `D:\jdk\jdk17`
- Home PC: `D:\dev\jdk\jdk17`
- 이 프로젝트의 Gradle 및 Java 작업은 **반드시 Java 17로 시작**합니다. 시스템 기본 `java`가 Java 8이어도 먼저 실행해 보지 않습니다.
- PowerShell 세션의 `JAVA_HOME`, `Path` 또는 시스템 환경변수를 변경하지 않습니다.
- 모든 Gradle 명령은 프로젝트 전용 실행기인 `.\gradlew17.bat`을 사용합니다. bare `gradle`이나 `.\gradlew.bat`을 먼저 시도하지 않습니다.
- `gradlew17.bat`은 `setlocal` 범위 안에서만 `JAVA_HOME`을 설정하므로 이 프로젝트의 Gradle 자식 프로세스에만 Java 17이 적용됩니다.
- 실행기는 Office PC의 `D:\jdk\jdk17`을 우선 사용하고, 없을 때 Home PC의 `D:\dev\jdk\jdk17`을 사용합니다.
- Gradle JVM을 확인할 때도 `.\gradlew17.bat --version`을 사용합니다.

```powershell
# PowerShell 및 시스템 환경변수를 변경하지 않는 프로젝트 전용 실행
.\gradlew17.bat --version
```

### 개발
```powershell
# 백엔드만
.\gradlew17.bat bootRun

# 프론트엔드만 (별도 터미널)
cd frontend
npm run dev

# 전체 백엔드 빌드 + 테스트
.\gradlew17.bat build
```

### 지속적 통합
```powershell
# 전체 빌드 (gradle이 npm을 내부적으로 관리)
.\gradlew17.bat build  # target/bjworld21_cms_congress-0.0.1-SNAPSHOT.jar 생성

# 테스트 실행
.\gradlew17.bat test
```

### 배포
- Gradle은 프로덕션 JAR을 `build/libs/`로 출력
- 프론트엔드는 JAR에 `META-INF/resources/static/`으로 포함됨
- Spring Boot는 기본 정적 리소스 핸들러로 프론트엔드 제공

---

## 프로젝트 구조 빠른 네비게이션

```
bjworld21_cms_congress/
├── build.gradle                    # 마스터 빌드 + npm 오케스트레이션
├── src/main/java/                  # 백엔드 소스 (메인 진입점만 존재)
├── src/main/resources/
│   ├── application.yaml            # Spring 설정 (최소한)
│   └── static/                     # 프론트엔드 빌드 출력 (자동 생성)
└── frontend/
    ├── src/
    │   ├── components/             # React 컴포넌트 (여기에 추가)
    │   ├── App.tsx                 # 메인 앱 레이아웃
    │   ├── index.css               # 글로벌 Tailwind + 커스텀 스타일
    │   └── main.tsx                # React 진입점
    ├── vite.config.ts              # Vite + API 프록시 설정
    ├── eslint.config.js            # TypeScript + React 린팅 규칙
    └── tsconfig.app.json           # TypeScript 엄격 설정
```

---

## AI 에이전트를 위한 일반 작업

### 새로운 React 컴포넌트 추가
1. `document/admin_ui_design_guide.md`와 유사 화면을 확인한 뒤 `frontend/src/components/YourComponent.tsx` 파일 생성
2. Props interface + export React.FC 정의
3. Tailwind + `dark:` 접두사로 스타일링
4. 필요하면 Lucide 아이콘 import
5. 새 관리자 메뉴가 필요한 페이지라면 `App.tsx`의 지원 메뉴·화면 렌더링과 `menuIcons.ts`를 함께 연결

### 백엔드 컨트롤러 생성
1. `src/main/java/com/bjworld21/congress/controller/YourController.java` 파일 생성
2. `@RestController` 및 `@RequestMapping("/api/your-path")` 주석 추가
3. 필요하면 MyBatis mapper 주입 (`@Autowired` 사용)
4. 프론트엔드 `response.text()` 처리를 위해 의미 있는 에러 메시지 반환

### 프론트엔드-백엔드 통신 디버깅
1. `frontend/vite.config.ts` 프록시 확인: `/api` → `http://localhost:8080`
2. 백엔드가 포트 8080에서 실행 중인지 확인 (`application.yaml` 기본값)
3. 브라우저 DevTools Network 탭으로 `/api/*` 요청 검사
4. 프론트엔드 fetch 에러가 브라우저 콘솔에 표시됨

---

## 데이터베이스 테이블 생성 규칙

### DB 종류 및 SQL 호환성

- 스키마·마이그레이션 DDL과 조회 SQL은 **MariaDB 10.6 문법**을 기준으로 작성합니다. MySQL 8 전용 문법을 그대로 적용하지 않습니다.
- CHECK 제약을 제거할 때는 `ALTER TABLE 테이블명 DROP CONSTRAINT 제약명`을 사용합니다. MySQL용 `DROP CHECK`를 사용하지 않습니다.
- DB 버전에 따라 지원 여부가 달라지는 기능은 MariaDB 10.6 공식 문서에서 확인합니다. 실제 서버 버전 확인이 필요하면 아래 전용 조회 스크립트로 `SELECT VERSION()`을 실행합니다.
- DB 접속 비밀값을 이 문서나 소스에 기록하지 않습니다. 접속 정보와 읽기 전용 조회 규칙은 아래 '로컬 DB 조회 도구' 항목을 따릅니다.

### 스키마 변경 관리

- 애플리케이션 시작 시 `CREATE TABLE`, `ALTER TABLE`, `CREATE INDEX` 등을 실행하는 `ApplicationRunner`, `CommandLineRunner`, 스키마 Initializer를 새로 만들지 않습니다.
- 테이블·컬럼·인덱스 변경이 필요하면 실행 가능한 MariaDB 10.6용 `.sql` 파일을 `document/`에 생성합니다.
- SQL 파일에는 적용 목적, 대상 DB 버전, 적용 순서와 1회 실행 여부를 주석으로 기록합니다.
- 애플리케이션 코드는 필요한 SQL이 먼저 적용된다는 전제로 작성하고, 서버 시작 과정에서 운영 DB 스키마를 자동 변경하지 않습니다.

### 테이블 및 컬럼 네이밍 규칙
- **테이블명**: snake_case (예: `members`, `abstract_submissions`)
- **컬럼명**: camelCase (예: `memberType`, `emailAddress`)
- **PK 컬럼**: 항상 `seq` (Auto Increment)
- **모든 테이블/컬럼에 COMMENT 포함**: 데이터베이스 스키마 문서화

### 테이블 생성 템플릿
```sql
CREATE TABLE table_name (
    seq BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '기본키 (자동증가)',
    columnName VARCHAR(255) COMMENT '컬럼 설명',
    createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
    INDEX idx_columnName (columnName)
) COMMENT='테이블 설명';
```

### 예시
```sql
CREATE TABLE members (
    seq BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '회원 ID',
    memberType VARCHAR(20) NOT NULL COMMENT '회원 유형 (international/domestic)',
    email VARCHAR(255) NOT NULL UNIQUE COMMENT '이메일 주소',
    password VARCHAR(255) NOT NULL COMMENT '암호화된 비밀번호',
    fullName VARCHAR(255) NOT NULL COMMENT '회원 성명',
    country VARCHAR(100) COMMENT '국가 (국제회원용)',
    phoneNumber VARCHAR(20) COMMENT '휴대폰 번호 (국내회원용)',
    newsletter BOOLEAN DEFAULT FALSE COMMENT '뉴스레터 수신 여부',
    createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '가입 시간',
    updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '정보 수정 시간',
    INDEX idx_email (email),
    INDEX idx_memberType (memberType)
) COMMENT='회원 정보 테이블';
```

---

## 엑셀 일괄등록 양식 제작 규칙

엑셀 일괄등록·일괄배정 버튼이나 모달을 추가·수정할 때는 **`document/admin_ui_design_guide.md`의 ‘엑셀 일괄등록·일괄배정 UI’ 항목을 반드시 확인하고 적용합니다.**

- 목록 진입 버튼은 보라색 연한 배경·테두리와 `FileSpreadsheet` 아이콘을 사용합니다.
- 모달 헤더는 보라색 아이콘 + 제목, 그 아래 업무 설명, 우측 상단 닫기 버튼으로 구성합니다.
- 하단은 작은 테두리형 닫기 버튼과 보라색 실행 버튼으로 구성하며, 실행 버튼은 `Upload` 아이콘과 처리 중 `LoaderCircle`을 사용합니다.
- 기준 구현은 `FreeRecipientPage.tsx`의 일괄등록 UI와 `AbstractPage.tsx` / `ReviewerAssignmentBulkImportModal.tsx`의 심사자 일괄배정 UI입니다. 정확한 클래스·다크모드·접근성·처리 중 동작은 위 가이드를 따릅니다.
- 이 스타일은 일괄등록·일괄배정 전용입니다. 일반 개별 등록·저장·엑셀 다운로드는 각 역할의 기존 스타일을 유지합니다.

엑셀 일괄등록·일괄배정 양식을 제작하거나 변경할 때는 `document/excel_bulk_import_template_guide.md`를 참고합니다.

---

## 카드 결제 및 PG사 연동 규칙

카드 결제 기능을 구현·변경하거나 새로운 PG사를 추가할 때는 작업 전에 `document/payment_gateway_integration_guide.md`를 전체 확인하고 해당 구조와 체크리스트를 따릅니다.

- `PaymentGatewayProperties`에는 전체 활성화, 선택 provider, 테스트 모드 등 공통 설정만 둡니다.
- PG사별 설정과 어댑터는 `payment/{provider}/` 하위 패키지에 분리합니다.
- 새 어댑터는 `PaymentGatewayAdapter`를 구현하고 Spring Bean으로 등록하여 `PaymentGatewayRegistry`가 자동 수집하게 합니다.
- 브라우저 SDK가 필요한 PG사는 `frontend/src/payment/`에 provider 전용 클라이언트를 분리하거나 등록합니다.
- MID, API 키, Secret Key 등 운영값은 소스에 하드코딩하거나 프론트엔드에 전달하지 않습니다.
- 브라우저 callback 또는 return URL만으로 결제를 확정하지 않고, 서버 notify·승인 조회·서명 검증 중 PG사가 제공하는 검증 절차를 적용합니다.
- 동일 PG사를 다른 학회에 적용할 때는 소스를 복제하지 않고 MID와 환경별 설정만 교체합니다.
- 새 PG사 추가 시 설정 바인딩, 국내·국외 요청, 비밀값 비노출, 결과 검증과 중복 통지 방지 테스트를 작성합니다.

---

## AI 에이전트를 위한 참고사항

- **데이터베이스 쿼리**: MyBatis mapper (XML 또는 주석)는 아직 생성되지 않음—추가해야 함
- **인증**: Spring Security는 구성되었지만 아직 연결됨—계획 필요
- **파일 저장**: `UploadStorage`를 통해 `base-directory/메뉴/yyyyMM/파일명` 구조로 저장하며 DB에는 상대 경로만 기록
- **에러 처리**: 프론트엔드는 에러 응답으로 평문 또는 JSON 기대
- **국제화 (i18n)**: 미구현; UI는 주로 한국어 (`AbstractRegisterModal` 라벨 참조)

### 로컬 DB 조회 도구

Codex가 설정된 MariaDB 10.6 서버 상태를 확인해야 할 때는 `.codex/db-query.local.ps1`을 사용합니다. 조회 도구가 로컬에 있더라도 접속 대상 서버는 `.codex/db.local.properties`의 설정을 따릅니다.

- 스크립트 위치: `.codex/db-query.local.ps1`
- DB 접속 설정: `.codex/db.local.properties`
- 결과 저장 위치: `.codex/db-query-results/last-result.json`
- 이 파일들은 로컬 진단 전용이며 Git에 커밋하지 않습니다.
- `.codex/db.local.properties`는 비밀값을 포함할 수 있으므로 읽거나 출력하지 말고, 스크립트만 실행합니다.
- 조회는 읽기 전용 SQL만 사용합니다. `INSERT`, `UPDATE`, `DELETE`, `ALTER`, `DROP` 등 쓰기 SQL은 사용하지 않습니다.

사용 예시:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .codex\db-query.local.ps1 -Sql "SELECT COUNT(*) AS count FROM members"
```



## Encoding / Korean text safety

When editing Java, SQL, or TypeScript files that contain Korean comments, labels, SQL comments, or error strings:

1. Always preserve UTF-8 encoding. Never save these files as ANSI/CP949.
2. Prefer rewriting the whole file in one operation when Korean text is touched, instead of patching corrupted fragments.
3. In PowerShell, use `Set-Content -Encoding utf8` or `Out-File -Encoding utf8` for file writes.
4. After editing, verify the file content with the same encoding that was written, and do not trust console rendering if it shows mojibake.
5. If a file already contains broken Korean text, replace the affected block entirely rather than fixing character-by-character.
6. Do not introduce new Korean strings through tools that may change encoding implicitly.

This applies especially to:
- `src/main/java/com/bjworld21/congress/config/DatabaseSchemaInitializer.java`
- React component files with UI labels and validation messages
