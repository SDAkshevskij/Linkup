import os,tempfile,sys,unittest,uuid
from pathlib import Path
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
if __name__=='__main__':unittest.main()
