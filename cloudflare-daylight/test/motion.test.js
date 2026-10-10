import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const html = await readFile(new URL('../public/index.html', import.meta.url), 'utf8');
const scriptPath = html.match(/<script\b[^>]*\bsrc="([^"]+)"/)?.[1];
assert.ok(scriptPath?.startsWith('/assets/'));
const source = await readFile(new URL(`../public${scriptPath}`, import.meta.url), 'utf8');

function classList() {
  const values = new Set();
  return {
    add(value) { values.add(value); },
    remove(value) { values.delete(value); },
    contains(value) { return values.has(value); },
  };
}

function setup({ noObserver = false, noPreference = false, reduced = false, legacyPreference = false, brokenObserver = false } = {}) {
  const root = { classList: classList() };
  const targets = Array.from({ length: 3 }, () => ({ classList: classList() }));
  const observers = [];
  const listeners = new Set();
  const preference = { matches: reduced };
  if (legacyPreference) {
    preference.addListener = callback => listeners.add(callback);
    preference.removeListener = callback => listeners.delete(callback);
  } else {
    preference.addEventListener = (name, callback) => {
      assert.equal(name, 'change');
      listeners.add(callback);
    };
    preference.removeEventListener = (name, callback) => {
      assert.equal(name, 'change');
      listeners.delete(callback);
    };
  }
  const globals = {
    document: {
      documentElement: root,
      querySelector() { return null; },
      querySelectorAll(selector) {
        if (selector === '[data-preview-video]') return [];
        assert.equal(selector, '[data-reveal]');
        return targets;
      },
    },
  };
  if (!noPreference) globals.matchMedia = query => {
    assert.equal(query, '(prefers-reduced-motion: reduce)');
    return preference;
  };
  if (!noObserver) globals.IntersectionObserver = class {
    constructor(callback, options) {
      if (brokenObserver) throw new Error('Observer unavailable');
      this.callback = callback;
      this.threshold = options.threshold;
      this.observed = new Set();
      this.unobserved = [];
      this.disconnects = 0;
      observers.push(this);
    }
    observe(target) { this.observed.add(target); }
    unobserve(target) { this.observed.delete(target); this.unobserved.push(target); }
    disconnect() { this.observed.clear(); this.disconnects += 1; }
  };
  vm.runInNewContext(source, globals);
  return {
    root, targets, observers, listeners,
    reduceMotion() {
      preference.matches = true;
      for (const listener of [...listeners]) listener({ matches: true });
    },
  };
}

test('unsupported browsers and initial reduced motion leave readable content untouched', () => {
  for (const options of [{ noObserver: true }, { noPreference: true }, { reduced: true }]) {
    const state = setup(options);
    assert.equal(state.root.classList.contains('motion-ready'), false);
    assert.equal(state.observers.length, 0);
    assert.equal(state.listeners.size, 0);
  }
});

test('one observer reveals content at the viewport threshold and releases every seen target', () => {
  const state = setup();
  assert.equal(state.root.classList.contains('motion-ready'), true);
  assert.equal(state.observers.length, 1);
  const observer = state.observers[0];
  assert.equal(observer.threshold, 0.12);
  assert.equal(observer.observed.size, state.targets.length);
  observer.callback([{ target: state.targets[0], isIntersecting: true, intersectionRatio: 0.05 }]);
  assert.equal(state.targets[0].classList.contains('is-visible'), false);
  observer.callback([{ target: state.targets[0], isIntersecting: false, intersectionRatio: 0.5 }]);
  assert.equal(state.targets[0].classList.contains('is-visible'), false);
  const visible = target => ({ target, isIntersecting: true, intersectionRatio: 0.2 });
  observer.callback([visible(state.targets[0]), visible(state.targets[0])]);
  assert.equal(state.targets[0].classList.contains('is-visible'), true);
  assert.deepEqual(observer.unobserved, [state.targets[0]]);
  observer.callback(state.targets.slice(1).map(visible));
  assert.ok(state.targets.every(target => target.classList.contains('is-visible')));
  assert.equal(observer.disconnects, 1);
  assert.equal(state.listeners.size, 0);
  observer.callback([visible(state.targets[0])]);
  assert.equal(observer.disconnects, 1);
});

test('a live reduced-motion change reveals all pending content and disconnects observation', () => {
  for (const legacyPreference of [false, true]) {
    const state = setup({ legacyPreference });
    state.reduceMotion();
    assert.ok(state.targets.every(target => target.classList.contains('is-visible')));
    assert.equal(state.root.classList.contains('motion-ready'), false);
    assert.equal(state.observers[0].disconnects, 1);
    assert.equal(state.listeners.size, 0);
    state.observers[0].callback([{ target: state.targets[0], isIntersecting: true, intersectionRatio: 0.5 }]);
    assert.equal(state.observers[0].disconnects, 1);
  }
});

test('a failed observer setup cannot leave page content hidden', () => {
  const state = setup({ brokenObserver: true });
  assert.equal(state.root.classList.contains('motion-ready'), false);
  assert.ok(state.targets.every(target => target.classList.contains('is-visible')));
  assert.equal(state.listeners.size, 0);
});
