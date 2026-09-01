package code.api.v6_0_0

import code.api.util.CallContext
import net.liftweb.common.{Empty, Full}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * `Http4s600.Implementations6_0_0.resolveResetPasswordPortalUrl` reports what happens when
 * `public_obp_portal_url` (or the legacy `portal_external_url`) isn't configured. That is an
 * operator's configuration mistake, not a caller's -- the same condition this PR's own
 * createTestEmail fix (Http4s700.scala) deliberately reports as 503, not 500, with the reasoning
 * "the server is not broken -- it is not configured to do this, and [a wrong code] tells a
 * caller with retry logic that the fault is transient." A missing portal URL should get the same
 * treatment here: 503, not 400 -- an admin resetting a user's password should not be told their
 * request was bad when the truth is nobody has configured the portal URL yet.
 */
class Http4s600ResetPasswordPortalUrlTest extends V600ServerSetup {

  private implicit val cc: CallContext = CallContext()

  private def resultOf[T](f: => T): Either[Throwable, T] =
    try Right(f) catch { case t: Throwable => Left(t) }

  feature("resetPasswordUrl reports a missing portal URL as a server misconfiguration, not a client error") {

    scenario("a configured portal URL is used as-is") {
      val url = Await.result(
        Http4s600.Implementations6_0_0.resolveResetPasswordPortalUrl(Full("https://portal.example.com")),
        5.seconds)
      url shouldBe "https://portal.example.com"
    }

    scenario("an unconfigured portal URL fails with 503, not 400") {
      val outcome = resultOf(Await.result(
        Http4s600.Implementations6_0_0.resolveResetPasswordPortalUrl(Empty), 5.seconds))

      outcome match {
        case Left(t) =>
          val msg = t.getMessage
          withClue(s"expected a JSON envelope carrying failCode 503 (an operator configuration " +
                   s"problem, not the caller's fault); got: $msg") {
            msg should include("\"failCode\":503")
            msg should not include "\"failCode\":400"
          }
        case Right(v) =>
          fail(s"expected the missing portal URL to fail, but it returned $v")
      }
    }
  }
}
