#!/usr/bin/env python3
"""Generate reviewable MariaDB SQL from read-only JSON exports; never connect to a DB.

Save the route export BEFORE the menu route migration. Export content AFTER the
schema, English seed, and menu route migration, so every translation is included.
Only actual quoted a/area href attributes and whitelisted URL columns are changed.
"""

import argparse
import hashlib
import html
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import sys


ROUTE_QUERY = """SELECT seq, conferenceSeq, routePath AS oldRoutePath,
    CASE
        WHEN routePath='/' THEN '/'
        WHEN routePath LIKE '/mypage/abstract/%' THEN CONCAT('/abstract-', SUBSTRING_INDEX(TRIM(TRAILING '/' FROM routePath), '/', -1))
        WHEN routePath LIKE '/mypage/%' THEN CONCAT('/mypage-', SUBSTRING_INDEX(TRIM(TRAILING '/' FROM routePath), '/', -1))
        WHEN routePath LIKE '/join/%' THEN CONCAT('/join-', SUBSTRING_INDEX(TRIM(TRAILING '/' FROM routePath), '/', -1))
        WHEN routePath LIKE '/password-reset/%' THEN CONCAT('/password-reset-', SUBSTRING_INDEX(TRIM(TRAILING '/' FROM routePath), '/', -1))
        ELSE CONCAT('/', SUBSTRING_INDEX(TRIM(TRAILING '/' FROM routePath), '/', -1))
    END AS newRoutePath
FROM menu_settings
WHERE menuScope='user' AND menuType <> 'link' AND routePath LIKE '/%'
ORDER BY conferenceSeq, seq"""

CONTENT_QUERY = """SELECT 'menu_settings' AS tableName, seq, conferenceSeq, 'menuHtml' AS columnName, HEX(menuHtml) AS sourceHex
FROM menu_settings WHERE menuScope='user' AND menuHtml IS NOT NULL AND menuHtml <> ''
UNION ALL
SELECT 'menu_translations', t.seq, m.conferenceSeq, 'menuHtml', HEX(t.menuHtml)
FROM menu_translations t JOIN menu_settings m ON m.seq=t.menuSeq
WHERE m.menuScope='user' AND t.menuHtml IS NOT NULL AND t.menuHtml <> ''
UNION ALL
SELECT 'menu_html_histories', h.seq, m.conferenceSeq, 'menuHtml', HEX(h.menuHtml)
FROM menu_html_histories h JOIN menu_settings m ON m.seq=h.menuSeq
WHERE m.menuScope='user' AND h.menuHtml IS NOT NULL AND h.menuHtml <> ''
UNION ALL
SELECT 'board_posts', seq, conferenceSeq, 'content', HEX(content)
FROM board_posts WHERE content IS NOT NULL AND content <> ''
UNION ALL
SELECT 'popups', seq, conferenceSeq, 'content', HEX(content)
FROM popups WHERE content IS NOT NULL AND content <> ''
UNION ALL
SELECT 'popups', seq, conferenceSeq, 'linkUrl', HEX(linkUrl)
FROM popups WHERE linkUrl IS NOT NULL AND linkUrl <> ''
UNION ALL
SELECT 'menu_settings', seq, conferenceSeq, 'linkUrl', HEX(linkUrl)
FROM menu_settings WHERE menuScope='user' AND menuType='link' AND linkUrl IS NOT NULL AND linkUrl <> ''
UNION ALL
SELECT 'menu_settings', seq, conferenceSeq, 'routePath', HEX(routePath)
FROM menu_settings WHERE menuScope='user' AND menuType='link' AND routePath IS NOT NULL AND routePath <> ''"""

