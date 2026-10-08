import { test } from 'node:test';
import assert from 'node:assert/strict';
import { landing } from '../src/website.js';

const origin = 'https://jam.sh1vvy.com';
const room = {
  code: 'ABC123', hostName: 'Shivvy', memberCount: 2, maxMembers: 5,
  isFull: false, members: [{ displayName: 'Shivvy', isHost: true }],
};
const unescape = value => value.replace(/&(?:amp|lt|gt|quot|#39);/g, entity => ({
  '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&#39;': "'",
})[entity]);
const hrefs = html => [...html.matchAll(/\bhref="([^"]*)"/g)].map(match => unescape(match[1]));

test('homepage has a server-rendered code form and real Android installation guidance', async () => {
  const response = landing(null, origin);
  const html = await response.text();
  assert.equal(response.status, 200);
  assert.match(html, /<form\b[^>]*\baction="\/join"/);
  assert.match(html, /<form\b[^>]*\bmethod="get"/i);
  assert.match(html, /<input\b[^>]*\bname="code"/);
  assert.match(html, /<input\b[^>]*\bmaxlength="64"/);
  assert.match(html, /<label\b[^>]*\bfor="[^"]+"/);
  assert.match(html, /Settings → Listen together/);
  assert.ok(hrefs(html).some(href => href.startsWith('https://github.com/sh1vvy/daylight')));
  assert.ok(!hrefs(html).some(href => href.includes('/releases/latest')));
  assert.equal(hrefs(html).filter(href => href.startsWith('daylight://')).length, 0);
});

test('active invite preserves the server and code through both Android links and install fallback', async () => {
  const html = await landing(room, origin).text();
  const links = hrefs(html);
  const deep = `daylight://party/${room.code}?server=${encodeURIComponent(origin)}`;
  const intent = links.find(href => href.startsWith('intent://'));
  assert.ok(links.includes(deep));
  assert.ok(intent?.startsWith(`intent://party/${room.code}?server=${encodeURIComponent(origin)}#Intent;scheme=daylight;`));
  const fallback = /;S\.browser_fallback_url=([^;]+);/.exec(intent)?.[1];
  assert.equal(decodeURIComponent(fallback), `${origin}/invite/${room.code}#install-help`);
  assert.match(html, /id="install-help"/);
  assert.match(html, /Open in Daylight/);
  assert.match(html, /data-copy-code(?:="ABC123")?/);
  assert.ok(html.includes(room.hostName));
});

test('full rooms clearly report capacity while keeping links for returning members', async () => {
  const html = await landing({ ...room, memberCount: 5, isFull: true }, origin).text();
  assert.match(html, /This Jam is full/);
  assert.match(html, /Open in Daylight/);
  assert.ok(hrefs(html).some(href => href.startsWith('intent://party/ABC123')));
});

test('closed and invalid invites keep their HTTP status and never offer a dead app link', async () => {
  for (const invite of [{ code: 'ABC123', expired: true }, { expired: true, invalid: true }]) {
    const response = landing(invite, origin, 404);
    const html = await response.text();
    assert.equal(response.status, 404);
    assert.match(response.headers.get('Content-Type'), /^text\/html/);
    assert.match(html, /Daylight/);
    assert.ok(!hrefs(html).some(href => /^(?:intent|daylight):/.test(href)));
  }
});

test('room display names and invalid form input cannot inject HTML or attributes', async () => {
  const hostile = `"><img src=x onerror=alert(1)><script>alert('x')</script>&`;
  const invite = await landing({ ...room, hostName: hostile }, origin).text();
  assert.ok(invite.includes('&lt;img'));
  assert.ok(!invite.includes(hostile));
  assert.ok(!invite.includes('<img src=x'));
  assert.ok(!invite.includes("<script>alert('x')</script>"));
  const form = await landing(null, origin, 422, {
    error: 'Enter the six-character code from your invite.', input: hostile,
  }).text();
  assert.ok(!form.includes(hostile));
  assert.ok(!form.includes('<img src=x'));
  assert.match(form, /Enter the six-character code from your invite\./);
  assert.match(form, /aria-invalid="true"/);
});

test('invite HTML exposes preview information without leaking membership credentials', async () => {
  const html = await landing({
    ...room,
    token: 'private-bearer-token-for-test',
    userId: 'private-account-id-for-test',
    deviceId: 'private-device-id-for-test',
    members: [{ displayName: 'Shivvy', token: 'private-member-token-for-test', userId: 'private-member-id-for-test' }],
  }, origin).text();
  assert.ok(!html.includes('private-'));
});

test('HTML uses same-origin assets without allowing inline scripts, remote requests or embedding', async () => {
  const response = landing(null, origin);
  const html = await response.text();
  const directives = new Map(response.headers.get('Content-Security-Policy').split(';').map(part => {
    const [name, ...values] = part.trim().split(/\s+/);
    return [name, values];
  }));
  for (const name of ['default-src', 'connect-src', 'frame-ancestors', 'base-uri']) {
    assert.deepEqual(directives.get(name), ["'none'"]);
  }
  for (const name of ['style-src', 'font-src', 'script-src', 'img-src', 'form-action']) {
    assert.deepEqual(directives.get(name), ["'self'"]);
  }
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
  assert.equal(response.headers.get('X-Content-Type-Options'), 'nosniff');
  assert.match(html, /\bhref="\/assets\/jam-v1\.css"/);
  assert.match(html, /<script\b[^>]*\bsrc="\/assets\/jam-v1\.js"[^>]*>\s*<\/script>/);
  assert.match(html, /\bhref="\/assets\/favicon\.svg"/);
  assert.ok(!/<style\b|\bstyle=|\bon(?:click|load|error)=/i.test(html));
  for (const match of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/g)) {
    assert.match(match[1], /\bsrc="\/assets\//);
    assert.equal(match[2].trim(), '');
  }
});
