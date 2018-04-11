package code.api.berlin.group.v1

import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil._
import code.api.util.ApiVersion
import code.api.util.ErrorMessages.{BankAccountNotFound, UnknownError, UserNoPermissionAccessView, UserNotLoggedIn, ViewNotFound}
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
    val implementedInApiVersion: ApiVersion = ApiVersion.berlinGroupV1 // was noV

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)


    resourceDocs += ResourceDoc(
      readAccountList,
      implementedInApiVersion,
      "readAccountList",
      "GET",
      "/accounts",
      "Experimental - BG Read Account List",
      s"""
         |Reads a list of bank accounts, with balances where required.
         |It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
         |
        |${authenticationRequiredMessage(true)}
         |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.coreAccountsJsonV1,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagBerlinGroup, apiTagAccount, apiTagPrivateData))


    apiRelations += ApiRelation(readAccountList, readAccountList, "self")



    lazy val readAccountList : OBPEndpoint = {
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
      readBalance,
      implementedInApiVersion,
      "readBalance",
      "GET",
      "/accounts/ACCOUNT_ID/balances",
      "Experimental - BG Read Balance",
      s"""
        |Reads account data from a given account addressed by “account-id”.
        |
        |${authenticationRequiredMessage(true)}
        |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.accountBalances,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagBerlinGroup, apiTagAccount, apiTagPrivateData))
  
    lazy val readBalance : OBPEndpoint = {
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
          
            _ <- Helper.booleanToFuture(failMsg = UserNoPermissionAccessView) {(u.hasViewAccess(view))}
          
            moderatedAccount <- Future {account.moderatedBankAccount(view, user)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(ViewNotFound, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
            
          } yield {
            (JSONFactory_BERLIN_GROUP_1.createAccountBalanceJSON(moderatedAccount), callContext)
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      readTransactionList,
      implementedInApiVersion,
      "readTransactionList",
      "GET",
      "/accounts/ACCOUNT_ID/transactions",
      "Experimental - BG Read Account Transactions",
      s"""
        |Reads account data from a given account addressed by “account-id”. 
        |${authenticationRequiredMessage(true)}
        |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.transactionsJsonV1,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagBerlinGroup, apiTagAccount, apiTagPrivateData))
  
    lazy val readTransactionList : OBPEndpoint = {
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
              x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
          
            transactionRequests <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(BankAccountNotFound, 400, Some(cc.toLight)))
            } map { unboxFull(_) }
            } yield {
              //TODO here, we map the `transactionRequests` to the Berlin Group `transactions`. 
              (JSONFactory_BERLIN_GROUP_1.createTransactionsJson(transactionRequests), callContext)
            }
      }
    }
  }

}


object APIMethods_BERLIN_GROUP_1 {
}