ALLOWED_COLUMNS = {
    'menu_settings': {'menuHtml', 'linkUrl', 'routePath'},
    'menu_translations': {'menuHtml'},
    'menu_html_histories': {'menuHtml'},
    'board_posts': {'content'},
    'popups': {'content', 'linkUrl'},
}
FUNCTION_PATHS = {
    '/join/domestic': '/join-domestic',
    '/join/international': '/join-international',
    '/mypage/abstract': '/mypage-abstract',
    '/mypage/registration': '/mypage-registration',
    '/mypage/certificate': '/mypage-certificate',
    '/mypage/profile': '/mypage-profile',
    '/mypage/password': '/mypage-password',
    '/mypage/abstract/write': '/abstract-write',
    '/mypage/abstract/review': '/abstract-review',
}
LEGACY_SECTIONS = {'apdrc8', 'program', 'abstract', 'registration', 'information', 'sponsors', 'mypage', 'join', 'password-reset'}
PRESERVED_SECTIONS = {'admin', 'api', 'public', 'assets', 'vendor', 'webjars', 'commoncode', 'actuator'}


def positive_id(value, field):
    if isinstance(value, bool) or not re.fullmatch(r'[1-9][0-9]*', str(value)):
        raise ValueError(f'{field} must be a positive integer')
    return int(value)


def load_rows(path):
    data = json.loads(Path(path).read_text(encoding='utf-8-sig'))
    if isinstance(data, dict):
        if data.get('ok') is False or data.get('truncatedByLimit'):
            raise ValueError('Export failed or was truncated; export again with -MaxRows 0')
        data = data.get('rows')
    if not isinstance(data, list) or any(not isinstance(row, dict) for row in data):
        raise ValueError('Expected a JSON row array or a db-query.local.ps1 JSON export')
    return data


def build_route_map(rows):
    result = {}
    destinations = {}
    for row in rows:
        conference = positive_id(row['conferenceSeq'], 'conferenceSeq')
        old = row['oldRoutePath']
        new = row['newRoutePath']
        if not isinstance(old, str) or not old.startswith('/') or old.startswith('//') or re.search(r'[?#\s]', old):
            raise ValueError(f'Invalid oldRoutePath for conference {conference}')
        if not isinstance(new, str) or not re.fullmatch(r'/(?:[a-z0-9]+(?:-[a-z0-9]+)*)?', new):
            raise ValueError(f'Invalid flat newRoutePath for conference {conference}: {new!r}')
        old = old.rstrip('/') or '/'
        mapping = result.setdefault(conference, {})
        if old in mapping and mapping[old] != new:
            raise ValueError(f'Ambiguous source route: conference {conference}, {old}')
        previous = destinations.setdefault(conference, {}).get(new)
        if previous is not None and previous != old:
            raise ValueError(f'Flattened route collision: conference {conference}, {new}')
        mapping[old] = new
        destinations[conference][new] = old
    return result


def rewrite_url(value, mapping):
    """Return (new URL, needs manual review), without changing external URLs."""
    if not value or value.startswith(('#', '//')) or re.match(r'^[a-zA-Z][a-zA-Z0-9+.-]*:', value):
        return value, False
    path_and_query, marker, fragment = value.partition('#')
    path, question, query = path_and_query.partition('?')
    path = '/' + path.lstrip('/')
    if path.split('/')[1] in PRESERVED_SECTIONS:
        return value, False
    clean = path.rstrip('/') or '/'
    notice = re.fullmatch(r'/information/noticedetail/([0-9]+)', clean)
    if notice:
        # A conflicting existing seq must be resolved by a human, never silently overridden.
        if question and any(part.partition('=')[0] == 'seq' for part in query.split('&')):
            return value, True
        target = '/notice-detail?seq=' + notice.group(1)
        if question and query:
            target += '&' + query
    else:
        target = mapping.get(clean, FUNCTION_PATHS.get(clean))
        if target is None:
            parts = clean.strip('/').split('/')
            return value, len(parts) > 1 and parts[0] in LEGACY_SECTIONS
        if question:
            target += '?' + query
    if marker:
        target += '#' + fragment
    return target, False


