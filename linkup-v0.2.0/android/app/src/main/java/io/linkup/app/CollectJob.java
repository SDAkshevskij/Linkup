package io.linkup.app;
import android.app.job.*;import android.content.*;import java.util.concurrent.*;
public class CollectJob extends JobService {
 static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
 static void schedule(Context c){JobScheduler s=c.getSystemService(JobScheduler.class);s.schedule(new JobInfo.Builder(1001,new ComponentName(c,CollectJob.class)).setPeriodic(15*60*1000L).setPersisted(true).build());}
 static void run(Context c){Collector a=new Collector(c);try{a.collect();a.collectLocation();a.sync();}catch(Exception e){a.p.edit().putString("error",e.getMessage()).apply();}finally{a.close();}}
 @Override public boolean onStartJob(JobParameters p){EXEC.execute(()->{try{run(this);}finally{jobFinished(p,false);}});return true;}
 @Override public boolean onStopJob(JobParameters p){return true;}
}
