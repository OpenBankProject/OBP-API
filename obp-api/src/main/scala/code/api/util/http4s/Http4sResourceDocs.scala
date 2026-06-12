package code.api.util.http4s

import org.json4s._
import cats.effect.IO
import code.api.Constant.HostName
import code.api.ResourceDocs1_4_0.{ResourceDocs140, ResourceDocs300, ResourceDocsAPIMethodsUtil}
import code.api.ResponseHeader
import code.api.cache.Caching
import code.api.util.ApiRole.{canReadDynamicResourceDocsAtOneBank, canReadResourceDoc}
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, ApiRole, ApiVersionUtils, CustomJsonFormats, YAMLUtils}
import code.api.v1_4_0.JSONFactory1_4_0
import code.apicollectionendpoint.MappedApiCollectionEndpointsProvider
import code.bankconnectors.rest.RestConnector_vMar2019
import code.util.Helper.{MdcLoggable, SILENCE_IS_GOLDEN}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.enums.ContentParam
import com.openbankproject.commons.model.enums.ContentParam.{DYNAMIC, STATIC}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Box, Empty, Full}
import com.openbankproject.commons.util.json
import org.json4s.JsonAST.{JField, JObject, JString, JValue}
import org.json4s.Extraction
import com.openbankproject.commons.util.JsonAliases.prettyRender
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s._
import org.typelevel.ci.CIString

import scala.concurrent.{Future, ExecutionContext => ScalaEC}
import code.api.util.ApiTag.ResourceDocTag

/**
 * Centralised native http4s service for OBP resource-docs / swagger / openapi /
 * openapi.yaml / message-docs traffic.
 *
 * Replaces the 10 per-version Lift singletons (`ResourceDocs140`..`ResourceDocs600`)
 * registered in `Boot.scala` via `LiftRules.statelessDispatch.append`. The version
 * prefix in the URL (`/obp/v6.0.0/resource-docs/...`) is functionally irrelevant
 * because the response content is determined by the `API_VERSION` path segment.
 * So one service handles all version prefixes for the following routes:
 *
 *   GET /obp/&#42;/resource-docs/{API_VERSION}/obp
 *   GET /obp/&#42;/resource-docs/{API_VERSION}/swagger
 *   GET /obp/&#42;/resource-docs/{API_VERSION}/openapi
 *   GET /obp/&#42;/resource-docs/{API_VERSION}/openapi.yaml
 *   GET /obp/&#42;/banks/{BANK_ID}/resource-docs/{API_VERSION}/obp
 *   GET /obp/&#42;/message-docs/{CONNECTOR}/swagger2.0
 *
 * Wired into `Http4sApp.baseServices` BEFORE the Lift bridge, so requests are
 * served natively instead of falling through to Lift.
 *
 * Business logic is delegated to `ResourceDocs140.ImplementationsResourceDocs`
 * (and `ResourceDocsAPIMethodsUtil`) so caching / content-param / locale /
 * api-collection-id semantics stay identical to the Lift handlers.
 */
object Http4sResourceDocs extends MdcLoggable {

  // Two ImplementationsResourceDocs instances are exposed because they differ on
  // `includeTechnologyInResponse`. The Lift dispatchers preserved per-prefix
  // behavior — ResourceDocs600 includes the `technology` field in responses, all
  // other ResourceDocs* leave it as None. Tests assert both shapes (v6 prefix →
  // Some("liftweb"); v5 prefix → None), so the centralized service picks Impl
  // based on the URL prefix segment.
  private val ImplDefault = ResourceDocs140.ImplementationsResourceDocs
  private val ImplV600 = ResourceDocs300.ResourceDocs600.ImplementationsResourceDocs

  private def implForPrefix(prefix: String) = prefix match {
    case "v6.0.0" => ImplV600
    case _        => ImplDefault
  }

  private def includeTechnologyForPrefix(prefix: String): Boolean = prefix == "v6.0.0"
  private val jsonContentType: `Content-Type` =
    `Content-Type`(MediaType.application.json, Charset.`UTF-8`)
  private val plainTextContentType: `Content-Type` =
    `Content-Type`(MediaType.text.plain, Charset.`UTF-8`)

  private def resourceDocsRequireRole: Boolean =
    APIUtil.getPropsAsBoolValue("resource_docs_requires_role", false)

