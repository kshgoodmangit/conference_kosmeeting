import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
    Bell,
    CalendarDays,
    ChevronDown,
    Layout,
    ListTree,
    Menu,
    Moon,
    Sun,
    X
} from 'lucide-react';
import { AbstractPage } from './components/AbstractPage';
import { MailHistoryPage } from './components/MailHistoryPage';
import { AbstractSimilarityReviewPage } from './components/AbstractSimilarityReviewPage';
import { AbstractEvaluationItemPage } from './components/AbstractEvaluationItemPage';
import { ReviewerReviewPage } from './components/ReviewerReviewPage';
import { ReviewerProfileModal } from './components/ReviewerProfileModal';
import { AdminDashboardPage } from './components/AdminDashboardPage';
import { AdminOperationStatusPage } from './components/AdminOperationStatusPage';
import { AdminShowcaseDashboardPage } from './components/AdminShowcaseDashboardPage';
import { AdminPreRegistrationDashboardPage } from './components/AdminPreRegistrationDashboardPage';
import { AdminAbstractDashboardPage } from './components/AdminAbstractDashboardPage';
import { AdminUserAnalyticsDashboardPage } from './components/AdminUserAnalyticsDashboardPage';
import { AdminUserAnalyticsDetailsPage } from './components/AdminUserAnalyticsDetailsPage';
import { AdminPage } from './components/AdminPage';
import { AdminIpAllowlistPage } from './components/AdminIpAllowlistPage';
import { AdminAccessRequestPage } from './components/AdminAccessRequestPage';
import { ExcelDownloadLogPage } from './components/ExcelDownloadLogPage';
import { AdminLoginPage, type AdminLoginData } from './components/AdminLoginPage';
import { AdminAccountDropdown } from './components/AdminAccountDropdown';
import { AdminPasswordChangeModal } from './components/AdminPasswordChangeModal';
import { BoardManagementPage } from './components/BoardManagementPage';
import { MaintenanceRequestPage } from './components/MaintenanceRequestPage';
import { ConferencePage } from './components/ConferencePage';
import { CountryPage } from './components/CountryPage';
import { CommonCodePage } from './components/CommonCodePage';
import { MemberPage } from './components/MemberPage';
import { FreeRecipientPage } from './components/FreeRecipientPage';
import { SocietyMemberPage } from './components/SocietyMemberPage';
import { MenuPage } from './components/MenuPage';
import { NotificationToast, type NotificationItem, type NotificationType } from './components/NotificationToast';
import { PopupPage } from './components/PopupPage';
import { PopupLayoutPage } from './components/PopupLayoutPage';
import { PreRegistrationManagementPage } from './components/PreRegistrationManagementPage';
import { SponsorPage } from './components/SponsorPage';
import { SpeakerPage } from './components/SpeakerPage';
import { SponsorshipPage } from './components/SponsorshipPage';
import { ProgramManagementPage } from './components/ProgramManagementPage';
import { AbstractProgramPage } from './components/AbstractProgramPage';
import { TestDataPage } from './components/TestDataPage';
import { PaymentTestPage } from './components/PaymentTestPage';
import { NAV_ICON_BY_KEY } from './menuIcons';
import {
    ADMIN_SESSION_EXPIRED_EVENT,
    getStoredAdminConferenceSeq,
    setSelectedAdminConferenceSeq
} from './adminSession';

const SmsPage = lazy(() => import('./components/SmsPage').then((module) => ({ default: module.SmsPage })));

const PromotionalMailPage = lazy(() => import('./components/PromotionalMailPage').then((module) => ({
    default: module.PromotionalMailPage
})));

interface AdminMenuNode {
    seq: number;
    menuScope: 'admin' | 'user';
    menuKey: string;
    parentKey?: string | null;
    menuName: string;
    routePath?: string | null;
    sortOrder: number;
    useStartDate?: string | null;
    useEndDate?: string | null;
    enabled: boolean;
    children?: AdminMenuNode[];
}

interface AdminNavRoute {
    menuKey: string;
    menuName: string;
    routePath: string;
}

interface AdminNavEntry extends AdminNavRoute {
    routeEnabled: boolean;
    children: AdminNavEntry[];
}

interface AdminCapabilities {
    conferenceCreationEnabled?: boolean;
    abstractSimilarityEnabled?: boolean;
    eventDashboardEnabled?: boolean;
    userAnalyticsDashboardEnabled?: boolean;
}

interface AdminConference {
    seq: number;
    eventName?: string | null;
    eventStartDate?: string | null;
    eventEndDate?: string | null;
}

const SUPPORTED_NAV_KEYS = new Set([
    'dashboard-overview',
    'dashboard-operation-status',
    'dashboard-board',
    'dashboard-registration',
    'dashboard-abstracts',
    'dashboard-user-analytics',
    'dashboard-user-analytics-details',
    'abstracts',
    'abstract-submissions',
    'oral-accepted-abstracts',
    'poster-accepted-abstracts',
    'abstract-similarity-reviews',
    'abstract-evaluation-items',
    'my-reviews',
    'members',
    'society-members',
    'free-recipients',
    'countries',
    'commoncode',
    'admin',
    'ip-allowlist',
    'access-requests',
    'excel-download-logs',
    'mail-history',
    'conference',
    'menu',
    'popup',
    'popup-layout',
    'pre-registrations',
    'program',
    'abstract-program',
    'sponsors',
    'speakers',
    'sponsorship',
    'promotional-mail',
    'sms',
    'notice',
    'resources',
    'faq',
    'maintenance-requests',
    'testdata',
    'payment-test'
]);

const REVIEWER_NAV_ENTRIES: AdminNavEntry[] = [{
    menuKey: 'my-reviews',
    menuName: '내 초록 심사',
    routePath: '/my-reviews',
    routeEnabled: true,
    children: []
}];

const MAINTENANCE_NAV_ENTRIES: AdminNavEntry[] = [{
    menuKey: 'admin-board',
    menuName: '관리자 게시판',
    routePath: '',
    routeEnabled: false,
    children: [{
        menuKey: 'maintenance-requests',
        menuName: '유지보수 요청',
        routePath: '/maintenance-requests',
        routeEnabled: true,
        children: []
    }]
}];

