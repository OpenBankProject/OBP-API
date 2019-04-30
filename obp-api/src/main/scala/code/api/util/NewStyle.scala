package code.api.util

import java.util.Date

import code.api.APIFailureNewStyle
import code.api.util.APIUtil.{OBPReturnType, connectorEmptyResponse, createHttpParamsByUrlFuture, createQueriesByHttpParamsFuture, fullBoxOrException, unboxFull, unboxFullOrFail}
import code.api.util.ErrorMessages._
import code.api.v1_4_0.OBPAPI1_4_0.Implementations1_4_0
import code.api.v2_0_0.OBPAPI2_0_0.Implementations2_0_0
import code.api.v2_1_0.OBPAPI2_1_0.Implementations2_1_0
import code.api.v2_2_0.OBPAPI2_2_0.Implementations2_2_0
import code.api.v3_0_0.OBPAPI3_0_0.Implementations3_0_0
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.bankconnectors.Connector
import code.branches.Branches.{Branch, DriveUpString, LobbyString}
import code.consumer.Consumers
import code.context.UserAuthContextUpdate
import code.customeraddress.CustomerAddress
import code.entitlement.Entitlement
import code.entitlementrequest.EntitlementRequest
import code.fx.{FXRate, MappedFXRate, fx}
import code.metadata.counterparties.Counterparties
import code.model._
import com.openbankproject.commons.model.Product
import code.transactionChallenge.ExpectedChallengeAnswer
import code.util.Helper
import code.views.Views
import code.webhook.AccountWebhook
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountApplication, Bank, Customer, CustomerAddress, ProductCollection, ProductCollectionItem, TaxResidence, UserAuthContext, _}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List
import scala.concurrent.Future

