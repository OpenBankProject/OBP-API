package code.api.v3_1_0

import java.text.SimpleDateFormat
import java.util.UUID
import java.util.regex.Pattern

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.ResourceDocs1_4_0.{MessageDocsSwaggerDefinitions, ResourceDocsAPIMethodsUtil, SwaggerDefinitionsJSON, SwaggerJSONFactory}
import code.api.util.APIUtil.{getWebUIPropsPairs, _}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{BankAccountNotFound, _}
import code.api.util.ExampleValue._
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.v1_2_1.{JSONFactory, RateLimiting}
import code.api.v2_0_0.CreateMeetingJson
import code.api.v2_1_0._
import code.api.v2_2_0.{CreateAccountJSONV220, JSONFactory220}
import code.api.v3_0_0.JSONFactory300
import code.api.v3_0_0.JSONFactory300.createAdapterInfoJson
import code.api.v3_1_0.JSONFactory310._
import code.bankconnectors.rest.RestConnector_vMar2019
import code.bankconnectors.{Connector, LocalMappedConnector}
import code.consent.{ConsentStatus, Consents}
import code.consumer.Consumers
import code.context.UserAuthContextUpdateProvider
import code.entitlement.Entitlement
import code.kafka.KafkaHelper
import code.loginattempts.LoginAttempt
import code.methodrouting.{MethodRouting, MethodRoutingCommons, MethodRoutingParam, MethodRoutingT}
import code.metrics.APIMetrics
import code.model._
import code.model.dataAccess.{AuthUser, BankAccountCreation}
import code.ratelimiting.RateLimitingDI
import code.userlocks.{UserLocks, UserLocksProvider}
import code.users.Users
import code.util.Helper
import code.views.Views
import code.webhook.AccountWebhook
import code.webuiprops.{MappedWebUiPropsProvider, WebUiPropsCommons}
import com.github.dwickern.macros.NameOf.nameOf
import com.nexmo.client.NexmoClient
import com.nexmo.client.sms.messages.TextMessage
import com.openbankproject.commons.model.enums.{AccountAttributeType, CardAttributeType, ProductAttributeType, StrongCustomerAuthentication}
import com.openbankproject.commons.model.{CreditLimit, Product, _}
import com.openbankproject.commons.util.{ApiVersion, ReflectUtils}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.S
import net.liftweb.http.provider.HTTPParam
import net.liftweb.http.rest.RestHelper
import net.liftweb.json._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Mailer.{From, PlainMailBodyType, Subject, To}
import net.liftweb.util.{Helpers, Mailer, Props}
import org.apache.commons.lang3.{StringUtils, Validate}

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto.GetProductsParam

import scala.concurrent.Future
import scala.util.Random

trait APIMethods310 {
  self: RestHelper =>

  val Implementations3_1_0 = new Implementations310() 
  // note, because RestHelper have a impicit Formats, it is not correct for OBP, so here override it
  protected implicit override abstract def formats: Formats = CustomJsonFormats.formats

  class Implementations310 {

    val implementedInApiVersion = ApiVersion.v3_1_0

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)

    resourceDocs += ResourceDoc(
      getCheckbookOrders,
      implementedInApiVersion,
      nameOf(getCheckbookOrders),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/checkbook/orders",
      "Get Checkbook orders",
      s"""${mockedDataText(false)}Get all checkbook orders""",
      emptyObjectJson,
      checkbookOrdersJson,
      List(
        UserNotLoggedIn,
        BankNotFound,
        BankAccountNotFound,
        InvalidConnectorResponseForGetCheckbookOrdersFuture,
        UnknownError
      ),
      apiTagAccount :: apiTagNewStyle :: Nil)

