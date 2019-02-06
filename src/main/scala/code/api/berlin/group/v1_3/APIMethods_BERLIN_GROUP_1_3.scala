package code.api.berlin.group.v1_3

import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.berlin.group.v1_3.OBP_BERLIN_GROUP_1_3.Implementations1_3
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.{ApiVersion, NewStyle}
import code.api.util.ErrorMessages._
import code.api.util.ApiTag._
import code.api.util.NewStyle.HttpCode
import code.bankconnectors.Connector
import code.model._
import code.util.Helper
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait APIMethods_BERLIN_GROUP_1_3 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations1_3 = new Object() {
    val implementedInApiVersion: ApiVersion = ApiVersion.berlinGroupV1_3

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)

//    def endpoints = 
//      getAccountList ::
//      getAccountBalances ::
//      getTransactionList ::
//      getTransactionDetails ::
//      getCardAccount ::
//      ReadCardAccount ::
//      getCardAccountBalances ::
//      getCardAccountTransactionList ::
//      createConsent ::
//      getConsentInformation ::
//      deleteConsent ::
//      getConsentStatus ::
//      initiatePayment ::
//      checkAvailabilityOfFunds ::
//      createSigningBasket ::
//      getPaymentInitiationScaStatus ::
//      Nil
    
    
//    resourceDocs += ResourceDoc(
//      getAccountList,
//      implementedInApiVersion,
//      "getAccountList",
//      "GET",
//      "/accounts",
//      "Read Account List",
//      s"""
//         |Reads a list of bank accounts, with balances where required.
//         |It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
//         |
//         |${authenticationRequiredMessage(true)}
//         |
//         |This endpoint is work in progress. Experimental!
//         |""",
//      emptyObjectJson,
//      SwaggerDefinitionsJSON.coreAccountsJsonV1,
//      List(UserNotLoggedIn,UnknownError),
//      Catalogs(Core, PSD2, OBWG),
//      List(apiTagBerlinGroup, apiTagAccount, apiTagPrivateData))
//
//
//    apiRelations += ApiRelation(getAccountList, getAccountList, "self")
//
//
//
//    lazy val getAccountList : OBPEndpoint = {
//      //get private accounts for one bank
//      case "accounts" :: Nil JsonGet _ => {
//        cc =>
//          for {
//            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
//  
//            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
//  
//            bankId = BankId(defaultBankId)
//  
//            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
//  
//            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
//            
//            Full((coreAccounts,callContext1)) <- {Connector.connector.vend.getCoreBankAccountsFuture(availablePrivateAccounts, callContext)}
//            
//          } yield {
//            (JSONFactory_BERLIN_GROUP_1_3.createTransactionListJSON(coreAccounts), callContext)
//          }
//      }
//    }

//    resourceDocs += ResourceDoc(
//      getAccountBalances,
//      implementedInApiVersion,
//      "getAccountBalances",
//      "GET",
//      "/accounts/ACCOUNT_ID/balances",
//      "Read Balance",
//      s"""
//        |Reads account data from a given account addressed by “account-id”.
//        |
//        |${authenticationRequiredMessage(true)}
//        |
//        |This endpoint is work in progress. Experimental!
//        |""",
//      emptyObjectJson,
//      SwaggerDefinitionsJSON.accountBalances,
//      List(UserNotLoggedIn, ViewNotFound, UserNoPermissionAccessView, UnknownError),
//      Catalogs(Core, PSD2, OBWG),
//      List(apiTagBerlinGroup, apiTagAccount, apiTagPrivateData))
//  
//    lazy val getAccountBalances : OBPEndpoint = {
//      //get private accounts for all banks
//      case "accounts" :: AccountId(accountId) :: "balances" :: Nil JsonGet _ => {
//        cc =>
//          for {
//            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
//            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) { defaultBankId != "DEFAULT_BANK_ID_NOT_SET" }
//            (_, callContext) <- NewStyle.function.getBank(BankId(defaultBankId), callContext)
//            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(BankId(defaultBankId), accountId, callContext)
//            view <- NewStyle.function.view(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)
//            _ <- Helper.booleanToFuture(failMsg = s"${UserNoPermissionAccessView} Current VIEW_ID (${view.viewId.value})") {(u.hasViewAccess(view))}
//            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
//              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
//            } map { unboxFull(_) }
//            moderatedAccount <- Future {bankAccount.moderatedBankAccount(view, Full(u))} map {
//              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
//            } map { unboxFull(_) }
//          } yield {
//            (JSONFactory_BERLIN_GROUP_1_3.createAccountBalanceJSON(moderatedAccount, transactionRequests), HttpCode.`200`(callContext))
//          }
//      }
//    }
  
//    resourceDocs += ResourceDoc(
//      getTransactionList,
//      implementedInApiVersion,
//      "getTransactionList",
//      "GET",
//      "/accounts/ACCOUNT_ID/transactions",
//      "Read transaction list of an account",
//      s"""
//        |Reads account data from a given account addressed by “account-id”. 
//        |${authenticationRequiredMessage(true)}
//        |
//        |This endpoint is work in progress. Experimental!
//        |""",
//      emptyObjectJson,
//      SwaggerDefinitionsJSON.transactionsJsonV1,
//      List(UserNotLoggedIn,UnknownError),
//      Catalogs(Core, PSD2, OBWG),
//      List(apiTagBerlinGroup, apiTagTransaction, apiTagPrivateData))
//  
//    lazy val getTransactionList : OBPEndpoint = {
//      //get private accounts for all banks
//      case "accounts" :: AccountId(accountId) :: "transactions" :: Nil JsonGet _ => {
//        cc =>
//          for {
//            
//            (Full(u), callContext) <- authorizeEndpoint(UserNotLoggedIn, cc)
//            
//            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
//            
//            bankId = BankId(defaultBankId)
//            
//            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
//            
//            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
//            
//            view <- NewStyle.function.view(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext) 
//            
//            params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
//              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
//            } map { unboxFull(_) }
//          
//            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
//              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
//            } map { unboxFull(_) }
//
//            (transactions, callContext) <- Future { bankAccount.getModeratedTransactions(Full(u), view, callContext, params: _*)} map {
//              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
//            } map { unboxFull(_) }
//            
//            } yield {
//              (JSONFactory_BERLIN_GROUP_1_3.createTransactionsJson(transactions, transactionRequests), callContext)
//            }
//      }
//    }
  }
}


object APIMethods_BERLIN_GROUP_1_3 {
}
