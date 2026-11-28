package io.linkup.app;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static io.linkup.app.WebViewProbe.js;
import static io.linkup.app.WebViewProbe.waitTrue;

@RunWith(AndroidJUnit4.class)
public class CloudContainmentTest {
    @Test public void connectingDoesNotOptInOrUploadLocalDraft() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitTrue(scenario,MobileUiTest.READY);
            try{
                js(scenario,"""
                    window.savedConfig=localStorage.getItem('linkup.config');
                    window.savedSession=localStorage.getItem('linkup.session');
                    window.outgoing=[];
                    window.fetch=async(url,options={})=>{
                        window.outgoing.push([String(url),options.method||'GET',options.body||'']);
                        if(url.includes('/auth/v1/token'))
                            return new Response(JSON.stringify({user:{id:'fixture-account'},
                                access_token:'fixture-only',refresh_token:'fixture-only'}),{status:200});
                        return new Response('[]',{status:200});
                    };
                    document.querySelector('#mobileSettings').click();
                    document.querySelector('#sbUrl').value='https://fixture.invalid';
                    document.querySelector('#sbKey').value='publishable-fixture';
                    document.querySelector('#email').value='ci@example.test';
                    document.querySelector('#password').value='fixture-only';
                    document.querySelector('#saveCloud').click();true
                    """);
                waitTrue(scenario,"document.querySelector('#supabaseStatus').textContent==='Supabase подключён'");
                waitTrue(scenario,"document.querySelector('#legacyTransfer').getAttribute('aria-pressed')==='false'");
                js(scenario,"document.querySelector('#legacyTransfer').click();true");
                waitTrue(scenario,"document.querySelector('#legacyConsent').open && window.outgoing.length===2");
                js(scenario,"document.querySelector('#legacyConsent').close();document.querySelector('#settingsDialog').close();"
                        + "document.querySelector('#message').value='Synthetic local canary';"
                        + "document.querySelector('#send').click();true");
                waitTrue(scenario,"document.querySelector('#history').textContent.includes('Synthetic local canary')");
                waitTrue(scenario,"window.outgoing.length===2 && !JSON.stringify(window.outgoing).includes('Synthetic local canary')");
                js(scenario,"document.querySelector('[data-page=activity]').click();true");
                waitTrue(scenario,"document.querySelector('#activityStatus').textContent.includes('Облако на паузе') && window.outgoing.length===2");
            }finally{
                js(scenario,"if(window.savedConfig!==undefined){localStorage.setItem('linkup.config',window.savedConfig);"
                        + "if(window.savedSession===null)localStorage.removeItem('linkup.session');"
                        + "else localStorage.setItem('linkup.session',window.savedSession);}true");
            }
        }
    }

    @Test public void androidNetworkProbeReportsRealDnsFailureWithoutCredentials() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitTrue(scenario,MobileUiTest.READY);
            js(scenario,"document.querySelector('#mobileSettings').click();"
                    + "document.querySelector('#sbUrl').value='https://linkup-network-fixture.invalid';"
                    + "document.querySelector('#cloudInspector').open=true;"
                    + "document.querySelector('#probeSupabase').click();true");
            waitTrue(scenario,"(()=>{const d=document.querySelector('#cloudProbeStatus');"
                    + "return d.textContent.includes('DNS') && !d.textContent.includes('.invalid')})()");
        }
    }
}
