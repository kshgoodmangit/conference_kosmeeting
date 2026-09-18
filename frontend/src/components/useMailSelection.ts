import { useCallback, useState } from 'react';
import type { MailRecipient } from './MailComposeModal';

/** Keeps selected rows across pages; callers clear them when applying search conditions. */
export const useMailSelection = <T extends { seq: number }>(
    items: readonly T[],
    disabled: boolean,
    toRecipient: (item: T) => MailRecipient
) => {
    const [selected, setSelected] = useState<Record<number, T>>({});
    const [recipients, setRecipients] = useState<MailRecipient[] | null>(null);
    const count = Object.keys(selected).length;
    const selectedOnPage = items.filter((item) => selected[item.seq]).length;

    const clear = useCallback(() => setSelected({}), []);
    const remove = useCallback((seq: number) => {
        setSelected((current) => {
            if (!current[seq]) return current;
            const next = { ...current };
            delete next[seq];
            return next;
        });
    }, []);
    const syncSelected = useCallback((rows: readonly T[]) => {
        setSelected((current) => {
            const next = { ...current };
            rows.forEach((row) => { if (next[row.seq]) next[row.seq] = row; });
            return next;
        });
    }, []);
    const toggle = (rows: readonly T[], checked: boolean) => {
        if (disabled) return;
        setSelected((current) => {
            const next = { ...current };
            rows.forEach((row) => {
                if (checked) next[row.seq] = row;
                else delete next[row.seq];
            });
            return next;
        });
    };

    return {
        selected, count, recipients, clear, remove, syncSelected, toggle,
        allOnPageSelected: items.length > 0 && selectedOnPage === items.length,
        someOnPageSelected: selectedOnPage > 0 && selectedOnPage < items.length,
        open: () => { if (!disabled && count) setRecipients(Object.values(selected).map(toRecipient)); },
        close: () => setRecipients(null)
    };
};
