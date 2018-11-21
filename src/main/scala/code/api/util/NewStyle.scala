package code.api.util

import code.api.APIFailureNewStyle
import code.api.util.APIUtil.{OBPReturnType, createHttpParamsByUrlFuture, createQueriesByHttpParamsFuture, fullBoxOrException, unboxFull, unboxFullOrFail}
import code.api.util.ErrorMessages._
import code.api.v1_4_0.OBPAPI1_4_0.Implementations1_4_0
import code.api.v2_0_0.OBPAPI2_0_0.Implementations2_0_0
import code.api.v2_1_0.OBPAPI2_1_0.Implementations2_1_0
import code.api.v2_1_0.TransactionRequestCommonBodyJSON
import code.api.v2_2_0.OBPAPI2_2_0.Implementations2_2_0
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.atms.Atms
import code.atms.Atms.AtmId
import code.bankconnectors.vMar2017.InboundAdapterInfoInternal
import code.bankconnectors.{Connector, OBPQueryParam, ObpApiLoopback}
import code.branches.Branches
import code.branches.Branches.BranchId
import code.consumer.Consumers
import code.context.UserAuthContext
import code.customer.Customer
import code.customeraddress.CustomerAddress
import code.entitlement.Entitlement
import code.metadata.counterparties.{Counterparties, CounterpartyTrait}
import code.model._
import code.taxresidence.TaxResidence
import code.transactionChallenge.ExpectedChallengeAnswer
import code.transactionrequests.TransactionRequests.TransactionRequest
import code.util.Helper
import code.views.Views
import code.webhook.AccountWebhook
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.Box
import net.liftweb.http.provider.HTTPParam
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future

