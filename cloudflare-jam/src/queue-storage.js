// SQLite Durable Object KV values are bounded. Keep whole tracks in small chunks,
// and avoid rewriting the catalogue for heartbeat, play/pause and seek updates.
const CHUNK_SIZE = 64;
const key = index => `queue:${index}`;
export class QueueStorage {
  constructor(storage) { this.storage = storage; this.count = 0; this.seq = null; this.identity = null; }
  async read() {
    const room = await this.storage.get('room');
    if (!room) return null;
    this.count = room.queueChunkCount ?? 0;
    if (room.queueChunkCount != null) {
      if (!Number.isInteger(this.count) || this.count < 0 || this.count > 34) throw new Error('Invalid queue chunk count');
      const keys = Array.from({length:this.count},(_,i)=>key(i));
      const values = keys.length ? await this.storage.get(keys) : new Map();
      room.playback.items = keys.flatMap(k => {
        const tracks = values.get(k);
        if (!Array.isArray(tracks)) throw new Error('Missing queue chunk');
        return tracks;
      });
      delete room.queueChunkCount;
      this.seq = room.playback.queueSeq;
      this.identity = `${room.code}:${room.createdAtMs}`;
    }
    // Legacy inline queues migrate on their next write.
    return room;
  }
  async write(room) {
    const identity = `${room.code}:${room.createdAtMs}`;
    const changed = this.identity !== identity || this.seq !== room.playback.queueSeq;
    const items = room.playback.items;
    const count = changed ? Math.ceil(items.length / CHUNK_SIZE) : this.count;
    const {items:_,...playback} = room.playback;
    const header = {...room,playback,queueChunkCount:count};
    await this.storage.transaction(async tx => {
      const writes = {room:header};
      if (changed) {
        for (let i=0;i<count;i++) writes[key(i)] = items.slice(i*CHUNK_SIZE,(i+1)*CHUNK_SIZE);
        const stale = Array.from({length:Math.max(0,this.count-count)},(_,i)=>key(count+i));
        if (stale.length) await tx.delete(stale);
      }
      await tx.put(writes);
    });
    this.count = count; this.seq = room.playback.queueSeq; this.identity = identity;
  }
}
