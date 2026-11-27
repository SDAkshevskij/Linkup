package io.linkup.app;

import android.app.Instrumentation;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static io.linkup.app.WebViewProbe.js;
import static io.linkup.app.WebViewProbe.waitTrue;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MobileUiTest {
    static final String READY = "document.readyState==='complete' && typeof window.linkupImportShare==='function'";
    private static final String SETTINGS = "document.querySelector('#settingsDialog')";
    private static final String NO_OVERFLOW = "document.documentElement.scrollWidth <= window.innerWidth";

    @Test public void preservesDraftAndPendingConnectionDuringRotationAndRecreation() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            orient(scenario, false);
            waitTrue(scenario, READY);
            AtomicReference<WebView> original = new AtomicReference<>();
            scenario.onActivity(activity -> original.set(activity.web));

            js(scenario, """
                document.querySelector('[data-filter=files]').click();
                const message=document.querySelector('#message');
                message.value='Synthetic unsent draft';
                message.dispatchEvent(new Event('input'));
                const files=document.querySelector('#files'), transfer=new DataTransfer();
                transfer.items.add(new File(['test fixture'],'orientation.txt',{type:'text/plain'}));
                files.files=transfer.files;
                files.dispatchEvent(new Event('change'));
                document.querySelector('[data-page=activity]').click();
                document.querySelector('#mobileSettings').click();
                document.querySelector('#deviceName').value='Synthetic orientation fixture';
                document.querySelector('#password').value='synthetic-password';
                document.querySelector('#diskToken').value='synthetic-token';
                document.querySelector('#settingsDialog').scrollTop=120;
                true
                """);
            orient(scenario, true);
            waitTrue(scenario, SETTINGS + ".open && " + SETTINGS + ".scrollTop > 0");
            verifyDraft(scenario);

            scenario.recreate();
            scenario.onActivity(activity -> assertSame(original.get(), activity.web));
            verifyDraft(scenario);

            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            verifyDraft(scenario);

            js(scenario, """
                window.authCalls=0;
                window.fetch=()=>new Promise(resolve=>{
                    window.authCalls++;
                    window.finishAuth=()=>resolve(new Response('{}',{status:401}));
                });
                document.querySelector('#sbUrl').value='https://example.test';
                document.querySelector('#sbKey').value='synthetic-key';
                document.querySelector('#email').value='ci@example.test';
                document.querySelector('#saveCloud').click();
                true
                """);
            waitTrue(scenario, "window.authCalls===1 && document.querySelector('#saveCloud').disabled");
            orient(scenario, false);
            verifyDraft(scenario);
            waitTrue(scenario, "window.authCalls===1 && document.querySelector('#saveCloud').disabled"
                    + " && document.querySelector('#supabaseStatus').textContent.includes('Подключаемся')");
            js(scenario, "window.finishAuth();true");
            waitTrue(scenario, "window.authCalls===1 && !document.querySelector('#saveCloud').disabled"
                    + " && document.querySelector('#supabaseStatus').textContent.includes('HTTP 401')");
        }
    }

    @Test public void keepsFocusedInputsAndActionsReachableInBothOrientations() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            for (boolean landscape : new boolean[]{false, true}) {
                orient(scenario, landscape);
                waitTrue(scenario, READY);
                awaitInsets(scenario, false);
                waitTrue(scenario, NO_OVERFLOW);
                waitTrue(scenario, insideViewport(".topbar"));
                waitTrue(scenario, insideViewport("#composer"));
                js(scenario, "document.querySelector('#mobileSettings').click();true");
                waitTrue(scenario, SETTINGS + ".open && " + insideViewport("#settingsDialog"));
                scrollTo(scenario, "#checkUpdate");
                waitTrue(scenario, insideViewport("#checkUpdate") + " && " + insideViewport("#settingsDialog [data-close]"));
                waitTrue(scenario, SETTINGS + ".scrollHeight > " + SETTINGS + ".clientHeight && "
                        + SETTINGS + ".scrollTop > 0");
                waitTrue(scenario, "getComputedStyle(" + SETTINGS + ").scrollbarWidth==='thin'");

                tap(scenario, "#email");
                waitTrue(scenario, "document.activeElement.id==='email'");
                scenario.onActivity(activity -> ViewCompat.getWindowInsetsController(activity.web)
                        .show(WindowInsetsCompat.Type.ime()));
                awaitInsets(scenario, true);
                scrollTo(scenario, "#email");
                waitTrue(scenario, insideViewport("#email") + " && " + NO_OVERFLOW);
                waitTrue(scenario, insideViewport("#settingsDialog [data-close]"));
                scrollTo(scenario, "#saveCloud");
                waitTrue(scenario, insideViewport("#saveCloud"));
                MobileScreenshotTest.capture(landscape ? "landscape-keyboard" : "portrait-keyboard");

                hideKeyboard(scenario);
                js(scenario, "document.querySelector('#settingsDialog').close();true");
                tap(scenario, "#message");
                waitTrue(scenario, "document.activeElement.id==='message'");
                scenario.onActivity(activity -> ViewCompat.getWindowInsetsController(activity.web)
                        .show(WindowInsetsCompat.Type.ime()));
                awaitInsets(scenario, true);
                waitTrue(scenario, insideViewport("#message") + " && " + insideViewport("#send") + " && " + NO_OVERFLOW);
                hideKeyboard(scenario);
            }
        }
    }

    @Test public void keepsTheOpenDialogAfterNotificationShade() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            orient(scenario, false);
            waitTrue(scenario, READY);
            js(scenario, """
                document.querySelector('#mobileSettings').click();
                document.querySelector('#deviceName').value='Synthetic shade fixture';true
                """);
            awaitInsets(scenario, false);
            try {
                shell("cmd statusbar expand-notifications");
                awaitActivity(scenario, activity -> !activity.hasWindowFocus(), "notification shade opened");
            } finally {
                shell("cmd statusbar collapse");
            }
            awaitActivity(scenario, MainActivity::hasWindowFocus, "notification shade closed");
            awaitInsets(scenario, false);
            waitTrue(scenario, SETTINGS + ".open && document.querySelector('#deviceName').value==='Synthetic shade fixture'");
        }
    }

    static void orient(ActivityScenario<MainActivity> scenario, boolean landscape) throws Exception {
        scenario.onActivity(activity -> activity.setRequestedOrientation(landscape
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        awaitActivity(scenario, activity -> activity.getResources().getConfiguration().orientation
                == (landscape ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT), "orientation");
        waitTrue(scenario, landscape ? "innerWidth > innerHeight" : "innerWidth < innerHeight");
    }

    private static void verifyDraft(ActivityScenario<MainActivity> scenario) throws Exception {
        waitTrue(scenario, """
            document.querySelector('#settingsDialog').open &&
            document.querySelector('#deviceName').value==='Synthetic orientation fixture' &&
            document.querySelector('#password').value==='synthetic-password' &&
            document.querySelector('#diskToken').value==='synthetic-token' &&
            document.querySelector('#message').value==='Synthetic unsent draft' &&
            document.querySelector('#staged').textContent.includes('orientation.txt') &&
            document.querySelector('[data-filter=files]').classList.contains('active') &&
            document.body.classList.contains('activity-mode')
            """);
    }

    private static void awaitInsets(ActivityScenario<MainActivity> scenario, boolean imeVisible) throws Exception {
        boolean requireCutout = Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("requireCutout"));
        awaitActivity(scenario, activity -> {
            View root = (View) activity.web.getParent();
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
            if (insets == null || insets.isVisible(WindowInsetsCompat.Type.ime()) != imeVisible) return false;
            Insets bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets visible = Insets.max(bars, insets.getInsets(WindowInsetsCompat.Type.ime()));
            Insets cutout = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout());
            return (!requireCutout || !Insets.NONE.equals(cutout)) && bars.top > 0 && (!imeVisible || visible.bottom > bars.bottom)
                    && root.getPaddingLeft() == visible.left && root.getPaddingTop() == visible.top
                    && root.getPaddingRight() == visible.right && root.getPaddingBottom() == visible.bottom
                    && activity.web.getWidth() == root.getWidth() - visible.left - visible.right
                    && activity.web.getHeight() == root.getHeight() - visible.top - visible.bottom;
        }, imeVisible ? "WebView resized for IME" : "stable system bar and display cutout insets");
    }

    private static void awaitActivity(ActivityScenario<MainActivity> scenario, Predicate<MainActivity> condition,
                                      String description) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 12000;
        AtomicReference<Boolean> matched = new AtomicReference<>(false);
        do {
            scenario.onActivity(activity -> matched.set(condition.test(activity)));
            if (matched.get()) return;
            Thread.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Timed out waiting for " + description);
    }

    private static void scrollTo(ActivityScenario<MainActivity> scenario, String selector) throws Exception {
        js(scenario, "document.querySelector('" + selector + "').scrollIntoView({block:'center'});true");
    }

    private static String insideViewport(String selector) {
        return "(()=>{const r=document.querySelector('" + selector + "').getBoundingClientRect();"
                + "return r.width>0 && r.height>0 && r.left>=-1 && r.right<=innerWidth+1"
                + " && r.top>=-1 && r.bottom<=innerHeight+1})()";
    }

    private static void tap(ActivityScenario<MainActivity> scenario, String selector) throws Exception {
        scrollTo(scenario, selector);
        JSONArray position = new JSONArray(js(scenario, "(()=>{const r=document.querySelector('" + selector
                + "').getBoundingClientRect();return [(r.left+r.width/2)*devicePixelRatio,"
                + "(r.top+r.height/2)*devicePixelRatio]})()"));
        int[] offset = new int[2];
        scenario.onActivity(activity -> activity.web.getLocationOnScreen(offset));
        float x = (float) (offset[0] + position.getDouble(0));
        float y = (float) (offset[1] + position.getDouble(1));
        Instrumentation test = InstrumentationRegistry.getInstrumentation();
        long when = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(when, when, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(when, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
        try {
            test.sendPointerSync(down);
            test.sendPointerSync(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private static void hideKeyboard(ActivityScenario<MainActivity> scenario) throws Exception {
        scenario.onActivity(activity -> ViewCompat.getWindowInsetsController(activity.web)
                .hide(WindowInsetsCompat.Type.ime()));
        awaitInsets(scenario, false);
    }

    private static void shell(String command) throws Exception {
        try (var output = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))) {
            while (output.read() != -1) { /* Wait until the command finishes. */ }
        }
    }
}
