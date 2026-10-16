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

package code.api.UKOpenBanking.v2_0_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.util.APIUtil.{Http4sHandler, Http4sRoute, ResourceDoc}
import code.api.util.http4s.{ResourceDocMatcher, ResourceDocMiddleware}
import code.api.util.http4s.IdempotencyMiddleware
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ApiVersion
import org.http4s._

import scala.collection.mutable.ArrayBuffer

/**
 * UK Open Banking v2.0 — http4s aggregator (mirror of Berlin Group's Http4sBGv2).
 *
 * Wraps the migrated account-information routes with ResourceDocMiddleware and
 * exposes `wrappedRoutes` for Http4sApp. All 5 v2.0 endpoints — including the two
 * account-scoped ones (/accounts/ID/balances, /accounts/ID/transactions) — are
 * migrated in Http4sUKOBv200AIS. The Lift ScannedApis aggregator
 * (OBP_UKOpenBanking_200) registers `routes = Nil`, so no UK v2.0 path is served
 * by Lift.
 */
object Http4sUKOBv200 extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  val implementedInApiVersion: ApiVersion = ApiVersion.ukOpenBankingV20

  val resourceDocs: ArrayBuffer[ResourceDoc] =
    Http4sUKOBv200AIS.resourceDocs

  lazy val routesInOrder: List[Http4sHandler] =
      Http4sUKOBv200AIS.routesInOrder

  lazy val orderedResourceDocs: ArrayBuffer[ResourceDoc] = ResourceDocMatcher.orderByRoutes(resourceDocs, routesInOrder)

  lazy val allRoutes: HttpRoutes[IO] = Http4sRoute.chain(routesInOrder)

  lazy val wrappedRoutes: HttpRoutes[IO] = ResourceDocMiddleware.apply(orderedResourceDocs, routes => IdempotencyMiddleware(routes))
}
