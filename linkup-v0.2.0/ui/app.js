import {all,get,put,saveItems,mutateItem,consumeInbox} from './db.js';
import {acknowledgeUpload,deleteLocally,mergeRemote} from './transfer-state.js';
import {Supabase,Drive,YandexDrive,assertPublicKey} from './cloud.js';
import {projectUrl,signIn,verifyDisk,cloudStep} from './cloud-settings.js';
import {native,initActivity} from './activity.js';
const $=id=>document.getElementById(id);const icon=name=>`<svg aria-hidden="true"><use href="#i-${name}"/></svg>`;const read=(k,f)=>{try{return JSON.parse(localStorage.getItem(k))??f}catch{return f}};const write=(k,v)=>localStorage.setItem(k,JSON.stringify(v));
if(!crypto.randomUUID)crypto.randomUUID=()=>{const a=crypto.getRandomValues(new Uint8Array(16));a[6]=(a[6]&15)|64;a[8]=(a[8]&63)|128;return [...a].map((x,i)=>([4,6,8,10].includes(i)?'-':'')+x.toString(16).padStart(2,'0')).join('')};
const mobile=/Android|iPhone|iPad/i.test(navigator.userAgent);let config=read('linkup.config',{deviceName:mobile?'Телефон':'Компьютер',deviceId:crypto.randomUUID(),url:'',key:'',email:'',googleId:''});if(!config.diskProvider)config.diskProvider=config.googleId?'google':'yandex';write('linkup.config',config);
function makeDrive(settings=config){return settings.diskProvider==='google'?new Drive(settings.googleId):new YandexDrive(settings.googleId);}
let session=read('linkup.session',null), sb=null, drive=makeDrive(), scope='local', items=[],filter='all',staged=[],syncing=false,again=false,realtime=false,lastOk=false,syncError='',generation=0,progress=new Map(),toastTimer,confirmAction,viewed,retentionBusy=false,composing=false;let verifiedDrive=false;
const legacyEnabled=()=>!!sb&&scope!=='local'&&config.legacyScope===scope;
const size=n=>{if(n<1024)return `${n} Б`;const units=['КБ','МБ','ГБ','ТБ'];let v=n/1024,i=0;while(v>=1024&&i<3){v/=1024;i++;}return `${v>=100?Math.round(v):Number(v.toFixed(1))} ${units[i]}`};const active=()=>items.filter(x=>!x.deleted_at);const localKey=(id,s=scope)=>`${s}:${id}`;const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function toast(msg,error=false){$('toast').textContent=msg;$('toast').className=`toast${error?' error':''}`;$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,4200);}
function setStatus(){const connected=!!sb&&lastOk&&navigator.onLine;const label=!legacyEnabled()?'Локально':!navigator.onLine?'Офлайн':syncing?'Синхронизация':syncError?'Ошибка синхронизации':connected?realtime?'В сети':'В сети · опрос':'Подключение';$('status').querySelector('span').textContent=label;$('status').classList.toggle('on',connected);$('status').title=syncError||label;$('sideStatus').textContent=label;$('sideDot').classList.toggle('on',connected);$('deviceLabel').textContent=config.deviceName;$('footerStatus').textContent=legacyEnabled()?(verifiedDrive&&drive.ready?'Supabase + Drive · без E2EE':'Supabase · без E2EE'):'Сохранение на устройстве';$('storageLabel').textContent=legacyEnabled()?'Supabase / облачный диск':'Локальное хранилище';$('sync').classList.toggle('spin',syncing);}
async function reload(){const current=scope, revision=generation;const rows=await all('items');if(revision!==generation)return;items=rows.filter(x=>x.scope==='local'||x.scope===current);render();}
function dayKey(d){return new Date(d).toLocaleDateString('sv-SE');}function dayTitle(d){const today=new Date(),y=new Date();y.setDate(y.getDate()-1);const k=dayKey(d);return k===dayKey(today)?'Сегодня':k===dayKey(y)?'Вчера':new Date(d).toLocaleDateString('ru-RU',{day:'numeric',month:'long',year:'numeric'});}
function render(){const data=active(),files=data.filter(x=>x.kind==='file'),total=data.reduce((a,x)=>a+Number(x.byte_size||0),0),q=$('search').value.toLowerCase().trim();const queue=items.filter(x=>x.state==='new'||x.state==='delete').length;$('countAll').textContent=data.length;$('countFiles').textContent=files.length;$('countText').textContent=data.length-files.length;$('summaryCount').textContent=`${data.length} объектов`;$('summarySize').textContent=size(total);$('summaryQueue').textContent=queue?`${queue} в очереди`:'';$('detailCount').textContent=String(data.length).padStart(2,'0');$('detailSize').textContent=size(total);const shown=data.filter(x=>(filter==='all'||x.kind===filter)&&(!q||[x.text_content,x.file_name,x.device_name].some(v=>String(v||'').toLowerCase().includes(q)))).sort((a,b)=>Date.parse(b.created_at)-Date.parse(a.created_at)||b.id.localeCompare(a.id));let html='',day='';for(const x of shown){const k=dayKey(x.created_at);if(k!==day){day=k;html+=`<div class="date-divider">${esc(dayTitle(x.created_at))}</div>`;}const text=x.kind==='text',img=x.mime_type?.startsWith('image/'),name=text?x.text_content:x.file_name;let pending=x.state==='new'?(progress.has(x.id)?`Загрузка ${progress.get(x.id)}%`:'В очереди'):'',time=new Date(x.created_at).toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit'});html+=`<article class="item" data-id="${esc(x.key)}"><div class="item-icon ${text?'':img?'image':'file'}">${icon(text?'text':img?'image':'file')}</div><div class="item-body" role="button" tabindex="0" data-action="open" aria-label="${text?'Открыть текст':'Скачать файл'}"><div class="item-title ${text?'item-preview':''}">${esc(name)}</div><div class="meta">${text?'Текст':esc(size(x.byte_size))}<span>·</span>${esc(x.device_name)}<span>·</span>${esc(time)}${pending?`<span class="pending">· ${esc(pending)}</span>`:''}</div></div><div class="item-actions"><button data-action="${text?'copy':'download'}" title="${text?'Копировать':'Скачать'}" aria-label="${text?'Копировать':'Скачать'}">${icon(text?'copy':'download')}</button><button class="remove" data-action="remove" title="Удалить" aria-label="Удалить">${icon('trash')}</button></div></article>`;}$('history').innerHTML=html||`<div class="empty">${icon(q?'search':'inbox')}<strong>${q?'Ничего не найдено':'История пуста'}</strong><small>${q?'Попробуйте другой запрос':'Добавьте файл или напишите сообщение'}</small></div>`;setStatus();}
function selectFilter(v){filter=v;document.querySelectorAll('[data-filter]').forEach(b=>b.classList.toggle('active',b.dataset.filter===v));const name={all:'Поток',file:'Файлы',text:'Текст'}[v];$('heading').innerHTML=`${name}<span>.</span>`;$('crumb').textContent=name;render();}
function stage(files){staged.push(...files.map(file=>({file})));renderStage();}function renderStage(){$('staged').hidden=!staged.length;$('staged').innerHTML=staged.map(({file:f},i)=>`<div class="stage">${icon('file')}<span>${esc(f.name)} · ${size(f.size)}</span><button type="button" data-index="${i}" aria-label="Убрать файл">${icon('x')}</button></div>`).join('');}
function resize(){const t=$('message');t.style.height='auto';t.style.height=Math.min(t.scrollHeight,160)+'px';}
async function send(){
    if(composing)return;
    const draft=$('message').value, text=draft.trim(), attachments=[...staged];
    if(!text&&!attachments.length)return;
    const bytes=new TextEncoder().encode(text).length;
    if(bytes>1048576){toast('Текст превышает 1 МБ',true);return;}
    composing=true;
    $('send').disabled=true;
    const sharing=legacyEnabled(), destination=sharing?scope:'local';
    const account=sharing?sb.session.user.id:null;
    const stamp=new Date().toISOString(), {deviceId,deviceName}=config;
    function entry(kind,extra,blob){
        const id=crypto.randomUUID();
        const item={id,key:localKey(id,destination),scope:destination,user_id:account,kind,
            device_id:deviceId,device_name:deviceName,created_at:stamp,state:sharing?'new':'local',...extra};
        return {item,blob};
    }
    try{
        const entries=attachments.map(({file})=>entry('file',{
            file_name:file.name,mime_type:file.type||'application/octet-stream',byte_size:file.size},file));
        if(text)entries.unshift(entry('text',{text_content:text,byte_size:bytes}));
        await saveItems(entries);
        if($('message').value===draft)$('message').value='';
        staged=staged.filter(attachment=>!attachments.includes(attachment));
        toast(sharing?'Добавлено в очередь · без E2EE':'Сохранено на устройстве');
        await reload().catch(()=>toast('Отправка сохранена. История пока недоступна',true));
        sync();
    }catch{
        toast('Не удалось сохранить отправку',true);
    }finally{
        renderStage();resize();composing=false;$('send').disabled=false;
    }
}
function rowOf(x){return {id:x.id,user_id:x.user_id,kind:x.kind,text_content:x.text_content??null,file_name:x.file_name??null,mime_type:x.mime_type??null,byte_size:x.byte_size,drive_id:x.drive_id??null,device_name:x.device_name,device_id:x.device_id,created_at:x.created_at};}
async function sync(force=false){
    if(!legacyEnabled()||!navigator.onLine)return;
    if(syncing){again=true;return;}
    const client=sb, disk=drive, diskVerified=verifiedDrive, runScope=scope;
    const revision=generation, account=client.session.user.id;
    const alive=()=>revision===generation&&client===sb&&legacyEnabled();
    const diskAlive=()=>alive()&&disk===drive&&disk.ready&&diskVerified;
    const transferError=error=>error.status?`Ошибка передачи (HTTP ${error.status})`:'Ошибка передачи';
    let fileError='';
    syncing=true;
    setStatus();
    try{
        const pending=(await all('items')).filter(item=>item.scope===runScope);
        for(const previous of pending){
            if(!alive())return;
            let item=await get('items',previous.key);
            if(!alive())return;
            if(!item||!['new','delete'].includes(item.state))continue;
            if(item.user_id!==account){fileError='Запись ожидает исходный аккаунт';continue;}
            try{
                if(item.state==='delete'){
                    await client.tombstone(item.id);
                    if(!alive())return;
                    if(item.kind==='file'&&!item.everSynced){
                        if(!diskAlive())continue;
                        const orphan=item.drive_id||await disk.find(item.id);
                        if(!diskAlive())continue;
                        if(orphan)await disk.remove(orphan);
                    }
                    await mutateItem(item.key,current=>alive()&&current?.state==='delete'?{...current,state:'hidden'}:undefined);
                    continue;
                }
                if(item.kind==='file'&&!item.drive_id){
                    if(!diskAlive())continue;
                    const stored=await get('blobs',item.key);
                    if(!diskAlive())continue;
                    if(!stored)throw Error('Локальный файл не найден');
                    const id=await disk.upload(stored.blob,item,percent=>{
                        if(diskAlive()){progress.set(item.id,percent);render();}
                    });
                    item=await mutateItem(item.key,current=>diskAlive()&&current&&
                        ['new','delete'].includes(current.state)?{...current,drive_id:id}:undefined);
                    if(!item)continue;
                    if(item.state==='delete'){again=true;continue;}
                }
                if(!alive())return;
                await client.insert(rowOf(item));
                await mutateItem(item.key,current=>{
                    if(!alive())return undefined;
                    if(current?.state==='delete')again=true;
                    return acknowledgeUpload(current);
                });
            }catch(error){
                if(alive())fileError=transferError(error);
            }finally{
                progress.delete(previous.id);
            }
        }
        if(!alive())return;
        const remote=await client.list();
        if(!alive())return;
        for(const row of remote){
            if(!alive())return;
            if(row.user_id!==account||typeof row.id!=='string')continue;
            const key=localKey(row.id,runScope);
            await mutateItem(key,current=>alive()?mergeRemote(current,row,key,runScope):undefined);
        }
        if(!alive())return;
        lastOk=true;syncError=fileError;
        if(!client.ws&&!client.connecting){
            client.connecting=true;
            client.subscribe(()=>{if(alive())sync();}, connected=>{
                client.connecting=false;
                if(alive()){realtime=connected;setStatus();}
            }).finally(()=>{if(!client.ws)client.connecting=false;});
        }
    }catch(error){
        if(alive()){
            lastOk=false;
            syncError=error.status===408?'Сервер не ответил вовремя':error.status?`Ошибка сети (HTTP ${error.status})`:'Облако недоступно';
            if(force)toast(syncError,true);
        }
    }finally{
        syncing=false;
        if(alive()){
            try{await reload();}catch{toast('История на устройстве недоступна',true);}
            if(force&&!syncError)toast('Синхронизировано');
            else if(force&&syncError)toast(syncError,true);
        }
        if(again){again=false;setTimeout(()=>sync(),300);}
    }
}
async function deleteItems(list,revision=generation){
    const stamp=new Date().toISOString();
    for(const item of list){
        await mutateItem(item.key,current=>{
            if(revision!==generation&&current?.scope!=='local')return undefined;
            const remote=legacyEnabled()&&current?.scope===scope&&current.user_id===sb.session.user.id;
            return deleteLocally(current,remote,stamp);
        });
    }
    await reload();
    sync();
}
function askDelete(list){
    if(!list.length)return;
    const revision=generation;
    $('confirmTitle').textContent=list.length===1?'Удалить объект?':`Удалить ${list.length} объектов?`;
    $('confirmText').textContent=legacyEnabled()&&list.some(item=>item.scope===scope)
        ?'Облачные записи исчезнут на подключённых устройствах.':'Записи исчезнут с этого устройства.';
    confirmAction=async()=>{await deleteItems(list,revision);toast('Удалено');};
    $('confirmDialog').showModal();
}
async function copyText(text){try{await native('copy',{text});toast('Скопировано');return;}catch{}try{await navigator.clipboard.writeText(text);toast('Скопировано');}catch{const t=document.createElement('textarea');t.value=text;document.body.append(t);t.select();const ok=document.execCommand('copy');t.remove();toast(ok?'Скопировано':'Буфер обмена недоступен',!ok);}}
async function download(x){try{const revision=generation, disk=drive;const sameAccount=()=>x.scope==='local'||(scope===x.scope&&revision===generation);let b=(await get('blobs',x.key))?.blob;if(!sameAccount())return;if(!b){if(!x.drive_id)throw Error('Файл ещё не синхронизирован');if(!legacyEnabled()||!verifiedDrive||!drive.ready)throw Error('Подключите соответствующий облачный диск в настройках');toast('Скачивание…');b=await disk.download(x.drive_id);if(!sameAccount()||!legacyEnabled()||disk!==drive)return;try{const current=await get('items',x.key);if(current&&!current.deleted_at)await put('blobs',{key:x.key,blob:b});}catch{/* download still works if local cache is full */}}if(window.LinkUpNative){const id=await native('downloadBegin',{name:x.file_name,mime:x.mime_type||'application/octet-stream'});try{for(let off=0;off<b.size;off+=192*1024){const bytes=new Uint8Array(await b.slice(off,off+192*1024).arrayBuffer());let str='';for(let i=0;i<bytes.length;i+=8192)str+=String.fromCharCode(...bytes.subarray(i,i+8192));await native('downloadChunk',{id,data:btoa(str)});}await native('downloadEnd',{id});toast('Сохранено в Загрузки');}catch(e){await native('downloadAbort',{id}).catch(()=>{});throw e;}}else{const url=URL.createObjectURL(b),a=document.createElement('a');a.href=url;a.download=x.file_name;a.click();setTimeout(()=>URL.revokeObjectURL(url),60000);}}catch(e){toast(e.message,true);}}
function action(e){const btn=e.target.closest('[data-action]');if(!btn)return;const x=items.find(a=>a.key===btn.closest('[data-id]')?.dataset.id);if(!x)return;switch(btn.dataset.action){case 'copy':copyText(x.text_content);break;case 'remove':askDelete([x]);break;case 'download':download(x);break;case 'open':if(x.kind==='file')download(x);else{viewed=x;$('viewText').textContent=x.text_content;$('viewDialog').showModal();}break;}}
function openClean(){const options=[[1,'Старше суток'],[7,'Старше недели'],[30,'Старше месяца (30 дней)'],[90,'Старше 90 дней'],[0,'Вся история']];$('cleanOptions').innerHTML=options.map(([days,name])=>{const n=older(days).length;return `<button data-days="${days}" ${n?'':'disabled'}>${name}<span>${n}</span></button>`;}).join('');$('retention').value=String(read('linkup.retention.local',0));$('cleanDialog').showModal();}
function older(days){return active().filter(x=>!days||Date.parse(x.created_at)<Date.now()-days*86400000);}async function retention(){if(retentionBusy)return;retentionBusy=true;try{const days=read('linkup.retention.local',0);if(days){const list=older(days).filter(x=>x.scope==='local');if(list.length)await deleteItems(list);}}finally{retentionBusy=false;}}
function setup(){
    sb?.disconnect();
    drive.disconnect();
    connectingDisk?.disconnect();
    generation++;
    realtime=false; lastOk=false; syncError=''; verifiedDrive=false; drive=makeDrive(); sb=null;
    scope=config.url&&session?.user?.id?`${config.url}:${session.user.id}`:'local';
    if(scope!=='local'&&config.key){
        try{sb=new Supabase(config.url, config.key, session, current=>{
            session=current;write('linkup.session',session);
        });}catch{/* Invalid legacy credentials leave cached and local history accessible. */}
    }
    items=items.filter(item=>item.scope==='local'||item.scope===scope);
    if(viewed&&viewed.scope!=='local'&&viewed.scope!==scope){viewed=null;$('viewDialog').close();}
    $('legacyTransfer').setAttribute('aria-pressed',String(legacyEnabled()));
    $('logout').hidden=!sb;
    render();
    reload().then(()=>{retention();sync();}).catch(()=>toast('История на устройстве недоступна',true));
}
document.querySelectorAll('[data-settings-target]').forEach(button=>button.onclick=()=>{
    const section=$(button.dataset.settingsTarget);
    section.scrollIntoView({block:'start'});
    section.focus({preventScroll:true});
});
let cloudBusy=false, cloudDiagnostic='';
function setCloudBusy(busy) {
    cloudBusy=busy;
    for (const id of ['saveCloud','driveConnect','logout']) $(id).disabled=busy;
}
function updateCloudStatus(id, text) {
    $(id).textContent=text;
    if ($('settingsDialog').open) $(id).scrollIntoView({block:'nearest'});
}
function openSettings() {
    $('deviceName').value=config.deviceName;
    $('sbUrl').value=config.url;
    $('sbKey').value=config.key;
    $('email').value=config.email;
    $('password').value='';
    $('googleId').value=config.googleId;
    $('diskProvider').value=config.diskProvider||'yandex';
    $('diskToken').value='';
    if (!cloudBusy) {
        $('supabaseStatus').textContent=sb?'Supabase подключён':'Supabase не подключён';
        $('diskStatus').textContent=verifiedDrive&&drive.ready?'Облачный диск подключён':'Облачный диск не подключён';
    }
    $('legacyTransfer').setAttribute('aria-pressed',String(legacyEnabled()));
    $('logout').hidden=!sb;
    $('settingsDialog').showModal();
}
async function saveConfig(event) {
    event.preventDefault();
    if (cloudBusy) return;
    const status=text=>updateCloudStatus('supabaseStatus',text);
    setCloudBusy(true);
    try {
        const next={...config,deviceName:$('deviceName').value.trim(),url:projectUrl($('sbUrl').value),
            key:$('sbKey').value.trim(),email:$('email').value.trim(),
            googleId:$('googleId').value.trim(),diskProvider:$('diskProvider').value};
        if (!next.deviceName) throw new Error('Введите имя устройства');
        if (next.key) assertPublicKey(next.key);
        if (Boolean(next.url)!==Boolean(next.key)) throw new Error('Supabase · Нужны URL и ключ проекта');
        const password=$('password').value;
        const changed=next.url!==config.url||next.key!==config.key||next.email!==config.email;
        let nextSession=next.url?session:null;
        if (next.url&&(changed||!session||password)) {
            status('Supabase · Подключаемся…');
            nextSession=await signIn(next,password,status);
        }
        const accountChanged=changed||nextSession?.user?.id!==session?.user?.id;
        const reset=accountChanged||next.diskProvider!==config.diskProvider||next.googleId!==config.googleId||Boolean(password);
        if (accountChanged) next.legacyScope=null;
        config=next;
        session=nextSession;
        write('linkup.config',config);
        write('linkup.session',session);
        $('password').value='';
        if (reset) {
            setup();
            $('diskStatus').textContent='Облачный диск не подключён';
        } else {
            reload().then(()=>sync()).catch(error=>toast(error.message,true));
        }
        cloudDiagnostic='';$('cloudProbeStatus').textContent='';
        status(session?'Supabase подключён':'Настройки сохранены · локальный режим');
    } catch (error) {
        cloudDiagnostic=error.phase?`${error.phase} · ${error.message}`:'Параметры проекта';
        $('cloudProbeStatus').textContent=cloudDiagnostic;
        status(error.message);
    } finally {
        setCloudBusy(false);
    }
}
let connectingDisk;
async function connectDrive() {
    if (cloudBusy) return;
    const status=text=>updateCloudStatus('diskStatus',text);
    if (!sb) return status('Сначала подключите Supabase');
    if (!legacyEnabled()) return status('Разрешите облачный Transfer в настройках');
    if ($('googleId').value.trim()!==config.googleId||$('diskProvider').value!==config.diskProvider) {
        return status('Сначала сохраните провайдера и его OAuth Client ID');
    }
    setCloudBusy(true);
    try {
        const candidate=connectingDisk=makeDrive(), revision=generation;
        await verifyDisk(sb,candidate,config.diskProvider,$('diskToken').value,status);
        if(revision!==generation||!legacyEnabled())return;
        drive.disconnect();
        drive=candidate;
        verifiedDrive=true;
        $('diskToken').value='';
        status(config.diskProvider==='yandex'?'Яндекс Диск подключён':'Google Drive подключён');
        setStatus();
        sync();
    } catch (error) {
        status(error.message);
    } finally {
        if(connectingDisk!==drive)connectingDisk?.disconnect();
        connectingDisk=null;
        setCloudBusy(false);
    }
}
$('composer').addEventListener('submit',e=>{e.preventDefault();send();});$('attach').onclick=()=>$('files').click();$('files').onchange=e=>{stage([...e.target.files]);e.target.value='';};$('staged').onclick=e=>{const b=e.target.closest('[data-index]');if(b){staged.splice(Number(b.dataset.index),1);renderStage();}};$('message').oninput=resize;$('message').onkeydown=e=>{if(e.key==='Enter'&&(e.ctrlKey||e.metaKey)){e.preventDefault();send();}};$('paste').onclick=async()=>{try{if(window.LinkUpNative||window.qt){const txt=await native('paste');if(txt){$('message').value+=txt;resize();return;}}}catch{}try{if(navigator.clipboard.read){const entries=await navigator.clipboard.read();let added=false;for(const entry of entries){for(const type of entry.types){if(type.startsWith('image/')){const b=await entry.getType(type);stage([new File([b],`clipboard-${Date.now()}.${type.split('/')[1]}`,{type})]);added=true;break;}if(type==='text/plain'){const b=await entry.getType(type);$('message').value+=await b.text();added=true;}}}if(!added)toast('Буфер пуст');}else $('message').value+=await navigator.clipboard.readText();resize();}catch{toast('Вставьте через Ctrl+V или системное меню',true);}};
document.addEventListener('paste',e=>{if(document.querySelector('dialog[open]'))return;const files=[...e.clipboardData.files];if(files.length){e.preventDefault();stage(files);}else if(!['INPUT','TEXTAREA'].includes(e.target.tagName)){const text=e.clipboardData.getData('text/plain');if(text){e.preventDefault();$('message').value+=text;resize();$('message').focus();}}});let depth=0;window.addEventListener('dragenter',e=>{if(!e.dataTransfer.types.includes('Files'))return;e.preventDefault();depth++;$('dropzone').hidden=false;});window.addEventListener('dragover',e=>{if(e.dataTransfer.types.includes('Files'))e.preventDefault();});window.addEventListener('dragleave',()=>{if(--depth<=0){depth=0;$('dropzone').hidden=true;}});window.addEventListener('drop',e=>{if(e.dataTransfer.files.length){e.preventDefault();stage([...e.dataTransfer.files]);}depth=0;$('dropzone').hidden=true;});
document.querySelectorAll('[data-filter]').forEach(b=>b.onclick=()=>selectFilter(b.dataset.filter));$('search').oninput=render;document.addEventListener('keydown',e=>{if(e.key==='/'&&!['INPUT','TEXTAREA'].includes(e.target.tagName)&&!document.querySelector('dialog[open]')){e.preventDefault();$('search').focus();}});$('history').onclick=action;$('history').onkeydown=e=>{if(e.target.classList.contains('item-body')&&['Enter',' '].includes(e.key)){e.preventDefault();action(e);}};$('viewCopy').onclick=()=>copyText(viewed?.text_content||'');$('clean').onclick=openClean;$('cleanOptions').onclick=e=>{const b=e.target.closest('[data-days]');if(b){const list=older(Number(b.dataset.days));$('cleanDialog').close();askDelete(list);}};$('retention').onchange=e=>{write('linkup.retention.local',Number(e.target.value));toast('Автоочистка настроена');};$('confirmYes').onclick=async()=>{$('confirmDialog').close();const action=confirmAction;confirmAction=null;try{await action?.();}catch(e){toast(e.message,true);}};document.querySelectorAll('[data-close]').forEach(b=>b.onclick=()=>b.closest('dialog').close());$('settings').onclick=$('mobileSettings').onclick=openSettings;$('configForm').onsubmit=saveConfig;
$('probeSupabase').onclick=async()=>{
    const button=$('probeSupabase'), output=$('cloudProbeStatus');
    button.disabled=true;
    try{
        const url=projectUrl($('sbUrl').value);
        if(!url)throw new Error('Введите адрес проекта');
        output.textContent='Проверяем HTTPS…';
        if(!window.LinkUpNative){
            const status=await cloudStep('HTTPS', async signal=>(await fetch(url+'/auth/v1/health',{redirect:'error',signal})).status,8000);
            output.textContent=`HTTPS · HTTP ${status}`;
            return;
        }
        const id=await native('probeSupabase',{url});
        let result;
        const limit=performance.now()+12000;
        do{
            result=await native('probeSupabaseResult');
            if(result.id===id&&result.stage!=='pending')break;
            await new Promise(resolve=>setTimeout(resolve,250));
        }while(performance.now()<limit);
        if(result.id!==id)throw Error('Результат проверки недоступен');
        const detail={dns:'DNS: адрес проекта не найден',tls:'TLS: Android отклонил сертификат',
            'connect-timeout':'Таймаут установления HTTPS', 'read-timeout':'HTTPS установлен, ответа нет',
            pending:'Истёк срок проверки HTTPS',network:'Сеть недоступна'};
        const network=result.stage==='http'?`Нативный HTTPS · HTTP ${result.status}`:detail[result.stage]||'Сеть недоступна';
        const elapsed=Number.isFinite(result.elapsed_ms)?` (${Math.round(result.elapsed_ms/100)/10} с)`:'';
        output.textContent=[cloudDiagnostic,network+elapsed].filter(Boolean).join(' · ');
    }catch(error){output.textContent=['Введите адрес проекта','Предыдущая проверка ещё выполняется'].includes(error.message)?error.message:'Адрес или сеть недоступны';}
    finally{button.disabled=false;}
};
$('legacyTransfer').onclick=()=>{
    if(legacyEnabled()){
        config={...config,legacyScope:null};write('linkup.config',config);setup();return;
    }
    if(!sb){updateCloudStatus('supabaseStatus','Сначала подключите Supabase');return;}
    $('legacyConsent').showModal();
};
$('allowLegacy').onclick=()=>{
    $('legacyConsent').close();
    if(!sb||cloudBusy)return;
    config={...config,legacyScope:scope};write('linkup.config',config);setup();
};$('driveConnect').onclick=connectDrive;$('checkUpdate').onclick=()=>native('checkUpdate').catch(e=>toast(e.message,true));$('logout').onclick=()=>{if(cloudBusy)return;native('disableCloud').catch(()=>{});session=null;config={...config,legacyScope:null};write('linkup.config',config);write('linkup.session',null);setup();$('settingsDialog').close();toast('Локальный режим');};$('sync').onclick=()=>legacyEnabled()?sync(true):openSettings();$('status').onclick=()=>{if(syncError)toast(syncError,true);else openSettings();};window.addEventListener('online',()=>{sync();setStatus()});window.addEventListener('offline',setStatus);document.addEventListener('visibilitychange',()=>{if(!document.hidden){sync();retention();importShare();}});setInterval(()=>{if(!document.hidden)sync()},15000);setInterval(()=>{if(!document.hidden)retention()},60000);
let importingShare=false;async function importShare(){if(importingShare)return;importingShare=true;try{if(window.LinkUpNative){const incoming=await native('shareList');if(incoming.text){$('message').value+=($('message').value?'\n':'')+incoming.text;resize();}for(const f of incoming.files){const parts=[];let offset=0;for(;;){const encoded=await native('shareRead',{index:f.index,offset});if(!encoded)break;const bytes=Uint8Array.from(atob(encoded),c=>c.charCodeAt(0));parts.push(bytes);offset+=bytes.length;if(f.size>=0&&offset>=f.size)break;}stage([new File(parts,f.name,{type:f.mime||'application/octet-stream'})]);}await native('shareClear');}const shared=await consumeInbox();for(const x of shared){if(x.files?.length)stage(x.files);if(x.text){$('message').value+=($('message').value?'\n':'')+x.text;resize();}}}catch(e){toast(e.message,true);}finally{importingShare=false;}}
window.linkupImportShare=importShare;
setup();initActivity(()=>config);importShare();
