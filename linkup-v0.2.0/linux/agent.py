#!/usr/bin/env python3
"""Per-user native collector. No keystrokes, window titles, or content collected."""
import os, sys, json, sqlite3, time, subprocess, re, uuid, urllib.request, urllib.parse, threading, fcntl
from pathlib import Path
ROOT=Path(os.environ.get('XDG_DATA_HOME',Path.home()/'.local/share'))/'linkup'; ROOT.mkdir(parents=True,exist_ok=True,mode=0o700); ROOT.chmod(0o700)
DB=ROOT/'activity.sqlite3'; os.umask(0o077)
def db():
 c=sqlite3.connect(DB,timeout=10); c.row_factory=sqlite3.Row;c.execute('pragma journal_mode=WAL');c.execute('create table if not exists config(k text primary key,v text)');c.execute('create table if not exists events(id text primary key,owner text not null, start integer,end integer,payload text,sent integer default 0)');c.execute('create index if not exists event_time on events(start,end)');return c
def cfg():
 with db() as c:return {r['k']:json.loads(r['v']) for r in c.execute('select * from config')}
def setcfg(values):
 with db() as c:c.executemany('insert or replace into config values(?,?)',[(k,json.dumps(v)) for k,v in values.items()])
def request(url, key, path, body=None, token=None, prefer=None):
 if urllib.parse.urlparse(url).scheme!='https':raise ValueError('Требуется HTTPS')
 headers={'apikey':key,'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 if prefer:headers['Prefer']=prefer
 req=urllib.request.Request(url.rstrip('/')+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method='GET' if body is None else 'POST')
 with urllib.request.urlopen(req,timeout=20) as res:
  raw=res.read();return json.loads(raw) if raw else None
def configure(a):
 old=cfg(); enabled=bool(a.get('enabled'));cloud=bool(a.get('cloud'));owner=a.get('user_id','');values=dict(enabled=enabled,location=bool(a.get('location')),cloud=cloud,device_id=a.get('device_id') or old.get('device_id') or str(uuid.uuid4()),device_name=a.get('device_name') or 'Linux Mint',owner=owner if cloud else '',error='')
 if cloud:
  if not owner:raise ValueError('Сначала войдите в Supabase')
  if a.get('password'):
   s=request(a['url'],a['key'],'/auth/v1/token?grant_type=password',{'email':a['email'],'password':a['password']})
   if s['user']['id']!=owner:raise ValueError('Аккаунты не совпадают')
   values.update(url=a['url'],key=a['key'],session=s)
  elif not(old.get('cloud') and old.get('owner')==owner and old.get('url')==a.get('url') and old.get('session')):raise ValueError('Введите пароль для фоновой сессии')
 else:values['session']=None
 setcfg(values)
def emit(kind,start,end,app=None,label=None,config=None,**extra):
 c=config or cfg()
 if not c.get('enabled'):return
 owner=c.get('owner','');r=dict(id=str(uuid.uuid4()),device_id=c['device_id'],device_name=c['device_name'],platform='linux',kind=kind,app=app,label=label,start_ms=start,end_ms=end,lat=None,lon=None,accuracy=None);r.update(extra)
 with db() as con:con.execute('insert into events(id,owner,start,end,payload) values(?,?,?,?,?)',(r['id'],owner,start,end,json.dumps(r)))
def rows(a,b):
 with db() as c:return [dict(json.loads(x['payload']),owner=x['owner']) for x in c.execute('select * from events where start<? and end>=? order by start',(b,a))]
def erase():
 setcfg({'enabled':False,'cloud':False,'location':False,'session':None,'owner':''})
 with db() as c:c.execute('delete from events')
def status():
 c=cfg();return dict(enabled=c.get('enabled',False),location=False,cloud=c.get('cloud',False),error=c.get('error',''),warning='Wayland: активные окна недоступны; войдите в сеанс X11' if os.environ.get('XDG_SESSION_TYPE')=='wayland' else 'Linux: X11 · геолокация только на Android')
SYNC_LOCK=threading.Lock()
def sync():
 if not SYNC_LOCK.acquire(False):return
 try:_sync()
 finally:SYNC_LOCK.release()
def _sync():
 c=cfg()
 if not(c.get('cloud') and c.get('session') and c.get('owner')):return
 try:
  s=c['session']
  if s.get('expires_at',0)<time.time()+60:
   s=request(c['url'],c['key'],'/auth/v1/token?grant_type=refresh_token',{'refresh_token':s['refresh_token']})
   if not s.get('expires_at'):s['expires_at']=int(time.time())+s.get('expires_in',3600)
   current=cfg()
   if current.get('session')!=c['session'] or not current.get('cloud'):return
   setcfg({'session':s})
  with db() as con: batch=list(con.execute('select id,payload from events where owner=? and sent=0 order by start limit 200',(c['owner'],)))
  if batch:
   current=cfg()
   if not current.get('cloud') or current.get('owner')!=c['owner']:return
   payload=[dict(json.loads(r['payload']),user_id=c['owner']) for r in batch]
   request(c['url'],c['key'],'/rest/v1/linkup_activity?on_conflict=id',payload,s['access_token'],'resolution=ignore-duplicates,return=minimal')
   with db() as con:con.executemany('update events set sent=1 where id=?',[(r['id'],) for r in batch])
  setcfg({'error':''})
 except Exception as e:setcfg({'error':'Синхронизация: '+str(e)[:160]})
def cmd(*args):return subprocess.check_output(args,stderr=subprocess.DEVNULL,text=True,timeout=2).strip()
def sample():
 if os.environ.get('XDG_SESSION_TYPE')=='wayland':return None
 try:
  if int(cmd('xprintidle'))>=60000:return None
  w=cmd('xprop','-root','_NET_ACTIVE_WINDOW');m=re.search(r'0x[0-9a-f]+',w)
  if not m or m[0]=='0x0':return None
  prop=cmd('xprop','-id',m[0],'WM_CLASS');names=re.findall(r'"([^"\n]+)"',prop)
  if not names:return None
  return names[-1].lower(),names[-1]
 except (OSError,ValueError,subprocess.SubprocessError):return None
def locked():
 for service,path in [('org.cinnamon.ScreenSaver','/org/cinnamon/ScreenSaver'),('org.freedesktop.ScreenSaver','/org/freedesktop/ScreenSaver')]:
  try:
   v=cmd('gdbus','call','--session','--dest',service,'--object-path',path,'--method',service+'.GetActive');return 'true' in v
  except (OSError,subprocess.SubprocessError):pass
 return None
def collector(next_program=lambda: None):
 last=None;start=None;previous=None;last_sync=0;prior_lock=None;signature=None;old_cfg={}
 while True:
  if program := next_program():return program
  now=int(time.time()*1000);c=cfg();enabled=c.get('enabled');sig=(enabled,c.get('owner'),c.get('device_id'),c.get('device_name'))
  if sig!=signature:last=None;previous=None;prior_lock=None;start=now;signature=sig
  lock=locked() if enabled else None
  cur=sample() if enabled and lock is not True else None
  if last and previous is not None:
   gap=now-previous;end=now if 0<gap<=12000 else previous
   if cur!=last or now-start>=60000 or gap>12000:
    if end>start:emit('app',start,end,*last,config=old_cfg)
    start=now
  if cur!=last:start=now
  if enabled and prior_lock is True and lock is False:emit('unlock',now,now,config=c)
  last=cur;previous=now;prior_lock=lock;old_cfg=c
  if time.monotonic()-last_sync>=30:
   last_sync=time.monotonic();threading.Thread(target=sync,daemon=True).start()
  time.sleep(5)
if __name__=='__main__':
 from updater import BASE, SOURCE
 current = BASE / 'current'
 managed = current.resolve() == SOURCE
 def next_program():
  target = current.resolve() / 'linux/agent.py'
  if managed and target.is_file() and target != SOURCE / 'linux/agent.py':
   return target
 with (ROOT / 'agent.lock').open('a') as lock:
  try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
  except BlockingIOError:sys.exit(0)
  program = collector(next_program)
 os.execv(sys.executable, [sys.executable, str(program)])
