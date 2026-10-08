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

## Jam website

`src/website.js` renders the homepage, active/full invitations and unavailable
invites. The code form uses `GET /join?code=CODE`, validates and normalizes the
existing six-character alphabet, then redirects to the invite preview. It works
without JavaScript. Creating a party and playing music remain in the Android app.

The homepage and invitation install help offer a direct Android APK download from
`https://github.com/sh1vvy/daylight/releases/latest/download/daylight.apk`, without
a GitHub sign-in. Keep the release asset name `daylight.apk` when publishing future
releases, and update the displayed version in `src/website.js` at the same time.
The dark download badge is specific to the APK and does not imply a Play Store
listing. Invitations keep “Open in Daylight” as their main action.
Install help also links `daylight-dev.apk` for people who already have Daylight
Dev installed; they can update that package without installing a second app.

`public/assets` contains the lightweight stylesheet, optional clipboard/install
helpers, flat Daylight butterfly icons and self-hosted Inter fonts. Static assets
are served directly by Cloudflare; dynamic HTML is never cached, so room previews
stay current. There are no remote font/icon services, analytics or animation loops.
Credits for BitChord, the icon artist and Inter are available in the footer.

CSS, JavaScript and fonts use versioned filenames and long-lived immutable caches.
When changing these files after deployment, use a new filename/version and update
the references and `public/_headers` together. Unversioned SVGs and the font license
have a one-hour cache. The existing API, WebSockets, Durable Objects and Android
app-link registration keep their original routes and behavior.

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
The public APK uses `com.sh1vvy.daylight`; its production signing certificate is
also registered in `src/asset-links.js`. Keep these fingerprints in sync with
the keys used for future releases. `ASSET_LINKS`
can override the JSON array as a deployment variable. Never upload private keys.

## Source and licensing

`src/party.js` and `src/worker.js` adapt the original Go party server's room,
HTTP and WebSocket protocol. That source is preserved in the
[upstream baseline](https://github.com/sh1vvy/daylight/tree/2c599a6cd7a195227a11dab5769f88da8fd08194/backend);
the checkout keeps only the deployed Cloudflare implementation. The current
[Jam protocol](../docs/JAM_PROTOCOL.md) documents the client/server contract.
Original authors and licenses are retained in [UPSTREAM.md](../UPSTREAM.md)
and [LICENSE](../LICENSE).
The Cloudflare adaptation is part of Daylight and distributed under GPLv3.