object NewStyle {
  lazy val endpoints: List[(String, String)] = List(
    (nameOf(Implementations1_4_0.getTransactionRequestTypes), ApiVersion.v1_4_0.toString),
    (nameOf(Implementations2_0_0.getAllEntitlements), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_1_0.getRoles), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations2_2_0.config), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getViewsForBankAccount), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getCurrentFxRate), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getExplictCounterpartiesForAccount), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getExplictCounterpartyById), ApiVersion.v2_2_0.toString),
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
    (nameOf(Implementations3_0_0.getOtherAccountByIdForBankAccount), ApiVersion.v3_0_0.toString),
    (nameOf(Implementations3_0_0.getOtherAccountsForBankAccount), ApiVersion.v3_0_0.toString),
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
    (nameOf(Implementations3_1_0.createAccountWebhook), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.enableDisableAccountWebhook), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getAdapterInfo), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getAccountWebhooks), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.config), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getTransactionByIdForBankAccount), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getTransactionRequests), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createCustomer), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getRateLimitingInfo), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getCustomerByCustomerId), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getCustomerByCustomerNumber), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createTaxResidence), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getTaxResidence), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.deleteTaxResidence), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createCustomerAddress), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getCustomerAddresses), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.deleteCustomerAddress), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createUserAuthContext), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getUserAuthContexts), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.deleteUserAuthContextById), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.deleteUserAuthContexts), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getObpApiLoopback), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.refreshUser), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getAllEntitlements), ApiVersion.v3_1_0.toString)
  )

  object HttpCode {
    def `200`(callContext: Option[CallContext]): Option[CallContext] = {
      callContext.map(_.copy(httpCode = Some(200)))
    }
    def `201`(callContext: Option[CallContext]): Option[CallContext] = {
      callContext.map(_.copy(httpCode = Some(201)))
    }
    def `202`(callContext: Option[CallContext]): Option[CallContext] = {
      callContext.map(_.copy(httpCode = Some(202)))
    }
    def `200`(callContext: CallContext): Option[CallContext] = {
      Some(callContext.copy(httpCode = Some(200)))
    }
  }


  object function {
    import scala.concurrent.ExecutionContext.Implicits.global

    def getBranch(bankId : BankId, branchId : BranchId, callContext: Option[CallContext]): OBPReturnType[Branches.BranchT] = {
      Connector.connector.vend.getBranchFuture(bankId, branchId, callContext) map {
        val msg: String = s"${BranchNotFoundByBranchId}, or License may not be set. meta.license.id and meta.license.name can not be empty"
        x => fullBoxOrException(x ~> APIFailureNewStyle(msg, 400, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def getAtm(bankId : BankId, atmId : AtmId, callContext: Option[CallContext]): OBPReturnType[Atms.AtmT] = {
      Connector.connector.vend.getAtmFuture(bankId, atmId, callContext) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(AtmNotFoundByAtmId, 400, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def getBank(bankId : BankId, callContext: Option[CallContext]) : OBPReturnType[Bank] = {
      Connector.connector.vend.getBankFuture(bankId, callContext) map {
        unboxFullOrFail(_, callContext, s"$BankNotFound Current BankId is $bankId", 400)
      }
    }
    def getBanks(callContext: Option[CallContext]) : OBPReturnType[List[Bank]] = {
      Connector.connector.vend.getBanksFuture(callContext: Option[CallContext]) map {
        unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
      }
    }

    def getBankAccount(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]): OBPReturnType[BankAccount] = {
      Connector.connector.vend.getBankAccountFuture(bankId, accountId, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$BankAccountNotFound Current BankId is $bankId and Current AccountId is $accountId", 400 ), i._2)
      }
    }

    def checkBankAccountExists(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]) : OBPReturnType[BankAccount] = {
      Future { Connector.connector.vend.checkBankAccountExists(bankId, accountId, callContext) } map {
        unboxFullOrFail(_, callContext, s"$BankAccountNotFound Current BankId is $bankId and Current AccountId is $accountId", 400)
      }
    }

    def moderatedBankAccount(account: BankAccount, view: View, user: Box[User]) = Future {
      account.moderatedBankAccount(view, user)
    } map { fullBoxOrException(_)
    } map { unboxFull(_) }

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

    def getAccountWebhooks(queryParams: List[OBPQueryParam], callContext: Option[CallContext]): Future[List[AccountWebhook]] = {
      AccountWebhook.accountWebhook.vend.getAccountWebhooksFuture(queryParams) map {
        unboxFullOrFail(_, callContext, GetWebhooksError, 400)
      }
    }

    def getConsumerByPrimaryId(id: Long, callContext: Option[CallContext]): Future[Consumer] = {
      Consumers.consumers.vend.getConsumerByPrimaryIdFuture(id) map {
        unboxFullOrFail(_, callContext, ConsumerNotFoundByConsumerId, 400)
      }
    }
    def getCustomers(bankId : BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[List[Customer]] = {
      Connector.connector.vend.getCustomersFuture(bankId, callContext, queryParams) map {
        unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
      }
    }
    def getCustomerByCustomerId(customerId : String, callContext: Option[CallContext]): OBPReturnType[Customer] = {
      Connector.connector.vend.getCustomerByCustomerIdFuture(customerId, callContext) map {
        unboxFullOrFail(_, callContext, CustomerNotFoundByCustomerId, 400)
      }
    }
    def getCustomerByCustomerNumber(customerNumber : String, bankId : BankId, callContext: Option[CallContext]): OBPReturnType[Customer] = {
      Connector.connector.vend.getCustomerByCustomerNumberFuture(customerNumber, bankId, callContext) map {
        unboxFullOrFail(_, callContext, CustomerNotFound, 400)
      }
    }


    def getCustomerAddress(customerId : String, callContext: Option[CallContext]): OBPReturnType[List[CustomerAddress]] = {
      Connector.connector.vend.getCustomerAddress(customerId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, ConnectorEmptyResponse, 400), i._2)
      }
    }
    def createCustomerAddress(customerId: String,
                              line1: String,
                              line2: String,
                              line3: String,
                              city: String,
                              county: String,
                              state: String,
                              postcode: String,
                              countryCode: String,
                              status: String,
                              callContext: Option[CallContext]): OBPReturnType[CustomerAddress] = {
      Connector.connector.vend.createCustomerAddress(
        customerId,
        line1,
        line2,
        line3,
        city,
        county,
        state,
        postcode,
        countryCode,
        status,
        callContext) map {
        i => (unboxFullOrFail(i._1, callContext, ConnectorEmptyResponse, 400), i._2)
      }
    }
    def deleteCustomerAddress(customerAddressId : String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteCustomerAddress(customerAddressId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, ConnectorEmptyResponse, 400), i._2)
      }
    }

    def createTaxResidence(customerId : String, domain: String, taxNumber: String, callContext: Option[CallContext]): OBPReturnType[TaxResidence] = {
      Connector.connector.vend.createTaxResidence(customerId, domain, taxNumber, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, ConnectorEmptyResponse, 400), i._2)
      }
    }
    def getTaxResidence(customerId : String, callContext: Option[CallContext]): OBPReturnType[List[TaxResidence]] = {
      Connector.connector.vend.getTaxResidence(customerId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, ConnectorEmptyResponse, 400), i._2)
      }
    }
    def deleteTaxResidence(taxResienceId : String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteTaxResidence(taxResienceId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, ConnectorEmptyResponse, 400), i._2)
      }
    }

    def getAdapterInfo(callContext: Option[CallContext]): OBPReturnType[InboundAdapterInfoInternal] = {
      Future {
        Connector.connector.vend.getAdapterInfo(callContext)
      } map {
        unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
      }
    }

    def getEntitlementsByUserId(userId: String, callContext: Option[CallContext]): Future[List[Entitlement]] = {
      Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(userId) map {
        unboxFullOrFail(_, callContext, ConnectorEmptyResponse, 400)
      }
    }

    def getCounterparties(bankId : BankId, accountId : AccountId, viewId : ViewId, callContext: Option[CallContext]): OBPReturnType[List[CounterpartyTrait]] = {
      Future(Connector.connector.vend.getCounterparties(bankId,accountId,viewId, callContext)) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(ConnectorEmptyResponse, 400, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def getMetadata(bankId : BankId, accountId : AccountId, counterpartyId : String, callContext: Option[CallContext]): Future[CounterpartyMetadata] = {
      Future(Counterparties.counterparties.vend.getMetadata(bankId, accountId, counterpartyId)) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(CounterpartyMetadataNotFound, 400, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def getCounterpartyTrait(bankId : BankId, accountId : AccountId, counterpartyId : String, callContext: Option[CallContext]): OBPReturnType[CounterpartyTrait] = {
      Connector.connector.vend.getCounterpartyTrait(bankId, accountId, counterpartyId, callContext) map { i=>
        (unboxFullOrFail(i._1, callContext, ConnectorEmptyResponse, 400), i._2)
      } 
    }


    def isEnabledTransactionRequests(): Future[Box[Unit]] = Helper.booleanToFuture(failMsg = TransactionRequestsNotEnabled)(APIUtil.getPropsAsBoolValue("transactionRequests_enabled", false))

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


    def hasEntitlement(failMsg: String)(bankId: String, userId: String, role: ApiRole): Future[Box[Unit]] = {
      Helper.booleanToFuture(failMsg) {
        code.api.util.APIUtil.hasEntitlement(bankId, userId, role)
      }
    }
    def hasAtLeastOneEntitlement(failMsg: String)(bankId: String, userId: String, role: List[ApiRole]): Future[Box[Unit]] = {
      Helper.booleanToFuture(failMsg) {
        code.api.util.APIUtil.hasAtLeastOneEntitlement(bankId, userId, role)
      }
    }


    def createUserAuthContext(userId: String, key: String, value: String,  callContext: Option[CallContext]): OBPReturnType[UserAuthContext] = {
      Connector.connector.vend.createUserAuthContext(userId, key, value, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, ConnectorEmptyResponse, 400), i._2)
      }
    }
    def getUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[List[UserAuthContext]] = {
      Connector.connector.vend.getUserAuthContexts(userId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, ConnectorEmptyResponse, 400), i._2)
      }
    }
    def deleteUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteUserAuthContexts(userId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, ConnectorEmptyResponse, 400), i._2)
      }
    }

    def deleteUserAuthContextById(userAuthContextId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteUserAuthContextById(userAuthContextId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, ConnectorEmptyResponse, 400), i._2)
      }
    }


    def findByUserId(userId: String, callContext: Option[CallContext]): OBPReturnType[User] = {
      Future { User.findByUserId(userId).map(user =>(user, callContext))} map {
        unboxFullOrFail(_, callContext, s"$UserNotFoundById Current USER_ID($userId)", 400)
      }
    }
  
    def createTransactionRequestv210(
      u: User,
      viewId: ViewId,
      fromAccount: BankAccount,
      toAccount: BankAccount,
      transactionRequestType: TransactionRequestType,
      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
      detailsPlain: String,
      chargePolicy: String,
      callContext: Option[CallContext]): OBPReturnType[TransactionRequest] =
    {
      Connector.connector.vend.createTransactionRequestv210(
        u: User,
        viewId: ViewId,
        fromAccount: BankAccount,
        toAccount: BankAccount,
        transactionRequestType: TransactionRequestType,
        transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
        detailsPlain: String,
        chargePolicy: String,
        callContext: Option[CallContext]
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetTransactionRequests210", 400), i._2)
      }
    }
    
    def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[CounterpartyTrait] = 
    {
      Connector.connector.vend.getCounterpartyByCounterpartyIdFuture(counterpartyId: CounterpartyId, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$CounterpartyNotFoundByCounterpartyId Current counterpartyId($counterpartyId) ", 400),
          i._2)
      }
    }
    
    
    def toBankAccount(counterparty: CounterpartyTrait, callContext: Option[CallContext]) : Future[BankAccount] =
    {
      Future{BankAccount.toBankAccount(counterparty)} map {
        unboxFullOrFail(_, callContext, s"$UnknownError ", 400)
      }
    }
    
    def getCounterpartyByIban(iban: String, callContext: Option[CallContext]) : OBPReturnType[CounterpartyTrait] = 
    {
      Connector.connector.vend.getCounterpartyByIban(iban: String, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(
          i._1, 
          callContext, 
          s"$CounterpartyNotFoundByIban. Please check how do you create Counterparty, " +
            s"set the proper IBan value to `other_account_secondary_routing_address`. Current Iban = $iban ", 
          400),
          i._2)
        
      }
    }
    
    def getTransactionRequestImpl(transactionRequestId: TransactionRequestId, callContext: Option[CallContext]): OBPReturnType[TransactionRequest] = 
    {
      //Note: this method is not over kafka yet, so use Future here.
      Future{ Connector.connector.vend.getTransactionRequestImpl(transactionRequestId, callContext)} map {
        unboxFullOrFail(_, callContext, s"$InvalidTransactionRequestId Current TransactionRequestId($transactionRequestId) ", 400)
      }
    }
    

    def validateChallengeAnswerInOBPSide(challengeId: String, challengeAnswer: String, callContext: Option[CallContext]) : Future[Boolean] = 
    {
      //Note: this method is not over kafka yet, so use Future here.
      Future{ ExpectedChallengeAnswer.expectedChallengeAnswerProvider.vend.validateChallengeAnswerInOBPSide(challengeId, challengeAnswer)} map {
        unboxFullOrFail(_, callContext, s"$UnknownError ", 400)
      }
    }
    
    def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]): OBPReturnType[Boolean] = 
     Connector.connector.vend.validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]) map { i =>
       (unboxFullOrFail(i._1, callContext, s"$UnknownError ", 400), i._2)
      } 
    
    def createTransactionAfterChallengeV300(
      initiator: User,
      fromAccount: BankAccount,
      transReqId: TransactionRequestId,
      transactionRequestType: TransactionRequestType,
      callContext: Option[CallContext]): OBPReturnType[TransactionRequest] = 
    {
      Connector.connector.vend.createTransactionAfterChallengev300(
        initiator: User,
        fromAccount: BankAccount,
        transReqId: TransactionRequestId,
        transactionRequestType: TransactionRequestType,
        callContext: Option[CallContext]
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForCreateTransactionAfterChallengev300 ", 400), i._2)
      }
      
    }
    
    def createTransactionAfterChallengeV210(fromAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]): OBPReturnType[TransactionRequest] =
      Connector.connector.vend.createTransactionAfterChallengeV210(fromAccount: BankAccount, transactionRequest: TransactionRequest, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForCreateTransactionAfterChallengev300 ", 400), i._2)
      } 
    
    def makePaymentv210(fromAccount: BankAccount,
                      toAccount: BankAccount,
                      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                      amount: BigDecimal,
                      description: String,
                      transactionRequestType: TransactionRequestType,
                      chargePolicy: String, 
                      callContext: Option[CallContext]): OBPReturnType[TransactionId]=
      Connector.connector.vend.makePaymentv210(
        fromAccount: BankAccount,
        toAccount: BankAccount,
        transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
        amount: BigDecimal,
        description: String,
        transactionRequestType: TransactionRequestType,
        chargePolicy: String, 
        callContext: Option[CallContext]
      ) map { i => 
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForMakePayment ",400), i._2)
      }
    
    def getObpApiLoopback(callContext: Option[CallContext]): OBPReturnType[ObpApiLoopback] = {
      Connector.connector.vend.getObpApiLoopback(callContext) map {
        i => (unboxFullOrFail(i._1, callContext, ConnectorEmptyResponse, 400), i._2)
      }
    }
        
  }

}