  // ─── Query-parameter parsing ─────────────────────────────────────────────
  // Mirrors `ResourceDocsAPIMethodsUtil.getParams()` but reads from
  // `req.uri.query.params` instead of Lift's `ObpS.param` / `S.request`.

  private final case class ParsedParams(
    tags: Option[List[ResourceDocTag]],
    partialFunctions: Option[List[String]],
    locale: Option[String],
    contentParam: Option[ContentParam],
    apiCollectionId: Option[String],
    rawTags: Option[String],
    rawFunctions: Option[String],
    rawApiCollectionId: Option[String],
    rawContent: Option[String]
  )

  private def parseParams(req: Request[IO]): ParsedParams = {
    def first(k: String): Option[String] = req.uri.query.params.get(k)
    val rawTags = first("tags")
    val rawFunctions = first("functions")
    val rawApiCollectionId = first("api-collection-id")
    val rawContent = first("content")

    val tags = rawTags match {
      case None | Some("") => None
      case Some(s)         =>
        val list = s.trim.split(",").toList.map(_.trim).filter(_.nonEmpty).map(ResourceDocTag(_))
        if (list.nonEmpty) Some(list) else None
    }
    val partialFunctions = rawFunctions match {
      case None | Some("") => None
      case Some(s)         =>
        val list = s.trim.split(",").toList.map(_.trim).filter(_.nonEmpty)
        if (list.nonEmpty) Some(list) else None
    }
    val locale = first("locale").orElse(first("language")).filter(_.trim.nonEmpty).map(_.trim)
    val contentParam = rawContent.flatMap { v =>
      v.toLowerCase.trim match {
        case "dynamic" => Some(DYNAMIC)
        case "static"  => Some(STATIC)
        case "all"     => Some(com.openbankproject.commons.model.enums.ContentParam.ALL)
        case _         => None
      }
    }
    val apiCollectionId = rawApiCollectionId.map(_.trim).filter(_.nonEmpty)

    ParsedParams(
      tags, partialFunctions, locale, contentParam, apiCollectionId,
      rawTags, rawFunctions, rawApiCollectionId, rawContent
    )
  }

  // ─── Response helpers ────────────────────────────────────────────────────

  private def jsonResponse(status: Status, body: JValue, extraHeaders: List[(String, String)] = Nil): IO[Response[IO]] = {
    val rendered = json.compactRender(body)
    val withHeaders = extraHeaders.foldLeft(
      Response[IO](status).withEntity(rendered).withContentType(jsonContentType)
    ) { case (resp, (n, v)) => resp.putHeaders(Header.Raw(CIString(n), v)) }
    IO.pure(withHeaders)
  }

  private def errorJson(status: Status, message: String): IO[Response[IO]] = {
    val body = Extraction.decompose(code.tesobe.ErrorMessage(message))(CustomJsonFormats.formats)
    jsonResponse(status, body)
  }

  private def plainTextResponse(status: Status, body: String): IO[Response[IO]] =
    IO.pure(Response[IO](status).withEntity(body).withContentType(plainTextContentType))

  private def yamlResponse(body: String): IO[Response[IO]] = {
    val bytes = body.getBytes("UTF-8")
    val correlationId =
      try APIUtil.getCorrelationId()
      catch { case _: Throwable => "" }
    val resp = Response[IO](Status.Ok)
      .withEntity(bytes)
      .putHeaders(
        Header.Raw(CIString("Content-Type"), YAMLUtils.getYAMLContentType),
        Header.Raw(CIString(ResponseHeader.`Correlation-Id`), correlationId)
      )
    IO.pure(resp)
  }

  // ─── Authentication helper ───────────────────────────────────────────────
  // When `resource_docs_requires_role=true`, look up the caller via the
  // existing Http4s authentication paths the per-version services already use.
  // We mimic the Lift handlers' check: extract user via authReq, then check
  // entitlement; if either fails return 401/403 as appropriate.

