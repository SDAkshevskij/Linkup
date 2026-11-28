"""Run the normal agent and Qt Activity page against actual X11 windows in Xvfb."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

ROOT=Path(__file__).resolve().parents[1]


class ActivityX11Test(unittest.TestCase):
    @unittest.skipUnless(os.environ.get('LINKUP_TEST_X11'), 'requires isolated Xvfb and an X11 window manager')
    def test_real_window_switch_through_agent_and_qt_bridge(self):
        with tempfile.TemporaryDirectory() as data:
            env={**os.environ, 'XDG_DATA_HOME':data,'XDG_SESSION_TYPE':'x11','QT_QPA_PLATFORM':'xcb'}
            subprocess.run([sys.executable,__file__,'probe'],env=env,check=True,timeout=80)


def probe(window_dir):
    import functools
    import threading
    sys.path.insert(0,str(ROOT/'linux'))
    from PyQt5 import sip
    from PyQt5.QtCore import QTimer
    import app as native
    agent=native.agent

    def wait_for(test,description,seconds=20):
        deadline=time.monotonic()+seconds
        while time.monotonic()<deadline:
            try:
                if value:=test():return value
            except subprocess.CalledProcessError:
                # The WM creates its client list after advertising EWMH support.
                pass
            time.sleep(.1)
        raise AssertionError(f'Timed out waiting for {description}: {agent.diagnostics()}')

    processes=[]
    supervisor=agent.Supervisor()
    def spawn(*args):
        child=subprocess.Popen(args)
        processes.append(child)
        return child

    application=native.QApplication([])
    window=None
    try:
        spawn('openbox')
        wait_for(lambda:'Openbox' in agent.cmd('wmctrl','-m'),'Openbox')

        def open_client(name):
            marker=window_dir/name
            child=spawn(sys.executable,__file__,'window',name,str(marker))
            def managed_window():
                if child.poll() is not None:
                    raise AssertionError(f'{name} exited with {child.returncode}')
                if not marker.exists() or not (identifier:=marker.read_text()):
                    return None
                registered=agent.cmd('xprop','-root','_NET_CLIENT_LIST').split('#')[-1]
                return identifier if int(identifier,16) in {
                    int(token,16) for token in registered.replace(',',' ').split()
                    if token.startswith('0x')} else None
            return wait_for(managed_window,f'an exposed and managed window for {name}')

        one=open_client('LinkUpFixtureOne')
        two=open_client('LinkUpFixtureTwo')
        agent.configure({'enabled':True,'device_name':'Fixture X11'})
        supervisor.ensure()
        subprocess.run(['wmctrl','-ia',one],check=True)
        wait_for(lambda:agent.diagnostics().get('last_app','').lower()=='linkupfixtureone','collection of the first window')
        subprocess.run(['wmctrl','-ia',two],check=True)
        def first_row():
            return next((r for r in agent.rows(0,int(time.time()*1000)+1)
                         if r['app']=='linkupfixtureone' and r['end_ms']>r['start_ms']),None)
        wait_for(first_row,'the first window session in SQLite')
        old_pid=agent.diagnostics()['pid']
        supervisor.child.terminate()
        supervisor.child.wait(timeout=5)
        supervisor.ensure()
        wait_for(lambda:agent.diagnostics().get('pid')!=old_pid and agent.diagnostics().get('last_app','').lower()=='linkupfixturetwo',
                 'a restarted collector observing the second window')
        assert first_row(), 'history survives collector restart'

        with native.http.server.ThreadingHTTPServer(('127.0.0.1',0),
                functools.partial(native.Handler,directory=str(native.UI))) as server:
            native.PORT=server.server_port
            thread=threading.Thread(target=server.serve_forever,daemon=True)
            thread.start()
            window=native.Window()
            window.show()
            result={}
            timer=QTimer(window)
            def capture_and_quit():
                if screenshot:=os.environ.get('LINKUP_X11_SCREENSHOT'):
                    result['screenshot']=window.grab().save(screenshot)
                    def capture_settings(opened):
                        result['settings_open']=opened
                        def completed():
                            target=Path(screenshot).with_name("linkup-settings.png")
                            result['settings_screenshot']=window.grab().save(str(target))
                            application.quit()
                        QTimer.singleShot(250,completed)
                    window.page.runJavaScript("""(() => {
                        document.querySelector('#settings').click();
                        return document.querySelector('#settingsDialog').open &&
                            document.querySelector('#settingsDialog').scrollWidth<=innerWidth;
                    })()""",capture_settings)
                else:
                    application.quit()

            def measured(value):
                if isinstance(value,dict):
                    result.update(value)
                    if value.get('stage')=='complete':
                        timer.stop()
                        QTimer.singleShot(200,capture_and_quit)
            def poll():
                window.page.runJavaScript("""(() => {
                    const ready=typeof window.linkupActivityResume==='function';
                    const view=document.querySelector('#activityView');
                    if(ready&&view.hidden)document.querySelector('[data-page=activity]').click();
                    const marks=[...document.querySelectorAll('#actTimeline .time-dot')].map(item=>item.title);
                    const recorded=marks.some(title=>title.toLowerCase().includes('linkupfixtureone'));
                    let stage=window.fixtureActivityStage||'initial';
                    if(stage==='initial'&&recorded){
                        document.querySelector('#actPrev').click();
                        stage='archive';
                    }else if(stage==='archive'&&document.querySelector('#actTimeline').textContent.includes('Нет записей за день')){
                        document.querySelector('#actToday').click();
                        stage='return';
                    }else if(stage==='return'&&recorded&&document.querySelector('#actNext').disabled){
                        stage='complete';
                    }
                    window.fixtureActivityStage=stage;
                    return {ready, qt:typeof window.qt, channel:typeof QWebChannel,
                        status:document.querySelector('#activityStatus')?.textContent,
                        shown:view&&!view.hidden, marks, stage};
                })()""",measured)
            timer.timeout.connect(poll)
            timer.start(350)
            QTimer.singleShot(20000,application.quit)
            application.exec_()
            assert result.get('stage')=='complete', f'Qt timeline did not show the recorded row after date navigation: {result}'
            assert result.get('screenshot',True), 'screenshot could not be saved'
            assert result.get('settings_open',True) and result.get('settings_screenshot',True), 'Qt settings screenshot failed'
            sip.delete(window.view)
            window.close()
            window=None
            server.shutdown()
    finally:
        if window:
            sip.delete(window.view)
            window.close()
        if supervisor.child:
            processes.append(supervisor.child)
        for child in reversed(processes):
            if child.poll() is None:
                child.terminate()
                child.wait(timeout=5)


if __name__=='__main__':
    if sys.argv[1:]==['probe']:
        with tempfile.TemporaryDirectory() as window_dir:
            probe(Path(window_dir))
    elif sys.argv[1:2]==['window']:
        from PyQt5.QtTest import QTest
        from PyQt5.QtWidgets import QApplication, QWidget
        name,marker=sys.argv[2:4]
        application=QApplication([name,'-name',name])
        application.setApplicationName(name)
        client=QWidget()
        client.setWindowTitle(name)
        client.resize(240,140)
        client.show()
        if not QTest.qWaitForWindowExposed(client,5000):
            sys.exit('X11 fixture window was not exposed')
        Path(marker).write_text(hex(int(client.winId())))
        sys.exit(application.exec_())
