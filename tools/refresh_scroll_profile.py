"""Extract bounded UI baseline rules from an unminified Android ART profile.

Export with `adb shell cmd package dump-profiles --dump-classes-and-methods
com.sh1vvy.daylight.dev`, then pull the reported text file. The source build
must be unminified; AGP rewrites these rules through R8 for the final APK.
Only Daylight UI startup/hot methods are retained, never account/app data.
"""

import argparse
from pathlib import Path


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("source", type=Path)
parser.add_argument("--output", type=Path, default=Path("app/src/main/baseline-prof.txt"))
args = parser.parse_args()

owners = (
    "Lcom/music/bitchord/ui/",
    "Lcom/music/bitchord/MainActivity",
    "Lcom/music/bitchord/BitChordApplication",
)
rules = set()
for line in args.source.read_text().splitlines():
    line = line.strip()
    flags, descriptor_start, method = line.partition("L")
    owner, method_separator, _ = method.partition("->")
    if (
        descriptor_start
        and method_separator
        and set(flags) <= set("HSP")
        and ("H" in flags or "S" in flags)
        and ("L" + owner).startswith(owners)
    ):
        rules.add(line)

if not rules:
    parser.error("No UI startup/hot method rules; the existing profile was left untouched.")
if len(rules) > 4096:
    parser.error("More than 4,096 rules; review the capture before expanding its budget.")
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(
    "# Daylight UI baseline: startup/hot methods from an unminified S22 capture.\n"
    "# Home, Library and Liked Songs scrolls; regenerate with tools/refresh_scroll_profile.py.\n"
    "# AGP rewrites/removes rules through R8; library profiles merge separately.\n"
    + "\n".join(sorted(rules))
    + "\n"
)
print(f"Wrote {len(rules)} UI method rules ({args.output.stat().st_size:,} bytes).")
