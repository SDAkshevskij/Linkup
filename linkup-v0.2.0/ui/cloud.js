export class CloudError extends Error{constructor(message,status=0){super(message);this.status=status;}}
async function checked(response){
 if(!response.ok)throw new CloudError(`HTTP ${response.status}`,response.status);
 return response;
}

export function assertPublicKey(key){
 key=key.trim();
 if(/^sb_secret_/i.test(key))throw new Error('Используйте только publishable или anon ключ Supabase');
 const parts=key.split('.');
 if(parts.length===3){
  let claims;
  try{claims=JSON.parse(atob(parts[1].replace(/-/g,'+').replace(/_/g,'/')));}
  catch{throw new Error('Некорректный ключ Supabase');}
  if(claims?.role!=='anon')throw new Error('Используйте только publishable или anon ключ Supabase');
 }
}

function https(url){
 const parsed=new URL(url);
 if(parsed.protocol!=='https:'||parsed.username||parsed.password)throw new Error('Требуется HTTPS');
 return parsed;
}

function secureFetch(url, options={}, googleResume=false){
 const endpoint=https(url);
 // Google uses the redirect status 308 for an incomplete upload even without Location.
 // The native shells' CSP enforces HTTPS on redirects as well.
 return fetch(url,{...options,redirect:googleResume&&endpoint.origin==='https://www.googleapis.com'?'follow':'error'});
}
// Keep disconnect, caller cancellation and the deadline active until the body is read.
async function requestWithin(url, options, lifetime, consume, timeout, googleResume=false){
 const {signal:caller,...request}=options;
 const controller=new AbortController();
 const sources=[...new Set([lifetime,caller].filter(Boolean))];
 let cause;
 const abort=error=>{if(!controller.signal.aborted){cause=error;controller.abort();}};
 const stop=()=>abort(new Error('Подключение закрыто или запрос отменён'));
 if(sources.some(signal=>signal.aborted))stop();
 if(controller.signal.aborted)throw cause;
 for(const signal of sources)signal.addEventListener('abort',stop,{once:true});
 let rejectAbort;
 const stopped=new Promise((_,reject)=>{rejectAbort=()=>reject(cause);});
 controller.signal.addEventListener('abort',rejectAbort,{once:true});
 const timer=setTimeout(()=>abort(new CloudError('Сервер не ответил вовремя',408)),timeout);
 try{
  return await Promise.race([secureFetch(url,{...request,signal:controller.signal},googleResume).then(consume),stopped]);
 }finally{
  clearTimeout(timer);
  controller.signal.removeEventListener('abort',rejectAbort);
  for(const signal of sources)signal.removeEventListener('abort',stop);
 }
}
const responseJson=async response=>(await checked(response)).json();
const responseBlob=async response=>(await checked(response)).blob();
const checkOnly=async response=>{
 await checked(response);
 // Header-only operations have no reason to retain a response body.
 response.body?.cancel().catch(()=>{});
 return response;
};
const validSession=s=>typeof s?.user?.id==='string'&&s.user.id.length>0
 &&typeof s.access_token==='string'&&s.access_token.length>0
 &&typeof s.refresh_token==='string'&&s.refresh_token.length>0;
