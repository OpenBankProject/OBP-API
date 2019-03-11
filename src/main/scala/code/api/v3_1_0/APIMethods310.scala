package code.api.v3_1_0

import java.util.{Calendar, UUID}

import code.accountattribute.AccountAttribute.AccountAttributeType
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{branchJsonV220, _}
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{BankAccountNotFound, _}
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.v1_2_1.{JSONFactory, RateLimiting}
import code.api.v2_0_0.{CreateMeetingJson, JSONFactory200}
import code.api.v2_1_0.JSONFactory210
import code.api.v2_2_0.{BranchJsonV220, JSONFactory220}
import code.api.v2_2_0.JSONFactory220.transformV220ToBranch
import code.api.v3_0_0.JSONFactory300
import code.api.v3_0_0.JSONFactory300.createAdapterInfoJson
import code.api.v3_1_0.JSONFactory310._
import code.bankconnectors.Connector
import code.branches.Branches.BranchId
import code.consumer.Consumers
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import code.meetings.{ContactDetails, Invitee, Meeting}
import code.metrics.APIMetrics
import code.model._
import code.model.dataAccess.{AuthUser, BankAccountCreation}
import code.productattribute.ProductAttribute.ProductAttributeType
import code.products.Products.{Product, ProductCode}
import code.users.Users
import code.util.Helper
import code.util.Helper.booleanToBox
import code.webhook.AccountWebhook
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{CreditLimit, _}
import net.liftweb.common.{Empty, Full}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.http.rest.RestHelper
import net.liftweb.json.Extraction
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.Validate

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait APIMethods310 {
  self: RestHelper =>

  val Implementations3_1_0 = new Implementations310() 
  
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
      s"""${mockedDataText(true)}Get all checkbook orders""",
      emptyObjectJson,
      checkbookOrdersJson,
      List(
        UserNotLoggedIn,
        BankNotFound,
        BankAccountNotFound,
        InvalidConnectorResponseForGetCheckbookOrdersFuture,
        UnknownError
      ),
      Catalogs(Core, notPSD2, OBWG),
      apiTagAccount :: apiTagNewStyle :: Nil)

    lazy val getCheckbookOrders : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "checkbook"  :: "orders" :: Nil JsonGet req => {
        cc =>
          for {
            (_, callContext) <- authorizedAccess(cc)

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)

            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)

            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
            
            (checkbookOrders, callContext)<- Connector.connector.vend.getCheckbookOrdersFuture(bankId.value,accountId.value, callContext) map {
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
      s"""${mockedDataText(true)}Get status of Credit Card orders
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
      Catalogs(Core, notPSD2, OBWG),
      apiTagCard :: apiTagNewStyle :: Nil)

    lazy val getStatusOfCreditCardOrder : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "credit_cards"  :: "orders" :: Nil JsonGet req => {
        cc =>
          for {
            (_, callContext) <- authorizedAccess(cc)

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)

            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)

            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
            
            //TODO need error handling here
            (checkbookOrders,callContext) <- Connector.connector.vend.getStatusOfCreditCardOrderFuture(bankId.value,accountId.value, callContext) map {
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
      Catalogs(Core, notPSD2, OBWG),
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
      Catalogs(Core, notPSD2, OBWG),
      apiTagCustomer :: Nil)

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
      Catalogs(Core, notPSD2, OBWG),
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
      s"""Get metrics abou the most popular APIs. e.g.: total count, response time (in ms), etc.
        |
        |Should be able to filter on the following fields
        |
        |eg: /management/metrics/top-apis?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&consumer_id=5
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
      Catalogs(Core, notPSD2, OBWG),
      apiTagMetric :: apiTagNewStyle :: Nil,
      Some(List(canReadMetrics))
    )

    lazy val getTopAPIs : OBPEndpoint = {
      case "management" :: "metrics" :: "top-apis" :: Nil JsonGet req => {
        cc =>
          for {
            
            (Full(u), callContext) <- authorizedAccess(cc)

            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canReadMetrics)
            
            httpParams <- NewStyle.function.createHttpParams(cc.url)
              
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
        |e.g.: /management/metrics/top-consumers?from_date=$DateWithMsExampleString&to_date=2017-05-22T01:02:03.000Z&consumer_id=5
        |&user_id=66214b8e-259e-44ad-8868-3eb47be70646&implemented_by_partial_function=getTransactionsForBankAccount
        |&implemented_in_version=v3.0.0&url=/obp/v3.0.0/banks/gh.29.uk/accounts/8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0/owner/transactions
        |&verb=GET&anon=false&app_name=MapperPostman
        |&exclude_app_names=API-EXPLORER,API-Manager,SOFI,null
        |&limit=100
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
      Catalogs(Core, notPSD2, OBWG),
      apiTagMetric :: apiTagNewStyle :: Nil,
      Some(List(canReadMetrics))
    )

    lazy val getMetricsTopConsumers : OBPEndpoint = {
      case "management" :: "metrics" :: "top-consumers" :: Nil JsonGet req => {
        cc =>
          for {
            
            (Full(u), callContext) <- authorizedAccess(cc)

            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canReadMetrics)
            
            httpParams <- NewStyle.function.createHttpParams(cc.url)
              
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
         |Possible custom URL parameters for pagination:
         |
         |* sort_direction=ASC/DESC
         |* limit=NUMBER ==> default value: 50
         |* offset=NUMBER ==> default value: 0
         |* from_date=DATE => default value: date of the oldest customer created (format below)
         |* to_date=DATE => default value: date of the newest customer created (format below)
         |
         |**Date format parameter**: $DateWithMs($DateWithMsExampleString) ==> time zone is UTC.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      emptyObjectJson,
      transactionsJsonV300,
      List(UserNotLoggedIn, FirehoseViewsNotAllowedOnThisInstance, UserHasMissingRoles, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccountFirehose, apiTagAccount, apiTagFirehoseData, apiTagNewStyle),
      Some(List(canUseFirehoseAtAnyBank)))

    lazy val getFirehoseCustomers : OBPEndpoint = {
      //get private accounts for all banks
      case "banks" :: BankId(bankId):: "firehose" ::  "customers" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <-  authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- Helper.booleanToFuture(failMsg = FirehoseViewsNotAllowedOnThisInstance +" or " + UserHasMissingRoles + CanUseFirehoseAtAnyBank  ) {
              canUseFirehose(u)
            }
            allowedParams = List("sort_direction", "limit", "offset", "from_date", "to_date")
            httpParams <- NewStyle.function.createHttpParams(cc.url)
            obpQueryParams <- NewStyle.function.createObpParams(httpParams, allowedParams, callContext)
            customers <- NewStyle.function.getCustomers(bankId, callContext, obpQueryParams)
          } yield {
            (JSONFactory300.createCustomersJson(customers), HttpCode.`200`(callContext))
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canReadUserLockedStatus))
    )

    lazy val getBadLoginStatus : OBPEndpoint = {
      //get private accounts for all banks
      case "users" :: username::  "lock-status" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <-  authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canReadUserLockedStatus)
            badLoginStatus <- Future { LoginAttempt.getBadLoginStatus(username) } map { unboxFullOrFail(_, callContext, s"$UserNotFoundByUsername($username)") }
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canUnlockUser)))

    lazy val unlockUser : OBPEndpoint = {
      //get private accounts for all banks
      case "users" :: username::  "lock-status" :: Nil JsonPut req => {
        cc =>
          for {
            (Full(u), callContext) <-  authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canUnlockUser)
            _ <- Future { LoginAttempt.resetBadLoginAttempts(username) } 
            badLoginStatus <- Future { LoginAttempt.getBadLoginStatus(username) } map { unboxFullOrFail(_, callContext, s"$UserNotFoundByUsername($username)") }
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagConsumer, apiTagNewStyle),
      Some(List(canSetCallLimits)))


    // TODO change URL to /../call-limits

    lazy val callsLimit : OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "call-limits" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <-  authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canSetCallLimits)
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CallLimitPostJson ", 400, callContext) {
              json.extract[CallLimitPostJson]
            }
            consumerIdToLong <- NewStyle.function.tryons(s"$InvalidConsumerId", 400, callContext) {
              consumerId.toLong
            }
            consumer <- NewStyle.function.getConsumerByPrimaryId(consumerIdToLong, callContext)
            updatedConsumer <- Consumers.consumers.vend.updateConsumerCallLimits(
              consumer.id.get,
              Some(postJson.per_second_call_limit),
              Some(postJson.per_minute_call_limit),
              Some(postJson.per_hour_call_limit),
              Some(postJson.per_day_call_limit),
              Some(postJson.per_week_call_limit),
              Some(postJson.per_month_call_limit)) map {
              unboxFullOrFail(_, callContext, UpdateConsumerError)
            }
          } yield {
            (createCallLimitJson(updatedConsumer, Nil), HttpCode.`200`(callContext))
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagConsumer, apiTagNewStyle),
      Some(List(canSetCallLimits)))


    
    lazy val getCallsLimit : OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "call-limits" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <-  authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canReadCallLimits)
            consumerIdToLong <- NewStyle.function.tryons(s"$InvalidConsumerId", 400, callContext) {
              consumerId.toLong
            }
            consumer <- NewStyle.function.getConsumerByPrimaryId(consumerIdToLong, callContext)
            rateLimit <- Future(RateLimitUtil.consumerRateLimitState(consumer.key.get).toList)
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
      Catalogs(Core, notPSD2, OBWG),
      apiTagAccount :: apiTagNewStyle :: Nil)

    lazy val checkFundsAvailable : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "funds-available" :: Nil JsonGet req => {
        cc =>
          val amount = "amount"
          val currency = "currency"
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
            _ <- Helper.booleanToFuture(failMsg = ViewDoesNotPermitAccess + " You need the view canQueryAvailableFunds.") {
              view.canQueryAvailableFunds
            }
            httpParams: List[HTTPParam] <- NewStyle.function.createHttpParams(cc.url)
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
            _ <- NewStyle.function.moderatedBankAccount(account, view, Full(u))
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagConsumer, apiTagApi, apiTagNewStyle),
      Some(List(canGetConsumers)))


    lazy val getConsumer: OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetConsumers)
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagConsumer, apiTagApi, apiTagNewStyle)
    )


    lazy val getConsumersForCurrentUser: OBPEndpoint = {
      case "management" :: "users" :: "current" :: "consumers" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagConsumer, apiTagApi, apiTagNewStyle),
      Some(List(canGetConsumers))
    )


    lazy val getConsumers: OBPEndpoint = {
      case "management" :: "consumers" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetConsumers)
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
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagWebhook :: apiTagBank :: apiTagNewStyle :: Nil,
      Some(List(canCreateWebhook))
    )

    lazy val createAccountWebhook : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "account-web-hooks" :: Nil JsonPost json -> _  => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canCreateWebhook)
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
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagWebhook :: apiTagBank :: apiTagNewStyle :: Nil,
      Some(List(canUpdateWebhook))
    )

    lazy val enableDisableAccountWebhook : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "account-web-hooks" :: Nil JsonPut json -> _  => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canUpdateWebhook)
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
         |* limit=NUMBER
         |* offset=NUMBER
         |* account_id=STRING
         |* user_id=STRING
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
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagWebhook :: apiTagBank :: apiTagNewStyle :: Nil,
      Some(List(canGetWebhooks))
    )


    lazy val getAccountWebhooks: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) ::"account-web-hooks" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canGetWebhooks)
            httpParams <- NewStyle.function.createHttpParams(cc.url)
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
      Catalogs(Core, PSD2, OBWG),
      apiTagApi :: apiTagNewStyle :: Nil,
      Some(List(canGetConfig)))

    lazy val config: OBPEndpoint = {
      case "config" :: Nil JsonGet _ =>
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetConfig)
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
      "Get Adapter Info (general)",
      s"""Get basic information about the Adapter.
         |
        |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      emptyObjectJson,
      adapterInfoJsonV300,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagApi, apiTagNewStyle))


    lazy val getAdapterInfo: OBPEndpoint = {
      case "adapter" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
            (ai,cc) <- NewStyle.function.getAdapterInfo(callContext)
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
      "Get Transaction by Id.",
      s"""Returns one transaction specified by TRANSACTION_ID of the account ACCOUNT_ID and [moderated](#1_2_1-getViewsForBankAccount) by the view (VIEW_ID).
         |
         |${authenticationRequiredMessage(false)}
         |Authentication is required if the view is not public.
         |
         |
         |""",
      emptyObjectJson,
      transactionJSON,
      List(UserNotLoggedIn, BankAccountNotFound ,ViewNotFound, UserNoPermissionAccessView, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransaction, apiTagNewStyle))

    lazy val getTransactionByIdForBankAccount : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: TransactionId(transactionId) :: "transaction" :: Nil JsonGet _ => {
        cc =>
          for {
            (user, callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
            (moderatedTransaction, callContext) <- account.moderatedTransactionFuture(bankId, accountId, transactionId, view, user, callContext) map {
              unboxFullOrFail(_, callContext, GetTransactionsException)
            }
          } yield {
            (JSONFactory.createTransactionJSON(moderatedTransaction), HttpCode.`200`(callContext))
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
      Catalogs(Core, PSD2, OBWG),
      List(apiTagTransactionRequest, apiTagNewStyle))

    lazy val getTransactionRequests: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transaction-requests" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.isEnabledTransactionRequests()
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (fromAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.view(viewId, BankIdAccountId(fromAccount.bankId, fromAccount.accountId), callContext)
            _ <- Helper.booleanToFuture(failMsg = UserNoPermissionAccessView) {
              u.hasViewAccess(view)
            }
            _ <- Helper.booleanToFuture(failMsg = UserNoOwnerView) {
              u.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId,fromAccount.accountId))
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
      "Create Customer.",
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagPerson, apiTagNewStyle),
      Some(List(canCreateCustomer,canCreateUserCustomerLink,canCreateCustomerAtAnyBank,canCreateUserCustomerLinkAtAnyBank)))
    lazy val createCustomer : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)

            _ <- Helper.booleanToFuture(failMsg =  UserHasMissingRoles + canCreateCustomer + " or " + canCreateCustomerAtAnyBank) {
              hasAtLeastOneEntitlement(bankId.value, u.userId,  canCreateCustomer :: canCreateCustomerAtAnyBank :: Nil)
            }

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerJsonV310 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostCustomerJsonV310]
            }
  
            customer <- Connector.connector.vend.createCustomerFuture(
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
              callContext,
              postedData.title,
              postedData.branchId,
              postedData.nameSuffix
            ) map {
              unboxFullOrFail(_, callContext, CreateCustomerError)
            }
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
      Catalogs(Core, PSD2, OBWG),
      List(apiTagApi, apiTagNewStyle))


    lazy val getRateLimitingInfo: OBPEndpoint = {
      case "rate-limiting" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
            rateLimiting <- NewStyle.function.tryons("", 400, callContext) {
              RateLimitUtil.inMemoryMode match {
                case true =>
                  val isActive = if(RateLimitUtil.useConsumerLimits == true) true else false
                  RateLimiting(RateLimitUtil.useConsumerLimits, "In-Memory", true, isActive)
                case false =>
                  val isRedisAvailable = RateLimitUtil.isRedisAvailable()
                  val isActive = if(RateLimitUtil.useConsumerLimits == true && isRedisAvailable == true) true else false
                  RateLimiting(RateLimitUtil.useConsumerLimits, "REDIS", isRedisAvailable, isActive)
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
      customerJsonV310,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagNewStyle))

    lazy val getCustomerByCustomerId : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId ::  Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetCustomer)
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`200`(callContext))
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
      customerJsonV310,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc ,apiTagNewStyle))

    lazy val getCustomerByCustomerNumber : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: "customer-number" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetCustomer)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerNumberJsonV310 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostCustomerNumberJsonV310]
            }
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerNumber(postedData.customer_number, bank.bankId, callContext)
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`201`(callContext))
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
      s"""Create User Auth Context.
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser, apiTagNewStyle))

    lazy val createUserAuthContext : OBPEndpoint = {
      case "users" :: userId ::"auth-context" :: Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateUserAuthContext)
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser, apiTagNewStyle))

    lazy val getUserAuthContexts : OBPEndpoint = {
      case "users" :: userId :: "auth-context" ::  Nil  JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetUserAuthContext)
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser, apiTagNewStyle))

    lazy val deleteUserAuthContexts : OBPEndpoint = {
      case "users" :: userId :: "auth-context" :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteUserAuthContext)
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser, apiTagNewStyle))

    lazy val deleteUserAuthContextById : OBPEndpoint = {
      case "users" :: userId :: "auth-context" :: userAuthContextId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteUserAuthContext)
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
      "Add Tax Residence to Customer",
      s"""Add a Tax Residence to the Customer specified by CUSTOMER_ID.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        |""",
      postTaxResidenceJsonV310,
      taxResidenceJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        InvalidJsonFormat,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc, apiTagNewStyle),
      Some(List(canCreateTaxResidence)))

    lazy val createTaxResidence : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "tax-residence" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canCreateTaxResidence)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostTaxResidenceJsonV310 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostTaxResidenceJsonV310]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (taxResidence, callContext) <- NewStyle.function.createTaxResidence(customerId, postedData.domain, postedData.tax_number, callContext)
          } yield {
            (JSONFactory310.createTaxResidence(List(taxResidence)), HttpCode.`201`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getTaxResidence,
      implementedInApiVersion,
      nameOf(getTaxResidence),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/tax-residence",
      "Get Tax Residences of Customer",
      s"""Get the Tax Residences of the Customer specified by CUSTOMER_ID.
         |
        |
        |${authenticationRequiredMessage(true)}
         |
        |""",
      emptyObjectJson,
      taxResidenceJsonV310,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc, apiTagNewStyle))

    lazy val getTaxResidence : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "tax-residence" ::  Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetTaxResidence)
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (taxResidence, callContext) <- NewStyle.function.getTaxResidence(customerId, callContext)
          } yield {
            (JSONFactory310.createTaxResidence(taxResidence), HttpCode.`200`(callContext))
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc, apiTagNewStyle))

    lazy val deleteTaxResidence : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "tax_residencies" :: taxResidenceId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canDeleteTaxResidence)
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
      List(UserNotLoggedIn, UserNotSuperAdmin, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagRole, apiTagEntitlement, apiTagNewStyle))


    lazy val getAllEntitlements: OBPEndpoint = {
      case "entitlements" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- Helper.booleanToFuture(failMsg = UserNotSuperAdmin) {
              isSuperAdmin(u.userId)
            }
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
      "Add Address to Customer",
      s"""Add an Address to the Customer specified by CUSTOMER_ID.
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canCreateCustomerAddress)))

    lazy val createCustomerAddress : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "address" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canCreateCustomerAddress)
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
      "/banks/BANK_ID/customers/CUSTOMER_ID/address/CUSTOMER_ADDRESS_ID",
      "Update the Address of an Customer",
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagNewStyle))

    lazy val updateCustomerAddress : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "address" :: customerAddressId ::  Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canCreateCustomer)
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
      "/banks/BANK_ID/customers/CUSTOMER_ID/address",
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc, apiTagNewStyle),
      Some(List(canGetCustomerAddress)))

    lazy val getCustomerAddresses : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "address" ::  Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetCustomerAddress)
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer, apiTagKyc, apiTagNewStyle),
      Some(List(canDeleteCustomerAddress)))

    lazy val deleteCustomerAddress : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "addresses" :: customerAddressId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canDeleteCustomerAddress)
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (address, callContext) <- NewStyle.function.deleteCustomerAddress(customerAddressId, callContext)
          } yield {
            (Full(address), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getObpApiLoopback,
      implementedInApiVersion,
      nameOf(getObpApiLoopback),
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagApi, apiTagNewStyle))

    lazy val getObpApiLoopback : OBPEndpoint = {
      case "connector" :: "loopback" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- anonymousAccess(cc)
            (obpApiLoopback, callContext) <- NewStyle.function.getObpApiLoopback(callContext)
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
      "Refresh User.",
      s""" The endpoint is used for updating the accounts, views, account holders for the user.
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser, apiTagNewStyle))

    lazy val refreshUser : OBPEndpoint = {
      case "users" :: userId :: "refresh" :: Nil JsonPost _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", userId, canRefreshUser)
            startTime <- Future{Helpers.now}
            _ <- NewStyle.function.findByUserId(userId, callContext)
            _ <- if (APIUtil.isSandboxMode) Future{} else Future{ tryo {AuthUser.updateUserAccountViews(u, callContext)}} map {
              unboxFullOrFail(_, callContext, RefreshUserError)
            }
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagProduct, apiTagNewStyle),
      Some(List(canCreateProductAttribute))
    )

    lazy val createProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attribute" :: Nil JsonPost json -> _=> {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canCreateProductAttribute)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $ProductAttributeJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[ProductAttributeJson]
            }
            (productAttribute, callContext) <- NewStyle.function.createOrUpdateProductAttribute(
              BankId(bankId),
              ProductCode(productCode),
              None,
              postedData.name,
              ProductAttributeType.withName(postedData.`type`),
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagProduct, apiTagNewStyle))

    lazy val getProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attributes" :: productAttributeId :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canGetProductAttribute)
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagProduct, apiTagNewStyle))

    lazy val updateProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attributes" :: productAttributeId :: Nil JsonPut json -> _ =>{
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canUpdateProductAttribute)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $ProductAttributeJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[ProductAttributeJson]
            }
            (productAttribute, callContext) <- NewStyle.function.createOrUpdateProductAttribute(
              BankId(bankId),
              ProductCode(productCode),
              Some(productAttributeId),
              postedData.name,
              ProductAttributeType.withName(postedData.`type`),
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
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagProduct, apiTagNewStyle))

    lazy val deleteProductAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "products" :: productCode:: "attributes" :: productAttributeId ::  Nil JsonDelete _=> {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId, u.userId, canDeleteProductAttribute)
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
      Catalogs(notCore, notPSD2, notOBWG), 
      List(apiTagAccountApplication, apiTagAccount, apiTagNewStyle))

    lazy val createAccountApplication : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "account-applications" :: Nil JsonPost json -> _=> {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccountApplication, apiTagAccount, apiTagNewStyle))

    lazy val getAccountApplications : OBPEndpoint = {
      case "banks" :: BankId(bankId) ::"account-applications" ::  Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)

            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetAccountApplications)

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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccountApplication, apiTagAccount, apiTagNewStyle))

    lazy val getAccountApplication : OBPEndpoint = {
      case "banks" :: BankId(bankId) ::"account-applications":: accountApplicationId ::  Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)

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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccountApplication, apiTagAccount, apiTagNewStyle)
    )

    lazy val updateAccountApplicationStatus : OBPEndpoint = {
      case "banks" :: BankId(bankId) ::"account-applications" :: accountApplicationId :: Nil JsonPut json -> _  => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)

            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canUpdateAccountApplications)
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
                  (_, callContext) <- NewStyle.function.createSandboxBankAccount(
                                                                                 bankId, 
                                                                                 accountId, 
                                                                                 accountApplication.productCode.value,
                                                                                 "", 
                                                                                 "EUR",
                                                                                 BigDecimal("0"), 
                                                                                 u.name,
                                                                                 "", 
                                                                                 "", 
                                                                                 "",
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
      "/banks/BANK_ID/products/CODE",
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
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagProduct),
      Some(List(canCreateProduct, canCreateProductAtAnyBank))
    )

    lazy val createProduct: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: ProductCode(productCode) :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = createProductEntitlementsRequiredText)(bankId.value, u.userId, createProductEntitlements)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostPutProductJsonV310 "
            product <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostPutProductJsonV310]
            }
            parentProductCode <- product.parent_product_code.nonEmpty match {
              case false => 
                Future(Empty)
              case true =>
                Future(Connector.connector.vend.getProduct(bankId, ProductCode(product.parent_product_code))) map {
                  getFullBoxOrFail(_, callContext, ProductNotFoundByProductCode + " {" + product.parent_product_code + "}", 400)
                }
            }
            success <- Future(Connector.connector.vend.createOrUpdateProduct(
              bankId = product.bank_id,
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
         |${authenticationRequiredMessage(!getProductsIsPublic)}""",
      emptyObjectJson,
      productJsonV310,
      List(
        UserNotLoggedIn,
        ProductNotFoundByProductCode,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagProduct)
    )

    lazy val getProduct: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: ProductCode(productCode) :: Nil JsonGet _ => {
        cc => {
          for {
            (_, callContext) <- 
              getProductsIsPublic match {
                case false => authorizedAccess(cc)
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
         |${authenticationRequiredMessage(!getProductsIsPublic)}""",
      emptyObjectJson,
      childProductTreeJsonV310,
      List(
        UserNotLoggedIn,
        ProductNotFoundByProductCode,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagProduct)
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
            (_, callContext) <-
              getProductsIsPublic match {
                case false => authorizedAccess(cc)
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
         |${authenticationRequiredMessage(!getProductsIsPublic)}""",
      emptyObjectJson,
      productsJsonV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
        ProductNotFoundByProductCode,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagProduct)
    )

    lazy val getProducts : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: Nil JsonGet _ => {
        cc => {
          for {
            (_, callContext) <-
              getProductsIsPublic match {
                case false => authorizedAccess(cc)
                case true => anonymousAccess(cc)
              }
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            products <- Future(Connector.connector.vend.getProducts(bankId)) map {
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
      "/banks/BANK_ID/accounts/ACCOUNT_ID/products/CODE/attribute",
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagNewStyle))

    lazy val createAccountAttribute : OBPEndpoint = {
      case "banks" :: bankId :: "accounts" :: accountId :: "products" :: productCode :: "attribute" :: Nil JsonPost json -> _=> {
        cc =>
          for {
            (_, callContext) <- authorizedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), callContext)
            (_, callContext) <- NewStyle.function.getBankAccount(BankId(bankId), AccountId(accountId), callContext)
            _  <- Future(Connector.connector.vend.getProduct(BankId(bankId), ProductCode(productCode))) map {
              getFullBoxOrFail(_, callContext, ProductNotFoundByProductCode + " {" + productCode + "}", 400)
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $AccountAttributeJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AccountAttributeJson]
            }
            (accountAttribute, callContext) <- NewStyle.function.createOrUpdateAccountAttribute(
              BankId(bankId),
              AccountId(accountId),
              ProductCode(productCode),
              None,
              postedData.name,
              AccountAttributeType.withName(postedData.`type`),
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
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagProductCollection, apiTagProduct),
      Some(List(canMaintainProductCollection))
    )

    lazy val createProductCollection: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "product-collections" :: collectionCode :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canMaintainProductCollection)
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
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagProductCollection, apiTagProduct)
    )

    lazy val getProductCollection : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "product-collections" :: collectionCode :: Nil JsonGet _ => {
        cc => {
          for {
            (_, callContext) <- authorizedAccess(cc)
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
      Catalogs(notCore, notPSD2, OBWG),
      List(apiTagBranch),
      Some(List(canDeleteBranch,canDeleteBranchAtAnyBank))
    )

    lazy val deleteBranch: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "branches" :: BranchId(branchId) :: Nil JsonDelete _  => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            allowedEntitlements = canDeleteBranch ::canDeleteBranchAtAnyBank:: Nil
            allowedEntitlementsTxt = allowedEntitlements.mkString(" or ")
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = UserHasMissingRoles + allowedEntitlementsTxt)(bankId.value, u.userId, allowedEntitlements)
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMeeting, apiTagKyc, apiTagCustomer, apiTagUser, apiTagExperimental))
    
    lazy val createMeeting: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "meetings" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
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
  
            creator = ContactDetails(createMeetingJson.creator.name,createMeetingJson.creator.mobile_phone,createMeetingJson.creator.email_addresse)
            invitees  = createMeetingJson.invitees.map(
              invitee =>
                Invitee(
                  ContactDetails(invitee.contact_details.name, invitee.contact_details.mobile_phone,invitee.contact_details.email_addresse),
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMeeting, apiTagKyc, apiTagCustomer, apiTagUser, apiTagExperimental))

    lazy val getMeetings: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "meetings" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
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
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagMeeting, apiTagKyc, apiTagCustomer, apiTagUser, apiTagExperimental))

    lazy val getMeeting: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "meetings" :: meetingId :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizedAccess(cc)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (meeting, callContext) <- NewStyle.function.getMeeting(bank.bankId, u, meetingId, callContext)
          } yield {
            (JSONFactory310.createMeetingJson(meeting), HttpCode.`200`(callContext))
          }
      }
    }


  }
}

object APIMethods310 {
}
