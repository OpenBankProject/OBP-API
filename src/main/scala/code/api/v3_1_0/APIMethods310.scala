package code.api.v3_1_0

import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.util._
import code.bankconnectors.{Connector, OBPQueryParam}
import code.metrics.APIMetrics
import code.model._
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
      """get top apis. eg count, Implemented_by_partial_function and implemented_in_version.
        |
        |Should be able to filter on the following fields:
        |
        |eg: /management/metrics/top-apis?start_date=2010-05-10T01:20:03&end_date=2017-05-22T01:02:03
        |
        |1 start_date (defaults to the day before the current date): eg:start_date=2010-05-10T01:20:03
        |
        |2 end_date (defaults to the current date) eg:end_date=2018-05-10T01:20:03
        |
        |""",
      emptyObjectJson,
      List(topApiJson),
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      Catalogs(Core, notPSD2, OBWG),
      apiTagMetric :: Nil)

    lazy val getTopAPIs : OBPEndpoint = {
      case "management" :: "metrics" :: "top-apis" :: Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            urlQueryParams <- Future{getHttpRequestUrlParams(cc.url)} map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidDateFormat, 400, Some(cc.toLight)))
              } map { unboxFull(_) }
            toApis <- APIMetrics.apiMetrics.vend.getTopApisFuture(urlQueryParams) map {
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
      """get metrics top consumers on api usage eg. total count, consumer_id and app_name.
        |
        |Should be able to filter on the following fields:
        |
        |eg: /management/metrics/top-consumers?start_date=2010-05-10T01:20:03&end_date=2017-05-22T01:02:03
        |
        |1 start_date (defaults to the day before the current date): eg:start_date=2010-05-10T01:20:03
        |
        |2 end_date (defaults to the current date) eg:end_date=2018-05-10T01:20:03
        |
      """.stripMargin,
      emptyObjectJson,
      List(topConsumerJson),
      List(UserNotLoggedIn, UnknownError, BankNotFound),
      Catalogs(Core, notPSD2, OBWG),
      apiTagMetric :: Nil)

    lazy val getMetricsTopConsumers : OBPEndpoint = {
      case "management" :: "metrics" :: "top-consumers" :: Nil JsonGet req => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            
            urlQueryParams <- Future{getHttpRequestUrlParams(cc.url)} map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidDateFormat, 400, Some(cc.toLight)))
              } map { unboxFull(_) }
            
            topConsumers <- APIMetrics.apiMetrics.vend.getTopConsumersFuture(urlQueryParams) map {
                x => fullBoxOrException(x ~> APIFailureNewStyle(GetMetricsTopConsumersError, 400, Some(cc.toLight)))
              } map { unboxFull(_) }
            
          } yield
           (JSONFactory310.createTopConsumersJson(topConsumers), Some(cc))
      }
    }
    
    
    
  }
}

object APIMethods310 {
}
