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

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import code.connector.RestConnector_vMar2019_FrozenUtil
import org.scalatest.{FlatSpec, Matchers}

/**
 * Keeps the two frozen-contract fixtures reviewable: each Java-serialized blob has a checked-in
 * text sibling, and this fails when the two disagree, so a regeneration cannot land as an
 * unreadable binary diff. See [[FrozenMetaDataText]] for why they exist and how to regenerate them.
 *
 * This only compares. It does not write - a test that repairs the tree it is checking hides the
 * thing it was added to surface, and would leave a release build with a file nobody reviewed.
 */
class FrozenMetaDataTextTest extends FlatSpec with Matchers {

  private def checkFixture(blobPath: String, render: String => String): Unit = {
    assume(new File(blobPath).exists(), s"fixture not persisted yet: $blobPath")

    val textPath = Paths.get(FrozenMetaDataText.textPathOf(blobPath))
    withClue(s"${textPath.getFileName} is missing; run code.util.FrozenMetaDataText to write it: ") {
      Files.exists(textPath) shouldBe true
    }

    val actual = new String(Files.readAllBytes(textPath), StandardCharsets.UTF_8)
    withClue(s"${textPath.getFileName} is out of date with its blob; " +
      s"run code.util.FrozenMetaDataText, then review the diff: ") {
      actual should equal(render(blobPath))
    }
  }

  "frozen_type_meta_data" should "match its checked-in text rendering" in {
    checkFixture(FrozenClassUtil.persistFilePath, FrozenMetaDataText.renderFrozenApiInfo)
  }

  "RestConnector_vMar2019_frozen_meta_data" should "match its checked-in text rendering" in {
    checkFixture(RestConnector_vMar2019_FrozenUtil.persistFilePath, FrozenMetaDataText.renderConnectorInfo)
  }
}
