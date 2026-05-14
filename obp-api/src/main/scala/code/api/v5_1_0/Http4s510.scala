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
  CustomerId, ProductCode, TransactionRequestId, User, View, ViewId
}
import com.openbankproject.commons.model.enums.{StrongCustomerAuthentication, TransactionRequestStatus}
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
