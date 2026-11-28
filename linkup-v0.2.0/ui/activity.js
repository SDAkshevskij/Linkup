const $=id=>document.getElementById(id);
let bridgeReady;export function native(action,args={}){return new Promise((resolve,reject)=>{const done=v=>{try{const r=JSON.parse(v);if(r.error)reject(Error(r.error));else resolve(r.value);}catch(e){reject(e)}};if(window.LinkUpNative){try{done(LinkUpNative.call(action,JSON.stringify(args)))}catch(e){reject(e)}return;}if(window.qt&&window.QWebChannel){bridgeReady??=new Promise(r=>new QWebChannel(qt.webChannelTransport,c=>r(c.objects.native)));bridgeReady.then(b=>b.call(action,JSON.stringify(args),done),reject);return;}reject(Error('Нативный агент недоступен'));});}
const colors=['#35b9e8','#94d82d','#ff862d','#ad92f5','#f4779c','#4ed3af','#e3c765','#7b9cff'];
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const duration=ms=>{if(ms>0&&ms<60000)return `${Math.max(1,Math.round(ms/1000))} с`;const m=Math.round(ms/60000);return m<60?`${m} мин`:`${Math.floor(m/60)} ч ${String(m%60).padStart(2,'0')} мин`;};
const time=ms=>new Date(ms).toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit'});
const dateKey=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
let day=dateKey(new Date()),device='all',data=[],status={},configGetter=()=>({}),tick=0;
function bounds(){const start=new Date(day+'T00:00:00'),end=new Date(start);end.setDate(end.getDate()+1);return [+start,+end];}
export function color(app){let h=0;for(const c of app)h=(h*31+c.charCodeAt(0))>>>0;return colors[h%colors.length];}
export function intervalsUnion(rows){const sorted=rows.map(r=>[r.start_ms,r.end_ms]).sort((a,b)=>a[0]-b[0]);let total=0,last=null;for(const [a,b] of sorted){if(!last||a>last[1]){if(last)total+=last[1]-last[0];last=[a,b];}else last[1]=Math.max(last[1],b);}return total+(last?last[1]-last[0]:0);}
async function refresh(){
    const revision=++tick, [from,to]=bounds();
    try{
        const local=await native('activity',{from,to});
        if(revision!==tick)return;
        status=local.status;
        data=local.rows;
        $('activityStatus').replaceChildren();
        const localState=document.createElement('strong');
        localState.textContent=status.warning||status.error||(status.enabled?(data.length?'Сбор включён':'Сбор включён · ждём события'):'Сбор выключен');
        localState.dataset.live=String(status.enabled&&!status.warning&&!status.error);
        const cloudState=document.createElement('small');
        cloudState.textContent='Облако на паузе';
        $('activityStatus').append(localState,cloudState);
        render();
    }catch{
        if(revision===tick)$('activityStatus').textContent='Локальная история пока недоступна';
    }
}
function render(){const [a,b]=bounds(),devs=new Map(data.map(r=>[r.device_id,r.device_name]));$('actDevice').innerHTML='<option value="all">Все устройства</option>'+[...devs].map(([id,name])=>`<option value="${esc(id)}">${esc(name)}</option>`).join('');if(device!=='all'&&!devs.has(device))device='all';$('actDevice').value=device;const rows=data.filter(r=>(device==='all'||r.device_id===device)&&(r.end_ms>r.start_ms?r.end_ms>a&&r.start_ms<b:r.start_ms>=a&&r.start_ms<b)),sessions=rows.filter(r=>r.kind==='app'&&r.end_ms>r.start_ms).map(r=>({...r,start_ms:Math.max(a,r.start_ms),end_ms:Math.min(b,r.end_ms)})).filter(r=>r.end_ms>r.start_ms),apps=new Map();for(const r of sessions){const k=r.app||r.label||'Неизвестно';const x=apps.get(k)||{name:r.label||k,rows:[],ms:0};x.rows.push(r);x.ms+=r.end_ms-r.start_ms;apps.set(k,x);}const sorted=[...apps].sort((a,b)=>b[1].ms-a[1].ms),total=intervalsUnion(sessions);$('actTotal').textContent=duration(total);$('actApps').textContent=apps.size;$('actUnlocks').textContent=rows.some(r=>r.platform==='android'||r.kind==='unlock')?rows.filter(r=>r.kind==='unlock').length:'—';const grouped=[];for(const r of [...sessions].sort((x,y)=>x.device_id.localeCompare(y.device_id)||x.start_ms-y.start_ms)){let p=grouped[grouped.length-1];if(p&&p.device_id===r.device_id&&p.app===r.app&&r.start_ms<=p.end_ms+1000){p.end_ms=Math.max(p.end_ms,r.end_ms);}else grouped.push({...r});}$('actAverage').textContent=grouped.length?duration(grouped.reduce((s,r)=>s+r.end_ms-r.start_ms,0)/grouped.length):'—';$('actBars').innerHTML=sorted.map(([k,x])=>`<div class="app-stat"><div><span>${esc(x.name)}</span><strong>${duration(x.ms)}</strong></div><div class="app-meter"><i style="width:${100*x.ms/sorted[0][1].ms}%;background:${color(k)}"></i></div></div>`).join('')||'<div class="act-empty">Нет записей за этот день</div>';
const tracks=[...new Map(rows.map(r=>[r.device_id,r.device_name])).entries()];$('actTimeline').innerHTML=(tracks.length?tracks:[['','Нет активности']]).map(([id,name])=>`<div class="track-name">${esc(name)}</div><div class="act-track">${Array.from({length:24},(_,i)=>`<span class="hour-cell" style="left:${i/24*100}%;width:${100/24}%"></span>`).join('')}${sessions.filter(r=>r.device_id===id).map(r=>`<button class="time-dot" title="${esc(r.label||r.app)} · ${time(r.start_ms)}–${time(r.end_ms)}" style="left:${100*(r.start_ms-a)/(b-a)}%;width:${Math.max(.22,100*(r.end_ms-r.start_ms)/(b-a))}%;background:${color(r.app||r.label)}" data-start="${r.start_ms}" data-device="${esc(id)}"></button>`).join('')}</div>`).join('');$('actTimeline').querySelectorAll('button').forEach(el=>el.onclick=()=>{$('actSelected').textContent=el.title;});
$('actDevices').innerHTML=tracks.map(([id,name])=>`<div class="activity-row"><span>${esc(name)}</span><b>${duration(intervalsUnion(sessions.filter(r=>r.device_id===id)))}</b></div>`).join('');const loc=rows.filter(r=>r.kind==='location'&&Number.isFinite(r.lat)&&Number.isFinite(r.lon)).sort((x,y)=>y.start_ms-x.start_ms);$('actPlaces').innerHTML=loc.map(r=>`<div class="activity-row"><span><b>${esc(time(r.start_ms))}</b> · ${esc(r.device_name)}<small>${r.lat.toFixed(5)}, ${r.lon.toFixed(5)} · ±${Math.round(r.accuracy||0)} м</small></span><button class="secondary" data-map="${r.lat},${r.lon}">Карта ↗</button></div>`).join('')||'<div class="act-empty">Нет точек местоположения</div>';$('actPlaces').querySelectorAll('button').forEach(el=>el.onclick=()=>{const [lat,lon]=el.dataset.map.split(',');native('openUrl',{url:`https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=17/${lat}/${lon}`}).catch(e=>alert(e.message));});$('actSessions').innerHTML=grouped.sort((x,y)=>y.start_ms-x.start_ms).map(r=>`<div class="activity-row"><span><i style="background:${color(r.app||r.label)}"></i>${esc(r.label||r.app)}<small>${esc(r.device_name)} · ${time(r.start_ms)}–${time(r.end_ms)}</small></span><b>${duration(r.end_ms-r.start_ms)}</b></div>`).join('');}
function sourceControls(){
    const android=status.platform==='android';
    $('trackEnabled').setAttribute('aria-pressed',String(!!status.enabled));
    $('trackAppState').textContent=status.enabled?(status.warning||'Сбор включён'):'Выключено';
    $('trackPermission').hidden=!(android&&status.enabled&&!status.usageAccess);
    $('trackLocation').hidden=!android;
    $('trackLocation').setAttribute('aria-pressed',String(!!status.location));
    $('trackLocationState').textContent=!status.location?'Выключено':!status.enabled?'Включите сбор приложений'
        :!status.locationAccess?'Ожидает разрешения':status.backgroundLocationAccess?'Сбор включён':'Только при открытом LinkUp';
    const permission=$('trackLocationPermission');
    permission.hidden=!(android&&status.enabled&&status.location&&!status.backgroundLocationAccess);
    permission.textContent=status.locationAccess?'Разрешить доступ в фоне':'Разрешить местоположение';
}

