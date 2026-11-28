package io.linkup.app;
import android.Manifest;import android.app.*;import android.app.usage.*;import android.content.*;import android.content.pm.PackageManager;import android.database.*;import android.database.sqlite.*;import android.location.*;import android.os.*;import org.json.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;
final class Collector extends SQLiteOpenHelper {
 static final Object LOCK=new Object(); final Context c; final SharedPreferences p;
 Collector(Context context){super(context,"activity.db",null,1);c=context.getApplicationContext();p=c.getSharedPreferences("activity",0);}
 public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY, owner TEXT NOT NULL, start INTEGER, end INTEGER, payload TEXT NOT NULL, sent INTEGER DEFAULT 0)");d.execSQL("CREATE INDEX event_time ON events(start,end)");}
 public void onUpgrade(SQLiteDatabase d,int a,int b){}
 boolean allowed(){AppOpsManager a=c.getSystemService(AppOpsManager.class);return a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),c.getPackageName())==AppOpsManager.MODE_ALLOWED;}
 boolean locationAllowed(){return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 JSONObject status() throws JSONException {
  boolean enabled=p.getBoolean("enabled",false), access=allowed(), location=p.getBoolean("location",false);
  return new JSONObject().put("platform","android").put("enabled",enabled).put("usageAccess",access)
      .put("location",location).put("locationAccess",locationAllowed()).put("cloud",false)
      .put("warning",enabled&&!access?"Нужен доступ к статистике приложений"
          :location&&!locationAllowed()?"Нужен доступ к геолокации":"")
      .put("error",p.getString("collect_error",""));
 }
 JSONObject diagnostics() throws JSONException {
  JSONObject result=status();
  for(String key:new String[]{"cursor","last_attempt","last_success","last_boundary","last_job"})
   result.put(key,p.getLong(key,0));
  result.put("last_app",p.getString("last_app",""));
  result.put("job_scheduled",c.getSystemService(android.app.job.JobScheduler.class)
      .getPendingJob(CollectJob.ID)!=null);
  result.put("job_error",p.getString("job_error",""));
  try(Cursor rows=getReadableDatabase().rawQuery("SELECT count(*), max(end) FROM events",null)){
   if(rows.moveToFirst())result.put("local_count",rows.getLong(0)).put("last_event",rows.getLong(1));
  }
  return result;
 }
 void configure(JSONObject options) {
  if(options.optBoolean("cloud"))throw new IllegalArgumentException("Облачная активность приостановлена");
  synchronized(LOCK) {
   boolean enabled=options.optBoolean("enabled"), location=options.optBoolean("location");
   SharedPreferences.Editor next=localOnly(p.edit());
   if(enabled!=p.getBoolean("enabled",false)) {
    next.putLong("cursor",System.currentTimeMillis()).putBoolean("bootstrap",true)
        .remove("app").remove("screen").remove("collect_error");
   }
   if(location&&!p.getBoolean("location",false))next.putLong("location_since",System.currentTimeMillis());
   next.putBoolean("enabled",enabled).putBoolean("location",location)
       .putString("device_id",options.optString("device_id",p.getString("device_id",UUID.randomUUID().toString())))
       .putString("device_name",options.optString("device_name",p.getString("device_name","Android"))).commit();
  }
 }
 private SharedPreferences.Editor localOnly(SharedPreferences.Editor edit) {
  return edit.putBoolean("cloud",false).putString("owner","").remove("session").remove("url").remove("key").remove("error");
 }
 void erase(){
  synchronized(LOCK){
   localOnly(p.edit()).putBoolean("enabled",false).putBoolean("location",false)
       .remove("cursor").remove("app").remove("screen").remove("collect_error").commit();
   getWritableDatabase().delete("events",null,null);
  }
 }
 void disableCloud(){synchronized(LOCK){localOnly(p.edit()).apply();}}
 String label(String pkg){try{return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception e){return pkg;}}
 void emit(SQLiteDatabase d,String kind,long start,long end,String app,Location loc)throws Exception{if(end<start||kind.equals("app")&&end==start)return;String id=UUID.nameUUIDFromBytes((p.getString("device_id","")+":"+kind+":"+start+":"+end+":"+app).getBytes(StandardCharsets.UTF_8)).toString();JSONObject r=new JSONObject().put("id",id).put("device_id",p.getString("device_id","")).put("device_name",p.getString("device_name","Android")).put("platform","android").put("kind",kind).put("start_ms",start).put("end_ms",end);r.put("app",app==null?JSONObject.NULL:app).put("label",app==null?JSONObject.NULL:label(app)).put("lat",loc==null?JSONObject.NULL:loc.getLatitude()).put("lon",loc==null?JSONObject.NULL:loc.getLongitude()).put("accuracy",loc==null?JSONObject.NULL:loc.getAccuracy());ContentValues v=new ContentValues();v.put("id",id);v.put("owner","");v.put("start",start);v.put("end",end);v.put("payload",r.toString());d.insertWithOnConflict("events",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 private static final long LOOKBACK=TimeUnit.MINUTES.toMillis(30);
 private static final long DELIVERY_DELAY=1500;
 private static final class UsageState {
  String app="",activity="";
  boolean screen;
  boolean transition(UsageEvents.Event event){
   String pkg=event.getPackageName(), name=event.getClassName();
   switch(event.getEventType()){
    case UsageEvents.Event.ACTIVITY_RESUMED: app=pkg==null?"":pkg; activity=name==null?"":name; screen=true; return true;
    case UsageEvents.Event.ACTIVITY_PAUSED:
    case UsageEvents.Event.ACTIVITY_STOPPED:
     if(!app.isEmpty()&&app.equals(pkg)&&(name==null||activity.isEmpty()||activity.equals(name))){app="";activity="";return true;}
     return false;
    case UsageEvents.Event.SCREEN_NON_INTERACTIVE:
    case UsageEvents.Event.KEYGUARD_SHOWN:
    case UsageEvents.Event.DEVICE_SHUTDOWN:
    case UsageEvents.Event.DEVICE_STARTUP:
     screen=false;app="";activity="";return true;
    case UsageEvents.Event.SCREEN_INTERACTIVE:
    case UsageEvents.Event.KEYGUARD_HIDDEN:
     screen=true;return true;
    default:return false;
   }
  }
 }
 void collect() throws Exception {
  synchronized(LOCK){
   if(p.getBoolean("cloud",false)||p.contains("session")||p.contains("url")||p.contains("key"))disableCloud();
   if(!p.getBoolean("enabled",false))return;
   long now=System.currentTimeMillis();
   p.edit().putLong("last_attempt",now).apply();
   if(!allowed()){
    p.edit().putBoolean("bootstrap",true).putLong("cursor",now).remove("app").remove("screen")
        .putString("collect_error","Нет доступа к статистике приложений").apply();
    return;
   }
   long cursor=p.getLong("cursor",now), end=now-DELIVERY_DELAY;
   if(cursor>now||now-cursor>TimeUnit.DAYS.toMillis(3)){
    p.edit().putBoolean("bootstrap",true).putLong("cursor",now).remove("app").remove("screen").apply();
    return;
   }
   if(end<=cursor)return;
   UsageStatsManager usage=c.getSystemService(UsageStatsManager.class);
   UsageEvents events=usage.queryEvents(cursor,end);
   if(events==null){
    p.edit().putString("collect_error","Система пока не отдала события").apply();
    return;
   }
   UsageState state=new UsageState();
   UsageEvents.Event event=new UsageEvents.Event();
   boolean bootstrap=p.getBoolean("bootstrap",false)||!p.contains("screen");
   if(bootstrap){
    UsageEvents prior=usage.queryEvents(Math.max(0,cursor-LOOKBACK),cursor);
    while(prior!=null&&prior.hasNextEvent()&&prior.getNextEvent(event))state.transition(event);
   }else{
    state.app=p.getString("app","");
    state.screen=p.getBoolean("screen",false);
    state.activity=p.getString("activity_class","");
   }

   SQLiteDatabase db=getWritableDatabase();
   long start=cursor, boundary=p.getLong("last_boundary",0);
   String lastApp=state.app.isEmpty()?p.getString("last_app",""):state.app;
   db.beginTransaction();
   try{
    while(events.hasNextEvent()&&events.getNextEvent(event)){
     long t=event.getTimeStamp();
     if(t<cursor||t>=end)continue;
     String previous=state.app;
     boolean visible=state.screen;
     if(!state.transition(event))continue;
     if(!state.app.isEmpty())lastApp=state.app;
     if(visible&&!previous.isEmpty())emit(db,"app",start,t,previous,null);
     start=t;
     boundary=t;
     if(event.getEventType()==UsageEvents.Event.SCREEN_INTERACTIVE)emit(db,"screen",t,t,null,null);
     if(event.getEventType()==UsageEvents.Event.KEYGUARD_HIDDEN)emit(db,"unlock",t,t,null,null);
    }
    if(!allowed())return;
    if(state.screen&&!state.app.isEmpty())emit(db,"app",start,end,state.app,null);
    db.setTransactionSuccessful();
   }finally{db.endTransaction();}
   p.edit().putLong("cursor",end).putString("app",state.app).putBoolean("screen",state.screen)
       .putString("activity_class",state.activity).putString("last_app",lastApp)
       .putBoolean("bootstrap",false).putLong("last_boundary",boundary)
       .putLong("last_success",now).remove("collect_error").commit();
  }
 }
 void collectLocation() throws Exception {
  if (!p.getBoolean("enabled", false) || !p.getBoolean("location", false) || !locationAllowed()) return;

  LocationManager manager = c.getSystemService(LocationManager.class);
  Location best = null;
  for (String provider : manager.getProviders(true)) {
   try {
    Location cached = manager.getLastKnownLocation(provider);
    if (cached != null && (best == null || cached.getTime() > best.getTime())) best = cached;
   } catch (SecurityException denied) {
    // Coarse access may still allow another provider, even if this one requires fine access.
    if (!locationAllowed()) return;
   } catch (IllegalArgumentException unavailable) {
    // A provider can disappear after getProviders().
   }
  }

  long now = System.currentTimeMillis();
  if ((best == null || now - best.getTime() > TimeUnit.MINUTES.toMillis(5))
      && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
   String provider = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
       && c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
       ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
   if (manager.isProviderEnabled(provider)) {
    CountDownLatch ready = new CountDownLatch(1);
    Location[] result = new Location[1];
    CancellationSignal signal = new CancellationSignal();
    try {
     manager.getCurrentLocation(provider, signal, Runnable::run, location -> {
      result[0] = location;
      ready.countDown();
     });
     if (ready.await(12, TimeUnit.SECONDS) && result[0] != null) best = result[0];
    } catch (SecurityException denied) {
     return;
    } catch (IllegalArgumentException unavailable) {
     // The cached fix can still be used if it is recent.
    } catch (InterruptedException interrupted) {
     Thread.currentThread().interrupt();
     return;
    } finally {
     signal.cancel();
    }
   }
  }

  if (best == null) return;
  long time = best.getTime();
  synchronized (LOCK) {
   if (!p.getBoolean("enabled", false) || !p.getBoolean("location", false) || !locationAllowed()
       || now - time > TimeUnit.MINUTES.toMillis(15) || time <= p.getLong("last_location", 0)
       || time < p.getLong("location_since", 0)) return;
   emit(getWritableDatabase(), "location", time, time, null, best);
   p.edit().putLong("last_location", time).apply();
  }
 }
 JSONArray rows(long a,long b)throws Exception{JSONArray r=new JSONArray();try(Cursor q=getReadableDatabase().rawQuery("select payload,owner from events where start<? and end>=? order by start",new String[]{""+b,""+a})){while(q.moveToNext())r.put(new JSONObject(q.getString(0)).put("owner",q.getString(1)));}return r;}
}
