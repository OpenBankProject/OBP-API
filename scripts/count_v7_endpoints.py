#!/usr/bin/env python3
"""
Count the OBP endpoints reachable via /obp/v7.0.0/.

This statically reproduces what `Http4s700.allResourceDocs` computes at runtime.

Aggregation chain (from the OBPAPIxxx.scala / Http4sXYZ.scala source):

  OBPAPI1_4_0.allResourceDocs = APIMethods140 ++ APIMethods130 ++ APIMethods121   (plain concat)
  OBPAPI2_0_0  = collectResourceDocs(OBPAPI1_4_0, Http4s200)
  OBPAPI2_1_0  = collectResourceDocs(OBPAPI2_0_0,  Http4s210)
  OBPAPI2_2_0  = collectResourceDocs(OBPAPI2_1_0,  Http4s220)
  OBPAPI3_0_0  = collectResourceDocs(OBPAPI2_2_0,  Http4s300)
  OBPAPI3_1_0  = collectResourceDocs(OBPAPI3_0_0,  Http4s310)
  OBPAPI4_0_0  = collectResourceDocs(OBPAPI3_1_0,  Http4s400).filterNot(v4 excludeEndpoints)
  OBPAPI5_0_0  = collectResourceDocs(OBPAPI4_0_0,  Http4s500)
  OBPAPI5_1_0  = collectResourceDocs(OBPAPI5_0_0,  Http4s510).filterNot(v5.1 excludeEndpoints)
  OBPAPI6_0_0  = collectResourceDocs(OBPAPI5_1_0,  Http4s600).filterNot(v6 excludeEndpoints)
  Http4s700.allResourceDocs = collectResourceDocs(OBPAPI6_0_0, Http4s700)   (v7 excludeEndpoints = Nil)

collectResourceDocs: concatenate, stable-sort by API version DESCENDING, then keep
the first ResourceDoc seen for each (requestUrl, requestVerb) pair.
filterNot: drop any ResourceDoc whose partialFunctionName exactly equals an
excluded name (Scala `String.matches(names.mkString("|"))` is a whole-string match).

Run:  python3 scripts/count_v7_endpoints.py
"""

import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "obp-api", "src", "main", "scala", "code", "api")

# file -> API version (as a sortable (major, minor, patch) tuple)
VERSION_FILES = [
    ("v1_2_1/APIMethods121.scala", (1, 2, 1)),
    ("v1_3_0/APIMethods130.scala", (1, 3, 0)),
    ("v1_4_0/APIMethods140.scala", (1, 4, 0)),
    ("v2_0_0/Http4s200.scala",     (2, 0, 0)),
    ("v2_1_0/Http4s210.scala",     (2, 1, 0)),
    ("v2_2_0/Http4s220.scala",     (2, 2, 0)),
    ("v3_0_0/Http4s300.scala",     (3, 0, 0)),
    ("v3_1_0/Http4s310.scala",     (3, 1, 0)),
    ("v4_0_0/Http4s400.scala",     (4, 0, 0)),
    ("v5_0_0/Http4s500.scala",     (5, 0, 0)),
    ("v5_1_0/Http4s510.scala",     (5, 1, 0)),
    ("v6_0_0/Http4s600.scala",     (6, 0, 0)),
    ("v7_0_0/Http4s700.scala",     (7, 0, 0)),
]

