package code.api.v5_1_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.{IdempotencyMiddleware, ResourceDocMatcher, ResourceDocMiddleware}
import code.api.util.newstyle.{BalanceNewStyle, RegulatedEntityAttributeNewStyle, ViewNewStyle}
import code.api.util.newstyle.RegulatedEntityNewStyle.{createRegulatedEntityNewStyle, deleteRegulatedEntityNewStyle, getRegulatedEntitiesNewStyle, getRegulatedEntityByEntityIdNewStyle}
import code.api.util.newstyle.Consumer.createConsumerNewStyle
import code.api.util.{APIUtil, Consent, ConsentJWT, CustomJsonFormats, JwtUtil, NewStyle, OBPBankId, OBPLimit, OBPOffset, OBPSortBy, SecureRandomUtil, X509}
import code.api.util.{ExampleValue, Glossary}
import code.api.v2_0_0.AccountsHelper
import code.api.v2_0_0.AccountsHelper.accountTypeFilterText
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{
  ConsentAccessAccountsJson,
  ConsentAccessJson
}
import code.api.v2_1_0.{ConsumerRedirectUrlJSON, JSONFactory210}
import code.api.v3_0_0.JSONFactory300
import code.api.v3_0_0.{AggregateMetricJSON, JSONFactory300}
import code.api.v3_0_0.JSONFactory300.createAggregateMetricJson
import code.api.v3_1_0.{ConsentChallengeJsonV310, ConsentJsonV310, JSONFactory310, PostConsentBodyCommonJson, PostConsentEmailJsonV310, PostConsentEntitlementJsonV310, PostConsentImplicitJsonV310, PostConsentPhoneJsonV310, PostConsentViewJsonV310}
import code.api.v4_0_0.{PutConsentStatusJsonV400, PutConsentUserJsonV400}
import code.api.v3_1_0.JSONFactory310.{createBadLoginStatusJson, createConsumerJSON}
import code.api.v4_0_0.JSONFactory400
import code.api.v4_0_0.JSONFactory400.{createAccountBalancesJson, createBalancesJson, createNewCoreBankAccountJson}
import code.api.v5_0_0.{Http4s500, JSONFactory500}
import code.api.v5_1_0.JSONFactory510.{createCallLimitJson, createConsentsInfoJsonV510, createConsentsJsonV510, createRegulatedEntitiesJson, createRegulatedEntityJson}
import code.atmattribute.AtmAttribute
import code.bankconnectors.Connector
import code.consent.{ConsentRequests, ConsentStatus, Consents, MappedConsent}
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import code.metrics.APIMetrics
import code.model.dataAccess.AuthUser
import code.model.{AppType, Consumer}
import code.ratelimiting.{RateLimiting, RateLimitingDI}
import code.regulatedentities.MappedRegulatedEntityProvider
import code.userlocks.UserLocksProvider
import code.users.Users
import code.util.Helper
import code.util.Helper.SILENCE_IS_GOLDEN
import code.views.Views
import code.views.system.{AccountAccess, ViewDefinition, ViewPermission}
import code.webuiprops.{MappedWebUiPropsProvider, WebUiPropsCommons}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{
  AccountId, AccountRouting, AccountRoutingJsonV121, AtmId, AtmT, BalanceId,
  Bank, BankAccount, BankAccountRoutings, BankId, BankIdAccountId, BankRoutingJson,
  BranchRoutingJsonV141, CounterpartyId, CustomerId, ListResult, ProductCode,
  RegulatedEntityId, TransactionRequestId, User, View, ViewId
}
import com.openbankproject.commons.model.enums.{AtmAttributeType, ChallengeType, ConsentType, RegulatedEntityAttributeType, StrongCustomerAuthentication, StrongCustomerAuthenticationStatus, SuppliedAnswerType, TransactionRequestStatus, UserAttributeType}
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.common.{Box, Empty, Failure, Full}
import com.openbankproject.commons.util.json
import com.openbankproject.commons.util.JsonAliases.prettyRender
import org.json4s.{Extraction, Formats}
import com.openbankproject.commons.util.JsonAliases.compactRender
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.{Helpers, Props, StringHelpers}
import code.api.util.http4s.{ErrorResponseConverter, RequestScopeConnection}
import org.http4s.{Header, HttpRoutes, MediaType, Method, Request, Response, Status, Uri}
import org.http4s.dsl.io._
import org.typelevel.ci.CIString

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Date
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.language.{higherKinds, implicitConversions}

// UK Open Banking consent SCA (see authoriseUKConsentChallenge / authoriseUKConsent):
// the challenge-start endpoint returns this; the authorise endpoint consumes the answer.
case class UKConsentScaChallengeJsonV510(challenge_id: String, sca_status: String, sca_method: String)
// account_ids: the accounts the PSU is selecting for this consent's granted permissions —
// see the Gap 4 remediation note above authoriseUKConsent (bankId comes from the URL BANK_ID).
case class PostUKConsentAuthoriseJsonV510(challenge_id: String, answer: String, account_ids: List[String])

object Http4s510 {
  /** The shared consent description, reused by the v6.0.0 create-consent doc. */
  def generalObpConsentTextForV600: String = Implementations5_1_0.generalObpConsentText


  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v5_1_0
  val versionStatus: String = ApiVersionStatus.STABLE.toString
  val resourceDocs: ArrayBuffer[ResourceDoc] = ArrayBuffer[ResourceDoc]()

  object Implementations5_1_0 {

    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // Statuses from which a UK Open Banking consent may be (re-)authorised. AWAITINGAUTHORISATION
    // is the initial authorise; AUTHORISED and REVOKED (wire: CANC) are the re-authentication
    // cases per the UK spec ("re-authenticate ... if the account-access-consent has a Status of
    // AUTH or CANC and the ExpirationDateTime has not elapsed"). EXPIRED and REJECTED are terminal
    // -- the TPP must create a new consent rather than re-authenticate.
    private val ukReAuthableStatuses: Set[String] = Set(
      ConsentStatus.AWAITINGAUTHORISATION.toString,
      ConsentStatus.AUTHORISED.toString,
      ConsentStatus.REVOKED.toString
    )

    // Used by lifted consumer-management endpoint descriptions.
    private def consumerDisabledText(): String = {
      if (APIUtil.getPropsAsBoolValue("consumers_enabled_by_default", false) == false) {
        "Please note: Your consumer may be disabled as a result of this action."
      } else {
        ""
      }
    }