  private def withOptionalRoleCheck[A](
    req: Request[IO],
    prefix: String,
    bankId: String,
    roles: List[ApiRole],
    failMsg: String
  )(body: => IO[Response[IO]]): IO[Response[IO]] = {
    if (!resourceDocsRequireRole) body
    else {
      // Pass the URL prefix as the apiVersion so getUserAndSessionContextFuture's
      // S.request fallback (used for `implementedInVersion` / `verb` / `url`) is not
      // reached — that fallback throws when not under a Lift dispatch, which would
      // surface as a 500 instead of the intended 401/403.
      val ccBuild: IO[code.api.util.CallContext] =
        Http4sCallContextBuilder.fromRequest(req, apiVersion = prefix)
      ccBuild.flatMap { cc =>
        IO.fromFuture(IO(APIUtil.getUserAndSessionContextFuture(cc))).attempt.flatMap {
          case Right((Full(user), _)) =>
            val hasRole =
              try APIUtil.hasAtLeastOneEntitlement(bankId, user.userId, roles)
              catch { case _: Throwable => false }
            if (hasRole) body
            else errorJson(Status.Forbidden, failMsg)
          case _ =>
            // Box-Empty/Failure OR thrown auth exception → unauthenticated.
            errorJson(Status.Unauthorized, AuthenticatedUserIsRequired)
        }
      }
    }
  }

  // ─── Common parameter validation ─────────────────────────────────────────
  // Mirrors the parameter-validation branches in the Lift handlers.

  private def validateBasicParams(params: ParsedParams): Option[(Status, String)] = {
    if (params.rawTags.exists(_.trim.isEmpty)) Some(Status.BadRequest -> InvalidTagsParameter)
    else if (params.rawFunctions.exists(_.trim.isEmpty)) Some(Status.BadRequest -> InvalidFunctionsParameter)
    else if (params.rawApiCollectionId.exists(_.trim.isEmpty)) Some(Status.BadRequest -> InvalidApiCollectionIdParameter)
    else if (params.rawContent.isDefined && params.contentParam.isEmpty) Some(Status.BadRequest -> InvalidContentParameter)
    else None
  }

  private def validateVersionAndLocale(
    requestedApiVersionString: String,
    locale: Option[String]
  ): Either[(Status, String), ApiVersion] = {
    val versionEither =
      try Right(ApiVersionUtils.valueOf(requestedApiVersionString))
      catch { case _: Throwable => Left(Status.BadRequest -> s"$InvalidApiVersionString Current Version is $requestedApiVersionString") }
    versionEither match {
      case Left(err) => Left(err)
      case Right(v) if !APIUtil.versionIsAllowed(v) =>
        Left(Status.BadRequest -> s"$ApiVersionNotSupported Current Version is $requestedApiVersionString")
      case Right(v) =>
        locale match {
          case Some(l) if APIUtil.obpLocaleValidation(l) != SILENCE_IS_GOLDEN =>
            Left(Status.BadRequest -> s"$InvalidLocale Current Locale is $l")
          case _ => Right(v)
        }
    }
  }

  // ─── JSON transformation for OBP format ──────────────────────────────────
  // Inlined copy of the file-private `resourceDocsJsonToJsonResponse` in
  // `ResourceDocsAPIMethods.scala`. Renames `jsonClass` → `role`,
  // unwraps the `jvalueToCaseclass` wrapper, and strips the `ApiRole$` prefix.

  private def resourceDocsJsonToJsonResponse(rdJson: JSONFactory1_4_0.ResourceDocsJson): JValue = {
    val decomposed = Extraction.decompose(rdJson)(CustomJsonFormats.formats)
    val unwrapped = decomposed transform {
      case JObject(List(JField("jvalueToCaseclass", JObject(x)))) => JObject(x)
    }
    val renamed = unwrapped transformField {
      case JField("jsonClass", x)      => JField("role", x)
      case JField("requiresBankId", x) => JField("requires_bank_id", x)
    }
    renamed transformField {
      case JField("role", JString(s)) => JField("role", JString(s.replace("ApiRole$", "")))
    }
  }

  // ─── Handler: GET /obp/*/resource-docs/{API_VERSION}/obp ─────────────────

  private def handleGetResourceDocsObp(
    req: Request[IO],
    prefix: String,
    requestedApiVersionString: String,
    isVersion4OrHigher: Boolean
  ): IO[Response[IO]] = {
    val params = parseParams(req)
    validateBasicParams(params) match {
      case Some((status, msg)) => errorJson(status, msg)
      case None =>
        withOptionalRoleCheck(req, prefix, "", canReadResourceDoc :: Nil,
          UserHasMissingRoles + canReadResourceDoc.toString) {
          validateVersionAndLocale(requestedApiVersionString, params.locale) match {
            case Left((s, m)) => errorJson(s, m)
            case Right(_) =>
              IO(buildObpResourceDocsJson(params, prefix, requestedApiVersionString, isVersion4OrHigher)).flatMap {
                case Right(body) => jsonResponse(Status.Ok, body)
                case Left((s, m)) => errorJson(s, m)
              }
          }
        }
    }
  }

