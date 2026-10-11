# DAYLIGHT-ATTRIBUTION
# Daylight modifications by sh1vvy.
# Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
# Daylight changes: 2026-10-11 through 2026-10-11.
# Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
# Original authors' notices and licenses remain applicable to their material.
# END DAYLIGHT-ATTRIBUTION

"""Exercise provenance enforcement on temporary repositories, never the working checkout."""
import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("daylight_attribution", Path(__file__).with_name("daylight_attribution.py"))
attribution = importlib.util.module_from_spec(spec)
spec.loader.exec_module(attribution)


class AttributionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.git("init", "-q")
        self.git("config", "user.name", "Fixture author")
        self.git("config", "user.email", "fixture@example.invalid")
        self.write("LICENSE", "Fixture original license, unchanged.\n")
        self.write("app/src/Original.kt", "/* Copyright Original Author; GPLv3 */\npackage fixture\nval value = 1\n")
        self.git("add", ".")
        self.git("commit", "-qm", "Upstream")
        self.baseline = self.git("rev-parse", "HEAD").strip()
        self.write("app/src/Original.kt", "/* Copyright Original Author; GPLv3 */\npackage fixture\nval value = 2\n")
        self.write("app/src/backdrop/Adapted.kt", "/* Copyright Third Party; Apache License 2.0 */\npackage fixture\n")
        self.write("app/src/layout.xml", '<?xml version="1.0" encoding="utf-8"?>\n<root />\n')
        self.write("tools/run.py", "#!/usr/bin/env python3\n# -*- coding: utf-8 -*-\nprint('fixture')\n")
        self.write("cloudflare-daylight/public/index.html", "<!DOCTYPE html>\n<html></html>\n")
        self.write("COPYRIGHT.md", "# Fixture authors\n")
        self.write("DAYLIGHT-NOTICE.md", "# Fixture terms\n")
        self.write("app/src/main/res/drawable/ic_logo.xml", "<vector />\n")
        self.original_bodies = {p.relative_to(self.root).as_posix(): p.read_bytes() for p in self.root.rglob('*') if p.is_file() and '.git' not in p.parts}
        self.git("add", ".")
        self.git("commit", "-qm", "Daylight changes")
        for name, value in (("ROOT", self.root), ("BASELINE", self.baseline), ("INVENTORY", self.root / "DAYLIGHT-CONTRIBUTIONS.json")):
            patcher = patch.object(attribution, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.root), *args],
            env={**os.environ, "GIT_AUTHOR_DATE": "2026-10-08T12:00:00+05:30", "GIT_COMMITTER_DATE": "2026-10-08T12:00:00+05:30"},
            stderr=subprocess.PIPE).decode()

    def write(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def run_tool(self, update=True, author="sh1vvy", date="2026-10-11"):
        with contextlib.redirect_stdout(io.StringIO()):
            attribution.run(update, author, date)

    def test_notices_preserve_original_bytes_licenses_and_required_preludes(self):
        self.run_tool()
        for name, body in self.original_bodies.items():
            self.assertEqual(body, attribution.strip_notice((self.root / name).read_text()).encode(), name)
        self.assertTrue((self.root / "app/src/layout.xml").read_text().startswith('<?xml version='))
        self.assertTrue((self.root / "tools/run.py").read_text().startswith('#!/usr/bin/env python3\n# -*- coding: utf-8 -*-\n'))
        self.assertTrue((self.root / "cloudflare-daylight/public/index.html").read_text().startswith('<!DOCTYPE html>\n'))
        self.assertEqual('<vector />\n', (self.root / "app/src/main/res/drawable/ic_logo.xml").read_text())
        separate = (self.root / "app/src/backdrop/Adapted.kt").read_text()
        self.assertIn("no Daylight additional terms applied", separate)
        self.assertIn("Copyright Third Party; Apache License 2.0", separate)
        self.assertFalse(attribution.separately_licensed("COPYRIGHT.md", "# Legal scope\nOther files use AGPL.\n"))
        self.assertFalse(attribution.separately_licensed("tools/tool.py", 'license_names = ["MIT License", "AGPL"]\n'))

    def test_commit_and_later_checks_do_not_change_dates_or_source_inventory(self):
        self.run_tool()
        inventory = (self.root / "DAYLIGHT-CONTRIBUTIONS.json").read_bytes()
        self.git("add", ".")
        self.git("commit", "-qm", "Record notices")
        self.run_tool(update=False, author=None, date="2026-11-01")
        self.run_tool(author="Another Author", date="2026-11-01")
        self.assertEqual(inventory, (self.root / "DAYLIGHT-CONTRIBUTIONS.json").read_bytes())

    def test_missing_notices_and_modified_original_license_fail_checks(self):
        self.run_tool()
        path = self.root / "app/src/Original.kt"
        path.write_text(attribution.strip_notice(path.read_text()))
        with self.assertRaisesRegex(ValueError, "Missing or stale scoped notice"):
            self.run_tool(update=False, author=None)
        self.write("LICENSE", "Changed upstream license\n")
        with self.assertRaisesRegex(ValueError, "byte-for-byte unchanged"):
            self.run_tool()

    def test_actual_new_authors_and_source_hashes_are_recorded_without_reassigning_prior_work(self):
        self.run_tool()
        self.write("app/src/New.kt", "package fixture\nval next = 3\n")
        with self.assertRaisesRegex(ValueError, "Unattributed contribution"):
            self.run_tool(update=False, author=None)
        self.run_tool(author="Contributor", date="2026-10-12")
        inventory = json.loads((self.root / "DAYLIGHT-CONTRIBUTIONS.json").read_text())
        entries = {entry["path"]: entry for entry in inventory["files"]}
        self.assertEqual(["sh1vvy"], entries["app/src/Original.kt"]["authors"])
        self.assertEqual(["Contributor"], entries["app/src/New.kt"]["authors"])
        self.assertEqual(hashlib.sha256(b"package fixture\nval next = 3\n").hexdigest(), entries["app/src/New.kt"]["source_sha256"])
        for site in ("cloudflare-jam", "cloudflare-daylight"):
            self.assertEqual((self.root / "LICENSE").read_bytes(), (self.root / site / "public/legal/LICENSE").read_bytes())
        self.write("cloudflare-jam/public/legal/COPYRIGHT.md", "Stale\n")
        with self.assertRaisesRegex(ValueError, "Stale or missing legal copy"):
            self.run_tool(update=False, author=None, date="2026-10-12")


if __name__ == "__main__":
    unittest.main()
