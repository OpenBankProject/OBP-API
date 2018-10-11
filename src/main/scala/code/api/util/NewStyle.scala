package code.api.util

import code.api.APIFailureNewStyle
import code.api.util.APIUtil.{createHttpParamsByUrlFuture, createQueriesByHttpParamsFuture, fullBoxOrException, unboxFull, unboxFullOrFail}
import code.api.util.ErrorMessages._
import code.api.v2_0_0.OBPAPI2_0_0.Implementations2_0_0
import code.api.v2_1_0.OBPAPI2_1_0.Implementations2_1_0
import code.api.v2_2_0.OBPAPI2_2_0.Implementations2_2_0
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.bankconnectors.{Connector, OBPQueryParam}
import code.consumer.Consumers
import code.customer.Customer
import code.model._
import code.util.Helper
import code.views.Views
import code.webhook.AccountWebHook
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.http.provider.HTTPParam
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future

object NewStyle {
  lazy val endpoints: List[(String, String)] = List(
    (nameOf(Implementations2_1_0.getRoles), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations2_2_0.config), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getViewsForBankAccount), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getCurrentFxRate), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations3_0_0.getUser), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getCurrentUser), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getUserByUserId), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getUserByUsername), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getUsers), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getUsers), ApiVersion.v2_1_0.toString),
    (nameOf(Implementations3_0_0.getCustomersForUser), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations3_0_0.getCustomersForUser), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getCoreTransactionsForBankAccount), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getTransactionsForBankAccount), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.corePrivateAccountsAllBanks), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getViewsForBankAccount), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getPrivateAccountIdsbyBankId), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.privateAccountsAtOneBank), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getCoreAccountById), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getPrivateAccountById), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getAtm), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getAtms), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getBranch), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getBranches), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.addEntitlementRequest), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getAllEntitlementRequests), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getEntitlementRequests), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getEntitlementRequestsForCurrentUser), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getEntitlementsForCurrentUser), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.deleteEntitlementRequest), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.createViewForBankAccount), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.updateViewForBankAccount), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.dataWarehouseSearch), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.addScope), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.deleteScope), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getScopes), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.dataWarehouseStatistics), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getBanks), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.bankById), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getPermissionForUserForBankAccount), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getAdapter), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations2_0_0.getAllEntitlements), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations3_1_0.getCheckbookOrders), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getStatusOfCreditCardOrder), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createCreditLimitRequest), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getCreditLimitRequests), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getCreditLimitRequestByRequestId), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getTopAPIs), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getMetricsTopConsumers), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getFirehoseCustomers), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getBadLoginStatus), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.unlockUser), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.callsLimit), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getCallsLimit), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.checkFundsAvailable), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getConsumer), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getConsumersForCurrentUser), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getConsumers), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createAccountWebHook), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.enableDisableAccountWebHook), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getAdapterInfo), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getAccountWebHooks), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.config), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getTransactionByIdForBankAccount), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getTransactionRequests), ApiVersion.v3_1_0.toString)
  )

  object HttpCode {
    def `200`(callContext: Option[CallContext])= {
      callContext.map(_.copy(httpCode = Some(200)))
    }
    def `200`(callContext: CallContext)  = {
      Some(callContext.copy(httpCode = Some(200)))
    }
  }


  object function {
    import scala.concurrent.ExecutionContext.Implicits.global

    def getBank(bankId : BankId, callContext: Option[CallContext]) : Future[Bank] = {
      Connector.connector.vend.getBankFuture(bankId) map {
        unboxFullOrFail(_, callContext, s"$BankNotFound Current BankId is $bankId", 400)
      }
    }
    def getBanks(callContext: Option[CallContext]) : Future[List[Bank]] = {
      Connector.connector.vend.getBanksFuture() map {
        unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
      }
    }

    def checkBankAccountExists(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]) : Future[(BankAccount, Option[CallContext])] = {
      Future { Connector.connector.vend.checkBankAccountExists(bankId, accountId, callContext) } map {
        unboxFullOrFail(_, callContext, s"$BankAccountNotFound Current BankId is $bankId and Current AccountId is $accountId", 400)
      }
    }

    def view(viewId : ViewId, bankAccountId: BankIdAccountId, callContext: Option[CallContext]) : Future[View] = {
      Views.views.vend.viewFuture(viewId, bankAccountId) map {
        unboxFullOrFail(_, callContext, s"$ViewNotFound Current ViewId is $viewId", 400)
      }
    }

    def getConsumerByConsumerId(consumerId: String, callContext: Option[CallContext]): Future[Consumer] = {
      Consumers.consumers.vend.getConsumerByConsumerIdFuture(consumerId) map {
        unboxFullOrFail(_, callContext, s"$ConsumerNotFoundByConsumerId Current ConsumerId is $consumerId", 400)
      }
    }

    def getAccountWebHooks(queryParams: List[OBPQueryParam], callContext: Option[CallContext]): Future[List[AccountWebHook]] = {
      AccountWebHook.accountWebHook.vend.getAccountWebHooksFuture(queryParams) map {
        unboxFullOrFail(_, callContext, GetWebHooksError, 400)
      }
    }

    def getConsumerByPrimaryId(id: Long, callContext: Option[CallContext]): Future[Consumer]= {
      Consumers.consumers.vend.getConsumerByPrimaryIdFuture(id) map {
        unboxFullOrFail(_, callContext, ConsumerNotFoundByConsumerId, 400)
      }
    }
    def getCustomers(bankId : BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[List[Customer]] = {
      Connector.connector.vend.getCustomersFuture(bankId, callContext, queryParams) map {
        unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
      }
    }

    def getAdapterInfo(callContext: Option[CallContext]) = {
      Future {
        Connector.connector.vend.getAdapterInfo()
      } map {
        unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
      }
    }

    def isEnabledTransactionRequests() = Helper.booleanToFuture(failMsg = TransactionRequestsNotEnabled)(APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false))

    /**
      * Wraps a Future("try") block around the function f and
      * @param f - the block of code to evaluate
      * @return <ul>
      *   <li>Future(result of the evaluation of f) if f doesn't throw any exception
      *   <li>a Failure if f throws an exception with message = failMsg and code = failCode
      *   </ul>
      */
    def tryons[T](failMsg: String, failCode: Int = 400, callContext: Option[CallContext])(f: => T)(implicit m: Manifest[T]): Future[T]= {
      Future {
        tryo {
          f
        }
      } map {
        x => unboxFullOrFail(x, callContext, failMsg, failCode)
      }
    }

    def createHttpParams(url: String): Future[List[HTTPParam]] = {
      createHttpParamsByUrlFuture(url) map { unboxFull(_) }
    }
    def createObpParams(httpParams: List[HTTPParam], allowedParams: List[String], callContext: Option[CallContext]): Future[List[OBPQueryParam]] = {
      val httpParamsAllowed = httpParams.filter(
        x => allowedParams.contains(x.name)
      )
      createQueriesByHttpParamsFuture(httpParamsAllowed) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidFilterParameterFormat, 400, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

  }

}