  private def buildObpResourceDocsJson(
    params: ParsedParams,
    prefix: String,
    requestedApiVersionString: String,
    isVersion4OrHigher: Boolean
  ): Either[(Status, String), JValue] = {
    try {
      val impl = implForPrefix(prefix)
      val includeTech = includeTechnologyForPrefix(prefix)
      val cacheKey = APIUtil.createResourceDocCacheKey(
        None,
        requestedApiVersionString,
        params.tags,
        params.partialFunctions,
        params.locale,
        params.contentParam,
        params.apiCollectionId,
        Some(isVersion4OrHigher)
      )
      val jvalue: JValue = (params.apiCollectionId, params.contentParam) match {
        case (Some(_), _) =>
          val operationIds = MappedApiCollectionEndpointsProvider.getApiCollectionEndpoints(params.apiCollectionId.getOrElse(""))
            .map(_.operationId).map(APIUtil.getObpFormatOperationId)
          val resourceDocs = APIUtil.ResourceDoc.getResourceDocs(operationIds)
          val rdJson = JSONFactory1_4_0.createResourceDocsJson(resourceDocs, isVersion4OrHigher, params.locale, includeTechnology = includeTech)
          resourceDocsJsonToJsonResponse(rdJson)
        case (None, Some(DYNAMIC)) =>
          val cached = Caching.getDynamicResourceDocCache(cacheKey)
          if (cached.isDefined) json.parse(cached.get)
          else {
            val rdJson = impl.getResourceDocsObpDynamicCached(params.tags, params.partialFunctions, params.locale, None, isVersion4OrHigher = false).head
            val jv = resourceDocsJsonToJsonResponse(rdJson)
            Caching.setDynamicResourceDocCache(cacheKey, json.compactRender(jv))
            jv
          }
        case (None, Some(STATIC)) =>
          val cached = Caching.getStaticResourceDocCache(cacheKey)
          if (cached.isDefined) json.parse(cached.get)
          else {
            val rdJson = impl.getStaticResourceDocsObpCached(requestedApiVersionString, params.tags, params.partialFunctions, params.locale, isVersion4OrHigher).head
            val jv = resourceDocsJsonToJsonResponse(rdJson)
            Caching.setStaticResourceDocCache(cacheKey, json.compactRender(jv))
            jv
          }
        case (None, _) =>
          val cached = Caching.getAllResourceDocCache(cacheKey)
          if (cached.isDefined) json.parse(cached.get)
          else {
            val rdJson = impl.getAllResourceDocsObpCached(requestedApiVersionString, params.tags, params.partialFunctions, params.locale, params.contentParam, isVersion4OrHigher).head
            val jv = resourceDocsJsonToJsonResponse(rdJson)
            Caching.setAllResourceDocCache(cacheKey, json.compactRender(jv))
            jv
          }
      }
      Right(jvalue)
    } catch {
      case e: Throwable =>
        logger.error(s"Http4sResourceDocs.buildObpResourceDocsJson failed: ${e.getMessage}", e)
        Left(Status.InternalServerError -> s"$UnknownError Can not prepare OBP resource docs.")
    }
  }

  // ─── Handler: GET /obp/*/resource-docs/{API_VERSION}/swagger ─────────────

  private def handleGetResourceDocsSwagger(
    req: Request[IO],
    prefix: String,
    requestedApiVersionString: String
  ): IO[Response[IO]] = {
    val params = parseParams(req)
    withOptionalRoleCheck(req, prefix, "", canReadResourceDoc :: Nil,
      UserHasMissingRoles + canReadResourceDoc.toString) {
      validateVersionAndLocale(requestedApiVersionString, params.locale) match {
        case Left((s, m)) => errorJson(s, m)
        case Right(_) =>
          IO(buildSwaggerJson(params, prefix, requestedApiVersionString)).flatMap {
            case Right(body) => jsonResponse(Status.Ok, body)
            case Left((s, m)) => errorJson(s, m)
          }
      }
    }
  }

