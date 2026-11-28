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


def probe():
    import functools
    import threading
    sys.path.insert(0,str(ROOT/'linux'))
    from PyQt5 import sip
    from PyQt5.QtCore import QTimer
    import app as native
    agent=native.agent

    def wait_for(test,seconds=20,pump=lambda:None):
        deadline=time.monotonic()+seconds
        while time.monotonic()<deadline:
            pump()
            try:
                if value:=test():return value
            except subprocess.CalledProcessError:
                # The WM creates its client list after advertising EWMH support.
                pass
            time.sleep(.1)
        raise AssertionError(f'integration wait timed out: {agent.diagnostics()}')

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
        wait_for(lambda:'Openbox' in agent.cmd('wmctrl','-m'))
        spawn(sys.executable,__file__,'window','LinkUpFixtureOne','LinkUp Fixture One')
        spawn(sys.executable,__file__,'window','LinkUpFixtureTwo','LinkUp Fixture Two')
        wait_for(lambda:'LinkUp Fixture One' in agent.cmd('wmctrl','-l') and
                         'LinkUp Fixture Two' in agent.cmd('wmctrl','-l'))
        agent.configure({'enabled':True,'device_name':'Fixture X11'})
        supervisor.ensure()
        def activate(title):
            subprocess.run(['wmctrl','-a',title],check=True)
        activate('LinkUp Fixture One')
        wait_for(lambda:agent.diagnostics().get('last_app','').lower()=='linkupfixtureone')
        activate('LinkUp Fixture Two')
        def first_row():
            return next((r for r in agent.rows(0,int(time.time()*1000)+1)
                         if r['app']=='linkupfixtureone' and r['end_ms']>r['start_ms']),None)
        wait_for(first_row)
        old_pid=agent.diagnostics()['pid']
        supervisor.child.terminate()
        supervisor.child.wait(timeout=5)
        supervisor.ensure()
        wait_for(lambda:agent.diagnostics().get('pid')!=old_pid and agent.diagnostics().get('last_app','').lower()=='linkupfixturetwo')
        assert first_row(), 'history survives collector restart'

        with native.http.server.ThreadingHTTPServer(('127.0.0.1',0),
                functools.partial(native.Handler,directory=str(native.UI))) as server:
            native.PORT=server.server_port
            thread=threading.Thread(target=server.serve_forever,daemon=True)
            thread.start()
            window=native.Window()
            window.show()
            result={}
            def poll():
                window.page.runJavaScript("""(() => {
                    if (typeof window.linkupImportShare !== 'function') return false;
                    if (document.querySelector('#activityView').hidden)
                        document.querySelector('[data-page=activity]').click();
                    return [...document.querySelectorAll('#actTimeline .time-dot')]
                        .some(item => item.title.toLowerCase().includes('linkupfixtureone'));
                })()""",lambda value:result.update(rendered=value))
            timer=QTimer(window)
            timer.timeout.connect(poll)
            timer.start(300)
            wait_for(lambda:result.get('rendered'),pump=application.processEvents)
            timer.stop()
            if screenshot:=os.environ.get('LINKUP_X11_SCREENSHOT'):
                application.processEvents()
                assert window.grab().save(screenshot)
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
        probe()
    elif sys.argv[1:2]==['window']:
        from PyQt5.QtWidgets import QApplication, QWidget
        name,title=sys.argv[2:4]
        application=QApplication([name,'-name',name])
        application.setApplicationName(name)
        client=QWidget()
        client.setWindowTitle(title)
        client.resize(240,140)
        client.show()
        sys.exit(application.exec_())
