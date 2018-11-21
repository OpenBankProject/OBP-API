package code.api.v3_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{BankAccountNotFound, _}
import code.api.util.NewStyle.HttpCode
import code.api.util._
import code.api.v1_2_1.{JSONFactory, RateLimiting}
import code.api.v2_1_0.JSONFactory210
import code.api.v3_0_0.JSONFactory300
import code.api.v3_0_0.JSONFactory300.createAdapterInfoJson
import code.api.v3_1_0.JSONFactory310._
import code.bankconnectors.{Connector, OBPBankId}
import code.consumer.Consumers
import code.customer.{CreditLimit, CreditRating, CustomerFaceImage}
import code.entitlement.Entitlement
import code.loginattempts.LoginAttempt
import code.metrics.APIMetrics
import code.model._
import code.model.dataAccess.AuthUser
import code.users.Users
import code.util.Helper
import code.webhook.AccountWebhook
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.Full
import net.liftweb.http.provider.HTTPParam
import net.liftweb.http.rest.RestHelper
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo

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
      nameOf(getCheckbookOrders),
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
            (_, callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)

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
      nameOf(getStatusOfCreditCardOrder),
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
            (_, callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)

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
      nameOf(createCreditLimitRequest),
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
      nameOf(getCreditLimitRequests),
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
      nameOf(getCreditLimitRequestByRequestId),
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
            
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)

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
            
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)

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
            (Full(u), callContext) <-  authorizeEndpoint(UserNotLoggedIn, cc)
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
            (Full(u), callContext) <-  authorizeEndpoint(UserNotLoggedIn, cc)
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
      nameOf(unlockUser),
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
            (Full(u), callContext) <-  authorizeEndpoint(UserNotLoggedIn, cc)
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
      nameOf(callsLimit),
      "PUT",
      "/management/consumers/CONSUMER_ID/consumer/calls_limit",
      "Set Calls Limit for a Consumer",
      s"""
         |Set the API call limits for a Consumer:
         |
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
      case "management" :: "consumers" :: consumerId :: "consumer" :: "calls_limit" :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <-  authorizeEndpoint(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanSetCallLimits)("", u.userId, canSetCallLimits)
            postJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CallLimitPostJson ", 400, callContext) {
              json.extract[CallLimitPostJson]
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
            (createCallLimitJson(updatedConsumer, Nil), HttpCode.`200`(callContext))
          }
      }
    }



    // TODO Change endpoint to ../call-limits
    resourceDocs += ResourceDoc(
      getCallsLimit,
      implementedInApiVersion,
      nameOf(getCallsLimit),
      "GET",
      "/management/consumers/CONSUMER_ID/consumer/calls_limit",
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



    // TODO Change endpoint to ../call-limits
    lazy val getCallsLimit : OBPEndpoint = {
      case "management" :: "consumers" :: consumerId :: "consumer" :: "calls_limit" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <-  authorizeEndpoint(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanReadCallLimits)("", u.userId, canReadCallLimits)
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
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanGetConsumers)("", u.userId, ApiRole.canGetConsumers)
            consumers <- Consumers.consumers.vend.getConsumersFuture()
            users <- Users.users.vend.getUsersByUserIdsFuture(consumers.map(_.createdByUserId.get))
          } yield {
            (createConsumersJson(consumers, users), HttpCode.`200`(callContext))
          }
      }
    }


    val accountWebHookInfo = """Webhooks are used to call external URLs when certain events happen.
                               |
                               |Account Webhooks focus on events around accounts.
                               |
                               |For instance, a webhook could be used to notify an external serivce if a balance changes on an account.
                               |
                               |This functionality is work in progress! Although you can create and modify Webhooks, they do not yet fire on triggers."""


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
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanCreateWebhook)(bankId.value, u.userId, ApiRole.canCreateWebhook)
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
              isActive = isActive
            ) map {
              unboxFullOrFail(_, callContext, CreateWebhookError, 400)
            }
          } yield {
            (createAccountWebhookJson(wh), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      enableDisableAccountWebhook,
      implementedInApiVersion,
      nameOf(enableDisableAccountWebhook),
      "PUT",
      "/banks/BANK_ID/account-web-hooks",
      "Update an Account Webhook",
      s"""Update an Account Webhook
        |
        |
        |$accountWebHookInfo
        |""",
      accountWebhookPutJson,
      accountWebhookJson,
      List(UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      apiTagWebhook :: apiTagBank :: apiTagNewStyle :: Nil,
      Some(List(canCreateWebhook))
    )

    lazy val enableDisableAccountWebhook : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "account-web-hooks" :: Nil JsonPut json -> _  => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanUpdateWebhook)(bankId.value, u.userId, ApiRole.canUpdateWebhook)
            failMsg = s"$InvalidJsonFormat The Json body should be the $AccountWebhookPutJson "
            putJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[AccountWebhookPutJson]
            }
            failMsg = s"$InvalidBoolean Possible values of the json field is_active are true or false."
            isActive <- NewStyle.function.tryons(failMsg, 400, callContext) {
              putJson.is_active.toBoolean
            }
            _ <- AccountWebhook.accountWebhook.vend.getAccountWebhookByIdFuture(putJson.account_webhook_id) map {
              unboxFullOrFail(_, callContext, WebhookNotFound, 400)
            }
            wh <- AccountWebhook.accountWebhook.vend.updateAccountWebhookFuture(
              accountWebhookId = putJson.account_webhook_id,
              isActive = isActive
            ) map {
              unboxFullOrFail(_, callContext, UpdateWebhookError, 400)
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
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanGetWebhooks)(bankId.value, u.userId, ApiRole.canGetWebhooks)
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
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanGetConfig)("", u.userId, ApiRole.canGetConfig)
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
            (ai,cc) <- NewStyle.function.getAdapterInfo(Some(cc))
          } yield {
            (createAdapterInfoJson(ai), HttpCode.`200`(cc))
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
            (user, callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
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
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)

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
              unboxFullOrFail(_, callContext, CreateCustomerError, 400)
            }
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`200`(callContext))
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
            rateLimiting <- NewStyle.function.tryons("", 400, Some(cc)) {
              val useConsumerLimits = RateLimitUtil.useConsumerLimits
              val isRedisAvailable = RateLimitUtil.isRedisAvailable()
              val isActive = if(useConsumerLimits == true && isRedisAvailable == true) true else false
              RateLimiting(useConsumerLimits, "REDIS", isRedisAvailable, isActive)
            }
          } yield {
            (createRateLimitingInfo(rateLimiting), HttpCode.`200`(cc))
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
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanGetCustomer)(bankId.value, u.userId, canGetCustomer)
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
      List(apiTagCustomer, apiTagNewStyle))

    lazy val getCustomerByCustomerNumber : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: "customer-number" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanGetCustomer)(bankId.value, u.userId, canGetCustomer)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostCustomerNumberJsonV310 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostCustomerNumberJsonV310]
            }
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerNumber(postedData.customer_number, bank.bankId, callContext)
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`200`(callContext))
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
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanCreateUserAuthContext)("", u.userId, canCreateUserAuthContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserAuthContextJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostUserAuthContextJson]
            }
            (_, callContext) <- NewStyle.function.findByUserId(userId, callContext)
            (userAuthContext, callContext) <- NewStyle.function.createUserAuthContext(userId, postedData.key, postedData.value, callContext)
          } yield {
            (JSONFactory310.createUserAuthContextJson(userAuthContext), HttpCode.`200`(callContext))
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
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanGetUserAuthContext)("", u.userId, canGetUserAuthContext)
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
      List(apiTagCustomer, apiTagNewStyle))

    lazy val deleteUserAuthContexts : OBPEndpoint = {
      case "users" :: userId :: "auth-context" :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanDeleteUserAuthContext)("", u.userId, canDeleteUserAuthContext)
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
      "Delete User AuthContext",
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
      List(apiTagCustomer, apiTagNewStyle))

    lazy val deleteUserAuthContextById : OBPEndpoint = {
      case "users" :: userId :: "auth-context" :: userAuthContextId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanDeleteUserAuthContext)("", u.userId, canDeleteUserAuthContext)
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
      List(apiTagCustomer, apiTagNewStyle))

    lazy val createTaxResidence : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "tax-residence" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanCreateTaxResidence)(bankId.value, u.userId, canCreateTaxResidence)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostTaxResidenceJsonV310 "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostTaxResidenceJsonV310]
            }
            (_, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
            (taxResidence, callContext) <- NewStyle.function.createTaxResidence(customerId, postedData.domain, postedData.tax_number, callContext)
          } yield {
            (JSONFactory310.createTaxResidence(List(taxResidence)), HttpCode.`200`(callContext))
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
      List(apiTagCustomer, apiTagNewStyle))

    lazy val getTaxResidence : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "tax-residence" ::  Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanGetTaxResidence)(bankId.value, u.userId, canGetTaxResidence)
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
      List(apiTagCustomer, apiTagNewStyle))

    lazy val deleteTaxResidence : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "tax_residencies" :: taxResidenceId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanDeleteTaxResidence)(bankId.value, u.userId, canDeleteTaxResidence)
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
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            _ <- Helper.booleanToFuture(failMsg = UserNotSuperAdmin) {
              isSuperAdmin(u.userId)
            }
            roleName = APIUtil.getHttpRequestUrlParam(cc.url, "role")
            entitlements <- Entitlement.entitlement.vend.getEntitlementsByRoleFuture(roleName) map {
              unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
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
      List(apiTagCustomer, apiTagNewStyle))

    lazy val createCustomerAddress : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "address" ::  Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanCreateCustomer)(bankId.value, u.userId, canCreateCustomer)
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
      List(apiTagCustomer, apiTagNewStyle))

    lazy val getCustomerAddresses : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "address" ::  Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanGetCustomer)(bankId.value, u.userId, canGetCustomer)
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
      List(apiTagCustomer, apiTagNewStyle))

    lazy val deleteCustomerAddress : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "addresses" :: customerAddressId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanCreateCustomer)(bankId.value, u.userId, canCreateCustomer)
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
            (obpApiLoopback, callContext) <- NewStyle.function.getObpApiLoopback(Some(cc))
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
      List(apiTagApi, apiTagNewStyle))

    lazy val refreshUser : OBPEndpoint = {
      case "users" :: userId :: "refresh" :: Nil JsonPost _ => {
        cc =>
          for {
            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
            _ <- NewStyle.function.hasEntitlement(failMsg = UserHasMissingRoles + CanRefreshUser)("", userId, canRefreshUser)
            startTime <- Future{Helpers.now}
            _ <- NewStyle.function.findByUserId(userId, Some(cc))
            _ <- if (APIUtil.isSandboxMode) Future{} else Future{ tryo {AuthUser.updateUserAccountViews(u, callContext)}} map {
              unboxFullOrFail(_, callContext, RefreshUserError, 400)
            }
            endTime <- Future{Helpers.now}
            durationTime = endTime.getTime - startTime.getTime
          } yield {
            (createRefreshUserJson(durationTime), HttpCode.`201`(callContext))
          }
      }
    }

  }
}

object APIMethods310 {
}
