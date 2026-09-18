-- 목적: 학회별 지원 언어와 메뉴 번역/언어별 HTML 이력 및 평탄한 사용자 경로 적용
-- 대상: MariaDB 10.6. 운영 반영 전 백업하고 애플리케이션 쓰기를 중단한 상태에서 실행합니다.
-- 순서: 기존 경로 JSON 보관 -> 사전 조회 -> 1 스키마 -> 2 기존 영문 이관 -> 3 메뉴 경로 변환
--       -> 4 저장된 HTML/링크 오프라인 이관 -> 새 코드 배포.
-- 1회 실행용입니다. DDL은 암시적으로 커밋되므로 오류 발생 시 결과를 확인하고 해당 단계부터 진행합니다.
-- DB 접속/실행은 운영자가 수행합니다. 서버 시작 시 자동 실행하지 않습니다.
-- 필수 사전 작업: scripts/migrate_public_content_links.py --print-route-query가 출력하는 읽기 전용
-- SELECT 결과를 JSON으로 저장합니다. 3단계 실행 이후에는 이전 경로가 사라지므로 먼저 보관해야 합니다.
-- 자세한 내보내기/검토 명령은 document/public_site_routing_guide.md를 따릅니다.

-- 사전 조회: 기존 HTML 이력 테이블의 인덱스를 확인합니다.
SHOW INDEX FROM menu_html_histories;

-- 1. 학회 설정. sitePath 및 conference_languages는 앞서 수동 적용되어 있어도 유지합니다.
ALTER TABLE conference_settings
    ADD COLUMN IF NOT EXISTS sitePath VARCHAR(100) NULL COMMENT '사용자 화면 학회 구분 경로 (예: apdrc8)',
    ADD COLUMN IF NOT EXISTS defaultLanguage VARCHAR(35) NOT NULL DEFAULT 'en' COMMENT '학회 기본 언어 코드 (지원 언어 중 하나)';

-- 기존 sitePath 값은 유지합니다. 공백/중복/예약 경로가 있으면 먼저 수정합니다.
SELECT seq, eventName, sitePath FROM conference_settings ORDER BY seq;
SELECT sitePath, COUNT(*) AS duplicateCount FROM conference_settings GROUP BY sitePath HAVING COUNT(*) > 1;

-- sitePath가 없는 신규 설치의 기존 행은 운영자가 실제 경로를 입력해야 합니다.
-- 예: UPDATE conference_settings SET sitePath = 'apdrc8' WHERE seq = 실제학회번호;
-- 아래 NOT NULL/UNIQUE 적용 전 NULL과 빈 문자열이 없어야 합니다.
ALTER TABLE conference_settings
    MODIFY COLUMN sitePath VARCHAR(100) NOT NULL COMMENT '사용자 화면 학회 구분 경로 (예: apdrc8)',
    ADD UNIQUE INDEX IF NOT EXISTS uk_conference_setting_sitePath (sitePath);

