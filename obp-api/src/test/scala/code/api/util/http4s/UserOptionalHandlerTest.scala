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

import code.api.util.APIUtil.ResourceDoc
import code.setup.ServerSetup
import org.scalatest.Tag

import java.io.File
import scala.io.Source

/**
 * An endpoint whose error list says that a user is optional must have a handler that works without one.
 *
 * ResourceDoc.requiresApplicationButNotUser is true when the error list names ApplicationNotIdentified but not
 * AuthenticatedUserIsRequired, and the doc has no Role. The middleware then lets an identified application in
 * with no user, as the Berlin Group and UK Open Banking consent and payment endpoints need. If the handler then
 * insists on a user anyway (an EndpointHelpers.withUser... helper, or opening the call context's user with
 * AuthenticatedUserIsRequired), the endpoint never works for that caller, and nothing else notices: calls with a
 * user still pass.
 *
 * The check reads each handler's source. It sees the handler body only, not the functions it calls.
 */
class UserOptionalHandlerTest extends ServerSetup {

  object UserOptionalHandlerTag extends Tag("UserOptionalHandler")

  private def catalogs: List[Iterable[ResourceDoc]] = List(
    code.api.v1_2_1.Http4s121.Implementations1_2_1.orderedResourceDocs,
    code.api.v1_3_0.Http4s130.Implementations1_3_0.orderedResourceDocs,
    code.api.v1_4_0.Http4s140.Implementations1_4_0.orderedResourceDocs,
    code.api.v2_0_0.Http4s200.Implementations2_0_0.orderedResourceDocs,
    code.api.v2_1_0.Http4s210.Implementations2_1_0.orderedResourceDocs,
    code.api.v2_2_0.Http4s220.Implementations2_2_0.orderedResourceDocs,
    code.api.v3_0_0.Http4s300.Implementations3_0_0.orderedResourceDocs,
    code.api.v3_1_0.Http4s310.Implementations3_1_0.orderedResourceDocs,
    code.api.v4_0_0.Http4s400.Implementations4_0_0.orderedResourceDocs,
    code.api.v5_0_0.Http4s500.Implementations5_0_0.orderedResourceDocs,
    code.api.v5_1_0.Http4s510.Implementations5_1_0.orderedResourceDocs,
    code.api.v6_0_0.Http4s600.Implementations6_0_0.orderedResourceDocs,
    code.api.v7_0_0.Http4s700.Implementations7_0_0.orderedResourceDocs,
    code.api.berlin.group.v1_3.Http4sBGv13.orderedResourceDocs,
    code.api.berlin.group.v2.Http4sBGv2.orderedResourceDocs,
    code.api.UKOpenBanking.v2_0_0.Http4sUKOBv200.orderedResourceDocs,
    code.api.UKOpenBanking.v3_1_0.Http4sUKOBv310.orderedResourceDocs,
    code.api.UKOpenBanking.v4_0_1.Http4sUKOBv401.orderedResourceDocs
  )

  private lazy val userOptionalDocs: List[ResourceDoc] =
    catalogs.flatten.filter(_.requiresApplicationButNotUser).distinctBy(d => (d.implementedInApiVersion, d.partialFunctionName))

  private def mainSourceRoot: File =
    List(new File("src/main/scala/code"), new File("obp-api/src/main/scala/code"))
      .find(_.isDirectory)
      .getOrElse(fail("cannot locate the main sources - this guard must not pass by failing to look"))

  private def scalaFilesUnder(dir: File): List[File] =
    Option(dir.listFiles).toList.flatten.flatMap { f =>
      if (f.isDirectory) scalaFilesUnder(f) else if (f.getName.endsWith(".scala")) List(f) else Nil
    }

