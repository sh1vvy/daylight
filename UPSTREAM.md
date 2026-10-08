# Upstream and attribution

Daylight is an independently maintained Android app by [sh1vvy](https://github.com/sh1vvy), based on [BitChord](https://github.com/kushagrasinghx/BitChord), created by Kushagra Singh and its contributors.

Upstream baseline: `2c599a6cd7a195227a11dab5769f88da8fd08194`.

Daylight-specific changes began on 8 October 2026. The GitHub repository became standalone on the same date. The original Git history, copyright notices, dependency credits, and [GPLv3 license](LICENSE) are retained. Original contributor names are preserved in [UPSTREAM_MAINTAINERS.md](docs/UPSTREAM_MAINTAINERS.md).

Daylight’s public app ID is `com.sh1vvy.daylight`; development uses `.dev`. Internal `com.music.bitchord` source namespaces, JNI entry points, and playback URI names remain compatible with the inherited engine. They are implementation identifiers, not the product’s install identity. Desktop code is retained for upstream reference but is excluded from Daylight’s build and CI.

Daylight updates use its own repository. The upstream party server, presence reporting, Discord application ID, donation links, and deployment workflows are not defaults for Daylight. Configure your own optional integrations in `local.properties`.

## Credits

- BitChord — Kushagra Singh and contributors.
- Lyrics animation — binimum.
- Lyrics providers inherited from BitChord — lrc.red, BiniLyrics, BetterLyrics, PaxSenix, LyricsPlus, SimpMusic, Unison, Megalobiz, KuGou, LRCLIB, Musixmatch, and Genius.
- Other dependency licenses and credits are retained in the source and Git history.
