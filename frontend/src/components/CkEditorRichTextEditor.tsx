import { forwardRef, useEffect, useImperativeHandle, useRef, useState, type ReactNode } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { createCkEditorSecurityConfig } from '../ckeditorSecurity.ts';
import { PUBLIC_MENU_CONTENT_CSS, publicMenuPreviewDocument } from '../publicMenuContent.ts';

const CKEDITOR_BASE_PATH = '/vendor/ckeditor4/';
const CKEDITOR_SCRIPT_URL = `${CKEDITOR_BASE_PATH}ckeditor.js`;

interface CkEditorEvent {
    editor: CkEditorInstance;
    data?: {
        dataTransfer?: CkEditorDataTransfer;
        keyCode?: number;
    };
    cancel: () => void;
}

interface CkEditorDataTransfer {
    getFilesCount: () => number;
    getFile: (index: number) => File | null;
}

interface CkEditorInstance {
    getData: () => string;
    checkDirty: () => boolean;
    resetDirty: () => void;
    setData: (html: string, callback?: () => void) => void;
    insertHtml: (html: string) => void;
    focus: () => void;
    showNotification?: (message: string, type: 'info' | 'success' | 'warning') => void;
    on: (
        eventName: string,
        listener: (event: CkEditorEvent) => void,
        scope?: unknown,
        listenerData?: unknown,
        priority?: number
    ) => void;
    destroy: (noUpdate?: boolean) => void;
}

interface CkEditorGlobal {
    replace: (element: HTMLTextAreaElement, config: Record<string, unknown>) => CkEditorInstance;
}

type CkEditorWindow = Window & {
    CKEDITOR?: CkEditorGlobal;
    CKEDITOR_BASEPATH?: string;
};

let ckEditorLoader: Promise<CkEditorGlobal> | null = null;

interface CkEditorUploadResponse {
    uploaded?: number;
    url?: string;
    width?: number;
    height?: number;
    error?: string | {
        message?: string;
    };
}

const loadCkEditor = (): Promise<CkEditorGlobal> => {
    const ckWindow = window as CkEditorWindow;
    if (ckWindow.CKEDITOR) return Promise.resolve(ckWindow.CKEDITOR);
    if (ckEditorLoader) return ckEditorLoader;

    ckEditorLoader = new Promise((resolve, reject) => {
        ckWindow.CKEDITOR_BASEPATH = CKEDITOR_BASE_PATH;
        const existingScript = document.querySelector<HTMLScriptElement>(`script[src="${CKEDITOR_SCRIPT_URL}"]`);
        const script = existingScript ?? document.createElement('script');

        const handleLoad = () => ckWindow.CKEDITOR
            ? resolve(ckWindow.CKEDITOR)
            : reject(new Error('CKEditor 전역 객체를 찾을 수 없습니다.'));
        const handleError = () => reject(new Error('CKEditor 파일을 불러오지 못했습니다.'));

        script.addEventListener('load', handleLoad, { once: true });
        script.addEventListener('error', handleError, { once: true });
        if (!existingScript) {
            script.src = CKEDITOR_SCRIPT_URL;
            script.async = true;
            script.dataset.ckeditor = 'true';
            document.head.appendChild(script);
        }
    });

    return ckEditorLoader;
};

const isImageFile = (file: File) => (
    file.type.startsWith('image/') || /\.(gif|jpe?g|png|bmp|webp)$/i.test(file.name)
);

const escapeHtmlAttribute = (value: string) => value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

const imageDimensionAttribute = (name: 'width' | 'height', value: number | undefined) => (
    Number.isSafeInteger(value) && Number(value) > 0 && Number(value) <= 10000
        ? ` ${name}="${value}"`
        : ''
);

const responseErrorMessage = (response: CkEditorUploadResponse, fallback: string) => {
    if (typeof response.error === 'string' && response.error.trim()) {
        return response.error;
    }
    if (response.error && typeof response.error === 'object' && response.error.message?.trim()) {
        return response.error.message;
    }
    return fallback;
};

