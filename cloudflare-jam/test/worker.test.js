import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {Miniflare,convertV4MiniflareOptions} from 'miniflare';
let mf, ip = 0;
const origin = 'https://jam.sh1vvy.com';
before(async () => {
  mf = new Miniflare(convertV4MiniflareOptions({unsafeInspectDurableObjects:true,name:'daylight-jam',modules:['worker.js','party.js','website.js','asset-links.js'].map(file=>({type:'ESModule',path:fileURLToPath(new URL('../src/'+file,import.meta.url))})),compatibilityDate:'2026-10-08',
    durableObjects:{DIRECTORY:{className:'JamDirectory',useSQLite:true},ROOMS:{className:'JamRoom',useSQLite:true}},
    bindings:{PUBLIC_ORIGIN:origin,MAX_PARTIES:'50',MAX_UPCOMING_QUEUE:'25'}}));
  await mf.ready;
});
after(async () => { await mf?.dispose(); });
const identity = name => ({userId:name,deviceId:name,displayName:name});
const request = (path,init) => mf.dispatchFetch(origin+path,init);
const post = (path,value,headers={}) => request(path,{method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify(value)});
async function create(name='Host') { const r=await post('/api/parties',identity(name),{'CF-Connecting-IP':`192.0.2.${++ip}`}); assert.equal(r.status,201); return r.json(); }
async function connect(member) {
  const r = await request(`/ws/parties/${member.code}`,{headers:{Upgrade:'websocket',Authorization:`Bearer ${member.token}`}});
  assert.equal(r.status,101);
  const ws = r.webSocket, frames = [], waiting = [];
  ws.addEventListener('message',e => {
    const f=JSON.parse(e.data); const index=waiting.findIndex(w=>w.filter(f));
    if (index >= 0) { const [w]=waiting.splice(index,1); clearTimeout(w.timer); w.resolve(f); } else frames.push(f);
  });
  const receive = (type,filter=()=>true) => {
    const matches = f=>f.type===type&&filter(f), index=frames.findIndex(matches);
    if (index >= 0) return Promise.resolve(frames.splice(index,1)[0]);
    return new Promise((resolve,reject)=>{ const w={filter:matches,resolve}; w.timer=setTimeout(()=>{waiting.splice(waiting.indexOf(w),1);reject(new Error(`Timed out waiting for ${type}`));},4000); waiting.push(w); });
  };
  ws.accept(); await receive('welcome');
  return {ws,receive,send:value=>ws.send(JSON.stringify(value))};
}
test('health, input bounds, unknown rooms and origin checks',async () => {
  assert.equal((await (await request('/healthz')).json()).ok,true);
  assert.equal((await request('/healthz',{headers:{Origin:'https://unrelated.example'}})).status,403);
  assert.equal((await request('/api/parties/ABC123/preview')).status,404);
  assert.equal((await post('/api/parties',{})).status,422);
  assert.equal((await post('/api/parties',{...identity('Host'),displayName:'x'.repeat(20000)})).status,413);
  assert.equal((await request('/internal/create')).status,404);
});
test('HTTP preview redacts identity and tokens; bearer authentication stays out of URLs',async () => {
  const room=await create();
  const r=await request(`/api/parties/${room.code}/preview`), preview=await r.json();
  assert.equal(preview.hostName,'Host'); assert.equal(preview.memberCount,1);
  assert.deepEqual(Object.keys(preview.members[0]).sort(),['avatarUrl','displayName','isHost']);
  assert.equal((await request(`/api/parties/${room.code}?token=${room.token}`)).status,401);
  assert.equal((await request(`/ws/parties/${room.code}?token=${room.token}`,{headers:{Upgrade:'websocket'}})).status,401);
  assert.equal((await request(`/api/parties/${room.code}`,{headers:{Authorization:`Bearer ${room.token}`}})).status,200);
  const page=await request(`/invite/${room.code}`);
  assert.match(await page.text(),/daylight:\/\/party/);
  await post(`/api/parties/${room.code}/leave`,null,{Authorization:`Bearer ${room.token}`});
});
test('two WebSockets share queues, clock anchors and policy; kicked tokens stop working',async () => {
  const host=await create(), guest=await (await post(`/api/parties/${host.code}/join`,identity('Guest'))).json();
  const a=await connect(host), b=await connect(guest);
  a.send({type:'ping',clientMs:12345}); assert.equal((await a.receive('pong')).clientMs,12345);
  const tracks=[{videoId:'first',title:'First',durationMs:120000},{videoId:'second',title:'Second'}];
  a.send({type:'control',action:'setQueue',queue:tracks,queueIndex:0});
  assert.equal((await b.receive('queue')).queue.items.length,2);
  a.send({type:'control',action:'setTrack',track:tracks[0],queueIndex:0,positionMs:1200});
  const state=await b.receive('state',f=>f.playback.seq===1);
  assert.equal(state.playback.track.videoId,'first'); assert.ok(state.playback.anchorMs > state.serverMs);
  b.send({type:'control',action:'next'});
  assert.equal((await a.receive('state',f=>f.playback.seq===2)).playback.track.videoId,'second');
  a.send({type:'control',action:'setHostOnlyControl',enabled:true});
  await b.receive('members',f=>f.hostOnlyControl);
  b.send({type:'control',action:'pause'});
  assert.equal((await b.receive('error')).error,'host_only');
  a.send({type:'control',action:'kick',memberId:guest.you.memberId});
  assert.equal((await b.receive('bye')).reason,'kicked');
  assert.equal((await request(`/api/parties/${host.code}`,{headers:{Authorization:`Bearer ${guest.token}`}})).status,401);
  await post(`/api/parties/${host.code}/leave`,null,{Authorization:`Bearer ${host.token}`});
  a.ws.close(); b.ws.close();
});
test('reconnect replaces stale socket without disconnecting its successor; leaving hands over host',async () => {
  const host=await create(), a=await connect(host);
  const returned=await (await post(`/api/parties/${host.code}/join`,identity('Host'))).json();
  assert.equal((await a.receive('bye')).reason,'replaced');
  const fresh=await connect(returned);
  assert.equal((await request(`/api/parties/${host.code}`,{headers:{Authorization:`Bearer ${host.token}`}})).status,401);
  const guest=await (await post(`/api/parties/${host.code}/join`,identity('Guest'))).json(), b=await connect(guest);
  await post(`/api/parties/${host.code}/leave`,null,{Authorization:`Bearer ${returned.token}`});
  const members=await b.receive('members',f=>f.members.length===1&&f.members[0].isHost);
  assert.equal(members.members[0].connected,true);
  b.send({type:'control',action:'setMaxMembers',maxMembers:7});
  assert.equal((await b.receive('members',f=>f.maxMembers===7)).maxMembers,7);
  await post(`/api/parties/${host.code}/leave`,null,{Authorization:`Bearer ${guest.token}`});
  fresh.ws.close(); b.ws.close(); a.ws.close();
});
test('creation budget survives independent requests',async () => {
  const headers={'CF-Connecting-IP':'192.0.2.250'};
  assert.equal((await post('/api/parties',identity('one'),headers)).status,201);
  assert.equal((await post('/api/parties',identity('two'),headers)).status,201);
  assert.equal((await post('/api/parties',identity('three'),headers)).status,429);
});
test('hibernation retains live sockets, playback and bearer membership',async () => {
  const host=await create(), a=await connect(host);
  a.send({type:'control',action:'setTrack',track:{videoId:'survives',title:'Survives'},positionMs:1000});
  await a.receive('state',f=>f.playback.seq===1);
  await mf.unsafeEvictDurableObject('daylight-jam','JamRoom',{name:host.code,webSockets:'hibernate'});
  a.send({type:'sync'});
  const state=await a.receive('state',f=>f.playback.seq===1);
  assert.equal(state.playback.track.videoId,'survives');
  const room=await (await request(`/api/parties/${host.code}`,{headers:{Authorization:`Bearer ${host.token}`}})).json();
  assert.equal(room.members[0].connected,true);
  await post(`/api/parties/${host.code}/leave`,null,{Authorization:`Bearer ${host.token}`});
  a.ws.close();
});
test('invite page escapes display names from other devices',async () => {
  const room=await create('<script>alert(1)</script>');
  const text=await (await request(`/invite/${room.code}`)).text();
  assert.ok(text.includes('&lt;script&gt;')); assert.ok(!text.includes('<script>alert(1)</script>'));
});
