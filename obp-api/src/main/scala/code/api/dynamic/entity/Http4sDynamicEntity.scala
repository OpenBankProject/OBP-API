/**
Open Bank Project - API
Copyright (C) 2011-2025, TESOBE GmbH

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
TESOBE GmbH
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
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.{Http4sCallContextBuilder, Http4sRequestAttributes, RequestScopeConnection}
import code.api.util.{CallContext, CustomJsonFormats, NewStyle}
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.DynamicEntityOperation
import com.openbankproject.commons.model.enums.DynamicEntityOperation._
import com.openbankproject.commons.util.{ApiShortVersions, ApiStandards, JsonUtils}
import net.liftweb.common._
import net.liftweb.json.JsonAST.{JArray, JBool, JObject, JValue}
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.util.StringHelpers
import org.apache.commons.lang3.StringUtils
import org.http4s.{HttpRoutes, Method, Request, Response}

import scala.concurrent.Future

/**
 * Native http4s service for DynamicEntity runtime CRUD (under /obp/dynamic-entity/).
 *
 * Replaces the Lift OBPAPIDynamicEntity dispatch (genericEndpoint / publicEndpoint /
 * communityEndpoint).  The business logic is a faithful port of
 * [[code.api.dynamic.entity.APIMethodsDynamicEntity]] — same `authenticatedAccess` /
 * `anonymousAccess` / `getBank` / `hasEntitlement` / `invokeDynamicConnector` calls,
 * same before/after authenticate interceptors, same response shapes and status codes.
 *
 * Notes on the port:
 *   - The dynamic-entity set is runtime-mutable (`DynamicEntityHelper.definitionsMap` is
 *     re-queried per request), so this service does NOT use `ResourceDocMiddleware`
 *     (whose ResourceDoc index is built once at startup).  Auth / role / bank checks are
 *     performed inline, exactly as the Lift handlers did.
 *   - The before/after authenticate interceptors carry auth-type / query-param / header-key
 *     validation (before) and Force-Error / JSON-schema validation (after) — see
 *     APIUtil.beforeAuthenticateInterceptors / afterAuthenticateInterceptors.  They are
 *     invoked here exactly as the Lift handlers invoked them; the resulting Box[JsonResponse]
 *     is reduced to (message, code) via JsonResponseExtractor and re-raised through
 *     booleanToFuture (no Lift JsonResponse rendering).
 *   - `CallContext` is built via `Http4sCallContextBuilder.fromRequest` and attached to the
 *     request so the `EndpointHelpers` (error conversion + metric) can be reused.
 *   - Mutating verbs (POST/PUT/DELETE) run inside
 *     `RequestScopeConnection.withBusinessDBTransaction`; GET runs on auto-commit.
 */
object Http4sDynamicEntity extends MdcLoggable {

  private type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats

  private val apiStandard = ApiStandards.obp.toString
  private val apiVersionString = ApiShortVersions.`dynamic-entity`.toString // "dynamic-entity"

  // ----- helpers ported from APIMethodsDynamicEntity -----

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

  /**
   * http4s equivalent of the Lift `filterDynamicObjects(resultList, req)`: filter GET-all
   * results by query parameters (AND across keys, OR across a key's values), excluding the
   * `locale` (PARAM_LOCALE) param.  Lift read `req.params`; here we read the http4s query
   * multiParams (same `Map[String, List[String]]` shape).
   */
  private def filterDynamicObjects(resultList: JArray, params: Map[String, List[String]]): JArray = {
    if (params.isEmpty) resultList
    else {
      val filtered = resultList.arr.filter { jValue =>
        params.filter(_._1 != PARAM_LOCALE).forall { case (path, values) =>
          values.exists(JsonUtils.isFieldEquals(jValue, path, _))
        }
      }
      JArray(filtered)
    }
  }

  private def queryParams(req: Request[IO]): Map[String, List[String]] =
    req.uri.query.multiParams.map { case (k, vs) => k -> vs.toList }

