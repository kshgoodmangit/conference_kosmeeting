-- 목적: 2026_136 학회의 기존 메뉴 경로를 /pageName 형태로 이관합니다.
-- 대상 DB: MariaDB 10.6
-- 적용 순서: 언어 테이블/번역 초기화 후 실행합니다. 기존 전체 마이그레이션의 3단계 대체용입니다.
-- 1회 실행: 2026-09-19 조회한 메뉴 43개 중 routePath 30개, menuPath만 다른 1개를 대상으로 합니다.
-- 기존 테이블 생성이나 번역 INSERT를 반복하지 않습니다.
-- 메뉴 트리(parentKey), 메뉴명, HTML, 사용 여부는 유지합니다.
-- 한 DB 연결에서 실행하고 마지막 changedMenus가 31인지 확인한 뒤 COMMIT합니다.
-- 31이 아니면 ROLLBACK 후 현재 데이터를 재확인합니다. 이미 적용된 경우 재실행하지 않습니다.
-- HTML 본문에 저장된 예전 링크는 scripts/migrate_public_content_links.py로 별도 이관합니다.

START TRANSACTION;
SET @flat_route_changed_menus = 0;

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/welcome-message', m.menuPath = 'welcome-message', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 2
  AND m.routePath = '/apdrc8/welcome-message' AND m.menuPath <=> 'welcome-message';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/overview', m.menuPath = 'overview', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 3
  AND m.routePath = '/apdrc8/overview' AND m.menuPath <=> 'overview';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/committee', m.menuPath = 'committee', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 4
  AND m.routePath = '/apdrc8/committee' AND m.menuPath <=> 'committee';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/about-apdrc8', m.menuPath = 'about-apdrc8', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 5
  AND m.routePath = '/apdrc8/about-apdrc8' AND m.menuPath <=> 'about-apdrc8';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/contact-us', m.menuPath = 'contact-us', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 6
  AND m.routePath = '/apdrc8/contact-us' AND m.menuPath <=> 'contact-us';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/program-at-a-glance', m.menuPath = 'program-at-a-glance', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 8
  AND m.routePath = '/program/program-at-a-glance' AND m.menuPath <=> 'program-at-a-glance';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/scientific-program', m.menuPath = 'scientific-program', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 9
  AND m.routePath = '/program/scientific-program' AND m.menuPath <=> 'scientific-program';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/invited-speakers', m.menuPath = 'invited-speakers', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 10
  AND m.routePath = '/program/invited-speakers' AND m.menuPath <=> 'invited-speakers';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/submission-guideline', m.menuPath = 'submission-guideline', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 12
  AND m.routePath = '/abstract/submission-guideline' AND m.menuPath <=> 'submission-guideline';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/abstract-submission', m.menuPath = 'abstract-submission', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 13
  AND m.routePath = '/abstract/abstract-submission' AND m.menuPath <=> 'abstract-submission';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/award', m.menuPath = 'award', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 14
  AND m.routePath = '/abstract/award' AND m.menuPath <=> 'award';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/presentation-guidelines', m.menuPath = 'presentation-guidelines', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 15
  AND m.routePath = '/abstract/presentation-guidelines' AND m.menuPath <=> 'presentation-guidelines';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/registration-guideline', m.menuPath = 'registration-guideline', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 17
  AND m.routePath = '/registration/registration-guideline' AND m.menuPath <=> 'registration-guideline';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/online-registration', m.menuPath = 'online-registration', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 18
  AND m.routePath = '/registration/online-registration' AND m.menuPath <=> 'online-registration';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/accommodation', m.menuPath = 'accommodation', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 19
  AND m.routePath = '/registration/accommodation' AND m.menuPath <=> 'accommodation';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/visa', m.menuPath = 'visa', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 20
  AND m.routePath = '/registration/visa' AND m.menuPath <=> 'visa';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/cancellation-policy', m.menuPath = 'cancellation-policy', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 21
  AND m.routePath = '/registration/cancellation-policy' AND m.menuPath <=> 'cancellation-policy';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/venue', m.menuPath = 'venue', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 23
  AND m.routePath = '/information/venue' AND m.menuPath <=> 'venue';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/transportation', m.menuPath = 'transportation', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 24
  AND m.routePath = '/information/transportation' AND m.menuPath <=> 'transportation';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/about-seoul-korea', m.menuPath = 'about-seoul-korea', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 25
  AND m.routePath = '/information/about-seoul-korea' AND m.menuPath <=> 'about-seoul-korea';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/notice', m.menuPath = 'notice', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 26
  AND m.routePath = '/information/notice' AND m.menuPath <=> 'notice';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/faq', m.menuPath = 'faq', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 27
  AND m.routePath = '/information/faq' AND m.menuPath <=> 'faq';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/sponsorship', m.menuPath = 'sponsorship', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 29
  AND m.routePath = '/sponsors/sponsorship' AND m.menuPath <=> 'sponsorship';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/sponsor', m.menuPath = 'sponsor', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 30
  AND m.routePath = '/sponsors/sponsor' AND m.menuPath <=> 'sponsor';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/member', m.menuPath = 'member', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 74
  AND m.routePath = '/member' AND m.menuPath <=> 'root';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/join-domestic', m.menuPath = 'join-domestic', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 75
  AND m.routePath = '/join/domestic' AND m.menuPath <=> 'join-domestic';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/join-international', m.menuPath = 'join-international', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 76
  AND m.routePath = '/join/international' AND m.menuPath <=> 'join-international';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/mypage-abstract', m.menuPath = 'mypage-abstract', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 77
  AND m.routePath = '/mypage/abstract' AND m.menuPath <=> 'mypage-abstract';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/mypage-registration', m.menuPath = 'mypage-registration', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 78
  AND m.routePath = '/mypage/registration' AND m.menuPath <=> 'mypage-registration';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/mypage-certificate', m.menuPath = 'mypage-certificate', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 79
  AND m.routePath = '/mypage/certificate' AND m.menuPath <=> 'mypage-certificate';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

UPDATE menu_settings m
JOIN conference_settings c ON c.seq = m.conferenceSeq
SET m.routePath = '/mypage-profile', m.menuPath = 'mypage-profile', m.updatedAt = NOW()
WHERE c.sitePath = '2026_136' AND m.conferenceSeq = 1
  AND m.menuScope = 'user' AND m.menuType <> 'link' AND m.seq = 80
  AND m.routePath = '/mypage/profile' AND m.menuPath <=> 'mypage-profile';
SET @flat_route_changed_menus = @flat_route_changed_menus + ROW_COUNT();

SELECT 31 AS expectedMenus, @flat_route_changed_menus AS changedMenus;
SELECT m.seq, m.menuKey, m.routePath, m.menuPath
FROM menu_settings m JOIN conference_settings c ON c.seq = m.conferenceSeq
WHERE c.sitePath = '2026_136' AND m.menuScope = 'user'
ORDER BY m.seq;

-- expectedMenus와 changedMenus가 모두 31이면 다음 문장을 실행합니다.
-- COMMIT;
-- 다르면 다음 문장을 실행합니다.
-- ROLLBACK;
