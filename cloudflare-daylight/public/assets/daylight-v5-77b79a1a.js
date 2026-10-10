const themeControls = document.querySelector('.theme-controls');
const preview = document.querySelector('.theme-preview');
const screen = document.querySelector('#theme-screen');
const status = document.querySelector('#theme-status');
const browserTheme = document.querySelector('meta[name="theme-color"]');
const themes = {
  light: { label: 'Light', image: '/assets/theme-light-dev8.webp', color: '#faf7f2' },
  dark: { label: 'Dark', image: '/assets/home-dev8.webp', color: '#19161d' },
  pink: { label: 'Pink Clouding', image: '/assets/theme-pink-dev8.webp', color: '#fff2f7' },
};

if (themeControls && preview && screen && status) {
  themeControls.hidden = false;
  themeControls.addEventListener('click', event => {
    const button = event.target.closest('button[data-theme]');
    if (!button || !themeControls.contains(button) || !Object.hasOwn(themes, button.dataset.theme)) return;
    const theme = themes[button.dataset.theme];
    screen.src = theme.image;
    screen.alt = `Daylight Home in the ${theme.label} theme`;
    preview.dataset.mood = button.dataset.theme;
    status.textContent = theme.label;
    document.documentElement.dataset.siteTheme = button.dataset.theme;
    if (browserTheme) browserTheme.content = theme.color;
    for (const choice of themeControls.querySelectorAll('button')) {
      choice.setAttribute('aria-pressed', String(choice === button));
    }
  });
}

// Preview media loads only when its section is visible or its controls are used.
if (typeof IntersectionObserver === 'function' && typeof matchMedia === 'function') {
  const videos = [...document.querySelectorAll('[data-preview-video]')];
  if (videos.length) {
    const preference = matchMedia('(prefers-reduced-motion: reduce)');
    const canWatchPreference = typeof preference.addEventListener === 'function'
      || typeof preference.addListener === 'function';
    const states = new Map(videos.map(video => [video, {
      visible: false, ratio: 0, userPaused: false, attempted: false, pendingPauses: 0,
    }]));
    let activeVideo = null;

    const pause = video => {
      if (video.paused) return;
      states.get(video).pendingPauses += 1;
      video.pause();
    };

    const pauseAll = () => {
      activeVideo = null;
      for (const video of videos) {
        states.get(video).attempted = false;
        pause(video);
      }
    };

    const reconcile = () => {
      if (document.hidden) {
        pauseAll();
        return;
      }
      for (const video of videos) {
        if (!states.get(video).visible) pause(video);
      }
      if (preference.matches) return;
      const eligible = video => {
        const state = states.get(video);
        return state.visible && !state.userPaused && (!state.attempted || !video.paused);
      };
      let chosen = activeVideo && eligible(activeVideo) ? activeVideo : null;
      if (!chosen) {
        chosen = videos.filter(eligible).sort((left, right) => states.get(right).ratio - states.get(left).ratio)[0];
      }
      if (!chosen) return;
      for (const video of videos) if (video !== chosen) pause(video);
      activeVideo = chosen;
      const state = states.get(chosen);
      if (state.attempted || !chosen.paused) return;
      state.attempted = true;
      chosen.muted = true;
      const denied = () => {
        if (activeVideo === chosen && chosen.paused) activeVideo = null;
      };
      try {
        const started = chosen.play();
        if (started && typeof started.catch === 'function') started.catch(denied);
      } catch {
        denied();
      }
    };

    if (canWatchPreference) {
      let observer;
      try {
        observer = new IntersectionObserver(entries => {
          for (const entry of entries) {
            const state = states.get(entry.target);
            if (!state) continue;
            state.visible = entry.isIntersecting && entry.intersectionRatio >= 0.35;
            state.ratio = entry.intersectionRatio;
            if (!state.visible) {
              state.userPaused = false;
              state.attempted = false;
            }
          }
          reconcile();
        }, { threshold: [0, 0.35] });

        for (const video of videos) {
          video.addEventListener('pause', () => {
            const state = states.get(video);
            if (state.pendingPauses) state.pendingPauses -= 1;
            else if (state.visible) state.userPaused = true;
            if (activeVideo === video && video.paused) activeVideo = null;
          });
          video.addEventListener('play', () => {
            if (document.hidden) {
              pause(video);
              return;
            }
            states.get(video).userPaused = false;
            activeVideo = video;
            for (const other of videos) if (other !== video) pause(other);
          });
          observer.observe(video);
        }

        const onPreferenceChange = event => {
          if (event.matches) pauseAll();
          else reconcile();
        };
        if (typeof preference.addEventListener === 'function') {
          preference.addEventListener('change', onPreferenceChange);
        } else {
          preference.addListener(onPreferenceChange);
        }
        document.addEventListener('visibilitychange', () => {
          if (document.hidden) pauseAll();
          else reconcile();
        });
      } catch {
        observer?.disconnect();
        // Native controls remain available when observation is unsupported.
      }
    }
  }
}

// Content is visible by default; motion enhances browsers that can observe it.
if (typeof IntersectionObserver === 'function' && typeof matchMedia === 'function') {
  const preference = matchMedia('(prefers-reduced-motion: reduce)');
  const canWatchPreference = typeof preference.addEventListener === 'function'
    || typeof preference.addListener === 'function';

  if (!preference.matches && canWatchPreference) {
    const targets = [...document.querySelectorAll('[data-reveal]')];
    const remaining = new Set(targets);
    let observer;
    let stopped = false;

    const stop = () => {
      if (stopped) return;
      stopped = true;
      observer?.disconnect();
      if (typeof preference.removeEventListener === 'function') {
        preference.removeEventListener('change', onPreferenceChange);
      } else if (typeof preference.removeListener === 'function') {
        preference.removeListener(onPreferenceChange);
      }
    };

    const showAll = () => {
      for (const target of targets) target.classList.add('is-visible');
      document.documentElement.classList.remove('motion-ready');
      stop();
    };

    const onPreferenceChange = event => {
      if (event.matches) showAll();
    };

    if (targets.length) {
      try {
        observer = new IntersectionObserver(entries => {
          if (stopped) return;
          for (const entry of entries) {
            if (!remaining.has(entry.target) || !entry.isIntersecting || entry.intersectionRatio < 0.12) continue;
            entry.target.classList.add('is-visible');
            observer.unobserve(entry.target);
            remaining.delete(entry.target);
          }
          if (!remaining.size) stop();
        }, { threshold: 0.12 });

        if (typeof preference.addEventListener === 'function') {
          preference.addEventListener('change', onPreferenceChange);
        } else {
          preference.addListener(onPreferenceChange);
        }
        for (const target of targets) observer.observe(target);
        document.documentElement.classList.add('motion-ready');
      } catch {
        showAll();
      }
    }
  }
}
