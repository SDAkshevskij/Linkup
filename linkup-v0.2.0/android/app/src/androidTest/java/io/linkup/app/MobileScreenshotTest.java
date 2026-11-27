package io.linkup.app;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static io.linkup.app.WebViewProbe.js;
import static io.linkup.app.WebViewProbe.waitTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class MobileScreenshotTest {
    @Test public void capturesSettingsInBothOrientations() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            for (boolean landscape : new boolean[]{false,true}) {
                scenario.onActivity(activity -> activity.setRequestedOrientation(landscape
                        ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                waitTrue(scenario, (landscape ? "innerWidth > innerHeight" : "innerWidth < innerHeight")
                        + " && typeof window.linkupImportShare==='function'");
                js(scenario, "(()=>{const d=document.querySelector('#settingsDialog');"
                        + "if(!d.open)document.querySelector('#mobileSettings').click();d.scrollTop=0;return true})()");
                waitTrue(scenario, "document.querySelector('#settingsDialog').open");
                capture(landscape ? "landscape-settings" : "portrait-settings");
            }
        }
    }

    static void capture(String name) throws Exception {
        Bitmap shot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("screen capture", shot);
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("mobile-screenshots");
        assertNotNull(directory);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(shot.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            shot.recycle();
        }
    }
}