const DIRECT_ADMIN_ROUTES: AdminNavRoute[] = [
    { menuKey: 'mail-history', menuName: '메일발송이력', routePath: '/mail-history' },
    {
        menuKey: 'dashboard-user-analytics-details',
        menuName: '사용자 접속 통계',
        routePath: '/dashboard/user-analytics-details'
    },
    {
        menuKey: 'dashboard-user-analytics',
        menuName: '사용자 접속 현황판',
        routePath: '/dashboard/user-analytics'
    },
    {
        menuKey: 'dashboard-abstracts',
        menuName: '초록 현황',
        routePath: '/dashboard/abstracts'
    },
    {
        menuKey: 'dashboard-overview',
        menuName: '행사 운영',
        routePath: '/dashboard'
    },
    {
        menuKey: 'dashboard-registration',
        menuName: '사전등록 현황',
        routePath: '/dashboard/registration'
    },
    {
        menuKey: 'dashboard-board',
        menuName: '행사 현황판',
        routePath: '/dashboard/board'
    },
    {
        menuKey: 'abstract-program',
        menuName: '초록 편성',
        routePath: '/abstract-program'
    },
    {
        menuKey: 'testdata',
        menuName: '테스트 데이터',
        routePath: '/testdata'
    },
    {
        menuKey: 'payment-test',
        menuName: 'PG 결제 테스트',
        routePath: '/payment-test'
    }
];

const EVENT_DASHBOARD_MENU_KEY = 'dashboard-board';
const EVENT_DASHBOARD_ROUTE_PATH = '/dashboard/board';
const isFullAdministrator = (role?: string | null) => role === 'admin' || role === 'maintenance';

const isAdminPath = () => (
    window.location.pathname === '/admin'
    || window.location.pathname.startsWith('/admin/')
);

const normalizeAdminRoutePath = (routePath?: string | null) => {
    const value = routePath?.trim();
    if (!value) {
        return null;
    }

    let normalized = value.startsWith('/') ? value : `/${value}`;
    if (normalized.startsWith('/admin/')) {
        normalized = normalized.slice('/admin'.length);
    }

    return normalized.length > 1 ? normalized.replace(/\/+$/, '') : normalized;
};

const getAdminRoutePathFromLocation = () => {
    const routePath = normalizeAdminRoutePath(window.location.pathname.slice('/admin'.length));
    // 기존 북마크는 화면을 유지하면서 의미 있는 새 주소로 이동합니다.
    if (routePath === '/dashboard2') return '/dashboard/board';
    if (routePath === '/dashboard3') return '/dashboard/registration';
    return routePath;
};

const toAdminPath = (routePath: string) => (
    `/admin${routePath}`
);

const fetchAdminCapabilities = async (signal: AbortSignal): Promise<AdminCapabilities> => {
    try {
        const response = await fetch('/api/admin/conference-settings/capabilities', { signal });
        if (!response.ok) {
            return {};
        }

        return await response.json() as AdminCapabilities;
    } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') {
            throw error;
        }

        return {};
    }
};

