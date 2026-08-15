package code.util

import java.io.{FileInputStream, ObjectInputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import code.connector.RestConnector_vMar2019_FrozenUtil
import org.apache.commons.io.IOUtils

/**
 * Renders the two frozen-contract fixtures as text, and writes those renderings beside them.
 *
 * The fixtures are Java-serialized blobs. They exist to fail when a frozen type drifts, which only
 * works if a human can see what changed - and a binary diff shows nothing. The Scala 2.13 migration
 * had to regenerate both, because collections written under 2.12 do not deserialize under 2.13, and
 * that regeneration went in as two unreadable blobs. Comparing them afterwards showed the migration
 * lost nothing and added three types that became describable (APIUtil.JArrayBody, org.json4s.JArray,
 * PostAccountTagJSON), plus one rendering change in the other fixture (org.json4s.JsonAST.JValue is
 * now org.json4s.JValue, which RestConnector_vMar2019_FrozenTest already normalises). Benign - but
 * it should not have taken a hex dump to establish.
 *
 * So each blob has a `.txt` sibling, and FrozenMetaDataTextTest fails when the two disagree.
 *
 * To regenerate after a frozen type legitimately changes: run the generator for that fixture
 * (FrozenClassUtil, RestConnector_vMar2019_FrozenUtil), then run this object's main - it rewrites
 * both texts from whatever the blobs now hold. Review the text diff, and commit blob and text
 * together. This reads the blobs only, so it needs no server and no props.
 */
object FrozenMetaDataText {

  /**
   * Each blob has to be read exactly the way it was written - a mismatch surfaces as an
   * OptionalDataException rather than anything descriptive. FrozenClassUtil writes one object:
   * (List[(ApiVersion, Set[endpointName])], Map[typeName, Map[fieldName, typeRendering]]).
   */
  def readFrozenApiInfo(path: String): (List[(Any, Set[Any])], Map[Any, Any]) = {
    val input = new ObjectInputStream(new FileInputStream(path))
    try {
      input.readObject() match {
        case (versions: List[_], types: Map[_, _]) =>
          val pairs = versions.collect { case (version, names: Set[_]) => (version: Any, names.map(n => n: Any)) }
          (pairs, types.asInstanceOf[Map[Any, Any]])
        case other => sys.error(s"unexpected shape in $path: ${other.getClass.getName}")
      }
    } finally IOUtils.closeQuietly(input)
  }

  /**
   * RestConnector_vMar2019_FrozenUtil writes a UTF header, then the connector method names, then
   * the type metadata. The names are connector methods, not endpoints, and are labelled as such.
   */
  def readConnectorInfo(path: String): (List[String], Map[Any, Any]) = {
    val input = new ObjectInputStream(new FileInputStream(path))
    try {
      input.readUTF()
      val methodNames = input.readObject().asInstanceOf[List[String]]
      val types = input.readObject().asInstanceOf[Map[Any, Any]]
      (methodNames, types)
    } finally IOUtils.closeQuietly(input)
  }

  private def renderTypes(types: Map[Any, Any]): List[String] =
    types.toList.flatMap {
      case (typeName, fields: Map[_, _]) =>
        fields.toList.map { case (fieldName, fieldType) => s"field\t$typeName\t$fieldName\t$fieldType" }
      case (typeName, other) => List(s"field\t$typeName\t<unrecognised>\t$other")
    }.sorted

  /** Sorted throughout, so two runs over the same blob produce the same bytes. */
  def renderFrozenApiInfo(path: String): String = {
    val (versions, types) = readFrozenApiInfo(path)
    val endpoints = versions.flatMap { case (version, names) => names.map(n => s"endpoint\t$version\t$n") }.sorted
    (endpoints ::: renderTypes(types)).mkString("\n") + "\n"
  }

  def renderConnectorInfo(path: String): String = {
    val (methodNames, types) = readConnectorInfo(path)
    (methodNames.map(n => s"method\t$n").sorted ::: renderTypes(types)).mkString("\n") + "\n"
  }

  /** blobPath + ".txt" - the text sits beside the blob it describes rather than somewhere central. */
  def textPathOf(blobPath: String): String = blobPath + ".txt"

  def main(args: Array[String]): Unit = {
    val written = List(
      FrozenClassUtil.persistFilePath -> renderFrozenApiInfo(FrozenClassUtil.persistFilePath),
      RestConnector_vMar2019_FrozenUtil.persistFilePath -> renderConnectorInfo(RestConnector_vMar2019_FrozenUtil.persistFilePath)
    ).map { case (blobPath, text) =>
      val target = Paths.get(textPathOf(blobPath))
      Files.write(target, text.getBytes(StandardCharsets.UTF_8))
      target
    }
    written.foreach(p => println(s"wrote $p"))
    println("review the diff, then commit each blob with its text")
  }
}
