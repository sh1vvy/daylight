# Daylight Jam on Cloudflare

The Android app defaults to **https://jam.sh1vvy.com**. This service adapts the
original BitChord room protocol to Cloudflare Workers and SQLite-backed Durable
Objects. The Worker, rooms, DNS and HTTPS run on Cloudflare. No computer, VPS,
container, Tunnel or external database needs to stay running.

Each phone streams its own music. The server shares membership, the queue and
playback timing; it does not relay audio or store music files.

## Develop and deploy

Use Node.js 22 or newer. From this directory:

```sh
npm ci
npm test
npm run check
npm run dev
```

The local server runs at http://localhost:8787. To deploy to the account that
owns the Cloudflare zone for `sh1vvy.com`:

```sh
npx wrangler login
npm run deploy
npm run test:live
```

Wrangler registers the `jam.sh1vvy.com` custom domain, DNS and HTTPS certificate.
It does not upgrade the account or purchase a plan. Only deploy with the intended
account selected. OAuth tokens, `.dev.vars`, `.env`, `.wrangler` and `node_modules`
are ignored by Git. The package lock pins the tooling used to verify this port.

`test:live` explicitly creates one temporary room, verifies two WebSocket clients,
then leaves. It never prints bearer tokens. Set `DAYLIGHT_JAM_URL` to test another
HTTPS deployment. The regular tests use a local Workers runtime and make no
production requests.

## Room behavior

- Six-character codes; five listeners by default, host can choose 2–10.
- Shared play/pause/seek, queue, next/previous, AutoPlay and host-only controls.
- Random per-device bearer tokens; rejoining rotates the token. Tokens are
  accepted only in the Authorization header, never invite/query URLs.
- Host handoff when the host leaves; the host can remove another listener.
- 45-second reconnect grace, rooms expire after two minutes without connected
  listeners or after 12 hours. Up to 50 rooms and 25 upcoming tracks per room.
- Two room creations per IP per minute, bounded messages and control rate limits.
- WebSocket hibernation, persisted state and alarms support eviction/restarts.
  State broadcasts retain the upstream five-second interval and 350ms play lead.
- Invitation previews show display names, avatars and occupancy. They do not
  disclose tokens, user/device IDs, the playing song or queue.

## Free-tier capacity

This deployment uses services available on **Workers Free** and does not require
Workers Paid. Keep the account on Free to avoid metered overage billing. Cloudflare
enforces free limits: exceeding a limit causes requests/operations to fail until
the allowance resets, rather than automatically upgrading the account.

As of deployment, Durable Objects include 100,000 requests/day, 13,000 GB-s of
active duration/day, 5 million row reads/day, 100,000 row writes/day and 5 GB of
storage. Workers also have their own free request/CPU limits. Allowances are shared
with other Workers in the account. Outgoing WebSocket messages are free; incoming
messages count at 20:1 for compute requests. Each five-second room alarm counts
as a request and writing the next alarm counts as a row write: one room active
for an hour uses approximately 720 alarms, plus controls and membership changes.
50 concurrent rooms is an application ceiling, not a guarantee that 50 rooms can
run all day within Free allowances. Monitor usage in the Cloudflare dashboard.

See [Cloudflare Durable Objects pricing](https://developers.cloudflare.com/durable-objects/platform/pricing/)
and [Workers limits](https://developers.cloudflare.com/workers/platform/limits/).

## Android invite links

Shared invites use `https://jam.sh1vvy.com/invite/CODE`. The landing page opens
`daylight://party/CODE?server=https%3A%2F%2Fjam.sh1vvy.com`, which also works with
the first Daylight APK after setting the server address manually.

The new APK handles the HTTPS invite directly. `/.well-known/assetlinks.json`
contains the public SHA-256 signing certificate for the development APK built on
sh1vvy's Mac. Other debug keys (including GitHub builds) need their own certificate
entry to verify app links; the landing page's custom scheme remains available.
Before distributing production APKs, add `com.sh1vvy.daylight` and its actual
release signing certificate to `src/asset-links.js`, then redeploy. `ASSET_LINKS`
can override the JSON array as a deployment variable. Never upload private keys.

## Source and licensing

`src/party.js` adapts `../backend/party/party.go`; `src/worker.js` adapts the HTTP
and WebSocket protocol from `../backend/main.go`. The original Go server remains
in `../backend` as a reference/alternative deployment. Original authors and
licenses are retained in [UPSTREAM.md](../UPSTREAM.md) and [LICENSE](../LICENSE).
The Cloudflare adaptation is part of Daylight and distributed under GPLv3.
