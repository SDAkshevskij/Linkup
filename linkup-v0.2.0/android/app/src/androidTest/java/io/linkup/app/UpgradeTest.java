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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static io.linkup.app.WebViewProbe.js;
import static io.linkup.app.WebViewProbe.waitTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class UpgradeTest {
    private static final String FIXTURE = "linkup-ci-upgrade";
    private static final String BUFFER = "window.upgradeProbe";
    private static final String STATE = "JSON.parse(localStorage.getItem('linkup.config')).deviceName";
    private static final String SESSION = "JSON.parse(localStorage.getItem('linkup.session')).user.id";

    @Test public void builtInUpdaterAcceptsOnlyTheMatchingSignedCandidate() throws Exception {
        Assume.assumeTrue("validate".equals(InstrumentationRegistry.getArguments().getString("upgradePhase")));
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var context=instrumentation.getTargetContext();
        var manager=context.getPackageManager();
        var current=manager.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        assertEquals(3,current.getLongVersionCode());
        for(String asset:new String[]{"candidate.apk","wrong.apk"}){
            File file=File.createTempFile("update-check-", ".apk", context.getCacheDir());
            try(var input=instrumentation.getContext().getAssets().open(asset)){
                Files.copy(input,file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                var info=manager.getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNING_CERTIFICATES);
                if("wrong.apk".equals(asset)){
                    try{
                        ReleaseUpdater.verifyPackage(current,info,4);
                        org.junit.Assert.fail("Updater accepted a different signer");
                    }catch(IOException expected){
                        assertTrue(expected.getMessage().contains("Подпись"));
                    }
                }else{
                    ReleaseUpdater.verifyPackage(current,info,4);
                }
            }finally{Files.deleteIfExists(file.toPath());}
        }
    }

    @Test public void retainsAppDataAcrossSignedUpgrade() throws Exception {
        String phase = InstrumentationRegistry.getArguments().getString("upgradePhase");
        Assume.assumeTrue("seed".equals(phase) || "verify".equals(phase));
        boolean seed = "seed".equals(phase);
        long expectedVersion = Long.parseLong(InstrumentationRegistry.getArguments().getString("expectedVersion"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class))) {
            waitTrue(scenario, "document.readyState==='complete' && typeof window.linkupImportShare==='function'");
            AtomicReference<Exception> failure = new AtomicReference<>();
            scenario.onActivity(activity -> {
                try (Collector collector = new Collector(activity)) {
                    PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(),
                            PackageManager.GET_SIGNING_CERTIFICATES);
                    assertEquals(expectedVersion, info.getLongVersionCode());
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
            if (!seed) {
                waitTrue(scenario, STATE + "==='" + FIXTURE + "' && " + SESSION + "==='" + FIXTURE + "'");
            }
            js(scenario, "(seed => {" + """
                const fixture = 'FIXTURE';
                window.upgradeProbe = 'pending';
                if (seed) {
                    localStorage.setItem('linkup.config', JSON.stringify({
                        ...JSON.parse(localStorage.getItem('linkup.config')), deviceName:fixture
                    }));
                    localStorage.setItem('linkup.session', JSON.stringify({user:{id:fixture}}));
                }
                const request = indexedDB.open('linkup-v1', 1);
                request.onerror = () => { window.upgradeProbe = String(request.error); };
                request.onsuccess = () => {
                    const db = request.result;
                    try {
                        const transaction = db.transaction(['items','blobs'], seed ? 'readwrite' : 'readonly');
                        transaction.onerror = () => { window.upgradeProbe = String(transaction.error); };
                        const items = transaction.objectStore('items'), blobs = transaction.objectStore('blobs');
                        if (seed) {
                            items.put({key:fixture,scope:'local',id:fixture,kind:'text',text_content:fixture});
                            blobs.put({key:fixture,blob:new Blob([fixture])});
                            transaction.oncomplete = () => { db.close(); window.upgradeProbe = true; };
                        } else {
                            const item = items.get(fixture), file = blobs.get(fixture);
                            transaction.oncomplete = async () => {
                                db.close();
                                window.upgradeProbe = item.result?.text_content === fixture
                                    && await file.result?.blob.text() === fixture;
                            };
                        }
                    } catch (error) {
                        window.upgradeProbe = String(error);
                    }
                };
                """.replace("FIXTURE", FIXTURE) + "})(" + seed + ")");
            waitTrue(scenario, BUFFER + "!=='pending'");
            assertEquals("IndexedDB " + phase, "true", js(scenario, BUFFER));
            if (seed) {
                // Chromium batches LocalStorage commits for five seconds. The test runner
                // kills the process on exit, so keep WebView alive until that commit can run.
                Thread.sleep(6500);
            }
        }
    }
}
