#!/usr/bin/env python3
"""Keep THIRD_PARTY_NOTICES.md in step with the release runtime classpath.

  scripts/third-party-notices.py            check; exit 1 on drift, unknown or copyleft license
  scripts/third-party-notices.py --write    rewrite the table, carrying each artifact's license over

A new artifact has no license on record: --write marks it UNKNOWN and the check fails until a
person looks the license up and edits the row.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NOTICES = ROOT / "THIRD_PARTY_NOTICES.md"
ROW = re.compile(r"^\| ([^|\s]+:[^|\s]+) \| ([^|\s]+) \| ([^|]+) \|$")
DEP = re.compile(r"([\w.\-]+):([\w.\-]+)(?::([\w.\-]+))?(?: -> ([\w.\-]+))?(?: \([*cn]\))?\s*$")
OK_LICENSE = re.compile(r"^(Apache-2\.0|MIT|BSD-[23]-Clause|ISC|EPL-1\.0|EPL-2\.0|CC0-1\.0|0BSD|Unlicense)( OR .+| AND .+)?$")


def runtime_artifacts():
    out = subprocess.run(
        [str(ROOT / "gradlew"), "--console=plain", "-q", ":kiosk-ops-sdk:dependencies",
         "--configuration", "releaseRuntimeClasspath"],
        cwd=ROOT, capture_output=True, text=True, check=True).stdout
    found = set()
    for line in out.splitlines():
        if not re.match(r"^[| ]*[+\\]--- ", line) or "project " in line:
            continue
        m = DEP.search(line.split("--- ", 1)[1])
        if m:
            found.add((m.group(1) + ":" + m.group(2), m.group(4) or m.group(3)))
    return found


def recorded():
    rows = {}
    for line in NOTICES.read_text().splitlines():
        m = ROW.match(line)
        if m:
            rows.setdefault(m.group(1), []).append((m.group(2), m.group(3).strip()))
    return rows


def main():
    live = runtime_artifacts()
    if len(live) < 20:
        sys.exit("only %d runtime artifacts parsed; the dependency report format changed" % len(live))
    rows = recorded()
    licenses = {k: v[0][1] for k, v in rows.items()}
    if "--write" in sys.argv:
        table = ["| Artifact | Version | License |", "|---|---|---|"]
        for ga, ver in sorted(live):
            table.append("| %s | %s | %s |" % (ga, ver, licenses.get(ga, "UNKNOWN")))
        lines = NOTICES.read_text().splitlines()
        start = next(i for i, l in enumerate(lines) if l.startswith("| Artifact |"))
        end = start
        while end < len(lines) and lines[end].startswith("|"):
            end += 1
        NOTICES.write_text("\n".join(lines[:start] + table + lines[end:]) + "\n")
        print("rewrote %d rows" % len(live))
        return
    recorded_set = {(ga, v) for ga, vs in rows.items() for v, _ in vs}
    problems = []
    for ga, ver in sorted(live - recorded_set):
        problems.append("missing or stale in notices: %s:%s" % (ga, ver))
    for ga, ver in sorted(recorded_set - live):
        problems.append("in notices but not on the runtime classpath: %s:%s" % (ga, ver))
    for ga, vs in rows.items():
        for _, lic in vs:
            if not OK_LICENSE.match(lic):
                problems.append("license not on the allow list: %s = %s" % (ga, lic))
    if problems:
        print("\n".join(problems))
        sys.exit("THIRD_PARTY_NOTICES.md is out of step; run scripts/third-party-notices.py --write and review")
    print("notices match %d runtime artifacts" % len(live))


main()
