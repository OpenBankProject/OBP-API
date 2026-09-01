package code.api.v3_1_0

import org.json4s._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import code.api.Constant._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.Glossary
import code.api.util.ExampleValue._
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, _}
import code.api.util.ApiRole
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.CertificateUtil
import code.api.util.{ApiTrigger, Consent, Glossary, SecureRandomUtil}
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.http4s.ResourceDocMiddleware
import code.api.util.http4s.IdempotencyMiddleware
import code.api.util.newstyle.{BalanceNewStyle, ViewNewStyle}
import code.api.util.{APIUtil, CallContext, CustomJsonFormats, NewStyle, OBPBankId, RateLimitingUtil}
import code.api.v1_2_1.{JSONFactory, RateLimiting}
import code.api.v2_1_0.{JSONFactory210, PutEnabledJSON}
import code.api.v3_0_0.{CreateViewJsonV300, JSONFactory300}
import code.api.v3_1_0.JSONFactory310._
import code.bankconnectors.Connector
import code.consent.{ConsentStatus, Consents, DoobieConsentQueries, MappedConsent}
import code.methodrouting.{MethodRouting, MethodRoutingCommons, MethodRoutingParam}
import code.model.dataAccess.AuthUser
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import code.metrics.APIMetrics
import code.model._
import code.ratelimiting.RateLimitingDI
import code.userlocks.UserLocksProvider
import code.users.Users
import code.views.Views
import code.webhook.AccountWebhook
import code.webuiprops.{MappedWebUiPropsProvider, WebUiPropsCommons}
import code.api.Constant
import code.model.dataAccess.BankAccountCreation
import com.openbankproject.commons.dto.GetProductsParam
import com.openbankproject.commons.model.enums.{AccountAttributeType, CardAttributeType, ProductAttributeType, StrongCustomerAuthentication}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.{ApiVersion, ApiVersionStatus, ScannedApiVersion}
import net.liftweb.common.{Empty, Full}
import org.json4s.Formats
import net.liftweb.mapper.By
import net.liftweb.util.{Helpers, Props}
import org.apache.commons.lang3.StringUtils

import java.text.SimpleDateFormat
import java.util.regex.Pattern
import org.http4s._
import org.http4s.dsl.io._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

object Http4s310 {
  val implementedInApiVersion: ScannedApiVersion = ApiVersion.v3_1_0
  val versionStatus: String                      = ApiVersionStatus.STABLE.toString
  val resourceDocs: ArrayBuffer[ResourceDoc]     = ArrayBuffer[ResourceDoc]()

  implicit val formats: Formats = CustomJsonFormats.formats

  type HttpF[A] = OptionT[IO, A]

  // Local doc-strings carried over from the commented-out APIMethods310.scala
  // so the restored ResourceDoc descriptions compile. Kept verbatim — these
  // are referenced inside `s"""..."""` interpolations in the doc text.
  private val productAttributeGeneralInfo =
    s"""
       |Product Attributes are used to describe a financial Product with a list of typed key value pairs.
       |
       |Each Product Attribute is linked to its Product by PRODUCT_CODE
       |
       |
     """.stripMargin

  private val accountAttributeGeneralInfo =
    s"""
       |Account Attributes are used to describe a financial Product with a list of typed key value pairs.
       |
       |Each Account Attribute is linked to its Account by ACCOUNT_ID
       |
       |
     """.stripMargin

  private val supportedConnectorNames =
    NewStyle.function.getSupportedConnectorNames().mkString("[", " | ", "]")