  private def buildSwaggerJson(
    params: ParsedParams,
    prefix: String,
    requestedApiVersionString: String
  ): Either[(Status, String), JValue] = {
    try {
      val impl = implForPrefix(prefix)
      val isVersion4OrHigher = true
      val cacheKey = APIUtil.createResourceDocCacheKey(
        None,
        requestedApiVersionString,
        params.tags,
        params.partialFunctions,
        params.locale,
        params.contentParam,
        params.apiCollectionId,
        Some(isVersion4OrHigher)
      )
      val cached = Caching.getStaticSwaggerDocCache(cacheKey)
      val jv: JValue =
        if (cached.isDefined) json.parse(cached.get)
        else {
          val resourceDocsJsonFiltered: List[JSONFactory1_4_0.ResourceDocJson] = params.apiCollectionId match {
            case Some(_) =>
              val operationIds = MappedApiCollectionEndpointsProvider.getApiCollectionEndpoints(params.apiCollectionId.getOrElse(""))
                .map(_.operationId).map(APIUtil.getObpFormatOperationId)
              val resourceDocs = APIUtil.ResourceDoc.getResourceDocs(operationIds)
              JSONFactory1_4_0.createResourceDocsJson(resourceDocs, isVersion4OrHigher, params.locale, includeTechnology = true).resource_docs
            case None =>
              params.contentParam match {
                case Some(DYNAMIC) =>
                  impl.getResourceDocsObpDynamicCached(params.tags, params.partialFunctions, params.locale, None, isVersion4OrHigher).head.resource_docs
                case Some(STATIC) =>
                  impl.getStaticResourceDocsObpCached(requestedApiVersionString, params.tags, params.partialFunctions, params.locale, isVersion4OrHigher).head.resource_docs
                case _ =>
                  impl.getAllResourceDocsObpCached(requestedApiVersionString, params.tags, params.partialFunctions, params.locale, params.contentParam, isVersion4OrHigher).head.resource_docs
              }
          }
          impl.convertResourceDocsToSwaggerJvalueAndSetCache(cacheKey, requestedApiVersionString, resourceDocsJsonFiltered)
        }
      Right(jv)
    } catch {
      case e: Throwable =>
        logger.error(s"Http4sResourceDocs.buildSwaggerJson failed: ${e.getMessage}", e)
        Left(Status.BadRequest -> s"$UnknownError Can not convert internal swagger file.")
    }
  }

  // ─── Handler: GET /obp/*/resource-docs/{API_VERSION}/openapi ─────────────

  private def handleGetResourceDocsOpenAPI31(
    req: Request[IO],
    prefix: String,
    requestedApiVersionString: String
  ): IO[Response[IO]] = {
    val params = parseParams(req)
    validateBasicParams(params) match {
      case Some((status, msg)) => errorJson(status, msg)
      case None =>
        withOptionalRoleCheck(req, prefix, "", canReadResourceDoc :: Nil,
          UserHasMissingRoles + canReadResourceDoc.toString) {
          validateVersionAndLocale(requestedApiVersionString, params.locale) match {
            case Left((s, m)) => errorJson(s, m)
            case Right(_) =>
              IO(buildOpenApi31Json(params, prefix, requestedApiVersionString)).flatMap {
                case Right(body) => jsonResponse(Status.Ok, body)
                case Left((s, m)) => errorJson(s, m)
              }
          }
        }
    }
  }

  private def buildOpenApi31Json(
    params: ParsedParams,
    prefix: String,
    requestedApiVersionString: String
  ): Either[(Status, String), JValue] = {
    try {
      val impl = implForPrefix(prefix)
      val isVersion4OrHigher = true
      val cacheKey = APIUtil.createResourceDocCacheKey(
        Some("openapi31"),
        requestedApiVersionString,
        params.tags,
        params.partialFunctions,
        params.locale,
        params.contentParam,
        params.apiCollectionId,
        Some(isVersion4OrHigher)
      )
      val cached = Caching.getStaticSwaggerDocCache(cacheKey)
      val jv: JValue =
        if (cached.isDefined) json.parse(cached.get)
        else {
          val resourceDocsJsonFiltered: List[JSONFactory1_4_0.ResourceDocJson] = params.apiCollectionId match {
            case Some(_) =>
              val operationIds = MappedApiCollectionEndpointsProvider.getApiCollectionEndpoints(params.apiCollectionId.getOrElse(""))
                .map(_.operationId).map(APIUtil.getObpFormatOperationId)
              val resourceDocs = APIUtil.ResourceDoc.getResourceDocs(operationIds)
              JSONFactory1_4_0.createResourceDocsJson(resourceDocs, isVersion4OrHigher, params.locale, includeTechnology = true).resource_docs
            case None =>
              params.contentParam match {
                case Some(DYNAMIC) =>
                  impl.getResourceDocsObpDynamicCached(params.tags, params.partialFunctions, params.locale, None, isVersion4OrHigher).head.resource_docs
                case Some(STATIC) =>
                  impl.getStaticResourceDocsObpCached(requestedApiVersionString, params.tags, params.partialFunctions, params.locale, isVersion4OrHigher).head.resource_docs
                case _ =>
                  impl.getAllResourceDocsObpCached(requestedApiVersionString, params.tags, params.partialFunctions, params.locale, params.contentParam, isVersion4OrHigher).head.resource_docs
              }
          }
          impl.convertResourceDocsToOpenAPI31JvalueAndSetCache(cacheKey, requestedApiVersionString, resourceDocsJsonFiltered)
        }
      Right(jv)
    } catch {
      case e: Throwable =>
        logger.error(s"Http4sResourceDocs.buildOpenApi31Json failed: ${e.getMessage}", e)
        Left(Status.BadRequest -> s"$UnknownError Can not convert internal openapi file.")
    }
  }

