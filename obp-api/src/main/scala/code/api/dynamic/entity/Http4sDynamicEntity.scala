/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package code.api.dynamic.entity

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import code.DynamicData.{DynamicData, DynamicDataProvider}
import code.api.Constant.PARAM_LOCALE
import code.api.dynamic.entity.helper.{CommunityEntityName, DynamicEntityHelper, DynamicEntityInfo, EntityName, PublicEntityName}
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.api.util.http4s.{Http4sCallContextBuilder, Http4sRequestAttributes}
import code.api.util.{CallContext, CustomJsonFormats, NewStyle}
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.DynamicEntityOperation
import com.openbankproject.commons.model.enums.DynamicEntityOperation._
import com.openbankproject.commons.util.{ApiVersion, JsonUtils}
import net.liftweb.common._
import net.liftweb.http.{InMemoryResponse, JsonResponse}
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.util.StringHelpers
import org.apache.commons.lang3.StringUtils
import org.http4s._
import org.typelevel.ci.CIString

import scala.concurrent.Future

/**
 * Native http4s routes for the dynamic-entity data plane.
 *
 * Serves the runtime, operator-created entity URLs under `/obp/dynamic-entity/...`
 * (`{entityName}`, `my/{entityName}`, `public/{entityName}`, `community/{entityName}`,
 * and their `banks/BANK_ID/...` variants). This replaces the Lift dispatch through
 * `OBPAPIDynamicEntity` → `APIMethodsDynamicEntity` for the entity data plane.
 *
 * The URL→registry matching reuses the framework-agnostic `EntityName` /
 * `PublicEntityName` / `CommunityEntityName` extractors (they take `List[String]`
 * and consult `DynamicEntityHelper.definitionsMap`, rebuilt per request from the DB).
 * The business logic mirrors the Lift `genericEndpoint` / `publicEndpoint` /
 * `communityEndpoint` partial functions verbatim — same auth (`authenticatedAccess` /
 * `anonymousAccess`), role checks, interceptors and `NewStyle.invokeDynamicConnector`
 * calls — only the Lift `Req` / `JsonResponse` plumbing is replaced: matching is done
 * on the http4s path, query params come from the URI, and the `(JValue, HttpCode)`
 * return is replaced by `EndpointHelpers` (200 for GET/PUT/DELETE, 201 for POST).
 *
 * Admin CRUD (`createDynamicEntity`, `getDynamicEntities`, …) is unaffected — it is
 * already native http4s in the versioned files (e.g. `Http4s600`).
 *
 * Note: `OBPAPIDynamicEntity` remains registered on the Lift bridge as a dormant
 * fallback (this route wins by ordering in `Http4sApp.baseServices`); the Lift
 * registration is removed in the bridge-removal PR. dynamic-*endpoint* (runtime
 * Scala codegen) is a separate workstream and still served by the bridge.
 */
object Http4sDynamicEntity extends MdcLoggable {

  private type HttpF[A] = OptionT[IO, A]

  private implicit val formats: Formats = CustomJsonFormats.formats

  // "dynamic-entity" — passed as the CallContext apiVersion so getUserAndSessionContextFuture's
  // S.request fallback (for implementedInVersion/verb/url) is never reached (it throws when not
  // under a Lift dispatch). Same trick as Http4sResourceDocs / DirectLoginRoutes.
  private val dynamicEntityVersion: String = ApiVersion.`dynamic-entity`.toString

  // ── Shared helpers (ported from ImplementationsDynamicEntity) ──────────────

  private def unboxResult[T: Manifest](box: Box[T], entityName: String): T = {
    if (box.isInstanceOf[Failure]) {
      val failure = box.asInstanceOf[Failure]
      // change the internal db column name 'dynamicdataid' to entity's id name
      val msg = failure.msg.replace(DynamicData.DynamicDataId.dbColumnName, StringUtils.uncapitalize(entityName) + "Id")
      val changedMsgFailure = failure.copy(msg = s"$InternalServerError $msg")
      fullBoxOrException[T](changedMsgFailure)
    }
    box.openOrThrowException("impossible error")
  }

