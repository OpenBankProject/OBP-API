package code.setup

import net.liftweb.util.Props
import org.apache.commons.lang3.reflect.FieldUtils
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}


/**
 * Test isolation for `Props.lockedProviders`.
 *
 * Problem this trait solves
 * -------------------------
 *
 * `Props.lockedProviders` is a process-global stack of Map overrides on top of
 * the property files. Any test mutation to it leaks across the JVM. In
 * single-fork test runs (Jenkins `forkMode=once`, ~2800 suites in one JVM) the
 * leak is permanent for the rest of the run unless explicitly unwound.
 *
 * ScalaTest instantiates every test suite up-front, before running any test.
 * Code at trait-body or feature-body level (outside any `scenario {}`) thus
 * mutates `Props.lockedProviders` *before any beforeAll fires*. Two consequences:
 *
 *   1. Any "snapshot at trait construction" approach captures pollution made by
 *      other suites' construction-time pushes — the pollution is then encoded
 *      as the captured baseline and resurrected on every afterEach. (This is
 *      the bug Jenkins #7861 hit: APIUtilHeavyTest pushed
 *      `api_enabled_versions=[OBPv4.0.0]` at feature-body level, every
 *      subsequent suite's snapshot captured it, every v6/v7 request 404'd.)
 *
 *   2. Simple per-suite tracking ("undo what I pushed") doesn't fix it either,
 *      because suite N cannot remove suite M's tagged pushes — suite M hasn't
 *      run yet, so N's afterEach sees M's pollution and is powerless.
 *
 * Design
 * ------
 *
 * Process-global ownership tracking + per-suite snapshot at first beforeEach.
 *
 *   - Every `setPropsValues` push is registered in a JVM-global identity set
 *     (`PropsReset.ownedMaps`). The set is shared across all suite instances.
 *   - `beforeAll` (PropsReset side): WIPE all owned maps from
 *     `Props.lockedProviders` **before** chaining `super.beforeAll()`. This
 *     removes every construction-time push made by every suite (including this
 *     one) and every prior-suite baseline. The wipe runs early in the super-
 *     chain because PropsReset is the rightmost trait in `ServerSetup`'s mixin
 *     order; `super.beforeAll()` then unwinds to parent traits' beforeAll
 *     overrides, including `ServerSetup.beforeAll` which (re-)pushes this
 *     suite's baselines.
 *   - `beforeEach` (PropsReset side): on first invocation, capture
 *     `lockedProviders` as the per-suite snapshot. By this point all
 *     beforeAll-chain pushes (e.g. ServerSetup's baselines) have happened, so
 *     the snapshot is "clean pristine + this suite's baselines".
 *   - `afterEach`: restore to snapshot. Removes any scenario-local mutations.
 *   - `afterAll`: wipe owned maps. Ensures the next suite's `beforeAll` doesn't
 *     have to re-wipe (defensive — even if a future suite is misbuilt and
 *     skips PropsReset, the previous suite cleans up after itself).
 *
 * Why "owned" identity tracking (not structural equality) and not size-based
 * tracking:
 *   - Two suites may push the same key=value content. Removing by structural
 *     equality would clobber pushes that other suites still need.
 *   - Size-based tracking assumes only this suite mutates the stack, which is
 *     not true under cross-suite contamination.
 *   - Identity tracking with `IdentityHashMap` is the only invariant that
 *     uniquely identifies a push's origin.
 *
 * Constraints on callers
 * ----------------------
 *
 *   - **`setPropsValues` is the only sanctioned writer.** Anything that writes
 *     `Props.lockedProviders` directly (via reflection or otherwise) won't be
 *     in `ownedMaps`, so the wipe won't touch it. That's intended — true
 *     baselines (e.g. from property files via Lift's own loader) live outside
 *     this set and are preserved.
 *   - **Baseline pushes that should persist for a suite's duration MUST go in
 *     `beforeAll`** (not in trait/class body). See `ServerSetup.beforeAll`.
 *     Body-level pushes get wiped by this trait's beforeAll before they have
 *     any effect.
 */
object PropsReset {
  // JVM-global set of every Map ever pushed via `setPropsValues`. Identity-based
  // membership: each push is a fresh Map instance, so adding the same key=value
  // twice creates two separate entries (and both can be tracked/removed
  // independently).
  private val ownedMaps: java.util.Set[Map[String, String]] = {
    val backing = new java.util.IdentityHashMap[Map[String, String], java.lang.Boolean]()
    java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(backing))
  }

  private[setup] def registerOwned(m: Map[String, String]): Unit = ownedMaps.add(m)

  private[setup] def isOwned(m: Map[String, String]): Boolean = ownedMaps.contains(m)
}

trait PropsReset extends BeforeAndAfterAll with BeforeAndAfterEach {
  this: Suite =>

  private var snapshot: List[Map[String, String]] = Nil
  private var snapshotTaken: Boolean = false

  override def beforeAll(): Unit = {
    // STEP 1: wipe pollution. This runs BEFORE super.beforeAll() chains to
    // ServerSetup/other parent beforeAll overrides. Removes every owned map
    // currently on Props.lockedProviders — i.e. every construction-time push
    // from any suite, plus any leftover from a previous suite's baselines.
    val current = getLockedProviders
    writeLockedProviders(current.filterNot(PropsReset.isOwned))

    // STEP 2: chain. ServerSetup.beforeAll (and any other ancestor) will push
    // this suite's baselines onto the now-clean stack.
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    // Snapshot once, after the first beforeAll-chain has completed and all
    // beforeEach pushes (e.g. ServerSetup's berlin_group re-application) have
    // fired. afterEach restores to this state.
    if (!snapshotTaken) {
      snapshot = getLockedProviders
      snapshotTaken = true
    }
  }

  override def afterEach(): Unit = {
    super.afterEach()
    if (snapshotTaken) writeLockedProviders(snapshot)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    // Defensive: wipe everything this trait class registered, so the next suite
    // starts from a known state regardless of what we left behind.
    val current = getLockedProviders
    writeLockedProviders(current.filterNot(PropsReset.isOwned))
  }

  private def getLockedProviders: List[Map[String, String]] = {
    // Props initializes lazily, so this field is null until something has touched Props. A
    // suite that mixes in this trait without otherwise using Props (a pure unit suite, say)
    // would then NPE in beforeAll before running a single test - which is a confusing way to
    // learn that the suite needed to touch Props first. Reading Props.mode forces the
    // initializer; the null fallback covers the field simply not being populated yet.
    Props.mode
    Option(FieldUtils.readDeclaredField(Props, "net$liftweb$util$Props$$lockedProviders", true))
      .map(_.asInstanceOf[List[Map[String, String]]])
      .getOrElse(Nil)
  }

  private def writeLockedProviders(value: List[Map[String, String]]): Unit = {
    FieldUtils.writeDeclaredField(Props, "net$liftweb$util$Props$$lockedProviders", value, true)
  }

  def setPropsValues(keyValues: (String, String)*): Unit = {
    // Always create a fresh Map instance — identity-based tracking requires
    // every push to be a distinct object so that removing it later cannot
    // accidentally affect another caller's push of the same content.
    val m: Map[String, String] = Map(keyValues: _*)
    PropsReset.registerOwned(m)
    writeLockedProviders(m :: getLockedProviders)
  }
}
