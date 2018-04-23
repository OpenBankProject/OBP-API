package code.api.berlin.group.v1

import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil._
import code.api.util.ApiVersion
import code.api.util.ErrorMessages._
import code.bankconnectors.Connector
import code.model._
import code.util.Helper
import code.views.Views
import net.liftweb.http.rest.RestHelper

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait APIMethods_BERLIN_GROUP_1 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations1 = new Object() {
    val implementedInApiVersion: ApiVersion = ApiVersion.berlinGroupV1

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)


    resourceDocs += ResourceDoc(
      getAccountList,
      implementedInApiVersion,
      "getAccountList",
      "GET",
      "/accounts",
      "Berlin Group: Read Account List",
      s"""
         |Reads a list of bank accounts, with balances where required.
         |It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
         |
        |${authenticationRequiredMessage(true)}
        |
        |This endpoint is work in progress. Experimental!
         |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.coreAccountsJsonV1,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagBerlinGroup, apiTagAccount, apiTagPrivateData))


    apiRelations += ApiRelation(getAccountList, getAccountList, "self")



    lazy val getAccountList : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: Nil JsonGet _ => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
            coreAccounts <- {Connector.connector.vend.getCoreBankAccountsFuture(availablePrivateAccounts, callContext)}
          } yield {
            (JSONFactory_BERLIN_GROUP_1.createTransactionListJSON(coreAccounts.getOrElse(Nil)), callContext)
          }
      }
    }

    resourceDocs += ResourceDoc(
      getAccountBalances,
      implementedInApiVersion,
      "getAccountBalances",
      "GET",
      "/accounts/ACCOUNT_ID/balances",
      "Berlin Group Read Balance",
      s"""
        |Reads account data from a given account addressed by “account-id”.
        |
        |${authenticationRequiredMessage(true)}
        |
        |This endpoint is work in progress. Experimental!
        |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.accountBalances,
      List(UserNotLoggedIn, ViewNotFound, UserNoPermissionAccessView, UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagBerlinGroup, apiTagAccount, apiTagPrivateData))
  
    lazy val getAccountBalances : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: AccountId(accountId) :: "balances" :: Nil JsonGet _ => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }

            account <- Future { BankAccount(BankId(defaultBankId), accountId, callContext) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
          
            view <- Views.views.vend.viewFuture(ViewId("owner"), BankIdAccountId(account.bankId, account.accountId)) map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(ViewNotFound, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
          
            _ <- Helper.booleanToFuture(failMsg = s"${UserNoPermissionAccessView} Current VIEW_ID (${view.viewId.value})") {(u.hasViewAccess(view))}

            transactionRequests <- Future { Connector.connector.vend.getTransactionRequests210(u, account)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
          
            moderatedAccount <- Future {account.moderatedBankAccount(view, user)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
            
          } yield {
            (JSONFactory_BERLIN_GROUP_1.createAccountBalanceJSON(moderatedAccount, transactionRequests), callContext)
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      getTransactionList,
      implementedInApiVersion,
      "getTransactionList",
      "GET",
      "/accounts/ACCOUNT_ID/transactions",
      "Berlin Group Read Account Transactions",
      s"""
        |Reads account data from a given account addressed by “account-id”. 
        |${authenticationRequiredMessage(true)}
        |
        |This endpoint is work in progress. Experimental!
        |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.transactionsJsonV1,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagBerlinGroup, apiTagTransaction, apiTagPrivateData))
  
    lazy val getTransactionList : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: AccountId(accountId) :: "transactions" :: Nil JsonGet _ => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            bankAccount <- Future { BankAccount(BankId(defaultBankId), accountId, callContext) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
            view <- Views.views.vend.viewFuture(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId)) map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(ViewNotFound, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
            params <- Future { getTransactionParams(callContext.get.requestHeaders)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
          
            transactionRequests <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, Some(cc.toLight)))
            } map { unboxFull(_) }

            transactions <- Future { bankAccount.getModeratedTransactions(user, view, params: _*)(callContext)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
            
            } yield {
              (JSONFactory_BERLIN_GROUP_1.createTransactionsJson(transactions, transactionRequests), callContext)
            }
      }
    }
  }

}


object APIMethods_BERLIN_GROUP_1 {
}