  // Filter a result list by `field=value` query params (skip the locale param), mirroring
  // the Lift `filterDynamicObjects(resultList, req)` which read `req.params`.
  private def filterDynamicObjects(resultList: JArray, params: Map[String, List[String]]): JArray = {
    val effective = params.filter(_._1 != PARAM_LOCALE)
    if (effective.isEmpty) resultList
    else JArray(resultList.arr.filter { jValue =>
      effective.forall { case (path, values) =>
        values.exists(JsonUtils.isFieldEquals(jValue, path, _))
      }
    })
  }

  // The before-authenticate interceptor short-circuits with a fully-formed Lift JsonResponse
  // (rarely configured). Render it directly to http4s — ErrorResponseConverter only knows how
  // to turn thrown APIFailures into responses, not an arbitrary JsonResponse.
  private def liftJsonResponseToHttp4s(jr: JsonResponse): IO[Response[IO]] = jr.toResponse match {
    case InMemoryResponse(data, headers, _, code) =>
      val status = org.http4s.Status.fromInt(code).getOrElse(org.http4s.Status.InternalServerError)
      val h = Headers(headers.map { case (k, v) => Header.Raw(CIString(k), v) })
      IO.pure(Response[IO](status).withEntity(data).withHeaders(h))
    case other =>
      IO.pure(Response[IO](org.http4s.Status.fromInt(other.code).getOrElse(org.http4s.Status.InternalServerError)))
  }

  /**
   * Build the CallContext, run the before-authenticate interceptor, then execute the
   * handler body through the standard EndpointHelpers (200 or 201). The augmented
   * CallContext (operationId + resourceDocument set, mirroring the Lift `cc.copy(...)`) is
   * stashed so auth/role checks and rate-limiting see it.
   */
  private def respond(
    req: Request[IO],
    resourceDoc: Option[ResourceDoc],
    operationId: String,
    created: Boolean
  )(body: CallContext => Future[JValue]): IO[Response[IO]] =
    Http4sCallContextBuilder.fromRequest(req, apiVersion = dynamicEntityVersion).flatMap { baseCc =>
      val cc = baseCc.copy(operationId = Some(operationId), resourceDocument = resourceDoc)
      beforeAuthenticateInterceptResult(Some(cc), operationId) match {
        case Full(jr) => liftJsonResponseToHttp4s(jr)
        case _ =>
          val reqWithCC = req.withAttribute(Http4sRequestAttributes.callContextKey, cc)
          if (created) EndpointHelpers.executeFutureCreated[JValue](reqWithCC)(body(cc))
          else EndpointHelpers.executeAndRespond[JValue](reqWithCC)(body)
      }
    }

  private def queryParams(req: Request[IO]): Map[String, List[String]] =
    req.uri.query.multiParams.map { case (k, vs) => k -> vs.toList }

  // ── Generic endpoint (authenticated, role-gated, full CRUD) ────────────────

