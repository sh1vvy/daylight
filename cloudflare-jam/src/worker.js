import { DurableObject } from 'cloudflare:workers';
import { AGE_MS, EMPTY_MS, GRACE_MS, HEARTBEAT_MS, JamError, fail, identity, newCode,
  normalizeCode, validCode, createRoom, expired, join, authenticate, memberWire,
  snapshot, playbackWire, queueWire, refreshEmpty, removeMember, pruneMembers,
  control, activity } from './party.js';
import { landing } from './website.js';
import { assetLinks } from './asset-links.js';

const MAX_BODY = 16384;
const JSON_HEADERS = { 'Content-Type':'application/json; charset=utf-8', 'Cache-Control':'no-store' };
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers:JSON_HEADERS });
function errorResponse(e) {
  if (e instanceof JamError) return json({error:e.code,message:e.message},e.status);
  console.error('Jam request failed', e?.stack ?? String(e));
  return json({error:'server_error',message:'The Jam server is temporarily unavailable.'},503);
}
async function body(request) {
  if (Number(request.headers.get('Content-Length')) > MAX_BODY) fail(413,'request_too_large','Request body is too large.');
  if (!request.body) fail(422,'invalid_json','Expected a JSON body.');
  const reader = request.body.getReader(), chunks = [];
  let size = 0;
  while (true) {
    const {value,done} = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > MAX_BODY) { await reader.cancel(); fail(413,'request_too_large','Request body is too large.'); }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk,offset); offset += chunk.length; }
  try { return JSON.parse(new TextDecoder().decode(bytes)); }
  catch { fail(422,'invalid_json','Malformed JSON body.'); }
}
function bearer(request) {
  const token = /^Bearer\s+(\S+)$/i.exec(request.headers.get('Authorization') ?? '')?.[1];
  if (!token) fail(401,'unauthorized','Missing bearer token.');
  return token;
}
function roomStub(env, code) { return env.ROOMS.get(env.ROOMS.idFromName(code)); }
function internalRequest(path, value) {
  return new Request(`https://room.internal${path}`, value ? {method:'POST',body:JSON.stringify(value)} : {});
}

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url), origin = request.headers.get('Origin');
      if (origin && origin !== env.PUBLIC_ORIGIN) fail(403,'origin_not_allowed','This origin is not allowed.');
      if (request.method === 'GET' && url.pathname === '/healthz') return json({ok:true,service:'daylight-jam',serverMs:Date.now()});
      if (request.method === 'GET' && url.pathname === '/api/time') return json({serverMs:Date.now()});
      if (request.method === 'GET' && url.pathname === '/') return landing(null,env.PUBLIC_ORIGIN);
      if (request.method === 'GET' && url.pathname === '/.well-known/assetlinks.json') return json(env.ASSET_LINKS ? JSON.parse(env.ASSET_LINKS) : assetLinks);
      if (request.method === 'POST' && url.pathname === '/api/parties') {
        const who = identity(await body(request));
        const directory = env.DIRECTORY.get(env.DIRECTORY.idFromName('daylight'));
        return directory.fetch(internalRequest('/create',{who,ip:request.headers.get('CF-Connecting-IP') ?? 'local'}));
      }
      const match = /^\/(api|ws)\/parties\/([^/]+)(?:\/(join|leave|preview))?$/.exec(url.pathname);
      const invite = /^\/invite\/([^/]+)$/.exec(url.pathname);
      const code = normalizeCode(match?.[2] ?? invite?.[1] ?? '');
      if ((!match && !invite) || !validCode(code)) fail(404,'not_found','This page does not exist.');
      const stub = roomStub(env,code);
      if (invite && request.method === 'GET') {
        const preview = await stub.fetch(internalRequest('/internal/preview'));
        return landing(preview.ok ? await preview.json() : {code,expired:true},env.PUBLIC_ORIGIN,preview.ok ? 200 : 404);
      }
      if (match) return stub.fetch(request);
      fail(405,'method_not_allowed','This method is not supported.');
    } catch (e) { return errorResponse(e); }
  }
};

