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

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.berlin.group.ConstantsBG
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.CustomJsonFormats
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.JsonAliases.prettyRender
import org.json4s.{Extraction, Formats}
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

object Http4sBGv2PIIS extends MdcLoggable {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val implementedInApiVersion = ConstantsBG.berlinGroupVersion2
  val resourceDocs = ArrayBuffer[ResourceDoc]()

  val bgV2Prefix = Root / ConstantsBG.berlinGroupVersion2.urlPrefix / ConstantsBG.berlinGroupVersion2.apiShortVersion

  // ── POST /v2/funds-confirmations ──────────────────────────────────

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(postConfirmationOfFunds),
    "POST",
    "/funds-confirmations",
    "Confirmation of Funds Request",
    "Checks whether a specific amount is available on an account.",
    EmptyBody,
    JSONFactory_BERLIN_GROUP_v2.mockFundsConfirmation,
    List(UnknownError),
    apiTagPSD2PIIS :: apiTagBerlinGroupM :: Nil,
    http4sPartialFunction = Some(postConfirmationOfFunds)
  )

  val postConfirmationOfFunds: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `bgV2Prefix` / "funds-confirmations" =>
      Ok(convertAnyToJsonString(JSONFactory_BERLIN_GROUP_v2.mockFundsConfirmation))
  }

  // ── Combined routes ───────────────────────────────────────────────

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    postConfirmationOfFunds(req)
  }
}
