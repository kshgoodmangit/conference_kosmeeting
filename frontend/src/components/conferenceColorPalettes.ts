export interface ConferenceColorPalette {
    key: string;
    name: string;
    category: '봄 행사' | '가을 행사' | '공공기관' | '의료학회';
    description: string;
    colors: readonly [string, string, string];
    primary: string;
    accent: string;
    surface: string;
    emphasis: string;
}

// Curated design suggestions, independent of the layout and event dates.
// Explicit utilities let Tailwind discover every preview color at build time.
export const conferenceColorPalettes: readonly ConferenceColorPalette[] = [
    {
        key: 'navy', name: '클래식 네이비', category: '공공기관', description: '단정한 네이비에 선명한 블루 포인트',
        colors: ['#1E3A5F', '#2563EB', '#F1F5F9'],
        primary: 'bg-[#1E3A5F] text-white dark:bg-[#1E3A5F] dark:text-white',
        accent: 'bg-[#2563EB] text-white dark:bg-[#2563EB] dark:text-white',
        surface: 'bg-[#F1F5F9] text-[#1E3A5F] dark:bg-[#F1F5F9] dark:text-[#1E3A5F]',
        emphasis: 'border-[#1E3A5F]/25 bg-[#F1F5F9] text-[#1E3A5F] dark:border-[#1E3A5F]/25 dark:bg-[#F1F5F9] dark:text-[#1E3A5F]'
    },
    {
        key: 'slate', name: '차분한 블루그레이', category: '공공기관', description: '절제된 색상으로 정보에 집중하는 구성',
        colors: ['#334155', '#0369A1', '#F0F9FF'],
        primary: 'bg-[#334155] text-white dark:bg-[#334155] dark:text-white',
        accent: 'bg-[#0369A1] text-white dark:bg-[#0369A1] dark:text-white',
        surface: 'bg-[#F0F9FF] text-[#334155] dark:bg-[#F0F9FF] dark:text-[#334155]',
        emphasis: 'border-[#334155]/25 bg-[#F0F9FF] text-[#334155] dark:border-[#334155]/25 dark:bg-[#F0F9FF] dark:text-[#334155]'
    },
    {
        key: 'sage', name: '봄빛 세이지', category: '봄 행사', description: '부드러운 초록과 꽃빛 포인트의 조합',
        colors: ['#286451', '#A63D61', '#F1F7F2'],
        primary: 'bg-[#286451] text-white dark:bg-[#286451] dark:text-white',
        accent: 'bg-[#A63D61] text-white dark:bg-[#A63D61] dark:text-white',
        surface: 'bg-[#F1F7F2] text-[#286451] dark:bg-[#F1F7F2] dark:text-[#286451]',
        emphasis: 'border-[#286451]/25 bg-[#F1F7F2] text-[#286451] dark:border-[#286451]/25 dark:bg-[#F1F7F2] dark:text-[#286451]'
    },
    {
        key: 'lilac', name: '라일락 가든', category: '봄 행사', description: '은은한 보라에 산뜻한 청록을 더한 구성',
        colors: ['#6D4590', '#28776E', '#F7F3FA'],
        primary: 'bg-[#6D4590] text-white dark:bg-[#6D4590] dark:text-white',
        accent: 'bg-[#28776E] text-white dark:bg-[#28776E] dark:text-white',
        surface: 'bg-[#F7F3FA] text-[#6D4590] dark:bg-[#F7F3FA] dark:text-[#6D4590]',
        emphasis: 'border-[#6D4590]/25 bg-[#F7F3FA] text-[#6D4590] dark:border-[#6D4590]/25 dark:bg-[#F7F3FA] dark:text-[#6D4590]'
    },
    {
        key: 'terracotta', name: '가을 테라코타', category: '가을 행사', description: '따뜻한 흙빛과 깊은 올리브의 조합',
        colors: ['#9A452E', '#59652D', '#FBF5EC'],
        primary: 'bg-[#9A452E] text-white dark:bg-[#9A452E] dark:text-white',
        accent: 'bg-[#59652D] text-white dark:bg-[#59652D] dark:text-white',
        surface: 'bg-[#FBF5EC] text-[#9A452E] dark:bg-[#FBF5EC] dark:text-[#9A452E]',
        emphasis: 'border-[#9A452E]/25 bg-[#FBF5EC] text-[#9A452E] dark:border-[#9A452E]/25 dark:bg-[#FBF5EC] dark:text-[#9A452E]'
    },
    {
        key: 'burgundy', name: '버건디 골드', category: '가을 행사', description: '깊은 와인색과 차분한 골드 포인트',
        colors: ['#7D3046', '#8A641C', '#FAF5ED'],
        primary: 'bg-[#7D3046] text-white dark:bg-[#7D3046] dark:text-white',
        accent: 'bg-[#8A641C] text-white dark:bg-[#8A641C] dark:text-white',
        surface: 'bg-[#FAF5ED] text-[#7D3046] dark:bg-[#FAF5ED] dark:text-[#7D3046]',
        emphasis: 'border-[#7D3046]/25 bg-[#FAF5ED] text-[#7D3046] dark:border-[#7D3046]/25 dark:bg-[#FAF5ED] dark:text-[#7D3046]'
    },
    {
        key: 'medical-blue', name: '메디컬 블루', category: '의료학회', description: '또렷한 블루와 깨끗한 청록의 조합',
        colors: ['#175FA0', '#087E8B', '#EFF8FC'],
        primary: 'bg-[#175FA0] text-white dark:bg-[#175FA0] dark:text-white',
        accent: 'bg-[#087E8B] text-white dark:bg-[#087E8B] dark:text-white',
        surface: 'bg-[#EFF8FC] text-[#175FA0] dark:bg-[#EFF8FC] dark:text-[#175FA0]',
        emphasis: 'border-[#175FA0]/25 bg-[#EFF8FC] text-[#175FA0] dark:border-[#175FA0]/25 dark:bg-[#EFF8FC] dark:text-[#175FA0]'
    },
    {
        key: 'teal', name: '딥 틸', category: '의료학회', description: '안정감 있는 청록과 딥 블루의 조합',
        colors: ['#11665E', '#37548B', '#EFF8F5'],
        primary: 'bg-[#11665E] text-white dark:bg-[#11665E] dark:text-white',
        accent: 'bg-[#37548B] text-white dark:bg-[#37548B] dark:text-white',
        surface: 'bg-[#EFF8F5] text-[#11665E] dark:bg-[#EFF8F5] dark:text-[#11665E]',
        emphasis: 'border-[#11665E]/25 bg-[#EFF8F5] text-[#11665E] dark:border-[#11665E]/25 dark:bg-[#EFF8F5] dark:text-[#11665E]'
    }
];

export const defaultConferenceColorPalette = conferenceColorPalettes[0];
