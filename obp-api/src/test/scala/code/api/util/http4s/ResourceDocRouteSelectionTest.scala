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
import code.api.util.APIUtil.{Http4sRoute, ResourceDoc}
import code.api.util.ApiTag.ResourceDocTag
import com.openbankproject.commons.util.ApiVersion
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.JsonAST.JObject
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers, Tag}

/**
 * Unit tests for ResourceDocMatcher.selectByRoute.
 *
 * A ResourceDoc that carries its http4s route is selected by asking the route whether it serves
 * the request, not by matching the request URL against the doc's template. The doc that is
 * selected is therefore always the doc of the route that will run.
 */
class ResourceDocRouteSelectionTest extends FeatureSpec with Matchers with GivenWhenThen {

  object ResourceDocRouteSelectionTag extends Tag("ResourceDocRouteSelection")

  private val ok: IO[Response[IO]] = IO.pure(Response[IO]())

  private def doc(name: String, verb: String, url: String, route: Option[Http4sRoute]): ResourceDoc =
    ResourceDoc(
      implementedInApiVersion = ApiVersion.v7_0_0,
      partialFunctionName = name,
      requestVerb = verb,
      requestUrl = url,
      summary = "Test endpoint",
      description = "Test description",
      exampleRequestBody = JObject(Nil),
      successResponseBody = JObject(Nil),
      errorResponseBodies = List.empty,
      tags = List(ResourceDocTag("test")),
      roles = None,
      http4sPartialFunction = route
    )

  private def request(method: Method, path: String): Request[IO] =
    Request[IO](method, Uri.unsafeFromString(path))

  feature("ResourceDocMatcher.selectByRoute - the doc of the route that serves the request") {

    scenario("The doc is the route's, even when its template says something else", ResourceDocRouteSelectionTag) {
      Given("a doc whose template names /things/THING_ID but whose route serves /gadgets/...")
      val route = Http4sRoute { case GET -> Root / "obp" / "v7.0.0" / "gadgets" / _ => ok }
      val drifted = doc("getGadget", "GET", "/things/THING_ID", Some(route))

      When("a request for the gadget is made")
      val selected = ResourceDocMatcher.selectByRoute(request(Method.GET, "/obp/v7.0.0/gadgets/g1"), List(drifted))

      Then("the doc of the route that serves it is selected")
      selected.map(_.partialFunctionName) shouldBe Some("getGadget")

      And("a request the template describes but the route does not serve selects nothing")
      ResourceDocMatcher.selectByRoute(request(Method.GET, "/obp/v7.0.0/things/t1"), List(drifted)) shouldBe None
    }

    scenario("Of several docs whose routes serve the request, the first in catalog order is selected", ResourceDocRouteSelectionTag) {
      val broad  = Http4sRoute { case GET -> Root / "obp" / "v7.0.0" / "a" / _ => ok }
      val narrow = Http4sRoute { case GET -> Root / "obp" / "v7.0.0" / "a" / "special" => ok }
      val broadDoc  = doc("getAny", "GET", "/a/ANY_ID", Some(broad))
      val narrowDoc = doc("getSpecial", "GET", "/a/special", Some(narrow))
      val req = request(Method.GET, "/obp/v7.0.0/a/special")

      ResourceDocMatcher.selectByRoute(req, List(broadDoc, narrowDoc)) shouldBe Some(broadDoc)
      ResourceDocMatcher.selectByRoute(req, List(narrowDoc, broadDoc)) shouldBe Some(narrowDoc)
    }

    scenario("The selected doc's route is the route that serves the request", ResourceDocRouteSelectionTag) {
      val first  = Http4sRoute { case GET -> Root / "obp" / "v7.0.0" / "x" => ok }
      val second = Http4sRoute { case GET -> Root / "obp" / "v7.0.0" / "y" => ok }
      val docs = List(doc("getX", "GET", "/x", Some(first)), doc("getY", "GET", "/y", Some(second)))

      val req = request(Method.GET, "/obp/v7.0.0/y")
      val selected = ResourceDocMatcher.selectByRoute(req, docs).get
      selected.partialFunctionName shouldBe "getY"
      selected.http4sPartialFunction.flatMap(_.route).get.isDefinedAt(req) shouldBe true
    }

    scenario("Docs that share one route are told apart by their templates", ResourceDocRouteSelectionTag) {
      Given("one route that serves every transaction-request type, and a generic doc and a SEPA doc that share it")
      val anyType = Http4sRoute { case POST -> Root / "obp" / "v7.0.0" / "types" / _ / "requests" => ok }
      val generic = doc("createAnyType", "POST", "/types/TRANSACTION_REQUEST_TYPE/requests", Some(anyType))
      val sepa    = doc("createSepa", "POST", "/types/SEPA/requests", Some(anyType))

      Then("a SEPA request gets the SEPA doc whichever is registered first")
      ResourceDocMatcher.selectByRoute(request(Method.POST, "/obp/v7.0.0/types/SEPA/requests"), List(generic, sepa)) shouldBe Some(sepa)
      ResourceDocMatcher.selectByRoute(request(Method.POST, "/obp/v7.0.0/types/SEPA/requests"), List(sepa, generic)) shouldBe Some(sepa)

      And("a type with no doc of its own gets the generic doc")
      ResourceDocMatcher.selectByRoute(request(Method.POST, "/obp/v7.0.0/types/CARDANO/requests"), List(sepa, generic)) shouldBe Some(generic)
    }

    scenario("A request no route serves selects nothing, so it can fall through to an older version", ResourceDocRouteSelectionTag) {
      val route = Http4sRoute { case POST -> Root / "obp" / "v7.0.0" / "types" / "MOBILE_WALLET" / "requests" => ok }
      val wallet = doc("createMobileWallet", "POST", "/types/MOBILE_WALLET/requests", Some(route))

      ResourceDocMatcher.selectByRoute(request(Method.POST, "/obp/v7.0.0/types/CARDANO/requests"), List(wallet)) shouldBe None
    }

    scenario("A route matches an empty path segment the template matcher would not count", ResourceDocRouteSelectionTag) {
      val route = Http4sRoute { case GET -> Root / "obp" / "v7.0.0" / "products" / code / "subscriptions" => ok }
      val subscriptions = doc("getSubscriptions", "GET", "/products/PRODUCT_CODE/subscriptions", Some(route))

      ResourceDocMatcher.selectByRoute(request(Method.GET, "/obp/v7.0.0/products//subscriptions"), List(subscriptions)) shouldBe Some(subscriptions)
    }

    scenario("A doc without a route is ignored", ResourceDocRouteSelectionTag) {
      val legacy = doc("getLegacy", "GET", "/legacy/LEGACY_ID", None)

      ResourceDocMatcher.selectByRoute(request(Method.GET, "/obp/v7.0.0/legacy/l1"), List(legacy)) shouldBe None
    }
  }
}
