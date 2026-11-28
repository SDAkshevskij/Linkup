package io.linkup.app;

import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static io.linkup.app.WebViewProbe.js;
import static io.linkup.app.WebViewProbe.waitTrue;

@RunWith(AndroidJUnit4.class)
public class SmokeTest {
    @Test public void opensLocalUiAndActivityScreen() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitTrue(scenario, "document.readyState === 'complete' && !!document.querySelector('#composer') && typeof window.linkupImportShare === 'function'");
            waitTrue(scenario, "(()=>{try{const r=JSON.parse(LinkUpNative.call('status','{}'));return !r.error && typeof r.value==='object';}catch(e){return false;}})()");
            js(scenario, "document.querySelector('[data-page=activity]').click(); true");
            waitTrue(scenario, "!document.querySelector('#activityView').hidden && !!document.querySelector('#actTimeline')");
            waitTrue(scenario, "document.querySelector('#activityStatus').textContent.length > 0");
        }
    }
    @Test public void showsSettingsErrorsWithoutLeavingAndroidDialog() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitTrue(scenario, "typeof window.linkupImportShare==='function'");
            js(scenario, "document.querySelector('#mobileSettings').click();"
                    + "document.querySelector('#sbUrl').value='invalid-url';document.querySelector('#saveCloud').click();true");
            waitTrue(scenario, "document.querySelector('#settingsDialog').open && "
                    + "document.querySelector('#supabaseStatus').textContent.includes('HTTPS')");
            js(scenario, "document.querySelector('#driveConnect').click();true");
            waitTrue(scenario, "document.querySelector('#diskStatus').textContent.includes('Supabase')");
        }
    }
    @Test public void importsSharedText() throws Exception {
        Context c = ApplicationProvider.getApplicationContext();
        Intent i = new Intent(c, MainActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, "LinkUp CI share test");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(i)) {
            waitTrue(scenario, "!!document.querySelector('#message') && document.querySelector('#message').value.includes('LinkUp CI share test')");
            scenario.recreate();
            waitTrue(scenario, "document.querySelector('#message').value === 'LinkUp CI share test'");
        }
    }

    @Test public void queuesTwoSharedIntentsInOrderBeforeTheWebViewReadsThem() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        // ActivityScenario matches the current Intent after onNewIntent by action and MIME type.
        // Use the same filter for initial launch and both subsequent system deliveries.
        Intent shareRoute = new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_SEND).setType("text/plain");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(shareRoute)) {
            waitTrue(scenario, MobileUiTest.READY);
            js(scenario,"window.resumeShares=window.linkupImportShare;window.linkupImportShare=()=>{};true");
            scenario.onActivity(activity -> {
                for (String text : new String[]{"First synthetic share", "Second synthetic share"}) {
                    activity.startActivity(new Intent(shareRoute)
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(Intent.EXTRA_TEXT,text));
                }
            });
            waitTrue(scenario,"JSON.parse(LinkUpNative.call('shareList','{}')).value.text==='First synthetic share'");
            js(scenario,"window.linkupImportShare=window.resumeShares;window.linkupImportShare();true");
            waitTrue(scenario,"document.querySelector('#message').value==='First synthetic share\\nSecond synthetic share'");
        }
    }
}
