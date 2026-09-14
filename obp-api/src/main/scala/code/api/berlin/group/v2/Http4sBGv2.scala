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

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.ResourceDoc
import code.api.util.ScannedApis
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.http4s.IdempotencyMiddleware
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.ScannedApiVersion
import org.http4s._

import scala.collection.mutable.ArrayBuffer

object Http4sBGv2 extends MdcLoggable with ScannedApis {

  type HttpF[A] = OptionT[IO, A]

  val implementedInApiVersion = ConstantsBG.berlinGroupVersion2

  // ScannedApis discovery marker: makes BGv2 convention-driven like the other Berlin Group /
  // UK Open Banking standards, so ResourceDocRegistry picks it up without a hand-maintained entry.
  override val apiVersion: ScannedApiVersion = implementedInApiVersion

  val resourceDocs: ArrayBuffer[ResourceDoc] =
    Http4sBGv2AIS.resourceDocs ++
    Http4sBGv2PIS.resourceDocs ++
    Http4sBGv2PIIS.resourceDocs

  override val allResourceDocs: ArrayBuffer[ResourceDoc] = resourceDocs

  val allRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    Http4sBGv2AIS.routes(req)
      .orElse(Http4sBGv2PIS.routes(req))
      .orElse(Http4sBGv2PIIS.routes(req))
  }

  val wrappedRoutes: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allRoutes))
}
