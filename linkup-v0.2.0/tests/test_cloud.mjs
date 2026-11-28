import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Supabase,Drive} from '../ui/cloud.js';
const user={id:'11111111-1111-4111-8111-111111111111'};
const json=(data,status=200,headers={})=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json',...headers}});
test('Supabase: login, refresh, authenticated pagination, insert, deletion',async()=>{
 let auth=0,rows=[],lastSession,requests=[];
 globalThis.fetch=async (url,options={})=>{requests.push([url,options]);if(url.includes('/auth/v1/token')){auth++;return json({user,access_token:'token'+auth,refresh_token:'refresh'+auth,expires_in:3600});}assert.equal(options.headers.Authorization,'Bearer token2');if(options.method==='POST'){rows.push(JSON.parse(options.body));return new Response(null,{status:201});}if(options.method==='PATCH'){Object.assign(rows[0],JSON.parse(options.body));return new Response(null,{status:204});}return json(rows);};
 const s=new Supabase('https://test.supabase.co','public',null,v=>lastSession=v);await s.login('test@example.com','test');assert.equal(lastSession.user.id,user.id);s.session.expires_at=0;await s.insert({id:'id',kind:'text',text_content:'hello'});assert.equal(auth,2);assert.equal((await s.list())[0].text_content,'hello');await s.tombstone('id');assert.equal(rows[0].text_content,null);assert.ok(rows[0].deleted_at);assert.ok(requests.at(-1)[0].includes('deleted_at=is.null'));
});
test('Drive: binary chunk upload, duplicate lookup, download, delete',async()=>{
 const payload=new Uint8Array(8*1024*1024+15);payload[0]=255;payload[payload.length-1]=44;let sent=[],found=false,deleted=false,progs=[];
 globalThis.fetch=async(url,o={})=>{assert.equal(o.headers.Authorization,'Bearer drive');if(url.includes('spaces=appDataFolder'))return json({files:found?[{id:'cloud-file'}]:[]});if(url.includes('uploadType=resumable')){assert.equal(JSON.parse(o.body).parents[0],'appDataFolder');return json({},200,{Location:'https://www.googleapis.com/upload-session'});}if(url.includes('www.googleapis.com/upload-session')){const b=new Uint8Array(await o.body.arrayBuffer());sent.push(b);if(sent.length===1)return new Response(null,{status:308,headers:{Range:`bytes=0-${b.length-1}`}});found=true;return json({id:'cloud-file'});}if(o.method==='DELETE'){deleted=true;return new Response(null,{status:204});}if(url.includes('alt=media'))return new Response(payload);throw Error(url);};
 const d=new Drive('client');d.token='drive';d.expiry=Date.now()+3600000;const item={id:'11111111-1111-4111-8111-111111111111',file_name:'any.bin',mime_type:'application/octet-stream'};assert.equal(await d.upload(new Blob([payload]),item,p=>progs.push(p)),'cloud-file');assert.equal(sent[0].length,8*1024*1024);assert.equal(sent[1].length,15);assert.equal(sent[0][0],255);assert.equal(sent[1][14],44);assert.equal(progs.at(-1),100);assert.equal(await d.upload(new Blob([payload]),item,()=>{}),'cloud-file');assert.equal(sent.length,2);assert.deepEqual(new Uint8Array(await (await d.download('cloud-file')).arrayBuffer()),payload);await d.remove('cloud-file');assert.ok(deleted);
});
test('Drive refuses unauthenticated access and tolerates already deleted object',async()=>{const d=new Drive('client');await assert.rejects(d.download('x'),/Подключите/);d.token='token';d.expiry=Date.now()+100000;globalThis.fetch=async()=>json({error:{message:'not found'}},404);await d.remove('x');});
test('Supabase: paginated history and realtime notification',async()=>{
 let calls=0;globalThis.fetch=async()=>json(++calls===1?Array.from({length:500},(_,id)=>({id})): [{id:500}]);const s=new Supabase('https://test.supabase.co','public',{user,access_token:'token',expires_at:Math.floor(Date.now()/1000)+3600},()=>{});assert.equal((await s.list()).length,501);assert.equal(calls,2);
 const original=globalThis.WebSocket;class FakeSocket {constructor(url){this.url=url;this.readyState=1;this.sent=[];}send(data){this.sent.push(JSON.parse(data));}close(){this.readyState=3;this.onclose?.();}}globalThis.WebSocket=FakeSocket;let changes=0,connected=false;
 try{await s.subscribe(()=>changes++,v=>connected=v);const ws=s.ws;ws.onopen();assert.match(ws.url.href,/realtime\/v1\/websocket/);const join=ws.sent[0];assert.equal(join.event,'phx_join');assert.equal(join.payload.config.postgres_changes[0].filter,`user_id=eq.${user.id}`);ws.onmessage({data:JSON.stringify({event:'phx_reply',ref:join.ref,payload:{status:'ok'}})});assert.equal(connected,true);ws.onmessage({data:JSON.stringify({event:'postgres_changes'})});assert.equal(changes,2);}finally{s.close();globalThis.WebSocket=original;}
});

test('insecure endpoints and privileged keys are rejected before any request', async t => {
    const calls=t.mock.method(globalThis,'fetch',async()=>{throw new Error('unexpected network');});
    assert.throws(()=>new Supabase('http://example.test','public',null,()=>{}),/HTTPS/);
    assert.throws(()=>new Supabase('https://example.test','sb_secret_fixture',null,()=>{}),/publishable/);
    const claims=Buffer.from(JSON.stringify({role:'service_role'})).toString('base64url');
    assert.throws(()=>new Supabase('https://example.test','header.'+claims+'.signature',null,()=>{}),/publishable/);
    assert.equal(calls.mock.callCount(),0);
});

test('account revocation aborts requests and rejects data before another network call', async t => {
    let signal;
    const calls=t.mock.method(globalThis,'fetch',(_url,options)=>{
        signal=options.signal;
        assert.equal(options.redirect,'error');
        return new Promise((_,reject)=>signal.addEventListener('abort',()=>reject(new Error('aborted')),{once:true}));
    });
    const client=new Supabase('https://example.test','public',
        {user,access_token:'synthetic',expires_at:Date.now()/1000+900},()=>{});
    const pending=client.list();
    await Promise.resolve();
    client.disconnect();
    await assert.rejects(pending);
    await assert.rejects(client.insert({text_content:'canary'}),/закрыто/);
    assert.equal(signal.aborted,true);
    assert.equal(calls.mock.callCount(),1);
});

test('Google Drive never forwards the bearer token to a supplied foreign upload host', async t => {
    const visited=[];
    t.mock.method(globalThis,'fetch',async(url)=>{
        visited.push(url);
        if(url.includes('spaces='))return json({files:[]});
        return json({},200,{Location:'https://example.test/upload'});
    });
    const drive=new Drive('fixture');
    await drive.connect('synthetic');
    await assert.rejects(drive.upload(new Blob(['canary']),{id:'fixture',file_name:'fixture'},()=>{}),/доверенный/);
    assert.equal(visited.length,2);
    assert.ok(visited.every(url=>new URL(url).hostname==='www.googleapis.com'));
});
