package code.util

import java.io.{File, FileInputStream, ObjectInputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import code.connector.RestConnector_vMar2019_FrozenUtil
import org.apache.commons.io.IOUtils
import org.scalatest.{FlatSpec, Matchers}

/**
 * Keeps the two frozen-contract fixtures reviewable.
 *
 * Both are Java-serialized blobs. They exist to fail when a frozen type drifts, which only works if
 * a human can see what changed - and a binary diff shows nothing. The Scala 2.13 migration had to
 * regenerate both, because collections written under 2.12 do not deserialize under 2.13, and that
 * regeneration went in as two unreadable blobs whose contents nobody could compare. Extracting the
 * strings afterwards showed the migration lost nothing and added five entries: three types that
 * became describable (APIUtil.JArrayBody, org.json4s.JArray, PostAccountTagJSON) and one type whose
 * rendering changed (org.json4s.JsonAST.JValue is now org.json4s.JValue). Benign, but it should not
 * have taken a hex dump to establish.
 *
 * So each blob now has a `.txt` sibling holding the same contract as sorted text, and this test
 * fails when the two disagree. Regenerating a blob without regenerating its text is caught here;
 * the text lands in the diff, where the change can be read.
 *
 * To regenerate: run the generator (FrozenClassUtil, RestConnector_vMar2019_FrozenUtil), then run
 * this test once - it rewrites the .txt when it is missing and tells you to review it. Commit both.
 */
class FrozenMetaDataTextTest extends FlatSpec with Matchers {

  /**
   * Renders whatever a fixture holds. The two differ in shape - one is a tuple of endpoint names
   * and type metadata, the other only type metadata - so this matches on shape rather than casting
   * to a type the file has to agree with.
   */
  private def render(value: Any): String = {
    val lines = value match {
      case (versions: List[_], types: Map[_, _]) =>
        val endpointLines = versions.collect {
          case (version, names: Set[_]) => names.map(n => s"endpoint\t$version\t$n")
        }.flatten
        endpointLines.toList.sorted ::: renderTypes(types)
      case types: Map[_, _] => renderTypes(types)
      case other => List(s"unrecognised fixture shape: ${other.getClass.getName}")
    }
    lines.mkString("\n") + "\n"
  }

  private def renderTypes(types: Map[_, _]): List[String] =
    types.toList.flatMap {
      case (typeName, fields: Map[_, _]) =>
        fields.toList.map { case (fieldName, fieldType) => s"field\t$typeName\t$fieldName\t$fieldType" }
      case (typeName, other) => List(s"field\t$typeName\t<unrecognised>\t$other")
    }.sorted

  /**
   * The two blobs are written differently and each reader has to match its writer exactly - a
   * mismatch is an OptionalDataException rather than anything descriptive. FrozenClassUtil writes
   * one object; RestConnector_vMar2019_FrozenUtil writes a UTF header, then the method names, then
   * the type metadata.
   */
  private def readSingleObject(path: String): Any = {
    val input = new ObjectInputStream(new FileInputStream(path))
    try input.readObject() finally IOUtils.closeQuietly(input)
  }

  private def readHeaderThenTwoObjects(path: String): Any = {
    val input = new ObjectInputStream(new FileInputStream(path))
    try {
      input.readUTF()
      val methodNames = input.readObject()
      val types = input.readObject()
      (List(("methods", methodNames.asInstanceOf[List[_]].toSet)), types.asInstanceOf[Map[_, _]])
    } finally IOUtils.closeQuietly(input)
  }

  private def checkFixture(blobPath: String, readBlob: String => Any): Unit = {
    val blob = new File(blobPath)
    assume(blob.exists(), s"fixture not persisted yet: $blobPath")

    val expected = render(readBlob(blobPath))
    val textPath = Paths.get(blobPath + ".txt")

    if (!Files.exists(textPath)) {
      Files.write(textPath, expected.getBytes(StandardCharsets.UTF_8))
      fail(s"generated ${textPath.getFileName} from the blob - review it and commit it alongside")
    }

    val actual = new String(Files.readAllBytes(textPath), StandardCharsets.UTF_8)
    withClue(s"${textPath.getFileName} is out of date with its blob; regenerate and review it: ") {
      actual should equal(expected)
    }
  }

  "frozen_type_meta_data" should "match its checked-in text rendering" in {
    checkFixture(FrozenClassUtil.persistFilePath, readSingleObject)
  }

  "RestConnector_vMar2019_frozen_meta_data" should "match its checked-in text rendering" in {
    checkFixture(RestConnector_vMar2019_FrozenUtil.persistFilePath, readHeaderThenTwoObjects)
  }
}
