## 🔐 관리자 관리 API 구현 완료

### 📌 생성된 파일 목록

#### Backend 

**Entity**
- ✅ `AdminAccount.java` - 관리자 계정 엔티티

**Repository**
- ✅ `AdminAccountRepository.java` - MyBatis 매퍼

**DTO**
- ✅ `AdminLoginRequest.java` - 로그인 요청
- ✅ `AdminLoginResponse.java` - 로그인 응답
- ✅ `AdminCreateRequest.java` - 관리자 생성 요청
- ✅ `AdminResponse.java` - 통합 응답

**Service**
- ✅ `AdminService.java` - 비즈니스 로직 (7가지 메서드)

**Controller**
- ✅ `AdminController.java` - REST API (7가지 엔드포인트)

#### Frontend

**Components**
- ✅ `AdminLoginModal.tsx` - 관리자 로그인 모달

**Views**
- ✅ `App.tsx` 수정 - Admin 로그인 UI 통합

#### Database

---

## 📚 API 엔드포인트 상세

### 1️⃣ 로그인
```
POST /api/admin/login
● 파라미터: email, password
● 응답: AdminLoginResponse

예시:
curl -X POST "http://localhost:8080/api/admin/login" \
  -d "email=admin@example.com" \
  -d "password=password123"

응답:
{
  "seq": 1,
  "email": "admin@example.com",
  "adminName": "최고관리자",
  "role": "admin",
  "message": "로그인 성공"
}
```

### 2️⃣ 관리자 계정 생성
```
POST /api/admin/accounts
● 파라미터: email, password, adminName, role (기본: moderator)
● 응답: AdminResponse
● 권한: 최고관리자만 사용

예시:
curl -X POST "http://localhost:8080/api/admin/accounts" \
  -d "email=moderator@example.com" \
  -d "password=password123" \
  -d "adminName=심사자" \
  -d "role=moderator"

응답:
{
  "seq": 2,
  "email": "moderator@example.com",
  "adminName": "심사자",
  "role": "moderator",
  "status": "active",
  "message": "관리자 계정이 생성되었습니다."
}
```

### 3️⃣ 관리자 정보 조회
```
GET /api/admin/accounts/{seq}
● 경로 파라미터: seq (관리자 ID)
● 응답: AdminResponse

예시:
curl -X GET "http://localhost:8080/api/admin/accounts/1"

응답:
{
  "seq": 1,
  "email": "admin@example.com",
  "adminName": "최고관리자",
  "role": "admin",
  "status": "active"
}
```

### 4️⃣ 활성 관리자 목록 조회
```
GET /api/admin/accounts/active
● 응답: List<AdminResponse>

예시:
curl -X GET "http://localhost:8080/api/admin/accounts/active"

응답:
[
  {
    "seq": 1,
    "email": "admin@example.com",
    "adminName": "최고관리자",
    "role": "admin",
    "status": "active"
  },
  {
    "seq": 2,
    "email": "moderator@example.com",
    "adminName": "심사자",
    "role": "moderator",
    "status": "active"
  }
]
```

### 5️⃣ 모든 관리자 목록 조회
```
GET /api/admin/accounts
● 응답: List<AdminResponse>

예시:
curl -X GET "http://localhost:8080/api/admin/accounts"

응답: (위와 동일, 비활성 계정도 포함)
```

### 6️⃣ 관리자 정보 업데이트
```
PUT /api/admin/accounts/{seq}
● 경로 파라미터: seq (관리자 ID)
● 쿼리 파라미터: adminName (선택), role (선택), status (선택)
● 응답: AdminResponse

예시:
curl -X PUT "http://localhost:8080/api/admin/accounts/2" \
  -d "adminName=심사자명변경" \
  -d "role=admin"

응답:
{
  "seq": 2,
  "email": "moderator@example.com",
  "adminName": "심사자명변경",
  "role": "admin",
  "status": "active",
  "message": "관리자 정보가 업데이트되었습니다."
}
```

### 7️⃣ 관리자 삭제
```
DELETE /api/admin/accounts/{seq}
● 경로 파라미터: seq (관리자 ID)
● 응답: 메시지

예시:
curl -X DELETE "http://localhost:8080/api/admin/accounts/2"

응답:
"관리자 계정이 삭제되었습니다."
```