    lazy val getCheckbookOrders : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "checkbook"  :: "orders" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)

            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)

            view <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext) 
            
            (checkbookOrders, callContext)<- Connector.connector.vend.getCheckbookOrders(bankId.value,accountId.value, callContext) map {
              unboxFullOrFail(_, callContext, InvalidConnectorResponseForGetCheckbookOrdersFuture)
            }
          } yield
           (JSONFactory310.createCheckbookOrdersJson(checkbookOrders), HttpCode.`200`(callContext))
      }
    }
    
    resourceDocs += ResourceDoc(
      getStatusOfCreditCardOrder,
      implementedInApiVersion,
      nameOf(getStatusOfCreditCardOrder),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/credit_cards/orders",
      "Get status of Credit Card order ",
      s"""${mockedDataText(false)}Get status of Credit Card orders
        |Get all orders
        |""",
      emptyObjectJson,
      creditCardOrderStatusResponseJson,
      List(
        UserNotLoggedIn,
        BankNotFound,
        BankAccountNotFound,
        InvalidConnectorResponseForGetStatusOfCreditCardOrderFuture,
        UnknownError
      ),
      apiTagCard :: apiTagNewStyle :: Nil)

    lazy val getStatusOfCreditCardOrder : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "credit_cards"  :: "orders" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)

            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)

            view <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext) 
            
            //TODO need error handling here
            (checkbookOrders,callContext) <- Connector.connector.vend.getStatusOfCreditCardOrder(bankId.value,accountId.value, callContext) map {
              unboxFullOrFail(_, callContext, InvalidConnectorResponseForGetStatusOfCreditCardOrderFuture)
            }
            
          } yield
           (JSONFactory310.createStatisOfCreditCardJson(checkbookOrders), HttpCode.`200`(callContext))
      }
    }
    
    resourceDocs += ResourceDoc(
      createCreditLimitRequest,
      implementedInApiVersion,
      nameOf(createCreditLimitRequest),
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_ID/credit_limit/requests",
      "Create Credit Limit Order Request",
      s"""${mockedDataText(true)}
        |Create credit limit order request
        |""",
      creditLimitRequestJson,
      creditLimitOrderResponseJson,
      List(UnknownError),
      apiTagCustomer :: apiTagNewStyle :: Nil)

    lazy val createCreditLimitRequest : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: CustomerId(customerId) :: "credit_limit"  :: "requests" :: Nil JsonPost json -> _  => {
        cc =>
//          val a: Future[(ChecksOrderStatusResponseDetails, Some[CallContext])] = for {
//            banksBox <- Connector.connector.vend.getBanksFuture()
//            banks <- unboxFullAndWrapIntoFuture{ banksBox }
//          } yield
          for { 
            (_, callContext) <- anonymousAccess(cc)
          } yield { 
            (JSONFactory310.createCreditLimitOrderResponseJson(), HttpCode.`201`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getCreditLimitRequests,
      implementedInApiVersion,
      nameOf(getCreditLimitRequests),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/credit_limit/requests",
      "Get Credit Limit Order Requests ",
      s"""${mockedDataText(true)}
        |Get Credit Limit Order Requests 
        |""",
      emptyObjectJson,
      creditLimitOrderJson,
      List(UnknownError),
      apiTagCustomer :: apiTagNewStyle :: Nil)

    lazy val getCreditLimitRequests : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: CustomerId(customerId) :: "credit_limit"  :: "requests" :: Nil JsonGet req => {
        cc =>
//          val a: Future[(ChecksOrderStatusResponseDetails, Some[CallContext])] = for {
//            banksBox <- Connector.connector.vend.getBanksFuture()
//            banks <- unboxFullAndWrapIntoFuture{ banksBox }
//          } yield
           for { 
             (_, callContext) <- anonymousAccess(cc)
           } yield {
             (JSONFactory310.getCreditLimitOrderResponseJson(), HttpCode.`200`(callContext))
           }
      }
    }

    resourceDocs += ResourceDoc(
      getCreditLimitRequestByRequestId,
      implementedInApiVersion,
      nameOf(getCreditLimitRequestByRequestId),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/credit_limit/requests/REQUEST_ID",
      "Get Credit Limit Order Request By Request Id",
      s"""${mockedDataText(true)}
        Get Credit Limit Order Request By Request Id
        |""",
      emptyObjectJson,
      creditLimitOrderJson,
      List(UnknownError),
      apiTagCustomer :: apiTagNewStyle :: Nil)

    lazy val getCreditLimitRequestByRequestId : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: CustomerId(customerId) :: "credit_limit"  :: "requests" :: requestId :: Nil JsonGet req => {
        cc =>
//          val a: Future[(ChecksOrderStatusResponseDetails, Some[CallContext])] = for {
//            banksBox <- Connector.connector.vend.getBanksFuture()
//            banks <- unboxFullAndWrapIntoFuture{ banksBox }
//          } yield
          for {
            (_, callContext) <- anonymousAccess(cc)
          } yield {
            (JSONFactory310.getCreditLimitOrderByRequestIdResponseJson(), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getTopAPIs,
      implementedInApiVersion,
      nameOf(getTopAPIs),
      "GET",
      "/management/metrics/top-apis",
      "Get Top APIs",
      s"""Get metrics about the most popular APIs. e.g.: total count, response time (in ms), etc.
        |
        |Should be able to filter on the following fields
        |
        |eg: /management/metrics/top-apis?from_date=$DefaultFromDateString&to_date=$DefaultToDateString&consumer_id=5
        |&user_id=66214b8e-259e-44ad-8868-3eb47be70646&implemented_by_partial_function=getTransactionsForBankAccount
        |&implemented_in_version=v3.0.0&url=/obp/v3.0.0/banks/gh.29.uk/accounts/8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0/owner/transactions
        |&verb=GET&anon=false&app_name=MapperPostman
        |&exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
        |
        |1 from_date (defaults to the one year ago): eg:from_date=$DefaultFromDateString
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
        |${authenticationRequiredMessage(true)}
        |
      """.stripMargin,
      emptyObjectJson,
      topApisJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidFilterParameterFormat,
        GetTopApisError,
        UnknownError
      ),
      apiTagMetric :: apiTagNewStyle :: Nil,
      Some(List(canReadMetrics))
    )

    lazy val getTopAPIs : OBPEndpoint = {
      case "management" :: "metrics" :: "top-apis" :: Nil JsonGet req => {
        cc =>
          for {
            
            (Full(u), callContext) <- authenticatedAccess(cc)

            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canReadMetrics, callContext)
            
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
              
            obpQueryParams <- createQueriesByHttpParamsFuture(httpParams) map {
              unboxFullOrFail(_, callContext, InvalidFilterParameterFormat)
            }
            
            toApis <- APIMetrics.apiMetrics.vend.getTopApisFuture(obpQueryParams) map {
                unboxFullOrFail(_, callContext, GetTopApisError)
            }
          } yield
           (JSONFactory310.createTopApisJson(toApis), HttpCode.`200`(callContext))
      }
    }
    
    resourceDocs += ResourceDoc(
      getMetricsTopConsumers,
      implementedInApiVersion,
      nameOf(getMetricsTopConsumers),
      "GET",
      "/management/metrics/top-consumers",
      "Get Top Consumers",
      s"""Get metrics about the top consumers of the API usage e.g. total count, consumer_id and app_name.
        |
        |Should be able to filter on the following fields
        |
        |e.g.: /management/metrics/top-consumers?from_date=$DefaultFromDateString&to_date=$DefaultToDateString&consumer_id=5
        |&user_id=66214b8e-259e-44ad-8868-3eb47be70646&implemented_by_partial_function=getTransactionsForBankAccount
        |&implemented_in_version=v3.0.0&url=/obp/v3.0.0/banks/gh.29.uk/accounts/8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0/owner/transactions
        |&verb=GET&anon=false&app_name=MapperPostman
        |&exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
        |&limit=100
        |
        |1 from_date (defaults to the one year ago): eg:from_date=$DefaultFromDateString
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
        |${authenticationRequiredMessage(true)}
        |
      """.stripMargin,
      emptyObjectJson,
      topConsumersJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidFilterParameterFormat,
        GetMetricsTopConsumersError,
        UnknownError
      ),
      apiTagMetric :: apiTagNewStyle :: Nil,
      Some(List(canReadMetrics))
    )

    lazy val getMetricsTopConsumers : OBPEndpoint = {
      case "management" :: "metrics" :: "top-consumers" :: Nil JsonGet req => {
        cc =>
          for {
            
            (Full(u), callContext) <- authenticatedAccess(cc)

            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canReadMetrics, callContext)
            
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
              
            obpQueryParams <- createQueriesByHttpParamsFuture(httpParams) map {
                unboxFullOrFail(_, callContext, InvalidFilterParameterFormat)
            }
            
            topConsumers <- APIMetrics.apiMetrics.vend.getTopConsumersFuture(obpQueryParams) map {
              unboxFullOrFail(_, callContext, GetMetricsTopConsumersError)
            }
            
          } yield
           (JSONFactory310.createTopConsumersJson(topConsumers), HttpCode.`200`(callContext))
      }
    }










    resourceDocs += ResourceDoc(
      getFirehoseCustomers,
      implementedInApiVersion,
      nameOf(getFirehoseCustomers),
      "GET",
      "/banks/BANK_ID/firehose/customers",
      "Get Firehose Customers",
      s"""
         |Get Customers that has a firehose View.
         |
         |Allows bulk access to customers.
         |User must have the CanUseFirehoseAtAnyBank Role
         |
         |${urlParametersDocument(true, true)}
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      emptyObjectJson,
      customerJSONs,
      List(UserNotLoggedIn, CustomerFirehoseNotAllowedOnThisInstance, UserHasMissingRoles, UnknownError),
      List(apiTagCustomer, apiTagFirehoseData, apiTagNewStyle),
      Some(List(canUseCustomerFirehoseAtAnyBank)))

    lazy val getFirehoseCustomers : OBPEndpoint = {
      //get private accounts for all banks
      case "banks" :: BankId(bankId):: "firehose" ::  "customers" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <-  authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- Helper.booleanToFuture(failMsg = CustomerFirehoseNotAllowedOnThisInstance +" or " + UserHasMissingRoles + CanUseCustomerFirehoseAtAnyBank  ) {
              canUseCustomerFirehose(u)
            }
            allowedParams = List("sort_direction", "limit", "offset", "from_date", "to_date")
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            obpQueryParams <- NewStyle.function.createObpParams(httpParams, allowedParams, callContext)
            customers <- NewStyle.function.getCustomers(bankId, callContext, obpQueryParams)
            reqParams = req.params.filterNot(param => allowedParams.contains(param._1))
            (customersFiltered, callContext) <- if(reqParams.isEmpty) {
              Future {(customers, callContext)}
            } else {
              for{
                (customerIds, callContext) <- NewStyle.function.getCustomerIdsByAttributeNameValues(bankId, reqParams, callContext)
              }yield{
                (customers.filter(customer => customerIds.contains(CustomerId(customer.customerId))),callContext)
              }
            }
          } yield {
            (JSONFactory300.createCustomersJson(customersFiltered), HttpCode.`200`(callContext))
          }
      }
    }
    
    
    resourceDocs += ResourceDoc(
      getBadLoginStatus,
      implementedInApiVersion,
      nameOf(getBadLoginStatus),
      "GET",
      "/users/USERNAME/lock-status",
      "Get User Lock Status",
      s"""
         |Get User Login Status.
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      emptyObjectJson,
      badLoginStatusJson,
      List(UserNotLoggedIn, UserNotFoundByUsername, UserHasMissingRoles, UnknownError),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canReadUserLockedStatus))
    )

    lazy val getBadLoginStatus : OBPEndpoint = {
      //get private accounts for all banks
      case "users" :: username::  "lock-status" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <-  authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canReadUserLockedStatus, callContext)
            badLoginStatus <- Future { LoginAttempt.getBadLoginStatus(username) } map { unboxFullOrFail(_, callContext, s"$UserNotFoundByUsername($username)", 404) }
          } yield {
            (createBadLoginStatusJson(badLoginStatus), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      unlockUser,
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
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      emptyObjectJson,
      badLoginStatusJson,
      List(UserNotLoggedIn, UserNotFoundByUsername, UserHasMissingRoles, UnknownError),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canUnlockUser)))

    lazy val unlockUser : OBPEndpoint = {
      //get private accounts for all banks
      case "users" :: username::  "lock-status" :: Nil JsonPut req => {
        cc =>
          for {
            (Full(u), callContext) <-  authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canUnlockUser, callContext)
            _ <- Future { LoginAttempt.resetBadLoginAttempts(username) } 
            _ <- Future { UserLocksProvider.unlockUser(username) } 
            badLoginStatus <- Future { LoginAttempt.getBadLoginStatus(username) } map { unboxFullOrFail(_, callContext, s"$UserNotFoundByUsername($username)", 404) }
          } yield {
            (createBadLoginStatusJson(badLoginStatus), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      callsLimit,
      implementedInApiVersion,
      nameOf(callsLimit),
      "PUT",
      "/management/consumers/CONSUMER_ID/consumer/call-limits",
      "Set Calls Limit for a Consumer",
      s"""
         |Set the API call limits for a Consumer:
         |
         |Per Second
         |Per Minute
         |Per Hour
         |Per Week
         |Per Month
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      callLimitPostJson,
      callLimitPostJson,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        InvalidConsumerId,
        ConsumerNotFoundByConsumerId,
        UserHasMissingRoles,
        UpdateConsumerError,
        UnknownError
      ),
      List(apiTagConsumer, apiTagNewStyle),
      Some(List(canSetCallLimits)))

    lazy val callsLimit : OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "call-limits" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <-  authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canSetCallLimits, callContext)
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CallLimitPostJson ", 400, callContext) {
              json.extract[CallLimitPostJson]
            }
            _ <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
            rateLimiting <- RateLimitingDI.rateLimiting.vend.createOrUpdateConsumerCallLimits(
              consumerId,
              postJson.from_date,
              postJson.to_date,
              None,
              None,
              None,
              Some(postJson.per_second_call_limit),
              Some(postJson.per_minute_call_limit),
              Some(postJson.per_hour_call_limit),
              Some(postJson.per_day_call_limit),
              Some(postJson.per_week_call_limit),
              Some(postJson.per_month_call_limit)) map {
              unboxFullOrFail(_, callContext, UpdateConsumerError)
            }
          } yield {
            (createCallsLimitJson(rateLimiting), HttpCode.`200`(callContext))
          }
      }
    }


    
    resourceDocs += ResourceDoc(
      getCallsLimit,
      implementedInApiVersion,
      nameOf(getCallsLimit),
      "GET",
      "/management/consumers/CONSUMER_ID/consumer/call-limits",
      "Get Call Limits for a Consumer",
      s"""
         |Get Calls limits per Consumer.
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      emptyObjectJson,
      callLimitJson,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        InvalidConsumerId,
        ConsumerNotFoundByConsumerId,
        UserHasMissingRoles,
        UpdateConsumerError,
        UnknownError
      ),
      List(apiTagConsumer, apiTagNewStyle),
      Some(List(canSetCallLimits)))


    
    lazy val getCallsLimit : OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "call-limits" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <-  authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canReadCallLimits, callContext)
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
            rateLimit <- Future(RateLimitingUtil.consumerRateLimitState(consumer.consumerId.get).toList)
          } yield {
            (createCallLimitJson(consumer, rateLimit), HttpCode.`200`(callContext))
          }
      }
    }




    resourceDocs += ResourceDoc(
      checkFundsAvailable,
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
      emptyObjectJson,
      checkFundsAvailableJson,
      List(
        UserNotLoggedIn,
        BankNotFound,
        BankAccountNotFound,
        InvalidAmount,
        InvalidISOCurrencyCode,
        UnknownError
      ),
      apiTagAccount :: apiTagPSD2PIIS :: apiTagPsd2 :: apiTagNewStyle :: Nil)

    lazy val checkFundsAvailable : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "funds-available" :: Nil JsonGet req => {
        cc =>
          val amount = "amount"
          val currency = "currency"
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext) 
            _ <- Helper.booleanToFuture(failMsg = ViewDoesNotPermitAccess + " You need the view canQueryAvailableFunds.") {
              view.canQueryAvailableFunds
            }
            httpParams: List[HTTPParam] <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            _ <- Helper.booleanToFuture(failMsg = MissingQueryParams + amount) {
              httpParams.exists(_.name == amount)
            }
            _ <- Helper.booleanToFuture(failMsg = MissingQueryParams + currency) {
              httpParams.exists(_.name == currency)
            }
            available <- NewStyle.function.tryons(s"$InvalidAmount", 400, callContext) {
              val value = httpParams.filter(_.name == amount).map(_.values.head).head
              new java.math.BigDecimal(value)
            }
            _ <- NewStyle.function.isValidCurrencyISOCode(httpParams.filter(_.name == currency).map(_.values.head).head, callContext)
            _ <- NewStyle.function.moderatedBankAccountCore(account, view, Full(u), callContext)
          } yield {
            val ccy = httpParams.filter(_.name == currency).map(_.values.head).head
            val fundsAvailable =  (view.canQueryAvailableFunds, account.balance, account.currency) match {
              case (false, _, _) => "" // 1st condition: MUST have a view can_query_available_funds
              case (true, _, c) if c != ccy => "no" // 2nd condition: Currency has to be matched
              case (true, b, _) if b.compare(available) >= 0 => "yes" // We have the vew, the right currency and enough funds
              case _ => "no"
            }
            val availableFundsRequestId = callContext.map(_.correlationId).getOrElse("")
            (createCheckFundsAvailableJson(fundsAvailable, availableFundsRequestId), HttpCode.`200`(callContext))
          }

      }
    }



    resourceDocs += ResourceDoc(
      getConsumer,
      implementedInApiVersion,
      nameOf(getConsumer),
      "GET",
      "/management/consumers/CONSUMER_ID",
      "Get Consumer",
      s"""Get the Consumer specified by CONSUMER_ID.
         |
        |""",
      emptyObjectJson,
      consumerJSON,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        ConsumerNotFoundByConsumerId,
        UnknownError
      ),
      List(apiTagConsumer, apiTagApi, apiTagNewStyle),
      Some(List(canGetConsumers)))


    lazy val getConsumer: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetConsumers, callContext)
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
            user <- Users.users.vend.getUserByUserIdFuture(consumer.createdByUserId.get)
          } yield {
            (createConsumerJSON(consumer, user), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getConsumersForCurrentUser,
      implementedInApiVersion,
      nameOf(getConsumersForCurrentUser),
      "GET",
      "/management/users/current/consumers",
      "Get Consumers (logged in User)",
      s"""Get the Consumers for logged in User.
         |
        |""",
      emptyObjectJson,
      consumersJson310,
      List(
        UserNotLoggedIn,
        UnknownError
      ),
      List(apiTagConsumer, apiTagApi, apiTagNewStyle)
    )


    lazy val getConsumersForCurrentUser: OBPEndpoint = {
      case "management" :: "users" :: "current" :: "consumers" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            consumer <- Consumers.consumers.vend.getConsumersByUserIdFuture(u.userId)
          } yield {
            (createConsumersJson(consumer, Full(u)), HttpCode.`200`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      getConsumers,
      implementedInApiVersion,
      nameOf(getConsumers),
      "GET",
      "/management/consumers",
      "Get Consumers",
      s"""Get the all Consumers.
         |
        |""",
      emptyObjectJson,
      consumersJson310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConsumer, apiTagApi, apiTagNewStyle),
      Some(List(canGetConsumers))
    )


    lazy val getConsumers: OBPEndpoint = {
      case "management" :: "consumers" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetConsumers, callContext)
            consumers <- Consumers.consumers.vend.getConsumersFuture()
            users <- Users.users.vend.getUsersByUserIdsFuture(consumers.map(_.createdByUserId.get))
          } yield {
            (createConsumersJson(consumers, users), HttpCode.`200`(callContext))
          }
      }
    }


    val accountWebHookInfo = s"""Webhooks are used to call external URLs when certain events happen.
                               |
                               |Account Webhooks focus on events around accounts.
                               |
                               |For instance, a webhook could be used to notify an external service if a balance changes on an account.
                               |
                               |This functionality is work in progress! Please note that only implemented trigger is: ${ApiTrigger.onBalanceChange}"""


    resourceDocs += ResourceDoc(
      createAccountWebhook,
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
      apiTagWebhook :: apiTagBank :: apiTagNewStyle :: Nil,
      Some(List(canCreateWebhook))
    )

    lazy val createAccountWebhook : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "account-web-hooks" :: Nil JsonPost json -> _  => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canCreateWebhook, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $AccountWebhookPostJson "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AccountWebhookPostJson]
            }
            failMsg = IncorrectTriggerName + postJson.trigger_name + ". Possible values are " + ApiTrigger.availableTriggers.sorted.mkString(", ")
            _ <- NewStyle.function.tryons(failMsg, 400, callContext) {
              ApiTrigger.valueOf(postJson.trigger_name)
            }
            failMsg = s"$InvalidBoolean Possible values of the json field is_active are true or false."
            isActive <- NewStyle.function.tryons(failMsg, 400, callContext) {
              postJson.is_active.toBoolean
            }
            wh <- AccountWebhook.accountWebhook.vend.createAccountWebhookFuture(
              bankId = bankId.value,
              accountId = postJson.account_id,
              userId = u.userId,
              triggerName = postJson.trigger_name,
              url = postJson.url,
              httpMethod = postJson.http_method,
              httpProtocol = postJson.http_protocol,
              isActive = isActive
            ) map {
              unboxFullOrFail(_, callContext, CreateWebhookError)
            }
          } yield {
            (createAccountWebhookJson(wh), HttpCode.`201`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      enableDisableAccountWebhook,
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
      apiTagWebhook :: apiTagBank :: apiTagNewStyle :: Nil,
      Some(List(canUpdateWebhook))
    )

    lazy val enableDisableAccountWebhook : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "account-web-hooks" :: Nil JsonPut json -> _  => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canUpdateWebhook, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $AccountWebhookPutJson "
            putJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AccountWebhookPutJson]
            }
            failMsg = s"$InvalidBoolean Possible values of the json field is_active are true or false."
            isActive <- NewStyle.function.tryons(failMsg, 400, callContext) {
              putJson.is_active.toBoolean
            }
            _ <- AccountWebhook.accountWebhook.vend.getAccountWebhookByIdFuture(putJson.account_webhook_id) map {
              unboxFullOrFail(_, callContext, WebhookNotFound)
            }
            wh <- AccountWebhook.accountWebhook.vend.updateAccountWebhookFuture(
              accountWebhookId = putJson.account_webhook_id,
              isActive = isActive
            ) map {
              unboxFullOrFail(_, callContext, UpdateWebhookError)
            }
          } yield {
            (createAccountWebhookJson(wh), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getAccountWebhooks,
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
      emptyObjectJson,
      accountWebhooksJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      apiTagWebhook :: apiTagBank :: apiTagNewStyle :: Nil,
      Some(List(canGetWebhooks))
    )


    lazy val getAccountWebhooks: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) ::"account-web-hooks" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canGetWebhooks, callContext)
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            allowedParams = List("limit", "offset", "account_id", "user_id")
            obpParams <- NewStyle.function.createObpParams(httpParams, allowedParams, callContext)
            additionalParam = OBPBankId(bankId.value)
            webhooks <- NewStyle.function.getAccountWebhooks(additionalParam :: obpParams, callContext)
          } yield {
            (createAccountWebhooksJson(webhooks), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      config,
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
      emptyObjectJson,
      configurationJSON,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      apiTagApi :: apiTagNewStyle :: Nil,
      Some(List(canGetConfig)))

    lazy val config: OBPEndpoint = {
      case "config" :: Nil JsonGet _ =>
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetConfig, callContext)
          } yield {
            (JSONFactory310.getConfigInfoJSON(), HttpCode.`200`(callContext))
          }
    }

    resourceDocs += ResourceDoc(
      getAdapterInfo,
      implementedInApiVersion,
      nameOf(getAdapterInfo),
      "GET",
      "/adapter",
      "Get Adapter Info",
      s"""Get basic information about the Adapter.
         |
        |${authenticationRequiredMessage(false)}
         |
      """.stripMargin,
      emptyObjectJson,
      adapterInfoJsonV300,
      List(UserNotLoggedIn, UnknownError),
      List(apiTagApi, apiTagNewStyle))


    lazy val getAdapterInfo: OBPEndpoint = {
      case "adapter" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
            (ai,_) <- NewStyle.function.getAdapterInfo(callContext)
          } yield {
            (createAdapterInfoJson(ai), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getTransactionByIdForBankAccount,
      implementedInApiVersion,
      nameOf(getTransactionByIdForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/transaction",
      "Get Transaction by Id",
      s"""Returns one transaction specified by TRANSACTION_ID of the account ACCOUNT_ID and [moderated](#1_2_1-getViewsForBankAccount) by the view (VIEW_ID).
         |
         |${authenticationRequiredMessage(false)}
         |Authentication is required if the view is not public.
         |
         |
         |""",
      emptyObjectJson,
      transactionJsonV300,
      List(UserNotLoggedIn, BankAccountNotFound ,ViewNotFound, UserNoPermissionAccessView, UnknownError),
      List(apiTagTransaction, apiTagNewStyle))

    lazy val getTransactionByIdForBankAccount : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "transaction" :: Nil JsonGet _ => {
        cc =>
          for {
            (user, callContext) <- authenticatedAccess(cc)
            _ <- passesPsd2Pisp(callContext)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), user, callContext) 
            (moderatedTransaction, callContext) <- account.moderatedTransactionFuture(bankId, accountId, transactionId, view, user, callContext) map {
              unboxFullOrFail(_, callContext, GetTransactionsException)
            }
            (transactionAttributes, callContext) <- NewStyle.function.getTransactionAttributes(
              bankId,
              transactionId,
              cc.callContext: Option[CallContext])
          } yield {
            (JSONFactory300.createTransactionJSON(moderatedTransaction, transactionAttributes), HttpCode.`200`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      getTransactionRequests,
      implementedInApiVersion,
      nameOf(getTransactionRequests),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests",
      "Get Transaction Requests." ,
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
      emptyObjectJson,
      transactionRequestWithChargeJSONs210,
      List(
        UserNotLoggedIn,
        BankNotFound,
        BankAccountNotFound,
        UserNoPermissionAccessView,
        UserNoOwnerView,
        GetTransactionRequestsException,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagPSD2PIS, apiTagNewStyle))

    lazy val getTransactionRequests: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-requests" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.isEnabledTransactionRequests()
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (fromAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext)
            _ <- Helper.booleanToFuture(failMsg = UserNoOwnerView) {
              u.hasOwnerViewAccess(BankIdAccountId(bankId,accountId))
            }
            (transactionRequests, callContext) <- Future(Connector.connector.vend.getTransactionRequests210(u, fromAccount, callContext)) map {
              unboxFullOrFail(_, callContext, GetTransactionRequestsException)
            }
          } yield {
            val json = JSONFactory210.createTransactionRequestJSONs(transactionRequests)
            (json, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      createCustomer,
      implementedInApiVersion,
      nameOf(createCustomer),
      "POST",
      "/banks/BANK_ID/customers",
      "Create Customer",
      s"""
         |The Customer resource stores the customer number, legal name, email, phone number, their date of birth, relationship status, education attained, a url for a profile image, KYC status etc.
         |Dates need to be in the format 2013-01-21T23:08:00Z
         |
          |${authenticationRequiredMessage(true)}
         |""",
      postCustomerJsonV310,
      customerJsonV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
        InvalidJsonFormat,
        CustomerNumberAlreadyExists,
        UserNotFoundById,
        CustomerAlreadyExistsForUser,
        CreateConsumerError,
        UnknownError
      ),
      List(apiTagCustomer, apiTagPerson, apiTagNewStyle),
      Some(List(canCreateCustomer,canCreateCustomerAtAnyBank))
    )
    lazy val createCustomer : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)

            _ <- NewStyle.function.hasAtLeastOneEntitlement(bankId.value, u.userId, canCreateCustomer :: canCreateCustomerAtAnyBank :: Nil, callContext)

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerJsonV310 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostCustomerJsonV310]
            }
            _ <- Helper.booleanToFuture(failMsg =  InvalidJsonContent + s" The field dependants(${postedData.dependants}) not equal the length(${postedData.dob_of_dependants.length }) of dob_of_dependants array" ) {
              postedData.dependants == postedData.dob_of_dependants.length 
            }
            (customer, callContext) <- NewStyle.function.createCustomer(
              bankId,
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
              callContext,
            ) 
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`201`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      getRateLimitingInfo,
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
         |
        |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      emptyObjectJson,
      rateLimitingInfoV310,
      List(UnknownError),
      List(apiTagApi, apiTagNewStyle))


    lazy val getRateLimitingInfo: OBPEndpoint = {
      case "rate-limiting" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
            rateLimiting <- NewStyle.function.tryons("", 400, callContext) {
              RateLimitingUtil.inMemoryMode match {
                case true =>
                  val isActive = if(RateLimitingUtil.useConsumerLimits == true) true else false
                  RateLimiting(RateLimitingUtil.useConsumerLimits, "In-Memory", true, isActive)
                case false =>
                  val isRedisAvailable = RateLimitingUtil.isRedisAvailable()
                  val isActive = if(RateLimitingUtil.useConsumerLimits == true && isRedisAvailable == true) true else false
                  RateLimiting(RateLimitingUtil.useConsumerLimits, "REDIS", isRedisAvailable, isActive)
              }
            }
          } yield {
            (createRateLimitingInfo(rateLimiting), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getCustomerByCustomerId,
      implementedInApiVersion,
      nameOf(getCustomerByCustomerId),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID",
      "Get Customer by CUSTOMER_ID",
      s"""Gets the Customer specified by CUSTOMER_ID.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        |""",
      emptyObjectJson,
      customerWithAttributesJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canGetCustomer)))

    lazy val getCustomerByCustomerId : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId ::  Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetCustomer, callContext)
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (customerAttributes, callContext) <- NewStyle.function.getCustomerAttributes(
              bankId,
              CustomerId(customerId),
              callContext: Option[CallContext])
          } yield {
            (JSONFactory310.createCustomerWithAttributesJson(customer, customerAttributes), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getCustomerByCustomerNumber,
      implementedInApiVersion,
      nameOf(getCustomerByCustomerNumber),
      "POST",
      "/banks/BANK_ID/customers/customer-number",
      "Get Customer by CUSTOMER_NUMBER",
      s"""Gets the Customer specified by CUSTOMER_NUMBER.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        |""",
      postCustomerNumberJsonV310,
      customerWithAttributesJsonV310,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagKyc ,apiTagNewStyle),
      Some(List(canGetCustomer))
    )

    lazy val getCustomerByCustomerNumber : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: "customer-number" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetCustomer, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerNumberJsonV310 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostCustomerNumberJsonV310]
            }
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerNumber(postedData.customer_number, bank.bankId, callContext)
            (customerAttributes, callContext) <- NewStyle.function.getCustomerAttributes(
              bankId,
              CustomerId(customer.customerId),
              callContext: Option[CallContext])
          } yield {
            (JSONFactory310.createCustomerWithAttributesJson(customer, customerAttributes), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      createUserAuthContext,
      implementedInApiVersion,
      nameOf(createUserAuthContext),
      "POST",
      "/users/USER_ID/auth-context",
      "Create User Auth Context",
      s"""Create User Auth Context. These key value pairs will be propagated over connector to adapter. Normally used for mapping OBP user and 
        | Bank User/Customer. 
        |${authenticationRequiredMessage(true)}
        |""",
      postUserAuthContextJson,
      userAuthContextJson,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        CreateUserAuthContextError,
        UnknownError
      ),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canCreateUserAuthContext)))

    lazy val createUserAuthContext : OBPEndpoint = {
      case "users" :: userId ::"auth-context" :: Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateUserAuthContext, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserAuthContextJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostUserAuthContextJson]
            }
            (_, callContext) <- NewStyle.function.findByUserId(userId, callContext)
            (userAuthContext, callContext) <- NewStyle.function.createUserAuthContext(userId, postedData.key, postedData.value, callContext)
          } yield {
            (JSONFactory310.createUserAuthContextJson(userAuthContext), HttpCode.`201`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getUserAuthContexts,
      implementedInApiVersion,
      nameOf(getUserAuthContexts),
      "GET",
      "/users/USER_ID/auth-context",
      "Get User Auth Contexts",
      s"""Get User Auth Contexts for a User.
        |
        |
        |${authenticationRequiredMessage(true)}
        |
        |""",
      emptyObjectJson,
      userAuthContextsJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        CreateUserAuthContextError,
        UnknownError
      ),
      List(apiTagUser, apiTagNewStyle),
      Some(canGetUserAuthContext :: Nil)
    )

    lazy val getUserAuthContexts : OBPEndpoint = {
      case "users" :: userId :: "auth-context" ::  Nil  JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetUserAuthContext, callContext)
            (_, callContext) <- NewStyle.function.findByUserId(userId, callContext)
            (userAuthContexts, callContext) <- NewStyle.function.getUserAuthContexts(userId, callContext)
          } yield {
            (JSONFactory310.createUserAuthContextsJson(userAuthContexts), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      deleteUserAuthContexts,
      implementedInApiVersion,
      nameOf(deleteUserAuthContexts),
      "DELETE",
      "/users/USER_ID/auth-context",
      "Delete User's Auth Contexts",
      s"""Delete the Auth Contexts of a User specified by USER_ID.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canDeleteUserAuthContext)))

    lazy val deleteUserAuthContexts : OBPEndpoint = {
      case "users" :: userId :: "auth-context" :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteUserAuthContext, callContext)
            (_, callContext) <- NewStyle.function.findByUserId(userId, callContext)
            (userAuthContext, callContext) <- NewStyle.function.deleteUserAuthContexts(userId, callContext)
          } yield {
            (Full(userAuthContext), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      deleteUserAuthContextById,
      implementedInApiVersion,
      nameOf(deleteUserAuthContextById),
      "DELETE",
      "/users/USER_ID/auth-context/USER_AUTH_CONTEXT_ID",
      "Delete User Auth Context",
      s"""Delete a User AuthContext of the User specified by USER_AUTH_CONTEXT_ID.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canDeleteUserAuthContext)))

    lazy val deleteUserAuthContextById : OBPEndpoint = {
      case "users" :: userId :: "auth-context" :: userAuthContextId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteUserAuthContext, callContext)
            (_, callContext) <- NewStyle.function.findByUserId(userId, callContext)
            (userAuthContext, callContext) <- NewStyle.function.deleteUserAuthContextById(userAuthContextId, callContext)
          } yield {
            (Full(userAuthContext), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      createTaxResidence,
      implementedInApiVersion,
      nameOf(createTaxResidence),
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_ID/tax-residence",
      "Create Tax Residence",
      s"""Create a Tax Residence for a Customer specified by CUSTOMER_ID.
        |
        |
        |${authenticationRequiredMessage(true)}
        |
        |""",
      postTaxResidenceJsonV310,
      taxResidenceV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagKyc, apiTagNewStyle),
      Some(List(canCreateTaxResidence)))

    lazy val createTaxResidence : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "tax-residence" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canCreateTaxResidence, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostTaxResidenceJsonV310 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostTaxResidenceJsonV310]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (taxResidence, callContext) <- NewStyle.function.createTaxResidence(customerId, postedData.domain, postedData.tax_number, callContext)
          } yield {
            (JSONFactory310.createTaxResidence(taxResidence), HttpCode.`201`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getTaxResidence,
      implementedInApiVersion,
      nameOf(getTaxResidence),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/tax-residences",
      "Get Tax Residences of Customer",
      s"""Get the Tax Residences of the Customer specified by CUSTOMER_ID.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        |""",
      emptyObjectJson,
      taxResidencesJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagCustomer, apiTagKyc, apiTagNewStyle))

    lazy val getTaxResidence : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "tax-residences" ::  Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetTaxResidence, callContext)
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (taxResidences, callContext) <- NewStyle.function.getTaxResidences(customerId, callContext)
          } yield {
            (JSONFactory310.createTaxResidences(taxResidences), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      deleteTaxResidence,
      implementedInApiVersion,
      nameOf(deleteTaxResidence),
      "DELETE",
      "/banks/BANK_ID/customers/CUSTOMER_ID/tax_residencies/TAX_RESIDENCE_ID",
      "Delete Tax Residence",
      s"""Delete a Tax Residence of the Customer specified by TAX_RESIDENCE_ID.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagCustomer, apiTagKyc, apiTagNewStyle))

    lazy val deleteTaxResidence : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "tax_residencies" :: taxResidenceId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canDeleteTaxResidence, callContext)
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (taxResidence, callContext) <- NewStyle.function.deleteTaxResidence(taxResidenceId, callContext)
          } yield {
            (Full(taxResidence), HttpCode.`200`(callContext))
          }
      }
    }
    resourceDocs += ResourceDoc(
      getAllEntitlements,
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
        |eg: /entitlements?role=${canGetCustomer.toString}
        |
        |
        |
      """.stripMargin,
      emptyObjectJson,
      entitlementJSONs,
      List(UserNotLoggedIn, UserHasMissingRoles, UnknownError),
      List(apiTagRole, apiTagEntitlement, apiTagNewStyle))


    lazy val getAllEntitlements: OBPEndpoint = {
      case "entitlements" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetEntitlementsForAnyUserAtAnyBank, callContext)
            roleName = APIUtil.getHttpRequestUrlParam(cc.url, "role")
            entitlements <- Entitlement.entitlement.vend.getEntitlementsByRoleFuture(roleName) map {
              connectorEmptyResponse(_, callContext)
            }
          } yield {
            (JSONFactory310.createEntitlementJsonsV310(entitlements), callContext)
          }
      }
    }



    resourceDocs += ResourceDoc(
      createCustomerAddress,
      implementedInApiVersion,
      nameOf(createCustomerAddress),
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_ID/address",
      "Create Address",
      s"""Create an Address for a Customer specified by CUSTOMER_ID.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        |""",
      postCustomerAddressJsonV310,
      customerAddressJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canCreateCustomerAddress)))

    lazy val createCustomerAddress : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "address" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canCreateCustomerAddress, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerAddressJsonV310 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostCustomerAddressJsonV310]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (address, callContext) <- NewStyle.function.createCustomerAddress(
              customerId,
              postedData.line_1,
              postedData.line_2,
              postedData.line_3,
              postedData.city,
              postedData.county,
              postedData.state,
              postedData.postcode,
              postedData.country_code,
              postedData.state,
              postedData.tags.mkString(","),
              callContext)
          } yield {
            (JSONFactory310.createAddress(address), HttpCode.`201`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      updateCustomerAddress,
      implementedInApiVersion,
      nameOf(updateCustomerAddress),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/addresses/CUSTOMER_ADDRESS_ID",
      "Update the Address of a Customer",
      s"""Update an Address of the Customer specified by CUSTOMER_ADDRESS_ID.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        |""",
      postCustomerAddressJsonV310,
      customerAddressJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle))

    lazy val updateCustomerAddress : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "addresses" :: customerAddressId ::  Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canCreateCustomer, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerAddressJsonV310 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostCustomerAddressJsonV310]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (address, callContext) <- NewStyle.function.updateCustomerAddress(
              customerAddressId,
              postedData.line_1,
              postedData.line_2,
              postedData.line_3,
              postedData.city,
              postedData.county,
              postedData.state,
              postedData.postcode,
              postedData.country_code,
              postedData.state,
              postedData.tags.mkString(","),
              callContext)
          } yield {
            (JSONFactory310.createAddress(address), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getCustomerAddresses,
      implementedInApiVersion,
      nameOf(getCustomerAddresses),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/addresses",
      "Get Customer Addresses",
      s"""Get the Addresses of the Customer specified by CUSTOMER_ID.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        |""",
      emptyObjectJson,
      customerAddressesJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagCustomer, apiTagKyc, apiTagNewStyle),
      Some(List(canGetCustomerAddress)))

    lazy val getCustomerAddresses : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "addresses" ::  Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetCustomerAddress, callContext)
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (customers, callContext) <- NewStyle.function.getCustomerAddress(customerId, callContext)
          } yield {
            (JSONFactory310.createAddresses(customers), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      deleteCustomerAddress,
      implementedInApiVersion,
      nameOf(deleteCustomerAddress),
      "DELETE",
      "/banks/BANK_ID/customers/CUSTOMER_ID/addresses/CUSTOMER_ADDRESS_ID",
      "Delete Customer Address",
      s"""Delete an Address of the Customer specified by CUSTOMER_ADDRESS_ID.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagCustomer, apiTagKyc, apiTagNewStyle),
      Some(List(canDeleteCustomerAddress)))

    lazy val deleteCustomerAddress : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "addresses" :: customerAddressId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canDeleteCustomerAddress, callContext)
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (address, callContext) <- NewStyle.function.deleteCustomerAddress(customerAddressId, callContext)
          } yield {
            (Full(address), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getObpConnectorLoopback,
      implementedInApiVersion,
      nameOf(getObpConnectorLoopback),
      "GET",
      "/connector/loopback",
      "Get Connector Status (Loopback)",
      s"""This endpoint makes a call to the Connector to check the backend transport (e.g. Kafka) is reachable.
         |
         |Currently this is only implemented for Kafka based connectors.
         |
         |For Kafka based connectors, this endpoint writes a message to Kafka and reads it again.
         |
         |In the future, this endpoint may also return information about database connections etc.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      emptyObjectJson,
      obpApiLoopbackJson,
      List(
        UnknownError
      ),
      List(apiTagApi, apiTagNewStyle))

    lazy val getObpConnectorLoopback : OBPEndpoint = {
      case "connector" :: "loopback" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
            connectorVersion = APIUtil.getPropsValue("connector").openOrThrowException("connector props field `connector` not set")
            starConnectorProps = APIUtil.getPropsValue("starConnector_supported_types").openOr("notfound")
            obpApiLoopback <- connectorVersion.contains("kafka") ||  (connectorVersion.contains("star") && starConnectorProps.contains("kafka")) match {
              case false => throw new IllegalStateException(s"${NotImplemented}for connector ${connectorVersion}")
              case true => KafkaHelper.echoKafkaServer.recover {
                case e: Throwable => throw new IllegalStateException(s"${KafkaServerUnavailable} Timeout error, because kafka do not return message to OBP-API. ${e.getMessage}")
              }
            }
          } yield {
            (createObpApiLoopbackJson(obpApiLoopback), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      refreshUser,
      implementedInApiVersion,
      nameOf(refreshUser),
      "POST",
      "/users/USER_ID/refresh",
      "Refresh User",
      s""" The endpoint is used for updating the accounts, views, account holders for the user.
         | As to the Json body, you can leave it as Empty. 
         | This call will get data from backend, no need to prepare the json body in api side.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      emptyObjectJson,
      refresUserJson,
      List(
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canRefreshUser))
    )

    lazy val refreshUser : OBPEndpoint = {
      case "users" :: userId :: "refresh" :: Nil JsonPost _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", userId, canRefreshUser, callContext)
            startTime <- Future{Helpers.now}
            _ <- NewStyle.function.findByUserId(userId, callContext)
            _ <- if (APIUtil.isSandboxMode) Future{} else AuthUser.updateUserAccountViewsFuture(u, callContext) 
            endTime <- Future{Helpers.now}
            durationTime = endTime.getTime - startTime.getTime
          } yield {
            (createRefreshUserJson(durationTime), HttpCode.`201`(callContext))
          }
      }
    }


    val productAttributeGeneralInfo =
      s"""
         |Product Attributes are used to describe a financial Product with a list of typed key value pairs.
         |
         |Each Product Attribute is linked to its Product by PRODUCT_CODE
         |
         |
       """.stripMargin

    resourceDocs += ResourceDoc(
      createProductAttribute,
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
         |${authenticationRequiredMessage(true)}
         |
         |""",
      productAttributeJson,
      productAttributeResponseJson,
      List(
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle),
      Some(List(canCreateProductAttribute))
    )

    lazy val createProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attribute" :: Nil JsonPost json -> _=> {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canCreateProductAttribute, callContext)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $ProductAttributeJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[ProductAttributeJson]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${ProductAttributeType.DOUBLE}(12.1234), ${ProductAttributeType.STRING}(TAX_NUMBER), ${ProductAttributeType.INTEGER}(123) and ${ProductAttributeType.DATE_WITH_DAY}(2012-04-23)"
            productAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
              ProductAttributeType.withName(postedData.`type`)
            }
            
            (productAttribute, callContext) <- NewStyle.function.createOrUpdateProductAttribute(
              BankId(bankId),
              ProductCode(productCode),
              None,
              postedData.name,
              productAttributeType,
              postedData.value,
              callContext: Option[CallContext]
            )
          } yield {
            (createProductAttributeJson(productAttribute), HttpCode.`201`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getProductAttribute,
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
         |${authenticationRequiredMessage(true)}
         |
         |""",
      emptyObjectJson,
      productAttributeResponseJson,
      List(
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle))

    lazy val getProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attributes" :: productAttributeId :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canGetProductAttribute, callContext)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            (productAttribute, callContext) <- NewStyle.function.getProductAttributeById(productAttributeId, callContext)
            
          } yield {
            (createProductAttributeJson(productAttribute), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      updateProductAttribute,
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
         |${authenticationRequiredMessage(true)}
         |
         |""",
      productAttributeJson,
      productAttributeResponseJson,
      List(
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle))

    lazy val updateProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attributes" :: productAttributeId :: Nil JsonPut json -> _ =>{
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canUpdateProductAttribute, callContext)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $ProductAttributeJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[ProductAttributeJson]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${ProductAttributeType.DOUBLE}(12.1234), ${ProductAttributeType.STRING}(TAX_NUMBER), ${ProductAttributeType.INTEGER}(123) and ${ProductAttributeType.DATE_WITH_DAY}(2012-04-23)"
            productAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
              ProductAttributeType.withName(postedData.`type`)
            }
            (_, callContext) <- NewStyle.function.getProductAttributeById(productAttributeId, callContext)
            (productAttribute, callContext) <- NewStyle.function.createOrUpdateProductAttribute(
              BankId(bankId),
              ProductCode(productCode),
              Some(productAttributeId),
              postedData.name,
              productAttributeType,
              postedData.value,
              callContext: Option[CallContext]
            )
          } yield {
            (createProductAttributeJson(productAttribute), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      deleteProductAttribute,
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
         |${authenticationRequiredMessage(true)}
         |
         |""",
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserHasMissingRoles,
        BankNotFound,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle))

    lazy val deleteProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attributes" :: productAttributeId ::  Nil JsonDelete _=> {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canDeleteProductAttribute, callContext)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            (productAttribute, callContext) <- NewStyle.function.deleteProductAttribute(productAttributeId, callContext)
          } yield {
            (Full(productAttribute), HttpCode.`204`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      createAccountApplication,
      implementedInApiVersion,
      nameOf(createAccountApplication),
      "POST",
      "/banks/BANK_ID/account-applications",
      "Create Account Application",
      s""" Create Account Application
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      accountApplicationJson,
      accountApplicationResponseJson,
      List(
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagAccountApplication, apiTagAccount, apiTagNewStyle))

    lazy val createAccountApplication : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "account-applications" :: Nil JsonPost json -> _=> {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            
            failMsg = s"$InvalidJsonFormat The Json body should be the $AccountApplicationJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AccountApplicationJson]
            }

            illegalProductCodeMsg = s"$InvalidJsonFormat product_code should not be empty."
            _ <- NewStyle.function.tryons(illegalProductCodeMsg, 400, callContext) {
              Validate.notBlank(postedData.product_code)
            }

            illegalUserIdOrCustomerIdMsg = s"$InvalidJsonFormat User_id and customer_id should not both are empty."
            _ <- NewStyle.function.tryons(illegalUserIdOrCustomerIdMsg, 400, callContext) {
              Validate.isTrue(postedData.user_id.isDefined || postedData.customer_id.isDefined)
            }

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            
            user <- unboxOptionOBPReturnType(postedData.user_id.map(NewStyle.function.findByUserId(_, callContext)))
            
            customer  <- unboxOptionOBPReturnType(postedData.customer_id.map(NewStyle.function.getCustomerByCustomerId(_, callContext)))

            (productAttribute, callContext) <- NewStyle.function.createAccountApplication(
              productCode = ProductCode(postedData.product_code),
              userId = postedData.user_id,
              customerId = postedData.customer_id,
              callContext = callContext
            )
          } yield {
            (createAccountApplicationJson(productAttribute, user, customer), HttpCode.`201`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getAccountApplications,
      implementedInApiVersion,
      nameOf(getAccountApplications),
      "GET",
      "/banks/BANK_ID/account-applications",
      "Get Account Applications",
      s"""Get the Account Applications.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        |""",
      emptyObjectJson,
      accountApplicationsJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagAccountApplication, apiTagAccount, apiTagNewStyle))

    lazy val getAccountApplications : OBPEndpoint = {
      case "banks" :: BankId(bankId) ::"account-applications" ::  Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)

            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetAccountApplications, callContext)

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            
            (accountApplications, _) <- NewStyle.function.getAllAccountApplication(callContext)
            (users, _) <- NewStyle.function.findUsers(accountApplications.map(_.userId), callContext)
            (customers, _) <- NewStyle.function.findCustomers(accountApplications.map(_.customerId), callContext)
          } yield {
            (JSONFactory310.createAccountApplications(accountApplications, users, customers), HttpCode.`200`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      getAccountApplication,
      implementedInApiVersion,
      nameOf(getAccountApplication),
      "GET",
      "/banks/BANK_ID/account-applications/ACCOUNT_APPLICATION_ID",
      "Get Account Application by Id",
      s"""Get the Account Application.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      emptyObjectJson,
      accountApplicationResponseJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagAccountApplication, apiTagAccount, apiTagNewStyle))

    lazy val getAccountApplication : OBPEndpoint = {
      case "banks" :: BankId(bankId) ::"account-applications":: accountApplicationId ::  Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            
            (accountApplication, _) <- NewStyle.function.getAccountApplicationById(accountApplicationId, callContext)

            userId = Option(accountApplication.userId)
            customerId = Option(accountApplication.customerId)

            user <- unboxOptionOBPReturnType(userId.map(NewStyle.function.findByUserId(_, callContext)))
            customer  <- unboxOptionOBPReturnType(customerId.map(NewStyle.function.getCustomerByCustomerId(_, callContext)))

          } yield {
            (createAccountApplicationJson(accountApplication, user, customer), HttpCode.`200`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      updateAccountApplicationStatus,
      implementedInApiVersion,
      nameOf(updateAccountApplicationStatus),
      "PUT",
      "/banks/BANK_ID/account-applications/ACCOUNT_APPLICATION_ID",
      "Update Account Application Status",
      s"""Update an Account Application status
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      accountApplicationUpdateStatusJson,
      accountApplicationResponseJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagAccountApplication, apiTagAccount, apiTagNewStyle)
    )

    lazy val updateAccountApplicationStatus : OBPEndpoint = {
      case "banks" :: BankId(bankId) ::"account-applications" :: accountApplicationId :: Nil JsonPut json -> _  => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)

            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canUpdateAccountApplications, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $AccountApplicationUpdateStatusJson "
            putJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AccountApplicationUpdateStatusJson]
            }

            failMsg = s"$InvalidJsonFormat status should not be blank."
            status <- NewStyle.function.tryons(failMsg, 400, callContext) {
               Validate.notBlank(putJson.status)
            }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            
            (accountApplication, _) <- NewStyle.function.getAccountApplicationById(accountApplicationId, callContext)
            
            (accountApplication, _) <- NewStyle.function.updateAccountApplicationStatus(accountApplicationId, status, callContext)
            
            userId = Option(accountApplication.userId)
            customerId = Option(accountApplication.customerId)

            user <- unboxOptionOBPReturnType(userId.map(NewStyle.function.findByUserId(_, callContext)))
            customer  <- unboxOptionOBPReturnType(customerId.map(NewStyle.function.getCustomerByCustomerId(_, callContext)))
            
            _ <- status match {
              case "ACCEPTED" =>
                for{
                  accountId <- Future{AccountId(UUID.randomUUID().toString)}
                  (_, callContext) <- NewStyle.function.createBankAccount(
                    bankId,
                    accountId,
                    accountApplication.productCode.value,
                    "",
                    "EUR",
                    BigDecimal("0"),
                    u.name,
                    "",
                    List.empty,
                    callContext)
                }yield {
                  BankAccountCreation.setAsOwner(bankId, accountId, u)
                }
              case _ => Future{""}
            }
            
          } yield {
            (createAccountApplicationJson(accountApplication, user, customer), HttpCode.`200`(callContext))
          }
      }
    }


    val productHiearchyAndCollectionNote =
      """
        |
        |Product hiearchy vs Product Collections:
        |
        |* You can define a hierarchy of products - so that a child Product inherits attributes of its parent Product -  using the parent_product_code in Product.
        |
        |* You can define a collection (also known as baskets or buckets) of products using Product Collections.
        |
      """.stripMargin
    
    
    val createProductEntitlements = canCreateProduct :: canCreateProductAtAnyBank ::  Nil
    val createProductEntitlementsRequiredText = UserHasMissingRoles + createProductEntitlements.mkString(" or ")

    resourceDocs += ResourceDoc(
      createProduct,
      implementedInApiVersion,
      "createProduct",
      "PUT",
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
         |${authenticationRequiredMessage(true) }
         |
         |
         |""",
      postPutProductJsonV310,
      productJsonV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle),
      Some(List(canCreateProduct, canCreateProductAtAnyBank))
    )

    lazy val createProduct: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: ProductCode(productCode) :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = createProductEntitlementsRequiredText)(bankId.value, u.userId, createProductEntitlements, callContext)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostPutProductJsonV310 "
            product <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostPutProductJsonV310]
            }
            parentProductCode <- product.parent_product_code.trim.nonEmpty match {
              case false => 
                Future(Empty)
              case true =>
                Future(Connector.connector.vend.getProduct(bankId, ProductCode(product.parent_product_code))) map {
                  getFullBoxOrFail(_, callContext, ParentProductNotFoundByProductCode + " {" + product.parent_product_code + "}", 400)
                }
            }
            success <- Future(Connector.connector.vend.createOrUpdateProduct(
              bankId = bankId.value,
              code = productCode.value,
              parentProductCode = parentProductCode.map(_.code.value).toOption,
              name = product.name,
              category = product.category,
              family = product.family,
              superFamily = product.super_family,
              moreInfoUrl = product.more_info_url,
              details = product.details,
              description = product.description,
              metaLicenceId = product.meta.license.id,
              metaLicenceName = product.meta.license.name
            )) map {
              connectorEmptyResponse(_, callContext)
            }
          } yield {
            (JSONFactory310.createProductJson(success), HttpCode.`201`(callContext))
          }
          
      }
    }


    val getProductsIsPublic = APIUtil.getPropsAsBoolValue("apiOptions.getProductsIsPublic", true)
    
    resourceDocs += ResourceDoc(
      getProduct,
      implementedInApiVersion,
      "getProduct",
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
         |${authenticationRequiredMessage(!getProductsIsPublic)}""".stripMargin,
      emptyObjectJson,
      productJsonV310,
      List(
        UserNotLoggedIn,
        ProductNotFoundByProductCode,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle)
    )

    lazy val getProduct: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: ProductCode(productCode) :: Nil JsonGet _ => {
        cc => {
          for {
            (_, callContext) <- getProductsIsPublic match {
                case false => authenticatedAccess(cc)
                case true => anonymousAccess(cc)
              }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            product <- Future(Connector.connector.vend.getProduct(bankId, productCode)) map {
              unboxFullOrFail(_, callContext, ProductNotFoundByProductCode)
            }
            (productAttributes, callContext) <- NewStyle.function.getProductAttributesByBankAndCode(bankId, productCode, callContext)
          } yield {
            (JSONFactory310.createProductJson(product, productAttributes), HttpCode.`200`(callContext))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getProductTree,
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
         |${authenticationRequiredMessage(!getProductsIsPublic)}""".stripMargin,
      emptyObjectJson,
      childProductTreeJsonV310,
      List(
        UserNotLoggedIn,
        ProductNotFoundByProductCode,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle)
    )

    lazy val getProductTree: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "product-tree" :: ProductCode(productCode) :: Nil JsonGet _ => {
        def getProductTre(bankId : BankId, productCode : ProductCode): List[Product] = {
          Connector.connector.vend.getProduct(bankId, productCode) match {
            case Full(p) if p.parentProductCode.value.nonEmpty => p :: getProductTre(p.bankId, p.parentProductCode)
            case Full(p) => List(p)
            case _ => List()
          }
        }
        cc => {
          for {
            (_, callContext) <- getProductsIsPublic match {
                case false => authenticatedAccess(cc)
                case true => anonymousAccess(cc)
              }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- Future(Connector.connector.vend.getProduct(bankId, productCode)) map {
              unboxFullOrFail(_, callContext, ProductNotFoundByProductCode)
            }
            product <- Future(getProductTre(bankId, productCode))
          } yield {
            (JSONFactory310.createProductTreeJson(product, productCode.value), HttpCode.`200`(callContext))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getProducts,
      implementedInApiVersion,
      "getProducts",
      "GET",
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
         |URL params example: /banks/some-bank-id/products?manager=John&count=8
         |
         |${authenticationRequiredMessage(!getProductsIsPublic)}""".stripMargin,
      emptyObjectJson,
      productsJsonV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
        ProductNotFoundByProductCode,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle)
    )

    lazy val getProducts : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: Nil JsonGet req => {
        cc => {
          for {
            (_, callContext) <- getProductsIsPublic match {
                case false => authenticatedAccess(cc)
                case true => anonymousAccess(cc)
              }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            params = req.params.toList.map(kv => GetProductsParam(kv._1, kv._2))
            products <- Future(Connector.connector.vend.getProducts(bankId, params)) map {
              unboxFullOrFail(_, callContext, ProductNotFoundByProductCode)
            }
          } yield {
            (JSONFactory310.createProductsJson(products), HttpCode.`200`(callContext))
          }
        }
      }
    }






    val accountAttributeGeneralInfo =
      s"""
         |Account Attributes are used to describe a financial Product with a list of typed key value pairs.
         |
         |Each Account Attribute is linked to its Account by ACCOUNT_ID
         |
         |
       """.stripMargin

    resourceDocs += ResourceDoc(
      createAccountAttribute,
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
         |${authenticationRequiredMessage(true)}
         |
         |""",
      accountAttributeJson,
      accountAttributeResponseJson,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagAccount, apiTagNewStyle),
      Some(List(canCreateAccountAttributeAtOneBank))
    )

    lazy val createAccountAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "accounts" :: accountId :: "products" :: productCode :: "attribute" :: Nil JsonPost json -> _=> {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), callContext)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, ApiRole.canCreateAccountAttributeAtOneBank, callContext)
            _  <- Future(Connector.connector.vend.getProduct(BankId(bankId), ProductCode(productCode))) map {
              getFullBoxOrFail(_, callContext, ProductNotFoundByProductCode + " {" + productCode + "}", 400)
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $AccountAttributeJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AccountAttributeJson]
            }
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${AccountAttributeType.DOUBLE}(2012-04-23), ${AccountAttributeType.STRING}(TAX_NUMBER), ${AccountAttributeType.INTEGER}(123) and ${AccountAttributeType.DATE_WITH_DAY}(2012-04-23)"
            accountAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
              AccountAttributeType.withName(postedData.`type`)
            }
            
            (accountAttribute, callContext) <- NewStyle.function.createOrUpdateAccountAttribute(
              BankId(bankId),
              AccountId(accountId),
              ProductCode(productCode),
              None,
              postedData.name,
              accountAttributeType,
              postedData.value,
              callContext: Option[CallContext]
            )
          } yield {
            (createAccountAttributeJson(accountAttribute), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateAccountAttribute,
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
         |${authenticationRequiredMessage(true)}
         |
         |""",
      accountAttributeJson,
      accountAttributeResponseJson,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagAccount, apiTagNewStyle),
      Some(List(canUpdateAccountAttribute))
    )

    lazy val updateAccountAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "accounts" :: accountId :: "products" :: productCode :: "attributes" :: accountAttributeId :: Nil JsonPut json -> _=> {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canUpdateAccountAttribute, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $AccountAttributeJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AccountAttributeJson]
            }
            
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${AccountAttributeType.DOUBLE}(2012-04-23), ${AccountAttributeType.STRING}(TAX_NUMBER), ${AccountAttributeType.INTEGER}(123) and ${AccountAttributeType.DATE_WITH_DAY}(2012-04-23)"
            accountAttributeType <- NewStyle.function.tryons(failMsg, 400, callContext) {
              AccountAttributeType.withName(postedData.`type`)
            }
            
            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), callContext)
            (_, callContext) <- NewStyle.function.getProduct(BankId(bankId), ProductCode(productCode), callContext)
            (_, callContext) <- NewStyle.function.getAccountAttributeById(accountAttributeId, callContext)
            

            (accountAttribute, callContext) <- NewStyle.function.createOrUpdateAccountAttribute(
              BankId(bankId),
              AccountId(accountId),
              ProductCode(productCode),
              Some(accountAttributeId),
              postedData.name,
              accountAttributeType,
              postedData.value,
              callContext: Option[CallContext]
            )
          } yield {
            (createAccountAttributeJson(accountAttribute), HttpCode.`201`(callContext))
          }
      }
    }    







    resourceDocs += ResourceDoc(
      createProductCollection,
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

         |${authenticationRequiredMessage(true) }
         |
         |
         |""",
      putProductCollectionsV310,
      productCollectionsJsonV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProductCollection, apiTagProduct, apiTagNewStyle),
      Some(List(canMaintainProductCollection))
    )

    lazy val createProductCollection: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "product-collections" :: collectionCode :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canMaintainProductCollection, callContext)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutProductCollectionsV310 "
            product <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutProductCollectionsV310]
            }
            products <- Future(Connector.connector.vend.getProducts(bankId)) map {
              connectorEmptyResponse(_, callContext)
            }
            _ <- Helper.booleanToFuture(ProductNotFoundByProductCode + " {" + (product.parent_product_code :: product.children_product_codes).mkString(", ") + "}") {
              val existingCodes = products.map(_.code.value)
              val codes = product.parent_product_code :: product.children_product_codes
              codes.forall(i => existingCodes.contains(i))
            }
            (productCollection, callContext) <- NewStyle.function.getOrCreateProductCollection(
              collectionCode,
              List(product.parent_product_code),
              callContext
            )
            (productCollectionItems, callContext) <- NewStyle.function.getOrCreateProductCollectionItems(
              collectionCode,
              product.children_product_codes,
              callContext
            )
          } yield {
            (createProductCollectionsJson(productCollection, productCollectionItems), HttpCode.`201`(callContext))
          }

      }
    }




    resourceDocs += ResourceDoc(
      getProductCollection,
      implementedInApiVersion,
      nameOf(getProductCollection),
      "GET",
      "/banks/BANK_ID/product-collections/COLLECTION_CODE",
      "Get Product Collection",
      s"""Returns information about the financial Product Collection specified by BANK_ID and COLLECTION_CODE:
         |
          """,
      emptyObjectJson,
      productCollectionJsonTreeV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UnknownError
      ),
      List(apiTagProductCollection, apiTagProduct, apiTagNewStyle)
    )

    lazy val getProductCollection : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "product-collections" :: collectionCode :: Nil JsonGet _ => {
        cc => {
          for {
            (_, callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (payload, callContext) <- NewStyle.function.getProductCollectionItemsTree(collectionCode, bankId.value, callContext)
          } yield {
            (createProductCollectionsTreeJson(payload), HttpCode.`200`(callContext))
          }
        }
      }
    }

    // Delete Branch
    private[this] val deleteBranchEntitlementsRequiredForSpecificBank = CanDeleteBranch :: Nil
    private[this] val deleteBranchEntitlementsRequiredForAnyBank = CanDeleteBranchAtAnyBank :: Nil
    private[this] val deleteBranchEntitlementsRequiredText = UserHasMissingRoles + deleteBranchEntitlementsRequiredForSpecificBank.mkString(" and ") + " entitlements are required OR " + deleteBranchEntitlementsRequiredForAnyBank.mkString(" and ")

    resourceDocs += ResourceDoc(
      deleteBranch,
      implementedInApiVersion,
      nameOf(deleteBranch),
      "DELETE",
      "/banks/BANK_ID/branches/BRANCH_ID",
      "Delete Branch",
      s"""Delete Branch from given Bank.
         |
         |${authenticationRequiredMessage(true) }
         |
         |""",
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        BankNotFound,
        InsufficientAuthorisationToDeleteBranch,
        UnknownError
      ),
      List(apiTagBranch, apiTagNewStyle),
      Some(List(canDeleteBranch,canDeleteBranchAtAnyBank))
    )

    lazy val deleteBranch: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" :: BranchId(branchId) :: Nil JsonDelete _  => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            allowedEntitlements = canDeleteBranch ::canDeleteBranchAtAnyBank:: Nil
            allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = UserHasMissingRoles + allowedEntitlementsTxt)(bankId.value, u.userId, allowedEntitlements, callContext)
            (branch, callContext) <- NewStyle.function.getBranch(bankId, branchId, callContext)
            (result, callContext) <- NewStyle.function.deleteBranch(branch, callContext)
          } yield {
            (Full(result), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      createMeeting,
      implementedInApiVersion,
      "createMeeting",
      "POST",
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
      createMeetingJsonV310,
      meetingJsonV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagMeeting, apiTagKyc, apiTagCustomer, apiTagUser, apiTagExperimental, apiTagNewStyle))
    
    lazy val createMeeting: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "meetings" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $CreateMeetingJson "
            createMeetingJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[CreateMeetingJsonV310]
            }
            //           These following are only for `tokbox` stuff, for now, just ignore it.          
            //            _ <- APIUtil.getPropsValue("meeting.tokbox_api_key") ~> APIFailure(MeetingApiKeyNotConfigured, 403)
            //            _ <- APIUtil.getPropsValue("meeting.tokbox_api_secret") ~> APIFailure(MeetingApiSecretNotConfigured, 403)
            //            u <- cc.user ?~! UserNotLoggedIn
            //            _ <- tryo(assert(isValidID(bankId.value)))?~! InvalidBankIdFormat
            //            (bank, callContext) <- Bank(bankId, Some(cc)) ?~! BankNotFound
            //            postedData <- tryo {json.extract[CreateMeetingJson]} ?~! InvalidJsonFormat
            //            now = Calendar.getInstance().getTime()
            //            sessionId <- tryo{code.opentok.OpenTokUtil.getSession.getSessionId()}
            //            customerToken <- tryo{code.opentok.OpenTokUtil.generateTokenForPublisher(60)}
            //            staffToken <- tryo{code.opentok.OpenTokUtil.generateTokenForModerator(60)}
            //The following three are just used for Tokbox 
            sessionId = ""
            customerToken =""
            staffToken = ""
  
            creator = ContactDetails(createMeetingJson.creator.name,createMeetingJson.creator.mobile_phone,createMeetingJson.creator.email_address)
            invitees  = createMeetingJson.invitees.map(
              invitee =>
                Invitee(
                  ContactDetails(invitee.contact_details.name, invitee.contact_details.mobile_phone,invitee.contact_details.email_address),
                  invitee.status))
            (meeting, callContext) <- NewStyle.function.createMeeting(
              bank.bankId,
              u,
              u,
              createMeetingJson.provider_id,
              createMeetingJson.purpose_id,
              createMeetingJson.date,
              sessionId,
              customerToken,
              staffToken,
              creator,
              invitees,
              callContext
            )
          } yield {
            (JSONFactory310.createMeetingJson(meeting), HttpCode.`201`(callContext))
          }
      }
    }
    
    
    resourceDocs += ResourceDoc(
      getMeetings,
      implementedInApiVersion,
      "getMeetings",
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
      emptyObjectJson,
      meetingsJsonV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UnknownError),
      List(apiTagMeeting, apiTagKyc, apiTagCustomer, apiTagUser, apiTagExperimental, apiTagNewStyle))

    lazy val getMeetings: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "meetings" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (meetings, callContext) <- NewStyle.function.getMeetings(bank.bankId, u, callContext)
          } yield {
            (createMeetingsJson(meetings), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getMeeting,
      implementedInApiVersion,
      "getMeeting",
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
      emptyObjectJson,
      meetingJsonV310,
      List(
        UserNotLoggedIn, 
        BankNotFound, 
        MeetingNotFound,
        UnknownError
      ),
      List(apiTagMeeting, apiTagKyc, apiTagCustomer, apiTagUser, apiTagExperimental, apiTagNewStyle))

    lazy val getMeeting: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "meetings" :: meetingId :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (meeting, callContext) <- NewStyle.function.getMeeting(bank.bankId, u, meetingId, callContext)
          } yield {
            (JSONFactory310.createMeetingJson(meeting), HttpCode.`200`(callContext))
          }
      }
    }

    
    resourceDocs += ResourceDoc(
      getServerJWK,
      implementedInApiVersion,
      "getServerJWK",
      "GET",
      "/certs",
      "Get JSON Web Key (JWK)",
      """Get the server's public JSON Web Key (JWK) set and certificate chain.
        | It is required by client applications to validate ID tokens, self-contained access tokens and other issued objects.
        |
      """.stripMargin,
      emptyObjectJson,
      severJWK,
      List(
        UnknownError
      ),
      List(apiTagApi, apiTagPSD2AIS, apiTagPsd2, apiTagNewStyle))

    lazy val getServerJWK: OBPEndpoint = {
      case "certs" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
          } yield {
            (parse(CertificateUtil.convertRSAPublicKeyToAnRSAJWK()), HttpCode.`200`(callContext))
          }
      }
    }
    
     resourceDocs += ResourceDoc(
      getMessageDocsSwagger,
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
      emptyObjectJson,
      messageDocsJson,
      List(UnknownError),
      List(apiTagDocumentation, apiTagApi, apiTagNewStyle)
    )

    lazy val getMessageDocsSwagger: OBPEndpoint = {
      case "message-docs" :: restConnectorVersion ::"swagger2.0" :: Nil JsonGet _ => {
          val (resourceDocTags, partialFunctions, languageParam, contentParam, apiCollectionIdParam) = ResourceDocsAPIMethodsUtil.getParams()
        cc => {
          for {
            (_, callContext) <- anonymousAccess(cc)
            messageDocsSwagger = RestConnector_vMar2019.messageDocs.map(toResourceDoc).toList
            resourceDocListFiltered = ResourceDocsAPIMethodsUtil.filterResourceDocs(messageDocsSwagger, resourceDocTags, partialFunctions)
            json <- Future {SwaggerJSONFactory.createSwaggerResourceDoc(resourceDocListFiltered, ApiVersion.v3_1_0)}
            //For this connector swagger, it share some basic fields with api swagger, eg: BankId, AccountId. So it need to merge here.
            allSwaggerDefinitionCaseClasses = MessageDocsSwaggerDefinitions.allFields++SwaggerDefinitionsJSON.allFields
            jsonAST <- Future{SwaggerJSONFactory.loadDefinitions(resourceDocListFiltered, allSwaggerDefinitionCaseClasses)}
          } yield {
            // Merge both results and return
            (Extraction.decompose(json) merge jsonAST, HttpCode.`200`(callContext))
          }
        }
      }
    }


    val generalObpConsentText : String =
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
        |Maximum time to live of the token is specified over props value consents.max_time_to_live. In case isn't defined default value is 3600 seconds.
        |
        |Example of POST JSON:
        |{
        |  "everything": false,
        |  "views": [
        |    {
        |      "bank_id": "GENODEM1GLS",
        |      "account_id": "8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0",
        |      "view_id": "owner"
        |    }
        |  ],
        |  "entitlements": [
        |    {
        |      "bank_id": "GENODEM1GLS",
        |      "role_name": "CanGetCustomer"
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

    resourceDocs += ResourceDoc(
      createConsentEmail,
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
         |A One Time Password (OTP) (AKA security challenge) is sent Out of band (OOB) to the User via the transport defined in SCA_METHOD
         |SCA_METHOD is typically "SMS" or "EMAIL". "EMAIL" is used for testing purposes.
         |
         |When the Consent is created, OBP (or a backend system) stores the challenge so it can be checked later against the value supplied by the User with the Answer Consent Challenge endpoint.
         |
         |$generalObpConsentText
         |
         |${authenticationRequiredMessage(true)}
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
         |      "view_id": "owner"
         |    }
         |  ],
         |  "entitlements": [
         |    {
         |      "bank_id": "GENODEM1GLS",
         |      "role_name": "CanGetCustomer"
         |    }
         |  ],
         |  "consumer_id": "7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
         |  "email": "eveline@example.com"
         |}
         |
         |""",
      postConsentEmailJsonV310,
      consentJsonV310,
      List(
        UserNotLoggedIn,
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
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagNewStyle :: Nil)

    resourceDocs += ResourceDoc(
      createConsentSms,
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
         |SCA_METHOD is typically "SMS" or "EMAIL". "EMAIL" is used for testing purposes.
         |
         |When the Consent is created, OBP (or a backend system) stores the challenge so it can be checked later against the value supplied by the User with the Answer Consent Challenge endpoint.
         |
         |$generalObpConsentText
         |
         |${authenticationRequiredMessage(true)}
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
         |      "view_id": "owner"
         |    }
         |  ],
         |  "entitlements": [
         |    {
         |      "bank_id": "GENODEM1GLS",
         |      "role_name": "CanGetCustomer"
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
        UserNotLoggedIn,
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
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 ::apiTagNewStyle :: Nil)

    lazy val createConsentEmail = createConsent
    lazy val createConsentSms = createConsent

    lazy val createConsent : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "my" :: "consents"  :: scaMethod :: Nil JsonPost json -> _  => {
        cc =>
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- Helper.booleanToFuture(ConsentAllowedScaMethods){
              List(StrongCustomerAuthentication.SMS.toString(), StrongCustomerAuthentication.EMAIL.toString()).exists(_ == scaMethod)
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostConsentBodyCommonJson "
            consentJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostConsentBodyCommonJson]
            }
            maxTimeToLive = APIUtil.getPropsAsIntValue(nameOfProperty="consents.max_time_to_live", defaultValue=3600)
            _ <- Helper.booleanToFuture(s"$ConsentMaxTTL ($maxTimeToLive)"){
              consentJson.time_to_live match {
                case Some(ttl) => ttl <= maxTimeToLive
                case _ => true
              }
            }
            requestedEntitlements = consentJson.entitlements
            myEntitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(user.userId)
            _ <- Helper.booleanToFuture(RolesAllowedInConsent){
              requestedEntitlements.forall(
                re => myEntitlements.getOrElse(Nil).exists(
                  e => e.roleName == re.role_name && e.bankId == re.bank_id
                )
              )
            }
            requestedViews = consentJson.views
            (_, assignedViews) <- Future(Views.views.vend.privateViewsUserCanAccess(user))
            _ <- Helper.booleanToFuture(ViewsAllowedInConsent){
              requestedViews.forall(
                rv => assignedViews.exists{
                  e => 
                    e.view_id == rv.view_id && 
                    e.bank_id == rv.bank_id && 
                    e.account_id == rv.account_id
                }
              )
            }
            (consumerId, applicationText) <- consentJson.consumer_id match {
              case Some(id) => NewStyle.function.checkConsumerByConsumerId(id, callContext) map {
                c => (Some(c.consumerId.get), c.description)
              }
              case None => Future(None, "Any application")
            }
            challengeAnswer = Props.mode match {
              case Props.RunModes.Test => Consent.challengeAnswerAtTestEnvironment
              case _ => Random.nextInt(99999999).toString()
            }
            createdConsent <- Future(Consents.consentProvider.vend.createConsent(user, challengeAnswer)) map {
              i => connectorEmptyResponse(i, callContext)
            }
            consentJWT = 
              Consent.createConsentJWT(
                user, 
                consentJson, 
                createdConsent.secret, 
                createdConsent.consentId, 
                consumerId,
                consentJson.valid_from,
                consentJson.time_to_live.getOrElse(3600)
              )
            _ <- Future(Consents.consentProvider.vend.setJsonWebToken(createdConsent.consentId, consentJWT)) map {
              i => connectorEmptyResponse(i, callContext)
            }
            challengeText = s"Your consent challenge : ${challengeAnswer}, Application: $applicationText"
            _ <- scaMethod match {
            case v if v == StrongCustomerAuthentication.EMAIL.toString => // Send the email
              for{
                failMsg <- Future {s"$InvalidJsonFormat The Json body should be the $PostConsentEmailJsonV310"}
                postConsentEmailJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
                  json.extract[PostConsentEmailJsonV310]
                }
                params = PlainMailBodyType(challengeText) :: List(To(postConsentEmailJson.email))
                _ <- Future{Mailer.sendMail(From("challenge@tesobe.com"), Subject("OBP Consent Challenge"), params :_*)}
              } yield Future{true}
            case v if v == StrongCustomerAuthentication.SMS.toString => // Not implemented
              for {
                failMsg <- Future {
                  s"$InvalidJsonFormat The Json body should be the $PostConsentPhoneJsonV310"
                }
                postConsentPhoneJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
                  json.extract[PostConsentPhoneJsonV310]
                }
                phoneNumber = postConsentPhoneJson.phone_number
                failMsg =s"$MissingPropsValueAtThisInstance sca_phone_api_key"
                smsProviderApiKey <- NewStyle.function.tryons(failMsg, 400, callContext) {
                  APIUtil.getPropsValue("sca_phone_api_key").openOrThrowException(s"")
                }
                failMsg = s"$MissingPropsValueAtThisInstance sca_phone_api_secret"
                smsProviderApiSecret <- NewStyle.function.tryons(failMsg, 400, callContext) {
                   APIUtil.getPropsValue("sca_phone_api_secret").openOrThrowException(s"")
                }
                client = new NexmoClient.Builder()
                  .apiKey(smsProviderApiKey)
                  .apiSecret(smsProviderApiSecret)
                  .build();
                messageText = challengeText;
                message = new TextMessage("OBP-API", phoneNumber, messageText);
                response <- Future{client.getSmsClient().submitMessage(message)}
                failMsg = s"$SmsServerNotResponding: $phoneNumber. Or Please to use EMAIL first." 
                _ <- Helper.booleanToFuture(failMsg) {
                  response.getMessages.get(0).getStatus == com.nexmo.client.sms.MessageStatus.OK
                }
              } yield Future{true}
            case _ =>Future{true}
            }
          } yield {
            (ConsentJsonV310(createdConsent.consentId, consentJWT, createdConsent.status), HttpCode.`201`(callContext))
          }
      }
    }
    
    
    resourceDocs += ResourceDoc(
      answerConsentChallenge,
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
         |${authenticationRequiredMessage(true)}
         |
         |""",
      PostConsentChallengeJsonV310(answer = "12345678"),
      ConsentChallengeJsonV310(
        consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
        jwt = "eyJhbGciOiJIUzI1NiJ9.eyJlbnRpdGxlbWVudHMiOltdLCJjcmVhdGVkQnlVc2VySWQiOiJhYjY1MzlhOS1iMTA1LTQ0ODktYTg4My0wYWQ4ZDZjNjE2NTciLCJzdWIiOiIyMWUxYzhjYy1mOTE4LTRlYWMtYjhlMy01ZTVlZWM2YjNiNGIiLCJhdWQiOiJlanpuazUwNWQxMzJyeW9tbmhieDFxbXRvaHVyYnNiYjBraWphanNrIiwibmJmIjoxNTUzNTU0ODk5LCJpc3MiOiJodHRwczpcL1wvd3d3Lm9wZW5iYW5rcHJvamVjdC5jb20iLCJleHAiOjE1NTM1NTg0OTksImlhdCI6MTU1MzU1NDg5OSwianRpIjoiMDlmODhkNWYtZWNlNi00Mzk4LThlOTktNjYxMWZhMWNkYmQ1Iiwidmlld3MiOlt7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAxIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifSx7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAyIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifV19.8cc7cBEf2NyQvJoukBCmDLT7LXYcuzTcSYLqSpbxLp4",
        status = "INITIATED"
      ),
      List(
        UserNotLoggedIn,
        BankNotFound,
        InvalidJsonFormat,
        InvalidConnectorResponse,
        UnknownError
      ),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagNewStyle :: Nil)

    lazy val answerConsentChallenge : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "consents"  :: consentId :: "challenge" :: Nil JsonPost json -> _  => {
        cc =>
          for {
            (_, callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostConsentChallengeJsonV310 "
            consentJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostConsentChallengeJsonV310]
            }
            consent <- Future(Consents.consentProvider.vend.checkAnswer(consentId, consentJson.answer)) map {
              i => connectorEmptyResponse(i, callContext)
            }
          } yield {
            (ConsentJsonV310(consent.consentId, consent.jsonWebToken, consent.status), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getConsents,
      implementedInApiVersion,
      "getConsents",
      "GET",
      "/banks/BANK_ID/my/consents",
      "Get Consents",
      s"""
         |$generalObpConsentText
         |
         |
         |
         |This endpoint gets the Consents that the current User created.
        |
        |${authenticationRequiredMessage(true)}
        |
      """.stripMargin,
      emptyObjectJson,
      consentsJsonV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UnknownError
      ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2, apiTagNewStyle))

    lazy val getConsents: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "my" :: "consents" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            consents <- Future(Consents.consentProvider.vend.getConsentsByUser(user.userId))
          } yield {
            (JSONFactory310.createConsentsJsonV310(consents), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      revokeConsent,
      implementedInApiVersion,
      "revokeConsent",
      "GET",
      "/banks/BANK_ID/my/consents/CONSENT_ID/revoke",
      "Revoke Consent",
      s"""
        |$generalObpConsentText
        |
        |
        |Revoke Consent for current user specified by CONSENT_ID
        |
        |
        |${authenticationRequiredMessage(true)}
        |
      """.stripMargin,
      emptyObjectJson,
      revokedConsentJsonV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UnknownError
      ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2, apiTagNewStyle))

    lazy val revokeConsent: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "my" :: "consents" :: consentId :: "revoke" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
              unboxFullOrFail(_, callContext, ConsentNotFound)
            }
            _ <- Helper.booleanToFuture(failMsg = ConsentNotFound) {
              consent.mUserId == user.userId
            }
            consent <- Future(Consents.consentProvider.vend.revoke(consentId)) map {
              i => connectorEmptyResponse(i, callContext)
            }
          } yield {
            (ConsentJsonV310(consent.consentId, consent.jsonWebToken, consent.status), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      createUserAuthContextUpdateRequest,
      implementedInApiVersion,
      nameOf(createUserAuthContextUpdateRequest),
      "POST",
      "/banks/BANK_ID/users/current/auth-context-updates/SCA_METHOD",
      "Create User Auth Context Update Request",
      s"""Create User Auth Context Update Request.
         |${authenticationRequiredMessage(true)}
         |
         |A One Time Password (OTP) (AKA security challenge) is sent Out of Band (OOB) to the User via the transport defined in SCA_METHOD
         |SCA_METHOD is typically "SMS" or "EMAIL". "EMAIL" is used for testing purposes.
         |
         |""",
      postUserAuthContextJson,
      userAuthContextUpdateJson,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        CreateUserAuthContextError,
        UnknownError
      ),
      List(apiTagUser, apiTagNewStyle),
      None
    )

    lazy val createUserAuthContextUpdateRequest : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "users" :: "current" ::"auth-context-updates" :: scaMethod :: Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            _ <- Helper.booleanToFuture(failMsg = ConsumerHasMissingRoles + CanCreateUserAuthContextUpdate) {
              checkScope(bankId.value, getConsumerPrimaryKey(callContext), ApiRole.canCreateUserAuthContextUpdate)
            }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- Helper.booleanToFuture(ConsentAllowedScaMethods){
              List(StrongCustomerAuthentication.SMS.toString(), StrongCustomerAuthentication.EMAIL.toString()).exists(_ == scaMethod)
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserAuthContextJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostUserAuthContextJson]
            }
            (_, callContext) <- NewStyle.function.findByUserId(user.userId, callContext)
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerNumber(postedData.value, bankId, callContext)
            (userAuthContextUpdate, callContext) <- NewStyle.function.createUserAuthContextUpdate(user.userId, postedData.key, postedData.value, callContext)
          } yield {
            scaMethod match {
              case v if v == StrongCustomerAuthentication.EMAIL.toString => // Send the email
                val params = PlainMailBodyType(userAuthContextUpdate.challenge) :: List(To(customer.email))
                Mailer.sendMail(
                  From("challenge@tesobe.com"),
                  Subject("Challenge request"),
                  params :_*
                )
              case v if v == StrongCustomerAuthentication.SMS.toString => // Not implemented
              case _ => // Not handled
            }
            (JSONFactory310.createUserAuthContextUpdateJson(userAuthContextUpdate), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      answerUserAuthContextUpdateChallenge,
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
      List(
        UserNotLoggedIn,
        BankNotFound,
        InvalidJsonFormat,
        InvalidConnectorResponse,
        UnknownError
      ),
      apiTagUser :: apiTagNewStyle :: Nil)

    lazy val answerUserAuthContextUpdateChallenge : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "users" :: "current" ::"auth-context-updates"  :: authContextUpdateId :: "challenge" :: Nil JsonPost json -> _  => {
        cc =>
          for {
            (_, callContext) <- authenticatedAccess(cc)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserAuthContextUpdateJsonV310 "
            postUserAuthContextUpdateJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostUserAuthContextUpdateJsonV310]
            }
            userAuthContextUpdate <- UserAuthContextUpdateProvider.userAuthContextUpdateProvider.vend.checkAnswer(authContextUpdateId, postUserAuthContextUpdateJson.answer) map {
              i => connectorEmptyResponse(i, callContext)
            }
            (_, callContext) <-
              userAuthContextUpdate.status match {
                case status if status == UserAuthContextUpdateStatus.ACCEPTED.toString => 
                  NewStyle.function.createUserAuthContext(
                    userAuthContextUpdate.userId, 
                    userAuthContextUpdate.key, 
                    userAuthContextUpdate.value, 
                    callContext).map(x => (Some(x._1), x._2))
                case _ =>
                  Future((None, callContext))
              }
            (_, callContext) <-
              userAuthContextUpdate.key match {
                case "CUSTOMER_NUMBER" =>
                  NewStyle.function.getOCreateUserCustomerLink(
                    bankId,
                    userAuthContextUpdate.value, // Customer number
                    userAuthContextUpdate.userId, 
                    callContext
                  )
                case _ =>
                  Future((None, callContext))
              }
          } yield {
            (createUserAuthContextUpdateJson(userAuthContextUpdate), HttpCode.`200`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      getSystemView,
      implementedInApiVersion,
      "getSystemView",
      "GET",
      "/system-views/VIEW_ID",
      "Get System View",
      s"""Get System View
         |
        |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      emptyObjectJson,
      viewJSONV220,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UnknownError
      ),
      List(apiTagSystemView, apiTagNewStyle),
      Some(List(canGetSystemView))
    )

    lazy val getSystemView: OBPEndpoint = {
      case "system-views" :: viewId :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", user.userId, canGetSystemView, callContext)
            view <- NewStyle.function.systemView(ViewId(viewId), callContext)
          } yield {
            (JSONFactory310.createViewJSON(view), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      createSystemView,
      implementedInApiVersion,
      nameOf(createSystemView),
      "POST",
      "/system-views",
      "Create System View",
      s"""Create a system view
        |
        | ${authenticationRequiredMessage(true)} and the user needs to have access to the $canCreateSystemView entitlement.
        | The 'alias' field in the JSON can take one of two values:
        |
        | * _public_: to use the public alias if there is one specified for the other account.
        | * _private_: to use the public alias if there is one specified for the other account.
        |
        | * _''(empty string)_: to use no alias; the view shows the real name of the other account.
        |
        | The 'hide_metadata_if_alias_used' field in the JSON can take boolean values. If it is set to `true` and there is an alias on the other account then the other accounts' metadata (like more_info, url, image_url, open_corporates_url, etc.) will be hidden. Otherwise the metadata will be shown.
        |
        | The 'allowed_actions' field is a list containing the name of the actions allowed on this view, all the actions contained will be set to `true` on the view creation, the rest will be set to `false`.
        | 
        | Please note that system views cannot be public. In case you try to set it you will get the error $SystemViewCannotBePublicError
        | """,
      SwaggerDefinitionsJSON.createSystemViewJson,
      viewJsonV300,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagSystemView, apiTagNewStyle),
      Some(List(canCreateSystemView))
    )

    lazy val createSystemView : OBPEndpoint = {
      //creates a system view
      case "system-views" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", user.userId, canCreateSystemView, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $CreateViewJson "
            createViewJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[CreateViewJson]
            }
            _ <- Helper.booleanToFuture(SystemViewCannotBePublicError, failCode=400) {
              createViewJson.is_public == false
            }
            view <- NewStyle.function.createSystemView(createViewJson, callContext)
          } yield {
            (JSONFactory310.createViewJSON(view),  HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteSystemView,
      implementedInApiVersion,
      "deleteSystemView",
      "DELETE",
      "/system-views/VIEW_ID",
      "Delete System View",
      "Deletes the system view specified by VIEW_ID",
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError,
        "user does not have owner access"
      ),
      List(apiTagSystemView, apiTagNewStyle),
      Some(List(canCreateSystemView))
    )

    lazy val deleteSystemView: OBPEndpoint = {
      //deletes a view on an bank account
      case "system-views" :: viewId :: Nil JsonDelete req => {
        cc =>
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", user.userId, canDeleteSystemView, callContext)
            _ <- NewStyle.function.systemView(ViewId(viewId), callContext)
            view <- NewStyle.function.deleteSystemView(ViewId(viewId), callContext)
          } yield {
            (Full(view),  HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      updateSystemView,
      implementedInApiVersion,
      nameOf(updateSystemView),
      "PUT",
      "/system-views/VIEW_ID",
      "Update System View",
      s"""Update an existing view on a bank account
         |
        |${authenticationRequiredMessage(true)} and the user needs to have access to the owner view.
         |
        |The json sent is the same as during view creation (above), with one difference: the 'name' field
         |of a view is not editable (it is only set when a view is created)""",
      updateSystemViewJson310,
      viewJsonV300,
      List(
        InvalidJsonFormat,
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError
      ),
      List(apiTagSystemView, apiTagNewStyle),
      Some(List(canUpdateSystemView))
    )

    lazy val updateSystemView : OBPEndpoint = {
      //updates a view on a bank account
      case "system-views" :: viewId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(user), callContext) <-  authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", user.userId, canUpdateSystemView, callContext)
            updateJson <- Future { tryo{json.extract[UpdateViewJSON]} } map {
              val msg = s"$InvalidJsonFormat The Json body should be the $UpdateViewJSON "
              x => unboxFullOrFail(x, callContext, msg)
            }
            _ <- Helper.booleanToFuture(SystemViewCannotBePublicError, failCode=400) {
              updateJson.is_public == false
            }
            _ <- NewStyle.function.systemView(ViewId(viewId), callContext)
            updatedView <- NewStyle.function.updateSystemView(ViewId(viewId), updateJson, callContext)
          } yield {
            (JSONFactory310.createViewJSON(updatedView), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getOAuth2ServerJWKsURIs,
      implementedInApiVersion,
      "getOAuth2ServerJWKsURIs",
      "GET",
      "/jwks-uris",
      "Get JSON Web Key (JWK) URIs",
      """Get the OAuth2 server's public JSON Web Key (JWK) URIs.
        | It is required by client applications to validate ID tokens, self-contained access tokens and other issued objects.
        |
      """.stripMargin,
      emptyObjectJson,
      oAuth2ServerJwksUrisJson,
      List(
        UnknownError
      ),
      List(apiTagApi, apiTagNewStyle))

    lazy val getOAuth2ServerJWKsURIs: OBPEndpoint = {
      case "jwks-uris" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
          } yield {
            (getOAuth2ServerJwksUrisJson(), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getMethodRoutings,
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
      emptyObjectJson,
      ListResult(
        "method_routings",
        (List(MethodRoutingCommons("getBanks", "rest_vMar2019", false, Some("some_bank_.*"), List(MethodRoutingParam("url", "http://mydomain.com/xxx")), Some("method-routing-id"))))
      )
    ,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagMethodRouting, apiTagApi, apiTagNewStyle),
      Some(List(canGetMethodRoutings))
    )


    lazy val getMethodRoutings: OBPEndpoint = {
      case "management" :: "method_routings":: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetMethodRoutings, callContext)
            methodRoutings <- NewStyle.function.getMethodRoutingsByMethodName(req.param("method_name"))
          } yield {
            val definedMethodRoutings = methodRoutings.sortWith(_.methodName < _.methodName)
            val listCommons: List[MethodRoutingCommons] = req.param("active") match {
              case Full("true") => (definedMethodRoutings ++ getDefaultMethodRountings ).sortWith(_.methodName < _.methodName)
              case _ => definedMethodRoutings
            }
            (ListResult("method_routings", listCommons.map(_.toJson)), HttpCode.`200`(callContext))
          }
      }
    }

    /**
      * get all default methodRountings,  
      * @return all default methodRounting#methodName, those just in mapped connector
      */
    private def getDefaultMethodRountings = {

      val methodRegex = """method \S+(?<!\$default\$\d{0,10})""".r.pattern

      ReflectUtils.getType(LocalMappedConnector)
        .decls
        .filter(it => methodRegex.matcher(it.toString).matches())
        .filter(_.asMethod.isPublic)
        .map(_.asMethod)
        .map(it => MethodRoutingCommons(
          methodName = it.name.toString,
          connectorName = "mapped",
          isBankIdExactMatch = false,
          bankIdPattern = Some("*"),
          parameters= List.empty[MethodRoutingParam],
          methodRoutingId  = Some(""),
      ))
        .toList
    }

    private val supportedConnectorNames = NewStyle.function.getSupportedConnectorNames().mkString("[", " | ", "]")
    resourceDocs += ResourceDoc(
      createMethodRouting,
      implementedInApiVersion,
      nameOf(createMethodRouting),
      "POST",
      "/management/method_routings",
      "Create MethodRouting",
      s"""Create a MethodRouting.
        |
        |
        |${authenticationRequiredMessage(true)}
        |
        |Explaination of Fields:
        |
        |* method_name is required String value, current supported value: $supportedConnectorNames
        |* connector_name is required String value
        |* is_bank_id_exact_match is required boolean value, if bank_id_pattern is exact bank_id value, this value is true; if bank_id_pattern is null or a regex, this value is false
        |* bank_id_pattern is optional String value, it can be null, a exact bank_id or a regex
        |* parameters is optional array of key value pairs. You can set some paremeters for this method
        |
        |note:
        |
        |* if bank_id_pattern is regex, special characters need to do escape, for example: bank_id_pattern = "some\\-id_pattern_\\d+"
        |
        |If connector name start with rest, parameters can contain "outBoundMapping" and "inBoundMapping", convert OutBound and InBound json structure.
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
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        InvalidConnectorName,
        InvalidConnectorMethodName,
        UnknownError
      ),
      List(apiTagMethodRouting, apiTagApi, apiTagNewStyle),
      Some(List(canCreateMethodRouting)))

    lazy val createMethodRouting : OBPEndpoint = {
      case "management" :: "method_routings" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateMethodRouting, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the ${classOf[MethodRoutingCommons]} "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              val entity = json.extract[MethodRoutingCommons]
              entity.bankIdPattern match {
                case Some(v) if(StringUtils.isBlank(v) || v.trim == "*") => entity.copy(bankIdPattern = Some(MethodRouting.bankIdPatternMatchAny))
                case v => entity
              }
            }
            _ <-NewStyle.function.tryons(InvalidOutBoundMapping, 400, callContext){
              postedData.getOutBoundMapping
            }
            _ <-NewStyle.function.tryons(InvalidInBoundMapping, 400, callContext){
              postedData.getInBoundMapping
            }
            connectorName = postedData.connectorName
            methodName = postedData.methodName
            _ <- Helper.booleanToFuture(s"$InvalidConnectorName please check connectorName: $connectorName or the connector($connectorName) is not supported for this sandbox. ", failCode=400) {
              NewStyle.function.getConnectorByName(connectorName).isDefined
            }
            _ <- Helper.booleanToFuture(s"$InvalidConnectorMethodName please check methodName: $methodName", failCode=400) {
              NewStyle.function.getConnectorMethod(connectorName, methodName).isDefined
            }
            invalidRegexMsg = s"$InvalidBankIdRegex The bankIdPattern is invalid regex, bankIdPatten: ${postedData.bankIdPattern.orNull} "
            _ <- NewStyle.function.tryons(invalidRegexMsg, 400, callContext) {
              // if do fuzzy match and bankIdPattern not empty, do check the regex is valid
              if(!postedData.isBankIdExactMatch && postedData.bankIdPattern.isDefined) {
                Pattern.compile(postedData.bankIdPattern.get)
              }
            }
            _ <- NewStyle.function.checkMethodRoutingAlreadyExists(postedData, callContext)
            Full(methodRouting) <- NewStyle.function.createOrUpdateMethodRouting(postedData)
          } yield {
            val commonsData: MethodRoutingCommons = methodRouting
            (commonsData.toJson, HttpCode.`201`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      updateMethodRouting,
      implementedInApiVersion,
      nameOf(updateMethodRouting),
      "PUT",
      "/management/method_routings/METHOD_ROUTING_ID",
      "Update MethodRouting",
      s"""Update a MethodRouting.
        |
        |
        |${authenticationRequiredMessage(true)}
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
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        InvalidConnectorName,
        InvalidConnectorMethodName,
        UnknownError
      ),
      List(apiTagMethodRouting, apiTagApi, apiTagNewStyle),
      Some(List(canUpdateMethodRouting)))

    lazy val updateMethodRouting : OBPEndpoint = {
      case "management" :: "method_routings" :: methodRoutingId :: Nil JsonPut  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canUpdateMethodRouting, callContext)

            failMsg = s"$InvalidJsonFormat The Json body should be the ${classOf[MethodRoutingCommons]} "
            putData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              val entity = json.extract[MethodRoutingCommons].copy(methodRoutingId = Some(methodRoutingId))
              entity.bankIdPattern match {
                case Some(v) if(StringUtils.isBlank(v) || v.trim == "*") => entity.copy(bankIdPattern = Some(MethodRouting.bankIdPatternMatchAny))
                case v => entity
              }
            }
            _ <-NewStyle.function.tryons(InvalidOutBoundMapping, 400, callContext){
              putData.getOutBoundMapping
            }
            _ <-NewStyle.function.tryons(InvalidInBoundMapping, 400, callContext){
              putData.getInBoundMapping
            }
            connectorName = putData.connectorName
            methodName = putData.methodName
            _ <- Helper.booleanToFuture(s"$InvalidConnectorName please check connectorName: $connectorName", failCode=400) {
              NewStyle.function.getConnectorByName(connectorName).isDefined
            }
            _ <- Helper.booleanToFuture(s"$InvalidConnectorMethodName please check methodName: $methodName", failCode=400) {
              NewStyle.function.getConnectorMethod(connectorName, methodName).isDefined
            }
            (_, _) <- NewStyle.function.getMethodRoutingById(methodRoutingId, callContext)

            invalidRegexMsg = s"$InvalidBankIdRegex The bankIdPattern is invalid regex, bankIdPatten: ${putData.bankIdPattern.orNull} "
            _ <- NewStyle.function.tryons(invalidRegexMsg, 400, callContext) {
              // if do fuzzy match and bankIdPattern not empty, do check the regex is valid
              if(!putData.isBankIdExactMatch && putData.bankIdPattern.isDefined) {
                Pattern.compile(putData.bankIdPattern.get)
              }
            }

            Full(methodRouting) <- NewStyle.function.createOrUpdateMethodRouting(putData)
          } yield {
            val commonsData: MethodRoutingCommons = methodRouting
            (commonsData.toJson, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteMethodRouting,
      implementedInApiVersion,
      nameOf(deleteMethodRouting),
      "DELETE",
      "/management/method_routings/METHOD_ROUTING_ID",
      "Delete MethodRouting",
      s"""Delete a MethodRouting specified by METHOD_ROUTING_ID.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagMethodRouting, apiTagApi, apiTagNewStyle),
      Some(List(canDeleteMethodRouting)))

    lazy val deleteMethodRouting : OBPEndpoint = {
      case "management" :: "method_routings" :: methodRoutingId ::  Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteMethodRouting, callContext)
            deleted: Box[Boolean] <- NewStyle.function.deleteMethodRouting(methodRoutingId)
          } yield {
            (deleted, HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      updateCustomerEmail,
      implementedInApiVersion,
      nameOf(updateCustomerEmail),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/email",
      "Update the email of a Customer",
      s"""Update an email of the Customer specified by CUSTOMER_ID.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        |""",
      putUpdateCustomerEmailJsonV310,
      customerJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(canUpdateCustomerEmail :: Nil)
    )

    lazy val updateCustomerEmail : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "email" ::  Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canUpdateCustomerEmail, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutUpdateCustomerEmailJsonV310 "
            putData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutUpdateCustomerEmailJsonV310]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (customer, callContext) <- NewStyle.function.updateCustomerScaData(
              customerId,
              None,
              Some(putData.email),
              None,
              callContext)
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateCustomerNumber,
      implementedInApiVersion,
      nameOf(updateCustomerNumber),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/number",
      "Update the number of a Customer",
      s"""Update the number of the Customer specified by CUSTOMER_ID.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        |""",
      putUpdateCustomerNumberJsonV310,
      customerJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(canUpdateCustomerNumber :: Nil)
    )

    lazy val updateCustomerNumber : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "number" ::  Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canUpdateCustomerNumber, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutUpdateCustomerNumberJsonV310 "
            putData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutUpdateCustomerNumberJsonV310]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            
            (customerNumberIsAvalible, callContext) <- NewStyle.function.checkCustomerNumberAvailable(bankId, putData.customer_number, callContext)
            //There should not be a customer for this number, If there is, then we throw the exception. 
            _ <- Helper.booleanToFuture(failMsg= s"$CustomerNumberAlreadyExists Current customer_number(${putData.customer_number}) and Current bank_id(${bankId.value})" ) {customerNumberIsAvalible}
            
            (customer, callContext) <- NewStyle.function.updateCustomerScaData(
              customerId,
              None,
              None,
              Some(putData.customer_number),
              callContext)
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`200`(callContext))
          }
      }
    }
    
    
    resourceDocs += ResourceDoc(
      updateCustomerMobileNumber,
      implementedInApiVersion,
      nameOf(updateCustomerMobileNumber),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/mobile-number",
      "Update the mobile number of a Customer",
      s"""Update the mobile number of the Customer specified by CUSTOMER_ID.
        |
        |
        |${authenticationRequiredMessage(true)}
        |
        |""",
      putUpdateCustomerMobileNumberJsonV310,
      customerJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(canUpdateCustomerMobilePhoneNumber :: Nil)
    )

    lazy val updateCustomerMobileNumber : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "mobile-number" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canUpdateCustomerMobilePhoneNumber, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutUpdateCustomerMobilePhoneNumberJsonV310 "
            putData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutUpdateCustomerMobilePhoneNumberJsonV310]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (customer, callContext) <- NewStyle.function.updateCustomerScaData(
              customerId,
              Some(putData.mobile_phone_number),
              None,
              None,
              callContext)
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`200`(callContext))
          }
      }
    }  
    
    resourceDocs += ResourceDoc(
      updateCustomerIdentity,
      implementedInApiVersion,
      nameOf(updateCustomerIdentity),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/identity",
      "Update the identity data of a Customer",
      s"""Update the identity data of the Customer specified by CUSTOMER_ID.
        |
        |
        |${authenticationRequiredMessage(true)}
        |
        |""",
      putUpdateCustomerIdentityJsonV310,
      customerJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(canUpdateCustomerIdentity :: Nil)
    )
    lazy val updateCustomerIdentity : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "identity" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canUpdateCustomerIdentity, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutUpdateCustomerIdentityJsonV310 "
            putData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutUpdateCustomerIdentityJsonV310]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (customer, callContext) <- NewStyle.function.updateCustomerGeneralData(
              customerId,
              Some(putData.legal_name),
              None,
              Some(putData.date_of_birth),
              None,
              None,
              None,
              None,
              Some(putData.title),
              None,
              Some(putData.name_suffix),
              callContext
            )
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      updateCustomerCreditLimit,
      implementedInApiVersion,
      nameOf(updateCustomerCreditLimit),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/credit-limit",
      "Update the credit limit of a Customer",
      s"""Update the credit limit of the Customer specified by CUSTOMER_ID.
        |
        |
        |${authenticationRequiredMessage(true)}
        |
        |""",
      putUpdateCustomerCreditLimitJsonV310,
      customerJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(canUpdateCustomerCreditLimit :: Nil)
    )

    lazy val updateCustomerCreditLimit : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "credit-limit" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canUpdateCustomerCreditLimit, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutUpdateCustomerCreditLimitJsonV310 "
            putData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutUpdateCustomerCreditLimitJsonV310]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (customer, callContext) <- NewStyle.function.updateCustomerCreditData(
              customerId,
              None,
              None,
              Some(putData.credit_limit),
              callContext)
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      updateCustomerCreditRatingAndSource,
      implementedInApiVersion,
      nameOf(updateCustomerCreditRatingAndSource),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/credit-rating-and-source",
      "Update the credit rating and source of a Customer",
      s"""Update the credit rating and source of the Customer specified by CUSTOMER_ID.
        |
        |
        |${authenticationRequiredMessage(true)}
        |
        |""",
      putUpdateCustomerCreditRatingAndSourceJsonV310,
      customerJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(canUpdateCustomerCreditRatingAndSource :: Nil)
    )

    lazy val updateCustomerCreditRatingAndSource : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "credit-rating-and-source" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canUpdateCustomerCreditRatingAndSource, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutUpdateCustomerCreditRatingAndSourceJsonV310 "
            putData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutUpdateCustomerCreditRatingAndSourceJsonV310]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (customer, callContext) <- NewStyle.function.updateCustomerCreditData(
              customerId,
              Some(putData.credit_rating),
              Some(putData.credit_source),
              None,
              callContext)
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateAccount,
      implementedInApiVersion,
      nameOf(updateAccount),
      "PUT",
      "/management/banks/BANK_ID/accounts/ACCOUNT_ID",
      "Update Account",
      s"""Update the account. 
         |
         |${authenticationRequiredMessage(true)}
         |
       """.stripMargin,
      updateAccountRequestJsonV310,
      updateAccountResponseJsonV310,
      List(InvalidJsonFormat, UserNotLoggedIn, UnknownError, BankAccountNotFound),
      List(apiTagAccount, apiTagNewStyle),
      Some(List(canUpdateAccount))
    )

    lazy val updateAccount : OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canUpdateAccount, callContext)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (bankAccount, callContext) <- NewStyle.function.getBankAccount(bankId, accountId, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $UpdateAccountRequestJsonV310 "
            consentJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[UpdateAccountRequestJsonV310]
            }
            _ <- Helper.booleanToFuture(s"$UpdateBankAccountException Duplication detected in account routings, please specify only one value per routing scheme"){
              consentJson.account_routings.map(_.scheme).distinct.size == consentJson.account_routings.size
            }
            alreadyExistAccountRoutings <- Future.sequence(consentJson.account_routings.map(accountRouting =>
              NewStyle.function.getAccountRouting(Some(bankId), accountRouting.scheme, accountRouting.address, callContext)
                .map {
                  // If we find an already existing account routing linked to the account, it just mean we don't want to update it
                  case bankAccount if !(bankAccount._1.bankId == bankId && bankAccount._1.accountId == accountId) => Some(accountRouting)
                  case _ => None
                } fallbackTo Future.successful(None)
            ))
            alreadyExistingAccountRouting = alreadyExistAccountRoutings.collect {
              case Some(accountRouting) => s"bankId: $bankId, scheme: ${accountRouting.scheme}, address: ${accountRouting.address}"
            }
            _ <- Helper.booleanToFuture(s"$AccountRoutingAlreadyExist (${alreadyExistingAccountRouting.mkString("; ")})") {
              alreadyExistingAccountRouting.isEmpty
            }
            (bankAccount,callContext) <- NewStyle.function.updateBankAccount(
              bankId,
              accountId,
              consentJson.`type`,
              consentJson.label,
              consentJson.branch_id,
              consentJson.account_routings.map(r => AccountRouting(r.scheme, r.address)),
              callContext
            )
          } yield {
            (JSONFactory310.createUpdateResponseAccountJson(bankAccount), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      addCardForBank,
      implementedInApiVersion,
      nameOf(addCardForBank),
      "POST",
      "/management/banks/BANK_ID/cards",
      "Create Card",
      s"""Create Card at bank specified by BANK_ID .
         |
         |${authenticationRequiredMessage(true)}
         |""",
      createPhysicalCardJsonV310,
      physicalCardJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        AllowedValuesAre,
        UnknownError
      ),
      List(apiTagCard, apiTagNewStyle),
      Some(List(canCreateCardsForBank)))
    lazy val addCardForBank: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "cards" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            
            failMsg = s"$InvalidJsonFormat The Json body should be the $CreatePhysicalCardJsonV310 "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {json.extract[CreatePhysicalCardJsonV310]}
            
            _ <- postJson.allows match {
              case List() => Future {true}
              case _ => Helper.booleanToFuture(AllowedValuesAre + CardAction.availableValues.mkString(", "))(postJson.allows.forall(a => CardAction.availableValues.contains(a)))
            }

            failMsg = AllowedValuesAre + CardReplacementReason.availableValues.mkString(", ")
            cardReplacementReason <- NewStyle.function.tryons(failMsg, 400, callContext) {
              postJson.replacement match {
                case Some(value) => CardReplacementReason.valueOf(value.reason_requested)
                case None => CardReplacementReason.valueOf(CardReplacementReason.FIRST.toString)
              }
            }
            
            _<-Helper.booleanToFuture(s"${maximumLimitExceeded.replace("10000", "10")} Current issue_number is ${postJson.issue_number}")(postJson.issue_number.length<= 10)

            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canCreateCardsForBank, callContext)
            
            (_, callContext)<- NewStyle.function.getBankAccount(bankId, AccountId(postJson.account_id), callContext)
            
            (_, callContext)<- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, callContext)

            replacement = postJson.replacement match {
              case Some(replacement) => 
                Some(CardReplacementInfo(requestedDate = replacement.requested_date, cardReplacementReason))
              case None => None
            }
            collected = postJson.collected match {
              case Some(collected) => Some(CardCollectionInfo(collected))
              case None => None
            }
            posted = postJson.posted match {
              case Some(posted) => Option(CardPostedInfo(posted))
              case None => None
            }
            
            (card, callContext) <- NewStyle.function.createPhysicalCard(
              bankCardNumber=postJson.card_number,
              nameOnCard=postJson.name_on_card,
              cardType = postJson.card_type,
              issueNumber=postJson.issue_number,
              serialNumber=postJson.serial_number,
              validFrom=postJson.valid_from_date,
              expires=postJson.expires_date,
              enabled=postJson.enabled,
              cancelled=false,
              onHotList=false,
              technology=postJson.technology,
              networks= postJson.networks,
              allows= postJson.allows,
              accountId= postJson.account_id,
              bankId=bankId.value,
              replacement = replacement,
              pinResets= postJson.pin_reset.map(e => PinResetInfo(e.requested_date, PinResetReason.valueOf(e.reason_requested.toUpperCase))),
              collected = collected,
              posted = posted,
              customerId = postJson.customer_id,
              callContext
            )
          } yield {
            (createPhysicalCardJson(card, u), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      updatedCardForBank,
      implementedInApiVersion,
      nameOf(updatedCardForBank),
      "PUT",
      "/management/banks/BANK_ID/cards/CARD_ID",
      "Update Card",
      s"""Update Card at bank specified by CARD_ID .
         |${authenticationRequiredMessage(true)}
         |""",
      updatePhysicalCardJsonV310,
      physicalCardJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        AllowedValuesAre,
        UnknownError
      ),
      List(apiTagCard, apiTagNewStyle),
      Some(List(canCreateCardsForBank)))
    lazy val updatedCardForBank: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "cards" :: cardId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canUpdateCardsForBank, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $UpdatePhysicalCardJsonV310 "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[UpdatePhysicalCardJsonV310]
            }

            _ <- postJson.allows match {
              case List() => Future {1}
              case _ => Helper.booleanToFuture(AllowedValuesAre + CardAction.availableValues.mkString(", "))(postJson.allows.forall(a => CardAction.availableValues.contains(a)))
            }

            failMsg = AllowedValuesAre + CardReplacementReason.availableValues.mkString(", ")
            _ <- NewStyle.function.tryons(failMsg, 400, callContext) {
              CardReplacementReason.valueOf(postJson.replacement.reason_requested)
            }
            
            _<-Helper.booleanToFuture(s"${maximumLimitExceeded.replace("10000", "10")} Current issue_number is ${postJson.issue_number}")(postJson.issue_number.length<= 10)

            (_, callContext)<- NewStyle.function.getBankAccount(bankId, AccountId(postJson.account_id), callContext)

            (card, callContext) <- NewStyle.function.getPhysicalCardForBank(bankId, cardId, callContext)
            
            (_, callContext)<- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, callContext)
            
            (card, callContext) <- NewStyle.function.updatePhysicalCard(
              cardId = cardId,
              bankCardNumber=card.bankCardNumber, 
              cardType = postJson.card_type,
              nameOnCard=postJson.name_on_card,
              issueNumber=postJson.issue_number,
              serialNumber=postJson.serial_number,
              validFrom=postJson.valid_from_date,
              expires=postJson.expires_date,
              enabled=postJson.enabled,
              cancelled=false,
              onHotList=false,
              technology=postJson.technology,
              networks= postJson.networks,
              allows= postJson.allows,
              accountId= postJson.account_id,
              bankId=bankId.value,
              replacement= Some(CardReplacementInfo(requestedDate = postJson.replacement.requested_date, CardReplacementReason.valueOf(postJson.replacement.reason_requested))),
              pinResets= postJson.pin_reset.map(e => PinResetInfo(e.requested_date, PinResetReason.valueOf(e.reason_requested.toUpperCase))),
              collected= Option(CardCollectionInfo(postJson.collected)),
              posted = Option(CardPostedInfo(postJson.posted)),
              customerId = postJson.customer_id,
              callContext = callContext
            )
          } yield {
            (createPhysicalCardJson(card, u), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getCardsForBank,
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
        |${authenticationRequiredMessage(true)}""".stripMargin,
      emptyObjectJson,
      physicalCardsJsonV310,
      List(UserNotLoggedIn,BankNotFound, UnknownError),
      List(apiTagCard, apiTagNewStyle))
    lazy val getCardsForBank : OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "cards" :: Nil JsonGet _ => {
        cc => {
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            obpQueryParams <- createQueriesByHttpParamsFuture(httpParams) map {
              x => unboxFullOrFail(x, callContext, InvalidFilterParameterFormat)
            }
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canGetCardsForBank, callContext)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (cards,callContext) <- NewStyle.function.getPhysicalCardsForBank(bank, u, obpQueryParams, callContext)
          } yield {
            (createPhysicalCardsJson(cards, u), HttpCode.`200`(callContext))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      getCardForBank,
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
      emptyObjectJson,
      physicalCardWithAttributesJsonV310,
      List(UserNotLoggedIn,BankNotFound, UnknownError),
      List(apiTagCard, apiTagNewStyle))
    lazy val getCardForBank : OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "cards" :: cardId ::  Nil JsonGet _ => {
        cc => {
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canGetCardsForBank, callContext)
            (_, callContext)<- NewStyle.function.getBank(bankId, callContext)
            (card, callContext) <- NewStyle.function.getPhysicalCardForBank(bankId, cardId, callContext)
            (cardAttributes, callContext) <- NewStyle.function.getCardAttributesFromProvider(cardId, callContext)
          } yield {
            val views: List[View] = Views.views.vend.assignedViewsForAccount(BankIdAccountId(card.account.bankId, card.account.accountId))
            val commonsData: List[CardAttributeCommons]= cardAttributes
            (createPhysicalCardWithAttributesJson(card, commonsData, u, views), HttpCode.`200`(callContext))
          }
        }
      }
    }

    resourceDocs += ResourceDoc(
      deleteCardForBank,
      implementedInApiVersion,
      nameOf(deleteCardForBank),
      "DELETE",
      "/management/banks/BANK_ID/cards/CARD_ID",
      "Delete Card",
      s"""Delete a Card at bank specified by CARD_ID .
         |
          |${authenticationRequiredMessage(true)}
         |""",
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        AllowedValuesAre,
        UnknownError
      ),
      List(apiTagCard, apiTagNewStyle),
      Some(List(canCreateCardsForBank)))
    lazy val deleteCardForBank: OBPEndpoint = {
      case "management"::"banks" :: BankId(bankId) :: "cards" :: cardId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canDeleteCardsForBank, callContext)
            (bank, callContext) <- NewStyle.function.getBank(bankId, Some(cc)) 
            (result, callContext) <- NewStyle.function.deletePhysicalCardForBank(bankId, cardId, callContext)
          } yield {
            (Full(result), HttpCode.`204`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      createCardAttribute,
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
         |${authenticationRequiredMessage(true)}
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
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCard, apiTagNewStyle))

    lazy val createCardAttribute : OBPEndpoint = {
      case "management"::"banks" :: bankId :: "cards" :: cardId :: "attribute" :: Nil JsonPost json -> _=> {
        cc =>
          for {
            (_, callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            (_, callContext) <- NewStyle.function.getPhysicalCardForBank(BankId(bankId), cardId, callContext)
            
            failMsg = s"$InvalidJsonFormat The Json body should be the $CardAttributeJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[CardAttributeJson]
            }
            
            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${CardAttributeType.DOUBLE}(12.1234), ${CardAttributeType.STRING}(TAX_NUMBER), ${CardAttributeType.INTEGER}(123) and ${CardAttributeType.DATE_WITH_DAY}(2012-04-23)"
            createCardAttribute <- NewStyle.function.tryons(failMsg, 400, callContext) {
              CardAttributeType.withName(postedData.`type`)
            }
            
            (cardAttribute, callContext) <- NewStyle.function.createOrUpdateCardAttribute(
              Some(BankId(bankId)),
              Some(cardId),
              None,
              postedData.name,
              createCardAttribute,
              postedData.value,
              callContext: Option[CallContext]
            )
          } yield {
            val commonsData: CardAttributeCommons = cardAttribute
            (commonsData, HttpCode.`201`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      updateCardAttribute,
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
         |${authenticationRequiredMessage(true)}
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
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCard, apiTagNewStyle))

    lazy val updateCardAttribute : OBPEndpoint = {
      case "management"::"banks" :: bankId :: "cards" :: cardId :: "attributes" :: cardAttributeId :: Nil JsonPut json -> _=> {
        cc =>
          for {
            (_, callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            (_, callContext) <- NewStyle.function.getPhysicalCardForBank(BankId(bankId), cardId, callContext)
            (_, callContext) <- NewStyle.function.getCardAttributeById(cardAttributeId, callContext)

            failMsg = s"$InvalidJsonFormat The Json body should be the $CardAttributeJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[CardAttributeJson]
            }

            failMsg = s"$InvalidJsonFormat The `Type` field can only accept the following field: " +
              s"${CardAttributeType.DOUBLE}(12.1234), ${CardAttributeType.STRING}(TAX_NUMBER), ${CardAttributeType.INTEGER}(123) and ${CardAttributeType.DATE_WITH_DAY}(2012-04-23)"
            createCardAttribute <- NewStyle.function.tryons(failMsg, 400, callContext) {
              CardAttributeType.withName(postedData.`type`)
            }

            (cardAttribute, callContext) <- NewStyle.function.createOrUpdateCardAttribute(
              Some(BankId(bankId)),
              Some(cardId),
              Some(cardAttributeId),
              postedData.name,
              createCardAttribute,
              postedData.value,
              callContext: Option[CallContext]
            )
          } yield {
            val commonsData: CardAttributeCommons = cardAttribute
            (commonsData, HttpCode.`201`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      updateCustomerBranch,
      implementedInApiVersion,
      nameOf(updateCustomerBranch),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/branch",
      "Update the Branch of a Customer",
      s"""Update the Branch of the Customer specified by CUSTOMER_ID.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        |""",
      putCustomerBranchJsonV310,
      customerJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(canUpdateCustomerIdentity :: Nil)
    )
    lazy val updateCustomerBranch : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "branch" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canUpdateCustomerBranch, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutUpdateCustomerBranchJsonV310 "
            putData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutUpdateCustomerBranchJsonV310]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (customer, callContext) <- NewStyle.function.updateCustomerGeneralData(
              customerId,
              None,
              None,
              None,
              None,
              None,
              None,
              None,
              None,
              Some(putData.branch_id),
              None,
              callContext
            )
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      updateCustomerData,
      implementedInApiVersion,
      nameOf(updateCustomerData),
      "PUT",
      "/banks/BANK_ID/customers/CUSTOMER_ID/data",
      "Update the other data of a Customer",
      s"""Update the other data of the Customer specified by CUSTOMER_ID.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        |""",
      putUpdateCustomerDataJsonV310,
      customerJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(canUpdateCustomerIdentity :: Nil)
    )
    lazy val updateCustomerData : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "data" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canUpdateCustomerData, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutUpdateCustomerDataJsonV310 "
            putData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutUpdateCustomerDataJsonV310]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (customer, callContext) <- NewStyle.function.updateCustomerGeneralData(
              customerId,
              None,
              Some(CustomerFaceImage(putData.face_image.date, putData.face_image.url)),
              None,
              Some(putData.relationship_status),
              Some(putData.dependants),
              Some(putData.highest_education_attained),
              Some(putData.employment_status),
              None,
              None,
              None,
              callContext
            )
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      createAccount,
      implementedInApiVersion,
      "createAccount",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID",
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
      createAccountRequestJsonV310,
      createAccountResponseJsonV310,
      List(
        InvalidJsonFormat,
        BankNotFound,
        UserNotLoggedIn,
        InvalidUserId,
        InvalidAccountIdFormat,
        InvalidBankIdFormat,
        UserNotFoundById,
        UserHasMissingRoles,
        InvalidAccountBalanceAmount,
        InvalidAccountInitialBalance,
        InitialBalanceMustBeZero,
        InvalidAccountBalanceCurrency,
        AccountIdAlreadyExists,
        UnknownError
      ),
      List(apiTagAccount,apiTagOnboarding, apiTagNewStyle),
      Some(List(canCreateAccount))
    )


    lazy val createAccount : OBPEndpoint = {
      // Create a new account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPut json -> _ => {
        cc =>{
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (account, callContext) <- Connector.connector.vend.checkBankAccountExists(bankId, accountId, callContext)
            _ <- Helper.booleanToFuture(AccountIdAlreadyExists){
              account.isEmpty
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the ${prettyRender(Extraction.decompose(createAccountRequestJsonV310))} "
            createAccountJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[CreateAccountRequestJsonV310]
            }
            loggedInUserId = u.userId
            userIdAccountOwner = if (createAccountJson.user_id.nonEmpty) createAccountJson.user_id else loggedInUserId
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat){
              isValidID(accountId.value)
            }
            _ <- Helper.booleanToFuture(InvalidBankIdFormat){
              isValidID(accountId.value)
            }
            (postedOrLoggedInUser,callContext) <- NewStyle.function.findByUserId(userIdAccountOwner, callContext)
            // User can create account for self or an account for another user if they have CanCreateAccount role
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat){
              isValidID(accountId.value)
            }
            _ <- if (userIdAccountOwner == loggedInUserId) Future.successful(Full(Unit))
                else NewStyle.function.hasEntitlement(bankId.value, loggedInUserId, canCreateAccount, callContext, s"${UserHasMissingRoles} $canCreateAccount or create account for self")

            initialBalanceAsString = createAccountJson.balance.amount
            accountType = createAccountJson.product_code
            accountLabel = createAccountJson.label
            initialBalanceAsNumber <- NewStyle.function.tryons(InvalidAccountInitialBalance, 400, callContext) {
              BigDecimal(initialBalanceAsString)
            }
            _ <-  Helper.booleanToFuture(InitialBalanceMustBeZero){0 == initialBalanceAsNumber}
            _ <-  Helper.booleanToFuture(InvalidISOCurrencyCode){isValidCurrencyISOCode(createAccountJson.balance.currency)}
            currency = createAccountJson.balance.currency
            (_, callContext ) <- NewStyle.function.getBank(bankId, callContext)
            _ <- Helper.booleanToFuture(s"$InvalidAccountRoutings Duplication detected in account routings, please specify only one value per routing scheme", 400){
              createAccountJson.account_routings.map(_.scheme).distinct.size == createAccountJson.account_routings.size
            }
            alreadyExistAccountRoutings <- Future.sequence(createAccountJson.account_routings.map(accountRouting =>
              NewStyle.function.getAccountRouting(Some(bankId), accountRouting.scheme, accountRouting.address, callContext).map(_ => Some(accountRouting)).fallbackTo(Future.successful(None))
            ))
            alreadyExistingAccountRouting = alreadyExistAccountRoutings.collect {
              case Some(accountRouting) => s"bankId: $bankId, scheme: ${accountRouting.scheme}, address: ${accountRouting.address}"
            }
            _ <- Helper.booleanToFuture(s"$AccountRoutingAlreadyExist (${alreadyExistingAccountRouting.mkString("; ")})") {
              alreadyExistingAccountRouting.isEmpty
            }
            (bankAccount,callContext) <- NewStyle.function.createBankAccount(
              bankId,
              accountId,
              accountType,
              accountLabel,
              currency,
              initialBalanceAsNumber,
              postedOrLoggedInUser.name,
              createAccountJson.branch_id,
              createAccountJson.account_routings.map(r => AccountRouting(r.scheme, r.address)),
              callContext
            )
            (productAttributes, callContext) <- NewStyle.function.getProductAttributesByBankAndCode(bankId, ProductCode(accountType), callContext)
            (accountAttributes, callContext) <- NewStyle.function.createAccountAttributes(
              bankId,
              accountId,
              ProductCode(accountType),
              productAttributes,
              callContext: Option[CallContext]
            )
          } yield {
            //1 Create or Update the `Owner` for the new account
            //2 Add permission to the user
            //3 Set the user as the account holder
            BankAccountCreation.setAsOwner(bankId, accountId, postedOrLoggedInUser)
            (JSONFactory310.createAccountJSON(userIdAccountOwner, bankAccount, accountAttributes), HttpCode.`201`(callContext))
          }
        }
      }
    }



    resourceDocs += ResourceDoc(
      getPrivateAccountByIdFull,
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
      emptyObjectJson,
      moderatedAccountJSON310,
      List(BankNotFound,AccountNotFound,ViewNotFound, UserNoPermissionAccessView, UnknownError),
      apiTagAccount ::  apiTagNewStyle :: Nil)
    lazy val getPrivateAccountByIdFull : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "account" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(bankId, accountId), Some(u), callContext) 
            moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(u), callContext)
            (accountAttributes, callContext) <- NewStyle.function.getAccountAttributesByAccount(
              bankId,
              accountId,
              callContext: Option[CallContext])
          } yield {
            val availableViews = Views.views.vend.privateViewsUserCanAccessForAccount(u, BankIdAccountId(account.bankId, account.accountId))
            val viewsAvailable = availableViews.map(JSONFactory.createViewJSON).sortBy(_.short_name)
            (JSONFactory310.createBankAccountJSON(moderatedAccount, viewsAvailable, accountAttributes), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      saveHistoricalTransaction,
      implementedInApiVersion,
      nameOf(saveHistoricalTransaction),
      "POST",
      "/management/historical/transactions ",
      "Save Historical Transactions ",
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
      List(
        InvalidJsonFormat,
        BankNotFound,
        AccountNotFound,
        CounterpartyNotFoundByCounterpartyId,
        InvalidNumber,
        NotPositiveAmount,
        InvalidTransactionRequestCurrency,
        UnknownError
      ),
      List(apiTagTransactionRequest, apiTagNewStyle),
      Some(List(canCreateHistoricalTransaction))
    )


    lazy val saveHistoricalTransaction : OBPEndpoint =  {
      case "management"  :: "historical" :: "transactions" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canCreateHistoricalTransaction, callContext)

            // Check the input JSON format, here is just check the common parts of all four types
            transDetailsJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostHistoricalTransactionJson ", 400, callContext) {
              json.extract[PostHistoricalTransactionJson]
            }
            fromAccountPost = transDetailsJson.from
            (fromAccount, callContext) <- if (fromAccountPost.bank_id.isDefined && fromAccountPost.account_id.isDefined && fromAccountPost.counterparty_id.isEmpty){
              for{
                (_, callContext) <- NewStyle.function.getBank(BankId(fromAccountPost.bank_id.get), callContext)
                (fromAccount, callContext) <- NewStyle.function.checkBankAccountExists(BankId(fromAccountPost.bank_id.get), AccountId(fromAccountPost.account_id.get), callContext)
              } yield {
                (fromAccount, callContext)
              }
            } else if (fromAccountPost.bank_id.isEmpty && fromAccountPost.account_id.isEmpty && fromAccountPost.counterparty_id.isDefined){
              for {
                 (fromCounterparty, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(CounterpartyId(fromAccountPost.counterparty_id.get), cc.callContext)
                 fromAccount <- NewStyle.function.getBankAccountFromCounterparty(fromCounterparty, false, callContext)
              }yield{
                (fromAccount, callContext)
              }
            } else {
              throw new RuntimeException(s"$InvalidJsonFormat from object should only contain bank_id and account_id or counterparty_id in the post json body.")
            }
            
            
            toAccountPost = transDetailsJson.to
            (toAccount, callContext) <- if (toAccountPost.bank_id.isDefined && toAccountPost.account_id.isDefined && toAccountPost.counterparty_id.isEmpty){
              for{
                (_, callContext) <- NewStyle.function.getBank(BankId(toAccountPost.bank_id.get), callContext)
                (toAccount, callContext) <- NewStyle.function.checkBankAccountExists(BankId(toAccountPost.bank_id.get), AccountId(toAccountPost.account_id.get), callContext)
              } yield {
                (toAccount, callContext)
              }
            } else if (toAccountPost.bank_id.isEmpty && toAccountPost.account_id.isEmpty && toAccountPost.counterparty_id.isDefined){
              for {
                (toCounterparty, callContext) <- NewStyle.function.getCounterpartyByCounterpartyId(CounterpartyId(toAccountPost.counterparty_id.get), cc.callContext)
                toAccount <- NewStyle.function.getBankAccountFromCounterparty(toCounterparty, true, callContext)
              }yield{
                (toAccount, callContext)
              }
            } else {
              throw new RuntimeException(s"$InvalidJsonFormat to object should only contain bank_id and account_id or counterparty_id in the post json body.")
            }
            
            amountNumber <- NewStyle.function.tryons(s"$InvalidNumber Current input is ${transDetailsJson.value.amount} ", 400, callContext) {
              BigDecimal(transDetailsJson.value.amount)
            }

            _ <- Helper.booleanToFuture(s"${NotPositiveAmount} Current input is: '${amountNumber}'") {
              amountNumber > BigDecimal("0")
            }

            posted <- NewStyle.function.tryons(s"$InvalidDateFormat Current `posted` field is ${transDetailsJson.posted}. Please use this format ${DateWithSecondsFormat.toPattern}! ", 400, callContext) {
              new SimpleDateFormat(DateWithSeconds).parse(transDetailsJson.posted)
            }

            completed <- NewStyle.function.tryons(s"$InvalidDateFormat Current `completed` field  is ${transDetailsJson.completed}. Please use this format ${DateWithSecondsFormat.toPattern}! ", 400, callContext) {
              new SimpleDateFormat(DateWithSeconds).parse(transDetailsJson.completed)
            }
            
            // Prevent default value for transaction request type (at least).
            _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${transDetailsJson.value.currency}'") {
              isValidCurrencyISOCode(transDetailsJson.value.currency)
            }

            amountOfMoneyJson = AmountOfMoneyJsonV121(transDetailsJson.value.currency, transDetailsJson.value.amount)
            chargePolicy = transDetailsJson.charge_policy
            
           //There is no constraint for the type at the moment  
            transactionType = transDetailsJson.`type` 

            (transactionId, callContext) <- NewStyle.function.makeHistoricalPayment(
              fromAccount,
              toAccount,
              posted,
              completed,
              amountNumber,
              transDetailsJson.value.currency,
              transDetailsJson.description,
              transactionType,
              chargePolicy,
              callContext
            )
          } yield {
            (JSONFactory310.createPostHistoricalTransactionResponseJson(
              transactionId,
              fromAccountPost,
              toAccountPost,
              value= amountOfMoneyJson,
              description = transDetailsJson.description,
              posted,
              completed,
              transactionRequestType = transactionType,
              chargePolicy =transDetailsJson.charge_policy), HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getWebUiProps,
      implementedInApiVersion,
      nameOf(getWebUiProps),
      "GET",
      "/management/webui_props",
      "Get WebUiProps",
      s"""
      |
      |Get the all WebUiProps key values, those props key with "webui_" can be stored in DB, this endpoint get all from DB.
      |
      |url query parameter: 
      |active: It must be a boolean string. and If active = true, it will show
      |          combination of explicit (inserted) + implicit (default)  method_routings.
      |
      |eg:  
      |${getObpApiRoot}/v3.1.0/management/webui_props
      |${getObpApiRoot}/v3.1.0/management/webui_props?active=true
      |
      |""",
      emptyObjectJson,
      ListResult(
        "webui_props",
        (List(WebUiPropsCommons("webui_api_explorer_url", "https://apiexplorer.openbankproject.com", Some("web-ui-props-id"))))
      )
      ,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagWebUiProps, apiTagApi, apiTagNewStyle),
      Some(List(canGetWebUiProps))
    )


    lazy val getWebUiProps: OBPEndpoint = {
      case "management" :: "webui_props":: Nil JsonGet req => {
        cc =>
          val active = S.param("active").getOrElse("false")
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            invalidMsg = s"""$InvalidFilterParameterFormat `active` must be a boolean, but current `active` value is: ${active} """
            isActived <- NewStyle.function.tryons(invalidMsg, 400, callContext) {
              active.toBoolean
            }
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetWebUiProps, callContext)
            explicitWebUiProps <- Future{ MappedWebUiPropsProvider.getAll() }
            implicitWebUiPropsRemovedDuplicated = if(isActived){
              val implicitWebUiProps = getWebUIPropsPairs.map(webUIPropsPairs=>WebUiPropsCommons(webUIPropsPairs._1, webUIPropsPairs._2, webUiPropsId= Some("default")))
              if(explicitWebUiProps.nonEmpty){
                //get the same name props in the `implicitWebUiProps`
                val duplicatedProps : List[WebUiPropsCommons]= explicitWebUiProps.map(explicitWebUiProp => implicitWebUiProps.filter(_.name == explicitWebUiProp.name)).flatten
                //remove the depulicated fields from `implicitWebUiProps`
                implicitWebUiProps diff duplicatedProps
              }
              else implicitWebUiProps.distinct
            } else {
              List.empty[WebUiPropsCommons]
            }
          } yield {
            val listCommons: List[WebUiPropsCommons] = explicitWebUiProps ++ implicitWebUiPropsRemovedDuplicated
            (ListResult("webui_props", listCommons), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      createWebUiProps,
      implementedInApiVersion,
      nameOf(createWebUiProps),
      "POST",
      "/management/webui_props",
      "Create WebUiProps",
      s"""Create a WebUiProps.
         |
         |
         |${authenticationRequiredMessage(true)}
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
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagWebUiProps, apiTagApi, apiTagNewStyle),
      Some(List(canCreateWebUiProps)))

    lazy val createWebUiProps : OBPEndpoint = {
      case "management" :: "webui_props" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateWebUiProps, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the ${classOf[WebUiPropsCommons]} "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[WebUiPropsCommons]
            }
            invalidMsg = s"""$InvalidWebUiProps name must be start with webui_, but current post name is: ${postedData.name} """
            _ <- NewStyle.function.tryons(invalidMsg, 400, callContext) {
              require(postedData.name.startsWith("webui_"))
            }
            Full(webUiProps) <- Future {  MappedWebUiPropsProvider.createOrUpdate(postedData) }
          } yield {
            val commonsData: WebUiPropsCommons = webUiProps
            (commonsData, HttpCode.`201`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      deleteWebUiProps,
      implementedInApiVersion,
      nameOf(deleteWebUiProps),
      "DELETE",
      "/management/webui_props/WEB_UI_PROPS_ID",
      "Delete WebUiProps",
      s"""Delete a WebUiProps specified by WEB_UI_PROPS_ID.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      emptyObjectJson,
      emptyObjectJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagWebUiProps, apiTagApi, apiTagNewStyle),
      Some(List(canDeleteWebUiProps)))

    lazy val deleteWebUiProps : OBPEndpoint = {
      case "management" :: "webui_props" :: webUiPropsId ::  Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteWebUiProps, callContext)
            deleted <- Future { MappedWebUiPropsProvider.delete(webUiPropsId) } map {
              unboxFullOrFail(_, callContext)
            }
          } yield {
            (deleted, HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getBankAccountsBalances,
      implementedInApiVersion,
      nameOf(getBankAccountsBalances),
      "GET",
      "/banks/BANK_ID/balances",
      "Get Accounts Balances",
      """Get the Balances for the Accounts of the current User at one bank.""",
      emptyObjectJson,
      accountBalancesV310Json,
      List(UnknownError),
      apiTagAccount :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagNewStyle :: Nil
    )

    lazy val getBankAccountsBalances : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "balances" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
            (accountsBalances, callContext)<- NewStyle.function.getBankAccountsBalances(availablePrivateAccounts, callContext)
          } yield{
            (createBalancesJson(accountsBalances), HttpCode.`200`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      enableDisableConsumers,
      implementedInApiVersion,
      "enableDisableConsumers",
      "PUT",
      "/management/consumers/CONSUMER_ID",
      "Enable or Disable Consumers",
      s"""Enable/Disable a Consumer specified by CONSUMER_ID.
         |
         |""",
      putEnabledJSON,
      putEnabledJSON,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagConsumer, apiTagApi, apiTagNewStyle),
      Some(List(canEnableConsumers,canDisableConsumers)))


    lazy val enableDisableConsumers: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            putData <- NewStyle.function.tryons(InvalidJsonFormat, 400,  cc.callContext) {
              json.extract[PutEnabledJSON]
            }
            _ <- putData.enabled match {
              case true  => NewStyle.function.hasEntitlement("", u.userId, ApiRole.canEnableConsumers, callContext)
              case false => NewStyle.function.hasEntitlement("", u.userId, ApiRole.canDisableConsumers, callContext)
            }
            consumer <- NewStyle.function.getConsumerByConsumerId(consumerId, callContext)
            updatedConsumer <- Future {
              Consumers.consumers.vend.updateConsumer(consumer.id.get, None, None, Some(putData.enabled), None, None, None, None, None, None) ?~! "Cannot update Consumer"
            }
          } yield {
            // Format the data as json
            val json = PutEnabledJSON(updatedConsumer.map(_.isActive.get).getOrElse(false))
            // Return
            (json, HttpCode.`200`(callContext))
          }
      }
    }

  }
}

object APIMethods310 extends RestHelper with APIMethods310 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations3_1_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}
