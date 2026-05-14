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
import code.api.util.{APIUtil, Consent, ConsentJWT, CustomJsonFormats, JwtUtil, NewStyle, OBPBankId, OBPLimit, OBPOffset, OBPSortBy, SecureRandomUtil, X509}
import code.api.v2_0_0.AccountsHelper
import code.api.v2_1_0.{ConsumerRedirectUrlJSON, JSONFactory210}
import code.api.v3_0_0.JSONFactory300
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
import com.openbankproject.commons.model.enums.{AtmAttributeType, ConsentType, RegulatedEntityAttributeType, StrongCustomerAuthentication, TransactionRequestStatus, UserAttributeType}
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

    // ─── Non-personal user attributes (3) ─────────────────────────────────

    val createNonPersonalUserAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / userId / "non-personal" / "attributes" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            (user, _) <- NewStyle.function.getUserByUserId(userId, Some(cc))
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $UserAttributeJsonV510 ", 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[UserAttributeJsonV510]
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
      null, implementedInApiVersion, nameOf(createNonPersonalUserAttribute), "POST",
      "/users/USER_ID/non-personal/attributes", "Create Non Personal User Attribute",
      s"Create Non Personal User Attribute. Type ∈ {STRING, INTEGER, DOUBLE, DATE_WITH_DAY}.\n\n${userAuthenticationMessage(true)}",
      userAttributeJsonV510, userAttributeResponseJsonV510,
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
      null, implementedInApiVersion, nameOf(deleteNonPersonalUserAttribute), "DELETE",
      "/users/USER_ID/non-personal/attributes/USER_ATTRIBUTE_ID", "Delete Non Personal User Attribute",
      s"Delete the Non Personal User Attribute.\n\n${userAuthenticationMessage(true)}",
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
      null, implementedInApiVersion, nameOf(getNonPersonalUserAttributes), "GET",
      "/users/USER_ID/non-personal/attributes", "Get Non Personal User Attributes",
      s"Get Non Personal User Attributes for a user.\n\n${userAuthenticationMessage(true)}",
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
      null, implementedInApiVersion, nameOf(syncExternalUser), "POST",
      "/users/PROVIDER/PROVIDER_ID/sync", "Sync User",
      s"Create or sync an OBP User with User from an external identity provider.\n\n${userAuthenticationMessage(true)}",
      EmptyBody, refresUserJson,
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
      null, implementedInApiVersion, nameOf(getEntitlementsAndPermissions), "GET",
      "/users/USER_ID/entitlements-and-permissions", "Get Entitlements and Permissions for a User",
      "",
      EmptyBody, userJsonV300,
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
      null, implementedInApiVersion, nameOf(getUserByProviderAndUsername), "GET",
      "/users/provider/PROVIDER/username/USERNAME", "Get User by Provider and Username",
      s"Get a User by PROVIDER + USERNAME.\n\n${userAuthenticationMessage(true)}",
      EmptyBody, userWithNamesJsonV510,
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
      null, implementedInApiVersion, nameOf(getUserLockStatus), "GET",
      "/users/PROVIDER/USERNAME/lock-status", "Get User Lock Status",
      s"Get User Login Status.\n\n${userAuthenticationMessage(true)}",
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
      null, implementedInApiVersion, nameOf(unlockUserByProviderAndUsername), "PUT",
      "/users/PROVIDER/USERNAME/lock-status", "Unlock the user",
      s"Unlock a User (e.g. after multiple failed login attempts).\n\n${userAuthenticationMessage(true)}",
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
      null, implementedInApiVersion, nameOf(lockUserByProviderAndUsername), "POST",
      "/users/PROVIDER/USERNAME/locks", "Lock the user",
      s"Lock a User.\n\n${userAuthenticationMessage(true)}",
      EmptyBody, userLockStatusJson,
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
      null, implementedInApiVersion, nameOf(validateUserByUserId), "PUT",
      "/management/users/USER_ID", "Validate a user",
      "Manually validate a User by USER_ID. Sets is_validated=true.",
      EmptyBody, UserValidatedJson(is_validated = true),
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
      null, implementedInApiVersion, nameOf(getAccountAccessByUserId), "GET",
      "/users/USER_ID/account-access", "Get Account Access by USER_ID",
      s"Get Account Access by USER_ID.\n\n${userAuthenticationMessage(true)}",
      EmptyBody, accountsMinimalJson400,
      List($AuthenticatedUserIsRequired, UserNotFoundByUserId, UnknownError),
      List(apiTagAccount),
      Some(List(canSeeAccountAccessForAnyUser)),
      http4sPartialFunction = Some(getAccountAccessByUserId)
    )

    // ─── Accounts-held (2) — left to Lift ─────────────────────────────────
    // getAccountsHeldByUserAtBank / getAccountsHeldByUser depend on
    // AccountsHelper.getFilteredCoreAccounts which takes a Lift `Req`. Need
    // to port the filter to http4s before migrating these.

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
      null, implementedInApiVersion, nameOf(getCustomersForUserIdsOnly), "GET",
      "/users/current/customers/customer_ids", "Get Customers for Current User (IDs only)",
      s"Gets all Customer IDs linked to the current User.\n\n${userAuthenticationMessage(true)}",
      EmptyBody, customersWithAttributesJsonV300,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostCustomerLegalNameJsonV510]
            }
            (customer, _) <- NewStyle.function.getCustomersByCustomerLegalName(bank.bankId, postedData.legal_name, Some(cc))
          } yield JSONFactory300.createCustomersJson(customer)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getCustomersByLegalName), "POST",
      "/banks/BANK_ID/customers/legal-name", "Get Customers by Legal Name",
      s"Gets the Customers specified by Legal Name.\n\n${userAuthenticationMessage(true)}",
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
      null, implementedInApiVersion, nameOf(customViewNamesCheck), "GET",
      "/management/system/integrity/custom-view-names-check", "Check Custom View Names",
      s"Check custom view names.\n\n${userAuthenticationMessage(true)}",
      EmptyBody, CheckSystemIntegrityJsonV510(true),
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
      null, implementedInApiVersion, nameOf(systemViewNamesCheck), "GET",
      "/management/system/integrity/system-view-names-check", "Check System View Names",
      s"Check system view names.\n\n${userAuthenticationMessage(true)}",
      EmptyBody, CheckSystemIntegrityJsonV510(true),
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
      null, implementedInApiVersion, nameOf(accountAccessUniqueIndexCheck), "GET",
      "/management/system/integrity/account-access-unique-index-1-check", "Check Unique Index at Account Access",
      s"Check unique index at account access table.\n\n${userAuthenticationMessage(true)}",
      EmptyBody, CheckSystemIntegrityJsonV510(true),
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
      null, implementedInApiVersion, nameOf(accountCurrencyCheck), "GET",
      "/management/system/integrity/banks/BANK_ID/account-currency-check", "Check for Sensible Currencies",
      s"Check for sensible currencies at Bank Account model.\n\n${userAuthenticationMessage(true)}",
      EmptyBody, CheckSystemIntegrityJsonV510(true),
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
      null, implementedInApiVersion, nameOf(orphanedAccountCheck), "GET",
      "/management/system/integrity/banks/BANK_ID/orphaned-account-check", "Check for Orphaned Accounts",
      s"Check for orphaned accounts at Bank Account model.\n\n${userAuthenticationMessage(true)}",
      EmptyBody, CheckSystemIntegrityJsonV510(true),
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
      null, implementedInApiVersion, nameOf(getCurrenciesAtBank), "GET",
      "/banks/BANK_ID/currencies", "Get Currencies at a Bank",
      "Get Currencies specified by BANK_ID.",
      EmptyBody, currenciesJsonV510,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[ConsumerRedirectUrlJSON]
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
      null, implementedInApiVersion, nameOf(updateConsumerRedirectURL), "PUT",
      "/management/consumers/CONSUMER_ID/consumer/redirect_url", "Update Consumer RedirectURL",
      "Update an existing redirectUrl for a Consumer specified by CONSUMER_ID.",
      consumerRedirectUrlJSON, consumerJSON,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[ConsumerLogoUrlJson]
            }
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            updatedConsumer <- NewStyle.function.updateConsumer(
              id = consumer.id.get, logoURL = Some(postJson.logo_url), callContext = Some(cc))
          } yield JSONFactory510.createConsumerJSON(updatedConsumer)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateConsumerLogoURL), "PUT",
      "/management/consumers/CONSUMER_ID/consumer/logo_url", "Update Consumer LogoURL",
      "Update an existing logoURL for a Consumer specified by CONSUMER_ID.",
      consumerLogoUrlJson, consumerJsonV510,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[ConsumerCertificateJson]
            }
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            updatedConsumer <- NewStyle.function.updateConsumer(
              id = consumer.id.get, certificate = Some(postJson.certificate), callContext = Some(cc))
          } yield JSONFactory510.createConsumerJSON(updatedConsumer)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateConsumerCertificate), "PUT",
      "/management/consumers/CONSUMER_ID/consumer/certificate", "Update Consumer Certificate",
      "Update Certificate for a Consumer specified by CONSUMER_ID.",
      consumerCertificateJson, consumerJsonV510,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[ConsumerNameJson]
            }
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, Some(cc))
            updatedConsumer <- NewStyle.function.updateConsumer(
              id = consumer.id.get, name = Some(postJson.app_name), callContext = Some(cc))
          } yield JSONFactory510.createConsumerJSON(updatedConsumer)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(updateConsumerName), "PUT",
      "/management/consumers/CONSUMER_ID/consumer/name", "Update Consumer Name",
      "Update an existing name for a Consumer specified by CONSUMER_ID.",
      consumerNameJson, consumerJsonV510,
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
      null, implementedInApiVersion, nameOf(getCallsLimit), "GET",
      "/management/consumers/CONSUMER_ID/consumer/rate-limits", "Get Rate Limits for a Consumer",
      s"Get Calls limits per Consumer.\n\n${userAuthenticationMessage(true)}",
      EmptyBody, callLimitsJson510Example,
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
      null, implementedInApiVersion, nameOf(createMyConsumer), "POST",
      "/my/consumers", "Create a Consumer",
      "Create a Consumer (Authenticated access).",
      createConsumerRequestJsonV510, consumerJsonV510,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagConsumer),
      None,
      http4sPartialFunction = Some(createMyConsumer)
    )

    val createConsumerDynamicRegistration: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "dynamic-registration" / "consumers" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          for {
            postedJwt <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[ConsumerJwtPostJsonV510]
            }
            pem = APIUtil.`getPSD2-CERT`(cc.requestHeaders)
            _ <- Helper.booleanToFuture(PostJsonIsNotSigned, 400, Some(cc)) {
              JwtUtil.verifyJwt(postedJwt.jwt, pem.getOrElse(""))
            }
            postedJson <- NewStyle.function.tryons(InvalidJsonFormat, 400, Some(cc)) {
              net.liftweb.json.parse(JwtUtil.getSignedPayloadAsJson(postedJwt.jwt).getOrElse("{}")).extract[ConsumerPostJsonV510]
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
      null, implementedInApiVersion, nameOf(createConsumerDynamicRegistration), "POST",
      "/dynamic-registration/consumers", "Create a Consumer(Dynamic Registration)",
      "Create a Consumer with full certificate validation (mTLS access) — recommended for PSD2/Berlin Group compliance.",
      ConsumerJwtPostJsonV510(""), consumerJsonV510,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostAccountAccessJsonV510]
            }
            targetViewId = ViewId(postJson.view_id)
            msg = getUserLacksGrantPermissionErrorMessage(viewId, targetViewId)
            _ <- Helper.booleanToFuture(msg, 403, cc = Some(cc)) {
              APIUtil.canGrantAccessToView(com.openbankproject.commons.model.BankIdAccountIdViewId(bankId, accountId, viewId), targetViewId, user, Some(cc))
            }
            (targetUser, _) <- NewStyle.function.findByUserId(postJson.user_id, Some(cc))
            view <- if (isValidSystemViewId(targetViewId.value)) ViewNewStyle.systemView(targetViewId, Some(cc))
                    else ViewNewStyle.customView(targetViewId, BankIdAccountId(bankId, accountId), Some(cc))
            addedView <- JSONFactory400.grantAccountAccessToUser(bankId, accountId, targetUser, view, Some(cc))
          } yield JSONFactory300.createViewJSON(addedView)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(grantUserAccessToViewById), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/account-access/grant", "Grant User access to View",
      "Grants the User identified by USER_ID access to the view on a bank account identified by VIEW_ID.",
      postAccountAccessJsonV510, viewJsonV300,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostAccountAccessJsonV510]
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
      null, implementedInApiVersion, nameOf(revokeUserAccessToViewById), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/account-access/revoke", "Revoke User access to View",
      "Revoke the User identified by USER_ID access to the view identified.",
      postAccountAccessJsonV510, revokedJsonV400,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostCreateUserAccountAccessJsonV510]
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
      null, implementedInApiVersion, nameOf(createUserWithAccountAccessById), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/user-account-access", "Create (DAuth) User with Account Access",
      "Grant access to account/transaction data to a smart contract on the blockchain.",
      postCreateUserAccountAccessJsonV400, List(viewJsonV300),
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
      null, implementedInApiVersion, nameOf(getTransactionRequestById), "GET",
      "/management/transaction-requests/TRANSACTION_REQUEST_ID", "Get Transaction Request by ID.",
      "Returns transaction request specified by TRANSACTION_REQUEST_ID.",
      EmptyBody, transactionRequestWithChargeJSON210,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostTransactionRequestStatusJsonV510]
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
      null, implementedInApiVersion, nameOf(updateTransactionRequestStatus), "PUT",
      "/management/transaction-requests/TRANSACTION_REQUEST_ID", "Update Transaction Request Status",
      s"Update Transaction Request Status.\n\n${userAuthenticationMessage(true)}",
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
      null, implementedInApiVersion, nameOf(getCoreAccountByIdThroughView), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID", "Get Account by Id (Core) through the VIEW_ID",
      "Information returned about the account through VIEW_ID.",
      EmptyBody, moderatedCoreAccountJsonV400,
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
      null, implementedInApiVersion, nameOf(getBankAccountBalances), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/balances", "Get Account Balances by BANK_ID and ACCOUNT_ID through the VIEW_ID",
      "Get the Balances for the Account specified by BANK_ID and ACCOUNT_ID through the VIEW_ID.",
      EmptyBody, accountBalanceV400,
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
      null, implementedInApiVersion, nameOf(getBankAccountsBalancesThroughView), "GET",
      "/banks/BANK_ID/views/VIEW_ID/balances", "Get Account Balances by BANK_ID through the VIEW_ID",
      "Get the Balances for the Account specified by BANK_ID through the VIEW_ID.",
      EmptyBody, accountBalancesV400Json,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostCounterpartyLimitV510]
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
      null, implementedInApiVersion, nameOf(createCounterpartyLimit), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/counterparties/COUNTERPARTY_ID/limits",
      "Create Counterparty Limit",
      "Create limits (single + recurring) for a counterparty.",
      postCounterpartyLimitV510, counterpartyLimitV510,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostCounterpartyLimitV510]
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
      null, implementedInApiVersion, nameOf(updateCounterpartyLimit), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/counterparties/COUNTERPARTY_ID/limits",
      "Update Counterparty Limit",
      "Update existing counterparty limits.",
      postCounterpartyLimitV510, counterpartyLimitV510,
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
      null, implementedInApiVersion, nameOf(getCounterpartyLimit), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/counterparties/COUNTERPARTY_ID/limits",
      "Get Counterparty Limit", "Get Counterparty Limit.",
      EmptyBody, counterpartyLimitV510,
      List($AuthenticatedUserIsRequired, $BankNotFound, $BankAccountNotFound, $UserNoPermissionAccessView,
        $CounterpartyNotFoundByCounterpartyId, InvalidJsonFormat, UnknownError),
      List(apiTagCounterpartyLimits),
      None,
      http4sPartialFunction = Some(getCounterpartyLimit)
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
      null, implementedInApiVersion, nameOf(deleteCounterpartyLimit), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/counterparties/COUNTERPARTY_ID/limits",
      "Delete Counterparty Limit", "Delete Counterparty Limit.",
      EmptyBody, EmptyBody,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[CreateCustomViewJson]
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
      null, implementedInApiVersion, nameOf(createCustomView), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/target-views", "Create Custom View",
      "Create a custom view on bank account. Name MUST start with `_`.",
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[UpdateCustomViewJson]
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
      null, implementedInApiVersion, nameOf(updateCustomView), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/target-views/TARGET_VIEW_ID", "Update Custom View",
      "Update an existing custom view on a bank account.",
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
      null, implementedInApiVersion, nameOf(getCustomView), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/target-views/TARGET_VIEW_ID", "Get Custom View",
      "Returns the custom view on the account.",
      EmptyBody, customViewJsonV510,
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
      null, implementedInApiVersion, nameOf(deleteCustomView), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/target-views/TARGET_VIEW_ID", "Delete Custom View",
      "Deletes the custom view.",
      EmptyBody, EmptyBody,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[BankAccountBalanceRequestJsonV510]
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
      null, implementedInApiVersion, nameOf(createBankAccountBalance), "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/balances", "Create Bank Account Balance",
      s"Create a new Balance for a Bank Account.\n\n${userAuthenticationMessage(true)}",
      bankAccountBalanceRequestJsonV510, bankAccountBalanceResponseJsonV510,
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
      null, implementedInApiVersion, nameOf(getBankAccountBalanceById), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/balances/BALANCE_ID", "Get Bank Account Balance By ID",
      "Get a specific Bank Account Balance.",
      EmptyBody, bankAccountBalanceResponseJsonV510,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[BankAccountBalanceRequestJsonV510]
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
      null, implementedInApiVersion, nameOf(updateBankAccountBalance), "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/balances/BALANCE_ID", "Update Bank Account Balance",
      "Update an existing Bank Account Balance.",
      bankAccountBalanceRequestJsonV510, bankAccountBalanceResponseJsonV510,
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
      null, implementedInApiVersion, nameOf(deleteBankAccountBalance), "DELETE",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/balances/BALANCE_ID", "Delete Bank Account Balance",
      "Delete a Bank Account Balance.",
      EmptyBody, EmptyBody,
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[CreateViewPermissionJson]
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
      null, implementedInApiVersion, nameOf(addSystemViewPermission), "POST",
      "/system-views/VIEW_ID/permissions", "Add Permission to a System View",
      "Add Permission to a System View.",
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
      null, implementedInApiVersion, nameOf(deleteSystemViewPermission), "DELETE",
      "/system-views/VIEW_ID/permissions/PERMISSION_NAME", "Delete Permission to a System View",
      "Delete Permission to a System View.",
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PutConsentStatusJsonV400]
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
      null, implementedInApiVersion, nameOf(updateConsentStatusByConsent), "PUT",
      "/management/banks/BANK_ID/consents/CONSENT_ID", "Update Consent Status by CONSENT_ID",
      s"Update the Status of a Consent. States: ${ConsentStatus.values.toList.sorted.mkString(", ")}.",
      PutConsentStatusJsonV400(status = "AUTHORISED"),
      ConsentChallengeJsonV310(consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945", jwt = "", status = "AUTHORISED"),
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PutConsentPayloadJsonV510]
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
      null, implementedInApiVersion, nameOf(updateConsentAccountAccessByConsentId), "PUT",
      "/management/banks/BANK_ID/consents/CONSENT_ID/account-access", "Update Consent Account Access by CONSENT_ID",
      "Update the Account Access of a Consent.",
      PutConsentPayloadJsonV510(access = code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.ConsentAccessJson()),
      ConsentChallengeJsonV310(consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945", jwt = "", status = "AUTHORISED"),
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PutConsentUserJsonV400]
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
      null, implementedInApiVersion, nameOf(updateConsentUserIdByConsentId), "PUT",
      "/management/banks/BANK_ID/consents/CONSENT_ID/created-by-user", "Update Created by User of Consent by CONSENT_ID",
      "Update the User bound to a consent.",
      PutConsentUserJsonV400(user_id = "ed7a7c01-db37-45cc-ba12-0ae8891c195c"),
      ConsentChallengeJsonV310(consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945", jwt = "", status = "AUTHORISED"),
      List($AuthenticatedUserIsRequired, $BankNotFound, InvalidJsonFormat, ConsentNotFound, InvalidConnectorResponse, UnknownError),
      apiTagConsent :: apiTagPSD2AIS :: Nil,
      Some(List(canUpdateConsentUserAtOneBank, canUpdateConsentUserAtAnyBank)),
      http4sPartialFunction = Some(updateConsentUserIdByConsentId)
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
      null, implementedInApiVersion, nameOf(getMyConsents), "GET",
      "/my/consents", "Get My Consents",
      "Get All Consents that the current User created.",
      EmptyBody, consentsInfoJsonV510,
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
      null, implementedInApiVersion, nameOf(getConsentsAtBank), "GET",
      "/management/consents/banks/BANK_ID", "Get Consents at Bank",
      "Gets the Consents at the specified Bank.",
      EmptyBody, consentsJsonV510,
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
      null, implementedInApiVersion, nameOf(getConsents), "GET",
      "/management/consents", "Get Consents",
      "Gets the Consents.",
      EmptyBody, consentsJsonV510,
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
            _ <- Helper.booleanToFuture(failMsg = ConsentNotFound, failCode = 404, cc = Some(cc)) {
              consent.mUserId == cc.userId
            }
          } yield JSONFactory510.getConsentInfoJson(consent)
        }
    }
    resourceDocs += ResourceDoc(
      null, implementedInApiVersion, nameOf(getConsentByConsentId), "GET",
      "/user/current/consents/CONSENT_ID", "Get Consent By Consent Id via User",
      "Gets the Consent specified by CONSENT_ID belonging to the current User.",
      EmptyBody, consentJsonV510,
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
      null, implementedInApiVersion, nameOf(getConsentByConsentIdViaConsumer), "GET",
      "/consumer/current/consents/CONSENT_ID", "Get Consent By Consent Id via Consumer",
      "Gets the Consent specified by CONSENT_ID belonging to the current Consumer.",
      EmptyBody, consentJsonV500,
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
      null, implementedInApiVersion, nameOf(revokeConsentAtBank), "DELETE",
      "/banks/BANK_ID/consents/CONSENT_ID", "Revoke Consent at Bank",
      "Revoke Consent specified by CONSENT_ID.",
      EmptyBody, revokedConsentJsonV310,
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
      null, implementedInApiVersion, nameOf(selfRevokeConsent), "DELETE",
      "/my/consent/current", "Revoke Consent used in the Current Call",
      "Revoke Consent specified by Consent-Id at Request Header.",
      EmptyBody, revokedConsentJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(selfRevokeConsent)
    )

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
      null, implementedInApiVersion, nameOf(revokeMyConsent), "DELETE",
      "/my/consents/CONSENT_ID", "Revoke My Consent",
      "Revoke a Consent for the current user, specified by CONSENT_ID.",
      EmptyBody, revokedConsentJsonV310,
      List($AuthenticatedUserIsRequired, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(revokeMyConsent)
    )

    val createConsentImplicit: HttpRoutes[IO] = HttpRoutes.of[IO] {
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
              net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostConsentBodyCommonJson]
            }
            maxTimeToLive = APIUtil.getPropsAsIntValue(nameOfProperty = "consents.max_time_to_live", defaultValue = 3600)
            _ <- Helper.booleanToFuture(s"$ConsentMaxTTL ($maxTimeToLive)", cc = callContextOpt) {
              consentJson.time_to_live match {
                case Some(ttl) => ttl <= maxTimeToLive
                case _         => true
              }
            }
            requestedEntitlements = consentJson.entitlements
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
                MappedConsent.find(By(MappedConsent.mConsentId, createdConsent.consentId))
                  .map(_.mStatus(ConsentStatus.ACCEPTED.toString).saveMe()).head
              }
            } else {
              val challengeText = s"Your consent challenge : ${challengeAnswer}, Application: $applicationText"
              scaMethod match {
                case v if v == StrongCustomerAuthentication.EMAIL.toString =>
                  for {
                    postEmail <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostConsentEmailJsonV310", 400, callContextOpt) {
                      net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostConsentEmailJsonV310]
                    }
                    _ <- NewStyle.function.sendCustomerNotification(
                      StrongCustomerAuthentication.EMAIL, postEmail.email,
                      Some("OBP Consent Challenge"), challengeText, callContextOpt)
                  } yield createdConsent
                case v if v == StrongCustomerAuthentication.SMS.toString =>
                  for {
                    postPhone <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostConsentPhoneJsonV310", 400, callContextOpt) {
                      net.liftweb.json.parse(cc.httpBody.getOrElse("")).extract[PostConsentPhoneJsonV310]
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
      null, implementedInApiVersion, nameOf(createConsentImplicit), "POST",
      "/my/consents/IMPLICIT", "Create Consent (IMPLICIT)",
      "Create a Consent in INITIATED state. SCA challenge is sent OOB based on SCA_METHOD.",
      postConsentImplicitJsonV310, consentJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, InvalidJsonFormat, ConsentAllowedScaMethods,
        RolesAllowedInConsent, ViewsAllowedInConsent, ConsumerNotFoundByConsumerId, ConsumerIsDisabled,
        MissingPropsValueAtThisInstance, SmsServerNotResponding, InvalidConnectorResponse, UnknownError),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(createConsentImplicit)
    )

    // ─── createVRPConsentRequest ────────────────────────────────────────────

    val createVRPConsentRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "consumer" / "vrp-consent-requests" =>
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: code.api.util.CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val parsedBody = net.liftweb.json.parse(rawBody)
          for {
            (_, callContextOpt) <- APIUtil.applicationAccess(cc)
            _ <- APIUtil.passesPsd2Aisp(callContextOpt)
            postConsentRequestJsonV510 <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostVRPConsentRequestJsonV510 ", 400, callContextOpt) {
              parsedBody.extract[PostVRPConsentRequestJsonV510]
            }
            maxTimeToLive = APIUtil.getPropsAsIntValue(nameOfProperty = "consents.max_time_to_live", defaultValue = 3600)
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
            consentTypeJ = net.liftweb.json.parse(s"""{"consent_type": "${ConsentType.VRP}"}""")
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
      null, implementedInApiVersion, nameOf(createVRPConsentRequest), "POST",
      "/consumer/vrp-consent-requests", "Create Consent Request VRP",
      "Create a Variable Recurring Payments (VRP) Consent Request.",
      postVRPConsentRequestJsonV510, vrpConsentRequestResponseJson,
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
          .orElse(getMyConsents(req))
          .orElse(getConsentsAtBank(req))
          .orElse(getConsents(req))
          .orElse(getConsentByConsentId(req))
          .orElse(getConsentByConsentIdViaConsumer(req))
          .orElse(revokeConsentAtBank(req))
          .orElse(selfRevokeConsent(req))
          .orElse(revokeMyConsent(req))
          .orElse(createConsentImplicit(req))
          .orElse(createVRPConsentRequest(req))
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
