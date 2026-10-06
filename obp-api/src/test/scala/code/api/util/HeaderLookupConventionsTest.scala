package code.api.util

import java.io.File

import org.scalatest.{FeatureSpec, Matchers}

import scala.io.Source

/**
 * This class keeps every request header lookup going through RequestHeadersUtil.
 *
 * HTTP header names are case-insensitive, and over HTTP/2 every name arrives in lower case. A lookup
 * written as `requestHeaders.find(_.name == "Consent-JWT")` works in a test that sends `Consent-JWT`
 * and silently misses the header from a real HTTP/2 client. RequestHeadersUtil matches names ignoring
 * case; this suite reads the main sources and fails on a lookup that compares a header name itself.
 *
 * It is a text scan, so it recognises a header list by its variable name: `requestHeaders`,
 * `reqHeaders` or `headers`, on the line of the comparison or one of the two lines before it (a
 * lookup is often split across lines). It does not need a database or a server.
 */
class HeaderLookupConventionsTest extends FeatureSpec with Matchers {

  // Maven runs the suite from obp-api/, an IDE often from the repository root.
  private val sourceRoot: File =
    List(new File("src/main/scala"), new File("obp-api/src/main/scala")).find(_.isDirectory)
      .getOrElse(throw new IllegalStateException("HeaderLookupConventionsTest cannot find obp-api/src/main/scala"))

  private val headerList = """\b(requestHeaders|reqHeaders|headers)\b""".r

  /** A comparison of a name itself: `==` or `!=`, equals or equalsIgnoreCase, a lower-cased name used as a key, or a pattern binding the name. */
  private val nameComparison =
    """\.name\s*(==|!=)|\.name\.(equalsIgnoreCase|equals)\(|\.name\.toLowerCase(\([^)]*\))?\s*(==|->)|groupBy\(_\.name|HTTPParam\(\s*name\s*,""".r

  /** This returns the 1-based numbers of the lines in `text` that compare a request header name directly. */
  private def directLookups(text: String): List[Int] = {
    val lines = text.split("\n", -1).toList
    lines.indices.toList.filter { index =>
      nameComparison.findFirstIn(lines(index)).isDefined &&
        headerList.findFirstIn(lines.slice(math.max(0, index - 2), index + 1).mkString(" ")).isDefined
    }.map(_ + 1)
  }

  private def scalaFiles(dir: File): List[File] =
    Option(dir.listFiles).toList.flatten.flatMap(file => if (file.isDirectory) scalaFiles(file) else List(file))
      .filter(_.getName.endsWith(".scala"))

  private def read(file: File): String = {
    val source = Source.fromFile(file, "UTF-8")
    try source.mkString finally source.close()
  }

  feature("Request headers are looked up through RequestHeadersUtil") {

    scenario("The scan recognises a direct lookup and passes one through the helper") {
      directLookups("""requestHeaders.find(_.name == "Consent-JWT")""") shouldBe List(1)
      directLookups("""reqHeaders.exists(_.name.equalsIgnoreCase(DAuthHeaderKey))""") shouldBe List(1)
      directLookups("val raw = requestHeaders\n  .find(_.name.toLowerCase() == name.toLowerCase())") shouldBe List(2)
      directLookups("""val byName = reqHeaders.map(h => h.name.toLowerCase -> h).toMap""") shouldBe List(1)
      directLookups("""callContext.requestHeaders.groupBy(_.name.toLowerCase)""") shouldBe List(1)
      directLookups("requestHeaders.collectFirst {\n  case HTTPParam(name, value :: _) if name == \"Force-Error\" => value") shouldBe List(2)

      directLookups("""RequestHeadersUtil.find(requestHeaders, "Consent-JWT")""") shouldBe Nil
      directLookups("""requestHeaders.map(header => header.name + ": " + header.values.mkString)""") shouldBe Nil
      directLookups("""attributes.find(_.name == "CERTIFICATE_CA_NAME")""") shouldBe Nil
    }

    scenario("No main source compares a request header name itself") {
      val files = scalaFiles(sourceRoot).filterNot(_.getName == "RequestHeadersUtil.scala")
      withClue("the scan must be reading the main sources: ") { files.size should be > 1000 }
      val found = files.flatMap { file =>
        val path = sourceRoot.toPath.relativize(file.toPath).toString
        directLookups(read(file)).map(line => s"$path:$line")
      }.sorted
      withClue("These lines compare a request header name directly. Use RequestHeadersUtil.find, exists, " +
        "findSingle or isNamed, which ignore letter case as HTTP requires:\n" + found.mkString("\n") + "\n") {
        found shouldBe empty
      }
    }
  }
}
