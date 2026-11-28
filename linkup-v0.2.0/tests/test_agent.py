import os,tempfile,sys,unittest,uuid
from pathlib import Path
from unittest.mock import patch
os.environ['XDG_DATA_HOME']=tempfile.mkdtemp();sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'linux'));import agent
class AgentTest(unittest.TestCase):
 def setUp(self):
  agent.erase();self.device=str(uuid.uuid4());agent.configure(dict(enabled=True,location=False,cloud=False,device_id=self.device,device_name='Mint'))
 def test_local_sessions_and_bounds(self):
  agent.emit('app',1000,4000,'code','Code');agent.emit('unlock',5000,5000);r=agent.rows(2000,4500);self.assertEqual(len(r),1);self.assertEqual(r[0]['device_id'],self.device);self.assertEqual(r[0]['app'],'code');self.assertEqual(r[0]['owner'],'')
 def test_pause(self):
  agent.setcfg({'enabled':False});agent.emit('app',0,10,'code','Code');self.assertEqual(agent.rows(0,100),[])
 def test_cloud_requires_login(self):
  with self.assertRaises(ValueError):agent.configure(dict(enabled=True,cloud=True,user_id=''))
 def test_erase(self):
  agent.emit('app',0,10,'code','Code');agent.erase();self.assertFalse(agent.status()['enabled']);self.assertEqual(agent.rows(0,100),[])
 def test_wayland_and_missing_display_are_distinct_from_idle(self):
  with patch.dict(os.environ, {'XDG_SESSION_TYPE':'wayland','DISPLAY':':77'}):
   self.assertIn('Wayland',agent.sample()[1])
  with patch.dict(os.environ, {'XDG_SESSION_TYPE':'x11','DISPLAY':''}):
   self.assertIn('недоступен',agent.sample()[1])
 def test_x11_failure_does_not_look_like_idle(self):
  with patch.object(agent,'source_error',return_value=''):
   with patch.object(agent,'cmd',return_value='60000'):
    self.assertEqual(agent.sample(),(None,''))
   with patch.object(agent,'cmd',side_effect=['0','not available']):
    self.assertIn('оконный менеджер',agent.sample()[1])
   with patch.object(agent,'cmd',side_effect=OSError('untrusted fixture')):
    self.assertNotIn('untrusted fixture',agent.sample()[1])
 def test_legacy_pause_keeps_existing_history(self):
  agent.emit('app',1000,2000,'code','Code')
  agent.setcfg({'cloud':True,'session':{'test':True},'key':'fixture','owner':'user'})
  agent.pause_legacy_cloud()
  self.assertIsNone(agent.cfg()['session'])
  self.assertFalse(agent.cfg()['cloud'])
  self.assertEqual(len(agent.rows(0,3000)),1)
if __name__=='__main__':unittest.main()
