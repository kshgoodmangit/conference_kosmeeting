import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

// Exercise App's actual navigation helpers without mounting the entire application.
const source = ts.createSourceFile('App.tsx', readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
const names = new Set(['SUPPORTED_NAV_KEYS', 'DIRECT_ADMIN_ROUTES', 'EVENT_DASHBOARD_MENU_KEY',
    'EVENT_DASHBOARD_ROUTE_PATH', 'normalizeAdminRoutePath', 'buildAdminNavEntries',
    'addFrontendDashboardRoutes', 'flattenAdminMenuNodes', 'flattenNavRoutes', 'findAdminRouteByPath',
    'isEventDashboardRoute', 'isLicensedAdminRoute', 'getLicensedDirectAdminRoutes',
    'sortMenuNodes', 'isVisibleAdminMenu', 'buildAdminNavEntry']);
const helpers = source.statements.filter(statement => ts.isVariableStatement(statement)
    && statement.declarationList.declarations.some(declaration => names.has(declaration.name.getText(source))))
    .map(statement => statement.getText(source)).join('\n');
const code = ts.transpileModule(helpers, {compilerOptions: {target: ts.ScriptTarget.ES2023}}).outputText;
const api = vm.runInNewContext(`${code}\n({buildAdminNavEntries, flattenNavRoutes, findAdminRouteByPath})`);
const node = (menuKey, routePath, children = []) => ({menuKey, routePath, children,
    menuScope: 'admin', menuName: menuKey, enabled: true, sortOrder: 0, seq: 1});
const eventPath = '/dashboard/board';
const trafficPath = '/dashboard/user-analytics';
const detailsPath = '/dashboard/user-analytics-details';

for (const eventEnabled of [false, true]) for (const trafficEnabled of [false, true]) {
    test(`independent menu and URL access: event=${eventEnabled}, traffic=${trafficEnabled}`, () => {
        for (const configuredTraffic of [false, true]) {
            const children = [node('dashboard-board', eventPath)];
            if (configuredTraffic) children.push(node('dashboard-user-analytics', trafficPath));
            const routes = api.flattenNavRoutes(api.buildAdminNavEntries([
                node('dashboard', '/dashboard', children)
            ], eventEnabled, trafficEnabled));
            assert.equal(routes.some(route => route.routePath === eventPath), eventEnabled);
            assert.equal(routes.some(route => route.routePath === trafficPath), trafficEnabled);
            assert.equal(routes.some(route => route.routePath === detailsPath), true);
            for (const configured of [routes, []]) {
                assert.equal(Boolean(api.findAdminRouteByPath(configured, trafficPath, true, eventEnabled, trafficEnabled)), trafficEnabled);
                assert.equal(Boolean(api.findAdminRouteByPath(configured, detailsPath, true, eventEnabled, trafficEnabled)), true);
            }
        }
    });
}

test('missing capability denies traffic dashboard, including menu fallback without a dashboard group', () => {
    const routes = api.flattenNavRoutes(api.buildAdminNavEntries([], true, undefined));
    assert.equal(routes.some(route => route.routePath === trafficPath), false);
    assert.equal(routes.some(route => route.routePath === detailsPath), true);
    assert.equal(api.findAdminRouteByPath([], trafficPath, true, true, undefined), undefined);
});
