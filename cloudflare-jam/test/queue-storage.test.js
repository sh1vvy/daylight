import test from 'node:test';
import assert from 'node:assert/strict';
import {QueueStorage} from '../src/queue-storage.js';
import {createRoom,identity,join,control} from '../src/party.js';
class MemoryStorage {
  constructor() { this.data = new Map(); this.writes=[]; this.fail=false; }
  async get(key) { return Array.isArray(key) ? new Map(key.map(k=>[k,structuredClone(this.data.get(k))])) : structuredClone(this.data.get(key)); }
  async put(values) { if (this.fail) throw new Error('write failed'); for (const [k,v] of Object.entries(values)) { this.data.set(k,structuredClone(v)); this.writes.push(k); } }
  async delete(keys) { for (const k of keys) this.data.delete(k); }
  async transaction(fn) { const before=structuredClone(this.data); try { return await fn(this); } catch(e) { this.data=before; throw e; } }
}
function fixture() { const room=createRoom('ABC123',identity({userId:'qa',deviceId:'qa',displayName:'QA'})); return {room,host:join(room,identity({userId:'qa',deviceId:'qa',displayName:'QA'}))}; }
test('large queues restore whole tracks across eviction, with bounded storage values',async()=>{
  const {room,host}=fixture(), storage=new MemoryStorage(), cache=new QueueStorage(storage);
  const songs=Array.from({length:2101},(_,i)=>({videoId:'id-'+i,title:'語'.repeat(300),artist:'語'.repeat(300),thumbnailUrl:'https://example.com/'+ '語'.repeat(980)}));
  control(room,host,{action:'setQueue',queue:songs,queueIndex:100});
  await cache.write(room);
  assert.equal(storage.data.get('room').playback.items,undefined);
  assert.ok([...storage.data.values()].every(v=>Buffer.byteLength(JSON.stringify(v))<2*1024*1024));
  assert.deepEqual((await new QueueStorage(storage).read()).playback.items,room.playback.items);
  const writes=storage.writes.length;
  control(room,host,{action:'pause'}); await cache.write(room);
  assert.deepEqual(storage.writes.slice(writes),['room']);
  control(room,host,{action:'queueClear'}); await cache.write(room);
  assert.equal([...storage.data.keys()].filter(k=>k.startsWith('queue:')).length,2);
  assert.equal((await new QueueStorage(storage).read()).playback.items.length,101);
});
test('legacy queues migrate without loss and failed saves can be retried',async()=>{
  const {room,host}=fixture(), storage=new MemoryStorage();
  control(room,host,{action:'setQueue',queue:[{videoId:'legacy'}],queueIndex:0});
  storage.data.set('room',structuredClone(room));
  const cache=new QueueStorage(storage), restored=await cache.read();
  assert.equal(restored.playback.items[0].videoId,'legacy');
  storage.fail=true; await assert.rejects(cache.write(restored),/write failed/);
  storage.fail=false; await cache.write(restored);
  assert.equal((await new QueueStorage(storage).read()).playback.items[0].videoId,'legacy');
  storage.data.delete('queue:0');
  await assert.rejects(new QueueStorage(storage).read(),/Missing queue chunk/);
});
test('production capacity and bounded previous history preserve current occurrence',()=>{
  const {room,host}=fixture();
  const songs=Array.from({length:4000},(_,i)=>({videoId:'track-'+i}));
  control(room,host,{action:'setQueue',queue:songs,queueIndex:1500});
  assert.equal(room.playback.items.length,2101); assert.equal(room.playback.queueIndex,100);
  control(room,host,{action:'setTrack',track:songs[1500],queueIndex:100});
  control(room,host,{action:'next'});
  assert.equal(room.playback.queueIndex,100); assert.equal(room.playback.track.videoId,'track-1501');
  assert.equal(room.playback.items.length,2100);
});
