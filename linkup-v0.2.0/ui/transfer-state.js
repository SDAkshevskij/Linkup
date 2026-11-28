export function mergeRemote(current, remote, key, scope) {
    if (current && current.state!=='synced') return undefined;
    if (remote.deleted_at) return current ? null : undefined;
    return {...remote,key,scope,state:'synced',everSynced:true};
}

export function acknowledgeUpload(current) {
    if (!current || !['new','delete'].includes(current.state)) return undefined;
    return {...current,everSynced:true,state:current.state==='new'?'synced':'delete'};
}

export function deleteLocally(current, allowRemote, timestamp) {
    if (!current || current.deleted_at) return undefined;
    if (current.scope==='local') return null;
    return {...current,deleted_at:timestamp,text_content:null,state:allowRemote?'delete':'hidden'};
}

export function boundSession(session, config) {
    return session ? {...session, linkupProject:config.url, linkupAccount:config.email} : null;
}

export function sessionFits(session, config) {
    return !session?.linkupProject ||
        (session.linkupProject===config.url && session.linkupAccount===config.email);
}
