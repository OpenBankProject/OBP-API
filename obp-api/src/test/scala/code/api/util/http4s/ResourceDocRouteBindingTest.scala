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

import cats.effect.IO
import code.api.util.APIUtil.ResourceDoc
import code.setup.ServerSetup
import org.http4s.{Method, Request, Uri}
import org.scalatest.Tag

import scala.collection.mutable.ArrayBuffer

/**
 * Every ResourceDoc of a converted catalog is bound to the route that serves it.
 *
 * For each doc the test sends the doc's own template as the request path. A placeholder in a route
 * pattern accepts any string, so the template text stands in for a value, and a fixed word in the
 * pattern needs exactly the word the template carries. The doc's own route must serve that
 * request, and selecting by route must give back that same doc.
 *
 * A doc whose template has drifted away from its route fails here, which nothing else catches:
 * the route still serves its real URLs, so its own tests pass.
 */
class ResourceDocRouteBindingTest extends ServerSetup {

  object ResourceDocRouteBindingTag extends Tag("ResourceDocRouteBinding")

  /**
   * Each converted version's docs in the order its middleware selects from, which is the order its routes
   * are tried in. Selection takes the first doc whose route serves a request, so a test that used the
   * registration order could pass while production picks another doc.
   */
  private def convertedCatalogs: List[(String, ArrayBuffer[ResourceDoc])] = List(
    "v7.0.0" -> code.api.v7_0_0.Http4s700.Implementations7_0_0.orderedResourceDocs
  )

  private def describe(doc: ResourceDoc): String =
    s"${doc.partialFunctionName} (${doc.requestVerb} ${doc.requestUrl})"

  private def requestFor(doc: ResourceDoc): Request[IO] = {
    val version = doc.implementedInApiVersion
    Request[IO](
      Method.fromString(doc.requestVerb.toUpperCase).fold(throw _, identity),
      Uri.unsafeFromString(s"/${version.urlPrefix}/$version${doc.requestUrl}")
    )
  }

  /** One line per doc that is not bound to a route that serves its own URL. */
  private def unbound(docs: ArrayBuffer[ResourceDoc]): List[String] = {
    val sameVerbAndUrlCount = docs.groupBy(doc => (doc.requestVerb.toUpperCase, doc.requestUrl)).view.mapValues(_.size).toMap
    docs.toList.flatMap { doc =>
      doc.http4sPartialFunction.flatMap(_.route) match {
        case None => Some(s"${describe(doc)} carries no Http4sRoute")
        case Some(route) =>
          val req = requestFor(doc)
          if (!route.isDefinedAt(req)) Some(s"${describe(doc)}: its own route does not serve its own URL")
          else if (sameVerbAndUrlCount((doc.requestVerb.toUpperCase, doc.requestUrl)) > 1) None
          else ResourceDocMatcher.selectByRoute(req, docs) match {
            case Some(selected) if selected eq doc => None
            case Some(selected) => Some(s"${describe(doc)} selects ${describe(selected)}")
            case None => Some(s"${describe(doc)} selects no doc")
          }
      }
    }
  }

  feature("Every ResourceDoc of a converted catalog is bound to the route that serves it") {
    convertedCatalogs.foreach { case (label, docs) =>
      scenario(s"$label: each doc's own route serves its own URL, and selection returns that doc", ResourceDocRouteBindingTag) {
        docs should not be empty
        val problems = unbound(docs)
        withClue(s"\n${problems.mkString("\n")}\n") {
          problems shouldBe empty
        }
      }
    }
  }
}
