package io.linkup.app;

import android.net.ConnectivityManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.charset.StandardCharsets;
import static io.linkup.app.WebViewProbe.js;
import static io.linkup.app.WebViewProbe.waitTrue;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ActivityIntegrationTest {
    @Test public void recordsActualForegroundTransitionsWhileOffline() throws Exception {
        Assume.assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("requireCutout")));
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String ops="appops set io.linkup.app GET_USAGE_STATS ";
        String airplane=shell("settings get global airplane_mode_on").trim();
        shell("cmd connectivity airplane-mode enable");
        shell(ops+"deny");
        try(Collector collector=new Collector(context);
            ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            var network=context.getSystemService(ConnectivityManager.class);
            long offlineBy=SystemClock.elapsedRealtime()+8000;
            while(network.getActiveNetwork()!=null&&SystemClock.elapsedRealtime()<offlineBy)Thread.sleep(150);
            assertNull("The collector test is actually offline",network.getActiveNetwork());
            collector.erase();
            long consent=System.currentTimeMillis();
            collector.configure(new JSONObject().put("enabled",true).put("device_name","Emulator fixture"));
            collector.collect();
            assertFalse(collector.status().getBoolean("usageAccess"));
            assertEquals(0,collector.rows(consent,System.currentTimeMillis()+1).length());
            shell(ops+"allow");
            assertTrue(collector.status().getBoolean("usageAccess"));
            assertTrue(shell("am start -W -a android.settings.SETTINGS").contains("Status: ok"));
            Thread.sleep(3000);
            assertTrue(shell("cmd jobscheduler run -f io.linkup.app 1001").contains("Running job"));
            long deadline=SystemClock.elapsedRealtime()+15000;
            boolean observed=false;
            do{
                Thread.sleep(350);
                observed=containsSettings(collector.rows(consent,System.currentTimeMillis()+1));
            }while(!observed&&SystemClock.elapsedRealtime()<deadline);
            assertTrue("The deferred job recorded an actual Settings session",observed);
            assertTrue(collector.diagnostics().getLong("last_job")>=consent);
            shell("input keyevent KEYCODE_BACK");
            waitTrue(scenario,MobileUiTest.READY);
            js(scenario,"document.querySelector('[data-page=activity]').click();true");
            waitTrue(scenario,"document.querySelector('#actTimeline .time-dot[title*=\"Settings\"],"
                    + "#actTimeline .time-dot[title*=\"com.android.settings\"]')!==null");
            MobileScreenshotTest.capture(scenario,"activity-local");
            js(scenario,"document.querySelector('#actDiagnostics').click();true");
            waitTrue(scenario,"document.querySelector('#activityFacts').textContent.includes('Локальные события')");
            long previous=collector.diagnostics().getLong("last_success");
            shell(ops+"deny");
            collector.collect();
            assertEquals(previous,collector.diagnostics().getLong("last_success"));
            assertFalse(collector.diagnostics().getBoolean("usageAccess"));
            assertTrue(containsSettings(collector.rows(consent,System.currentTimeMillis()+1)));
            js(scenario,"document.querySelector('#activityInspector').close();document.querySelector('#actRefresh').click();true");
            waitTrue(scenario,"document.querySelector('#activityStatus').textContent.includes('доступ')");
            MobileScreenshotTest.capture(scenario,"activity-denied");
            js(scenario,"document.querySelector('#actSettings').click();true");
            waitTrue(scenario,"!document.querySelector('#trackPermission').hidden");
        }finally{
            shell(ops+"default");
            if(!"1".equals(airplane))shell("cmd connectivity airplane-mode disable");
            try(Collector collector=new Collector(context)){collector.erase();}
        }
    }

    private static boolean containsSettings(JSONArray rows) throws Exception {
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);
            if("com.android.settings".equals(row.optString("app"))
                    && row.getLong("end_ms")>row.getLong("start_ms"))return true;
        }
        return false;
    }

    private static String shell(String cmd) throws Exception{
        var automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try(var input=new ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(cmd))){
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
}
