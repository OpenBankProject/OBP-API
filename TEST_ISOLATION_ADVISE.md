# Test Isolation: Don't Mutate Global State at Class Init

## The rule

**Every `setPropsValues(...)`, `LiftRules.*`, or other global-state mutation MUST live inside a `scenario(...)` block — never at `feature(...)` body level.**

```scala
// ❌ WRONG — runs at class instantiation, before any test
feature("...") {
  setPropsValues("api_enabled_versions" -> "[OBPv4.0.0]")
  someAssertion()
}

// ✅ RIGHT — runs only when the scenario executes
feature("...") {
  scenario("only OBPv4.0.0 is enabled") {
    setPropsValues("api_enabled_versions" -> "[OBPv4.0.0]")
    someAssertion()
  }
}
```

## Why it matters

ScalaTest's `feature(...)` body is invoked **immediately** when the test class is instantiated. During discovery, ScalaTest instantiates every test suite to register its scenarios — so any code at feature-body level runs **before any test in any suite executes**.

`PropsReset` snapshots `lockedProviders` once, at instantiation. `afterEach` restores **that snapshot**. So:

1. Test class **A** is instantiated first → `PropsReset` captures clean state.
2. Test class **B** is instantiated next → its feature-body `setPropsValues` mutates global Props.
3. **A**'s first scenario runs → sees **B**'s polluted Props → fails.
4. **A**'s `afterEach` restores **A**'s clean snapshot → all subsequent scenarios in **A** pass.

The signature symptom: **only the first scenario of one suite fails**, every other test in the same suite passes, and the failure disappears when that suite is run alone. The polluting suite often runs successfully because its own scenarios test the mutations it makes.

## Verifying

To diagnose suspected pollution:

```bash
# Reproduce: passes alone, fails when paired
mvn test -pl obp-api -DwildcardSuites="<suspect-suite>"           # passes
mvn test -pl obp-api -DwildcardSuites="<suspect>,<other-suite>"   # fails

# Find culprits
grep -rn "setPropsValues\|LiftRules\." obp-api/src/test/scala \
  | grep -v "scenario\|//\s*"   # rough filter
```

Any hit at a feature-body level (between `feature("...") {` and the first `scenario`, with no `scenario` wrapper) is a candidate.

## Wider applicability

The same rule covers anything that touches process-global state:

- `LiftRules.statelessDispatch` / `statefulDispatch` modifications
- `System.setProperty`
- Static singletons (`SomeObject.foo = ...`)
- Mutable connector-vendor assignments (`Connector.connector.default.set(...)`)
- Resource-doc list mutations

If it persists past the current request and there's no `afterEach` reverting it, keep it inside `scenario { ... }`.

## Enforcement (in this repo)

Two defenses are in place; both should catch the same mistakes from different angles.

**Runtime guard** — `code/setup/PropsReset.scala` keeps a `ThreadLocal[Boolean]` flag that `beforeAll`/`beforeEach` flip on and `afterEach`/`afterAll` flip off. `setPropsValues` throws `IllegalStateException` if it's called when the flag is false (i.e. at class/trait/feature-body level). You see this the first time you run the offending test, with a stack trace pointing at the line and a message referencing this file.

**Static lint** — `.github/scripts/check_test_isolation.py` scans every `*.scala` file under `obp-api/src/test/scala`, tracks brace-depth scoping, and fails the build if a `setPropsValues(` call appears outside a `scenario`, `def`, or `before*`/`after*` block. The check runs in `.github/workflows/build_pull_request.yml` as the `Lint — test-isolation` step, before `mvn install`.

If you need a baseline prop set before tests run (e.g. something `Migration.scala` reads at class-load time), put it in `obp-api/src/main/resources/props/test.default.props` — that file is read by Lift Props before Boot and isn't affected by `PropsReset.afterEach`.

## TL;DR

`feature { ... }` runs at class load. `scenario { ... }` runs when the test runs. Put side-effects in `scenario`. The runtime guard and CI lint will catch you if you forget.
