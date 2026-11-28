#!/usr/bin/env python3
"""Per-user native collector. No keystrokes, window titles, or content collected."""
import os, sys, json, sqlite3, time, subprocess, re, uuid, shutil, fcntl, signal
from pathlib import Path
from contextlib import closing, contextmanager
ROOT=Path(os.environ.get('XDG_DATA_HOME',Path.home()/'.local/share'))/'linkup'; ROOT.mkdir(parents=True,exist_ok=True,mode=0o700); ROOT.chmod(0o700)
DB=ROOT/'activity.sqlite3'; os.umask(0o077)
def db():
 c=sqlite3.connect(DB,timeout=10); c.row_factory=sqlite3.Row;c.execute('pragma journal_mode=WAL');c.execute('create table if not exists config(k text primary key,v text)');c.execute('create table if not exists events(id text primary key,owner text not null, start integer,end integer,payload text,sent integer default 0)');c.execute('create index if not exists event_time on events(start,end)');return c
@contextmanager
def transaction():
 with closing(db()) as connection, connection:
  yield connection

def read_config(connection):
 return {row['k']:json.loads(row['v']) for row in connection.execute('select * from config')}

def write_config(connection, values):
 connection.executemany('insert or replace into config values(?,?)',[(key,json.dumps(value)) for key,value in values.items()])

def cfg():
 with transaction() as connection:return read_config(connection)

def setcfg(values):
 with transaction() as connection:write_config(connection,values)

def configure(options):
 if options.get('cloud'):
  raise ValueError('Облачная активность приостановлена')
 with transaction() as connection:
  connection.execute('BEGIN IMMEDIATE')
  old=read_config(connection)
  enabled=bool(options.get('enabled'))
  revision=old.get('consent_revision',0)+int(enabled!=bool(old.get('enabled')))
  write_config(connection,dict(enabled=enabled,location=False,cloud=False,session=None,owner='',
      consent_revision=revision, device_id=options.get('device_id') or old.get('device_id') or str(uuid.uuid4()),
      device_name=options.get('device_name') or old.get('device_name') or 'Linux Mint'))

def emit(kind,start,end,app=None,label=None,config=None,**extra):
 with transaction() as connection:
  connection.execute('BEGIN IMMEDIATE')
  current=read_config(connection)
  if not current.get('enabled') or end<start:return
  if config and any(config.get(key)!=current.get(key) for key in ('consent_revision','device_id','device_name')):return
  row=dict(id=str(uuid.uuid4()),device_id=current['device_id'],device_name=current['device_name'],
           platform='linux',kind=kind,app=app,label=label,start_ms=start,end_ms=end,lat=None,lon=None,accuracy=None)
  row.update(extra)
  connection.execute('insert into events(id,owner,start,end,payload) values(?,?,?,?,?)',
                      (row['id'],'',start,end,json.dumps(row)))

def rows(a,b):
 with transaction() as c:return [dict(json.loads(x['payload']),owner=x['owner']) for x in c.execute('select * from events where start<? and end>=? order by start',(b,a))]

def erase():
 with transaction() as connection:
  connection.execute('BEGIN IMMEDIATE')
  revision=read_config(connection).get('consent_revision',0)+1
  write_config(connection,{'enabled':False,'cloud':False,'location':False,'session':None,'owner':'',
                          'consent_revision':revision})
  connection.execute('delete from events')

HEARTBEAT_LIMIT=45
try:
 BOOT_ID=Path('/proc/sys/kernel/random/boot_id').read_text().strip()
except OSError:
 BOOT_ID=None

def heartbeat_age(health):
 if BOOT_ID and health.get('boot_id')==BOOT_ID and 'monotonic' in health:
  return time.monotonic()-health['monotonic']
 if BOOT_ID and health.get('boot_id'):
  return float('inf')
 age=time.time()-health.get('heartbeat',0)
 return age if age>=0 else float('inf')

def continuous_sample(wall_ms, monotonic_ms):
 return 0<wall_ms<=12000 and 0<monotonic_ms<=12000 and abs(wall_ms-monotonic_ms)<=1000

def source_error():
 if os.environ.get('XDG_SESSION_TYPE','').lower()=='wayland':
  return 'Wayland: сбор приложений доступен в сеансе X11'
 if not os.environ.get('DISPLAY'):
  return 'Сеанс X11 недоступен'
 for tool in ('xprintidle','xprop'):
  if not shutil.which(tool):
   return f'Не найдена утилита {tool}'
 return ''

def status():
 c=cfg()
 health=c.get('agent_health') or {}
 alive=0 <= heartbeat_age(health) < HEARTBEAT_LIMIT
 warning=source_error()
 if c.get('enabled') and not warning:
  warning=health.get('sample_error','') if alive else 'Агент запускается или не отвечает'
 return dict(platform='linux', enabled=c.get('enabled',False), usageAccess=not bool(source_error()),
             location=False, locationAccess=False, cloud=False, warning=warning)

def diagnostics():
 health=cfg().get('agent_health') or {}
 with transaction() as connection:
  count,last=connection.execute('select count(*), max(end) from events').fetchone()
 display=os.environ.get('DISPLAY','')
 safe_health={key:value for key,value in health.items() if key not in ('boot_id','monotonic')}
 return dict(status(), **safe_health, session_type=os.environ.get('XDG_SESSION_TYPE',''),
             display=display if re.fullmatch(r':\d+(?:\.\d+)?',display) else ('задан' if display else ''),
             xprop=bool(shutil.which('xprop')), xprintidle=bool(shutil.which('xprintidle')),
             local_count=count,last_event=last)