# excludeEndpoints lists, taken verbatim from the OBPAPIxxx.scala source.
# Applied as filterNot(partialFunctionName) at the level keyed below.
EXCLUDE = {
    (4, 0, 0): {
        "addPermissionForUserForBankAccountForMultipleViews",
        "removePermissionForUserForBankAccountForAllViews",
        "addPermissionForUserForBankAccountForOneView",
        "removePermissionForUserForBankAccountForOneView",
        "createAccount",
        "revokeConsent",
    },
    (5, 1, 0): {
        "getUserByUsername", "getBadLoginStatus", "unlockUser", "lockUser",
        "createUserWithAccountAccess", "grantUserAccessToView",
        "revokeUserAccessToView", "revokeGrantUserAccessToViews",
    },
    (6, 0, 0): {
        "getUserByUsername", "getBadLoginStatus", "unlockUser", "lockUser",
        "createUserWithAccountAccess", "grantUserAccessToView",
        "revokeUserAccessToView", "revokeGrantUserAccessToViews",
        "getMyPersonalUserAttributes", "createMyPersonalUserAttribute",
        "updateMyPersonalUserAttribute", "createNonPersonalUserAttribute",
        "getNonPersonalUserAttributes", "deleteNonPersonalUserAttribute",
    },
}

REG_START = re.compile(r'^\s*(?:static)?[Rr]esourceDocs\s*\+=\s*ResourceDoc\s*\(')
HTTP_VERBS = {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"}


class Doc:
    __slots__ = ("verb", "url", "func", "version", "src")

    def __init__(self, verb, url, func, version, src):
        self.verb, self.url, self.func = verb, url, func
        self.version, self.src = version, src

    def key(self):
        return (self.url, self.verb)


def split_top_level_args(text, want):
    """Return the first `want` comma-separated args of a Scala call.

    `text` starts just after the opening '(' of ResourceDoc(...). Tracks string
    literals (incl. triple-quoted), nested brackets, and // and /* */ comments
    so commas inside them are not treated as separators.
    """
    args, buf = [], []
    depth = 0
    i, n = 0, len(text)
    while i < n and len(args) < want:
        c = text[i]
        two = text[i:i + 2]
        three = text[i:i + 3]
        if three == '"""':                       # triple-quoted string
            end = text.find('"""', i + 3)
            if end == -1:
                buf.append(text[i:]); break
            buf.append(text[i:end + 3]); i = end + 3; continue
        if c == '"':                             # single-quoted string
            j = i + 1
            while j < n:
                if text[j] == '\\':
                    j += 2; continue
                if text[j] == '"':
                    break
                j += 1
            buf.append(text[i:j + 1]); i = j + 1; continue
        if two == '//':                          # line comment
            end = text.find('\n', i)
            i = n if end == -1 else end
            continue
        if two == '/*':                          # block comment
            end = text.find('*/', i + 2)
            i = n if end == -1 else end + 2
            continue
        if c in '([{':
            depth += 1; buf.append(c); i += 1; continue
        if c in ')]}':
            if depth == 0:                       # closing ResourceDoc(
                break
            depth -= 1; buf.append(c); i += 1; continue
        if c == ',' and depth == 0:
            args.append("".join(buf)); buf = []; i += 1; continue
        buf.append(c); i += 1
    if buf and len(args) < want:
        args.append("".join(buf))
    return args


def strip_str(literal):
    s = literal.strip()
    if s.startswith("s"):           # interpolator prefix
        s = s[1:].strip()
    if s.startswith('"""') and s.endswith('"""'):
        return s[3:-3]
    if s.startswith('"') and s.endswith('"'):
        return s[1:-1]
    return s


def parse_file(path, version):
    with open(path, encoding="utf-8") as fh:
        lines = fh.readlines()
    docs = []
    for idx, line in enumerate(lines):
        if not REG_START.match(line):
            continue
        # gather a generous window of source from the opening paren onward
        chunk = "".join(lines[idx:idx + 60])
        chunk = chunk[chunk.index("ResourceDoc") + len("ResourceDoc"):]
        chunk = chunk[chunk.index("(") + 1:]
        args = split_top_level_args(chunk, want=5)
        if len(args) < 5:
            print(f"  WARN: could not parse args at {path}:{idx + 1}", file=sys.stderr)
            continue
        func_arg, verb_arg, url_arg = args[2], args[3], args[4]
        m = re.search(r'nameOf\s*\(\s*[\w.]*?(\w+)\s*\)', func_arg)
        func = m.group(1) if m else strip_str(func_arg)
        verb = strip_str(verb_arg).upper()
        url = strip_str(url_arg)
        if verb not in HTTP_VERBS:
            print(f"  WARN: unexpected verb {verb!r} at {path}:{idx + 1}", file=sys.stderr)
            continue
        docs.append(Doc(verb, url, func, version, f"{os.path.basename(path)}:{idx + 1}"))
    return docs


def collect(*buckets):
    """Reproduce collectResourceDocs: concat, stable sort by version desc, dedup by (url,verb)."""
    merged = [d for bucket in buckets for d in bucket]
    merged.sort(key=lambda d: d.version, reverse=True)   # stable
    seen, out = set(), []
    for d in merged:
        if d.key() not in seen:
            seen.add(d.key())
            out.append(d)
    return out


def filter_not(docs, excluded_names):
    return [d for d in docs if d.func not in excluded_names]


def main():
    by_version = {}
    print("Parsing ResourceDoc registrations:")
    for rel, version in VERSION_FILES:
        path = os.path.join(SRC, rel)
        if not os.path.isfile(path):
            sys.exit(f"ERROR: missing source file {path}")
        docs = parse_file(path, version)
        by_version[version] = docs
        print(f"  {rel:30s} v{'.'.join(map(str, version))}: {len(docs):4d} docs")
    print()

    # OBPAPI1_4_0.allResourceDocs = plain ++ (no dedup, no filter)
    level = by_version[(1, 4, 0)] + by_version[(1, 3, 0)] + by_version[(1, 2, 1)]

    steps = [
        ((2, 0, 0), None), ((2, 1, 0), None), ((2, 2, 0), None),
        ((3, 0, 0), None), ((3, 1, 0), None),
        ((4, 0, 0), EXCLUDE[(4, 0, 0)]),
        ((5, 0, 0), None),
        ((5, 1, 0), EXCLUDE[(5, 1, 0)]),
        ((6, 0, 0), EXCLUDE[(6, 0, 0)]),
        ((7, 0, 0), None),
    ]
    excluded_log = []
    for version, excl in steps:
        level = collect(level, by_version[version])
        if excl:
            removed = [d for d in level if d.func in excl]
            excluded_log += [(version, d) for d in removed]
            level = filter_not(level, excl)

    final = level

    # transparency: did any exclusion remove a (url,verb) that another doc also serves?
    final_keys = {d.key() for d in final}
    shadow = [(v, d) for v, d in excluded_log if d.key() in final_keys]

    print("=" * 66)
    print("OBP endpoints reachable via /obp/v7.0.0/")
    print("=" * 66)
    print(f"v7.0.0 native (http4s) endpoints       : {len(by_version[(7,0,0)]):4d}")
    print(f"Total reachable (aggregated + deduped) : {len(final):4d}")
    print()

    wins = {}
    for d in final:
        wins[d.version] = wins.get(d.version, 0) + 1
    print("Owned by version (newest wins on URL+verb clash):")
    for version in sorted(wins, reverse=True):
        print(f"  v{'.'.join(map(str, version)):<14s} {wins[version]:4d}")
    print()

    verbs = {}
    for d in final:
        verbs[d.verb] = verbs.get(d.verb, 0) + 1
    print("By HTTP method:")
    for verb in sorted(verbs, key=lambda v: -verbs[v]):
        print(f"  {verb:<8s} {verbs[verb]:4d}")
    print()

    print(f"Endpoints removed by excludeEndpoints filters: {len(excluded_log)}")
    for version, d in excluded_log:
        print(f"  v{'.'.join(map(str, version))} drops {d.verb:6s} {d.url}  ({d.func})")
    if shadow:
        print()
        print("NOTE: an excluded endpoint shared a (url,verb) still served by "
              "another doc — count may need a closer look:")
        for version, d in shadow:
            print(f"  {d.verb} {d.url} ({d.func})")


if __name__ == "__main__":
    main()
