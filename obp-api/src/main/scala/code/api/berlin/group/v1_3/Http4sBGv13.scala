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

package code.api.berlin.group.v1_3

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.ResourceDoc
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.http4s.IdempotencyMiddleware
import code.util.Helper.MdcLoggable
import org.http4s._

import scala.collection.mutable.ArrayBuffer

/**
 * Native http4s aggregator for Berlin Group v1.3, replacing the Lift
 * `OBP_BERLIN_GROUP_1_3` statelessDispatch registration. Mirrors `Http4sBGv2`.
 *
 * Groups: AIS / PIS / SigningBaskets / PIIS. Added incrementally.
 */
object Http4sBGv13 extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  val implementedInApiVersion = ConstantsBG.berlinGroupVersion1

  val resourceDocs: ArrayBuffer[ResourceDoc] =
    Http4sBGv13AIS.resourceDocs ++
    Http4sBGv13PIS.resourceDocs ++
    Http4sBGv13PIIS.resourceDocs ++
    Http4sBGv13SigningBaskets.resourceDocs

  val allRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    Http4sBGv13AIS.routes(req)
      .orElse(Http4sBGv13PIS.routes(req))
      .orElse(Http4sBGv13PIIS.routes(req))
      .orElse(Http4sBGv13SigningBaskets.routes(req))
  }

  val wrappedRoutes: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allRoutes))
}
