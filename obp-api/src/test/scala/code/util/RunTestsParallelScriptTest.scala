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

import org.scalatest.{FlatSpec, Matchers}

import java.io.File
import scala.io.Source

/**
 * Pins that run_tests_parallel.sh's zero-test-floor diagnostic message quotes the same number
 * it actually compares against.
 *
 * The floor check reads:
 *
 *     if [[ "${SF_TOTAL:-0}" -lt 3200 ]]; then
 *         echo "  ✗ suspicious total: only ${SF_TOTAL:-0} tests ran (< 2000 floor) ..."
 *
 * The threshold was raised from 2000 to 3200 (see the script's own comment: "3200 is 90% of the
 * 3571 measured on develop-obp") but the message text was not updated alongside it. A run that
 * produces, say, 2500 tests is correctly failed by the `-lt 3200` check, but the printed
 * diagnostic reads "only 2500 tests ran (< 2000 floor)" -- which is arithmetically
 * self-contradictory (2500 is not less than 2000) to whoever is reading the CI log to work out
 * why the build failed.
 *
 * A runtime test cannot exercise a bash script's own comparison, so this reads the script's
 * source and asserts the number in the `-lt` comparison matches the number quoted in the message
 * -- the same drift-guard shape SweepCoverageDriftCheckTest uses for a Scala file.
 */
class RunTestsParallelScriptTest extends FlatSpec with Matchers {

  private def scriptSource: String = {
    val candidates = List(
      new File("run_tests_parallel.sh"),
      new File("../run_tests_parallel.sh")
    )
    val file = candidates.find(_.isFile).getOrElse(
      fail(s"Cannot locate run_tests_parallel.sh under either candidate path - this guard must " +
           s"not pass by failing to look. Tried: ${candidates.mkString(", ")}"))
    val source = Source.fromFile(file, "UTF-8")
    try source.mkString finally source.close()
  }

  "the zero-test floor diagnostic" should "quote the same threshold it actually compares against" in {
    val src = scriptSource

    val comparisonThreshold = """\$\{SF_TOTAL:-0\}"\s*-lt\s*(\d+)""".r
      .findFirstMatchIn(src).map(_.group(1)).getOrElse(
        fail("could not find the zero-test floor comparison (\"${SF_TOTAL:-0}\" -lt N) in the " +
             "script - this guard must not pass by failing to look"))

    val messageThreshold = """\(<\s*(\d+)\s+floor\)""".r
      .findFirstMatchIn(src).map(_.group(1)).getOrElse(
        fail("could not find the \"(< N floor)\" diagnostic text in the script - this guard " +
             "must not pass by failing to look"))

    withClue(s"the script compares against $comparisonThreshold but tells the reader the floor " +
             s"is $messageThreshold -- whichever one is stale, a CI failure reads as " +
             s"self-contradictory until they match: ") {
      messageThreshold shouldBe comparisonThreshold
    }
  }
}
