export const ADMIN_SESSION_EXPIRED_EVENT = 'admin-session-expired';
export const ADMIN_CONFERENCE_HEADER = 'X-Conference-Seq';
export const ADMIN_CONFERENCE_STORAGE_KEY = 'adminConferenceSeq';
const CSRF_TOKEN_URL = '/api/security/csrf-token';
const CSRF_ERROR_HEADER = 'X-CSRF-ERROR';
const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

interface CsrfTokenResponse {
    headerName: string;
    token: string;
}

let csrfToken: CsrfTokenResponse | null = null;
let csrfTokenRequest: Promise<CsrfTokenResponse> | null = null;
let selectedAdminConferenceSeq: number | null = null;

export const getStoredAdminConferenceSeq = () => {
    const value = sessionStorage.getItem(ADMIN_CONFERENCE_STORAGE_KEY);
    return value === null ? null : Number(value);
};

export const setSelectedAdminConferenceSeq = (conferenceSeq: number | null) => {
    selectedAdminConferenceSeq = conferenceSeq;
    if (conferenceSeq === null) {
        sessionStorage.removeItem(ADMIN_CONFERENCE_STORAGE_KEY);
    } else {
        sessionStorage.setItem(ADMIN_CONFERENCE_STORAGE_KEY, String(conferenceSeq));
    }
};

const isAdminAuthenticationRequest = (input: RequestInfo | URL) => {
    const requestUrl = input instanceof Request ? input.url : input.toString();
    const url = new URL(requestUrl, window.location.origin);

    return url.origin === window.location.origin
        && url.pathname.startsWith('/api/')
        && url.pathname !== '/api/admin/login';
};

const requestUrl = (input: RequestInfo | URL) => {
    const value = input instanceof Request ? input.url : input.toString();
    return new URL(value, window.location.origin);
};

const requestMethod = (input: RequestInfo | URL, init?: RequestInit) => (
    (init?.method ?? (input instanceof Request ? input.method : 'GET')).toUpperCase()
);

const isSameOriginApiRequest = (input: RequestInfo | URL) => {
    const url = requestUrl(input);
    return url.origin === window.location.origin && url.pathname.startsWith('/api/');
};

const isConferenceScopedApiRequest = (input: RequestInfo | URL) => {
    const url = requestUrl(input);
    return url.origin === window.location.origin
        && (url.pathname.startsWith('/api/admin/') || url.pathname.startsWith('/api/maintenance/'));
};

const loadCsrfToken = async (fetchFunction: typeof window.fetch, forceRefresh = false) => {
    if (forceRefresh) {
        csrfToken = null;
        csrfTokenRequest = null;
    }
    if (csrfToken) {
        return csrfToken;
    }
    if (!csrfTokenRequest) {
        csrfTokenRequest = fetchFunction(CSRF_TOKEN_URL, {
            cache: 'no-store',
            credentials: 'same-origin',
            headers: { Accept: 'application/json' }
        }).then(async (response) => {
            if (!response.ok) {
                throw new Error(await response.text() || 'CSRF 토큰을 발급받지 못했습니다.');
            }

            const data = await response.json() as CsrfTokenResponse;
            if (!data.headerName || !data.token) {
                throw new Error('CSRF 토큰 응답이 올바르지 않습니다.');
            }
            csrfToken = data;
            return data;
        }).finally(() => {
            csrfTokenRequest = null;
        });
    }
    return csrfTokenRequest;
};

export const getApiCsrfRequestHeaders = async (): Promise<Record<string, string>> => {
    const token = await loadCsrfToken(window.fetch);
    return { [token.headerName]: token.token };
};

export const installApiFetchSecurity = () => {
    const originalFetch = window.fetch;

    const securedFetch = async (
        input: RequestInfo | URL,
        init?: RequestInit,
        allowCsrfRetry = true
    ): Promise<Response> => {
        const method = requestMethod(input, init);
        const requiresCsrf = UNSAFE_METHODS.has(method) && isSameOriginApiRequest(input);
        const includesConference = selectedAdminConferenceSeq !== null && isConferenceScopedApiRequest(input);
        const retryInput = input instanceof Request ? input.clone() : input;
        let requestInit = init;

        if (requiresCsrf || includesConference) {
            const headers = new Headers(input instanceof Request ? input.headers : undefined);
            new Headers(init?.headers).forEach((value, key) => headers.set(key, value));
            if (includesConference) {
                headers.set(ADMIN_CONFERENCE_HEADER, String(selectedAdminConferenceSeq));
            }
            if (requiresCsrf) {
                const token = await loadCsrfToken(originalFetch);
                headers.set(token.headerName, token.token);
            }
            requestInit = {
                ...init,
                headers
            };
        }

        const response = await originalFetch.call(window, input, requestInit);

        if (requiresCsrf
            && allowCsrfRetry
            && response.status === 403
            && response.headers.get(CSRF_ERROR_HEADER) === 'true') {
            await loadCsrfToken(originalFetch, true);
            return securedFetch(retryInput, init, false);
        }

        if (requiresCsrf
            && response.ok
            && requestUrl(input).pathname === '/api/admin/logout') {
            csrfToken = null;
            csrfTokenRequest = null;
        }

        if (response.status === 401 && isAdminAuthenticationRequest(input)) {
            window.dispatchEvent(new Event(ADMIN_SESSION_EXPIRED_EVENT));
        }

        return response;
    };

    const apiFetchSecurity: typeof window.fetch = (input, init) => securedFetch(input, init);
    window.fetch = apiFetchSecurity;

    return () => {
        if (window.fetch === apiFetchSecurity) {
            window.fetch = originalFetch;
        }
    };
};
