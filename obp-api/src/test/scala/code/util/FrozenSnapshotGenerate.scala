/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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