CREATE TABLE IF NOT EXISTS conference_languages (
    seq BIGINT NOT NULL AUTO_INCREMENT COMMENT '학회 지원 언어 ID',
    conferenceSeq BIGINT NOT NULL COMMENT '학회 ID (conference_settings.seq)',
    languageCode VARCHAR(35) NOT NULL COMMENT '지원 언어 코드 (예: ko, en, ja, zh-Hans)',
    sortOrder INT NOT NULL DEFAULT 0 COMMENT '언어 선택 표시 순서',
    createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
    PRIMARY KEY (seq),
    UNIQUE KEY uk_conference_languages_conference_language (conferenceSeq, languageCode),
    CONSTRAINT fk_conference_languages_conference FOREIGN KEY (conferenceSeq) REFERENCES conference_settings(seq)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='학회별 지원 언어 설정';

CREATE TABLE menu_translations (
    seq BIGINT NOT NULL AUTO_INCREMENT COMMENT '메뉴 번역 ID',
    menuSeq BIGINT NOT NULL COMMENT '원본 메뉴 ID (menu_settings.seq)',
    languageCode VARCHAR(35) NOT NULL COMMENT '언어 코드',
    menuName VARCHAR(255) NOT NULL COMMENT '언어별 메뉴명',
    menuHtml LONGTEXT NULL COMMENT '언어별 메뉴 HTML 본문',
    htmlRevisionNo BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '해당 언어의 현재 HTML 이력 버전',
    createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
    PRIMARY KEY (seq),
    UNIQUE KEY uk_menu_translations_menu_language (menuSeq, languageCode),
    CONSTRAINT fk_menu_translations_menu FOREIGN KEY (menuSeq) REFERENCES menu_settings(seq)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 메뉴 언어별 메뉴명 및 HTML';

ALTER TABLE menu_html_histories
    ADD COLUMN languageCode VARCHAR(35) NOT NULL DEFAULT 'en' COMMENT 'HTML 이력 언어 코드 (기존 이력은 영문)';

-- 기존 (menuSeq, revisionNo) 유일 인덱스 이름을 조회하여 정확히 해당 인덱스만 교체합니다.
SET @old_revision_index = (
    SELECT index_name FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'menu_html_histories' AND non_unique = 0
    GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'menuSeq,revisionNo'
    LIMIT 1
);
SET @drop_revision_index = IF(@old_revision_index IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE menu_html_histories DROP INDEX `', REPLACE(@old_revision_index, '`', '``'), '`'));
-- FK의 menuSeq 인덱스를 먼저 확보하여 기존 유일 인덱스를 안전하게 교체합니다.
ALTER TABLE menu_html_histories ADD INDEX IF NOT EXISTS idx_menu_html_histories_menu_language (menuSeq, languageCode);
PREPARE migration_statement FROM @drop_revision_index;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
ALTER TABLE menu_html_histories ADD UNIQUE KEY uk_menu_html_histories_language_revision (menuSeq, languageCode, revisionNo);

-- 2. 초기 운영은 모든 기존 학회 국문/영문, 기본 영문입니다. 기존 지원 언어 행은 보존합니다.
INSERT INTO conference_languages(conferenceSeq, languageCode, sortOrder)
SELECT c.seq, 'ko', 0 FROM conference_settings c
WHERE NOT EXISTS (SELECT 1 FROM conference_languages l WHERE l.conferenceSeq=c.seq AND l.languageCode='ko');
INSERT INTO conference_languages(conferenceSeq, languageCode, sortOrder)
SELECT c.seq, 'en', 1 FROM conference_settings c
WHERE NOT EXISTS (SELECT 1 FROM conference_languages l WHERE l.conferenceSeq=c.seq AND l.languageCode='en');

-- 기존 메뉴명/HTML/이력 버전을 영문으로 보존합니다. 국문은 작성 전까지 준비 중으로 표시합니다.
INSERT INTO menu_translations(menuSeq, languageCode, menuName, menuHtml, htmlRevisionNo)
SELECT seq, 'en', menuName, menuHtml, COALESCE(htmlRevisionNo, 0)
FROM menu_settings WHERE menuScope='user';

-- 3. 경로 변환은 임시 매핑에서 충돌을 검증한 후 일괄 반영합니다. 관리자/외부 링크는 제외합니다.
CREATE TEMPORARY TABLE public_menu_route_migration AS
SELECT seq, conferenceSeq, routePath AS oldRoutePath,
    CASE
        WHEN routePath='/' THEN '/'
        WHEN routePath LIKE '/mypage/abstract/%' THEN CONCAT('/abstract-', SUBSTRING_INDEX(TRIM(TRAILING '/' FROM routePath), '/', -1))
        WHEN routePath LIKE '/mypage/%' THEN CONCAT('/mypage-', SUBSTRING_INDEX(TRIM(TRAILING '/' FROM routePath), '/', -1))
        WHEN routePath LIKE '/join/%' THEN CONCAT('/join-', SUBSTRING_INDEX(TRIM(TRAILING '/' FROM routePath), '/', -1))
        WHEN routePath LIKE '/password-reset/%' THEN CONCAT('/password-reset-', SUBSTRING_INDEX(TRIM(TRAILING '/' FROM routePath), '/', -1))
        ELSE CONCAT('/', SUBSTRING_INDEX(TRIM(TRAILING '/' FROM routePath), '/', -1))
    END AS newRoutePath
FROM menu_settings
WHERE menuScope='user' AND menuType <> 'link' AND routePath LIKE '/%';

SELECT conferenceSeq, newRoutePath, COUNT(*) AS duplicateCount, GROUP_CONCAT(seq) AS menuSeqs
FROM public_menu_route_migration GROUP BY conferenceSeq, newRoutePath HAVING COUNT(*) > 1;

-- 충돌이 있으면 오류로 중단하며, 경로 UPDATE는 실행되지 않습니다.
DELIMITER //
CREATE PROCEDURE apply_public_menu_flat_routes()
BEGIN
    IF EXISTS (SELECT 1 FROM public_menu_route_migration GROUP BY conferenceSeq, newRoutePath HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Duplicate flattened menu routes: resolve mapping before migration';
    END IF;
    UPDATE menu_settings m JOIN public_menu_route_migration r ON r.seq=m.seq
    SET m.routePath=r.newRoutePath, m.menuPath=TRIM(LEADING '/' FROM r.newRoutePath);
END//
DELIMITER ;
CALL apply_public_menu_flat_routes();
DROP PROCEDURE apply_public_menu_flat_routes;
-- oldRoutePath -> newRoutePath는 본문 링크 이관 검토용으로 보관합니다.
SELECT * FROM public_menu_route_migration ORDER BY conferenceSeq, seq;
DROP TEMPORARY TABLE public_menu_route_migration;

-- 4. 저장된 HTML과 별도 링크 컬럼 이관 (이 파일만 실행하면 본문 링크 이관은 완료되지 않습니다).
-- 런타임에서 이전 폴더 경로를 자동으로 바꾸지 않으므로 새 코드 배포 전에 반드시 수행합니다.
-- 위 1~3단계가 끝난 뒤 scripts/migrate_public_content_links.py --print-content-query의
-- 읽기 전용 SELECT 결과를 JSON으로 내보냅니다. 이 시점에 내보내야 새 영문 번역도 포함됩니다.
-- 사전 보관한 경로 JSON + 본문 JSON으로 오프라인 파서를 실행하여 검토용 UPDATE SQL을 생성합니다.
-- 대상: 사용자 menu_settings.menuHtml, 모든 언어의 menu_translations.menuHtml,
--       사용자 메뉴 menu_html_histories.menuHtml, board_posts.content, popups.content,
--       popups.linkUrl 및 사용자 링크 메뉴의 linkUrl/routePath.
-- 실제 a/area 태그의 따옴표로 감싼 href만 변환하며 외부 URL, 자산 URL, 본문 텍스트는 유지합니다.
-- HTML에 대해 REPLACE/REGEXP_REPLACE를 일괄 실행하지 않습니다.
-- 생성 SQL은 원본문 SHA2 조건과 학회 조건으로 보호되며 COMMIT을 자동 실행하지 않습니다.
-- manualReview를 먼저 해결하고 한 세션에서 실행한 뒤 expectedColumns=changedColumns를 확인하여
-- 수동 COMMIT합니다. 개수가 다르거나 검토에 실패하면 ROLLBACK하고 다시 내보냅니다.