def cmd(*args):
 return subprocess.check_output(args,stderr=subprocess.DEVNULL,text=True,timeout=2).strip()

def sample():
 error=source_error()
 if error:return None,error
 try:
  idle=int(cmd('xprintidle'))
  if idle<0:raise ValueError('idle')
  if idle>=60000:return None,''
 except (OSError,ValueError,subprocess.SubprocessError):
  return None,'X11: не удалось определить время простоя (xprintidle)'
 try:
  window=cmd('xprop','-root','_NET_ACTIVE_WINDOW')
  match=re.search(r'0x[0-9a-fA-F]+',window)
  if not match:return None,'X11: оконный менеджер не сообщает активное окно'
  if int(match[0],16)==0:return None,''
  properties=cmd('xprop','-id',match[0],'WM_CLASS')
  names=re.findall(r'"([^"\n]+)"',properties)
  if not names:return None,'X11: у активного окна нет WM_CLASS'
  return (names[-1].lower(),names[-1]),''
 except (OSError,ValueError,subprocess.SubprocessError):
  return None,'X11: не удалось прочитать активное окно (xprop)'

def locked():
 for service,path in [('org.cinnamon.ScreenSaver','/org/cinnamon/ScreenSaver'),('org.freedesktop.ScreenSaver','/org/freedesktop/ScreenSaver')]:
  try:
   v=cmd('gdbus','call','--session','--dest',service,'--object-path',path,'--method',service+'.GetActive');return 'true' in v
  except (OSError,subprocess.SubprocessError):pass
 return None
def pause_legacy_cloud(config=None):
 config=cfg() if config is None else config
 if any(config.get(key) for key in ('cloud','session','url','key','owner')):
  setcfg({'cloud':False,'session':None,'url':None,'key':None,'owner':''})

def collector(next_program=lambda: None):
 last=None
 start=None
 previous=None
 previous_mono=None
 prior_lock=None
 signature=None
 while True:
  if program := next_program():return program
  now=int(time.time()*1000)
  mono=time.monotonic()
  config=cfg()
  pause_legacy_cloud(config)
  enabled=config.get('enabled',False)
  current_signature=(enabled,config.get('consent_revision'),config.get('device_id'),config.get('device_name'))
  if current_signature!=signature:
   last=None
   previous=None
   prior_lock=None
   start=now
   signature=current_signature
  lock=locked() if enabled and not source_error() else None
  current,error=sample() if enabled and lock is not True else (None,'')
  if last and previous is not None:
   contiguous=continuous_sample(now-previous,round((mono-previous_mono)*1000))
   # A suspend, clock jump or unavailable source can't establish the intervening usage.
   end=now if contiguous and not error else previous
   if current!=last or now-start>=15000 or not contiguous or error:
    if end>start:emit('app',start,end,*last,config=config)
    start=now
  if current!=last:start=now
  if enabled and prior_lock is True and lock is False and previous is not None and continuous_sample(now-previous,round((mono-previous_mono)*1000)):
   emit('unlock',now,now,config=config)
  setcfg({'agent_health':{'pid':os.getpid(),'heartbeat':time.time(),'last_sample':now if enabled else 0,
                          'last_app':current[1] if current else '', 'sample_error':error,
                          'monotonic':time.monotonic(),'boot_id':BOOT_ID}})
  last,previous,previous_mono,prior_lock=current,now,mono,lock
  time.sleep(5)

def stop_stale_agent(pid):
 # Pin the process before reading /proc so a reused PID cannot receive the signal.
 if not hasattr(os,'pidfd_open') or not hasattr(signal,'pidfd_send_signal'):
  return False
 try:
  handle=os.pidfd_open(pid)
 except (OSError,ValueError):
  return False
 try:
  process=Path('/proc')/str(pid)
  if process.stat().st_uid!=os.getuid():
   return False
  args=process.joinpath('cmdline').read_bytes().split(b'\0')
  if (len(args)!=3 or Path(os.fsdecode(args[0])).resolve()!=Path(sys.executable).resolve()
      or Path(os.fsdecode(args[1])).resolve()!=Path(__file__).resolve()):
   return False
  signal.pidfd_send_signal(handle,signal.SIGTERM)
  signal.pidfd_send_signal(handle,signal.SIGCONT)
  return True
 except (OSError,ValueError):
  return False
 finally:
  os.close(handle)

class Supervisor:
 def __init__(self):
  self.child=None
  self.started=0

 def ensure(self):
  now=time.monotonic()
  health=cfg().get('agent_health') or {}
  age=heartbeat_age(health)
  if self.child and self.child.poll() is None:
   age=min(now-self.started,age) if health.get('pid')==self.child.pid else now-self.started
   if age>HEARTBEAT_LIMIT:
    self.child.terminate()
    try:self.child.send_signal(signal.SIGCONT)
    except ProcessLookupError:pass
   return
  if 0<=age<HEARTBEAT_LIMIT and health.get('pid'):
   try:
    os.kill(health['pid'],0)
    return
   except ProcessLookupError:
    pass
   except PermissionError:
    return
  if health.get('pid') and age>=HEARTBEAT_LIMIT:
   if stop_stale_agent(health['pid']):
    return
  self.started=now
  self.child=subprocess.Popen([sys.executable,str(Path(__file__).with_name('agent.py'))],
                              start_new_session=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)

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