// A small persistent directory enforces room limits and creation budgets.
// All playback traffic goes directly to its room, avoiding a global bottleneck.
export class JamDirectory extends DurableObject {
  async fetch(request) {
    return this.ctx.blockConcurrencyWhile(async () => {
      try {
        if (new URL(request.url).pathname !== '/create') fail(404,'not_found','Unknown directory operation.');
        const {who,ip} = await body(request), now = Date.now();
        const budgets = await this.ctx.storage.get('budgets') ?? {};
        for (const key of Object.keys(budgets)) if (now - budgets[key].since >= 60000) delete budgets[key];
        const budget = budgets[ip] ?? {since:now,count:0};
        if (budget.count >= 2 || Object.keys(budgets).length >= 10000) fail(429,'create_rate_limited','You can create up to two parties per minute. Please try again shortly.');
        budget.count++; budgets[ip] = budget;
        await this.ctx.storage.put('budgets',budgets);
        const rooms = await this.ctx.storage.get('rooms') ?? {};
        for (const [code,created] of Object.entries(rooms)) if (now - created >= AGE_MS) delete rooms[code];
        const max = Number(this.env.MAX_PARTIES) || 50;
        if (Object.keys(rooms).length >= max) {
          // Empty/expired rooms release capacity without a per-room directory timer.
          const checks = await Promise.all(Object.keys(rooms).map(async code => [code,(await roomStub(this.env,code).fetch(internalRequest('/internal/alive'))).ok]));
          for (const [code,alive] of checks) if (!alive) delete rooms[code];
        }
        if (Object.keys(rooms).length >= max) fail(503,'server_full','The server is full. Please try again shortly.');
        let code;
        do { code = newCode(); } while (rooms[code]);
        const result = await roomStub(this.env,code).fetch(internalRequest('/internal/create',{code,who}));
        if (result.ok) { rooms[code] = now; await this.ctx.storage.put('rooms',rooms); }
        return result;
      } catch (e) { return errorResponse(e); }
    });
  }
}

