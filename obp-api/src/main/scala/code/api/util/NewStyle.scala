package code.api.util

import java.util.Date
import java.util.UUID.randomUUID

import code.api.APIFailureNewStyle
import code.api.cache.Caching
import code.api.util.APIUtil.{OBPReturnType, canGrantAccessToViewCommon, canRevokeAccessToViewCommon, connectorEmptyResponse, createHttpParamsByUrlFuture, createQueriesByHttpParamsFuture, fullBoxOrException, unboxFull, unboxFullOrFail}
import code.api.util.ApiRole.canCreateAnyTransactionRequest
import code.api.util.ErrorMessages.{InsufficientAuthorisationToCreateTransactionRequest, _}
import code.api.v1_4_0.OBPAPI1_4_0.Implementations1_4_0
import code.api.v2_0_0.OBPAPI2_0_0.Implementations2_0_0
import code.api.v2_1_0.OBPAPI2_1_0.Implementations2_1_0
import code.api.v2_2_0.OBPAPI2_2_0.Implementations2_2_0
import code.bankconnectors.Connector
import code.branches.Branches.{Branch, DriveUpString, LobbyString}
import code.consumer.Consumers
import code.directdebit.DirectDebitTrait
import code.dynamicEntity.{DynamicEntityProvider, DynamicEntityT}
import code.entitlement.Entitlement
import code.entitlementrequest.EntitlementRequest
import code.fx.{FXRate, MappedFXRate, fx}
import code.metadata.counterparties.Counterparties
import code.methodrouting.{MethodRoutingProvider, MethodRoutingT}
import code.model._
import code.standingorders.StandingOrderTrait
import code.transactionChallenge.ExpectedChallengeAnswer
import code.usercustomerlinks.UserCustomerLink
import code.util.Helper
import code.views.Views
import code.webhook.AccountWebhook
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.{AccountAttributeType, CardAttributeType, CustomerAttributeType, DynamicEntityOperation, ProductAttributeType, TransactionAttributeType}
import com.openbankproject.commons.model.{AccountApplication, Bank, Customer, CustomerAddress, Product, ProductCollection, ProductCollectionItem, TaxResidence, UserAuthContext, UserAuthContextUpdate, _}
import com.openbankproject.commons.util.ApiVersion
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.provider.HTTPParam
import net.liftweb.json.{JObject, JValue}
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List
import scala.concurrent.Future
import scala.math.BigDecimal
import scala.reflect.runtime.universe.MethodSymbol