async function readSources(){
    status=await native('status');
    sourceControls();
}

async function setSource(source){
    const cfg=configGetter();
    const controls=[$('trackEnabled'),$('trackLocation')];
    controls.forEach(control=>control.disabled=true);
    try{
        const requested=!status[source];
        const options={enabled:!!status.enabled,location:!!status.location,
            device_id:cfg.deviceId,device_name:cfg.deviceName,[source]:requested};
        await native('configure',options);
        await readSources();
        if(requested&&status.platform==='android'){
            if(source==='enabled'&&!status.usageAccess)await native('permission');
            if(source==='location'&&status.enabled&&!status.locationAccess)await native('locationPermission');
        }
        $('trackInfo').textContent='';
        refresh();
    }catch{
        $('trackInfo').textContent='Не удалось сохранить настройки сбора';
    }finally{controls.forEach(control=>control.disabled=false);}
}

const factLabels={
    platform:'Платформа', enabled:'Сбор', usageAccess:'Доступ', locationAccess:'Доступ к геолокации',
    backgroundLocationAccess:'Геолокация в фоне',
    local_count:'Локальные события', last_event:'Последняя запись', last_attempt:'Последняя попытка',
    last_success:'Последний успешный сбор', cursor:'Курсор', last_boundary:'Последний переход',
    last_app:'Последнее приложение', last_job:'Последняя фоновая задача', job_scheduled:'Фоновая задача', job_error:'Планировщик',
    pid:'Процесс агента', heartbeat:'Связь с агентом', session_type:'Тип сеанса', display:'Дисплей',
    xprop:'xprop', xprintidle:'xprintidle', last_sample:'Последняя выборка', sample_error:'Источник',
    warning:'Состояние', error:'Ошибка'
};
const timestamps=new Set(['last_event','last_attempt','last_success','cursor','last_boundary','last_job','heartbeat','last_sample']);
let shareableFacts='';
async function inspectActivity(){
    const list=$('activityFacts');
    list.replaceChildren();
    $('activityCopyStatus').textContent='';
    $('copyActivityFacts').disabled=true;
    $('activityInspector').showModal();
    try{
        const facts=await native('activityDiagnostics');
        const summary=[];
        for(const [name,label] of Object.entries(factLabels)){
            const raw=facts[name];
            if(raw===null||raw===undefined||raw==='')continue;
            const value=timestamps.has(name)?(raw?new Date(name==='heartbeat'?raw*1000:raw).toLocaleString('ru-RU'):'—')
                :typeof raw==='boolean'?(raw?'Да':'Нет'):String(raw);
            const term=document.createElement('dt'), description=document.createElement('dd');
            term.textContent=label;description.textContent=value;
            list.append(term,description);
            // App names and exact usage times stay in the local inspector.
            if(!timestamps.has(name)&&name!=='last_app')summary.push(label+' — '+value);
        }
        shareableFacts=summary.join('\n');
        $('copyActivityFacts').disabled=false;
    }catch{$('activityCopyStatus').textContent='Диагностика пока недоступна';}
}