const uploadImageFile = async (uploadUrl: string, file: File): Promise<CkEditorUploadResponse> => {
    const formData = new FormData();
    formData.append('upload', file, file.name || 'editor-image.png');

    const response = await fetch(uploadUrl, {
        method: 'POST',
        body: formData
    });
    const responseText = await response.text();
    let result: CkEditorUploadResponse = {};
    try {
        result = JSON.parse(responseText) as CkEditorUploadResponse;
    } catch {
        if (!response.ok) {
            throw new Error(responseText || `이미지 업로드에 실패했습니다. (${response.status})`);
        }
    }

    if (!response.ok || result.uploaded !== 1 || !result.url) {
        throw new Error(responseErrorMessage(result, `이미지 업로드에 실패했습니다. (${response.status})`));
    }

    const parsedUrl = new URL(result.url, window.location.origin);
    if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
        throw new Error('서버가 안전하지 않은 이미지 경로를 반환했습니다.');
    }
    return result;
};

const uploadAndInsertImages = async (editor: CkEditorInstance, uploadUrl: string, files: File[]) => {
    editor.showNotification?.('이미지를 업로드하는 중입니다.', 'info');
    try {
        for (const file of files) {
            const result = await uploadImageFile(uploadUrl, file);
            const imageHtml = '<img src="' + escapeHtmlAttribute(result.url ?? '') + '" alt=""'
                + imageDimensionAttribute('width', result.width)
                + imageDimensionAttribute('height', result.height)
                + ' />';
            editor.insertHtml(imageHtml);
        }
        editor.showNotification?.('이미지를 등록했습니다.', 'success');
    } catch (reason) {
        editor.showNotification?.(
            reason instanceof Error ? reason.message : '이미지를 업로드하지 못했습니다.',
            'warning'
        );
    }
};

export interface CkEditorInsertAction {
    label: string;
    html: string;
    icon?: ReactNode;
}

interface CkEditorRichTextEditorProps {
    initialContent: string;
    onChange: (html: string) => void;
    uploadUrl?: string;
    previewTitle: string;
    editorHeight?: number;
    previewHeight?: number;
    allowYoutube?: boolean;
    compactToolbar?: boolean;
    contentStyle?: 'default' | 'public-menu';
    insertActions?: CkEditorInsertAction[];
    previewNote?: ReactNode;
    footer?: ReactNode;
    showPreviewToggle?: boolean;
    onEscape?: () => void;
}

export interface CkEditorRichTextEditorHandle {
    getData: () => string;
    hasChanges: () => boolean;
    focus: () => void;
}