  // ─── Handler: GET /obp/*/resource-docs/{API_VERSION}/openapi.yaml ────────

  private def handleGetResourceDocsOpenAPI31Yaml(
    req: Request[IO],
    prefix: String,
    requestedApiVersionString: String
  ): IO[Response[IO]] = {
    val params = parseParams(req)
    validateBasicParams(params) match {
      case Some((_, msg)) => plainTextResponse(Status.BadRequest, msg)
      case None =>
        validateVersionAndLocale(requestedApiVersionString, params.locale) match {
          case Left((_, msg)) => plainTextResponse(Status.BadRequest, msg)
          case Right(_) =>
            IO(buildOpenApi31Yaml(params, prefix, requestedApiVersionString)).flatMap {
              case Right(yamlString) => yamlResponse(yamlString)
              case Left((_, msg)) => plainTextResponse(Status.BadRequest, msg)
            }
        }
    }
  }

  private def buildOpenApi31Yaml(
    params: ParsedParams,
    prefix: String,
    requestedApiVersionString: String
  ): Either[(Status, String), String] = {
    try {
      val impl = implForPrefix(prefix)
      val isVersion4OrHigher = true
      val cacheKey = APIUtil.createResourceDocCacheKey(
        Some("openapi31yaml"),
        requestedApiVersionString,
        params.tags,
        params.partialFunctions,
        params.locale,
        params.contentParam,
        params.apiCollectionId,
        Some(isVersion4OrHigher)
      )
      val cached = Caching.getStaticSwaggerDocCache(cacheKey)
      val yamlString: String =
        if (cached.isDefined) cached.get
        else {
          val resourceDocsJsonFiltered: List[JSONFactory1_4_0.ResourceDocJson] = params.apiCollectionId match {
            case Some(_) =>
              val operationIds = MappedApiCollectionEndpointsProvider.getApiCollectionEndpoints(params.apiCollectionId.getOrElse(""))
                .map(_.operationId).map(APIUtil.getObpFormatOperationId)
              val resourceDocs = APIUtil.ResourceDoc.getResourceDocs(operationIds)
              JSONFactory1_4_0.createResourceDocsJson(resourceDocs, isVersion4OrHigher, params.locale, includeTechnology = true).resource_docs
            case None =>
              params.contentParam match {
                case Some(DYNAMIC) =>
                  impl.getResourceDocsObpDynamicCached(params.tags, params.partialFunctions, params.locale, None, isVersion4OrHigher).head.resource_docs
                case Some(STATIC) =>
                  impl.getStaticResourceDocsObpCached(requestedApiVersionString, params.tags, params.partialFunctions, params.locale, isVersion4OrHigher).head.resource_docs
                case _ =>
                  impl.getAllResourceDocsObpCached(requestedApiVersionString, params.tags, params.partialFunctions, params.locale, params.contentParam, isVersion4OrHigher).head.resource_docs
              }
          }
          impl.convertResourceDocsToOpenAPI31YAMLAndSetCache(cacheKey, requestedApiVersionString, resourceDocsJsonFiltered)
        }
      Right(yamlString)
    } catch {
      case e: Throwable =>
        logger.error(s"Http4sResourceDocs.buildOpenApi31Yaml failed: ${e.getMessage}", e)
        Left(Status.BadRequest -> s"Invalid API version: $requestedApiVersionString")
    }
  }

