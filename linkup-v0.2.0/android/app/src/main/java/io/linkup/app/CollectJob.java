package io.linkup.app;

import android.app.job.*;
import android.content.*;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CollectJob extends JobService {
 static final int ID=1001;
 private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
 private static final AtomicBoolean FOREGROUND_PENDING=new AtomicBoolean();
 private final Handler main=new Handler(Looper.getMainLooper());
 private Future<?> scheduled;
 private JobParameters current;

 static void requestForeground(Context context){
  if(!FOREGROUND_PENDING.compareAndSet(false,true))return;
  Context app=context.getApplicationContext();
  EXEC.execute(()->{
   try{run(app);}finally{FOREGROUND_PENDING.set(false);}
  });
 }
 static void schedule(Context context){
  JobScheduler scheduler=context.getSystemService(JobScheduler.class);
  try(Collector collector=new Collector(context)){
   if(scheduler.getPendingJob(ID)!=null)return;
   int result=scheduler.schedule(new JobInfo.Builder(ID,new ComponentName(context,CollectJob.class))
       .setPeriodic(TimeUnit.MINUTES.toMillis(15)).setPersisted(true).build());
   collector.p.edit().putString("job_error",result==JobScheduler.RESULT_SUCCESS?"":"Планировщик отклонил задачу").apply();
  }catch(RuntimeException failure){
   context.getSharedPreferences("activity",0).edit().putString("job_error","Ошибка планировщика").apply();
  }
 }
 static void run(Context context){
  try(Collector collector=new Collector(context)){
   try{collector.collect();if(!Thread.currentThread().isInterrupted())collector.collectLocation();}
   catch(Exception failure){collector.p.edit().putString("collect_error","Не удалось считать данные Android").apply();}
  }
 }
 @Override public boolean onStartJob(JobParameters params){
  getSharedPreferences("activity",0).edit().putLong("last_job",System.currentTimeMillis()).apply();
  current=params;
  scheduled=EXEC.submit(()->{
   try{run(this);}finally{
    main.post(()->{
     if(current!=params)return;
     current=null;
     scheduled=null;
     jobFinished(params,false);
    });
   }
  });
  return true;
 }
 @Override public boolean onStopJob(JobParameters params){
  if(current==params){
   current=null;
   if(scheduled!=null)scheduled.cancel(true);
   scheduled=null;
  }
  return true;
 }
}
