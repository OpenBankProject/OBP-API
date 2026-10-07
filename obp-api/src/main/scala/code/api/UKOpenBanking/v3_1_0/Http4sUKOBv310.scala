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

package code.api.UKOpenBanking.v3_1_0

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
 * UK Open Banking v3.1 — http4s aggregator (mirror of Berlin Group's Http4sBGv2).
 *
 * Collects resource docs and routes from every per-category endpoint object,
 * wraps the combined routes once with ResourceDocMiddleware (which builds the
 * CallContext via anonymousAccess for these non-/obp paths), and exposes
 * `wrappedRoutes` for wiring into Http4sApp.baseServices.
 *
 * Coverage: all 20 v3.1 categories (~67 endpoints) are migrated to http4s and
 * composed into allRoutes below. The Lift ScannedApis aggregator
 * (OBP_UKOpenBanking_310) registers `routes = Nil`, so no UK v3.1 path is served
 * by Lift — nothing falls through to the Lift bridge.
 */
object Http4sUKOBv310 extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  val implementedInApiVersion: ApiVersion = ApiVersion.ukOpenBankingV31

  val resourceDocs: ArrayBuffer[ResourceDoc] =
    Http4sUKOBv310AccountAccess.resourceDocs ++
    Http4sUKOBv310Accounts.resourceDocs ++
    Http4sUKOBv310Products.resourceDocs ++
    Http4sUKOBv310Beneficiaries.resourceDocs ++
    Http4sUKOBv310DirectDebits.resourceDocs ++
    Http4sUKOBv310Offers.resourceDocs ++
    Http4sUKOBv310Partys.resourceDocs ++
    Http4sUKOBv310ScheduledPayments.resourceDocs ++
    Http4sUKOBv310StandingOrders.resourceDocs ++
    Http4sUKOBv310Statements.resourceDocs ++
    Http4sUKOBv310DomesticPayments.resourceDocs ++
    Http4sUKOBv310DomesticScheduledPayments.resourceDocs ++
    Http4sUKOBv310DomesticStandingOrders.resourceDocs ++
    Http4sUKOBv310FilePayments.resourceDocs ++
    Http4sUKOBv310FundsConfirmations.resourceDocs ++
    Http4sUKOBv310InternationalPayments.resourceDocs ++
    Http4sUKOBv310InternationalScheduledPayments.resourceDocs ++
    Http4sUKOBv310InternationalStandingOrders.resourceDocs ++
    Http4sUKOBv310Balances.resourceDocs ++
    Http4sUKOBv310Transactions.resourceDocs

  lazy val routesInOrder: List[Http4sHandler] =
      Http4sUKOBv310AccountAccess.routesInOrder ++
      Http4sUKOBv310Accounts.routesInOrder ++
      Http4sUKOBv310Balances.routesInOrder ++
      Http4sUKOBv310Transactions.routesInOrder ++
      Http4sUKOBv310Products.routesInOrder ++
      Http4sUKOBv310Beneficiaries.routesInOrder ++
      Http4sUKOBv310DirectDebits.routesInOrder ++
      Http4sUKOBv310Offers.routesInOrder ++
      Http4sUKOBv310Partys.routesInOrder ++
      Http4sUKOBv310ScheduledPayments.routesInOrder ++
      Http4sUKOBv310StandingOrders.routesInOrder ++
      Http4sUKOBv310Statements.routesInOrder ++
      Http4sUKOBv310DomesticPayments.routesInOrder ++
      Http4sUKOBv310DomesticScheduledPayments.routesInOrder ++
      Http4sUKOBv310DomesticStandingOrders.routesInOrder ++
      Http4sUKOBv310FilePayments.routesInOrder ++
      Http4sUKOBv310FundsConfirmations.routesInOrder ++
      Http4sUKOBv310InternationalPayments.routesInOrder ++
      Http4sUKOBv310InternationalScheduledPayments.routesInOrder ++
      Http4sUKOBv310InternationalStandingOrders.routesInOrder

  lazy val orderedResourceDocs: ArrayBuffer[ResourceDoc] = ResourceDocMatcher.orderByRoutes(resourceDocs, routesInOrder)

  lazy val allRoutes: HttpRoutes[IO] = Http4sRoute.chain(routesInOrder)

  lazy val wrappedRoutes: HttpRoutes[IO] = ResourceDocMiddleware.apply(orderedResourceDocs, routes => IdempotencyMiddleware(routes))
}
