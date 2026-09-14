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

package code.api.v5_1_0

import code.api.util.CallContext

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * `Http4s510.Implementations5_1_0.resolveJwtSignatureValid` decides what a thrown exception from
 * `JwtUtil.verifyJwt` means: a malformed client certificate or JWT (client's fault, 400) versus a
 * JVM/security-provider configuration problem -- the requested signature algorithm is not
 * registered (a hardened/FIPS JRE, a stripped provider list, a provider-registration bug). Both
 * currently surface as the same `JOSEException`/generic `Exception`, and wrapping the whole call
 * in `tryons(..., 400, ...)` cannot tell them apart: a security-provider fault would be reported
 * to the caller as "your JSON is not signed" when nothing about their JWT is wrong -- the server
 * cannot perform this verification for ANY caller until an operator fixes the JVM.
 *
 * These tests call the production function directly with a stub `verify`, so no real PEM/JWT
 * material or JOSE library internals are needed to exercise the distinction.
 */
class Http4s510JwtSignatureResolutionTest extends V510ServerSetup {

  private implicit val cc: CallContext = CallContext()

  private def resultOf[T](f: => T): Either[Throwable, T] =
    try Right(f) catch { case t: Throwable => Left(t) }

  feature("createConsumerDynamicRegistration's JWT verification distinguishes a bad client " +
          "JWT from a broken security provider") {

    scenario("a verify() that returns false is a normal signature mismatch, not an error") {
      val outcome = Await.result(
        Http4s510.Implementations5_1_0.resolveJwtSignatureValid(() => false), 5.seconds)
      outcome shouldBe false
    }

    scenario("a verify() that throws for a malformed client JWT fails with the 400 envelope") {
      val badJwt = new IllegalArgumentException("Invalid JWT serialization")
      val outcome = resultOf(Await.result(
        Http4s510.Implementations5_1_0.resolveJwtSignatureValid(() => throw badJwt), 5.seconds))

      outcome match {
        case Left(t) =>
          val msg = t.getMessage
          withClue(s"expected a JSON envelope carrying failCode 400; got: $msg") {
            msg should include("\"failCode\":400")
          }
        case Right(v) =>
          fail(s"expected verify() to fail as a bad JWT, but it returned $v")
      }
    }

    scenario("a verify() that throws because the JVM lacks the signature algorithm propagates " +
             "that exception, not a 400") {
      val providerFailure =
        new com.nimbusds.jose.JOSEException("no such algorithm",
          new java.security.NoSuchAlgorithmException("SHA256withRSA Signature not available"))
      val outcome = resultOf(Await.result(
        Http4s510.Implementations5_1_0.resolveJwtSignatureValid(() => throw providerFailure), 5.seconds))

      outcome match {
        case Left(t) =>
          val msg = t.getMessage
          withClue(s"expected the original security-provider exception to propagate untouched " +
                   s"so it resolves to 500, not the 400 envelope reserved for a malformed " +
                   s"client JWT/certificate; got: $msg") {
            msg should not include "\"failCode\":400"
            msg should include("no such algorithm")
          }
        case Right(v) =>
          fail(s"expected verify()'s exception to propagate, but it returned $v")
      }
    }
  }
}
