package code.api.v6_0_0

import code.api.{APIFailureNewStyle, DirectLogin, ObpApiFailure}
import code.api.v6_0_0.JSONFactory600
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole.{CanCreateEntitlementAtOneBank, CanReadDynamicResourceDocsAtOneBank, canCreateBank, canDeleteRateLimiting, canReadCallLimits, canSetCallLimits}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{$UserNotLoggedIn, InvalidDateFormat, InvalidJsonFormat, UnknownError, _}
import code.api.util.FutureUtil.EndpointContext
import code.api.util.{APIUtil, ErrorMessages, NewStyle, RateLimitingUtil}
import code.api.util.NewStyle.HttpCode
import code.api.v5_0_0.{JSONFactory500, PostBankJson500}
import code.api.v6_0_0.JSONFactory600.{createActiveCallLimitsJsonV600, createCallLimitJsonV600, createCurrentUsageJson}
import code.bankconnectors.LocalMappedConnectorInternal
import code.bankconnectors.LocalMappedConnectorInternal._
import code.entitlement.Entitlement
import code.ratelimiting.RateLimitingDI
import code.util.Helper
import code.util.Helper.SILENCE_IS_GOLDEN
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.rest.RestHelper

import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future


trait APIMethods600 {
  self: RestHelper =>

  val Implementations6_0_0 = new Implementations600()

  class Implementations600 {

    val implementedInApiVersion: ScannedApiVersion = ApiVersion.v6_0_0

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()
    def resourceDocs = staticResourceDocs 

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)