export default function App() {
    const adminRoute = isAdminPath();

    const getInitialDarkMode = () => {
        const savedTheme = localStorage.getItem('theme');

        if (savedTheme === 'dark') {
            return true;
        }

        if (savedTheme === 'light') {
            return false;
        }

        return window.matchMedia('(prefers-color-scheme: dark)').matches;
    };

    const [darkMode, setDarkMode] = useState(getInitialDarkMode);
    const [isVertical, setIsVertical] = useState(true);
    const [currentNav, setCurrentNav] = useState('');
    const [adminUser, setAdminUser] = useState<AdminLoginData | null>(null);
    const [adminSessionChecked, setAdminSessionChecked] = useState(false);
    const [notifications, setNotifications] = useState<NotificationItem[]>([]);
    const [applicationName, setApplicationName] = useState('');
    const [adminConferences, setAdminConferences] = useState<AdminConference[]>([]);
    const [selectedConferenceSeq, setSelectedConferenceSeq] = useState<number | null>(null);
    const [adminConferencesLoaded, setAdminConferencesLoaded] = useState(false);
    const [adminNavEntries, setAdminNavEntries] = useState<AdminNavEntry[]>([]);
    const [adminMenusLoaded, setAdminMenusLoaded] = useState(false);
    const [conferenceCreationEnabled, setConferenceCreationEnabled] = useState(false);
    const [abstractSimilarityEnabled, setAbstractSimilarityEnabled] = useState(false);
    const [eventDashboardEnabled, setEventDashboardEnabled] = useState(false);
    const [userAnalyticsDashboardEnabled, setUserAnalyticsDashboardEnabled] = useState(false);
    const [expandedNavGroups, setExpandedNavGroups] = useState<Record<string, boolean>>({});
    const [menuReloadKey, setMenuReloadKey] = useState(0);
    const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
    const [isReviewerProfileOpen, setIsReviewerProfileOpen] = useState(false);
    const [isPasswordChangeOpen, setIsPasswordChangeOpen] = useState(false);
    const activeNavEntries = useMemo(
        () => adminUser?.role === 'reviewer'
            ? REVIEWER_NAV_ENTRIES
            : adminNavEntries,
        [adminUser?.role, adminNavEntries]
    );
    const activeMenusLoaded = adminUser?.role === 'reviewer' || adminMenusLoaded;

    const notificationIdRef = useRef(0);
    const notify = useCallback((type: NotificationType, message: string) => {
        const id = ++notificationIdRef.current;
        setNotifications((items) => [...items, { id, type, message }]);
        window.setTimeout(() => {
            setNotifications((items) => items.filter((item) => item.id !== id));
        }, 3500);
    }, []);

    const closeNotification = (id: number) => {
        setNotifications((items) => items.filter((item) => item.id !== id));
    };

    useEffect(() => {
        const controller = new AbortController();
        const loadApplicationInfo = async () => {
            try {
                const response = await fetch('/api/application-info', {
                    cache: 'no-store',
                    signal: controller.signal
                });
                if (!response.ok) throw new Error('애플리케이션 이름을 불러오지 못했습니다.');
                const data = await response.json() as { applicationName: string };
                setApplicationName(data.applicationName);
            } catch (error) {
                if (!controller.signal.aborted) {
                    notify('error', error instanceof Error ? error.message : '애플리케이션 이름을 불러오지 못했습니다.');
                }
            }
        };
        void loadApplicationInfo();
        return () => controller.abort();
    }, [notify]);

    useEffect(() => {
        localStorage.removeItem('adminToken');

        const handleSessionExpired = () => {
            setAdminUser(null);
            setAdminSessionChecked(true);
            setAdminNavEntries([]);
            setAdminMenusLoaded(false);
            setConferenceCreationEnabled(false);
            setAbstractSimilarityEnabled(false);
            setEventDashboardEnabled(false);

            setUserAnalyticsDashboardEnabled(false);
            setAdminConferences([]);
            setSelectedConferenceSeq(null);
            setSelectedAdminConferenceSeq(null);
            setAdminConferencesLoaded(false);
            setCurrentNav('');
            setExpandedNavGroups({});
            setNotifications([]);
            setIsReviewerProfileOpen(false);
            setIsPasswordChangeOpen(false);

            if (window.location.pathname !== '/admin' && window.location.pathname !== '/admin/access-requests') {
                window.history.replaceState(null, '', '/admin');
            }
        };

        window.addEventListener(ADMIN_SESSION_EXPIRED_EVENT, handleSessionExpired);

        return () => {
            window.removeEventListener(ADMIN_SESSION_EXPIRED_EVENT, handleSessionExpired);
        };
    }, []);

    useEffect(() => {
        if (!adminRoute) {
            return;
        }

        const abortController = new AbortController();

        const restoreAdminSession = async () => {
            try {
                const response = await fetch('/api/admin/session', {
                    signal: abortController.signal
                });

                if (!response.ok) {
                    throw new Error('Admin session is unavailable.');
                }

                const data = await response.json() as AdminLoginData;
                setAdminUser(data);
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }

                setAdminUser(null);
                if (window.location.pathname !== '/admin' && window.location.pathname !== '/admin/access-requests') {
                    window.history.replaceState(null, '', '/admin');
                }
            } finally {
                if (!abortController.signal.aborted) {
                    setAdminSessionChecked(true);
                }
            }
        };

        void restoreAdminSession();
        return () => abortController.abort();
    }, [adminRoute]);

    const changeNav = (nav: string) => {
        const nextRoute = flattenNavRoutes(activeNavEntries).find((item) => (
            item.menuKey === nav && isLicensedAdminRoute(item, abstractSimilarityEnabled, eventDashboardEnabled, userAnalyticsDashboardEnabled)
        ))
            ?? (isFullAdministrator(adminUser?.role)
                ? getLicensedDirectAdminRoutes(abstractSimilarityEnabled, eventDashboardEnabled, userAnalyticsDashboardEnabled).find((item) => item.menuKey === nav)
                : undefined);
        if (!nextRoute) {
            return;
        }

        const ancestorGroupKeys = findAncestorGroupKeys(activeNavEntries, nav) ?? [];
        setExpandedNavGroups(isVertical ? Object.fromEntries(
            ancestorGroupKeys.map((groupKey) => [groupKey, true])
        ) : {});
        setCurrentNav(nav);
        setIsMobileMenuOpen(false);
        window.history.pushState(null, '', toAdminPath(nextRoute.routePath));
    };

    const applyAdminConferences = useCallback((conferences: AdminConference[]) => {
        setAdminConferences(conferences);
        setSelectedConferenceSeq((previous) => {
            const stored = getStoredAdminConferenceSeq();
            const selected = conferences.find((conference) => conference.seq === previous)
                ?? conferences.find((conference) => conference.seq === stored)
                ?? conferences[0]
                ?? null;
            const nextSeq = selected?.seq ?? null;
            setSelectedAdminConferenceSeq(nextSeq);
            return nextSeq;
        });
        setAdminConferencesLoaded(true);
    }, []);

    const refreshConferences = async () => {
        try {
            const response = await fetch('/api/admin/conference-settings');
            if (!response.ok) throw new Error(await response.text() || '학회 목록을 불러오지 못했습니다.');
            applyAdminConferences(await response.json() as AdminConference[]);
        } catch (error) {
            setAdminConferencesLoaded(true);
            notify('error', error instanceof Error ? error.message : '학회 목록을 불러오지 못했습니다.');
        }
    };

    useEffect(() => {
        if (!adminRoute) {
            window.location.replace('/admin');
        }
    }, [adminRoute]);

    useEffect(() => {
        if (!adminUser) {
            return;
        }

        const abortController = new AbortController();

        const loadConferences = async () => {
            try {
                const response = await fetch(
                    isFullAdministrator(adminUser.role) ? '/api/admin/conference-settings' : '/api/conference-settings',
                    {
                    signal: abortController.signal
                    }
                );
                if (!response.ok) {
                    throw new Error(await response.text() || '학회 목록을 불러오지 못했습니다.');
                }

                if (isFullAdministrator(adminUser.role)) {
                    applyAdminConferences(await response.json() as AdminConference[]);
                } else {
                    const data = await response.json() as AdminConference;
                    const roleConferenceSeq = adminUser.conferenceSeq ?? data.seq ?? null;
                    setSelectedConferenceSeq(roleConferenceSeq);
                    setSelectedAdminConferenceSeq(roleConferenceSeq);
                    setAdminConferencesLoaded(true);
                }
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }
                setAdminConferencesLoaded(true);
                notify('error', error instanceof Error ? error.message : '학회 목록을 불러오지 못했습니다.');
            }
        };

        void loadConferences();
        return () => abortController.abort();
    }, [adminUser, applyAdminConferences, notify]);

    useEffect(() => {
        document.documentElement.classList.toggle('dark', darkMode);
        localStorage.setItem('theme', darkMode ? 'dark' : 'light');
    }, [darkMode]);

    useEffect(() => {
        if (!isMobileMenuOpen) {
            return;
        }

        const desktopMedia = window.matchMedia('(min-width: 768px)');
        const closeMobileMenu = () => setIsMobileMenuOpen(false);
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                closeMobileMenu();
            }
        };
        const handleDesktopChange = (event: MediaQueryListEvent) => {
            if (event.matches) {
                closeMobileMenu();
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        desktopMedia.addEventListener('change', handleDesktopChange);

        return () => {
            window.removeEventListener('keydown', handleKeyDown);
            desktopMedia.removeEventListener('change', handleDesktopChange);
        };
    }, [isMobileMenuOpen]);

    useEffect(() => {
        if (!adminRoute || !adminUser) {
            return;
        }

        const syncAdminLocation = () => {
            if (!isAdminPath()) {
                window.location.reload();
                return;
            }

            const routes = flattenNavRoutes(activeNavEntries);
            const requestedRoutePath = getAdminRoutePathFromLocation();
            const requestedRoute = findAdminRouteByPath(
                routes,
                requestedRoutePath,
                isFullAdministrator(adminUser.role),
                abstractSimilarityEnabled,
                eventDashboardEnabled,
                userAnalyticsDashboardEnabled
            );

            if (!requestedRoute && !activeMenusLoaded) {
                if (window.location.hash) {
                    window.history.replaceState(null, '', window.location.pathname);
                }
                return;
            }

            const nextRoute = requestedRoute ?? routes[0];
            if (!nextRoute) {
                if (window.location.hash) {
                    window.history.replaceState(null, '', window.location.pathname);
                }
                setCurrentNav('');
                return;
            }

            const canonicalPath = toAdminPath(nextRoute.routePath);
            if (window.location.pathname !== canonicalPath || window.location.hash) {
                window.history.replaceState(null, '', canonicalPath);
            }
            const ancestorGroupKeys = findAncestorGroupKeys(activeNavEntries, nextRoute.menuKey) ?? [];
            setExpandedNavGroups(isVertical ? Object.fromEntries(
                ancestorGroupKeys.map((groupKey) => [groupKey, true])
            ) : {});
            setCurrentNav(nextRoute.menuKey);
        };

        syncAdminLocation();
        window.addEventListener('popstate', syncAdminLocation);
        window.addEventListener('hashchange', syncAdminLocation);

        return () => {
            window.removeEventListener('popstate', syncAdminLocation);
            window.removeEventListener('hashchange', syncAdminLocation);
        };
    }, [abstractSimilarityEnabled, activeMenusLoaded, activeNavEntries, adminRoute, adminUser, eventDashboardEnabled, isVertical, userAnalyticsDashboardEnabled]);

    useEffect(() => {
        if (
            !adminRoute
            || !adminUser
            || !isFullAdministrator(adminUser.role)
            || !adminConferencesLoaded
            || selectedConferenceSeq === null
        ) {
            return;
        }

        const abortController = new AbortController();

        const fetchAdminMenus = async () => {
            setAdminMenusLoaded(false);
            setAbstractSimilarityEnabled(false);
            setEventDashboardEnabled(false);

            setUserAnalyticsDashboardEnabled(false);

            try {
                const [response, capabilities] = await Promise.all([
                    fetch('/api/admin/menu-settings/tree', {
                        signal: abortController.signal
                    }),
                    fetchAdminCapabilities(abortController.signal)
                ]);

                if (!response.ok) {
                    throw new Error(await response.text() || '관리자 메뉴를 불러오지 못했습니다.');
                }

                const data = await response.json() as AdminMenuNode[];
                const nextConferenceCreationEnabled = capabilities.conferenceCreationEnabled === true;
                const nextAbstractSimilarityEnabled = capabilities.abstractSimilarityEnabled === true;
                const nextEventDashboardEnabled = capabilities.eventDashboardEnabled === true;
                const nextUserAnalyticsDashboardEnabled = capabilities.userAnalyticsDashboardEnabled === true;
                const nextEntries = buildAdminNavEntries(
                    data,
                    nextAbstractSimilarityEnabled,
                    nextEventDashboardEnabled,
                    nextUserAnalyticsDashboardEnabled
                );
                const nextRoutes = flattenNavRoutes(nextEntries);

                setConferenceCreationEnabled(nextConferenceCreationEnabled);
                setAbstractSimilarityEnabled(nextAbstractSimilarityEnabled);
                setEventDashboardEnabled(nextEventDashboardEnabled);
                setUserAnalyticsDashboardEnabled(nextUserAnalyticsDashboardEnabled);
                setAdminNavEntries(nextEntries);
                setAdminMenusLoaded(true);
                const requestedRoutePath = getAdminRoutePathFromLocation();
                const requestedRoute = findAdminRouteByPath(
                    nextRoutes,
                    requestedRoutePath,
                    true,
                    nextAbstractSimilarityEnabled,
                    nextEventDashboardEnabled,
                    nextUserAnalyticsDashboardEnabled
                );
                const nextRoute = requestedRoute ?? nextRoutes[0];
                if (!nextRoute) {
                    setCurrentNav('');
                    setExpandedNavGroups({});
                    return;
                }

                setCurrentNav(nextRoute.menuKey);
                window.history.replaceState(null, '', toAdminPath(nextRoute.routePath));
            } catch (error) {
                if (error instanceof DOMException && error.name === 'AbortError') {
                    return;
                }

                const message = error instanceof Error ? error.message : '관리자 메뉴를 불러오지 못했습니다.';
                notify('error', message);
                setAdminNavEntries([]);
                setAdminMenusLoaded(true);
                setConferenceCreationEnabled(false);
                setAbstractSimilarityEnabled(false);
                setEventDashboardEnabled(false);

                setUserAnalyticsDashboardEnabled(false);
                setCurrentNav('');
                setExpandedNavGroups({});
            }
        };

        void fetchAdminMenus();
        return () => abortController.abort();
    }, [
        adminConferencesLoaded,
        adminRoute,
        adminUser,
        menuReloadKey,
        notify,
        selectedConferenceSeq
    ]);

    const toggleDarkMode = () => {
        setDarkMode((value) => !value);
    };

    const handleLogout = async () => {
        try {
            const response = await fetch('/api/admin/logout', { method: 'POST' });
            if (!response.ok) {
                throw new Error('로그아웃에 실패했습니다.');
            }

            setAdminUser(null);
            setAdminNavEntries([]);
            setAdminMenusLoaded(false);
            setConferenceCreationEnabled(false);
            setEventDashboardEnabled(false);

            setUserAnalyticsDashboardEnabled(false);
            setAdminConferences([]);
            setSelectedConferenceSeq(null);
            setSelectedAdminConferenceSeq(null);
            setAdminConferencesLoaded(false);
            setCurrentNav('');
            setExpandedNavGroups({});
            setIsReviewerProfileOpen(false);
            setIsPasswordChangeOpen(false);
            window.history.replaceState(null, '', '/admin');
        } catch (error) {
            notify('error', error instanceof Error ? error.message : '로그아웃에 실패했습니다.');
        }
    };

    const handleReviewerProfileSaved = (adminName: string) => {
        setAdminUser((current) => current ? { ...current, adminName } : current);
    };

    const handlePasswordChanged = () => {
        setIsPasswordChangeOpen(false);
        setIsReviewerProfileOpen(false);
        setAdminUser(null);
        setAdminNavEntries([]);
        setAdminMenusLoaded(false);
        setConferenceCreationEnabled(false);
        setEventDashboardEnabled(false);

        setUserAnalyticsDashboardEnabled(false);
        setAdminConferences([]);
        setSelectedConferenceSeq(null);
        setSelectedAdminConferenceSeq(null);
        setAdminConferencesLoaded(false);
        setCurrentNav('');
        setExpandedNavGroups({});
        window.history.replaceState(null, '', '/admin');
    };

    const toggleLayout = () => {
        setExpandedNavGroups({});
        setIsVertical((value) => !value);
    };

    const handleConferenceSelectionChange = (conferenceSeq: number) => {
        const selected = adminConferences.find((conference) => conference.seq === conferenceSeq);
        if (!selected) return;
        setSelectedConferenceSeq(selected.seq);
        setSelectedAdminConferenceSeq(selected.seq);
        setIsMobileMenuOpen(false);
    };

    const toggleNavGroup = (groupKey: string) => {
        setExpandedNavGroups((previous) => {
            const nextValue = !previous[groupKey];
            const ancestorGroupKeys = findAncestorGroupKeys(activeNavEntries, groupKey) ?? [];

            if (!nextValue) {
                return Object.fromEntries(
                    ancestorGroupKeys
                        .filter((ancestorKey) => previous[ancestorKey])
                        .map((ancestorKey) => [ancestorKey, true])
                );
            }

            return Object.fromEntries(
                [...ancestorGroupKeys, groupKey].map((expandedKey) => [expandedKey, true])
            );
        });
    };

    if (!adminRoute) {
        return null;
    }

    if (!adminSessionChecked) {
        return null;
    }

    if (!adminUser) {
        return (
            <>
                <NotificationToast items={notifications} onClose={closeNotification} />
                <AdminLoginPage applicationName={applicationName} onSuccess={setAdminUser} homeHref="/" onNotify={notify} />
            </>
        );
    }

    return (
        <div className={`grid min-h-screen bg-slate-50 text-slate-900 transition-colors duration-200 dark:bg-slate-900 dark:text-slate-50 ${
            isVertical
                ? 'grid-cols-1 grid-rows-[64px_1fr] md:grid-cols-[auto_1fr]'
                : 'grid-cols-1 grid-rows-[64px_1fr] md:grid-rows-[64px_54px_1fr]'
        }`}>
            <NotificationToast items={notifications} onClose={closeNotification} />
            {isReviewerProfileOpen && (
                <ReviewerProfileModal
                    isOpen
                    onClose={() => setIsReviewerProfileOpen(false)}
                    onSaved={handleReviewerProfileSaved}
                    onNotify={notify}
                />
            )}
            {isPasswordChangeOpen && (
                <AdminPasswordChangeModal
                    isOpen
                    onClose={() => setIsPasswordChangeOpen(false)}
                    onSuccess={handlePasswordChanged}
                    onNotify={notify}
                />
            )}

            <header className="col-span-full z-40 flex h-16 items-center justify-between border-b border-slate-200 bg-white px-4 dark:border-slate-800 dark:bg-slate-950 md:px-6">
                <div className="flex min-w-0 items-center gap-3">
                    <button
                        type="button"
                        onClick={() => setIsMobileMenuOpen((open) => !open)}
                        className="-ml-2 rounded-lg p-2 text-slate-500 transition-colors hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800 md:hidden"
                        aria-label={isMobileMenuOpen ? 'Close admin menu' : 'Open admin menu'}
                        aria-controls="admin-navigation"
                        aria-expanded={isMobileMenuOpen}
                    >
                        {isMobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
                    </button>
                    <img
                        src="/images/admin-branding/logo-v3.png"
                        alt="ICMS"
                        width={32}
                        height={32}
                        className="h-8 w-8 shrink-0 rounded object-contain"
                    />
                    <span className="truncate text-base font-bold tracking-tight md:text-lg" title={applicationName}>{applicationName}</span>
                    <span className="hidden rounded bg-blue-50 px-2 py-0.5 text-[10px] font-medium text-blue-600 dark:bg-blue-950/50 dark:text-blue-400 sm:inline md:ml-2 md:text-xs">
                        {adminRoleLabel(adminUser.role)}
                    </span>
                </div>

                <div className="flex min-w-0 items-center gap-1 sm:gap-3">
                    {isFullAdministrator(adminUser.role) && conferenceCreationEnabled && (
                        <label className="flex min-w-0 items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-2 py-1.5 dark:border-slate-800 dark:bg-slate-900">
                            <CalendarDays className="hidden h-4 w-4 shrink-0 text-blue-500 sm:block" />
                            <span className="sr-only">관리 학회 선택</span>
                            <select
                                aria-label="관리 학회 선택"
                                value={selectedConferenceSeq ?? ''}
                                onChange={(event) => handleConferenceSelectionChange(Number(event.target.value))}
                                disabled={!adminConferencesLoaded || adminConferences.length === 0}
                                className="min-w-0 max-w-44 bg-transparent text-xs font-semibold text-slate-700 outline-none disabled:text-slate-400 dark:text-slate-200 dark:disabled:text-slate-500 sm:max-w-64"
                            >
                                {adminConferences.length === 0 && <option value="">등록된 학회 없음</option>}
                                {adminConferences.map((conference) => (
                                    <option key={conference.seq} value={conference.seq}>
                                        #{conference.seq} {conference.eventName?.trim() || '이름 없는 학회'}
                                    </option>
                                ))}
                            </select>
                        </label>
                    )}
                    <button
                        onClick={toggleLayout}
                        className="hidden rounded-lg p-2 text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800 md:block"
                        title="레이아웃 전환"
                    >
                        <Layout className="h-5 w-5" />
                    </button>
                    <button
                        onClick={toggleDarkMode}
                        className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800"
                    >
                        {darkMode ? <Sun className="h-5 w-5 text-amber-400" /> : <Moon className="h-5 w-5" />}
                    </button>
                    <button className="relative rounded-lg p-2 text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800">
                        <Bell className="h-5 w-5" />
                        <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-rose-500" />
                    </button>
                    <AdminAccountDropdown
                        adminUser={adminUser}
                        onEditProfile={adminUser.role === 'reviewer' ? () => setIsReviewerProfileOpen(true) : undefined}
                        onChangePassword={() => setIsPasswordChangeOpen(true)}
                        onLogout={() => void handleLogout()}
                    />
                </div>
            </header>

            {isMobileMenuOpen && (
                <button
                    type="button"
                    onClick={() => setIsMobileMenuOpen(false)}
                    className="fixed inset-x-0 bottom-0 top-16 z-20 bg-slate-950/45 backdrop-blur-[1px] md:hidden"
                    aria-label="Close admin menu"
                />
            )}

            <nav
                id="admin-navigation"
                className={`fixed bottom-0 left-0 top-16 z-30 w-72 overflow-y-auto border-r border-slate-200 bg-white p-4 transition-transform duration-200 ease-out dark:border-slate-800 dark:bg-slate-950 md:static md:z-10 md:translate-x-0 md:overflow-visible md:transition-all ${
                    isMobileMenuOpen ? 'translate-x-0 shadow-2xl' : '-translate-x-full'
                } ${
                    isVertical
                        ? 'md:w-72 md:border-b-0 md:border-r md:p-4'
                        : 'md:flex md:h-[54px] md:w-full md:items-center md:border-b md:border-r-0 md:px-6 md:py-0'
                }`}
            >
                <ul className={`flex w-full flex-col gap-1.5 ${isVertical ? '' : 'md:flex-row md:gap-3 md:whitespace-nowrap'}`}>
                    {activeNavEntries.map((entry) => {
                        return (
                            <AdminNavItem
                                key={entry.menuKey}
                                entry={entry}
                                currentNav={currentNav}
                                isVertical={isVertical}
                                expandedNavGroups={expandedNavGroups}
                                onToggle={toggleNavGroup}
                                onChangeNav={changeNav}
                                onCloseHorizontalMenus={() => setExpandedNavGroups({})}
                                depth={0}
                            />
                        );
                    })}
                </ul>
            </nav>

            <main className={`max-h-[calc(100vh-64px)] space-y-6 overflow-y-auto p-4 md:p-6 ${
                isVertical ? '' : 'md:max-h-[calc(100vh-118px)]'
            }`}>
                {!adminConferencesLoaded ? (
                    <div className="rounded-xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500 shadow-sm dark:border-slate-800 dark:bg-slate-950 dark:text-slate-400">
                        학회 목록을 불러오는 중입니다.
                    </div>
                ) : isFullAdministrator(adminUser.role) && selectedConferenceSeq === null ? (
                    <div className="rounded-xl border border-amber-200 bg-amber-50 p-8 text-center text-sm text-amber-800 shadow-sm dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-200">
                        관리할 학회를 먼저 등록하거나 선택해 주세요.
                    </div>
                ) : (
                <div key={isFullAdministrator(adminUser.role) ? selectedConferenceSeq : adminUser.role} className="contents">
                {currentNav === 'dashboard-overview' && (
                    <AdminDashboardPage onNavigate={changeNav} />
                )}
                {currentNav === 'dashboard-operation-status' && <AdminOperationStatusPage />}
                {currentNav === EVENT_DASHBOARD_MENU_KEY && eventDashboardEnabled && <AdminShowcaseDashboardPage />}
                {currentNav === 'dashboard-registration' && <AdminPreRegistrationDashboardPage onNavigate={changeNav} onNotify={notify} />}
                {currentNav === 'dashboard-abstracts' && <AdminAbstractDashboardPage onNavigate={changeNav} onNotify={notify} />}
                {currentNav === 'dashboard-user-analytics' && userAnalyticsDashboardEnabled && <AdminUserAnalyticsDashboardPage conferenceSeq={selectedConferenceSeq} onNotify={notify} />}
                {currentNav === 'dashboard-user-analytics-details' && <AdminUserAnalyticsDetailsPage dashboardEnabled={userAnalyticsDashboardEnabled} conferenceSeq={selectedConferenceSeq} onNotify={notify} />}

                {currentNav === 'abstracts' && <AbstractPage view="submissions" onNotify={notify} />}
                {currentNav === 'abstract-submissions' && <AbstractPage view="submissions" onNotify={notify} />}
                {currentNav === 'oral-accepted-abstracts' && <AbstractPage view="oral-accepted" onNotify={notify} />}
                {currentNav === 'poster-accepted-abstracts' && <AbstractPage view="poster-accepted" onNotify={notify} />}
                {currentNav === 'abstract-similarity-reviews' && abstractSimilarityEnabled && (
                    <AbstractSimilarityReviewPage key={selectedConferenceSeq} onNotify={notify} />
                )}
                {currentNav === 'my-reviews' && adminUser.role === 'reviewer' && <ReviewerReviewPage onNotify={notify} />}
                {currentNav === 'abstract-evaluation-items' && <AbstractEvaluationItemPage onNotify={notify} />}
                {currentNav === 'members' && <MemberPage onNotify={notify} />}
                {currentNav === 'society-members' && <SocietyMemberPage onNotify={notify} />}
                {currentNav === 'free-recipients' && <FreeRecipientPage onNotify={notify} />}
                {currentNav === 'countries' && <CountryPage onNotify={notify} />}
                {currentNav === 'commoncode' && <CommonCodePage onNotify={notify} />}
                {currentNav === 'admin' && <AdminPage onNotify={notify} />}
                {currentNav === 'ip-allowlist' && <AdminIpAllowlistPage onNotify={notify} />}
                {currentNav === 'access-requests' && <AdminAccessRequestPage onNotify={notify} />}
                {currentNav === 'excel-download-logs' && <ExcelDownloadLogPage onNotify={notify} />}
                {currentNav === 'conference' && <ConferencePage onNotify={notify} onSaved={refreshConferences} />}
                {currentNav === 'menu' && <MenuPage onNotify={notify} onSaved={() => setMenuReloadKey((value) => value + 1)} />}
                {currentNav === 'popup' && <PopupPage onNotify={notify} />}
                {currentNav === 'popup-layout' && <PopupLayoutPage onNotify={notify} />}
                {currentNav === 'pre-registrations' && <PreRegistrationManagementPage onNotify={notify} />}
                {currentNav === 'mail-history' && <MailHistoryPage onNotify={notify} />}
                {currentNav === 'program' && <ProgramManagementPage onNotify={notify} />}
                {currentNav === 'abstract-program' && <AbstractProgramPage onNotify={notify} onOpenProgram={() => changeNav('program')} />}
                {currentNav === 'sponsors' && <SponsorPage onNotify={notify} />}
                {currentNav === 'speakers' && <SpeakerPage onNotify={notify} />}
                {currentNav === 'sponsorship' && <SponsorshipPage onNotify={notify} />}
                {currentNav === 'testdata' && <TestDataPage dashboardEnabled={userAnalyticsDashboardEnabled} key={selectedConferenceSeq} onNotify={notify} />}
                {currentNav === 'payment-test' && <PaymentTestPage onNotify={notify} />}
                {currentNav === 'notice' && <BoardManagementPage boardSeq={1} boardCode="NOTICE" boardName="공지사항" onNotify={notify} />}
                {currentNav === 'resources' && <BoardManagementPage boardSeq={2} boardCode="RESOURCE" boardName="자료실" onNotify={notify} />}
                {currentNav === 'faq' && <BoardManagementPage boardSeq={3} boardCode="FAQ" boardName="FAQ" onNotify={notify} />}
                {currentNav === 'maintenance-requests' && <MaintenanceRequestPage onNotify={notify} />}
                {currentNav === 'sms' && (
                    <Suspense fallback={<div className="p-5 text-sm text-slate-500 dark:text-slate-400">문자발송 화면을 불러오는 중입니다.</div>}>
                        <SmsPage onNotify={notify} />
                    </Suspense>
                )}
                {currentNav === 'promotional-mail' && (
                    <Suspense fallback={<div className="rounded-xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500 dark:border-slate-800 dark:bg-slate-950">메일발송 화면을 불러오는 중입니다.</div>}>
                        <PromotionalMailPage onNotify={notify} />
                    </Suspense>
                )}
                </div>
                )}
            </main>

        </div>
    );
}

