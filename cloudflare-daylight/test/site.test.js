import assert from 'node:assert/strict';
import { readFile, readdir, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const root = fileURLToPath(new URL('../public/', import.meta.url));
const origin = 'https://daylight.sh1vvy.com';
const stableApk = 'https://github.com/sh1vvy/daylight/releases/latest/download/daylight.apk';
const html = await readFile(path.join(root, 'index.html'), 'utf8');
const headers = await readFile(path.join(root, '_headers'), 'utf8');
const assetNames = await readdir(path.join(root, 'assets'));

const attributes = (source, name) => [...source.matchAll(new RegExp(`\\b${name}=["']([^"']*)["']`, 'g'))].map(match => match[1]);
const assetPath = reference => path.join(root, decodeURIComponent(reference.split(/[?#]/)[0]).replace(/^\//, ''));

test('every referenced local HTML, stylesheet and SVG asset is shipped', async () => {
  const documents = [html, await readFile(path.join(root, '404.html'), 'utf8')];
  for (const name of assetNames.filter(name => /\.(?:css|svg)$/.test(name))) {
    documents.push(await readFile(path.join(root, 'assets', name), 'utf8'));
  }
  const references = new Set(documents.flatMap(source => [
    ...attributes(source, 'src'), ...attributes(source, 'href'), ...attributes(source, 'poster'),
    ...[...source.matchAll(/url\(["']?([^)'"\s]+)["']?\)/g)].map(match => match[1]),
  ]).filter(reference => reference.startsWith('/assets/')));
  assert.ok(references.size > 10, 'The page should exercise its asset references.');
  for (const reference of references) {
    assert.ok((await stat(assetPath(reference))).isFile(), `Missing ${reference}`);
  }
});

test('primary downloads remain the public stable Android APK', () => {
  const buttons = [...html.matchAll(/<a\b[^>]*class="download-button"[^>]*>/g)].map(match => match[0]);
  assert.ok(buttons.length >= 2, 'A hero and final download call to action are expected.');
  assert.ok(buttons.every(button => attributes(button, 'href')[0] === stableApk));
  const links = attributes(html, 'href');
  assert.ok(links.includes('https://github.com/sh1vvy/daylight/releases/latest'));
  assert.ok(links.includes('https://github.com/sh1vvy/daylight'));
  assert.ok(links.includes('https://jam.sh1vvy.com'));
  assert.ok(links.includes('https://github.com/sh1vvy/daylight/releases/download/v0.2.2-dev.9/daylight-dev.apk'));
  assert.ok(links.every(link => !/canary|localhost|127\.0\.0\.1|\/output\//i.test(link)), 'Internal test builds must not be published.');
});

test('canonical and social metadata advertise the deployed host and a real preview', async () => {
  assert.match(html, /<meta name="description" content="[^"]+">/);
  assert.match(html, /<link rel="canonical" href="https:\/\/daylight\.sh1vvy\.com\/">/);
  const ogUrl = html.match(/<meta property="og:url" content="([^"]+)">/)?.[1];
  const cardUrl = html.match(/<meta property="og:image" content="([^"]+)">/)?.[1];
  assert.equal(ogUrl, `${origin}/`);
  assert.equal(cardUrl, `${origin}/assets/social-card.png`);
  const card = await readFile(assetPath(new URL(cardUrl).pathname));
  assert.deepEqual([...card.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
  assert.equal(card.readUInt32BE(16), 1200);
  assert.equal(card.readUInt32BE(20), 630);
});

test('the page runs only its own static script under restrictive headers', async () => {
  assert.match(headers, /X-Content-Type-Options:\s*nosniff/);
  assert.match(headers, /X-Frame-Options:\s*DENY/);
  const policy = headers.match(/Content-Security-Policy:\s*([^\n]+)/)?.[1];
  assert.ok(policy);
  for (const directive of ["default-src 'none'", "script-src 'self'", "media-src 'self'", "connect-src 'none'", "frame-ancestors 'none'"]) {
    assert.ok(policy.includes(directive), `Missing ${directive}`);
  }
  assert.doesNotMatch(policy, /unsafe-inline|unsafe-eval|https?:/);
  const scripts = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/g)];
  assert.ok(scripts.length > 0);
  for (const script of scripts) {
    const source = attributes(script[1], 'src')[0];
    assert.ok(source?.startsWith('/assets/'), 'Do not add provider scripts to the landing page.');
    assert.equal(script[2].trim(), '', 'Executable code must stay in the local script asset.');
    const code = await readFile(assetPath(source), 'utf8');
    assert.doesNotMatch(code, /\b(?:fetch|XMLHttpRequest|WebSocket)\s*\(|\bsendBeacon\s*\(/);
  }
});

test('unknown paths use a genuine noindex 404 instead of the home page', async () => {
  const config = JSON.parse(await readFile(new URL('../wrangler.jsonc', import.meta.url), 'utf8'));
  assert.equal(config.assets.not_found_handling, '404-page');
  assert.ok(config.routes.some(route => route.custom_domain === true && route.pattern === 'daylight.sh1vvy.com'));
  const notFound = await readFile(path.join(root, '404.html'), 'utf8');
  assert.match(notFound, /<meta name="robots" content="noindex">/);
  assert.ok(attributes(notFound, 'href').includes('/'));
  const robots = await readFile(path.join(root, 'robots.txt'), 'utf8');
  const sitemap = await readFile(path.join(root, 'sitemap.xml'), 'utf8');
  assert.ok(robots.includes(`Sitemap: ${origin}/sitemap.xml`));
  assert.ok(sitemap.includes(`<loc>${origin}/</loc>`));
});

test('vector graphics are self-contained and use no raster or remote dependencies', async () => {
  const svgNames = assetNames.filter(name => name.endsWith('.svg'));
  assert.ok(svgNames.length >= 4);
  for (const name of svgNames) {
    const source = await readFile(path.join(root, 'assets', name), 'utf8');
    assert.doesNotMatch(source, /<(?:script|image|foreignObject)\b/i, name);
    assert.doesNotMatch(source, /@import|data:image|url\(["']?(?:https?:|\/\/)/i, name);
    for (const reference of attributes(source, 'href')) {
      assert.ok(reference.startsWith('#'), `${name} has an external dependency: ${reference}`);
    }
  }
});

test('document navigation and images have accessible targets and alternatives', () => {
  assert.match(html, /<html\b[^>]*\blang="en"[^>]*>/);
  assert.equal([...html.matchAll(/<h1\b/g)].length, 1);
  assert.match(html, /<main\b[^>]*id="main"/);
  assert.match(html, /class="skip-link" href="#main"/);
  const ids = attributes(html, 'id');
  assert.equal(new Set(ids).size, ids.length, 'Duplicate IDs break anchor and label resolution.');
  for (const reference of attributes(html, 'href').filter(reference => reference.startsWith('#'))) {
    assert.ok(ids.includes(reference.slice(1)), `Missing anchor target ${reference}`);
  }
  for (const image of [...html.matchAll(/<img\b[^>]*>/g)].map(match => match[0])) {
    assert.match(image, /\balt="[^"]*"/, 'Every image needs a text alternative or explicit decorative alt.');
  }
});