object NewStyle {
  lazy val endpoints: List[(String, String)] = List(
    (nameOf(Implementations1_4_0.getTransactionRequestTypes), ApiVersion.v1_4_0.toString),
    (nameOf(Implementations1_4_0.addCustomerMessage), ApiVersion.v1_4_0.toString),
    (nameOf(Implementations2_0_0.getAllEntitlements), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.publicAccountsAtOneBank), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.privateAccountsAtOneBank), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.corePrivateAccountsAtOneBank), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.getKycDocuments), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.getKycMedia), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.getKycStatuses), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.getKycChecks), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.addKycDocument), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.addKycMedia), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.addKycStatus), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_0_0.addKycCheck), ApiVersion.v2_0_0.toString),
    (nameOf(Implementations2_1_0.getRoles), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations2_1_0.getCustomersForCurrentUserAtBank), ApiVersion.v3_1_0.toString),
    (nameOf(Implementations2_2_0.config), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getViewsForBankAccount), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getCurrentFxRate), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getExplictCounterpartiesForAccount), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.getExplictCounterpartyById), ApiVersion.v2_2_0.toString),
    (nameOf(Implementations2_2_0.createAccount), ApiVersion.v2_2_0.toString)
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
    def `201`(callContext: CallContext): Option[CallContext] = {
      Some(callContext.copy(httpCode = Some(201)))
    }
  }


  object function {

    import com.openbankproject.commons.ExecutionContext.Implicits.global

    def getBranch(bankId : BankId, branchId : BranchId, callContext: Option[CallContext]): OBPReturnType[BranchT] = {
      Connector.connector.vend.getBranch(bankId, branchId, callContext) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(BranchNotFoundByBranchId, 400, callContext.map(_.toLight)))
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
      Connector.connector.vend.getAtm(bankId, atmId, callContext) map {
        x => fullBoxOrException(x ~> APIFailureNewStyle(AtmNotFoundByAtmId, 400, callContext.map(_.toLight)))
      } map { unboxFull(_) }
    }

    def getBank(bankId : BankId, callContext: Option[CallContext]) : OBPReturnType[Bank] = {
      Connector.connector.vend.getBank(bankId, callContext) map {
        unboxFullOrFail(_, callContext, s"$BankNotFound Current BankId is $bankId")
      }
    }
    def getBanks(callContext: Option[CallContext]) : OBPReturnType[List[Bank]] = {
      Connector.connector.vend.getBanks(callContext: Option[CallContext]) map {
        connectorEmptyResponse(_, callContext)
      }
    }
    def createOrUpdateBank(bankId: String,
                           fullBankName: String,
                           shortBankName: String,
                           logoURL: String,
                           websiteURL: String,
                           swiftBIC: String,
                           national_identifier: String,
                           bankRoutingScheme: String,
                           bankRoutingAddress: String,
                           callContext: Option[CallContext]): OBPReturnType[Bank] = {
      Future {
        Connector.connector.vend.createOrUpdateBank(
          bankId,
          fullBankName,
          shortBankName,
          logoURL,
          websiteURL,
          swiftBIC,
          national_identifier,
          bankRoutingScheme,
          bankRoutingAddress
        ) map {
          i =>  (i, callContext)
        }
      } map { unboxFull(_) }
    }
    def getBalances(callContext: Option[CallContext]) : OBPReturnType[List[Bank]] = {
      Connector.connector.vend.getBanks(callContext: Option[CallContext]) map {
        connectorEmptyResponse(_, callContext)
      }
    }
    def getBankAccount(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]): OBPReturnType[BankAccount] = {
      Connector.connector.vend.getBankAccount(bankId, accountId, callContext) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$BankAccountNotFound Current BankId is $bankId and Current AccountId is $accountId", 400 ), i._2)
      }
    }
    def getBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[List[BankAccount]] = {
      Connector.connector.vend.getBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$InvalidConnectorResponseForGetBankAccounts", 400 ), i._2)
      }
    }

    def getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[AccountsBalances] = {
      Connector.connector.vend.getBankAccountsBalances(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$InvalidConnectorResponseForGetBankAccounts", 400 ), i._2)
      }
    }

    def getBankAccountByIban(iban : String, callContext: Option[CallContext]) : OBPReturnType[BankAccount] = {
      Connector.connector.vend.getBankAccountByIban(iban : String, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext,s"${BankAccountNotFound.replaceAll("BANK_ID and ACCOUNT_ID. ", "IBAN.")} Current IBAN is $iban", 400 ), i._2)
      }
    }

    def checkBankAccountExists(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]) : OBPReturnType[BankAccount] = {
      Connector.connector.vend.checkBankAccountExists(bankId, accountId, callContext) } map { i =>
        (unboxFullOrFail(i._1, callContext, s"$BankAccountNotFound Current BankId is $bankId and Current AccountId is $accountId"), i._2)
      }

    def getTransaction(bankId: BankId, accountId : AccountId, transactionId : TransactionId, callContext: Option[CallContext] = None) : OBPReturnType[Transaction] = {
      Connector.connector.vend.getTransaction(bankId, accountId, transactionId, callContext) map {
        x => (unboxFullOrFail(x._1, callContext, TransactionNotFound, 400), x._2)
      }
    }
    
    def moderatedBankAccountCore(account: BankAccount, view: View, user: Box[User], callContext: Option[CallContext]) = Future {
      account.moderatedBankAccountCore(view, BankIdAccountId(account.bankId, account.accountId), user, callContext)
    } map { fullBoxOrException(_)
    } map { unboxFull(_) }
    
    def moderatedOtherBankAccounts(account: BankAccount, 
                                   view: View, 
                                   user: Box[User], 
                                   callContext: Option[CallContext]): Future[List[ModeratedOtherBankAccount]] = 
      Future(account.moderatedOtherBankAccounts(view, BankIdAccountId(account.bankId, account.accountId), user)) map { connectorEmptyResponse(_, callContext) }    
    def moderatedOtherBankAccount(account: BankAccount,
                                  counterpartyId: String, 
                                  view: View, 
                                  user: Box[User], 
                                  callContext: Option[CallContext]): Future[ModeratedOtherBankAccount] = 
      Future(account.moderatedOtherBankAccount(counterpartyId, view, BankIdAccountId(account.bankId, account.accountId), user, callContext)) map { connectorEmptyResponse(_, callContext) }

    def getTransactionsCore(bankId: BankId, accountID: AccountId, queryParams:  List[OBPQueryParam], callContext: Option[CallContext]): OBPReturnType[List[TransactionCore]] =
      Connector.connector.vend.getTransactionsCore(bankId: BankId, accountID: AccountId, queryParams:  List[OBPQueryParam], callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext,s"$InvalidConnectorResponseForGetTransactions", 400 ), i._2)
      }
    def checkOwnerViewAccessAndReturnOwnerView(user: User, bankAccountId: BankIdAccountId, callContext: Option[CallContext]) : Future[View] = {
      Future {user.checkOwnerViewAccessAndReturnOwnerView(bankAccountId)}  map {
        unboxFullOrFail(_, callContext, s"$UserNoOwnerView" +"userId : " + user.userId + ". bankId : " + s"${bankAccountId.bankId}" + ". accountId : " + s"${bankAccountId.accountId}")
      }
    }

    def checkViewAccessAndReturnView(viewId : ViewId, bankAccountId: BankIdAccountId, user: Option[User], callContext: Option[CallContext]) : Future[View] = {
      Future{
        APIUtil.checkViewAccessAndReturnView(viewId, bankAccountId, user)
      } map {
        unboxFullOrFail(_, callContext, s"$UserNoPermissionAccessView")
      }
    }
    
    def checkAuthorisationToCreateTransactionRequest(viewId : ViewId, bankAccountId: BankIdAccountId, user: User, callContext: Option[CallContext]) : Future[Boolean] = {
      Future{
        code.api.util.APIUtil.hasEntitlement(bankAccountId.bankId.value, user.userId, canCreateAnyTransactionRequest) match {
          case true => Full(true)
          case false => user.hasOwnerViewAccess(BankIdAccountId(bankAccountId.bankId,bankAccountId.accountId)) match {
            case true => Full(true)
            case false => Empty
          }
        }
      } map {
        unboxFullOrFail(_, callContext, s"$InsufficientAuthorisationToCreateTransactionRequest")
      }
    }
    
    def customView(viewId : ViewId, bankAccountId: BankIdAccountId, callContext: Option[CallContext]) : Future[View] = {
      Views.views.vend.customViewFuture(viewId, bankAccountId) map {
        unboxFullOrFail(_, callContext, s"$ViewNotFound. Current ViewId is $viewId")
      }
    }    
    
    def systemView(viewId : ViewId, callContext: Option[CallContext]) : Future[View] = {
      Views.views.vend.systemViewFuture(viewId) map {
        unboxFullOrFail(_, callContext, s"$SystemViewNotFound. Current ViewId is $viewId")
      }
    }
    def grantAccessToCustomView(view : View, user: User, callContext: Option[CallContext]) : Future[View] = {
      view.isSystem match {
        case false =>
          Future(Views.views.vend.grantAccessToCustomView(ViewIdBankIdAccountId(view.viewId, view.bankId, view.accountId), user)) map {
            unboxFullOrFail(_, callContext, s"$CannotGrantAccountAccess Current ViewId is ${view.viewId.value}")
          }
        case true =>
          Future(Empty) map {
            unboxFullOrFail(_, callContext, s"This function cannot be used for system views.")
          }
      }
    }
    def revokeAccessToCustomView(view : View, user: User, callContext: Option[CallContext]) : Future[Boolean] = {
      view.isSystem match {
        case false =>
          Future(Views.views.vend.revokeAccess(ViewIdBankIdAccountId(view.viewId, view.bankId, view.accountId), user)) map {
            unboxFullOrFail(_, callContext, s"$CannotRevokeAccountAccess Current ViewId is ${view.viewId.value}")
          }
        case true =>
          Future(Empty) map {
            unboxFullOrFail(_, callContext, s"This function cannot be used for system views.")
          }
      }
    }
    def grantAccessToSystemView(bankId: BankId, accountId: AccountId, view : View, user: User, callContext: Option[CallContext]) : Future[View] = {
      view.isSystem match {
        case true =>
          Future(Views.views.vend.grantAccessToSystemView(bankId, accountId, view, user)) map {
            unboxFullOrFail(_, callContext, s"$CannotGrantAccountAccess Current ViewId is ${view.viewId.value}")
          }
        case false =>
          Future(Empty) map {
            unboxFullOrFail(_, callContext, s"This function cannot be used for custom views.")
          }
      }
    }
    def revokeAccessToSystemView(bankId: BankId, accountId: AccountId, view : View, user: User, callContext: Option[CallContext]) : Future[Boolean] = {
      view.isSystem match {
        case true =>
          Future(Views.views.vend.revokeAccessToSystemView(bankId, accountId, view, user)) map {
            unboxFullOrFail(_, callContext, s"$CannotRevokeAccountAccess Current ViewId is ${view.viewId.value}")
          }
        case false =>
          Future(Empty) map {
            unboxFullOrFail(_, callContext, s"This function cannot be used for custom views.")
          }
      }
    }
    
    def canGrantAccessToView(bankId: BankId, accountId: AccountId, user: User, callContext: Option[CallContext]) : Future[Box[Boolean]] = {
      Helper.wrapStatementToFuture(UserMissOwnerViewOrNotAccountHolder) {
        canGrantAccessToViewCommon(bankId, accountId, user)
      }
    }

    def canRevokeAccessToView(bankId: BankId, accountId: AccountId, user: User, callContext: Option[CallContext]) : Future[Box[Boolean]] = {
      Helper.wrapStatementToFuture(UserMissOwnerViewOrNotAccountHolder) {
        canRevokeAccessToViewCommon(bankId, accountId, user)
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

    def getConsumerByConsumerId(consumerId: String, callContext: Option[CallContext]): Future[Consumer] = {
      Consumers.consumers.vend.getConsumerByConsumerIdFuture(consumerId) map {
        unboxFullOrFail(_, callContext, s"$ConsumerNotFoundByConsumerId Current ConsumerId is $consumerId")
      }
    }
    def checkConsumerByConsumerId(consumerId: String, callContext: Option[CallContext]): Future[Consumer] = {
      Consumers.consumers.vend.getConsumerByConsumerIdFuture(consumerId) map {
        unboxFullOrFail(_, callContext, s"$ConsumerNotFoundByConsumerId Current ConsumerId is $consumerId")
      } map {
        c => c.isActive.get match {
          case true => c
          case false => unboxFullOrFail(Empty, callContext, s"$ConsumerIsDisabled ConsumerId: $consumerId")
        }
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
      Connector.connector.vend.getCustomers(bankId, callContext, queryParams) map {
        connectorEmptyResponse(_, callContext)
      }
    }
    def getCustomersByCustomerPhoneNumber(bankId : BankId, phoneNumber: String, callContext: Option[CallContext]): OBPReturnType[List[Customer]] = {
      Connector.connector.vend.getCustomersByCustomerPhoneNumber(bankId, phoneNumber, callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getCustomerByCustomerId(customerId : String, callContext: Option[CallContext]): OBPReturnType[Customer] = {
      Connector.connector.vend.getCustomerByCustomerId(customerId, callContext) map {
        unboxFullOrFail(_, callContext, s"$CustomerNotFoundByCustomerId. Current CustomerId($customerId)")
      }
    }
    def checkCustomerNumberAvailable(bankId: BankId, customerNumber: String, callContext: Option[CallContext]): OBPReturnType[Boolean] = {
      Connector.connector.vend.checkCustomerNumberAvailable(bankId: BankId, customerNumber: String, callContext: Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse", 400), i._2) 
      }
    }
    def getCustomerByCustomerNumber(customerNumber : String, bankId : BankId, callContext: Option[CallContext]): OBPReturnType[Customer] = {
      Connector.connector.vend.getCustomerByCustomerNumber(customerNumber, bankId, callContext) map {
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
    def getTaxResidences(customerId : String, callContext: Option[CallContext]): OBPReturnType[List[TaxResidence]] = {
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
        Connector.connector.vend.getAdapterInfo(callContext) map {
          connectorEmptyResponse(_, callContext)
        }
    }

    def getEntitlementsByUserId(userId: String, callContext: Option[CallContext]): Future[List[Entitlement]] = {
      Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(userId) map {
        connectorEmptyResponse(_, callContext)
      }
    }

    def getEntitlementsByBankId(bankId: String, callContext: Option[CallContext]): Future[List[Entitlement]] = {
      Entitlement.entitlement.vend.getEntitlementsByBankId(bankId) map {
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
      Connector.connector.vend.getCounterparties(bankId,accountId,viewId, callContext) map { i=>
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
        APIUtil.hasEntitlement(bankId, userId, role)
      }
    }
    def hasEntitlement(bankId: String, userId: String, role: ApiRole, callContext: Option[CallContext] = None): Future[Box[Unit]] = {
      hasEntitlement(UserHasMissingRoles)(bankId, userId, role)
    }
    
    def hasAtLeastOneEntitlement(failMsg: => String)(bankId: String, userId: String, roles: List[ApiRole]): Future[Box[Unit]] =
      Helper.booleanToFuture(failMsg) {
        APIUtil.hasAtLeastOneEntitlement(bankId, userId, roles)
      }

    def hasAtLeastOneEntitlement(bankId: String, userId: String, roles: List[ApiRole]): Future[Box[Unit]] =
      hasAtLeastOneEntitlement(UserHasMissingRoles + roles.mkString(" or "))(bankId, userId, roles)


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
      Future { UserX.findByUserId(userId).map(user =>(user, callContext))} map {
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
      challengeType: Option[String],
      scaMethod: Option[SCA],
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
        challengeType: Option[String],
        scaMethod: Option[SCA],
        callContext: Option[CallContext]
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetTransactionRequests210", 400), i._2)
      }
    }  
    def createTransactionRequestv400(
      u: User,
      viewId: ViewId,
      fromAccount: BankAccount,
      toAccount: BankAccount,
      transactionRequestType: TransactionRequestType,
      transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
      detailsPlain: String,
      chargePolicy: String,
      challengeType: Option[String],
      scaMethod: Option[SCA],
      callContext: Option[CallContext]): OBPReturnType[TransactionRequest] =
    {
      Connector.connector.vend.createTransactionRequestv400(
        u: User,
        viewId: ViewId,
        fromAccount: BankAccount,
        toAccount: BankAccount,
        transactionRequestType: TransactionRequestType,
        transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
        detailsPlain: String,
        chargePolicy: String,
        challengeType: Option[String],
        scaMethod: Option[SCA],
        callContext: Option[CallContext]
      ) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponseForGetTransactionRequests210", 400), i._2)
      }
    }
    
    def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]): OBPReturnType[CounterpartyTrait] = 
    {
      Connector.connector.vend.getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext]) map { i =>
        (unboxFullOrFail(i._1, callContext, s"$CounterpartyNotFoundByCounterpartyId Current counterpartyId($counterpartyId) ", 400),
          i._2)
      }
    }
    
    
    def toBankAccount(counterparty: CounterpartyTrait, callContext: Option[CallContext]) : Future[BankAccount] =
    {
      Future{BankAccountX.toBankAccount(counterparty)} map {
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
      Future{ ExpectedChallengeAnswer.expectedChallengeAnswerProvider.vend.validateChallengeAnswerInOBPSide(challengeId, challengeAnswer, None)} map {
        unboxFullOrFail(_, callContext, s"$UnknownError ")
      }
    }
    def validateChallengeAnswerInOBPSide400(challengeId: String, challengeAnswer: String, userId: String, callContext: Option[CallContext]) : Future[Boolean] = 
    {
      //Note: this method is not over kafka yet, so use Future here.
      Future{ ExpectedChallengeAnswer.expectedChallengeAnswerProvider.vend.validateChallengeAnswerInOBPSide(challengeId, challengeAnswer, Some(userId))} map {
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

    def getAccountAttributeById(accountAttributeId: String, callContext: Option[CallContext]): OBPReturnType[AccountAttribute] = 
      Connector.connector.vend.getAccountAttributeById(accountAttributeId: String, callContext: Option[CallContext]) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    
    def getTransactionAttributeById(transactionAttributeId: String, callContext: Option[CallContext]): OBPReturnType[TransactionAttribute] = 
      Connector.connector.vend.getTransactionAttributeById(transactionAttributeId: String, callContext: Option[CallContext]) map {
        x => (unboxFullOrFail(x._1, callContext, TransactionAttributeNotFound, 400), x._2)
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

    def createOrUpdateCustomerAttribute(
      bankId: BankId,
      customerId: CustomerId,
      customerAttributeId: Option[String],
      name: String,
      attributeType: CustomerAttributeType.Value,
      value: String,
      callContext: Option[CallContext]
    ): OBPReturnType[CustomerAttribute] = {
      Connector.connector.vend.createOrUpdateCustomerAttribute(
        bankId: BankId,
        customerId: CustomerId,
        customerAttributeId: Option[String],
        name: String,
        attributeType: CustomerAttributeType.Value,
        value: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def createOrUpdateTransactionAttribute(
      bankId: BankId,
      transactionId: TransactionId,
      transactionAttributeId: Option[String],
      name: String,
      attributeType: TransactionAttributeType.Value,
      value: String,
      callContext: Option[CallContext]
    ): OBPReturnType[TransactionAttribute] = {
      Connector.connector.vend.createOrUpdateTransactionAttribute(
        bankId: BankId,
        transactionId: TransactionId,
        transactionAttributeId: Option[String],
        name: String,
        attributeType: TransactionAttributeType.Value,
        value: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    def createAccountAttributes(bankId: BankId,
                                accountId: AccountId,
                                productCode: ProductCode,
                                accountAttributes: List[ProductAttribute],
                                callContext: Option[CallContext]): OBPReturnType[List[AccountAttribute]] = {
      Connector.connector.vend.createAccountAttributes(
        bankId: BankId,
        accountId: AccountId,
        productCode: ProductCode,
        accountAttributes,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getAccountAttributesByAccount(bankId: BankId,
                                      accountId: AccountId,
                                      callContext: Option[CallContext]): OBPReturnType[List[AccountAttribute]] = {
      Connector.connector.vend.getAccountAttributesByAccount(
        bankId: BankId,
        accountId: AccountId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getCustomerAttributes(bankId: BankId,
      customerId: CustomerId,
      callContext: Option[CallContext]): OBPReturnType[List[CustomerAttribute]] = {
      Connector.connector.vend.getCustomerAttributes(
        bankId: BankId,
        customerId: CustomerId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    /**
     * get CustomerAttribute according name and values
     * @param bankId CustomerAttribute must belongs the bank
     * @param nameValues key is attribute name, value is attribute values.
     *                   CustomerAttribute name must equals name,
     *                   CustomerAttribute value must be one of values
     * @param callContext
     * @return filtered CustomerAttribute.customerId
     */
    def getCustomerIdByAttributeNameValues(
                                            bankId: BankId,
                                            nameValues: Map[String, List[String]],
                                            callContext: Option[CallContext]): Future[(List[CustomerId], Option[CallContext])] =
      Connector.connector.vend.getCustomerIdByAttributeNameValues(bankId, nameValues, callContext)  map {
        i =>
          val customerIds: Box[List[CustomerId]] = i._1.map(_.map(CustomerId(_)))
          (connectorEmptyResponse(customerIds, callContext), i._2)
      }

    def getCustomerAttributesForCustomers(
      customers: List[Customer],
      callContext: Option[CallContext]): OBPReturnType[List[(Customer, List[CustomerAttribute])]] = {
      Connector.connector.vend.getCustomerAttributesForCustomers(
        customers: List[Customer],
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    
    def getTransactionAttributes(bankId: BankId,
      transactionId: TransactionId,
      callContext: Option[CallContext]): OBPReturnType[List[TransactionAttribute]] = {
      Connector.connector.vend.getTransactionAttributes(
        bankId: BankId,
        transactionId: TransactionId,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def getCustomerAttributeById(
      customerAttributeId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[CustomerAttribute] = {
      Connector.connector.vend.getCustomerAttributeById(
        customerAttributeId: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext,CustomerAttributeNotFound), i._2)
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
      val userList = userIds.filterNot(StringUtils.isBlank).distinct.map(UserX.findByUserId).collect {
        case Full(user) => user
      }
      (userList, callContext)
    }

    def createBankAccount(
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
    ): OBPReturnType[BankAccount] = 
      Connector.connector.vend.createBankAccount(
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
        callContext
      ) map {
        i => (unboxFullOrFail(i._1, callContext, UnknownError, 400), i._2)
      }

    def addBankAccount(
      bankId: BankId,
      accountType: String,
      accountLabel: String,
      currency: String,
      initialBalance: BigDecimal,
      accountHolderName: String,
      branchId: String,
      accountRoutingScheme: String,
      accountRoutingAddress: String,
      callContext: Option[CallContext]
    ): OBPReturnType[BankAccount] =
      Connector.connector.vend.addBankAccount(
        bankId: BankId,
        accountType: String,
        accountLabel: String,
        currency: String,
        initialBalance: BigDecimal,
        accountHolderName: String,
        branchId: String,
        accountRoutingScheme: String,
        accountRoutingAddress: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, UnknownError, 400), i._2)
      }
    
    def updateBankAccount(
                           bankId: BankId,
                           accountId: AccountId,
                           accountType: String,
                           accountLabel: String,
                           branchId: String,
                           accountRoutingScheme: String,
                           accountRoutingAddress: String,
                           callContext: Option[CallContext]
                         ): OBPReturnType[BankAccount] =
      Connector.connector.vend.updateBankAccount(
        bankId: BankId,
        accountId: AccountId,
        accountType: String,
        accountLabel: String,
        branchId: String,
        accountRoutingScheme: String,
        accountRoutingAddress: String,
        callContext
      ) map {
        i => (unboxFullOrFail(i._1, callContext, UnknownError, 400), i._2)
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
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current collection code($collectionCode)", 400), i._2)
      }
    }
    def getProduct(bankId : BankId, productCode : ProductCode, callContext: Option[CallContext]) : OBPReturnType[Product] =
      Future {Connector.connector.vend.getProduct(bankId : BankId, productCode : ProductCode)} map {
        i => (unboxFullOrFail(i, callContext, ProductNotFoundByProductCode + " {" + productCode.value + "}", 400), callContext)
      }
    
    def getProductCollection(collectionCode: String, 
                             callContext: Option[CallContext]): OBPReturnType[List[ProductCollection]] = {
      Connector.connector.vend.getProductCollection(collectionCode, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current collection code($collectionCode)", 400), i._2)
      }
    }
    def getOrCreateProductCollectionItems(collectionCode: String,
                                          memberProductCodes: List[String],
                                          callContext: Option[CallContext]): OBPReturnType[List[ProductCollectionItem]] = {
      Connector.connector.vend.getOrCreateProductCollectionItem(collectionCode, memberProductCodes, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current collection code($collectionCode)", 400), i._2)
      }
    }
    def getProductCollectionItems(collectionCode: String,
                                          callContext: Option[CallContext]): OBPReturnType[List[ProductCollectionItem]] = {
      Connector.connector.vend.getProductCollectionItem(collectionCode, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current collection code($collectionCode)", 400), i._2)
      }
    }
    def getProductCollectionItemsTree(collectionCode: String, 
                                      bankId: String,
                                      callContext: Option[CallContext]): OBPReturnType[List[(ProductCollectionItem, Product, List[ProductAttribute])]] = {
      Connector.connector.vend.getProductCollectionItemsTree(collectionCode, bankId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current collection code($collectionCode)", 400), i._2)
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
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Can not createMeeting in the backend. ", 400), i._2)
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
          i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Can not getMeetings in the backend. ", 400), i._2)
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
        Connector.connector.vend.getCoreBankAccounts(bankIdAccountIds, callContext) map {
          i => (unboxFullOrFail(i, callContext, s"$InvalidConnectorResponse Can not ${nameOf(getCoreBankAccountsFuture(bankIdAccountIds, callContext))} in the backend. ", 400))
        }
      }

    def getBankAccountsHeldFuture(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]): OBPReturnType[List[AccountHeld]] =
    {
      Connector.connector.vend.getBankAccountsHeld(bankIdAccountIds, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Can not ${nameOf(getBankAccountsHeldFuture(bankIdAccountIds, callContext))} in the backend. ", 400), i._2)
      }
    }

    def createOrUpdateKycCheck(bankId: String,
                                customerId: String,
                                id: String,
                                customerNumber: String,
                                date: Date,
                                how: String,
                                staffUserId: String,
                                mStaffName: String,
                                mSatisfied: Boolean,
                                comments: String,
                                callContext: Option[CallContext]): OBPReturnType[KycCheck] = {
      Connector.connector.vend.createOrUpdateKycCheck(bankId, customerId, id, customerNumber, date, how, staffUserId, mStaffName, mSatisfied, comments, callContext)
       .map {
          i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Can not create or update KycCheck in the backend. ", 400), i._2)
       }
    }

    def createOrUpdateKycDocument(bankId: String,
                                  customerId: String,
                                  id: String,
                                  customerNumber: String,
                                  `type`: String,
                                  number: String,
                                  issueDate: Date,
                                  issuePlace: String,
                                  expiryDate: Date,
                                  callContext: Option[CallContext]): OBPReturnType[KycDocument] = {
      Connector.connector.vend.createOrUpdateKycDocument(
          bankId,
          customerId,
          id,
          customerNumber,
          `type`,
          number,
          issueDate,
          issuePlace,
          expiryDate,
          callContext)
        .map {
          i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Can not create or update KycDocument in the backend. ", 400), i._2)
        }
    }

    def createOrUpdateKycMedia(bankId: String,
                               customerId: String,
                               id: String,
                               customerNumber: String,
                               `type`: String,
                               url: String,
                               date: Date,
                               relatesToKycDocumentId: String,
                               relatesToKycCheckId: String,
                               callContext: Option[CallContext]): OBPReturnType[KycMedia] = {
      Connector.connector.vend.createOrUpdateKycMedia(
        bankId,
        customerId,
        id,
        customerNumber,
        `type`,
        url,
        date,
        relatesToKycDocumentId,
        relatesToKycCheckId,
        callContext
      ).map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Can not create or update KycMedia in the backend. ", 400), i._2)
      }
    }

    def createOrUpdateKycStatus(bankId: String,
      customerId: String,
      customerNumber: String,
      ok: Boolean,
      date: Date,
      callContext: Option[CallContext]): OBPReturnType[KycStatus] = {
      Connector.connector.vend.createOrUpdateKycStatus(
        bankId,
        customerId,
        customerNumber,
        ok,
        date,
        callContext
      ).map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse Can not create or update KycStatus in the backend. ", 400), i._2)
      }
    }

    def getKycChecks(customerId: String,
                              callContext: Option[CallContext]
                             ): OBPReturnType[List[KycCheck]] = {
      Connector.connector.vend.getKycChecks(customerId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current customerId ($customerId)", 400), i._2)
      }
    }

    def getKycDocuments(customerId: String,
                                 callContext: Option[CallContext]
                                ): OBPReturnType[List[KycDocument]] = {
      Connector.connector.vend.getKycDocuments(customerId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current customerId ($customerId)", 400), i._2)
      }
    }

    def getKycMedias(customerId: String,
                              callContext: Option[CallContext]
                             ): OBPReturnType[List[KycMedia]] = {
      Connector.connector.vend.getKycMedias(customerId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current customerId ($customerId)", 400), i._2)
      }
    }

    def getKycStatuses(customerId: String,
                                callContext: Option[CallContext]
                               ): OBPReturnType[List[KycStatus]] = {
      Connector.connector.vend.getKycStatuses(customerId, callContext) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Current customerId ($customerId)", 400), i._2)
      }
    }


    def createMessage(user : User,
                      bankId : BankId,
                      message : String,
                      fromDepartment : String,
                      fromPerson : String,
                      callContext: Option[CallContext]) : OBPReturnType[CustomerMessage] = {
      Connector.connector.vend.createMessage(
        user : User,
        bankId : BankId,
        message : String,
        fromDepartment : String,
        fromPerson : String,
        callContext: Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, s"$InvalidConnectorResponse  Can not create message in the backend.", 400), i._2)
      }
    }

    def getUserCustomerLinkByCustomerId(customerId: String, callContext: Option[CallContext]) : OBPReturnType[UserCustomerLink] = {
      Future {UserCustomerLink.userCustomerLink.vend.getUserCustomerLinkByCustomerId(customerId)}   map {
        i => (unboxFullOrFail(i, callContext, s"$UserCustomerLinksNotFoundForUser Current customerId ($customerId)", 400), callContext)
      }
    }

    def createCustomer(
                        bankId: BankId,
                        legalName: String,
                        mobileNumber: String,
                        email: String,
                        faceImage:
                        CustomerFaceImageTrait,
                        dateOfBirth: Date,
                        relationshipStatus: String,
                        dependents: Int,
                        dobOfDependents: List[Date],
                        highestEducationAttained: String,
                        employmentStatus: String,
                        kycStatus: Boolean,
                        lastOkDate: Date,
                        creditRating: Option[CreditRatingTrait],
                        creditLimit: Option[AmountOfMoneyTrait],
                        title: String,
                        branchId: String,
                        nameSuffix: String,
                        callContext: Option[CallContext]): OBPReturnType[Customer] = 
      Connector.connector.vend.createCustomer(
        bankId: BankId,
        legalName: String,
        mobileNumber: String,
        email: String,
        faceImage:
          CustomerFaceImageTrait,
        dateOfBirth: Date,
        relationshipStatus: String,
        dependents: Int,
        dobOfDependents: List[Date],
        highestEducationAttained: String,
        employmentStatus: String,
        kycStatus: Boolean,
        lastOkDate: Date,
        creditRating: Option[CreditRatingTrait],
        creditLimit: Option[AmountOfMoneyTrait],
        title: String,
        branchId: String,
        nameSuffix: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, CreateCustomerError), i._2)
      }

    def updateCustomerScaData(customerId: String,
                              mobileNumber: Option[String],
                              email: Option[String],
                              customerNumber: Option[String],
                              callContext: Option[CallContext]): OBPReturnType[Customer] =
      Connector.connector.vend.updateCustomerScaData(
        customerId,
        mobileNumber,
        email,
        customerNumber,
        callContext) map {
        i => (unboxFullOrFail(i._1, callContext, UpdateCustomerError), i._2)
      }
    def updateCustomerCreditData(customerId: String,
                                 creditRating: Option[String],
                                 creditSource: Option[String],
                                 creditLimit: Option[AmountOfMoney],
                                 callContext: Option[CallContext]): OBPReturnType[Customer] =
      Connector.connector.vend.updateCustomerCreditData(
        customerId,
        creditRating,
        creditSource,
        creditLimit,
        callContext) map {
        i => (unboxFullOrFail(i._1, callContext, UpdateCustomerError), i._2)
      }

    def updateCustomerGeneralData(customerId: String,
                                  legalName: Option[String] = None,
                                  faceImage: Option[CustomerFaceImageTrait] = None,
                                  dateOfBirth: Option[Date] = None,
                                  relationshipStatus: Option[String] = None,
                                  dependents: Option[Int] = None,
                                  highestEducationAttained: Option[String] = None,
                                  employmentStatus: Option[String] = None,
                                  title: Option[String] = None,
                                  branchId: Option[String] = None,
                                  nameSuffix: Option[String] = None,
                                  callContext: Option[CallContext]): OBPReturnType[Customer] =
      Connector.connector.vend.updateCustomerGeneralData(
        customerId,
        legalName,
        faceImage,
        dateOfBirth,
        relationshipStatus,
        dependents,
        highestEducationAttained,
        employmentStatus,
        title,
        branchId,
        nameSuffix,
        callContext) map {
        i => (unboxFullOrFail(i._1, callContext, UpdateCustomerError), i._2)
      }

    def createPhysicalCard(
      bankCardNumber: String,
      nameOnCard: String,
      cardType: String,
      issueNumber: String,
      serialNumber: String,
      validFrom: Date,
      expires: Date,
      enabled: Boolean,
      cancelled: Boolean,
      onHotList: Boolean,
      technology: String,
      networks: List[String],
      allows: List[String],
      accountId: String,
      bankId: String,
      replacement: Option[CardReplacementInfo],
      pinResets: List[PinResetInfo],
      collected: Option[CardCollectionInfo],
      posted: Option[CardPostedInfo],
      customerId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[PhysicalCard] =
      Connector.connector.vend.createPhysicalCard(
        bankCardNumber: String,
        nameOnCard: String,
        cardType: String,
        issueNumber: String,
        serialNumber: String,
        validFrom: Date,
        expires: Date,
        enabled: Boolean,
        cancelled: Boolean,
        onHotList: Boolean,
        technology: String,
        networks: List[String],
        allows: List[String],
        accountId: String,
        bankId: String,
        replacement: Option[CardReplacementInfo],
        pinResets: List[PinResetInfo],
        collected: Option[CardCollectionInfo],
        posted: Option[CardPostedInfo],
        customerId: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, s"$CreateCardError"), i._2)
      }

    def updatePhysicalCard(
      cardId: String,
      bankCardNumber: String,
      nameOnCard: String,
      cardType: String,
      issueNumber: String,
      serialNumber: String,
      validFrom: Date,
      expires: Date,
      enabled: Boolean,
      cancelled: Boolean,
      onHotList: Boolean,
      technology: String,
      networks: List[String],
      allows: List[String],
      accountId: String,
      bankId: String,
      replacement: Option[CardReplacementInfo],
      pinResets: List[PinResetInfo],
      collected: Option[CardCollectionInfo],
      posted: Option[CardPostedInfo],
      customerId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[PhysicalCardTrait] =
      Connector.connector.vend.updatePhysicalCard(
        cardId: String,
        bankCardNumber: String,
        nameOnCard: String,
        cardType: String,
        issueNumber: String,
        serialNumber: String,
        validFrom: Date,
        expires: Date,
        enabled: Boolean,
        cancelled: Boolean,
        onHotList: Boolean,
        technology: String,
        networks: List[String],
        allows: List[String],
        accountId: String,
        bankId: String,
        replacement: Option[CardReplacementInfo],
        pinResets: List[PinResetInfo],
        collected: Option[CardCollectionInfo],
        posted: Option[CardPostedInfo],
        customerId: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, s"$UpdateCardError"), i._2)
      }
    
    def getPhysicalCardsForBank(bank: Bank, user : User, queryParams: List[OBPQueryParam], callContext:Option[CallContext]) : OBPReturnType[List[PhysicalCard]] =
      Connector.connector.vend.getPhysicalCardsForBank(bank: Bank, user : User, queryParams: List[OBPQueryParam], callContext:Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, CardNotFound), i._2)
      }

    def getPhysicalCardForBank(bankId: BankId, cardId:String ,callContext:Option[CallContext]) : OBPReturnType[PhysicalCardTrait] =
      Connector.connector.vend.getPhysicalCardForBank(bankId: BankId, cardId: String, callContext:Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, s"$CardNotFound Current CardId($cardId)"), i._2)
      }

    def deletePhysicalCardForBank(bankId: BankId, cardId:String ,callContext:Option[CallContext]) : OBPReturnType[Boolean] =
      Connector.connector.vend.deletePhysicalCardForBank(bankId: BankId, cardId: String, callContext:Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, s"$CardNotFound Current CardId($cardId)"), i._2)
      }
    def getMethodRoutingsByMethdName(methodName: Box[String]): Future[List[MethodRoutingT]] = Future {
      this.getMethodRoutings(methodName.toOption)
    }
    def getCardAttributeById(cardAttributeId: String, callContext:Option[CallContext]) =
      Connector.connector.vend.getCardAttributeById(cardAttributeId: String, callContext:Option[CallContext]) map {
        i => (unboxFullOrFail(i._1, callContext, s"$CardAttributeNotFound Current CardAttributeId($cardAttributeId)"), i._2)
      }
    
    
    def createOrUpdateCardAttribute(
      bankId: Option[BankId],
      cardId: Option[String],
      cardAttributeId: Option[String],
      name: String,
      attributeType: CardAttributeType.Value,
      value: String,
      callContext: Option[CallContext]
    ): OBPReturnType[CardAttribute] = {
      Connector.connector.vend.createOrUpdateCardAttribute(
        bankId: Option[BankId],
        cardId: Option[String],
        cardAttributeId: Option[String],
        name: String,
        attributeType: CardAttributeType.Value,
        value: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def getCardAttributesFromProvider(
      cardId: String,
      callContext: Option[CallContext]
    ): OBPReturnType[List[CardAttribute]] = {
      Connector.connector.vend.getCardAttributesFromProvider(
        cardId: String,
        callContext: Option[CallContext]
      ) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }
    def createOrUpdateMethodRouting(methodRouting: MethodRoutingT) = Future {
      MethodRoutingProvider.connectorMethodProvider.vend.createOrUpdate(methodRouting)
     }

    def deleteMethodRouting(methodRoutingId: String) = Future {
      MethodRoutingProvider.connectorMethodProvider.vend.delete(methodRoutingId)
    }

    def getMethodRoutingById(methodRoutingId : String, callContext: Option[CallContext]): OBPReturnType[MethodRoutingT] = {
      val methodRoutingBox: Box[MethodRoutingT] = MethodRoutingProvider.connectorMethodProvider.vend.getById(methodRoutingId)
      val methodRouting = unboxFullOrFail(methodRoutingBox, callContext, MethodRoutingNotFoundByMethodRoutingId)
      Future{
        (methodRouting, callContext)
      }
    }

    private[this] val methodRoutingTTL = APIUtil.getPropsValue(s"methodRouting.cache.ttl.seconds", "0").toInt 

    def getMethodRoutings(methodName: Option[String], isBankIdExactMatch: Option[Boolean] = None, bankIdPattern: Option[String] = None): List[MethodRoutingT] = {
      import scala.concurrent.duration._

      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(methodRoutingTTL second) {
          MethodRoutingProvider.connectorMethodProvider.vend.getMethodRoutings(methodName, isBankIdExactMatch, bankIdPattern)
        }
      }
    }

    private def createDynamicEntity(dynamicEntity: DynamicEntityT, callContext: Option[CallContext]): Future[Box[DynamicEntityT]] = {
      val existsDynamicEntity = DynamicEntityProvider.connectorMethodProvider.vend.getByEntityName(dynamicEntity.entityName)

      if(existsDynamicEntity.isDefined) {
        val errorMsg = s"$DynamicEntityNameAlreadyExists current entityName is '${dynamicEntity.entityName}'."
        return Helper.booleanToFuture(errorMsg)(existsDynamicEntity.isEmpty).map(_.asInstanceOf[Box[DynamicEntityT]])
      }

      Future {
        DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(dynamicEntity)
      }
    }

    private def updateDynamicEntity(dynamicEntity: DynamicEntityT, dynamicEntityId: String , callContext: Option[CallContext]): Future[Box[DynamicEntityT]] = {
      val originEntity = DynamicEntityProvider.connectorMethodProvider.vend.getById(dynamicEntityId)
      // if can't find by id, return 404 error
      val idNotExistsMsg = s"$DynamicEntityNotFoundByDynamicEntityId dynamicEntityId = ${dynamicEntity.dynamicEntityId.get}."

      if (originEntity.isEmpty) {
        return Helper.booleanToFuture(idNotExistsMsg, 404)(originEntity.isDefined).map(_.asInstanceOf[Box[DynamicEntityT]])
      }

      val originEntityName = originEntity.map(_.entityName).orNull
      // if entityName changed and the new entityName already exists, return error message
      if(dynamicEntity.entityName != originEntityName) {
        val existsDynamicEntity = DynamicEntityProvider.connectorMethodProvider.vend.getByEntityName(dynamicEntity.entityName)

        if(existsDynamicEntity.isDefined) {
          val errorMsg = s"$DynamicEntityNameAlreadyExists current entityName is '${dynamicEntity.entityName}'."
          return Helper.booleanToFuture(errorMsg)(existsDynamicEntity.isEmpty).map(_.asInstanceOf[Box[DynamicEntityT]])
        }
      }

      Future {
        DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(dynamicEntity)
      }

    }

    def createOrUpdateDynamicEntity(dynamicEntity: DynamicEntityT, callContext: Option[CallContext]): Future[Box[DynamicEntityT]] =
      dynamicEntity.dynamicEntityId match {
        case Some(dynamicEntityId) => updateDynamicEntity(dynamicEntity, dynamicEntityId, callContext)
        case None => createDynamicEntity(dynamicEntity, callContext)
      }

    def deleteDynamicEntity(dynamicEntityId: String): Future[Box[Boolean]] = Future {
      DynamicEntityProvider.connectorMethodProvider.vend.delete(dynamicEntityId)
    }

    def getDynamicEntityById(dynamicEntityId : String, callContext: Option[CallContext]): OBPReturnType[DynamicEntityT] = {
      val dynamicEntityBox: Box[DynamicEntityT] = DynamicEntityProvider.connectorMethodProvider.vend.getById(dynamicEntityId)
      val dynamicEntity = unboxFullOrFail(dynamicEntityBox, callContext, DynamicEntityNotFoundByDynamicEntityId, 404)
      Future{
        (dynamicEntity, callContext)
      }
    }

    def getDynamicEntityByEntityName(entityName : String, callContext: Option[CallContext]): OBPReturnType[Box[DynamicEntityT]] = Future {
      val boxedDynamicEntity = DynamicEntityProvider.connectorMethodProvider.vend.getByEntityName(entityName)
      (boxedDynamicEntity, callContext)
    }

    private[this] val dynamicEntityTTL = APIUtil.getPropsValue(s"dynamicEntity.cache.ttl.seconds", "0").toInt

    def getDynamicEntities(): List[DynamicEntityT] = {
      import scala.concurrent.duration._

      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(dynamicEntityTTL second) {
          DynamicEntityProvider.connectorMethodProvider.vend.getDynamicEntities()
        }
      }
    }
    
    def makeHistoricalPayment(
      fromAccount: BankAccount,
      toAccount: BankAccount,
      posted:  Date,
      completed: Date,
      amount: BigDecimal,
      description: String,
      transactionRequestType: String,
      chargePolicy: String,
      callContext: Option[CallContext]
    ) : OBPReturnType[TransactionId] =
      Connector.connector.vend.makeHistoricalPayment(
        fromAccount: BankAccount,
        toAccount: BankAccount,
        posted:  Date,
        completed: Date,
        amount: BigDecimal,
        description: String,
        transactionRequestType: String,
        chargePolicy: String,
        callContext: Option[CallContext]
      ) map {
        i => (unboxFullOrFail(i._1, callContext, s"$CreateTransactionsException"), i._2)
      }

    def invokeDynamicConnector(operation: DynamicEntityOperation, entityName: String, requestBody: Option[JObject], entityId: Option[String], callContext: Option[CallContext]): OBPReturnType[Box[JValue]] = {
      Connector.connector.vend.dynamicEntityProcess(operation, entityName, requestBody, entityId, callContext)
    }


    def createDirectDebit(bankId : String, 
                          accountId: String, 
                          customerId: String,
                          userId: String,
                          couterpartyId: String,
                          dateSigned: Date,
                          dateStarts: Date,
                          dateExpires: Option[Date], 
                          callContext: Option[CallContext]): OBPReturnType[DirectDebitTrait] = {
      Connector.connector.vend.createDirectDebit(
        bankId, 
        accountId, 
        customerId, 
        userId, 
        couterpartyId, 
        dateSigned, 
        dateStarts, 
        dateExpires, 
        callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    def createStandingOrder(bankId: String,
                            accountId: String,
                            customerId: String,
                            userId: String,
                            couterpartyId: String,
                            amountValue: BigDecimal,
                            amountCurrency: String,
                            whenFrequency: String,
                            whenDetail: String,
                            dateSigned: Date,
                            dateStarts: Date,
                            dateExpires: Option[Date],
                            callContext: Option[CallContext]): OBPReturnType[StandingOrderTrait] = {
      Connector.connector.vend.createStandingOrder(
        bankId,
        accountId,
        customerId,
        userId,
        couterpartyId,
        amountValue,
        amountCurrency,
        whenFrequency,
        whenDetail,
        dateSigned,
        dateStarts,
        dateExpires,
        callContext) map {
        i => (connectorEmptyResponse(i._1, callContext), i._2)
      }
    }

    private lazy val supportedConnectorNames: Set[String] = {
       APIUtil.getPropsValue("connector", "star") match {
         case "star" =>
           APIUtil.getPropsValue("starConnector_supported_types", "mapped")
           .split(',').map(_.trim).toSet
         case conn => Set(conn)
       }
    }

    def getConnectorByName(connectorName: String): Option[Connector] = {
      if(supportedConnectorNames.exists(connectorName.startsWith _)) {
        Connector.nameToConnector.get(connectorName).map(_())
      } else {
        None
      }
    }

    def getConnectorMethod(connectorName: String, methodName: String): Option[MethodSymbol] = {
      getConnectorByName(connectorName).flatMap(_.implementedMethods.get(methodName))
    }

  }
}
