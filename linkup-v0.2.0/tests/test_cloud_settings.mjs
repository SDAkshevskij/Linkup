import {test} from 'node:test';
import assert from 'node:assert/strict';
import {YandexDrive} from '../ui/cloud.js';
import {cloudStep, projectUrl, signIn, verifyDisk} from '../ui/cloud-settings.js';

test('settings validate project URL and bound stalled connections', async () => {
    assert.equal(projectUrl(' https://example.test/ '), 'https://example.test');
    for (const invalid of ['supabase.co', 'http://example.test', 'https://x:y@example.test', 'https://example.test/auth']) {
        assert.throws(() => projectUrl(invalid), /Supabase/);
    }
    let signal;
    await assert.rejects(cloudStep('Supabase', s => {
        signal = s;
        return new Promise(() => {});
    }, 10), /Supabase.*вовремя/);
    assert.equal(signal.aborted, true);
});

test('Supabase reports auth and schema failures separately from success', async t => {
    const steps = [];
    const config = {url:'https://example.test', key:'test', email:'owner@example.test'};
    let authStatus = 400, schemaStatus = 404;
    const session = {user:{id:'synthetic'}, access_token:'fake', refresh_token:'fake'};
    t.mock.method(globalThis, 'fetch', async (url, opts) => {
        assert.ok(opts.signal);
        if (url.includes('/auth/')) return Response.json(session, {status:authStatus});
        assert.match(url, /\/rest\/v1\/linkup_items/);
        return Response.json([], {status:schemaStatus});
    });
    await assert.rejects(signIn(config, 'fake', s=>steps.push(s)), /Supabase.*HTTP 400/);
    assert.deepEqual(steps, []);
    authStatus = 200;
    await assert.rejects(signIn(config, 'fake', s=>steps.push(s)), /Supabase.*HTTP 404/);
    assert.match(steps[0], /схему/);
    schemaStatus = 200;
    assert.equal((await signIn(config, 'fake', ()=>{})).user.id, 'synthetic');
});

test('Yandex token and Supabase binding failures identify the service', async t => {
    let diskStatus = 401, binding = 'another-disk';
    t.mock.method(globalThis, 'fetch', async (url, opts) => {
        assert.equal(url, 'https://cloud-api.yandex.net/v1/disk');
        assert.ok(opts.signal);
        return Response.json({user:{uid:'123'}}, {status:diskStatus});
    });
    const client = {session:{user:{id:'synthetic'}}, request:async (path, opts, consume) => {
        assert.ok(opts.signal);
        const response=Response.json([{drive_identity:binding}]);
        return consume?consume(response):response;
    }};
    const steps = [];
    const connect = () => verifyDisk(client, new YandexDrive('id'), 'yandex', 'fake', s=>steps.push(s));
    await assert.rejects(connect(), /Яндекс Диск.*HTTP 401/);
    diskStatus = 200;
    await assert.rejects(connect(), /Supabase.*тот же аккаунт/);
    binding = 'yandex:id:123';
    await connect();
    assert.ok(steps.some(s=>s.includes('привязку')));
});
