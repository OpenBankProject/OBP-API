package code.api.UKOpenBanking.v2_0_0

import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil._
import code.api.util.ErrorMessages.{BankAccountNotFound, InvalidConnectorResponseForGetTransactionRequests210, UnknownError, UserNotLoggedIn, ViewNotFound}
import code.api.util.{ApiVersion, ErrorMessages}
import code.bankconnectors.Connector
import code.model._
import code.model.AccountId
import code.views.Views
import net.liftweb.http.rest.RestHelper

import scala.collection.immutable.Nil

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait APIMethods_UKOpenBanking_200 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val ImplementationsUKOpenBanking200 = new Object() {
    val implementedInApiVersion: ApiVersion = ApiVersion.ukOpenBankingV200 // was noV

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)

    resourceDocs += ResourceDoc(
      readAccountList,
      implementedInApiVersion,
      "readAccountList",
      "GET",
      "/accounts",
      "Experimental! - UK Open Banking: Read Account List",
      s"""
         |Reads a list of bank accounts, with balances where required.
         |It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
         |
        |${authenticationRequiredMessage(true)}
         |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.accountsJsonUKOpenBanking_v200,
      List(ErrorMessages.UserNotLoggedIn,ErrorMessages.UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUKOpenBanking, apiTagAccount, apiTagPrivateData))

    apiRelations += ApiRelation(readAccountList, readAccountList, "self")

    lazy val readAccountList : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: Nil JsonGet _ => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(ErrorMessages.UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
            accounts <- {Connector.connector.vend.getBankAccountsFuture(availablePrivateAccounts, callContext)}
          } yield {
            (JSONFactory_UKOpenBanking_200.createAccountsListJSON(accounts.getOrElse(Nil)), callContext)
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      getAccountTransactions,
      implementedInApiVersion,
      "getAccountTransactions",
      "GET",
      "/accounts/ACCOUNT_ID/transactions",
      "Experimental - UK Open Banking: Get Account Transactions",
      s"""
         |Reads account data from a given account addressed by “account-id”. 
         |${authenticationRequiredMessage(true)}
         |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.branchJsonV300,
      List(UserNotLoggedIn,UnknownError),
      Catalogs(Core, PSD2, OBWG),
      List(apiTagBerlinGroup, apiTagAccount, apiTagPrivateData))
  
    lazy val getAccountTransactions : OBPEndpoint = {
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
            (JSONFactory_UKOpenBanking_200.createTransactionsJson(transactions, transactionRequests), callContext)
          }
      }
    }


    resourceDocs += ResourceDoc(
      readAccount,
      implementedInApiVersion,
      "readAccount",
      "GET",
      "/accounts/ACCOUNT_ID",
      "Experimental! - UK Open Banking: Read Account",
      s"""
         |Reads a bank account, with balances where required.
         |It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
         |
        |${authenticationRequiredMessage(true)}
         |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.accountsJsonUKOpenBanking_v200,
      List(ErrorMessages.UserNotLoggedIn,ErrorMessages.UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagUKOpenBanking, apiTagAccount, apiTagPrivateData))

    apiRelations += ApiRelation(readAccount, readAccount, "self")

    lazy val readAccount : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: AccountId(accountId) :: Nil JsonGet _ => {
        cc =>
          for {
            (user, callContext) <- extractCallContext(ErrorMessages.UserNotLoggedIn, cc)
            u <- unboxFullAndWrapIntoFuture{ user }
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u) map {
              _.filter(_.accountId.value == accountId.value)
            }
            accounts <- {Connector.connector.vend.getBankAccountsFuture(availablePrivateAccounts, callContext)}
          } yield {
            (JSONFactory_UKOpenBanking_200.createAccountJSON(accounts.getOrElse(Nil)), callContext)
          }
      }
    }


  }

}



