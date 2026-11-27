const DB='linkup-v1'; let promise;
export function database(){return promise??=new Promise((resolve,reject)=>{const r=indexedDB.open(DB,1);r.onupgradeneeded=()=>{for(const name of ['items','blobs','inbox'])if(!r.result.objectStoreNames.contains(name))r.result.createObjectStore(name,{keyPath:'key'});};r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});}
async function tx(stores,mode,fn){const db=await database();return new Promise((resolve,reject)=>{const t=db.transaction(stores,mode);let value;try{value=fn(t);}catch(e){t.abort();reject(e);return}t.oncomplete=()=>resolve(value?.result);t.onerror=()=>reject(t.error);t.onabort=()=>reject(t.error||Error('Хранилище недоступно'));});}
export const all=store=>tx([store],'readonly',t=>t.objectStore(store).getAll());
export const get=(store,key)=>tx([store],'readonly',t=>t.objectStore(store).get(key));
export const put=(store,value)=>tx([store],'readwrite',t=>t.objectStore(store).put(value));
export const del=(store,key)=>tx([store],'readwrite',t=>t.objectStore(store).delete(key));
export const saveItem=(item,blob)=>tx(['items','blobs'],'readwrite',t=>{t.objectStore('items').put(item);if(blob)t.objectStore('blobs').put({key:item.key,blob});});
export const removeItem=key=>tx(['items','blobs'],'readwrite',t=>{t.objectStore('items').delete(key);t.objectStore('blobs').delete(key);});
export const consumeInbox=()=>tx(['inbox'],'readwrite',t=>{const s=t.objectStore('inbox'),r=s.getAll();s.clear();return r;});
