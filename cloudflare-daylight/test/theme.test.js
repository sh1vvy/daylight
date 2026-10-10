import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const html = await readFile(new URL('../public/index.html', import.meta.url), 'utf8');
const scriptPath = html.match(/<script\b[^>]*\bsrc="([^"]+)"/)?.[1];
assert.ok(scriptPath?.startsWith('/assets/'), 'Use the local script advertised by the page.');
const source = await readFile(new URL(`../public${scriptPath}`, import.meta.url), 'utf8');

function setup({ missingScreen = false, missingMetadata = false } = {}) {
  const buttons = ['light', 'dark', 'pink'].map(theme => ({
    dataset: { theme },
    pressed: String(theme === 'dark'),
    setAttribute(name, value) {
      assert.equal(name, 'aria-pressed');
      this.pressed = value;
    },
  }));
  const listeners = {};
  const controls = {
    hidden: true,
    addEventListener(name, callback) { listeners[name] = callback; },
    contains(button) { return buttons.includes(button); },
    querySelectorAll(selector) { assert.equal(selector, 'button'); return buttons; },
  };
  const screen = { src: '/assets/home-dev8.webp', alt: 'Daylight Home in the Dark theme' };
  const preview = { dataset: { mood: 'dark' } };
  const status = { textContent: 'Dark' };
  const root = { dataset: { siteTheme: 'dark' } };
  const browserTheme = { content: '#19161d' };
  const elements = new Map([
    ['.theme-controls', controls], ['.theme-preview', preview],
    ['#theme-screen', missingScreen ? null : screen], ['#theme-status', status],
    ['meta[name="theme-color"]', missingMetadata ? null : browserTheme],
  ]);
  vm.runInNewContext(source, { document: {
    documentElement: root,
    querySelector(selector) { return elements.get(selector) ?? null; },
  } });
  const click = button => listeners.click({ target: { closest(selector) {
    assert.equal(selector, 'button[data-theme]');
    return button;
  } } });
  return { buttons, controls, screen, preview, status, root, browserTheme, listeners, click };
}

test('each theme updates the entire site, browser color and screenshot with one selected control', () => {
  const state = setup();
  const originalScreen = state.screen;
  assert.equal(state.controls.hidden, false);
  assert.match(html, /<html\b[^>]*data-site-theme="dark"/);
  assert.match(html, /data-theme="dark" aria-pressed="true"/);
  assert.match(html, /id="theme-screen" src="\/assets\/home-dev8.webp"/);
  assert.equal(state.root.dataset.siteTheme, 'dark');
  assert.equal(state.browserTheme.content, '#19161d');
  for (const [key, label, color] of [['dark', 'Dark', '#19161d'], ['pink', 'Pink Clouding', '#fff2f7'], ['light', 'Light', '#faf7f2']]) {
    const selected = state.buttons.find(button => button.dataset.theme === key);
    state.click(selected);
    assert.equal(state.screen, originalScreen, 'The preview should use one persistent image.');
    assert.equal(state.screen.src, key === 'dark' ? '/assets/home-dev8.webp' : `/assets/theme-${key}-dev8.webp`);
    assert.equal(state.screen.alt, `Daylight Home in the ${label} theme`);
    assert.equal(state.preview.dataset.mood, key);
    assert.equal(state.status.textContent, label);
    assert.equal(state.root.dataset.siteTheme, key);
    assert.equal(state.browserTheme.content, color);
    assert.deepEqual(state.buttons.filter(button => button.pressed === 'true'), [selected]);
    assert.ok(state.buttons.filter(button => button !== selected).every(button => button.pressed === 'false'));
  }
});

test('unrelated, unsupported and outside clicks leave the selected theme intact', () => {
  const state = setup();
  state.click(state.buttons.find(button => button.dataset.theme === 'dark'));
  const unchanged = { ...state.screen };
  state.click(null);
  state.click({ dataset: { theme: 'dark' } });
  state.buttons[0].dataset.theme = 'unsupported';
  state.click(state.buttons[0]);
  assert.deepEqual(state.screen, unchanged);
  assert.equal(state.preview.dataset.mood, 'dark');
  assert.equal(state.status.textContent, 'Dark');
  assert.equal(state.root.dataset.siteTheme, 'dark');
  assert.equal(state.browserTheme.content, '#19161d');
  assert.equal(state.buttons.filter(button => button.pressed === 'true').length, 1);
});

test('theme controls stay hidden when the screenshot is unavailable', () => {
  const state = setup({ missingScreen: true });
  assert.equal(state.controls.hidden, true);
  assert.equal(state.listeners.click, undefined);
  assert.equal(state.root.dataset.siteTheme, 'dark');
  assert.equal(state.browserTheme.content, '#19161d');
});

test('theme switching remains usable if browser color metadata is unavailable', () => {
  const state = setup({ missingMetadata: true });
  state.click(state.buttons.find(button => button.dataset.theme === 'pink'));
  assert.equal(state.root.dataset.siteTheme, 'pink');
  assert.equal(state.screen.src, '/assets/theme-pink-dev8.webp');
  assert.equal(state.preview.dataset.mood, 'pink');
});
