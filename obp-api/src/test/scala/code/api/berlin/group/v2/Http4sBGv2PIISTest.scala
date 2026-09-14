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

package code.api.berlin.group.v2

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import code.api.berlin.group.ConstantsBG
import code.util.Helper.MdcLoggable
import org.http4s._
import org.http4s.implicits._
import org.scalatest.{FlatSpec, Matchers, Tag}

/**
 * Unit tests for Berlin Group v2 PIIS endpoint.
 * Tests POST /v2/funds-confirmations returns correct HTTP status and JSON structure.
 * Validates: Requirements 6.1
 */
class Http4sBGv2PIISTest extends FlatSpec with Matchers with MdcLoggable {

  object PIISTag extends Tag("BerlinGroupV2_PIIS")

  private val routes = Http4sBGv2PIIS.routes
  private val prefix = s"/${ConstantsBG.berlinGroupVersion2.urlPrefix}/${ConstantsBG.berlinGroupVersion2.apiShortVersion}"

  private def runRequest(method: Method, uri: String): (Status, String) = {
    val req = Request[IO](method, Uri.unsafeFromString(uri))
    val resp = routes.run(req).value.unsafeRunSync().getOrElse(Response[IO](Status.NotFound))
    val body = resp.bodyText.compile.string.unsafeRunSync()
    (resp.status, body)
  }

  s"POST $prefix/funds-confirmations" should "return 200 with funds confirmation JSON" taggedAs PIISTag in {
    val (status, body) = runRequest(Method.POST, s"$prefix/funds-confirmations")
    status shouldBe Status.Ok
    body should include("fundsAvailable")
  }
}