const buildAdminNavEntries = (
    nodes: AdminMenuNode[],
    abstractSimilarityEnabled: boolean,
    eventDashboardEnabled: boolean,
    userAnalyticsDashboardEnabled: boolean
): AdminNavEntry[] => {
    const adminRoots = nodes.filter((node) => node.menuScope === 'admin');
    const adminRoot = adminRoots.find((node) => node.menuKey === 'root');
    const sourceNodes = sortMenuNodes(adminRoot?.children?.length
        ? adminRoot.children
        : flattenAdminMenuNodes(adminRoots).filter((node) => node.menuKey !== 'root'));

    const entries = sourceNodes
        .map((node) => buildAdminNavEntry(
            node,
            abstractSimilarityEnabled,
            eventDashboardEnabled,
            userAnalyticsDashboardEnabled
        ))
        .filter((entry): entry is AdminNavEntry => entry !== null);

    return addMaintenanceRequestRoute(addFrontendDashboardRoutes(entries, userAnalyticsDashboardEnabled));
};

const addMaintenanceRequestRoute = (entries: AdminNavEntry[]): AdminNavEntry[] => {
    const maintenanceEntry: AdminNavEntry = {
        menuKey: 'maintenance-requests',
        menuName: '유지보수 요청',
        routePath: '/maintenance-requests',
        routeEnabled: true,
        children: []
    };
    let boardGroupFound = false;
    const addToBoardGroup = (items: AdminNavEntry[]): AdminNavEntry[] => items.map((entry) => {
        const isBoardGroup = entry.menuName.includes('관리자 게시판')
            || entry.children.some((child) => ['notice', 'resources', 'faq'].includes(child.menuKey));
        if (isBoardGroup) {
            boardGroupFound = true;
            return entry.children.some((child) => child.menuKey === maintenanceEntry.menuKey)
                ? entry
                : { ...entry, children: [...entry.children, maintenanceEntry] };
        }
        return entry.children.length > 0 ? { ...entry, children: addToBoardGroup(entry.children) } : entry;
    });
    const nextEntries = addToBoardGroup(entries);
    return boardGroupFound ? nextEntries : [...nextEntries, ...MAINTENANCE_NAV_ENTRIES];
};

