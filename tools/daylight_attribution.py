#!/usr/bin/env python3
# DAYLIGHT-ATTRIBUTION
# Daylight modifications by sh1vvy.
# Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
# Daylight changes: 2026-10-11 through 2026-10-11.
# Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
# Original authors' notices and licenses remain applicable to their material.
# END DAYLIGHT-ATTRIBUTION

"""Maintain scoped Daylight notices and a source provenance inventory; never edit LICENSE."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BASELINE = "2c599a6cd7a195227a11dab5769f88da8fd08194"
INVENTORY = ROOT / "DAYLIGHT-CONTRIBUTIONS.json"
ROOT_FILES = {"README.md", "CONTRIBUTING.md", "UPSTREAM.md", "MAINTAINERS.md", "COPYRIGHT.md",
              "DAYLIGHT-NOTICE.md", "build.gradle.kts", "settings.gradle.kts", "app/build.gradle.kts",
              "shared/build.gradle.kts", "sharedUi/build.gradle.kts"}
PREFIXES = ("app/src/", "shared/src/", "sharedUi/src/", "native/", "cloudflare-jam/src/", "cloudflare-jam/public/",
            "cloudflare-jam/test/", "cloudflare-daylight/test/", "cloudflare-daylight/public/",
            "tools/", "docs/", ".github/")
EXTENSIONS = {".kt", ".kts", ".java", ".js", ".cpp", ".cc", ".h", ".py", ".xml", ".md", ".yml", ".yaml", ".html", ".css"}
EXCLUDED = ("/licenses/", "/credits/", "/dsd/", "/com/my/kizzy/")
EXCLUDED_FILES = {"docs/UPSTREAM_MAINTAINERS.md",
                  "app/src/main/res/drawable/ic_launcher_foreground.xml",
                  "app/src/main/res/drawable/ic_launcher_monochrome.xml",
                  "app/src/main/res/drawable/ic_logo.xml",
                  "app/src/main/res/drawable/ic_notification_logo.xml",
                  "sharedUi/src/commonMain/composeResources/drawable/ic_logo.xml"}


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args], stderr=subprocess.PIPE)


def prelude(text):
    """Keep XML declarations, shebangs, BOMs and HTML doctypes in their required position."""
    match = re.match(r"(?:\ufeff)?(?:(?:#![^\n]*\n(?:#.*coding[:=][^\n]*\n)?)|(?:<\?xml[^>]*\?>\r?\n)|(?:<!DOCTYPE[^>]*>\r?\n))?", text, re.I)
    return text[:match.end()], text[match.end():]


def strip_notice(text):
    prefix, body = prelude(text)
    patterns = (r"/\* DAYLIGHT-ATTRIBUTION\r?\n.*?\*/\r?\n\r?\n",
                r"<!-- DAYLIGHT-ATTRIBUTION\r?\n.*?-->\r?\n\r?\n",
                r"# DAYLIGHT-ATTRIBUTION\r?\n.*?# END DAYLIGHT-ATTRIBUTION\r?\n\r?\n")
    for pattern in patterns:
        match = re.match(pattern, body, re.S)
        if match:
            return prefix + body[match.end():]
    return text


def separately_licensed(name, text):
    # License words in documentation or string literals do not relicense a file.
    _, body = prelude(text)
    leading_comments = re.match(r"\s*(?:(?:/\*.*?\*/|<!--.*?-->|(?:\#|//)[^\n]*(?:\n|$))\s*)*", body[:8000], re.S).group()
    return (any(part in name for part in ("/backdrop/", "/floatingtabbar/", "native/analyzer/")) or
            any(marker in leading_comments for marker in ("GNU Affero", "AGPL", "Apache License", "MIT License")))


def notice(name, authors, first, last, separate):
    line_end = "\r\n" if "\r\n" in (ROOT / name).read_bytes().decode("utf-8") else "\n"
    lines = [f"Daylight modifications by {', '.join(authors)}.",
             f"Copyright (C) 2026 {', '.join(authors)}, for copyrightable Daylight contributions only.",
             f"Daylight changes: {first} through {last}."]
    if separate:
        lines += ["Existing upstream license retained; no Daylight additional terms applied.", "See COPYRIGHT.md for contribution provenance."]
    else:
        lines += ["Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md."]
    lines += ["Original authors' notices and licenses remain applicable to their material."]
    suffix = Path(name).suffix
    if suffix in {".py", ".yml", ".yaml"}:
        rendered = ["# DAYLIGHT-ATTRIBUTION", *["# " + line for line in lines], "# END DAYLIGHT-ATTRIBUTION"]
    elif suffix in {".xml", ".md", ".html"}:
        rendered = ["<!-- DAYLIGHT-ATTRIBUTION", *lines, "-->"]
    else:
        rendered = ["/* DAYLIGHT-ATTRIBUTION", *[" * " + line for line in lines], " */"]
    return line_end.join(rendered) + line_end * 2


def candidates():
    changed = set(git("diff", "--name-only", BASELINE, "--").decode().splitlines())
    changed.update(git("ls-files", "--others", "--exclude-standard", "-z").decode().split("\0"))
    return sorted(name for name in changed if name and (ROOT / name).is_file() and
                  (name in ROOT_FILES or name.startswith(PREFIXES)) and Path(name).suffix in EXTENSIONS and
                  not any(part in name for part in EXCLUDED) and name not in EXCLUDED_FILES and
                  "/legal/" not in name)


def historical_dates():
    first, last = {}, {}
    date = None
    for line in git("log", "--format=DATE:%ad", "--date=short", "--name-only", "--diff-filter=AM", f"{BASELINE}..HEAD").decode().splitlines():
        if line.startswith("DATE:"):
            date = line[5:]
        elif line and date:
            first[line] = date
            last.setdefault(line, date)
    return first, last


def root_legal_copies(update):
    errors = []
    for site in ("cloudflare-daylight", "cloudflare-jam"):
        destination = ROOT / site / "public/legal"
        for name in ("LICENSE", "COPYRIGHT.md", "DAYLIGHT-NOTICE.md", "DAYLIGHT-CONTRIBUTIONS.json"):
            target = destination / name
            source = (ROOT / name).read_bytes()
            if update:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source)
            elif not target.is_file() or target.read_bytes() != source:
                errors.append(f"Stale or missing legal copy: {target.relative_to(ROOT)}")
    return errors


def run(update, author, today):
    existing = json.loads(INVENTORY.read_text()) if INVENTORY.exists() else {}
    previous = {entry["path"]: entry for entry in existing.get("files", [])}
    baseline_license = git("show", f"{BASELINE}:LICENSE")
    if (ROOT / "LICENSE").read_bytes() != baseline_license:
        raise ValueError("Original BitChord LICENSE must remain byte-for-byte unchanged")
    first_dates, last_dates = historical_dates()
    tree = {}
    for line in git("ls-tree", "-rz", BASELINE).decode().split("\0"):
        if line:
            metadata, name = line.split("\t", 1)
            tree[name] = metadata.split()[2]
    records, errors = [], []
    for name in candidates():
        path = ROOT / name
        current = path.read_bytes().decode("utf-8")
        body = strip_notice(current)
        old = previous.get(name, {})
        authors = old.get("authors") or ([author] if author else [])
        if not authors:
            errors.append(f"Unattributed contribution: {name}; record its actual author with --update --author")
            continue
        first = old.get("first_modified") or first_dates.get(name, today)
        last = old.get("last_modified") or last_dates.get(name, today)
        source_hash = hashlib.sha256(body.encode("utf-8")).hexdigest()
        try:
            committed = strip_notice(git("show", f"HEAD:{name}").decode("utf-8"))
        except subprocess.CalledProcessError:
            committed = None
        if (old and source_hash != old.get("source_sha256")) or (not old and committed != body):
            last = today
        separate = separately_licensed(name, body)
        header = notice(name, authors, first, last, separate)
        prefix, rest = prelude(body)
        wanted = prefix + header + rest
        if update:
            path.write_bytes(wanted.encode("utf-8"))
        elif wanted != current:
            errors.append(f"Missing or stale scoped notice: {name}")
        records.append({"path": name, "authors": authors, "first_modified": first, "last_modified": last,
                        "scope": "upstream-license-retained" if separate else "daylight-gplv3-additions",
                        "upstream_blob": tree.get(name), "source_sha256": source_hash})
    result = {"format": 1, "upstream_baseline": BASELINE,
              "original_license_sha256": hashlib.sha256(baseline_license).hexdigest(),
              "terms_effective": "2026-10-11", "notice": "DAYLIGHT-NOTICE.md",
              "scope": "Copyrightable Daylight contributions only; original and third-party rights retained.", "files": records}
    if errors:
        raise ValueError("\n".join(errors))
    if update:
        INVENTORY.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    elif result != existing:
        raise ValueError("Contribution inventory differs from source; run --update with the actual contributor's name")
    errors += root_legal_copies(update)
    if errors:
        raise ValueError("\n".join(errors))
    print(f"{'Updated' if update else 'Verified'} {len(records)} scoped notices; original GPLv3 unchanged; website legal copies match.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--update", action="store_true")
    mode.add_argument("--check", action="store_true")
    parser.add_argument("--author", help="Actual author for newly recorded Daylight contributions; never replaces recorded authors")
    parser.add_argument("--date", default=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=5, minutes=30))).date().isoformat())
    args = parser.parse_args()
    datetime.date.fromisoformat(args.date)
    if args.author and any(char in args.author for char in "\r\n<>*/"):
        parser.error("Author must be a plain attribution name")
    try:
        run(args.update, args.author, args.date)
    except (ValueError, subprocess.CalledProcessError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
