const escape = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const appUrl = 'https://github.com/sh1vvy/daylight';
const downloadUrl = `${appUrl}/releases/latest/download/daylight.apk`;
const devDownloadUrl = `${appUrl}/releases/latest/download/daylight-dev.apk`;
const releaseUrl = `${appUrl}/releases/latest`;
const appVersion = '0.2.0';
const icons = {
  arrow: '<path d="M5 12h14m-6-6 6 6-6 6"/>',
  download: '<path d="M12 3v12m-5-5 5 5 5-5M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4"/>',
  external: '<path d="M14 4h6v6m0-6-9 9"/><path d="M10 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4"/>',
  phone: '<rect x="6" y="2" width="12" height="20" rx="3"/><path d="M10 18h4"/>',
  headphones: '<path d="M4 14v-3a8 8 0 0 1 16 0v3"/><rect x="3" y="12" width="4" height="8" rx="2"/><rect x="17" y="12" width="4" height="8" rx="2"/>',
  link: '<path d="m10 14 4-4m-5 6-2 2a4.2 4.2 0 0 1-6-6l4-4a4.2 4.2 0 0 1 6 0m2 0 2-2a4.2 4.2 0 0 1 6 6l-4 4a4.2 4.2 0 0 1-6 0"/>',
  copy: '<rect x="8" y="8" width="12" height="12" rx="3"/><path d="M15 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h3"/>',
  heart: '<path d="M20 5a5 5 0 0 0-7 0l-1 1-1-1a5 5 0 0 0-7 7l8 8 8-8a5 5 0 0 0 0-7Z"/>',
  music: '<path d="M9 18V5l12-3v13M9 9l12-3"/><ellipse cx="6" cy="18" rx="3" ry="3"/><ellipse cx="18" cy="15" rx="3" ry="3"/>',
};
const icon = name => `<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">${icons[name]}</svg>`;

function downloadBadge() {
  return `<a class="download-badge" href="${downloadUrl}" aria-label="Download Daylight ${appVersion} for Android as an APK">
    <span class="download-icon">${icon('download')}</span><span class="download-copy"><strong>Download Daylight</strong><span>Android · v${appVersion} · APK</span></span>${icon('arrow')}
  </a>`;
}

function codeForm({error, input = ''} = {}) {
  return `<form class="code-form" action="/join" method="get">
    <label for="jam-code">Have an invite code?</label>
    <div class="code-entry${error ? ' has-error' : ''}">
      <input id="jam-code" name="code" type="text" value="${escape(input)}" placeholder="ABC123" maxlength="64" required autocomplete="off" autocapitalize="characters" spellcheck="false" aria-describedby="code-hint${error ? ' code-error' : ''}"${error ? ' aria-invalid="true"' : ''}>
      <button class="button button-small" type="submit">Preview party ${icon('arrow')}</button>
    </div>
    <p class="form-hint" id="code-hint">Six characters. One shared soundtrack.</p>
    ${error ? `<p class="form-error" id="code-error" role="alert">${escape(error)}</p>` : ''}
  </form>`;
}

function illustration() {
  return `<div class="hero-art" aria-hidden="true">
    <div class="art-caption">a little closer, one song at a time</div>
    <div class="sleeve"><span class="sleeve-word">daylight</span><div class="sleeve-sun"></div><div class="sleeve-horizon"></div><span class="sleeve-note">SIDE A &nbsp; / &nbsp; TOGETHER</span></div>
    <div class="record"><div class="record-label"><img src="/assets/daylight-mark.svg" alt="" width="64" height="46"><span>our kind of company</span><i></i></div></div>
    <div class="art-note">${icon('heart')} sounds better with you.</div>
    <span class="spark spark-one">✦</span><span class="spark spark-two">✧</span>
  </div>`;
}

function steps() {
  return `<section class="how-it-works" aria-labelledby="how-title">
    <div class="section-heading"><h2 id="how-title">Good company. Three little steps.</h2><span>No distance too far.</span></div>
    <ol class="steps">
      <li><span class="step-icon peach">${icon('phone')}</span><div><h3>01 &nbsp; Open Daylight</h3><p>Your music, on Android. Have the app ready on everyone’s phone.</p></div></li>
      <li><span class="step-icon lilac">${icon('headphones')}</span><div><h3>02 &nbsp; Start a Jam</h3><p>Go to Settings → Listen together. Sign in, then create a party.</p></div></li>
      <li><span class="step-icon sage">${icon('link')}</span><div><h3>03 &nbsp; Send the invite</h3><p>Share your link or code. Pick a song and settle into the same moment.</p></div></li>
    </ol>
  </section>`;
}

function installHelp() {
  return `<details class="install-help" id="install-help"><summary>New to Daylight, or the app didn’t open?</summary>
    <div class="help-content"><p>Jam lives in the Daylight Android app. Download and install Daylight, then open this invite again. In the app, you can also enter the code in Settings → Listen together.</p>
    ${downloadBadge()}<span class="help-note">Your phone may ask you to allow installs from your browser. <a href="${releaseUrl}">Release notes ${icon('external')}</a></span>
    <span class="help-note">Already using Daylight Dev? <a href="${devDownloadUrl}">Get the matching update ${icon('external')}</a></span></div>
  </details>`;
}

