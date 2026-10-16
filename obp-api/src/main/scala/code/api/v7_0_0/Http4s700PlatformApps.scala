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
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, UserOrApplication, Http4sRoute}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{ApiRole, CustomJsonFormats, Glossary, NewStyle}
import code.consumer.Consumers
import code.platformapp.{PlatformAppRequiredScopeInput, PlatformAppTrait, PlatformApps}
import code.scope.Scope
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Box, Full}
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * The v7.0.0 Platform Apps endpoints: the Consumers an installation runs as part of its own deployment
 * (see [[code.platformapp.PlatformApps]] and the Glossary item "Platform Apps").
 *
 * Declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700PlatformApps {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private val MaxLabelLength = 100
  private val MaxRequiredScopes = 100
  private val MaxNeededForLength = 1000
  private val MaxVersionLength = 100

  private def provider = PlatformApps.platformAppProvider.vend

  /** The app as JSON: its declared Scopes, each marked held or not by its Consumer. */
  private def platformAppJson(app: PlatformAppTrait): PlatformAppJsonV700 = {
    val consumer = Consumers.consumers.vend.getConsumerByConsumerId(app.consumerId)
    val held = consumer
      .flatMap(c => Scope.scope.vend.getScopesByConsumerId(c.id.get.toString))
      .openOr(Nil)
      .map(s => (s.roleName, s.bankId))
    JSONFactory700PlatformApps.createPlatformAppJson(
      app,
      consumer.map(_.name.get).openOr(""),
      provider.getRequiredScopes(app.consumerId).openOr(Nil),
      held)
  }

  /** A declared Scope is valid if its Role exists and its bank_id suits the Role. */
  private def validScope(s: PlatformAppRequiredScopeJsonV700): Boolean = {
    val role: Box[ApiRole] = net.liftweb.util.Helpers.tryo(ApiRole.valueOf(s.role_name))
    val bankId = Option(s.bank_id).getOrElse("")
    val neededFor = Option(s.needed_for).map(_.trim).getOrElse("")
    role.exists(r => r.requiresBankId == bankId.nonEmpty) &&
      neededFor.nonEmpty && neededFor.length <= MaxNeededForLength
  }

  private val platformAppsDescription =
    s"""A Platform App is a Consumer an installation runs as part of its own deployment, for example the
       |Portal or the API Manager, which calls OBP with its own application token. An administrator marks the
       |Consumer as a Platform App; the app then declares, as itself, the Scopes it needs and what they are
       |needed for, and the administrator can see which of them its Consumer holds.
       |
       |For more information see ${Glossary.getGlossaryItemLink("Platform Apps")}""".stripMargin

  // Route: POST /obp/v7.0.0/management/platform-apps (201)
  lazy val createPlatformApp: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "management" / "platform-apps" =>
      EndpointHelpers.withUserAndBodyCreated[PostPlatformAppJsonV700, PlatformAppJsonV700](req) { (user, body, cc) =>
        val label = Option(body.label).map(_.trim).getOrElse("")
        for {
          _ <- Helper.booleanToFuture(InvalidPlatformApp, failCode = 400, cc = Some(cc)) {
            label.nonEmpty && label.length <= MaxLabelLength
          }
          _ <- NewStyle.function.getConsumerByConsumerId(body.consumer_id, Some(cc))
          _ <- Helper.booleanToFuture(PlatformAppAlreadyExists, failCode = 409, cc = Some(cc)) {
            provider.getPlatformApp(body.consumer_id).isEmpty
          }
          app <- Future(provider.createPlatformApp(body.consumer_id, label, user.userId)) map {
            x => fullOrFail(x, cc)
          }
        } yield platformAppJson(app)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createPlatformApp),
    "POST",
    "/management/platform-apps",
    "Create Platform App",
    s"""Mark a Consumer as a Platform App, with the name administrators will know it by (`label`, 1 to
       |$MaxLabelLength characters). Once marked, the app can declare the Scopes it needs with
       |Update Current Consumer Platform App.
       |
       |$platformAppsDescription
       |""".stripMargin,
    JSONFactory700PlatformApps.postPlatformAppJsonV700Example,
    JSONFactory700PlatformApps.platformAppJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, InvalidPlatformApp,
      ConsumerNotFoundByConsumerId, PlatformAppAlreadyExists, UnknownError),
    List(apiTagConsumer, apiTagScope),
    Some(List(canCreatePlatformApp)),
    http4sPartialFunction = Some(createPlatformApp)
  )

  // Route: GET /obp/v7.0.0/management/platform-apps
  lazy val getPlatformApps: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "platform-apps" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        Future(provider.getPlatformApps()) map { x =>
          PlatformAppsJsonV700(fullOrFail(x, cc).map(platformAppJson))
        }
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getPlatformApps),
    "GET",
    "/management/platform-apps",
    "Get Platform Apps",
    s"""The Platform Apps of this installation. For each: its Consumer, the Scopes it has declared it needs
       |and whether its Consumer holds each (`held`), and a `state`: `ok` when every required Scope is held,
       |`missing` when some are not (Scopes marked `optional` do not count), and `not_declared` when the app
       |has not yet said what it needs.
       |
       |$platformAppsDescription
       |""".stripMargin,
    EmptyBody,
    JSONFactory700PlatformApps.platformAppsJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
    List(apiTagConsumer, apiTagScope),
    Some(List(canGetPlatformApps)),
    http4sPartialFunction = Some(getPlatformApps)
  )

  // Route: DELETE /obp/v7.0.0/management/platform-apps/CONSUMER_ID (204)
  lazy val deletePlatformApp: Http4sRoute = Http4sRoute {
    case req @ DELETE -> `prefixPath` / "management" / "platform-apps" / consumerId =>
      EndpointHelpers.withUserDelete(req) { (_, cc) =>
        for {
          _ <- Helper.booleanToFuture(PlatformAppNotFound, failCode = 404, cc = Some(cc)) {
            provider.getPlatformApp(consumerId).isDefined
          }
          deleted <- Future(provider.deletePlatformApp(consumerId)) map { x => fullOrFail(x, cc) }
        } yield deleted
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deletePlatformApp),
    "DELETE",
    "/management/platform-apps/CONSUMER_ID",
    "Delete Platform App",
    s"""Stop treating a Consumer as a Platform App, and forget the Scopes it declared. The Consumer and
       |its Scopes are not changed.
       |
       |$platformAppsDescription
       |""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, PlatformAppNotFound, UnknownError),
    List(apiTagConsumer, apiTagScope),
    Some(List(canDeletePlatformApp)),
    http4sPartialFunction = Some(deletePlatformApp)
  )

  // Route: PUT /obp/v7.0.0/consumers/current/platform-app
  // No Role: an app may always say what it needs. It is refused for a Consumer an administrator has not
  // marked, so only the apps an administrator chose appear on the list.
  lazy val updateCurrentConsumerPlatformApp: Http4sRoute = Http4sRoute {
    case req @ PUT -> `prefixPath` / "consumers" / "current" / "platform-app" =>
      EndpointHelpers.executeFutureWithBody[PutPlatformAppDeclarationJsonV700, PlatformAppJsonV700](req) { (body, cc) =>
        for {
          consumer <- Future(cc.consumer match {
            case Full(c) => Full(c)
            case _ => net.liftweb.common.Empty
          }).map(code.api.util.APIUtil.unboxFullOrFail(_, Some(cc), ApplicationNotIdentified, 401))
          consumerId = consumer.consumerId.get
          // Say which Consumer to mark and where to read how: the app's developer may never have heard of Platform Apps.
          notMarked = s"$PlatformAppNotFound This Consumer's CONSUMER_ID is $consumerId (name: ${consumer.name.get}). " +
            s"See the glossary entry Platform Apps: GET /obp/v7.0.0/api/glossary/Platform%20Apps or " +
            s"${Glossary.apiExplorerUrl}/glossary#Platform%20Apps"
          _ <- Helper.booleanToFuture(notMarked, failCode = 404, cc = Some(cc)) {
            provider.getPlatformApp(consumerId).isDefined
          }
          scopes = Option(body.required_scopes).getOrElse(Nil)
          version = body.version.map(_.trim).filter(_.nonEmpty)
          _ <- Helper.booleanToFuture(InvalidPlatformAppDeclaration, failCode = 400, cc = Some(cc)) {
            scopes.length <= MaxRequiredScopes && scopes.forall(validScope) &&
              version.forall(_.length <= MaxVersionLength)
          }
          inputs = scopes.map(s => PlatformAppRequiredScopeInput(s.role_name, Option(s.bank_id).getOrElse(""),
            s.needed_for.trim, s.optional.getOrElse(false)))
          // One row per Role and bank id: a repeated Scope keeps its first declaration.
          distinct = inputs.foldLeft(List.empty[PlatformAppRequiredScopeInput]) { (kept, s) =>
            if (kept.exists(k => k.roleName == s.roleName && k.bankId == s.bankId)) kept else kept :+ s
          }
          app <- Future(provider.declareRequiredScopes(consumerId, version, distinct)) map { x => fullOrFail(x, cc) }
        } yield platformAppJson(app)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateCurrentConsumerPlatformApp),
    "PUT",
    "/consumers/current/platform-app",
    "Update Current Consumer Platform App",
    s"""Declare the Scopes the calling Consumer needs, as a Platform App: for each, the Role, its `bank_id`
       |(a bank id, SYS for the system space of Dynamic Entities, or empty for a system Role), what it is
       |`needed_for` (1 to $MaxNeededForLength characters, written for the administrator deciding whether
       |to grant it) and whether it is `optional`. The declaration replaces the previous one. `version` is
       |the app's own version, if it wants to report it.
       |
       |No Role is required, and an Application on its own may call it (client credentials or a Consumer
       |Key), so an app can declare its needs at startup. The Consumer must first be marked as a Platform App
       |by an administrator, otherwise the call gets $PlatformAppNotFound
       |
       |$platformAppsDescription
       |""".stripMargin,
    JSONFactory700PlatformApps.putPlatformAppDeclarationJsonV700Example,
    JSONFactory700PlatformApps.platformAppJsonV700Example,
    List(ApplicationNotIdentified, InvalidJsonFormat, PlatformAppNotFound, InvalidPlatformAppDeclaration, UnknownError),
    List(apiTagConsumer, apiTagScope),
    None,
    authMode = UserOrApplication,
    http4sPartialFunction = Some(updateCurrentConsumerPlatformApp)
  )

  private def fullOrFail[T: Manifest](box: Box[T], cc: code.api.util.CallContext): T =
    code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), UnknownError, 400)
}