  private def genericGet(req: Request[IO], bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): IO[Response[IO]] = {
    val listName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "_list")
    val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
    val isGetAll = StringUtils.isBlank(id)
    val operation: DynamicEntityOperation = if (isGetAll) GET_ALL else GET_ONE
    val splitNameWithBankId = if (bankId.isDefined) s"""$entityName(${bankId.getOrElse("")})""" else entityName
    val mySplitNameWithBankId = s"My$splitNameWithBankId"
    val resourceDoc =
      if (isPersonalEntity) DynamicEntityHelper.operationToResourceDoc.get(operation -> mySplitNameWithBankId)
      else DynamicEntityHelper.operationToResourceDoc.get(operation -> splitNameWithBankId)
    val operationId = resourceDoc.map(_.operationId).orNull
    val params = queryParams(req)
    respond(req, resourceDoc, operationId, created = false) { cc =>
      for {
        (Full(u), callContext) <- authenticatedAccess(cc)
        (_, callContext) <-
          if (bankId.isDefined) NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
          else Future.successful(("", callContext))
        personalRequiresRole = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.personalRequiresRole)
        _ <- if (isPersonalEntity && !personalRequiresRole) Future.successful(true)
             else NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canGetRole(entityName, bankId), callContext)
        jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
          case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
        })
        _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
          jsonResponse.isEmpty
        }
        (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, None, Option(id).filter(StringUtils.isNotBlank), bankId, None,
          Some(u.userId), isPersonalEntity, Some(cc))
        _ <- Helper.booleanToFuture(
          s"$EntityNotFoundByEntityId Entity: '$entityName', entityId: '${id}'" + bankId.map(bid => s", bank_id: '$bid'").getOrElse(""),
          404, cc = callContext) {
          box.isDefined
        }
      } yield {
        if (isGetAll) {
          val resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entityName)
          if (bankId.isDefined) {
            val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
            val result: JObject = (listName -> filterDynamicObjects(resultList, params))
            bankIdJobject merge result
          } else {
            val result: JObject = (listName -> filterDynamicObjects(resultList, params))
            result
          }
        } else {
          val singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
          if (bankId.isDefined) {
            val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
            val result: JObject = (singleName -> singleObject)
            bankIdJobject merge result
          } else {
            val result: JObject = (singleName -> singleObject)
            result
          }
        }
      }
    }
  }

  private def genericCreate(req: Request[IO], bankId: Option[String], entityName: String, isPersonalEntity: Boolean): IO[Response[IO]] = {
    val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
    val operation: DynamicEntityOperation = CREATE
    val splitNameWithBankId = if (bankId.isDefined) s"""$entityName(${bankId.getOrElse("")})""" else entityName
    val mySplitNameWithBankId = s"My$splitNameWithBankId"
    val resourceDoc =
      if (isPersonalEntity) DynamicEntityHelper.operationToResourceDoc.get(operation -> mySplitNameWithBankId)
      else DynamicEntityHelper.operationToResourceDoc.get(operation -> splitNameWithBankId)
    val operationId = resourceDoc.map(_.operationId).orNull
    respond(req, resourceDoc, operationId, created = true) { cc =>
      val json = net.liftweb.json.parse(cc.httpBody.getOrElse(""))
      for {
        (Full(u), callContext) <- authenticatedAccess(cc)
        (_, callContext) <-
          if (bankId.isDefined) NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
          else Future.successful(("", callContext))
        personalRequiresRole = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.personalRequiresRole)
        _ <- if (isPersonalEntity && !personalRequiresRole) Future.successful(true)
             else NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canCreateRole(entityName, bankId), callContext)
        jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
          case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
        })
        _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
          jsonResponse.isEmpty
        }
        // Pass userId for all authenticated requests - personal records are filtered by userId
        (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, Some(json.asInstanceOf[JObject]), None, bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
      } yield {
        val result: JObject = (singleName -> singleObject)
        if (bankId.isDefined) {
          val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
          bankIdJobject merge result
        } else {
          result
        }
      }
    }
  }

  private def genericUpdate(req: Request[IO], bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): IO[Response[IO]] = {
    val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
    val operation: DynamicEntityOperation = UPDATE
    val splitNameWithBankId = if (bankId.isDefined) s"""$entityName(${bankId.getOrElse("")})""" else entityName
    val mySplitNameWithBankId = s"My$splitNameWithBankId"
    val resourceDoc =
      if (isPersonalEntity) DynamicEntityHelper.operationToResourceDoc.get(operation -> mySplitNameWithBankId)
      else DynamicEntityHelper.operationToResourceDoc.get(operation -> splitNameWithBankId)
    val operationId = resourceDoc.map(_.operationId).orNull
    respond(req, resourceDoc, operationId, created = false) { cc =>
      val json = net.liftweb.json.parse(cc.httpBody.getOrElse(""))
      for {
        (Full(u), callContext) <- authenticatedAccess(cc)
        (_, callContext) <-
          if (bankId.isDefined) NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
          else Future.successful(("", callContext))
        personalRequiresRole = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.personalRequiresRole)
        _ <- if (isPersonalEntity && !personalRequiresRole) Future.successful(true)
             else NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canUpdateRole(entityName, bankId), callContext)
        jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
          case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
        })
        _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
          jsonResponse.isEmpty
        }
        (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        _ <- Helper.booleanToFuture(
          s"$EntityNotFoundByEntityId Entity: '$entityName', entityId: '$id'" + bankId.map(bid => s", bank_id: '$bid'").getOrElse(""),
          404, cc = callContext) {
          box.isDefined
        }
        (box: Box[JValue], _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, Some(json.asInstanceOf[JObject]), Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
      } yield {
        val result: JObject = (singleName -> singleObject)
        if (bankId.isDefined) {
          val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
          bankIdJobject merge result
        } else {
          result
        }
      }
    }
  }

  private def genericDelete(req: Request[IO], bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): IO[Response[IO]] = {
    val operation: DynamicEntityOperation = DELETE
    val splitNameWithBankId = if (bankId.isDefined) s"""$entityName(${bankId.getOrElse("")})""" else entityName
    val mySplitNameWithBankId = s"My$splitNameWithBankId"
    val resourceDoc =
      if (isPersonalEntity) DynamicEntityHelper.operationToResourceDoc.get(operation -> mySplitNameWithBankId)
      else DynamicEntityHelper.operationToResourceDoc.get(operation -> splitNameWithBankId)
    val operationId = resourceDoc.map(_.operationId).orNull
    respond(req, resourceDoc, operationId, created = false) { cc =>
      for {
        (Full(u), callContext) <- authenticatedAccess(cc)
        (_, callContext) <-
          if (bankId.isDefined) NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
          else Future.successful(("", callContext))
        personalRequiresRole = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.personalRequiresRole)
        _ <- if (isPersonalEntity && !personalRequiresRole) Future.successful(true)
             else NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canDeleteRole(entityName, bankId), callContext)
        jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
          case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
        })
        _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
          jsonResponse.isEmpty
        }
        (box, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        _ <- Helper.booleanToFuture(
          s"$EntityNotFoundByEntityId Entity: '$entityName', entityId: '$id'" + bankId.map(bid => s", bank_id: '$bid'").getOrElse(""),
          404, cc = callContext) {
          box.isDefined
        }
        (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, None, Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        deleteResult: JBool = unboxResult(box.asInstanceOf[Box[JBool]], entityName)
      } yield {
        deleteResult
      }
    }
  }

  // ── Public endpoint (anonymous, read-only) ─────────────────────────────────

  private def publicGet(req: Request[IO], bankId: Option[String], entityName: String, id: String): IO[Response[IO]] = {
    val listName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "_list")
    val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
    val isGetAll = StringUtils.isBlank(id)
    val operation: DynamicEntityOperation = if (isGetAll) GET_ALL else GET_ONE
    val splitNameWithBankId = if (bankId.isDefined) s"""$entityName(${bankId.getOrElse("")})""" else entityName
    val publicSplitNameWithBankId = s"Public$splitNameWithBankId"
    val resourceDoc = DynamicEntityHelper.operationToResourceDoc.get(operation -> publicSplitNameWithBankId)
    val operationId = resourceDoc.map(_.operationId).orNull
    val params = queryParams(req)
    respond(req, resourceDoc, operationId, created = false) { cc =>
      for {
        (_, callContext) <- anonymousAccess(cc)
        (_, callContext) <-
          if (bankId.isDefined) NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
          else Future.successful(("", callContext))
        // No entitlement checks for public endpoints; userId=None, isPersonalEntity=false
        (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, None, Option(id).filter(StringUtils.isNotBlank), bankId, None,
          None, false, Some(cc))
        _ <- Helper.booleanToFuture(
          s"$EntityNotFoundByEntityId Entity: '$entityName', entityId: '${id}'" + bankId.map(bid => s", bank_id: '$bid'").getOrElse(""),
          404, cc = callContext) {
          box.isDefined
        }
      } yield {
        if (isGetAll) {
          val resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entityName)
          if (bankId.isDefined) {
            val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
            val result: JObject = (listName -> filterDynamicObjects(resultList, params))
            bankIdJobject merge result
          } else {
            val result: JObject = (listName -> filterDynamicObjects(resultList, params))
            result
          }
        } else {
          val singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
          if (bankId.isDefined) {
            val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
            val result: JObject = (singleName -> singleObject)
            bankIdJobject merge result
          } else {
            val result: JObject = (singleName -> singleObject)
            result
          }
        }
      }
    }
  }

  // ── Community endpoint (authenticated, role-gated, returns all users' records) ─

  private def communityGet(req: Request[IO], bankId: Option[String], entityName: String, id: String): IO[Response[IO]] = {
    val listName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "_list")
    val singleName = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")
    val isGetAll = StringUtils.isBlank(id)
    val operation: DynamicEntityOperation = if (isGetAll) GET_ALL else GET_ONE
    val splitNameWithBankId = if (bankId.isDefined) s"""$entityName(${bankId.getOrElse("")})""" else entityName
    val communitySplitNameWithBankId = s"Community$splitNameWithBankId"
    val resourceDoc = DynamicEntityHelper.operationToResourceDoc.get(operation -> communitySplitNameWithBankId)
    val operationId = resourceDoc.map(_.operationId).orNull
    val params = queryParams(req)
    respond(req, resourceDoc, operationId, created = false) { cc =>
      for {
        (Full(u), callContext) <- authenticatedAccess(cc)
        (_, callContext) <-
          if (bankId.isDefined) NewStyle.function.getBank(bankId.map(BankId(_)).orNull, callContext)
          else Future.successful(("", callContext))
        _ <- NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canGetRole(entityName, bankId), callContext)
        jsonResponse: Box[ErrorMessage] = afterAuthenticateInterceptResult(callContext, operationId).collect({
          case JsonResponseExtractor(message, code) => ErrorMessage(code, message)
        })
        _ <- Helper.booleanToFuture(failMsg = jsonResponse.map(_.message).orNull, failCode = jsonResponse.map(_.code).openOr(400), cc = callContext) {
          jsonResponse.isEmpty
        }
      } yield {
        if (isGetAll) {
          val resultList: List[JObject] = DynamicDataProvider.connectorMethodProvider.vend.getAllDataJsonCommunity(bankId, entityName)
          val resultArray = JArray(resultList)
          if (bankId.isDefined) {
            val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
            val result: JObject = (listName -> filterDynamicObjects(resultArray, params))
            bankIdJobject merge result
          } else {
            val result: JObject = (listName -> filterDynamicObjects(resultArray, params))
            result
          }
        } else {
          val singleResult = DynamicDataProvider.connectorMethodProvider.vend.getCommunity(bankId, entityName, id)
          val singleObject: JValue = singleResult match {
            case Full(data) => net.liftweb.json.parse(data.dataJson)
            case _ => throw new RuntimeException(s"$EntityNotFoundByEntityId Entity: '$entityName', entityId: '$id'" + bankId.map(bid => s", bank_id: '$bid'").getOrElse(""))
          }
          if (bankId.isDefined) {
            val bankIdJobject: JObject = ("bank_id" -> bankId.getOrElse(""))
            val result: JObject = (singleName -> singleObject)
            bankIdJobject merge result
          } else {
            val result: JObject = (singleName -> singleObject)
            result
          }
        }
      }
    }
  }

  // ── Routing ────────────────────────────────────────────────────────────────
  // Match `/obp/dynamic-entity/...`, strip the prefix to the segment list the extractors
  // expect, then dispatch. Public/Community are tried before the generic extractor to
  // preserve the Lift registration precedence (publicEndpoint, communityEndpoint,
  // genericEndpoint). A non-match yields OptionT.none so the request falls through the
  // chain (to the Lift bridge) unchanged.

  private def handle(req: Request[IO], rest: List[String]): IO[Option[Response[IO]]] =
    req.method match {
      case Method.GET => rest match {
        case PublicEntityName(bankId, entityName, id)    => publicGet(req, bankId, entityName, id).map(Some(_))
        case CommunityEntityName(bankId, entityName, id) => communityGet(req, bankId, entityName, id).map(Some(_))
        case EntityName(bankId, entityName, id, isP)     => genericGet(req, bankId, entityName, id, isP).map(Some(_))
        case _ => IO.pure(None)
      }
      case Method.POST => rest match {
        case EntityName(bankId, entityName, _, isP) => genericCreate(req, bankId, entityName, isP).map(Some(_))
        case _ => IO.pure(None)
      }
      case Method.PUT => rest match {
        case EntityName(bankId, entityName, id, isP) => genericUpdate(req, bankId, entityName, id, isP).map(Some(_))
        case _ => IO.pure(None)
      }
      case Method.DELETE => rest match {
        case EntityName(bankId, entityName, id, isP) => genericDelete(req, bankId, entityName, id, isP).map(Some(_))
        case _ => IO.pure(None)
      }
      case _ => IO.pure(None)
    }

  val routes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
    // Drop empty segments to mirror Lift's Req.path.partPath (e.g. trailing slash).
    val segments = req.uri.path.segments.map(_.decoded()).filter(_.nonEmpty).toList
    segments match {
      case "obp" :: "dynamic-entity" :: rest => OptionT(handle(req, rest))
      case _                                 => OptionT.none[IO, Response[IO]]
    }
  }
}
