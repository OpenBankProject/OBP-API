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
import code.api.dynamic.domainapi.DomainApiPaths
import code.api.dynamic.entity.helper.DynamicEntitySpace
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, Http4sRoute}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.{CallContext, CustomJsonFormats, Glossary}
import code.domainapi.{DomainApiDbProvider, DomainApiTrait, DomainApis}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Box
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * This object holds the v7.0.0 endpoints that manage Domain APIs: a space's Dynamic Entities and Dynamic
 * Resource Docs published under a base path of its own (see [[code.domainapi.DomainApis]] and the Glossary
 * item "Domain APIs"). The calls under a base path are served by
 * [[code.api.dynamic.domainapi.Http4sDomainApi]], not here.
 *
 * As for the v7.0.0 Dynamic Entity definitions, BANK_ID is a bank's id or SYS for the system space; the
 * ResourceDocs declare allowSystemSpace() so the middleware lets SYS through and checks each Role at the
 * BANK_ID in the URL.
 *
 * Declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700DomainApis {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private val MaxTitleLength = 255
  private val MaxDescriptionLength = 2000

  private def provider = DomainApis.domainApiProvider.vend

  private def space(bankIdInUrl: String): String =
    DynamicEntitySpace.bankIdOrSystem(DynamicEntitySpace.bankIdOrNoneForSystem(bankIdInUrl))

  private case class Checked(basePath: String, version: String, title: String, description: String)

  /**
   * The checks a registration must pass, on create and on update (`domainApiId` is the one being updated,
   * which may keep its own base path): the base path's shape, the version, the title, that no other
   * Domain API's base path overlaps it, and that none of the space's endpoints is ambiguous with another
   * (see DomainApiPaths.ambiguitiesInSpace). Dynamic Entity and Dynamic Resource Doc writes are refused when
   * they would make one ambiguous, so this only finds what predates that rule.
   */
  private def check(spaceId: String, body: PostDomainApiJsonV700, domainApiId: Option[String], cc: CallContext): Future[Checked] = {
    val basePath = Option(body.base_path).map(_.trim).getOrElse("")
    val version = Option(body.version).map(_.trim).getOrElse("")
    val title = Option(body.title).map(_.trim).getOrElse("")
    val description = body.description.map(_.trim).getOrElse("")
    val basePathProblem = DomainApiPaths.basePathProblem(basePath)
    lazy val overlapping = provider.getAllInEverySpace().openOr(Nil)
      .filterNot(other => domainApiId.contains(other.domainApiId))
      .filter(other => DomainApiPaths.overlap(other.basePath, basePath))
      .map(other => s"${other.basePath} (bank_id ${other.bankId})")
    lazy val ambiguities = DomainApiPaths.storedAmbiguitiesInSpace(DynamicEntitySpace.bankIdOrNoneForSystem(spaceId))
    for {
      _ <- Helper.booleanToFuture(s"$InvalidDomainApiBasePath${DomainApiPaths.reservedFirstSegments.toList.sorted.mkString(", ")}. Current base_path is $basePath: ${basePathProblem.getOrElse("")}.", 400, Some(cc)) {
        basePathProblem.isEmpty
      }
      _ <- Helper.booleanToFuture(s"$InvalidDomainApiVersion Current version is $version.", 400, Some(cc)) {
        DomainApiPaths.versionFits(version, basePath)
      }
      _ <- Helper.booleanToFuture(InvalidDomainApiTitle, 400, Some(cc)) {
        title.nonEmpty && title.length <= MaxTitleLength && description.length <= MaxDescriptionLength
      }
      _ <- Helper.booleanToFuture(s"$DomainApiBasePathAlreadyExists${overlapping.mkString(", ")}", 409, Some(cc)) {
        overlapping.isEmpty
      }
      _ <- Helper.booleanToFuture(s"$DomainApiPathClash${ambiguities.mkString("; ")}", 409, Some(cc)) {
        ambiguities.isEmpty
      }
    } yield Checked(basePath, version, title, description)
  }

  private def fullOrFail[T: Manifest](box: Box[T], cc: CallContext): T =
    code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), UnknownError, 400)

  private def found(spaceId: String, domainApiId: String, cc: CallContext): Future[DomainApiTrait] =
    Future(provider.get(spaceId, domainApiId)).map(box =>
      code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), s"$DomainApiNotFound Current DOMAIN_API_ID is $domainApiId.", 404))

  private val domainApisDescription =
    s"""A Domain API publishes the Dynamic Entities and Dynamic Resource Docs (Dynamic Queries included) of one
       |space under a base path of its own, without OBP's own URL structure in front of them: with the base path
       |`carbon-registry/v1` over the system space, `/carbon-registry/v1/activity` answers what
       |`/obp/v7.0.0/banks/SYS/dynamic-entities/activity` answers, and `/carbon-registry/v1/openapi.yaml` (or
       |`openapi.json`) is its OpenAPI document. It only renames: every call runs the same authentication, Roles
       |and access checks as the OBP URL, and a Dynamic Entity record response leaves out `bank_id`.
       |
       |BANK_ID is the space: a bank's id, or `SYS` for the system space. The Role is checked at that BANK_ID.
       |
       |`base_path` is two to five segments of lowercase letters, digits, hyphens and dots, ending with the
       |major version as `vN`, and must not overlap another Domain API's. `version` is the full semantic
       |version, MAJOR.MINOR.PATCH, whose MAJOR is the N of the base path: a compatible change edits `version`
       |and leaves every URL alone, and a breaking change gets a new Domain API with a new base path.
       |
       |A Domain API is refused while two endpoints of its space are ambiguous with each other under it: a
       |Dynamic Resource Doc whose path starts with a path variable, with the name of one of the space's
       |Dynamic Entities or with a segment the Dynamic Entity URLs or the documentation use (`my`, `public`,
       |`community`, `openapi.json`, `openapi.yaml`), or two docs of one verb that would match one request.
       |Creating or changing a Dynamic Entity or Dynamic Resource Doc that would cause one is refused in
       |every space, so this only happens to a space that held one before that rule.
       |
       |On this instance a change is seen at once by the node that made it, and by the others within
       |${DomainApiDbProvider.cacheTtlSeconds} seconds.
       |
       |For more information see ${Glossary.getGlossaryItemLink("Domain APIs")}""".stripMargin

  private val errorsOnWrite = List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
    InvalidDomainApiBasePath, InvalidDomainApiVersion, InvalidDomainApiTitle, DomainApiBasePathAlreadyExists,
    DomainApiPathClash, UnknownError)

  // Route: POST /obp/v7.0.0/management/banks/BANK_ID/domain-apis (201)
  lazy val createDomainApi: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "management" / "banks" / bankIdInUrl / "domain-apis" =>
      EndpointHelpers.withUserAndBodyCreated[PostDomainApiJsonV700, DomainApiJsonV700](req) { (user, body, cc) =>
        val spaceId = space(bankIdInUrl)
        for {
          checked <- check(spaceId, body, None, cc)
          created <- Future(provider.create(spaceId, checked.basePath, checked.version, checked.title, checked.description, user.userId))
            .map(fullOrFail(_, cc))
        } yield JSONFactory700DomainApis.createDomainApiJson(created)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createDomainApi),
    "POST",
    "/management/banks/BANK_ID/domain-apis",
    "Create Domain API",
    s"""Publish the Dynamic Entities and Dynamic Resource Docs of a space under a base path of its own.
       |
       |$domainApisDescription""".stripMargin,
    JSONFactory700DomainApis.postDomainApiJsonV700Example,
    JSONFactory700DomainApis.domainApiJsonV700Example,
    errorsOnWrite,
    apiTagDynamic :: apiTagApi :: Nil,
    Some(canCreateDomainApi :: Nil),
    http4sPartialFunction = Some(createDomainApi)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/domain-apis
  lazy val getDomainApis: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankIdInUrl / "domain-apis" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        Future(provider.getAll(space(bankIdInUrl))).map(box =>
          DomainApisJsonV700(fullOrFail(box, cc).map(JSONFactory700DomainApis.createDomainApiJson)))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomainApis),
    "GET",
    "/management/banks/BANK_ID/domain-apis",
    "Get Domain APIs",
    s"""The Domain APIs of a space.
       |
       |$domainApisDescription""".stripMargin,
    EmptyBody,
    JSONFactory700DomainApis.domainApisJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
    apiTagDynamic :: apiTagApi :: Nil,
    Some(canGetDomainApis :: Nil),
    http4sPartialFunction = Some(getDomainApis)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/domain-apis/DOMAIN_API_ID
  lazy val getDomainApi: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankIdInUrl / "domain-apis" / domainApiId =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        found(space(bankIdInUrl), domainApiId, cc).map(JSONFactory700DomainApis.createDomainApiJson)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDomainApi),
    "GET",
    "/management/banks/BANK_ID/domain-apis/DOMAIN_API_ID",
    "Get Domain API",
    s"""One Domain API of a space.
       |
       |$domainApisDescription""".stripMargin,
    EmptyBody,
    JSONFactory700DomainApis.domainApiJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, DomainApiNotFound, UnknownError),
    apiTagDynamic :: apiTagApi :: Nil,
    Some(canGetDomainApis :: Nil),
    http4sPartialFunction = Some(getDomainApi)
  ).allowSystemSpace()

  // Route: PUT /obp/v7.0.0/management/banks/BANK_ID/domain-apis/DOMAIN_API_ID
  lazy val updateDomainApi: Http4sRoute = Http4sRoute {
    case req @ PUT -> `prefixPath` / "management" / "banks" / bankIdInUrl / "domain-apis" / domainApiId =>
      EndpointHelpers.withUserAndBody[PostDomainApiJsonV700, DomainApiJsonV700](req) { (_, body, cc) =>
        val spaceId = space(bankIdInUrl)
        for {
          _ <- found(spaceId, domainApiId, cc)
          checked <- check(spaceId, body, Some(domainApiId), cc)
          updated <- Future(provider.update(spaceId, domainApiId, checked.basePath, checked.version, checked.title, checked.description))
            .map(fullOrFail(_, cc))
        } yield JSONFactory700DomainApis.createDomainApiJson(updated)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(updateDomainApi),
    "PUT",
    "/management/banks/BANK_ID/domain-apis/DOMAIN_API_ID",
    "Update Domain API",
    s"""Change a Domain API: its base path, version, title or description. The same checks as on create apply.
       |Changing the base path moves every published URL, so it is a breaking change for its clients; a
       |compatible change only edits `version`.
       |
       |$domainApisDescription""".stripMargin,
    JSONFactory700DomainApis.postDomainApiJsonV700Example,
    JSONFactory700DomainApis.domainApiJsonV700Example,
    DomainApiNotFound :: errorsOnWrite,
    apiTagDynamic :: apiTagApi :: Nil,
    Some(canUpdateDomainApi :: Nil),
    http4sPartialFunction = Some(updateDomainApi)
  ).allowSystemSpace()

  // Route: DELETE /obp/v7.0.0/management/banks/BANK_ID/domain-apis/DOMAIN_API_ID (204)
  lazy val deleteDomainApi: Http4sRoute = Http4sRoute {
    case req @ DELETE -> `prefixPath` / "management" / "banks" / bankIdInUrl / "domain-apis" / domainApiId =>
      EndpointHelpers.withUserDelete(req) { (_, cc) =>
        val spaceId = space(bankIdInUrl)
        for {
          _ <- found(spaceId, domainApiId, cc)
          deleted <- Future(provider.delete(spaceId, domainApiId)).map(fullOrFail(_, cc))
        } yield deleted
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(deleteDomainApi),
    "DELETE",
    "/management/banks/BANK_ID/domain-apis/DOMAIN_API_ID",
    "Delete Domain API",
    s"""Stop publishing a space under a Domain API's base path. The Dynamic Entities and Dynamic Resource Docs
       |are not changed and stay available at their OBP URLs.
       |
       |$domainApisDescription""".stripMargin,
    EmptyBody,
    EmptyBody,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, DomainApiNotFound, UnknownError),
    apiTagDynamic :: apiTagApi :: Nil,
    Some(canDeleteDomainApi :: Nil),
    http4sPartialFunction = Some(deleteDomainApi)
  ).allowSystemSpace()
}
