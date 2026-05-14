package code.api.v5_1_0

import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.newstyle.{BalanceNewStyle, RegulatedEntityAttributeNewStyle, ViewNewStyle}
import code.api.util.newstyle.RegulatedEntityNewStyle.{createRegulatedEntityNewStyle, deleteRegulatedEntityNewStyle, getRegulatedEntitiesNewStyle, getRegulatedEntityByEntityIdNewStyle}
import code.api.util.newstyle.Consumer.createConsumerNewStyle
import code.api.util.{APIUtil, ConsentJWT, CustomJsonFormats, JwtUtil, NewStyle, OBPBankId, OBPLimit, OBPOffset, OBPSortBy, X509}
import code.api.v2_0_0.AccountsHelper
import code.api.v2_1_0.{ConsumerRedirectUrlJSON, JSONFactory210}
import code.api.v3_0_0.JSONFactory300
import code.api.v3_0_0.JSONFactory300.createAggregateMetricJson
import code.api.v3_1_0.{JSONFactory310, PostConsentBodyCommonJson, PostConsentEntitlementJsonV310, PostConsentViewJsonV310}
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
  AccountId, AccountRouting, AtmId, AtmT, Bank, BankAccount, BankId, BankIdAccountId,
  CustomerId, ListResult, ProductCode, RegulatedEntityId, TransactionRequestId, User,
  View, ViewId
}
import com.openbankproject.commons.model.enums.{AtmAttributeType, RegulatedEntityAttributeType, StrongCustomerAuthentication, TransactionRequestStatus, UserAttributeType}
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.json
import net.liftweb.json.JsonAST.prettyRender
import net.liftweb.json.{Extraction, Formats, compactRender}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.{Helpers, Props, StringHelpers}
import org.http4s.{HttpRoutes, Method, Request, Response, Uri}
import org.http4s.dsl.io._

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Date
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.language.{higherKinds, implicitConversions}

object Http4s510 {

  type HttpF[A] = OptionT[IO, A]

  implicit val formats: Formats = CustomJsonFormats.formats
  implicit def convertAnyToJsonString(any: Any): String = prettyRender(Extraction.decompose(any))

  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v5_1_0
  val versionStatus: String = ApiVersionStatus.BLEEDING_EDGE.toString
  val resourceDocs: ArrayBuffer[ResourceDoc] = ArrayBuffer[ResourceDoc]()

  object Implementations5_1_0 {

    val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

