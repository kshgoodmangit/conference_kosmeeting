export type PageStatus = 'normal' | 'warning' | 'urgent';

export interface MonitoredPage {
    name: string;
    path: string;
    status: PageStatus;
    responseMs: number;
    checkedAt: string;
}

export interface PageGroup {
    name: string;
    pages: MonitoredPage[];
}

export const pageGroups: PageGroup[] = [
    { name: '홈', pages: [
        { name: '메인 화면', path: '/', status: 'normal', responseMs: 184, checkedAt: '14:30:00' },
    ] },
    { name: '학회 소개', pages: [
        { name: 'Welcome Message', path: '/apdrc8/welcome-message', status: 'normal', responseMs: 212, checkedAt: '14:29:56' },
        { name: 'Overview', path: '/apdrc8/overview', status: 'normal', responseMs: 198, checkedAt: '14:29:52' },
        { name: 'Committee', path: '/apdrc8/committee', status: 'normal', responseMs: 231, checkedAt: '14:29:48' },
        { name: 'About APDRC8', path: '/apdrc8/about-apdrc8', status: 'normal', responseMs: 205, checkedAt: '14:29:44' },
        { name: 'Contact Us', path: '/apdrc8/contact-us', status: 'normal', responseMs: 219, checkedAt: '14:29:40' },
    ] },
    { name: '프로그램', pages: [
        { name: 'Program at a Glance', path: '/program/program-at-a-glance', status: 'normal', responseMs: 268, checkedAt: '14:29:36' },
        { name: 'Scientific Program', path: '/program/scientific-program', status: 'warning', responseMs: 780, checkedAt: '14:29:32' },
        { name: 'Invited Speakers', path: '/program/invited-speakers', status: 'normal', responseMs: 346, checkedAt: '14:29:28' },
    ] },
    { name: '초록', pages: [
        { name: 'Submission Guideline', path: '/abstract/submission-guideline', status: 'normal', responseMs: 225, checkedAt: '14:29:24' },
        { name: 'Abstract Submission', path: '/abstract/abstract-submission', status: 'urgent', responseMs: 2408, checkedAt: '14:29:20' },
        { name: 'Award', path: '/abstract/award', status: 'normal', responseMs: 203, checkedAt: '14:29:16' },
        { name: 'Presentation Guidelines', path: '/abstract/presentation-guidelines', status: 'normal', responseMs: 238, checkedAt: '14:29:12' },
    ] },
    { name: '사전등록', pages: [
        { name: 'Registration Guideline', path: '/registration/registration-guideline', status: 'normal', responseMs: 247, checkedAt: '14:29:08' },
        { name: 'Online Registration', path: '/registration/online-registration', status: 'warning', responseMs: 920, checkedAt: '14:29:04' },
        { name: 'Accommodation', path: '/registration/accommodation', status: 'normal', responseMs: 254, checkedAt: '14:29:00' },
        { name: 'Visa', path: '/registration/visa', status: 'normal', responseMs: 215, checkedAt: '14:28:56' },
        { name: 'Cancellation Policy', path: '/registration/cancellation-policy', status: 'normal', responseMs: 226, checkedAt: '14:28:52' },
    ] },
    { name: '행사 정보', pages: [
        { name: 'Venue', path: '/information/venue', status: 'normal', responseMs: 243, checkedAt: '14:28:48' },
        { name: 'Transportation', path: '/information/transportation', status: 'normal', responseMs: 251, checkedAt: '14:28:44' },
        { name: 'About Seoul & Korea', path: '/information/about-seoul-korea', status: 'normal', responseMs: 286, checkedAt: '14:28:40' },
        { name: 'Notice', path: '/information/notice', status: 'warning', responseMs: 540, checkedAt: '14:28:36' },
        { name: 'FAQ', path: '/information/faq', status: 'normal', responseMs: 312, checkedAt: '14:28:32' },
    ] },
    { name: '스폰서', pages: [
        { name: 'Sponsorship', path: '/sponsors/sponsorship', status: 'normal', responseMs: 221, checkedAt: '14:28:28' },
        { name: 'Sponsor', path: '/sponsors/sponsor', status: 'normal', responseMs: 318, checkedAt: '14:28:24' },
    ] },
    { name: '회원', pages: [
        { name: 'Login', path: '/login', status: 'normal', responseMs: 176, checkedAt: '14:28:20' },
        { name: 'Sign-Up', path: '/join', status: 'normal', responseMs: 204, checkedAt: '14:28:16' },
        { name: '국내 회원가입', path: '/join/domestic', status: 'normal', responseMs: 233, checkedAt: '14:28:12' },
        { name: '국외 회원가입', path: '/join/international', status: 'normal', responseMs: 249, checkedAt: '14:28:08' },
        { name: 'My Page', path: '/mypage', status: 'normal', responseMs: 271, checkedAt: '14:28:04' },
        { name: 'Personal Information', path: '/mypage/profile', status: 'normal', responseMs: 294, checkedAt: '14:28:00' },
        { name: 'Mypage Abstract', path: '/mypage/abstract', status: 'normal', responseMs: 338, checkedAt: '14:27:56' },
        { name: 'Mypage Registration', path: '/mypage/registration', status: 'warning', responseMs: 680, checkedAt: '14:27:52' },
        { name: 'Mypage Certificate', path: '/mypage/certificate', status: 'normal', responseMs: 266, checkedAt: '14:27:48' },
    ] },
    { name: '정책', pages: [
        { name: 'Privacy Policy', path: '/privacy-policy', status: 'normal', responseMs: 190, checkedAt: '14:27:44' },
    ] },
];