const adminRoleLabel = (role: string) => role === 'reviewer'
    ? 'Reviewer'
    : 'Admin';

const addFrontendDashboardRoutes = (entries: AdminNavEntry[], userAnalyticsDashboardEnabled: boolean): AdminNavEntry[] => {
    const analyticsEntry: AdminNavEntry = {
        menuKey: 'dashboard-user-analytics',
        menuName: '사용자 접속 현황판',
        routePath: '/dashboard/user-analytics',
        routeEnabled: true,
        children: []
    };
    const detailEntry: AdminNavEntry = {
        menuKey: 'dashboard-user-analytics-details', menuName: '사용자 접속 통계',
        routePath: '/dashboard/user-analytics-details', routeEnabled: true, children: []
    };
    const analyticsEntries = userAnalyticsDashboardEnabled ? [analyticsEntry, detailEntry] : [detailEntry];
    const frontendEntries = [...analyticsEntries];
    let dashboardGroupFound = false;

    const addToDashboardGroup = (items: AdminNavEntry[]): AdminNavEntry[] => items.map((entry) => {
        if (entry.menuKey === 'dashboard') {
            dashboardGroupFound = true;
            const configuredChildren = entry.children.map((child) => {
                const frontendEntry = frontendEntries.find((item) => item.menuKey === child.menuKey);
                return frontendEntry ? { ...child, menuName: frontendEntry.menuName } : child;
            });
            const children = [...configuredChildren];
            children.push(...analyticsEntries.filter((item) => !children.some((child) => child.menuKey === item.menuKey)));
            return { ...entry, children };
        }

        return entry.children.length > 0
            ? { ...entry, children: addToDashboardGroup(entry.children) }
            : entry;
    });

    const nextEntries = addToDashboardGroup(entries);
    return dashboardGroupFound ? nextEntries : [...frontendEntries, ...nextEntries];
};