### 8️⃣ 비밀번호 변경
```
POST /api/admin/change-password/{seq}
● 경로 파라미터: seq (관리자 ID)
● 파라미터: oldPassword, newPassword
● 응답: 메시지

예시:
curl -X POST "http://localhost:8080/api/admin/change-password/1" \
  -d "oldPassword=password123" \
  -d "newPassword=newpassword456"

응답:
"비밀번호가 변경되었습니다."
```

---

## 🗄️ admin_accounts 테이블 구조

```sql
CREATE TABLE admin_accounts (
    seq BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    adminName VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'admin',      -- admin, moderator
    status VARCHAR(20) NOT NULL DEFAULT 'active',   -- active, inactive
    lastLoginAt TIMESTAMP,
    createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_role (role),
    INDEX idx_status (status)
);
```

---

## 👤 권한 구분

| 권한 | 설명 | 권한 |
|------|------|------|
| admin | 최고관리자 | 모든 기능 + 관리자 계정 관리 |
| moderator | 심사자 | 논문심사, 통계 조회만 가능 |

---

## 🎯 프론트엔드 Admin 로그인 UI

### Admin 로그인 모달 (`AdminLoginModal.tsx`)

**기능:**
- ✅ 이메일/비밀번호 입력
- ✅ 비밀번호 표시/숨김
- ✅ 에러 메시지 표시
- ✅ 로딩 상태 관리
- ✅ localStorage에 토큰 저장
- ✅ Dark Mode 지원

**위치:** Header 우측 상단
```
미로그인: [관리자 로그인] 버튼 표시
로그인 후: [관리자명] [로그아웃] 표시
```

---

## 🔄 로그인 플로우

```
1. 관리자 로그인 버튼 클릭
   ↓
2. AdminLoginModal 열기
   ↓
3. 이메일/비밀번호 입력 후 제출
   ↓
4. POST /api/admin/login
   ↓
5. 성공 시:
   ├─ localStorage에 토큰 저장
   ├─ Header에 관리자명 표시
   └─ onSuccess() 콜백 실행
   
6. 실패 시:
   └─ 에러 메시지 표시
```

---

## 🚀 사용 예시

### 1. 관리자 로그인

프론트엔드에서 "관리자 로그인" 클릭 → 모달 열기 → 인증정보 입력 → 로그인

### 2. 심사자 계정 추가

```bash
curl -X POST "http://localhost:8080/api/admin/accounts" \
  -d "email=reviewer1@icms2026.com" \
  -d "password=reviewer123" \
  -d "adminName=김심사자" \
  -d "role=moderator"
```

### 3. 심사자 계정 비활성화

```bash
curl -X PUT "http://localhost:8080/api/admin/accounts/2" \
  -d "status=inactive"
```

---

## 🔒 보안 주의사항

1. **비밀번호**: BCrypt로 자동 암호화 ✅
2. **입력 검증**: 모든 필드 유효성 검사 ✅
3. **상태 확인**: inactive 계정 로그인 차단 ✅
4. **중복 방지**: 이메일 UNIQUE 제약 ✅
5. **마지막 로그인 추적**: 보안 감시 ✅

---

## 📋 구현된 메서드 (AdminService)

| 메서드 | 기능 |
|--------|------|
| login() | 관리자 로그인 |
| create() | 관리자 계정 생성 |
| getAdmin() | 관리자 정보 조회 |
| getAllActiveAdmins() | 활성 관리자 목록 |
| getAllAdmins() | 모든 관리자 목록 |
| updateAdmin() | 관리자 정보 업데이트 |
| deleteAdmin() | 관리자 삭제 |
| changePassword() | 비밀번호 변경 |

---

## 🧪 테스트 방법

### 1. Postman으로 테스트

**로그인:**
- Method: POST
- URL: `http://localhost:8080/api/admin/login`
- Body (form-data): 
  - email: admin@icms2026.com
  - password: admin123

**계정 생성:**
- Method: POST
- URL: `http://localhost:8080/api/admin/accounts`
- Body (form-data):
  - email: reviewer@icms2026.com
  - password: reviewer123
  - adminName: 김심사자
  - role: moderator

### 2. 프론트엔드로 테스트

```
1. http://localhost:5173 접속
2. Header 우측 "관리자 로그인" 클릭
3. 이메일/비밀번호 입력 후 로그인
4. 로그인 성공 시 관리자명 표시
5. 로그아웃 버튼으로 로그아웃 가능
```

---

모든 기본 관리 기능이 구현되었습니다! 🎉

