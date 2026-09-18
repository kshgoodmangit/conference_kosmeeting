export const conferenceLayoutOptions = [
    {
        key: 'visual', number: '01', name: '비주얼 중심형',
        description: '큰 대표 이미지로 행사를 소개하고, 주요 정보를 아래에 배치합니다.',
        emphasis: '행사 이미지와 소개',
        navigation: '상단 가로 메뉴',
        suitableFor: '행사 분위기와 브랜드를 먼저 전달하고 싶을 때',
        structure: '상단 메뉴 → 대표 이미지 → 등록·초록 바로가기 → 소개·주요 일정'
    },
    {
        key: 'information', number: '02', name: '정보 중심형',
        description: '접수 일정과 등록·초록 제출 바로가기를 첫 화면에서 보여줍니다.',
        emphasis: '접수 일정과 바로가기',
        navigation: '상단 가로 메뉴',
        suitableFor: '참가자가 등록과 제출 정보를 빠르게 찾아야 할 때',
        structure: '상단 메뉴 → 행사 안내·접수 일정 → 등록·초록 바로가기 → 공지사항'
    },
    {
        key: 'program', number: '03', name: '프로그램 중심형',
        description: '왼쪽 메뉴를 두고, 프로그램과 초청 연자를 중심으로 구성합니다.',
        emphasis: '프로그램과 연자',
        navigation: '좌측 세로 메뉴',
        suitableFor: '여러 세션과 연자 정보를 둘러보는 일이 중요할 때',
        structure: '좌측 메뉴 + 행사 안내 → 프로그램·초청 연자 → 참가 등록'
    }
] as const;

export type ConferenceLayoutKey = typeof conferenceLayoutOptions[number]['key'];
export type ConferenceLayoutOption = typeof conferenceLayoutOptions[number];
