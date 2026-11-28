import assert from 'node:assert/strict';
import {test} from 'node:test';
import {acknowledgeUpload,boundSession,deleteLocally,mergeRemote,sessionFits} from '../ui/transfer-state.js';

test('the session from a partially saved account change cannot be sent to the old project',()=>{
    const first={url:'https://first.invalid',email:'first@example.invalid'};
    const next={url:'https://next.invalid',email:'next@example.invalid'};
    const session=boundSession({user:{id:'fixture'}},next);
    assert.equal(sessionFits(session,first),false);
    assert.equal(sessionFits(session,{...next,email:first.email}),false);
    assert.equal(sessionFits(session,next),true);
});

test('late acknowledgements and stale server rows cannot restore a deleted record',()=>{
    const remote={id:'fixture',text_content:'synthetic'};
    for(const consent of [true,false]){
        const pending={...remote,scope:'scope',state:'new'};
        const deletion=deleteLocally(pending,consent,'now');
        assert.equal(deletion.state,consent?'delete':'hidden');
        const acknowledged=acknowledgeUpload(deletion);
        assert.equal(acknowledged?.state||deletion.state,deletion.state);
        assert.equal(mergeRemote(acknowledged||deletion,remote,'scope:fixture','scope'),undefined);
    }
});

test('seeded reconciliation preserves every unsent edit and hides every confirmed deletion',()=>{
    const states=['new','delete','hidden','synced'];
    let seed=39;
    for(let trial=0;trial<500;trial++){
        seed=(Math.imul(seed,1664525)+1013904223)>>>0;
        const state=states[seed>>>30], deleted=Boolean(seed&1);
        const local={key:'fixture',state}, server={id:'fixture',deleted_at:deleted?'now':null};
        const decision=mergeRemote(local,server,'fixture','scope');
        if(state!=='synced')assert.equal(decision,undefined);
        else if(deleted)assert.equal(decision,null);
        else assert.equal(decision.state,'synced');
    }
});
