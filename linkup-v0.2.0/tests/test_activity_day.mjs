import assert from 'node:assert/strict';
import {test} from 'node:test';
import {clipToDay, dayScale, localDay, stepDay} from '../ui/activity-day.js';

const actualDate = Date;
let fixtureNumber = 0;

async function withActivity(t, timezone, initial, check) {
    const timezoneBefore = process.env.TZ;
    const globals = {Date, setInterval, window: globalThis.window, document: globalThis.document};
    t.after(() => {
        if (timezoneBefore === undefined) delete process.env.TZ;
        else process.env.TZ = timezoneBefore;
        Object.assign(globalThis, globals);
    });
    process.env.TZ = timezone;
    let now = initial;
    globalThis.Date = class extends actualDate {
        constructor(...args) { super(...(args.length ? args : [now])); }
        static now() { return +new actualDate(now); }
    };
    const nodes = new Map();
    function element() {
        return {
            value: '', dataset: {}, hidden: false, innerHTML: '', textContent: '',
            classList: {toggle() {}}, replaceChildren() {}, append() {},
            querySelectorAll() { return []; }, setAttribute() {},
        };
    }
    const get = id => {
        if (!nodes.has(id)) nodes.set(id, element());
        return nodes.get(id);
    };
    const requests = [];
    const nav = element();
    nav.dataset.page = 'activity';
    globalThis.document = {
        body: element(), getElementById: get, createElement: element,
        addEventListener() {},
        querySelectorAll(selector) { return selector === '[data-page]' ? [nav] : []; },
    };
    globalThis.setInterval = () => {};
    globalThis.window = {
        qt: {webChannelTransport: {}},
        QWebChannel: function(_transport, ready) {
            ready({objects: {native: {call(action, raw, resolve) {
                assert.equal(action, 'activity');
                requests.push({...JSON.parse(raw), resolve});
            }}}});
        },
    };
    Object.assign(globalThis, {qt: window.qt, QWebChannel: window.QWebChannel});
    t.after(() => { delete globalThis.qt; delete globalThis.QWebChannel; });
    const {initActivity} = await import(`../ui/activity.js?day-fixture=${++fixtureNumber}`);
    initActivity(() => ({}));
    const settle = () => new Promise(resolve => setImmediate(resolve));
    const finish = async (request, rows = []) => {
        request.resolve(JSON.stringify({value: {rows, status: {enabled: true, platform: 'android'}}}));
        await settle();
    };
    const fail = async request => {
        request.resolve(JSON.stringify({error: 'fixture error'}));
        await settle();
    };
    nav.onclick();
    await settle();
    await check({get, requests, settle, finish, fail, clock(value) { now = value; }});
}

test('resuming the live day crosses midnight without restarting the view', async t => {
    await withActivity(t, 'Europe/Moscow', '2026-01-11T23:59:12', async fixture => {
        const {requests, get, clock, settle, finish} = fixture;
        await finish(requests[0]);
        clock('2026-01-12T00:00:08');
        window.linkupActivityResume();
        await settle();
        assert.equal(requests.at(-1).from, +new actualDate('2026-01-12T00:00:00'));
        assert.equal(get('actDate').value, '2026-01-12');
    });
});

test('query ends at the next independent midnight after a midnight clock gap', async t => {
    await withActivity(t, 'America/Sao_Paulo', '2018-11-04T12:00:00', async ({requests}) => {
        const [{from, to}] = requests;
        assert.equal(from, Date.parse('2018-11-04T03:00:00Z'));
        assert.equal(to, Date.parse('2018-11-05T02:00:00Z'));
    });
});

test('a previous-day query cannot show figures from today while it is pending', async t => {
    await withActivity(t, 'UTC', '2026-04-10T12:00:00', async ({requests, get, finish, settle}) => {
        const from = requests[0].from;
        const row = {device_id: 'fixture', device_name: 'Example',
            kind: 'app', app: 'Browser', start_ms: from + 10, end_ms: from + 1020};
        await finish(requests[0], [row]);
        assert.equal(get('actApps').textContent, 1);
        get('actPrev').onclick();
        await settle();
        assert.notEqual(get('actApps').textContent, 1);
        get('actNext').onclick();
        await settle();
        await finish(requests[2], [row]);
        await finish(requests[1], []);
        assert.equal(get('actApps').textContent, 1);
    });
});

test('an explicit past day stays fixed after midnight and stays usable after a failed refresh', async t => {
    await withActivity(t, 'UTC', '2024-03-01T23:58:00', async ({requests, get, settle, finish, fail, clock}) => {
        await finish(requests[0]);
        get('actPrev').onclick();
        await settle();
        assert.equal(get('actDate').value, '2024-02-29');
        await finish(requests[1], [{device_id: 'fixture', device_name: 'Example',
            kind: 'app', app: 'Browser', start_ms: requests[1].from + 10, end_ms: requests[1].from + 1020}]);
        clock('2024-03-02T00:05:00');
        window.linkupActivityResume();
        window.linkupActivityResume();
        await settle();
        assert.equal(requests.length, 3);
        assert.equal(requests[2].from, requests[1].from);
        await fail(requests[2]);
        assert.equal(get('actApps').textContent, 1);
        get('actRefresh').onclick();
        await settle();
        await finish(requests[3]);
        assert.equal(get('actApps').textContent, 0);
    });
});

