package io.linkup.app;

import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class UpgradeTest {
    private static final String FIXTURE = "linkup-ci-upgrade";
    private static final String BUFFER = "window.upgradeProbe";
    private static final String STATE = "JSON.parse(localStorage.getItem('linkup.config')).deviceName";
    private static final String SESSION = "JSON.parse(localStorage.getItem('linkup.session')).user.id";

    @Test public void retainsAppDataAcrossSignedUpgrade() throws Exception {
        String phase = InstrumentationRegistry.getArguments().getString("upgradePhase");
        Assume.assumeTrue("seed".equals(phase) || "verify".equals(phase));
        boolean seed = "seed".equals(phase);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class))) {
            SmokeTest ui = new SmokeTest();
            ui.waitTrue(scenario, "document.readyState==='complete' && typeof window.linkupImportShare==='function'");
            AtomicReference<Exception> failure = new AtomicReference<>();
            scenario.onActivity(activity -> {
                try (Collector collector = new Collector(activity)) {
                    PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(),
                            PackageManager.GET_SIGNING_CERTIFICATES);
                    assertEquals(seed ? 2 : 3, info.getLongVersionCode());
                    if (seed) {
                        assertTrue(collector.p.edit().putString("upgrade_fixture", FIXTURE).commit());
                        ContentValues row = new ContentValues();
                        row.put("id", FIXTURE);
                        row.put("owner", "");
                        row.put("start", 100);
                        row.put("end", 200);
                        row.put("payload", "{\"id\":\"" + FIXTURE + "\"}");
                        collector.getWritableDatabase().insertOrThrow("events", null, row);
                    } else {
                        assertEquals(FIXTURE, collector.p.getString("upgrade_fixture", ""));
                        assertEquals(FIXTURE, collector.rows(0, 300).getJSONObject(0).getString("id"));
                    }
                } catch (Exception error) {
                    failure.set(error);
                }
            });
            if (failure.get() != null) throw failure.get();
            if (seed) {
                ui.js(scenario, "localStorage.setItem('linkup.config',JSON.stringify({...JSON.parse(localStorage.getItem('linkup.config')),deviceName:'"
                        + FIXTURE + "'}));localStorage.setItem('linkup.session',JSON.stringify({user:{id:'" + FIXTURE + "'}}));"
                        + "import('/ui/db.js').then(db=>db.saveItem({key:'" + FIXTURE
                        + "',scope:'local',id:'" + FIXTURE + "',kind:'text',text_content:'" + FIXTURE
                        + "'},new Blob(['" + FIXTURE + "']))).then(()=>{" + BUFFER + "=true});");
            } else {
                ui.waitTrue(scenario, STATE + "==='" + FIXTURE + "' && " + SESSION + "==='" + FIXTURE + "'");
                ui.js(scenario, "import('/ui/db.js').then(async db=>{const item=await db.get('items','" + FIXTURE
                        + "');const file=await db.get('blobs','" + FIXTURE + "');" + BUFFER
                        + "=item?.text_content==='" + FIXTURE + "' && await file?.blob.text()==='" + FIXTURE + "'});");
            }
            ui.waitTrue(scenario, BUFFER + "===true");
        }
    }
}
