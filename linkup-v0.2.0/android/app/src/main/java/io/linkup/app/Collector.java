package io.linkup.app;
import android.Manifest;import android.app.*;import android.app.usage.*;import android.content.*;import android.content.pm.PackageManager;import android.database.*;import android.database.sqlite.*;import android.location.*;import android.os.*;import org.json.*;import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;
final class Collector extends SQLiteOpenHelper {
 static final Object LOCK=new Object(); final Context c; final SharedPreferences p;
 Collector(Context context){super(context,"activity.db",null,1);c=context.getApplicationContext();p=c.getSharedPreferences("activity",0);}
 public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY, owner TEXT NOT NULL, start INTEGER, end INTEGER, payload TEXT NOT NULL, sent INTEGER DEFAULT 0)");d.execSQL("CREATE INDEX event_time ON events(start,end)");}
 public void onUpgrade(SQLiteDatabase d,int a,int b){}
 boolean allowed(){AppOpsManager a=c.getSystemService(AppOpsManager.class);return a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),c.getPackageName())==AppOpsManager.MODE_ALLOWED;}
 boolean locationAllowed(){return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 JSONObject status()throws Exception{return new JSONObject().put("enabled",p.getBoolean("enabled",false)).put("location",p.getBoolean("location",false)).put("cloud",p.getBoolean("cloud",false)).put("warning",!allowed()?"Нет доступа к статистике использования":p.getBoolean("location",false)&&!locationAllowed()?"Нет разрешения геолокации":p.getBoolean("location",false)&&c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED?"Геолокация в фоне не разрешена":"Android · фоновый сбор по расписанию").put("error",p.getString("error",""));}
 void configure(JSONObject a)throws Exception{synchronized(LOCK){boolean enabled=a.optBoolean("enabled"), cloud=a.optBoolean("cloud");String owner=a.optString("user_id"),url=a.optString("url"),key=a.optString("key");SharedPreferences.Editor ed=p.edit();if(cloud){if(owner.isEmpty())throw new Exception("Сначала войдите в Supabase");if(!a.optString("password").isEmpty()){JSONObject s=api(url,key,"/auth/v1/token?grant_type=password",new JSONObject().put("email",a.optString("email")).put("password",a.optString("password")),null,null);if(!owner.equals(s.getJSONObject("user").getString("id")))throw new Exception("Аккаунты не совпадают");ed.putString("session",s.toString());}else if(!p.getBoolean("cloud",false)||!owner.equals(p.getString("owner",""))||!url.equals(p.getString("url",""))||p.getString("session","").isEmpty())throw new Exception("Введите пароль для фоновой сессии");ed.putString("url",url).putString("key",key);}else ed.remove("session");if(enabled!=p.getBoolean("enabled",false)||!owner.equals(p.getString("owner",""))){ed.putLong("cursor",System.currentTimeMillis()).remove("app").remove("screen");}if(a.optBoolean("location")&&!p.getBoolean("location",false))ed.putLong("location_since",System.currentTimeMillis());ed.putBoolean("enabled",enabled).putBoolean("location",a.optBoolean("location")).putBoolean("cloud",cloud).putString("owner",cloud?owner:"").putString("device_id",a.optString("device_id",UUID.randomUUID().toString())).putString("device_name",a.optString("device_name","Android")).putString("error","").commit();}}
 void erase(){synchronized(LOCK){p.edit().putBoolean("enabled",false).putBoolean("cloud",false).putBoolean("location",false).remove("session").remove("cursor").remove("app").remove("screen").putString("owner","").commit();getWritableDatabase().delete("events",null,null);}}
 void disableCloud(){synchronized(LOCK){p.edit().putBoolean("cloud",false).remove("session").putString("owner","").putLong("cursor",System.currentTimeMillis()).remove("app").remove("screen").apply();}}
 String label(String pkg){try{return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception e){return pkg;}}
 void emit(SQLiteDatabase d,String kind,long start,long end,String app,Location loc)throws Exception{if(end<start||kind.equals("app")&&end==start)return;String id=UUID.nameUUIDFromBytes((p.getString("device_id","")+":"+kind+":"+start+":"+end+":"+app).getBytes(StandardCharsets.UTF_8)).toString();JSONObject r=new JSONObject().put("id",id).put("device_id",p.getString("device_id","")).put("device_name",p.getString("device_name","Android")).put("platform","android").put("kind",kind).put("start_ms",start).put("end_ms",end);r.put("app",app==null?JSONObject.NULL:app).put("label",app==null?JSONObject.NULL:label(app)).put("lat",loc==null?JSONObject.NULL:loc.getLatitude()).put("lon",loc==null?JSONObject.NULL:loc.getLongitude()).put("accuracy",loc==null?JSONObject.NULL:loc.getAccuracy());ContentValues v=new ContentValues();v.put("id",id);v.put("owner",p.getString("owner",""));v.put("start",start);v.put("end",end);v.put("payload",r.toString());d.insertWithOnConflict("events",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 void collect()throws Exception{synchronized(LOCK){if(!p.getBoolean("enabled",false)||!allowed())return;long now=System.currentTimeMillis();long cursor=p.getLong("cursor",now);if(cursor>now||now-cursor>3*86400000L)cursor=Math.max(0,now-3*86400000L);String app=p.getString("app","");boolean screen=p.getBoolean("screen",false);if(now-p.getLong("cursor",now)>3*86400000L){app="";screen=false;}UsageEvents events=c.getSystemService(UsageStatsManager.class).queryEvents(cursor,now);if(events==null)return;SQLiteDatabase d=getWritableDatabase();long start=cursor;d.beginTransaction();try{UsageEvents.Event e=new UsageEvents.Event();while(events.hasNextEvent()){events.getNextEvent(e);long t=e.getTimeStamp();if(t<cursor||t>=now)continue;int type=e.getEventType();String pkg=e.getPackageName();boolean boundary=type==UsageEvents.Event.ACTIVITY_RESUMED||type==UsageEvents.Event.ACTIVITY_PAUSED||type==UsageEvents.Event.SCREEN_NON_INTERACTIVE||type==UsageEvents.Event.SCREEN_INTERACTIVE||type==UsageEvents.Event.KEYGUARD_SHOWN||type==UsageEvents.Event.KEYGUARD_HIDDEN||type==UsageEvents.Event.DEVICE_SHUTDOWN||type==UsageEvents.Event.DEVICE_STARTUP;if(!boundary)continue;if(screen&&!app.isEmpty())emit(d,"app",start,t,app,null);start=t;if(type==UsageEvents.Event.ACTIVITY_RESUMED){app=pkg==null?"":pkg;screen=true;}else if(type==UsageEvents.Event.ACTIVITY_PAUSED&&app.equals(pkg))app="";else if(type==UsageEvents.Event.SCREEN_NON_INTERACTIVE||type==UsageEvents.Event.KEYGUARD_SHOWN||type==UsageEvents.Event.DEVICE_SHUTDOWN||type==UsageEvents.Event.DEVICE_STARTUP){screen=false;app="";}else if(type==UsageEvents.Event.SCREEN_INTERACTIVE){screen=true;emit(d,"screen",t,t,null,null);}else if(type==UsageEvents.Event.KEYGUARD_HIDDEN){screen=true;emit(d,"unlock",t,t,null,null);}}if(screen&&!app.isEmpty())emit(d,"app",start,now,app,null);d.setTransactionSuccessful();}finally{d.endTransaction();}p.edit().putLong("cursor",now).putString("app",app).putBoolean("screen",screen).commit();}}
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
 void sync(){synchronized(LOCK){if(!p.getBoolean("cloud",false))return;try{String owner=p.getString("owner",""),url=p.getString("url",""),key=p.getString("key","");JSONObject s=new JSONObject(p.getString("session","{}"));if(s.optLong("expires_at",0)<System.currentTimeMillis()/1000+60){s=api(url,key,"/auth/v1/token?grant_type=refresh_token",new JSONObject().put("refresh_token",s.getString("refresh_token")),null,null);if(!s.has("expires_at"))s.put("expires_at",System.currentTimeMillis()/1000+s.optLong("expires_in",3600));p.edit().putString("session",s.toString()).commit();}SQLiteDatabase d=getWritableDatabase();JSONArray batch=new JSONArray();ArrayList<String> ids=new ArrayList<>();try(Cursor q=d.rawQuery("select id,payload from events where owner=? and sent=0 order by start limit 200",new String[]{owner})){while(q.moveToNext()){ids.add(q.getString(0));batch.put(new JSONObject(q.getString(1)).put("user_id",owner));}}if(batch.length()>0){api(url,key,"/rest/v1/linkup_activity?on_conflict=id",batch,s.getString("access_token"),"resolution=ignore-duplicates,return=minimal");for(String id:ids)d.execSQL("update events set sent=1 where id=?",new String[]{id});}p.edit().putString("error","").apply();}catch(Exception e){p.edit().putString("error","Синхронизация: "+e.getMessage()).apply();}}}
 static JSONObject api(String url,String key,String path,Object body,String token,String prefer)throws Exception{URL u=new URL(url.replaceAll("/$","")+path);if(!u.getProtocol().equals("https"))throw new Exception("Требуется HTTPS");HttpURLConnection h=(HttpURLConnection)u.openConnection();h.setInstanceFollowRedirects(false);h.setConnectTimeout(15000);h.setReadTimeout(20000);h.setRequestMethod("POST");h.setRequestProperty("apikey",key);h.setRequestProperty("Content-Type","application/json");if(token!=null)h.setRequestProperty("Authorization","Bearer "+token);if(prefer!=null)h.setRequestProperty("Prefer",prefer);h.setDoOutput(true);try(OutputStream o=h.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}try{int code=h.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);try(InputStream in=h.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.size()==0?new JSONObject():new JSONObject(out.toString("UTF-8"));}}finally{h.disconnect();}}
}
