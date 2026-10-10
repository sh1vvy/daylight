# Daylight website

The Android app landing page at **https://daylight.sh1vvy.com**. The site starts
in Dark; Light and Pink Clouding change the entire page and its screenshot preview.

## Hosting

This is an assets-only Cloudflare Worker named `daylight-landing`. There are no
runtime bindings, credentials, analytics or application data. The separate
`daylight-jam` Worker is not affected by website deployments.

Use the repository's Wrangler installation in `cloudflare-jam`:

```sh
cd cloudflare-daylight
npm run dev
npm test
npm run check
npm run deploy
```

If dependencies are missing, install them first with `npm ci` in
`cloudflare-jam`. Preview runs at http://localhost:8789. Deployment uses the
existing Cloudflare account and custom domain in `wrangler.jsonc`.

## Content and assets

- `public/index.html`: feature chapters, downloads, screenshots and FAQ.
- `public/assets/daylight-modern-*.css`: responsive layout and three theme palettes.
- `public/assets/daylight-v5-*.js`: theme switching, scroll reveals and preview playback.
- `public/_headers`: self-hosted resources, restrictive CSP and bounded caching.
- `public/404.html`, `robots.txt`, `sitemap.xml`: routing and discoverability.

CSS and JavaScript filenames include a content hash. Recalculate it after editing
and update the HTML references; the 404 page shares the stylesheet.

The supplied `daylight_banner.png` is used unchanged in the download section.
Screenshots are actual Dev.8 captures from `docs/screenshots`, including Home,
Now playing, Library, Discover, player controls, Light and Pink Clouding.

The current Automix recording is a silent 540×1170, 30 fps H.264 MP4 with a WebP
poster. Liquid Glass uses a silent 960×480 crop of the Dev.8 controls recording.
The supplied synced-lyrics recording retains its portrait proportions. All video
copies use fast-start metadata and `preload="none"`. Source recordings remain
unchanged. Each preview starts in view and pauses offscreen; only one plays at a
time. Manual pause, hidden tabs and reduced-motion preferences are respected.

Primary downloads remain Stable 0.2.1. Dev 0.2.2-dev.9 is secondary. Features shown
are identified as development-build features. Lossless uses a separate community
source, Spotify supplies playlist metadata, and Jams use YouTube Music audio.
The site does not claim that YouTube streams are lossless.

Content and downloads work without JavaScript. Reveals enhance supported browsers
and stop observing content after it appears. Entrance animations are finite;
there is no scroll event loop or animation framework. Inter and the credited
butterfly artwork are self-hosted; attribution is available in the footer.

## Validation

Run the tests before deploying. They cover assets, links, metadata, headers, 404s,
accessible navigation, global themes, progressive motion and in-view playback.
Check desktop and narrow-phone layouts in a browser, including theme selection,
native video controls, FAQ disclosures and keyboard scrolling of the gallery.