  private val generalObpConsentText: String =
    s"""
       |
       |An OBP Consent allows the holder of the Consent to call one or more endpoints.
       |
       |Consents must be created and authorisied using SCA (Strong Customer Authentication).
       |
       |That is, Consents can be created by an authorised User via the OBP REST API but they must be confirmed via an out of band (OOB) mechanism such as a code sent to a mobile phone.
       |
       |Each Consent has one of the following states: ${ConsentStatus.values.toList.sorted.mkString(", ") }.
       |
       |Each Consent is bound to a consumer i.e. you need to identify yourself over request header value Consumer-Key.
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

  object Implementations3_1_0 {
    val prefixPath: Path = Root / ApiPathZero.toString / implementedInApiVersion.toString

    private val productAttributeGeneralInfo =
      s"""Product Attributes are used to describe a financial Product with a list of typed key value pairs.
         |
         |Each Product Attribute is linked to its Product by PRODUCT_CODE
         |""".stripMargin

    private val accountAttributeGeneralInfo =
      s"""Account Attributes are used to describe a financial Product with a list of typed key value pairs.
         |
         |Each Account Attribute is linked to its Account by ACCOUNT_ID
         |""".stripMargin

    private val generalObpConsentText: String =
      s"""An OBP Consent allows the holder of the Consent to call one or more endpoints.
         |
         |Consents must be created and authorised using SCA (Strong Customer Authentication).
         |
         |That is, Consents can be created by an authorised User via the OBP REST API but they must be confirmed via an out of band (OOB) mechanism such as a code sent to a mobile phone.
         |
         |Each Consent has one of the following states: ${code.consent.ConsentStatus.values.toList.sorted.mkString(", ")}.
         |""".stripMargin

    private val supportedConnectorNames = NewStyle.function.getSupportedConnectorNames().mkString("[", " | ", "]")

    // ─── root ─────────────────────────────────────────────────────────────────

    val root: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory.getApiInfoJSON(ApiVersion.v3_1_0, versionStatus))
        }
      case req @ GET -> `prefixPath` / "root" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory.getApiInfoJSON(ApiVersion.v3_1_0, versionStatus))
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
      |* Git Commit""",
      EmptyBody,
      apiInfoJSON,
      List(UnknownError, MandatoryPropertyIsNotSet),
      apiTagApi :: Nil,
      None,
      http4sPartialFunction = Some(root)
    )

    // ─── getCheckbookOrders ───────────────────────────────────────────────────

    val getCheckbookOrders: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "checkbook" / "orders" =>
        EndpointHelpers.withView(req) { (_, account, _, cc) =>
          for {
            (checkbookOrders, _) <- Connector.connector.vend.getCheckbookOrders(
              account.bankId.value, account.accountId.value, Some(cc)) map {
              unboxFullOrFail(_, Some(cc), InvalidConnectorResponseForGetCheckbookOrdersFuture)
            }
          } yield JSONFactory310.createCheckbookOrdersJson(checkbookOrders)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getCheckbookOrders), "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/checkbook/orders",
      "Get Checkbook orders",
      s"""${mockedDataText(false)}Get all checkbook orders""",
      EmptyBody, checkbookOrdersJson,
      List(AuthenticatedUserIsRequired, BankNotFound, BankAccountNotFound,
        InvalidConnectorResponseForGetCheckbookOrdersFuture, UnknownError),
      apiTagAccount :: Nil, None,
      http4sPartialFunction = Some(getCheckbookOrders))

    // ─── getStatusOfCreditCardOrder ───────────────────────────────────────────

    val getStatusOfCreditCardOrder: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "credit_cards" / "orders" =>
        EndpointHelpers.withView(req) { (_, account, _, cc) =>
          for {
            (cards, _) <- Connector.connector.vend.getStatusOfCreditCardOrder(
              account.bankId.value, account.accountId.value, Some(cc)) map {
              unboxFullOrFail(_, Some(cc), InvalidConnectorResponseForGetStatusOfCreditCardOrderFuture)
            }
          } yield JSONFactory310.createStatisOfCreditCardJson(cards)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getStatusOfCreditCardOrder),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/credit_cards/orders",
      "Get status of Credit Card order ",
      s"""${mockedDataText(false)}Get status of Credit Card orders
      |Get all orders
      |""",
      EmptyBody,
      creditCardOrderStatusResponseJson,
      List(AuthenticatedUserIsRequired, BankNotFound, BankAccountNotFound,
      InvalidConnectorResponseForGetStatusOfCreditCardOrderFuture, UnknownError),
      apiTagCard :: Nil,
      None,
      http4sPartialFunction = Some(getStatusOfCreditCardOrder)
    )

    // ─── getTopAPIs ───────────────────────────────────────────────────────────

    val getTopAPIs: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "metrics" / "top-apis" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          val httpParams = req.headers.headers.toList.map(h => HTTPParam(h.name.toString, h.value)) :::
            req.uri.query.multiParams.toList.flatMap { case (k, vs) => vs.map(v => HTTPParam(k, v)) }
          for {
            (params, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            topApis <- APIMetrics.apiMetrics.vend.getTopApisFuture(params) map {
              unboxFullOrFail(_, Some(cc), GetTopApisError)
            }
          } yield JSONFactory310.createTopApisJson(topApis)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getTopAPIs),
      "GET",
      "/management/metrics/top-apis",
      "Get Top APIs",
      s"""Get metrics about the most popular APIs. e.g.: total count, response time (in ms), etc.
         |
         |Should be able to filter on the following fields
         |
         |eg: /management/metrics/top-apis?from_date=$epochTimeString&to_date=$DefaultToDateString&consumer_id=5
         |&user_id=66214b8e-259e-44ad-8868-3eb47be70646&implemented_by_partial_function=getTransactionsForBankAccount
         |&implemented_in_version=v3.0.0&url=/obp/v3.0.0/banks/gh.29.uk/accounts/8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0/owner/transactions
         |&verb=GET&anon=false&app_name=MapperPostman
         |&exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
         |
         |1 from_date (defaults to the one year ago): eg:from_date=$epochTimeString
         |
         |2 to_date (defaults to the current date) eg:to_date=$DefaultToDateString
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
         |12 duration (if null ignore) non digit chars will be silently omitted
         |
         |13 exclude_app_names (if null ignore).eg: &exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
         |
         |14 exclude_url_patterns (if null ignore).you can design you own SQL NOT LIKE pattern. eg: &exclude_url_patterns=%management/metrics%,%management/aggregate-metrics%
         |
         |15 exclude_implemented_by_partial_functions (if null ignore).eg: &exclude_implemented_by_partial_functions=getMetrics,getConnectorMetrics,getAggregateMetrics
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      topApisJson,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidFilterParameterFormat,
      GetTopApisError, UnknownError),
      apiTagMetric :: Nil,
      Some(List(canReadMetrics)),
      http4sPartialFunction = Some(getTopAPIs)
    )

    // ─── getMetricsTopConsumers ───────────────────────────────────────────────

    val getMetricsTopConsumers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "metrics" / "top-consumers" =>
        EndpointHelpers.withUser(req) { (_, cc) =>
          val httpParams = req.headers.headers.toList.map(h => HTTPParam(h.name.toString, h.value)) :::
            req.uri.query.multiParams.toList.flatMap { case (k, vs) => vs.map(v => HTTPParam(k, v)) }
          for {
            (params, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            topConsumers <- APIMetrics.apiMetrics.vend.getTopConsumersFuture(params) map {
              unboxFullOrFail(_, Some(cc), GetMetricsTopConsumersError)
            }
          } yield JSONFactory310.createTopConsumersJson(topConsumers)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getMetricsTopConsumers),
      "GET",
      "/management/metrics/top-consumers",
      "Get Top Consumers",
      s"""Get metrics about the top consumers of the API usage e.g. total count, consumer_id and app_name.
         |
         |Should be able to filter on the following fields
         |
         |e.g.: /management/metrics/top-consumers?from_date=$epochTimeString&to_date=$DefaultToDateString&consumer_id=5
         |&user_id=66214b8e-259e-44ad-8868-3eb47be70646&implemented_by_partial_function=getTransactionsForBankAccount
         |&implemented_in_version=v3.0.0&url=/obp/v3.0.0/banks/gh.29.uk/accounts/8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0/owner/transactions
         |&verb=GET&anon=false&app_name=MapperPostman
         |&exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
         |&limit=100
         |
         |1 from_date (defaults to the one year ago): eg:from_date=$epochTimeString
         |
         |2 to_date (defaults to the current date) eg:to_date=$DefaultToDateString
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
         |12 duration (if null ignore) non digit chars will be silently omitted
         |
         |13 exclude_app_names (if null ignore).eg: &exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
         |
         |14 exclude_url_patterns (if null ignore).you can design you own SQL NOT LIKE pattern. eg: &exclude_url_patterns=%management/metrics%,%management/aggregate-metrics%
         |
         |15 exclude_implemented_by_partial_functions (if null ignore).eg: &exclude_implemented_by_partial_functions=getMetrics,getConnectorMetrics,getAggregateMetrics
         |
         |16 limit (for pagination: defaults to 50)  eg:limit=200
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      topConsumersJson,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidFilterParameterFormat,
      GetMetricsTopConsumersError, UnknownError),
      apiTagMetric :: Nil,
      Some(List(canReadMetrics)),
      http4sPartialFunction = Some(getMetricsTopConsumers)
    )

    // ─── getFirehoseCustomers ────────────────────────────────────────────────
    // Firehose pattern: prop check (→400) before role check (→403) before bank lookup (→404).
    // Uses non-standard ALL_CAPS template var FIREHOSE_BANK_ID so middleware skips bank validation.

    val getFirehoseCustomers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "firehose" / "customers" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val roles = ApiRole.canUseCustomerFirehose :: canUseCustomerFirehoseAtAnyBank :: Nil
          val roleMsg = UserHasMissingRoles + roles.mkString(" or ")
          for {
            // Lift used AccountFirehoseNotAllowedOnThisInstance here despite this being the
            // customer firehose endpoint — preserve the message verbatim (the test asserts it).
            _ <- code.util.Helper.booleanToFuture(AccountFirehoseNotAllowedOnThisInstance, cc = Some(cc)) {
              allowCustomerFirehose
            }
            _ <- code.util.Helper.booleanToFuture(roleMsg, failCode = 403, cc = Some(cc)) {
              APIUtil.hasAtLeastOneEntitlement(bankIdStr, user.userId, roles)
            }
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            allowedParams = List("sort_direction", "limit", "offset", "from_date", "to_date")
            httpParams = req.headers.headers.toList.map(h => HTTPParam(h.name.toString, h.value)) :::
              req.uri.query.multiParams.toList.flatMap { case (k, vs) => vs.map(v => HTTPParam(k, v)) }
            (obpQueryParams, _) <- NewStyle.function.createObpParams(httpParams, allowedParams, Some(cc))
            customers <- NewStyle.function.getCustomers(BankId(bankIdStr), Some(cc), obpQueryParams)
            reqParams: Map[String, List[String]] = req.uri.query.multiParams
              .filterNot { case (k, _) => allowedParams.contains(k) }
              .map { case (k, vs) => k -> vs.toList }
            customersFiltered <- if (reqParams.isEmpty) Future.successful(customers)
            else for {
              (customerIds, _) <- NewStyle.function.getCustomerIdsByAttributeNameValues(BankId(bankIdStr), reqParams, Some(cc))
            } yield customers.filter(customer => customerIds.contains(CustomerId(customer.customerId)))
          } yield JSONFactory300.createCustomersJson(customersFiltered)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getFirehoseCustomers),
      "GET",
      "/banks/FIREHOSE_BANK_ID/firehose/customers",
      "Get Firehose Customers",
      s"""
      |Get Customers that has a firehose View.
      |
      |Allows bulk access to customers.
      |User must have the CanUseFirehoseAtAnyBank Role
      |
      |${urlParametersDocument(true, true)}
      |
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      EmptyBody,
      customerJSONs,
      List(AuthenticatedUserIsRequired, CustomerFirehoseNotAllowedOnThisInstance,
      UserHasMissingRoles, UnknownError),
      List(apiTagCustomer, apiTagFirehoseData),
      None,
      http4sPartialFunction = Some(getFirehoseCustomers)
    )

    // ─── getBadLoginStatus ────────────────────────────────────────────────────

    val getBadLoginStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / username / "lock-status" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canReadUserLockedStatus, Some(cc))
            _ <- Users.users.vend.getUserByProviderAndUsernameFuture(Constant.localIdentityProvider, username) map {
              x => unboxFullOrFail(x, Some(cc), UserNotFoundByProviderAndUsername, 404)
            }
            badLoginStatus <- Future {
              LoginAttempt.getOrCreateBadLoginStatus(localIdentityProvider, username)
            } map { unboxFullOrFail(_, Some(cc), s"$UserNotFoundByProviderAndUsername($username)", 404) }
          } yield createBadLoginStatusJson(badLoginStatus)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getBadLoginStatus),
      "GET",
      "/users/USERNAME/lock-status",
      "Get User Lock Status",
      s"""
      |Get User Login Status.
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      EmptyBody,
      badLoginStatusJson,
      List(AuthenticatedUserIsRequired, UserNotFoundByProviderAndUsername,
      UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canReadUserLockedStatus)),
      http4sPartialFunction = Some(getBadLoginStatus)
    )

    // ─── getCallsLimit ────────────────────────────────────────────────────────

    val getCallsLimit: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consumers" / consumerIdStr / "consumer" / "call-limits" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canReadCallLimits, Some(cc))
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerIdStr, Some(cc))
            rateLimit <- Future(RateLimitingUtil.consumerRateLimitState(consumer.consumerId.get).toList)
          } yield createCallLimitJson(consumer, rateLimit)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCallsLimit),
      "GET",
      "/management/consumers/CONSUMER_ID/consumer/call-limits",
      "Get Rate Limits for a Consumer",
      s"""
      |Get Rate Limits per Consumer.
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      EmptyBody,
      callLimitJson,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidConsumerId,
      ConsumerNotFoundByConsumerId, UserHasMissingRoles, UpdateConsumerError, UnknownError),
      List(apiTagConsumer),
      Some(List(canReadCallLimits)),
      http4sPartialFunction = Some(getCallsLimit)
    )

    // ─── getConsumer ──────────────────────────────────────────────────────────

    val getConsumer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consumers" / consumerIdStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canGetConsumers, Some(cc))
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerIdStr, Some(cc))
            consumerUser <- Users.users.vend.getUserByUserIdFuture(consumer.createdByUserId.get)
          } yield createConsumerJSON(consumer, consumerUser)
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
      List(AuthenticatedUserIsRequired, UserHasMissingRoles,
      ConsumerNotFoundByConsumerId, UnknownError),
      List(apiTagConsumer),
      Some(List(canGetConsumers)),
      http4sPartialFunction = Some(getConsumer)
    )

    // ─── getConsumersForCurrentUser ──────────────────────────────────────────

    val getConsumersForCurrentUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "users" / "current" / "consumers" =>
        EndpointHelpers.withUser(req) { (user, _) =>
          for {
            consumers <- Consumers.consumers.vend.getConsumersByUserIdFuture(user.userId)
          } yield createConsumersJson(consumers, Full(user))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getConsumersForCurrentUser),
      "GET",
      "/management/users/current/consumers",
      "Get Consumers (logged in User)",
      s"""Get the Consumers for logged in User.
      |
      |""",
      EmptyBody,
      consumersJson310,
      List(AuthenticatedUserIsRequired, UnknownError),
      List(apiTagConsumer),
      None,
      http4sPartialFunction = Some(getConsumersForCurrentUser)
    )

    // ─── getConsumers ────────────────────────────────────────────────────────

    val getConsumers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "consumers" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canGetConsumers, Some(cc))
            httpParams = req.headers.headers.toList.map(h => HTTPParam(h.name.toString, h.value)) :::
              req.uri.query.multiParams.toList.flatMap { case (k, vs) => vs.map(v => HTTPParam(k, v)) }
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            consumers <- Consumers.consumers.vend.getConsumersFuture(obpQueryParams, Some(cc))
            users <- Users.users.vend.getUsersByUserIdsFuture(consumers.map(_.createdByUserId.get))
          } yield createConsumersJson(consumers, users)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getConsumers),
      "GET",
      "/management/consumers",
      "Get Consumers",
      s"""Get the all Consumers.
      |
      |${userAuthenticationMessage(true)}
      |
      |${urlParametersDocument(true, true)}
      |
      |""",
      EmptyBody,
      consumersJson310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagConsumer),
      Some(List(canGetConsumers)),
      http4sPartialFunction = Some(getConsumers)
    )

    // ─── getAccountWebhooks ──────────────────────────────────────────────────

    val getAccountWebhooks: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / _ / "account-web-hooks" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, ApiRole.canGetWebhooks, Some(cc))
            httpParams = req.headers.headers.toList.map(h => HTTPParam(h.name.toString, h.value)) :::
              req.uri.query.multiParams.toList.flatMap { case (k, vs) => vs.map(v => HTTPParam(k, v)) }
            allowedParams = List("limit", "offset", "account_id", "user_id")
            (obpParams, _) <- NewStyle.function.createObpParams(httpParams, allowedParams, Some(cc))
            additionalParam = OBPBankId(bank.bankId.value)
            webhooks <- NewStyle.function.getAccountWebhooks(additionalParam :: obpParams, Some(cc))
          } yield createAccountWebhooksJson(webhooks)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAccountWebhooks),
      "GET",
      "/management/banks/BANK_ID/account-web-hooks",
      "Get Account Webhooks",
      s"""Get Account Webhooks.
      |
      |Possible custom URL parameters for pagination:
      |
      |${urlParametersDocument(false, false)}
      |* account_id=STRING (if null ignore)
      |* user_id=STRING (if null ignore)
      |
      |
      |""",
      EmptyBody,
      accountWebhooksJson,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagWebhook :: apiTagBank :: Nil,
      Some(List(canGetWebhooks)),
      http4sPartialFunction = Some(getAccountWebhooks)
    )

    // ─── config ───────────────────────────────────────────────────────────────

    val config: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "config" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canGetConfig, Some(cc))
          } yield JSONFactory310.getConfigInfoJSON()
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(config),
      "GET",
      "/config",
      "Get API Configuration",
      """Returns information about:
      |
      |* The default bank_id
      |* Akka configuration
      |* Elastic Search configuration
      |* Cached functions """,
      EmptyBody,
      configurationJSON,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      apiTagApi :: Nil,
      Some(List(canGetConfig)),
      http4sPartialFunction = Some(config)
    )

    // ─── getAdapterInfo ───────────────────────────────────────────────────────

    val getAdapterInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "adapter" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canGetAdapterInfo, Some(cc))
            (ai, _) <- NewStyle.function.getAdapterInfo(Some(cc))
          } yield JSONFactory300.createAdapterInfoJson(ai)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAdapterInfo),
      "GET",
      "/adapter",
      "Get Adapter Info",
      s"""Get basic information about the Adapter.
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      adapterInfoJsonV300,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagApi),
      Some(List(canGetAdapterInfo)),
      http4sPartialFunction = Some(getAdapterInfo)
    )

    // ─── getRateLimitingInfo ──────────────────────────────────────────────────
    // Anonymous endpoint — no auth required.

    val getRateLimitingInfo: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "rate-limiting" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            rateLimiting <- NewStyle.function.tryons("", 400, Some(cc)) {
              val isActive = if (RateLimitingUtil.useConsumerLimits) true else false
              RateLimiting(RateLimitingUtil.useConsumerLimits, "REDIS", true, isActive)
            }
          } yield createRateLimitingInfo(rateLimiting)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getRateLimitingInfo),
      "GET",
      "/rate-limiting",
      "Get Rate Limiting Info",
      s"""Get information about the Rate Limiting setup on this OBP Instance such as:
         |
         |Is rate limiting enabled and active?
         |What backend is used to keep track of the API calls (e.g. REDIS).
         |
         |Note: Rate limiting can be set at the Consumer level and also for anonymous calls.
         |
         |See the consumer rate limits / call limits endpoints.
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      rateLimitingInfoV310,
      List(UnknownError),
      List(apiTagApi, apiTagRateLimits),
      None,
      http4sPartialFunction = Some(getRateLimitingInfo)
    )

    // ─── getCustomerByCustomerId ──────────────────────────────────────────────

    val getCustomerByCustomerId: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customers" / customerIdStr =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canGetCustomersAtOneBank, Some(cc))
            (customer, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (customerAttributes, _) <- NewStyle.function.getCustomerAttributes(
              bank.bankId, CustomerId(customerIdStr), Some(cc))
          } yield JSONFactory310.createCustomerWithAttributesJson(customer, customerAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCustomerByCustomerId),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID",
      "Get Customer by CUSTOMER_ID",
      s"""Gets the Customer specified by CUSTOMER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      customerWithAttributesJsonV310,
      List(
        AuthenticatedUserIsRequired,
        UserHasMissingRoles,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer),
      Some(List(canGetCustomersAtOneBank)),
      http4sPartialFunction = Some(getCustomerByCustomerId)
    )

    // ─── getUserAuthContexts ─────────────────────────────────────────────────

    val getUserAuthContexts: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "users" / userIdStr / "auth-context" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetUserAuthContext, Some(cc))
            (_, _) <- NewStyle.function.findByUserId(userIdStr, Some(cc))
            (userAuthContexts, _) <- NewStyle.function.getUserAuthContexts(userIdStr, Some(cc))
          } yield JSONFactory310.createUserAuthContextsJson(userAuthContexts)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getUserAuthContexts),
      "GET",
      "/users/USER_ID/auth-context",
      "Get User Auth Contexts",
      s"""Get User Auth Contexts for a User.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      userAuthContextsJson,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(canGetUserAuthContext :: Nil),
      http4sPartialFunction = Some(getUserAuthContexts)
    )

    // ─── getTaxResidence ─────────────────────────────────────────────────────

    val getTaxResidence: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "tax-residences" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canGetTaxResidence, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (taxResidences, _) <- NewStyle.function.getTaxResidences(customerIdStr, Some(cc))
          } yield JSONFactory310.createTaxResidences(taxResidences)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getTaxResidence),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/tax-residences",
      "Get Tax Residences of Customer",
      s"""Get the Tax Residences of the Customer specified by CUSTOMER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      taxResidencesJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagCustomer, apiTagKyc),
      Some(List(canGetTaxResidence)),
      http4sPartialFunction = Some(getTaxResidence)
    )

    // ─── getAllEntitlements ──────────────────────────────────────────────────

    val getAllEntitlements: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "entitlements" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val roleName = req.uri.query.params.getOrElse("role", "")
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetEntitlementsForAnyUserAtAnyBank, Some(cc))
            entitlements <- Entitlement.entitlement.vend.getEntitlementsByRoleFuture(roleName) map {
              connectorEmptyResponse(_, Some(cc))
            }
          } yield JSONFactory310.createEntitlementJsonsV310(entitlements)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAllEntitlements),
      "GET",
      "/entitlements",
      "Get all Entitlements",
      s"""
         |
         |Login is required.
         |
         |Possible filter on the role field:
         |
         |eg: /entitlements?role=${canGetCustomersAtOneBank.toString}
         |
         |
         |
      """.stripMargin,
      EmptyBody,
      entitlementJSonsV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagRole, apiTagEntitlement),
      None,
      http4sPartialFunction = Some(getAllEntitlements)
    )

    // ─── getCustomerAddresses ────────────────────────────────────────────────

    val getCustomerAddresses: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "addresses" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canGetCustomerAddress, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (addresses, _) <- NewStyle.function.getCustomerAddress(customerIdStr, Some(cc))
          } yield JSONFactory310.createAddresses(addresses)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCustomerAddresses),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/addresses",
      "Get Customer Addresses",
      s"""Get the Addresses of the Customer specified by CUSTOMER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      customerAddressesJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagCustomer, apiTagKyc),
      Some(List(canGetCustomerAddress)),
      http4sPartialFunction = Some(getCustomerAddresses)
    )

    // ─── getProductAttribute ─────────────────────────────────────────────────

    val getProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "products" / _ / "attributes" / productAttributeIdStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bankIdStr, user.userId, canGetProductAttribute, Some(cc))
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            (productAttribute, _) <- NewStyle.function.getProductAttributeById(productAttributeIdStr, Some(cc))
          } yield createProductAttributeJson(productAttribute)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getProductAttribute),
      "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE/attributes/PRODUCT_ATTRIBUTE_ID",
      "Get Product Attribute",
      s""" Get Product Attribute
      |
      |$productAttributeGeneralInfo
      |
      |Get one product attribute by its id.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      productAttributeResponseJson,
      List(UserHasMissingRoles, UnknownError),
      List(apiTagProduct, apiTagProductAttribute, apiTagAttribute),
      Some(List(canGetProductAttribute)),
      http4sPartialFunction = Some(getProductAttribute)
    )

    // ─── getAccountApplications ──────────────────────────────────────────────

    val getAccountApplications: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "account-applications" =>
        EndpointHelpers.withUserAndBank(req) { (user, _, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetAccountApplications, Some(cc))
            (accountApplications, _) <- NewStyle.function.getAllAccountApplication(Some(cc))
            (users, _) <- NewStyle.function.findUsers(accountApplications.map(_.userId), Some(cc))
            (customers, _) <- NewStyle.function.findCustomers(accountApplications.map(_.customerId), Some(cc))
          } yield JSONFactory310.createAccountApplications(accountApplications, users, customers)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAccountApplications),
      "GET",
      "/banks/BANK_ID/account-applications",
      "Get Account Applications",
      s"""Get the Account Applications.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      accountApplicationsJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagAccountApplication, apiTagAccount),
      None,
      http4sPartialFunction = Some(getAccountApplications)
    )

    // ─── getAccountApplication ───────────────────────────────────────────────

    val getAccountApplication: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "account-applications" / accountApplicationIdStr =>
        EndpointHelpers.withUserAndBank(req) { (_, _, cc) =>
          for {
            (accountApplication, _) <- NewStyle.function.getAccountApplicationById(accountApplicationIdStr, Some(cc))
            userId = Option(accountApplication.userId)
            customerId = Option(accountApplication.customerId)
            user <- unboxOptionOBPReturnType(userId.map(NewStyle.function.findByUserId(_, Some(cc))))
            customer <- unboxOptionOBPReturnType(customerId.map(NewStyle.function.getCustomerByCustomerId(_, Some(cc))))
          } yield createAccountApplicationJson(accountApplication, user, customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getAccountApplication),
      "GET",
      "/banks/BANK_ID/account-applications/ACCOUNT_APPLICATION_ID",
      "Get Account Application by Id",
      s"""Get the Account Application.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      accountApplicationResponseJson,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagAccountApplication, apiTagAccount),
      None,
      http4sPartialFunction = Some(getAccountApplication)
    )

    // ─── getMeetings ─────────────────────────────────────────────────────────

    val getMeetings: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "meetings" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            (meetings, _) <- NewStyle.function.getMeetings(bank.bankId, user, Some(cc))
          } yield createMeetingsJson(meetings)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getMeetings),
      "GET",
      "/banks/BANK_ID/meetings",
      "Get Meetings",
      """Meetings contain meta data about, and are used to facilitate, video conferences / chats etc.
        |
        |The actual conference/chats are handled by external services.
        |
        |Login is required.
        |
        |This call is **experimental** and will require further authorisation in the future.
      """.stripMargin,
      EmptyBody,
      meetingsJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagMeeting, apiTagCustomer, apiTagExperimental),
      None,
      http4sPartialFunction = Some(getMeetings)
    )

    // ─── getMeeting ──────────────────────────────────────────────────────────

    val getMeeting: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "meetings" / meetingIdStr =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            (meeting, _) <- NewStyle.function.getMeeting(bank.bankId, user, meetingIdStr, Some(cc))
          } yield JSONFactory310.createMeetingJson(meeting)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getMeeting),
      "GET",
      "/banks/BANK_ID/meetings/MEETING_ID",
      "Get Meeting",
      """Get Meeting specified by BANK_ID / MEETING_ID
        |Meetings contain meta data about, and are used to facilitate, video conferences / chats etc.
        |
        |The actual conference/chats are handled by external services.
        |
        |Login is required.
        |
        |This call is **experimental** and will require further authorisation in the future.
      """.stripMargin,
      EmptyBody,
      meetingJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, MeetingNotFound, UnknownError),
      List(apiTagMeeting, apiTagCustomer, apiTagExperimental),
      None,
      http4sPartialFunction = Some(getMeeting)
    )

    // ─── getServerJWK ────────────────────────────────────────────────────────

    val getServerJWK: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "certs" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(com.openbankproject.commons.util.JsonAliases.parse(CertificateUtil.convertRSAPublicKeyToAnRSAJWK()))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getServerJWK),
      "GET",
      "/certs",
      "Get JSON Web Key (JWK)",
      """Get the server's public JSON Web Key (JWK) set and certificate chain.
        | It is required by client applications to validate ID tokens, self-contained access tokens and other issued objects.
        |
      """.stripMargin,
      EmptyBody,
      severJWK,
      List(UnknownError),
      List(apiTagApi, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(getServerJWK)
    )

    // ─── getOAuth2ServerJWKsURIs ─────────────────────────────────────────────

    val getOAuth2ServerJWKsURIs: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "jwks-uris" =>
        EndpointHelpers.executeAndRespond(req) { _ =>
          Future.successful(JSONFactory310.getOAuth2ServerJwksUrisJson())
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getOAuth2ServerJWKsURIs),
      "GET",
      "/jwks-uris",
      "Get JSON Web Key (JWK) URIs",
      """Get the OAuth2 server's public JSON Web Key (JWK) URIs.
        | It is required by client applications to validate ID tokens, self-contained access tokens and other issued objects.
        |
      """.stripMargin,
      EmptyBody,
      oAuth2ServerJwksUrisJson,
      List(UnknownError),
      List(apiTagApi, apiTagOAuth, apiTagOIDC),
      None,
      http4sPartialFunction = Some(getOAuth2ServerJWKsURIs)
    )

    // ─── getMethodRoutings ───────────────────────────────────────────────────

    val getMethodRoutings: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "method_routings" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val methodNameParam = req.uri.query.params.get("method_name").map(Full(_)).getOrElse(net.liftweb.common.Empty)
          val activeParam = req.uri.query.params.get("active")
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canGetMethodRoutings, Some(cc))
            methodRoutings <- NewStyle.function.getMethodRoutingsByMethodName(methodNameParam)
          } yield {
            val definedMethodRoutings = methodRoutings.sortWith(_.methodName < _.methodName)
            val listCommons: List[code.methodrouting.MethodRoutingCommons] = activeParam match {
              case Some("true") => (definedMethodRoutings ++ getDefaultMethodRoutings).sortWith(_.methodName < _.methodName)
              case _ => definedMethodRoutings
            }
            ListResult("method_routings", listCommons.map(_.toJson))
          }
        }
    }

    private def getDefaultMethodRoutings: List[code.methodrouting.MethodRoutingCommons] = {
      val methodRegex = """method \S+(?<!\$default\$\d{0,10})""".r.pattern
      com.openbankproject.commons.util.ReflectUtils.getType(code.bankconnectors.LocalMappedConnector)
        .decls
        .filter(it => methodRegex.matcher(it.toString).matches())
        .filter(_.asMethod.isPublic)
        .map(_.asMethod)
        .map(it => code.methodrouting.MethodRoutingCommons(
          methodName = it.name.toString,
          connectorName = "mapped",
          isBankIdExactMatch = false,
          bankIdPattern = Some("*"),
          parameters = List.empty[code.methodrouting.MethodRoutingParam],
          methodRoutingId = Some(""),
        ))
        .toList
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getMethodRoutings),
      "GET",
      "/management/method_routings",
      "Get MethodRoutings",
      s"""Get the all MethodRoutings.
      |
      |Query url parameters:
      |
      |* method_name: filter with method_name
      |* active: if active = true, it will show all the webui_ props. Even if they are set yet, we will return all the default webui_ props
      |
      |eg:
      |${getObpApiRoot}/v3.1.0/management/method_routings?active=true
      |${getObpApiRoot}/v3.1.0/management/method_routings?method_name=getBank
      |
      |""",
      EmptyBody,
      ListResult(
        "method_routings",
        (List(MethodRoutingCommons("getBanks", "rest_vMar2019", false, Some("some_bank_.*"), List(MethodRoutingParam("url", "http://mydomain.com/xxx")), Some("method-routing-id"))))
      ),
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagMethodRouting, apiTagApi),
      Some(List(canGetMethodRoutings)),
      http4sPartialFunction = Some(getMethodRoutings)
    )

    // ─── getSystemView ───────────────────────────────────────────────────────
    // VIEW_ID path is /system-views/VIEW_ID — no BANK_ID/ACCOUNT_ID, so the middleware
    // validateView would try to look up an account that doesn't exist. Use SYS_VIEW_ID
    // (non-standard ALL_CAPS) to make middleware skip view validation; we look up via
    // ViewNewStyle.systemView inline.

    val getSystemView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "system-views" / viewIdStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetSystemView, Some(cc))
            view <- ViewNewStyle.systemView(ViewId(viewIdStr), Some(cc))
          } yield JSONFactory310.createViewJSON(view)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getSystemView),
      "GET",
      "/system-views/SYS_VIEW_ID",
      "Get System View",
      s"""Get System View
         |
         |${userAuthenticationMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      viewJSONV220,
      List(
        AuthenticatedUserIsRequired,
        BankNotFound,
        UnknownError
      ),
      List(apiTagSystemView),
      Some(List(canGetSystemView)),
      http4sPartialFunction = Some(getSystemView)
    )

    // ─── getCardsForBank ─────────────────────────────────────────────────────

    val getCardsForBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / _ / "cards" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          val httpParams = req.headers.headers.toList.map(h => HTTPParam(h.name.toString, h.value)) :::
            req.uri.query.multiParams.toList.flatMap { case (k, vs) => vs.map(v => HTTPParam(k, v)) }
          for {
            (obpQueryParams, _) <- createQueriesByHttpParamsFuture(httpParams, Some(cc))
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, ApiRole.canGetCardsForBank, Some(cc))
            (cards, _) <- NewStyle.function.getPhysicalCardsForBank(bank, user, obpQueryParams, Some(cc))
          } yield createPhysicalCardsJson(cards, user)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCardsForBank),
      "GET",
      "/management/banks/BANK_ID/cards",
      "Get Cards for the specified bank",
      s"""Should be able to filter on the following fields
      |
      |eg:/management/banks/BANK_ID/cards?customer_id=66214b8e-259e-44ad-8868-3eb47be70646&account_id=8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0
      |
      |1 customer_id should be valid customer_id, otherwise, it will return an empty card list.
      |
      |2 account_id should be valid account_id , otherwise, it will return an empty card list.
      |
      |
      |${userAuthenticationMessage(true)}""".stripMargin,
      EmptyBody,
      physicalCardsJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagCard),
      None,
      http4sPartialFunction = Some(getCardsForBank)
    )

    // ─── getCardForBank ──────────────────────────────────────────────────────

    val getCardForBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "banks" / _ / "cards" / cardIdStr =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, ApiRole.canGetCardsForBank, Some(cc))
            (card, _) <- NewStyle.function.getPhysicalCardForBank(bank.bankId, cardIdStr, Some(cc))
            (cardAttributes, _) <- NewStyle.function.getCardAttributesFromProvider(cardIdStr, Some(cc))
          } yield {
            val views: List[View] = Views.views.vend.assignedViewsForAccount(
              BankIdAccountId(card.account.bankId, card.account.accountId))
            val commonsData: List[CardAttributeCommons] = cardAttributes
            createPhysicalCardWithAttributesJson(card, commonsData, user, views)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCardForBank),
      "GET",
      "/management/banks/BANK_ID/cards/CARD_ID",
      "Get Card By Id",
      s"""
        |This will the datails of the card.
        |It shows the account infomation which linked the the card.
        |Also shows the card attributes of the card.
        |
      """.stripMargin,
      EmptyBody,
      physicalCardWithAttributesJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagCard),
      Some(List(canGetCardsForBank)),
      http4sPartialFunction = Some(getCardForBank)
    )

    // ─── getBankAccountsBalances ─────────────────────────────────────────────

    val getBankAccountsBalances: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "balances" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(user, bank.bankId)
            (accountsBalances, _) <- BalanceNewStyle.getBankAccountsBalances(availablePrivateAccounts, Some(cc))
          } yield createBalancesJson(accountsBalances)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, nameOf(getBankAccountsBalances), "GET",
      "/banks/BANK_ID/balances",
      "Get Accounts Balances",
      """Get the Balances for the Accounts of the current User at one bank.""",
      EmptyBody, accountBalancesV310Json,
      List(UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 :: Nil, None,
      http4sPartialFunction = Some(getBankAccountsBalances))

    // ─── checkFundsAvailable ─────────────────────────────────────────────────

    val checkFundsAvailable: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "funds-available" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          val amountKey = "amount"
          val currencyKey = "currency"
          val queryParams = req.uri.query.params
          for {
            _ <- code.util.Helper.booleanToFuture(
              s"$ViewDoesNotPermitAccess +  You need the `${CAN_QUERY_AVAILABLE_FUNDS}` permission on any your views",
              cc = Some(cc)) {
              view.allowed_actions.exists(_ == CAN_QUERY_AVAILABLE_FUNDS)
            }
            _ <- code.util.Helper.booleanToFuture(MissingQueryParams + amountKey, cc = Some(cc)) {
              queryParams.contains(amountKey)
            }
            _ <- code.util.Helper.booleanToFuture(MissingQueryParams + currencyKey, cc = Some(cc)) {
              queryParams.contains(currencyKey)
            }
            available <- NewStyle.function.tryons(s"$InvalidAmount", 400, Some(cc)) {
              new java.math.BigDecimal(queryParams(amountKey))
            }
            ccy = queryParams(currencyKey)
            _ <- NewStyle.function.isValidCurrencyISOCode(ccy, Some(cc))
            _ <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), Some(cc))
          } yield {
            val fundsAvailable = (view.allowed_actions.exists(_ == CAN_QUERY_AVAILABLE_FUNDS), account.balance, account.currency) match {
              case (false, _, _) => ""
              case (true, _, c) if c != ccy => "no"
              case (true, b, _) if b.compare(available) >= 0 => "yes"
              case _ => "no"
            }
            val availableFundsRequestId = cc.correlationId
            createCheckFundsAvailableJson(fundsAvailable, availableFundsRequestId)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(checkFundsAvailable),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/funds-available",
      "Check Available Funds",
      """Check Available Funds
        |Mandatory URL parameters:
        |
        |* amount=NUMBER
        |* currency=STRING
        |
      """.stripMargin,
      EmptyBody,
      checkFundsAvailableJson,
      List(AuthenticatedUserIsRequired, BankNotFound, BankAccountNotFound,
      InvalidAmount, InvalidISOCurrencyCode, UnknownError),
      apiTagAccount :: apiTagPSD2PIIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(checkFundsAvailable)
    )

    // ─── getTransactionByIdForBankAccount ────────────────────────────────────

    val getTransactionByIdForBankAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "transactions" / transactionIdStr / "transaction" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            _ <- code.api.util.APIUtil.passesPsd2Pisp(Some(cc))
            (moderatedTransaction, _) <- account.moderatedTransactionFuture(
              TransactionId(transactionIdStr), view, Full(user), Some(cc)) map {
              unboxFullOrFail(_, Some(cc), GetTransactionsException)
            }
            (transactionAttributes, _) <- NewStyle.function.getTransactionAttributes(
              account.bankId, TransactionId(transactionIdStr), Some(cc))
          } yield JSONFactory300.createTransactionJSON(moderatedTransaction, transactionAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getTransactionByIdForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/transaction",
      "Get Transaction by Id",
      // Intentional drift from Lift's APIMethods310.scala source-of-truth.
      // Lift's description had userAuthenticationMessage(false) (auth optional),
      // but Lift's handler uses authenticatedAccess(cc) (auth required). The
      // ResourceDoc constructor removed $AuthenticatedUserIsRequired from errors
      // when the description claimed auth was optional, making middleware return
      // 403 (view-permission check) for unauthenticated requests instead of 401.
      // See upstream commit 14abed06c.
      s"""Returns one transaction specified by TRANSACTION_ID of the account ACCOUNT_ID and [moderated](#1_2_1-getViewsForBankAccount) by the view (VIEW_ID).
      |
      |${userAuthenticationMessage(true)}
      |
      |
      |""",
      EmptyBody,
      transactionJsonV300,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, ViewNotFound,
      UserNoPermissionAccessView, UnknownError),
      List(apiTagTransaction),
      None,
      http4sPartialFunction = Some(getTransactionByIdForBankAccount)
    )

    // ─── getTransactionRequests ──────────────────────────────────────────────

    val getTransactionRequests: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / viewIdStr / "transaction-requests" =>
        EndpointHelpers.withBankAccount(req) { (user, account, cc) =>
          for {
            _ <- NewStyle.function.isEnabledTransactionRequests(Some(cc))
            view <- ViewNewStyle.checkAccountAccessAndGetView(
              ViewId(viewIdStr), BankIdAccountId(account.bankId, account.accountId), Full(user), Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              s"${ViewDoesNotPermitAccess} You need the `${CAN_SEE_TRANSACTION_REQUESTS}` permission on the View(${viewIdStr})",
              cc = Some(cc)) {
              view.allowed_actions.exists(_ == CAN_SEE_TRANSACTION_REQUESTS)
            }
            (transactionRequests, _) <- Future(Connector.connector.vend.getTransactionRequests210(user, account, Some(cc))) map {
              unboxFullOrFail(_, Some(cc), GetTransactionRequestsException)
            }
          } yield JSONFactory210.createTransactionRequestJSONs(transactionRequests)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getTransactionRequests),
      "GET",
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
      """.stripMargin,
      EmptyBody,
      transactionRequestWithChargeJSONs210,
      List(AuthenticatedUserIsRequired, BankNotFound, BankAccountNotFound,
      UserNoPermissionAccessView, ViewDoesNotPermitAccess,
      GetTransactionRequestsException, UnknownError),
      List(apiTagTransactionRequest, apiTagPSD2PIS),
      None,
      http4sPartialFunction = Some(getTransactionRequests)
    )

    // ─── getProduct ──────────────────────────────────────────────────────────
    // Conditional auth: middleware uses `userAuthenticationMessage(!getProductsIsPublic)`
    // in description to drive needsAuthentication. When public, anonymous is OK.

    val getProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "products" / productCodeStr =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            (product, _) <- NewStyle.function.getProduct(BankId(bankIdStr), ProductCode(productCodeStr), Some(cc))
            (productAttributes, _) <- NewStyle.function.getProductAttributesByBankAndCode(
              BankId(bankIdStr), ProductCode(productCodeStr), Some(cc))
          } yield JSONFactory310.createProductJson(product, productAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getProduct),
      "GET",
      "/banks/BANK_ID/products/PRODUCT_CODE",
      "Get Bank Product",
      s"""Returns information about a financial Product offered by the bank specified by BANK_ID and PRODUCT_CODE including:
      |
      |* Name
      |* Code
      |* Parent Product Code
      |* Category
      |* Family
      |* Super Family
      |* More info URL
      |* Description
      |* Terms and Conditions
      |* License the data under this endpoint is released under
      |
      |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody,
      productJsonV310,
      List(AuthenticatedUserIsRequired, ProductNotFoundByProductCode, UnknownError),
      List(apiTagProduct),
      None,
      http4sPartialFunction = Some(getProduct)
    )

    // ─── getProductTree ──────────────────────────────────────────────────────

    val getProductTree: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "product-tree" / productCodeStr =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            (_, _) <- NewStyle.function.getProduct(BankId(bankIdStr), ProductCode(productCodeStr), Some(cc))
            (products, _) <- NewStyle.function.getProductTree(BankId(bankIdStr), ProductCode(productCodeStr), Some(cc))
          } yield JSONFactory310.createProductTreeJson(products, productCodeStr)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getProductTree),
      "GET",
      "/banks/BANK_ID/product-tree/PRODUCT_CODE",
      "Get Product Tree",
      s"""Returns information about a particular financial product specified by BANK_ID and PRODUCT_CODE
      |and it's parent product(s) recursively as specified by parent_product_code.
      |
      |Each product includes the following information.
      |
      |* Name
      |* Code
      |* Parent Product Code
      |* Category
      |* Family
      |* Super Family
      |* More info URL
      |* Description
      |* Terms and Conditions
      |* License: The licence under which this product data is released. Licence can be an Open Data licence such as Open Data Commons Public Domain Dedication and License (PDDL) or Copyright etc.
      |
      |
      |
      |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody,
      childProductTreeJsonV310,
      List(AuthenticatedUserIsRequired, ProductNotFoundByProductCode, UnknownError),
      List(apiTagProduct),
      None,
      http4sPartialFunction = Some(getProductTree)
    )

    // ─── getProducts ─────────────────────────────────────────────────────────

    val getProducts: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / bankIdStr / "products" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          val params = req.uri.query.multiParams.toList.map { case (k, vs) => GetProductsParam(k, vs.toList) }
          for {
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            (products, _) <- NewStyle.function.getProducts(BankId(bankIdStr), params, Some(cc))
          } yield JSONFactory310.createProductsJson(products)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, "getProducts", "GET",
      "/banks/BANK_ID/products",
      "Get Products",
      s"""Returns information about the financial products offered by a bank specified by BANK_ID including:
      |
      |* Name
      |* Code
      |* Parent Product Code
      |* Category
      |* Family
      |* Super Family
      |* More info URL
      |* Description
      |* Terms and Conditions
      |* License the data under this endpoint is released under
      |
      |Can filter with attributes name and values.
      |URL params example: /banks/some-bank-id/products?&limit=50&offset=1
      |
      |${userAuthenticationMessage(!getProductsIsPublic)}""".stripMargin,
      EmptyBody, productsJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, ProductNotFoundByProductCode, UnknownError),
      List(apiTagProduct), None,
      http4sPartialFunction = Some(getProducts))

    // ─── getProductCollection ────────────────────────────────────────────────

    val getProductCollection: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "product-collections" / collectionCodeStr =>
        EndpointHelpers.withUserAndBank(req) { (_, bank, cc) =>
          for {
            (payload, _) <- NewStyle.function.getProductCollectionItemsTree(
              collectionCodeStr, bank.bankId.value, Some(cc))
          } yield createProductCollectionsTreeJson(payload)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getProductCollection),
      "GET",
      "/banks/BANK_ID/product-collections/COLLECTION_CODE",
      "Get Product Collection",
      s"""Returns information about the financial Product Collection specified by BANK_ID and COLLECTION_CODE:
      |
       """,
      EmptyBody,
      productCollectionJsonTreeV310,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagProductCollection, apiTagProduct),
      None,
      http4sPartialFunction = Some(getProductCollection)
    )

    // ─── getConsents ─────────────────────────────────────────────────────────

    val getConsents: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "my" / "consents" =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, _) =>
          val params = req.uri.query.params
          val limit = params.get("limit").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(50)
          val offset = params.get("offset").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
          for {
            rows <- Future {
              DoobieConsentQueries.getConsentsByUserAndBank(
                userId = user.userId,
                bankId = bank.bankId.value,
                status = None,
                limit = limit,
                offset = offset,
                sortField = "created_date",
                sortDirection = "desc"
              )
            }
          } yield {
            val consents = rows.map(r => ConsentJsonV310(r.consentId, r.jwt.getOrElse(""), r.status))
            ConsentsJsonV310(consents)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getConsents),
      "GET",
      "/banks/BANK_ID/my/consents",
      "Get Consents",
      s"""
         |This endpoint gets the Consents that the current User created.
         |
         |${userAuthenticationMessage(true)}
         |
         |1 limit (for pagination: defaults to 50)  eg:limit=200
         |
         |2 offset (for pagination: zero index, defaults to 0) eg: offset=10
         |
      """.stripMargin,
      EmptyBody,
      consentsJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2),
      None,
      http4sPartialFunction = Some(getConsents)
    )

    // ─── getPrivateAccountByIdFull ───────────────────────────────────────────

    val getPrivateAccountByIdFull: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "accounts" / _ / _ / "account" =>
        EndpointHelpers.withView(req) { (user, account, view, cc) =>
          for {
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), Some(cc))
            (accountAttributes, _) <- NewStyle.function.getAccountAttributesByAccount(
              account.bankId, account.accountId, Some(cc))
          } yield {
            val availableViews = Views.views.vend.privateViewsUserCanAccessForAccount(
              user, BankIdAccountId(account.bankId, account.accountId))
            val viewsAvailable = availableViews.map(JSONFactory.createViewJSON).sortBy(_.short_name)
            JSONFactory310.createBankAccountJSON(moderatedAccount, viewsAvailable, accountAttributes)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getPrivateAccountByIdFull),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account",
      "Get Account by Id (Full)",
      """Information returned about an account specified by ACCOUNT_ID as moderated by the view (VIEW_ID):
      |
      |* Number
      |* Owners
      |* Type
      |* Balance
      |* IBAN
      |* Available views (sorted by short_name)
      |
      |More details about the data moderation by the view [here](#1_2_1-getViewsForBankAccount).
      |
      |PSD2 Context: PSD2 requires customers to have access to their account information via third party applications.
      |This call provides balance and other account information via delegated authentication using OAuth.
      |
      |Authentication is required if the 'is_public' field in view (VIEW_ID) is not set to `true`.
      |""".stripMargin,
      EmptyBody,
      moderatedAccountJSON310,
      List(BankNotFound, AccountNotFound, ViewNotFound, UserNoPermissionAccessView, UnknownError),
      apiTagAccount :: Nil,
      None,
      http4sPartialFunction = Some(getPrivateAccountByIdFull)
    )

    // ─── getWebUiProps ───────────────────────────────────────────────────────

    val getWebUiProps: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "management" / "webui_props" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          val activeRaw = req.uri.query.params.getOrElse("active", "false")
          for {
            isActive <- NewStyle.function.tryons(
              s"$InvalidFilterParameterFormat `active` must be a boolean, but current `active` value is: $activeRaw",
              400, Some(cc)) { activeRaw.toBoolean }
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canGetWebUiProps, Some(cc))
            explicitWebUiProps <- Future(MappedWebUiPropsProvider.getAll())
            implicitWebUiPropsRemovedDuplicated = if (isActive) {
              val implicitWebUiProps = getWebUIPropsPairs.map(p => WebUiPropsCommons(p._1, p._2, webUiPropsId = Some("default")))
              if (explicitWebUiProps.nonEmpty) {
                val duplicatedProps: List[WebUiPropsCommons] =
                  explicitWebUiProps.flatMap(e => implicitWebUiProps.filter(_.name == e.name))
                implicitWebUiProps diff duplicatedProps
              } else implicitWebUiProps.distinct
            } else List.empty[WebUiPropsCommons]
          } yield {
            val listCommons: List[WebUiPropsCommons] = explicitWebUiProps ++ implicitWebUiPropsRemovedDuplicated
            ListResult("webui_props", listCommons)
          }
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getWebUiProps),
      "GET",
      "/management/webui_props",
      "Get WebUiProps",
      s"""
      |
      |Get WebUiProps - properties that configure the Web UI behavior and appearance.
      |
      |Properties with names starting with "webui_" can be stored in the database and managed via API.
      |
      |**Data Sources:**
      |
      |1. **Explicit WebUiProps (Database)**: Custom values created/updated via the API and stored in the database.
      |
      |2. **Implicit WebUiProps (Configuration File)**: Default values defined in the `sample.props.template` configuration file.
      |
      |**Query Parameter:**
      |
      |* `active` (optional, boolean string, default: "false")
      |  - If `active=false` or omitted: Returns only explicit props from the database
      |  - If `active=true`: Returns explicit props + implicit (default) props from configuration file
      |    - When both sources have the same property name, the database value takes precedence
      |    - Implicit props are marked with `webUiPropsId = "default"`
      |
      |**Examples:**
      |
      |Get only database-stored props:
      |${getObpApiRoot}/v3.1.0/management/webui_props
      |
      |Get database props combined with defaults:
      |${getObpApiRoot}/v3.1.0/management/webui_props?active=true
      |
      |For more details about WebUI Props, including how to set config file defaults and precedence order, see ${Glossary.getGlossaryItemLink("webui_props")}.
      |
      |""",
      EmptyBody,
      ListResult(
        "webui_props",
        (List(WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com", Some("web-ui-props-id"))))
      ),
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagWebUiProps),
      Some(List(canGetWebUiProps)),
      http4sPartialFunction = Some(getWebUiProps)
    )

    // ─── deleteUserAuthContexts ──────────────────────────────────────────────

    val deleteUserAuthContexts: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "users" / userIdStr / "auth-context" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canDeleteUserAuthContext, Some(cc))
            (_, _) <- NewStyle.function.findByUserId(userIdStr, Some(cc))
            (deleted, _) <- NewStyle.function.deleteUserAuthContexts(userIdStr, Some(cc))
          } yield Full(deleted)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteUserAuthContexts),
      "DELETE",
      "/users/USER_ID/auth-context",
      "Delete User's Auth Contexts",
      s"""Delete the Auth Contexts of a User specified by USER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canDeleteUserAuthContext)),
      http4sPartialFunction = Some(deleteUserAuthContexts)
    )

    // ─── deleteUserAuthContextById ──────────────────────────────────────────

    val deleteUserAuthContextById: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "users" / userIdStr / "auth-context" / userAuthContextIdStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canDeleteUserAuthContext, Some(cc))
            (subjectUser, _) <- NewStyle.function.findByUserId(userIdStr, Some(cc))
            (deleted, _) <- NewStyle.function.deleteUserAuthContextById(subjectUser, userAuthContextIdStr, Some(cc))
          } yield Full(deleted)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteUserAuthContextById),
      "DELETE",
      "/users/USER_ID/auth-context/USER_AUTH_CONTEXT_ID",
      "Delete User Auth Context",
      s"""Delete a User AuthContext of the User specified by USER_AUTH_CONTEXT_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canDeleteUserAuthContext)),
      http4sPartialFunction = Some(deleteUserAuthContextById)
    )

    // ─── deleteTaxResidence ──────────────────────────────────────────────────

    val deleteTaxResidence: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "tax_residencies" / taxResidenceIdStr =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canDeleteTaxResidence, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (deleted, _) <- NewStyle.function.deleteTaxResidence(taxResidenceIdStr, Some(cc))
          } yield Full(deleted)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteTaxResidence),
      "DELETE",
      "/banks/BANK_ID/customers/CUSTOMER_ID/tax_residencies/TAX_RESIDENCE_ID",
      "Delete Tax Residence",
      s"""Delete a Tax Residence of the Customer specified by TAX_RESIDENCE_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagCustomer, apiTagKyc),
      Some(List(canDeleteTaxResidence)),
      http4sPartialFunction = Some(deleteTaxResidence)
    )

    // ─── deleteCustomerAddress ───────────────────────────────────────────────

    val deleteCustomerAddress: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "addresses" / customerAddressIdStr =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canDeleteCustomerAddress, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (deleted, _) <- NewStyle.function.deleteCustomerAddress(customerAddressIdStr, Some(cc))
          } yield Full(deleted)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteCustomerAddress),
      "DELETE",
      "/banks/BANK_ID/customers/CUSTOMER_ID/addresses/CUSTOMER_ADDRESS_ID",
      "Delete Customer Address",
      s"""Delete an Address of the Customer specified by CUSTOMER_ADDRESS_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagCustomer, apiTagKyc),
      Some(List(canDeleteCustomerAddress)),
      http4sPartialFunction = Some(deleteCustomerAddress)
    )

    // ─── deleteProductAttribute ──────────────────────────────────────────────
    // Note: this DELETE returns 204 (matches original v3.1.0 behavior).

    val deleteProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / bankIdStr / "products" / _ / "attributes" / productAttributeIdStr =>
        EndpointHelpers.withUserDelete(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bankIdStr, user.userId, canDeleteProductAttribute, Some(cc))
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            (deleted, _) <- NewStyle.function.deleteProductAttribute(productAttributeIdStr, Some(cc))
          } yield deleted
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteProductAttribute),
      "DELETE",
      "/banks/BANK_ID/products/PRODUCT_CODE/attributes/PRODUCT_ATTRIBUTE_ID",
      "Delete Product Attribute",
      s""" Delete Product Attribute
      |
      |$productAttributeGeneralInfo
      |
      |Delete a Product Attribute by its id.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      EmptyBody,
      List(
        UserHasMissingRoles,
        BankNotFound,
        UnknownError
      ),
      List(apiTagProduct, apiTagProductAttribute, apiTagAttribute),
      Some(List(canDeleteProductAttribute)),
      http4sPartialFunction = Some(deleteProductAttribute)
    )

    // ─── deleteBranch ────────────────────────────────────────────────────────

    val deleteBranch: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "banks" / _ / "branches" / branchIdStr =>
        EndpointHelpers.withUserAndBank(req) { (user, bank, cc) =>
          val allowedEntitlements = canDeleteBranch :: canDeleteBranchAtAnyBank :: Nil
          val allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")
          for {
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = UserHasMissingRoles + allowedEntitlementsTxt)(
              bank.bankId.value, user.userId, allowedEntitlements, Some(cc))
            (branch, _) <- NewStyle.function.getBranch(bank.bankId, BranchId(branchIdStr), Some(cc))
            (deleted, _) <- NewStyle.function.deleteBranch(branch, Some(cc))
          } yield Full(deleted)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteBranch),
      "DELETE",
      "/banks/BANK_ID/branches/BRANCH_ID",
      "Delete Branch",
      s"""Delete Branch from given Bank.
      |
      |${userAuthenticationMessage(true) }
      |
      |""",
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, BankNotFound, InsufficientAuthorisationToDeleteBranch, UnknownError),
      List(apiTagBranch),
      Some(List(canDeleteBranch, canDeleteBranchAtAnyBank)),
      http4sPartialFunction = Some(deleteBranch)
    )

    // ─── deleteSystemView ────────────────────────────────────────────────────

    val deleteSystemView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "system-views" / viewIdStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canDeleteSystemView, Some(cc))
            _ <- ViewNewStyle.systemView(ViewId(viewIdStr), Some(cc))
            deleted <- ViewNewStyle.deleteSystemView(ViewId(viewIdStr), Some(cc))
          } yield Full(deleted)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, "deleteSystemView", "DELETE",
      "/system-views/SYS_VIEW_ID",
      "Delete System View",
      "Deletes the system view specified by VIEW_ID",
      EmptyBody, EmptyBody,
      List(AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError, "user does not have owner access"),
      List(apiTagSystemView), Some(List(canDeleteSystemView)),
      http4sPartialFunction = Some(deleteSystemView))

    // ─── deleteMethodRouting ─────────────────────────────────────────────────

    val deleteMethodRouting: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "method_routings" / methodRoutingIdStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            (_, _) <- NewStyle.function.getMethodRoutingById(methodRoutingIdStr, Some(cc))
            _ <- NewStyle.function.hasEntitlement("", user.userId, canDeleteMethodRouting, Some(cc))
            deleted <- NewStyle.function.deleteMethodRouting(methodRoutingIdStr)
          } yield deleted
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteMethodRouting),
      "DELETE",
      "/management/method_routings/METHOD_ROUTING_ID",
      "Delete MethodRouting",
      s"""Delete a MethodRouting specified by METHOD_ROUTING_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagMethodRouting, apiTagApi),
      Some(List(canDeleteMethodRouting)),
      http4sPartialFunction = Some(deleteMethodRouting)
    )

    // ─── deleteCardForBank ───────────────────────────────────────────────────
    // Note: original v3.1.0 returns 204 — use withUserAndBankDelete.

    val deleteCardForBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "banks" / _ / "cards" / cardIdStr =>
        EndpointHelpers.withUserAndBankDelete(req) { (user, bank, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, ApiRole.canDeleteCardsForBank, Some(cc))
            (deleted, _) <- NewStyle.function.deletePhysicalCardForBank(bank.bankId, cardIdStr, Some(cc))
          } yield deleted
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteCardForBank),
      "DELETE",
      "/management/banks/BANK_ID/cards/CARD_ID",
      "Delete Card",
      s"""Delete a Card at bank specified by CARD_ID .
      |
      |${userAuthenticationMessage(true)}
      |""",
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, AllowedValuesAre, UnknownError),
      List(apiTagCard),
      Some(List(canCreateCardsForBank)),
      http4sPartialFunction = Some(deleteCardForBank)
    )

    // ─── deleteWebUiProps ────────────────────────────────────────────────────

    val deleteWebUiProps: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ DELETE -> `prefixPath` / "management" / "webui_props" / webUiPropsIdStr =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canDeleteWebUiProps, Some(cc))
            deleted <- Future(MappedWebUiPropsProvider.delete(webUiPropsIdStr)) map {
              unboxFullOrFail(_, Some(cc))
            }
          } yield deleted
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(deleteWebUiProps),
      "DELETE",
      "/management/webui_props/WEB_UI_PROPS_ID",
      "Delete WebUiProps",
      s"""Delete a WebUiProps specified by WEB_UI_PROPS_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      EmptyBody,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagWebUiProps),
      Some(List(canDeleteWebUiProps)),
      http4sPartialFunction = Some(deleteWebUiProps)
    )

    // ─── revokeConsent ───────────────────────────────────────────────────────
    // Routed as GET in Lift — keep matching shape.

    val revokeConsent: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "banks" / _ / "my" / "consents" / consentIdStr / "revoke" =>
        EndpointHelpers.withUserAndBank(req) { (user, _, cc) =>
          for {
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentIdStr)) map {
              unboxFullOrFail(_, Some(cc), ConsentNotFound)
            }
            _ <- code.util.Helper.booleanToFuture(failMsg = ConsentNotFound, cc = Some(cc)) {
              consent.mUserId == user.userId
            }
            revoked <- Future(Consents.consentProvider.vend.revoke(consentIdStr)) map {
              i => connectorEmptyResponse(i, Some(cc))
            }
          } yield ConsentJsonV310(revoked.consentId, revoked.jsonWebToken, revoked.status)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, "revokeConsent", "GET",
      "/banks/BANK_ID/my/consents/CONSENT_ID/revoke",
      "Revoke Consent",
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
      List(AuthenticatedUserIsRequired, BankNotFound, UnknownError),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2), None,
      http4sPartialFunction = Some(revokeConsent))

    // ─── createTaxResidence (POST) ───────────────────────────────────────────

    val createTaxResidence: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "tax-residence" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostTaxResidenceJsonV310, Any](req) { (user, bank, postedData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canCreateTaxResidence, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (taxResidence, _) <- NewStyle.function.createTaxResidence(
              customerIdStr, postedData.domain, postedData.tax_number, Some(cc))
          } yield JSONFactory310.createTaxResidence(taxResidence)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createTaxResidence),
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_ID/tax-residence",
      "Create Tax Residence",
      s"""Create a Tax Residence for a Customer specified by CUSTOMER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      postTaxResidenceJsonV310,
      taxResidenceV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagCustomer, apiTagKyc),
      Some(List(canCreateTaxResidence)),
      http4sPartialFunction = Some(createTaxResidence)
    )

    // ─── createCustomerAddress (POST) ────────────────────────────────────────

    val createCustomerAddress: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "address" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostCustomerAddressJsonV310, Any](req) { (user, bank, postedData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canCreateCustomerAddress, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (address, _) <- NewStyle.function.createCustomerAddress(
              customerIdStr,
              postedData.line_1, postedData.line_2, postedData.line_3,
              postedData.city, postedData.county, postedData.state,
              postedData.postcode, postedData.country_code,
              postedData.state,
              postedData.tags.mkString(","),
              Some(cc))
          } yield JSONFactory310.createAddress(address)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createCustomerAddress),
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_ID/address",
      "Create Address",
      s"""Create an Address for a Customer specified by CUSTOMER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      postCustomerAddressJsonV310,
      customerAddressJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagCustomer),
      Some(List(canCreateCustomerAddress)),
      http4sPartialFunction = Some(createCustomerAddress)
    )

    // ─── updateCustomerAddress (PUT) ─────────────────────────────────────────

    val updateCustomerAddress: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "addresses" / customerAddressIdStr =>
        EndpointHelpers.withUserAndBankAndBody[PostCustomerAddressJsonV310, Any](req) { (user, bank, postedData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canCreateCustomer, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (address, _) <- NewStyle.function.updateCustomerAddress(
              customerAddressIdStr,
              postedData.line_1, postedData.line_2, postedData.line_3,
              postedData.city, postedData.county, postedData.state,
              postedData.postcode, postedData.country_code,
              postedData.state,
              postedData.tags.mkString(","),
              Some(cc))
          } yield JSONFactory310.createAddress(address)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateCustomerAddress),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/addresses/CUSTOMER_ADDRESS_ID",
      "Update the Address of a Customer",
      s"""Update an Address of the Customer specified by CUSTOMER_ADDRESS_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      postCustomerAddressJsonV310,
      customerAddressJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagCustomer),
      Some(List(canCreateCustomer)),
      http4sPartialFunction = Some(updateCustomerAddress)
    )

    // ─── createUserAuthContext (POST) ────────────────────────────────────────

    val createUserAuthContext: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / userIdStr / "auth-context" =>
        EndpointHelpers.withUserAndBodyCreated[PostUserAuthContextJson, Any](req) { (user, postedData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canCreateUserAuthContext, Some(cc))
            (subjectUser, _) <- NewStyle.function.findByUserId(userIdStr, Some(cc))
            (userAuthContext, _) <- NewStyle.function.createUserAuthContext(
              subjectUser, postedData.key.trim, postedData.value.trim, Some(cc))
          } yield JSONFactory310.createUserAuthContextJson(userAuthContext)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createUserAuthContext),
      "POST",
      "/users/USER_ID/auth-context",
      "Create User Auth Context",
      s"""Create User Auth Context. These key value pairs will be propagated over connector to adapter. Normally used for mapping OBP user and
      | Bank User/Customer.
      |${userAuthenticationMessage(true)}
      |""",
      postUserAuthContextJson,
      userAuthContextJson,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, CreateUserAuthContextError, UnknownError),
      List(apiTagUser),
      Some(List(canCreateUserAuthContext)),
      http4sPartialFunction = Some(createUserAuthContext)
    )

    // ─── createProductAttribute (POST) ───────────────────────────────────────

    val createProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "products" / productCodeStr / "attribute" =>
        EndpointHelpers.withUserAndBodyCreated[ProductAttributeJson, Any](req) { (user, postedData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bankIdStr, user.userId, canCreateProductAttribute, Some(cc))
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            productAttributeType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${ProductAttributeType.DOUBLE}(12.1234), ${ProductAttributeType.STRING}(TAX_NUMBER), ${ProductAttributeType.INTEGER}(123) and ${ProductAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) { ProductAttributeType.withName(postedData.`type`) }
            (productAttribute, _) <- NewStyle.function.createOrUpdateProductAttribute(
              BankId(bankIdStr), ProductCode(productCodeStr), None,
              postedData.name, productAttributeType, postedData.value, None, Some(cc))
          } yield createProductAttributeJson(productAttribute)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createProductAttribute),
      "POST",
      "/banks/BANK_ID/products/PRODUCT_CODE/attribute",
      "Create Product Attribute",
      s""" Create Product Attribute
      |
      |$productAttributeGeneralInfo
      |
      |Typical product attributes might be:
      |
      |ISIN (for International bonds)
      |VKN (for German bonds)
      |REDCODE (markit short code for credit derivative)
      |LOAN_ID (e.g. used for Anacredit reporting)
      |
      |ISSUE_DATE (When the bond was issued in the market)
      |MATURITY_DATE (End of life time of a product)
      |TRADABLE
      |
      |See [FPML](http://www.fpml.org/) for more examples.
      |
      |
      |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
      |
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      productAttributeJson,
      productAttributeResponseJson,
      List(InvalidJsonFormat, UnknownError),
      List(apiTagProduct, apiTagProductAttribute, apiTagAttribute),
      Some(List(canCreateProductAttribute)),
      http4sPartialFunction = Some(createProductAttribute)
    )

    // ─── createAccountWebhook (POST) ─────────────────────────────────────────

    private val accountWebHookInfo =
      s"""Webhooks are used to call external URLs when certain events happen.
         |
         |Account Webhooks focus on events around accounts.
         |
         |For instance, a webhook could be used to notify an external service if a balance changes on an account.
         |
         |This functionality is work in progress! Please note that only implemented trigger is: ${ApiTrigger.onBalanceChange}"""

    val createAccountWebhook: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "account-web-hooks" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AccountWebhookPostJson, Any](req) { (user, bank, postJson, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, ApiRole.canCreateWebhook, Some(cc))
            _ <- NewStyle.function.tryons(
              IncorrectTriggerName + postJson.trigger_name + ". Possible values are " + ApiTrigger.availableTriggers.sorted.mkString(", "),
              400, Some(cc)) { ApiTrigger.valueOf(postJson.trigger_name) }
            isActive <- NewStyle.function.tryons(
              s"$InvalidBoolean Possible values of the json field is_active are true or false.",
              400, Some(cc)) { postJson.is_active.toBoolean }
            wh <- AccountWebhook.accountWebhook.vend.createAccountWebhookFuture(
              bankId = bank.bankId.value,
              accountId = postJson.account_id,
              userId = user.userId,
              triggerName = postJson.trigger_name,
              url = postJson.url,
              httpMethod = postJson.http_method,
              httpProtocol = postJson.http_protocol,
              isActive = isActive
            ) map { unboxFullOrFail(_, Some(cc), CreateWebhookError) }
          } yield createAccountWebhookJson(wh)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createAccountWebhook),
      "POST",
      "/banks/BANK_ID/account-web-hooks",
      "Create an Account Webhook",
      s"""Create an Account Webhook
      |
      |$accountWebHookInfo
      |""",
      accountWebhookPostJson,
      accountWebhookJson,
      List(UnknownError),
      apiTagWebhook :: apiTagBank :: Nil,
      Some(List(canCreateWebhook)),
      http4sPartialFunction = Some(createAccountWebhook)
    )

    // ─── unlockUser (PUT) ────────────────────────────────────────────────────

    val unlockUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "users" / username / "lock-status" =>
        EndpointHelpers.withUser(req) { (user, cc) =>
          for {
            subjectUser <- Users.users.vend.getUserByProviderAndUsernameFuture(Constant.localIdentityProvider, username) map {
              x => unboxFullOrFail(x, Some(cc), UserNotFoundByProviderAndUsername, 404)
            }
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canUnlockUser, Some(cc))
            _ <- Future(LoginAttempt.resetBadLoginAttempts(localIdentityProvider, username))
            _ <- Future(UserLocksProvider.unlockUser(localIdentityProvider, username))
            badLoginStatus <- Future(LoginAttempt.getOrCreateBadLoginStatus(localIdentityProvider, username)) map {
              unboxFullOrFail(_, Some(cc), s"$UserNotFoundByProviderAndUsername($username)", 404)
            }
          } yield createBadLoginStatusJson(badLoginStatus)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(unlockUser),
      "PUT",
      "/users/USERNAME/lock-status",
      "Unlock the user",
      s"""
      |Unlock a User.
      |
      |(Perhaps the user was locked due to multiple failed login attempts)
      |
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      EmptyBody,
      badLoginStatusJson,
      List(AuthenticatedUserIsRequired, UserNotFoundByProviderAndUsername,
      UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canUnlockUser)),
      http4sPartialFunction = Some(unlockUser)
    )

    // ─── callsLimit (PUT) ────────────────────────────────────────────────────

    val callsLimit: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "consumers" / consumerIdStr / "consumer" / "call-limits" =>
        EndpointHelpers.withUserAndBody[CallLimitPostJson, Any](req) { (user, postJson, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canUpdateRateLimits, Some(cc))
            _ <- NewStyle.function.getConsumerByConsumerId(consumerIdStr, Some(cc))
            rateLimiting <- RateLimitingDI.rateLimiting.vend.createOrUpdateConsumerCallLimits(
              consumerIdStr,
              postJson.from_date,
              postJson.to_date,
              None, None, None,
              Some(postJson.per_second_call_limit),
              Some(postJson.per_minute_call_limit),
              Some(postJson.per_hour_call_limit),
              Some(postJson.per_day_call_limit),
              Some(postJson.per_week_call_limit),
              Some(postJson.per_month_call_limit)) map {
              unboxFullOrFail(_, Some(cc), UpdateConsumerError)
            }
          } yield createCallsLimitJson(rateLimiting)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(callsLimit),
      "PUT",
      "/management/consumers/CONSUMER_ID/consumer/call-limits",
      "Set Rate Limits (call limits) per Consumer",
      s"""
      |Set the API rate limiting (call limits) per Consumer:
      |
      |Rate limits can be set:
      |
      |Per Second
      |Per Minute
      |Per Hour
      |Per Week
      |Per Month
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""".stripMargin,
      callLimitPostJson,
      callLimitPostJson,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, InvalidConsumerId,
      ConsumerNotFoundByConsumerId, UserHasMissingRoles, UpdateConsumerError, UnknownError),
      List(apiTagConsumer),
      Some(List(canUpdateRateLimits)),
      http4sPartialFunction = Some(callsLimit)
    )

    // ─── enableDisableAccountWebhook (PUT) ───────────────────────────────────

    val enableDisableAccountWebhook: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "account-web-hooks" =>
        EndpointHelpers.withUserAndBankAndBody[AccountWebhookPutJson, Any](req) { (user, bank, putJson, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, ApiRole.canUpdateWebhook, Some(cc))
            isActive <- NewStyle.function.tryons(
              s"$InvalidBoolean Possible values of the json field is_active are true or false.",
              400, Some(cc)) { putJson.is_active.toBoolean }
            _ <- AccountWebhook.accountWebhook.vend.getAccountWebhookByIdFuture(putJson.account_webhook_id) map {
              unboxFullOrFail(_, Some(cc), WebhookNotFound)
            }
            wh <- AccountWebhook.accountWebhook.vend.updateAccountWebhookFuture(
              accountWebhookId = putJson.account_webhook_id,
              isActive = isActive
            ) map { unboxFullOrFail(_, Some(cc), UpdateWebhookError) }
          } yield createAccountWebhookJson(wh)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(enableDisableAccountWebhook),
      "PUT",
      "/banks/BANK_ID/account-web-hooks",
      "Enable/Disable an Account Webhook",
      s"""Enable/Disable an Account Webhook
      |
      |
      |$accountWebHookInfo
      |""",
      accountWebhookPutJson,
      accountWebhookJson,
      List(UnknownError),
      apiTagWebhook :: apiTagBank :: Nil,
      Some(List(canUpdateWebhook)),
      http4sPartialFunction = Some(enableDisableAccountWebhook)
    )

    // ─── enableDisableConsumers (PUT) ────────────────────────────────────────

    val enableDisableConsumers: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "consumers" / consumerIdStr =>
        EndpointHelpers.withUserAndBody[PutEnabledJSON, Any](req) { (user, putData, cc) =>
          for {
            _ <- putData.enabled match {
              case true  => NewStyle.function.hasEntitlement("", user.userId, ApiRole.canEnableConsumers, Some(cc))
              case false => NewStyle.function.hasEntitlement("", user.userId, ApiRole.canDisableConsumers, Some(cc))
            }
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerIdStr, Some(cc))
            updatedConsumer <- Future {
              Consumers.consumers.vend.updateConsumer(
                consumer.id.get, None, None, Some(putData.enabled),
                None, None, None, None, None, None, None, None) ?~! "Cannot update Consumer"
            }
          } yield PutEnabledJSON(updatedConsumer.map(_.isActive.get).getOrElse(false))
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, "enableDisableConsumers", "PUT",
      "/management/consumers/CONSUMER_ID",
      "Enable or Disable Consumers",
      s"""Enable/Disable a Consumer specified by CONSUMER_ID.""",
      putEnabledJSON, putEnabledJSON,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagConsumer), Some(List(canEnableConsumers, canDisableConsumers)),
      http4sPartialFunction = Some(enableDisableConsumers))

    // ─── updateSystemView (PUT) ──────────────────────────────────────────────

    val updateSystemView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "system-views" / viewIdStr =>
        EndpointHelpers.withUserAndBody[UpdateViewJSON, Any](req) { (user, updateJson, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canUpdateSystemView, Some(cc))
            _ <- code.util.Helper.booleanToFuture(SystemViewCannotBePublicError, failCode = 400, cc = Some(cc)) {
              updateJson.is_public == false
            }
            _ <- ViewNewStyle.systemView(ViewId(viewIdStr), Some(cc))
            updatedView <- ViewNewStyle.updateSystemView(ViewId(viewIdStr), updateJson, Some(cc))
          } yield JSONFactory310.createViewJSON(updatedView)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateSystemView),
      "PUT",
      "/system-views/SYS_VIEW_ID",
      "Update System View",
      s"""Update an existing view on a bank account
      |
      |${userAuthenticationMessage(true)} and the user needs to have access to the owner view.
      |
      |The json sent is the same as during view creation (above), with one difference: the 'name' field
      |of a view is not editable (it is only set when a view is created)""",
      updateSystemViewJson310,
      viewJsonV300,
      List(InvalidJsonFormat, AuthenticatedUserIsRequired, BankAccountNotFound, UnknownError),
      List(apiTagSystemView),
      Some(List(canUpdateSystemView)),
      http4sPartialFunction = Some(updateSystemView)
    )

    // ─── updateProductAttribute (PUT) ────────────────────────────────────────

    val updateProductAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "products" / productCodeStr / "attributes" / productAttributeIdStr =>
        EndpointHelpers.withUserAndBody[ProductAttributeJson, Any](req) { (user, postedData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bankIdStr, user.userId, canUpdateProductAttribute, Some(cc))
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            productAttributeType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${ProductAttributeType.DOUBLE}(12.1234), ${ProductAttributeType.STRING}(TAX_NUMBER), ${ProductAttributeType.INTEGER}(123) and ${ProductAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) { ProductAttributeType.withName(postedData.`type`) }
            (_, _) <- NewStyle.function.getProductAttributeById(productAttributeIdStr, Some(cc))
            (productAttribute, _) <- NewStyle.function.createOrUpdateProductAttribute(
              BankId(bankIdStr), ProductCode(productCodeStr), Some(productAttributeIdStr),
              postedData.name, productAttributeType, postedData.value, None, Some(cc))
          } yield createProductAttributeJson(productAttribute)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateProductAttribute),
      "PUT",
      "/banks/BANK_ID/products/PRODUCT_CODE/attributes/PRODUCT_ATTRIBUTE_ID",
      "Update Product Attribute",
      s""" Update Product Attribute.
      |

      |$productAttributeGeneralInfo
      |
      |Update one Product Attribute by its id.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      productAttributeJson,
      productAttributeResponseJson,
      List(UserHasMissingRoles, UnknownError),
      List(apiTagProduct, apiTagProductAttribute, apiTagAttribute),
      Some(List(canUpdateProductAttribute)),
      http4sPartialFunction = Some(updateProductAttribute)
    )

    // ─── updateCustomerEmail (PUT) ───────────────────────────────────────────

    val updateCustomerEmail: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "email" =>
        EndpointHelpers.withUserAndBankAndBody[PutUpdateCustomerEmailJsonV310, Any](req) { (user, bank, putData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canUpdateCustomerEmail, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (customer, _) <- NewStyle.function.updateCustomerScaData(
              customerIdStr, None, Some(putData.email), None, Some(cc))
          } yield JSONFactory310.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateCustomerEmail),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/email",
      "Update the email of a Customer",
      s"""Update an email of the Customer specified by CUSTOMER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      putUpdateCustomerEmailJsonV310,
      customerJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagCustomer),
      Some(canUpdateCustomerEmail :: Nil),
      http4sPartialFunction = Some(updateCustomerEmail)
    )

    // ─── updateCustomerNumber (PUT) ──────────────────────────────────────────

    val updateCustomerNumber: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "number" =>
        EndpointHelpers.withUserAndBankAndBody[PutUpdateCustomerNumberJsonV310, Any](req) { (user, bank, putData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canUpdateCustomerNumber, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (customerNumberIsAvalible, _) <- NewStyle.function.checkCustomerNumberAvailable(bank.bankId, putData.customer_number, Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              failMsg = s"$CustomerNumberAlreadyExists Current customer_number(${putData.customer_number}) and Current bank_id(${bank.bankId.value})",
              cc = Some(cc)) { customerNumberIsAvalible }
            (customer, _) <- NewStyle.function.updateCustomerScaData(
              customerIdStr, None, None, Some(putData.customer_number), Some(cc))
          } yield JSONFactory310.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateCustomerNumber),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/number",
      "Update the number of a Customer",
      s"""Update the number of the Customer specified by CUSTOMER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      putUpdateCustomerNumberJsonV310,
      customerJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagCustomer),
      Some(canUpdateCustomerNumber :: Nil),
      http4sPartialFunction = Some(updateCustomerNumber)
    )

    // ─── updateCustomerMobileNumber (PUT) ────────────────────────────────────

    val updateCustomerMobileNumber: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "mobile-number" =>
        EndpointHelpers.withUserAndBankAndBody[PutUpdateCustomerMobilePhoneNumberJsonV310, Any](req) { (user, bank, putData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canUpdateCustomerMobilePhoneNumber, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (customer, _) <- NewStyle.function.updateCustomerScaData(
              customerIdStr, Some(putData.mobile_phone_number), None, None, Some(cc))
          } yield JSONFactory310.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateCustomerMobileNumber),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/mobile-number",
      "Update the mobile number of a Customer",
      s"""Update the mobile number of the Customer specified by CUSTOMER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      putUpdateCustomerMobileNumberJsonV310,
      customerJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagCustomer),
      Some(canUpdateCustomerMobilePhoneNumber :: Nil),
      http4sPartialFunction = Some(updateCustomerMobileNumber)
    )

    // ─── updateCustomerIdentity (PUT) ────────────────────────────────────────

    val updateCustomerIdentity: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "identity" =>
        EndpointHelpers.withUserAndBankAndBody[PutUpdateCustomerIdentityJsonV310, Any](req) { (user, bank, putData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canUpdateCustomerIdentity, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (customer, _) <- NewStyle.function.updateCustomerGeneralData(
              customerIdStr,
              Some(putData.legal_name), None, Some(putData.date_of_birth),
              None, None, None, None,
              Some(putData.title), None, Some(putData.name_suffix),
              None, None, Some(cc))
          } yield JSONFactory310.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateCustomerIdentity),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/identity",
      "Update the identity data of a Customer",
      s"""Update the identity data of the Customer specified by CUSTOMER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      putUpdateCustomerIdentityJsonV310,
      customerJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagCustomer),
      Some(canUpdateCustomerIdentity :: Nil),
      http4sPartialFunction = Some(updateCustomerIdentity)
    )

    // ─── updateCustomerCreditLimit (PUT) ─────────────────────────────────────

    val updateCustomerCreditLimit: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "credit-limit" =>
        EndpointHelpers.withUserAndBankAndBody[PutUpdateCustomerCreditLimitJsonV310, Any](req) { (user, bank, putData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canUpdateCustomerCreditLimit, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (customer, _) <- NewStyle.function.updateCustomerCreditData(
              customerIdStr, None, None, Some(putData.credit_limit), Some(cc))
          } yield JSONFactory310.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateCustomerCreditLimit),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/credit-limit",
      "Update the credit limit of a Customer",
      s"""Update the credit limit of the Customer specified by CUSTOMER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      putUpdateCustomerCreditLimitJsonV310,
      customerJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagCustomer),
      Some(canUpdateCustomerCreditLimit :: Nil),
      http4sPartialFunction = Some(updateCustomerCreditLimit)
    )

    // ─── updateCustomerCreditRatingAndSource (PUT) ───────────────────────────

    val updateCustomerCreditRatingAndSource: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "credit-rating-and-source" =>
        EndpointHelpers.withUserAndBankAndBody[PutUpdateCustomerCreditRatingAndSourceJsonV310, Any](req) { (user, bank, putData, cc) =>
          for {
            _ <- NewStyle.function.hasAtLeastOneEntitlement(bank.bankId.value, user.userId,
              List(canUpdateCustomerCreditRatingAndSource, canUpdateCustomerCreditRatingAndSourceAtAnyBank), Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (customer, _) <- NewStyle.function.updateCustomerCreditData(
              customerIdStr, Some(putData.credit_rating), Some(putData.credit_source), None, Some(cc))
          } yield JSONFactory310.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateCustomerCreditRatingAndSource),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/credit-rating-and-source",
      "Update the credit rating and source of a Customer",
      s"""Update the credit rating and source of the Customer specified by CUSTOMER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      putUpdateCustomerCreditRatingAndSourceJsonV310,
      customerJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagCustomer),
      Some(canUpdateCustomerCreditRatingAndSource :: canUpdateCustomerCreditRatingAndSourceAtAnyBank :: Nil),
      http4sPartialFunction = Some(updateCustomerCreditRatingAndSource)
    )

    // ─── updateCustomerBranch (PUT) ──────────────────────────────────────────

    val updateCustomerBranch: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "branch" =>
        EndpointHelpers.withUserAndBankAndBody[PutUpdateCustomerBranchJsonV310, Any](req) { (user, bank, putData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canUpdateCustomerBranch, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (customer, _) <- NewStyle.function.updateCustomerGeneralData(
              customerIdStr,
              None, None, None, None, None, None, None,
              None, Some(putData.branch_id), None, None, None, Some(cc))
          } yield JSONFactory310.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateCustomerBranch),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/branch",
      "Update the Branch of a Customer",
      s"""Update the Branch of the Customer specified by CUSTOMER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      putCustomerBranchJsonV310,
      customerJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagCustomer),
      Some(canUpdateCustomerBranch :: Nil),
      http4sPartialFunction = Some(updateCustomerBranch)
    )

    // ─── updateCustomerData (PUT) ────────────────────────────────────────────

    val updateCustomerData: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "customers" / customerIdStr / "data" =>
        EndpointHelpers.withUserAndBankAndBody[PutUpdateCustomerDataJsonV310, Any](req) { (user, bank, putData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canUpdateCustomerData, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(customerIdStr, Some(cc))
            (customer, _) <- NewStyle.function.updateCustomerGeneralData(
              customerIdStr,
              None,
              Some(CustomerFaceImage(putData.face_image.date, putData.face_image.url)),
              None,
              Some(putData.relationship_status),
              Some(putData.dependants),
              Some(putData.highest_education_attained),
              Some(putData.employment_status),
              None, None, None, None, None, Some(cc))
          } yield JSONFactory310.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateCustomerData),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/data",
      "Update the other data of a Customer",
      s"""Update the other data of the Customer specified by CUSTOMER_ID.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      putUpdateCustomerDataJsonV310,
      customerJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagCustomer),
      Some(canUpdateCustomerData :: Nil),
      http4sPartialFunction = Some(updateCustomerData)
    )

    // ─── updateAccountApplicationStatus (PUT) ────────────────────────────────
    // Side effect: when status == "ACCEPTED", a new bank account is created and the
    // APPLICANT (the application's user) becomes its holder. The Lift implementation
    // (and its verbatim port) made the logged-in approver the holder — fixed 2026-09.

    val updateAccountApplicationStatus: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "account-applications" / accountApplicationIdStr =>
        EndpointHelpers.withUserAndBankAndBody[AccountApplicationUpdateStatusJson, Any](req) { (user, bank, putJson, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canUpdateAccountApplications, Some(cc))
            _ <- NewStyle.function.tryons(s"$InvalidJsonFormat status should not be blank.", 400, Some(cc)) {
              org.apache.commons.lang3.Validate.notBlank(putJson.status)
            }
            (applicationBefore, _) <- NewStyle.function.getAccountApplicationById(accountApplicationIdStr, Some(cc))
            userIdOpt = Option(applicationBefore.userId)
            customerIdOpt = Option(applicationBefore.customerId)
            appUser <- unboxOptionOBPReturnType(userIdOpt.map(NewStyle.function.findByUserId(_, Some(cc))))
            customer <- unboxOptionOBPReturnType(customerIdOpt.map(NewStyle.function.getCustomerByCustomerId(_, Some(cc))))
            // Guard BEFORE the status transition commits: failing after it would strand the
            // application as ACCEPTED with no account. A consent-user applicant can only come
            // from a row that predates the creation-side guard (or was written another way).
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidUserId The application's user is a consent user (an agent identity minted by a Consent). Accounts are held by humans - re-apply with the granting user's USER_ID.",
              failCode = 400, cc = Some(cc))(!appUser.exists(_.isConsentUser))
            (accountApplication, _) <- NewStyle.function.updateAccountApplicationStatus(accountApplicationIdStr, putJson.status, Some(cc))
            _ <- putJson.status match {
              case "ACCEPTED" =>
                // The APPLICANT becomes the holder. The Lift-era code (ported verbatim) made the
                // approving admin the holder and left appUser unused — every accepted application
                // handed the account to whoever clicked approve. Customer-only applications
                // (userId empty) keep the legacy approver-as-holder behaviour: there is no user
                // to hold, and refusing here would strand the just-committed ACCEPTED status.
                for {
                  accountId <- Future(AccountId(java.util.UUID.randomUUID().toString))
                  holder = appUser.getOrElse(user)
                  (_, _) <- NewStyle.function.createBankAccount(
                    bank.bankId, accountId,
                    accountApplication.productCode.value,
                    "", "EUR", BigDecimal("0"),
                    holder.name, "",
                    List.empty, Some(cc))
                  success <- code.model.dataAccess.BankAccountCreation.setAccountHolderAndRefreshUserAccountAccess(
                    bank.bankId, accountId, holder, Some(cc))
                } yield success
              case _ => Future("")
            }
          } yield createAccountApplicationJson(accountApplication, appUser, customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateAccountApplicationStatus),
      "PUT",
      "/banks/BANK_ID/account-applications/ACCOUNT_APPLICATION_ID",
      "Update Account Application Status",
      s"""Update an Account Application status
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      accountApplicationUpdateStatusJson,
      accountApplicationResponseJson,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
      List(apiTagAccountApplication, apiTagAccount),
      None,
      http4sPartialFunction = Some(updateAccountApplicationStatus)
    )

    // ─── createCustomer (POST) ───────────────────────────────────────────────

    val createCustomer: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customers" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostCustomerJsonV310, Any](req) { (user, bank, postedData, cc) =>
          for {
            _ <- NewStyle.function.hasAtLeastOneEntitlement(bank.bankId.value, user.userId,
              canCreateCustomer :: canCreateCustomerAtAnyBank :: Nil, Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              failMsg = InvalidJsonContent + s" The field dependants(${postedData.dependants}) not equal the length(${postedData.dob_of_dependants.length}) of dob_of_dependants array",
              cc = Some(cc)) {
              postedData.dependants == postedData.dob_of_dependants.length
            }
            (customer, _) <- NewStyle.function.createCustomer(
              bank.bankId,
              postedData.legal_name,
              postedData.mobile_phone_number,
              postedData.email,
              CustomerFaceImage(postedData.face_image.date, postedData.face_image.url),
              postedData.date_of_birth,
              postedData.relationship_status,
              postedData.dependants,
              postedData.dob_of_dependants,
              postedData.highest_education_attained,
              postedData.employment_status,
              postedData.kyc_status,
              postedData.last_ok_date,
              Option(CreditRating(postedData.credit_rating.rating, postedData.credit_rating.source)),
              Option(CreditLimit(postedData.credit_limit.currency, postedData.credit_limit.amount)),
              postedData.title,
              postedData.branch_id,
              postedData.name_suffix,
              Some(cc))
          } yield JSONFactory310.createCustomerJson(customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createCustomer),
      "POST",
      "/banks/BANK_ID/customers",
      "Create Customer",
      s"""
      |The Customer resource stores the customer number (which is set by the backend), legal name, email, phone number, their date of birth, relationship status, education attained, a url for a profile image, KYC status etc.
      |Dates need to be in the format 2013-01-21T23:08:00Z
      |
      |Note: If you need to set a specific customer number, use the Update Customer Number endpoint after this call.
      |
      |${userAuthenticationMessage(true)}
      |""",
      postCustomerJsonV310,
      customerJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, InvalidJsonFormat,
      CustomerNumberAlreadyExists, UserNotFoundById, CustomerAlreadyExistsForUser,
      CreateCustomerError, UnknownError),
      List(apiTagCustomer, apiTagPerson),
      Some(List(canCreateCustomer, canCreateCustomerAtAnyBank)),
      http4sPartialFunction = Some(createCustomer)
    )

    // ─── getCustomerByCustomerNumber (POST → 200) ────────────────────────────

    val getCustomerByCustomerNumber: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "customers" / "customer-number" =>
        EndpointHelpers.withUserAndBankAndBody[PostCustomerNumberJsonV310, Any](req) { (user, bank, postedData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canGetCustomersAtOneBank, Some(cc))
            (customer, _) <- NewStyle.function.getCustomerByCustomerNumber(postedData.customer_number, bank.bankId, Some(cc))
            (customerAttributes, _) <- NewStyle.function.getCustomerAttributes(
              bank.bankId, CustomerId(customer.customerId), Some(cc))
          } yield JSONFactory310.createCustomerWithAttributesJson(customer, customerAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getCustomerByCustomerNumber),
      "POST",
      "/banks/BANK_ID/customers/customer-number",
      "Get Customer by CUSTOMER_NUMBER",
      s"""Gets the Customer specified by CUSTOMER_NUMBER.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      postCustomerNumberJsonV310,
      customerWithAttributesJsonV310,
      List(AuthenticatedUserIsRequired, UserCustomerLinksNotFoundForUser, UnknownError),
      List(apiTagCustomer, apiTagKyc),
      Some(List(canGetCustomersAtOneBank)),
      http4sPartialFunction = Some(getCustomerByCustomerNumber)
    )

    // ─── createAccountApplication (POST) ─────────────────────────────────────

    val createAccountApplication: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "account-applications" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[AccountApplicationJson, Any](req) { (_, bank, postedData, cc) =>
          for {
            _ <- NewStyle.function.tryons(s"$InvalidJsonFormat product_code should not be empty.", 400, Some(cc)) {
              org.apache.commons.lang3.Validate.notBlank(postedData.product_code)
            }
            _ <- NewStyle.function.tryons(s"$InvalidJsonFormat User_id and customer_id should not both are empty.", 400, Some(cc)) {
              org.apache.commons.lang3.Validate.isTrue(postedData.user_id.isDefined || postedData.customer_id.isDefined)
            }
            appUser <- unboxOptionOBPReturnType(postedData.user_id.map(NewStyle.function.findByUserId(_, Some(cc))))
            // Explicit target: fail loud rather than redirect (see the entitlement endpoints).
            // On ACCEPTED the application's user becomes the account holder, so a consent
            // user must be rejected here, before the application is stored.
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidUserId user_id names a consent user (an agent identity minted by a Consent). Accounts are held by humans - use the granting user's USER_ID.",
              failCode = 400, cc = Some(cc))(!appUser.exists(_.isConsentUser))
            customer <- unboxOptionOBPReturnType(postedData.customer_id.map(NewStyle.function.getCustomerByCustomerId(_, Some(cc))))
            (accountApplication, _) <- NewStyle.function.createAccountApplication(
              productCode = ProductCode(postedData.product_code),
              userId = postedData.user_id,
              customerId = postedData.customer_id,
              callContext = Some(cc))
          } yield createAccountApplicationJson(accountApplication, appUser, customer)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createAccountApplication),
      "POST",
      "/banks/BANK_ID/account-applications",
      "Create Account Application",
      s""" Create Account Application
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      accountApplicationJson,
      accountApplicationResponseJson,
      List(InvalidJsonFormat, UnknownError),
      List(apiTagAccountApplication, apiTagAccount),
      None,
      http4sPartialFunction = Some(createAccountApplication)
    )

    // ─── createAccountAttribute (POST) ───────────────────────────────────────

    val createAccountAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "products" / productCodeStr / "attribute" =>
        EndpointHelpers.withUserAndBodyCreated[AccountAttributeJson, Any](req) { (user, postedData, cc) =>
          for {
            accountAttributeType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${AccountAttributeType.DOUBLE}(2012-04-23), ${AccountAttributeType.STRING}(TAX_NUMBER), ${AccountAttributeType.INTEGER}(123) and ${AccountAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) { AccountAttributeType.withName(postedData.`type`) }
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            (_, _) <- NewStyle.function.getBankAccount(BankId(bankIdStr), AccountId(accountIdStr), Some(cc))
            _ <- NewStyle.function.hasEntitlement(bankIdStr, user.userId, ApiRole.canCreateAccountAttributeAtOneBank, Some(cc))
            (_, _) <- NewStyle.function.getProduct(BankId(bankIdStr), ProductCode(productCodeStr), Some(cc))
            (accountAttribute, _) <- NewStyle.function.createOrUpdateAccountAttribute(
              BankId(bankIdStr), AccountId(accountIdStr), ProductCode(productCodeStr),
              None, postedData.name, accountAttributeType, postedData.value,
              postedData.product_instance_code, Some(cc))
          } yield createAccountAttributeJson(accountAttribute)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createAccountAttribute),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/products/PRODUCT_CODE/attribute",
      "Create Account Attribute",
      s""" Create Account Attribute
      |
      |$accountAttributeGeneralInfo
      |
      |Typical account attributes might be:
      |
      |ISIN (for International bonds)
      |VKN (for German bonds)
      |REDCODE (markit short code for credit derivative)
      |LOAN_ID (e.g. used for Anacredit reporting)
      |
      |ISSUE_DATE (When the bond was issued in the market)
      |MATURITY_DATE (End of life time of a product)
      |TRADABLE
      |
      |See [FPML](http://www.fpml.org/) for more examples.
      |
      |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      accountAttributeJson,
      accountAttributeResponseJson,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagAccount, apiTagAccountAttribute, apiTagAttribute),
      Some(List(canCreateAccountAttributeAtOneBank)),
      http4sPartialFunction = Some(createAccountAttribute)
    )

    // ─── updateAccountAttribute (PUT) ────────────────────────────────────────

    val updateAccountAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr / "products" / productCodeStr / "attributes" / accountAttributeIdStr =>
        EndpointHelpers.withUserAndBodyCreated[AccountAttributeJson, Any](req) { (user, postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            _ <- NewStyle.function.hasEntitlement(bankIdStr, user.userId, canUpdateAccountAttribute, Some(cc))
            accountAttributeType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${AccountAttributeType.DOUBLE}(2012-04-23), ${AccountAttributeType.STRING}(TAX_NUMBER), ${AccountAttributeType.INTEGER}(123) and ${AccountAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) { AccountAttributeType.withName(postedData.`type`) }
            (_, _) <- NewStyle.function.getBankAccount(BankId(bankIdStr), AccountId(accountIdStr), Some(cc))
            (_, _) <- NewStyle.function.getProduct(BankId(bankIdStr), ProductCode(productCodeStr), Some(cc))
            (_, _) <- NewStyle.function.getAccountAttributeById(accountAttributeIdStr, Some(cc))
            (accountAttribute, _) <- NewStyle.function.createOrUpdateAccountAttribute(
              BankId(bankIdStr), AccountId(accountIdStr), ProductCode(productCodeStr),
              Some(accountAttributeIdStr), postedData.name, accountAttributeType, postedData.value,
              postedData.product_instance_code, Some(cc))
          } yield createAccountAttributeJson(accountAttribute)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateAccountAttribute),
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/products/PRODUCT_CODE/attributes/ACCOUNT_ATTRIBUTE_ID",
      "Update Account Attribute",
      s""" Update Account Attribute
      |
      |$accountAttributeGeneralInfo
      |
      |Typical account attributes might be:
      |
      |ISIN (for International bonds)
      |VKN (for German bonds)
      |REDCODE (markit short code for credit derivative)
      |LOAN_ID (e.g. used for Anacredit reporting)
      |
      |ISSUE_DATE (When the bond was issued in the market)
      |MATURITY_DATE (End of life time of a product)
      |TRADABLE
      |
      |See [FPML](http://www.fpml.org/) for more examples.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      accountAttributeJson,
      accountAttributeResponseJson,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagAccount, apiTagAccountAttribute, apiTagAttribute),
      Some(List(canUpdateAccountAttribute)),
      http4sPartialFunction = Some(updateAccountAttribute)
    )

    // ─── createMeeting (POST) ────────────────────────────────────────────────

    val createMeeting: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "meetings" =>
        // Manual body parsing to preserve Lift's error message format:
        //   "$InvalidJsonFormat The Json body should be the $CreateMeetingJson "
        // (uses the v2.0.0 class name in the message; the test asserts this exact prefix.)
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          val rawBody = cc.httpBody.getOrElse("")
          val user = cc.user.openOrThrowException(AuthenticatedUserIsRequired)
          val bank = cc.bank.getOrElse(throw new RuntimeException(BankNotFound))
          for {
            createMeetingJson <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ${classOf[code.api.v2_0_0.CreateMeetingJson].getSimpleName} ",
              400, Some(cc)) { com.openbankproject.commons.util.JsonAliases.parse(rawBody).extract[CreateMeetingJsonV310] }
            creator = ContactDetails(
              createMeetingJson.creator.name,
              createMeetingJson.creator.mobile_phone,
              createMeetingJson.creator.email_address)
            invitees = createMeetingJson.invitees.map(invitee =>
              Invitee(
                ContactDetails(invitee.contact_details.name, invitee.contact_details.mobile_phone, invitee.contact_details.email_address),
                invitee.status))
            (meeting, _) <- NewStyle.function.createMeeting(
              bank.bankId, user, user,
              createMeetingJson.provider_id, createMeetingJson.purpose_id,
              createMeetingJson.date, "", "", "",
              creator, invitees, Some(cc))
          } yield JSONFactory310.createMeetingJson(meeting)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, "createMeeting", "POST",
      "/banks/BANK_ID/meetings",
      "Create Meeting (video conference/call)",
      """Create Meeting: Initiate a video conference/call with the bank.
        |
        |The Meetings resource contains meta data about video/other conference sessions
        |
        |provider_id determines the provider of the meeting / video chat service. MUST be url friendly (no spaces).
        |
        |purpose_id explains the purpose of the chat. onboarding | mortgage | complaint etc. MUST be url friendly (no spaces).
        |
        |Login is required.
        |
        |This call is **experimental**. Currently staff_user_id is not set. Further calls will be needed to correctly set this.
      """.stripMargin,
      createMeetingJsonV310, meetingJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, InvalidJsonFormat, UnknownError),
      List(apiTagMeeting, apiTagCustomer, apiTagExperimental), None,
      http4sPartialFunction = Some(createMeeting))

    // ─── createSystemView (POST) ─────────────────────────────────────────────

    val createSystemView: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "system-views" =>
        EndpointHelpers.withUserAndBodyCreated[CreateViewJsonV300, Any](req) { (user, createViewJson, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canCreateSystemView, Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              failMsg = InvalidSystemViewFormat + s"Current view_name (${createViewJson.name})",
              cc = Some(cc)) { isValidSystemViewName(createViewJson.name) }
            _ <- code.util.Helper.booleanToFuture(SystemViewCannotBePublicError, failCode = 400, cc = Some(cc)) {
              createViewJson.is_public == false
            }
            view <- ViewNewStyle.createSystemView(createViewJson.toCreateViewJson, Some(cc))
          } yield JSONFactory310.createViewJSON(view)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createSystemView),
      "POST",
      "/system-views",
      "Create System View",
      s"""Create a system view
      |
      | ${userAuthenticationMessage(true)} and the user needs to have access to the $canCreateSystemView entitlement.
      | The 'alias' field in the JSON can take one of two values:
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
      | Please note that system views cannot be public. In case you try to set it you will get the error $SystemViewCannotBePublicError
      | """,
      SwaggerDefinitionsJSON.createSystemViewJsonV300,
      viewJsonV300,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagSystemView),
      Some(List(canCreateSystemView)),
      http4sPartialFunction = Some(createSystemView)
    )

    // ─── createProductCollection (PUT — "Create or Update") ──────────────────

    val createProductCollection: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / _ / "product-collections" / collectionCodeStr =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PutProductCollectionsV310, Any](req) { (user, bank, product, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, canMaintainProductCollection, Some(cc))
            (products, _) <- NewStyle.function.getProducts(bank.bankId, Nil, Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              ProductNotFoundByProductCode + " {" + (product.parent_product_code :: product.children_product_codes).mkString(", ") + "}",
              cc = Some(cc)) {
              val existingCodes = products.map(_.code.value)
              val codes = product.parent_product_code :: product.children_product_codes
              codes.forall(i => existingCodes.contains(i))
            }
            (productCollection, _) <- NewStyle.function.getOrCreateProductCollection(
              collectionCodeStr, List(product.parent_product_code), Some(cc))
            (productCollectionItems, _) <- NewStyle.function.getOrCreateProductCollectionItems(
              collectionCodeStr, product.children_product_codes, Some(cc))
          } yield createProductCollectionsJson(productCollection, productCollectionItems)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createProductCollection),
      "PUT",
      "/banks/BANK_ID/product-collections/COLLECTION_CODE",
      "Create Product Collection",
      s"""Create or Update a Product Collection at the Bank.
      |
      |Use Product Collections to create Product "Baskets", "Portfolios", "Indices", "Collections", "Underlyings-lists", "Buckets" etc. etc.
      |
      |There is a many to many relationship between Products and Product Collections:
      |
      |* A Product can exist in many Collections
      |
      |* A Collection can contain many Products.
      |
      |A collection has collection code, one parent Product and one or more child Products.
      |
      |
      |$productHiearchyAndCollectionNote

      |${userAuthenticationMessage(true) }
      |
      |
      |""",
      putProductCollectionsV310,
      productCollectionsJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagProductCollection, apiTagProduct),
      Some(List(canMaintainProductCollection)),
      http4sPartialFunction = Some(createProductCollection)
    )

    // ─── addCardForBank (POST) ───────────────────────────────────────────────

    val addCardForBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "banks" / _ / "cards" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[CreatePhysicalCardJsonV310, Any](req) { (user, bank, postJson, cc) =>
          for {
            _ <- postJson.allows match {
              case List() => Future.successful(true)
              case _ => code.util.Helper.booleanToFuture(
                AllowedValuesAre + CardAction.availableValues.mkString(", "), cc = Some(cc)) {
                postJson.allows.forall(a => CardAction.availableValues.contains(a))
              }
            }
            cardReplacementReason <- NewStyle.function.tryons(
              AllowedValuesAre + CardReplacementReason.availableValues.mkString(", "), 400, Some(cc)) {
              postJson.replacement match {
                case Some(value) => CardReplacementReason.valueOf(value.reason_requested)
                case None => CardReplacementReason.valueOf(CardReplacementReason.FIRST.toString)
              }
            }
            _ <- code.util.Helper.booleanToFuture(
              s"${maximumLimitExceeded.replace("10000", "10")} Current issue_number is ${postJson.issue_number}",
              cc = Some(cc)) { postJson.issue_number.length <= 10 }
            _ <- code.util.Helper.booleanToFuture(
              s"$UserHasMissingRoles${ApiRole.canCreateCardsForBank}",
              failCode = 403, cc = Some(cc)) {
              APIUtil.hasEntitlement(bank.bankId.value, user.userId, ApiRole.canCreateCardsForBank)
            }
            (_, _) <- NewStyle.function.getBankAccount(bank.bankId, AccountId(postJson.account_id), Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, Some(cc))
            replacement = postJson.replacement.map(r =>
              CardReplacementInfo(requestedDate = r.requested_date, cardReplacementReason))
            collected = postJson.collected.map(c => CardCollectionInfo(c))
            posted = postJson.posted.map(p => CardPostedInfo(p))
            (card, _) <- NewStyle.function.createPhysicalCard(
              bankCardNumber = postJson.card_number,
              nameOnCard = postJson.name_on_card,
              cardType = postJson.card_type,
              issueNumber = postJson.issue_number,
              serialNumber = postJson.serial_number,
              validFrom = postJson.valid_from_date,
              expires = postJson.expires_date,
              enabled = postJson.enabled,
              cancelled = false,
              onHotList = false,
              technology = postJson.technology,
              networks = postJson.networks,
              allows = postJson.allows,
              accountId = postJson.account_id,
              bankId = bank.bankId.value,
              replacement = replacement,
              pinResets = postJson.pin_reset.map(e => PinResetInfo(e.requested_date, PinResetReason.valueOf(e.reason_requested.toUpperCase))),
              collected = collected,
              posted = posted,
              customerId = postJson.customer_id,
              cvv = "",
              brand = "",
              callContext = Some(cc))
          } yield createPhysicalCardJson(card, user)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(addCardForBank),
      "POST",
      "/management/banks/BANK_ID/cards",
      "Create Card",
      s"""Create Card at bank specified by BANK_ID .
      |
      |${userAuthenticationMessage(true)}
      |""",
      createPhysicalCardJsonV310,
      physicalCardJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, AllowedValuesAre, UnknownError),
      List(apiTagCard),
      None,
      http4sPartialFunction = Some(addCardForBank)
    )

    // ─── updatedCardForBank (PUT) ────────────────────────────────────────────

    val updatedCardForBank: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "banks" / _ / "cards" / cardIdStr =>
        EndpointHelpers.withUserAndBankAndBody[UpdatePhysicalCardJsonV310, Any](req) { (user, bank, postJson, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, ApiRole.canUpdateCardsForBank, Some(cc))
            _ <- postJson.allows match {
              case List() => Future.successful(1)
              case _ => code.util.Helper.booleanToFuture(
                AllowedValuesAre + CardAction.availableValues.mkString(", "), cc = Some(cc)) {
                postJson.allows.forall(a => CardAction.availableValues.contains(a))
              }
            }
            _ <- NewStyle.function.tryons(
              AllowedValuesAre + CardReplacementReason.availableValues.mkString(", "), 400, Some(cc)) {
              CardReplacementReason.valueOf(postJson.replacement.reason_requested)
            }
            _ <- code.util.Helper.booleanToFuture(
              s"${maximumLimitExceeded.replace("10000", "10")} Current issue_number is ${postJson.issue_number}",
              cc = Some(cc)) { postJson.issue_number.length <= 10 }
            (_, _) <- NewStyle.function.getBankAccount(bank.bankId, AccountId(postJson.account_id), Some(cc))
            (existingCard, _) <- NewStyle.function.getPhysicalCardForBank(bank.bankId, cardIdStr, Some(cc))
            (_, _) <- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, Some(cc))
            (card, _) <- NewStyle.function.updatePhysicalCard(
              cardId = cardIdStr,
              bankCardNumber = existingCard.bankCardNumber,
              cardType = postJson.card_type,
              nameOnCard = postJson.name_on_card,
              issueNumber = postJson.issue_number,
              serialNumber = postJson.serial_number,
              validFrom = postJson.valid_from_date,
              expires = postJson.expires_date,
              enabled = postJson.enabled,
              cancelled = false,
              onHotList = false,
              technology = postJson.technology,
              networks = postJson.networks,
              allows = postJson.allows,
              accountId = postJson.account_id,
              bankId = bank.bankId.value,
              replacement = Some(CardReplacementInfo(
                requestedDate = postJson.replacement.requested_date,
                CardReplacementReason.valueOf(postJson.replacement.reason_requested))),
              pinResets = postJson.pin_reset.map(e => PinResetInfo(e.requested_date, PinResetReason.valueOf(e.reason_requested.toUpperCase))),
              collected = Option(CardCollectionInfo(postJson.collected)),
              posted = Option(CardPostedInfo(postJson.posted)),
              customerId = postJson.customer_id,
              callContext = Some(cc))
          } yield createPhysicalCardJson(card, user)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updatedCardForBank),
      "PUT",
      "/management/banks/BANK_ID/cards/CARD_ID",
      "Update Card",
      s"""Update Card at bank specified by CARD_ID .
      |${userAuthenticationMessage(true)}
      |""",
      updatePhysicalCardJsonV310,
      physicalCardJsonV310,
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, AllowedValuesAre, UnknownError),
      List(apiTagCard),
      Some(List(canCreateCardsForBank)),
      http4sPartialFunction = Some(updatedCardForBank)
    )

    // ─── createCardAttribute (POST) ──────────────────────────────────────────

    val createCardAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "banks" / bankIdStr / "cards" / cardIdStr / "attribute" =>
        EndpointHelpers.executeFutureWithBodyCreated[CardAttributeJson, Any](req) { (postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            (_, _) <- NewStyle.function.getPhysicalCardForBank(BankId(bankIdStr), cardIdStr, Some(cc))
            cardAttrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${CardAttributeType.DOUBLE}(12.1234), ${CardAttributeType.STRING}(TAX_NUMBER), ${CardAttributeType.INTEGER}(123) and ${CardAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) { CardAttributeType.withName(postedData.`type`) }
            (cardAttribute, _) <- NewStyle.function.createOrUpdateCardAttribute(
              Some(BankId(bankIdStr)), Some(cardIdStr), None,
              postedData.name, cardAttrType, postedData.value, Some(cc))
          } yield (cardAttribute: CardAttributeCommons)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createCardAttribute),
      "POST",
      "/management/banks/BANK_ID/cards/CARD_ID/attribute",
      "Create Card Attribute",
      s""" Create Card Attribute
      |
      |Card Attributes are used to describe a financial Product with a list of typed key value pairs.
      |
      |Each Card Attribute is linked to its Card by CARD_ID
      |
      |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      CardAttributeJson(
        cardAttributeNameExample.value,
        CardAttributeType.DOUBLE.toString,
        cardAttributeValueExample.value
      ),
      CardAttributeCommons(
      Some(BankId(bankIdExample.value)),
      Some(cardIdExample.value),
      Some(cardAttributeIdExample.value),
      cardAttributeNameExample.value,
      CardAttributeType.DOUBLE,
      cardAttributeValueExample.value),
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagCard, apiTagCardAttribute, apiTagAttribute),
      None,
      http4sPartialFunction = Some(createCardAttribute)
    )

    // ─── updateCardAttribute (PUT) ───────────────────────────────────────────

    val updateCardAttribute: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "banks" / bankIdStr / "cards" / cardIdStr / "attributes" / cardAttributeIdStr =>
        EndpointHelpers.executeFutureWithBody[CardAttributeJson, Any](req) { (postedData, cc) =>
          for {
            (_, _) <- NewStyle.function.getBank(BankId(bankIdStr), Some(cc))
            (_, _) <- NewStyle.function.getPhysicalCardForBank(BankId(bankIdStr), cardIdStr, Some(cc))
            (_, _) <- NewStyle.function.getCardAttributeById(cardAttributeIdStr, Some(cc))
            cardAttrType <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
                s"${CardAttributeType.DOUBLE}(12.1234), ${CardAttributeType.STRING}(TAX_NUMBER), ${CardAttributeType.INTEGER}(123) and ${CardAttributeType.DATE_WITH_DAY}(2012-04-23)",
              400, Some(cc)) { CardAttributeType.withName(postedData.`type`) }
            (cardAttribute, _) <- NewStyle.function.createOrUpdateCardAttribute(
              Some(BankId(bankIdStr)), Some(cardIdStr), Some(cardAttributeIdStr),
              postedData.name, cardAttrType, postedData.value, Some(cc))
          } yield (cardAttribute: CardAttributeCommons)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateCardAttribute),
      "PUT",
      "/management/banks/BANK_ID/cards/CARD_ID/attributes/CARD_ATTRIBUTE_ID",
      "Update Card Attribute",
      s""" Update Card Attribute
      |
      |Card Attributes are used to describe a financial Product with a list of typed key value pairs.
      |
      |Each Card Attribute is linked to its Card by CARD_ID
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      CardAttributeJson(
        cardAttributeNameExample.value,
        CardAttributeType.DOUBLE.toString,
        cardAttributeValueExample.value
      ),
      CardAttributeCommons(
      Some(BankId(bankIdExample.value)),
      Some(cardIdExample.value),
      Some(cardAttributeIdExample.value),
      cardAttributeNameExample.value,
      CardAttributeType.DOUBLE,
      cardAttributeValueExample.value),
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, UnknownError),
      List(apiTagCard, apiTagCardAttribute, apiTagAttribute),
      None,
      http4sPartialFunction = Some(updateCardAttribute)
    )

    // ─── createWebUiProps (POST) ─────────────────────────────────────────────

    val createWebUiProps: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "webui_props" =>
        EndpointHelpers.withUserAndBodyCreated[WebUiPropsCommons, Any](req) { (user, postedData, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canCreateWebUiProps, Some(cc))
            _ <- NewStyle.function.tryons(
              s"""$InvalidWebUiProps name must be start with webui_, but current post name is: ${postedData.name} """,
              400, Some(cc)) { require(postedData.name.startsWith("webui_")) }
            webUiProps <- Future(MappedWebUiPropsProvider.createOrUpdate(postedData)) map {
              unboxFullOrFail(_, Some(cc))
            }
          } yield (webUiProps: WebUiPropsCommons)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createWebUiProps),
      "POST",
      "/management/webui_props",
      "Create WebUiProps",
      s"""Create a WebUiProps.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |Explaination of Fields:
      |
      |* name is required String value
      |* value is required String value
      |
      |The line break and double quotations should do escape, example:
      |
      |```
      |
      |{"name": "webui_some", "value": "this value
      |have "line break" and double quotations."}
      |
      |```
      |should do escape like this:
      |
      |```
      |
      |{"name": "webui_some", "value": "this value\\nhave \\"line break\\" and double quotations."}
      |
      |```
      |
      |Insert image examples:
      |
      |```
      |// set width=100 and height=50
      |{"name": "webui_some_pic", "value": "here is a picture ![hello](http://somedomain.com/images/pic.png =100x50)"}
      |
      |// only set height=50
      |{"name": "webui_some_pic", "value": "here is a picture ![hello](http://somedomain.com/images/pic.png =x50)"}
      |
      |// only width=20%
      |{"name": "webui_some_pic", "value": "here is a picture ![hello](http://somedomain.com/images/pic.png =20%x)"}
      |
      |```
      |
      |""",
      WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com"),
      WebUiPropsCommons( "webui_api_explorer_url", "https://apiexplorer.openbankproject.com", Some("some-web-ui-props-id")),
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, UnknownError),
      List(apiTagWebUiProps),
      Some(List(canCreateWebUiProps)),
      http4sPartialFunction = Some(createWebUiProps)
    )

    // ─── createUserAuthContextUpdateRequest (POST) ───────────────────────────

    val createUserAuthContextUpdateRequest: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "users" / "current" / "auth-context-updates" / scaMethod =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostUserAuthContextJson, Any](req) { (user, bank, postedData, cc) =>
          for {
            _ <- code.util.Helper.booleanToFuture(
              failMsg = ConsumerHasMissingRoles + ApiRole.canCreateUserAuthContextUpdate,
              cc = Some(cc)) {
              APIUtil.checkScope(bank.bankId.value, APIUtil.getConsumerPrimaryKey(Some(cc)), ApiRole.canCreateUserAuthContextUpdate)
            }
            _ <- code.util.Helper.booleanToFuture(UserAuthContextUpdateRequestAllowedScaMethods, cc = Some(cc)) {
              List(StrongCustomerAuthentication.SMS.toString, StrongCustomerAuthentication.EMAIL.toString).contains(scaMethod)
            }
            (userAuthContextUpdate, _) <- NewStyle.function.validateUserAuthContextUpdateRequest(
              bank.bankId.value, user.userId, postedData.key, postedData.value, scaMethod, Some(cc))
          } yield JSONFactory310.createUserAuthContextUpdateJson(userAuthContextUpdate)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createUserAuthContextUpdateRequest),
      "POST",
      "/banks/BANK_ID/users/current/auth-context-updates/SCA_METHOD",
      "Create User Auth Context Update Request",
      s"""Create User Auth Context Update Request.
      |${userAuthenticationMessage(true)}
      |
      |A One Time Password (OTP) (AKA security challenge) is sent Out of Band (OOB) to the User via the transport defined in SCA_METHOD
      |SCA_METHOD is typically "SMS" or "EMAIL". "EMAIL" is used for testing purposes.
      |
      |""",
      postUserAuthContextJson,
      userAuthContextUpdateJson,
      List(AuthenticatedUserIsRequired, InvalidJsonFormat, CreateUserAuthContextError, UnknownError),
      List(apiTagUser),
      None,
      http4sPartialFunction = Some(createUserAuthContextUpdateRequest)
    )

    // ─── answerUserAuthContextUpdateChallenge (POST → 200) ───────────────────

    val answerUserAuthContextUpdateChallenge: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "users" / "current" / "auth-context-updates" / authContextUpdateIdStr / "challenge" =>
        EndpointHelpers.executeFutureWithBody[PostUserAuthContextUpdateJsonV310, Any](req) { (postBody, cc) =>
          for {
            (userAuthContextUpdate, _) <- NewStyle.function.checkAnswer(authContextUpdateIdStr, postBody.answer, Some(cc))
            (subjectUser, _) <- NewStyle.function.getUserByUserId(userAuthContextUpdate.userId, Some(cc))
            _ <- userAuthContextUpdate.status match {
              case status if status == com.openbankproject.commons.model.UserAuthContextUpdateStatus.ACCEPTED.toString =>
                NewStyle.function.createUserAuthContext(
                  subjectUser, userAuthContextUpdate.key, userAuthContextUpdate.value, Some(cc)
                ).map(x => (Some(x._1), x._2))
              case _ => Future((None, Some(cc)))
            }
            _ <- userAuthContextUpdate.key match {
              case "CUSTOMER_NUMBER" =>
                NewStyle.function.getOCreateUserCustomerLink(
                  BankId(bankIdStr), userAuthContextUpdate.value, subjectUser.userId, Some(cc))
              case _ => Future((None, Some(cc)))
            }
          } yield createUserAuthContextUpdateJson(userAuthContextUpdate)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(answerUserAuthContextUpdateChallenge),
      "POST",
      "/banks/BANK_ID/users/current/auth-context-updates/AUTH_CONTEXT_UPDATE_ID/challenge",
      "Answer Auth Context Update Challenge",
      s"""
      |Answer Auth Context Update Challenge.
      |""",
      PostUserAuthContextUpdateJsonV310(answer = "12345678"),
      userAuthContextUpdateJson,
      List(AuthenticatedUserIsRequired, BankNotFound, InvalidJsonFormat, InvalidConnectorResponse, UnknownError),
      apiTagUser :: Nil,
      None,
      http4sPartialFunction = Some(answerUserAuthContextUpdateChallenge)
    )

    // ─── refreshUser (POST) ──────────────────────────────────────────────────

    val refreshUser: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "users" / userIdStr / "refresh" =>
        // Lift returns 201 (CREATED) for this POST — middleware has already validated auth
        // and the canRefreshUser role, so we use executeFutureCreated to preserve the status.
        EndpointHelpers.executeFutureCreated(req) {
          implicit val cc: CallContext = req.callContext
          for {
            startTime <- Future(Helpers.now)
            (subjectUser, _) <- NewStyle.function.findByUserId(userIdStr, Some(cc))
            _ <- AuthUser.refreshUser(subjectUser, Some(cc))
            endTime <- Future(Helpers.now)
            durationTime = endTime.getTime - startTime.getTime
          } yield createRefreshUserJson(durationTime)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(refreshUser),
      "POST",
      "/users/USER_ID/refresh",
      "Refresh User",
      s""" The endpoint is used for updating the accounts, views, account holders for the user.
      | As to the Json body, you can leave it as Empty.
      | This call will get data from backend, no need to prepare the json body in api side.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      EmptyBody,
      refresUserJson,
      List(UserHasMissingRoles, UnknownError),
      List(apiTagUser),
      Some(List(canRefreshUser)),
      http4sPartialFunction = Some(refreshUser)
    )

    // ─── createProduct (PUT — "Create or Update") ────────────────────────────

    val createProduct: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "products" / productCodeStr =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostPutProductJsonV310, Any](req) { (user, bank, product, cc) =>
          for {
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = createProductEntitlementsRequiredText)(
              bank.bankId.value, user.userId, createProductEntitlements, Some(cc))
            (parentProduct, _) <- product.parent_product_code.trim.nonEmpty match {
              case false => Future((Empty, Some(cc)))
              case true =>
                NewStyle.function.getProduct(bank.bankId, ProductCode(product.parent_product_code), Some(cc))
                  .map(p => (Full(p._1), Some(cc)))
            }
            (success, _) <- NewStyle.function.createOrUpdateProduct(
              bankId = bank.bankId.value,
              code = productCodeStr,
              parentProductCode = parentProduct.map(_.code.value).toOption,
              name = product.name,
              category = product.category,
              family = product.family,
              superFamily = product.super_family,
              moreInfoUrl = product.more_info_url,
              termsAndConditionsUrl = null,
              details = product.details,
              description = product.description,
              metaLicenceId = product.meta.license.id,
              metaLicenceName = product.meta.license.name,
              Some(cc))
          } yield JSONFactory310.createProductJson(success)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, "createProduct", "PUT",
      "/banks/BANK_ID/products/PRODUCT_CODE",
      "Create Product",
      s"""Create or Update Product for the Bank.
      |
      |
      |Typical Super Family values / Asset classes are:
      |
      |Debt
      |Equity
      |FX
      |Commodity
      |Derivative
      |
      |$productHiearchyAndCollectionNote
      |
      |
      |${userAuthenticationMessage(true) }
      |
      |
      |""",
      postPutProductJsonV310, productJsonV310,
      List(AuthenticatedUserIsRequired, BankNotFound, UserHasMissingRoles, UnknownError),
      List(apiTagProduct), Some(List(canCreateProduct, canCreateProductAtAnyBank)),
      http4sPartialFunction = Some(createProduct))

    // ─── createMethodRouting (POST) ──────────────────────────────────────────

    val createMethodRouting: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "method_routings" =>
        EndpointHelpers.withUserAndBodyCreated[MethodRoutingCommons, Any](req) { (user, raw, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canCreateMethodRouting, Some(cc))
            postedData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ${classOf[MethodRoutingCommons]}", 400, Some(cc)) {
              raw.bankIdPattern match {
                case Some(v) if StringUtils.isBlank(v) || v.trim == "*" =>
                  raw.copy(bankIdPattern = Some(MethodRouting.bankIdPatternMatchAny))
                case _ => raw
              }
            }
            _ <- NewStyle.function.tryons(InvalidOutBoundMapping, 400, Some(cc)) { postedData.getOutBoundMapping }
            _ <- NewStyle.function.tryons(InvalidInBoundMapping, 400, Some(cc)) { postedData.getInBoundMapping }
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidConnectorName please check connectorName: ${postedData.connectorName} or the connector(${postedData.connectorName}) is not supported for this sandbox. ",
              failCode = 400, cc = Some(cc)) {
              NewStyle.function.getConnectorByName(postedData.connectorName).isDefined
            }
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidConnectorMethodName please check methodName: ${postedData.methodName}",
              failCode = 400, cc = Some(cc)) {
              if (postedData.connectorName == "internal")
                NewStyle.function.getConnectorMethod("mapped", postedData.methodName).isDefined
              else
                NewStyle.function.getConnectorMethod(postedData.connectorName, postedData.methodName).isDefined
            }
            _ <- NewStyle.function.tryons(
              s"$InvalidBankIdRegex The bankIdPattern is invalid regex, bankIdPatten: ${postedData.bankIdPattern.orNull} ",
              400, Some(cc)) {
              if (!postedData.isBankIdExactMatch && postedData.bankIdPattern.isDefined)
                Pattern.compile(postedData.bankIdPattern.get)
            }
            _ <- NewStyle.function.checkMethodRoutingAlreadyExists(postedData, Some(cc))
            created <- NewStyle.function.createOrUpdateMethodRouting(postedData) map {
              unboxFullOrFail(_, Some(cc))
            }
          } yield (created: MethodRoutingCommons).toJson
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createMethodRouting),
      "POST",
      "/management/method_routings",
      "Create MethodRouting",
      s"""Create a MethodRouting.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |Explanation of Fields:
      |
      |* method_name is required String value, current supported value: $supportedConnectorNames
      |* connector_name is required String value
      |* is_bank_id_exact_match is required boolean value, if bank_id_pattern is exact bank_id value, this value is true; if bank_id_pattern is null or a regex, this value is false
      |* bank_id_pattern is optional String value, it can be null, a exact bank_id or a regex
      |* parameters is optional array of key value pairs. You can set some parameters for this method
      |
      |note and CAVEAT!:
      |
      |* bank_id_pattern has to be empty for methods that do not take bank_id as a function parameter, otherwise might get empty result
      |* methods that aggregate bank objects (e.g. getBankAccountsForUser) have to take any  existing method routings for these objects into consideration
      |* so if you create e.g. a bank specific method routing for getting an account, make sure that it is also served by endpoints getting ALL accounts for ALL banks
      |* if bank_id_pattern is regex, special characters need to do escape, for example: bank_id_pattern = "some\\-id_pattern_\\d+"
      |
      |If the connector name starts with rest, parameters can contain "outBoundMapping" and "inBoundMapping", convert OutBound and InBound json structure.
      |for example:
      | outBoundMapping example, convert json from source to target:
      |![Snipaste_outBoundMapping](https://user-images.githubusercontent.com/2577334/75248007-33332e00-580e-11ea-8d2a-d1856035fa24.png)
      |Build OutBound json value rules:
      |1 set cId value with: outboundAdapterCallContext.correlationId value
      |2 set bankId value with: concat bankId.value value with  string helloworld
      |3 set originalJson value with: whole source json, note: the field value expression is $$root
      |
      |
      | inBoundMapping example, convert json from source to target:
      |![inBoundMapping](https://user-images.githubusercontent.com/2577334/75248199-a9d02b80-580e-11ea-9238-e073264e9170.png)
      |Build InBound json value rules:
      |1 and 2 set inboundAdapterCallContext and status value: because field name ends with "$$default", remove "$$default" from field name, not change the value
      |3 set fullName value with: concat string full: with result.name value
      |4 set bankRoutingScheme value: because source value is Array, but target value is not Array, the mapping field name must ends with [0].
      |""",
      MethodRoutingCommons("getBank", "rest_vMar2019", false, Some("some_bankId_.*"), List(MethodRoutingParam("url", "http://mydomain.com/xxx"))),
      MethodRoutingCommons("getBank", "rest_vMar2019", false, Some("some_bankId_.*"),
        List(MethodRoutingParam("url", "http://mydomain.com/xxx")),
        Some("this-method-routing-Id")
      ),
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
      InvalidConnectorName, InvalidConnectorMethodName, UnknownError),
      List(apiTagMethodRouting, apiTagApi),
      Some(List(canCreateMethodRouting)),
      http4sPartialFunction = Some(createMethodRouting)
    )

    // ─── updateMethodRouting (PUT) ───────────────────────────────────────────

    val updateMethodRouting: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "method_routings" / methodRoutingIdStr =>
        EndpointHelpers.withUserAndBody[MethodRoutingCommons, Any](req) { (user, raw, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, canUpdateMethodRouting, Some(cc))
            putData <- NewStyle.function.tryons(
              s"$InvalidJsonFormat The Json body should be the ${classOf[MethodRoutingCommons]}", 400, Some(cc)) {
              val entity = raw.copy(methodRoutingId = Some(methodRoutingIdStr))
              entity.bankIdPattern match {
                case Some(v) if StringUtils.isBlank(v) || v.trim == "*" =>
                  entity.copy(bankIdPattern = Some(MethodRouting.bankIdPatternMatchAny))
                case _ => entity
              }
            }
            _ <- NewStyle.function.tryons(InvalidOutBoundMapping, 400, Some(cc)) { putData.getOutBoundMapping }
            _ <- NewStyle.function.tryons(InvalidInBoundMapping, 400, Some(cc)) { putData.getInBoundMapping }
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidConnectorName please check connectorName: ${putData.connectorName}",
              failCode = 400, cc = Some(cc)) {
              NewStyle.function.getConnectorByName(putData.connectorName).isDefined
            }
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidConnectorMethodName please check methodName: ${putData.methodName}",
              failCode = 400, cc = Some(cc)) {
              if (putData.connectorName == "internal")
                NewStyle.function.getConnectorMethod("mapped", putData.methodName).isDefined
              else
                NewStyle.function.getConnectorMethod(putData.connectorName, putData.methodName).isDefined
            }
            (_, _) <- NewStyle.function.getMethodRoutingById(methodRoutingIdStr, Some(cc))
            _ <- NewStyle.function.tryons(
              s"$InvalidBankIdRegex The bankIdPattern is invalid regex, bankIdPatten: ${putData.bankIdPattern.orNull} ",
              400, Some(cc)) {
              if (!putData.isBankIdExactMatch && putData.bankIdPattern.isDefined)
                Pattern.compile(putData.bankIdPattern.get)
            }
            updated <- NewStyle.function.createOrUpdateMethodRouting(putData) map {
              unboxFullOrFail(_, Some(cc))
            }
          } yield (updated: MethodRoutingCommons).toJson
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateMethodRouting),
      "PUT",
      "/management/method_routings/METHOD_ROUTING_ID",
      "Update MethodRouting",
      s"""Update a MethodRouting.
      |
      |
      |${userAuthenticationMessage(true)}
      |
      |Explaination of Fields:
      |
      |* method_name is required String value, current supported value: $supportedConnectorNames
      |* connector_name is required String value
      |* is_bank_id_exact_match is required boolean value, if bank_id_pattern is exact bank_id value, this value is true; if bank_id_pattern is null or a regex, this value is false
      |* bank_id_pattern is optional String value, it can be null, a exact bank_id or a regex
      |* parameters is optional array of key value pairs. You can set some paremeters for this method
      |note:
      |
      |* if bank_id_pattern is regex, special characters need to do escape, for example: bank_id_pattern = "some\\-id_pattern_\\d+"
      |
      |If connector name start with rest, parameters can contain "outBoundMapping" and "inBoundMapping", to convert OutBound and InBound json structure.
      |for example:
      | outBoundMapping example, convert json from source to target:
      |![Snipaste_outBoundMapping](https://user-images.githubusercontent.com/2577334/75248007-33332e00-580e-11ea-8d2a-d1856035fa24.png)
      |Build OutBound json value rules:
      |1 set cId value with: outboundAdapterCallContext.correlationId value
      |2 set bankId value with: concat bankId.value value with  string helloworld
      |3 set originalJson value with: whole source json, note: the field value expression is $$root
      |
      |
      | inBoundMapping example, convert json from source to target:
      |![inBoundMapping](https://user-images.githubusercontent.com/2577334/75248199-a9d02b80-580e-11ea-9238-e073264e9170.png)
      |Build InBound json value rules:
      |1 and 2 set inboundAdapterCallContext and status value: because field name ends with "$$default", remove "$$default" from field name, not change the value
      |3 set fullName value with: concat string full: with result.name value
      |4 set bankRoutingScheme value: because source value is Array, but target value is not Array, the mapping field name must ends with [0].
      |""",
      MethodRoutingCommons("getBank", "rest_vMar2019", true, Some("some_bankId"), List(MethodRoutingParam("url", "http://mydomain.com/xxx"))),
      MethodRoutingCommons("getBank", "rest_vMar2019", true, Some("some_bankId"), List(MethodRoutingParam("url", "http://mydomain.com/xxx")), Some("this-method-routing-Id")),
      List(AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat,
      InvalidConnectorName, InvalidConnectorMethodName, UnknownError),
      List(apiTagMethodRouting, apiTagApi),
      Some(List(canUpdateMethodRouting)),
      http4sPartialFunction = Some(updateMethodRouting)
    )

    // ─── updateAccount (PUT) ─────────────────────────────────────────────────

    val updateAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "management" / "banks" / _ / "accounts" / accountIdStr =>
        EndpointHelpers.withUserAndBankAndBody[UpdateAccountRequestJsonV310, Any](req) { (user, bank, body, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement(bank.bankId.value, user.userId, ApiRole.canUpdateAccount, Some(cc))
            (_, _) <- NewStyle.function.getBankAccount(bank.bankId, AccountId(accountIdStr), Some(cc))
            _ <- code.util.Helper.booleanToFuture(
              s"$UpdateBankAccountException Duplication detected in account routings, please specify only one value per routing scheme",
              cc = Some(cc)) {
              body.account_routings.map(_.scheme).distinct.size == body.account_routings.size
            }
            alreadyExistAccountRoutings <- Future.sequence(body.account_routings.map(accountRouting =>
              NewStyle.function.getAccountRouting(Some(bank.bankId), accountRouting.scheme, accountRouting.address, Some(cc))
                .map {
                  case bankAccount if !(bankAccount._1.bankId == bank.bankId && bankAccount._1.accountId == AccountId(accountIdStr)) => Some(accountRouting)
                  case _ => None
                } fallbackTo Future.successful(None)
            ))
            alreadyExistingAccountRouting = alreadyExistAccountRoutings.collect {
              case Some(accountRouting) => s"bankId: ${bank.bankId}, scheme: ${accountRouting.scheme}, address: ${accountRouting.address}"
            }
            _ <- code.util.Helper.booleanToFuture(
              s"$AccountRoutingAlreadyExist (${alreadyExistingAccountRouting.mkString("; ")})",
              cc = Some(cc)) { alreadyExistingAccountRouting.isEmpty }
            (bankAccount, _) <- NewStyle.function.updateBankAccount(
              bank.bankId, AccountId(accountIdStr),
              body.`type`, body.label, body.branch_id,
              body.account_routings.map(r => AccountRouting(r.scheme, r.address)),
              Some(cc))
          } yield JSONFactory310.createUpdateResponseAccountJson(bankAccount)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(updateAccount),
      "PUT",
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID",
      "Update Account",
      s"""Update the account.
        |
        |${userAuthenticationMessage(true)}
        |
      """.stripMargin,
      updateAccountRequestJsonV310,
      updateAccountResponseJsonV310,
      List(InvalidJsonFormat, AuthenticatedUserIsRequired, UnknownError, BankAccountNotFound),
      List(apiTagAccount),
      Some(List(canUpdateAccount)),
      http4sPartialFunction = Some(updateAccount)
    )

    // ─── createAccount (PUT) ─────────────────────────────────────────────────
    // Self-or-other role check: when the logged-in user is creating an account
    // for themselves the role is waived; otherwise canCreateAccount is required
    // (403 on missing). booleanToFuture is used to enforce 403, matching CLAUDE.md
    // guidance for conditional roles.

    val createAccount: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ PUT -> `prefixPath` / "banks" / bankIdStr / "accounts" / accountIdStr =>
        EndpointHelpers.withUserAndBankAndBodyCreated[CreateAccountRequestJsonV310, Any](req) { (user, bank, body, cc) =>
          for {
            (accountBox, _) <- Connector.connector.vend.checkBankAccountExists(bank.bankId, AccountId(accountIdStr), Some(cc))
            _ <- code.util.Helper.booleanToFuture(AccountIdAlreadyExists, cc = Some(cc)) { accountBox.isEmpty }
            loggedInUserId = user.userId
            // Implicit owner resolves to the HUMAN: under a Consent the caller is the
            // per-consent shadow, and an account held by it strands when the consent dies.
            userIdAccountOwner = if (body.user_id.nonEmpty) body.user_id else cc.accountableUserId
            _ <- code.util.Helper.booleanToFuture(InvalidAccountIdFormat, cc = Some(cc)) { isValidID(accountIdStr) }
            _ <- code.util.Helper.booleanToFuture(InvalidBankIdFormat, cc = Some(cc)) { isValidID(bankIdStr) }
            (accountOwner, _) <- NewStyle.function.findByUserId(userIdAccountOwner, Some(cc))
            // Explicit target: fail loud rather than redirect (see the entitlement endpoints).
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidUserId user_id names a consent user (an agent identity minted by a Consent). Accounts are held by humans - use the granting user's USER_ID.",
              failCode = 400, cc = Some(cc))(!accountOwner.isConsentUser)
            _ <- if (userIdAccountOwner == loggedInUserId) Future.successful(Full(()))
                 else code.util.Helper.booleanToFuture(
                   s"$UserHasMissingRoles $canCreateAccount or create account for self",
                   failCode = 403, cc = Some(cc)) {
                   APIUtil.hasEntitlement(bank.bankId.value, loggedInUserId, canCreateAccount)
                 }
            initialBalanceAsNumber <- NewStyle.function.tryons(InvalidAccountInitialBalance, 400, Some(cc)) {
              BigDecimal(body.balance.amount)
            }
            _ <- code.util.Helper.booleanToFuture(InitialBalanceMustBeZero, cc = Some(cc)) { 0 == initialBalanceAsNumber }
            _ <- code.util.Helper.booleanToFuture(InvalidISOCurrencyCode, cc = Some(cc)) {
              isValidCurrencyISOCode(body.balance.currency)
            }
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidAccountRoutings Duplication detected in account routings, please specify only one value per routing scheme",
              failCode = 400, cc = Some(cc)) {
              body.account_routings.map(_.scheme).distinct.size == body.account_routings.size
            }
            alreadyExistAccountRoutings <- Future.sequence(body.account_routings.map(ar =>
              NewStyle.function.getAccountRouting(Some(bank.bankId), ar.scheme, ar.address, Some(cc))
                .map(_ => Some(ar)).fallbackTo(Future.successful(None))
            ))
            alreadyExistingAccountRouting = alreadyExistAccountRoutings.collect {
              case Some(ar) => s"bankId: ${bank.bankId}, scheme: ${ar.scheme}, address: ${ar.address}"
            }
            _ <- code.util.Helper.booleanToFuture(
              s"$AccountRoutingAlreadyExist (${alreadyExistingAccountRouting.mkString("; ")})",
              cc = Some(cc)) { alreadyExistingAccountRouting.isEmpty }
            (bankAccount, _) <- NewStyle.function.createBankAccount(
              bank.bankId, AccountId(accountIdStr),
              body.product_code, body.label, body.balance.currency, initialBalanceAsNumber,
              accountOwner.name, body.branch_id,
              body.account_routings.map(r => AccountRouting(r.scheme, r.address)),
              Some(cc))
            (productAttributes, _) <- NewStyle.function.getProductAttributesByBankAndCode(
              bank.bankId, ProductCode(body.product_code), Some(cc))
            (accountAttributes, _) <- NewStyle.function.createAccountAttributes(
              bank.bankId, AccountId(accountIdStr), ProductCode(body.product_code),
              productAttributes, None, Some(cc))
            _ <- BankAccountCreation.setAccountHolderAndRefreshUserAccountAccess(
              bank.bankId, AccountId(accountIdStr), accountOwner, Some(cc))
          } yield JSONFactory310.createAccountJSON(userIdAccountOwner, bankAccount, accountAttributes)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion, "createAccount", "PUT",
      "/banks/BANK_ID/accounts/NEW_ACCOUNT_ID",
      "Create Account",
      """Create Account at bank specified by BANK_ID with Id specified by ACCOUNT_ID.
      |
      |The User can create an Account for themself  - or -  the User that has the USER_ID specified in the POST body.
      |
      |If the PUT body USER_ID *is* specified, the logged in user must have the Role canCreateAccount. Once created, the Account will be owned by the User specified by USER_ID.
      |
      |If the PUT body USER_ID is *not* specified, the account will be owned by the logged in User.
      |
      |The 'product_code' field SHOULD be a product_code from Product.
      |If the 'product_code' matches a product_code from Product, account attributes will be created that match the Product Attributes.
      |
      |Note: The Amount MUST be zero.""".stripMargin,
      createAccountRequestJsonV310, createAccountResponseJsonV310,
      List(InvalidJsonFormat, BankNotFound, AuthenticatedUserIsRequired,
        InvalidUserId, InvalidAccountIdFormat, InvalidBankIdFormat,
        UserNotFoundById, UserHasMissingRoles, InvalidAccountBalanceAmount,
        InvalidAccountInitialBalance, InitialBalanceMustBeZero,
        InvalidAccountBalanceCurrency, AccountIdAlreadyExists, UnknownError),
      List(apiTagAccount, apiTagOnboarding), None,
      http4sPartialFunction = Some(createAccount))

    // ─── createConsent (POST) ────────────────────────────────────────────────

    val createConsent: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / bankIdStr / "my" / "consents" / scaMethod =>
        EndpointHelpers.withUserAndBodyCreated[PostConsentBodyCommonJson, Any](req) { (user, consentJson, cc) =>
          val raw = cc.httpBody.getOrElse("")
          for {
            _ <- code.util.Helper.booleanToFuture(ConsentAllowedScaMethods, cc = Some(cc)) {
              List(StrongCustomerAuthentication.SMS.toString,
                StrongCustomerAuthentication.EMAIL.toString,
                StrongCustomerAuthentication.IMPLICIT.toString).contains(scaMethod)
            }
            maxTimeToLive = APIUtil.getPropsAsIntValue(nameOfProperty = "consents.max_time_to_live", defaultValue = Constant.DEFAULT_CONSENT_TTL)
            _ <- code.util.Helper.booleanToFuture(s"$ConsentMaxTTL ($maxTimeToLive)", cc = Some(cc)) {
              consentJson.time_to_live match {
                case Some(ttl) => ttl <= maxTimeToLive
                case _ => true
              }
            }
            myEntitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(user.userId)
            _ <- code.util.Helper.booleanToFuture(RolesAllowedInConsent, cc = Some(cc)) {
              consentJson.entitlements.forall(re =>
                myEntitlements.getOrElse(Nil).exists(e => e.roleName == re.role_name && e.bankId == re.bank_id))
            }
            (_, assignedViews) <- Future(Views.views.vend.privateViewsUserCanAccess(user))
            _ <- code.util.Helper.booleanToFuture(ViewsAllowedInConsent, cc = Some(cc)) {
              consentJson.views.forall(rv => assignedViews.exists(e =>
                e.view_id == rv.view_id && e.bank_id == rv.bank_id && e.account_id == rv.account_id))
            }
            consumerTuple <- consentJson.consumer_id match {
              case Some(id) => NewStyle.function.checkConsumerByConsumerId(id, Some(cc)) map {
                c => (Some(c.consumerId.get), c.description, Some(c))
              }
              case None => Future((None, "Any application", None))
            }
            (consumerId, applicationText, consumer) = consumerTuple
            challengeAnswer = Props.mode match {
              case Props.RunModes.Test => Consent.challengeAnswerAtTestEnvironment
              case _ => SecureRandomUtil.numeric()
            }
            createdConsent <- Future(Consents.consentProvider.vend.createObpConsent(user, challengeAnswer, None, consumer)) map {
              i => connectorEmptyResponse(i, Some(cc))
            }
            consentJWT = Consent.createConsentJWT(
              user, consentJson, createdConsent.secret, createdConsent.consentId,
              consumerId, consentJson.valid_from, consentJson.time_to_live.getOrElse(3600), None)
            _ <- Future(Consents.consentProvider.vend.setJsonWebToken(createdConsent.consentId, consentJWT)) map {
              i => connectorEmptyResponse(i, Some(cc))
            }
            validUntil = code.util.Helper.calculateValidTo(consentJson.valid_from, consentJson.time_to_live.getOrElse(3600))
            _ <- Future(Consents.consentProvider.vend.setValidUntil(createdConsent.consentId, validUntil)) map {
              i => connectorEmptyResponse(i, Some(cc))
            }
            grantorConsumerId = cc.consumer.toOption.map(_.consumerId.get).getOrElse("Unknown")
            granteeConsumerId = consentJson.consumer_id.getOrElse("Unknown")
            shouldSkipConsentSca = APIUtil.skipConsentScaForConsumerIdPairs.contains(
              APIUtil.ConsumerIdPair(grantorConsumerId, granteeConsumerId))
            _ <- if (shouldSkipConsentSca) {
              Future {
                // Atomic guarded auto-accept: only move INITIATED -> ACCEPTED. If the consent was
                // concurrently revoked, the conditional UPDATE is a 0-row no-op and the revoke stands,
                // instead of the skip-SCA write blindly resurrecting it to ACCEPTED.
                code.bankconnectors.DoobieConsentStatusQueries.conditionalStatusTransitionByConsentId(
                  createdConsent.consentId, ConsentStatus.INITIATED.toString, ConsentStatus.ACCEPTED.toString)
              }
            } else {
              val challengeText = s"Your consent challenge : $challengeAnswer, Application: $applicationText"
              scaMethod match {
                case v if v == StrongCustomerAuthentication.EMAIL.toString =>
                  for {
                    postConsentEmailJson <- NewStyle.function.tryons(
                      s"$InvalidJsonFormat The Json body should be the $PostConsentEmailJsonV310", 400, Some(cc)) {
                      com.openbankproject.commons.util.JsonAliases.parse(raw).extract[PostConsentEmailJsonV310]
                    }
                    _ <- NewStyle.function.sendCustomerNotification(
                      StrongCustomerAuthentication.EMAIL,
                      postConsentEmailJson.email,
                      Some("OBP Consent Challenge"), challengeText, Some(cc))
                  } yield createdConsent
                case v if v == StrongCustomerAuthentication.SMS.toString =>
                  for {
                    postConsentPhoneJson <- NewStyle.function.tryons(
                      s"$InvalidJsonFormat The Json body should be the $PostConsentPhoneJsonV310", 400, Some(cc)) {
                      com.openbankproject.commons.util.JsonAliases.parse(raw).extract[PostConsentPhoneJsonV310]
                    }
                    _ <- NewStyle.function.sendCustomerNotification(
                      StrongCustomerAuthentication.SMS,
                      postConsentPhoneJson.phone_number,
                      None, challengeText, Some(cc))
                  } yield createdConsent
                case v if v == StrongCustomerAuthentication.IMPLICIT.toString =>
                  for {
                    (consentImplicitSCA, _) <- NewStyle.function.getConsentImplicitSCA(user, Some(cc))
                    _ <- consentImplicitSCA.scaMethod match {
                      case m if m == StrongCustomerAuthentication.EMAIL =>
                        NewStyle.function.sendCustomerNotification(
                          StrongCustomerAuthentication.EMAIL,
                          consentImplicitSCA.recipient,
                          Some("OBP Consent Challenge"), challengeText, Some(cc))
                      case m if m == StrongCustomerAuthentication.SMS =>
                        NewStyle.function.sendCustomerNotification(
                          StrongCustomerAuthentication.SMS,
                          consentImplicitSCA.recipient,
                          None, challengeText, Some(cc))
                      case _ => Future("Success")
                    }
                  } yield createdConsent
                case _ => Future(createdConsent)
              }
            }
          } yield ConsentJsonV310(createdConsent.consentId, consentJWT, createdConsent.status)
        }
    }

    // Lift registered three separate endpoints with concrete SCA-method URL segments.
    // Preserve those names so FrozenClassTest (STABLE API contract) stays green.
    val createConsentEmail: HttpRoutes[IO] = createConsent
    val createConsentSms: HttpRoutes[IO] = createConsent
    val createConsentImplicit: HttpRoutes[IO] = createConsent

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createConsentEmail),
      "POST",
      "/banks/BANK_ID/my/consents/EMAIL",
      "Create Consent (EMAIL)",
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
      |  "phone_number": "+49 170 1234567"
      |}
      |
      |Please note that consumer_id is optional field
      |Example 2:
      |{
      |  "everything": true,
      |  "views": [],
      |  "entitlements": [],
      |  "phone_number": "+49 170 1234567"
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
      |  "phone_number": "+49 170 1234567"
      |}
      |
      |""",
      postConsentEmailJsonV310,
      consentJsonV310,
      List(
        AuthenticatedUserIsRequired,
        BankNotFound,
        InvalidJsonFormat,
        ConsentAllowedScaMethods,
        RolesAllowedInConsent,
        ViewsAllowedInConsent,
        ConsumerNotFoundByConsumerId,
        ConsumerIsDisabled,
        InvalidConnectorResponse,
        UnknownError
      ),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(createConsentEmail)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createConsentSms),
      "POST",
      "/banks/BANK_ID/my/consents/SMS",
      "Create Consent (SMS)",
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
      |  "email": "eveline@example.com"
      |}
      |
      |Please note that consumer_id is optional field
      |Example 2:
      |{
      |  "everything": true,
      |  "views": [],
      |  "entitlements": [],
      |  "email": "eveline@example.com"
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
      |  "email": "eveline@example.com"
      |}
      |
      |""",
      postConsentPhoneJsonV310,
      consentJsonV310,
      List(
        AuthenticatedUserIsRequired,
        BankNotFound,
        InvalidJsonFormat,
        ConsentAllowedScaMethods,
        RolesAllowedInConsent,
        ViewsAllowedInConsent,
        ConsumerNotFoundByConsumerId,
        ConsumerIsDisabled,
        MissingPropsValueAtThisInstance,
        SmsServerNotResponding,
        InvalidConnectorResponse,
        UnknownError
      ),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(createConsentSms)
    )

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(createConsentImplicit),
      "POST",
      "/banks/BANK_ID/my/consents/IMPLICIT",
      "Create Consent (IMPLICIT)",
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
      postConsentImplicitJsonV310,
      consentJsonV310,
      List(
        AuthenticatedUserIsRequired,
        BankNotFound,
        InvalidJsonFormat,
        ConsentAllowedScaMethods,
        RolesAllowedInConsent,
        ViewsAllowedInConsent,
        ConsumerNotFoundByConsumerId,
        ConsumerIsDisabled,
        MissingPropsValueAtThisInstance,
        SmsServerNotResponding,
        InvalidConnectorResponse,
        UnknownError
      ),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(createConsentImplicit)
    )

    // ─── answerConsentChallenge (POST → 201) ─────────────────────────────────

    val answerConsentChallenge: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "banks" / _ / "consents" / consentIdStr / "challenge" =>
        EndpointHelpers.withUserAndBankAndBodyCreated[PostConsentChallengeJsonV310, Any](req) { (_, _, body, cc) =>
          for {
            consent <- Future(Consents.consentProvider.vend.checkAnswer(consentIdStr, body.answer)) map {
              i => connectorEmptyResponse(i, Some(cc))
            }
          } yield ConsentJsonV310(consent.consentId, consent.jsonWebToken, consent.status)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(answerConsentChallenge),
      "POST",
      "/banks/BANK_ID/consents/CONSENT_ID/challenge",
      "Answer Consent Challenge",
      s"""
      |
      |$generalObpConsentText
      |
      |
      |This endpoint is used to confirm a Consent previously created.
      |
      |The User must supply a code that was sent out of band (OOB) for example via an SMS.
      |
      |${userAuthenticationMessage(true)}
      |
      |""",
      PostConsentChallengeJsonV310(answer = "12345678"),
      ConsentChallengeJsonV310(
        consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
        jwt = "eyJhbGciOiJIUzI1NiJ9.eyJlbnRpdGxlbWVudHMiOltdLCJjcmVhdGVkQnlVc2VySWQiOiJhYjY1MzlhOS1iMTA1LTQ0ODktYTg4My0wYWQ4ZDZjNjE2NTciLCJzdWIiOiIyMWUxYzhjYy1mOTE4LTRlYWMtYjhlMy01ZTVlZWM2YjNiNGIiLCJhdWQiOiJlanpuazUwNWQxMzJyeW9tbmhieDFxbXRvaHVyYnNiYjBraWphanNrIiwibmJmIjoxNTUzNTU0ODk5LCJpc3MiOiJodHRwczpcL1wvd3d3Lm9wZW5iYW5rcHJvamVjdC5jb20iLCJleHAiOjE1NTM1NTg0OTksImlhdCI6MTU1MzU1NDg5OSwianRpIjoiMDlmODhkNWYtZWNlNi00Mzk4LThlOTktNjYxMWZhMWNkYmQ1Iiwidmlld3MiOlt7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAxIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifSx7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAyIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifV19.8cc7cBEf2NyQvJoukBCmDLT7LXYcuzTcSYLqSpbxLp4",
        status = "INITIATED"
      ),
      List(AuthenticatedUserIsRequired, BankNotFound, InvalidJsonFormat,
      InvalidConnectorResponse, UnknownError),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: Nil,
      None,
      http4sPartialFunction = Some(answerConsentChallenge)
    )

    // ─── getObpConnectorLoopback ─────────────────────────────────────────────

    val getObpConnectorLoopback: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ GET -> `prefixPath` / "connector" / "loopback" =>
        EndpointHelpers.executeAndRespond(req) { cc =>
          for {
            _ <- code.util.Helper.booleanToFuture(code.api.util.ErrorMessages.NotImplemented, failCode = 400, cc = Some(cc)) { false }
          } yield EmptyBody
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getObpConnectorLoopback),
      "GET",
      "/connector/loopback",
      "Get Connector Status (Loopback)",
      // Intentional drift from Lift's APIMethods310.scala source-of-truth.
      // Lift's description had userAuthenticationMessage(true) (auth required),
      // but Lift's handler uses anonymousAccess(cc) (no auth required). The
      // ResourceDoc constructor added $AuthenticatedUserIsRequired to errors,
      // setting needsAuthentication=true so middleware returned 401 instead of
      // letting the handler return 400 NotImplemented. See upstream commit
      // 14abed06c.
      s"""This endpoint makes a call to the Connector to check the backend transport is reachable. (Deprecated)
      |
      |${userAuthenticationMessage(false)}
      |
      |""",
      EmptyBody,
      obpApiLoopbackJson,
      List(UnknownError),
      List(apiTagApi, apiTagOAuth, apiTagOIDC),
      http4sPartialFunction = Some(getObpConnectorLoopback)
    )

    // ─── getMessageDocsSwagger ───────────────────────────────────────────────
    // Real routing is handled by Http4sResourceDocs (wildcard /obp/*/message-docs/{CONNECTOR}/swagger2.0
    // matched before v310Routes in Http4sApp). This stub val exists only so nameOf compiles
    // in downstream test files, and the ResourceDoc entry appears in /resource-docs/v3.1.0/obp.

    val getMessageDocsSwagger: HttpRoutes[IO] = HttpRoutes.empty

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(getMessageDocsSwagger),
      "GET",
      "/message-docs/CONNECTOR/swagger2.0",
      "Get Message Docs Swagger",
      """
        |This endpoint provides example message docs in swagger format.
        |It is only relavent for REST Connectors.
        |
        |This endpoint can be used by the developer building a REST Adapter that connects to the Core Banking System (CBS).
        |That is, the Adapter developer can use the Swagger surfaced here to build the REST APIs that the OBP REST connector will call to consume CBS services.
        |
        |i.e.:
        |
        |OBP API (Core OBP API code) -> OBP REST Connector (OBP REST Connector code) -> OBP REST Adapter (Adapter developer code) -> CBS (Main Frame)
        |
      """.stripMargin,
      EmptyBody,
      EmptyBody,
      List(UnknownError),
      List(apiTagMessageDoc, apiTagDocumentation, apiTagApi)
    )

    // ─── saveHistoricalTransaction (POST) ────────────────────────────────────

    val saveHistoricalTransaction: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case req @ POST -> `prefixPath` / "management" / "historical" / "transactions" =>
        EndpointHelpers.withUserAndBodyCreated[PostHistoricalTransactionJson, Any](req) { (user, body, cc) =>
          for {
            _ <- NewStyle.function.hasEntitlement("", user.userId, ApiRole.canCreateHistoricalTransaction, Some(cc))
            fromAccountPost = body.from
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidJsonFormat from object should only contain bank_id and account_id or counterparty_id in the post json body.",
              cc = Some(cc)) {
              (fromAccountPost.bank_id.isDefined && fromAccountPost.account_id.isDefined && fromAccountPost.counterparty_id.isEmpty) ||
                (fromAccountPost.bank_id.isEmpty && fromAccountPost.account_id.isEmpty && fromAccountPost.counterparty_id.isDefined)
            }
            (fromAccount, _) <-
              if (fromAccountPost.counterparty_id.isEmpty)
                for {
                  (_, _) <- NewStyle.function.getBank(BankId(fromAccountPost.bank_id.get), Some(cc))
                  (acc, _) <- NewStyle.function.checkBankAccountExists(
                    BankId(fromAccountPost.bank_id.get), AccountId(fromAccountPost.account_id.get), Some(cc))
                } yield (acc, Some(cc))
              else
                for {
                  (fromCp, _) <- NewStyle.function.getCounterpartyByCounterpartyId(
                    CounterpartyId(fromAccountPost.counterparty_id.get), Some(cc))
                  (acc, _) <- NewStyle.function.getBankAccountFromCounterparty(fromCp, false, Some(cc))
                } yield (acc, Some(cc))
            toAccountPost = body.to
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidJsonFormat to object should only contain bank_id and account_id or counterparty_id in the post json body.",
              cc = Some(cc)) {
              (toAccountPost.bank_id.isDefined && toAccountPost.account_id.isDefined && toAccountPost.counterparty_id.isEmpty) ||
                (toAccountPost.bank_id.isEmpty && toAccountPost.account_id.isEmpty && toAccountPost.counterparty_id.isDefined)
            }
            (toAccount, _) <-
              if (toAccountPost.counterparty_id.isEmpty)
                for {
                  (_, _) <- NewStyle.function.getBank(BankId(toAccountPost.bank_id.get), Some(cc))
                  (acc, _) <- NewStyle.function.checkBankAccountExists(
                    BankId(toAccountPost.bank_id.get), AccountId(toAccountPost.account_id.get), Some(cc))
                } yield (acc, Some(cc))
              else
                for {
                  (toCp, _) <- NewStyle.function.getCounterpartyByCounterpartyId(
                    CounterpartyId(toAccountPost.counterparty_id.get), Some(cc))
                  (acc, _) <- NewStyle.function.getBankAccountFromCounterparty(toCp, true, Some(cc))
                } yield (acc, Some(cc))
            amountNumber <- NewStyle.function.tryons(
              s"$InvalidNumber Current input is ${body.value.amount} ", 400, Some(cc)) {
              BigDecimal(body.value.amount)
            }
            _ <- code.util.Helper.booleanToFuture(
              s"$NotPositiveAmount Current input is: '$amountNumber'", cc = Some(cc)) {
              amountNumber > BigDecimal("0")
            }
            posted <- NewStyle.function.tryons(
              s"$InvalidDateFormat Current `posted` field is ${body.posted}. Please use this format ${DateWithSecondsFormat.toPattern}! ",
              400, Some(cc)) { new SimpleDateFormat(DateWithSeconds).parse(body.posted) }
            completed <- NewStyle.function.tryons(
              s"$InvalidDateFormat Current `completed` field is ${body.completed}. Please use this format ${DateWithSecondsFormat.toPattern}! ",
              400, Some(cc)) { new SimpleDateFormat(DateWithSeconds).parse(body.completed) }
            _ <- code.util.Helper.booleanToFuture(
              s"$InvalidISOCurrencyCode Current input is: '${body.value.currency}'", cc = Some(cc)) {
              isValidCurrencyISOCode(body.value.currency)
            }
            amountOfMoneyJson = AmountOfMoneyJsonV121(body.value.currency, body.value.amount)
            (transactionId, _) <- NewStyle.function.makeHistoricalPayment(
              fromAccount, toAccount, posted, completed,
              amountNumber, body.value.currency, body.description, body.`type`,
              body.charge_policy, Some(cc))
          } yield JSONFactory310.createPostHistoricalTransactionResponseJson(
            transactionId, fromAccountPost, toAccountPost,
            value = amountOfMoneyJson, description = body.description,
            posted, completed,
            transactionRequestType = body.`type`,
            chargePolicy = body.charge_policy)
        }
    }

    resourceDocs += ResourceDoc(
      implementedInApiVersion,
      nameOf(saveHistoricalTransaction),
      "POST",
      "/management/historical/transactions",
      "Save Historical Transactions",
      s"""
        |Import the historical transactions.
        |
        |The fields bank_id, account_id, counterparty_id in the json body are all optional ones.
        |It support transfer money from account to account, account to counterparty and counterparty to counterparty
        |Both bank_id + account_id and counterparty_id can identify the account, so OBP only need one of them to make the payment.
        |So:
        |When you need the account to account, just omit counterparty_id field.eg:
        |{
        |  "from": {
        |    "bank_id": "gh.29.uk",
        |    "account_id": "1ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        |  },
        |  "to": {
        |    "bank_id": "gh.29.uk",
        |    "account_id": "2ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        |  },
        |  "value": {
        |    "currency": "GBP",
        |    "amount": "10"
        |  },
        |  "description": "this is for work",
        |  "posted": "2017-09-19T02:31:05Z",
        |  "completed": "2017-09-19T02:31:05Z",
        |  "type": "SANDBOX_TAN",
        |  "charge_policy": "SHARED"
        |}
        |
        |When you need the counterparty to counterparty, need to omit bank_id and account_id field.eg:
        |{
        |  "from": {
        |    "counterparty_id": "f6392b7d-4218-45ea-b9a7-eaa71c0202f9"
        |  },
        |  "to": {
        |    "counterparty_id": "26392b7d-4218-45ea-b9a7-eaa71c0202f9"
        |  },
        |  "value": {
        |    "currency": "GBP",
        |    "amount": "10"
        |  },
        |  "description": "this is for work",
        |  "posted": "2017-09-19T02:31:05Z",
        |  "completed": "2017-09-19T02:31:05Z",
        |  "type": "SANDBOX_TAN",
        |  "charge_policy": "SHARED"
        |}
        |
        |or, you can counterparty to account
        |{
        |  "from": {
        |    "counterparty_id": "f6392b7d-4218-45ea-b9a7-eaa71c0202f9"
        |  },
        |  "to": {
        |    "bank_id": "gh.29.uk",
        |    "account_id": "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        |  },
        |  "value": {
        |    "currency": "GBP",
        |    "amount": "10"
        |  },
        |  "description": "this is for work",
        |  "posted": "2017-09-19T02:31:05Z",
        |  "completed": "2017-09-19T02:31:05Z",
        |  "type": "SANDBOX_TAN",
        |  "charge_policy": "SHARED"
        |}
        |
        |This call is experimental.
      """.stripMargin,
      postHistoricalTransactionJson,
      postHistoricalTransactionResponseJson,
      List(InvalidJsonFormat, BankNotFound, AccountNotFound,
      CounterpartyNotFoundByCounterpartyId, InvalidNumber, NotPositiveAmount,
      InvalidTransactionRequestCurrency, UnknownError),
      List(apiTagTransactionRequest),
      Some(List(canCreateHistoricalTransaction)),
      http4sPartialFunction = Some(saveHistoricalTransaction)
    )

    // ─── allRoutes ────────────────────────────────────────────────────────────

    private val allOwnRoutes: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      root.run(req)
        .orElse(getCheckbookOrders.run(req))
        .orElse(getStatusOfCreditCardOrder.run(req))
        .orElse(getTopAPIs.run(req))
        .orElse(getMetricsTopConsumers.run(req))
        .orElse(getFirehoseCustomers.run(req))
        .orElse(getBadLoginStatus.run(req))
        .orElse(getCallsLimit.run(req))
        .orElse(getConsumer.run(req))
        .orElse(getConsumersForCurrentUser.run(req))
        .orElse(getConsumers.run(req))
        .orElse(getAccountWebhooks.run(req))
        .orElse(config.run(req))
        .orElse(getAdapterInfo.run(req))
        .orElse(getRateLimitingInfo.run(req))
        .orElse(getCustomerByCustomerId.run(req))
        .orElse(getUserAuthContexts.run(req))
        .orElse(getTaxResidence.run(req))
        .orElse(getAllEntitlements.run(req))
        .orElse(getCustomerAddresses.run(req))
        .orElse(getProductAttribute.run(req))
        .orElse(getAccountApplications.run(req))
        .orElse(getAccountApplication.run(req))
        .orElse(getMeetings.run(req))
        .orElse(getMeeting.run(req))
        .orElse(getServerJWK.run(req))
        .orElse(getOAuth2ServerJWKsURIs.run(req))
        .orElse(getMethodRoutings.run(req))
        .orElse(getSystemView.run(req))
        .orElse(getCardsForBank.run(req))
        .orElse(getCardForBank.run(req))
        .orElse(getBankAccountsBalances.run(req))
        .orElse(checkFundsAvailable.run(req))
        .orElse(getTransactionByIdForBankAccount.run(req))
        .orElse(getTransactionRequests.run(req))
        .orElse(getProduct.run(req))
        .orElse(getProductTree.run(req))
        .orElse(getProducts.run(req))
        .orElse(getProductCollection.run(req))
        .orElse(getConsents.run(req))
        .orElse(getPrivateAccountByIdFull.run(req))
        .orElse(getWebUiProps.run(req))
        .orElse(deleteUserAuthContexts.run(req))
        .orElse(deleteUserAuthContextById.run(req))
        .orElse(deleteTaxResidence.run(req))
        .orElse(deleteCustomerAddress.run(req))
        .orElse(deleteProductAttribute.run(req))
        .orElse(deleteBranch.run(req))
        .orElse(deleteSystemView.run(req))
        .orElse(deleteMethodRouting.run(req))
        .orElse(deleteCardForBank.run(req))
        .orElse(deleteWebUiProps.run(req))
        .orElse(revokeConsent.run(req))
        .orElse(createTaxResidence.run(req))
        .orElse(createCustomerAddress.run(req))
        .orElse(updateCustomerAddress.run(req))
        .orElse(createUserAuthContext.run(req))
        .orElse(createProductAttribute.run(req))
        .orElse(createAccountWebhook.run(req))
        .orElse(unlockUser.run(req))
        .orElse(callsLimit.run(req))
        .orElse(enableDisableAccountWebhook.run(req))
        .orElse(enableDisableConsumers.run(req))
        .orElse(updateSystemView.run(req))
        .orElse(updateProductAttribute.run(req))
        .orElse(updateCustomerEmail.run(req))
        .orElse(updateCustomerNumber.run(req))
        .orElse(updateCustomerMobileNumber.run(req))
        .orElse(updateCustomerIdentity.run(req))
        .orElse(updateCustomerCreditLimit.run(req))
        .orElse(updateCustomerCreditRatingAndSource.run(req))
        .orElse(updateCustomerBranch.run(req))
        .orElse(updateCustomerData.run(req))
        .orElse(updateAccountApplicationStatus.run(req))
        .orElse(createCustomer.run(req))
        .orElse(getCustomerByCustomerNumber.run(req))
        .orElse(createAccountApplication.run(req))
        .orElse(createAccountAttribute.run(req))
        .orElse(updateAccountAttribute.run(req))
        .orElse(createMeeting.run(req))
        .orElse(createSystemView.run(req))
        .orElse(createProductCollection.run(req))
        .orElse(addCardForBank.run(req))
        .orElse(updatedCardForBank.run(req))
        .orElse(createCardAttribute.run(req))
        .orElse(updateCardAttribute.run(req))
        .orElse(createWebUiProps.run(req))
        .orElse(createUserAuthContextUpdateRequest.run(req))
        .orElse(answerUserAuthContextUpdateChallenge.run(req))
        .orElse(refreshUser.run(req))
        .orElse(createProduct.run(req))
        .orElse(createMethodRouting.run(req))
        .orElse(updateMethodRouting.run(req))
        .orElse(updateAccount.run(req))
        .orElse(createAccount.run(req))
        .orElse(createConsent.run(req))
        .orElse(answerConsentChallenge.run(req))
        .orElse(saveHistoricalTransaction.run(req))
        .orElse(getObpConnectorLoopback.run(req))
    }

    val allRoutesWithMiddleware: HttpRoutes[IO] = ResourceDocMiddleware.apply(resourceDocs)(IdempotencyMiddleware(allOwnRoutes))

    // ─── path-rewriting bridge: /obp/v3.1.0/… → /obp/v3.0.0/… ──────────────

    val v310ToV300Bridge: HttpRoutes[IO] = Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      val rawPath = req.uri.path.renderString
      if (rawPath.startsWith("/obp/v3.1.0/")) {
        val rewritten    = rawPath.replaceFirst("/obp/v3\\.1\\.0/", "/obp/v3.0.0/")
        val newUri       = req.uri.withPath(Uri.Path.unsafeFromString(rewritten))
        val rewrittenReq = req.withUri(newUri)
        code.api.v3_0_0.Http4s300.wrappedRoutesV300Services.run(rewrittenReq)
      } else {
        OptionT.none[IO, Response[IO]]
      }
    }
  }

  val wrappedRoutesV310Services: HttpRoutes[IO] =
    Kleisli[HttpF, Request[IO], Response[IO]] { req =>
      Implementations3_1_0.allRoutesWithMiddleware.run(req)
        .orElse(Implementations3_1_0.v310ToV300Bridge.run(req))
    }
}
