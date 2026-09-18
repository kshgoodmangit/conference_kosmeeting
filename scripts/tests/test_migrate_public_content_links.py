import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / 'migrate_public_content_links.py'
SPEC = importlib.util.spec_from_file_location('content_migration', SCRIPT)
migration = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(migration)


class ContentLinkMigrationTest(unittest.TestCase):
    def setUp(self):
        self.routes = [{'conferenceSeq': 1, 'oldRoutePath': '/program/overview', 'newRoutePath': '/overview'}]
        self.mapping = migration.build_route_map(self.routes)[1]

    def row(self, source, table='menu_settings', column='menuHtml', seq=7, conference=1):
        return {'tableName': table, 'columnName': column, 'seq': seq,
                'conferenceSeq': conference, 'sourceHex': source.encode('utf-8').hex()}

    def test_exact_href_only_preserves_html_bytes_outside_value(self):
        source = '<DIV data-href="/program/overview">국문\n<A class="x" HREF = \'/program/overview?x=1&amp;y=2#발표\'>Go</A></DIV>'
        parser = migration.AnchorRewriter(source, self.mapping)
        expected = source.replace("HREF = '/program/overview", "HREF = '/overview")
        self.assertEqual(expected, parser.rewrite())
        self.assertEqual(1, len(parser.changes))

    def test_html_examples_scripts_comments_and_other_attributes_are_untouched(self):
        source = '''<!-- <a href="/program/overview"> -->
<script>const sample = '<a href="/program/overview">';</script>
<style>a[href="/program/overview"]{color:red}</style>
<textarea><a href="/program/overview">sample</a></textarea>
<title><a href="/program/overview">sample</a></title>
<div title="href='/program/overview'">/program/overview</div>
<a data-other="href='/program/overview'" href="https://example.org/program/overview">External</a>
<img src="/program/overview"><form action="/program/overview"></form>'''
        self.assertEqual(source, migration.AnchorRewriter(source, self.mapping).rewrite())

    def test_quoted_greater_than_and_escaped_quote_do_not_confuse_attribute_scanner(self):
        source = '<a title="x > y; href=\'/other\'" href="/program/overview?text=&quot;a&quot;">Text</a>'
        expected = source.replace('href="/program/overview?', 'href="/overview?')
        self.assertEqual(expected, migration.AnchorRewriter(source, self.mapping).rewrite())

    def test_area_links_and_multiple_lines(self):
        source = '<map>\n<area shape="rect" href="/mypage/abstract/write?seq=15" />\n<a\n href="/program/overview/">Go</a></map>'
        expected = source.replace('/mypage/abstract/write', '/abstract-write').replace('/program/overview/', '/overview')
        self.assertEqual(expected, migration.AnchorRewriter(source, self.mapping).rewrite())

    def test_unquoted_and_duplicate_href_are_reported_without_guessing(self):
        source = '<a href=/program/overview>One</a><a href="/program/overview" href="/elsewhere">Two</a>'
        parser = migration.AnchorRewriter(source, self.mapping)
        self.assertEqual(source, parser.rewrite())
        self.assertEqual(['/program/overview', '/program/overview'], parser.review)

    def test_notice_id_keeps_query_and_fragment(self):
        self.assertEqual(('/notice-detail?seq=123&page=2#top', False),
                         migration.rewrite_url('/information/noticedetail/123?page=2#top', self.mapping))
        self.assertEqual(('/notice-detail?seq=123#top', False),
                         migration.rewrite_url('/information/noticedetail/123/#top', self.mapping))

    def test_conflicting_notice_seq_requires_manual_review(self):
        value = '/information/noticedetail/123?seq=999'
        self.assertEqual((value, True), migration.rewrite_url(value, self.mapping))

    def test_query_substrings_and_external_or_asset_urls_are_not_replaced(self):
        values = ['https://example.org/program/overview', '//example.org/program/overview',
                  'mailto:user@example.org', '#/program/overview', '/api/boards/images/202609/file.png',
                  '/public/images/logo.png', '/vendor/plugin.js', '/admin/program/overview',
                  '/overview?next=/program/overview', '/program/overview-longer']
        for value in values:
            with self.subTest(value=value):
                self.assertEqual(value, migration.rewrite_url(value, self.mapping)[0])

    def test_function_routes_without_menu_rows_are_supported_once(self):
        self.assertEqual(('/abstract-review?seq=20', False),
                         migration.rewrite_url('/mypage/abstract/review?seq=20', {}))
        self.assertEqual(('/join-domestic', False), migration.rewrite_url('/join/domestic', {}))

    def test_route_mappings_do_not_cross_conferences(self):
        rows = [self.row('<a href="/program/overview">Go</a>'),
                self.row('<a href="/program/overview">Go</a>', seq=8, conference=2)]
        sql, report = migration.generate_sql(self.routes, rows)
        self.assertEqual(1, len(report['changedColumns']))
        self.assertEqual(2, report['manualReview'][0]['conferenceSeq'])
        self.assertIn('conferenceSeq=1', sql)
        self.assertNotIn('WHERE seq=8', sql)

    def test_all_languages_histories_boards_popups_and_url_columns_are_supported(self):
        fields = [('menu_settings', 'menuHtml'), ('menu_translations', 'menuHtml'),
                  ('menu_html_histories', 'menuHtml'), ('board_posts', 'content'),
                  ('popups', 'content'), ('popups', 'linkUrl'),
                  ('menu_settings', 'linkUrl'), ('menu_settings', 'routePath')]
        rows = [self.row('/program/overview' if column in {'linkUrl', 'routePath'}
                         else '<a href="/program/overview">안내</a>', table, column)
                for table, column in fields]
        sql, report = migration.generate_sql(self.routes, rows)
        self.assertEqual(8, len(report['changedColumns']))
        self.assertEqual([], report['manualReview'])
        self.assertIn('m.seq=`menu_html_histories`.menuSeq', sql)
        self.assertIn("menuScope='user' AND menuType='link'", sql)

    def test_sql_is_hex_encoded_with_original_hash_and_explicit_commit_decision(self):
        source = '<a href="/program/overview">국문 \'quoted\'</a>'
        sql, report = migration.generate_sql(self.routes, [self.row(source)])
        expected_digest = hashlib.sha256(source.encode('utf-8')).hexdigest()
        self.assertIn(expected_digest, sql)
        self.assertIn('SHA2(CAST(`menuHtml` AS BINARY),256)', sql)
        self.assertIn("CONVERT(X'", sql)
        self.assertNotIn('국문', sql)
        self.assertNotRegex(sql, r'(?m)^COMMIT;')
        self.assertIn('START TRANSACTION;', sql)
        self.assertEqual(1, len(report['changedColumns']))

    def test_unmodified_input_generates_no_update(self):
        sql, report = migration.generate_sql(self.routes, [self.row('<p>이미 새 주소 <a href="/overview">go</a></p>')])
        self.assertNotIn('UPDATE `', sql)
        self.assertEqual([], report['changedColumns'])

    def test_whitelist_ids_encoding_and_duplicate_rows_are_validated(self):
        invalid_rows = [self.row('x', table='members'), self.row('x', column='password'),
                        self.row('x', seq='7 OR 1=1'), self.row('x', conference=True),
                        {**self.row('x'), 'sourceHex': 'ff'}, {**self.row('x'), 'sourceHex': '6'}]
        for row in invalid_rows:
            with self.subTest(row=row), self.assertRaises(ValueError):
                migration.generate_sql(self.routes, [row])
        with self.assertRaises(ValueError):
            migration.generate_sql(self.routes, [self.row('x'), self.row('x')])

    def test_colliding_flat_routes_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'collision'):
            migration.build_route_map(self.routes + [
                {'conferenceSeq': 1, 'oldRoutePath': '/abstract/overview', 'newRoutePath': '/overview'}])

    def test_local_query_export_is_accepted_and_truncation_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'input.json'
            path.write_text(json.dumps({'ok': True, 'truncatedByLimit': False, 'rows': self.routes}), encoding='utf-8-sig')
            self.assertEqual(self.routes, migration.load_rows(path))
            path.write_text(json.dumps({'ok': True, 'truncatedByLimit': True, 'rows': self.routes}), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'truncated'):
                migration.load_rows(path)

    def test_cli_creates_reviewable_artifacts_without_database_access(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            routes, content = directory / 'routes.json', directory / 'content.json'
            output, report = directory / 'review.sql', directory / 'report.json'
            routes.write_text(json.dumps(self.routes), encoding='utf-8')
            content.write_text(json.dumps([self.row('<a href="/program/overview">Go</a>')]), encoding='utf-8')
            self.assertEqual(0, migration.main(['--routes', str(routes), '--content', str(content),
                                               '--output', str(output), '--report', str(report)]))
            self.assertIn('UPDATE `menu_settings`', output.read_text(encoding='utf-8'))
            self.assertEqual(1, len(json.loads(report.read_text(encoding='utf-8'))['changedColumns']))


if __name__ == '__main__':
    unittest.main()
