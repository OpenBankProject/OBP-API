package code.api.util.http4s

import org.json4s._
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.util.CallContext
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, callContextKey}
import org.json4s.{DefaultFormats, Formats}
import org.http4s.{Request, Response}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}
import org.typelevel.ci.CIString

import scala.concurrent.Future

/**
 * Regression tests for the native http4s endpoint response helpers in
 * `Http4sRequestAttributes.EndpointHelpers`.
 *
 * These helpers render a Lift-JSON String and hand it to http4s' `Ok`/`Created`. Passing a
 * raw String uses the default `EntityEncoder[String]`, which labels the response
 * `Content-Type: text/plain; charset=UTF-8`. OBP only ever returns JSON from these helpers,
 * so the content type must be `application/json` — otherwise strict clients (e.g. the
 * API Manager frontend) reject a perfectly good JSON body.
 *
 * This was a real bug on the http4s-migrated dynamic-entity CRUD endpoints. The older Lift
 * endpoints and the Lift -> http4s bridge set `application/json` correctly (covered by
 * Http4sResponseConversionTest); this test pins the *native* http4s builders.
 */
class Http4sJsonContentTypeTest extends FeatureSpec with Matchers with GivenWhenThen {

  private implicit val formats: Formats = DefaultFormats

  /** A request carrying a (default) CallContext, as the helpers expect. */
  private def reqWithCallContext: Request[IO] =
    Request[IO]().withAttribute(callContextKey, CallContext())

  private def contentTypeOf(resp: Response[IO]): String =
    resp.headers.get(CIString("Content-Type")).map(_.head.value).getOrElse("")

  feature("Native http4s endpoint helpers label JSON responses as application/json") {

    scenario("executeAndRespond (200 OK) sets application/json") {
      Given("a 200 helper returning a JSON object")
      When("the response is built")
      val resp = EndpointHelpers
        .executeAndRespond(reqWithCallContext)(_ => Future.successful(Map("message" -> "ok")))
        .unsafeRunSync()

      Then("status is 200 and Content-Type is application/json")
      resp.status.code should equal(200)
      contentTypeOf(resp) should include("application/json")
    }

    scenario("executeFutureCreated (201 Created) sets application/json") {
      Given("a 201 helper returning a JSON object")
      When("the response is built")
      val resp = EndpointHelpers
        .executeFutureCreated(reqWithCallContext)(Future.successful(Map("id" -> "123")))
        .unsafeRunSync()

      Then("status is 201 and Content-Type is application/json")
      resp.status.code should equal(201)
      contentTypeOf(resp) should include("application/json")
    }

    scenario("executeFutureWithStatus sets application/json for a custom status") {
      Given("a helper returning a JSON object with an explicit 202 status")
      When("the response is built")
      val resp = EndpointHelpers
        .executeFutureWithStatus(reqWithCallContext)(Future.successful((Map("queued" -> "true"), 202)))
        .unsafeRunSync()

      Then("status is 202 and Content-Type is application/json")
      resp.status.code should equal(202)
      contentTypeOf(resp) should include("application/json")
    }

    scenario("regression: helpers must NOT fall back to text/plain") {
      Given("the 201 helper (the path the dynamic-entity create endpoint uses)")
      When("the response is built")
      val resp = EndpointHelpers
        .executeFutureCreated(reqWithCallContext)(Future.successful(Map("k" -> "v")))
        .unsafeRunSync()

      Then("the Content-Type must not be text/plain")
      contentTypeOf(resp) should not include "text/plain"
    }
  }
}
