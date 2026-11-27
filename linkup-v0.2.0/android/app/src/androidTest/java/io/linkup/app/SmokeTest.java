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
        }
    }
}
