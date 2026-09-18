-- 목적: 로그인 전 접근 허용 요청, IP별 접수 제한, 유지보수 담당자 메일 알림 및 관리자 메뉴 추가
-- 대상: MariaDB 10.6 / InnoDB. 반드시 적용할 데이터베이스를 선택한 후 실행.
-- 적용 순서: 1) 아래 테이블 생성 2) 메뉴 등록 3) 배포. 별도 기능 활성화 설정 없음.
-- 1회 실행용. 애플리케이션 시작 시 자동 실행하지 않음.
-- 기존 admin_accounts, admin_ip_allowlist, menu_settings 테이블을 전제로 함.

CREATE TABLE admin_access_request_locks (
    seq BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '잠금 행 기본키',
    siteUrl VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '접속 요청에서 확인한 사이트 주소',
    requestIp VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '정규화된 요청 IP',
    UNIQUE KEY uk_access_request_lock (siteUrl, requestIp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사이트 IP별 동시 접수 직렬화 잠금';

CREATE TABLE admin_access_requests (
    seq BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '접근 요청 기본키',
    siteUrl VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '접속 요청에서 확인한 사이트 주소',
    requestIp VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '서버 확인 요청 IP',
    affiliation TEXT NOT NULL COMMENT '암호화된 소속 HEX AES',
    requesterName TEXT NOT NULL COMMENT '암호화된 요청자명 HEX AES',
    contact TEXT NOT NULL COMMENT '암호화된 전화번호 HEX AES',
    purpose TEXT NOT NULL COMMENT '암호화된 요청 목적 HEX AES',
    startDate DATE NOT NULL COMMENT '요청 사용 시작일 포함',
    endDate DATE NOT NULL COMMENT '요청 사용 종료일 포함',
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED APPROVED REJECTED, 만료는 조회시 EXPIRED 계산',
    createdAt DATETIME(6) NOT NULL COMMENT '접수시각 Asia Seoul',
    expiresAt DATETIME(6) NOT NULL COMMENT '대기 만료시각 접수 후 24시간',
    processedByAdminSeq BIGINT NULL COMMENT '처리 관리자 기본키',
    processedAt DATETIME(6) NULL COMMENT '허용 거절 처리시각',
    allowlistSeq BIGINT NULL COMMENT '승인으로 연결된 접근허용 IP 규칙 기본키 삭제 후 이력 유지',
    INDEX idx_access_request_ip_date (siteUrl,requestIp,createdAt),
    INDEX idx_access_request_list (siteUrl,status,seq),
    CONSTRAINT chk_access_request_period CHECK (endDate >= startDate),
    CONSTRAINT chk_access_request_status CHECK (status IN ('REQUESTED','APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='관리자 접근허용요청 접수 및 처리 이력';

CREATE TABLE admin_access_request_mail (
    seq BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '메일 작업 기본키',
    requestSeq BIGINT NOT NULL COMMENT '접근 요청 기본키',
    recipientEmail TEXT NOT NULL COMMENT '접수시 활성 유지보수 담당자 이메일 암호문',
    status VARCHAR(20) NOT NULL DEFAULT 'READY' COMMENT 'READY SENDING SENT FAILED',
    attemptCount INT NOT NULL DEFAULT 0 COMMENT '발송 시도 횟수 최대 3회',
    nextAttemptAt DATETIME(6) NOT NULL COMMENT '다음 발송시각',
    leaseUntil DATETIME(6) NULL COMMENT '작업 점유 만료시각',
    claimToken VARCHAR(36) NULL COMMENT '작업 점유 토큰',
    failureReason VARCHAR(500) NULL COMMENT '비밀값을 제외한 실패 안내',
    INDEX idx_access_mail_queue(status,nextAttemptAt),
    INDEX idx_access_mail_request(requestSeq),
    CONSTRAINT fk_access_mail_request FOREIGN KEY(requestSeq) REFERENCES admin_access_requests(seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='접근 요청 알림 메일 발송 및 재시도 작업';

-- 공통 관리자 시스템관리 아래 메뉴 추가. 관리자 메뉴는 conferenceSeq IS NULL.
INSERT INTO menu_settings
    (conferenceSeq,menuScope,menuKey,parentKey,menuName,menuPath,menuType,pathType,routePath,
     depth,targetType,authRequired,navigationVisible,sortOrder,enabled,createdAt,updatedAt)
SELECT p.conferenceSeq,'admin','access-requests',p.menuKey,'접근허용요청','/access-requests',
       'page','full','/access-requests',p.depth+1,'self',TRUE,TRUE,90,TRUE,NOW(),NOW()
FROM menu_settings p
WHERE p.menuScope='admin' AND p.menuKey='system' AND p.conferenceSeq IS NULL
  AND NOT EXISTS (SELECT 1 FROM menu_settings c WHERE c.conferenceSeq IS NULL
      AND c.menuScope='admin' AND c.menuKey='access-requests');
