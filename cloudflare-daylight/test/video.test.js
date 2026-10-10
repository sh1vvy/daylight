import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const html = await readFile(new URL('../public/index.html', import.meta.url), 'utf8');
const scriptPath = html.match(/<script\b[^>]*\bsrc="([^"]+)"/)?.[1];
assert.ok(scriptPath?.startsWith('/assets/'));
const source = await readFile(new URL(`../public${scriptPath}`, import.meta.url), 'utf8');

function setup({ noObserver = false, reduced = false, denial = false, throws = false, asyncPauses = false } = {}) {
  const observers = [];
  const preferenceListeners = new Set();
  const documentListeners = new Map();
  const pauseEvents = [];
  const preference = {
    matches: reduced,
    addEventListener(name, callback) { assert.equal(name, 'change'); preferenceListeners.add(callback); },
    removeEventListener(name, callback) { assert.equal(name, 'change'); preferenceListeners.delete(callback); },
  };
  const videos = Array.from({ length: [...html.matchAll(/\bdata-preview-video\b/g)].length }, () => {
    const listeners = new Map();
    const video = {
      paused: true, muted: false, playCalls: 0, pauseCalls: 0, controls: true, preload: 'none',
      addEventListener(name, callback) { listeners.set(name, callback); },
      play() {
        this.playCalls += 1;
        if (throws) throw new Error('Playback blocked');
        if (!denial) { this.paused = false; listeners.get('play')?.(); }
        return { catch(callback) { if (denial) callback(new Error('Autoplay denied')); } };
      },
      pause() {
        this.pauseCalls += 1;
        this.paused = true;
        if (asyncPauses) pauseEvents.push(() => listeners.get('pause')?.());
        else listeners.get('pause')?.();
      },
      userPause() {
        this.paused = true;
        if (asyncPauses) pauseEvents.push(() => listeners.get('pause')?.());
        else listeners.get('pause')?.();
      },
      userPlay() { this.paused = false; listeners.get('play')?.(); },
    };
    return video;
  });
  const document = {
    hidden: false,
    documentElement: { classList: { add() {}, remove() {} } },
    querySelector() { return null; },
    querySelectorAll(selector) {
      if (selector === '[data-reveal]') return [];
      assert.equal(selector, '[data-preview-video]');
      return videos;
    },
    addEventListener(name, callback) { documentListeners.set(name, callback); },
  };
  const globals = {
    document,
    matchMedia(query) { assert.equal(query, '(prefers-reduced-motion: reduce)'); return preference; },
  };
  if (!noObserver) globals.IntersectionObserver = class {
    constructor(callback, options) { this.callback = callback; this.threshold = [...options.threshold]; this.targets = []; observers.push(this); }
    observe(target) { this.targets.push(target); }
  };
  vm.runInNewContext(source, globals);
  const enter = (index, ratio = 0.6) => observers[0].callback([{ target: videos[index], isIntersecting: true, intersectionRatio: ratio }]);
  const leave = index => observers[0].callback([{ target: videos[index], isIntersecting: false, intersectionRatio: 0 }]);
  return {
    videos, observers, document, enter, leave,
    flushPauseEvents() { for (const callback of pauseEvents.splice(0)) callback(); },
    setHidden(hidden) { document.hidden = hidden; documentListeners.get('visibilitychange')?.(); },
    setReduced(matches) { preference.matches = matches; for (const callback of preferenceListeners) callback({ matches }); },
  };
}

test('one media observer defers loading and starts muted playback only in its visible section', () => {
  const state = setup();
  assert.equal(state.observers.length, 1);
  assert.deepEqual(state.observers[0].threshold, [0, 0.35]);
  assert.deepEqual(state.observers[0].targets, state.videos);
  assert.ok(state.videos.every(video => video.playCalls === 0 && video.preload === 'none'));
  state.enter(0, 0.2);
  assert.equal(state.videos[0].playCalls, 0);
  state.enter(0);
  assert.equal(state.videos[0].playCalls, 1);
  assert.equal(state.videos[0].muted, true);
  assert.equal(state.videos[1].playCalls, 0);
  state.leave(0);
  assert.equal(state.videos[0].paused, true);
});

