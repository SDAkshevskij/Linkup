"""Download and switch user-local LinkUp releases without touching its data directory."""
import argparse
from contextlib import contextmanager
from dataclasses import dataclass
import fcntl
import hashlib
import hmac
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

REPO = "SDAkshevskij/Linkup"
LATEST = f"https://api.github.com/repos/{REPO}/releases/latest"
BASE = Path.home() / ".local/opt/linkup"
SOURCE = Path(__file__).resolve().parents[1]
LIMIT = 100 * 1024 * 1024
ALLOWED_HOSTS = {"api.github.com", "github.com", "release-assets.githubusercontent.com",
                 "objects.githubusercontent.com"}


@dataclass(frozen=True)
class Release:
    tag: str
    notes: str
    version: int
    url: str
    digest: str
    size: int


def version(path):
    properties = dict(line.split("=", 1) for line in (path / "version.properties").read_text().splitlines() if line)
    return int(properties["versionCode"])


def check_url(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or parsed.netloc not in ALLOWED_HOSTS:
        raise ValueError("Недопустимый адрес загрузки")


class Redirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        check_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def request(url):
    check_url(url)
    opener = urllib.request.build_opener(Redirects())
    return opener.open(urllib.request.Request(url, headers={"User-Agent": "LinkUp-Updater"}), timeout=12)


def read_chunks(response, limit, cancelled, seconds):
    deadline = time.monotonic() + seconds
    total = 0
    while not cancelled():
        block = response.read1(65536)
        if not block:
            return
        total += len(block)
        if total > limit or time.monotonic() > deadline:
            raise ValueError("Превышен лимит загрузки")
        yield block
    raise InterruptedError("Загрузка отменена")


def parse_release(payload, installed):
    if payload["draft"] or payload["prerelease"]:
        return None
    tag = payload["tag_name"]
    if not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag):
        raise ValueError("Неизвестный формат релиза")
    for asset in payload["assets"]:
        name = asset["name"]
        match = re.fullmatch(r"LinkUp-Linux-([1-9][0-9]{0,8})\.zip", name)
        if not match:
            continue
        code = int(match[1])
        if code <= installed:
            return None
        url = f"https://github.com/{REPO}/releases/download/{tag}/{name}"
        digest, size = asset.get("digest"), asset["size"]
        if asset["browser_download_url"] != url or not isinstance(digest, str) or not re.fullmatch(
                r"sha256:[0-9a-f]{64}", digest) or not 0 < size <= LIMIT:
            raise ValueError("Некорректный файл релиза")
        return Release(tag, (payload.get("body") or "")[:4000], code, url, digest[7:], size)
    raise ValueError("В релизе нет пакета LinkUp для Linux")


def latest(installed):
    try:
        with request(LATEST) as response:
            payload = json.loads(b"".join(read_chunks(response, 2 * 1024 * 1024, lambda: False, 20)))
        return parse_release(payload, installed)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise ValueError(f"GitHub вернул HTTP {error.code}") from error


