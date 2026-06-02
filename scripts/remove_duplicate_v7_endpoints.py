#!/usr/bin/env python3
"""
Remove the 19 duplicate v7.0.0 endpoints from Http4s700.scala so they cascade to
their v6/v4 twin. See ~/.claude/plans/plan-cozy-yao.md and
todo_remove_duplicate_v7.0.0_endpoints.md (Appendix B).

Each endpoint is a `val NAME: HttpRoutes[IO] = ...` immediately followed by its
`resourceDocs += ResourceDoc(... http4sPartialFunction = Some(NAME))` block. This
deletes both (plus any immediately-preceding `//` comment lines and one trailing
blank line), asserts no block overlaps, and prints what it removed. Idempotent.
"""
import re

f = "obp-api/src/main/scala/code/api/v7_0_0/Http4s700.scala"
lines = open(f).read().split("\n")

# All 19 endpoints (16 clean + 3 divergent: getCurrentUser, getUsers,
# getExplicitCounterpartyById). The 3 KEPT (deleteEntitlement/addEntitlement/
# getUserByUserId — improved response codes) are deliberately NOT here.
targets = [
    "getBank", "getCoreAccountById", "getPrivateAccountByIdFull", "getAccountsAtBank",
    "getStoredProcedureConnectorHealth", "getMigrations", "getCacheNamespaces",
    "getCacheConfig", "getCacheInfo", "getDatabasePoolInfo", "getFeatures",
    "getConnectors", "getProviders", "getScannedApiVersions", "getCustomersAtOneBank",
    "getCustomerByCustomerId", "getCurrentUser", "getUsers", "getExplicitCounterpartyById",
]

val_re  = {t: re.compile(rf"^\s*(lazy )?val {t}: HttpRoutes\[IO\]") for t in targets}
some_re = {t: re.compile(rf"http4sPartialFunction = Some\({t}\)\s*$") for t in targets}

blocks = []
for t in targets:
    vi = next(i for i, l in enumerate(lines) if val_re[t].search(l))
    si = next(i for i, l in enumerate(lines) if some_re[t].search(l))
    assert si > vi, t
    ci = next(i for i in range(si + 1, len(lines)) if re.match(r"^\s*\)\s*$", lines[i]))
    start = vi
    while start - 1 >= 0 and re.match(r"^\s*//", lines[start - 1]):
        start -= 1
    blocks.append((start, ci, t))

blocks.sort()
for (a, b, t), (c, d, t2) in zip(blocks, blocks[1:]):
    assert b < c, f"OVERLAP {t} vs {t2}"

to_del = set()
for a, b, t in blocks:
    end = b
    if end + 1 < len(lines) and lines[end + 1].strip() == "":
        end += 1
    to_del.update(range(a, end + 1))

open(f, "w").write("\n".join(l for i, l in enumerate(lines) if i not in to_del))
print(f"removed {len(to_del)} lines across {len(targets)} endpoints; resourceDoc count should drop 64 -> 45")