object NewStyle {
  lazy val endpoints: List[(String, String)] = List(
    (nameOf(Implementations1_4_0.getTransactionRequestTypes), ApiVersion.v1_4_0.toString),
    (nameOf(Implementations2_0_0.getAllEntitlements), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.publicAccountsAtOneBank), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.privateAccountsAtOneBank), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.corePrivateAccountsAtOneBank), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.getPrivateAccountsAtOneBank), ApiVersion.v2_0_0.toString),
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
    (nameOf(Implementations3_1_0.getAllEntitlements), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createProductAttribute), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getProductAttribute), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.updateProductAttribute), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.deleteProductAttribute), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createAccountApplication), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getAccountApplications), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getAccountApplication), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.updateAccountApplicationStatus), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createProduct), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.updateCustomerAddress), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getProduct), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getProducts), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getProductTree), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createProductCollection), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getProductCollection), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createAccountAttribute), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.deleteBranch), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getOAuth2ServerJWKsURIs), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createConsent), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.answerConsentChallenge), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getConsents), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.revokeConsent), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createUserAuthContextUpdate), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.answerUserAuthContextUpdateChallenge), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getSystemView), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.createSystemView), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.deleteSystemView), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.updateSystemView), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations3_1_0.getOAuth2ServerJWKsURIs), ApiVersion.v3_1_0.toString)
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
    def `204`(callContext: Option[CallContext]): Option[CallContext] = {
      callContext.map(_.copy(httpCode = Some(204)))
    }
    def `200`(callContext: CallContext): Option[CallContext] = {
      Some(callContext.copy(httpCode = Some(200)))
    }
  }


  object function {
    import scala.concurrent.ExecutionContext.Implicits.global

    def getBranch(bankId : BankId, branchId : BranchId, callContext: Option[CallContext]): OBPReturnType[BranchT] = {
      Connector.connector.vend.getBranchFuture(bankId, branchId, callContext) map {
        val msg: String = s"${BranchNotFoundByBranchId}, or License may not be set. meta.license.id and meta.license.name can not be empty"
        x => fullBoxOrException(x ~> APIFailureNewStyle(msg, 400, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    /**
      * delete a branch, just set isDeleted field to true, marks it is deleted
      * also check this: https://github.com/OpenBankProject/OBP-API/issues/1241
      * @param branch to be deleted branch
      * @param callContext call context
      * @return true is mean delete success, false is delete fail
      */
    def deleteBranch(branch : BranchT, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      val deletedBranch = Branch(
        branch.branchId ,
        branch.bankId ,
        branch.name ,
        branch.address ,
        branch.location ,
        branch.lobbyString.map(it => LobbyString(it.hours)),
        branch.driveUpString.map(it => DriveUpString(it.hours)),
        branch.meta: Meta,
        branch.branchRouting.map(it => Routing(it.scheme, it.address)),
        branch.lobby,
        branch.driveUp,
        branch.isAccessible,
        branch.accessibleFeatures,
        branch.branchType,
        branch.moreInfo,
        branch.phoneNumber,
        Some(true)
      )
      Future {
        Connector.connector.vend.createOrUpdateBranch(deletedBranch) map {
          i =>  (i.isDeleted.get, callContext)
        }
      } map { unboxFull(_) }
    }

    def getAtm(bankId : BankId, atmId : AtmId, callContext: Option[CallContext]): OBPReturnType[AtmT] = {
      Connector.connector.vend.getAtmFuture(bankId, atmId, callContext) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(AtmNotFoundByAtmId, 400, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def getBank(bankId : BankId, callContext: Option[CallContext]) : OBPReturnType[Bank] = {
      Connector.connector.vend.getBankFuture(bankId, callContext) map {
        unboxFullOrFail(_, callContext, s"$BankNotFound Current BankId is $bankId")
      }
    }
    def getBanks(callContext: Option[CallContext]) : OBPReturnType[List[Bank]] = {
      Connector.connector.vend.getBanksFuture(callContext: Option[CallContext]) map {
        connectorEmptyResponse(_, callContext)
      }
    }

    def getBankAccount(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]): OBPReturnType[BankAccount] = {
      Connector.connector.vend.getBankAccountFuture(bankId, accountId, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$BankAccountNotFound Current BankId is $bankId and Current AccountId is $accountId", 400 ), i._2)
      }
    }

    def checkBankAccountExists(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]) : OBPReturnType[BankAccount] = {
      Connector.connector.vend.checkBankAccountExistsFuture(bankId, accountId, callContext) } map {
        unboxFullOrFail(_, callContext, s"$BankAccountNotFound Current BankId is $bankId and Current AccountId is $accountId")
      }

    def moderatedBankAccount(account: BankAccount, view: View, user: Box[User], callContext: Option[CallContext]) = Future {
      account.moderatedBankAccount(view, user, callContext)
    } map { fullBoxOrException(_)
    } map { unboxFull(_) }
    
    def moderatedOtherBankAccounts(account: BankAccount, 
                                   view: View, 
                                   user: Box[User], 
                                   callContext: Option[CallContext]): Future[List[ModeratedOtherBankAccount]] = 
      Future(account.moderatedOtherBankAccounts(view, user)) map { connectorEmptyResponse(_, callContext) }    
    def moderatedOtherBankAccount(account: BankAccount,
                                  counterpartyId: String, 
                                  view: View, 
                                  user: Box[User], 
                                  callContext: Option[CallContext]): Future[ModeratedOtherBankAccount] = 
      Future(account.moderatedOtherBankAccount(counterpartyId, view, user, callContext)) map { connectorEmptyResponse(_, callContext) }

    def view(viewId : ViewId, bankAccountId: BankIdAccountId, callContext: Option[CallContext]) : Future[View] = {
      Views.views.vend.viewFuture(viewId, bankAccountId) map {
        unboxFullOrFail(_, callContext, s"$ViewNotFound. Current ViewId is $viewId")
      }
    }
    def systemView(viewId : ViewId, callContext: Option[CallContext]) : Future[View] = {
      Views.views.vend.systemViewFuture(viewId) map {
        unboxFullOrFail(_, callContext, s"$SystemViewNotFound. Current ViewId is $viewId")
      }
    }
    def createSystemView(view: CreateViewJson, callContext: Option[CallContext]) : Future[View] = {
      Views.views.vend.createSystemView(view) map {
        unboxFullOrFail(_, callContext, s"$CreateSystemViewError")
      }
    }
    def updateSystemView(viewId: ViewId, view: UpdateViewJSON, callContext: Option[CallContext]) : Future[View] = {
      Views.views.vend.updateSystemView(viewId, view) map {
        unboxFullOrFail(_, callContext, s"$UpdateSystemViewError")
      }
    }
    def deleteSystemView(viewId : ViewId, callContext: Option[CallContext]) : Future[Boolean] = {
      Views.views.vend.removeSystemView(viewId) map {
        unboxFullOrFail(_, callContext, s"$DeleteSystemViewError")
      }
    }
    def hasViewAccess(view: View, user: User): Future[Box[Unit]] = {
      Helper.booleanToFuture(failMsg = UserNoPermissionAccessView) {(user.hasViewAccess(view))}
    }

    def getConsumerByConsumerId(consumerId: String, callContext: Option[CallContext]): Future[Consumer] = {
      Consumers.consumers.vend.getConsumerByConsumerIdFuture(consumerId) map {
        unboxFullOrFail(_, callContext, s"$ConsumerNotFoundByConsumerId Current ConsumerId is $consumerId")
      }
    }

    def getAccountWebhooks(queryParams: List[OBPQueryParam], callContext: Option[CallContext]): Future[List[AccountWebhook]] = {
      AccountWebhook.accountWebhook.vend.getAccountWebhooksFuture(queryParams) map {
        unboxFullOrFail(_, callContext, GetWebhooksError)
      }
    }

    def getConsumerByPrimaryId(id: Long, callContext: Option[CallContext]): Future[Consumer] = {
      Consumers.consumers.vend.getConsumerByPrimaryIdFuture(id) map {
        unboxFullOrFail(_, callContext, ConsumerNotFoundByConsumerId)
      }
    }
    def getCustomers(bankId : BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]): Future[List[Customer]] = {
      Connector.connector.vend.getCustomersFuture(bankId, callContext, queryParams) map {
        connectorEmptyResponse(_, callContext)
      }
    }
    def getCustomerByCustomerId(customerId : String, callContext: Option[CallContext]): OBPReturnType[Customer] = {
      Connector.connector.vend.getCustomerByCustomerIdFuture(customerId, callContext) map {
        unboxFullOrFail(_, callContext, CustomerNotFoundByCustomerId)
      }
    }
    def getCustomerByCustomerNumber(customerNumber : String, bankId : BankId, callContext: Option[CallContext]): OBPReturnType[Customer] = {
      Connector.connector.vend.getCustomerByCustomerNumberFuture(customerNumber, bankId, callContext) map {
        unboxFullOrFail(_, callContext, CustomerNotFound)
      }
    }


    def getCustomerAddress(customerId : String, callContext: Option[CallContext]): OBPReturnType[List[CustomerAddress]] = {
      Connector.connector.vend.getCustomerAddress(customerId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
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
                              tags: String,
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
        tags,
        status,
        callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def updateCustomerAddress(customerAddressId: String,
                              line1: String,
                              line2: String,
                              line3: String,
                              city: String,
                              county: String,
                              state: String,
                              postcode: String,
                              countryCode: String,
                              status: String,
                              tags: String,
                              callContext: Option[CallContext]): OBPReturnType[CustomerAddress] = {
      Connector.connector.vend.updateCustomerAddress(
        customerAddressId,
        line1,
        line2,
        line3,
        city,
        county,
        state,
        postcode,
        countryCode,
        tags,
        status,
        callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def deleteCustomerAddress(customerAddressId : String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteCustomerAddress(customerAddressId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def createTaxResidence(customerId : String, domain: String, taxNumber: String, callContext: Option[CallContext]): OBPReturnType[TaxResidence] = {
      Connector.connector.vend.createTaxResidence(customerId, domain, taxNumber, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getTaxResidence(customerId : String, callContext: Option[CallContext]): OBPReturnType[List[TaxResidence]] = {
      Connector.connector.vend.getTaxResidence(customerId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def deleteTaxResidence(taxResienceId : String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteTaxResidence(taxResienceId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getAdapterInfo(callContext: Option[CallContext]): OBPReturnType[InboundAdapterInfoInternal] = {
        Connector.connector.vend.getAdapterInfoFuture(callContext) map {
          connectorEmptyResponse(_, callContext)
        }
    }

    def getEntitlementsByUserId(userId: String, callContext: Option[CallContext]): Future[List[Entitlement]] = {
      Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(userId) map {
        connectorEmptyResponse(_, callContext)
      }
    }
    def getEntitlementRequestsFuture(userId: String, callContext: Option[CallContext]): Future[List[EntitlementRequest]] = {
      EntitlementRequest.entitlementRequest.vend.getEntitlementRequestsFuture(userId) map {
        connectorEmptyResponse(_, callContext)
      }
    }
    def getEntitlementRequestsFuture(callContext: Option[CallContext]): Future[List[EntitlementRequest]] = {
      EntitlementRequest.entitlementRequest.vend.getEntitlementRequestsFuture() map {
        connectorEmptyResponse(_, callContext)
      }
    }

    def getCounterparties(bankId : BankId, accountId : AccountId, viewId : ViewId, callContext: Option[CallContext]): OBPReturnType[List[CounterpartyTrait]] = {
      Connector.connector.vend.getCounterpartiesFuture(bankId,accountId,viewId, callContext) map { i=>
        (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getMetadata(bankId : BankId, accountId : AccountId, counterpartyId : String, callContext: Option[CallContext]): Future[CounterpartyMetadata] = {
      Future(Counterparties.counterparties.vend.getMetadata(bankId, accountId, counterpartyId)) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(CounterpartyMetadataNotFound, 400, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def getCounterpartyTrait(bankId : BankId, accountId : AccountId, counterpartyId : String, callContext: Option[CallContext]): OBPReturnType[CounterpartyTrait] = {
      Connector.connector.vend.getCounterpartyTrait(bankId, accountId, counterpartyId, callContext) map { i=>
        (connectorEmptyResponse(i._1, callContext), i._2)
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
    
    
    def isValidCurrencyISOCode(currencyCode: String,  callContext: Option[CallContext]) = {
      tryons(failMsg = InvalidISOCurrencyCode,400, callContext) {
        assert(APIUtil.isValidCurrencyISOCode(currencyCode))
      }
    }
    def isValidCurrencyISOCode(currencyCode: String, failMsg: String, callContext: Option[CallContext])= {
      tryons(failMsg = failMsg,400, callContext) {
        assert(APIUtil.isValidCurrencyISOCode(currencyCode))
      }
    }


    def hasEntitlement(failMsg: String)(bankId: String, userId: String, role: ApiRole): Future[Box[Unit]] = {
      Helper.booleanToFuture(failMsg + role.toString()) {
        code.api.util.APIUtil.hasEntitlement(bankId, userId, role)
      }
    }
    def hasEntitlement(bankId: String, userId: String, role: ApiRole, callContext: Option[CallContext] = None): Future[Box[Unit]] = {
      hasEntitlement(UserHasMissingRoles)(bankId, userId, role)
    }
    
    def hasAtLeastOneEntitlement(failMsg: String)(bankId: String, userId: String, role: List[ApiRole]): Future[Box[Unit]] = {
      Helper.booleanToFuture(failMsg) {
        code.api.util.APIUtil.hasAtLeastOneEntitlement(bankId, userId, role)
      }
    }


    def createUserAuthContext(userId: String, key: String, value: String,  callContext: Option[CallContext]): OBPReturnType[UserAuthContext] = {
      Connector.connector.vend.createUserAuthContext(userId, key, value, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def createUserAuthContextUpdate(userId: String, key: String, value: String, callContext: Option[CallContext]): OBPReturnType[UserAuthContextUpdate] = {
      Connector.connector.vend.createUserAuthContextUpdate(userId, key, value, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[List[UserAuthContext]] = {
      Connector.connector.vend.getUserAuthContexts(userId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def deleteUserAuthContexts(userId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteUserAuthContexts(userId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def deleteUserAuthContextById(userAuthContextId: String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteUserAuthContextById(userAuthContextId, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }


    def findByUserId(userId: String, callContext: Option[CallContext]): OBPReturnType[User] = {
      Future { User.findByUserId(userId).map(user =>(user, callContext))} map {
        unboxFullOrFail(_, callContext, s"$UserNotFoundById Current USER_ID($userId)")
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
        unboxFullOrFail(_, callContext, s"$UnknownError ")
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
        unboxFullOrFail(_, callContext, s"$InvalidTransactionRequestId Current TransactionRequestId($transactionRequestId) ")
      }
    }
    

    def validateChallengeAnswerInOBPSide(challengeId: String, challengeAnswer: String, callContext: Option[CallContext]) : Future[Boolean] = 
    {
      //Note: this method is not over kafka yet, so use Future here.
      Future{ ExpectedChallengeAnswer.expectedChallengeAnswerProvider.vend.validateChallengeAnswerInOBPSide(challengeId, challengeAnswer)} map {
        unboxFullOrFail(_, callContext, s"$UnknownError ")
      }
    }
    
    def validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]): OBPReturnType[Boolean] = 
     Connector.connector.vend.validateChallengeAnswer(challengeId: String, hashOfSuppliedAnswer: String, callContext: Option[CallContext]) map { i =>
       (unboxFullOrFail(i._1, callContext, s"$UnknownError "), i._2)
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
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    
    def createOrUpdateProductAttribute(
      bankId: BankId,
      productCode: ProductCode,
      productAttributeId: Option[String],
      name: String,
      attributType: ProductAttributeType.Value,
      value: String,
      callContext: Option[CallContext]
    ): OBPReturnType[ProductAttribute] = {
      Connector.connector.vend.createOrUpdateProductAttribute(
        bankId: BankId,
        productCode: ProductCode,
        productAttributeId: Option[String],
        name: String,
        attributType: ProductAttributeType.Value,
        value: String,
        callContext: Option[CallContext]
      ) map {
          i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getProductAttributesByBankAndCode(
                                           bank: BankId,
                                           productCode: ProductCode,
                                           callContext: Option[CallContext]
                                         ): OBPReturnType[List[ProductAttribute]] = {
      Connector.connector.vend.getProductAttributesByBankAndCode(
        bank: BankId,
        productCode: ProductCode,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    def getProductAttributeById(
      productAttributeId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[ProductAttribute] = {
      Connector.connector.vend.getProductAttributeById(
        productAttributeId: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    def deleteProductAttribute(
      productAttributeId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[Boolean] = {
      Connector.connector.vend.deleteProductAttribute(
        productAttributeId: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }


    def createOrUpdateAccountAttribute(
                                        bankId: BankId,
                                        accountId: AccountId,
                                        productCode: ProductCode,
                                        accountAttributeId: Option[String],
                                        name: String,
                                        attributeType: AccountAttributeType.Value,
                                        value: String,
                                        callContext: Option[CallContext]
                                      ): OBPReturnType[AccountAttribute] = {
      Connector.connector.vend.createOrUpdateAccountAttribute(
        bankId: BankId,
        accountId: AccountId,
        productCode: ProductCode,
        accountAttributeId: Option[String],
        name: String,
        attributeType: AccountAttributeType.Value,
        value: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    def createAccountApplication(
                                  productCode: ProductCode,
                                  userId: Option[String],
                                  customerId: Option[String],
                                  callContext: Option[CallContext]
                                ): OBPReturnType[AccountApplication] =
      Connector.connector.vend.createAccountApplication(productCode, userId, customerId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, CreateAccountApplicationError, 400), i._2)
      }


    def getAllAccountApplication(callContext: Option[CallContext]): OBPReturnType[List[AccountApplication]] =
      Connector.connector.vend.getAllAccountApplication(callContext) map {
        i => (unboxFullOrFail(i._1, callContext, UnknownError, 400), i._2)
      }

    def getAccountApplicationById(accountApplicationId: String, callContext: Option[CallContext]): OBPReturnType[AccountApplication] =
      Connector.connector.vend.getAccountApplicationById(accountApplicationId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$AccountApplicationNotFound Current Account-Application-Id($accountApplicationId)", 400), i._2)
      }

    def updateAccountApplicationStatus(accountApplicationId:String, status: String, callContext: Option[CallContext]): OBPReturnType[AccountApplication] =
      Connector.connector.vend.updateAccountApplicationStatus(accountApplicationId, status, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$UpdateAccountApplicationStatusError  Current Account-Application-Id($accountApplicationId)", 400), i._2)
      }

    def findUsers(userIds: List[String], callContext: Option[CallContext]): OBPReturnType[List[User]] = Future {
      val userList = userIds.filterNot(StringUtils.isBlank).distinct.map(User.findByUserId).collect {
        case Full(user) => user
      }
      (userList, callContext)
    }

    def createSandboxBankAccount(
      bankId: BankId,
      accountId: AccountId,
      accountType: String,
      accountLabel: String,
      currency: String,
      initialBalance: BigDecimal,
      accountHolderName: String,
      branchId: String,
      accountRoutingScheme: String,
      accountRoutingAddress: String, 
      callContext: Option[CallContext]
    ): OBPReturnType[BankAccount] = Future {
      Connector.connector.vend.createSandboxBankAccount(
        bankId: BankId,
        accountId: AccountId,
        accountType: String,
        accountLabel: String,
        currency: String,
        initialBalance: BigDecimal,
        accountHolderName: String,
        branchId: String,
        accountRoutingScheme: String,
        accountRoutingAddress: String
      )} map {
        i => (unboxFullOrFail(i, callContext, UnknownError, 400), callContext)
      }

    def findCustomers(customerIds: List[String], callContext: Option[CallContext]): OBPReturnType[List[Customer]] = {
      val customerList = customerIds.filterNot(StringUtils.isBlank).distinct
        .map(Consumers.consumers.vend.getConsumerByConsumerIdFuture)
      Future.foldLeft(customerList)(List.empty[Customer]) { (prev, next) =>
         next match {
           case Full(customer:Customer) => customer :: prev
           case _ => prev
        }
      } map {
        (_, callContext)
      }
    }

    def getOrCreateProductCollection(collectionCode: String, 
                                     productCodes: List[String], 
                                     callContext: Option[CallContext]): OBPReturnType[List[ProductCollection]] = {
      Connector.connector.vend.getOrCreateProductCollection(collectionCode, productCodes, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$ConnectorEmptyResponse  Current collection code($collectionCode)", 400), i._2)
      }
    }
    def getProductCollection(collectionCode: String, 
                             callContext: Option[CallContext]): OBPReturnType[List[ProductCollection]] = {
      Connector.connector.vend.getProductCollection(collectionCode, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$ConnectorEmptyResponse  Current collection code($collectionCode)", 400), i._2)
      }
    }
    def getOrCreateProductCollectionItems(collectionCode: String,
                                          memberProductCodes: List[String],
                                          callContext: Option[CallContext]): OBPReturnType[List[ProductCollectionItem]] = {
      Connector.connector.vend.getOrCreateProductCollectionItem(collectionCode, memberProductCodes, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$ConnectorEmptyResponse  Current collection code($collectionCode)", 400), i._2)
      }
    }
    def getProductCollectionItems(collectionCode: String,
                                          callContext: Option[CallContext]): OBPReturnType[List[ProductCollectionItem]] = {
      Connector.connector.vend.getProductCollectionItem(collectionCode, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$ConnectorEmptyResponse  Current collection code($collectionCode)", 400), i._2)
      }
    }
    def getProductCollectionItemsTree(collectionCode: String, 
                                      bankId: String,
                                      callContext: Option[CallContext]): OBPReturnType[List[(ProductCollectionItem, Product, List[ProductAttribute])]] = {
      Connector.connector.vend.getProductCollectionItemsTree(collectionCode, bankId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$ConnectorEmptyResponse  Current collection code($collectionCode)", 400), i._2)
      }
    }
      
    
    def getExchangeRate(bankId: BankId, fromCurrencyCode: String, toCurrencyCode: String, callContext: Option[CallContext]): Future[FXRate] =
      Future(Connector.connector.vend.getCurrentFxRateCached(bankId, fromCurrencyCode, toCurrencyCode)) map {
        fallbackFxRate =>
          fallbackFxRate match {
            case Empty =>
              val rate = fx.exchangeRate(fromCurrencyCode, toCurrencyCode)
              val inverseRate = fx.exchangeRate(toCurrencyCode, fromCurrencyCode)
              (rate, inverseRate) match {
                case (Some(r), Some(ir)) =>
                  Full(
                    MappedFXRate.create
                      .mBankId(bankId.value)
                      .mFromCurrencyCode(fromCurrencyCode)
                      .mToCurrencyCode(toCurrencyCode)
                      .mConversionValue(r)
                      .mInverseConversionValue(ir)
                      .mEffectiveDate(new Date())
                  )
                case _ => fallbackFxRate
              }
            case _ => fallbackFxRate
          }
      } map {
        unboxFullOrFail(_, callContext, FXCurrencyCodeCombinationsNotSupported)
      }
    
    def createMeeting(
      bankId: BankId,
      staffUser: User,
      customerUser: User,
      providerId: String,
      purposeId: String,
      when: Date,
      sessionId: String,
      customerToken: String,
      staffToken: String,
      creator: ContactDetails,
      invitees: List[Invitee],
      callContext: Option[CallContext]
    ): OBPReturnType[Meeting] =
    {
      Connector.connector.vend.createMeeting(
        bankId: BankId,
        staffUser: User,
        customerUser: User,
        providerId: String,
        purposeId: String,
        when: Date,
        sessionId: String,
        customerToken: String,
        staffToken: String,
        creator: ContactDetails,
        invitees: List[Invitee],
        callContext: Option[CallContext]
    ) map {
        i => (unboxFullOrFail(i._1, callContext, s"$ConnectorEmptyResponse Can not createMeeting in the backend. ", 400), i._2)
      }
    }
    
    def getMeetings(
      bankId : BankId, 
      user: User,
      callContext: Option[CallContext]
    ) :OBPReturnType[List[Meeting]] ={
      Connector.connector.vend.getMeetings(
        bankId : BankId,
        user: User,
        callContext: Option[CallContext]
      ) map {
          i => (unboxFullOrFail(i._1, callContext, s"$ConnectorEmptyResponse Can not getMeetings in the backend. ", 400), i._2)
        }
      }
    
    def getMeeting(
      bankId: BankId,
      user: User, 
      meetingId : String,
      callContext: Option[CallContext]
    ) :OBPReturnType[Meeting]={ 
      Connector.connector.vend.getMeeting(
        bankId: BankId,
        user: User,
        meetingId : String,
        callContext: Option[CallContext]
    ) map {
        i => (unboxFullOrFail(i._1, callContext, s"$MeetingNotFound Current MeetingId(${meetingId}) ", 400), i._2)
      }
    }

    def getCoreBankAccountsFuture(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[List[CoreAccount]] = 
      {
        Connector.connector.vend.getCoreBankAccountsFuture(bankIdAccountIds, callContext) map {
          i => (unboxFullOrFail(i, callContext, s"$ConnectorEmptyResponse Can not ${nameOf(getCoreBankAccountsFuture(bankIdAccountIds, callContext))} in the backend. ", 400))
        }
      }
    
  }

}
