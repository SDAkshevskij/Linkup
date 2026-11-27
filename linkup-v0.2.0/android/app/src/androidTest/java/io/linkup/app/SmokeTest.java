package io.linkup.app;

import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SmokeTest {
    private String js(ActivityScenario<MainActivity> scenario, String script) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        scenario.onActivity(a -> a.web.evaluateJavascript(script, value -> {
            result.set(value); latch.countDown();
        }));
        assertTrue("WebView evaluation timed out", latch.await(10, TimeUnit.SECONDS));
        return result.get();
    }
    private void waitTrue(ActivityScenario<MainActivity> scenario, String script) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        String last = null;
        do {
            last = js(scenario, script);
            if ("true".equals(last)) return;
            Thread.sleep(250);
        } while (System.nanoTime() < deadline);
        fail("Condition not satisfied: " + script + "; last=" + last);
    }
    @Test public void opensLocalUiAndActivityScreen() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitTrue(scenario, "document.readyState === 'complete' && !!document.querySelector('#composer') && typeof window.linkupImportShare === 'function'");
            waitTrue(scenario, "(()=>{try{const r=JSON.parse(LinkUpNative.call('status','{}'));return !r.error && typeof r.value==='object';}catch(e){return false;}})()");
            js(scenario, "document.querySelector('[data-page=activity]').click(); true");
            waitTrue(scenario, "!document.querySelector('#activityView').hidden && !!document.querySelector('#actTimeline')");
            waitTrue(scenario, "document.querySelector('#activityStatus').textContent.length > 0");
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