  // ─── Handler: GET /obp/*/banks/{BANK_ID}/resource-docs/{API_VERSION}/obp ─

  private def handleGetBankLevelDynamicResourceDocsObp(
    req: Request[IO],
    prefix: String,
    bankIdStr: String,
    requestedApiVersionString: String
  ): IO[Response[IO]] = {
    val params = parseParams(req)
    withOptionalRoleCheck(req, prefix, bankIdStr, canReadDynamicResourceDocsAtOneBank :: Nil,
      UserHasMissingRoles + canReadDynamicResourceDocsAtOneBank.toString) {
      // Bank-level handler ALWAYS requires the bank to exist. Use the legacy connector lookup
      // synchronously; it does its own 404 if absent.
      val bankBox: Box[com.openbankproject.commons.model.Bank] =
        code.bankconnectors.Connector.connector.vend.getBankLegacy(BankId(bankIdStr), None).map(_._1)
      if (bankBox.isEmpty) errorJson(Status.NotFound, s"$BankNotFound Current BANK_ID = $bankIdStr")
      else {
        val localeError: Option[String] = params.locale match {
          case Some(l) if APIUtil.obpLocaleValidation(l) != SILENCE_IS_GOLDEN =>
            Some(s"$InvalidLocale Current Locale is $l")
          case _ => None
        }
        val versionError: Option[String] =
          try { ApiVersionUtils.valueOf(requestedApiVersionString); None }
          catch { case _: Throwable => Some(s"$InvalidApiVersionString $requestedApiVersionString") }
        localeError.orElse(versionError) match {
          case Some(msg) => errorJson(Status.BadRequest, msg)
          case None =>
            IO(buildBankLevelResourceDocsJson(params, prefix, bankIdStr, requestedApiVersionString)).flatMap {
              case Right(body) => jsonResponse(Status.Ok, body)
              case Left((s, m)) => errorJson(s, m)
            }
        }
      }
    }
  }

  private def buildBankLevelResourceDocsJson(
    params: ParsedParams,
    prefix: String,
    bankIdStr: String,
    requestedApiVersionString: String
  ): Either[(Status, String), JValue] = {
    try {
      val impl = implForPrefix(prefix)
      val cacheKey = APIUtil.createResourceDocCacheKey(
        Some(bankIdStr),
        requestedApiVersionString,
        params.tags,
        params.partialFunctions,
        params.locale,
        params.contentParam,
        params.apiCollectionId,
        None
      )
      val cached = Caching.getDynamicResourceDocCache(cacheKey)
      val jv: JValue =
        if (cached.isDefined) json.parse(cached.get)
        else {
          val rdJson = impl.getResourceDocsObpDynamicCached(params.tags, params.partialFunctions, params.locale, None, isVersion4OrHigher = false).head
          val response = resourceDocsJsonToJsonResponse(rdJson)
          Caching.setDynamicResourceDocCache(cacheKey, json.compactRender(response))
          response
        }
      Right(jv)
    } catch {
      case e: Throwable =>
        logger.error(s"Http4sResourceDocs.buildBankLevelResourceDocsJson failed: ${e.getMessage}", e)
        Left(Status.BadRequest -> s"$UnknownError Can not create dynamic resource docs.")
    }
  }

  // ─── Handler: GET /obp/*/message-docs/{CONNECTOR}/swagger2.0 ─────────────

  private def handleGetMessageDocsSwagger(
    req: Request[IO],
    connector: String
  ): IO[Response[IO]] = {
    val params = parseParams(req)
    IO(buildMessageDocsSwagger(params, connector)).flatMap {
      case Right(body) => jsonResponse(Status.Ok, body)
      case Left((s, m)) => errorJson(s, m)
    }
  }