export const CkEditorRichTextEditor = forwardRef<CkEditorRichTextEditorHandle, CkEditorRichTextEditorProps>(({
    initialContent,
    onChange,
    uploadUrl,
    previewTitle,
    editorHeight = 360,
    previewHeight = 420,
    allowYoutube = false,
    compactToolbar = false,
    contentStyle = 'default',
    insertActions = [],
    previewNote,
    footer,
    showPreviewToggle = true,
    onEscape
}, ref) => {
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const editorRef = useRef<CkEditorInstance | null>(null);
    const onChangeRef = useRef(onChange);
    const onEscapeRef = useRef(onEscape);
    const latestValueRef = useRef(initialContent);
    const lastEditorValueRef = useRef(initialContent);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [showPreview, setShowPreview] = useState(false);
    const [previewHtml, setPreviewHtml] = useState(initialContent);

    useImperativeHandle(ref, () => ({
        getData: () => editorRef.current?.getData() ?? latestValueRef.current,
        hasChanges: () => editorRef.current?.checkDirty() ?? false,
        focus: () => editorRef.current?.focus()
    }), []);

    useEffect(() => {
        onChangeRef.current = onChange;
        onEscapeRef.current = onEscape;
    }, [onChange, onEscape]);

    useEffect(() => {
        latestValueRef.current = initialContent;
        setPreviewHtml(initialContent);
        const editor = editorRef.current;
        if (editor && initialContent !== lastEditorValueRef.current && editor.getData() !== initialContent) {
            editor.setData(initialContent);
        }
    }, [initialContent]);

    useEffect(() => {
        let cancelled = false;

        void loadCkEditor()
            .then(async (ckeditor) => {
                if (cancelled || !textareaRef.current) return;
                const securityConfig = await createCkEditorSecurityConfig(allowYoutube, Boolean(uploadUrl));
                if (cancelled || !textareaRef.current) return;

                const editor = ckeditor.replace(textareaRef.current, {
                    language: 'ko',
                    height: editorHeight,
                    resize_enabled: true,
                    versionCheck: false,
                    extraPlugins: allowYoutube
                        ? 'font,colorbutton,justify,safeyoutube'
                        : 'font,colorbutton,justify',
                    removePlugins: 'about,elementspath,scayt,wsc,uploadimage,uploadwidget',
                    toolbar: compactToolbar ? [
                        { name: 'document', items: ['Source', '-', 'Undo', 'Redo', 'Maximize'] },
                        { name: 'basicstyles', items: ['Bold', 'Italic', 'Link', 'BulletedList', 'Image', 'Table'] }
                    ] : [
                        { name: 'document', items: ['Source'] },
                        { name: 'clipboard', items: ['Undo', 'Redo'] },
                        { name: 'styles', items: ['Format', 'Font', 'FontSize'] },
                        { name: 'colors', items: ['TextColor', 'BGColor'] },
                        { name: 'basicstyles', items: ['Bold', 'Italic', 'Underline', 'Strike', 'RemoveFormat'] },
                        { name: 'paragraph', items: ['NumberedList', 'BulletedList', '-', 'Outdent', 'Indent', '-', 'JustifyLeft', 'JustifyCenter', 'JustifyRight'] },
                        { name: 'links', items: ['Link', 'Unlink'] },
                        { name: 'insert', items: allowYoutube
                            ? ['Image', 'Table', 'HorizontalRule', 'SafeYoutube']
                            : ['Image', 'Table', 'HorizontalRule'] },
                        { name: 'tools', items: ['Maximize'] }
                    ],
                    format_tags: 'p;h1;h2;h3;h4;h5;h6;pre',
                    font_names: '맑은 고딕/Malgun Gothic,Apple SD Gothic Neo,sans-serif;돋움/Dotum,sans-serif;굴림/Gulim,sans-serif;Arial/Arial,Helvetica,sans-serif;Verdana/Verdana,Geneva,sans-serif;Georgia/Georgia,serif;Courier New/Courier New,Courier,monospace',
                    fontSize_sizes: '10/10px;12/12px;14/14px;16/16px;18/18px;20/20px;24/24px;28/28px;32/32px;36/36px;48/48px',
                    uploadUrl,
                    imageUploadUrl: uploadUrl,
                    filebrowserImageUploadUrl: uploadUrl,
                    filebrowserUploadMethod: 'xhr',
                    image_previewText: ' ',
                    removeDialogTabs: uploadUrl ? 'image:advanced;link:advanced' : 'image:advanced;image:Upload;link:advanced',
                    contentsCss: contentStyle === 'public-menu'
                        ? PUBLIC_MENU_CONTENT_CSS
                        : `${CKEDITOR_BASE_PATH}contents.css`,
                    ...(contentStyle === 'public-menu' ? {
                        bodyId: 'content',
                        bodyClass: 'cms-content',
                        baseHref: `${window.location.origin}/`
                    } : {}),
                    ...securityConfig
                });

                editorRef.current = editor;
                editor.on('key', (event) => {
                    if (event.data?.keyCode !== 27 || !onEscapeRef.current) return;
                    event.cancel();
                    onEscapeRef.current();
                });
                editor.on('instanceReady', () => {
                    if (cancelled) return;
                    editor.setData(latestValueRef.current, () => {
                        lastEditorValueRef.current = editor.getData();
                        editor.resetDirty();
                        setLoading(false);
                    });
                });
                editor.on('paste', (event) => {
                    const dataTransfer = event.data?.dataTransfer;
                    if (!dataTransfer) return;

                    const imageFiles: File[] = [];
                    for (let index = 0; index < dataTransfer.getFilesCount(); index += 1) {
                        const file = dataTransfer.getFile(index);
                        if (file && isImageFile(file)) {
                            imageFiles.push(file);
                        }
                    }
                    if (imageFiles.length === 0) return;

                    event.cancel();
                    if (!uploadUrl) {
                        editor.showNotification?.('이 편집기에서는 파일 업로드를 지원하지 않습니다.', 'info');
                        return;
                    }
                    void uploadAndInsertImages(editor, uploadUrl, imageFiles);
                }, undefined, undefined, 1);
                editor.on('change', ({ editor: changedEditor }) => {
                    const html = changedEditor.getData();
                    lastEditorValueRef.current = html;
                    latestValueRef.current = html;
                    setPreviewHtml(html);
                    onChangeRef.current(html);
                });
            })
            .catch((reason: unknown) => {
                if (cancelled) return;
                setLoading(false);
                setError(reason instanceof Error ? reason.message : '에디터를 초기화하지 못했습니다.');
            });

        return () => {
            cancelled = true;
            if (editorRef.current) {
                editorRef.current.destroy(true);
                editorRef.current = null;
            }
        };
    }, [allowYoutube, compactToolbar, contentStyle, editorHeight, uploadUrl]);

    const insertHtml = (html: string) => {
        const editor = editorRef.current;
        if (!editor) return;
        editor.focus();
        editor.insertHtml(html);
    };

    const previewButton = (
        <button
            type="button"
            onClick={() => {
                setPreviewHtml(editorRef.current?.getData() ?? latestValueRef.current);
                setShowPreview((current) => !current);
            }}
            className="inline-flex items-center gap-1.5 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-100 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
        >
            {showPreview ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
            {showPreview ? '미리보기 닫기' : '미리보기'}
        </button>
    );
    const imageHelp = <span className="text-[11px] text-slate-500 dark:text-slate-400">{uploadUrl ? '이미지는 선택·붙여넣기·드래그앤드롭으로 등록할 수 있습니다.' : '서식 편집 · HTML 소스 · 미리보기'}</span>;

    return (
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white dark:border-slate-700">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 bg-slate-50 px-3 py-2 dark:border-slate-700 dark:bg-slate-900">
                {insertActions.length === 0 && imageHelp}
                <div className="flex flex-wrap gap-2">
                    {insertActions.map((action) => (
                        <button
                            key={action.label}
                            type="button"
                            onClick={() => insertHtml(action.html)}
                            disabled={loading || Boolean(error)}
                            className="inline-flex items-center gap-1.5 rounded-md border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-40 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700"
                        >
                            {action.icon}{action.label}
                        </button>
                    ))}
                    {showPreviewToggle && previewButton}
                </div>
                {insertActions.length > 0 && imageHelp}
            </div>

            {loading && !error && <div className="flex min-h-24 items-center justify-center text-sm text-slate-400">CKEditor를 불러오는 중입니다.</div>}
            {error && <div className="bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">{error}</div>}
            <textarea ref={textareaRef} defaultValue={initialContent} className={loading || error ? 'hidden' : 'block'} />

            {contentStyle === 'public-menu' && (
                <p className="border-t border-slate-200 bg-slate-50 px-3 py-2 text-[11px] text-slate-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">
                    사용자 화면의 CSS와 글꼴을 적용했습니다. 편집 영역 너비에 따라 반응형 배치가 달라질 수 있습니다. 넓게 보려면 편집기의 최대화를 사용하세요.
                </p>
            )}

            {showPreview && (
                <div className="border-t border-slate-200 bg-slate-100 p-3 dark:border-slate-700 dark:bg-slate-900">
                    <iframe
                        title={previewTitle}
                        sandbox=""
                        srcDoc={contentStyle === 'public-menu' ? publicMenuPreviewDocument(previewHtml) : previewHtml}
                        style={{ height: previewHeight }}
                        className="w-full rounded-lg border border-slate-200 bg-white dark:border-slate-700"
                    />
                    {previewNote}
                </div>
            )}

            {footer}
        </div>
    );
});
