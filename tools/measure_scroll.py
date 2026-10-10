"""Capture Android frame deadlines for an already opened, warmed Daylight list.

Does not navigate, alter settings, compile packages or clear app data. Keep the
same page, rows, theme, refresh rate and thermal conditions for comparisons.
"""

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import time


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default=shutil.which("adb"))
parser.add_argument("--serial", required=True)
parser.add_argument("--package", default="com.sh1vvy.daylight.dev")
parser.add_argument("--label", required=True)
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--passes", type=int, choices=range(1, 11), default=3)
parser.add_argument("--pattern", choices=("alternating", "round-trip"), default="alternating")
args = parser.parse_args()
if not args.adb:
    parser.error("Supply --adb or add Android platform-tools to PATH.")
if not re.fullmatch(r"[A-Za-z0-9_.-]+", args.label):
    parser.error("Use a simple filename label.")


def shell(*command):
    return subprocess.run(
        [args.adb, "-s", args.serial, "shell", *command],
        check=True, capture_output=True, text=True,
    ).stdout


dimensions = re.findall(r"(\d+)x(\d+)", shell("wm", "size"))
if not dimensions:
    parser.error("Cannot read the phone's screen dimensions.")
width, height = map(int, dimensions[-1])
x, upper, lower = round(width / 2), round(height * 700 / 2340), round(height * 1650 / 2340)
args.output.mkdir(parents=True, exist_ok=True)
summary = {
    "serial": args.serial,
    "model": shell("getprop", "ro.product.model").strip(),
    "android": shell("getprop", "ro.build.version.release").strip(),
    "package": args.package,
    "pattern": args.pattern,
    "swipes_per_pass": 8,
    "swipe_duration_ms": 500,
    "pause_seconds": 0.12,
    "passes": [],
}
for stage in ("before", "after"):
    if stage == "after":
        for run in range(1, args.passes + 1):
            shell("dumpsys", "gfxinfo", args.package, "reset")
            for index in range(8):
                down = index % 2 == 0 if args.pattern == "alternating" else index < 4
                start, end = (lower, upper) if down else (upper, lower)
                shell("input", "swipe", str(x), str(start), str(x), str(end), "500")
                time.sleep(0.12)
            raw = shell("dumpsys", "gfxinfo", args.package, "framestats")
            (args.output / f"{args.label}-{run}-gfxinfo.txt").write_text(raw)
            metrics = {"pass": run}
            for name, pattern in (
                ("frames", r"Total frames rendered: (\d+)"),
                ("janky", r"Janky frames: (\d+)"),
                ("janky_percent", r"Janky frames: \d+ \(([\d.]+)%\)"),
                ("p95_ms", r"95th percentile: (\d+)ms"),
                ("p99_ms", r"99th percentile: (\d+)ms"),
            ):
                match = re.search(pattern, raw)
                if match:
                    metrics[name] = float(match.group(1)) if name.endswith("percent") else int(match.group(1))
            if not metrics.get("frames"):
                parser.error("No rendered frames: open a visible scrollable list before measuring.")
            summary["passes"].append(metrics)
            print(json.dumps(metrics), flush=True)
    for service in ("thermalservice", "battery", "meminfo"):
        command = ("dumpsys", service, args.package) if service == "meminfo" else ("dumpsys", service)
        (args.output / f"{args.label}-{stage}-{service}.txt").write_text(shell(*command))
(args.output / f"{args.label}-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
