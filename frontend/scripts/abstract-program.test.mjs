import test from 'node:test';
import assert from 'node:assert/strict';
import { isAssignableSlot, nextAvailableSlot } from '../src/components/abstractProgramTypes.ts';
import { buildAbstractProgramTree } from '../src/components/abstractProgramTreeModel.ts';

const slot = (seq, time, overrides = {}) => ({ seq, startTime: time, endTime: '17:00:00', programDaySeq: 1,
    scopeType: 'ROOM', roomSeq: 1, parentSeq: 100, enabled: true, itemType: 'ABSTRACT_PRESENTATION', sortOrder: 0, ...overrides });
const program = items => ({ days: [{ seq: 1, enabled: true }], rooms: [{ seq: 1, enabled: true }],
    dayRooms: [{ programDaySeq: 1, roomSeq: 1, enabled: true }], items: [
        slot(100, '16:00:00', { itemType: 'SESSION', parentSeq: null }), ...items] });

test('next slot skips assigned and disabled items and follows chronological order', () => {
    const current = slot(1, '16:00:00');
    const data = program([slot(4, '16:15:00'), slot(2, '16:05:00', { abstractSubmissionSeq: 90 }),
        current, slot(3, '16:10:00', { enabled: false })]);
    assert.equal(nextAvailableSlot(data, current)?.seq, 4);
});

test('last slot does not wrap or move to a different day, room, or session', () => {
    const current = slot(2, '16:05:00');
    const data = program([slot(1, '16:00:00'), current, slot(3, '16:10:00', { programDaySeq: 2 }),
        slot(4, '16:10:00', { roomSeq: 2 }), slot(5, '16:10:00', { parentSeq: 200 })]);
    assert.equal(nextAvailableSlot(data, current), undefined);
});

test('inactive day, room assignment, session and out-of-session times cannot receive assignment', () => {
    const current = slot(1, '16:00:00');
    for (const mutate of [d => { d.days[0].enabled = false; }, d => { d.dayRooms[0].enabled = false; },
        d => { d.items[0].enabled = false; }, d => { d.items[0].endTime = '16:30:00'; }]) {
        const data = program([current]);
        mutate(data);
        assert.equal(isAssignableSlot(current, data), false);
    }
});

test('common-room slots without a session also advance within their own group', () => {
    const current = slot(1, '16:00:00', { scopeType: 'ALL_ROOMS', roomSeq: null, parentSeq: null });
    const next = { ...current, seq: 2, startTime: '16:05:00' };
    assert.equal(nextAvailableSlot(program([current, next]), current)?.seq, 2);
});

test('tree only includes abstract slots under the correct date, room, and session', () => {
    const data = program([slot(2, '16:10:00'), slot(1, '16:05:00'), slot(3, '16:15:00', { itemType: 'PRESENTATION' })]);
    data.days[0] = { ...data.days[0], eventDate: '2027-05-09', dayNumber: 1, sortOrder: 0 };
    data.rooms[0].roomName = 'Main Hall';
    data.items[0].title = 'Flash Talks';
    const tree = buildAbstractProgramTree(data);
    assert.equal(tree.length, 1);
    assert.equal(tree[0].label, 'DAY 1 · 2027-05-09');
    assert.equal(tree[0].children[0].label, 'Main Hall');
    assert.equal(tree[0].children[0].children[0].label, 'Flash Talks');
    assert.deepEqual(tree[0].children[0].children[0].slots.map(item => item.seq), [1, 2]);
});

test('tree preserves disabled slots and separates sessionless common-room programs', () => {
    const data = program([slot(1, '16:05:00', { enabled: false }),
        slot(2, '16:10:00', { parentSeq: null, roomSeq: null, scopeType: 'ALL_ROOMS' })]);
    data.days[0] = { ...data.days[0], eventDate: '2027-05-09', dayNumber: 1, sortOrder: 0 };
    const rooms = buildAbstractProgramTree(data)[0].children;
    assert.equal(rooms.length, 2);
    assert.equal(rooms[0].children[0].slots[0].enabled, false);
    assert.equal(rooms[1].label, '전체 룸 공통');
    assert.equal(rooms[1].children[0].label, '세션 미지정');
    assert.equal(rooms[1].children[0].slots[0].seq, 2);
});