export class JamRoom extends DurableObject {
  constructor(ctx,env) {
    super(ctx,env);
    ctx.blockConcurrencyWhile(async () => {
      this.room = await ctx.storage.get('room');
      if (!this.room) return;
      let repaired = false;
      // Attachments and sockets survive eviction; reconnect the persisted members.
      for (const m of this.room.members) {
        const socket = this.sockets(m.memberId).find(ws => {
          const attachment = ws.deserializeAttachment();
          return attachment?.token === m.token && attachment.connectionId === m.connectionId;
        });
        if (socket) {
          m.connected = true;
          m.lastSeenMs = socket.deserializeAttachment().lastSeenMs;
        } else if (m.connected) { m.connected = false; m.lastSeenMs = Date.now(); repaired = true; }
      }
      refreshEmpty(this.room);
      // Persist a lost connection once so subsequent evictions cannot restart
      // its reconnect grace indefinitely after an abrupt restart/deployment.
      if (repaired) { await this.save(); await this.arm(); }
    });
  }
  sockets(id) { return this.ctx.getWebSockets(id); }
  send(ws,value) { try { ws.send(JSON.stringify(value)); } catch { /* close/error handles membership */ } }
  broadcast(value) { for (const ws of this.sockets()) this.send(ws,value); }
  stateFrame() { return {type:'state',playback:playbackWire(this.room.playback),serverMs:Date.now()}; }
  queueFrame() { return {type:'queue',queue:queueWire(this.room.playback),serverMs:Date.now()}; }
  membersFrame() { return {type:'members',members:this.room.members.map(memberWire),maxMembers:this.room.maxMembers,hostOnlyControl:this.room.hostOnlyControl,serverMs:Date.now()}; }
  closeMember(id,reason) {
    for (const ws of this.sockets(id)) {
      this.send(ws,{type:'bye',reason,message:reason === 'kicked' ? 'The host removed you from this party.' : 'This connection was closed.'});
      try { ws.close(1000,reason); } catch { /* already closed */ }
    }
  }
  async destroy() {
    for (const ws of this.sockets()) { this.send(ws,{type:'bye',reason:'expired',message:'This party has ended.'}); try { ws.close(1000,'expired'); } catch {} }
    this.room = null;
    await this.ctx.storage.deleteAll();
    await this.ctx.storage.deleteAlarm();
  }
  async requireRoom() {
    if (expired(this.room)) { if (this.room) await this.destroy(); fail(404,'no_such_party','No party with that code.'); }
  }
  async save() { await this.ctx.storage.put('room',this.room); }
  async arm() {
    const now = Date.now();
    // No intervals: an alarm wakes the room briefly and lets it hibernate again.
    let next = Math.min(this.room.createdAtMs + AGE_MS, this.room.emptySinceMs == null ? now + HEARTBEAT_MS : this.room.emptySinceMs + EMPTY_MS);
    for (const m of this.room.members) if (!m.connected) next = Math.min(next,m.lastSeenMs + GRACE_MS);
    const current = await this.ctx.storage.getAlarm();
    if (current == null || current > next || current <= now) await this.ctx.storage.setAlarm(Math.max(now + 100,next));
  }
  preview() {
    return {code:this.room.code,hostName:this.room.members.find(m => m.isHost)?.displayName ?? '',
      memberCount:this.room.members.length,maxMembers:this.room.maxMembers,isFull:this.room.members.length >= this.room.maxMembers,
      members:this.room.members.map(({displayName,avatarUrl,isHost}) => ({displayName,avatarUrl,isHost}))};
  }
  async fetch(request) {
    try {
      const url = new URL(request.url), path = url.pathname;
      if (path === '/internal/create' && request.method === 'POST') {
        if (this.room && !expired(this.room)) fail(409,'already_exists','This party already exists.');
        const {code,who} = await body(request);
        this.room = createRoom(code,who); const m = join(this.room,who);
        await this.save(); await this.arm();
        return json({code,token:m.token,you:memberWire(m),party:snapshot(this.room)},201);
      }
      await this.requireRoom();
      if (pruneMembers(this.room)) { await this.save(); this.broadcast(this.membersFrame()); }
      if (path === '/internal/alive') return json({ok:true});
      if (path === '/internal/preview' || (path.endsWith('/preview') && request.method === 'GET')) return json(this.preview());
      if (path.endsWith('/join') && request.method === 'POST') {
        const who = identity(await body(request));
        // Parsing a streamed body can yield. Recheck after it completes.
        await this.requireRoom();
        const previous = this.room.members.find(m => m.deviceId === who.deviceId);
        if (previous) { this.closeMember(previous.memberId,'replaced'); previous.connected = false; }
        const m = join(this.room,who); refreshEmpty(this.room);
        await this.save(); await this.arm(); this.broadcast(this.membersFrame());
        return json({code:this.room.code,token:m.token,you:memberWire(m),party:snapshot(this.room)});
      }
      const token = bearer(request), m = authenticate(this.room,token);
      if (path.startsWith('/ws/parties/') && request.method === 'GET') {
        if (request.headers.get('Upgrade')?.toLowerCase() !== 'websocket') fail(426,'upgrade_required','Expected a WebSocket connection.');
        this.closeMember(m.memberId,'replaced');
        const [client,server] = Object.values(new WebSocketPair());
        this.ctx.acceptWebSocket(server,[m.memberId]);
        const connectionId = crypto.randomUUID();
        server.serializeAttachment({memberId:m.memberId,token,connectionId,lastSeenMs:Date.now(),window:0,frames:0,controls:0});
        m.connectionId = connectionId; m.connected = true; m.lastSeenMs = Date.now(); refreshEmpty(this.room);
        await this.save(); await this.arm();
        this.send(server,{type:'welcome',you:memberWire(m),party:snapshot(this.room),serverMs:Date.now()});
        this.broadcast(this.membersFrame());
        return new Response(null,{status:101,webSocket:client});
      }
      if (path.endsWith('/leave') && request.method === 'POST') {
        removeMember(this.room,m.memberId); this.closeMember(m.memberId,'left');
        await this.save(); await this.arm(); this.broadcast(this.membersFrame());
        return json({ok:true});
      }
      if (request.method === 'GET' && /^\/api\/parties\/[^/]+$/.test(path)) return json(snapshot(this.room));
      fail(405,'method_not_allowed','This method is not supported.');
    } catch (e) { return errorResponse(e); }
  }
  async webSocketMessage(ws,message) {
    try {
      await this.requireRoom();
      const a = ws.deserializeAttachment(), m = authenticate(this.room,a?.token);
      if (m.connectionId !== a.connectionId) { ws.close(1000,'replaced'); return; }
      if (typeof message !== 'string' || new TextEncoder().encode(message).length > MAX_BODY) { ws.close(1009,'Message too large'); return; }
      const now = Date.now();
      if (now - a.window >= 1000) { a.window = now; a.frames = 0; a.controls = 0; }
      a.frames++; a.lastSeenMs = now; m.lastSeenMs = now;
      ws.serializeAttachment(a);
      if (a.frames > 30) fail(429,'rate_limited','Too many messages at once.');
      let f;
      try { f = JSON.parse(message); } catch { fail(422,'invalid_json','Malformed JSON message.'); }
      if (!f || typeof f !== 'object' || Array.isArray(f)) fail(422,'invalid_json','Expected a JSON object.');
      switch (f.type) {
        case 'ping': this.send(ws,{type:'pong',clientMs:f.clientMs ?? null,serverMs:now}); break;
        case 'sync': this.send(ws,this.stateFrame()); break;
        case 'syncQueue': this.send(ws,this.queueFrame()); break;
        case 'report': break; // Monitoring frames never change the authoritative playhead.
        case 'control': {
          a.controls++; ws.serializeAttachment(a);
          if (a.controls > 25) fail(429,'rate_limited','Too many controls at once.');
          const result = control(this.room,m,f,Number(this.env.MAX_UPCOMING_QUEUE) || 25,now);
          if (result.kicked) this.closeMember(result.kicked.memberId,'kicked');
          await this.save();
          if (result.queueChanged) this.broadcast(this.queueFrame());
          this.broadcast(this.stateFrame());
          if (result.membersChanged) this.broadcast(this.membersFrame());
          this.broadcast(activity(m,f,now)); break;
        }
      }
    } catch (e) {
      if (e instanceof JamError) {
        this.send(ws,{type:'error',error:e.code,message:e.message});
        if (e.status === 401 || e.status === 404) { try { ws.close(1000,e.code); } catch {} }
      } else { console.error('Jam WebSocket failed',e?.stack ?? String(e)); this.send(ws,{type:'error',error:'server_error',message:'Please reconnect to this party.'}); }
    }
  }
  async disconnected(ws) {
    const a = ws.deserializeAttachment();
    const m = this.room?.members.find(m => m.memberId === a?.memberId && m.connectionId === a.connectionId);
    if (!m || !m.connected) return;
    m.connected = false; m.lastSeenMs = Date.now(); refreshEmpty(this.room);
    await this.save(); await this.arm(); this.broadcast(this.membersFrame());
  }
  async webSocketClose(ws) { await this.disconnected(ws); }
  async webSocketError(ws) { try { ws.close(1011,'Connection error'); } catch {} await this.disconnected(ws); }
  async alarm() {
    if (!this.room) return;
    if (expired(this.room)) { await this.destroy(); return; }
    let changed = false;
    for (const ws of this.sockets()) {
      const a = ws.deserializeAttachment();
      if (Date.now() - (a?.lastSeenMs ?? 0) > 15 * 60 * 1000) {
        const m = this.room.members.find(m => m.connectionId === a.connectionId);
        if (m) { m.connected = false; m.lastSeenMs = Date.now(); changed = true; }
        try { ws.close(1000,'idle'); } catch {}
      }
    }
    if (pruneMembers(this.room)) changed = true;
    refreshEmpty(this.room);
    if (changed) { await this.save(); this.broadcast(this.membersFrame()); }
    if (this.room.members.some(m => m.connected)) this.broadcast(this.stateFrame());
    await this.arm();
  }
}