test('overlapping previews keep one active and scrolling switches playback without restarting the first', () => {
  const state = setup();
  state.enter(0);
  state.enter(1);
  assert.equal(state.videos[0].playCalls, 1);
  assert.equal(state.videos[1].playCalls, 0);
  state.leave(0);
  assert.equal(state.videos[0].paused, true);
  assert.equal(state.videos[1].paused, false);
  assert.equal(state.videos[1].playCalls, 1);
  state.videos[0].userPlay();
  assert.equal(state.videos[1].paused, true, 'Native manual playback also keeps only one preview active.');
});

test('native user pause is respected until the preview leaves and re-enters', () => {
  const state = setup();
  state.enter(0);
  state.videos[0].userPause();
  state.enter(0, 0.8);
  state.setHidden(true);
  state.setHidden(false);
  assert.equal(state.videos[0].playCalls, 1);
  assert.equal(state.videos[0].paused, true);
  state.leave(0);
  state.enter(0);
  assert.equal(state.videos[0].playCalls, 2);
});

test('hidden tabs pause previews and visible tabs resume only an eligible section', () => {
  const state = setup();
  state.enter(0);
  state.setHidden(true);
  assert.ok(state.videos.every(video => video.paused));
  state.enter(1);
  assert.equal(state.videos[1].playCalls, 0);
  state.leave(0);
  state.setHidden(false);
  assert.equal(state.videos[1].playCalls, 1);
  assert.equal(state.videos[0].playCalls, 1);
});

test('delayed programmatic pause events do not misclassify a resumed preview as user-paused', () => {
  const state = setup({ asyncPauses: true });
  state.enter(0);
  state.setHidden(true);
  state.setHidden(false);
  state.flushPauseEvents();
  assert.equal(state.videos[0].paused, false);
  assert.equal(state.videos[0].playCalls, 2);
  state.enter(1);
  assert.equal(state.videos[1].playCalls, 0, 'The resumed preview stays active after the delayed event.');
  state.videos[0].userPause();
  state.flushPauseEvents();
  state.leave(1);
  state.enter(0, 0.8);
  assert.equal(state.videos[0].playCalls, 2, 'A subsequent native user pause is still respected.');
});

test('reduced motion prevents automatic playback while allowing native controls', () => {
  const state = setup({ reduced: true });
  state.enter(0);
  assert.equal(state.videos[0].playCalls, 0);
  state.videos[0].userPlay();
  assert.equal(state.videos[0].paused, false);
  state.leave(0);
  assert.equal(state.videos[0].paused, true);
  state.enter(1);
  state.setReduced(false);
  assert.equal(state.videos[1].playCalls, 1);
  state.setReduced(true);
  assert.equal(state.videos[1].paused, true);
  state.enter(1);
  assert.equal(state.videos[1].playCalls, 1);
});

test('blocked autoplay is handled silently and native controls stay usable', () => {
  for (const options of [{ denial: true }, { throws: true }]) {
    const state = setup(options);
    state.enter(0);
    state.enter(0, 0.8);
    assert.equal(state.videos[0].playCalls, 1, 'A blocked attempt is not retried repeatedly in the same viewport.');
    assert.equal(state.videos[0].paused, true);
    assert.equal(state.videos[0].controls, true);
    state.videos[0].userPlay();
    assert.equal(state.videos[0].paused, false);
  }
});

test('unsupported observation leaves playback entirely to native controls', () => {
  const state = setup({ noObserver: true });
  assert.equal(state.observers.length, 0);
  assert.ok(state.videos.every(video => video.playCalls === 0 && video.controls));
  state.videos[0].userPlay();
  assert.equal(state.videos[0].paused, false);
});
