-- MariaDB 10.6. Run manually before deploying the read-model implementation.
-- Raw events and existing daily aggregates are preserved.
CREATE TABLE analytics_daily_facts (
    seq BIGINT NOT NULL AUTO_INCREMENT COMMENT '접속 경량 집계 기본키',
    conferenceSeq BIGINT NOT NULL COMMENT '학회 기본키',
    statDate DATE NOT NULL COMMENT '집계 일자 (서울 시간)',
    factKey CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '방문자·세션·페이지·분류 조합 SHA-256',
    visitorIdHash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '익명 방문자 식별값 해시',
    sessionIdHash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '익명 세션 식별값 해시',
    pagePath VARCHAR(500) NOT NULL COMMENT '조회 페이지 경로',
    pageTitle VARCHAR(255) DEFAULT NULL COMMENT '페이지 제목',
    sourceType VARCHAR(20) DEFAULT NULL COMMENT '유입경로 유형',
    deviceType VARCHAR(20) DEFAULT NULL COMMENT '기기 유형',
    browserFamily VARCHAR(50) DEFAULT NULL COMMENT '브라우저',
    osFamily VARCHAR(50) DEFAULT NULL COMMENT '운영체제',
    countryCode CHAR(2) DEFAULT NULL COMMENT 'ISO 국가 코드',
    pageViewCount BIGINT NOT NULL DEFAULT 0 COMMENT '페이지뷰 수',
    totalDurationSeconds BIGINT NOT NULL DEFAULT 0 COMMENT '증분 체류시간 합계',
    lastOccurredAt DATETIME NOT NULL COMMENT '조합 내 마지막 이벤트 발생 시각',
    lastEventOrder CHAR(34) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '마지막 이벤트 정렬키 (발생시각 14자리와 원본 seq 20자리)',
    createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
    PRIMARY KEY (seq),
    UNIQUE KEY uk_analytics_daily_fact (conferenceSeq, statDate, factKey),
    KEY idx_analytics_fact_session_date (conferenceSeq, sessionIdHash, statDate),
    KEY idx_analytics_fact_country_date (conferenceSeq, countryCode, statDate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='날짜별 방문자·세션·페이지·분류 경량 접속 집계';

ALTER TABLE analytics_daily_summary
    ADD COLUMN sourceMaxEventSeq BIGINT NOT NULL DEFAULT 0 COMMENT '집계에 반영한 해당 날짜 원본 이벤트 최대 seq',
    ADD COLUMN aggregationVersion INT NOT NULL DEFAULT 0 COMMENT '경량 집계 형식 버전 (0은 기존 집계로 재집계 필요)';
