/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-08 through 2026-10-08.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

// Progressive enhancement only: entering an invite works without JavaScript.
const normalize = value => value.toUpperCase().replace(/[^A-Z0-9]/g, '').replace(/[IL]/g, '1').replace(/O/g, '0');
const codeForm = document.querySelector('.code-form');
codeForm?.addEventListener('submit', () => {
  const input = codeForm.elements.namedItem('code');
  input.value = normalize(input.value);
});

const copyButton = document.querySelector('[data-copy-code]');
if (copyButton && navigator.clipboard?.writeText) {
  copyButton.hidden = false;
  copyButton.addEventListener('click', async () => {
    const status = document.querySelector('.copy-status');
    try {
      await navigator.clipboard.writeText(copyButton.dataset.copyCode);
      status.textContent = 'Code copied. Send it to your people.';
    } catch {
      status.textContent = 'Couldn’t copy automatically. Select the code above to copy it.';
    }
  });
}

// Android Chrome returns here if the app is missing; keep the invite visible.
function showInstallHelp() {
  if (location.hash !== '#install-help') return;
  const help = document.querySelector('#install-help');
  if (help) help.open = true;
}
showInstallHelp();
addEventListener('hashchange', showInstallHelp);
