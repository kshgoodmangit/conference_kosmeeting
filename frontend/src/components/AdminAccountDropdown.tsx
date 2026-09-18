import { useEffect, useRef, useState } from 'react';
import { ChevronDown, Crown, KeyRound, LogOut, UserRoundPen } from 'lucide-react';
import type { AdminLoginData } from './AdminLoginPage';

interface Props {
    adminUser: AdminLoginData;
    onEditProfile?: () => void;
    onChangePassword: () => void;
    onLogout: () => void;
}

export const AdminAccountDropdown = ({
    adminUser,
    onEditProfile,
    onChangePassword,
    onLogout
}: Props) => {
    const [isOpen, setIsOpen] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!isOpen) return;

        const handlePointerDown = (event: PointerEvent) => {
            if (!containerRef.current?.contains(event.target as Node)) {
                setIsOpen(false);
            }
        };
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                setIsOpen(false);
            }
        };

        document.addEventListener('pointerdown', handlePointerDown);
        document.addEventListener('keydown', handleKeyDown);
        return () => {
            document.removeEventListener('pointerdown', handlePointerDown);
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, [isOpen]);

    const selectMenu = (action: () => void) => {
        setIsOpen(false);
        action();
    };

    return (
        <div ref={containerRef} className="relative border-l border-slate-200 pl-2 dark:border-slate-800">
            <button
                type="button"
                onClick={() => setIsOpen((open) => !open)}
                className="flex items-center gap-2 rounded-lg px-1.5 py-1 transition-colors hover:bg-slate-100 dark:hover:bg-slate-800"
                aria-haspopup="menu"
                aria-expanded={isOpen}
                aria-label="로그인 계정 메뉴"
            >
                <span className="flex h-8 w-8 items-center justify-center rounded-full border border-slate-200 bg-blue-100 text-blue-600 dark:border-slate-800 dark:bg-blue-900 dark:text-blue-400">
                    <Crown className="h-4 w-4" />
                </span>
                <span className="hidden max-w-36 truncate text-sm font-medium md:inline">{adminUser.adminName}</span>
                <ChevronDown className={`h-4 w-4 text-slate-400 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
            </button>

            {isOpen && (
                <div
                    role="menu"
                    className="absolute right-0 top-[calc(100%+0.5rem)] z-[120] w-64 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl dark:border-slate-800 dark:bg-slate-950"
                >
                    <div className="border-b border-slate-200 px-4 py-3 dark:border-slate-800">
                        <p className="truncate text-sm font-bold text-slate-900 dark:text-slate-50">{adminUser.adminName}</p>
                        <p className="mt-0.5 truncate text-xs text-slate-500 dark:text-slate-400">{adminUser.email}</p>
                        <span className="mt-2 inline-flex rounded-full bg-blue-50 px-2 py-0.5 text-[10px] font-semibold uppercase text-blue-600 dark:bg-blue-950/50 dark:text-blue-300">
                            {adminUser.role === 'reviewer' ? 'Reviewer' : 'Admin'}
                        </span>
                    </div>

                    <div className="py-1">
                        {onEditProfile && (
                            <button
                                type="button"
                                role="menuitem"
                                onClick={() => selectMenu(onEditProfile)}
                                className="flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-900"
                            >
                                <UserRoundPen className="h-4 w-4 text-violet-500" />
                                내 정보 수정
                            </button>
                        )}
                        <button
                            type="button"
                            role="menuitem"
                            onClick={() => selectMenu(onChangePassword)}
                            className="flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-900"
                        >
                            <KeyRound className="h-4 w-4 text-blue-500" />
                            비밀번호 변경
                        </button>
                    </div>

                    <div className="border-t border-slate-200 py-1 dark:border-slate-800">
                        <button
                            type="button"
                            role="menuitem"
                            onClick={() => selectMenu(onLogout)}
                            className="flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm font-medium text-rose-600 hover:bg-rose-50 dark:text-rose-400 dark:hover:bg-rose-950/20"
                        >
                            <LogOut className="h-4 w-4" />
                            로그아웃
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};