const flattenAdminMenuNodes = (nodes: AdminMenuNode[]): AdminMenuNode[] => {
    return nodes.flatMap((node) => [node, ...flattenAdminMenuNodes(node.children ?? [])]);
};

const flattenNavRoutes = (entries: AdminNavEntry[]): AdminNavRoute[] => {
    return entries.flatMap((entry) => [
        ...(entry.routeEnabled ? [{ menuKey: entry.menuKey, menuName: entry.menuName, routePath: entry.routePath }] : []),
        ...flattenNavRoutes(entry.children)
    ]);
};

const findAdminRouteByPath = (
    routes: AdminNavRoute[],
    routePath: string | null,
    includeDirectRoutes: boolean,
    abstractSimilarityEnabled: boolean,
    eventDashboardEnabled: boolean,
    userAnalyticsDashboardEnabled: boolean
) => {
    const configuredRoute = routes.find((item) => (
        item.routePath === routePath && isLicensedAdminRoute(
            item,
            abstractSimilarityEnabled,
            eventDashboardEnabled,
            userAnalyticsDashboardEnabled
        )
    ));
    if (configuredRoute || !includeDirectRoutes) {
        return configuredRoute;
    }

    return getLicensedDirectAdminRoutes(
        abstractSimilarityEnabled,
        eventDashboardEnabled,
        userAnalyticsDashboardEnabled
    ).find((item) => item.routePath === routePath);
};