export class Supabase{
 constructor(url,key,session,onSession){const endpoint=https(url);assertPublicKey(key);
 if(endpoint.pathname!=='/'||endpoint.search||endpoint.hash)throw new Error('Некорректный адрес проекта');
 this.cancellation=new AbortController();this.requestTimeout=30000;this.url=endpoint.origin;this.key=key;this.session=session;this.onSession=onSession;this.ws=null;this.timer=null;this.seq=0;}
 async auth(path,body,signal){
  return requestWithin(`${this.url}/auth/v1/${path}`,{signal,method:'POST',
   headers:{apikey:this.key,'Content-Type':'application/json'},body:JSON.stringify(body)},
   this.cancellation.signal,responseJson,this.requestTimeout);
 }
 setSession(s){if(this.cancellation.signal.aborted)throw new Error('Подключение закрыто');this.session=s?{...s,expires_at:s.expires_at||Math.floor(Date.now()/1000)+(s.expires_in||3600)}:null;this.onSession(this.session);}
 async login(email,password,signal){
  const session=await this.auth('token?grant_type=password',{email,password},signal);
  if(!validSession(session))throw new CloudError('Некорректная сессия Supabase',502);
  this.setSession(session);
  return session.user;
 }
 async token(){
  if(!this.session)throw new CloudError('Войдите в Supabase',401);
  if(this.session.expires_at>Date.now()/1000+60)return this.session.access_token;
  if(!this.refreshing){
   const original=this.session;
   this.refreshing=this.auth('token?grant_type=refresh_token',{refresh_token:original.refresh_token}).then(session=>{
    if(!validSession(session)||session.user.id!==original.user.id||this.session!==original){
     throw new CloudError('Supabase вернул другую сессию',502);
    }
    this.setSession(session);
    if(this.ws?.readyState===1)this.send('realtime:linkup','access_token',{access_token:session.access_token});
    return session.access_token;
   }).finally(()=>this.refreshing=null);
  }
  return this.refreshing;
 }
 async request(path,opts={},consume=checkOnly){
  const token=await this.token();
  return requestWithin(`${this.url}/rest/v1/${path}`,{...opts,headers:{apikey:this.key,
   Authorization:`Bearer ${token}`,'Content-Type':'application/json',...opts.headers}},
   this.cancellation.signal,async response=>consume(await checked(response)),this.requestTimeout);
 }
 async list(){
  const rows=[];
  const seen=new Set();
  let cursor='';
  for(;;){
   const next=cursor?'&id=gt.'+encodeURIComponent(cursor):'';
   const batch=await this.request('linkup_items?select=*&order=id.asc&limit=500'+next,{},responseJson);
   if(!Array.isArray(batch)||batch.length>500)throw new CloudError('Некорректный список Transfer',502);
   for(const row of batch){
    const id=row?.id;
    if(id==null||seen.has(id))throw new CloudError('Повтор страницы Transfer',502);
    seen.add(id);
    rows.push(row);
   }
   if(batch.length<500)return rows;
   cursor=String(batch[batch.length-1].id);
  }
 }
 async insert(row){await this.request('linkup_items?on_conflict=id',{method:'POST',headers:{Prefer:'resolution=ignore-duplicates,return=minimal'},body:JSON.stringify(row)});}
 async tombstone(id){await this.request(`linkup_items?id=eq.${encodeURIComponent(id)}&deleted_at=is.null`,{method:'PATCH',body:JSON.stringify({deleted_at:new Date().toISOString(),text_content:null})});}
 async cleanup(id){await this.request(`linkup_items?id=eq.${encodeURIComponent(id)}&deleted_at=not.is.null`,{method:'PATCH',body:JSON.stringify({storage_deleted_at:new Date().toISOString()})});}
 send(topic,event,payload){if(this.ws?.readyState===1){const ref=String(++this.seq);this.ws.send(JSON.stringify({topic,event,payload,ref}));return ref;}}
 async subscribe(onChange,onState){this.close();const generation=this.generation;try{const token=await this.token();if(this.generation!==generation)return;const u=new URL(this.url);u.protocol='wss:';u.pathname='/realtime/v1/websocket';u.search=new URLSearchParams({apikey:this.key,vsn:'1.0.0'});const ws=this.ws=new WebSocket(u);ws.onopen=()=>{if(this.ws!==ws)return;const join=this.send('realtime:linkup','phx_join',{config:{broadcast:{self:false},presence:{key:''},postgres_changes:[{event:'*',schema:'public',table:'linkup_items',filter:`user_id=eq.${this.session.user.id}`}]},access_token:token});this.joinRef=join;this.timer=setInterval(()=>this.send('phoenix','heartbeat',{}),25000);};ws.onmessage=e=>{if(this.ws!==ws)return;try{const m=JSON.parse(e.data);if(m.event==='postgres_changes')onChange();if(m.event==='phx_reply'&&m.ref===this.joinRef){onState(m.payload?.status==='ok');onChange();}if(m.event==='phx_error'||m.event==='phx_close')ws.close();}catch{}};ws.onclose=()=>{if(this.ws!==ws)return;clearInterval(this.timer);this.ws=null;onState(false);};ws.onerror=()=>ws.close();}catch{onState(false);}}
 disconnect(){this.cancellation.abort();this.close();}
 close(){this.generation=(this.generation||0)+1;clearInterval(this.timer);if(this.ws){const ws=this.ws;this.ws=null;ws.close();}}
}
export class Drive{
 constructor(clientId){this.cancellation=new AbortController();this.requestTimeout=30000;this.clientId=clientId;this.token=null;this.expiry=0;}
 get ready(){return Boolean(this.token&&this.expiry>Date.now()+30000);}
 disconnect(){this.cancellation.abort();this.token=null;}
 async connect(token){if(!this.clientId)throw Error('Укажите Google OAuth Client ID');if(!token)throw Error('Введите OAuth access token');this.token=token.trim();this.expiry=Date.now()+55*60000;}
 async request(url,opts={},consume=checkOnly){if(!this.ready)throw new CloudError('Подключите Google Drive',401);try{return await requestWithin(url,{...opts,headers:{Authorization:`Bearer ${this.token}`,...opts.headers}},this.cancellation.signal,async response=>consume(await checked(response)),this.requestTimeout);}catch(e){if(e.status===401)this.token=null;throw e;}}
 async find(id){const q=`trashed = false and appProperties has { key='linkup_id' and value='${id}' }`;const data=await this.request(`https://www.googleapis.com/drive/v3/files?${new URLSearchParams({spaces:'appDataFolder',q,fields:'files(id,size),nextPageToken',pageSize:'100'})}`,{},responseJson);return data.files[0]?.id||null;}
 async upload(file,item,progress){const found=await this.find(item.id);if(found)return found;const meta={name:item.file_name,parents:['appDataFolder'],appProperties:{linkup_id:item.id}};if(file.size===0){const boundary='linkup_'+crypto.randomUUID();const body=new Blob([`--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify(meta)}\r\n--${boundary}\r\nContent-Type: application/octet-stream\r\n\r\n`,file,`\r\n--${boundary}--`]);return (await this.request('https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id',{method:'POST',headers:{'Content-Type':`multipart/related; boundary=${boundary}`},body},responseJson)).id;}
 const start=await this.request('https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id',{method:'POST',headers:{'Content-Type':'application/json; charset=UTF-8','X-Upload-Content-Type':item.mime_type||'application/octet-stream','X-Upload-Content-Length':String(file.size)},body:JSON.stringify(meta)});const location=start.headers.get('Location');if(!location||https(location).origin!=='https://www.googleapis.com')throw Error('Drive не вернул доверенный адрес загрузки');const chunk=8*1024*1024;let offset=0;while(offset<file.size){const end=Math.min(offset+chunk,file.size);if(!this.ready)throw new CloudError('Срок доступа к Drive истёк',401);const r=await requestWithin(location,{method:'PUT',headers:{Authorization:`Bearer ${this.token}`,'Content-Type':item.mime_type||'application/octet-stream','Content-Range':`bytes ${offset}-${end-1}/${file.size}`},body:file.slice(offset,end)},this.cancellation.signal,async response=>{if(response.status===308)return response;if(response.status===401)this.token=null;return {data:await responseJson(response)};},this.requestTimeout,true);if(r.status===308){const range=r.headers.get('Range');const next=range?Number(range.split('-').pop())+1:0;if(!Number.isFinite(next)||next<=offset||next>end)throw Error('Drive не подтвердил загрузку');offset=next;progress(Math.floor(offset/file.size*100));continue;}const {data}=r;if(!data.id)throw Error('Drive не подтвердил файл');progress(100);return data.id;}throw Error('Загрузка не завершена');}
 async download(id){if(id.startsWith('yd:'))throw Error('Этот файл хранится на Яндекс Диске');return this.request(`https://www.googleapis.com/drive/v3/files/${encodeURIComponent(id)}?alt=media`,{},responseBlob);}
 async remove(id){if(id.startsWith('yd:'))throw Error('Этот файл хранится на Яндекс Диске');try{await this.request(`https://www.googleapis.com/drive/v3/files/${encodeURIComponent(id)}`,{method:'DELETE'});}catch(e){if(e.status!==404)throw e;}}
}