    staticResourceDocs += ResourceDoc(
      getCurrentCallsLimit,
      implementedInApiVersion,
      nameOf(getCurrentCallsLimit),
      "GET",
      "/management/consumers/CONSUMER_ID/consumer/current-usage",
      "Get Call Limits for a Consumer Usage",
      s"""
         |Get Call Limits for a Consumer Usage.
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      redisCallLimitJson,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        InvalidConsumerId,
        ConsumerNotFoundByConsumerId,
        UserHasMissingRoles,
        UpdateConsumerError,
        UnknownError
      ),
      List(apiTagConsumer),
      Some(List(canReadCallLimits)))


    lazy val getCurrentCallsLimit: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "current-usage" :: Nil JsonGet _ =>
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, cc.callContext)
            currentUsage <- Future(RateLimitingUtil.consumerRateLimitState(consumerId).toList)
          } yield {
            (createCurrentUsageJson(currentUsage), HttpCode.`200`(cc.callContext))
          }
    }


    staticResourceDocs += ResourceDoc(
      createCallLimits,
      implementedInApiVersion,
      nameOf(createCallLimits),
      "POST",
      "/management/consumers/CONSUMER_ID/consumer/call-limits",
      "Create Call Limits for a Consumer",
      s"""
         |Create Call Limits for a Consumer
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      callLimitPostJsonV600,
      callLimitJsonV600,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        InvalidConsumerId,
        ConsumerNotFoundByConsumerId,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConsumer),
      Some(List(canSetCallLimits)))


    lazy val createCallLimits: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "call-limits" :: Nil JsonPost json -> _ =>
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canSetCallLimits, callContext)
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CallLimitPostJsonV600 ", 400, callContext) {
              json.extract[CallLimitPostJsonV600]
            }
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
            rateLimiting <- RateLimitingDI.rateLimiting.vend.createOrUpdateConsumerCallLimits(
              consumerId,
              postJson.from_date,
              postJson.to_date,
              postJson.api_version,
              postJson.api_name,
              postJson.bank_id,
              Some(postJson.per_second_call_limit),
              Some(postJson.per_minute_call_limit),
              Some(postJson.per_hour_call_limit),
              Some(postJson.per_day_call_limit),
              Some(postJson.per_week_call_limit),
              Some(postJson.per_month_call_limit)
            )
          } yield {
            rateLimiting match {
              case Full(rateLimitingObj) => (createCallLimitJsonV600(rateLimitingObj), HttpCode.`201`(callContext))
              case _ => (UnknownError, HttpCode.`400`(callContext))
            }
          }
    }


    staticResourceDocs += ResourceDoc(
      deleteCallLimits,
      implementedInApiVersion,
      nameOf(deleteCallLimits),
      "DELETE",
      "/management/consumers/CONSUMER_ID/consumer/call-limits/RATE_LIMITING_ID",
      "Delete Call Limit by Rate Limiting ID",
      s"""
         |Delete a specific Call Limit by Rate Limiting ID
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        InvalidConsumerId,
        ConsumerNotFoundByConsumerId,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConsumer),
      Some(List(canDeleteRateLimiting)))


    lazy val deleteCallLimits: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "call-limits" :: rateLimitingId :: Nil JsonDelete _ =>
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteRateLimiting, callContext)
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
            rateLimiting <- RateLimitingDI.rateLimiting.vend.getByRateLimitingId(rateLimitingId)
            _ <- rateLimiting match {
              case Full(rl) if rl.consumerId == consumerId => 
                Future.successful(Full(rl))
              case Full(_) => 
                Future.successful(ObpApiFailure(s"Rate limiting ID $rateLimitingId does not belong to consumer $consumerId", 400, callContext))
              case _ => 
                Future.successful(ObpApiFailure(s"Rate limiting ID $rateLimitingId not found", 404, callContext))
            }
            deleteResult <- RateLimitingDI.rateLimiting.vend.deleteByRateLimitingId(rateLimitingId)
          } yield {
            deleteResult match {
              case Full(true) => (EmptyBody, HttpCode.`204`(callContext))
              case _ => (UnknownError, HttpCode.`400`(callContext))
            }
          }
    }


    staticResourceDocs += ResourceDoc(
      getActiveCallLimitsAtDate,
      implementedInApiVersion,
      nameOf(getActiveCallLimitsAtDate),
      "GET",
      "/management/consumers/CONSUMER_ID/consumer/call-limits/active-at-date/DATE",
      "Get Active Call Limits at Date",
      s"""
         |Get the sum of call limits at a certain date time. This returns a SUM of all the records that span that time.
         |
         |Date format: YYYY-MM-DDTHH:MM:SSZ (e.g. 1099-12-31T23:00:00Z)
         |
         |${userAuthenticationMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      activeCallLimitsJsonV600,
      List(
        $UserNotLoggedIn,
        InvalidConsumerId,
        ConsumerNotFoundByConsumerId,
        UserHasMissingRoles,
        InvalidDateFormat,
        UnknownError
      ),
      List(apiTagConsumer),
      Some(List(canReadCallLimits)))


    lazy val getActiveCallLimitsAtDate: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "call-limits" :: "active-at-date" :: dateString :: Nil JsonGet _ =>
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canReadCallLimits, callContext)
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
            date <- NewStyle.function.tryons(s"$InvalidDateFormat Current date format is: $dateString. Please use this format: YYYY-MM-DDTHH:MM:SSZ (e.g. 1099-12-31T23:00:00Z)", 400, callContext) {
              val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
              format.parse(dateString)
            }
            activeCallLimits <- RateLimitingDI.rateLimiting.vend.getActiveCallLimitsByConsumerIdAtDate(consumerId, date)
          } yield {
            (createActiveCallLimitsJsonV600(activeCallLimits, date), HttpCode.`200`(callContext))
          }
    }


    staticResourceDocs += ResourceDoc(
      getCurrentUser,
      implementedInApiVersion,
      nameOf(getCurrentUser), // TODO can we get this string from the val two lines above?
      "GET",
      "/users/current",
      "Get User (Current)",
      s"""Get the logged in user
         |
         |${userAuthenticationMessage(true)}
      """.stripMargin,
      EmptyBody,
      userJsonV300,
      List(UserNotLoggedIn, UnknownError),
      List(apiTagUser))

    lazy val getCurrentUser: OBPEndpoint = {
      case "users" :: "current" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            entitlements <- NewStyle.function.getEntitlementsByUserId(u.userId, callContext)
          } yield {
            val permissions: Option[Permission] = Views.views.vend.getPermissionForUser(u).toOption
            val currentUser = UserV600(u, entitlements, permissions)
            val onBehalfOfUser = if(cc.onBehalfOfUser.isDefined) {
              val user = cc.onBehalfOfUser.toOption.get
              val entitlements = Entitlement.entitlement.vend.getEntitlementsByUserId(user.userId).headOption.toList.flatten
              val permissions: Option[Permission] = Views.views.vend.getPermissionForUser(user).toOption
              Some(UserV600(user, entitlements, permissions))
            } else {
              None
            }
            (JSONFactory600.createUserInfoJSON(currentUser, onBehalfOfUser), HttpCode.`200`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      createTransactionRequestCardano,
      implementedInApiVersion,
      nameOf(createTransactionRequestCardano),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/CARDANO/transaction-requests",
      "Create Transaction Request (CARDANO)",
      s"""
         |
         |For sandbox mode, it will use the Cardano Preprod Network.
         |The accountId can be the wallet_id for now, as it uses cardano-wallet in the backend.
         |
         |${transactionRequestGeneralText}
         |
       """.stripMargin,
      transactionRequestBodyCardanoJsonV600,
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
    )
    
    lazy val createTransactionRequestCardano: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "CARDANO" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("CARDANO")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }

    staticResourceDocs += ResourceDoc(
      createTransactionRequestEthereumeSendTransaction,
      implementedInApiVersion,
      nameOf(createTransactionRequestEthereumeSendTransaction),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/ETH_SEND_TRANSACTION/transaction-requests",
      "Create Transaction Request (ETH_SEND_TRANSACTION)",
      s"""
         |
         |Send ETH via Ethereum JSON-RPC.
         |AccountId should hold the 0x address for now.
         |
         |${transactionRequestGeneralText}
         |
       """.stripMargin,
      transactionRequestBodyEthereumJsonV600,
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
    )

    lazy val createTransactionRequestEthereumeSendTransaction: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "ETH_SEND_TRANSACTION" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("ETH_SEND_TRANSACTION")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }
    staticResourceDocs += ResourceDoc(
      createTransactionRequestEthSendRawTransaction,
      implementedInApiVersion,
      nameOf(createTransactionRequestEthSendRawTransaction),
      "POST",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/owner/transaction-request-types/ETH_SEND_RAW_TRANSACTION/transaction-requests",
      "CREATE TRANSACTION REQUEST (ETH_SEND_RAW_TRANSACTION )",
      s"""
         |
         |Send ETH via Ethereum JSON-RPC.
         |AccountId should hold the 0x address for now.
         |
         |${transactionRequestGeneralText}
         |
       """.stripMargin,
      transactionRequestBodyEthSendRawTransactionJsonV600,
      transactionRequestWithChargeJSON400,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        $BankAccountNotFound,
        InsufficientAuthorisationToCreateTransactionRequest,
        InvalidTransactionRequestType,
        InvalidJsonFormat,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        TransactionDisabled,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagPsd2)
    )

    lazy val createTransactionRequestEthSendRawTransaction: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-request-types" ::
        "ETH_SEND_RAW_TRANSACTION" :: "transaction-requests" :: Nil JsonPost json -> _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          val transactionRequestType = TransactionRequestType("ETH_SEND_RAW_TRANSACTION")
          LocalMappedConnectorInternal.createTransactionRequest(bankId, accountId, viewId , transactionRequestType, json)
    }


    staticResourceDocs += ResourceDoc(
      createBank,
      implementedInApiVersion,
      "createBank",
      "POST",
      "/banks",
      "Create Bank",
      s"""Create a new bank (Authenticated access).
         |
         |The user creating this will be automatically assigned the Role CanCreateEntitlementAtOneBank.
         |Thus the User can manage the bank they create and assign Roles to other Users.
         |
         |Only SANDBOX mode
         |The settlement accounts are created specified by the bank in the POST body.
         |Name and account id are created in accordance to the next rules:
         |  - Incoming account (name: Default incoming settlement account, Account ID: OBP_DEFAULT_INCOMING_ACCOUNT_ID, currency: EUR)
         |  - Outgoing account (name: Default outgoing settlement account, Account ID: OBP_DEFAULT_OUTGOING_ACCOUNT_ID, currency: EUR)
         |
         |""",
      postBankJson600,
      bankJson500,
      List(
        InvalidJsonFormat,
        $UserNotLoggedIn,
        InsufficientAuthorisationToCreateBank,
        UnknownError
      ),
      List(apiTagBank),
      Some(List(canCreateBank))
    )

    lazy val createBank: OBPEndpoint = {
      case "banks" :: Nil JsonPost json -> _ => {
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostBankJson600 "
          for {
            postJson <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostBankJson600]
            }

            checkShortStringValue = APIUtil.checkOptionalShortString(postJson.bank_id)
            _ <- Helper.booleanToFuture(failMsg = s"$checkShortStringValue.", cc = cc.callContext) {
              checkShortStringValue == SILENCE_IS_GOLDEN
            }

            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.InvalidConsumerCredentials, cc = cc.callContext) {
              cc.callContext.map(_.consumer.isDefined == true).isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat Min length of BANK_ID should be greater than 3 characters.", cc = cc.callContext) {
              postJson.bank_id.length > 3
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat BANK_ID can not contain space characters", cc = cc.callContext) {
              !postJson.bank_id.contains(" ")
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat BANK_ID can not contain `::::` characters", cc = cc.callContext) {
              !`checkIfContains::::`(postJson.bank_id)
            }
            (banks, callContext) <- NewStyle.function.getBanks(cc.callContext)
            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.bankIdAlreadyExists, cc = cc.callContext) {
              !banks.exists { b => postJson.bank_id.contains(b.bankId.value) }
            }
            (success, callContext) <- NewStyle.function.createOrUpdateBank(
              postJson.bank_id,
              postJson.full_name.getOrElse(""),
              postJson.bank_code,
              postJson.logo.getOrElse(""),
              postJson.website.getOrElse(""),
              postJson.bank_routings.getOrElse(Nil).find(_.scheme == "BIC").map(_.address).getOrElse(""),
              "",
              postJson.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.scheme).getOrElse(""),
              postJson.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.address).getOrElse(""),
              callContext
            )
            entitlements <- NewStyle.function.getEntitlementsByUserId(cc.userId, callContext)
            entitlementsByBank = entitlements.filter(_.bankId == postJson.bank_id)
            _ <- entitlementsByBank.exists(_.roleName == CanCreateEntitlementAtOneBank.toString()) match {
              case true =>
                // Already has entitlement
                Future()
              case false =>
                Future(Entitlement.entitlement.vend.addEntitlement(postJson.bank_id, cc.userId, CanCreateEntitlementAtOneBank.toString()))
            }
            _ <- entitlementsByBank.exists(_.roleName == CanReadDynamicResourceDocsAtOneBank.toString()) match {
              case true =>
                // Already has entitlement
                Future()
              case false =>
                Future(Entitlement.entitlement.vend.addEntitlement(postJson.bank_id, cc.userId, CanReadDynamicResourceDocsAtOneBank.toString()))
            }
          } yield {
            (JSONFactory500.createBankJSON500(success), HttpCode.`201`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      directLoginEndpoint,
      implementedInApiVersion,
      nameOf(directLoginEndpoint),
      "POST",
      "/my/logins/direct",
      "Direct Login",
      s"""This endpoint allows users to create a DirectLogin token to access the API.
         |
         |DirectLogin is a simple authentication flow. You POST your credentials (username, password, and consumer key) 
         |to the DirectLogin endpoint and receive a token in return.
         |
         |This is an alias to the legacy DirectLogin endpoint that includes the standard API versioning prefix.
         |
         |This endpoint requires the following headers:
         |- DirectLogin: username=YOUR_USERNAME, password=YOUR_PASSWORD, consumer_key=YOUR_CONSUMER_KEY
         |OR
         |- Authorization: DirectLogin username=YOUR_USERNAME, password=YOUR_PASSWORD, consumer_key=YOUR_CONSUMER_KEY
         |
         |Example header:
         |DirectLogin: username=YOUR_USERNAME, password=YOUR_PASSWORD, consumer_key=GET-YOUR-OWN-API-KEY-FROM-THE-OBP
         |
         |The token returned can be used as a bearer token in subsequent API calls.
         |
         |""".stripMargin,
      EmptyBody,
      JSONFactory600.createTokenJSON("DirectLoginToken{eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwczpcL1wvd3d3Lm9wZW5iYW5rcHJvamVjdC5jb20iLCJpYXQiOjE0NTU4OTQyNzYsImV4cCI6MTQ1NTg5Nzg3NiwiYXVkIjoib2JwLWFwaSIsInN1YiI6IjA2Zjc0YjUwLTA5OGYtNDYwNi1hOGNjLTBjNDc5MjAyNmI5ZCIsImNvbnN1bWVyX2tleSI6IjYwNGY3ZTAyNGQ5MWU2MzMwNGMzOGM0YzRmZjc0MjMwZGU5NDk4NTEwNjgxZWNjM2Q5MzViNWQ5MGEwOTI3ODciLCJyb2xlIjoiY2FuX2FjY2Vzc19hcGkifQ.f8xHvXP5fDxo5-LlfTj1OQS9oqHNZfFd7N-WkV2o4Cc}"),
      List(
        InvalidDirectLoginParameters,
        InvalidLoginCredentials,
        InvalidConsumerCredentials,
        UnknownError
      ),
      List(apiTagUser),
      Some(List()))


    lazy val directLoginEndpoint: OBPEndpoint = {
      case "my" :: "logins" :: "direct" :: Nil JsonPost _ =>
        cc => implicit val ec = EndpointContext(Some(cc))
          for {
            (httpCode: Int, message: String, userId: Long) <- DirectLogin.createTokenFuture(DirectLogin.getAllParameters)
            _ <- Future { DirectLogin.grantEntitlementsToUseDynamicEndpointsInSpacesInDirectLogin(userId) }
          } yield {
            if (httpCode == 200) {
              (JSONFactory600.createTokenJSON(message), HttpCode.`201`(cc.callContext))
            } else {
              unboxFullOrFail(Empty, None, message, httpCode)
            }
          }
    }
    
  }
}



object APIMethods600 extends RestHelper with APIMethods600 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations6_0_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

