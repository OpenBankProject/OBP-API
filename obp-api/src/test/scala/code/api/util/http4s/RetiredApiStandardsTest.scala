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

package code.api.util.http4s

import code.api.util.ScannedApis
import code.api.v4_0_0.V400ServerSetup
import org.scalatest.Tag

/**
 * Regression-guard for the "retire by commenting-out" pattern used to take
 * non-OBP Lift API standards (BahrainOBF, AUOpenBanking, STET, Polish, MxOF/CNBV9)
 * off the bridge in PR #2814 (commit `d19af2b92`, 2026-05-22).
 *
 * Background: each standard is registered with Lift via reflection in
 * `ScannedApis.versionMapScannedApis`, which calls
 * `ClassScanUtils.getSubTypeObjects[ScannedApis]`. Commenting out the source
 * removes the `OBPAPIxxx` objects from the classpath, so the reflection finds
 * nothing and the routes stop being served. The disable is "structural" — no
 * `Boot.scala` change — which makes it cheap but also reversible by accident:
 * a partial uncomment that brings back even one object would silently
 * re-register that standard at startup.
 *
 * This test asserts the inverse: at the moment of the scan, none of the
 * `ScannedApis` instances live in a package matching the five retired
 * standards. If any does, someone has put a piece of one of those standards
 * back in business; review what they uncommented and decide whether that's
 * intentional.
 *
 * The test sits in the v4 server-setup hierarchy (same hierarchy as
 * `ApiVersionUtilsTest`) because the scan only sees compiled classes — a
 * fresh test JVM with the test classpath has the right view.
 */
class RetiredApiStandardsTest extends V400ServerSetup {

  object RetiredStandardsTag extends Tag("RetiredStandards")

  // Packages whose every concrete object/class extends `ScannedApis`. Any
  // entry showing up under one of these prefixes means a once-retired
  // standard has been (partially) brought back to life.
  private val retiredPackagePrefixes: Set[String] = Set(
    "code.api.BahrainOBF.",
    "code.api.AUOpenBanking.",
    "code.api.STET.",
    "code.api.Polish.",
    "code.api.MxOF."
  )

  feature("Retired API standards stay retired") {

    scenario("ScannedApis registry must not contain any object from a retired-standard package", RetiredStandardsTag) {
      Given("`ScannedApis.versionMapScannedApis` is built via `ClassScanUtils.getSubTypeObjects`")
      val scanned = ScannedApis.versionMapScannedApis.values.toList

      When("we inspect each scanned object's fully-qualified class name")
      val resurrected: List[(String, String)] =
        scanned.flatMap { obj =>
          val cls = obj.getClass.getName
          retiredPackagePrefixes.collectFirst {
            case pkg if cls.startsWith(pkg) => (pkg.stripSuffix("."), cls)
          }
        }

      Then(s"no scanned object should live in a retired-standard package, but found: $resurrected")
      resurrected shouldBe empty
    }
  }
}