export function initActivity(getConfig){
    configGetter=getConfig;
    const toggle=on=>{
        document.body.classList.toggle('activity-mode',on);
        $('activityView').hidden=!on;
        document.querySelectorAll('[data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page===(on?'activity':'transfer')));
        if(on)refresh();
    };
    document.querySelectorAll('[data-page]').forEach(el=>el.onclick=()=>toggle(el.dataset.page==='activity'));
    document.querySelectorAll('[data-filter]').forEach(el=>el.addEventListener('click',()=>toggle(false)));
    $('actDate').value=day;
    $('actDate').onchange=e=>{day=e.target.value||dateKey(new Date());refresh();};
    for(const [id,step] of [['actPrev',-1],['actNext',1]])$(id).onclick=()=>{
        const value=new Date(day+'T12:00:00');value.setDate(value.getDate()+step);
        day=dateKey(value);$('actDate').value=day;refresh();
    };
    $('actDevice').onchange=e=>{device=e.target.value;render();};
    $('actRefresh').onclick=refresh;
    $('actSettings').onclick=async()=>{
        $('trackInfo').textContent='';
        $('trackDialog').showModal();
        try{await readSources();}catch{$('trackInfo').textContent='Источник недоступен';}
    };
    $('trackEnabled').onclick=()=>setSource('enabled');
    $('trackLocation').onclick=()=>setSource('location');
    $('trackPermission').onclick=() => native('permission').catch(() => {
        $('trackInfo').textContent='Не удалось открыть системный доступ';
    });
    $('trackLocationPermission').onclick=() => native('locationPermission').catch(() => {
        $('trackInfo').textContent='Не удалось открыть разрешения местоположения';
    });
    $('trackErase').onclick=async()=>{
        if(!confirm('Остановить сбор и удалить локальную историю активности?'))return;
        try{await native('eraseActivity');await readSources();refresh();}
        catch{$('trackInfo').textContent='Не удалось удалить историю';}
    };
    $('actDiagnostics').onclick=inspectActivity;
    $('copyActivityFacts').onclick=async()=>{
        try{await native('copy',{text:shareableFacts});$('activityCopyStatus').textContent='Сводка скопирована';}
        catch{$('activityCopyStatus').textContent='Не удалось скопировать сводку';}
    };
    window.linkupActivityResume=()=>{
        if($('trackDialog').open)readSources().catch(()=>{});
        if(!$('activityView').hidden)refresh();
    };
    document.addEventListener('visibilitychange',()=>{if(!document.hidden)window.linkupActivityResume();});
    setInterval(()=>{if(!document.hidden&&!$('activityView').hidden)refresh();},15000);
}
