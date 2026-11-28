const DB='linkup-v1'; let promise;
export function database(){
    return promise??=new Promise((resolve,reject)=>{
        const request=indexedDB.open(DB,1);
        request.onupgradeneeded=()=>{
            for(const name of ['items','blobs','inbox']){
                if(!request.result.objectStoreNames.contains(name))request.result.createObjectStore(name,{keyPath:'key'});
            }
        };
        request.onsuccess=()=>{
            const connection=request.result;
            connection.onversionchange=connection.onclose=()=>{connection.close();promise=undefined;};
            resolve(connection);
        };
        request.onerror=()=>reject(request.error);
    }).catch(error=>{promise=undefined;throw error;});
}
async function tx(stores,mode,fn){const db=await database();return new Promise((resolve,reject)=>{const t=db.transaction(stores,mode);let value;try{value=fn(t);}catch(e){t.abort();reject(e);return}t.oncomplete=()=>resolve(value?.result);t.onerror=()=>reject(t.error);t.onabort=()=>reject(t.error||Error('Хранилище недоступно'));});}
export const all=store=>tx([store],'readonly',t=>t.objectStore(store).getAll());
export const get=(store,key)=>tx([store],'readonly',t=>t.objectStore(store).get(key));
export const put=(store,value)=>tx([store],'readwrite',t=>t.objectStore(store).put(value));
export const del=(store,key)=>tx([store],'readwrite',t=>t.objectStore(store).delete(key));
export const saveItem=(item,blob)=>tx(['items','blobs'],'readwrite',t=>{t.objectStore('items').put(item);if(blob)t.objectStore('blobs').put({key:item.key,blob});});
export const saveItems=entries=>tx(['items','blobs'],'readwrite',transaction=>{
    const items=transaction.objectStore('items'), blobs=transaction.objectStore('blobs');
    for(const {item,blob} of entries){
        items.add(item);
        if(blob)blobs.add({key:item.key,blob});
    }
});
export const mutateItem=(key,decide)=>tx(['items','blobs'],'readwrite',transaction=>{
    const items=transaction.objectStore('items'), blobs=transaction.objectStore('blobs');
    const request=items.get(key);
    let result;
    request.onsuccess=()=>{
        result=decide(request.result);
        if(result===null)items.delete(key);
        else if(result!==undefined)items.put(result);
        if(result===null||result?.deleted_at)blobs.delete(key);
    };
    return {get result(){return result;}};
});
export const cacheBlob=(key,blob,allowed)=>tx(['items','blobs'],'readwrite',transaction=>{
    const request=transaction.objectStore('items').get(key);
    request.onsuccess=()=>{
        if(allowed()&&request.result&&!request.result.deleted_at){
            transaction.objectStore('blobs').put({key,blob});
        }
    };
});
export const removeItem=key=>tx(['items','blobs'],'readwrite',t=>{t.objectStore('items').delete(key);t.objectStore('blobs').delete(key);});
export const consumeInbox=()=>tx(['inbox'],'readwrite',t=>{const s=t.objectStore('inbox'),r=s.getAll();s.clear();return r;});
