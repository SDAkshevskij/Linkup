"""Qt dialogs and worker for the Linux updater."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading

from PyQt5.QtCore import QObject, Qt, pyqtSignal
from PyQt5.QtWidgets import QApplication, QMessageBox, QProgressDialog

import updater


class UpdateDialog(QObject):
    checked = pyqtSignal(object)
    progressed = pyqtSignal(int)
    downloaded = pyqtSignal(object)
    failed = pyqtSignal(str)

    def __init__(self, parent):
        super().__init__(parent)
        self.window = parent
        self.dialog = None
        self.stop = threading.Event()
        self.checked.connect(self.confirm)
        self.progressed.connect(self.progress)
        self.downloaded.connect(self.restart)
        self.failed.connect(self.error)

    def begin(self, text, maximum):
        self.stop.clear()
        self.dialog = QProgressDialog(text, "Отмена", 0, maximum, self.window)
        self.dialog.setWindowTitle("Обновления LinkUp")
        self.dialog.setWindowModality(Qt.WindowModal)
        self.dialog.setAutoClose(False)
        self.dialog.setAutoReset(False)
        self.dialog.canceled.connect(self.stop.set)
        self.dialog.show()

    def finish(self):
        self.dialog.hide()
        self.dialog.deleteLater()
        self.dialog = None

    def progress(self, value):
        if self.dialog is not None and not self.stop.is_set():
            self.dialog.setValue(value)

    def error(self, message):
        self.finish()
        if not self.stop.is_set():
            QMessageBox.warning(self.window, "Обновления LinkUp", message)

    def check(self):
        if self.dialog is not None:
            return
        if (updater.BASE / "current").resolve() != updater.SOURCE:
            QMessageBox.information(self.window, "Обновления LinkUp",
                                    "Для обновлений установите релиз с помощью linux/install.sh.")
            return
        self.begin("Проверяем обновления…", 0)

        def check():
            try:
                self.checked.emit(updater.latest(updater.version(updater.SOURCE)))
            except Exception as error:
                self.failed.emit(str(error))

        threading.Thread(target=check, daemon=True).start()

    def confirm(self, release):
        self.finish()
        if self.stop.is_set():
            return
        if release is None:
            QMessageBox.information(self.window, "Обновления LinkUp", "Обновления не найдены")
            return
        dialog = QMessageBox(QMessageBox.Question, f"LinkUp {release.tag}",
                             release.notes or "Доступно обновление", parent=self.window)
        dialog.setTextFormat(Qt.PlainText)
        dialog.setStandardButtons(QMessageBox.Yes | QMessageBox.Cancel)
        dialog.button(QMessageBox.Yes).setText("Скачать")
        if dialog.exec_() != QMessageBox.Yes:
            return
        self.begin("Скачиваем обновление…", 100)

        def download():
            directory = None
            try:
                directory = Path(tempfile.mkdtemp(prefix="download-", dir=updater.BASE))
                path = directory / "linkup.zip"
                updater.download(release, path, self.stop.is_set, self.progressed.emit)
                self.downloaded.emit((path, release.version))
            except Exception as error:
                if directory is not None:
                    directory.rmdir()
                self.failed.emit(str(error))

        threading.Thread(target=download, daemon=True).start()

    def restart(self, result):
        self.finish()
        path, version = result
        if self.stop.is_set() or QMessageBox.question(
                self.window, "Обновление готово", "Установить обновление и перезапустить LinkUp?"
        ) != QMessageBox.Yes:
            path.unlink()
            path.parent.rmdir()
            return
        try:
            subprocess.Popen([sys.executable, updater.__file__, "apply", str(path),
                              str(version), str(os.getpid())], start_new_session=True)
        except OSError as error:
            path.unlink()
            path.parent.rmdir()
            QMessageBox.warning(self.window, "Обновления LinkUp", str(error))
            return
        QApplication.quit()
