package code.util

import code.setup.ServerSetup
import org.scalatest.Tag

/**
 * Regenerates the frozen-API snapshot (`frozen_type_meta_data` and its `.txt` rendering) from a
 * normal Maven test run, so the generator runs on the reactor classpath and inside the same
 * server bootstrap as FrozenClassTest. Gated on the environment variable FROZEN_REGENERATE=true;
 * without it the scenario cancels, so a CI run can never rewrite the snapshot and hide drift.
 *
 * Usage (see README "Steps to freeze an API"):
 *   FROZEN_REGENERATE=true mvn -pl obp-api -am test -DwildcardSuites=code.util.FrozenSnapshotGenerate
 * then review `git diff obp-api/src/test/resources/` and commit both files.
 */
class FrozenSnapshotGenerate extends ServerSetup {

  object FrozenSnapshotTag extends Tag("Frozen_Snapshot")

  feature("Regenerate the frozen API snapshot") {
    scenario("write frozen_type_meta_data and its text rendering (only when FROZEN_REGENERATE=true)", FrozenSnapshotTag) {
      assume(sys.env.get("FROZEN_REGENERATE").exists(_.equalsIgnoreCase("true")),
        "set FROZEN_REGENERATE=true to regenerate the frozen snapshot; otherwise this suite does nothing")
      val blob = FrozenClassUtil.writeSnapshot()
      val written = FrozenMetaDataText.writeAll()
      info(s"wrote $blob")
      written.foreach(p => info(s"wrote $p"))
      // The snapshot must describe what is on the classpath right now.
      val (persistedVersions, persistedTypes) = FrozenClassUtil.readPersistedFrozenApiInfo
      val (versions, types) = FrozenClassUtil.getFrozenApiInfo
      persistedVersions.map(_._1).toSet shouldBe versions.map(_._1).toSet
      persistedTypes.keySet shouldBe types.keySet
    }
  }
}