def quoted_href_span(tag_text):
    """Lex one start tag; quoted values can contain >, spaces, or other attributes."""
    start = re.match(r'<\s*[^\s/>]+', tag_text)
    if not start:
        return None
    position = start.end()
    hrefs = []
    while position < len(tag_text):
        while position < len(tag_text) and tag_text[position].isspace():
            position += 1
        if position >= len(tag_text) or tag_text[position] in '/>':
            break
        name = re.match(r'[^\s/>=]+', tag_text[position:])
        if not name:
            return None
        attribute = name.group().lower()
        position += len(name.group())
        while position < len(tag_text) and tag_text[position].isspace():
            position += 1
        span = None
        if position < len(tag_text) and tag_text[position] == '=':
            position += 1
            while position < len(tag_text) and tag_text[position].isspace():
                position += 1
            if position < len(tag_text) and tag_text[position] in '\"\'':
                quote = tag_text[position]
                beginning = position + 1
                ending = tag_text.find(quote, beginning)
                if ending < 0:
                    return None
                span = (beginning, ending)
                position = ending + 1
            else:
                unquoted = re.match(r'[^\s>]*', tag_text[position:]).group()
                position += len(unquoted)
        if attribute == 'href':
            hrefs.append(span)
    # Duplicate and unquoted hrefs need manual review. Do not guess browser recovery.
    return hrefs[0] if len(hrefs) == 1 else None


class AnchorRewriter(HTMLParser):
    RAW_TEXT = {'script', 'style', 'textarea', 'title', 'xmp', 'iframe', 'noembed', 'noframes', 'plaintext'}

    def __init__(self, source, mapping):
        super().__init__(convert_charrefs=False)
        self.source = source
        self.mapping = mapping
        self.line_starts = [0] + [m.end() for m in re.finditer('\n', source)]
        self.replacements = []
        self.changes = []
        self.review = []
        self.raw_tag = None

    def handle_starttag(self, tag, attrs):
        if self.raw_tag:
            return
        if tag in self.RAW_TEXT:
            self.raw_tag = tag
            return
        if tag not in {'a', 'area'}:
            return
        text = self.get_starttag_text()
        span = quoted_href_span(text)
        if span is None:
            for key, value in attrs:
                if key == 'href' and value:
                    changed, review = rewrite_url(value, self.mapping)
                    if changed != value or review:
                        self.review.append(value)
            return
        begin, end = span
        original = html.unescape(text[begin:end])
        rewritten, review = rewrite_url(original, self.mapping)
        if review:
            self.review.append(original)
        if rewritten != original:
            line, column = self.getpos()
            offset = self.line_starts[line - 1] + column
            self.replacements.append((offset + begin, offset + end, html.escape(rewritten, quote=True)))
            self.changes.append({'from': original, 'to': rewritten})

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        if tag in self.RAW_TEXT:
            self.handle_endtag(tag)

    def handle_endtag(self, tag):
        if tag == self.raw_tag and tag != 'plaintext':
            self.raw_tag = None

    def rewrite(self):
        self.feed(self.source)
        self.close()
        result = self.source
        for start, end, value in reversed(self.replacements):
            result = result[:start] + value + result[end:]
        return result


