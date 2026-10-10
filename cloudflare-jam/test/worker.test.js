import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {Miniflare,convertV4MiniflareOptions} from 'miniflare';
import {assetLinks} from '../src/asset-links.js';
let mf, ip = 0;
const origin = 'https://jam.sh1vvy.com';
before(async () => {
  mf = new Miniflare(convertV4MiniflareOptions({unsafeInspectDurableObjects:true,name:'daylight-jam',modules:['worker.js','party.js','website.js','asset-links.js','queue-storage.js'].map(file=>({type:'ESModule',path:fileURLToPath(new URL('../src/'+file,import.meta.url))})),compatibilityDate:'2026-10-08',
    durableObjects:{DIRECTORY:{className:'JamDirectory',useSQLite:true},ROOMS:{className:'JamRoom',useSQLite:true}},
    bindings:{PUBLIC_ORIGIN:origin,MAX_PARTIES:'50',MAX_UPCOMING_QUEUE:'2000'},
    assets:{directory:fileURLToPath(new URL('../public',import.meta.url)),routerConfig:{has_user_worker:true},assetConfig:{html_handling:'none',not_found_handling:'none'}}}));
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
test('code form routes normalize the app alphabet without silently truncating long input',async () => {
  for (const [raw,code] of [['abc-123','ABC123'],[' ilO 123 ','110123']]) {
    const response=await request(`/join?code=${encodeURIComponent(raw)}`,{redirect:'manual'});
    assert.equal(response.status,303);
    assert.equal(new URL(response.headers.get('Location'),origin).pathname,`/invite/${code}`);
  }
  for (const raw of ['', 'ABC12', 'ABC1234', 'ABC12!', 'A'.repeat(1000), 'ABC123'+' '.repeat(58)+'4', '"><img src=x onerror=alert(1)>']) {
    const response=await request(`/join?code=${encodeURIComponent(raw)}`);
    const html=await response.text();
    assert.equal(response.status,422);
    assert.match(response.headers.get('Content-Type'),/^text\/html/);
    assert.match(html,/Enter the six-character code from your invite\./);
    assert.ok(!html.includes('<img src=x'));
    assert.ok(!html.includes('A'.repeat(65)));
  }
});
test('missing and malformed invitations render friendly HTML without altering API errors',async () => {
  for (const code of ['ZZZ999','not-a-valid-code']) {
    const response=await request(`/invite/${code}`);
    assert.equal(response.status,404);
    assert.match(response.headers.get('Content-Type'),/^text\/html/);
    assert.match(await response.text(),/Daylight/);
  }
  const api=await request('/api/parties/ZZZ999/preview');
  assert.equal(api.status,404);
  assert.match(api.headers.get('Content-Type'),/^application\/json/);
});
test('full invitation keeps an app-opening action and redacts private identity',async () => {
  const response=await post('/api/parties',{...identity('Full room host'),maxMembers:2},{'CF-Connecting-IP':`192.0.2.${++ip}`});
  assert.equal(response.status,201);
  const host=await response.json();
  const guest=await (await post(`/api/parties/${host.code}/join`,identity('Full room guest'))).json();
  const invite=await request(`/invite/${host.code}`);
  const html=await invite.text();
  assert.equal(invite.status,200);
  assert.match(html,/This Jam is full/);
  assert.match(html,/Open in Daylight/);
  assert.ok(!html.includes(host.token));
  assert.ok(!html.includes(guest.token));
  await post(`/api/parties/${host.code}/leave`,null,{Authorization:`Bearer ${host.token}`});
  await post(`/api/parties/${host.code}/leave`,null,{Authorization:`Bearer ${guest.token}`});
});
test('static assets load with correct types without intercepting app verification or the protocol',async () => {
  const assets=[
    ['/assets/jam-v2.css',/^text\/css/],
    ['/assets/jam-v1.js',/^(?:text|application)\/javascript/],
    ['/assets/daylight-mark.svg',/^image\/svg\+xml/],
    ['/assets/favicon.svg',/^image\/svg\+xml/],
    ['/assets/Inter-Regular-v4.1.woff2',/^font\/woff2/],
    ['/assets/Inter-SemiBold-v4.1.woff2',/^font\/woff2/],
    ['/assets/font-license.txt',/^text\/plain/],
  ];
  for (const [path,type] of assets) {
    const response=await request(path);
    assert.equal(response.status,200,path);
    assert.match(response.headers.get('Content-Type'),type,path);
    assert.ok((await response.arrayBuffer()).byteLength>0,path);
    assert.ok(response.headers.get('Cache-Control'),path);
    const maxAges=response.headers.get('Cache-Control').match(/max-age=/g) ?? [];
    assert.equal(maxAges.length,1,path+' must have exactly one cache lifetime');
  }
  const links=await request('/.well-known/assetlinks.json');
  assert.equal(links.status,200);
  assert.deepEqual(await links.json(),assetLinks);
  assert.equal((await (await request('/healthz')).json()).service,'daylight-jam');
  assert.equal((await request('/assets/does-not-exist.svg')).status,404);
});

test('a full liked collection passes the socket size bound and survives hibernation',async()=>{
  const host=await create(), guest=await (await post(`/api/parties/${host.code}/join`,identity('Large queue guest'))).json();
  const a=await connect(host),b=await connect(guest);
  const tracks=Array.from({length:1727},(_,i)=>({videoId:'large-'+i,title:'Collection track '+i,artist:'Daylight QA',thumbnailUrl:'https://example.com/cover-'+i+'.jpg',durationMs:180000}));
  a.send({type:'control',action:'setQueue',queue:tracks,queueIndex:0});
  const queue=await b.receive('queue'); assert.equal(queue.queue.items.length,1727);
  a.send({type:'control',action:'setTrack',track:tracks[0],queueIndex:0});
  await b.receive('state',f=>f.playback.seq===1);
  await mf.unsafeEvictDurableObject('daylight-jam','JamRoom',{name:host.code,webSockets:'hibernate'});
  const stored=await (await request(`/api/parties/${host.code}`,{headers:{Authorization:`Bearer ${host.token}`}})).json();
  assert.equal(stored.queue.items.length,1727); assert.equal(stored.queue.items.at(-1).videoId,'large-1726');
  a.send({type:'control',action:'queueClear'});
  assert.equal((await b.receive('queue',f=>f.queue.seq===2)).queue.items.length,1);
  await post(`/api/parties/${host.code}/leave`,null,{Authorization:`Bearer ${guest.token}`});
  await post(`/api/parties/${host.code}/leave`,null,{Authorization:`Bearer ${host.token}`});
  a.ws.close(); b.ws.close();
});
