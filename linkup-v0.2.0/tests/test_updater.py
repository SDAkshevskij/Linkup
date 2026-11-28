import hashlib
import io
import json
import os
import socket
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / "linux"), str(ROOT / "tools")]
import updater
from package_linux import package


class UpdateTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.work = Path(temporary.name)
        self.base = self.work / "installation"
        baseline = os.environ.get("LINKUP_PUBLISHED_LINUX_BASELINE")
        self.old = Path(baseline) if baseline else self.work / "old.zip"
        self.new = self.work / "new.zip"
        if baseline:
            self.assertEqual(hashlib.sha256(self.old.read_bytes()).hexdigest(),
                             "250da058ec0f7625885bbacb9fb02c77d505b8c3b45a821da80f53762f5e73e4")
        else:
            package(self.old, version_code=3)
        package(self.new, version_code=4)
        initial = self.work / "initial"
        initial.mkdir()
        updater.extract(self.old, initial, 3)
        updater.install_initial(self.base, initial)

    def test_release_asset_and_download_validation(self):
        payload = {
            "tag_name": "v0.2.2", "draft": False, "prerelease": False, "body": "Changes",
            "assets": [{
                "name": "LinkUp-Linux-4.zip",
                "browser_download_url": f"https://github.com/{updater.REPO}/releases/download/v0.2.2/LinkUp-Linux-4.zip",
                "digest": "sha256:" + hashlib.sha256(b"verified").hexdigest(), "size": 8,
            }],
        }
        release = updater.parse_release(payload, 3)
        self.assertEqual(release.version, 4)
        self.assertIsNone(updater.parse_release(payload, 4))
        path = self.work / "download"
        with patch.object(updater, "request", return_value=io.BytesIO(b"verified")):
            updater.download(release, path, lambda: False, lambda _: None)
        self.assertEqual(path.read_bytes(), b"verified")
        path.unlink()
        with patch.object(updater, "request", return_value=io.BytesIO(b"tampered")):
            with self.assertRaisesRegex(ValueError, "сумма"):
                updater.download(release, path, lambda: False, lambda _: None)
        self.assertFalse(path.exists())
        payload["assets"][0]["browser_download_url"] = "https://example.test/package"
        with self.assertRaises(ValueError):
            updater.parse_release(payload, 3)

    def test_archive_rejects_entries_outside_program(self):
        with zipfile.ZipFile(self.work / "unsafe.zip", "w") as archive:
            archive.writestr("linux/../../escape", "fixture")
        with self.assertRaisesRegex(ValueError, "содержимое"):
            updater.extract(self.work / "unsafe.zip", self.work / "stage", 4)
        self.assertFalse((self.work / "escape").exists())

    def test_failed_start_restores_previous_version(self):
        data = self.work / "data"
        data.write_bytes(b"unchanged")
        with patch.object(updater, "wait_for_exit"), patch.object(
                updater, "launch", side_effect=OSError("synthetic startup failure")):
            with self.assertRaisesRegex(OSError, "startup failure"):
                updater.activate(self.base, self.new, 4, 123)
        self.assertEqual(updater.version(self.base / "current"), 3)
        self.assertFalse((self.base / "versions/4").exists())
        self.assertEqual(data.read_bytes(), b"unchanged")

    @unittest.skipUnless(os.environ.get("LINKUP_TEST_WEBENGINE"), "requires Qt WebEngine")
    def test_packaged_upgrade_preserves_real_sqlite_webengine_storage(self):
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            port = str(listener.getsockname()[1])
        env = {**os.environ, "LINKUP_TEST_PORT": port, "HOME": str(self.work), "XDG_DATA_HOME": str(self.work / "data"),
               "XDG_CACHE_HOME": str(self.work / "cache"), "XDG_CONFIG_HOME": str(self.work / "config")}

        def command(target, mode):
            return [sys.executable, str(Path(__file__).resolve()), "probe", str(target), mode]

        old = (self.base / "current").resolve()
        subprocess.run(command(old, "seed"), env=env, check=True, timeout=45)
        children = []

        def launch(target, marker_env):
            child = subprocess.Popen(command(target, "verify"), env={
                **env, "LINKUP_STARTUP_MARKER": marker_env["LINKUP_STARTUP_MARKER"]})
            children.append(child)
            return child

        with patch.object(updater, "wait_for_exit"), patch.object(updater, "launch", side_effect=launch):
            updater.activate(self.base, self.new, 4, 123)
        self.assertEqual(updater.version(self.base / "current"), 4)
        self.assertEqual(children[0].wait(timeout=15), 0)


def probe(target, mode):
    # Start the packaged Window against the production origin in an isolated user profile.
    sys.path.insert(0, str(target / "linux"))
    for previous in ("updater", "agent", "app"):
        sys.modules.pop(previous, None)
    from PyQt5.QtCore import QTimer
    from PyQt5 import sip
    import app as native
    import functools
    import threading

    native.PORT = int(os.environ["LINKUP_TEST_PORT"])
    application = native.QApplication([])
    with native.http.server.ThreadingHTTPServer(
            ("127.0.0.1", native.PORT), functools.partial(native.Handler, directory=str(native.UI))) as server:
        threading.Thread(target=server.serve_forever, daemon=True).start()
        window = native.Window()
        window.show()
        fixture = "linkup-release-test"
        if mode == "seed":
            native.agent.setcfg({"enabled": True, "device_id": fixture, "device_name": fixture,
                                "session": {"fixture": fixture}})
            native.agent.emit("app", 100, 200, fixture)
            script = """
            localStorage.setItem('linkup.config',JSON.stringify({...JSON.parse(localStorage.getItem('linkup.config')),deviceName:'linkup-release-test'}));
            localStorage.setItem('linkup.session',JSON.stringify({user:{id:'linkup-release-test'}}));
            import('./db.js').then(db=>db.saveItem({key:'upgrade',scope:'local',text_content:'linkup-release-test'},new Blob(['local file'])))
                .then(()=>{window.fixtureReady=true});
            """
        else:
            assert native.agent.cfg()["session"]["fixture"] == fixture
            assert native.agent.rows(0, 300)[0]["app"] == fixture
            script = """
            import('./db.js').then(async db=>{
                const item=await db.get('items','upgrade'), file=await db.get('blobs','upgrade');
                window.fixtureReady=item?.text_content==='linkup-release-test' && await file?.blob.text()==='local file'
                  && JSON.parse(localStorage.getItem('linkup.config')).deviceName==='linkup-release-test'
                  && JSON.parse(localStorage.getItem('linkup.session')).user.id==='linkup-release-test';
            });
            """

        deadline = time.monotonic() + 25
        def await_fixture(ready):
            if ready is True:
                if mode == "verify":
                    Path(os.environ["LINKUP_STARTUP_MARKER"]).touch()
                QTimer.singleShot(750, application.quit)
            elif time.monotonic() >= deadline:
                application.exit(1)
            else:
                QTimer.singleShot(100, lambda: window.page.runJavaScript("window.fixtureReady===true", await_fixture))

        def loaded(ok):
            if not ok:
                application.exit(1)
                return
            window.page.runJavaScript(script, lambda _: await_fixture(False))

        window.page.loadFinished.connect(loaded)
        QTimer.singleShot(30000, lambda: application.exit(1))
        result = application.exec_()
        sip.delete(window.view)
        window.close()
        return result


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "probe":
        sys.exit(probe(Path(sys.argv[2]), sys.argv[3]))
    unittest.main()