def generate_sql(route_rows, content_rows):
    mappings = build_route_map(route_rows)
    statements = []
    report = {'changedColumns': [], 'manualReview': [], 'scannedColumns': len(content_rows)}
    seen = set()
    for row in content_rows:
        table = row['tableName']
        column = row['columnName']
        if table not in ALLOWED_COLUMNS or column not in ALLOWED_COLUMNS[table]:
            raise ValueError('Table/column not in the migration whitelist')
        seq = positive_id(row['seq'], 'seq')
        conference = positive_id(row['conferenceSeq'], 'conferenceSeq')
        key = (table, seq, column)
        if key in seen:
            raise ValueError(f'Duplicate exported column: {key}')
        seen.add(key)
        source_hex = row['sourceHex']
        if not isinstance(source_hex, str) or not re.fullmatch(r'(?:[0-9a-fA-F]{2})*', source_hex):
            raise ValueError(f'Invalid sourceHex for {key}')
        raw = bytes.fromhex(source_hex)
        original = raw.decode('utf-8', errors='strict')
        mapping = mappings.get(conference, {})
        if column in {'linkUrl', 'routePath'}:
            rewritten, needs_review = rewrite_url(original, mapping)
            changes = [{'from': original, 'to': rewritten}] if rewritten != original else []
            review = [original] if needs_review else []
        else:
            parser = AnchorRewriter(original, mapping)
            rewritten = parser.rewrite()
            changes, review = parser.changes, parser.review
        identity = {'tableName': table, 'seq': seq, 'conferenceSeq': conference, 'columnName': column}
        if review:
            report['manualReview'].append({**identity, 'urls': review})
        if rewritten == original:
            continue
        report['changedColumns'].append({**identity, 'urls': changes})
        if table in {'menu_translations', 'menu_html_histories'}:
            scope = (f"EXISTS (SELECT 1 FROM menu_settings m WHERE m.seq=`{table}`.menuSeq "
                     f"AND m.conferenceSeq={conference} AND m.menuScope='user')")
        else:
            scope = f'conferenceSeq={conference}'
            if table == 'menu_settings':
                scope += " AND menuScope='user'"
                if column in {'linkUrl', 'routePath'}:
                    scope += " AND menuType='link'"
        target_hex = rewritten.encode('utf-8').hex()
        digest = hashlib.sha256(raw).hexdigest()
        statements.append(
            f'-- {table}.{column}, seq={seq}, conferenceSeq={conference}\n'
            f"UPDATE `{table}` SET `{column}`=CONVERT(X'{target_hex}' USING utf8mb4)\n"
            f"WHERE seq={seq} AND {scope}\n"
            f"  AND SHA2(CAST(`{column}` AS BINARY),256)='{digest}';\n"
            'SET @public_content_changed = @public_content_changed + ROW_COUNT();'
        )
    header = (
        '-- One-time offline content migration, MariaDB 10.6. No runtime path compatibility.\n'
        '-- Back up first. Keep application writes stopped until deployment is complete.\n'
        '-- Run this entire file in ONE session. Review report.manualReview before applying.\n'
        '-- No COMMIT is issued: commit only when changedColumns equals expectedColumns.\n'
        'START TRANSACTION;\n'
        f'SET @public_content_expected = {len(statements)};\n'
        'SET @public_content_changed = 0;\n\n'
    )
    footer = (
        '\n\nSELECT @public_content_expected AS expectedColumns, @public_content_changed AS changedColumns;\n'
        '-- If both counts match and the result was reviewed: COMMIT;\n'
        '-- If any original content changed since export, or a check failed: ROLLBACK;\n'
    )
    return header + '\n\n'.join(statements) + footer, report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--print-route-query', action='store_true')
    parser.add_argument('--print-content-query', action='store_true')
    parser.add_argument('--routes', type=Path)
    parser.add_argument('--content', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args(argv)
    if args.print_route_query:
        print(ROUTE_QUERY)
        return 0
    if args.print_content_query:
        print(CONTENT_QUERY)
        return 0
    if not all((args.routes, args.content, args.output, args.report)):
        parser.error('--routes, --content, --output and --report are required')
    try:
        sql, report = generate_sql(load_rows(args.routes), load_rows(args.content))
        args.output.write_text(sql, encoding='utf-8', newline='\n')
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    except (ValueError, KeyError, OSError) as exc:
        print(f'Migration generation failed: {exc}', file=sys.stderr)
        return 1
    print(f"Scanned {report['scannedColumns']} columns; changed {len(report['changedColumns'])}; manual review {len(report['manualReview'])}.")
    return 2 if report['manualReview'] else 0


if __name__ == '__main__':
    sys.exit(main())
