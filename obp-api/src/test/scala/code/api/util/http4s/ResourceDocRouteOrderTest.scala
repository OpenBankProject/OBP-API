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
import code.api.util.APIUtil.{Http4sHandler, Http4sRoute, ResourceDoc}
import code.api.util.ApiTag.ResourceDocTag
import com.openbankproject.commons.util.ApiVersion
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.JsonAST.JObject
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers, Tag}

/**
 * Unit tests for ResourceDocMatcher.orderByRoutes and Http4sRoute.chain.
 *
 * The middleware selects the doc of the first route that serves a request, so the docs it is given
 * must be in the order the routes are tried in. orderByRoutes derives that order from the route chain.
 */
class ResourceDocRouteOrderTest extends FeatureSpec with Matchers with GivenWhenThen {

  object ResourceDocRouteOrderTag extends Tag("ResourceDocRouteOrder")

  private def route(segment: String): Http4sRoute =
    Http4sRoute { case GET -> Root / "obp" / "v7.0.0" / `segment` => IO.pure(Response[IO]()) }

  private def doc(name: String, handler: Option[Http4sHandler]): ResourceDoc =
    ResourceDoc(
      implementedInApiVersion = ApiVersion.v7_0_0,
      partialFunctionName = name,
      requestVerb = "GET",
      requestUrl = s"/$name",
      summary = "Test endpoint",
      description = "Test description",
      exampleRequestBody = JObject(Nil),
      successResponseBody = JObject(Nil),
      errorResponseBodies = List.empty,
      tags = List(ResourceDocTag("test")),
      roles = None,
      http4sPartialFunction = handler
    )

  feature("ResourceDocMatcher.orderByRoutes - the docs in the order their routes are tried") {

    scenario("Docs follow the route chain, whatever order they were registered in", ResourceDocRouteOrderTag) {
      val (a, b, c) = (route("a"), route("b"), route("c"))
      val registered = List(doc("c", Some(c)), doc("a", Some(a)), doc("b", Some(b)))

      val ordered = ResourceDocMatcher.orderByRoutes(registered, List(a, b, c))

      ordered.map(_.partialFunctionName) shouldBe List("a", "b", "c")
    }

    scenario("Docs that share a route stay together, in registration order", ResourceDocRouteOrderTag) {
      val (a, b) = (route("a"), route("b"))
      val registered = List(doc("b1", Some(b)), doc("a", Some(a)), doc("b2", Some(b)))

      val ordered = ResourceDocMatcher.orderByRoutes(registered, List(a, b))

      ordered.map(_.partialFunctionName) shouldBe List("a", "b1", "b2")
    }

    scenario("An alias of a route is the same route", ResourceDocRouteOrderTag) {
      val (a, b) = (route("a"), route("b"))
      val aliasOfB: Http4sRoute = b
      val registered = List(doc("viaAlias", Some(aliasOfB)), doc("a", Some(a)))

      val ordered = ResourceDocMatcher.orderByRoutes(registered, List(a, b))

      ordered.map(_.partialFunctionName) shouldBe List("a", "viaAlias")
    }

    scenario("A doc whose route is deliberately outside the chain is left out", ResourceDocRouteOrderTag) {
      val (a, outside) = (route("a"), route("outside"))
      val registered = List(doc("outside", Some(outside)), doc("a", Some(a)))

      val ordered = ResourceDocMatcher.orderByRoutes(registered, List(a), outsideTheChain = List(outside))

      ordered.map(_.partialFunctionName) shouldBe List("a")
    }

    scenario("A doc whose route is missing from the chain is an error naming the doc", ResourceDocRouteOrderTag) {
      val (a, forgotten) = (route("a"), route("forgotten"))
      val registered = List(doc("a", Some(a)), doc("forgotten", Some(forgotten)))

      val thrown = the[IllegalStateException] thrownBy ResourceDocMatcher.orderByRoutes(registered, List(a))

      thrown.getMessage should include("forgotten")
      thrown.getMessage should not include "(GET /a)"
    }

    scenario("A doc with no route documents an endpoint served elsewhere and is left out", ResourceDocRouteOrderTag) {
      val a = route("a")
      val registered = List(doc("noRoute", None), doc("a", Some(a)))

      val ordered = ResourceDocMatcher.orderByRoutes(registered, List(a))

      ordered.map(_.partialFunctionName) shouldBe List("a")
    }
  }

  feature("Http4sRoute.chain - the first route that serves a request answers it") {

    scenario("Routes are tried in the order given", ResourceDocRouteOrderTag) {
      import cats.effect.unsafe.implicits.global
      val first = Http4sRoute { case GET -> Root / "x" => IO.pure(Response[IO](Status.Ok)) }
      val second = Http4sRoute { case GET -> Root / "x" => IO.pure(Response[IO](Status.Accepted)) }
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/x"))

      Http4sRoute.chain(List(first, second)).run(req).value.unsafeRunSync().map(_.status) shouldBe Some(Status.Ok)
      Http4sRoute.chain(List(second, first)).run(req).value.unsafeRunSync().map(_.status) shouldBe Some(Status.Accepted)
    }

    scenario("A request no route serves is not answered", ResourceDocRouteOrderTag) {
      import cats.effect.unsafe.implicits.global
      val only = Http4sRoute { case GET -> Root / "x" => IO.pure(Response[IO]()) }
      val req = Request[IO](Method.GET, Uri.unsafeFromString("/y"))

      Http4sRoute.chain(List(only)).run(req).value.unsafeRunSync() shouldBe None
    }
  }
}
