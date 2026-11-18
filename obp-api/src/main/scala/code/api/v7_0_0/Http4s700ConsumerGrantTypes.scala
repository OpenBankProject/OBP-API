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

import code.api.Constant.ApiPathZero
import code.api.util.APIUtil.{EmptyBody, Http4sRoute, ResourceDoc}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{CallContext, CustomJsonFormats, Glossary, NewStyle}
import code.consumer.{ConsumerGrantTypes, Consumers}
import code.model.Consumer
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Box
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer

/** grant_types: the grant types set on the Consumer (empty if none are set).
  * oauth2_grant_types: the grant types an OAuth2 provider may issue tokens for. */
case class ConsumerGrantTypesJsonV700(consumer_id: String, grant_types: List[String], oauth2_grant_types: List[String])
case class PutConsumerGrantTypesJsonV700(grant_types: List[String])

/**
 * The v7.0.0 Consumer grant types endpoints: how people and the application itself may call through a Consumer
 * (see [[code.consumer.ConsumerGrantTypes]] and the Glossary item "Consumer Grant Types").
 *
 * Declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700ConsumerGrantTypes {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private def fullOrFail[T: Manifest](box: Box[T], cc: CallContext): T =
    code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), UnknownError, 400)

  private def json(consumer: Consumer) = ConsumerGrantTypesJsonV700(
    consumer_id = consumer.consumerId.get,
    grant_types = ConsumerGrantTypes.of(consumer),
    oauth2_grant_types = ConsumerGrantTypes.oauth2GrantTypes(consumer))

  private val example = ConsumerGrantTypesJsonV700(
    consumer_id = "7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
    grant_types = List(ConsumerGrantTypes.ClientCredentials),
    oauth2_grant_types = List(ConsumerGrantTypes.ClientCredentials))

  private val description =
    s"""A Consumer's grant types control how tokens can be obtained through the Consumer. They say whether people can
       |log in through it, whether the application can get a token for itself, or both.
       |
       |A person "logs in through a Consumer" when they get a token with that Consumer's client id or key. They can do this by an OAuth2
       |login, by DirectLogin, or by renewing such a login. The token then names both the person and the application.
       |
       |Grant types are optional. A Consumer that has no grant types set can be used in every way, as before.
       |
       |The values are:
       |
       |- `authorization_code`: people log in through an OAuth2 / OpenID Connect provider, such as OBP-OIDC or Keycloak.
       |- `refresh_token`: such a login can be renewed. Requires `authorization_code`.
       |- `client_credentials`: the application gets a token for itself.
       |- `direct_login`: people log in with DirectLogin.
       |
       |For example, through a Consumer with only `client_credentials`, the application can get a token for itself,
       |but people cannot log in. Through a Consumer without `client_credentials`, people can log in, but the
       |application cannot get a token for itself.
       |
       |Grant types only control how tokens are obtained. What the application may do with a token depends on the
       |Scopes its Consumer holds.
       |
       |The response has two lists:
       |
       |- `grant_types`: the grant types set on the Consumer. Empty if none are set.
       |- `oauth2_grant_types`: the grant types an OAuth2 provider such as OBP-OIDC may issue tokens for. These are
       |  the Consumer's grant types without `direct_login`, or all three OAuth2 grant types if none are set.
       |
       |For more information see ${Glossary.getGlossaryItemLink("Consumer Grant Types")}""".stripMargin

  // Route: GET /obp/v7.0.0/management/consumers/CONSUMER_ID/grant-types
  lazy val getConsumerGrantTypes: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "consumers" / consumerId / "grant-types" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc)).map(json)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getConsumerGrantTypes),
    "GET",
    "/management/consumers/CONSUMER_ID/grant-types",
    "Get Consumer Grant Types",
    s"""Get the grant types of a Consumer.
       |
       |$description
       |""".stripMargin,
    EmptyBody,
    example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, ConsumerNotFoundByConsumerId, UnknownError),
    List(apiTagConsumer),
    Some(List(canGetConsumers)),
    http4sPartialFunction = Some(getConsumerGrantTypes)
  )

  // Route: PUT /obp/v7.0.0/management/consumers/CONSUMER_ID/grant-types
  lazy val updateConsumerGrantTypes: Http4sRoute = Http4sRoute {
    case req @ PUT -> `prefixPath` / "management" / "consumers" / consumerId / "grant-types" =>
      EndpointHelpers.withUserAndBody[PutConsumerGrantTypesJsonV700, ConsumerGrantTypesJsonV700](req) { (_, body, cc) =>
        val grantTypes = Option(body.grant_types).getOrElse(Nil).map(_.trim.toLowerCase).filter(_.nonEmpty).distinct
        for {
          _ <- Helper.booleanToFuture(InvalidConsumerGrantTypes, failCode = 400, cc = Some(cc)) {
            ConsumerGrantTypes.unknown(grantTypes).isEmpty &&
              (!grantTypes.contains(ConsumerGrantTypes.RefreshToken) || grantTypes.contains(ConsumerGrantTypes.AuthorizationCode))
          }
          consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
          updated <- Consumers.consumers.vend.updateConsumerGrantTypes(consumer.id.get, grantTypes) map {
            x => fullOrFail(x, cc)
          }
        } yield json(updated)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateConsumerGrantTypes),
    "PUT",
    "/management/consumers/CONSUMER_ID/grant-types",
    "Update Consumer Grant Types",
    s"""Set the grant types of a Consumer. This replaces any grant types it already has.
       |
       |Sending an empty list removes them, so the Consumer can again be used in every way.
       |
       |$description
       |""".stripMargin,
    PutConsumerGrantTypesJsonV700(grant_types = List(ConsumerGrantTypes.ClientCredentials)),
    example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, InvalidConsumerGrantTypes,
      ConsumerNotFoundByConsumerId, UnknownError),
    List(apiTagConsumer),
    Some(List(canUpdateConsumerGrantTypes)),
    http4sPartialFunction = Some(updateConsumerGrantTypes)
  )
}