  private def listName(entityName: String): String = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "_list")
  private def singleName(entityName: String): String = StringHelpers.snakify(entityName).replaceFirst("[-_]*$", "")

  private def wrapBankId(bankId: Option[String], result: JObject): JObject =
    if (bankId.isDefined) (("bank_id" -> bankId.getOrElse("")): JObject) merge result else result

  private def notFoundMsg(entityName: String, id: String, bankId: Option[String]): String =
    s"$EntityNotFoundByEntityId Entity: '$entityName', entityId: '$id'" + bankId.map(b => s", bank_id: '$b'").getOrElse("")

  /** Resolve bankId to a Bank (404 if missing) for bank-level entities; no-op otherwise. */
  private def bankCheck(bankId: Option[String], cc: Option[CallContext]): Future[(Any, Option[CallContext])] =
    if (bankId.isDefined) NewStyle.function.getBank(BankId(bankId.get), cc).map { case (b, c) => (b, c) }
    else Future.successful(("", cc))

  /**
   * Enrich the CallContext with the dynamic-entity operationId + ResourceDoc, mirroring the
   * Lift handlers.  Used for rate limiting (authenticatedAccess injects operationId), metrics,
   * and the interceptors (Force-Error validation reads resourceDocument.errorResponseBodies).
   * The name key follows the Lift convention: generic system/bank -> `Entity` / `Entity(bankId)`,
   * personal -> `MyEntity...`, public -> `PublicEntity...`, community -> `CommunityEntity...`.
   */
  private def enrichCallContext(cc: CallContext, operation: DynamicEntityOperation, entityName: String, bankId: Option[String], scope: String): CallContext = {
    val splitNameWithBankId = if (bankId.isDefined) s"$entityName(${bankId.getOrElse("")})" else entityName
    val key = scope match {
      case "my"        => s"My$splitNameWithBankId"
      case "public"    => s"Public$splitNameWithBankId"
      case "community" => s"Community$splitNameWithBankId"
      case _           => splitNameWithBankId
    }
    val resourceDoc = DynamicEntityHelper.operationToResourceDoc.get(operation -> key)
    cc.copy(operationId = Some(resourceDoc.map(_.operationId).orNull), resourceDocument = resourceDoc)
  }

  // Before-authenticate interceptors: auth-type / query-param / request-header-key validation.
  // After-authenticate interceptors: Force-Error / JSON-schema validation.
  // Both reduce a Box[JsonResponse] to a Box[ErrorMessage(code, message)] via JsonResponseExtractor.
  private def beforeIntercept(cc: CallContext, operationId: String): Box[ErrorMessage] =
    beforeAuthenticateInterceptResult(Option(cc), operationId).collect { case JsonResponseExtractor(message, code) => ErrorMessage(code, message) }

  private def afterIntercept(cc: Option[CallContext], operationId: String): Box[ErrorMessage] =
    afterAuthenticateInterceptResult(cc, operationId).collect { case JsonResponseExtractor(message, code) => ErrorMessage(code, message) }

  private def failIf(error: Box[ErrorMessage], cc: Option[CallContext]): Future[Box[Unit]] =
    Helper.booleanToFuture(failMsg = error.map(_.message).orNull, failCode = error.map(_.code).openOr(400), cc = cc) { error.isEmpty }

  // ----- generic endpoint (authenticated, system / bank / personal) -----

  private def genericGet(req: Request[IO], bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      val isGetAll = StringUtils.isBlank(id)
      val operation: DynamicEntityOperation = if (isGetAll) GET_ALL else GET_ONE
      val callContext0 = enrichCallContext(cc, operation, entityName, bankId, if (isPersonalEntity) "my" else "")
      val operationId = callContext0.operationId.orNull
      for {
        _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
        (Full(u), callContext) <- authenticatedAccess(callContext0)
        (_, callContext) <- bankCheck(bankId, callContext)
        personalRequiresRole = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.personalRequiresRole)
        _ <- if (isPersonalEntity && !personalRequiresRole) Future.successful(true)
             else NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canGetRole(entityName, bankId), callContext)
        _ <- failIf(afterIntercept(callContext, operationId), callContext)
        (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, None, Option(id).filter(StringUtils.isNotBlank), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        _ <- Helper.booleanToFuture(notFoundMsg(entityName, id, bankId), 404, cc = callContext) { box.isDefined }
      } yield {
        if (isGetAll) {
          val resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entityName)
          wrapBankId(bankId, (listName(entityName) -> filterDynamicObjects(resultList, queryParams(req))))
        } else {
          val singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
          wrapBankId(bankId, (singleName(entityName) -> singleObject))
        }
      }
    }

  private def genericPost(req: Request[IO], bankId: Option[String], entityName: String, isPersonalEntity: Boolean): IO[Response[IO]] =
    EndpointHelpers.executeFutureCreated(req) {
      val cc = req.callContext
      val callContext0 = enrichCallContext(cc, CREATE, entityName, bankId, if (isPersonalEntity) "my" else "")
      val operationId = callContext0.operationId.orNull
      for {
        _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
        (Full(u), callContext) <- authenticatedAccess(callContext0)
        (_, callContext) <- bankCheck(bankId, callContext)
        personalRequiresRole = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.personalRequiresRole)
        _ <- if (isPersonalEntity && !personalRequiresRole) Future.successful(true)
             else NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canCreateRole(entityName, bankId), callContext)
        _ <- failIf(afterIntercept(callContext, operationId), callContext)
        json <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { net.liftweb.json.parse(cc.httpBody.getOrElse("")) }
        (box, _) <- NewStyle.function.invokeDynamicConnector(CREATE, entityName, Some(json.asInstanceOf[JObject]), None, bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
      } yield wrapBankId(bankId, (singleName(entityName) -> singleObject))
    }

  private def genericPut(req: Request[IO], bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      val callContext0 = enrichCallContext(cc, UPDATE, entityName, bankId, if (isPersonalEntity) "my" else "")
      val operationId = callContext0.operationId.orNull
      for {
        _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
        (Full(u), callContext) <- authenticatedAccess(callContext0)
        (_, callContext) <- bankCheck(bankId, callContext)
        personalRequiresRole = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.personalRequiresRole)
        _ <- if (isPersonalEntity && !personalRequiresRole) Future.successful(true)
             else NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canUpdateRole(entityName, bankId), callContext)
        _ <- failIf(afterIntercept(callContext, operationId), callContext)
        json <- NewStyle.function.tryons(InvalidJsonFormat, 400, callContext) { net.liftweb.json.parse(cc.httpBody.getOrElse("")) }
        (existing, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        _ <- Helper.booleanToFuture(notFoundMsg(entityName, id, bankId), 404, cc = callContext) { existing.isDefined }
        (box: Box[JValue], _) <- NewStyle.function.invokeDynamicConnector(UPDATE, entityName, Some(json.asInstanceOf[JObject]), Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        singleObject: JValue = unboxResult(box, entityName)
      } yield wrapBankId(bankId, (singleName(entityName) -> singleObject))
    }

  private def genericDelete(req: Request[IO], bankId: Option[String], entityName: String, id: String, isPersonalEntity: Boolean): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      val callContext0 = enrichCallContext(cc, DELETE, entityName, bankId, if (isPersonalEntity) "my" else "")
      val operationId = callContext0.operationId.orNull
      for {
        _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
        (Full(u), callContext) <- authenticatedAccess(callContext0)
        (_, callContext) <- bankCheck(bankId, callContext)
        personalRequiresRole = DynamicEntityHelper.definitionsMap.get((bankId, entityName)).exists(_.personalRequiresRole)
        _ <- if (isPersonalEntity && !personalRequiresRole) Future.successful(true)
             else NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canDeleteRole(entityName, bankId), callContext)
        _ <- failIf(afterIntercept(callContext, operationId), callContext)
        (existing, _) <- NewStyle.function.invokeDynamicConnector(GET_ONE, entityName, None, Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        _ <- Helper.booleanToFuture(notFoundMsg(entityName, id, bankId), 404, cc = callContext) { existing.isDefined }
        (box, _) <- NewStyle.function.invokeDynamicConnector(DELETE, entityName, None, Some(id), bankId, None, Some(u.userId), isPersonalEntity, Some(cc))
        deleteResult: JBool = unboxResult(box.asInstanceOf[Box[JBool]], entityName)
      } yield deleteResult
    }

  // ----- public endpoint (anonymous, read-only; before-interceptors only, no role) -----

  private def publicGet(req: Request[IO], bankId: Option[String], entityName: String, id: String): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      val isGetAll = StringUtils.isBlank(id)
      val operation: DynamicEntityOperation = if (isGetAll) GET_ALL else GET_ONE
      val callContext0 = enrichCallContext(cc, operation, entityName, bankId, "public")
      val operationId = callContext0.operationId.orNull
      for {
        _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
        (_, callContext) <- anonymousAccess(callContext0)
        (_, callContext) <- bankCheck(bankId, callContext)
        (box, _) <- NewStyle.function.invokeDynamicConnector(operation, entityName, None, Option(id).filter(StringUtils.isNotBlank), bankId, None, None, false, Some(cc))
        _ <- Helper.booleanToFuture(notFoundMsg(entityName, id, bankId), 404, cc = callContext) { box.isDefined }
      } yield {
        if (isGetAll) {
          val resultList: JArray = unboxResult(box.asInstanceOf[Box[JArray]], entityName)
          wrapBankId(bankId, (listName(entityName) -> filterDynamicObjects(resultList, queryParams(req))))
        } else {
          val singleObject: JValue = unboxResult(box.asInstanceOf[Box[JValue]], entityName)
          wrapBankId(bankId, (singleName(entityName) -> singleObject))
        }
      }
    }

  // ----- community endpoint (authenticated + CanGet role, read-only, ALL records) -----

  private def communityGet(req: Request[IO], bankId: Option[String], entityName: String, id: String): IO[Response[IO]] =
    EndpointHelpers.executeAndRespond(req) { cc =>
      val isGetAll = StringUtils.isBlank(id)
      val operation: DynamicEntityOperation = if (isGetAll) GET_ALL else GET_ONE
      val callContext0 = enrichCallContext(cc, operation, entityName, bankId, "community")
      val operationId = callContext0.operationId.orNull
      for {
        _ <- failIf(beforeIntercept(callContext0, operationId), Some(callContext0))
        (Full(u), callContext) <- authenticatedAccess(callContext0)
        (_, callContext) <- bankCheck(bankId, callContext)
        _ <- NewStyle.function.hasEntitlement(bankId.getOrElse(""), u.userId, DynamicEntityInfo.canGetRole(entityName, bankId), callContext)
        _ <- failIf(afterIntercept(callContext, operationId), callContext)
      } yield {
        if (isGetAll) {
          val resultList: List[JObject] = DynamicDataProvider.connectorMethodProvider.vend.getAllDataJsonCommunity(bankId, entityName)
          val resultArray = JArray(resultList)
          wrapBankId(bankId, (listName(entityName) -> filterDynamicObjects(resultArray, queryParams(req))))
        } else {
          val singleResult = DynamicDataProvider.connectorMethodProvider.vend.getCommunity(bankId, entityName, id)
          val singleObject: JValue = singleResult match {
            case Full(data) => net.liftweb.json.parse(data.dataJson)
            case _ => throw new RuntimeException(notFoundMsg(entityName, id, bankId))
          }
          wrapBankId(bankId, (singleName(entityName) -> singleObject))
        }
      }
    }

  // ----- dispatch -----

  /**
   * Match the remaining path segments (after `/obp/dynamic-entity`) against the same
   * extractors the Lift dispatcher used.  Order public -> community -> generic mirrors
   * OBPAPIDynamicEntity.routes.  No match -> OptionT.none (request falls through the chain).
   */
  private def dispatch(req: Request[IO], rest: List[String]): OptionT[IO, Response[IO]] = {
    val handlerOpt: Option[Request[IO] => IO[Response[IO]]] = (req.method, rest) match {
      case (Method.GET, PublicEntityName(bankId, entityName, id)) =>
        Some(r => publicGet(r, bankId, entityName, id))
      case (Method.GET, CommunityEntityName(bankId, entityName, id)) =>
        Some(r => communityGet(r, bankId, entityName, id))
      case (method, EntityName(bankId, entityName, id, isPersonalEntity)) =>
        method match {
          case Method.GET    => Some(r => genericGet(r, bankId, entityName, id, isPersonalEntity))
          case Method.POST   => Some(r => genericPost(r, bankId, entityName, isPersonalEntity))
          case Method.PUT    => Some(r => genericPut(r, bankId, entityName, id, isPersonalEntity))
          case Method.DELETE => Some(r => genericDelete(r, bankId, entityName, id, isPersonalEntity))
          case _             => None
        }
      case _ => None
    }

    handlerOpt match {
      case None => OptionT.none[IO, Response[IO]]
      case Some(handler) =>
        OptionT.liftF {
          Http4sCallContextBuilder.fromRequest(req, apiVersionString).flatMap { cc =>
            val reqWithCc = req.withAttribute(Http4sRequestAttributes.callContextKey, cc)
            val io = handler(reqWithCc)
            if (req.method == Method.GET || req.method == Method.HEAD) io
            else RequestScopeConnection.withBusinessDBTransaction(io)
          }
        }
    }
  }

  /** Entry point wired into Http4sApp.baseServices (before the Lift bridge). */
  lazy val wrappedRoutesDynamicEntity: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { (req: Request[IO]) =>
      req.uri.path.segments.map(_.encoded).toList match {
        case standard :: version :: rest if standard == apiStandard && version == apiVersionString =>
          dispatch(req, rest)
        case _ =>
          OptionT.none[IO, Response[IO]]
      }
    }
}
