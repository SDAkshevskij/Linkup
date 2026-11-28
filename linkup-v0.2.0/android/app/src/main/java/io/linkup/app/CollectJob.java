package io.linkup.app;

import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;

public class CollectJob extends JobService {
 static final int ID=1001;
 static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
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
   try{collector.collect();collector.collectLocation();}
   catch(Exception failure){collector.p.edit().putString("collect_error","Не удалось считать данные Android").apply();}
  }
 }
 @Override public boolean onStartJob(JobParameters params){
  getSharedPreferences("activity",0).edit().putLong("last_job",System.currentTimeMillis()).apply();
  EXEC.execute(()->{try{run(this);}finally{jobFinished(params,false);}});
  return true;
 }
 @Override public boolean onStopJob(JobParameters params){return true;}
}
