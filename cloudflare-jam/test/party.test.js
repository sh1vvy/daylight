import test from 'node:test';
import assert from 'node:assert/strict';
import {identity,createRoom,join,authenticate,control,positionAt,queueWire,snapshot,removeMember,pruneMembers,expired,AGE_MS,EMPTY_MS} from '../src/party.js';
const who = (name, extra = {}) => identity({userId:name,deviceId:name,displayName:name,...extra});
const song = (videoId, extra = {}) => ({videoId,title:videoId,durationMs:120000,...extra});
function fixture() { const room = createRoom('ABC123',who('Host'),1000); return {room,host:join(room,who('Host'),1000),guest:join(room,who('Guest'),1000)}; }
test('future playback anchor, pause and seek preserve the shared playhead',() => {
  const {room,host} = fixture();
  control(room,host,{action:'setTrack',track:song('first'),positionMs:1000},25,2000);
  assert.equal(room.playback.anchorMs,2350);
  assert.equal(positionAt(room.playback,2200),1000);
  assert.equal(positionAt(room.playback,3350),2000);
  control(room,host,{action:'pause'},25,3350);
  assert.equal(positionAt(room.playback,5000),2000);
  control(room,host,{action:'seek',positionMs:-100},25,6000);
  assert.equal(room.playback.positionMs,0);
  assert.equal(room.playback.seq,3);
});
test('host policy, capacity, kick and host handoff are enforced server-side',() => {
  const {room,host,guest} = fixture();
  control(room,host,{action:'setHostOnlyControl',enabled:true});
  assert.throws(() => control(room,guest,{action:'setTrack',track:song('first')}),e => e.code === 'host_only');
  assert.throws(() => control(room,guest,{action:'kick',memberId:host.memberId}),e => e.code === 'host_only');
  control(room,host,{action:'setMaxMembers',maxMembers:2});
  assert.throws(() => join(room,who('Third'),1001),e => e.code === 'party_full');
  removeMember(room,host.memberId);
  assert.equal(guest.isHost,true);
  control(room,guest,{action:'setMaxMembers',maxMembers:3});
});
test('same-device rejoin rotates bearer token without consuming a slot or exposing secrets',() => {
  const {room,guest} = fixture(), oldToken = guest.token;
  const returned = join(room,who('Guest',{displayName:'Updated'}),2000);
  assert.equal(returned.memberId,guest.memberId);
  assert.equal(room.members.length,2);
  assert.throws(() => authenticate(room,oldToken),e => e.status === 401);
  const wire = JSON.stringify(snapshot(room));
  assert.ok(!wire.includes(returned.token)); assert.ok(!wire.includes('deviceId'));
});
test('duplicate songs select the correct occurrence, autoplay deduplicates and manual repeats survive',() => {
  const {room,host} = fixture();
  control(room,host,{action:'setQueue',queue:['a','b','a','c'].map(id=>song(id)),queueIndex:2});
  control(room,host,{action:'setTrack',track:song('a'),queueIndex:2});
  control(room,host,{action:'queueAdd',tracks:[song('c',{fromAutoplay:true}),song('d',{fromAutoplay:true}),song('d',{fromAutoplay:true}),song('c')]});
  assert.deepEqual(queueWire(room.playback).items.map(t=>t.videoId),['a','b','a','c','d','c']);
  control(room,host,{action:'next'});
  assert.equal(room.playback.queueIndex,3);
  control(room,host,{action:'previous'});
  assert.equal(room.playback.queueIndex,2);
});
test('upcoming queue limit preserves history and current track; invalid operations leave it intact',() => {
  const {room,host} = fixture();
  control(room,host,{action:'setQueue',queue:Array.from({length:40},(_,i)=>song(String(i))),queueIndex:10},25);
  control(room,host,{action:'setTrack',track:song('10'),queueIndex:10},25);
  assert.equal(room.playback.items.length,36);
  assert.throws(()=>control(room,host,{action:'queueAdd',track:song('overflow')},25),e=>e.code==='queue_full');
  assert.throws(()=>control(room,host,{action:'queueMove',fromIndex:10,toIndex:11}),e=>e.code==='invalid_move');
  control(room,host,{action:'queueClear'});
  assert.equal(room.playback.items.length,11);
  assert.equal(room.playback.track.videoId,'10');
});
test('out-of-range queue index cannot bypass the queue bound',() => {
  const {room,host} = fixture();
  control(room,host,{action:'setQueue',queue:Array.from({length:40},(_,i)=>song(String(i))),queueIndex:99999},25);
  assert.equal(room.playback.queueIndex,-1);
  assert.equal(room.playback.items.length,26);
});
test('disconnected members release capacity and empty rooms expire; age limit also applies to active rooms',() => {
  const {room,host,guest} = fixture();
  guest.connected = true; room.emptySinceMs = null;
  assert.equal(pruneMembers(room,46000),1);
  assert.equal(guest.isHost,true);
  assert.equal(expired(room,AGE_MS+1000),true);
  guest.connected = false; guest.lastSeenMs = 47000;
  removeMember(room,guest.memberId,47000);
  assert.equal(expired(room,47000+EMPTY_MS-1),false);
  assert.equal(expired(room,47000+EMPTY_MS),true);
  assert.equal(room.members.includes(host),false);
});
