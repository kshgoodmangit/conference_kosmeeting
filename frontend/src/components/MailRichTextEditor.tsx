import { CkEditorRichTextEditor } from './CkEditorRichTextEditor';

interface MailRichTextEditorProps {
    initialContent: string;
    onChange: (html: string) => void;
}

export const MailRichTextEditor = ({ initialContent, onChange }: MailRichTextEditorProps) => {
    return (
        <CkEditorRichTextEditor
            initialContent={initialContent}
            onChange={onChange}
            uploadUrl="/api/admin/mail/images"
            previewTitle="메일 HTML 미리보기"
            contentStyle="default"
            previewNote={<p className="mt-2 text-[11px] text-slate-500">스크립트가 차단된 미리보기이며, 최종 저장 시 서버 보안 정제가 적용됩니다.</p>}
            footer={(
                <div className="flex flex-wrap gap-x-4 gap-y-1 border-t border-slate-200 bg-slate-50 px-3 py-2 text-[11px] text-slate-500 dark:border-slate-700 dark:bg-slate-900">
                    <span>개인화: {'{{name}}'}, {'{{email}}'}, {'{{affiliation}}'}</span>
                </div>
            )}
        />
    );
};
