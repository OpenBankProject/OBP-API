package code.util

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import code.connector.RestConnector_vMar2019_FrozenUtil
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Keeps the two frozen-contract fixtures reviewable: each Java-serialized blob has a checked-in
 * text sibling, and this fails when the two disagree, so a regeneration cannot land as an
 * unreadable binary diff. See [[FrozenMetaDataText]] for why they exist and how to regenerate them.
 *
 * This only compares. It does not write - a test that repairs the tree it is checking hides the
 * thing it was added to surface, and would leave a release build with a file nobody reviewed.
 */
class FrozenMetaDataTextTest extends AnyFlatSpec with Matchers {

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
