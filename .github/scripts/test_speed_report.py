#!/usr/bin/env python3
"""
Parse surefire XML reports from all shards and print an http4s-vs-Lift
per-test speed table to stdout (plain text) and, if GITHUB_STEP_SUMMARY
is set, append a markdown version to that file.

Usage:
    python3 test_speed_report.py <reports-root-dir>

<reports-root-dir> should contain the extracted artifacts from all shards,
e.g. after downloading test-reports-shard{1,2,3} into one directory.
"""

import os
import sys
import glob
import xml.etree.ElementTree as ET
from collections import defaultdict

# ---------------------------------------------------------------------------
# Classification
# ---------------------------------------------------------------------------

# These suites run a real embedded server — they pay the same DB/HTTP cost
# as Lift integration tests.
HTTP4S_INTEGRATION_SUITES = {
    "code.api.v7_0_0.Http4s700RoutesTest",
    "code.api.v7_0_0.Http4s700TransactionTest",
    "code.api.http4sbridge.Http4sLiftBridgePropertyTest",
    "code.api.http4sbridge.Http4sServerIntegrationTest",
    "code.api.v5_0_0.Http4s500SystemViewsTest",
}


def categorize(suite_name: str) -> str | None:
    """Return a display category or None to exclude from the table."""
    # http4s integration (real server)
    if suite_name in HTTP4S_INTEGRATION_SUITES:
        return "http4s v7 — integration"

    # http4s unit/pure (no server) — everything http4s-flavoured that isn't
    # in the integration set above
    if (
        "Http4s" in suite_name
        or "http4s" in suite_name
        or "v7_0_0" in suite_name
        or suite_name.startswith("code.api.util.http4s.")
        or suite_name.startswith("code.api.berlin.group.v2.Http4sBGv2")
    ):
        return "http4s v7 — unit/pure"

    # Lift versions
    for v in ("v6_0_0", "v5_1_0", "v5_0_0", "v4_0_0", "v3_1_0", "v3_0_0",
              "v2_2_0", "v2_1_0", "v2_0_0", "v1_4_0", "v1_3_0", "v1_2_1"):
        if v in suite_name:
            major = v[1]  # "1" … "6"
            return f"Lift v{major}"

    return None  # exclude (util, berlin group non-http4s, etc.)


# ---------------------------------------------------------------------------
# Parse
# ---------------------------------------------------------------------------

def collect(reports_root: str) -> dict:
    stats = defaultdict(lambda: {"tests": 0, "time": 0.0})

    pattern = os.path.join(reports_root, "**", "TEST-*.xml")
    for path in glob.glob(pattern, recursive=True):
        try:
            root = ET.parse(path).getroot()
            name  = root.get("name", "")
            tests = int(root.get("tests", 0))
            time  = float(root.get("time", 0))
            if tests == 0:
                continue
            cat = categorize(name)
            if cat is None:
                continue
            stats[cat]["tests"] += tests
            stats[cat]["time"]  += time
        except Exception:
            pass

    return stats


# ---------------------------------------------------------------------------
# Render
# ---------------------------------------------------------------------------

CATEGORY_ORDER = [
    "http4s v7 — unit/pure",
    "http4s v7 — integration",
    "Lift v6",
    "Lift v5",
    "Lift v4",
    "Lift v3",
    "Lift v2",
    "Lift v1",
]


def render_plain(stats: dict) -> str:
    col_w = [25, 7, 12, 10]
    sep   = "+-" + "-+-".join("-" * w for w in col_w) + "-+"
    hdr   = "| " + " | ".join(
        h.center(w) for h, w in zip(
            ["Category", "Tests", "Total time", "Avg/test"], col_w
        )
    ) + " |"

    lines = [sep, hdr, sep]
    for cat in CATEGORY_ORDER:
        if cat not in stats:
            continue
        d   = stats[cat]
        avg = d["time"] / d["tests"] if d["tests"] else 0
        row = "| " + " | ".join([
            cat.ljust(col_w[0]),
            str(d["tests"]).rjust(col_w[1]),
            f"{d['time']:.1f}s".rjust(col_w[2]),
            f"{avg:.3f}s".rjust(col_w[3]),
        ]) + " |"
        lines.append(row)
    lines.append(sep)
    return "\n".join(lines)


def render_markdown(stats: dict) -> str:
    rows = ["## http4s v7 vs Lift — per-test speed",
            "",
            "| Category | Tests | Total time | Avg/test |",
            "|---|---:|---:|---:|"]
    for cat in CATEGORY_ORDER:
        if cat not in stats:
            continue
        d   = stats[cat]
        avg = d["time"] / d["tests"] if d["tests"] else 0
        rows.append(f"| {cat} | {d['tests']} | {d['time']:.1f}s | {avg:.3f}s |")

    # Highlight ratio
    u = stats.get("http4s v7 — unit/pure")
    lift_times  = [stats[c]["time"]  for c in CATEGORY_ORDER if c.startswith("Lift") and c in stats]
    lift_tests  = [stats[c]["tests"] for c in CATEGORY_ORDER if c.startswith("Lift") and c in stats]
    if u and lift_tests:
        lift_avg = sum(lift_times) / sum(lift_tests)
        unit_avg = u["time"] / u["tests"]
        rows += [
            "",
            f"> **Unit/pure tests are {lift_avg/unit_avg:.0f}× faster than Lift integration tests** "
            f"({unit_avg:.3f}s vs {lift_avg:.3f}s per test).",
        ]
    return "\n".join(rows)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <reports-root-dir>", file=sys.stderr)
        sys.exit(1)

    stats = collect(sys.argv[1])
    if not stats:
        print("No matching surefire XML reports found.", file=sys.stderr)
        sys.exit(0)

    print(render_plain(stats))

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a") as f:
            f.write("\n")
            f.write(render_markdown(stats))
            f.write("\n")
