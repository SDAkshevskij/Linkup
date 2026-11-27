#!/usr/bin/env python3
import os,sys,json,subprocess,threading,functools,http.server,urllib.parse, pathlib
from PyQt5.QtCore import QObject,pyqtSlot,QUrl,QFile,QIODevice,QTimer,Qt
from PyQt5.QtWidgets import QApplication,QMainWindow,QFileDialog,QMessageBox
from PyQt5.QtWebEngineWidgets import QWebEngineView,QWebEnginePage,QWebEngineProfile,QWebEngineSettings
from PyQt5.QtWebChannel import QWebChannel
from PyQt5.QtGui import QDesktopServices,QIcon
import agent
from update_ui import UpdateDialog
import updater
UI=pathlib.Path(__file__).resolve().parent.parent/'ui'; PORT=18763
class Handler(http.server.SimpleHTTPRequestHandler):
 def log_message(self,*a):pass
 def do_GET(self):
  if self.path=='/qwebchannel.js':
   f=QFile(':/qtwebchannel/qwebchannel.js');f.open(QIODevice.ReadOnly);d=bytes(f.readAll());self.send_response(200);self.send_header('Content-Type','application/javascript');self.send_header('Content-Length',str(len(d)));self.end_headers();self.wfile.write(d);return
  super().do_GET()
 def end_headers(self):
  self.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; connect-src https: wss: 'self'; object-src 'none'; frame-src 'none'; base-uri 'none'")
  super().end_headers()
class Bridge(QObject):
 @pyqtSlot(str,str,result=str)
 def call(self,action,raw):
  try:
   a=json.loads(raw);v=None
   if action=='checkUpdate':QTimer.singleShot(0,self.parent().updates.check)
   elif action=='status':v=agent.status()
   elif action=='activity':v={'rows':agent.rows(a['from'],a['to']),'status':agent.status()}
   elif action=='configure':agent.configure(a)
   elif action=='eraseActivity':agent.erase()
   elif action=='disableCloud':agent.setcfg({'cloud':False,'session':None,'owner':''})
   elif action=='permission':v='X11'
   elif action=='locationPermission':raise ValueError('Linux: GPS не реализован; местоположение собирает Android')
   elif action=='copy':QApplication.clipboard().setText(a['text'])
   elif action=='paste':v=QApplication.clipboard().text()
   elif action=='openUrl':
    url=QUrl(a['url']);assert url.scheme()=='https' and url.host()=='www.openstreetmap.org';QDesktopServices.openUrl(url)
   else:raise ValueError('Неизвестная операция')
   return json.dumps({'value':v})
  except Exception as e:return json.dumps({'error':str(e)})
class Page(QWebEnginePage):
 def acceptNavigationRequest(self,url,kind,main):
  if url.scheme()=='http' and url.host()=='127.0.0.1' and url.port()==PORT:return True
  if not main:return False
  if url.scheme()=='https':QDesktopServices.openUrl(url)
  return False
class Window(QMainWindow):
 def __init__(self):
  super().__init__();self.setWindowTitle('LinkUp');self.resize(1120,800);self.setWindowIcon(QIcon(str(UI/'assets/icon-192.png')))
  self.updates=UpdateDialog(self)
  self.profile=QWebEngineProfile('linkup',self);self.profile.setPersistentStoragePath(str(agent.ROOT/'webengine'));self.profile.setCachePath(str(agent.ROOT/'cache'));self.profile.downloadRequested.connect(self.download)
  self.view=QWebEngineView(self);self.page=Page(self.profile,self.view);self.view.setPage(self.page);self.setCentralWidget(self.view);self.channel=QWebChannel(self.page);self.bridge=Bridge(self);self.channel.registerObject('native',self.bridge);self.page.setWebChannel(self.channel);self.view.load(QUrl(f'http://127.0.0.1:{PORT}/index.html'))
 def report_ready(self, marker):
  def checked(ready):
   if ready:
    pathlib.Path(marker).touch()
   else:
    QTimer.singleShot(250, lambda: self.report_ready(marker))
  self.page.runJavaScript("document.readyState === 'complete' && typeof window.linkupImportShare === 'function'", checked)
 def download(self,d):
  path,_=QFileDialog.getSaveFileName(self,'Сохранить файл',d.path());
  if path:d.setPath(path);d.accept()
  else:d.cancel()
if __name__=='__main__':
 app=QApplication(sys.argv)
 try:srv=http.server.ThreadingHTTPServer(('127.0.0.1',PORT),functools.partial(Handler,directory=str(UI)))
 except OSError:QMessageBox.critical(None,'LinkUp','LinkUp уже запущен или порт 18763 занят.');sys.exit(1)
 threading.Thread(target=srv.serve_forever,daemon=True).start()
 subprocess.Popen([sys.executable,str(pathlib.Path(__file__).with_name('agent.py'))],start_new_session=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 w=Window()
 w.show()
 if marker := os.environ.pop('LINKUP_STARTUP_MARKER', ''):
  QTimer.singleShot(0, lambda: w.report_ready(marker))
 error = updater.BASE / 'update-error'
 if error.is_file() and (updater.BASE / 'current').resolve() == updater.SOURCE:
  detail = error.read_text()
  error.unlink()
  QTimer.singleShot(0, lambda: QMessageBox.warning(w, 'Обновление LinkUp', detail))
 sys.exit(app.exec_())
