<!-- DAYLIGHT-ATTRIBUTION
Daylight modifications by sh1vvy.
Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
Daylight changes: 2026-10-11 through 2026-10-11.
Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
Original authors' notices and licenses remain applicable to their material.
-->

# Copyright and contribution provenance

## Daylight

**Daylight modifications by sh1vvy.**

**Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.**

Daylight-specific development began on **8 October 2026**. This attribution
identifies the Daylight maintainer's contributions, rather than claiming sole
authorship of the inherited app, third-party work or non-copyrightable output.
Other authors keep ownership of their contributions.

Daylight's code continues under GNU GPLv3. The reasonable attribution and origin
terms in [DAYLIGHT-NOTICE.md](DAYLIGHT-NOTICE.md) apply to the identified
Daylight material in copies distributed with those terms from 11 October 2026.
The original [LICENSE](LICENSE) text is unchanged.

## Original application and other authors

BitChord was created by Kushagra Singh and its contributors. Their original
copyright and license notices remain intact. Daylight's upstream baseline is
`2c599a6cd7a195227a11dab5769f88da8fd08194`. Original history and authors remain
in Git, [UPSTREAM.md](UPSTREAM.md) and
[UPSTREAM_MAINTAINERS.md](docs/UPSTREAM_MAINTAINERS.md).

Existing separately licensed source, including imported backdrop/tab-bar code
and AGPL material, retains its own terms. An attribution-only adaptation notice
on such a file does not replace its license or apply Daylight's additional terms.

The butterfly artwork belongs to **art by 11 (_artbyeleven on IG)**. Inter,
the Discover icon, music artwork, demo recordings and other third-party assets
are not covered by the sh1vvy code copyright notice. Their own credits and
licenses remain applicable; inclusion here does not grant additional asset rights.

## Dated contribution record

[DAYLIGHT-CONTRIBUTIONS.json](DAYLIGHT-CONTRIBUTIONS.json) records the files
changed or added by Daylight relative to the upstream baseline, dates available
from Git, the upstream blob identifier, and a hash of the source excluding its
Daylight notice. It includes source, build configuration, tests and documentation;
it is a provenance inventory, not a claim that every recorded edit is copyrightable.
Git commits provide the detailed changes and authorship history.

The inventory and source notices can be maintained with
`python3 tools/daylight_attribution.py --update --author sh1vvy` and checked with
`python3 tools/daylight_attribution.py --check`. Use the actual contributor's
name when recording a new contribution; do not assign someone else's work to
the maintainer. The tool preserves existing notices and separate license scopes.

The Android build packages this notice, the GPL text, the additional terms and
the contribution inventory. The app exposes the legal notices through
Settings → Account & integrations → Credits. Website source packages include
the same documents under `public/legal/`.
