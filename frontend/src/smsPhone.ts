export const normalizeSmsPhone = (value: string): string | null => {
    if (!/^[+0-9().\s-]+$/.test(value.trim())) return null;
    let number = value.trim().replace(/[().\s-]/g, '');
    if (number.startsWith('0082')) number = '+82' + number.slice(4);
    if (number.startsWith('82') && number.length >= 11) number = '+' + number;
    if (number.startsWith('+82')) {
        const local = number.slice(3);
        number = local.startsWith('0') ? local : '0' + local;
    }
    if (/^(010[0-9]{8}|01[16789][0-9]{7,8})$/.test(number)) return '+82' + number.slice(1);
    if (number.startsWith('+82') || number.startsWith('0')) return null;
    return /^\+[1-9][0-9]{7,14}$/.test(number) ? number : null;
};
