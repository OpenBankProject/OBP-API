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

package code.api.UKOpenBanking.v4_0_1

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.api.util.APIUtil.{EmptyBody, ResourceDoc}
import code.api.util.ApiTag
import code.api.util.CustomJsonFormats
import code.api.util.ErrorMessages.{AuthenticatedUserIsRequired, UnknownError}
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.util.Helper.MdcLoggable
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import com.openbankproject.commons.util.JsonAliases
import org.json4s.{Formats, JObject}
import org.http4s._
import org.http4s.dsl.io._
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

// AUTO-GENERATED from UK Open Banking read-write-api-specs v4.0.1 (EventNotifications).
// Spec-faithful scaffold: routes return synthesized example JSON from the
// OpenAPI schemas (the specs carry no examples). Deepen to real OBP
// connector logic per endpoint later, mirroring v3_1_0.
object Http4sUKOBv401EventNotifications extends MdcLoggable {
  type HttpF[A] = OptionT[IO, A]
  implicit val formats: Formats = CustomJsonFormats.formats
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.ukOpenBankingV401
  val resourceDocs = ArrayBuffer[ResourceDoc]()
  private def parseBody(s: String): JObject = JsonAliases.parse(s).asInstanceOf[JObject]
  val ukV401Prefix = Root / ApiVersion.ukOpenBankingV401.urlPrefix / ApiVersion.ukOpenBankingV401.apiShortVersion

  private val EX_createEventNotification: String = """{}"""
  lazy val createEventNotification: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> `ukV401Prefix` / "event-notifications" =>
      EndpointHelpers.executeFutureCreated(req)(Future.successful(parseBody(EX_createEventNotification)))
  }
  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createEventNotification),
    "POST",
    "/event-notifications",
    "Send an event notification",
    """Allows the ASPSP to send an event-notification resource to a TPP.""",
    EmptyBody,
    parseBody(EX_createEventNotification),
    List(AuthenticatedUserIsRequired, UnknownError),
    ApiTag("Event Notification") :: Nil,
    http4sPartialFunction = Some(createEventNotification)
  )

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
    createEventNotification(req)
  }
}