export function landing(room, origin, status = 200, options = {}) {
  const code = room?.code, active = Boolean(code && !room.expired);
  const full = active && room.isFull;
  const inviteUrl = active ? `${origin}/invite/${encodeURIComponent(code)}` : origin;
  const deep = active ? `daylight://party/${encodeURIComponent(code)}?server=${encodeURIComponent(origin)}` : null;
  const intent = active ? `intent://party/${encodeURIComponent(code)}?server=${encodeURIComponent(origin)}#Intent;scheme=daylight;S.browser_fallback_url=${encodeURIComponent(inviteUrl+'#install-help')};end` : null;
  const host = room?.hostName || 'your friends';
  const title = active ? `${full ? 'A full Jam' : 'Join '+code} · Daylight Jam` : room ? 'Catch the next Jam · Daylight' : 'Daylight Jam · A little closer';
  const description = active ? `Listen together with ${host} on Daylight. Open the invite in the Android app.` : 'Different places. Same song. A shared moment with your people, powered by Daylight.';
  const content = active ? `<div class="hero-copy">
    <p class="eyebrow">${icon('headphones')} You’re invited</p><h1>Your people.<br><span>Your playlist.</span></h1>
    <p class="intro">A shared soundtrack with <strong>${escape(host)}</strong>.<br>Come as you are. Bring a song.</p>
    <div class="invite-card">
      <div class="room-heading"><span>${full ? 'This Jam is full' : 'Your invite code'}</span><span class="listener-count">${escape(room.memberCount)} / ${escape(room.maxMembers)} listeners</span></div>
      <div class="room-code"><strong>${escape(code)}</strong><button class="copy-button" type="button" data-copy-code="${escape(code)}" aria-label="Copy invite code ${escape(code)}" hidden>${icon('copy')}<span>Copy</span></button></div>
      <p class="room-note">${full ? 'All spots are taken right now. Already part of this Jam? You can still open it in the app.' : 'Open the invite in Daylight to preview the party and join.'}</p>
      <a class="button app-button" href="${escape(intent)}">${icon('headphones')} Open in Daylight ${icon('arrow')}</a>
      <a class="app-fallback" href="${escape(deep)}">Try the direct app link</a><p class="copy-status" role="status" aria-live="polite"></p>
    </div>${installHelp()}</div>`
  : room ? `<div class="hero-copy">
    <p class="eyebrow">${icon('music')} ${room.invalid ? 'A little mix-up' : 'Until the next song'}</p>
    <h1>${room.invalid ? 'Let’s find<br><span>your people.</span>' : 'Catch the<br><span>next daylight.</span>'}</h1>
    <p class="intro">${room.invalid ? 'That invite doesn’t look quite right. Ask your friend for the six-character code, or try it below.' : 'This Jam has ended, or the invite is no longer available. Ask your friend for a fresh link.'}</p>
    ${codeForm(options)}<a class="text-link" href="/">Back to Daylight Jam ${icon('arrow')}</a></div>`
  : `<div class="hero-copy">
    <p class="eyebrow">${icon('headphones')} Better together</p><h1>Different places.<br><span>Same song.</span></h1>
    <p class="intro">For the songs you send each other.<br>Listen together with your people on Daylight.</p>
    <div class="download-callout">${downloadBadge()}</div>
    ${codeForm(options)}
    <p class="start-hint">Starting one? <a href="#how-title">Here’s how ${icon('arrow')}</a></p>${installHelp()}</div>`;
  return new Response(`<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#faf7f2"><meta name="description" content="${escape(description)}">
<meta property="og:title" content="${escape(title)}"><meta property="og:description" content="${escape(description)}"><meta property="og:type" content="website"><meta property="og:url" content="${escape(inviteUrl)}">
${status !== 200 ? '<meta name="robots" content="noindex">' : ''}<link rel="canonical" href="${escape(inviteUrl)}"><title>${escape(title)}</title>
<link rel="icon" href="/assets/favicon.svg" type="image/svg+xml"><link rel="preload" href="/assets/Inter-Regular-v4.1.woff2" as="font" type="font/woff2" crossorigin>
<link rel="stylesheet" href="/assets/jam-v2.css"><script src="/assets/jam-v1.js" defer></script></head>
<body><a class="skip-link" href="#main">Skip to content</a><div class="page">
<header class="site-header"><a class="brand" href="/" aria-label="Daylight Jam home"><img src="/assets/daylight-mark.svg" width="42" height="31" alt=""><span>daylight</span><span class="brand-divider"></span><span class="brand-jam">jam</span></a>
<a class="app-link" href="${downloadUrl}" aria-label="Download Daylight for Android">${icon('phone')} Get Daylight ${icon('download')}</a></header>
<main id="main"><section class="hero${active ? ' hero-invite' : ''}">${content}${illustration()}</section>${active ? '' : steps()}</main>
<footer class="site-footer"><div><p class="footer-message">A shared moment. A little daylight.</p><p class="byline">Made with care by <a href="https://sh1vvy.com">sh1vvy ${icon('external')}</a></p></div>
<div class="footer-links"><a href="${appUrl}">The app ${icon('external')}</a><details class="credits"><summary>Credits</summary><div><p>Daylight is open source, based on <a href="https://github.com/kushagrasinghx/BitChord">BitChord</a>, under <a href="${appUrl}/blob/main/LICENSE">GPL-3.0</a>.</p><p>© art by 11 (<a href="https://www.instagram.com/_artbyeleven/">_artbyeleven on IG</a>)</p><p>Set in <a href="/assets/font-license.txt">Inter by Rasmus Andersson</a>.</p></div></details></div></footer>
</div></body></html>`, {status, headers:{
    'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer',
    'Content-Security-Policy':"default-src 'none'; style-src 'self'; script-src 'self'; font-src 'self'; img-src 'self'; connect-src 'none'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
  }});
}
