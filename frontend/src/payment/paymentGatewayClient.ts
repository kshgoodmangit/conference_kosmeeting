export interface PaymentCheckoutSession {
    provider: string;
    providerDisplayName: string;
    route: string;
    scriptUrl: string;
    formName: string;
    screenElementId: string;
    orderNumber: string;
    currency: string;
    amount: number;
    fields: Record<string, string>;
    resultFieldNames: string[];
    notice: string;
}

export type PaymentResult = Record<string, string>;

interface PaymentGatewayClient {
    providerCode: string;
    start: (
        session: PaymentCheckoutSession,
        form: HTMLFormElement,
        screen: HTMLDivElement
    ) => Promise<PaymentResult>;
}

declare global {
    interface Window {
        doTransaction?: (form: HTMLFormElement) => void;
        getPGIOresult?: () => void;
    }
}

const loadedScripts = new Map<string, Promise<void>>();

const loadExternalScript = (source: string) => {
    const existingRequest = loadedScripts.get(source);
    if (existingRequest) {
        return existingRequest;
    }

    const request = new Promise<void>((resolve, reject) => {
        const existingScript = document.querySelector<HTMLScriptElement>(`script[data-payment-script="${CSS.escape(source)}"]`);
        if (existingScript?.dataset.loaded === 'true') {
            resolve();
            return;
        }

        const script = existingScript ?? document.createElement('script');
        script.src = source;
        script.async = true;
        script.dataset.paymentScript = source;
        script.addEventListener('load', () => {
            script.dataset.loaded = 'true';
            resolve();
        }, { once: true });
        script.addEventListener('error', () => {
            loadedScripts.delete(source);
            reject(new Error('PG사 결제 스크립트를 불러오지 못했습니다.'));
        }, { once: true });

        if (!existingScript) {
            document.head.appendChild(script);
        }
    });

    loadedScripts.set(source, request);
    return request;
};

const setFormFields = (form: HTMLFormElement, session: PaymentCheckoutSession) => {
    form.replaceChildren();
    form.name = session.formName;
    form.id = session.formName;
    form.method = 'post';
    form.autocomplete = 'off';

    Object.entries(session.fields).forEach(([name, value]) => {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        form.appendChild(input);
    });
};

const readFormResult = (form: HTMLFormElement, fieldNames: string[]): PaymentResult => {
    const result: PaymentResult = {};
    fieldNames.forEach((fieldName) => {
        const field = form.elements.namedItem(fieldName);
        if (field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement) {
            result[fieldName] = field.value;
        }
    });
    return result;
};

const payGateClient: PaymentGatewayClient = {
    providerCode: 'paygate',
    async start(session, form, screen) {
        await loadExternalScript(session.scriptUrl);
        if (typeof window.doTransaction !== 'function') {
            throw new Error('PayGate doTransaction 함수를 찾을 수 없습니다. 스크립트 URL을 확인해주세요.');
        }

        screen.id = session.screenElementId;
        screen.classList.add('paygate-payment-screen');
        screen.replaceChildren();
        setFormFields(form, session);

        return new Promise<PaymentResult>((resolve, reject) => {
            const previousCallback = window.getPGIOresult;
            let settled = false;
            const restoreCallback = () => {
                if (previousCallback) {
                    window.getPGIOresult = previousCallback;
                } else {
                    delete window.getPGIOresult;
                }
            };

            const timeout = window.setTimeout(() => {
                if (settled) {
                    return;
                }
                settled = true;
                restoreCallback();
                reject(new Error('PayGate 결제 응답 대기 시간이 초과되었습니다.'));
            }, 10 * 60 * 1000);

            window.getPGIOresult = () => {
                if (settled) {
                    return;
                }
                settled = true;
                window.clearTimeout(timeout);
                const result = readFormResult(form, session.resultFieldNames);
                restoreCallback();
                resolve(result);
            };

            try {
                window.doTransaction?.(form);
            } catch (error) {
                settled = true;
                window.clearTimeout(timeout);
                restoreCallback();
                reject(error instanceof Error ? error : new Error('PayGate 결제를 시작하지 못했습니다.'));
            }
        });
    }
};

const clients = new Map<string, PaymentGatewayClient>([
    [payGateClient.providerCode, payGateClient]
]);

export const startConfiguredPayment = (
    session: PaymentCheckoutSession,
    form: HTMLFormElement,
    screen: HTMLDivElement
) => {
    const client = clients.get(session.provider.toLowerCase());
    if (!client) {
        throw new Error(`지원하지 않는 PG사 클라이언트입니다: ${session.provider}`);
    }
    return client.start(session, form, screen);
};
