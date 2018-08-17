package code.api.v3_1_0

import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole.{CanReadMetrics, CanUnlockUser, CanUseFirehoseAtAnyBank, canUseFirehoseAtAnyBank}
import code.api.util.ErrorMessages._
import code.api.util._
import code.api.v3_0_0.JSONFactory300
import code.api.v3_1_0.JSONFactory310._
import code.bankconnectors.Connector
import code.loginattempts.LoginAttempt
import code.metrics.APIMetrics
import code.model._
import code.util.Helper
import code.views.Views
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
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      Catalogs(Core, notPSD2, OBWG),
      apiTagBank :: Nil)

    lazy val getCheckbookOrders : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "checkbook"  :: "orders" :: Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }

            bankBox <- Connector.connector.vend.getBankFuture(bankId) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(BankNotFound, 400, Some(cc.toLight)))
              } map { unboxFull(_) }

            account <- Future { Connector.connector.vend.checkBankAccountExists(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, Some(cc.toLight)))
              } map { unboxFull(_) }

            view <- Views.views.vend.viewFuture(viewId, BankIdAccountId(account.bankId, account.accountId)) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(ViewNotFound, 400, Some(cc.toLight)))
              } map { unboxFull(_) }
            
            //TODO need error handling here
            checkbookOrders <- Connector.connector.vend.getCheckbookOrdersFuture(bankId.value,accountId.value, Some(cc)) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetCheckbookOrdersFuture, 400, Some(cc.toLight)))
              } map { unboxFull(_) }
          } yield
           (JSONFactory310.createCheckbookOrdersJson(checkbookOrders), Some(cc))
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
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      Catalogs(Core, notPSD2, OBWG),
      apiTagBank :: Nil)

    lazy val getStatusOfCreditCardOrder : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "credit_cards"  :: "orders" :: Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }

            bankBox <- Connector.connector.vend.getBankFuture(bankId) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(BankNotFound, 400, Some(cc.toLight)))
              } map { unboxFull(_) }

            account <- Future { Connector.connector.vend.checkBankAccountExists(bankId, accountId, callContext) } map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, Some(cc.toLight)))
              } map { unboxFull(_) }

            view <- Views.views.vend.viewFuture(viewId, BankIdAccountId(account.bankId, account.accountId)) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(ViewNotFound, 400, Some(cc.toLight)))
              } map { unboxFull(_) }
            
            //TODO need error handling here
            checkbookOrders <- Connector.connector.vend.getStatusOfCreditCardOrderFuture(bankId.value,accountId.value, Some(cc)) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetStatusOfCreditCardOrderFuture, 400, Some(cc.toLight)))
              } map { unboxFull(_) }
            
          } yield
           (JSONFactory310.createStatisOfCreditCardJson(checkbookOrders), Some(cc))
      }
    }
    
    resourceDocs += ResourceDoc(
      createCreditLimitOrderRequest,
      implementedInApiVersion,
      "createCreditLimitOrderRequest",
      "POST",
      "/banks/BANK_ID/customers/CUSTOMER_ID/credit_limit/requests",
      "Create Credit Limit Order Request",
      """Create credit limit order request
        |""",
      creditLimitOrderRequestJson,
      creditLimitOrderResponseJson,
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      Catalogs(Core, notPSD2, OBWG),
      apiTagBank :: Nil)

    lazy val createCreditLimitOrderRequest : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: CustomerId(customerId) :: "credit_limit"  :: "requests" :: Nil JsonPost json -> _  => {
        cc =>
//          val a: Future[(ChecksOrderStatusResponseDetails, Some[CallContext])] = for {
//            banksBox <- Connector.connector.vend.getBanksFuture()
//            banks <- unboxFullAndWrapIntoFuture{ banksBox }
//          } yield
           Future{ (JSONFactory310.createCreditLimitOrderResponseJson(), Some(cc))}
      }
    }
    
    resourceDocs += ResourceDoc(
      getCreditLimitOrderRequests,
      implementedInApiVersion,
      "getCreditLimitOrderRequests",
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/credit_limit/requests",
      "Get Credit Limit Order Requests ",
      """Get Credit Limit Order Requests 
        |""",
      emptyObjectJson,
      creditLimitOrderJson,
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      Catalogs(Core, notPSD2, OBWG),
      apiTagBank :: Nil)

    lazy val getCreditLimitOrderRequests : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: CustomerId(customerId) :: "credit_limit"  :: "requests" :: Nil JsonGet req => {
        cc =>
//          val a: Future[(ChecksOrderStatusResponseDetails, Some[CallContext])] = for {
//            banksBox <- Connector.connector.vend.getBanksFuture()
//            banks <- unboxFullAndWrapIntoFuture{ banksBox }
//          } yield
           Future{ (JSONFactory310.getCreditLimitOrderResponseJson(), Some(cc))}
      }
    }

    resourceDocs += ResourceDoc(
      getCreditLimitOrderRequestByRequestId,
      implementedInApiVersion,
      "getCreditLimitOrderRequestByRequestId",
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/credit_limit/requests/REQUEST_ID",
      "Get Creadit Limit Order Request By Request Id",
      """Get Creadit Limit Order Request By Request Id
        |""",
      emptyObjectJson,
      creditLimitOrderJson,
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      Catalogs(Core, notPSD2, OBWG),
      apiTagBank :: Nil)

    lazy val getCreditLimitOrderRequestByRequestId : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: CustomerId(customerId) :: "credit_limit"  :: "requests" :: requestId :: Nil JsonGet req => {
        cc =>
//          val a: Future[(ChecksOrderStatusResponseDetails, Some[CallContext])] = for {
//            banksBox <- Connector.connector.vend.getBanksFuture()
//            banks <- unboxFullAndWrapIntoFuture{ banksBox }
//          } yield
           Future{ (JSONFactory310.getCreditLimitOrderByRequestIdResponseJson(), Some(cc))}
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
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      Catalogs(Core, notPSD2, OBWG),
      apiTagMetric :: Nil)

    lazy val getTopAPIs : OBPEndpoint = {
      case "management" :: "metrics" :: "top-apis" :: Nil JsonGet req => {
        cc =>
          for {
            
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            
            _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + CanReadMetrics) {
              hasEntitlement("", u.userId, ApiRole.canReadMetrics)
            }
            
            httpParams <- createHttpParamsByUrlFuture(cc.url) map { unboxFull(_) }
              
            obpQueryParams <- createQueriesByHttpParamsFuture(httpParams) map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidFilterParameterFormat, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
            
            toApis <- APIMetrics.apiMetrics.vend.getTopApisFuture(obpQueryParams) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(GetTopApisError, 400, Some(cc.toLight)))
              } map { unboxFull(_) }
          } yield
           (JSONFactory310.createTopApisJson(toApis), Some(cc))
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
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      Catalogs(Core, notPSD2, OBWG),
      apiTagMetric :: Nil)

    lazy val getMetricsTopConsumers : OBPEndpoint = {
      case "management" :: "metrics" :: "top-consumers" :: Nil JsonGet req => {
        cc =>
          for {
            
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            
            _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + CanReadMetrics) {
              hasEntitlement("", u.userId, ApiRole.canReadMetrics)
            }
            
            httpParams <- createHttpParamsByUrlFuture(cc.url) map { unboxFull(_) }
              
            obpQueryParams <- createQueriesByHttpParamsFuture(httpParams) map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidFilterParameterFormat, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
            
            topConsumers <- APIMetrics.apiMetrics.vend.getTopConsumersFuture(obpQueryParams) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(GetMetricsTopConsumersError, 400, Some(cc.toLight)))
              } map { unboxFull(_) }
            
          } yield
           (JSONFactory310.createTopConsumersJson(topConsumers), Some(cc))
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
      List(apiTagAccountFirehose, apiTagAccount, apiTagFirehoseData),
      Some(List(canUseFirehoseAtAnyBank)))

    lazy val getFirehoseCustomers : OBPEndpoint = {
      //get private accounts for all banks
      case "banks" :: BankId(bankId):: "firehose" ::  "customers" :: Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            _ <- Future { Bank(bankId) } map { unboxFullOrFail(_, cc, BankNotFound,400) }
            _ <- Helper.booleanToFuture(failMsg = FirehoseViewsNotAllowedOnThisInstance +" or " + UserHasMissingRoles + CanUseFirehoseAtAnyBank  ) {
              canUseFirehose(u)
            }
            httpParams <- createHttpParamsByUrlFuture(cc.url) map { unboxFull(_) }
            obpQueryParams <- createQueriesByHttpParamsFuture(httpParams) map {
              unboxFullOrFail(_, cc, InvalidFilterParameterFormat, 400)
            }
            customers <- Connector.connector.vend.getCustomersFuture(bankId, callContext, obpQueryParams) map {
              unboxFullOrFail(_, cc, ConnectorEmptyResponse, 400)
            }
          } yield {
            (JSONFactory300.createCustomersJson(customers), callContext)
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
      List(UserNotLoggedIn, UserNotFoundByUsername, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUser),
      Some(List(canUseFirehoseAtAnyBank)))

    lazy val getBadLoginStatus : OBPEndpoint = {
      //get private accounts for all banks
      case "users" :: username::  "lock-status" :: Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            badLoginStatus <- Future { LoginAttempt.getBadLoginStatus(username) } map { unboxFullOrFail(_, cc, s"$UserNotFoundByUsername($username)",400) }
          } yield {
            (createBadLoginStatusJson(badLoginStatus), callContext)
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
      List(apiTagUser),
      Some(List(canUseFirehoseAtAnyBank)))

    lazy val unlockUser : OBPEndpoint = {
      //get private accounts for all banks
      case "users" :: username::  "lock-status" :: Nil JsonPut req => {
        cc =>
          for {
            (user, callContext) <-  extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            _ <- Helper.booleanToFuture(failMsg = UserHasMissingRoles + CanUnlockUser) {
              hasEntitlement("", u.userId, ApiRole.canUnlockUser)
            }
            _ <- Future { LoginAttempt.resetBadLoginAttempts(username) } 
            badLoginStatus <- Future { LoginAttempt.getBadLoginStatus(username) } map { unboxFullOrFail(_, cc, s"$UserNotFoundByUsername($username)",400) }
          } yield {
            (createBadLoginStatusJson(badLoginStatus), callContext)
          }
      }
    }
    
  }
}

object APIMethods310 {
}
