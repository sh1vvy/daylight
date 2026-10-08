# Daylight Jam protocol

The Android client and Cloudflare service use the same camelCase JSON contract.
Room behavior and deployment are documented in the [service guide](../cloudflare-jam/README.md).
The implementation is in [party.js](../cloudflare-jam/src/party.js) and
[worker.js](../cloudflare-jam/src/worker.js); the original server remains in Git history.

## Timing

Playback carries `positionMs`, `anchorMs`, `isPlaying` and an increasing `seq`.
The client measures its clock offset with `GET /api/time` and WebSocket
`ping`/`pong`, favoring the sample with the smallest round trip.

```text
serverNow = deviceNow + clockOffset
position = positionMs + max(0, serverNow - anchorMs)  // playing
position = positionMs                               // paused
```

Playing anchors are scheduled 350 ms ahead. A delayed packet still identifies
the same playhead rather than asking each listener to start on arrival. State
heartbeats run every five seconds. Clients ignore stale playback sequences;
reports describe a listener's actual position without replacing server state.

## HTTP

| Route | Contract |
| --- | --- |
| `GET /healthz` | Service health. |
| `GET /api/time` | Clock sample: `{serverMs}`. |
| `POST /api/parties` | Create and join as host; identity has `userId`, `deviceId`, `displayName`, optional `avatarUrl` and `maxMembers`. Returns `{code, token, you, party}`. |
| `POST /api/parties/{code}/join` | Join with the same identity; returns the membership token and snapshot. |
| `GET /api/parties/{code}` | Read the authenticated snapshot. |
| `POST /api/parties/{code}/leave` | Release the authenticated membership. |

Authenticated calls and the WebSocket handshake require `Authorization: Bearer
<token>`. Tokens are never accepted in invitation or query URLs. Codes use six
characters; normalization ignores spacing and maps O/I/L to 0/1/1.

## WebSocket

Connect to `/ws/parties/{code}`. Client messages include:

| Type | Fields / purpose |
| --- | --- |
| `ping` | `clientMs`; server echoes it with `serverMs`. |
| `sync` | Request playback state. |
| `syncQueue` | Request the complete queue. |
| `report` | Actual `positionMs` and `isPlaying`. |
| `control` | `action` and its arguments below. |

Controls include `play`, `pause`, `seek`, `setTrack`, `setQueue`, `queueAdd`,
`queueRemove`, `queueClear`, `queueMove`, `next`, `previous` and `setAutoplay`.
`setMaxMembers`, `setHostOnlyControl` and `kick` always require the host.
Enabling `hostOnlyControl` also restricts playback and queue controls at the
server; the flag is carried in snapshots and membership messages.

A track carries `videoId`, `title`, `artist`, `thumbnailUrl`, `durationMs` and
`fromAutoplay`. Audio URLs, playback quality and downloads stay on each device.
`startedBy` and `startedByName` identify who selected a track; `updatedBy`
identifies the latest playback controller.

Server messages are `welcome` (`you`, `party`), `pong` (`clientMs`), `state`
(`playback`), `queue` (`queue`), `members` (`members`, `maxMembers`,
`hostOnlyControl`), `error` (`error`, `message`) and `bye` (`reason`). Timing
messages include `serverMs`. Departure reasons distinguish `left` and `kicked`.

## Queue synchronization

State messages carry `queueSeq`, `queueIndex` and `queueLength`, not the song
list. Snapshots contain `party.queue`; separate queue messages carry
`{seq, index, items}`. A queue change broadcasts the queue before playback state.
If a client's stored queue sequence differs from the playback message's
`queueSeq`, it requests `syncQueue` to repair a missed update. Controls are
broadcast back to the sender so all devices apply the same authoritative state.

Membership survives a dropped socket for 45 seconds. Rejoining rotates the
token and reclaims the device's slot; a departing host hands ownership to a
remaining member. Current capacity, expiry and rate limits are in the service
guide and its local tests.
