import {clipToDay, dateKey, dayScale, localDay, stepDay} from './activity-day.js';

const $=id=>document.getElementById(id);
let bridgeReady;export function native(action,args={}){return new Promise((resolve,reject)=>{const done=v=>{try{const r=JSON.parse(v);if(r.error)reject(Error(r.error));else resolve(r.value);}catch(e){reject(e)}};if(window.LinkUpNative){try{done(LinkUpNative.call(action,JSON.stringify(args)))}catch(e){reject(e)}return;}if(window.qt&&window.QWebChannel){bridgeReady??=new Promise(r=>new QWebChannel(qt.webChannelTransport,c=>r(c.objects.native)));bridgeReady.then(b=>b.call(action,JSON.stringify(args),done),reject);return;}reject(Error('Нативный агент недоступен'));});}
const colors=['#35b9e8','#94d82d','#ff862d','#ad92f5','#f4779c','#4ed3af','#e3c765','#7b9cff'];
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const duration=ms=>{if(ms>0&&ms<60000)return `${Math.max(1,Math.round(ms/1000))} с`;const m=Math.round(ms/60000);return m<60?`${m} мин`:`${Math.floor(m/60)} ч ${String(m%60).padStart(2,'0')} мин`;};
const time=ms=>new Date(ms).toLocaleTimeString('ru-RU',{hour:'2-digit',minute:'2-digit'});
let selectedDay=null, range=null, phase='loading', hasResult=false;
let device='all', data=[], status={}, configGetter=()=>({}), tick=0, pending=false;
const dayLabel=(date,options)=>date.toLocaleDateString('ru-RU',{day:'numeric',month:'long',...options});
const todayKey=()=>dateKey(new Date());

function activeDay(today) {
    return localDay(selectedDay) || localDay(today);
}

function updateDayControls(day, today) {
    const current=selectedDay===null;
    const date=new Date(day.from);
    $('actDayLabel').textContent=current?'Сегодня':dayLabel(date);
    $('actDayCaption').textContent=current?dayLabel(date):String(date.getFullYear());
    $('actChooseDay').setAttribute('aria-label',`Выбрать дату, ${dayLabel(date,{year:'numeric'})}`);
    $('actDate').max=today;
    if (!$('actDateDialog').open) $('actDate').value=day.key;
    $('actToday').hidden=current;
    const next=$('actNext');
    next.disabled=day.key>=today;
    if (next.disabled && document.activeElement===next) $('actChooseDay').focus();
}

function pickDay(key) {
    const today=todayKey();
    const day=localDay(key);
    if (!day || key>today) return false;
    selectedDay=key===today?null:key;
    refresh();
    return true;
}

function moveDay(direction) {
    const day=stepDay(activeDay(todayKey()),direction);
    if (day) pickDay(day.key);
}

export function color(app){let h=0;for(const c of app)h=(h*31+c.charCodeAt(0))>>>0;return colors[h%colors.length];}
export function intervalsUnion(rows){const sorted=rows.map(r=>[r.start_ms,r.end_ms]).sort((a,b)=>a[0]-b[0]);let total=0,last=null;for(const [a,b] of sorted){if(!last||a>last[1]){if(last)total+=last[1]-last[0];last=[a,b];}else last[1]=Math.max(last[1],b);}return total+(last?last[1]-last[0]:0);}
async function refresh({retry=false}={}){
    const today=todayKey();
    const next=activeDay(today);
    if (!selectedDay || next.key!==selectedDay || next.key>today) selectedDay=null;
    const day=selectedDay?next:localDay(today);
    updateDayControls(day,today);
    const changed=!range || range.from!==day.from || range.to!==day.to;
    if (!changed && pending && !retry) return;
    const revision=++tick;
    pending=true;
    if (changed) {
        range=day;
        data=[];
        phase='loading';
        hasResult=false;
        $('actSelected').textContent='';
        render();
    }

    const {from,to}=day;
    try {
        const local=await native('activity',{from,to});
        if (revision!==tick) return;
        if (!local?.status || !Array.isArray(local.rows)) throw Error('Invalid activity response');
        status=local.status;
        data=clipToDay(local.rows,day);
        phase='ready';
        hasResult=true;
        const localState=document.createElement('strong');
        localState.textContent=status.warning||status.error||(status.enabled?'Сбор включён':'Сбор выключен');
        localState.dataset.live=String(status.enabled&&!status.warning&&!status.error);
        const cloudState=document.createElement('small');
        cloudState.textContent='Облако на паузе';
        $('activityStatus').replaceChildren(localState,cloudState);
        render();
    } catch {
        if (revision!==tick) return;
        phase='error';
        const localState=document.createElement('strong');
        localState.textContent='Локальная история пока недоступна';
        const cloudState=document.createElement('small');
        cloudState.textContent='Облако на паузе';
        $('activityStatus').replaceChildren(localState,cloudState);
        render();
    } finally {
        if (revision===tick) pending=false;
    }
}