test('the refresh button lets the user recover from a local bridge request that never answers', async t => {
    await withActivity(t, 'UTC', '2026-04-10T12:00:00Z', async ({requests, get, settle, finish}) => {
        get('actRefresh').onclick();
        await settle();
        assert.equal(requests.length, 2);
        await finish(requests[1]);
        assert.equal(get('actApps').textContent, 0);
        await finish(requests[0],[{device_id:'obsolete',device_name:'Old device',
            kind:'app',app:'Old app',start_ms:requests[0].from+10,end_ms:requests[0].from+90}]);
        assert.equal(get('actApps').textContent, 0);
    });
});

test('a timezone change replaces an in-flight day with the same label and different boundaries', async t => {
    await withActivity(t, 'UTC', '2026-04-10T12:00:00Z', async ({requests, get, settle, finish}) => {
        process.env.TZ = 'Europe/Moscow';
        window.linkupActivityResume();
        await settle();
        assert.equal(get('actDate').value, '2026-04-10');
        assert.equal(requests.length, 2);
        assert.equal(requests[1].from, requests[0].from - 3 * 3_600_000);
        const row={device_id:'fixture',device_name:'<&"',kind:'app',app:'"<fixture>',
            start_ms:requests[1].from,end_ms:requests[1].from+62000};
        await finish(requests[1],[row]);
        await finish(requests[0]);
        assert.equal(get('actApps').textContent, 1);
        assert.match(get('actTimeline').innerHTML, /&quot;&lt;fixture&gt;/);
        assert.doesNotMatch(get('actTimeline').innerHTML, /<fixture>/);
    });
});

test('short, long and half-hour days have a ruler for their actual elapsed time', t => {
    const before = process.env.TZ;
    t.after(() => { if (before === undefined) delete process.env.TZ; else process.env.TZ = before; });
    for (const [zone, day, hours] of [
        ['Europe/Berlin', '2024-03-31', 23],
        ['Europe/Berlin', '2024-10-27', 25],
        ['Australia/Lord_Howe', '2024-10-06', 23.5],
        ['Australia/Lord_Howe', '2024-04-07', 24.5],
        ['America/Sao_Paulo', '2018-11-04', 23],
    ]) {
        process.env.TZ = zone;
        const range = localDay(day);
        const {cells,ticks} = dayScale(range);
        assert.equal(range.to - range.from, hours * 3_600_000, zone + day);
        assert.equal(cells.length, Math.ceil(hours));
        assert.equal(ticks[0], range.from);
        assert.equal(ticks.at(-1), range.to);
    }
    process.env.TZ = 'Pacific/Apia';
    assert.equal(localDay('2011-12-30'), null);
    assert.equal(stepDay(localDay('2011-12-29'), 1).key, '2011-12-31');
    assert.equal(stepDay(localDay('2011-12-31'), -1).key, '2011-12-29');
    for (const invalid of ['2025-02-29','2024-02-30','2026-2-01','invalid']) assert.equal(localDay(invalid), null);
});

test('seeded day and clipping properties across local time zones', t => {
    const before = process.env.TZ;
    t.after(() => { if (before === undefined) delete process.env.TZ; else process.env.TZ = before; });
    let seed = 0x3744392e;
    function draw(n) {
        seed = (Math.imul(1664525, seed) + 1013904223) >>> 0;
        return Math.floor(seed / 0x1_0000_0000 * n);
    }
    for (const zone of ['UTC', 'Europe/Moscow', 'America/New_York', 'America/Sao_Paulo', 'Australia/Lord_Howe']) {
        process.env.TZ = zone;
        for (let trial = 0; trial < 180; trial++) {
            const year = 2000 + draw(33), month = draw(12), day = 1 + draw(28);
            const key = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
            const label = `${zone} ${key} seed=${seed}`;
            const expectedStart = +new actualDate(year, month, day);
            const expectedEnd = +new actualDate(year, month, day + 1);
            const range = localDay(key);
            assert.deepEqual(range, {key, from: expectedStart, to: expectedEnd}, label);
            const {cells,ticks} = dayScale(range);
            assert.deepEqual(cells.map(cell => cell.end), [...cells.slice(1).map(cell => cell.start), expectedEnd], label);
            assert.equal(cells[0].start, expectedStart, label);
            assert.ok(ticks.slice(1).every((value,i) => value > ticks[i]), label);

            const step=900_000, start=expectedStart+(draw(170)-36)*step, length=draw(80);
            const end=start+length*step;
            const clipped=clipToDay([{start_ms:start,end_ms:end}],range);
            const covered=Array.from({length},(_,i)=>start+i*step)
                .filter(time=>time>=expectedStart&&time<expectedEnd);
            assert.equal(clipped.reduce((sum,row)=>sum+row.end_ms-row.start_ms,0), covered.length*step, label);
            assert.deepEqual(clipToDay([{start_ms:expectedEnd,end_ms:expectedEnd}],range), [], label);
            assert.equal(clipToDay([{start_ms:expectedStart,end_ms:expectedStart}],range).length, 1, label);
        }
    }
});
