-- 목적: 공개 가입 이메일 인증번호의 이메일/IP별 발송 제한을 서버 간 공유합니다.
-- 대상: MariaDB 10.6. 적용 순서: DB 백업 -> 이 SQL 1회 실행 -> 애플리케이션 배포.
-- 애플리케이션 시작 시 자동 실행하지 않습니다. 개발/운영 DB에 각각 적용합니다.
-- 이메일과 IP의 원문 대신 서버 키를 사용한 HMAC-SHA256 값만 저장합니다.
CREATE TABLE member_email_verification_limits (
    seq BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '기본키 (자동증가)',
    keyHash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '제한 대상과 용도의 HMAC-SHA256 값',
    windowStart DATETIME(6) NOT NULL COMMENT '제한 시간 구간 시작 시각 (UTC)',
    requestCount INT NOT NULL DEFAULT 0 COMMENT '시간 구간 내 요청 횟수',
    UNIQUE KEY uk_email_verification_limit_key (keyHash),
    INDEX idx_email_verification_limit_window (windowStart)
) ENGINE=InnoDB COMMENT='회원 이메일 인증 요청 제한';