function render(){
    if (!range) return;
    const {from,to}=range;
    const percent=value=>100*(value-from)/(to-from);
    const allDevices=new Map(data.map(row=>[row.device_id,row.device_name]));
    $('actDevice').innerHTML='<option value="all">Все устройства</option>'+[...allDevices]
        .map(([id,name])=>`<option value="${esc(id)}">${esc(name)}</option>`).join('');
    if (hasResult && device!=='all' && !allDevices.has(device)) device='all';
    $('actDevice').value=device;
    $('actDevice').disabled=allDevices.size===0;
    const rows=data.filter(row=>device==='all'||row.device_id===device);
    const sessions=rows.filter(row=>row.kind==='app'&&row.end_ms>row.start_ms);
    const tracks=[...new Map(rows.map(row=>[row.device_id,row.device_name]))];
    const apps=new Map();
    for (const row of sessions) {
        const key=row.app||row.label||'Неизвестно';
        if (!apps.has(key)) apps.set(key,{name:row.label||key,ms:0});
        apps.get(key).ms+=row.end_ms-row.start_ms;
    }
    const sorted=[...apps].sort((a,b)=>b[1].ms-a[1].ms);
    const grouped=[];
    for (const row of [...sessions].sort((a,b)=>a.device_id.localeCompare(b.device_id)||a.start_ms-b.start_ms)) {
        const previous=grouped[grouped.length-1];
        if (previous&&previous.device_id===row.device_id&&previous.app===row.app&&row.start_ms<=previous.end_ms+1000) {
            previous.end_ms=Math.max(previous.end_ms,row.end_ms);
        } else grouped.push({...row});
    }

    $('actTotal').textContent=hasResult?duration(intervalsUnion(sessions)):'—';
    $('actApps').textContent=hasResult?apps.size:'—';
    $('actUnlocks').textContent=rows.some(row=>row.platform==='android'||row.kind==='unlock')
        ?rows.filter(row=>row.kind==='unlock').length:'—';
    $('actAverage').textContent=grouped.length
        ?duration(grouped.reduce((sum,row)=>sum+row.end_ms-row.start_ms,0)/grouped.length):'—';
    $('actBars').innerHTML=sorted.map(([key,app])=>`<div class="app-stat"><div><span>${esc(app.name)}</span><strong>${duration(app.ms)}</strong></div><div class="app-meter"><i style="width:${100*app.ms/sorted[0][1].ms}%;background:${color(key)}"></i></div></div>`).join('');

    const {cells,ticks}=dayScale(range);
    const grid=cells.map(({start,end})=>`<span class="hour-cell" style="left:${percent(start)}%;width:${percent(end)-percent(start)}%"></span>`).join('');
    const timeline=tracks.length?tracks:[['',phase==='loading'?'Загружаем день…':phase==='error'?'Не удалось открыть день':'Нет записей за день']];
    $('actTimeline').innerHTML=timeline.map(([id,name])=>{
        const marks=sessions.filter(row=>row.device_id===id).map(row=>{
            const title=`${row.label||row.app} · ${time(row.start_ms)}–${time(row.end_ms)}`;
            const width=percent(row.end_ms)-percent(row.start_ms);
            return `<button class="time-dot" title="${esc(title)}" aria-label="${esc(title)}" style="left:${percent(row.start_ms)}%;width:${width}%;background:${color(row.app||row.label||'')}"></button>`;
        }).join('');
        return `<div class="track-name">${esc(name)}</div><div class="act-track">${grid}${marks}</div>`;
    }).join('');
    const selected=$('actSelected');
    for (const mark of $('actTimeline').querySelectorAll('button')) mark.onclick=()=>{selected.textContent=mark.title;};
    $('actHours').innerHTML=ticks.map((instant,index)=>{
        const label=time(instant);
        const side=index===0?'start':instant===to?'end':'middle';
        return `<span data-side="${side}" style="left:${percent(instant)}%">${instant===to&&label==='00:00'?'24:00':label}</span>`;
    }).join('');

    $('actDevices').innerHTML=tracks.map(([id,name])=>`<div class="activity-row"><span>${esc(name)}</span><b>${duration(intervalsUnion(sessions.filter(row=>row.device_id===id)))}</b></div>`).join('');
    const locations=rows.filter(row=>row.kind==='location'&&Number.isFinite(row.lat)&&Number.isFinite(row.lon)).sort((a,b)=>b.start_ms-a.start_ms);
    $('actPlaces').innerHTML=locations.map(row=>`<div class="activity-row"><span><b>${esc(time(row.start_ms))}</b> · ${esc(row.device_name)}<small>${row.lat.toFixed(5)}, ${row.lon.toFixed(5)} · ±${Math.round(row.accuracy||0)} м</small></span><button class="secondary" data-map="${row.lat},${row.lon}">Карта ↗</button></div>`).join('')
        ||(hasResult?'<div class="act-empty">Нет точек местоположения</div>':'');
    for (const button of $('actPlaces').querySelectorAll('button')) button.onclick=()=>{
        const [lat,lon]=button.dataset.map.split(',');
        native('openUrl',{url:`https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=17/${lat}/${lon}`}).catch(error=>alert(error.message));
    };
    $('actSessions').innerHTML=grouped.sort((a,b)=>b.start_ms-a.start_ms).map(row=>`<div class="activity-row"><span><i style="background:${color(row.app||row.label||'')}"></i>${esc(row.label||row.app)}<small>${esc(row.device_name)} · ${time(row.start_ms)}–${time(row.end_ms)}</small></span><b>${duration(row.end_ms-row.start_ms)}</b></div>`).join('');
}
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
    $('actPrev').onclick=()=>moveDay(-1);
    $('actNext').onclick=()=>moveDay(1);
    $('actToday').onclick=()=>{
        pickDay(todayKey());
        $('actChooseDay').focus();
    };
    $('actChooseDay').onclick=()=>{
        const today=todayKey();
        $('actDate').value=activeDay(today).key;
        $('actDate').max=today;
        $('actDateError').textContent='';
        $('actDateDialog').showModal();
    };
    $('actDateForm').onsubmit=event=>{
        event.preventDefault();
        if (!pickDay($('actDate').value)) {
            $('actDateError').textContent='Выберите сегодня или прошедший день';
            return;
        }
        $('actDateDialog').close();
    };
    $('actDevice').onchange=event=>{
        device=event.target.value;
        $('actSelected').textContent='';
        render();
    };
    $('actRefresh').onclick=()=>refresh({retry:true});
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
