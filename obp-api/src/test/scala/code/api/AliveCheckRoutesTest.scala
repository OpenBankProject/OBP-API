package code.api

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.{Header, Method, Request, Uri}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}
import org.typelevel.ci.CIString

/**
 * Pins the exact contract that Kubernetes liveness probes depend on for
 * `GET /alive`. Any change to status code, body bytes, content-type, path,
 * or auth requirement will fail this test and surface in code review.
 *
 * Keep these assertions verbatim — they are the freeze.
 */
class AliveCheckRoutesTest extends FeatureSpec with Matchers with GivenWhenThen {

  private def runRoute(req: Request[IO]) =
    AliveCheckRoutes.routes.run(req).value.unsafeRunSync()

  feature("GET /alive contract (Kubernetes liveness probe)") {

    scenario("Returns 200 with body 'true' and JSON content-type") {
      Given("an unauthenticated GET /alive request")
      val req = Request[IO](method = Method.GET, uri = Uri.unsafeFromString("/alive"))

      When("the route is invoked")
      val resp = runRoute(req).getOrElse(fail("AliveCheckRoutes did not match GET /alive"))

      Then("status is 200")
      resp.status.code should equal(200)

      And("body is exactly the 4 bytes of the JSON literal `true`")
      val bodyBytes = resp.body.compile.to(Array).unsafeRunSync()
      new String(bodyBytes, "UTF-8") should equal("true")
      bodyBytes.length should equal(4)

      And("Content-Type is application/json with UTF-8 charset")
      val contentType = resp.headers.get(CIString("Content-Type"))
      contentType should not be empty
      val ctValue = contentType.get.head.value
      ctValue should include("application/json")
      ctValue.toLowerCase should include("utf-8")
    }

    scenario("Succeeds without any Authorization header") {
      Given("a GET /alive request with no auth headers at all")
      val req = Request[IO](method = Method.GET, uri = Uri.unsafeFromString("/alive"))
      req.headers.get(CIString("Authorization")) shouldBe empty

      When("the route is invoked")
      val resp = runRoute(req).getOrElse(fail("AliveCheckRoutes did not match GET /alive"))

      Then("it still returns 200 — the probe must never require auth")
      resp.status.code should equal(200)
    }

    scenario("Ignores Authorization header if one happens to be sent") {
      Given("a GET /alive request that carries a bogus Authorization header")
      val req = Request[IO](method = Method.GET, uri = Uri.unsafeFromString("/alive"))
        .putHeaders(Header.Raw(CIString("Authorization"), "Bearer not-a-real-token"))

      When("the route is invoked")
      val resp = runRoute(req).getOrElse(fail("AliveCheckRoutes did not match GET /alive"))

      Then("status is still 200 and the body is still 'true'")
      resp.status.code should equal(200)
      new String(resp.body.compile.to(Array).unsafeRunSync(), "UTF-8") should equal("true")
    }

    scenario("Path is literally /alive — not under /obp/...") {
      Given("a GET request to a prefixed path like /obp/v5.0.0/alive")
      val req = Request[IO](method = Method.GET, uri = Uri.unsafeFromString("/obp/v5.0.0/alive"))

      When("the route is invoked")
      val matched = runRoute(req)

      Then("the route does not match — probe path must remain top-level /alive")
      matched shouldBe None
    }

    scenario("Does not match POST /alive") {
      Given("a POST /alive request")
      val req = Request[IO](method = Method.POST, uri = Uri.unsafeFromString("/alive"))

      When("the route is invoked")
      val matched = runRoute(req)

      Then("the route does not match — only GET is supported")
      matched shouldBe None
    }

    scenario("Does not match /alive/anything") {
      Given("a GET request to a child path of /alive")
      val req = Request[IO](method = Method.GET, uri = Uri.unsafeFromString("/alive/foo"))

      When("the route is invoked")
      val matched = runRoute(req)

      Then("the route does not match — only the exact path /alive is served")
      matched shouldBe None
    }

    scenario("Does not match the root path /") {
      Given("a GET / request")
      val req = Request[IO](method = Method.GET, uri = Uri.unsafeFromString("/"))

      When("the route is invoked")
      val matched = runRoute(req)

      Then("the route does not match")
      matched shouldBe None
    }
  }
}