  private def buildMessageDocsSwagger(params: ParsedParams, connector: String): Either[(Status, String), JValue] = {
    try {
      val cacheKey = APIUtil.createResourceDocCacheKey(
        None,
        connector,
        params.tags,
        params.partialFunctions,
        params.locale,
        params.contentParam,
        params.apiCollectionId,
        None
      )
      val cached = Caching.getStaticSwaggerDocCache(cacheKey)
      val jv: JValue =
        if (cached.isDefined) json.parse(cached.get)
        else {
          val convertedToResourceDocs = RestConnector_vMar2019.messageDocs.map(APIUtil.toResourceDoc).toList
          val resourceDocListFiltered = ResourceDocsAPIMethodsUtil.filterResourceDocs(convertedToResourceDocs, params.tags, params.partialFunctions)
          val resourceDocJsonList = JSONFactory1_4_0.createResourceDocsJson(resourceDocListFiltered, isVersion4OrHigher = true, None).resource_docs
          val swaggerResourceDoc = code.api.ResourceDocs1_4_0.SwaggerJSONFactory.createSwaggerResourceDoc(resourceDocJsonList, ApiVersion.v3_1_0)
          val allSwaggerDefinitionCaseClasses =
            code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions.allFields ++
              code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.allFields
          val jsonAST = code.api.ResourceDocs1_4_0.SwaggerJSONFactory.loadDefinitions(resourceDocJsonList, allSwaggerDefinitionCaseClasses)
          val swaggerDocJsonJValue = Extraction.decompose(swaggerResourceDoc)(CustomJsonFormats.formats) merge jsonAST
          Caching.setStaticSwaggerDocCache(cacheKey, json.compactRender(swaggerDocJsonJValue))
          swaggerDocJsonJValue
        }
      Right(jv)
    } catch {
      case e: Throwable =>
        logger.error(s"Http4sResourceDocs.buildMessageDocsSwagger failed: ${e.getMessage}", e)
        Left(Status.BadRequest -> s"$UnknownError Can not convert internal swagger file.")
    }
  }

  // ─── Routes ──────────────────────────────────────────────────────────────
  // The version prefix segment (`obp` / `vX.Y.Z`) is captured but ignored —
  // the requested API version comes from the `{API_VERSION}` segment further
  // along the path. The Lift dispatch did the same thing (one dispatcher per
  // version prefix, all calling the same handlers).

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "obp" / prefix / "resource-docs" / requestedApiVersionString / "obp" =>
      // Match the Lift dispatchers' `isVersion4OrHigher` setting per prefix —
      // ResourceDocs400/500/510/600 used the V400-shaped output, all earlier
      // (v1.4.0/v2.0.0/v2.1.0/v2.2.0/v3.0.0/v3.1.0) used the pre-V400 shape.
      val isV4OrHigher = prefix match {
        case "v4.0.0" | "v5.0.0" | "v5.1.0" | "v6.0.0" => true
        case _                                          => false
      }
      handleGetResourceDocsObp(req, prefix, requestedApiVersionString, isVersion4OrHigher = isV4OrHigher)

    case req @ GET -> Root / "obp" / prefix / "resource-docs" / requestedApiVersionString / "swagger" =>
      handleGetResourceDocsSwagger(req, prefix, requestedApiVersionString)

    // OpenAPI 3.1 JSON and YAML — served for every URL prefix.
    //
    // Historically these routes were only registered by ResourceDocs600 (v6.0.0
    // prefix). With the centralised service, generating the OpenAPI spec only
    // depends on the requested-API-version path segment (`requestedApiVersionString`),
    // not on the URL prefix the client used to reach the service — so guarding
    // on a single prefix added no value and surprised callers that hit e.g.
    // `/obp/v5.1.0/resource-docs/v5.1.0/openapi` and got a Lift 404 fall-through.
    // The handlers use `implForPrefix(prefix)` which falls back to `ImplDefault`
    // for non-v6 prefixes; `isVersion4OrHigher` is hardcoded `true` inside the
    // handlers because the OpenAPI converter always consumes the v4-shape input.
    case req @ GET -> Root / "obp" / prefix / "resource-docs" / requestedApiVersionString / "openapi" =>
      handleGetResourceDocsOpenAPI31(req, prefix, requestedApiVersionString)

    case req @ GET -> Root / "obp" / prefix / "resource-docs" / requestedApiVersionString / "openapi.yaml" =>
      handleGetResourceDocsOpenAPI31Yaml(req, prefix, requestedApiVersionString)

    case req @ GET -> Root / "obp" / prefix / "banks" / bankIdStr / "resource-docs" / requestedApiVersionString / "obp" =>
      handleGetBankLevelDynamicResourceDocsObp(req, prefix, bankIdStr, requestedApiVersionString)

    case req @ GET -> Root / "obp" / _ / "message-docs" / connector / "swagger2.0" =>
      handleGetMessageDocsSwagger(req, connector)
  }
}
