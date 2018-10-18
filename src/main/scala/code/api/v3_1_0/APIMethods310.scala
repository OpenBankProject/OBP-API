package code.api.v3_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{BankAccountNotFound, _}
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.v1_2_1.JSONFactory
import code.api.v2_1_0.JSONFactory210
import code.api.v3_0_0.JSONFactory300
import code.api.v3_0_0.JSONFactory300.createAdapterInfoJson
import code.api.v3_1_0.JSONFactory310._
import code.bankconnectors.{Connector, OBPBankId}
import code.consumer.Consumers
import code.loginattempts.LoginAttempt
import code.metrics.APIMetrics
import code.model._
import code.users.Users
import code.util.Helper
import code.webhook.AccountWebHook
import net.liftweb.common.Full
import net.liftweb.http.provider.HTTPParam
import net.liftweb.http.rest.RestHelper

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait APIMethods310 {
  self: RestHelper =>

  val Implementations3_1_0 = new Object() {

    val implementedInApiVersion: ApiVersion = ApiVersion.v3_1_0

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)

    resourceDocs += ResourceDoc(
      getCheckbookOrders,
      implementedInApiVersion,
      "getCheckbookOrders",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/checkbook/orders",
      "get Checkbook orders",
      """Get all checkbook orders""",
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
      apiTagBank :: apiTagNewStyle :: Nil)

    lazy val getCheckbookOrders : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "checkbook"  :: "orders" :: Nil JsonGet req => {
        cc =>
          for {
            (_, callContext) <- extractCallContext(UserNotLoggedIn, cc)

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)

            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)

            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
            
            (checkbookOrders, callContext)<- Connector.connector.vend.getCheckbookOrdersFuture(bankId.value,accountId.value, callContext) map {
              unboxFullOrFail(_, callContext, InvalidConnectorResponseForGetCheckbookOrdersFuture, 400)
            }
          } yield
           (JSONFactory310.createCheckbookOrdersJson(checkbookOrders), HttpCode.`200`(callContext))
      }
    }
    
    resourceDocs += ResourceDoc(
      getStatusOfCreditCardOrder,
      implementedInApiVersion,
      "getStatusOfCreditCardOrder",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/credit_cards/orders",
      "Get status of Credit Card order ",
      """Get status of Credit Card orders
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
      apiTagBank :: apiTagNewStyle :: Nil)

    lazy val getStatusOfCreditCardOrder : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "credit_cards"  :: "orders" :: Nil JsonGet req => {
        cc =>
          for {
            (_, callContext) <- extractCallContext(UserNotLoggedIn, cc)

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)

            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)

            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
            
            //TODO need error handling here
            (checkbookOrders,callContext) <- Connector.connector.vend.getStatusOfCreditCardOrderFuture(bankId.value,accountId.value, callContext) map {
              unboxFullOrFail(_, callContext, InvalidConnectorResponseForGetStatusOfCreditCardOrderFuture, 400)
            }
            
          } yield
           (JSONFactory310.createStatisOfCreditCardJson(checkbookOrders), HttpCode.`200`(callContext))
      }
    }
    
    resourceDocs += ResourceDoc(
      createCreditLimitRequest,
      implementedInApiVersion,
      "createCreditLimitRequest",
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_ID/credit_limit/requests",
      "Create Credit Limit Order Request",
      """Create credit limit order request
        |""",
      creditLimitRequestJson,
      creditLimitOrderResponseJson,
      List(UnknownError),
      Catalogs(Core, notPSD2, OBWG),
      apiTagBank :: apiTagNewStyle :: Nil)

    lazy val createCreditLimitRequest : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: CustomerId(customerId) :: "credit_limit"  :: "requests" :: Nil JsonPost json -> _  => {
        cc =>
//          val a: Future[(ChecksOrderStatusResponseDetails, Some[CallContext])] = for {
//            banksBox <- Connector.connector.vend.getBanksFuture()
//            banks <- unboxFullAndWrapIntoFuture{ banksBox }
//          } yield
           Future{ (JSONFactory310.createCreditLimitOrderResponseJson(), HttpCode.`200`(cc))}
      }
    }
    
    resourceDocs += ResourceDoc(
      getCreditLimitRequests,
      implementedInApiVersion,
      "getCreditLimitRequests",
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/credit_limit/requests",
      "Get Credit Limit Order Requests ",
      """Get Credit Limit Order Requests 
        |""",
      emptyObjectJson,
      creditLimitOrderJson,
      List(UnknownError),
      Catalogs(Core, notPSD2, OBWG),
      apiTagBank :: Nil)

    lazy val getCreditLimitRequests : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: CustomerId(customerId) :: "credit_limit"  :: "requests" :: Nil JsonGet req => {
        cc =>
//          val a: Future[(ChecksOrderStatusResponseDetails, Some[CallContext])] = for {
//            banksBox <- Connector.connector.vend.getBanksFuture()
//            banks <- unboxFullAndWrapIntoFuture{ banksBox }
//          } yield
           Future{ (JSONFactory310.getCreditLimitOrderResponseJson(), HttpCode.`200`(cc))}
      }
    }

    resourceDocs += ResourceDoc(
      getCreditLimitRequestByRequestId,
      implementedInApiVersion,
      "getCreditLimitRequestByRequestId",
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/credit_limit/requests/REQUEST_ID",
      "Get Credit Limit Order Request By Request Id",
      """Get Credit Limit Order Request By Request Id
        |""",
      emptyObjectJson,
      creditLimitOrderJson,
      List(UnknownError),
      Catalogs(Core, notPSD2, OBWG),
      apiTagBank :: apiTagNewStyle :: Nil)

    lazy val getCreditLimitRequestByRequestId : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: CustomerId(customerId) :: "credit_limit"  :: "requests" :: requestId :: Nil JsonGet req => {
        cc =>
//          val a: Future[(ChecksOrderStatusResponseDetails, Some[CallContext])] = for {
//            banksBox <- Connector.connector.vend.getBanksFuture()
//            banks <- unboxFullAndWrapIntoFuture{ banksBox }
//          } yield
           Future{ (JSONFactory310.getCreditLimitOrderByRequestIdResponseJson(), HttpCode.`200`(cc))}
      }
    }
    
    resourceDocs += ResourceDoc(
      getTopAPIs,
      implementedInApiVersion,
      "getTopAPIs",
      "GET",
      "/management/metrics/top-apis",
      "get top apis",
      s"""Returns get top apis. eg. total count, response time (in ms), etc.
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
            
            (Full(u), callContext) <- extractCallContext(UserNotLoggedIn, cc)

            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanReadMetrics)("", u.userId, ApiRole.canReadMetrics)
            
            httpParams <- NewStyle.function.createHttpParams(cc.url)
              
            obpQueryParams <- createQueriesByHttpParamsFuture(httpParams) map {
              unboxFullOrFail(_, callContext, InvalidFilterParameterFormat, 400)
            }
            
            toApis <- APIMetrics.apiMetrics.vend.getTopApisFuture(obpQueryParams) map {
                unboxFullOrFail(_, callContext, GetTopApisError, 400)
            }
          } yield
           (JSONFactory310.createTopApisJson(toApis), HttpCode.`200`(callContext))
      }
    }
    
    resourceDocs += ResourceDoc(
      getMetricsTopConsumers,
      implementedInApiVersion,
      "getMetricsTopConsumers",
      "GET",
      "/management/metrics/top-consumers",
      "get metrics top consumers",
      s"""get metrics top consumers on api usage eg. total count, consumer_id and app_name.
        |
        |Should be able to filter on the following fields
        |
        |eg: /management/metrics/top-consumers?from_date=$DateWithMsExampleString&to_date=2017-05-22T01:02:03.000Z&consumer_id=5
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
            
            (Full(u), callContext) <- extractCallContext(UserNotLoggedIn, cc)

            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanReadMetrics)("", u.userId, ApiRole.canReadMetrics)
            
            httpParams <- NewStyle.function.createHttpParams(cc.url)
              
            obpQueryParams <- createQueriesByHttpParamsFuture(httpParams) map {
                unboxFullOrFail(_, callContext, InvalidFilterParameterFormat, 400)
            }
            
            topConsumers <- APIMetrics.apiMetrics.vend.getTopConsumersFuture(obpQueryParams) map {
              unboxFullOrFail(_, callContext, GetMetricsTopConsumersError, 400)
            }
            
          } yield
           (JSONFactory310.createTopConsumersJson(topConsumers), HttpCode.`200`(callContext))
      }
    }










    resourceDocs += ResourceDoc(
      getFirehoseCustomers,
      implementedInApiVersion,
      "getFirehoseCustomers",
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
            (Full(u), callContext) <-  extractCallContext(UserNotLoggedIn, cc)
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
      "getBadLoginStatus",
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
            (Full(u), callContext) <-  extractCallContext(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanReadUserLockedStatus)("", u.userId, ApiRole.canReadUserLockedStatus)
            badLoginStatus <- Future { LoginAttempt.getBadLoginStatus(username) } map { unboxFullOrFail(_, callContext, s"$UserNotFoundByUsername($username)",400) }
          } yield {
            (createBadLoginStatusJson(badLoginStatus), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      unlockUser,
      implementedInApiVersion,
      "unlockUser",
      "PUT",
      "/users/USERNAME/lock-status",
      "Unlock the user",
      s"""
         |Get Customers that has a firehose View.
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
            (Full(u), callContext) <-  extractCallContext(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanUnlockUser)("", u.userId, ApiRole.canUnlockUser)
            _ <- Future { LoginAttempt.resetBadLoginAttempts(username) } 
            badLoginStatus <- Future { LoginAttempt.getBadLoginStatus(username) } map { unboxFullOrFail(_, callContext, s"$UserNotFoundByUsername($username)",400) }
          } yield {
            (createBadLoginStatusJson(badLoginStatus), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      callsLimit,
      implementedInApiVersion,
      "callsLimit",
      "PUT",
      "/management/consumers/CONSUMER_ID/consumer/calls_limit",
      "Set Calls' Limit for a Consumer",
      s"""
         |Set calls' limit per Consumer.
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      callLimitJson,
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

    lazy val callsLimit : OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "calls_limit" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <-  extractCallContext(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanSetCallLimits)("", u.userId, canSetCallLimits)
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CallLimitJson ", 400, callContext) {
              json.extract[CallLimitJson]
            }
            consumerIdToLong <- NewStyle.function.tryons(s"$InvalidConsumerId", 400, callContext) {
              consumerId.toLong
            }
            consumer <- NewStyle.function.getConsumerByPrimaryId(consumerIdToLong, callContext)
            updatedConsumer <- Consumers.consumers.vend.updateConsumerCallLimits(
              consumer.id.get,
              Some(postJson.per_minute_call_limit),
              Some(postJson.per_hour_call_limit),
              Some(postJson.per_day_call_limit),
              Some(postJson.per_week_call_limit),
              Some(postJson.per_month_call_limit)) map {
              unboxFullOrFail(_, callContext, UpdateConsumerError, 400)
            }
          } yield {
            (createCallLimitJson(updatedConsumer), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getCallsLimit,
      implementedInApiVersion,
      "getCallsLimit",
      "GET",
      "/management/consumers/CONSUMER_ID/consumer/calls_limit",
      "Get Calls' Limit for a Consumer",
      s"""
         |Get calls' limit per Consumer.
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      callLimitJson,
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
      case "management" :: "consumers" :: consumerId :: "consumer" :: "calls_limit" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <-  extractCallContext(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanReadCallLimits)("", u.userId, canReadCallLimits)
            consumerIdToLong <- NewStyle.function.tryons(s"$InvalidConsumerId", 400, callContext) {
              consumerId.toLong
            }
            consumer <- NewStyle.function.getConsumerByPrimaryId(consumerIdToLong, callContext)
          } yield {
            (createCallLimitJson(consumer), HttpCode.`200`(callContext))
          }
      }
    }




    resourceDocs += ResourceDoc(
      checkFundsAvailable,
      implementedInApiVersion,
      "checkFundsAvailable",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/funds-available",
      "Check available funds",
      """Check Available Funds
        |Possible custom URL parameters for pagination:
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
      apiTagBank :: apiTagNewStyle :: Nil)

    lazy val checkFundsAvailable : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "funds-available" :: Nil JsonGet req => {
        cc =>
          val amount = "amount"
          val currency = "currency"
          for {
            (Full(u), callContext) <- extractCallContext(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanCheckFundsAvailable)("", u.userId, canCheckFundsAvailable)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
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
            _ <- Helper.booleanToFuture(failMsg = InvalidISOCurrencyCode) {
              val currencyCode = httpParams.filter(_.name == currency).map(_.values.head).head
              isValidCurrencyISOCode(currencyCode)
            }
            _ <- Future {account.moderatedBankAccount(view, Full(u)) } map {
              fullBoxOrException(_)
            } map { unboxFull(_) }
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
      "getConsumer",
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
            (Full(u), callContext) <- extractCallContext(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanGetConsumers)("", u.userId, ApiRole.canGetConsumers)
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
      "getConsumersForCurrentUser",
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
            (Full(u), callContext) <- extractCallContext(UserNotLoggedIn, cc)
            consumer <- Consumers.consumers.vend.getConsumersByUserIdFuture(u.userId)
          } yield {
            (createConsumersJson(consumer, Full(u)), HttpCode.`200`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      getConsumers,
      implementedInApiVersion,
      "getConsumers",
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
            (Full(u), callContext) <- extractCallContext(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanGetConsumers)("", u.userId, ApiRole.canGetConsumers)
            consumers <- Consumers.consumers.vend.getConsumersFuture()
            users <- Users.users.vend.getUsersByUserIdsFuture(consumers.map(_.createdByUserId.get))
          } yield {
            (createConsumersJson(consumers, users), HttpCode.`200`(callContext))
          }
      }
    }



    resourceDocs += ResourceDoc(
      createAccountWebHook,
      implementedInApiVersion,
      "createAccountWebHook",
      "POST",
      "/banks/BANK_ID/account-web-hooks",
      "Create an Account Web Hook",
      """Create an Account Web Hook
        |""",
      accountWebHookPostJson,
      accountWebHookJson,
      List(UnknownError),
      Catalogs(Core, notPSD2, OBWG),
      apiTagWebHook :: apiTagBank :: apiTagNewStyle :: Nil,
      Some(List(canCreateWebHook))
    )

    lazy val createAccountWebHook : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "account-web-hooks" :: Nil JsonPost json -> _  => {
        cc =>
          for {
            (Full(u), callContext) <- extractCallContext(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanCreateWebHook)(bankId.value, u.userId, ApiRole.canCreateWebHook)
            failMsg = s"$InvalidJsonFormat The Json body should be the $AccountWebHookPostJson "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AccountWebHookPostJson]
            }
            failMsg = IncorrectTriggerName + postJson.trigger_name + ". Possible values are " + ApiTrigger.availableTriggers.sorted.mkString(", ")
            _ <- NewStyle.function.tryons(failMsg, 400, callContext) {
              ApiTrigger.valueOf(postJson.trigger_name)
            }
            failMsg = s"$InvalidBoolean Possible values of the json field is_active are true or false."
            isActive <- NewStyle.function.tryons(failMsg, 400, callContext) {
              postJson.is_active.toBoolean
            }
            wh <- AccountWebHook.accountWebHook.vend.createAccountWebHookFuture(
              bankId = bankId.value,
              accountId = postJson.account_id,
              userId = u.userId,
              triggerName = postJson.trigger_name,
              url = postJson.url,
              httpMethod = postJson.http_method,
              isActive = isActive
            ) map {
              unboxFullOrFail(_, callContext, CreateWebHookError, 400)
            }
          } yield {
            (createAccountWebHookJson(wh), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      enableDisableAccountWebHook,
      implementedInApiVersion,
      "enableDisableAccountWebHook",
      "PUT",
      "/banks/BANK_ID/account-web-hooks",
      "Update an Account Web Hook",
      """Update an Account Web Hook
        |""",
      accountWebHookPutJson,
      accountWebHookJson,
      List(UnknownError),
      Catalogs(Core, notPSD2, OBWG),
      apiTagWebHook :: apiTagBank :: apiTagNewStyle :: Nil,
      Some(List(canCreateWebHook))
    )

    lazy val enableDisableAccountWebHook : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "account-web-hooks" :: Nil JsonPut json -> _  => {
        cc =>
          for {
            (Full(u), callContext) <- extractCallContext(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanUpdateWebHook)(bankId.value, u.userId, ApiRole.canUpdateWebHook)
            failMsg = s"$InvalidJsonFormat The Json body should be the $AccountWebHookPutJson "
            putJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AccountWebHookPutJson]
            }
            failMsg = s"$InvalidBoolean Possible values of the json field is_active are true or false."
            isActive <- NewStyle.function.tryons(failMsg, 400, callContext) {
              putJson.is_active.toBoolean
            }
            _ <- AccountWebHook.accountWebHook.vend.getAccountWebHookByIdFuture(putJson.account_web_hook_id) map {
              unboxFullOrFail(_, callContext, WebHookNotFound, 400)
            }
            wh <- AccountWebHook.accountWebHook.vend.updateAccountWebHookFuture(
              accountWebHookId = putJson.account_web_hook_id,
              isActive = isActive
            ) map {
              unboxFullOrFail(_, callContext, UpdateWebHookError, 400)
            }
          } yield {
            (createAccountWebHookJson(wh), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getAccountWebHooks,
      implementedInApiVersion,
      "getAccountWebHooks",
      "GET",
      "/management/banks/BANK_ID/account-web-hooks",
      "Get Account Web Hooks",
      s"""Get Account Web Hooks.
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
      accountWebHooksJson,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagWebHook :: apiTagBank :: apiTagNewStyle :: Nil,
      Some(List(canGetWebHooks))
    )


    lazy val getAccountWebHooks: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) ::"account-web-hooks" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- extractCallContext(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanGetWebHooks)(bankId.value, u.userId, ApiRole.canGetWebHooks)
            httpParams <- NewStyle.function.createHttpParams(cc.url)
            allowedParams = List("limit", "offset", "account_id", "user_id")
            obpParams <- NewStyle.function.createObpParams(httpParams, allowedParams, callContext)
            additionalParam = OBPBankId(bankId.value)
            webHooks <- NewStyle.function.getAccountWebHooks(additionalParam :: obpParams, callContext)
          } yield {
            (createAccountWebHooksJson(webHooks), HttpCode.`200`(callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      config,
      implementedInApiVersion,
      "config",
      "GET",
      "/config",
      "Get API Configuration",
      """Returns information about:
        |
        |* API Config
        |* Default bank id
        |* Akka ports
        |* Elastic search ports
        |* Cached function """,
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
            (Full(u), callContext) <- extractCallContext(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanGetConfig)("", u.userId, ApiRole.canGetConfig)
          } yield {
            (JSONFactory310.getConfigInfoJSON(), HttpCode.`200`(callContext))
          }
    }

    resourceDocs += ResourceDoc(
      getAdapterInfo,
      implementedInApiVersion,
      "getAdapterInfo",
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
            (ai,cc) <- NewStyle.function.getAdapterInfo(Some(cc))
          } yield {
            (createAdapterInfoJson(ai), HttpCode.`200`(cc))
          }
      }
    }


    resourceDocs += ResourceDoc(
      getTransactionByIdForBankAccount,
      implementedInApiVersion,
      "getTransactionByIdForBankAccount",
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
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (account, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            view <- NewStyle.function.view(viewId, BankIdAccountId(account.bankId, account.accountId), callContext)
            (moderatedTransaction, callContext) <- Future(account.moderatedTransaction(transactionId, view, user, callContext)) map {
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
      "getTransactionRequests",
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
            (Full(u), callContext) <- extractCallContext(UserNotLoggedIn, cc)
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

    
  }
}

object APIMethods310 {
}