const isEventDashboardRoute = (route: Pick<AdminNavRoute, 'menuKey' | 'routePath'>) => (
    route.menuKey === EVENT_DASHBOARD_MENU_KEY || route.routePath === EVENT_DASHBOARD_ROUTE_PATH
);

const isLicensedAdminRoute = (
    route: Pick<AdminNavRoute, 'menuKey' | 'routePath'>,
    abstractSimilarityEnabled: boolean,
    eventDashboardEnabled: boolean,
    userAnalyticsDashboardEnabled: boolean
) => (eventDashboardEnabled || !isEventDashboardRoute(route))
    && (abstractSimilarityEnabled || (
        route.menuKey !== 'abstract-similarity-reviews'
        && route.routePath !== '/abstract-similarity-reviews'
    ))
    && (userAnalyticsDashboardEnabled || (route.menuKey !== 'dashboard-user-analytics' && route.routePath !== '/dashboard/user-analytics'));

const getLicensedDirectAdminRoutes = (
    abstractSimilarityEnabled: boolean,
    eventDashboardEnabled: boolean,
    userAnalyticsDashboardEnabled: boolean
) => (
    DIRECT_ADMIN_ROUTES.filter((route) => isLicensedAdminRoute(
        route,
        abstractSimilarityEnabled,
        eventDashboardEnabled,
        userAnalyticsDashboardEnabled
    ))
);

const sortMenuNodes = <T extends { sortOrder: number; seq: number }>(nodes: T[]): T[] => {
    return [...nodes].sort((left, right) => left.sortOrder - right.sortOrder || left.seq - right.seq);
};

const findAncestorGroupKeys = (entries: AdminNavEntry[], menuKey: string): string[] | null => {
    for (const entry of entries) {
        if (entry.menuKey === menuKey) {
            return [];
        }

        const descendantPath = findAncestorGroupKeys(entry.children, menuKey);
        if (descendantPath !== null) {
            return [entry.menuKey, ...descendantPath];
        }
    }

    return null;
};

const isVisibleAdminMenu = (node: AdminMenuNode) => {
    if (node.menuScope !== 'admin' || node.enabled === false) {
        return false;
    }

    const today = new Date().toISOString().slice(0, 10);
    if (node.useStartDate && node.useStartDate > today) {
        return false;
    }

    if (node.useEndDate && node.useEndDate < today) {
        return false;
    }

    return true;
};

