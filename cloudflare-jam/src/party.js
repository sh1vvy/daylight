// Daylight's Cloudflare adaptation of BitChord's backend/party and protocol code.
// Original authors and GPLv3 terms are retained in the repository's LICENSE and UPSTREAM.md.
export const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTUVWXYZ';
export const AGE_MS = 12 * 60 * 60 * 1000;
export const EMPTY_MS = 120000;
export const GRACE_MS = 45000;
export const HEARTBEAT_MS = 5000;
export class JamError extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}
export function fail(status, code, message) { throw new JamError(status, code, message); }
export function normalizeCode(value) {
  return String(value).toUpperCase().replace(/[^A-Z0-9]/g, '').replace(/[IL]/g, '1').replace(/O/g, '0');
}
export function validCode(code) { return code.length === 6 && [...code].every(c => ALPHABET.includes(c)); }
export function randomHex(bytes = 24) {
  return [...crypto.getRandomValues(new Uint8Array(bytes))].map(n => n.toString(16).padStart(2, '0')).join('');
}
export function newCode() {
  // Rejection sampling avoids modulo bias with the room-code alphabet.
  let code = '';
  while (code.length < 6) {
    for (const n of crypto.getRandomValues(new Uint8Array(12))) {
      if (n < Math.floor(256 / ALPHABET.length) * ALPHABET.length) code += ALPHABET[n % ALPHABET.length];
      if (code.length === 6) break;
    }
  }
  return code;
}
export function sameToken(a, b) {
  if (typeof b !== 'string' || a.length !== b.length) return false;
  let different = 0;
  for (let i = 0; i < a.length; i++) different |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return different === 0;
}
export function identity(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) fail(422, 'validation_error', 'Expected an identity object.');
  const result = {};
  for (const [key, max] of [['userId',128],['deviceId',128],['displayName',80]]) {
    if (typeof body[key] !== 'string' || !body[key].trim() || body[key].trim().length > max)
      fail(422, 'validation_error', `${key} must be between 1 and ${max} characters.`);
    result[key] = body[key].trim();
  }
  result.avatarUrl = null;
  if (body.avatarUrl != null && body.avatarUrl !== '') {
    if (typeof body.avatarUrl !== 'string' || body.avatarUrl.length > 1000 || !/^https?:\/\//i.test(body.avatarUrl))
      fail(422, 'validation_error', 'avatarUrl must be an http(s) URL.');
    result.avatarUrl = body.avatarUrl.trim();
  }
  if (body.maxMembers != null && (!Number.isInteger(body.maxMembers) || body.maxMembers < 2 || body.maxMembers > 10))
    fail(422, 'invalid_capacity', 'Party size must be between 2 and 10.');
  if (body.autoplayEnabled != null && typeof body.autoplayEnabled !== 'boolean')
    fail(422, 'invalid_autoplay', 'AutoPlay must be enabled or disabled.');
  return { ...result, maxMembers: body.maxMembers ?? 5, autoplayEnabled: body.autoplayEnabled ?? false };
}
export function track(raw) {
  if (!raw || typeof raw.videoId !== 'string' || !raw.videoId) return null;
  return { videoId: raw.videoId.slice(0,128), title: String(raw.title ?? '').slice(0,300),
    artist: String(raw.artist ?? '').slice(0,300), thumbnailUrl: typeof raw.thumbnailUrl === 'string' ? raw.thumbnailUrl.slice(0,1000) : null,
    durationMs: Number.isFinite(raw.durationMs) && raw.durationMs > 0 ? Math.trunc(raw.durationMs) : null,
    fromAutoplay: raw.fromAutoplay === true };
}
export function createRoom(code, who, now = Date.now()) {
  return { code, createdAtMs: now, emptySinceMs: now, maxMembers: who.maxMembers, hostOnlyControl: false, members: [],
    playback: { track: null, items: [], queueIndex: -1, seq: 0, queueSeq: 0, isPlaying: false,
      positionMs: 0, anchorMs: now, updatedBy: null, startedBy: null, startedByName: null,
      autoplayEnabled: who.autoplayEnabled, updatedAtMs: now } };
}
export function expired(room, now = Date.now()) {
  return !room || now - room.createdAtMs >= AGE_MS || (room.emptySinceMs != null && now - room.emptySinceMs >= EMPTY_MS);
}
export function memberWire(m) {
  const { memberId, userId, displayName, avatarUrl, isHost, connected, joinedAtMs, lastSeenMs } = m;
  return { memberId, userId, displayName, avatarUrl, isHost, connected, joinedAtMs, lastSeenMs };
}
export function positionAt(p, now = Date.now()) {
  let pos = p.positionMs + (p.isPlaying ? Math.max(0, now - p.anchorMs) : 0);
  if (p.track?.durationMs) pos = Math.min(pos, p.track.durationMs);
  return pos;
}
export function playbackWire(p, now = Date.now()) {
  const { items, ...wire } = p;
  return { ...wire, queueLength: items.length, effectivePositionMs: positionAt(p, now) };
}
export function queueWire(p) { return { seq: p.queueSeq, index: p.queueIndex, items: p.items }; }
export function snapshot(room, now = Date.now()) {
  return { code: room.code, createdAtMs: room.createdAtMs, maxMembers: room.maxMembers,
    hostOnlyControl: room.hostOnlyControl, members: room.members.map(memberWire),
    playback: playbackWire(room.playback, now), queue: queueWire(room.playback), serverMs: now };
}
export function refreshEmpty(room, now = Date.now()) {
  if (room.members.some(m => m.connected)) room.emptySinceMs = null;
  else room.emptySinceMs ??= now;
}
export function removeMember(room, id, now = Date.now()) {
  const old = room.members.find(m => m.memberId === id);
  if (!old) return null;
  room.members = room.members.filter(m => m.memberId !== id);
  if (old.isHost && room.members.length) room.members[0].isHost = true;
  refreshEmpty(room, now);
  return old;
}
export function pruneMembers(room, now = Date.now()) {
  const removed = room.members.filter(m => !m.connected && now - m.lastSeenMs >= GRACE_MS);
  for (const m of removed) removeMember(room, m.memberId, now);
  return removed.length;
}
export function join(room, who, now = Date.now()) {
  pruneMembers(room, now);
  let m = room.members.find(m => m.deviceId === who.deviceId);
  if (m) Object.assign(m, { userId: who.userId, displayName: who.displayName, avatarUrl: who.avatarUrl, token: randomHex(), lastSeenMs: now });
  else {
    if (room.members.length >= room.maxMembers) fail(409, 'party_full', `This party is full (${room.maxMembers} devices).`);
    m = { memberId: randomHex(8), token: randomHex(), deviceId: who.deviceId, userId: who.userId,
      displayName: who.displayName, avatarUrl: who.avatarUrl, isHost: room.members.length === 0,
      connected: false, joinedAtMs: now, lastSeenMs: now };
    room.members.push(m);
  }
  return m;
}
export function authenticate(room, token) {
  const m = room.members.find(m => sameToken(m.token, token));
  if (!m) fail(401, 'bad_token', 'This device is not a member of that party.');
  return m;
}
export function nearestIndex(items, videoId, around) {
  if (items[around + 1]?.videoId === videoId) return around + 1;
  if (items[around - 1]?.videoId === videoId) return around - 1;
  let best = -1, score = Infinity;
  items.forEach((item,i) => {
    const d = i - around, distance = d < 0 ? -d * 2 + 1 : d;
    if (item.videoId === videoId && distance < score) { best = i; score = distance; }
  });
  return best;
}
export function setTrack(p, t, pos, playing, index, m, now) {
  p.track = t; p.positionMs = Math.max(0,pos); p.isPlaying = playing && t != null;
  p.anchorMs = now + (p.isPlaying ? 350 : 0);
  p.queueIndex = Number.isInteger(index) && index >= 0 && p.items[index]?.videoId === t?.videoId ? index : t ? nearestIndex(p.items, t.videoId, p.queueIndex) : -1;
  p.startedBy = m.memberId; p.startedByName = m.displayName;
}
const PLAYBACK_ACTIONS = new Set(['play','pause','seek','setTrack','setQueue','queueAdd','queueRemove','queueClear','queueMove','next','previous','setAutoplay']);
export function control(room, m, f, maxUpcoming = 25, now = Date.now()) {
  const p = room.playback, action = f.action;
  if (PLAYBACK_ACTIONS.has(action) && room.hostOnlyControl && !m.isHost)
    fail(403, 'host_only', 'Only the host can control the music in this party.');
  const host = () => { if (!m.isHost) fail(403,'host_only','Only the host can change this setting.'); };
  const boolean = key => { if (typeof f[key] !== 'boolean') fail(422,'invalid_control',`${key} must be a boolean.`); return f[key]; };
  const supplied = Number.isFinite(f.positionMs) ? Math.max(0,Math.trunc(f.positionMs)) : null;
  let queueChanged = false, stateChanged = false, membersChanged = false, kicked = null;
  switch (action) {
    case 'play': case 'pause': case 'seek': {
      if (action === 'seek' && supplied == null) fail(422,'missing_position','seek requires positionMs.');
      p.positionMs = supplied ?? positionAt(p,now);
      if (action !== 'seek') p.isPlaying = action === 'play';
      p.anchorMs = now + (p.isPlaying ? 350 : 0); stateChanged = true; break;
    }
    case 'setTrack': {
      const t = track(f.track); if (!t) fail(422,'invalid_track','Track must include videoId.');
      setTrack(p,t,supplied ?? 0, f.isPlaying !== false,f.queueIndex,m,now); stateChanged = true; break;
    }
    case 'setQueue': {
      const items = Array.isArray(f.queue) ? f.queue.map(track).filter(Boolean) : [];
      let i = Number.isInteger(f.queueIndex) ? f.queueIndex : -1;
      if (p.track && items[i]?.videoId !== p.track.videoId) i = nearestIndex(items,p.track.videoId,i);
      if (i < 0 || i >= items.length) i = -1;
      p.items = items.slice(0, i >= 0 ? i + 1 + maxUpcoming : 1 + maxUpcoming);
      p.queueIndex = i >= 0 && i < p.items.length ? i : -1; queueChanged = true; break;
    }
    case 'queueAdd': {
      const raw = f.track ? [f.track] : (Array.isArray(f.tracks) ? f.tracks : []);
      const incoming = raw.map(track).filter(Boolean), waiting = new Set(p.items.slice(p.queueIndex + 1).map(t => t.videoId));
      const slots = maxUpcoming - (p.items.length - Math.max(0,p.queueIndex + 1));
      if (slots <= 0) fail(409,'queue_full',`Queue is full (maximum ${maxUpcoming} upcoming songs).`);
      const unique = incoming.filter(t => {
        if (!t.fromAutoplay) return true;
        if (waiting.has(t.videoId)) return false;
        waiting.add(t.videoId); return true;
      }).slice(0,slots);
      if (!incoming.length) fail(422,'no_tracks','No tracks to add.');
      if (f.playNext && p.queueIndex >= 0) p.items.splice(p.queueIndex + 1,0,...unique);
      else p.items.push(...unique);
      queueChanged = unique.length > 0; break;
    }
    case 'queueRemove': {
      const id = f.videoId || p.items[f.index]?.videoId;
      if (!id) fail(422,'missing_track','queueRemove requires videoId or index.');
      const i = p.items.findIndex(t => t.videoId === id);
      if (i < 0 || i === p.queueIndex) fail(404,'not_found','Track is not in the upcoming queue.');
      p.items.splice(i,1); if (i < p.queueIndex) p.queueIndex--; queueChanged = true; break;
    }
    case 'queueClear': {
      const start = Math.max(0,p.queueIndex + 1);
      if (p.items.length <= start) fail(409,'no_upcoming','No upcoming tracks to clear.');
      p.items = p.items.slice(0,start); queueChanged = true; break;
    }
    case 'queueMove': {
      let from = f.fromIndex, to = f.toIndex;
      if (!Number.isInteger(from) || !Number.isInteger(to)) fail(422,'missing_indices','queueMove requires fromIndex and toIndex.');
      if (f.videoId) from = p.items.findIndex(t => t.videoId === f.videoId);
      if (from <= p.queueIndex || to <= p.queueIndex || from < 0 || to < 0 || from >= p.items.length || to >= p.items.length)
        fail(422,'invalid_move','Invalid queue move indices.');
      if (from !== to) { p.items.splice(to,0,p.items.splice(from,1)[0]); queueChanged = true; } break;
    }
    case 'next': case 'previous': {
      const i = p.queueIndex + (action === 'next' ? 1 : -1);
      if (i < 0 || i >= p.items.length) fail(409,action === 'next' ? 'end_of_queue' : 'start_of_queue','No track in that direction.');
      setTrack(p,p.items[i],0,true,i,m,now); stateChanged = true; break;
    }
    case 'setMaxMembers': {
      host(); const n = f.maxMembers;
      if (!Number.isInteger(n) || n < 2 || n > 10) fail(422,'invalid_capacity','Party size must be between 2 and 10.');
      if (n < room.members.length) fail(409,'party_too_small','Party size cannot be smaller than the current member count.');
      room.maxMembers = n; membersChanged = true; break;
    }
    case 'setHostOnlyControl': host(); room.hostOnlyControl = boolean('enabled'); membersChanged = true; break;
    case 'setAutoplay': p.autoplayEnabled = boolean('enabled'); break;
    case 'kick': {
      host(); if (!f.memberId || f.memberId === m.memberId) fail(422,'invalid_member','Choose another listener.');
      kicked = removeMember(room,f.memberId,now);
      if (!kicked) fail(404,'not_found','That listener is no longer in this party.');
      membersChanged = true; break;
    }
    default: fail(422,'unknown_action',`Unknown control action '${String(action).slice(0,80)}'.`);
  }
  if (stateChanged) p.seq++;
  if (queueChanged) p.queueSeq++;
  p.updatedBy = m.memberId; p.updatedAtMs = now;
  return { queueChanged, membersChanged, kicked };
}
export function activity(m,f,now = Date.now()) {
  const detail = ({play:'Resumed playback',pause:'Paused playback',seek:'Changed playback position',next:'Played the next track',previous:'Played the previous track',queueClear:'Cleared the upcoming queue',queueRemove:'Removed a track from the queue',queueMove:'Moved a track in the queue',setAutoplay:'Changed AutoPlay',setHostOnlyControl:'Changed who controls the music',setMaxMembers:'Changed the party size',kick:'Removed a listener'})[f.action]
    ?? (f.action === 'setTrack' ? `Changed the song to "${String(f.track?.title ?? '').slice(0,300)}"` : f.action === 'queueAdd' ? 'Added tracks to the queue' : f.action === 'setQueue' ? 'Replaced the queue' : f.action);
  return { type:'activity',action:f.action,by:m.displayName,detail,atMs:now };
}
