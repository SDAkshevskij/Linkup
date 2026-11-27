package io.linkup.app;

import androidx.test.core.app.ActivityScenario;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

final class WebViewProbe {
    static String js(ActivityScenario<MainActivity> scenario, String script) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        scenario.onActivity(a -> a.web.evaluateJavascript(script, value -> {
            result.set(value); latch.countDown();
        }));
        assertTrue("WebView evaluation timed out", latch.await(10, TimeUnit.SECONDS));
        return result.get();
    }
    static void waitTrue(ActivityScenario<MainActivity> scenario, String script) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        String last = null;
        do {
            last = js(scenario, script);
            if ("true".equals(last)) return;
            Thread.sleep(250);
        } while (System.nanoTime() < deadline);
        fail("Condition not satisfied: " + script + "; last=" + last);
    }
}
