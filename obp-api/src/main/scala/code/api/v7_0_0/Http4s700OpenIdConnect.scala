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
package code.api.v7_0_0

import cats.effect.IO
import code.api.Constant.ApiPathZero
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, UserOrApplication, unboxFullOrFail, userAuthenticationMessage}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{CallContext, CustomJsonFormats, NewStyle}
import code.consent.Consents
import code.consumer.Consumers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * This object holds the v7.0.0 endpoints an OIDC provider (such as OBP-OIDC) calls while it signs Users in
 * for Consumers. The older ones, which look up and verify OIDC clients, are in v6.0.0.
 *
 * It is declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700OpenIdConnect {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  // Route: GET /obp/v7.0.0/oidc/consents/CONSENT_ID
  // EndpointHelpers.executeAndRespond rather than withUser, so an OIDC provider can call this as an
  // application holding the Scope, without a User.
  lazy val getOidcConsent: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> `prefixPath` / "oidc" / "consents" / consentId =>
      EndpointHelpers.executeAndRespond(req) { implicit cc: CallContext =>
        for {
          consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
            .map(unboxFullOrFail(_, Some(cc), s"$ConsentNotFound ($consentId)", 404))
          (user, _) <- NewStyle.function.getUserByUserId(consent.userId, Some(cc))
          consumerBox <- Future(Consumers.consumers.vend.getConsumerByConsumerId(consent.consumerId))
        } yield OpenIdConnectConsentJsonV700(
          consent_id = consent.consentId,
          status = consent.status,
          consent_request_id = Option(consent.consentRequestId).filter(_.nonEmpty),
          consumer_id = consent.consumerId,
          client_id = consumerBox.toOption.map(_.key.get),
          user_id = user.userId,
          username = user.name,
          provider = user.provider
        )
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getOidcConsent),
    "GET",
    "/oidc/consents/CONSENT_ID",
    "Get OIDC Consent",
    s"""Gets the facts an OIDC provider needs to finish a consent flow, read from the Consent stored in OBP-API.
       |
       |After a User accepts a Consent on the consent page, the OIDC provider issues an authorization code for that User.
       |It must take the User, the Consent's status and its Consumer from this record, never from parameters on the browser's
       |callback URL, because anyone can edit those. The provider then checks that `status` is an accepted one, that
       |`consent_request_id` is the Consent Request it started the flow for, and that `client_id` is the client it is about
       |to issue the code to.
       |
       |`client_id` is the Consumer Key of the Consumer the Consent belongs to; it is absent if that Consumer no longer exists.
       |`user_id`, `username` and `provider` identify the User who gave the Consent.
       |
       |${userAuthenticationMessage(true)}
       |""".stripMargin,
    EmptyBody,
    OpenIdConnectConsentJsonV700(
      consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
      status = "ACCEPTED",
      consent_request_id = Some("8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0"),
      consumer_id = "7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
      client_id = Some("abc123def456"),
      user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
      username = "felixsmith",
      provider = "https://my-obp-oidc.example.com"
    ),
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, ConsentNotFound, UserNotFoundByUserId, UnknownError),
    apiTagOIDC :: apiTagConsent :: apiTagOAuth :: Nil,
    Some(canGetOidcConsent :: Nil),
    authMode = UserOrApplication,
    http4sPartialFunction = Some(getOidcConsent)
  )
}