const buildAdminNavEntry = (
    node: AdminMenuNode,
    abstractSimilarityEnabled: boolean,
    eventDashboardEnabled: boolean,
    userAnalyticsDashboardEnabled: boolean
): AdminNavEntry | null => {
    if (!isVisibleAdminMenu(node)) {
        return null;
    }

    const children = sortMenuNodes(node.children ?? [])
        .map((child) => buildAdminNavEntry(
            child,
            abstractSimilarityEnabled,
            eventDashboardEnabled,
            userAnalyticsDashboardEnabled
        ))
        .filter((entry): entry is AdminNavEntry => entry !== null);
    const routeKey = node.menuKey;
    const routePath = normalizeAdminRoutePath(node.routePath);
    const routeEnabled = SUPPORTED_NAV_KEYS.has(routeKey) && routePath !== null;

    if ((!eventDashboardEnabled && routeKey === EVENT_DASHBOARD_MENU_KEY) || (!userAnalyticsDashboardEnabled && routeKey === 'dashboard-user-analytics')) {
        return null;
    }

    if (routePath !== null && !isLicensedAdminRoute(
        { menuKey: routeKey, routePath },
        abstractSimilarityEnabled,
        eventDashboardEnabled,
        userAnalyticsDashboardEnabled
    )) {
        return null;
    }

    if (!routeEnabled && children.length === 0) {
        return null;
    }

    return {
        menuKey: routeEnabled ? routeKey : node.menuKey,
        menuName: routeKey === 'dashboard-operation-status' ? '홈페이지 상태모니터링' : node.menuName,
        routePath: routePath ?? '',
        routeEnabled,
        children
    };
};

interface AdminNavItemProps {
    entry: AdminNavEntry;
    currentNav: string;
    isVertical: boolean;
    expandedNavGroups: Record<string, boolean>;
    onToggle: (groupKey: string) => void;
    onChangeNav: (menuKey: string) => void;
    onCloseHorizontalMenus: () => void;
    depth: number;
}

const AdminNavItem = ({
    entry,
    currentNav,
    isVertical,
    expandedNavGroups,
    onToggle,
    onChangeNav,
    onCloseHorizontalMenus,
    depth
}: AdminNavItemProps) => {
    const menuRef = useRef<HTMLLIElement>(null);
    const Icon = depth === 0 ? (NAV_ICON_BY_KEY[entry.menuKey] ?? ListTree) : null;
    const hasChildren = entry.children.length > 0;
    const expanded = expandedNavGroups[entry.menuKey] ?? false;
    const isActive = currentNav === entry.menuKey || hasDescendantRoute(entry.children, currentNav);

    useEffect(() => {
        if (isVertical || depth !== 0 || !hasChildren || !expanded) return;

        const handleOutsidePointerDown = (event: PointerEvent) => {
            if (!window.matchMedia('(min-width: 768px)').matches) return;
            if (event.target instanceof Node && !menuRef.current?.contains(event.target)) {
                onCloseHorizontalMenus();
            }
        };

        document.addEventListener('pointerdown', handleOutsidePointerDown, true);
        return () => document.removeEventListener('pointerdown', handleOutsidePointerDown, true);
    }, [depth, expanded, hasChildren, isVertical, onCloseHorizontalMenus]);

    return (
        <li ref={menuRef} className={isVertical || depth > 0 ? '' : 'relative'}>
            <div className="flex items-center gap-1">
                {entry.routeEnabled ? (
                    <a
                        href={toAdminPath(entry.routePath)}
                        onClick={(event) => {
                            event.preventDefault();
                            onChangeNav(entry.menuKey);
                            if (!isVertical) {
                                onCloseHorizontalMenus();
                            }
                        }}
                        className={`flex flex-1 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                            isActive && currentNav === entry.menuKey
                                ? 'bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-400'
                                : 'text-slate-500 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-slate-100'
                        }`}
                    >
                        {Icon ? <Icon className="h-5 w-5" /> : <span className="h-1.5 w-1.5 rounded-full bg-current opacity-60" />}
                        <span className="flex-1 text-left">{entry.menuName}</span>
                    </a>
                ) : (
                    <button
                        type="button"
                        onClick={() => onToggle(entry.menuKey)}
                        aria-expanded={expanded}
                        className={`flex flex-1 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                        expanded
                            ? 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-200'
                            : isActive
                                ? 'text-blue-600 hover:bg-slate-100 dark:text-blue-400 dark:hover:bg-slate-800'
                                : 'text-slate-500 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-slate-100'
                    }`}>
                        {Icon ? <Icon className="h-5 w-5" /> : <span className="h-1.5 w-1.5 rounded-full bg-current opacity-60" />}
                        <span className="flex-1 text-left">{entry.menuName}</span>
                        <ChevronDown className={`h-4 w-4 transition-transform ${expanded ? 'rotate-180 text-blue-600 dark:text-blue-400' : ''}`} />
                    </button>
                )}

                {entry.routeEnabled && hasChildren && (
                    <button
                        type="button"
                        onClick={() => onToggle(entry.menuKey)}
                        className={`rounded-lg p-2 transition-colors ${
                            expanded
                                ? 'bg-slate-100 text-blue-600 dark:bg-slate-800 dark:text-blue-400'
                                : isActive
                                    ? 'text-blue-600 hover:bg-slate-100 dark:text-blue-400 dark:hover:bg-slate-800'
                                    : 'text-slate-500 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-slate-100'
                        }`}
                    >
                        <ChevronDown className={`h-4 w-4 transition-transform ${expanded ? 'rotate-180' : ''}`} />
                    </button>
                )}
            </div>

            {hasChildren && expanded && (
                <ul className={`${
                    isVertical || depth > 0
                        ? 'mt-1 space-y-1 border-l border-slate-200 pl-4 dark:border-slate-800'
                        : 'absolute left-0 top-full z-20 mt-2 min-w-[240px] space-y-1 rounded-xl border border-slate-200 bg-white p-2 shadow-lg dark:border-slate-800 dark:bg-slate-950'
                }`}>
                    {entry.children.map((child) => (
                        <AdminNavItem
                            key={child.menuKey}
                            entry={child}
                            currentNav={currentNav}
                            isVertical={isVertical}
                            expandedNavGroups={expandedNavGroups}
                            onToggle={onToggle}
                            onChangeNav={onChangeNav}
                            onCloseHorizontalMenus={onCloseHorizontalMenus}
                            depth={depth + 1}
                        />
                    ))}
                </ul>
            )}
        </li>
    );
};

const hasDescendantRoute = (entries: AdminNavEntry[], menuKey: string): boolean => {
    return entries.some((entry) => entry.menuKey === menuKey || hasDescendantRoute(entry.children, menuKey));
};