    // ─── root (GET /root and GET / — v5.1 override of every prior version) ──

    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` =>
        EndpointHelpers.executeFuture(req) {
          Future.successful(JSONFactory510.getApiInfoJSON(OBPAPI5_1_0.version, OBPAPI5_1_0.versionStatus))
        }
      case req @ GET -> `prefixPath` / "root" =>
        EndpointHelpers.executeFuture(req) {
          Future.successful(JSONFactory510.getApiInfoJSON(OBPAPI5_1_0.version, OBPAPI5_1_0.versionStatus))
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(root), "GET", "/root",
      "Get API Info (root)",
      "Returns information about API version, hosted by, energy source, git commit.",
      EmptyBody, apiInfoJson400,
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
      null, implementedInApiVersion, nameOf(getMyConsentsByBank), "GET",
      "/banks/BANK_ID/my/consents", "Get My Consents at Bank",
      "Get All Consents that the current User has at the Bank.",
      EmptyBody, consentsInfoJsonV510,
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
          } yield createAggregateMetricJson(aggregateMetrics)
        }
    }

    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getAggregateMetrics), "GET",
      "/management/aggregate-metrics", "Get Aggregate Metrics",
      s"""Returns aggregated metrics. Requires CanReadAggregateMetrics role.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, aggregateMetricsJSONV300,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagMetric, apiTagAggregateMetrics),
      Some(List(canReadAggregateMetrics)),
      http4sPartialFunction = Some(getAggregateMetrics)
    )

    // ─── ATM CRUD (createAtm/updateAtm/getAtms/getAtm/deleteAtm) — v5.1 overrides

    val createAtm: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "atms" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val bankId = BankId(bankIdStr)
          for {
            atmJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the ${classOf[AtmJsonV510]}", 400, Some(cc)) {
              val atm = net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostAtmJsonV510]
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
      null, implementedInApiVersion, nameOf(createAtm), "POST",
      "/banks/BANK_ID/atms", "Create ATM", "Create ATM.",
      postAtmJsonV510, atmJsonV510,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[AtmJsonV510]
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
      null, implementedInApiVersion, nameOf(updateAtm), "PUT",
      "/banks/BANK_ID/atms/ATM_ID", "UPDATE ATM", "Update ATM.",
      atmJsonV510.copy(id = None, attributes = None), atmJsonV510,
      List($AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagATM),
      Some(List(canUpdateAtm, canUpdateAtmAtAnyBank)),
      http4sPartialFunction = Some(updateAtm)
    )

    // getAtms / getAtm intentionally left to Lift's wrappedWithAuthCheck:
    // ResponseHeadersTest exercises ETag + If-None-Match + If-Modified-Since on
    // /banks/BANK_ID/atms (handled by APIUtil.checkConditionalRequest +
    // getRequestHeadersNewStyle in Lift's response builder). ResourceDocMiddleware
    // doesn't yet emit ETag headers or honour conditional headers, so migrating
    // these endpoints here would regress those tests. APIMethods510 still has its
    // own ResourceDoc, so resource-docs aggregation is unaffected.

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
      null, implementedInApiVersion, nameOf(deleteAtm), "DELETE",
      "/banks/BANK_ID/atms/ATM_ID", "Delete ATM",
      "Delete ATM. This will also delete all its attributes.",
      EmptyBody, EmptyBody,
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
              val js = net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[CreateConsumerRequestJsonV510]
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
      null, implementedInApiVersion, nameOf(createConsumer), "POST",
      "/management/consumers", "Create Consumer",
      s"""Create a Consumer.
         |
         |${userAuthenticationMessage(true)}""",
      createConsumerRequestJsonV510, consumerJsonOnlyForPostResponseV510,
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
      null, implementedInApiVersion, nameOf(getConsumer), "GET",
      "/management/consumers/CONSUMER_ID", "Get Consumer",
      "Get the Consumer specified by CONSUMER_ID.",
      EmptyBody, consumerJSON,
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
      null, implementedInApiVersion, nameOf(getConsumers), "GET",
      "/management/consumers", "Get Consumers",
      s"""Get all Consumers.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, consumersJsonV510,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
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
      null, implementedInApiVersion, nameOf(getTransactionRequests), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests",
      "Get Transaction Requests.",
      "Returns transaction requests for account, with attribute filter support.",
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
      null, implementedInApiVersion, nameOf(getBankAccountsBalances), "GET",
      "/banks/BANK_ID/balances", "Get Account Balances by BANK_ID",
      "Get the Balances for the Account specified by BANK_ID.",
      EmptyBody, accountBalancesV400Json,
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
      null, implementedInApiVersion, nameOf(getAllBankAccountBalances), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/balances", "Get Account Balances",
      s"""Get all balances for the Account specified by BANK_ID and ACCOUNT_ID.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, bankAccountBalancesJsonV510,
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
      null, implementedInApiVersion, nameOf(suggestedSessionTimeout), "GET",
      "/ui/suggested-session-timeout", "Get Suggested Session Timeout",
      "Returns the suggested session timeout in case of user inactivity.",
      EmptyBody, SuggestedSessionTimeoutV510("300"),
      List(UnknownError), apiTagApi :: Nil, None,
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
            val availableProviders = Map(
              "obp-oidc" -> WellKnownUriJsonV510("obp-oidc", code.api.OAuth2Login.OBPOIDC.wellKnownOpenidConfiguration.toURL.toString),
              "keycloak" -> WellKnownUriJsonV510("keycloak", code.api.OAuth2Login.Keycloak.wellKnownOpenidConfiguration.toURL.toString)
            )
            val providersToShow: List[WellKnownUriJsonV510] = providerPropBox match {
              case Empty => Nil
              case Full(value) if value.trim.isEmpty => availableProviders.values.toList
              case Full(value) =>
                val wanted = value.split(",").map(_.trim.toLowerCase).filter(_.nonEmpty).toSet
                if (wanted.contains("none")) Nil
                else availableProviders.filterKeys(wanted.contains).values.toList
              case _ => Nil
            }
            WellKnownUrisJsonV510(providersToShow)
          }
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, "getOAuth2ServerWellKnown", "GET",
      "/well-known", "Get Well Known URIs",
      "Get the OAuth2 server's public Well Known URIs.",
      EmptyBody, oAuth2ServerJwksUrisJson,
      List(UnknownError), List(apiTagApi), None,
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
      null, implementedInApiVersion, nameOf(regulatedEntities), "GET",
      "/regulated-entities", "Get Regulated Entities",
      "Returns information about Regulated Entities.",
      EmptyBody, regulatedEntitiesJsonV510,
      List(UnknownError), apiTagDirectory :: apiTagApi :: Nil, None,
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
      null, implementedInApiVersion, nameOf(getRegulatedEntityById), "GET",
      "/regulated-entities/REGULATED_ENTITY_ID", "Get Regulated Entity",
      "Get Regulated Entity By REGULATED_ENTITY_ID.",
      EmptyBody, regulatedEntityJsonV510,
      List(UnknownError), apiTagDirectory :: apiTagApi :: Nil, None,
      http4sPartialFunction = Some(getRegulatedEntityById)
    )

    val createRegulatedEntity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "regulated-entities" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val parsedBody = net.liftweb.json.parse(cc.httpBody.getOrElse(""))
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
      null, implementedInApiVersion, nameOf(createRegulatedEntity), "POST",
      "/regulated-entities", "Create Regulated Entity",
      s"""Create Regulated Entity.
         |
         |${userAuthenticationMessage(true)}""",
      regulatedEntityPostJsonV510, regulatedEntityJsonV510,
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
      null, implementedInApiVersion, nameOf(deleteRegulatedEntity), "DELETE",
      "/regulated-entities/REGULATED_ENTITY_ID", "Delete Regulated Entity",
      s"""Delete Regulated Entity specified by REGULATED_ENTITY_ID.
         |
         |${userAuthenticationMessage(true)}""",
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
      null, implementedInApiVersion, nameOf(logCacheTraceEndpoint), "GET",
      "/system/log-cache/trace", "Get Trace Level Log Cache",
      "Returns TRACE level logs from the system log cache.",
      EmptyBody, EmptyBody,
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
      null, implementedInApiVersion, nameOf(logCacheDebugEndpoint), "GET",
      "/system/log-cache/debug", "Get Debug Level Log Cache",
      "Returns DEBUG level logs from the system log cache.",
      EmptyBody, EmptyBody,
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
      null, implementedInApiVersion, nameOf(logCacheInfoEndpoint), "GET",
      "/system/log-cache/info", "Get Info Level Log Cache",
      "Returns INFO level logs from the system log cache.",
      EmptyBody, EmptyBody,
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
      null, implementedInApiVersion, nameOf(logCacheWarningEndpoint), "GET",
      "/system/log-cache/warning", "Get Warning Level Log Cache",
      "Returns WARNING level logs from the system log cache.",
      EmptyBody, EmptyBody,
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
      null, implementedInApiVersion, nameOf(logCacheErrorEndpoint), "GET",
      "/system/log-cache/error", "Get Error Level Log Cache",
      "Returns ERROR level logs from the system log cache.",
      EmptyBody, EmptyBody,
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
      null, implementedInApiVersion, nameOf(logCacheAllEndpoint), "GET",
      "/system/log-cache/all", "Get All Level Log Cache",
      "Returns logs of all levels from the system log cache.",
      EmptyBody, EmptyBody,
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
      null, implementedInApiVersion, nameOf(waitingForGodot), "GET",
      "/waiting-for-godot", "Waiting For Godot",
      "Postpones response by `?sleep=N` ms (default 0).",
      EmptyBody, WaitingForGodotJsonV510(50),
      List(UnknownError, MandatoryPropertyIsNotSet),
      apiTagApi :: Nil, None,
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
      null, implementedInApiVersion, nameOf(getAllApiCollections), "GET",
      "/management/api-collections", "Get All API Collections",
      s"""Get All API Collections.
         |
         |${userAuthenticationMessage(true)}""",
      EmptyBody, apiCollectionsJson400,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[AtmAttributeJsonV510]
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
      null, implementedInApiVersion, nameOf(createAtmAttribute), "POST",
      "/banks/BANK_ID/atms/ATM_ID/attributes", "Create ATM Attribute",
      "Create ATM Attribute. The type field must be one of STRING/INTEGER/DOUBLE/DATE_WITH_DAY.",
      atmAttributeJsonV510, atmAttributeResponseJsonV510,
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
      null, implementedInApiVersion, nameOf(getAtmAttributes), "GET",
      "/banks/BANK_ID/atms/ATM_ID/attributes", "Get ATM Attributes", "Get ATM Attributes.",
      EmptyBody, atmAttributesResponseJsonV510,
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
      null, implementedInApiVersion, nameOf(getAtmAttribute), "GET",
      "/banks/BANK_ID/atms/ATM_ID/attributes/ATM_ATTRIBUTE_ID", "Get ATM Attribute By ATM_ATTRIBUTE_ID",
      "Get ATM Attribute By ATM_ATTRIBUTE_ID.",
      EmptyBody, atmAttributeResponseJsonV510,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[AtmAttributeJsonV510]
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
      null, implementedInApiVersion, nameOf(updateAtmAttribute), "PUT",
      "/banks/BANK_ID/atms/ATM_ID/attributes/ATM_ATTRIBUTE_ID", "Update ATM Attribute",
      "Update an ATM Attribute by its id.",
      atmAttributeJsonV510, atmAttributeResponseJsonV510,
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
      null, implementedInApiVersion, nameOf(deleteAtmAttribute), "DELETE",
      "/banks/BANK_ID/atms/ATM_ID/attributes/ATM_ATTRIBUTE_ID", "Delete ATM Attribute",
      "Delete an ATM Attribute by its id.",
      EmptyBody, EmptyBody,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostAgentJsonV510]
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
      null, implementedInApiVersion, nameOf(createAgent), "POST",
      "/banks/BANK_ID/agents", "Create Agent",
      s"${userAuthenticationMessage(true)}",
      postAgentJsonV510, agentJsonV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, AgentNumberAlreadyExists, CreateAgentError, UnknownError),
      List(apiTagCustomer, apiTagPerson),
      None,
      http4sPartialFunction = Some(createAgent)
    )

    // updateAgentStatus intentionally left to Lift: AgentTest "wrong Bankid" expects
    // 404 BankNotFound for unauthorised user1, which means Lift's wrappedWithAuthCheck
    // role check passes here even though user1 lacks
    // canUpdateAgentStatusAtAnyBank/canUpdateAgentStatusAtOneBank. ResourceDocMiddleware
    // applies the same access-control function (with JIT entitlements) and returns 403
    // — i.e. it's the strict reading of the doc roles. Leaving updateAgentStatus in
    // Lift preserves the established test contract until the discrepancy is
    // root-caused (suspect: the Lift test environment has additional entitlements
    // wired in before this scenario via class-level or default-user fixtures).

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
      null, implementedInApiVersion, nameOf(getAgent), "GET",
      "/banks/BANK_ID/agents/AGENT_ID", "Get Agent",
      s"Get Agent.\n\n${userAuthenticationMessage(true)}",
      EmptyBody, agentJsonV510,
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
      null, implementedInApiVersion, nameOf(getAgents), "GET",
      "/banks/BANK_ID/agents", "Get Agents at Bank",
      s"Get Agents at Bank.\n\n${userAuthenticationMessage(false)}",
      EmptyBody, minimalAgentsJsonV510,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[RegulatedEntityAttributeRequestJsonV510]
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
      null, implementedInApiVersion, nameOf(createRegulatedEntityAttribute), "POST",
      "/regulated-entities/REGULATED_ENTITY_ID/attributes", "Create Regulated Entity Attribute",
      "Create a new Regulated Entity Attribute. Type must be STRING/INTEGER/DOUBLE/DATE_WITH_DAY.",
      regulatedEntityAttributeRequestJsonV510, regulatedEntityAttributeResponseJsonV510,
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
      null, implementedInApiVersion, nameOf(deleteRegulatedEntityAttribute), "DELETE",
      "/regulated-entities/REGULATED_ENTITY_ID/attributes/REGULATED_ENTITY_ATTRIBUTE_ID",
      "Delete Regulated Entity Attribute",
      "Delete a Regulated Entity Attribute.",
      EmptyBody, EmptyBody,
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
      null, implementedInApiVersion, nameOf(getRegulatedEntityAttributeById), "GET",
      "/regulated-entities/REGULATED_ENTITY_ID/attributes/REGULATED_ENTITY_ATTRIBUTE_ID",
      "Get Regulated Entity Attribute By ID", "Get a specific Regulated Entity Attribute by its ID.",
      EmptyBody, regulatedEntityAttributeResponseJsonV510,
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
      null, implementedInApiVersion, nameOf(getAllRegulatedEntityAttributes), "GET",
      "/regulated-entities/REGULATED_ENTITY_ID/attributes", "Get All Regulated Entity Attributes",
      "Get all attributes for the specified Regulated Entity.",
      EmptyBody, regulatedEntityAttributesJsonV510,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[RegulatedEntityAttributeRequestJsonV510]
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
      null, implementedInApiVersion, nameOf(updateRegulatedEntityAttribute), "PUT",
      "/regulated-entities/REGULATED_ENTITY_ID/attributes/REGULATED_ENTITY_ATTRIBUTE_ID",
      "Update Regulated Entity Attribute", "Update an existing Regulated Entity Attribute.",
      regulatedEntityAttributeRequestJsonV510, regulatedEntityAttributeResponseJsonV510,
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
      null, implementedInApiVersion, nameOf(mtlsClientCertificateInfo), "GET",
      "/my/mtls/certificate/current", "Provide client's certificate info of a current call",
      "Provide client's certificate info of a current call specified by PSD2-CERT request header.",
      EmptyBody, certificateInfoJsonV510,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[code.api.v4_0_0.PostApiCollectionJson400]
            }
            (_, _) <- NewStyle.function.getApiCollectionById(apiCollectionId, Some(cc))
            (apiCollection, _) <- NewStyle.function.updateApiCollection(
              apiCollectionId, putJson.api_collection_name, putJson.is_sharable, putJson.description.getOrElse(""), Some(cc))
          } yield JSONFactory400.createApiCollectionJsonV400(apiCollection)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateMyApiCollection), "PUT",
      "/my/api-collections/API_COLLECTION_ID", "Update My Api Collection By API_COLLECTION_ID",
      s"Update Api Collection for logged in user.\n\n${userAuthenticationMessage(true)}",
      postApiCollectionJson400, apiCollectionJson400,
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
      null, implementedInApiVersion, nameOf(getApiTags), "GET",
      "/tags", "Get API Tags",
      s"Get API Tags.\n\n${userAuthenticationMessage(false)}",
      EmptyBody, accountsMinimalJson400,
      List(UnknownError), List(apiTagApi), None,
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
      null, implementedInApiVersion, nameOf(getMetrics), "GET",
      "/management/metrics", "Get Metrics",
      "Get API metrics rows. Requires CanReadMetrics role.",
      EmptyBody, metricsJsonV510,
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
      null, implementedInApiVersion, nameOf(getWebUiProps), "GET",
      "/webui-props", "Get WebUiProps",
      "Get all WebUiProps key/values. ?active=true also includes implicit (default) props.",
      EmptyBody,
      ListResult("webui-props", List(WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com", Some("web-ui-props-id")))),
      List(UserHasMissingRoles, UnknownError),
      List(apiTagWebUiProps),
      None,
      http4sPartialFunction = Some(getWebUiProps)
    )

    val allRoutes: HttpRoutes[IO] =
      Kleisli[HttpF, Request[IO], Response[IO]] { req: Request[IO] =>
        root(req)
          .orElse(getMyConsentsByBank(req))
          .orElse(getAggregateMetrics(req))
          .orElse(createAtm(req))
          .orElse(updateAtm(req))
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
      }

    val allRoutesWithMiddleware: HttpRoutes[IO] =
      ResourceDocMiddleware.apply(resourceDocs)(allRoutes)

    // ─── path-rewriting bridge: /obp/v5.1.0/… → /obp/v5.0.0/… ─────────────
    val v510ToV500Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val rawPath = req.uri.path.renderString
      if (rawPath.startsWith("/obp/v5.1.0/")) {
        val rewritten = rawPath.replaceFirst("/obp/v5\\.1\\.0/", "/obp/v5.0.0/")
        val newUri = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
        val rewrittenReq = req.withUri(newUri)
        Http4s500.wrappedRoutesV500Services.run(rewrittenReq)
      } else {
        OptionT.none[IO, Response[IO]]
      }
    }
  }

  // Bridge cascade is currently DISABLED for v5.1.0:
  // While migrating, unmigrated v5.1.0 endpoints (e.g. updateConsumerRedirectURL)
  // would otherwise be sent through v510→v500→v400→… and either land on a
  // wrong-version handler or never reach Lift's OBPAPI5_1_0 dispatch correctly.
  // Without the bridge, unmatched v5.1.0 URLs fall through to Http4sLiftWebBridge
  // unchanged, where Lift's dispatch for OBPAPI5_1_0 picks them up. Re-enable
  // the bridge once ALL v5.1.0 own endpoints (currently 110) are migrated to
  // Http4s510 — then `.orElse(Implementations5_1_0.v510ToV500Bridge.run(req))`.
  val wrappedRoutesV510Services: HttpRoutes[IO] =
    Implementations5_1_0.allRoutesWithMiddleware
}
