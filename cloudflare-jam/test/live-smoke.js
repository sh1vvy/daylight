// Explicit live check: creates one disposable room, connects two clients, then leaves.
// Never runs in the unit-test suite or CI, and never prints membership tokens.
import https from 'node:https';
import dns from 'node:dns';
import assert from 'node:assert/strict';
import WebSocket from 'ws';
const origin = new URL(process.env.DAYLIGHT_JAM_URL ?? 'https://jam.sh1vvy.com');
if (origin.protocol !== 'https:') throw new Error('Live checks require HTTPS.');
const resolver = new dns.Resolver(); resolver.setServers(['1.1.1.1']);
function lookup(host,options,callback) {
  dns.lookup(host,options,(error,address,family) => {
    if (!error || error.code !== 'ENOTFOUND') return callback(error,address,family);
    // Newly created domains can remain in the machine's negative DNS cache.
    resolver.resolve4(host,(err,addresses) => {
      if (err) return callback(err);
      callback(null,options.all ? addresses.map(address=>({address,family:4})) : addresses[0],4);
    });
  });
}
function request(path,method='GET',value,token) {
  return new Promise((resolve,reject) => {
    const data=value === undefined ? null : JSON.stringify(value);
    const req=https.request(new URL(path,origin),{method,lookup,headers:{...(data ? {'Content-Type':'application/json','Content-Length':Buffer.byteLength(data)} : {}),...(token ? {Authorization:`Bearer ${token}`} : {})}},res=>{
      let body=''; res.setEncoding('utf8'); res.on('data',chunk=>body+=chunk);
      res.on('end',()=>resolve({status:res.statusCode,body,json:()=>JSON.parse(body)}));
    });
    req.on('error',reject); req.setTimeout(15000,()=>req.destroy(new Error('HTTP request timed out')));
    if (data) req.write(data); req.end();
  });
}
function socket(member) {
  const url=new URL(`/ws/parties/${member.code}`,origin); url.protocol='wss:';
  const ws=new WebSocket(url,{lookup,headers:{Authorization:`Bearer ${member.token}`}}),frames=[],waiting=[];
  ws.on('message',data=>{
    const frame=JSON.parse(data); const i=waiting.findIndex(w=>w.matches(frame));
    if(i>=0){const [w]=waiting.splice(i,1);clearTimeout(w.timer);w.resolve(frame);}else frames.push(frame);
  });
  ws.on('error',error=>{for(const w of waiting){clearTimeout(w.timer);w.reject(error);}waiting.length=0;});
  const receive=(type,filter=()=>true)=>{
    const matches=f=>f.type===type&&filter(f),i=frames.findIndex(matches);
    if(i>=0)return Promise.resolve(frames.splice(i,1)[0]);
    return new Promise((resolve,reject)=>{const w={matches,resolve,reject};w.timer=setTimeout(()=>{waiting.splice(waiting.indexOf(w),1);reject(new Error('Timed out waiting for '+type));},15000);waiting.push(w);});
  };
  return {ws,receive,send:frame=>ws.send(JSON.stringify(frame))};
}
const who=name=>({userId:`daylight-verification-${name}`,deviceId:crypto.randomUUID(),displayName:`Daylight verification ${name}`});
let host,guest,a,b;
try {
  assert.equal((await request('/healthz')).json().ok,true);
  const created=await request('/api/parties','POST',{...who('host'),maxMembers:2});
  assert.equal(created.status,201); host=created.json();
  const joined=await request(`/api/parties/${host.code}/join`,'POST',who('guest'));
  assert.equal(joined.status,200); guest=joined.json();
  assert.equal((await request(`/api/parties/${host.code}/preview`)).json().memberCount,2);
  const page=await request(`/invite/${host.code}`); assert.equal(page.status,200); assert.ok(page.body.includes('Open in Daylight'));
  a=socket(host); b=socket(guest); await Promise.all([a.receive('welcome'),b.receive('welcome')]);
  a.send({type:'ping',clientMs:12345}); assert.equal((await a.receive('pong')).clientMs,12345);
  const tracks=[{videoId:'verification-first',title:'Verification First',durationMs:120000},{videoId:'verification-second',title:'Verification Second',durationMs:120000}];
  a.send({type:'control',action:'setQueue',queue:tracks,queueIndex:0});
  assert.equal((await b.receive('queue')).queue.items.length,2);
  a.send({type:'control',action:'setTrack',track:tracks[0],queueIndex:0});
  const state=await b.receive('state',f=>f.playback.seq===1); assert.equal(state.playback.track.videoId,tracks[0].videoId);
  assert.equal(state.playback.anchorMs-state.playback.updatedAtMs,350);
  b.send({type:'control',action:'next'});
  assert.equal((await a.receive('state',f=>f.playback.seq===2)).playback.track.videoId,tracks[1].videoId);
  a.send({type:'control',action:'setHostOnlyControl',enabled:true});
  await b.receive('members',f=>f.hostOnlyControl);
  b.send({type:'control',action:'pause'}); assert.equal((await b.receive('error')).error,'host_only');
  a.send({type:'control',action:'kick',memberId:guest.you.memberId}); assert.equal((await b.receive('bye')).reason,'kicked');
  assert.equal((await request(`/api/parties/${host.code}`,'GET',undefined,guest.token)).status,401);
  console.log('Live HTTPS, invites, two authenticated WebSockets, clock sync, queue/next, host policy and kick: passed.');
} finally {
  if(guest) await request(`/api/parties/${guest.code}/leave`,'POST',{},guest.token);
  if(host) await request(`/api/parties/${host.code}/leave`,'POST',{},host.token);
  a?.ws.close(); b?.ws.close();
}