  // Comments are blanked, so a helper named in a comment is not taken for a call
  private lazy val sources: List[(File, String)] =
    scalaFilesUnder(new File(mainSourceRoot, "api")).map { f =>
      val s = Source.fromFile(f, "UTF-8")
      try f -> s.mkString.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)^\\s*//[^\n]*", " ")
      finally s.close()
    }

  /** The source files of the doc's own version: the same route name is used in several versions. */
  private def sourcesOf(doc: ResourceDoc): List[String] = {
    val version = doc.implementedInApiVersion
    val dir = version.apiShortVersion.replace('.', '_')
    val standardDir = version.apiStandard match {
      case "BG" => "code/api/berlin/group"
      case "UK" => "code/api/UKOpenBanking"
      case _ => "code/api"
    }
    sources.collect {
      case (f, src) if f.getParentFile.getName.startsWith(dir) &&
        f.getParentFile.getParentFile.getPath.replace('\\', '/').endsWith(standardDir) => src
    }
  }

  /** The text from the brace at `open` to its matching close, skipping braces inside string literals. */
  private def braceBlock(source: String, open: Int): Option[String] = {
    var depth = 0
    var i = open
    while (i < source.length) {
      if (source.startsWith("\"\"\"", i)) {
        val end = source.indexOf("\"\"\"", i + 3)
        if (end < 0) return None
        i = end + 3
      } else {
        source.charAt(i) match {
          case '"' =>
            var j = i + 1
            while (j < source.length && source.charAt(j) != '"') j += (if (source.charAt(j) == '\\') 2 else 1)
            i = j + 1
          case '{' => depth += 1; i += 1
          case '}' =>
            depth -= 1
            if (depth == 0) return Some(source.substring(open, i + 1))
            i += 1
          case _ => i += 1
        }
      }
    }
    None
  }

  /** The route the doc names in `http4sPartialFunction = Some(route)`, read from the ResourceDoc that names the doc. */
  private def routeNameOf(doc: ResourceDoc): Option[String] = {
    val name = java.util.regex.Pattern.quote(doc.partialFunctionName)
    val named = s"""(?:nameOf\\(\\s*(?:\\w+\\.)*$name\\s*\\)|"$name")""".r
    val route = """http4sPartialFunction\s*=\s*Some\(\s*(\w+)\s*\)""".r
    sourcesOf(doc).iterator.flatMap { src =>
      named.findAllMatchIn(src).flatMap { m =>
        val docStart = src.lastIndexOf("ResourceDoc(", m.start)
        val nextDoc = src.indexOf("ResourceDoc(", m.end) match { case -1 => src.length; case n => n }
        if (docStart < 0) None else route.findFirstMatchIn(src.substring(m.end, nextDoc)).map(_.group(1))
      }
    }.toSeq.headOption
  }

  private def handlerOf(doc: ResourceDoc, route: String): Option[String] = {
    val definition = s"""val\\s+$route\\s*(?::\\s*Http4sRoute\\s*)?=\\s*Http4sRoute\\s*\\{""".r
    sourcesOf(doc).iterator.flatMap { src =>
      definition.findFirstMatchIn(src).flatMap(m => braceBlock(src, m.end - 1))
    }.toSeq.headOption
  }

  private val needsUser = """EndpointHelpers\s*\.\s*withUser\w*|\.user\s*\.\s*openOrThrowException\(\s*\$?AuthenticatedUserIsRequired""".r

  feature("A handler whose doc says a user is optional works without a user") {

    scenario("the docs that need only an identified application are found", UserOptionalHandlerTag) {
      // The Berlin Group and UK Open Banking consent and payment endpoints, and the v7.0.0 consumers/current ones
      userOptionalDocs.size should be >= 20
    }

    scenario("none of their handlers insists on a user", UserOptionalHandlerTag) {
      val problems = userOptionalDocs.flatMap { doc =>
        val label = s"${doc.implementedInApiVersion} ${doc.partialFunctionName}"
        routeNameOf(doc) match {
          case None => Some(s"$label: no http4sPartialFunction found for its ResourceDoc")
          case Some(route) => handlerOf(doc, route) match {
            case None => Some(s"$label: the route $route was not found")
            case Some(body) => needsUser.findFirstIn(body).map(hit => s"$label: the handler of $route requires a user ($hit)")
          }
        }
      }
      withClue(s"\n${problems.mkString("\n")}\n") {
        problems shouldBe empty
      }
    }
  }
}