    // ─── root (GET /root and GET / — v5.1 override of every prior version) ──

    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` =>
        EndpointHelpers.executeFuture(req) {
          Future.successful(JSONFactory510.getApiInfoJSON(ApiVersion.v5_1_0, versionStatus))
        }
      case req @ GET -> `prefixPath` / "root" =>
        EndpointHelpers.executeFuture(req) {
          Future.successful(JSONFactory510.getApiInfoJSON(ApiVersion.v5_1_0, versionStatus))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(root),
      "GET",
      "/root",
      "Get API Info (root)",
      """Returns information about:
      |
      |* API version
      |* Hosted by information
      |* Hosted at information
      |* Energy source information
      |* Git Commit""",
      EmptyBody,
      apiInfoJson400,
      List(UnknownError, MandatoryPropertyIsNotSet),
      apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(root)
    )

    // ─── getMyConsentsByBank (GET /banks/BANK_ID/my/consents) — v5.1 override

    val getMyConsentsByBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "my" / "consents" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val params = req.uri.query.multiParams
          val limitParam = params.get("limit").flatMap(_.headOption).flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(50)
          val offsetParam = params.get("offset").flatMap(_.headOption).flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
          val statusParam = params.get("status").flatMap(_.headOption)
          val sortByParam = params.get("sort_by").flatMap(_.headOption).getOrElse("created_date:desc")
          val sortParts = sortByParam.split(":").map(_.trim.toLowerCase)
          val sortField = sortParts(0)
          val sortDirection = sortParts.lift(1).getOrElse("desc")
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            rows <- Future {
              code.consent.DoobieConsentQueries.getConsentsByUserAndBank(
                userId = user.userId, bankId = bankIdStr,
                status = statusParam, limit = limitParam, offset = offsetParam,
                sortField = sortField, sortDirection = sortDirection)
            }
          } yield ConsentsInfoJsonV510(rows.map(Implementations5_1_0.rowToConsentInfoJsonV510))
        }
    }

    private[v5_1_0] def rowToConsentInfoJsonV510(row: code.consent.DoobieConsentQueries.ConsentRow): ConsentInfoJsonV510 = {
      ConsentInfoJsonV510(
        consent_reference_id = row.consentReferenceId.toString,
        consent_id = row.consentId,
        consumer_id = row.consumerId.orNull,
        created_by_user_id = row.createdByUserId,
        status = row.status,
        last_action_date = row.lastActionDate.map(d => new java.text.SimpleDateFormat(DateWithDay).format(d)).orNull,
        last_usage_date = row.lastUsageDate.map(d => new java.text.SimpleDateFormat(DateWithSeconds).format(d)).orNull,
        jwt = row.jwt.orNull,
        jwt_payload = row.jwtPayload.orNull,
        api_standard = row.apiStandard.orNull,
        api_version = row.apiVersion.orNull,
        jwt_expires_at = row.jwtExpiresAt.map(d => new java.text.SimpleDateFormat(DateWithSeconds).format(d)).orNull
      )
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getMyConsentsByBank),
      "GET",
      "/banks/BANK_ID/my/consents",
      "Get My Consents at Bank",
      s"""
         |
         |This endpoint gets the Consents created by a current User at the specified Bank.
         |
         |${userAuthenticationMessage(true)}
         |
         |1 limit (for pagination: defaults to 50)  eg:limit=200
         |
         |2 offset (for pagination: zero index, defaults to 0) eg: offset=10
         |
         |3 status  (ignore if omitted)
         |
         |4 sort_by (defaults to created_date:desc)  eg: sort_by=created_date:desc
         |
         |Note: This endpoint only returns consents that explicitly reference the specified BANK_ID.
         |Consents created before the consent_item join table was introduced will not appear in results.
         |
         |eg: /banks/BANK_ID/my/consents?limit=10&offset=0&sort_by=created_date:desc
         |
      """.stripMargin,
      EmptyBody,
      consentsInfoJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(getMyConsentsByBank)
    )

    // ─── getAggregateMetrics (GET /management/aggregate-metrics) — v5.1 override

    val getAggregateMetrics: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "aggregate-metrics" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            aggregateMetrics <- APIMetrics.apiMetrics.vend.getAllAggregateMetricsFuture(obpQueryParams, true)
              .map(x => unboxFullOrFail(x, Some(cc), GetAggregateMetricsError))
          } yield createAggregateMetricJson(aggregateMetrics).headOption
              .getOrElse(AggregateMetricJSON(0, 0.0, 0.0, 0.0))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getAggregateMetrics), "GET",
      "/management/aggregate-metrics", "Get Aggregate Metrics",
      s"""Returns aggregate metrics on api usage eg. total count, response time (in ms), etc.
         |
         |Should be able to filter on the following fields
         |
         |eg: /management/aggregate-metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&consumer_id=5
         |&user_id=66214b8e-259e-44ad-8868-3eb47be70646&implemented_by_partial_function=getTransactionsForBankAccount
         |&implemented_in_version=v3.0.0&url=/obp/v3.0.0/banks/gh.29.uk/accounts/8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0/owner/transactions
         |&verb=GET&anon=false&app_name=MapperPostman
         |&exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
         |
         |1 from_date (defaults to the day before the current date): eg:from_date=$DateWithMsExampleString
         |
         |2 to_date (defaults to the current date) eg:to_date=$DateWithMsExampleString
         |
         |3 consumer_id  (if null ignore)
         |
         |4 user_id (if null ignore)
         |
         |5 anon (if null ignore) only support two value : true (return where user_id is null.) or false (return where user_id is not null.)
         |
         |6 url (if null ignore), note: can not contain '&'.
         |
         |7 app_name (if null ignore)
         |
         |8 implemented_by_partial_function (if null ignore),
         |
         |9 implemented_in_version (if null ignore)
         |
         |10 verb (if null ignore)
         |
         |11 correlation_id (if null ignore)
         |
         |12 include_app_names (if null ignore).eg: &include_app_names=API-EXPLORER,API-Manager,SOFI,null
         |
         |13 include_url_patterns (if null ignore).you can design you own SQL LIKE pattern. eg: &include_url_patterns=%management/metrics%,%management/aggregate-metrics%
         |
         |14 include_implemented_by_partial_functions (if null ignore).eg: &include_implemented_by_partial_functions=getMetrics,getConnectorMetrics,getAggregateMetrics
         |
         |15 http_status_code (if null ignore) - Filter by HTTP status code. eg: http_status_code=200 returns only successful calls, http_status_code=500 returns server errors
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody, aggregateMetricsJSONV300,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagMetric, apiTagAggregateMetrics),
      Some(List(canReadAggregateMetrics)),
      http4sPartialFunction = Some(getAggregateMetrics)
    )

    // ─── getBanks — kept in v5.1.0 layer so metrics are attributed to this version ──

    val getBanks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" =>
        EndpointHelpers.executeAndRespond(req) { implicit cc =>
          for {
            (banks, _) <- NewStyle.function.getBanks(Some(cc))
          } yield JSONFactory400.createBanksJson(banks)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getBanks), "GET",
      "/banks", "Get Banks",
      """Get banks on this API instance
        |Returns a list of banks supported on this server.""",
      EmptyBody, banksJSON,
      List(UnknownError),
      List(apiTagBank),
      None,
      http4sPartialFunction = Some(getBanks)
    )

    // ─── ATM CRUD (createAtm/updateAtm/getAtms/getAtm/deleteAtm) — v5.1 overrides

    val createAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "atms" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            atmJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[AtmJsonV510]}", 400, Some(cc)) {
              val atm = com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PostAtmJsonV510]
              atm.id.get  // require id
              atm
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", 400, Some(cc)) {
              atmJson.bank_id == bankId.value
            }
            atm <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Atm", 400, Some(cc)) {
              JSONFactory510.transformToAtmFromV510(atmJson)
            }
            (created, _) <- NewStyle.function.createOrUpdateAtm(atm, Some(cc))
            (atmAttributes, _) <- NewStyle.function.getAtmAttributesByAtm(bankId, created.atmId, Some(cc))
          } yield JSONFactory510.createAtmJsonV510(created, atmAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createAtm),
      "POST",
      "/banks/BANK_ID/atms",
      "Create ATM",
      s"""Create ATM.""",
      postAtmJsonV510,
      atmJsonV510,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagATM),
      Some(List(canCreateAtm, canCreateAtmAtAnyBank)),
      http4sPartialFunction = Some(createAtm)
    )

    val updateAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "atms" / atmIdStr =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          val atmId = AtmId(atmIdStr)
          for {
            (_, _) <- NewStyle.function.getAtm(bankId, atmId, Some(cc))
            atmJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[AtmJsonV510]}", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[AtmJsonV510]
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonValue BANK_ID has to be the same in the URL and Body", 400, Some(cc)) {
              atmJson.bank_id == bankId.value
            }
            atm <- NewStyle.function.tryons(CouldNotTransformJsonToInternalModel + " Atm", 400, Some(cc)) {
              JSONFactory510.transformToAtmFromV510(atmJson.copy(id = Some(atmId.value)))
            }
            (updated, _) <- NewStyle.function.createOrUpdateAtm(atm, Some(cc))
            (atmAttributes, _) <- NewStyle.function.getAtmAttributesByAtm(bankId, updated.atmId, Some(cc))
          } yield JSONFactory510.createAtmJsonV510(updated, atmAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateAtm),
      "PUT",
      "/banks/BANK_ID/atms/ATM_ID",
      "UPDATE ATM",
      s"""Update ATM.""",
      atmJsonV510.copy(id = None, attributes = None),
      atmJsonV510,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagATM),
      Some(List(canUpdateAtm, canUpdateAtmAtAnyBank)),
      http4sPartialFunction = Some(updateAtm)
    )

    // ─── getAtms / getAtm ─────────────────────────────────────────────────
    // ResponseHeadersTest exercises ETag, If-None-Match → 304, and
    // If-Modified-Since → 304 on these two GETs. We bypass the standard
    // executeFuture path and inline the ETag/conditional-header logic
    // (mirror of APIUtil.checkConditionalRequest:470 + getRequestHeadersNewStyle:532).

    private def respondWithETag[A](
      req: Request[IO],
      f: code.api.util.CallContext => Future[A]
    )(implicit formats: Formats): IO[Response[IO]] = {
      implicit val cc: code.api.util.CallContext = req.callContext
      RequestScopeConnection.fromFuture(f(cc)).attempt.flatMap {
        case Left(err) => ErrorResponseConverter.toHttp4sResponse(err, cc)
        case Right(result) =>
          val body = prettyRender(Extraction.decompose(result))
          val url = cc.url
          val eTag = code.api.util.HashUtil.calculateETag(url, Full(body))

          // If-None-Match: 304 if matches
          val ifNoneMatch = req.headers.get(CIString("If-None-Match")).map(_.head.value)
          val ifModifiedSince = req.headers.get(CIString("If-Modified-Since")).map(_.head.value)

          val maybe304: Option[IO[Response[IO]]] = ifNoneMatch match {
            case Some(value) if value == eTag =>
              Some(IO.pure(Response[IO](Status.NotModified)
                .putHeaders(Header.Raw(CIString(code.api.ResponseHeader.ETag), eTag))))
            case _ if ifNoneMatch.isDefined => None  // header present but mismatch → fall through
            case None => ifModifiedSince.map { since =>
              IO.blocking(checkIfModifiedSinceCached(cc, eTag, since)).map { isCachedFresh =>
                if (isCachedFresh) Response[IO](Status.NotModified)
                  .putHeaders(Header.Raw(CIString(code.api.ResponseHeader.ETag), eTag))
                else Response[IO](Status.Ok).withEntity(body)
                  .withContentType(org.http4s.headers.`Content-Type`(MediaType.application.json))
                  .putHeaders(Header.Raw(CIString(code.api.ResponseHeader.ETag), eTag))
              }
            }
          }

          maybe304.getOrElse(
            IO.pure(Response[IO](Status.Ok).withEntity(body)
              .withContentType(org.http4s.headers.`Content-Type`(MediaType.application.json))
              .putHeaders(Header.Raw(CIString(code.api.ResponseHeader.ETag), eTag)))
          )
      }
    }

    // Mirror of APIUtil.checkIfModifiedSinceHeader:390 (without the async-update
    // race we don't strictly need either — Lift's behaviour is best-effort).
    // Returns true if the cached ETag is fresh (response 304), false otherwise.
    private def checkIfModifiedSinceCached(
      cc: code.api.util.CallContext,
      currentETag: String,
      headerValue: String
    ): Boolean = {
      val df = new java.text.SimpleDateFormat(DateWithSeconds)
      val headerEpoch: Long = scala.util.Try(df.parse(headerValue).getTime).getOrElse(0L)
      val requestHeaders = cc.requestHeaders
        .filter(i => i.name == "limit" || i.name == "offset").sortBy(_.name)
      val hashedRequestPayload = code.api.util.HashUtil.Sha256Hash(cc.url + requestHeaders)
      val consumerId = cc.consumer.map(_.consumerId.get).getOrElse("None")
      val userId = scala.util.Try(cc.userId).getOrElse("None")
      val compositeKey =
        if (consumerId == "None" && userId == "None") "anonymous"
        else s"consumerId${consumerId}::userId${userId}"
      val cacheKey = s"$compositeKey::$hashedRequestPayload"
      code.etag.MappedETag.find(By(code.etag.MappedETag.ETagResource, cacheKey)) match {
        case Full(row) if row.lastUpdatedMSSinceEpoch < headerEpoch =>
          val modified = row.eTagValue != currentETag
          if (modified) {
            // Async update — match Lift's behaviour
            scala.concurrent.Future(row.LastUpdatedMSSinceEpoch(System.currentTimeMillis).ETagValue(currentETag).save)
            false
          } else true
        case Empty =>
          // Async create
          scala.concurrent.Future(tryo(
            code.etag.MappedETag.create
              .ETagResource(cacheKey).ETagValue(currentETag)
              .LastUpdatedMSSinceEpoch(System.currentTimeMillis).save))
          false
        case _ => false
      }
    }

    val getAtms: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "atms" =>
        Implementations5_1_0.respondWithETag(req, { cc =>
          implicit val ccImpl: code.api.util.CallContext = cc
          val bankId = BankId(bankIdStr)
          val params = req.uri.query.multiParams
          val limit: Box[String] = params.get("limit").flatMap(_.headOption).map(Full(_)).getOrElse(Empty)
          val offset: Box[String] = params.get("offset").flatMap(_.headOption).map(Full(_)).getOrElse(Empty)
          for {
            _ <- if (getAtmsIsPublic) Future.successful(Full(())) else Future.successful(Full(cc.user.openOrThrowException(AuthenticatedUserIsRequired)))
            _ <- Helper.booleanToFuture(s"$InvalidNumber limit:${limit.getOrElse("")}", cc = Some(cc)) {
              limit match {
                case Full(i) => i.toList.forall(c => Character.isDigit(c))
                case _       => true
              }
            }
            _ <- Helper.booleanToFuture(maximumLimitExceeded, cc = Some(cc)) {
              limit match {
                case Full(i) if i.toInt > 10000 => false
                case _                          => true
              }
            }
            (atms, _) <- NewStyle.function.getAtmsByBankId(bankId, offset, limit, Some(cc))
            atmAndAttrs <- Future.sequence(atms.map(atm =>
              NewStyle.function.getAtmAttributesByAtm(bankId, atm.atmId, Some(cc)).map(x => (atm, x._1))))
          } yield JSONFactory510.createAtmsJsonV510(atmAndAttrs)
        })
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAtms),
      "GET",
      "/banks/BANK_ID/atms",
      "Get Bank ATMS",
      s"""Returns information about ATMs for a single bank specified by BANK_ID including:
      |
      |* Address
      |* Geo Location
      |* License the data under this endpoint is released under
      |
      |Pagination:
      |
      |By default, 100 records are returned.
      |
      |You can use the url query parameters *limit* and *offset* for pagination
      |
      |${userAuthenticationMessage(!getAtmsIsPublic)}""".stripMargin,
      EmptyBody,
      atmsJsonV510,
      List($BankNotFound, UnknownError),
      List(apiTagATM),
      None,
      http4sPartialFunction = Some(getAtms)
    )

    val getAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "atms" / atmIdStr =>
        Implementations5_1_0.respondWithETag(req, { cc =>
          implicit val ccImpl: code.api.util.CallContext = cc
          val bankId = BankId(bankIdStr); val atmId = AtmId(atmIdStr)
          for {
            _ <- if (getAtmsIsPublic) Future.successful(Full(())) else Future.successful(Full(cc.user.openOrThrowException(AuthenticatedUserIsRequired)))
            (_, _) <- NewStyle.function.getBank(bankId, Some(cc))
            (atm, _) <- NewStyle.function.getAtm(bankId, atmId, Some(cc))
            (atmAttributes, _) <- NewStyle.function.getAtmAttributesByAtm(bankId, atmId, Some(cc))
          } yield JSONFactory510.createAtmJsonV510(atm, atmAttributes)
        })
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getAtm), "GET",
      "/banks/BANK_ID/atms/ATM_ID", "Get Bank ATM",
      s"""Returns information about ATM for a single bank specified by BANK_ID and ATM_ID including:
      |
      |* Address
      |* Geo Location
      |* License the data under this endpoint is released under
      |* ATM Attributes
      |
      |
      |
      |${userAuthenticationMessage(!getAtmsIsPublic)}""".stripMargin,
      EmptyBody, atmJsonV510,
      List(AuthenticatedUserIsRequired, BankNotFound, AtmNotFoundByAtmId, UnknownError),
      List(apiTagATM),
      None,
      http4sPartialFunction = Some(getAtm)
    )

    val deleteAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / bankIdStr / "atms" / atmIdStr =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          val bankId = BankId(bankIdStr)
          val atmId = AtmId(atmIdStr)
          for {
            (atm, _) <- NewStyle.function.getAtm(bankId, atmId, Some(cc))
            (deleted, _) <- NewStyle.function.deleteAtm(atm, Some(cc))
            (attrsDeleted, _) <- NewStyle.function.deleteAtmAttributesByAtmId(atmId, Some(cc))
          } yield deleted && attrsDeleted
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteAtm),
      "DELETE",
      "/banks/BANK_ID/atms/ATM_ID",
      "Delete ATM",
      s"""Delete ATM.
      |
      |This will also delete all its attributes.
      |
      |""".stripMargin,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      List(apiTagATM),
      Some(List(canDeleteAtmAtAnyBank, canDeleteAtm)),
      http4sPartialFunction = Some(deleteAtm)
    )

    // ─── createConsumer / getConsumer / getConsumers — v5.1 overrides ──────

    val createConsumer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "consumers" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            tup <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              val js = com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[CreateConsumerRequestJsonV510]
              val appType = if (js.app_type.equals("Confidential")) AppType.valueOf("Confidential") else AppType.valueOf("Public")
              (js, appType)
            }
            (postedJson, appType) = tup
            (consumer, _) <- createConsumerNewStyle(
              key = Some(Helpers.randomString(40).toLowerCase),
              secret = Some(Helpers.randomString(40).toLowerCase),
              isActive = Some(postedJson.enabled),
              name = Some(postedJson.app_name),
              appType = Some(appType),
              description = Some(postedJson.description),
              developerEmail = Some(postedJson.developer_email),
              company = Some(postedJson.company),
              redirectURL = Some(postedJson.redirect_url),
              createdByUserId = Some(user.userId),
              clientCertificate = postedJson.client_certificate,
              logoURL = postedJson.logo_url,
              Some(cc)
            )
          } yield JSONFactory510.createConsumerJsonOnlyForPostResponseV510(consumer, None)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createConsumer),
      "POST",
      "/management/consumers",
      "Create a Consumer",
      s"""Create a Consumer (Authenticated access).
      |
      |A Consumer represents an application that uses the Open Bank Project API. Each Consumer has:
      |- A unique **key** (40 character random string) - used as the client ID for authentication
      |- A unique **secret** (40 character random string) - used for secure authentication
      |- An **app_type** (Confidential or Public) - determines OAuth2 flow requirements
      |- Metadata like app_name, description, developer_email, company, etc.
      |
      |**How it works (for comprehension flow):**
      |
      |1. **Extract authenticated user**: Retrieves the currently logged-in user who is creating the consumer
      |2. **Parse and validate JSON request**: Extracts the CreateConsumerRequestJsonV510 from the request body
      |3. **Determine app_type**: Converts the string "Confidential" or "Public" to the AppType enum
      |4. **Generate credentials**: Creates random 40-character key and secret for the new consumer
      |5. **Create consumer record**: Calls createConsumerNewStyle with all parameters:
      |   - Auto-generated key and secret
      |   - enabled flag (controls if consumer is active)
      |   - app_name, description, developer_email, company
      |   - redirect_url (for OAuth flows)
      |   - client_certificate (optional, for certificate-based auth)
      |   - logo_url (optional)
      |   - createdByUserId (the authenticated user's ID)
      |6. **Return response**: Returns the newly created consumer with HTTP 201 Created status
      |
      |**Client Certificate (Optional but Recommended for PSD2/Berlin Group):**
      |
      |The `client_certificate` field provides enhanced security through X.509 certificate validation.
      |
      |**IMPORTANT SECURITY NOTE:**
      |- **This endpoint does NOT validate the certificate at creation time** - any certificate can be provided
      |- The certificate is simply stored with the consumer record without checking if it's from a trusted CA
      |- For PSD2/Berlin Group compliance with certificate validation, use the **Dynamic Registration** endpoint instead
      |- Dynamic Registration validates certificates against registered Regulated Entities and trusted CAs
      |
      |**How certificates are used (after creation):**
      |- Certificate is stored in PEM format (Base64-encoded X.509) with the consumer record
      |- On subsequent API requests, the certificate from the `PSD2-CERT` header is compared against the stored certificate
      |- If certificates don't match, access is denied even with valid OAuth2 tokens
      |- First request populates the certificate if not set; subsequent requests must match that certificate
      |
      |**Certificate validation process (during API requests, NOT at consumer creation):**
      |1. Certificate from `PSD2-CERT` header is compared to stored certificate (simple string match)
      |2. Certificate is parsed from PEM format to X.509Certificate object
      |3. Validated against a configured trust store (PKCS12 format) containing trusted root CAs
      |4. Certificate chain is verified using PKIX validation
      |5. Optional CRL (Certificate Revocation List) checking if enabled via `use_tpp_signature_revocation_list`
      |6. Public key from certificate can verify signed requests (Berlin Group requirement)
      |
      |**Note:** Steps 3-6 only apply during API request validation, NOT during consumer creation via this endpoint.
      |
      |**Security benefits (when properly configured):**
      |- **Certificate binding**: Links consumer to a specific certificate (prevents token reuse with different certs)
      |- **Request verification**: Certificate's public key can verify signed requests
      |- **Non-repudiation**: Certificate-based signatures prove request origin
      |
      |**Security limitations of this endpoint:**
      |- **No validation at creation**: Any certificate (even self-signed or expired) can be stored
      |- **No CA verification**: Certificate is not checked against trusted root CAs during creation
      |- **No Regulated Entity check**: Does not verify the TPP is registered
      |- **Use Dynamic Registration instead** for proper PSD2/Berlin Group compliance with full certificate validation
      |
      |**For proper PSD2 compliance:**
      |Use the **Dynamic Consumer Registration** endpoint (`POST /obp/v5.1.0/dynamic-registration/consumers`) which:
      |- Requires JWT-signed request using the certificate's private key
      |- Validates certificate against Regulated Entity registry
      |- Checks certificate is from a trusted CA using the configured trust store
      |- Ensures proper QWAC/eIDAS compliance for EU TPPs
      |
      |**Configuration properties (for runtime validation):**
      |- `truststore.path.tpp_signature` - Path to trust store for certificate validation during API requests
      |- `truststore.password.tpp_signature` - Trust store password
      |- `use_tpp_signature_revocation_list` - Enable/disable CRL checking during requests (default: true)
      |- `consumer_validation_method_for_consent` - Set to "CONSUMER_CERTIFICATE" for cert-based validation
      |- `bypass_tpp_signature_validation` - Emergency bypass (default: false, use only for testing)
      |
      |**Important**: The key and secret are only shown once in the response. Save them securely as they cannot be retrieved later.
      |
      |${consumerDisabledText()}
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      createConsumerRequestJsonV510,
      consumerJsonOnlyForPostResponseV510,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagConsumer),
      Some(List(canCreateConsumer)),
      authMode = UserOrApplication,
      http4sPartialFunction = Some(createConsumer)
    )

    val getConsumer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consumers" / consumerId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            user <- Users.users.vend.getUserByUserIdFuture(consumer.createdByUserId.get)
          } yield createConsumerJSON(consumer, user)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getConsumer),
      "GET",
      "/management/consumers/CONSUMER_ID",
      "Get Consumer",
      s"""Get the Consumer specified by CONSUMER_ID.
      |
      |""",
      EmptyBody,
      consumerJSON,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, ConsumerNotFoundByConsumerId, UnknownError),
      List(apiTagConsumer),
      Some(List(canGetConsumers)),
      http4sPartialFunction = Some(getConsumer)
    )

    val getConsumers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consumers" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            consumers <- Consumers.consumers.vend.getConsumersFuture(obpQueryParams, Some(cc))
          } yield JSONFactory510.createConsumersJson(consumers)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getConsumers), "GET",
      "/management/consumers", "Get Consumers",
      s"""Get the all Consumers.
      |
      |${userAuthenticationMessage(true)}
      |
      |${urlParametersDocument(true, true)}
      |
      |""",
      EmptyBody, consumersJsonV510,
      List(
        $AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConsumer),
      Some(List(canGetConsumers)),
      authMode = UserOrApplication,
      http4sPartialFunction = Some(getConsumers)
    )

    // ─── getTransactionRequests (v5.1 — adds attributes filter) ─────────────

    val getTransactionRequests: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / viewIdStr / "transaction-requests" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          val accountId = AccountId(accountIdStr)
          val viewId = ViewId(viewIdStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            _ <- NewStyle.function.isEnabledTransactionRequests(Some(cc))
            (_, _) <- NewStyle.function.getBank(bankId, Some(cc))
            (fromAccount, _) <- NewStyle.function.checkBankAccountExists(bankId, accountId, Some(cc))
            view <- ViewNewStyle.checkAccountAccessAndGetView(viewId, BankIdAccountId(bankId, accountId), Full(user), Some(cc))
            _ <- Helper.booleanToFuture(
              s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${CAN_SEE_TRANSACTION_REQUESTS}` permission on the View(${viewId.value})",
              cc = Some(cc)) {
              view.allowed_actions.exists(_ == CAN_SEE_TRANSACTION_REQUESTS)
            }
            (transactionRequests, _) <- Future(Connector.connector.vend.getTransactionRequests210(user, fromAccount, Some(cc)))
              .map(unboxFullOrFail(_, Some(cc), GetTransactionRequestsException))
            paramsMap = req.uri.query.multiParams.map { case (k, vs) => k -> vs.toList }
            (transactionRequestAttributes, _) <- NewStyle.function.getByAttributeNameValues(bankId, paramsMap, true, Some(cc))
            transactionRequestIds = transactionRequestAttributes.map(_.transactionRequestId)
            transactionRequestsFiltered = if (paramsMap.isEmpty) transactionRequests
              else transactionRequests.filter(tr => transactionRequestIds.contains(tr.id))
          } yield JSONFactory510.createTransactionRequestJSONs(transactionRequestsFiltered, transactionRequestAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getTransactionRequests), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests",
      "Get Transaction Requests.",
      """Returns transaction requests for account specified by ACCOUNT_ID at bank specified by BANK_ID.
        |
        |The VIEW_ID specified must be 'owner' and the user must have access to this view.
        |
        |Version 2.0.0 now returns charge information.
        |
        |Transaction Requests serve to initiate transactions that may or may not proceed. They contain information including:
        |
        |* Transaction Request Id
        |* Type
        |* Status (INITIATED, COMPLETED)
        |* Challenge (in order to confirm the request)
        |* From Bank / Account
        |* Details including Currency, Value, Description and other initiation information specific to each type. (Could potentialy include a list of future transactions.)
        |* Related Transactions
        |
        |PSD2 Context: PSD2 requires transparency of charges to the customer.
        |This endpoint provides the charge that would be applied if the Transaction Request proceeds - and a record of that charge there after.
        |The customer can proceed with the Transaction by answering the security challenge.
        |
        |We support query transaction request by attribute
        |URL params example:/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests?invoiceNumber=123&referenceNumber=456
        |
      """.stripMargin,
      EmptyBody, transactionRequestWithChargeJSONs210,
      List(AuthenticatedUserIsRequired, BankNotFound, BankAccountNotFound,
        UserNoPermissionAccessView, ViewDoesNotPermitAccess,
        GetTransactionRequestsException, UnknownError),
      List(apiTagTransactionRequest, apiTagPSD2PIS),
      None,
      http4sPartialFunction = Some(getTransactionRequests)
    )

    // ─── getBankAccountsBalances (v5.1 — same shape as v4 but in v5.1) ─────

    val getBankAccountsBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "balances" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            (allowedAccounts, _) <- BalanceNewStyle.getAccountAccessAtBank(user, bankId, Some(cc))
            (accountsBalances, _) <- BalanceNewStyle.getBankAccountsBalances(allowedAccounts, Some(cc))
          } yield createBalancesJson(accountsBalances)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getBankAccountsBalances),
      "GET",
      "/banks/BANK_ID/balances",
      "Get Account Balances by BANK_ID",
      """Get the Balances for the Account specified by BANK_ID.""",
      EmptyBody,
      accountBalancesV400Json,
      List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(getBankAccountsBalances)
    )

    // ─── getAllBankAccountBalances (v5.1 override returns BankAccountBalancesJsonV510)

    val getAllBankAccountBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "balances" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val accountId = AccountId(accountIdStr)
          for {
            _ <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            (balances, _) <- code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountBalances(accountId, Some(cc))
          } yield JSONFactory510.createBankAccountBalancesJson(balances)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAllBankAccountBalances),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/balances",
      "Get All Bank Account Balances",
      s"""Get all Balances for a Bank Account.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      bankAccountBalancesJsonV510,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagAccount, apiTagBalance),
      None,
      http4sPartialFunction = Some(getAllBankAccountBalances)
    )

    // ─── allRoutes (chained as endpoints land below) ────────────────────────

    // ─── Simple GETs: suggestedSessionTimeout, well-known, regulatedEntities,
    //                 waitingForGodot, getApiTags, mtlsClientCertificateInfo
    //                 (plus log-cache×6, regulated-entities CRUD, getAllApiCollections)

    val suggestedSessionTimeout: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "ui" / "suggested-session-timeout" =>
        EndpointHelpers.executeFuture(req) {
          Future(APIUtil.getPropsAsIntValue("session_inactivity_timeout_in_seconds", 300))
            .map(t => SuggestedSessionTimeoutV510(t.toString))
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(suggestedSessionTimeout),
      "GET",
      "/ui/suggested-session-timeout",
      "Get Suggested Session Timeout",
      """Returns information about:
      |
      |* Suggested session timeout in case of a user inactivity
      """,
      EmptyBody,
      SuggestedSessionTimeoutV510("300"),
      List(UnknownError),
      apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(suggestedSessionTimeout)
    )

    val getOAuth2ServerWellKnown: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "well-known" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (_, _) <- APIUtil.anonymousAccess(cc)
          } yield {
            val providerPropBox = APIUtil.getPropsValue("oauth2.oidc_provider")
            // "google" is an intentional addition over the Lift original (obp-oidc/keycloak only):
            // token validation for Google ID tokens already exists in OAuth2Login.Google,
            // this just lets oauth2.oidc_provider advertise its well-known URL too.
            val availableProviders = Map(
              "obp-oidc" -> WellKnownUriJsonV510("obp-oidc", code.api.OAuth2Login.OBPOIDC.wellKnownOpenidConfiguration.toURL.toString),
              "keycloak" -> WellKnownUriJsonV510("keycloak", code.api.OAuth2Login.Keycloak.wellKnownOpenidConfiguration.toURL.toString),
              "google" -> WellKnownUriJsonV510("google", code.api.OAuth2Login.Google.wellKnownOpenidConfiguration.toURL.toString)
            )
            val providersToShow: List[WellKnownUriJsonV510] = providerPropBox match {
              case Empty => Nil
              case Full(value) if value.trim.isEmpty => availableProviders.values.toList
              case Full(value) =>
                val wanted = value.split(",").map(_.trim.toLowerCase).filter(_.nonEmpty).toSet
                if (wanted.contains("none")) Nil
                else availableProviders.filter { case (name, _) => wanted.contains(name) }.values.toList
              case _ => Nil
            }
            WellKnownUrisJsonV510(providersToShow)
          }
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getOAuth2ServerWellKnown),
      "GET",
      "/well-known",
      "Get Well Known URIs",
      """Get the OAuth2 server's public Well Known URIs.
        |
      """.stripMargin,
      EmptyBody,
      oAuth2ServerJwksUrisJson,
      List(UnknownError),
      List(apiTagApi),
      None,
      http4sPartialFunction = Some(getOAuth2ServerWellKnown)
    )

    val regulatedEntities: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "regulated-entities" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for { (entities, _) <- getRegulatedEntitiesNewStyle(Some(cc)) }
            yield createRegulatedEntitiesJson(entities)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(regulatedEntities),
      "GET",
      "/regulated-entities",
      "Get Regulated Entities",
      """Returns information about:
      |
      |* Regulated Entities
      """,
      EmptyBody,
      regulatedEntitiesJsonV510,
      List(UnknownError),
      apiTagDirectory :: apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(regulatedEntities)
    )

    val getRegulatedEntityById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "regulated-entities" / regulatedEntityId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for { (entity, _) <- getRegulatedEntityByEntityIdNewStyle(regulatedEntityId, Some(cc)) }
            yield createRegulatedEntityJson(entity)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getRegulatedEntityById),
      "GET",
      "/regulated-entities/REGULATED_ENTITY_ID",
      "Get Regulated Entity",
      """Get Regulated Entity By REGULATED_ENTITY_ID
      """,
      EmptyBody,
      regulatedEntityJsonV510,
      List(UnknownError),
      apiTagDirectory :: apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(getRegulatedEntityById)
    )

    val createRegulatedEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "regulated-entities" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val parsedBody = com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse(""))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $RegulatedEntityPostJsonV510 "
          for {
            postedData <- NewStyle.function.tryons(failMsg, 400, Some(cc)) {
              parsedBody.extract[RegulatedEntityPostJsonV510]
            }
            servicesString <- NewStyle.function.tryons(s"$InvalidJsonFormat The `services` field is not valid JSON", 400, Some(cc)) {
              prettyRender(postedData.services)
            }
            (entity, _) <- createRegulatedEntityNewStyle(
              certificateAuthorityCaOwnerId = Some(postedData.certificate_authority_ca_owner_id),
              entityCertificatePublicKey = Some(postedData.entity_certificate_public_key),
              entityName = Some(postedData.entity_name),
              entityCode = Some(postedData.entity_code),
              entityType = Some(postedData.entity_type),
              entityAddress = Some(postedData.entity_address),
              entityTownCity = Some(postedData.entity_town_city),
              entityPostCode = Some(postedData.entity_post_code),
              entityCountry = Some(postedData.entity_country),
              entityWebSite = Some(postedData.entity_web_site),
              services = Some(servicesString),
              Some(cc)
            )
          } yield createRegulatedEntityJson(entity)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createRegulatedEntity),
      "POST",
      "/regulated-entities",
      "Create Regulated Entity",
      s"""Create Regulated Entity
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      regulatedEntityPostJsonV510,
      regulatedEntityJsonV510,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagDirectory, apiTagApi),
      Some(List(canCreateRegulatedEntity)),
      http4sPartialFunction = Some(createRegulatedEntity)
    )

    val deleteRegulatedEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "regulated-entities" / regulatedEntityId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for { (deleted, _) <- deleteRegulatedEntityNewStyle(regulatedEntityId, Some(cc)) }
            yield Full(deleted)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteRegulatedEntity), "DELETE",
      "/regulated-entities/REGULATED_ENTITY_ID", "Delete Regulated Entity",
      s"""Delete Regulated Entity specified by REGULATED_ENTITY_ID
      |
      |${userAuthenticationMessage(true)}
      |""".stripMargin,
      EmptyBody, EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidConnectorResponse, UnknownError),
      List(apiTagDirectory, apiTagApi),
      Some(List(canDeleteRegulatedEntity)),
      http4sPartialFunction = Some(deleteRegulatedEntity)
    )

    // ─── log-cache×6 (single helper) ───────────────────────────────────────

    private def logCacheHandler(req: Request[IO], level: code.api.cache.RedisLogger.LogLevel.Value): IO[Response[IO]] =
      EndpointHelpers.executeFuture(req) {
        implicit val cc: code.api.util.CallContext = req.callContext
        for {
          httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
          (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
          limit = obpQueryParams.collectFirst { case OBPLimit(value) => value }
          offset = obpQueryParams.collectFirst { case OBPOffset(value) => value }
          logs <- Future(code.api.cache.RedisLogger.getLogTail(level, limit, offset))
        } yield logs
      }

    val logCacheTraceEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "log-cache" / "trace" =>
        logCacheHandler(req, code.api.cache.RedisLogger.LogLevel.TRACE)
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(logCacheTraceEndpoint),
      "GET",
      "/system/log-cache/trace",
      "Get Trace Level Log Cache",
      """Returns TRACE level logs from the system log cache.
      |
      |This endpoint supports pagination via the following optional query parameters:
      |* limit - Maximum number of log entries to return
      |* offset - Number of log entries to skip (for pagination)
      |
      |Example: GET /system/log-cache/trace?limit=50&offset=100
      """,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagSystem :: apiTagApi :: apiTagLogCache :: Nil,
      Some(List(canGetSystemLogCacheTrace, canGetSystemLogCacheAll)),
      http4sPartialFunction = Some(logCacheTraceEndpoint)
    )

    val logCacheDebugEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "log-cache" / "debug" =>
        logCacheHandler(req, code.api.cache.RedisLogger.LogLevel.DEBUG)
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(logCacheDebugEndpoint),
      "GET",
      "/system/log-cache/debug",
      "Get Debug Level Log Cache",
      """Returns DEBUG level logs from the system log cache.
      |
      |This endpoint supports pagination via the following optional query parameters:
      |* limit - Maximum number of log entries to return
      |* offset - Number of log entries to skip (for pagination)
      |
      |Example: GET /system/log-cache/debug?limit=50&offset=100
      """,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagSystem :: apiTagApi :: apiTagLogCache :: Nil,
      Some(List(canGetSystemLogCacheDebug, canGetSystemLogCacheAll)),
      http4sPartialFunction = Some(logCacheDebugEndpoint)
    )

    val logCacheInfoEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "log-cache" / "info" =>
        logCacheHandler(req, code.api.cache.RedisLogger.LogLevel.INFO)
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(logCacheInfoEndpoint),
      "GET",
      "/system/log-cache/info",
      "Get Info Level Log Cache",
      """Returns INFO level logs from the system log cache.
      |
      |This endpoint supports pagination via the following optional query parameters:
      |* limit - Maximum number of log entries to return
      |* offset - Number of log entries to skip (for pagination)
      |
      |Example: GET /system/log-cache/info?limit=50&offset=100
      """,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagSystem :: apiTagApi :: apiTagLogCache :: Nil,
      Some(List(canGetSystemLogCacheInfo, canGetSystemLogCacheAll)),
      http4sPartialFunction = Some(logCacheInfoEndpoint)
    )

    val logCacheWarningEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "log-cache" / "warning" =>
        logCacheHandler(req, code.api.cache.RedisLogger.LogLevel.WARNING)
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(logCacheWarningEndpoint),
      "GET",
      "/system/log-cache/warning",
      "Get Warning Level Log Cache",
      """Returns WARNING level logs from the system log cache.
      |
      |This endpoint supports pagination via the following optional query parameters:
      |* limit - Maximum number of log entries to return
      |* offset - Number of log entries to skip (for pagination)
      |
      |Example: GET /system/log-cache/warning?limit=50&offset=100
      """,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagSystem :: apiTagApi :: apiTagLogCache :: Nil,
      Some(List(canGetSystemLogCacheWarning, canGetSystemLogCacheAll)),
      http4sPartialFunction = Some(logCacheWarningEndpoint)
    )

    val logCacheErrorEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "log-cache" / "error" =>
        logCacheHandler(req, code.api.cache.RedisLogger.LogLevel.ERROR)
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(logCacheErrorEndpoint),
      "GET",
      "/system/log-cache/error",
      "Get Error Level Log Cache",
      """Returns ERROR level logs from the system log cache.
      |
      |This endpoint supports pagination via the following optional query parameters:
      |* limit - Maximum number of log entries to return
      |* offset - Number of log entries to skip (for pagination)
      |
      |Example: GET /system/log-cache/error?limit=50&offset=100
      """,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagSystem :: apiTagApi :: apiTagLogCache :: Nil,
      Some(List(canGetSystemLogCacheError, canGetSystemLogCacheAll)),
      http4sPartialFunction = Some(logCacheErrorEndpoint)
    )

    val logCacheAllEndpoint: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system" / "log-cache" / "all" =>
        logCacheHandler(req, code.api.cache.RedisLogger.LogLevel.ALL)
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(logCacheAllEndpoint),
      "GET",
      "/system/log-cache/all",
      "Get All Level Log Cache",
      """Returns logs of all levels from the system log cache.
      |
      |This endpoint supports pagination via the following optional query parameters:
      |* limit - Maximum number of log entries to return
      |* offset - Number of log entries to skip (for pagination)
      |
      |Example: GET /system/log-cache/all?limit=50&offset=100
      """,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      apiTagSystem :: apiTagApi :: apiTagLogCache :: Nil,
      Some(List(canGetSystemLogCacheAll)),
      http4sPartialFunction = Some(logCacheAllEndpoint)
    )

    val waitingForGodot: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "waiting-for-godot" =>
        EndpointHelpers.executeFuture(req) {
          val sleep = req.uri.query.params.get("sleep").getOrElse("0")
          val sleepInMillis: Long = scala.util.Try(sleep.trim.toLong).getOrElse(0L)
          for { _ <- Future(Thread.sleep(sleepInMillis)) }
            yield JSONFactory510.waitingForGodot(sleepInMillis)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(waitingForGodot),
      "GET",
      "/waiting-for-godot",
      "Waiting For Godot",
      """Waiting For Godot
      |
      |Uses query parameter "sleep" in milliseconds.
      |For instance: .../waiting-for-godot?sleep=50 means postpone response in 50 milliseconds.
      |""".stripMargin,
      EmptyBody,
      WaitingForGodotJsonV510(sleep_in_milliseconds = 50),
      List(UnknownError, MandatoryPropertyIsNotSet),
      apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(waitingForGodot)
    )

    val getAllApiCollections: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "api-collections" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for { (apiCollections, _) <- NewStyle.function.getAllApiCollections(Some(cc)) }
            yield JSONFactory400.createApiCollectionsJsonV400(apiCollections)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAllApiCollections),
      "GET",
      "/management/api-collections",
      "Get All API Collections",
      s"""Get All API Collections.
      |
      |${userAuthenticationMessage(true)}
      |""".stripMargin,
      EmptyBody,
      apiCollectionsJson400,
      List(UserHasMissingRoles, UnknownError),
      List(apiTagApiCollection),
      Some(canGetAllApiCollections :: Nil),
      http4sPartialFunction = Some(getAllApiCollections)
    )

    // ─── ATM attributes (5) ────────────────────────────────────────────────

    val createAtmAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "atms" / atmIdStr / "attributes" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val atmId = AtmId(atmIdStr)
          for {
            (_, _) <- NewStyle.function.getAtm(bankId, atmId, Some(cc))
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $AtmAttributeJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[AtmAttributeJsonV510]
            }
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${AtmAttributeType.DOUBLE}(12.1234), ${AtmAttributeType.STRING}(TAX_NUMBER), ${AtmAttributeType.INTEGER}(123) and ${AtmAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) { AtmAttributeType.withName(postedData.`type`) }
            (atmAttribute, _) <- NewStyle.function.createOrUpdateAtmAttribute(
              bankId, atmId, None, postedData.name, attrType, postedData.value, postedData.is_active, Some(cc))
          } yield JSONFactory510.createAtmAttributeJson(atmAttribute)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createAtmAttribute),
      "POST",
      "/banks/BANK_ID/atms/ATM_ID/attributes",
      "Create ATM Attribute",
      s""" Create ATM Attribute
      |
      |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      atmAttributeJsonV510,
      atmAttributeResponseJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
      List(apiTagATM, apiTagAtmAttribute, apiTagAttribute),
      Some(List(canCreateAtmAttribute, canCreateAtmAttributeAtAnyBank)),
      http4sPartialFunction = Some(createAtmAttribute)
    )

    val getAtmAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "atms" / atmIdStr / "attributes" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val atmId = AtmId(atmIdStr)
          for {
            (_, _) <- NewStyle.function.getAtm(bankId, atmId, Some(cc))
            (attributes, _) <- NewStyle.function.getAtmAttributesByAtm(bankId, atmId, Some(cc))
          } yield JSONFactory510.createAtmAttributesJson(attributes)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAtmAttributes),
      "GET",
      "/banks/BANK_ID/atms/ATM_ID/attributes",
      "Get ATM Attributes",
      s""" Get ATM Attributes
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      atmAttributesResponseJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
      List(apiTagATM, apiTagAtmAttribute, apiTagAttribute),
      Some(List(canGetAtmAttribute, canGetAtmAttributeAtAnyBank)),
      http4sPartialFunction = Some(getAtmAttributes)
    )

    val getAtmAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "atms" / atmIdStr / "attributes" / atmAttributeId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val atmId = AtmId(atmIdStr)
          for {
            (_, _) <- NewStyle.function.getAtm(bankId, atmId, Some(cc))
            (attribute, _) <- NewStyle.function.getAtmAttributeById(atmAttributeId, Some(cc))
          } yield JSONFactory510.createAtmAttributeJson(attribute)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAtmAttribute),
      "GET",
      "/banks/BANK_ID/atms/ATM_ID/attributes/ATM_ATTRIBUTE_ID",
      "Get ATM Attribute By ATM_ATTRIBUTE_ID",
      s""" Get ATM Attribute By ATM_ATTRIBUTE_ID
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      atmAttributeResponseJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, UnknownError),
      List(apiTagATM, apiTagAtmAttribute, apiTagAttribute),
      Some(List(canGetAtmAttribute, canGetAtmAttributeAtAnyBank)),
      http4sPartialFunction = Some(getAtmAttribute)
    )

    val updateAtmAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "atms" / atmIdStr / "attributes" / atmAttributeId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val atmId = AtmId(atmIdStr)
          for {
            (_, _) <- NewStyle.function.getAtm(bankId, atmId, Some(cc))
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $AtmAttributeJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[AtmAttributeJsonV510]
            }
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${AtmAttributeType.DOUBLE}(12.1234), ${AtmAttributeType.STRING}(TAX_NUMBER), ${AtmAttributeType.INTEGER}(123) and ${AtmAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) { AtmAttributeType.withName(postedData.`type`) }
            (_, _) <- NewStyle.function.getAtmAttributeById(atmAttributeId, Some(cc))
            (atmAttribute, _) <- NewStyle.function.createOrUpdateAtmAttribute(
              bankId, atmId, Some(atmAttributeId), postedData.name, attrType, postedData.value, postedData.is_active, Some(cc))
          } yield JSONFactory510.createAtmAttributeJson(atmAttribute)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateAtmAttribute),
      "PUT",
      "/banks/BANK_ID/atms/ATM_ID/attributes/ATM_ATTRIBUTE_ID",
      "Update ATM Attribute",
      s""" Update ATM Attribute.
      |
      |Update an ATM Attribute by its id.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      atmAttributeJsonV510,
      atmAttributeResponseJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagATM, apiTagAtmAttribute, apiTagAttribute),
      Some(List(canUpdateAtmAttribute, canUpdateAtmAttributeAtAnyBank)),
      http4sPartialFunction = Some(updateAtmAttribute)
    )

    val deleteAtmAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / bankIdStr / "atms" / atmIdStr / "attributes" / atmAttributeId =>
        EndpointHelpers.withUserAndBankDelete(req) { (_, _, cc) =>
          val bankId = BankId(bankIdStr); val atmId = AtmId(atmIdStr)
          for {
            (_, _) <- NewStyle.function.getAtm(bankId, atmId, Some(cc))
            (deleted, _) <- NewStyle.function.deleteAtmAttribute(atmAttributeId, Some(cc))
          } yield deleted
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteAtmAttribute),
      "DELETE",
      "/banks/BANK_ID/atms/ATM_ID/attributes/ATM_ATTRIBUTE_ID",
      "Delete ATM Attribute",
      s""" Delete ATM Attribute
      |
      |Delete a Atm Attribute by its id.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagATM, apiTagAtmAttribute, apiTagAttribute),
      Some(List(canDeleteAtmAttribute, canDeleteAtmAttributeAtAnyBank)),
      http4sPartialFunction = Some(deleteAtmAttribute)
    )

    // ─── Agents (4) ─────────────────────────────────────────────────────────

    val createAgent: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "agents" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            putData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostAgentJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PostAgentJsonV510]
            }
            (available, _) <- NewStyle.function.checkAgentNumberAvailable(bankId, putData.agent_number, Some(cc))
            _ <- Helper.booleanToFuture(s"$AgentNumberAlreadyExists Current agent_number(${putData.agent_number}) and Current bank_id(${bankId.value})", cc = Some(cc)) { available }
            (agent, _) <- NewStyle.function.createAgent(bankId.value, putData.legal_name, putData.mobile_phone_number, putData.agent_number, Some(cc))
            (bankAccount, _) <- NewStyle.function.createBankAccount(
              bankId, AccountId(APIUtil.generateUUID()), "AGENT", "AGENT",
              putData.currency, 0, putData.legal_name, null, Nil, Some(cc))
            _ <- NewStyle.function.createAgentAccountLink(agent.agentId, bankAccount.bankId.value, bankAccount.accountId.value, Some(cc))
          } yield JSONFactory510.createAgentJson(agent, bankAccount)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createAgent),
      "POST",
      "/banks/BANK_ID/agents",
      "Create Agent",
      s"""
      |${userAuthenticationMessage(true)}
      |""",
      postAgentJsonV510,
      agentJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, AgentNumberAlreadyExists, CreateAgentError, UnknownError),
      List(apiTagCustomer, apiTagPerson),
      None,
      http4sPartialFunction = Some(createAgent)
    )

    val updateAgentStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "agents" / agentId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostAgentJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PutAgentJsonV510]
            }
            (_, _) <- NewStyle.function.getAgentByAgentId(agentId, Some(cc))
            (links, _) <- NewStyle.function.getAgentAccountLinksByAgentId(agentId, Some(cc))
            link <- NewStyle.function.tryons(AgentAccountLinkNotFound, 400, Some(cc)) { links.head }
            (bankAccount, _) <- NewStyle.function.getBankAccount(BankId(link.bankId), AccountId(link.accountId), Some(cc))
            (agent, _) <- NewStyle.function.updateAgentStatus(agentId, postedData.is_pending_agent, postedData.is_confirmed_agent, Some(cc))
          } yield JSONFactory510.createAgentJson(agent, bankAccount)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateAgentStatus),
      "PUT",
      "/banks/BANK_ID/agents/AGENT_ID",
      "Update Agent status",
      s"""
      |${userAuthenticationMessage(true)}
      |""",
      putAgentJsonV510,
      agentJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, AgentNotFound, AgentAccountLinkNotFound, UnknownError),
      List(apiTagCustomer, apiTagPerson),
      Some(canUpdateAgentStatusAtAnyBank :: canUpdateAgentStatusAtOneBank :: Nil),
      http4sPartialFunction = Some(updateAgentStatus)
    )

    val getAgent: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "agents" / agentId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (agent, _) <- NewStyle.function.getAgentByAgentId(agentId, Some(cc))
            (links, _) <- NewStyle.function.getAgentAccountLinksByAgentId(agentId, Some(cc))
            link <- NewStyle.function.tryons(AgentAccountLinkNotFound, 400, Some(cc)) { links.head }
            (bankAccount, _) <- NewStyle.function.getBankAccount(BankId(link.bankId), AccountId(link.accountId), Some(cc))
          } yield JSONFactory510.createAgentJson(agent, bankAccount)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAgent),
      "GET",
      "/banks/BANK_ID/agents/AGENT_ID",
      "Get Agent",
      s"""Get Agent.
      |
      |${userAuthenticationMessage(true)}
      |""".stripMargin,
      EmptyBody,
      agentJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, AgentNotFound, AgentAccountLinkNotFound, UnknownError),
      List(apiTagAccount),
      None,
      http4sPartialFunction = Some(getAgent)
    )

    val getAgents: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "agents" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            (requestParams, _) <- NewStyle.function.extractQueryParams(req.uri.renderString, List("limit", "offset", "sort_direction"), Some(cc))
            (agents, _) <- NewStyle.function.getAgents(bankId.value, requestParams, Some(cc))
          } yield JSONFactory510.createMinimalAgentsJson(agents)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAgents),
      "GET",
      "/banks/BANK_ID/agents",
      "Get Agents at Bank",
      s"""Get Agents at Bank.
      |
      |${userAuthenticationMessage(false)}
      |
      |${urlParametersDocument(true, true)}
      |""".stripMargin,
      EmptyBody,
      minimalAgentsJsonV510,
      List($BankNotFound, AgentsNotFound, UnknownError),
      List(apiTagAccount),
      None,
      http4sPartialFunction = Some(getAgents)
    )

    // ─── Regulated entity attributes (5) ───────────────────────────────────

    val createRegulatedEntityAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "regulated-entities" / entityIdStr / "attributes" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $RegulatedEntityAttributeRequestJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[RegulatedEntityAttributeRequestJsonV510]
            }
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${RegulatedEntityAttributeType.DOUBLE}(12.1234), ${RegulatedEntityAttributeType.STRING}(TAX_NUMBER), ${RegulatedEntityAttributeType.INTEGER}(123) and ${RegulatedEntityAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) { RegulatedEntityAttributeType.withName(postedData.attribute_type) }
            (attribute, _) <- RegulatedEntityAttributeNewStyle.createOrUpdateRegulatedEntityAttribute(
              regulatedEntityId = RegulatedEntityId(entityIdStr),
              regulatedEntityAttributeId = None,
              name = postedData.name, attributeType = attrType,
              value = postedData.value, isActive = postedData.is_active,
              callContext = Some(cc))
          } yield JSONFactory510.createRegulatedEntityAttributeJson(attribute)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createRegulatedEntityAttribute),
      "POST",
      "/regulated-entities/REGULATED_ENTITY_ID/attributes",
      "Create Regulated Entity Attribute",
      s"""
          | Create a new Regulated Entity Attribute for a given REGULATED_ENTITY_ID.
          |
          | The type field must be one of "STRING", "INTEGER", "DOUBLE" or "DATE_WITH_DAY".
          | ${userAuthenticationMessage(true)}
          |
      """.stripMargin,
      regulatedEntityAttributeRequestJsonV510,
      regulatedEntityAttributeResponseJsonV510,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagDirectory, apiTagApi),
      Some(List(canCreateRegulatedEntityAttribute)),
      http4sPartialFunction = Some(createRegulatedEntityAttribute)
    )

    val deleteRegulatedEntityAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "regulated-entities" / entityIdStr / "attributes" / attributeId =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          for {
            (_, _) <- getRegulatedEntityByEntityIdNewStyle(entityIdStr, Some(cc))
            (deleted, _) <- RegulatedEntityAttributeNewStyle.deleteRegulatedEntityAttribute(attributeId, Some(cc))
          } yield deleted
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteRegulatedEntityAttribute),
      "DELETE",
      "/regulated-entities/REGULATED_ENTITY_ID/attributes/REGULATED_ENTITY_ATTRIBUTE_ID",
      "Delete Regulated Entity Attribute",
      s"""
          | Delete a Regulated Entity Attribute specified by REGULATED_ENTITY_ATTRIBUTE_ID.
          |
          | ${userAuthenticationMessage(true)}
          |
      """.stripMargin,
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UnknownError),
      List(apiTagDirectory, apiTagApi),
      Some(List(canDeleteRegulatedEntityAttribute)),
      http4sPartialFunction = Some(deleteRegulatedEntityAttribute)
    )

    val getRegulatedEntityAttributeById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "regulated-entities" / entityIdStr / "attributes" / attributeId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (_, _) <- getRegulatedEntityByEntityIdNewStyle(entityIdStr, Some(cc))
            (attribute, _) <- RegulatedEntityAttributeNewStyle.getRegulatedEntityAttributeById(attributeId, Some(cc))
          } yield JSONFactory510.createRegulatedEntityAttributeJson(attribute)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getRegulatedEntityAttributeById),
      "GET",
      "/regulated-entities/REGULATED_ENTITY_ID/attributes/REGULATED_ENTITY_ATTRIBUTE_ID",
      "Get Regulated Entity Attribute By ID",
      s"""
          | Get a specific Regulated Entity Attribute by its REGULATED_ENTITY_ATTRIBUTE_ID.
          |
          | ${userAuthenticationMessage(true)}
          |
      """.stripMargin,
      EmptyBody,
      regulatedEntityAttributeResponseJsonV510,
      List($AuthenticatedUserIsRequired, UnknownError),
      List(apiTagDirectory, apiTagApi),
      Some(List(canGetRegulatedEntityAttribute)),
      http4sPartialFunction = Some(getRegulatedEntityAttributeById)
    )

    val getAllRegulatedEntityAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "regulated-entities" / entityIdStr / "attributes" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val entityId = RegulatedEntityId(entityIdStr)
          for {
            (_, _) <- getRegulatedEntityByEntityIdNewStyle(entityIdStr, Some(cc))
            (attributes, _) <- RegulatedEntityAttributeNewStyle.getRegulatedEntityAttributes(entityId, Some(cc))
          } yield JSONFactory510.createRegulatedEntityAttributesJson(attributes)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAllRegulatedEntityAttributes),
      "GET",
      "/regulated-entities/REGULATED_ENTITY_ID/attributes",
      "Get All Regulated Entity Attributes",
      s"""
          | Get all attributes for the specified Regulated Entity.
          |
          | ${userAuthenticationMessage(true)}
          |
      """.stripMargin,
      EmptyBody,
      regulatedEntityAttributesJsonV510,
      List($AuthenticatedUserIsRequired, UnknownError),
      List(apiTagDirectory, apiTagApi),
      Some(List(canGetRegulatedEntityAttributes)),
      http4sPartialFunction = Some(getAllRegulatedEntityAttributes)
    )

    val updateRegulatedEntityAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "regulated-entities" / entityIdStr / "attributes" / attributeId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $RegulatedEntityAttributeRequestJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[RegulatedEntityAttributeRequestJsonV510]
            }
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${RegulatedEntityAttributeType.DOUBLE}(12.1234), ${RegulatedEntityAttributeType.STRING}(TAX_NUMBER), ${RegulatedEntityAttributeType.INTEGER}(123) and ${RegulatedEntityAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) { RegulatedEntityAttributeType.withName(postedData.attribute_type) }
            (_, _) <- getRegulatedEntityByEntityIdNewStyle(entityIdStr, Some(cc))
            (updated, _) <- RegulatedEntityAttributeNewStyle.createOrUpdateRegulatedEntityAttribute(
              regulatedEntityId = RegulatedEntityId(entityIdStr),
              regulatedEntityAttributeId = Some(attributeId),
              name = postedData.name, attributeType = attrType,
              value = postedData.value, isActive = postedData.is_active,
              callContext = Some(cc))
          } yield JSONFactory510.createRegulatedEntityAttributeJson(updated)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateRegulatedEntityAttribute),
      "PUT",
      "/regulated-entities/REGULATED_ENTITY_ID/attributes/REGULATED_ENTITY_ATTRIBUTE_ID",
      "Update Regulated Entity Attribute",
      s"""
          | Update an existing Regulated Entity Attribute specified by ATTRIBUTE_ID.
          |
          | ${userAuthenticationMessage(true)}
          |
      """.stripMargin,
      regulatedEntityAttributeRequestJsonV510,
      regulatedEntityAttributeResponseJsonV510,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagDirectory, apiTagApi),
      Some(List(canUpdateRegulatedEntityAttribute)),
      http4sPartialFunction = Some(updateRegulatedEntityAttribute)
    )

    // ─── mtls / api-collection / api-tags / metrics / webui-props (5) ─────

    val mtlsClientCertificateInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "mtls" / "certificate" / "current" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            info <- Future(X509.getCertificateInfo(APIUtil.`getPSD2-CERT`(cc.requestHeaders)))
              .map(unboxFullOrFail(_, Some(cc), X509GeneralError))
          } yield info
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(mtlsClientCertificateInfo),
      "GET",
      "/my/mtls/certificate/current",
      "Provide client's certificate info of a current call",
      s"""
         |Provide client's certificate info of a current call specified by PSD2-CERT value at Request Header
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      certificateInfoJsonV510,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(mtlsClientCertificateInfo)
    )

    val updateMyApiCollection: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "my" / "api-collections" / apiCollectionId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[code.api.v4_0_0.PostApiCollectionJson400].getSimpleName}", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[code.api.v4_0_0.PostApiCollectionJson400]
            }
            (_, _) <- NewStyle.function.getApiCollectionById(apiCollectionId, Some(cc))
            (apiCollection, _) <- NewStyle.function.updateApiCollection(
              apiCollectionId, putJson.api_collection_name, putJson.is_sharable, putJson.description.getOrElse(""), Some(cc))
          } yield JSONFactory400.createApiCollectionJsonV400(apiCollection)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateMyApiCollection),
      "PUT",
      "/my/api-collections/API_COLLECTION_ID",
      "Update My Api Collection By API_COLLECTION_ID",
      s"""Update Api Collection for logged in user.
      |
      |${userAuthenticationMessage(true)}
      |""".stripMargin,
      postApiCollectionJson400,
      apiCollectionJson400,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, UserNotFoundByUserId, UnknownError),
      List(apiTagApiCollection),
      None,
      http4sPartialFunction = Some(updateMyApiCollection)
    )

    val getApiTags: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "tags" =>
        EndpointHelpers.executeFuture(req) {
          Future.successful(code.api.v5_1_0.APITags(code.api.util.ApiTag.allDisplayTagNames.toList))
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getApiTags),
      "GET",
      "/tags",
      "Get API Tags",
      s"""Get API TagsGet API Tags
      |
      |${userAuthenticationMessage(false)}
      |
      |""",
      EmptyBody,
      accountsMinimalJson400,
      List(UnknownError),
      List(apiTagApi),
      None,
      http4sPartialFunction = Some(getApiTags)
    )

    val getMetrics: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "metrics" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            metrics <- Future(APIMetrics.apiMetrics.vend.getAllMetrics(obpQueryParams))
          } yield JSONFactory510.createMetricsJson(metrics)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getMetrics),
      "GET",
      "/management/metrics",
      "Get Metrics",
      s"""Get API metrics rows. These are records of each REST API call.
         |
         |require CanReadMetrics role
         |
         |**IMPORTANT: Smart Caching & Performance**
         |
         |This endpoint uses intelligent two-tier caching to optimize performance:
         |
         |**Stable Data Cache (Long TTL):**
         |- Metrics older than ${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600")} seconds (${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt / 60} minutes) are considered immutable/stable
         |- These are cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400")} seconds (${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt / 3600} hours)
         |- Used when your query's from_date is older than the stable boundary
         |
         |**Recent Data Cache (Short TTL):**
         |- Recent metrics (within the stable boundary) are cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds
         |- Used when your query includes recent data or has no from_date
         |
         |**STRONGLY RECOMMENDED: Always specify from_date in your queries!**
         |
         |**Why from_date matters:**
         |- Queries WITH from_date older than ${APIUtil.getPropsValue("MappedMetrics.stable.boundary.seconds", "600").toInt / 60} mins → cached for ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt / 3600} hours (fast!)
         |- Queries WITHOUT from_date → cached for only ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds (slower)
         |
         |**Examples:**
         |- `from_date=2025-01-01T00:00:00.000Z` → Uses ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getStableMetrics", "86400").toInt / 3600} hours cache (historical data)
         |- `from_date=$DateWithMsExampleString` (recent date) → Uses ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds cache (recent data)
         |- No from_date (e.g., `?limit=50`) → Uses ${APIUtil.getPropsValue("MappedMetrics.cache.ttl.seconds.getAllMetrics", "7")} seconds cache (assumes recent data)
         |
         |For best performance on historical/reporting queries, always include a from_date parameter!
         |
         |Filters Part 1.*filtering* (no wilde cards etc.) parameters to GET /management/metrics
         |
         |You can filter by the following fields by applying url parameters
         |
         |eg: /management/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=50&offset=2
         |
         |1 from_date e.g.:from_date=$DateWithMsExampleString Defaults to the Unix Epoch i.e. ${theEpochTime}
         |   **IMPORTANT**: Including from_date enables long-term caching for historical data queries!
         |
         |2 to_date e.g.:to_date=$DateWithMsExampleString Defaults to a far future date i.e. ${APIUtil.ToDateInFuture}
         |
         |3 limit (for pagination: defaults to 50)  eg:limit=200
         |
         |4 offset (for pagination: zero index, defaults to 0) eg: offset=10
         |
         |5 sort_by (defaults to date field) eg: sort_by=date
         |  possible values:
         |    "url",
         |    "date",
         |    "username" (or "user_name" for backward compatibility),
         |    "app_name",
         |    "developer_email",
         |    "implemented_by_partial_function",
         |    "implemented_in_version",
         |    "consumer_id",
         |    "verb",
         |    "http_status_code"
         |
         |6 direction (defaults to date desc) eg: direction=desc
         |
         |eg: /management/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=10000&offset=0&anon=false&app_name=TeatApp&implemented_in_version=v2.1.0&verb=POST&user_id=c7b6cb47-cb96-4441-8801-35b57456753a&username=susan.uk.29@example.com&consumer_id=78
         |
         |Other filters:
         |
         |7 consumer_id  (if null ignore)
         |
         |8 user_id (if null ignore)
         |
         |9 anon (if null ignore) only support two value : true (return where user_id is null.) or false (return where user_id is not null.)
         |
         |10 url (if null ignore), note: can not contain '&'.
         |
         |11 app_name (if null ignore)
         |
         |12 implemented_by_partial_function (if null ignore),
         |
         |13 implemented_in_version (if null ignore)
         |
         |14 verb (if null ignore)
         |
         |15 correlation_id (if null ignore)
         |
         |16 duration (if null ignore) - Returns calls where duration > specified value (in milliseconds). Use this to find slow API calls. eg: duration=5000 returns calls taking more than 5 seconds
         |
         |17 http_status_code (if null ignore) - Returns calls with specific HTTP status code. eg: http_status_code=200 returns only successful calls, http_status_code=500 returns server errors
         |
      """.stripMargin,
      EmptyBody,
      metricsJsonV510,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagMetric, apiTagApi),
      Some(List(canReadMetrics)),
      http4sPartialFunction = Some(getMetrics)
    )

    val getWebUiProps: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "webui-props" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val active = req.uri.query.params.get("active").getOrElse("false")
          for {
            invalidMsg <- Future.successful(s"$InvalidFilterParameterFormat `active` must be a boolean, but current `active` value is: $active ")
            isActive <- NewStyle.function.tryons(invalidMsg, 400, Some(cc)) { active.toBoolean }
            explicitWebUiProps <- Future { MappedWebUiPropsProvider.getAll() }
            implicitDeduped = if (isActive) {
              val implicitProps = APIUtil.getWebUIPropsPairs.map(p => WebUiPropsCommons(p._1, p._2, webUiPropsId = Some("default")))
              if (explicitWebUiProps.nonEmpty) {
                val dups: List[WebUiPropsCommons] = explicitWebUiProps.flatMap(e => implicitProps.filter(_.name == e.name))
                implicitProps diff dups
              } else implicitProps.distinct
            } else List.empty[WebUiPropsCommons]
          } yield ListResult("webui_props", explicitWebUiProps ++ implicitDeduped)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getWebUiProps), "GET",
      "/webui-props", "Get WebUiProps",
      s"""
      |
      |Get the all WebUiProps key values, those props key with "webui_" can be stored in DB, this endpoint get all from DB.
      |
      |url query parameter:
      |active: It must be a boolean string. and If active = true, it will show
      |          combination of explicit (inserted) + implicit (default)  method_routings.
      |
      |eg:
      |${getObpApiRoot}/v5.1.0/webui-props
      |${getObpApiRoot}/v5.1.0/webui-props?active=true
      |
      |""",
      EmptyBody,
      ListResult(
        "webui-props",
        (List(WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com", Some("web-ui-props-id"))))
      ),
      List(UserHasMissingRoles, UnknownError),
      List(apiTagWebUiProps),
      None,
      http4sPartialFunction = Some(getWebUiProps)
    )

    // ─── Non-personal user attributes (3) ─────────────────────────────────

    val createNonPersonalUserAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / userId / "non-personal" / "attributes" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (user, _) <- NewStyle.function.getUserByUserId(userId, Some(cc))
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $UserAttributeJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[UserAttributeJsonV510]
            }
            attrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${UserAttributeType.DOUBLE}(12.1234), ${UserAttributeType.STRING}(TAX_NUMBER), ${UserAttributeType.INTEGER} (123)and ${UserAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) { UserAttributeType.withName(postedData.`type`) }
            (userAttribute, _) <- NewStyle.function.createOrUpdateUserAttribute(
              user.userId, None, postedData.name, attrType, postedData.value, false, Some(cc))
          } yield JSONFactory510.createUserAttributeJson(userAttribute)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createNonPersonalUserAttribute),
      "POST",
      "/users/USER_ID/non-personal/attributes",
      "Create Non Personal User Attribute",
      s""" Create Non Personal User Attribute
      |
      |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      userAttributeJsonV510,
      userAttributeResponseJsonV510,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagUser),
      Some(List(canCreateNonPersonalUserAttribute)),
      http4sPartialFunction = Some(createNonPersonalUserAttribute)
    )

    val deleteNonPersonalUserAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "users" / userId / "non-personal" / "attributes" / userAttributeId =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          for {
            (_, _) <- NewStyle.function.getUserByUserId(userId, Some(cc))
            (deleted, _) <- Connector.connector.vend.deleteUserAttribute(userAttributeId, Some(cc))
              .map(i => (connectorEmptyResponse(i._1, Some(cc)), i._2))
          } yield deleted
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteNonPersonalUserAttribute), "DELETE",
      "/users/USER_ID/non-personal/attributes/USER_ATTRIBUTE_ID", "Delete Non Personal User Attribute",
      s"""Delete the Non Personal User Attribute specified by ENTITLEMENT_REQUEST_ID for a user specified by USER_ID
      |
      |${userAuthenticationMessage(true)}
      |""".stripMargin,
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidConnectorResponse, UnknownError),
      List(apiTagUser),
      Some(List(canDeleteNonPersonalUserAttribute)),
      http4sPartialFunction = Some(deleteNonPersonalUserAttribute)
    )

    val getNonPersonalUserAttributes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userId / "non-personal" / "attributes" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (user, _) <- NewStyle.function.getUserByUserId(userId, Some(cc))
            (userAttributes, _) <- NewStyle.function.getNonPersonalUserAttributes(user.userId, Some(cc))
          } yield JSONFactory510.createUserAttributesJson(userAttributes)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getNonPersonalUserAttributes), "GET",
      "/users/USER_ID/non-personal/attributes", "Get Non Personal User Attributes",
      s"""Get Non Personal User Attribute for a user specified by USER_ID
      |
      |${userAuthenticationMessage(true)}
      |""".stripMargin,
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidConnectorResponse, UnknownError),
      List(apiTagUser),
      Some(List(canGetNonPersonalUserAttributes)),
      http4sPartialFunction = Some(getNonPersonalUserAttributes)
    )

    // ─── User / lock / sync (8) ───────────────────────────────────────────

    val syncExternalUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / provider / providerId / "sync" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (user, _) <- NewStyle.function.getOrCreateResourceUser(provider, providerId, Some(cc))
            _ <- AuthUser.refreshUser(user, Some(cc))
          } yield JSONFactory510.getSyncedUser(user)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(syncExternalUser),
      "POST",
      "/users/PROVIDER/PROVIDER_ID/sync",
      "Sync User",
      s"""The endpoint is used to create or sync an OBP User with User from an external identity provider.
      |PROVIDER is the host of the provider e.g. a Keycloak Host.
      |PROVIDER_ID is the unique identifier for the User at the PROVIDER.
      |At the end of the process, a User will exist in OBP with the Account Access records defined by the CBS.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      refresUserJson,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canSyncUser)),
      http4sPartialFunction = Some(syncExternalUser)
    )

    val getEntitlementsAndPermissions: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userId / "entitlements-and-permissions" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (user, _) <- NewStyle.function.getUserByUserId(userId, Some(cc))
            entitlements <- NewStyle.function.getEntitlementsByUserId(userId, Some(cc))
          } yield {
            val permissions: Option[com.openbankproject.commons.model.Permission] =
              Views.views.vend.getPermissionForUser(user).toOption
            JSONFactory300.createUserInfoJSON(user, entitlements, permissions)
          }
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getEntitlementsAndPermissions),
      "GET",
      "/users/USER_ID/entitlements-and-permissions",
      "Get Entitlements and Permissions for a User",
      s"""
         |
         |
      """.stripMargin,
      EmptyBody,
      userJsonV300,
      List($AuthenticatedUserIsRequired, UserNotFoundByUserId, UserHasMissingRoles, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagUser),
      Some(List(canGetEntitlementsForAnyUserAtAnyBank)),
      http4sPartialFunction = Some(getEntitlementsAndPermissions)
    )

    val getUserByProviderAndUsername: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "provider" / provider / "username" / username =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            user <- Users.users.vend.getUserByProviderAndUsernameFuture(URLDecoder.decode(provider, StandardCharsets.UTF_8), username)
              .map(x => unboxFullOrFail(x, Some(cc), UserNotFoundByProviderAndUsername, 404))
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, Some(cc))
            isLocked = LoginAttempt.userIsLocked(user.provider, user.name)
            authUser = AuthUser.find(By(AuthUser.user, user.userPrimaryKey.value))
          } yield JSONFactory510.createUserWithNamesJSON(
            user,
            authUser.map(_.firstName.get).getOrElse(""),
            authUser.map(_.lastName.get).getOrElse(""),
            entitlements, None, isLocked
          )
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getUserByProviderAndUsername),
      "GET",
      "/users/provider/PROVIDER/username/USERNAME",
      "Get User by USERNAME",
      s"""Get user by PROVIDER and USERNAME
         |
         |Get a User by their authentication provider and username.
         |
         |**URL Parameters:**
         |
         |* PROVIDER - The authentication provider (e.g., http://127.0.0.1:8080, google.com, OBP)
         |* USERNAME - The username at that provider (e.g., obpstripe, john.doe)
         |
         |**Important:** The PROVIDER parameter can contain special characters like slashes and colons.
         |For example, if the provider is "http://127.0.0.1:8080", the full URL would be:
         |
         |`GET /obp/v5.1.0/users/provider/http://127.0.0.1:8080/username/obpstripe`
         |
         |The API will correctly parse the provider value even with these special characters.
         |
         |**To find valid providers**, use the GET /obp/v6.0.0/providers endpoint (available in API version 6.0.0).
         |
         |${userAuthenticationMessage(true)}
         |
         |CanGetAnyUser entitlement is required.
         |
      """.stripMargin,
      EmptyBody,
      userWithNamesJsonV510,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UserNotFoundByProviderAndUsername, UnknownError),
      List(apiTagUser),
      Some(List(canGetAnyUser)),
      http4sPartialFunction = Some(getUserByProviderAndUsername)
    )

    val getUserLockStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / provider / username / "lock-status" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            _ <- Users.users.vend.getUserByProviderAndUsernameFuture(provider, username)
              .map(x => unboxFullOrFail(x, Some(cc), UserNotFoundByProviderAndUsername, 404))
            badLoginStatus <- Future(LoginAttempt.getOrCreateBadLoginStatus(provider, username))
              .map(unboxFullOrFail(_, Some(cc), s"$UserNotFoundByProviderAndUsername provider($provider), username($username)", 404))
          } yield createBadLoginStatusJson(badLoginStatus)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getUserLockStatus), "GET",
      "/users/PROVIDER/USERNAME/lock-status", "Get User Lock Status",
      s"""
      |Get User Login Status.
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      EmptyBody, badLoginStatusJson,
      List(AuthenticatedUserIsRequired, UserNotFoundByProviderAndUsername, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canReadUserLockedStatus)),
      http4sPartialFunction = Some(getUserLockStatus)
    )

    val unlockUserByProviderAndUsername: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "users" / provider / username / "lock-status" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            _ <- Users.users.vend.getUserByProviderAndUsernameFuture(provider, username)
              .map(x => unboxFullOrFail(x, Some(cc), UserNotFoundByProviderAndUsername, 404))
            _ <- Future(LoginAttempt.resetBadLoginAttempts(provider, username))
            _ <- Future(UserLocksProvider.unlockUser(provider, username))
            badLoginStatus <- Future(LoginAttempt.getOrCreateBadLoginStatus(provider, username))
              .map(unboxFullOrFail(_, Some(cc), s"$UserNotFoundByProviderAndUsername provider($provider), username($username)", 404))
          } yield createBadLoginStatusJson(badLoginStatus)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(unlockUserByProviderAndUsername), "PUT",
      "/users/PROVIDER/USERNAME/lock-status", "Unlock the user",
      s"""
      |Unlock a User.
      |
      |(Perhaps the user was locked due to multiple failed login attempts)
      |
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      EmptyBody, badLoginStatusJson,
      List(AuthenticatedUserIsRequired, UserNotFoundByProviderAndUsername, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canUnlockUser)),
      http4sPartialFunction = Some(unlockUserByProviderAndUsername)
    )

    val lockUserByProviderAndUsername: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / provider / username / "locks" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            userLocks <- Future(UserLocksProvider.lockUser(provider, username))
              .map(unboxFullOrFail(_, Some(cc), s"$UserNotFoundByProviderAndUsername provider($provider), username($username)", 404))
          } yield JSONFactory400.createUserLockStatusJson(userLocks)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(lockUserByProviderAndUsername),
      "POST",
      "/users/PROVIDER/USERNAME/locks",
      "Lock the user",
      s"""
      |Lock a User.
      |
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      EmptyBody,
      userLockStatusJson,
      List($AuthenticatedUserIsRequired, UserNotFoundByProviderAndUsername, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canLockUser)),
      http4sPartialFunction = Some(lockUserByProviderAndUsername)
    )

    val validateUserByUserId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "users" / userId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (user, _) <- NewStyle.function.findByUserId(userId, Some(cc))
            (userValidated, _) <- NewStyle.function.validateUser(user.userPrimaryKey, Some(cc))
          } yield UserValidatedJson(userValidated.validated.get)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(validateUserByUserId),
      "PUT",
      "/management/users/USER_ID",
      "Validate a user",
      s"""
      |Manually validate a User by USER_ID.
      |
      |This is an administrative endpoint that marks a user's account as validated (i.e. sets is_validated to true).
      |
      |This is useful when an administrator needs to validate a user on their behalf,
      |for example if the user did not receive the validation email, or if the email validation token has expired.
      |
      |For self-service email validation, see the Validate User Email endpoint (POST /users/email-validation).
      |
      |Authentication is Required and the user must have the canValidateUser role.
      |
      |""".stripMargin,
      EmptyBody,
      UserValidatedJson(is_validated = true),
      List($AuthenticatedUserIsRequired, UserNotFoundByUserId, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canValidateUser)),
      http4sPartialFunction = Some(validateUserByUserId)
    )

    val getAccountAccessByUserId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userId / "account-access" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (user, _) <- NewStyle.function.getUserByUserId(userId, Some(cc))
            (_, accountAccess) <- Future(Views.views.vend.privateViewsUserCanAccess(user))
          } yield JSONFactory400.createAccountsMinimalJson400(accountAccess)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAccountAccessByUserId),
      "GET",
      "/users/USER_ID/account-access",
      "Get Account Access by USER_ID",
      s"""Get Account Access by USER_ID
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      accountsMinimalJson400,
      List($AuthenticatedUserIsRequired, UserNotFoundByUserId, UnknownError),
      List(apiTagAccount),
      Some(List(canSeeAccountAccessForAnyUser)),
      http4sPartialFunction = Some(getAccountAccessByUserId)
    )

    // ─── Accounts-held (2) ────────────────────────────────────────────────
    // Lift's AccountsHelper.getFilteredCoreAccounts takes a `Req`; ported
    // inline here against http4s' multiParams. Filter shape mirrors
    // AccountsHelper.filterWithAccountType (v2_0_0/AccountsHelper.scala:39).

    private def filteredCoreAccountsByQueryParams(
      bankIdAccountIds: List[BankIdAccountId],
      params: Map[String, Seq[String]],
      cc: code.api.util.CallContext
    ): Future[List[com.openbankproject.commons.model.CoreAccount]] = {
      val filters: List[String] =
        params.get("account_type_filter").map(_.toList.flatMap(_.split(","))).getOrElse(Nil)
      val filtersOperation: String =
        params.get("account_type_filter_operation").flatMap(_.headOption).getOrElse("INCLUDE")
      val failMsg = s"${ErrorMessages.InvalidFilterParameterFormat}request parameter " +
        s"account_type_filter_operation must be either INCLUDE or EXCLUDE, current it is: $filtersOperation "
      unboxFullOrFail(tryo {
        assume(filtersOperation == "INCLUDE" || filtersOperation == "EXCLUDE")
      }, Some(cc), failMsg)
      NewStyle.function.getCoreBankAccountsFuture(bankIdAccountIds, Some(cc)).map { case (coreAccounts, _) =>
        coreAccounts.filter { account =>
          (filters, filtersOperation) match {
            case (f, "INCLUDE") if f.nonEmpty => filters.contains(account.accountType)
            case (f, "EXCLUDE") if f.nonEmpty => !filters.contains(account.accountType)
            case _                            => true
          }
        }
      }
    }

    val getAccountsHeldByUserAtBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userId / "banks" / bankIdStr / "accounts-held" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            (u, _) <- NewStyle.function.getUserByUserId(userId, Some(cc))
            (availableAccounts, _) <- NewStyle.function.getAccountsHeld(bankId, u, Some(cc))
            (accounts, _) <- NewStyle.function.getBankAccountsHeldFuture(availableAccounts.toList, Some(cc))
            filteredCore <- Implementations5_1_0.filteredCoreAccountsByQueryParams(availableAccounts.toList, req.uri.query.multiParams, cc)
            coreIds = filteredCore.map(_.id)
            accountHelds = accounts.filter(a => coreIds.contains(a.id))
          } yield JSONFactory300.createCoreAccountsByCoreAccountsJSON(accountHelds)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAccountsHeldByUserAtBank),
      "GET",
      "/users/USER_ID/banks/BANK_ID/accounts-held",
      "Get Accounts Held By User",
      s"""Get Accounts held by the User if even the User has not been assigned the owner View yet.
       |
       |Can be used to onboard the account to the API - since all other account and transaction endpoints require views to be assigned.
       |
       |${accountTypeFilterText("/users/USER_ID/banks/BANK_ID/accounts-held")}
       |
       |
       |
      """.stripMargin,
      EmptyBody,
      coreAccountsHeldJsonV300,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserNotFoundByUserId, UnknownError),
      List(apiTagAccount),
      Some(List(canGetAccountsHeldAtOneBank, canGetAccountsHeldAtAnyBank)),
      http4sPartialFunction = Some(getAccountsHeldByUserAtBank)
    )

    val getAccountsHeldByUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userId / "accounts-held" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (u, _) <- NewStyle.function.getUserByUserId(userId, Some(cc))
            (availableAccounts, _) <- NewStyle.function.getAccountsHeldByUser(u, Some(cc))
            (accounts, _) <- NewStyle.function.getBankAccountsHeldFuture(availableAccounts, Some(cc))
            filteredCore <- Implementations5_1_0.filteredCoreAccountsByQueryParams(availableAccounts, req.uri.query.multiParams, cc)
            coreIds = filteredCore.map(_.id)
            accountHelds = accounts.filter(a => coreIds.contains(a.id))
          } yield JSONFactory300.createCoreAccountsByCoreAccountsJSON(accountHelds)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAccountsHeldByUser),
      "GET",
      "/users/USER_ID/accounts-held",
      "Get Accounts Held By User",
      s"""Get Accounts held by the User if even the User has not been assigned the owner View yet.
       |
       |Can be used to onboard the account to the API - since all other account and transaction endpoints require views to be assigned.
       |
       |${accountTypeFilterText("/users/USER_ID/accounts-held")}
       |
       |
       |
      """.stripMargin,
      EmptyBody,
      coreAccountsHeldJsonV300,
      List($AuthenticatedUserIsRequired, $BankNotFound, UserNotFoundByUserId, UnknownError),
      List(apiTagAccount),
      Some(List(canGetAccountsHeldAtAnyBank)),
      http4sPartialFunction = Some(getAccountsHeldByUser)
    )

    // ─── Customer helpers (2) ─────────────────────────────────────────────

    val getCustomersForUserIdsOnly: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / "current" / "customers" / "customer_ids" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (customers, _) <- Connector.connector.vend.getCustomersByUserId(cc.userId, Some(cc))
              .map(connectorEmptyResponse(_, Some(cc)))
          } yield JSONFactory510.createCustomersIds(customers)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCustomersForUserIdsOnly),
      "GET",
      "/users/current/customers/customer_ids",
      "Get Customers for Current User (IDs only)",
      s"""Gets all Customers Ids that are linked to a User.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      customersWithAttributesJsonV300,
      List($AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, UnknownError),
      List(apiTagCustomer, apiTagUser),
      None,
      http4sPartialFunction = Some(getCustomersForUserIdsOnly)
    )

    val getCustomersByLegalName: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "customers" / "legal-name" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            (bank, _) <- NewStyle.function.getBank(bankId, Some(cc))
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostCustomerLegalNameJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PostCustomerLegalNameJsonV510]
            }
            (customer, _) <- NewStyle.function.getCustomersByCustomerLegalName(bank.bankId, postedData.legal_name, Some(cc))
          } yield JSONFactory300.createCustomersJson(customer)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getCustomersByLegalName), "POST",
      "/banks/BANK_ID/customers/legal-name", "Get Customers by Legal Name",
      s"""Gets the Customers specified by Legal Name.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      postCustomerLegalNameJsonV510, customerJsonV310,
      List(AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, UnknownError),
      List(apiTagCustomer, apiTagKyc),
      Some(List(canGetCustomersAtOneBank)),
      http4sPartialFunction = Some(getCustomersByLegalName)
    )

    // ─── System integrity (5) + currencies (1) ────────────────────────────

    val customViewNamesCheck: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "system" / "integrity" / "custom-view-names-check" =>
        EndpointHelpers.executeFuture(req) {
          for {
            incorrectViews: List[ViewDefinition] <- Future {
              ViewDefinition.getCustomViews().filterNot(_.viewId.value.startsWith("_"))
            }
          } yield JSONFactory510.getCustomViewNamesCheck(incorrectViews)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(customViewNamesCheck),
      "GET",
      "/management/system/integrity/custom-view-names-check",
      "Check Custom View Names",
      s"""Check custom view names.
      |
      |${userAuthenticationMessage(true)}
      |""".stripMargin,
      EmptyBody,
      CheckSystemIntegrityJsonV510(true),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagSystemIntegrity),
      Some(canGetSystemIntegrity :: Nil),
      http4sPartialFunction = Some(customViewNamesCheck)
    )

    val systemViewNamesCheck: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "system" / "integrity" / "system-view-names-check" =>
        EndpointHelpers.executeFuture(req) {
          for {
            incorrectViews: List[ViewDefinition] <- Future {
              ViewDefinition.getSystemViews().filter(_.viewId.value.startsWith("_"))
            }
          } yield JSONFactory510.getSystemViewNamesCheck(incorrectViews)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(systemViewNamesCheck),
      "GET",
      "/management/system/integrity/system-view-names-check",
      "Check System View Names",
      s"""Check system view names.
      |
      |${userAuthenticationMessage(true)}
      |""".stripMargin,
      EmptyBody,
      CheckSystemIntegrityJsonV510(true),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagSystemIntegrity),
      Some(canGetSystemIntegrity :: Nil),
      http4sPartialFunction = Some(systemViewNamesCheck)
    )

    val accountAccessUniqueIndexCheck: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "system" / "integrity" / "account-access-unique-index-1-check" =>
        EndpointHelpers.executeFuture(req) {
          for {
            groupedRows: Map[String, List[AccountAccess]] <- Future {
              AccountAccess.findAll().groupBy { a =>
                s"${a.bank_id.get}-${a.account_id.get}-${a.view_id.get}-${a.user_fk.get}-${a.consumer_id.get}"
              }.filter(_._2.size > 1)
            }
          } yield JSONFactory510.getAccountAccessUniqueIndexCheck(groupedRows)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(accountAccessUniqueIndexCheck),
      "GET",
      "/management/system/integrity/account-access-unique-index-1-check",
      "Check Unique Index at Account Access",
      s"""Check unique index at account access table.
      |
      |${userAuthenticationMessage(true)}
      |""".stripMargin,
      EmptyBody,
      CheckSystemIntegrityJsonV510(true),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagSystemIntegrity),
      Some(canGetSystemIntegrity :: Nil),
      http4sPartialFunction = Some(accountAccessUniqueIndexCheck)
    )

    val accountCurrencyCheck: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "system" / "integrity" / "banks" / bankIdStr / "account-currency-check" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            currencies: List[String] <- Future {
              code.model.dataAccess.MappedBankAccount.findAll().map(_.accountCurrency.get).distinct
            }
            (bankCurrencies, _) <- NewStyle.function.getCurrentCurrencies(bankId, Some(cc))
          } yield JSONFactory510.getSensibleCurrenciesCheck(bankCurrencies, currencies)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(accountCurrencyCheck),
      "GET",
      "/management/system/integrity/banks/BANK_ID/account-currency-check",
      "Check for Sensible Currencies",
      s"""Check for sensible currencies at Bank Account model
      |
      |${userAuthenticationMessage(true)}
      |""".stripMargin,
      EmptyBody,
      CheckSystemIntegrityJsonV510(true),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagSystemIntegrity),
      Some(canGetSystemIntegrity :: Nil),
      http4sPartialFunction = Some(accountCurrencyCheck)
    )

    val orphanedAccountCheck: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "system" / "integrity" / "banks" / bankIdStr / "orphaned-account-check" =>
        EndpointHelpers.executeFuture(req) {
          val bankId = BankId(bankIdStr)
          for {
            accountAccesses: List[String] <- Future {
              AccountAccess.findAll(By(AccountAccess.bank_id, bankId.value)).map(_.account_id.get)
            }
            bankAccounts <- Future {
              code.model.dataAccess.MappedBankAccount.findAll(By(code.model.dataAccess.MappedBankAccount.bank, bankId.value)).map(_.accountId.value)
            }
          } yield {
            val orphaned = accountAccesses.filterNot(bankAccounts.contains)
            JSONFactory510.getOrphanedAccountsCheck(orphaned)
          }
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(orphanedAccountCheck),
      "GET",
      "/management/system/integrity/banks/BANK_ID/orphaned-account-check",
      "Check for Orphaned Accounts",
      s"""Check for orphaned accounts at Bank Account model
      |
      |${userAuthenticationMessage(true)}
      |""".stripMargin,
      EmptyBody,
      CheckSystemIntegrityJsonV510(true),
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagSystemIntegrity),
      Some(canGetSystemIntegrity :: Nil),
      http4sPartialFunction = Some(orphanedAccountCheck)
    )

    val getCurrenciesAtBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "currencies" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            _ <- Helper.booleanToFuture(ConsumerHasMissingRoles + CanReadFx, failCode = 403, cc = Some(cc)) {
              checkScope(bankId.value, getConsumerPrimaryKey(Some(cc)), ApiRole.canReadFx)
            }
            (_, _) <- NewStyle.function.getBank(bankId, Some(cc))
            (currencies, _) <- NewStyle.function.getCurrentCurrencies(bankId, Some(cc))
          } yield CurrenciesJsonV510(currencies.map(CurrencyJsonV510(_)))
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCurrenciesAtBank),
      "GET",
      "/banks/BANK_ID/currencies",
      "Get Currencies at a Bank",
      """Get Currencies specified by BANK_ID
        |
      """.stripMargin,
      EmptyBody,
      currenciesJsonV510,
      List($AuthenticatedUserIsRequired, UnknownError),
      List(apiTagFx),
      None,
      http4sPartialFunction = Some(getCurrenciesAtBank)
    )

    // ─── Consumer mgmt PUTs (4) + getCallsLimit + createMyConsumer +
    //     createConsumerDynamicRegistration (7 total) ───────────────────────

    val updateConsumerRedirectURL: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "consumers" / consumerId / "consumer" / "redirect_url" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            _ <- APIUtil.getPropsAsBoolValue("consumers_enabled_by_default", false) match {
              case true  => Future.successful(Full(()))
              case false => NewStyle.function.hasEntitlement("", user.userId, ApiRole.canUpdateConsumerRedirectUrl, Some(cc))
            }
            postJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[ConsumerRedirectUrlJSON]
            }
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            _ <- Helper.booleanToFuture(UserNoPermissionUpdateConsumer, 400, Some(cc)) {
              consumer.createdByUserId.equals(user.userId)
            }
            updatedConsumer <- NewStyle.function.updateConsumer(
              id = consumer.id.get,
              isActive = Some(APIUtil.getPropsAsBoolValue("consumers_enabled_by_default", defaultValue = false)),
              redirectURL = Some(postJson.redirect_url),
              callContext = Some(cc))
          } yield JSONFactory510.createConsumerJSON(updatedConsumer)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateConsumerRedirectURL),
      "PUT",
      "/management/consumers/CONSUMER_ID/consumer/redirect_url",
      "Update Consumer RedirectURL",
      s"""Update an existing redirectUrl for a Consumer specified by CONSUMER_ID.
        |
        | ${consumerDisabledText()}
        |
        | CONSUMER_ID can be obtained after you register the application.
        |
        | Or use the endpoint 'Get Consumers' to get it
        |
      """.stripMargin,
      consumerRedirectUrlJSON,
      consumerJSON,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagConsumer),
      Some(List(canUpdateConsumerRedirectUrl)),
      http4sPartialFunction = Some(updateConsumerRedirectURL)
    )

    val updateConsumerLogoURL: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "consumers" / consumerId / "consumer" / "logo_url" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            postJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[ConsumerLogoUrlJson]
            }
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            updatedConsumer <- NewStyle.function.updateConsumer(
              id = consumer.id.get, logoURL = Some(postJson.logo_url), callContext = Some(cc))
          } yield JSONFactory510.createConsumerJSON(updatedConsumer)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateConsumerLogoURL),
      "PUT",
      "/management/consumers/CONSUMER_ID/consumer/logo_url",
      "Update Consumer LogoURL",
      s"""Update an existing logoURL for a Consumer specified by CONSUMER_ID.
        |
        | ${consumerDisabledText()}
        |
        | CONSUMER_ID can be obtained after you register the application.
        |
        | Or use the endpoint 'Get Consumers' to get it
        |
      """.stripMargin,
      consumerLogoUrlJson,
      consumerJsonV510,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagConsumer),
      Some(List(canUpdateConsumerLogoUrl)),
      http4sPartialFunction = Some(updateConsumerLogoURL)
    )

    val updateConsumerCertificate: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "consumers" / consumerId / "consumer" / "certificate" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            postJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[ConsumerCertificateJson]
            }
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            updatedConsumer <- NewStyle.function.updateConsumer(
              id = consumer.id.get, certificate = Some(postJson.certificate), callContext = Some(cc))
          } yield JSONFactory510.createConsumerJSON(updatedConsumer)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateConsumerCertificate),
      "PUT",
      "/management/consumers/CONSUMER_ID/consumer/certificate",
      "Update Consumer Certificate",
      s"""Update a Certificate for a Consumer specified by CONSUMER_ID.
        |
        | ${consumerDisabledText()}
        |
        | CONSUMER_ID can be obtained after you register the application.
        |
        | Or use the endpoint 'Get Consumers' to get it
        |
      """.stripMargin,
      consumerCertificateJson,
      consumerJsonV510,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagConsumer),
      Some(List(canUpdateConsumerCertificate)),
      http4sPartialFunction = Some(updateConsumerCertificate)
    )

    val updateConsumerName: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "consumers" / consumerId / "consumer" / "name" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            postJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[ConsumerNameJson]
            }
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            updatedConsumer <- NewStyle.function.updateConsumer(
              id = consumer.id.get, name = Some(postJson.app_name), callContext = Some(cc))
          } yield JSONFactory510.createConsumerJSON(updatedConsumer)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateConsumerName),
      "PUT",
      "/management/consumers/CONSUMER_ID/consumer/name",
      "Update Consumer Name",
      s"""Update an existing name for a Consumer specified by CONSUMER_ID.
        |
        | ${consumerDisabledText()}
        |
        | CONSUMER_ID can be obtained after you register the application.
        |
        | Or use the endpoint 'Get Consumers' to get it
        |
      """.stripMargin,
      consumerNameJson,
      consumerJsonV510,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagConsumer),
      Some(List(canUpdateConsumerName)),
      http4sPartialFunction = Some(updateConsumerName)
    )

    val getCallsLimit: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consumers" / consumerId / "consumer" / "rate-limits" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            rateLimiting <- RateLimitingDI.rateLimiting.vend.getAllByConsumerId(consumerId, None)
          } yield createCallLimitJson(rateLimiting)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCallsLimit),
      "GET",
      "/management/consumers/CONSUMER_ID/consumer/rate-limits",
      "Get Rate Limits for a Consumer",
      s"""
      |Get Calls limits per Consumer.
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      EmptyBody,
      callLimitsJson510Example,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidConsumerId, ConsumerNotFoundByConsumerId,
      UserHasMissingRoles, UpdateConsumerError, UnknownError),
      List(apiTagConsumer),
      Some(List(canReadCallLimits)),
      http4sPartialFunction = Some(getCallsLimit)
    )

    val createMyConsumer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "my" / "consumers" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            tup <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              val js = com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[CreateConsumerRequestJsonV510]
              val appType = if (js.app_type.equals("Confidential")) AppType.valueOf("Confidential") else AppType.valueOf("Public")
              (js, appType)
            }
            (postedJson, appType) = tup
            (consumer, _) <- createConsumerNewStyle(
              key = Some(Helpers.randomString(40).toLowerCase),
              secret = Some(Helpers.randomString(40).toLowerCase),
              isActive = Some(postedJson.enabled),
              name = Some(postedJson.app_name),
              appType = Some(appType),
              description = Some(postedJson.description),
              developerEmail = Some(postedJson.developer_email),
              company = Some(postedJson.company),
              redirectURL = Some(postedJson.redirect_url),
              createdByUserId = Some(user.userId),
              clientCertificate = postedJson.client_certificate,
              logoURL = postedJson.logo_url,
              Some(cc)
            )
          } yield JSONFactory510.createConsumerJsonOnlyForPostResponseV510(consumer, None)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createMyConsumer),
      "POST",
      "/my/consumers",
      "Create a Consumer",
      s"""Create a Consumer (Authenticated access).
      |
      |""",
      createConsumerRequestJsonV510,
      consumerJsonV510,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagConsumer),
      None,
      http4sPartialFunction = Some(createMyConsumer)
    )

    // Walks a Throwable's cause chain looking for a JVM/security-provider configuration problem
    // (the requested algorithm or provider is unavailable) rather than anything about the
    // caller-supplied certificate or JWT. `RSASSAVerifier`/`SignedJWT.verify` wrap
    // NoSuchAlgorithmException in a JOSEException when the JVM's registered security providers
    // don't have the requested signature algorithm (a hardened/FIPS JRE, a stripped provider
    // list, a provider-registration bug) -- a server/environment fault that has nothing to do
    // with whether this particular client's certificate is well-formed.
    private[v5_1_0] def hasSecurityProviderCause(t: Throwable): Boolean =
      Iterator.iterate(t)(_.getCause).takeWhile(_ != null).exists {
        case _: java.security.NoSuchAlgorithmException => true
        case _: java.security.NoSuchProviderException  => true
        case _                                          => false
      }

    // `JwtUtil.verifyJwt` does not merely return false for a bad certificate -- it can THROW at
    // several points (PEM parsing, JWT parsing, key extraction, signature verification), and a
    // client-malformed certificate or JWT is exactly what most of those throws mean. But wrapping
    // the whole call in tryons(..., 400, ...) also converted a JVM/security-provider failure (see
    // hasSecurityProviderCause) into the same 400 -- telling a caller their input was bad when
    // the truth is the server's environment cannot perform this verification for ANY caller.
    // `verify` is a thunk rather than a direct call so this is testable without live PEM/JWT
    // material: production passes `() => JwtUtil.verifyJwt(jwt, pem)`, the test a stub that
    // throws a chosen exception.
    private[v5_1_0] def resolveJwtSignatureValid(
      verify: () => Boolean
    )(implicit cc: code.api.util.CallContext): Future[Boolean] =
      Future(verify()).recoverWith {
        case t if hasSecurityProviderCause(t) =>
          Future.failed(t)
        case t =>
          NewStyle.function.tryons(PostJsonIsNotSigned, 400, Some(cc)) { throw t }
      }

    val createConsumerDynamicRegistration: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "dynamic-registration" / "consumers" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            postedJwt <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[ConsumerJwtPostJsonV510]
            }
            pem = APIUtil.`getPSD2-CERT`(cc.requestHeaders)
            // `verifyJwt` does not merely return false for a bad certificate -- it THROWS
            // ("No PEM-encoded keys found") when the PSD2-CERT header is absent or unparseable,
            // and booleanToFuture only guards the false case, so the exception escaped as
            // OBP-50000 / HTTP 500. A missing or malformed client certificate is a client error;
            // reporting it as a server fault tells a caller with retry logic to keep sending a
            // request that cannot ever succeed.
            signatureValid <- resolveJwtSignatureValid(() => JwtUtil.verifyJwt(postedJwt.jwt, pem.getOrElse("")))
            _ <- Helper.booleanToFuture(PostJsonIsNotSigned, 400, Some(cc)) { signatureValid }
            postedJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(JwtUtil.getSignedPayloadAsJson(postedJwt.jwt).getOrElse("{}")).extract[ConsumerPostJsonV510]
            }
            certificateInfo: CertificateInfoJsonV510 <- Future(X509.getCertificateInfo(pem))
              .map(unboxFullOrFail(_, Some(cc), X509GeneralError))
            _ <- Helper.booleanToFuture(RegulatedEntityNotFoundByCertificate, 400, Some(cc)) {
              MappedRegulatedEntityProvider.getRegulatedEntities()
                .exists(_.entityCertificatePublicKey.replace("""\n""", "") == pem.getOrElse("").replace("""\n""", ""))
            }
            (consumer, _) <- createConsumerNewStyle(
              key = Some(Helpers.randomString(40).toLowerCase),
              secret = Some(Helpers.randomString(40).toLowerCase),
              isActive = Some(true),
              name = X509.getCommonName(pem).or(postedJson.app_name),
              appType = postedJson.app_type.map(AppType.valueOf).orElse(Some(AppType.valueOf("Confidential"))),
              description = Some(postedJson.description),
              developerEmail = X509.getEmailAddress(pem).or(postedJson.developer_email),
              company = X509.getOrganization(pem),
              redirectURL = postedJson.redirect_url,
              createdByUserId = None,
              clientCertificate = pem,
              logoURL = None,
              Some(cc)
            )
          } yield JSONFactory510.createConsumerJSON(consumer, Some(certificateInfo))
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createConsumerDynamicRegistration),
      "POST",
      "/dynamic-registration/consumers",
      "Create a Consumer(Dynamic Registration)",
      s"""Create a Consumer with full certificate validation (mTLS access) - **Recommended for PSD2/Berlin Group compliance**.
      |
      |This endpoint provides **secure, validated consumer registration** unlike the standard `/management/consumers` endpoint.
      |
      |**How it works (for comprehension flow):**
      |
      |1. **Extract JWT from request**: Parse the signed JWT from the request body
      |2. **Extract certificate**: Get certificate from `PSD2-CERT` header in PEM format
      |3. **Verify JWT signature**: Validate JWT is signed with the certificate's private key (proves possession)
      |4. **Parse JWT payload**: Extract consumer details (description, app_name, app_type, developer_email, redirect_url)
      |5. **Extract certificate info**: Parse certificate to get Common Name, Email, Organization
      |6. **Validate against Regulated Entity**: Check certificate exists in Regulated Entity registry (PSD2 requirement)
      |7. **Create consumer**: Generate credentials and create consumer record with validated certificate
      |8. **Return consumer with certificate info**: Returns consumer details including parsed certificate information
      |
      |**Certificate Validation (CRITICAL SECURITY DIFFERENCE from regular creation):**
      |
      |[YES] **JWT Signature Verification**: JWT must be signed with certificate's private key - proves TPP owns the certificate
      |[YES] **Regulated Entity Check**: Certificate must match a pre-registered Regulated Entity in the database
      |[YES] **Certificate Binding**: Certificate is permanently bound to the consumer at creation time
      |[YES] **CA Validation**: Certificate chain can be validated against trusted root CAs during API requests
      |[YES] **PSD2 Compliance**: Meets EU regulatory requirements for TPP registration
      |
      |**Security benefits vs regular consumer creation:**
      |
      || Feature | Regular Creation | Dynamic Registration |
      ||---------|-----------------|---------------------|
      || Certificate validation | [NO] None | [YES] Full validation |
      || Regulated Entity check | [NO] Not required | [YES] Required |
      || JWT signature proof | [NO] Not required | [YES] Required (proves private key possession) |
      || Self-signed certs | [YES] Accepted | [NO] Rejected |
      || PSD2 compliant | [NO] No | [YES] Yes |
      || Rogue TPP prevention | [NO] No | [YES] Yes |
      |
      |**Prerequisites:**
      |1. TPP must be registered as a Regulated Entity with their certificate
      |2. Certificate must be provided in `PSD2-CERT` request header (PEM format)
      |3. JWT must be signed with the private key corresponding to the certificate
      |4. Trust store must be configured with trusted root CAs
      |
      |**JWT Payload Structure:**
      |
      |Minimal:
      |```json
      |{ "description":"TPP Application Description" }
      |```
      |
      |Full:
      |```json
      |{
      |  "description": "Payment Initiation Service",
      |  "app_name": "Tesobe GmbH",
      |  "app_type": "Confidential",
      |  "developer_email": "contact@tesobe.com",
      |  "redirect_url": "https://tpp.example.com/callback"
      |}
      |```
      |
      |**Note:** JWT must be signed with the private key that corresponds to the public key in the certificate sent via `PSD2-CERT` header.
      |
      |**Certificate Information Extraction:**
      |
      |The endpoint automatically extracts information from the certificate:
      |- Common Name (CN) → used as app_name if not provided in JWT
      |- Email Address → used as developer_email if not provided
      |- Organization (O) → used as company
      |- Certificate validity period
      |- Issuer information
      |
      |**Configuration Required:**
      |- `truststore.path.tpp_signature` - Path to trust store for CA validation
      |- `truststore.password.tpp_signature` - Trust store password
      |- Regulated Entity must be pre-registered with certificate public key
      |
      |**Error Scenarios:**
      |- JWT signature invalid → `PostJsonIsNotSigned` (400)
      |- Certificate not in Regulated Entity registry → `RegulatedEntityNotFoundByCertificate` (400)
      |- Invalid JWT format → `InvalidJsonFormat` (400)
      |- Missing PSD2-CERT header → Signature verification fails
      |
      |**This is the SECURE way to register consumers for production PSD2/Berlin Group implementations.**
      |
      |""",
      ConsumerJwtPostJsonV510("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJkZXNjcmlwdGlvbiI6IlRQUCBkZXNjcmlwdGlvbiJ9.c5gPPsyUmnVW774y7h2xyLXg0wdtu25nbU2AvOmyzcWa7JTdCKuuy3CblxueGwqYkQDDQIya1Qny4blyAvh_a1Q28LgzEKBcH7Em9FZXerhkvR9v4FWbCC5AgNLdQ7sR8-rUQdShmJcGDKdVmsZjuO4XhY2Zx0nFnkcvYfsU9bccoAvkKpVJATXzwBqdoEOuFlplnbxsMH1wWbAd3hbcPPWTdvO43xavNZTB5ybgrXVDEYjw8D-98_ZkqxS0vfvhJ4cGefHViaFzp6zXm7msdBpcE__O9rFbdl9Gvup_bsMbrHJioIrmc2d15Yc-tTNTF9J4qjD_lNxMRlx5o2TZEw"),
      consumerJsonV510,
      List(InvalidJsonFormat, UnknownError),
      List(apiTagDirectory, apiTagConsumer),
      Some(Nil),
      http4sPartialFunction = Some(createConsumerDynamicRegistration)
    )

    // ─── View access (3) + transaction-request mgmt (2) ───────────────────

    val grantUserAccessToViewById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "account-access" / "grant" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val viewId = ViewId(viewIdStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostAccountAccessJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PostAccountAccessJsonV510]
            }
            targetViewId = ViewId(postJson.view_id)
            msg = getUserLacksGrantPermissionErrorMessage(viewId, targetViewId)
            _ <- Helper.booleanToFuture(msg, 403, cc = Some(cc)) {
              APIUtil.canGrantAccessToView(com.openbankproject.commons.model.BankIdAccountIdViewId(bankId, accountId, viewId), targetViewId, user, Some(cc))
            }
            (targetUser, _) <- NewStyle.function.findByUserId(postJson.user_id, Some(cc))
            // Explicit target: fail loud rather than redirect (see the entitlement endpoints).
            // A consent user's account access comes ONLY from its Consent (materialised and
            // revoked with it); access granted here would outlive nothing and confuse audits.
            _ <- Helper.booleanToFuture(
              s"$InvalidUserId user_id names a consent user (an agent identity minted by a Consent). Account access targets humans - a consent user's access comes only from its Consent.",
              failCode = 400, cc = Some(cc))(!targetUser.isConsentUser)
            view <- if (isValidSystemViewId(targetViewId.value)) ViewNewStyle.systemView(targetViewId, Some(cc))
                    else ViewNewStyle.customView(targetViewId, BankIdAccountId(bankId, accountId), Some(cc))
            addedView <- JSONFactory400.grantAccountAccessToUser(bankId, accountId, targetUser, view, Some(cc))
          } yield JSONFactory300.createViewJSON(addedView)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(grantUserAccessToViewById),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/account-access/grant",
      "Grant User access to View",
      s"""Grants the User identified by USER_ID access to the view on a bank account identified by VIEW_ID.
      |
      |${userAuthenticationMessage(true)}
      |
      |**Permission Requirements:**
      |The requesting user must have access to the source VIEW_ID and must possess specific grant permissions:
      |
      |**For System Views (e.g., owner, accountant, auditor, public etc.):**
      |- The user's current view must have the target view listed in its `canGrantAccessToViews` field
      |- Example: If granting access to "accountant" view, the user's view must include "accountant" in `canGrantAccessToViews`
      |
      |**For Custom Views (account-specific views):**
      |- The user's current view must have the `can_grant_access_to_custom_views` permission in its `allowed_actions` field
      |- This permission allows granting access to any custom view on the account
      |
      |**Security Checks Performed:**
      |1. User authentication validation
      |2. JSON format validation (USER_ID and VIEW_ID required)
      |3. Permission authorization via `APIUtil.canGrantAccessToView()`
      |4. Target user existence verification
      |5. Target view existence and type validation (system vs custom)
      |6. Final access grant operation in database
      |
      |**Final Database Operation:**
      |The system creates an `AccountAccess` record linking the user to the view if one doesn't already exist.
      |This operation includes:
      |- Duplicate check: Prevents creating duplicate access records (idempotent operation)
      |- Public view restriction: Blocks access to public views if disabled instance-wide
      |- Database constraint validation: Ensures referential integrity
      |
      |**Note:** The permission model ensures users can only delegate access rights they themselves possess or are explicitly authorized to grant.
      |
      |""",
      postAccountAccessJsonV510,
      viewJsonV300,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView,
      UserLacksPermissionCanGrantAccessToSystemViewForTargetAccount,
      UserLacksPermissionCanGrantAccessToCustomViewForTargetAccount,
      InvalidJsonFormat, UserNotFoundById, SystemViewNotFound, ViewNotFound,
      CannotGrantAccountAccess, UnknownError),
      List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired),
      None,
      http4sPartialFunction = Some(grantUserAccessToViewById)
    )

    val revokeUserAccessToViewById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "account-access" / "revoke" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val viewId = ViewId(viewIdStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[code.api.v4_0_0.PostAccountAccessJsonV400].getSimpleName} ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PostAccountAccessJsonV510]
            }
            targetViewId = ViewId(postJson.view_id)
            msg = getUserLacksRevokePermissionErrorMessage(viewId, targetViewId)
            _ <- Helper.booleanToFuture(msg, 403, cc = Some(cc)) {
              APIUtil.canRevokeAccessToView(com.openbankproject.commons.model.BankIdAccountIdViewId(bankId, accountId, viewId), targetViewId, user, Some(cc))
            }
            (targetUser, _) <- NewStyle.function.findByUserId(postJson.user_id, Some(cc))
            view <- if (isValidSystemViewId(targetViewId.value)) ViewNewStyle.systemView(targetViewId, Some(cc))
                    else ViewNewStyle.customView(targetViewId, BankIdAccountId(bankId, accountId), Some(cc))
            revoked <- if (isValidSystemViewId(targetViewId.value))
              ViewNewStyle.revokeAccessToSystemView(bankId, accountId, view, targetUser, Some(cc))
            else ViewNewStyle.revokeAccessToCustomView(view, targetUser, Some(cc))
          } yield code.api.v4_0_0.RevokedJsonV400(revoked)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(revokeUserAccessToViewById),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/account-access/revoke",
      "Revoke User access to View",
      s"""Revoke the User identified by USER_ID access to the view identified.
      |
      |${userAuthenticationMessage(true)}.
      |
      |""",
      postAccountAccessJsonV510,
      revokedJsonV400,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView,
      UserLacksPermissionCanRevokeAccessToCustomViewForTargetAccount,
      UserLacksPermissionCanRevokeAccessToSystemViewForTargetAccount,
      InvalidJsonFormat, UserNotFoundById, SystemViewNotFound, ViewNotFound,
      CannotRevokeAccountAccess, CannotFindAccountAccess, UnknownError),
      List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired),
      None,
      http4sPartialFunction = Some(revokeUserAccessToViewById)
    )

    val createUserWithAccountAccessById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "user-account-access" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val viewId = ViewId(viewIdStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostCreateUserAccountAccessJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PostCreateUserAccountAccessJsonV510]
            }
            _ <- Helper.booleanToFuture(s"$InvalidUserProvider The user.provider must be start with 'dauth.'", cc = Some(cc)) {
              postJson.provider.startsWith("dauth.")
            }
            targetViewId = ViewId(postJson.view_id)
            msg = getUserLacksGrantPermissionErrorMessage(viewId, targetViewId)
            _ <- Helper.booleanToFuture(msg, 403, cc = Some(cc)) {
              APIUtil.canGrantAccessToView(com.openbankproject.commons.model.BankIdAccountIdViewId(bankId, accountId, viewId), targetViewId, user, Some(cc))
            }
            (targetUser, _) <- NewStyle.function.getOrCreateResourceUser(postJson.provider, postJson.username, Some(cc))
            view <- if (isValidSystemViewId(targetViewId.value)) ViewNewStyle.systemView(targetViewId, Some(cc))
                    else ViewNewStyle.customView(targetViewId, BankIdAccountId(bankId, accountId), Some(cc))
            addedView <- if (isValidSystemViewId(targetViewId.value))
              ViewNewStyle.grantAccessToSystemView(bankId, accountId, view, targetUser, Some(cc))
            else ViewNewStyle.grantAccessToCustomView(view, targetUser, Some(cc))
          } yield JSONFactory300.createViewJSON(addedView)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createUserWithAccountAccessById),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/user-account-access",
      "Create (DAuth) User with Account Access",
      s"""This endpoint is used as part of the DAuth solution to grant access to account and transaction data to a smart contract on the blockchain.
      |
      |Put the smart contract address in username
      |
      |For provider use "dauth"
      |
      |This endpoint will create the (DAuth) User with username and provider if the User does not already exist.
      |
      |${userAuthenticationMessage(true)} and the logged in user needs to be account holder.
      |
      |For information about DAuth see below:
      |
      |${Glossary.getGlossaryItem("DAuth")}
      |
      |""",
      postCreateUserAccountAccessJsonV400,
      List(viewJsonV300),
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView,
      UserLacksPermissionCanGrantAccessToSystemViewForTargetAccount,
      UserLacksPermissionCanGrantAccessToCustomViewForTargetAccount,
      InvalidJsonFormat, SystemViewNotFound, ViewNotFound, CannotGrantAccountAccess, UnknownError),
      List(apiTagAccountAccess, apiTagView, apiTagAccount, apiTagUser, apiTagOwnerRequired, apiTagDAuth),
      None,
      http4sPartialFunction = Some(createUserWithAccountAccessById)
    )

    val getTransactionRequestById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "transaction-requests" / requestIdStr =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val requestId = TransactionRequestId(requestIdStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            (transactionRequest, _) <- NewStyle.function.getTransactionRequestImpl(requestId, Some(cc))
            _ <- NewStyle.function.hasAtLeastOneEntitlement(transactionRequest.from.bank_id, user.userId,
              canGetTransactionRequestAtOneBank :: canGetTransactionRequestAtAnyBank :: Nil, Some(cc))
          } yield JSONFactory210.createTransactionRequestWithChargeJSON(transactionRequest)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getTransactionRequestById),
      "GET",
      "/management/transaction-requests/TRANSACTION_REQUEST_ID",
      "Get Transaction Request by ID.",
      """Returns transaction request for transaction specified by TRANSACTION_REQUEST_ID.
        |
      """.stripMargin,
      EmptyBody,
      transactionRequestWithChargeJSON210,
      List($AuthenticatedUserIsRequired, GetTransactionRequestsException, UnknownError),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2),
      Some(List(canGetTransactionRequestAtOneBank, canGetTransactionRequestAtAnyBank)),
      http4sPartialFunction = Some(getTransactionRequestById)
    )

    val updateTransactionRequestStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "transaction-requests" / requestIdStr =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val requestId = TransactionRequestId(requestIdStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostTransactionRequestStatusJsonV510", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PostTransactionRequestStatusJsonV510]
            }
            // Lock the transaction-request row for the duration of this request transaction (the
            // FOR UPDATE lock runs on the request connection via RequestScopeConnection, so it is held
            // through the read + status write below). Without it this management update races the
            // challenge-answer path (Http4s400, which already locks) and can overwrite a COMPLETED
            // payment with a stale status.
            _ <- code.util.Helper.booleanToFuture(TransactionRequestLockFailed, cc = Some(cc)) {
              code.bankconnectors.DoobieTransactionRequestQueries.lockTransactionRequest(requestId.value).isDefined
            }
            (existing, _) <- NewStyle.function.getTransactionRequestImpl(requestId, Some(cc))
            _ <- NewStyle.function.hasAtLeastOneEntitlement(existing.from.bank_id, user.userId,
              canUpdateTransactionRequestStatusAtOneBank :: canUpdateTransactionRequestStatusAtAnyBank :: Nil, Some(cc))
            _ <- NewStyle.function.saveTransactionRequestStatusImpl(requestId, postedData.status, Some(cc))
            (transactionRequest, _) <- NewStyle.function.getTransactionRequestImpl(requestId, Some(cc))
          } yield TransactionRequestStatusJsonV510(transactionRequest.id.value, transactionRequest.status)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateTransactionRequestStatus),
      "PUT",
      "/management/transaction-requests/TRANSACTION_REQUEST_ID",
      "Update Transaction Request Status",
      s""" Update Transaction Request Status
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      PostTransactionRequestStatusJsonV510(TransactionRequestStatus.COMPLETED.toString),
      PostTransactionRequestStatusJsonV510(TransactionRequestStatus.COMPLETED.toString),
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, InvalidJsonFormat, UnknownError),
      List(apiTagTransactionRequest),
      Some(List(canUpdateTransactionRequestStatusAtOneBank, canUpdateTransactionRequestStatusAtAnyBank)),
      http4sPartialFunction = Some(updateTransactionRequestStatus)
    )

    // ─── View account/balance reads (3) ───────────────────────────────────

    val getCoreAccountByIdThroughView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val viewId = ViewId(viewIdStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            (account, _) <- NewStyle.function.checkBankAccountExists(bankId, accountId, Some(cc))
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Full(user), Some(cc))
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), Some(cc))
          } yield {
            val availableViews: List[View] = Views.views.vend.privateViewsUserCanAccessForAccount(user, BankIdAccountId(bankId, accountId))
            createNewCoreBankAccountJson(moderatedAccount, availableViews)
          }
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCoreAccountByIdThroughView),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID",
      "Get Account by Id (Core) through the VIEW_ID",
      s"""Information returned about the account through VIEW_ID :
      |""".stripMargin,
      EmptyBody,
      moderatedCoreAccountJsonV400,
      List($AuthenticatedUserIsRequired, $BankAccountNotFound, UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(getCoreAccountByIdThroughView)
    )

    val getBankAccountBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "balances" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val viewId = ViewId(viewIdStr)
          val bankIdAccountId = BankIdAccountId(bankId, accountId)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            view <- ViewNewStyle.checkViewAccessAndReturnView(viewId, bankIdAccountId, Full(user), Some(cc))
            _ <- Helper.booleanToFuture(
              ViewDoesNotPermitAccess + s" You need the `${CAN_SEE_BANK_ACCOUNT_BALANCE}` permission on VIEW_ID(${viewId.value})",
              403, cc = Some(cc)) {
              view.allowed_actions.exists(_ == CAN_SEE_BANK_ACCOUNT_BALANCE)
            }
            (accountBalances, _) <- BalanceNewStyle.getBankAccountBalances(bankIdAccountId, Some(cc))
          } yield createAccountBalancesJson(accountBalances)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getBankAccountBalances),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/balances",
      "Get Account Balances by BANK_ID and ACCOUNT_ID through the VIEW_ID",
      """Get the Balances for the Account specified by BANK_ID and ACCOUNT_ID through the VIEW_ID.""",
      EmptyBody,
      accountBalanceV400,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, UserNoPermissionAccessView, UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(getBankAccountBalances)
    )

    val getBankAccountsBalancesThroughView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "views" / viewIdStr / "balances" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val viewId = ViewId(viewIdStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            (allowedAccounts, _) <- BalanceNewStyle.getAccountAccessAtBankThroughView(user, bankId, viewId, Some(cc))
            (accountsBalances, _) <- BalanceNewStyle.getBankAccountsBalances(allowedAccounts, Some(cc))
          } yield createBalancesJson(accountsBalances)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getBankAccountsBalancesThroughView),
      "GET",
      "/banks/BANK_ID/views/VIEW_ID/balances",
      "Get Account Balances by BANK_ID through the VIEW_ID",
      """Get the Balances for the Account specified by BANK_ID.""",
      EmptyBody,
      accountBalancesV400Json,
      List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(getBankAccountsBalancesThroughView)
    )

    // ─── Counterparty limits (4 simple) — getCounterpartyLimitStatus deferred (complex)

    val createCounterpartyLimit: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "counterparties" / counterpartyIdStr / "limits" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val viewId = ViewId(viewIdStr); val counterpartyId = CounterpartyId(counterpartyIdStr)
          for {
            postLimit <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[PostCounterpartyLimitV510]}", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PostCounterpartyLimitV510]
            }
            _ <- Helper.booleanToFuture(s"$InvalidISOCurrencyCode Current input is: '${postLimit.currency}'", cc = Some(cc)) {
              isValidCurrencyISOCode(postLimit.currency)
            }
            (existingBox, _) <- Connector.connector.vend.getCounterpartyLimit(bankId.value, accountId.value, viewId.value, counterpartyId.value, Some(cc))
            _ <- Helper.booleanToFuture(
              s"$CounterpartyLimitAlreadyExists Current BANK_ID($bankId), ACCOUNT_ID($accountId), VIEW_ID($viewId),COUNTERPARTY_ID($counterpartyId)",
              cc = Some(cc)) { existingBox.isEmpty }
            (counterpartyLimit, _) <- NewStyle.function.createOrUpdateCounterpartyLimit(
              bankId.value, accountId.value, viewId.value, counterpartyId.value,
              postLimit.currency,
              BigDecimal(postLimit.max_single_amount),
              BigDecimal(postLimit.max_monthly_amount),
              postLimit.max_number_of_monthly_transactions,
              BigDecimal(postLimit.max_yearly_amount),
              postLimit.max_number_of_yearly_transactions,
              BigDecimal(postLimit.max_total_amount),
              postLimit.max_number_of_transactions,
              Some(cc))
          } yield counterpartyLimit.toJValue
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createCounterpartyLimit),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/counterparties/COUNTERPARTY_ID/limits",
      "Create Counterparty Limit",
      s"""Create limits (for single or recurring payments) for a counterparty specified by the COUNTERPARTY_ID.
      |
      |Using this endpoint, we can attach a limit record to a Counterparty referenced by its counterparty_id (a UUID).
      |
      |For more information on Counterparty Limits, see ${Glossary.getGlossaryItemLink("Counterparty-Limits")}
      |
      |For an introduction to Counterparties in OBP, see ${Glossary.getGlossaryItemLink("Counterparties")}
      |
      |You can automate the process of creating counterparty limits and consents for VRP with this ${Glossary.getApiExplorerLink("endpoint", "OBPv5.1.0-createVRPConsentRequest")}.
      |
      |
      |
      |""".stripMargin,
      postCounterpartyLimitV510,
      counterpartyLimitV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView,
      $CounterpartyNotFoundByCounterpartyId, InvalidJsonFormat, UnknownError),
      List(apiTagCounterpartyLimits),
      None,
      http4sPartialFunction = Some(createCounterpartyLimit)
    )

    val updateCounterpartyLimit: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "counterparties" / counterpartyIdStr / "limits" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val viewId = ViewId(viewIdStr); val counterpartyId = CounterpartyId(counterpartyIdStr)
          for {
            postLimit <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[PostCounterpartyLimitV510]}", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PostCounterpartyLimitV510]
            }
            _ <- Helper.booleanToFuture(s"$InvalidISOCurrencyCode Current input is: '${postLimit.currency}'", cc = Some(cc)) {
              isValidCurrencyISOCode(postLimit.currency)
            }
            (counterpartyLimit, _) <- NewStyle.function.createOrUpdateCounterpartyLimit(
              bankId.value, accountId.value, viewId.value, counterpartyId.value,
              postLimit.currency,
              BigDecimal(postLimit.max_single_amount),
              BigDecimal(postLimit.max_monthly_amount),
              postLimit.max_number_of_monthly_transactions,
              BigDecimal(postLimit.max_yearly_amount),
              postLimit.max_number_of_yearly_transactions,
              BigDecimal(postLimit.max_total_amount),
              postLimit.max_number_of_transactions,
              Some(cc))
          } yield counterpartyLimit.toJValue
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateCounterpartyLimit),
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/counterparties/COUNTERPARTY_ID/limits",
      "Update Counterparty Limit",
      s"""Update Counterparty Limit.""",
      postCounterpartyLimitV510,
      counterpartyLimitV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView,
      $CounterpartyNotFoundByCounterpartyId, InvalidJsonFormat, UnknownError),
      List(apiTagCounterpartyLimits),
      None,
      http4sPartialFunction = Some(updateCounterpartyLimit)
    )

    val getCounterpartyLimit: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "counterparties" / counterpartyIdStr / "limits" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val viewId = ViewId(viewIdStr); val counterpartyId = CounterpartyId(counterpartyIdStr)
          for {
            (counterpartyLimit, _) <- NewStyle.function.getCounterpartyLimit(bankId.value, accountId.value, viewId.value, counterpartyId.value, Some(cc))
          } yield counterpartyLimit.toJValue
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCounterpartyLimit),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/counterparties/COUNTERPARTY_ID/limits",
      "Get Counterparty Limit",
      s"""Get Counterparty Limit.""",
      EmptyBody,
      counterpartyLimitV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView,
      $CounterpartyNotFoundByCounterpartyId, InvalidJsonFormat, UnknownError),
      List(apiTagCounterpartyLimits),
      None,
      http4sPartialFunction = Some(getCounterpartyLimit)
    )

    val getCounterpartyLimitStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "counterparties" / counterpartyIdStr / "limit-status" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr)
          val viewId = ViewId(viewIdStr); val counterpartyId = CounterpartyId(counterpartyIdStr)
          val zoneId = java.time.ZoneId.systemDefault()
          val today = java.time.LocalDate.now()
          val firstDayOfMonth = today.withDayOfMonth(1)
          val lastDayOfMonth = today.withDayOfMonth(today.lengthOfMonth())
          val firstDayOfYear = today.withDayOfYear(1)
          val lastDayOfYear = today.withDayOfYear(today.lengthOfYear())
          val firstCurrentMonthDate: Date = Date.from(firstDayOfMonth.atStartOfDay(zoneId).toInstant)
          val lastCurrentMonthDate: Date = Date.from(lastDayOfMonth.atTime(23, 59, 59, 999000000).atZone(zoneId).toInstant)
          val firstCurrentYearDate: Date = Date.from(firstDayOfYear.atStartOfDay(zoneId).toInstant)
          val lastCurrentYearDate: Date = Date.from(lastDayOfYear.atTime(23, 59, 59, 999000000).atZone(zoneId).toInstant)
          val defaultFromDate: Date = APIUtil.theEpochTime
          val defaultToDate: Date = APIUtil.ToDateInFuture
          for {
            (counterpartyLimit, _) <- NewStyle.function.getCounterpartyLimit(
              bankId.value, accountId.value, viewId.value, counterpartyId.value, Some(cc))
            (fromBankAccount, _) <- NewStyle.function.getBankAccount(bankId, accountId, Some(cc))
            (sumMonthly, _) <- NewStyle.function.getSumOfTransactionsFromAccountToCounterparty(
              bankId, accountId, counterpartyId, firstCurrentMonthDate, lastCurrentMonthDate, Some(cc))
            (countMonthly, _) <- NewStyle.function.getCountOfTransactionsFromAccountToCounterparty(
              bankId, accountId, counterpartyId, firstCurrentMonthDate, lastCurrentMonthDate, Some(cc))
            (sumYearly, _) <- NewStyle.function.getSumOfTransactionsFromAccountToCounterparty(
              bankId, accountId, counterpartyId, firstCurrentYearDate, lastCurrentYearDate, Some(cc))
            (countYearly, _) <- NewStyle.function.getCountOfTransactionsFromAccountToCounterparty(
              bankId, accountId, counterpartyId, firstCurrentYearDate, lastCurrentYearDate, Some(cc))
            (sumAll, _) <- NewStyle.function.getSumOfTransactionsFromAccountToCounterparty(
              bankId, accountId, counterpartyId, defaultFromDate, defaultToDate, Some(cc))
            (countAll, _) <- NewStyle.function.getCountOfTransactionsFromAccountToCounterparty(
              bankId, accountId, counterpartyId, defaultFromDate, defaultToDate, Some(cc))
          } yield CounterpartyLimitStatusV510(
            counterparty_limit_id = counterpartyLimit.counterpartyLimitId,
            bank_id = counterpartyLimit.bankId,
            account_id = counterpartyLimit.accountId,
            view_id = counterpartyLimit.viewId,
            counterparty_id = counterpartyLimit.counterpartyId,
            currency = counterpartyLimit.currency,
            max_single_amount = counterpartyLimit.maxSingleAmount.toString(),
            max_monthly_amount = counterpartyLimit.maxMonthlyAmount.toString(),
            max_number_of_monthly_transactions = counterpartyLimit.maxNumberOfMonthlyTransactions,
            max_yearly_amount = counterpartyLimit.maxYearlyAmount.toString(),
            max_number_of_yearly_transactions = counterpartyLimit.maxNumberOfYearlyTransactions,
            max_total_amount = counterpartyLimit.maxTotalAmount.toString(),
            max_number_of_transactions = counterpartyLimit.maxNumberOfTransactions,
            status = CounterpartyLimitStatus(
              currency_status = fromBankAccount.currency,
              max_monthly_amount_status = sumMonthly.amount,
              max_number_of_monthly_transactions_status = countMonthly,
              max_yearly_amount_status = sumYearly.amount,
              max_number_of_yearly_transactions_status = countYearly,
              max_total_amount_status = sumAll.amount,
              max_number_of_transactions_status = countAll
            )
          )
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCounterpartyLimitStatus),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/counterparties/COUNTERPARTY_ID/limit-status",
      "Get Counterparty Limit Status",
      s"""Get Counterparty Limit Status.""",
      EmptyBody,
      counterpartyLimitStatusV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView,
      $CounterpartyNotFoundByCounterpartyId, InvalidJsonFormat, UnknownError),
      List(apiTagCounterpartyLimits),
      None,
      http4sPartialFunction = Some(getCounterpartyLimitStatus)
    )

    val deleteCounterpartyLimit: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "counterparties" / counterpartyIdStr / "limits" =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val viewId = ViewId(viewIdStr); val counterpartyId = CounterpartyId(counterpartyIdStr)
          for {
            (counterpartyLimit, _) <- NewStyle.function.deleteCounterpartyLimit(bankId.value, accountId.value, viewId.value, counterpartyId.value, Some(cc))
          } yield counterpartyLimit
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteCounterpartyLimit),
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/counterparties/COUNTERPARTY_ID/limits",
      "Delete Counterparty Limit",
      s"""Delete Counterparty Limit.""",
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView,
      $CounterpartyNotFoundByCounterpartyId, InvalidJsonFormat, UnknownError),
      List(apiTagCounterpartyLimits),
      None,
      http4sPartialFunction = Some(deleteCounterpartyLimit)
    )

    // ─── Custom view CRUD (4) ─────────────────────────────────────────────

    val createCustomView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "target-views" =>
        EndpointHelpers.withViewCreated(req) { (user, account, view, cc) =>
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val viewId = ViewId(viewIdStr)
          for {
            createJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[com.openbankproject.commons.model.CreateViewJson]}", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[CreateCustomViewJson]
            }
            _ <- Helper.booleanToFuture(InvalidCustomViewFormat + s"Current view_name (${createJson.name})", cc = Some(cc)) {
              isValidCustomViewName(createJson.name)
            }
            permissionsFromSource = view.asInstanceOf[ViewDefinition].allowed_actions.toSet
            permissionsFromTarget = createJson.allowed_permissions
            _ <- Helper.booleanToFuture(SourceViewHasLessPermission + s"Current source viewId($viewId) permissions ($permissionsFromSource), target viewName${createJson.name} permissions ($permissionsFromTarget)", cc = Some(cc)) {
              permissionsFromTarget.toSet.subsetOf(permissionsFromSource)
            }
            _ <- Helper.booleanToFuture(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${CAN_CREATE_CUSTOM_VIEW}` permission on VIEW_ID(${viewId.value})", cc = Some(cc)) {
              view.allowed_actions.exists(_ == CAN_CREATE_CUSTOM_VIEW)
            }
            (newView, _) <- ViewNewStyle.createCustomView(BankIdAccountId(bankId, accountId), createJson.toCreateViewJson, Some(cc))
          } yield JSONFactory510.createViewJson(newView)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(createCustomView), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/target-views", "Create Custom View",
      s"""Create a custom view on bank account
      |
      | ${userAuthenticationMessage(true)} and the user needs to have access to the owner view.
      | The 'alias' field in the JSON can take one of three values:
      |
      | * _public_: to use the public alias if there is one specified for the other account.
      | * _private_: to use the private alias if there is one specified for the other account.
      |
      | * _''(empty string)_: to use no alias; the view shows the real name of the other account.
      |
      | The 'hide_metadata_if_alias_used' field in the JSON can take boolean values. If it is set to `true` and there is an alias on the other account then the other accounts' metadata (like more_info, url, image_url, open_corporates_url, etc.) will be hidden. Otherwise the metadata will be shown.
      |
      | The 'allowed_actions' field is a list containing the name of the actions allowed on this view, all the actions contained will be set to `true` on the view creation, the rest will be set to `false`.
      |
      | The 'metadata_view' field determines where metadata (comments, tags, images, where tags) for transactions are stored and retrieved. If set to another view's ID (e.g. 'owner'), metadata added through this view will be shared with all other views that also use the same metadata_view value. If left empty, metadata is stored under this view's own ID and is not shared with other views.
      |
      | You MUST use a leading _ (underscore) in the view name because other view names are reserved for OBP [system views](/index#group-View-System).
      | """,
      createCustomViewJson, customViewJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView, InvalidJsonFormat, UnknownError),
      List(apiTagView, apiTagAccount),
      None,
      http4sPartialFunction = Some(createCustomView)
    )

    val updateCustomView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "target-views" / targetViewIdStr =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val viewId = ViewId(viewIdStr); val targetViewId = ViewId(targetViewIdStr)
          for {
            updateJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[UpdateCustomViewJson]}", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[UpdateCustomViewJson]
            }
            _ <- Helper.booleanToFuture(InvalidCustomViewFormat + s"Current TARGET_VIEW_ID (${targetViewId})", cc = Some(cc)) {
              isValidCustomViewId(targetViewId.value)
            }
            permissionsFromSource = view.asInstanceOf[ViewDefinition].allowed_actions.toSet
            permissionsFromTarget = updateJson.allowed_permissions
            _ <- Helper.booleanToFuture(SourceViewHasLessPermission + s"Current source view permissions ($permissionsFromSource), target view permissions ($permissionsFromTarget)", cc = Some(cc)) {
              permissionsFromTarget.toSet.subsetOf(permissionsFromSource)
            }
            _ <- Helper.booleanToFuture(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${CAN_UPDATE_CUSTOM_VIEW}` permission on VIEW_ID(${viewId.value})", cc = Some(cc)) {
              view.allowed_actions.exists(_ == CAN_CREATE_CUSTOM_VIEW)
            }
            (updatedView, _) <- ViewNewStyle.updateCustomView(BankIdAccountId(bankId, accountId), targetViewId, updateJson.toUpdateViewJson, Some(cc))
          } yield JSONFactory510.createViewJson(updatedView)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(updateCustomView), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/target-views/TARGET_VIEW_ID", "Update Custom View",
      s"""Update an existing custom view on a bank account
      |
      |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.
      |
      |The json sent is the same as during view creation (above), with one difference: the 'name' field
      |of a view is not editable (it is only set when a view is created)""",
      updateCustomViewJson, customViewJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView, InvalidJsonFormat, UnknownError),
      List(apiTagView, apiTagAccount),
      None,
      http4sPartialFunction = Some(updateCustomView)
    )

    val getCustomView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "target-views" / targetViewIdStr =>
        EndpointHelpers.withView(req) { (_, _, view, cc) =>
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val viewId = ViewId(viewIdStr); val targetViewId = ViewId(targetViewIdStr)
          for {
            _ <- Helper.booleanToFuture(InvalidCustomViewFormat + s"Current TARGET_VIEW_ID (${targetViewId.value})", cc = Some(cc)) {
              isValidCustomViewId(targetViewId.value)
            }
            _ <- Helper.booleanToFuture(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${CAN_GET_CUSTOM_VIEW}`permission on any your views. Current VIEW_ID (${viewId.value})", cc = Some(cc)) {
              view.allowed_actions.exists(_ == CAN_GET_CUSTOM_VIEW)
            }
            targetView <- ViewNewStyle.customView(targetViewId, BankIdAccountId(bankId, accountId), Some(cc))
          } yield JSONFactory510.createViewJson(targetView)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCustomView),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/target-views/TARGET_VIEW_ID",
      "Get Custom View",
      s"""#Views
      |
      |
      |Views in Open Bank Project provide a mechanism for fine grained access control and delegation to Accounts and Transactions. Account holders use the 'owner' view by default. Delegated access is made through other views for example 'accountants', 'share-holders' or 'tagging-application'. Views can be created via the API and each view has a list of entitlements.
      |
      |Views on accounts and transactions filter the underlying data to redact certain fields for certain users. For instance the balance on an account may be hidden from the public. The way to know what is possible on a view is determined in the following JSON.
      |
      |**Data:** When a view moderates a set of data, some fields my contain the value `null` rather than the original value. This indicates either that the user is not allowed to see the original data or the field is empty.
      |
      |There is currently one exception to this rule; the 'holder' field in the JSON contains always a value which is either an alias or the real name - indicated by the 'is_alias' field.
      |
      |**Action:** When a user performs an action like trying to post a comment (with POST API call), if he is not allowed, the body response will contain an error message.
      |
      |**Metadata:**
      |Transaction metadata (like images, tags, comments, etc.) will appears *ONLY* on the view where they have been created e.g. comments posted to the public view only appear on the public view.
      |
      |The other account metadata fields (like image_URL, more_info, etc.) are unique through all the views. Example, if a user edits the 'more_info' field in the 'team' view, then the view 'authorities' will show the new value (if it is allowed to do it).
      |
      |# All
      |*Optional*
      |
      |Returns the list of the views created for account ACCOUNT_ID at BANK_ID.
      |
      |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody,
      customViewJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView, UnknownError),
      List(apiTagView, apiTagAccount),
      None,
      http4sPartialFunction = Some(getCustomView)
    )

    val deleteCustomView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "views" / viewIdStr / "target-views" / targetViewIdStr =>
        EndpointHelpers.executeDelete(req) { cc =>
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val viewId = ViewId(viewIdStr); val targetViewId = ViewId(targetViewIdStr)
          val view = cc.view.getOrElse(throw new RuntimeException(UserNoPermissionAccessView))
          for {
            _ <- Helper.booleanToFuture(InvalidCustomViewFormat + s"Current TARGET_VIEW_ID (${targetViewId.value})", cc = Some(cc)) {
              isValidCustomViewId(targetViewId.value)
            }
            _ <- Helper.booleanToFuture(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${CAN_DELETE_CUSTOM_VIEW}` permission on any your views.Current VIEW_ID (${viewId.value})", cc = Some(cc)) {
              view.allowed_actions.exists(_ == CAN_DELETE_CUSTOM_VIEW)
            }
            _ <- ViewNewStyle.customView(targetViewId, BankIdAccountId(bankId, accountId), Some(cc))
            deleted <- ViewNewStyle.removeCustomView(targetViewId, BankIdAccountId(bankId, accountId), Some(cc))
          } yield deleted
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteCustomView),
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/target-views/TARGET_VIEW_ID",
      "Delete Custom View",
      "Deletes the custom view specified by VIEW_ID on the bank account specified by ACCOUNT_ID at bank BANK_ID",
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView, UnknownError),
      List(apiTagView, apiTagAccount),
      None,
      http4sPartialFunction = Some(deleteCustomView)
    )

    // ─── Bank account balance CRUD (4) ────────────────────────────────────

    val createBankAccountBalance: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "balances" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr)
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $BankAccountBalanceRequestJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[BankAccountBalanceRequestJsonV510]
            }
            balanceAmount <- NewStyle.function.tryons(s"$InvalidNumber Current balance_amount is  ${postedData.balance_amount}", 400, Some(cc)) {
              BigDecimal(postedData.balance_amount)
            }
            (balance, _) <- code.api.util.newstyle.BankAccountBalanceNewStyle.createOrUpdateBankAccountBalance(
              bankId, accountId, None, postedData.balance_type, balanceAmount, Some(cc))
          } yield JSONFactory510.createBankAccountBalanceJson(balance)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createBankAccountBalance),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/balances",
      "Create Bank Account Balance",
      s"""Create a new Balance for a Bank Account.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      bankAccountBalanceRequestJsonV510,
      bankAccountBalanceResponseJsonV510,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagAccount, apiTagBalance),
      Some(List(canCreateBankAccountBalance)),
      http4sPartialFunction = Some(createBankAccountBalance)
    )

    val getBankAccountBalanceById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / "balances" / balanceIdStr =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (balance, _) <- code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountBalanceById(BalanceId(balanceIdStr), Some(cc))
          } yield JSONFactory510.createBankAccountBalanceJson(balance)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getBankAccountBalanceById),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/balances/BALANCE_ID",
      "Get Bank Account Balance By ID",
      s"""Get a specific Bank Account Balance by its BALANCE_ID.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      bankAccountBalanceResponseJsonV510,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagAccount, apiTagBalance),
      None,
      http4sPartialFunction = Some(getBankAccountBalanceById)
    )

    val updateBankAccountBalance: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "balances" / balanceIdStr =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr); val accountId = AccountId(accountIdStr); val balanceId = BalanceId(balanceIdStr)
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the BankAccountBalanceRequestJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[BankAccountBalanceRequestJsonV510]
            }
            balanceAmount <- NewStyle.function.tryons(s"$InvalidNumber Current balance_amount is  ${postedData.balance_amount}", 400, Some(cc)) {
              BigDecimal(postedData.balance_amount)
            }
            (_, _) <- code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountBalanceById(balanceId, Some(cc))
            (updated, _) <- code.api.util.newstyle.BankAccountBalanceNewStyle.createOrUpdateBankAccountBalance(
              bankId, accountId, Some(balanceId), postedData.balance_type, balanceAmount, Some(cc))
          } yield JSONFactory510.createBankAccountBalanceJson(updated)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateBankAccountBalance),
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/balances/BALANCE_ID",
      "Update Bank Account Balance",
      s"""Update an existing Bank Account Balance specified by BALANCE_ID.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      bankAccountBalanceRequestJsonV510,
      bankAccountBalanceResponseJsonV510,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagAccount, apiTagBalance),
      Some(List(canUpdateBankAccountBalance)),
      http4sPartialFunction = Some(updateBankAccountBalance)
    )

    val deleteBankAccountBalance: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "accounts" / _ / "balances" / balanceIdStr =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          val balanceId = BalanceId(balanceIdStr)
          for {
            (_, _) <- code.api.util.newstyle.BankAccountBalanceNewStyle.getBankAccountBalanceById(balanceId, Some(cc))
            (deleted, _) <- code.api.util.newstyle.BankAccountBalanceNewStyle.deleteBankAccountBalance(balanceId, Some(cc))
          } yield deleted
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteBankAccountBalance),
      "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/balances/BALANCE_ID",
      "Delete Bank Account Balance",
      s"""Delete a Bank Account Balance specified by BALANCE_ID.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      EmptyBody,
      List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagAccount, apiTagBalance),
      Some(List(canDeleteBankAccountBalance)),
      http4sPartialFunction = Some(deleteBankAccountBalance)
    )

    // ─── System view permissions (2) ──────────────────────────────────────

    val addSystemViewPermission: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "system-views" / viewIdStr / "permissions" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val viewId = ViewId(viewIdStr)
          for {
            createJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CreateViewPermissionJson ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[CreateViewPermissionJson]
            }
            _ <- Helper.booleanToFuture(s"$InvalidViewPermissionName The current value is ${createJson.permission_name}", 400, Some(cc)) {
              ALL_VIEW_PERMISSION_NAMES.exists(_ == createJson.permission_name)
            }
            _ <- ViewNewStyle.systemView(viewId, Some(cc))
            _ <- Helper.booleanToFuture(s"$ViewPermissionNameExists The current value is ${createJson.permission_name}", 400, Some(cc)) {
              ViewPermission.findSystemViewPermission(viewId, createJson.permission_name).isEmpty
            }
            (viewPermission, _) <- ViewNewStyle.createSystemViewPermission(viewId, createJson.permission_name, createJson.extra_data, Some(cc))
          } yield JSONFactory510.createViewPermissionJson(viewPermission)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(addSystemViewPermission), "POST",
      "/system-views/VIEW_ID/permissions", "Add Permission to a System View",
      """Add Permission to a System View.""",
      createViewPermissionJson, entitlementJSON,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, IncorrectRoleName, EntitlementAlreadyExists, UnknownError),
      List(apiTagSystemView),
      Some(List(canCreateSystemViewPermission)),
      http4sPartialFunction = Some(addSystemViewPermission)
    )

    val deleteSystemViewPermission: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "system-views" / viewIdStr / "permissions" / permissionName =>
        EndpointHelpers.withUserDelete(req) { (_, cc) =>
          val viewId = ViewId(viewIdStr)
          for {
            (viewPermission, _) <- ViewNewStyle.findSystemViewPermission(viewId, permissionName, Some(cc))
            _ <- Helper.booleanToFuture(s"$DeleteViewPermissionError The current value is $permissionName", 400, Some(cc)) {
              viewPermission.delete_!
            }
          } yield true
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(deleteSystemViewPermission), "DELETE",
      "/system-views/VIEW_ID/permissions/PERMISSION_NAME", "Delete Permission to a System View",
      """Delete Permission to a System View
      """.stripMargin,
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagSystemView),
      Some(List(canDeleteSystemViewPermission)),
      http4sPartialFunction = Some(deleteSystemViewPermission)
    )

    // ─── Consents family (12) ─────────────────────────────────────────────

    val updateConsentStatusByConsent: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "banks" / _ / "consents" / consentId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            consentJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PutConsentStatusJsonV400 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PutConsentStatusJsonV400]
            }
            _ <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
              .map(unboxFullOrFail(_, Some(cc), s"$ConsentNotFound ($consentId)", 404))
            status = ConsentStatus.withName(consentJson.status)
            consent <- Future(Consents.consentProvider.vend.updateConsentStatus(consentId, status))
              .map(i => connectorEmptyResponse(i, Some(cc)))
          } yield ConsentJsonV310(consent.consentId, consent.jsonWebToken, consent.status)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateConsentStatusByConsent),
      "PUT",
      "/management/banks/BANK_ID/consents/CONSENT_ID",
      "Update Consent Status by CONSENT_ID",
      s"""
      |
      |
      |This endpoint is used to update the Status of Consent.
      |
      |Each Consent has one of the following states: ${ConsentStatus.values.toList.sorted.mkString(", ")}.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      PutConsentStatusJsonV400(status = "AUTHORISED"),
      ConsentChallengeJsonV310(
        consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
        jwt = "eyJhbGciOiJIUzI1NiJ9.eyJlbnRpdGxlbWVudHMiOltdLCJjcmVhdGVkQnlVc2VySWQiOiJhYjY1MzlhOS1iMTA1LTQ0ODktYTg4My0wYWQ4ZDZjNjE2NTciLCJzdWIiOiIyMWUxYzhjYy1mOTE4LTRlYWMtYjhlMy01ZTVlZWM2YjNiNGIiLCJhdWQiOiJlanpuazUwNWQxMzJyeW9tbmhieDFxbXRvaHVyYnNiYjBraWphanNrIiwibmJmIjoxNTUzNTU0ODk5LCJpc3MiOiJodHRwczpcL1wvd3d3Lm9wZW5iYW5rcHJvamVjdC5jb20iLCJleHAiOjE1NTM1NTg0OTksImlhdCI6MTU1MzU1NDg5OSwianRpIjoiMDlmODhkNWYtZWNlNi00Mzk4LThlOTktNjYxMWZhMWNkYmQ1Iiwidmlld3MiOlt7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAxIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifSx7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAyIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifV19.8cc7cBEf2NyQvJoukBCmDLT7LXYcuzTcSYLqSpbxLp4",
        status = "AUTHORISED"
      ),
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, ConsentNotFound, InvalidConnectorResponse, UnknownError),
      apiTagConsent :: apiTagPSD2AIS :: Nil,
      Some(List(canUpdateConsentStatusAtOneBank, canUpdateConsentStatusAtAnyBank)),
      http4sPartialFunction = Some(updateConsentStatusByConsent)
    )

    val updateConsentAccountAccessByConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "banks" / _ / "consents" / consentId / "account-access" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
              .map(unboxFullOrFail(_, Some(cc), s"$ConsentNotFound ($consentId)", 404))
            consentJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PutConsentPayloadJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PutConsentPayloadJsonV510]
            }
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat The Json body should be the $PutConsentPayloadJsonV510 ", 400, Some(cc)) {
              !(consentJson.access.accounts.isEmpty && consentJson.access.balances.isEmpty && consentJson.access.transactions.isEmpty)
            }
            consentJWT <- Consent.updateAccountAccessOfBerlinGroupConsentJWT(consentJson.access, consent, Some(cc))
              .map(i => connectorEmptyResponse(i, Some(cc)))
            updatedConsent <- Future(Consents.consentProvider.vend.setJsonWebToken(consent.consentId, consentJWT))
              .map(i => connectorEmptyResponse(i, Some(cc)))
          } yield ConsentJsonV310(updatedConsent.consentId, updatedConsent.jsonWebToken, updatedConsent.status)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateConsentAccountAccessByConsentId),
      "PUT",
      "/management/banks/BANK_ID/consents/CONSENT_ID/account-access",
      "Update Consent Account Access by CONSENT_ID",
      s"""
      |
      |This endpoint is used to update the Account Access of Consent.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      PutConsentPayloadJsonV510(
        access = ConsentAccessJson(
          accounts = Option(List(ConsentAccessAccountsJson(
            iban = Some(ExampleValue.ibanExample.value),
            bban = None,
            pan = None,
            maskedPan = None,
            msisdn = None,
            currency = None,
          )))
        )
      ),
      ConsentChallengeJsonV310(
        consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
        jwt = "eyJhbGciOiJIUzI1NiJ9.eyJlbnRpdGxlbWVudHMiOltdLCJjcmVhdGVkQnlVc2VySWQiOiJhYjY1MzlhOS1iMTA1LTQ0ODktYTg4My0wYWQ4ZDZjNjE2NTciLCJzdWIiOiIyMWUxYzhjYy1mOTE4LTRlYWMtYjhlMy01ZTVlZWM2YjNiNGIiLCJhdWQiOiJlanpuazUwNWQxMzJyeW9tbmhieDFxbXRvaHVyYnNiYjBraWphanNrIiwibmJmIjoxNTUzNTU0ODk5LCJpc3MiOiJodHRwczpcL1wvd3d3Lm9wZW5iYW5rcHJvamVjdC5jb20iLCJleHAiOjE1NTM1NTg0OTksImlhdCI6MTU1MzU1NDg5OSwianRpIjoiMDlmODhkNWYtZWNlNi00Mzk4LThlOTktNjYxMWZhMWNkYmQ1Iiwidmlld3MiOlt7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAxIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifSx7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAyIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifV19.8cc7cBEf2NyQvJoukBCmDLT7LXYcuzTcSYLqSpbxLp4",
        status = "AUTHORISED"
      ),
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, ConsentNotFound, InvalidConnectorResponse, UnknownError),
      apiTagConsent :: apiTagPSD2AIS :: Nil,
      Some(List(canUpdateConsentAccountAccessAtOneBank, canUpdateConsentAccountAccessAtAnyBank)),
      http4sPartialFunction = Some(updateConsentAccountAccessByConsentId)
    )

    val updateConsentUserIdByConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "banks" / _ / "consents" / consentId / "created-by-user" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
              .map(unboxFullOrFail(_, Some(cc), s"$ConsentNotFound ($consentId)", 404))
            consentJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PutConsentUserJsonV400 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PutConsentUserJsonV400]
            }
            user <- Users.users.vend.getUserByUserIdFuture(consentJson.user_id)
              .map(x => unboxFullOrFail(x, Some(cc), s"$UserNotFoundByUserId Current UserId(${consentJson.user_id})"))
            consent2 <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
              .map(i => connectorEmptyResponse(i, Some(cc)))
            _ <- Helper.booleanToFuture(ConsentUserAlreadyAdded, cc = Some(cc)) {
              Option(consent2.userId).forall(_.isBlank)
            }
            consent3 <- Future(Consents.consentProvider.vend.updateConsentUser(consentId, user))
              .map(i => connectorEmptyResponse(i, Some(cc)))
            consentJWT <- Future(Consent.updateUserIdOfBerlinGroupConsentJWT(consentJson.user_id, consent3, Some(cc)))
              .map(i => connectorEmptyResponse(i, Some(cc)))
            updatedConsent <- Future(Consents.consentProvider.vend.setJsonWebToken(consent3.consentId, consentJWT))
              .map(i => connectorEmptyResponse(i, Some(cc)))
          } yield ConsentJsonV310(updatedConsent.consentId, updatedConsent.jsonWebToken, updatedConsent.status)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateConsentUserIdByConsentId),
      "PUT",
      "/management/banks/BANK_ID/consents/CONSENT_ID/created-by-user",
      "Update Created by User of Consent by CONSENT_ID",
      s"""
      |
      |This endpoint is used to Update the User bound to a consent.
      |
      |In general we would not expect for a management user to set the User bound to a consent, but there may be
      |some use cases where this workflow is useful.
      |
      |If successful, the "Created by User ID" field in the OBP Consent table will be updated.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      PutConsentUserJsonV400(user_id = "ed7a7c01-db37-45cc-ba12-0ae8891c195c"),
      ConsentChallengeJsonV310(
        consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
        jwt = "eyJhbGciOiJIUzI1NiJ9.eyJlbnRpdGxlbWVudHMiOltdLCJjcmVhdGVkQnlVc2VySWQiOiJhYjY1MzlhOS1iMTA1LTQ0ODktYTg4My0wYWQ4ZDZjNjE2NTciLCJzdWIiOiIyMWUxYzhjYy1mOTE4LTRlYWMtYjhlMy01ZTVlZWM2YjNiNGIiLCJhdWQiOiJlanpuazUwNWQxMzJyeW9tbmhieDFxbXRvaHVyYnNiYjBraWphanNrIiwibmJmIjoxNTUzNTU0ODk5LCJpc3MiOiJodHRwczpcL1wvd3d3Lm9wZW5iYW5rcHJvamVjdC5jb20iLCJleHAiOjE1NTM1NTg0OTksImlhdCI6MTU1MzU1NDg5OSwianRpIjoiMDlmODhkNWYtZWNlNi00Mzk4LThlOTktNjYxMWZhMWNkYmQ1Iiwidmlld3MiOlt7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAxIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifSx7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAyIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifV19.8cc7cBEf2NyQvJoukBCmDLT7LXYcuzTcSYLqSpbxLp4",
        status = "AUTHORISED"
      ),
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, ConsentNotFound, InvalidConnectorResponse, UnknownError),
      apiTagConsent :: apiTagPSD2AIS :: Nil,
      Some(List(canUpdateConsentUserAtOneBank, canUpdateConsentUserAtAnyBank)),
      http4sPartialFunction = Some(updateConsentUserIdByConsentId)
    )

    // Start SCA for a UK Open Banking consent: issue a one-time challenge (OTP) to the PSU.
    // Uses the shared OBP challenge engine (createChallengesC2) — the same one Berlin Group
    // uses — with ChallengeType.OBP_CONSENT_CHALLENGE, which is status-agnostic and so works
    // on an AWAITINGAUTHORISATION consent. The OTP is delivered per the configured SCA method
    // (props suggested_default_sca_method: DUMMY answer "123" for dev, EMAIL/SMS for prod).
    val authoriseUKConsentChallenge: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "consents" / consentId / "authorise" / "challenge" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val scaMethod = getSuggestedDefaultScaMethod()
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
              .map(unboxFullOrFail(_, Some(cc), s"$ConsentNotFound ($consentId)", 404))
            _ <- Helper.booleanToFuture(s"$ConsentStatusIssue one of ${ukReAuthableStatuses.mkString(", ")} to start SCA (current: ${consent.status}).", 400, Some(cc)) {
              ukReAuthableStatuses.contains(consent.status.toUpperCase)
            }
            // Re-authentication is only allowed before the consent's ExpirationDateTime (a null
            // ExpirationDateTime = never expires); an expired consent must be recreated.
            _ <- Helper.booleanToFuture(s"$ConsentExpiredIssue", 400, Some(cc)) {
              Option(consent.expirationDateTime).forall(_.getTime >= System.currentTimeMillis)
            }
            // A consent already bound to a PSU may only be (re-)authorised by that same PSU --
            // otherwise a different user of the same consumer could hijack it.
            _ <- Helper.booleanToFuture(s"$ConsentDoesNotMatchUser", 403, Some(cc)) {
              Option(consent.userId).forall(_.isBlank) || consent.userId == user.userId
            }
            (challenges, _) <- NewStyle.function.createChallengesC2(
              List(user.userId),
              ChallengeType.OBP_CONSENT_CHALLENGE,
              None,
              scaMethod,
              Some(StrongCustomerAuthenticationStatus.received),
              Some(consentId),
              None,
              Some(cc))
            challenge <- NewStyle.function.tryons(s"$InvalidConnectorResponseForCreateChallenge", 400, Some(cc)) {
              challenges.head
            }
          } yield UKConsentScaChallengeJsonV510(
            challenge.challengeId,
            challenge.scaStatus.map(_.toString).getOrElse(StrongCustomerAuthenticationStatus.received.toString),
            scaMethod.map(_.toString).getOrElse("")
          )
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(authoriseUKConsentChallenge),
      "POST",
      "/banks/BANK_ID/consents/CONSENT_ID/authorise/challenge",
      "Start UK Consent SCA Challenge",
      s"""
      |
      |Start Strong Customer Authentication for a UK Open Banking account-access consent: issue a
      |one-time challenge (OTP) to the current (PSU) user, delivered per the configured SCA method.
      |
      |Call this before `POST /banks/BANK_ID/consents/CONSENT_ID/authorise`, then submit the
      |returned `challenge_id` together with the OTP answer to that endpoint to complete authorisation.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      UKConsentScaChallengeJsonV510("74a8ebda-9e5a-4c3f-9b0b-1a2b3c4d5e6f", "received", "SMS"),
      List($AuthenticatedUserIsRequired, ConsentNotFound, ConsentStatusIssue, ConsentExpiredIssue, ConsentDoesNotMatchUser, InvalidConnectorResponse, UnknownError),
      apiTagConsent :: apiTagPSD2AIS :: Nil,
      None,
      http4sPartialFunction = Some(authoriseUKConsentChallenge)
    )

    // Authorise a UK Open Banking account-access consent as the current PSU, after SCA.
    //
    // UK consents are lodged by the TPP via client_credentials (no user, status
    // AWAITINGAUTHORISATION). After the PSU authenticates and answers the SCA challenge
    // (started via .../authorise/challenge), Portal calls this endpoint with the PSU's own
    // access token + the challenge answer to verify SCA, bind the consent to that user, and
    // flip it to AUTHORISED — the missing "authorisation binding" step of the UK flow.
    // User-authenticated but role-free (the account holder is approving their own consent).
    val authoriseUKConsent: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "consents" / consentId / "authorise" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
              .map(unboxFullOrFail(_, Some(cc), s"$ConsentNotFound ($consentId)", 404))
            // The initial authorisation (AWAITINGAUTHORISATION) and re-authentication of an
            // already-authorised or dashboard-revoked (wire: CANC) consent are both allowed --
            // see ukReAuthableStatuses. EXPIRED/REJECTED are terminal. These statuses are
            // UK-specific (Berlin Group uses "received", OBP uses INITIATED), so the guard also
            // effectively scopes this endpoint to the UK flow.
            _ <- Helper.booleanToFuture(s"$ConsentStatusIssue one of ${ukReAuthableStatuses.mkString(", ")} to be authorised (current: ${consent.status}).", 400, Some(cc)) {
              ukReAuthableStatuses.contains(consent.status.toUpperCase)
            }
            // Re-authentication is only allowed before the consent's ExpirationDateTime (a null
            // ExpirationDateTime = never expires); an expired consent must be recreated.
            _ <- Helper.booleanToFuture(s"$ConsentExpiredIssue", 400, Some(cc)) {
              Option(consent.expirationDateTime).forall(_.getTime >= System.currentTimeMillis)
            }
            // A consent already bound to a PSU may only be (re-)authorised by that same PSU --
            // otherwise a different user of the same consumer could hijack it.
            _ <- Helper.booleanToFuture(s"$ConsentDoesNotMatchUser", 403, Some(cc)) {
              Option(consent.userId).forall(_.isBlank) || consent.userId == user.userId
            }
            // Verify the SCA challenge answer before authorising (dynamic linking to this consent).
            // The challenge must have been started via POST .../authorise/challenge.
            authJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostUKConsentAuthoriseJsonV510 ", 400, Some(cc)) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PostUKConsentAuthoriseJsonV510]
            }
            // The PSU must select at least one account for the consented permissions to bind to —
            // see grantUKConsentAccountAccess (Gap 4 remediation: previously the consent's
            // Permissions were never bound to a real account and had zero enforcement effect).
            _ <- Helper.booleanToFuture(s"$InvalidJsonFormat The Json body should be the $PostUKConsentAuthoriseJsonV510 (account_ids must not be empty) ", 400, Some(cc)) {
              authJson.account_ids.nonEmpty
            }
            (startedChallenge, _) <- NewStyle.function.getChallenge(authJson.challenge_id, Some(cc))
            // The consent this challenge was minted for is the consent it may authorise, and nothing
            // below establishes that: validateChallengeAnswerC4 is handed the consentId but the
            // connector ignores it and matches on challengeId, answer and expected user alone. So a
            // PSU holding an OTP raised for one consent could answer it against another of their own
            // -- and since the two need not carry the same Permissions, an OTP the PSU was given for
            // a narrow consent authorised a wider one, which is precisely the dynamic linking the
            // profile is asking for ("all authentication journeys take place in the context of a
            // consent"; the ConsentId is the intent identifier).
            //
            // Same guard, message and code as the Berlin Group twin in Http4sBGv13AIS, which gets
            // the binding structurally by nesting authorisations under the consent's own path. It
            // also refuses a transaction-request challenge here for free: that carries no consentId.
            _ <- Helper.booleanToFuture(s"$InvalidChallengeChallengeId Current challengeId(${authJson.challenge_id}) does not belong to CONSENTID($consentId) ", 400, Some(cc)) {
              startedChallenge.consentId.contains(consentId)
            }
            (challenge, _) <- NewStyle.function.validateChallengeAnswerC4(
              ChallengeType.OBP_CONSENT_CHALLENGE,
              None,
              Some(consentId),
              authJson.challenge_id,
              authJson.answer,
              SuppliedAnswerType.PLAIN_TEXT_VALUE,
              Some(cc))
            _ <- Helper.booleanToFuture(s"$InvalidChallengeAnswer", 403, Some(cc)) {
              challenge.scaStatus.contains(StrongCustomerAuthenticationStatus.finalised)
            }
            // Bind the consented permissions to the PSU-selected accounts, replacing the
            // (bank_id=null, account_id=null) dead views createUKConsentJWT wrote at consent
            // creation time.
            //
            // This runs before anything is written because it is the last step that can still
            // refuse the request: it rejects an account_id the PSU does not hold
            // (ConsentAccountNotHeldByUser) or one that does not exist at this bank. It used to run
            // after updateConsentUser, and a refused authorisation therefore left the consent bound
            // to whoever attempted it -- status still AWAITINGAUTHORISATION, mUserId now the
            // caller. The ConsentDoesNotMatchUser guard above then locked the real PSU out of their
            // own consent, and the lodging TPP lost it too, with no way back through the API. A
            // consent id travels to the browser in the authorisation redirect, so a single failing
            // request from anyone who had seen one was enough. Nothing here is transactional, so
            // ordering is what has to carry it: refuse first, commit afterwards.
            //
            // Not connectorEmptyResponse: that turns every Box into InvalidConnectorResponse at
            // 400, so the refusal reached the TPP as "OBP-50200 Connector cannot return the data
            // we requested. connectorEmptyResponse <- OBP-35037 ..." -- an authorisation decision
            // presented as a connector fault, with the reason buried behind a cause it has
            // nothing to do with. A Failure here carries its own message and is the same kind of
            // answer as the ConsentDoesNotMatchUser guard above, so it gets the same 403. Only a
            // genuinely empty Box is a connector problem.
            _ <- Consent.grantUKConsentAccountAccess(user, BankId(bankIdStr), authJson.account_ids, consent, Some(cc))
              .flatMap {
                case Full(granted) => Future.successful(granted)
                case Failure(reason, _, _) =>
                  // booleanToFuture(false) always fails, so the mapped value is never reached --
                  // it only lines the branches up to one type.
                  Helper.booleanToFuture(reason, 403, Some(cc))(false).map(_ => consent)
                case Empty => Future.successful(connectorEmptyResponse(Empty: Box[MappedConsent], Some(cc)))
              }
            // Bind the PSU as the consent's user in the DB (mUserId).
            consentAfterBind <- Future(Consents.consentProvider.vend.updateConsentUser(consentId, user))
              .map(i => connectorEmptyResponse(i, Some(cc)))
            // Also stamp the PSU into the consent JWT's createdByUserId, so consent-scoped
            // identity resolution (e.g. GET /users/current) reports the authorising user
            // rather than the client_credentials pseudo-user the consent was lodged under.
            // Despite its name, updateUserIdOfBerlinGroupConsentJWT only rewrites
            // createdByUserId (via ConsentJWT.copy) — the UK permission views are preserved.
            // updateConsentUser re-reads the row from the database, so the JWT copied here is the
            // one grantUKConsentAccountAccess just wrote, views and all.
            updatedJwt <- Future(Consent.updateUserIdOfBerlinGroupConsentJWT(user.userId, consentAfterBind, Some(cc)))
              .map(i => connectorEmptyResponse(i, Some(cc)))
            consentWithUser <- Future(Consents.consentProvider.vend.setJsonWebToken(consentId, updatedJwt))
              .map(i => connectorEmptyResponse(i, Some(cc)))
            updatedConsent <- Future(Consents.consentProvider.vend.updateConsentStatus(consentWithUser.consentId, ConsentStatus.AUTHORISED))
              .map(i => connectorEmptyResponse(i, Some(cc)))
          } yield ConsentJsonV310(updatedConsent.consentId, updatedConsent.jsonWebToken, updatedConsent.status)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(authoriseUKConsent),
      "POST",
      "/banks/BANK_ID/consents/CONSENT_ID/authorise",
      "Authorise UK Consent",
      s"""
      |
      |Authorise a UK Open Banking account-access consent as the current (PSU) user, after SCA.
      |
      |The TPP first lodges the intent via `POST /account-access-consents`; the consent is
      |created in ${ConsentStatus.AWAITINGAUTHORISATION} state with no bound user and no bound
      |accounts. The PSU then starts SCA via `POST .../authorise/challenge` and submits the
      |resulting `challenge_id` plus the OTP `answer`, together with the `account_ids` the PSU
      |is selecting for this consent, here. On a valid answer this binds the consent to the PSU
      |and to those accounts — every permission the consent declared is granted on each selected
      |account — and transitions it to ${ConsentStatus.AUTHORISED}, so subsequent UK data calls
      |whose access token carries the `consent_id` claim pass the consent check and are scoped to
      |exactly the accounts and permissions the PSU approved.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      PostUKConsentAuthoriseJsonV510("74a8ebda-9e5a-4c3f-9b0b-1a2b3c4d5e6f", "123", List("8ca8a7e4-6d05-4b21-a165-c02c39d77e55")),
      ConsentJsonV310(
        "9d429899-24f5-42c8-8565-943ffa6a7945",
        "eyJhbGciOiJIUzI1NiJ9.eyJ2aWV3cyI6W119.signature",
        "AUTHORISED"
      ),
      List($AuthenticatedUserIsRequired, ConsentNotFound, ConsentStatusIssue, ConsentExpiredIssue, ConsentDoesNotMatchUser, InvalidJsonFormat, InvalidChallengeAnswer, $BankAccountNotFound, InvalidConnectorResponse, UnknownError),
      apiTagConsent :: apiTagPSD2AIS :: Nil,
      None,
      http4sPartialFunction = Some(authoriseUKConsent)
    )

    val getMyConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "my" / "consents" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val params = req.uri.query.multiParams
          val limitParam = params.get("limit").flatMap(_.headOption).flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(50)
          val offsetParam = params.get("offset").flatMap(_.headOption).flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
          val statusParam = params.get("status").flatMap(_.headOption)
          val sortByParam = params.get("sort_by").flatMap(_.headOption).getOrElse("created_date:desc")
          val sortParts = sortByParam.split(":").map(_.trim.toLowerCase)
          val sortField = sortParts(0)
          val sortDirection = sortParts.lift(1).getOrElse("desc")
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            rows <- Future {
              code.consent.DoobieConsentQueries.getConsentsByUser(
                userId = user.userId, status = statusParam,
                limit = limitParam, offset = offsetParam,
                sortField = sortField, sortDirection = sortDirection)
            }
          } yield ConsentsInfoJsonV510(rows.map(Implementations5_1_0.rowToConsentInfoJsonV510))
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getMyConsents),
      "GET",
      "/my/consents",
      "Get My Consents",
      s"""
         |
         |This endpoint gets the Consents created by the current User.
         |
         |${userAuthenticationMessage(true)}
         |
         |1 limit (for pagination: defaults to 50)  eg:limit=200
         |
         |2 offset (for pagination: zero index, defaults to 0) eg: offset=10
         |
         |3 status  (ignore if omitted)
         |
         |4 sort_by (defaults to created_date:desc)  eg: sort_by=created_date:desc
         |
         |eg: /my/consents?limit=10&offset=0&sort_by=created_date:desc
         |
      """.stripMargin,
      EmptyBody,
      consentsInfoJsonV510,
      List($AuthenticatedUserIsRequired, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(getMyConsents)
    )

    val getConsentsAtBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consents" / "banks" / bankIdStr =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            (consents, totalPages) <- Future(Consents.consentProvider.vend.getConsents(obpQueryParams))
          } yield {
            val consentsOfBank = Consent.filterByBankId(consents, bankId)
            createConsentsJsonV510(consentsOfBank, totalPages)
          }
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getConsentsAtBank),
      "GET",
      "/management/consents/banks/BANK_ID",
      "Get Consents at Bank",
      s"""
         |
         |This endpoint gets the Consents at Bank by BANK_ID.
         |
         |${userAuthenticationMessage(true)}
         |
         |1 limit (for pagination: defaults to 50)  eg:limit=200
         |
         |2 offset (for pagination: zero index, defaults to 0) eg: offset=10
         |
         |3 consumer_id  (ignore if omitted)
         |
         |4 user_id  (ignore if omitted)
         |
         |5 status  (ignore if omitted)
         |
         |eg: /management/consents/banks/BANK_ID?&consumer_id=78&limit=10&offset=10
         |
      """.stripMargin,
      EmptyBody,
      consentsJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      Some(List(canGetConsentsAtOneBank, canGetConsentsAtAnyBank)),
      http4sPartialFunction = Some(getConsentsAtBank)
    )

    val getConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consents" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(req.uri.renderString)
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            (consents, totalPages) <- Future(Consents.consentProvider.vend.getConsents(obpQueryParams))
          } yield createConsentsJsonV510(consents, totalPages)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getConsents),
      "GET",
      "/management/consents",
      "Get Consents",
      s"""
         |
         |This endpoint gets the Consents.
         |
         |${userAuthenticationMessage(true)}
         |
         |1 limit (for pagination: defaults to 50)  eg:limit=200
         |
         |2 offset (for pagination: zero index, defaults to 0) eg: offset=10
         |
         |3 consumer_id  (ignore if omitted)
         |
         |4 consent_id  (ignore if omitted)
         |
         |5 user_id  (ignore if omitted)
         |
         |6 status  (ignore if omitted)
         |
         |7 bank_id  (ignore if omitted)
         |
         |8 provider_provider_id  (ignore if omitted)
         |provider and provider_id values are separated by pipe char
         |eg: provider_provider_id=http%3A%2F%2Flocalhost%3A7070%2Frealms%2Fmaster|7837ee9c-3446-4d8c-9b90-301a52b4851d
         |
         |eg:/management/consents?consumer_id=78&limit=10&offset=10
         |
      """.stripMargin,
      EmptyBody,
      consentsJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      Some(List(canGetConsentsAtAnyBank)),
      http4sPartialFunction = Some(getConsents)
    )

    val getConsentByConsentId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "user" / "current" / "consents" / consentId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
              .map(unboxFullOrFail(_, Some(cc), ConsentNotFound, 404))
            // cc.onBehalfOfUser, not cc.userId: under consent authentication the authenticated user
            // is the consent user, so comparing it against the consent's PSU never matched and
            // the PSU got a 404 for their own consent. See Consent.checkObpConsentUserAccess for
            // why an unbound consent stays readable.
            _ <- Consent.checkObpConsentUserAccess(consent.userId, cc.onBehalfOfUser.toOption.map(_.userId)) match {
              case Some(reason) => Helper.booleanToFuture(failMsg = reason, failCode = 404, cc = Some(cc))(false)
              case None => Future.successful(true)
            }
          } yield JSONFactory510.getConsentInfoJson(consent)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getConsentByConsentId),
      "GET",
      "/user/current/consents/CONSENT_ID",
      "Get Consent By Consent Id via User",
      s"""
         |
         |This endpoint gets the Consent By consent id.
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      consentJsonV510,
      List($AuthenticatedUserIsRequired, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(getConsentByConsentId)
    )

    val getConsentByConsentIdViaConsumer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "consumer" / "current" / "consents" / consentId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
              .map(unboxFullOrFail(_, Some(cc), ConsentNotFound, 404))
            _ <- Helper.booleanToFuture(failMsg = ConsentNotFound, failCode = 404, cc = Some(cc)) {
              consent.mConsumerId.get == cc.consumer.map(_.consumerId.get).getOrElse("None")
            }
          } yield JSONFactory510.getConsentInfoJson(consent)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getConsentByConsentIdViaConsumer),
      "GET",
      "/consumer/current/consents/CONSENT_ID",
      "Get Consent By Consent Id via Consumer",
      s"""
         |
         |This endpoint gets the Consent By consent id.
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      consentJsonV500,
      List($AuthenticatedUserIsRequired, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(getConsentByConsentIdViaConsumer)
    )

    val revokeConsentAtBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / bankIdStr / "consents" / consentId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            (_, _) <- NewStyle.function.getBank(bankId, Some(cc))
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
              .map(unboxFullOrFail(_, Some(cc), ConsentNotFound))
            _ <- Helper.booleanToFuture(failMsg = ConsentNotFound, cc = Some(cc)) {
              consent.mUserId == user.userId
            }
            revoked <- Future(Consents.consentProvider.vend.revoke(consentId))
              .map(i => connectorEmptyResponse(i, Some(cc)))
          } yield ConsentJsonV310(revoked.consentId, revoked.jsonWebToken, revoked.status)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(revokeConsentAtBank),
      "DELETE",
      "/banks/BANK_ID/consents/CONSENT_ID",
      "Revoke Consent at Bank",
      s"""
         |Revoke Consent specified by CONSENT_ID
         |
         |There are a few reasons you might need to revoke an application’s access to a user’s account:
         |  - The user explicitly wishes to revoke the application’s access
         |  - You as the service provider have determined an application is compromised or malicious, and want to disable it
         |  - etc.
         ||
         |OBP as a resource server stores access tokens in a database, then it is relatively easy to revoke some token that belongs to a particular user.
         |The status of the token is changed to "REVOKED" so the next time the revoked client makes a request, their token will fail to validate.
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      revokedConsentJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      Some(List(canRevokeConsentAtBank)),
      http4sPartialFunction = Some(revokeConsentAtBank)
    )

    val selfRevokeConsent: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "my" / "consent" / "current" =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            _ <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            consentId = APIUtil.getConsentIdRequestHeaderValue(cc.requestHeaders).getOrElse("")
            _ <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
              .map(unboxFullOrFail(_, Some(cc), ConsentNotFound, 404))
            consent <- Future(Consents.consentProvider.vend.revoke(consentId))
              .map(i => connectorEmptyResponse(i, Some(cc)))
          } yield ConsentJsonV310(consent.consentId, consent.jsonWebToken, consent.status)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(selfRevokeConsent),
      "DELETE",
      "/my/consent/current",
      "Revoke Consent used in the Current Call",
      s"""
         |Revoke Consent specified by Consent-Id at Request Header
         |
         |There are a few reasons you might need to revoke an application’s access to a user’s account:
         |  - The user explicitly wishes to revoke the application’s access
         |  - You as the service provider have determined an application is compromised or malicious, and want to disable it
         |  - etc.
         ||
         |OBP as a resource server stores access tokens in a database, then it is relatively easy to revoke some token that belongs to a particular user.
         |The status of the token is changed to "REVOKED" so the next time the revoked client makes a request, their token will fail to validate.
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      revokedConsentJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(selfRevokeConsent)
    )

    val generalObpConsentText: String =
      s"""
         |
         |An OBP Consent allows the holder of the Consent to call one or more endpoints.
         |
         |Consents must be created and authorisied using SCA (Strong Customer Authentication).
         |
         |That is, Consents can be created by an authorised User via the OBP REST API but they must be confirmed via an out of band (OOB) mechanism such as a code sent to a mobile phone.
         |
         |Each Consent has one of the following states: ${ConsentStatus.values.toList.sorted.mkString(", ")}.
         |
         |Each Consent is bound to a consumer i.e. you need to identify yourself over request header value Consumer-Key.
         |
         |Examples:
         |
         |For example:
         |GET /obp/v4.0.0/users/current HTTP/1.1
         |Host: 127.0.0.1:8080
         |Consent-JWT: eyJhbGciOiJIUzI1NiJ9.eyJlbnRpdGxlbWVudHMiOlt7InJvbGVfbmFtZSI6IkNhbkdldEFueVVzZXIiLCJiYW5rX2lkIjoiIn
         |1dLCJjcmVhdGVkQnlVc2VySWQiOiJhYjY1MzlhOS1iMTA1LTQ0ODktYTg4My0wYWQ4ZDZjNjE2NTciLCJzdWIiOiIzNDc1MDEzZi03YmY5LTQyNj
         |EtOWUxYy0xZTdlNWZjZTJlN2UiLCJhdWQiOiI4MTVhMGVmMS00YjZhLTQyMDUtYjExMi1lNDVmZDZmNGQzYWQiLCJuYmYiOjE1ODA3NDE2NjcsIml
         |zcyI6Imh0dHA6XC9cLzEyNy4wLjAuMTo4MDgwIiwiZXhwIjoxNTgwNzQ1MjY3LCJpYXQiOjE1ODA3NDE2NjcsImp0aSI6ImJkYzVjZTk5LTE2ZTY
         |tNDM4Yi1hNjllLTU3MTAzN2RhMTg3OCIsInZpZXdzIjpbXX0.L3fEEEhdCVr3qnmyRKBBUaIQ7dk1VjiFaEBW8hUNjfg
         |
         |Consumer-Key: ejznk505d132ryomnhbx1qmtohurbsbb0kijajsk
         |cache-control: no-cache
         |
         |Maximum time to live of the token is specified over props value consents.max_time_to_live. In case isn't defined default value is 7776000 seconds (90 days).
         |
         |Example of POST JSON:
         |{
         |  "everything": false,
         |  "views": [
         |    {
         |      "bank_id": "GENODEM1GLS",
         |      "account_id": "8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
         |      "view_id": "${Constant.SYSTEM_OWNER_VIEW_ID}"
         |    }
         |  ],
         |  "entitlements": [
         |    {
         |      "bank_id": "GENODEM1GLS",
         |      "role_name": "CanGetCustomersAtOneBank"
         |    }
         |  ],
         |  "consumer_id": "7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         |  "email": "eveline@example.com",
         |  "valid_from": "2020-02-07T08:43:34Z",
         |  "time_to_live": 3600
         |}
         |Please note that only optional fields are: consumer_id, valid_from and time_to_live.
         |In case you omit they the default values are used:
         |consumer_id = consumer of current user
         |valid_from = current time
         |time_to_live = consents.max_time_to_live
         |
      """.stripMargin

    // ─── createConsent (IMPLICIT alias) — handles SCA: EMAIL/SMS/IMPLICIT ──

    val revokeMyConsent: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "my" / "consents" / consentId =>
        EndpointHelpers.executeFuture(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId))
              .map(unboxFullOrFail(_, Some(cc), ConsentNotFound, 404))
            _ <- Helper.booleanToFuture(failMsg = ConsentNotFound, cc = Some(cc)) {
              consent.mUserId == user.userId
            }
            revoked <- Future(Consents.consentProvider.vend.revoke(consentId))
              .map(i => connectorEmptyResponse(i, Some(cc)))
          } yield ConsentJsonV310(revoked.consentId, revoked.jsonWebToken, revoked.status)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(revokeMyConsent), "DELETE",
      "/my/consents/CONSENT_ID", "Revoke My Consent",
      s"""
         |Revoke Consent for current user specified by CONSENT_ID
         |
         |There are a few reasons you might need to revoke an application’s access to a user’s account:
         |  - The user explicitly wishes to revoke the application’s access
         |  - You as the service provider have determined an application is compromised or malicious, and want to disable it
         |  - etc.
         |
         |Please note that this endpoint only supports the case:: "The user explicitly wishes to revoke the application’s access"
         |
         |OBP as a resource server stores access tokens in a database, then it is relatively easy to revoke some token that belongs to a particular user.
         |The status of the token is changed to "REVOKED" so the next time the revoked client makes a request, their token will fail to validate.
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody, revokedConsentJsonV310,
      List($AuthenticatedUserIsRequired, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(revokeMyConsent)
    )

    // Lift named this endpoint "createConsent"; test tag nameOf references still use that name.
    val createConsent: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "my" / "consents" / scaMethod
        if scaMethod == "EMAIL" || scaMethod == "SMS" || scaMethod == "IMPLICIT" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val callContextOpt = Some(cc)
          for {
            user <- Future.successful(cc.user.openOrThrowException(AuthenticatedUserIsRequired))
            _ <- Helper.booleanToFuture(ConsentAllowedScaMethods, cc = callContextOpt) {
              List(StrongCustomerAuthentication.SMS.toString(),
                   StrongCustomerAuthentication.EMAIL.toString(),
                   StrongCustomerAuthentication.IMPLICIT.toString()).contains(scaMethod)
            }
            consentJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostConsentBodyCommonJson ", 400, callContextOpt) {
              com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PostConsentBodyCommonJson]
            }
            maxTimeToLive = APIUtil.getPropsAsIntValue(nameOfProperty = "consents.max_time_to_live", defaultValue = Constant.DEFAULT_CONSENT_TTL)
            _ <- Helper.booleanToFuture(s"$ConsentMaxTTL ($maxTimeToLive)", cc = callContextOpt) {
              consentJson.time_to_live match {
                case Some(ttl) => ttl <= maxTimeToLive
                case _         => true
              }
            }
            requestedEntitlements = consentJson.entitlements
            // Reject CanCreateEntitlementAtAnyBank explicitly (same rule as the consent-request flow).
            // createConsentJWT drops it anyway, but silently omitting a requested role is worse
            // than a 400: the caller must never believe a consent carries a role it does not.
            // CanCreateEntitlementAtOneBank is allowed, see createConsentByConsentRequestId.
            _ <- Helper.booleanToFuture(RolesForbiddenInConsent, cc = callContextOpt) {
              !requestedEntitlements.map(_.role_name).contains(canCreateEntitlementAtAnyBank.toString())
            }
            myEntitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(user.userId)
            _ <- Helper.booleanToFuture(RolesAllowedInConsent, cc = callContextOpt) {
              requestedEntitlements.forall(re =>
                myEntitlements.getOrElse(Nil).exists(e => e.roleName == re.role_name && e.bankId == re.bank_id))
            }
            requestedViews = consentJson.views
            (_, assignedViews) <- Future(Views.views.vend.privateViewsUserCanAccess(user))
            _ <- Helper.booleanToFuture(ViewsAllowedInConsent, cc = callContextOpt) {
              requestedViews.forall(rv =>
                assignedViews.exists(e =>
                  e.view_id == rv.view_id && e.bank_id == rv.bank_id && e.account_id == rv.account_id))
            }
            consumerFromBodyTuple <- consentJson.consumer_id match {
              case Some(id) => NewStyle.function.checkConsumerByConsumerId(id, callContextOpt).map(c => (Some(c), c.description))
              case None     => Future.successful((None: Option[Consumer], "Any application"))
            }
            (consumerFromRequestBody, applicationText) = consumerFromBodyTuple
            challengeAnswer = Props.mode match {
              case Props.RunModes.Test => Consent.challengeAnswerAtTestEnvironment
              case _                   => SecureRandomUtil.numeric()
            }
            createdConsent <- Future(Consents.consentProvider.vend.createObpConsent(user, challengeAnswer, None, consumerFromRequestBody))
              .map(i => connectorEmptyResponse(i, callContextOpt))
            consentJWT = Consent.createConsentJWT(
              user, consentJson, createdConsent.secret, createdConsent.consentId,
              consumerFromRequestBody.map(_.consumerId.get),
              consentJson.valid_from,
              consentJson.time_to_live.getOrElse(3600),
              None
            )
            _ <- Future(Consents.consentProvider.vend.setJsonWebToken(createdConsent.consentId, consentJWT))
              .map(i => connectorEmptyResponse(i, callContextOpt))
            validUntil = Helper.calculateValidTo(consentJson.valid_from, consentJson.time_to_live.getOrElse(3600))
            _ <- Future(Consents.consentProvider.vend.setValidUntil(createdConsent.consentId, validUntil))
              .map(i => connectorEmptyResponse(i, callContextOpt))
            grantorConsumerId = callContextOpt.flatMap(_.consumer.toOption.map(_.consumerId.get)).getOrElse("Unknown")
            granteeConsumerId = consentJson.consumer_id.getOrElse("Unknown")
            shouldSkip = APIUtil.skipConsentScaForConsumerIdPairs.contains(
              APIUtil.ConsumerIdPair(grantorConsumerId, granteeConsumerId))
            mappedConsent <- if (shouldSkip) {
              Future {
                // Atomic guarded auto-accept: only move INITIATED -> ACCEPTED. If the consent was
                // concurrently revoked, the conditional UPDATE is a 0-row no-op and the revoke stands,
                // instead of the skip-SCA write blindly resurrecting it to ACCEPTED.
                code.bankconnectors.DoobieConsentStatusQueries.conditionalStatusTransitionByConsentId(
                  createdConsent.consentId, ConsentStatus.INITIATED.toString, ConsentStatus.ACCEPTED.toString)
                MappedConsent.find(By(MappedConsent.mConsentId, createdConsent.consentId))
                  .openOrThrowException(s"Consent ${createdConsent.consentId} not found immediately after creation")
              }
            } else {
              val challengeText = s"Your consent challenge : ${challengeAnswer}, Application: $applicationText"
              scaMethod match {
                case v if v == StrongCustomerAuthentication.EMAIL.toString =>
                  for {
                    postEmail <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostConsentEmailJsonV310", 400, callContextOpt) {
                      com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PostConsentEmailJsonV310]
                    }
                    _ <- NewStyle.function.sendCustomerNotification(
                      StrongCustomerAuthentication.EMAIL, postEmail.email,
                      Some("OBP Consent Challenge"), challengeText, callContextOpt)
                  } yield createdConsent
                case v if v == StrongCustomerAuthentication.SMS.toString =>
                  for {
                    postPhone <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostConsentPhoneJsonV310", 400, callContextOpt) {
                      com.openbankproject.commons.util.JsonAliases.parse(cc.httpBody.getOrElse("")).extract[PostConsentPhoneJsonV310]
                    }
                    _ <- NewStyle.function.sendCustomerNotification(
                      StrongCustomerAuthentication.SMS, postPhone.phone_number, None, challengeText, callContextOpt)
                  } yield createdConsent
                case v if v == StrongCustomerAuthentication.IMPLICIT.toString =>
                  for {
                    (consentImplicitSCA, _) <- NewStyle.function.getConsentImplicitSCA(user, callContextOpt)
                    _ <- consentImplicitSCA.scaMethod match {
                      case x if x == StrongCustomerAuthentication.EMAIL =>
                        NewStyle.function.sendCustomerNotification(
                          StrongCustomerAuthentication.EMAIL, consentImplicitSCA.recipient,
                          Some("OBP Consent Challenge"), challengeText, callContextOpt)
                      case x if x == StrongCustomerAuthentication.SMS =>
                        NewStyle.function.sendCustomerNotification(
                          StrongCustomerAuthentication.SMS, consentImplicitSCA.recipient,
                          None, challengeText, callContextOpt)
                      case _ => Future.successful("Success")
                    }
                  } yield createdConsent
                case _ => Future.successful(createdConsent)
              }
            }
          } yield ConsentJsonV310(mappedConsent.consentId, consentJWT, mappedConsent.status)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(createConsent), "POST",
      "/my/consents/IMPLICIT", "Create Consent (IMPLICIT)",
      s"""
      |
      |This endpoint starts the process of creating a Consent.
      |
      |The Consent is created in an ${ConsentStatus.INITIATED} state.
      |
      |A One Time Password (OTP) (AKA security challenge) is sent Out of Band (OOB) to the User via the transport defined in SCA_METHOD
      |SCA_METHOD is typically "SMS","EMAIL" or "IMPLICIT". "EMAIL" is used for testing purposes. OBP mapped mode "IMPLICIT" is "EMAIL".
      |Other mode, bank can decide it in the connector method 'getConsentImplicitSCA'.
      |
      |When the Consent is created, OBP (or a backend system) stores the challenge so it can be checked later against the value supplied by the User with the Answer Consent Challenge endpoint.
      |
      |$generalObpConsentText
      |
      |${userAuthenticationMessage(true)}
      |
      |Example 1:
      |{
      |  "everything": true,
      |  "views": [],
      |  "entitlements": [],
      |  "consumer_id": "7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
      |}
      |
      |Please note that consumer_id is optional field
      |Example 2:
      |{
      |  "everything": true,
      |  "views": [],
      |  "entitlements": [],
      |}
      |
      |Please note if everything=false you need to explicitly specify views and entitlements
      |Example 3:
      |{
      |  "everything": false,
      |  "views": [
      |    {
      |      "bank_id": "GENODEM1GLS",
      |      "account_id": "8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
      |      "view_id": "${Constant.SYSTEM_OWNER_VIEW_ID}"
      |    }
      |  ],
      |  "entitlements": [
      |    {
      |      "bank_id": "GENODEM1GLS",
      |      "role_name": "CanGetCustomersAtOneBank"
      |    }
      |  ],
      |  "consumer_id": "7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
      |}
      |
      |""",
      postConsentImplicitJsonV310, consentJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, InvalidJsonFormat, ConsentAllowedScaMethods,
        RolesAllowedInConsent, RolesForbiddenInConsent, ViewsAllowedInConsent, ConsumerNotFoundByConsumerId, ConsumerIsDisabled,
        MissingPropsValueAtThisInstance, SmsServerNotResponding, InvalidConnectorResponse, UnknownError),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(createConsent)
    )

    // ─── createVRPConsentRequest ────────────────────────────────────────────

    val createVRPConsentRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "consumer" / "vrp-consent-requests" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val parsedBody = com.openbankproject.commons.util.JsonAliases.parse(rawBody)
          for {
            (_, callContextOpt) <- APIUtil.applicationAccess(cc)
            _ <- APIUtil.passesPsd2Aisp(callContextOpt)
            postConsentRequestJsonV510 <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostVRPConsentRequestJsonV510 ", 400, callContextOpt) {
              parsedBody.extract[PostVRPConsentRequestJsonV510]
            }
            maxTimeToLive = APIUtil.getPropsAsIntValue(nameOfProperty = "consents.max_time_to_live", defaultValue = Constant.DEFAULT_CONSENT_TTL)
            _ <- Helper.booleanToFuture(s"$ConsentMaxTTL ($maxTimeToLive)", cc = callContextOpt) {
              postConsentRequestJsonV510.time_to_live match {
                case Some(ttl) => ttl <= maxTimeToLive
                case _         => true
              }
            }
            fromAccountRoutingScheme = postConsentRequestJsonV510.from_account.account_routing.scheme
            fromAccountRoutingSchemeOBPFormat = if (fromAccountRoutingScheme.equalsIgnoreCase("AccountNo")) "ACCOUNT_NUMBER"
              else StringHelpers.snakify(fromAccountRoutingScheme).toUpperCase
            fromAccountRouting = postConsentRequestJsonV510.from_account.account_routing.copy(scheme = fromAccountRoutingSchemeOBPFormat)
            fromAccountTweaked = postConsentRequestJsonV510.from_account.copy(account_routing = fromAccountRouting)
            toAccountRoutingScheme = postConsentRequestJsonV510.to_account.account_routing.scheme
            toAccountRoutingSchemeOBPFormat = if (toAccountRoutingScheme.equalsIgnoreCase("AccountNo")) "ACCOUNT_NUMBER"
              else StringHelpers.snakify(toAccountRoutingScheme).toUpperCase
            toAccountRouting = postConsentRequestJsonV510.to_account.account_routing.copy(scheme = toAccountRoutingSchemeOBPFormat)
            toAccountTweaked = postConsentRequestJsonV510.to_account.copy(account_routing = toAccountRouting)
            fromBankAccountRoutings = BankAccountRoutings(
              bank = BankRoutingJson(
                postConsentRequestJsonV510.from_account.bank_routing.scheme,
                postConsentRequestJsonV510.from_account.bank_routing.address),
              account = BranchRoutingJsonV141(
                fromAccountRoutingSchemeOBPFormat,
                postConsentRequestJsonV510.from_account.account_routing.address),
              branch = AccountRoutingJsonV121(
                postConsentRequestJsonV510.from_account.branch_routing.scheme,
                postConsentRequestJsonV510.from_account.branch_routing.address)
            )
            consentTypeJ = com.openbankproject.commons.util.JsonAliases.parse(s"""{"consent_type": "${ConsentType.VRP}"}""")
            (_, _) <- NewStyle.function.getBankAccountByRoutings(fromBankAccountRoutings, callContextOpt)
            postConsentRequestJsonTweaked = postConsentRequestJsonV510.copy(
              from_account = fromAccountTweaked, to_account = toAccountTweaked)
            createdConsentRequest <- Future(ConsentRequests.consentRequestProvider.vend.createConsentRequest(
              callContextOpt.flatMap(_.consumer),
              Some(compactRender(Extraction.decompose(postConsentRequestJsonTweaked) merge consentTypeJ))))
              .map(i => connectorEmptyResponse(i, callContextOpt))
          } yield JSONFactory500.createConsentRequestResponseJson(createdConsentRequest)
        }
    }
    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createVRPConsentRequest),
      "POST",
      "/consumer/vrp-consent-requests",
      "Create Consent Request VRP",
      s"""
      |This endpoint is used to begin the process of creating a consent that may be used for Variable Recurring Payments (VRPs).
      |
      |VRPs are useful in situations when a beneficiary needs to be paid different amounts on a regular basis.
      |
      |Once granted, the consent allows its holder to initiate multiple Transaction Requests to the Counterparty defined in this endpoint as long as the
      |Counterparty Limits linked to this particular consent are respected.
      |
      |Client, Consumer or Application Authentication is mandatory for this endpoint.
      |
      |i.e. the caller of this endpoint is the API Client, Consumer or Application rather than a specific User.
      |
      |At the end of the process the following objects are created in OBP or connected backend systems:
      | - An automatically generated View which controls access.
      | - A Counterparty that is the Beneficiary of the Variable Recurring Payments. The Counterparty specifies the Bank Account number or other routing address.
      | - Limits for the Counterparty which constrain the amount of money that can be sent to it in various periods (yearly, monthly, weekly).
      |
      |The Account holder may modify the Counterparty or Limits e.g. to increase or decrease the maximum possible payment amounts or the frequencey of the payments.
      |
      |
      |In the case of a public client we use the client_id and private key to obtain an access token, otherwise we use the client_id and client_secret.
      |The obtained access token is used in the HTTP Authorization header of the request as follows:
      |
      |Example:
      |Authorization: Bearer eXtneO-THbQtn3zvK_kQtXXfvOZyZFdBCItlPDbR2Bk.dOWqtXCtFX-tqGTVR0YrIjvAolPIVg7GZ-jz83y6nA0
      |
      |After successfully creating the VRP consent request, you need to call the `Create Consent By CONSENT_REQUEST_ID` endpoint to finalize the consent using the CONSENT_REQUEST_ID returned by this endpoint.
      |
      |${applicationAccessMessage(true)}
      |
      |${userAuthenticationMessage(false)}
      |
      |
      |""".stripMargin,
      postVRPConsentRequestJsonV510,
      vrpConsentRequestResponseJson,
      List(InvalidJsonFormat, ConsentMaxTTL, X509CannotGetCertificate, X509GeneralError, InvalidConnectorResponse, UnknownError),
      apiTagConsent :: apiTagVrp :: apiTagTransactionRequest :: Nil,
      None,
      http4sPartialFunction = Some(createVRPConsentRequest)
    )

    val allRoutes: HttpRoutes[IO] =
      Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
        root(req)
          .orElse(getMyConsentsByBank(req))
          .orElse(getAggregateMetrics(req))
          .orElse(getBanks(req))
          .orElse(createAtm(req))
          .orElse(updateAtm(req))
          .orElse(getAtms(req))
          .orElse(getAtm(req))
          .orElse(deleteAtm(req))
          .orElse(createConsumer(req))
          .orElse(getConsumer(req))
          .orElse(getConsumers(req))
          .orElse(getTransactionRequests(req))
          .orElse(getBankAccountsBalances(req))
          .orElse(getAllBankAccountBalances(req))
          .orElse(suggestedSessionTimeout(req))
          .orElse(getOAuth2ServerWellKnown(req))
          .orElse(regulatedEntities(req))
          .orElse(getRegulatedEntityById(req))
          .orElse(createRegulatedEntity(req))
          .orElse(deleteRegulatedEntity(req))
          .orElse(logCacheTraceEndpoint(req))
          .orElse(logCacheDebugEndpoint(req))
          .orElse(logCacheInfoEndpoint(req))
          .orElse(logCacheWarningEndpoint(req))
          .orElse(logCacheErrorEndpoint(req))
          .orElse(logCacheAllEndpoint(req))
          .orElse(waitingForGodot(req))
          .orElse(getAllApiCollections(req))
          .orElse(createAtmAttribute(req))
          .orElse(getAtmAttributes(req))
          .orElse(getAtmAttribute(req))
          .orElse(updateAtmAttribute(req))
          .orElse(deleteAtmAttribute(req))
          .orElse(createAgent(req))
          .orElse(updateAgentStatus(req))
          .orElse(getAgent(req))
          .orElse(getAgents(req))
          .orElse(createRegulatedEntityAttribute(req))
          .orElse(deleteRegulatedEntityAttribute(req))
          .orElse(getRegulatedEntityAttributeById(req))
          .orElse(getAllRegulatedEntityAttributes(req))
          .orElse(updateRegulatedEntityAttribute(req))
          .orElse(mtlsClientCertificateInfo(req))
          .orElse(updateMyApiCollection(req))
          .orElse(getApiTags(req))
          .orElse(getMetrics(req))
          .orElse(getWebUiProps(req))
          .orElse(createNonPersonalUserAttribute(req))
          .orElse(deleteNonPersonalUserAttribute(req))
          .orElse(getNonPersonalUserAttributes(req))
          .orElse(syncExternalUser(req))
          .orElse(getEntitlementsAndPermissions(req))
          .orElse(getUserByProviderAndUsername(req))
          .orElse(getUserLockStatus(req))
          .orElse(unlockUserByProviderAndUsername(req))
          .orElse(lockUserByProviderAndUsername(req))
          .orElse(lockUserByProviderAndUsername(req))
          .orElse(validateUserByUserId(req))
          .orElse(getAccountAccessByUserId(req))
          .orElse(getAccountsHeldByUserAtBank(req))
          .orElse(getAccountsHeldByUser(req))
          .orElse(getCustomersForUserIdsOnly(req))
          .orElse(getCustomersByLegalName(req))
          .orElse(customViewNamesCheck(req))
          .orElse(systemViewNamesCheck(req))
          .orElse(accountAccessUniqueIndexCheck(req))
          .orElse(accountCurrencyCheck(req))
          .orElse(orphanedAccountCheck(req))
          .orElse(getCurrenciesAtBank(req))
          .orElse(updateConsumerRedirectURL(req))
          .orElse(updateConsumerLogoURL(req))
          .orElse(updateConsumerCertificate(req))
          .orElse(updateConsumerName(req))
          .orElse(getCallsLimit(req))
          .orElse(createMyConsumer(req))
          .orElse(createConsumerDynamicRegistration(req))
          .orElse(grantUserAccessToViewById(req))
          .orElse(revokeUserAccessToViewById(req))
          .orElse(createUserWithAccountAccessById(req))
          .orElse(getTransactionRequestById(req))
          .orElse(updateTransactionRequestStatus(req))
          .orElse(getCoreAccountByIdThroughView(req))
          .orElse(getBankAccountBalances(req))
          .orElse(getBankAccountsBalancesThroughView(req))
          .orElse(createCounterpartyLimit(req))
          .orElse(updateCounterpartyLimit(req))
          .orElse(getCounterpartyLimit(req))
          .orElse(getCounterpartyLimitStatus(req))
          .orElse(deleteCounterpartyLimit(req))
          .orElse(createCustomView(req))
          .orElse(updateCustomView(req))
          .orElse(getCustomView(req))
          .orElse(deleteCustomView(req))
          .orElse(createBankAccountBalance(req))
          .orElse(getBankAccountBalanceById(req))
          .orElse(updateBankAccountBalance(req))
          .orElse(deleteBankAccountBalance(req))
          .orElse(addSystemViewPermission(req))
          .orElse(deleteSystemViewPermission(req))
          .orElse(updateConsentStatusByConsent(req))
          .orElse(updateConsentAccountAccessByConsentId(req))
          .orElse(updateConsentUserIdByConsentId(req))
          .orElse(authoriseUKConsentChallenge(req))
          .orElse(authoriseUKConsent(req))
          .orElse(getMyConsents(req))
          .orElse(getConsentsAtBank(req))
          .orElse(getConsents(req))
          .orElse(getConsentByConsentId(req))
          .orElse(getConsentByConsentIdViaConsumer(req))
          .orElse(revokeConsentAtBank(req))
          .orElse(selfRevokeConsent(req))
          .orElse(revokeMyConsent(req))
          .orElse(createConsent(req))
          .orElse(createVRPConsentRequest(req))
      }

    val allRoutesWithMiddleware: HttpRoutes[IO] =
      ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allRoutes))

    // ─── path-rewriting bridge: /obp/v5.1.0/… → /obp/v5.0.0/… ─────────────
    lazy val v510ToV500Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val rawPath = req.uri.path.renderString
      if (rawPath.startsWith("/obp/v5.1.0/") &&
          ResourceDocMatcher.findResourceDoc(req.method.name, req.uri.path, v5_1ResourceDocIndex).isEmpty) {
        val rewritten = rawPath.replaceFirst("/obp/v5\\.1\\.0/", "/obp/v5.0.0/")
        val newUri = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
        Http4s500.wrappedRoutesV500Services.run(req.withUri(newUri))
          .map(_.putHeaders(Header.Raw(CIString("X-OBP-Version-Served"), "v5.0.0")))
      } else {
        OptionT.none[IO, Response[IO]]
      }
    }
  }

  private lazy val v5_1ResourceDocIndex: ResourceDocMatcher.ResourceDocIndex =
    ResourceDocMatcher.buildIndex(resourceDocs)

  lazy val wrappedRoutesV510Services: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      Implementations5_1_0.allRoutesWithMiddleware.run(req)
        .orElse(Implementations5_1_0.v510ToV500Bridge.run(req))
    }
}
