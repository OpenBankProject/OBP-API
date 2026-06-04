#!/usr/bin/env python3
"""
Parse surefire XML reports from all shards and print a per-test speed table.

The Lift -> http4s migration is complete: *every* API version (v1.2.1 through
v7.0.0) is served by http4s. There is no Lift HTTP code left, so the old
"http4s vs Lift" split is meaningless. The meaningful axis now is **execution
model**:

  * unit/pure   — no embedded server; pure logic / JSON-factory / route-matcher
                  / middleware tests. These are the speed win of the migration.
  * integration — boots a real server (test class extends ServerSetup) or a
                  self-started http4s server; pays DB/HTTP cost per test.

Two tables are printed:
  1. By execution model (unit/pure vs integration) — the migration KPI.
  2. By API version (API v1 .. v7, all http4s) — per-version cost.

A suite counts as integration when its test class extends `ServerSetup`
(detected by scanning obp-api/src/test/scala) or is one of the self-starting
http4s server suites in HTTP4S_INTEGRATION_SUITES. Everything else is unit/pure.

Usage:
    python3 test_speed_report.py <reports-root-dir>

<reports-root-dir> should contain the extracted artifacts from all shards.
Override the source root (used only for integration detection) with
OBP_TEST_SRC_ROOT; if sources are not found, the execution-model split degrades
gracefully (suites counted as integration) and the by-version table still prints.
"""

from __future__ import annotations

import os
import re
import sys
import glob
import xml.etree.ElementTree as ET
from collections import defaultdict

# Self-starting http4s integration suites that boot a server WITHOUT extending
# ServerSetup (so the source scan can't detect them) — list them explicitly.
HTTP4S_INTEGRATION_SUITES = {
    "code.api.v7_0_0.Http4s700RoutesTest",
    "code.api.v7_0_0.Http4s700TransactionTest",
    "code.api.http4sbridge.Http4sServerIntegrationTest",
    "code.api.v5_0_0.Http4s500SystemViewsTest",
}

DEFAULT_SRC_ROOT = "obp-api/src/test/scala"


# ---------------------------------------------------------------------------
# Integration detection by source scan (does the test class extend ServerSetup?)
# ---------------------------------------------------------------------------

def build_integration_map(src_root):
    """Return ({fqClassName: extendsServerSetup}, scan_ok)."""
    fqmap = {}
    if not os.path.isdir(src_root):
        return fqmap, False
    for root, _dirs, files in os.walk(src_root):
        for fname in files:
            if not fname.endswith(".scala"):
                continue
            try:
                with open(os.path.join(root, fname), encoding="utf-8", errors="ignore") as fh:
                    txt = fh.read()
            except OSError:
                continue
            pm = re.search(r'^\s*package\s+([\w.]+)', txt, re.M)
            pkg = pm.group(1) if pm else ""
            # For each `class X ... {`, inspect the parents portion (everything
            # up to the first brace) and check whether it mentions ServerSetup.
            for cm in re.finditer(r'\bclass\s+(\w+)\b(.*?)\{', txt, re.S):
                cls, parents = cm.group(1), cm.group(2)
                fqmap[f"{pkg}.{cls}"] = ("ServerSetup" in parents)
    return fqmap, True


def is_integration(fq, fqmap):
    if fq in HTTP4S_INTEGRATION_SUITES:
        return True
    if fq in fqmap:
        return fqmap[fq]
    # Unknown (class/file-name mismatch or degraded scan): default to
    # integration so we never overstate the unit/pure win.
    return True


# ---------------------------------------------------------------------------
# API version from the suite's package
# ---------------------------------------------------------------------------

_VERSIONS = ("v7_0_0", "v6_0_0", "v5_1_0", "v5_0_0", "v4_0_0", "v3_1_0", "v3_0_0",
             "v2_2_0", "v2_1_0", "v2_0_0", "v1_4_0", "v1_3_0", "v1_2_1")


def api_version(fq):
    for v in _VERSIONS:
        if v in fq:
            return f"API v{v[1]}"  # "1" .. "7"
    return "other"


# ---------------------------------------------------------------------------
# Parse
# ---------------------------------------------------------------------------

def collect(reports_root, fqmap):
    by_model = defaultdict(lambda: {"tests": 0, "time": 0.0})
    by_version = defaultdict(lambda: {"tests": 0, "time": 0.0})

    pattern = os.path.join(reports_root, "**", "TEST-*.xml")
    for path in glob.glob(pattern, recursive=True):
        try:
            root = ET.parse(path).getroot()
            name = root.get("name", "")
            tests = int(root.get("tests", 0))
            t = float(root.get("time", 0))
            if tests == 0:
                continue
            model = "integration" if is_integration(name, fqmap) else "unit/pure"
            by_model[model]["tests"] += tests
            by_model[model]["time"] += t
            ver = api_version(name)
            by_version[ver]["tests"] += tests
            by_version[ver]["time"] += t
        except Exception:
            pass

    return by_model, by_version