export class YandexDrive {
 constructor(clientId){this.cancellation=new AbortController();this.requestTimeout=30000;this.clientId=clientId;this.token=null;this.expiry=0;}
 get ready(){return !!this.token;}
 disconnect(){this.cancellation.abort();this.token=null;}
 async connect(token){if(!this.clientId)throw Error('Укажите Client ID OAuth-приложения Яндекс');if(!token)throw Error('Введите OAuth access token');this.token=token.trim();}
 async api(path,opts={},consume=checkOnly){if(!this.ready)throw new CloudError('Подключите Яндекс Диск',401);try{return await requestWithin('https://cloud-api.yandex.net/v1/disk'+path,{...opts,headers:{Authorization:`OAuth ${this.token}`,...opts.headers}},this.cancellation.signal,async response=>consume(await checked(response)),this.requestTimeout);}catch(e){if(e.status===401)this.token=null;throw e;}}
 async identity(signal){const data=await this.api('',{signal},responseJson);if(!data.user?.uid)throw Error('Не удалось определить аккаунт Яндекс Диска');return 'yandex:'+this.clientId+':'+data.user.uid;}
 path(id){if(!/^[a-f0-9-]{36}$/i.test(id))throw Error('Некорректный ID файла');return 'app:/'+id;}
 async find(id){try{await this.api('/resources?'+new URLSearchParams({path:this.path(id)}));return 'yd:'+id;}catch(e){if(e.status===404)return null;throw e;}}
 async upload(file,item,progress){const found=await this.find(item.id);if(found)return found;const q=new URLSearchParams({path:this.path(item.id),overwrite:'false'});const d=await this.api('/resources/upload?'+q,{},responseJson);if(!d.href||new URL(d.href).protocol!=='https:')throw Error('Не получен URL загрузки');await requestWithin(d.href,{method:'PUT',body:file},this.cancellation.signal,checkOnly,this.requestTimeout);progress(100);return 'yd:'+item.id;}
 async download(id){if(!id.startsWith('yd:'))throw Error('Этот файл хранится в Google Drive');const d=await this.api('/resources/download?'+new URLSearchParams({path:this.path(id.slice(3))}),{},responseJson);if(new URL(d.href).protocol!=='https:')throw Error('Некорректный URL');return requestWithin(d.href,{},this.cancellation.signal,responseBlob,this.requestTimeout);}
 async remove(id){if(!id.startsWith('yd:'))throw Error('Этот файл хранится в Google Drive');try{await this.api('/resources?'+new URLSearchParams({path:this.path(id.slice(3)),permanently:'true'}),{method:'DELETE'});}catch(e){if(e.status!==404)throw e;}}
}
