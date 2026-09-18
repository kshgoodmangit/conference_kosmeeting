import { useEffect, useState } from 'react';
import { Clock3, type LucideIcon } from 'lucide-react';

interface AdminDashboardHeaderProps {
    badge: string;
    icon: LucideIcon;
    title: string;
    description: string;
    deadlines: { label: string; value: string; accent?: string }[];
}

const currentTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: '2-digit',
    day: '2-digit',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23'
});

export const AdminDashboardHeader = ({ badge, icon: Icon, title, description, deadlines }: AdminDashboardHeaderProps) => {
    const [currentTime, setCurrentTime] = useState(() => new Date());

    useEffect(() => {
        const intervalId = window.setInterval(() => setCurrentTime(new Date()), 1_000);
        return () => window.clearInterval(intervalId);
    }, []);

    return (
        <section className="relative overflow-hidden rounded-2xl border border-blue-200/70 bg-gradient-to-br from-blue-700 via-blue-600 to-indigo-700 px-5 py-5 text-white shadow-sm dark:border-blue-900/70 dark:from-blue-700 dark:via-blue-600 dark:to-indigo-700 dark:text-white md:px-7 md:py-6">
            <div className="pointer-events-none absolute -right-16 -top-24 h-64 w-64 rounded-full bg-white/10 blur-2xl dark:bg-white/10" />
            <div className="pointer-events-none absolute bottom-0 right-1/4 h-32 w-32 rounded-full bg-cyan-300/10 blur-xl dark:bg-cyan-300/10" />
            <div className="relative flex flex-col justify-between gap-5 lg:flex-row lg:items-center">
                <div>
                    <div className="flex flex-wrap items-center gap-2">
                        <span className="inline-flex items-center gap-1.5 rounded-full bg-white/15 px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ring-white/20 dark:bg-white/15 dark:ring-white/20">
                            <Icon size={13} /> {badge}
                        </span>
                        <span className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-2.5 py-1 text-xs font-semibold text-blue-50 ring-1 ring-inset ring-white/20 dark:bg-white/10 dark:text-blue-50 dark:ring-white/20">
                            <Clock3 size={12} /> 현재 {currentTimeFormatter.format(currentTime)}
                        </span>
                    </div>
                    <h1 className="mt-3 text-xl font-bold tracking-tight md:text-2xl">{title}</h1>
                    <p className="mt-1.5 text-sm text-blue-100 dark:text-blue-100">{description}</p>
                </div>
                <div className="flex flex-wrap items-stretch gap-2.5 sm:gap-3">
                    {deadlines.map(({ label, value, accent = '' }) => (
                        <div key={label} className="min-w-32 rounded-xl bg-white/10 px-4 py-3 ring-1 ring-inset ring-white/15 backdrop-blur-sm dark:bg-white/10 dark:ring-white/15">
                            <p className="text-xs text-blue-100 dark:text-blue-100">{label}</p>
                            <p className={`mt-0.5 text-xl font-bold ${accent}`}>{value}</p>
                        </div>
                    ))}
                </div>
            </div>
        </section>
    );
};