# ---------------------------------------------------------------------------
# Render
# ---------------------------------------------------------------------------

MODEL_ORDER = ["unit/pure", "integration"]
VERSION_ORDER = ["API v7", "API v6", "API v5", "API v4", "API v3",
                 "API v2", "API v1", "other"]


def _table(stats, order, col_w=(24, 7, 12, 10)):
    sep = "+-" + "-+-".join("-" * w for w in col_w) + "-+"
    hdr = "| " + " | ".join(h.center(w) for h, w in zip(
        ["Category", "Tests", "Total time", "Avg/test"], col_w)) + " |"
    lines = [sep, hdr, sep]
    for cat in order:
        if cat not in stats:
            continue
        d = stats[cat]
        avg = d["time"] / d["tests"] if d["tests"] else 0
        lines.append("| " + " | ".join([
            cat.ljust(col_w[0]),
            str(d["tests"]).rjust(col_w[1]),
            f"{d['time']:.1f}s".rjust(col_w[2]),
            f"{avg:.3f}s".rjust(col_w[3]),
        ]) + " |")
    lines.append(sep)
    return "\n".join(lines)


def render_plain(by_model, by_version, scan_ok):
    out = ["All API versions (v1-v7) are served by http4s — the split is by",
           "execution model, not framework.\n",
           "By execution model (unit/pure = no server, integration = embedded server):"]
    if not scan_ok:
        out.append("  (source scan unavailable — suites counted as integration)")
    out.append(_table(by_model, MODEL_ORDER))
    out += ["", "By API version:", _table(by_version, VERSION_ORDER)]

    u = by_model.get("unit/pure")
    i = by_model.get("integration")
    if u and i and u["tests"] and i["tests"]:
        ua = u["time"] / u["tests"]
        ia = i["time"] / i["tests"]
        if ua > 0:
            out += ["",
                    f"--> unit/pure tests are {ia/ua:.0f}x faster per test than "
                    f"integration tests ({ua:.3f}s vs {ia:.3f}s)."]
    return "\n".join(out)


def render_markdown(by_model, by_version, scan_ok):
    rows = ["## Per-test speed — all endpoints served by http4s", "",
            "_All API versions (v1-v7) run on http4s. The split below is by "
            "execution model, not framework._", "",
            "### By execution model", ""]
    if not scan_ok:
        rows += ["> source scan unavailable — suites counted as integration", ""]
    rows += ["| Category | Tests | Total time | Avg/test |", "|---|---:|---:|---:|"]
    for cat in MODEL_ORDER:
        if cat not in by_model:
            continue
        d = by_model[cat]
        avg = d["time"] / d["tests"] if d["tests"] else 0
        rows.append(f"| {cat} | {d['tests']} | {d['time']:.1f}s | {avg:.3f}s |")

    rows += ["", "### By API version", "",
             "| Category | Tests | Total time | Avg/test |", "|---|---:|---:|---:|"]
    for cat in VERSION_ORDER:
        if cat not in by_version:
            continue
        d = by_version[cat]
        avg = d["time"] / d["tests"] if d["tests"] else 0
        rows.append(f"| {cat} | {d['tests']} | {d['time']:.1f}s | {avg:.3f}s |")

    u = by_model.get("unit/pure")
    i = by_model.get("integration")
    if u and i and u["tests"] and i["tests"]:
        ua = u["time"] / u["tests"]
        ia = i["time"] / i["tests"]
        if ua > 0:
            rows += ["",
                     f"> **Unit/pure tests are {ia/ua:.0f}x faster per test than "
                     f"integration tests** ({ua:.3f}s vs {ia:.3f}s). This is the "
                     f"migration win: logic that used to need a running server is "
                     f"now pure unit-tested."]
    return "\n".join(rows)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <reports-root-dir>", file=sys.stderr)
        sys.exit(1)

    src_root = os.environ.get("OBP_TEST_SRC_ROOT", DEFAULT_SRC_ROOT)
    fqmap, scan_ok = build_integration_map(src_root)

    by_model, by_version = collect(sys.argv[1], fqmap)
    if not by_model and not by_version:
        print("No matching surefire XML reports found.", file=sys.stderr)
        sys.exit(0)

    print(render_plain(by_model, by_version, scan_ok))

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as f:
            f.write("\n")
            f.write(render_markdown(by_model, by_version, scan_ok))
            f.write("\n")