def download(release, destination, cancelled, progress):
    digest = hashlib.sha256()
    size = 0
    created = False
    try:
        with destination.open("xb") as output:
            created = True
            with request(release.url) as response:
                for block in read_chunks(response, release.size, cancelled, 180):
                    digest.update(block)
                    output.write(block)
                    size += len(block)
                    progress(100 * size // release.size)
        if cancelled():
            raise InterruptedError("Загрузка отменена")
        if size != release.size or not hmac.compare_digest(digest.hexdigest(), release.digest):
            raise ValueError("Контрольная сумма пакета не совпадает")
    except Exception:
        if created:
            destination.unlink(missing_ok=True)
        raise


def extract(archive, target, expected):
    with zipfile.ZipFile(archive) as bundle:
        total = 0
        names = set()
        for entry in bundle.infolist():
            path = PurePosixPath(entry.filename)
            filetype = stat.S_IFMT(entry.external_attr >> 16)
            total += entry.file_size
            if (entry.is_dir() or path.is_absolute() or ".." in path.parts or "\\" in entry.filename
                    or entry.filename != path.as_posix() or filetype not in (0, stat.S_IFREG)
                    or path in names or total > LIMIT
                    or (path.parts[0] not in {"linux", "ui"} and str(path) != "version.properties")):
                raise ValueError("Недопустимое содержимое пакета")
            names.add(path)
            destination = target.joinpath(*path.parts)
            destination.parent.mkdir(parents=True, exist_ok=True)
            with bundle.open(entry) as source, destination.open("xb") as output:
                shutil.copyfileobj(source, output)
    if version(target) != expected:
        raise ValueError("Версия пакета не совпадает с релизом")
    for name in ("linux/app.py", "linux/agent.py", "linux/updater.py", "ui/index.html", "ui/app.js"):
        if not (target / name).is_file():
            raise ValueError("Неполный пакет LinkUp")


@contextmanager
def install_lock(base):
    base.mkdir(mode=0o700, parents=True, exist_ok=True)
    with (base / "update.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield


def switch(base, target):
    temporary = base / ".next"
    temporary.unlink(missing_ok=True)
    temporary.symlink_to(target)
    temporary.replace(base / "current")


def install_initial(base, source):
    with install_lock(base):
        current = base / "current"
        code = version(source)
        if current.is_symlink() and version(current) >= code:
            return
        with tempfile.TemporaryDirectory(prefix="install-", dir=base) as tmp:
            stage = Path(tmp) / "release"
            stage.mkdir()
            for folder in ("linux", "ui"):
                shutil.copytree(source / folder, stage / folder, ignore=shutil.ignore_patterns("__pycache__"))
            shutil.copyfile(source / "version.properties", stage / "version.properties")
            destination = base / "versions" / str(code)
            destination.parent.mkdir(exist_ok=True)
            if destination.exists():
                shutil.rmtree(destination)
            stage.rename(destination)
            switch(base, destination)


def wait_for_exit(pid, seconds):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            return
        time.sleep(0.1)
    raise RuntimeError("LinkUp не завершился")


def launch(target, env=None):
    return subprocess.Popen([sys.executable, str(target / "linux/app.py")], env=env, start_new_session=True)


def activate(base, archive, expected, previous_pid, timeout=35, digest=None):
    with install_lock(base):
        if digest is not None:
            if archive.stat().st_size > LIMIT:
                raise ValueError("Пакет превышает допустимый размер")
            with archive.open("rb") as input_file:
                actual = hashlib.sha256()
                for block in iter(lambda: input_file.read(65536), b""):
                    actual.update(block)
                if not hmac.compare_digest(actual.hexdigest(), digest):
                    raise ValueError("Контрольная сумма пакета не совпадает")
        current = base / "current"
        old = current.resolve(strict=True)
        if expected <= version(old):
            raise ValueError("Обновление уже установлено")
        destination = base / "versions" / str(expected)
        with tempfile.TemporaryDirectory(prefix="stage-", dir=base) as tmp:
            stage = Path(tmp) / "release"
            stage.mkdir()
            extract(archive, stage, expected)
            wait_for_exit(previous_pid, 15)
            if destination.exists():
                shutil.rmtree(destination)
            stage.rename(destination)
            marker = Path(tmp) / "ready"
            switch(base, destination)
            child = None
            try:
                child = launch(destination, {**os.environ, "LINKUP_STARTUP_MARKER": str(marker)})
                deadline = time.monotonic() + timeout
                while time.monotonic() < deadline:
                    if child.poll() is not None:
                        break
                    if marker.exists():
                        return
                    time.sleep(0.2)
                raise RuntimeError("Новая версия LinkUp не запустилась")
            except Exception:
                if child is not None and child.poll() is None:
                    child.kill()
                    child.wait()
                switch(base, old)
                shutil.rmtree(destination)
                raise


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("install")
    apply = commands.add_parser("apply")
    apply.add_argument("archive", type=Path)
    apply.add_argument("version", type=int)
    apply.add_argument("pid", type=int)
    apply.add_argument("digest")
    args = parser.parse_args()
    if args.command == "install":
        install_initial(BASE, SOURCE)
        return
    directory = args.archive.parent
    if (directory.parent != BASE or not directory.name.startswith("download-") or directory.is_symlink()
            or args.archive.name != "linkup.zip" or args.archive.is_symlink()
            or not re.fullmatch(r"[0-9a-f]{64}", args.digest)):
        parser.error("Недопустимый подготовленный пакет обновления")
    try:
        activate(BASE, args.archive, args.version, args.pid, digest=args.digest)
    except Exception as error:
        # Store only a local, transient diagnostic for the restarted UI.
        (BASE / "update-error").write_text(str(error)[:500])
        launch((BASE / "current").resolve(strict=True))
        return 1
    finally:
        args.archive.unlink(missing_ok=True)
        args.archive.parent.rmdir()


if __name__ == "__main__":
    sys.exit(main())
