package code.api.UKOpenBanking.v2_0_0

import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{InvalidConnectorResponseForGetTransactionRequests210, UnknownError, UserNotLoggedIn, _}
import com.openbankproject.commons.util.ApiVersion
import code.api.util.{ ErrorMessages, NewStyle}
import code.bankconnectors.Connector
import code.model._
import code.util.Helper
import code.views.Views
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, ViewId}
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

object APIMethods_UKOpenBanking_200 extends RestHelper{

    val implementedInApiVersion = OBP_UKOpenBanking_200.apiVersion

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)

    resourceDocs += ResourceDoc(
      getAccountList,
      implementedInApiVersion,
      "getAccountList",
      "GET",
      "/accounts",
      "UK Open Banking: Get Account List",
      s"""
         |Reads a list of bank accounts, with balances where required.
         |It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
         |
        |${authenticationRequiredMessage(true)}
        |
        |This call is work in progress - Experimental!
         |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.accountsJsonUKOpenBanking_v200,
      List(ErrorMessages.UserNotLoggedIn,ErrorMessages.UnknownError),
      List(apiTagUKOpenBanking, apiTagAccount, apiTagPrivateData))

    apiRelations += ApiRelation(getAccountList, getAccountList, "self")

    lazy val getAccountList : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
            (accounts, callContext)<- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
          } yield {
            (JSONFactory_UKOpenBanking_200.createAccountsListJSON(accounts), callContext)
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      getAccountTransactions,
      implementedInApiVersion,
      "getAccountTransactions",
      "GET",
      "/accounts/ACCOUNT_ID/transactions",
      "UK Open Banking: Get Account Transactions",
      s"""
         |Reads account data from a given account addressed by “account-id”. 
         |${authenticationRequiredMessage(true)}
         |
         |This call is work in progress - Experimental!
         |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.transactionsJsonUKV200,
      List(UserNotLoggedIn,UnknownError),
      List(apiTagUKOpenBanking, apiTagTransaction, apiTagPrivateData, apiTagPsd2))
  
    lazy val getAccountTransactions : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: AccountId(accountId) :: "transactions" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (bank, callContext) <- NewStyle.function.getBank(BankId(defaultBankId), callContext)
            (bankAccount, callContext) <- Future { BankAccountX(BankId(defaultBankId), accountId, callContext) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(DefaultBankIdNotSet, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)
            params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          
            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          
            (transactions, callContext) <- Future { bankAccount.getModeratedTransactions(bank, Full(u), view, BankIdAccountId(BankId(defaultBankId), accountId), callContext, params)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          
          } yield {
            (JSONFactory_UKOpenBanking_200.createTransactionsJson(transactions, transactionRequests), callContext)
          }
      }
    }


    resourceDocs += ResourceDoc(
      getAccount,
      implementedInApiVersion,
      "getAccount",
      "GET",
      "/accounts/ACCOUNT_ID",
      "UK Open Banking: Get Account",
      s"""
         |Reads a bank account, with balances where required.
         |It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
         |
        |${authenticationRequiredMessage(true)}
        |
        |This call is work in progress - Experimental!
         |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.accountsJsonUKOpenBanking_v200,
      List(ErrorMessages.UserNotLoggedIn,ErrorMessages.UnknownError),
      List(apiTagUKOpenBanking, apiTagAccount, apiTagPrivateData))

    apiRelations += ApiRelation(getAccount, getAccount, "self")

    lazy val getAccount : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: AccountId(accountId) :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u) map {
              _.filter(_.accountId.value == accountId.value)
            }
            (accounts, callContext)<- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
          } yield {
            (JSONFactory_UKOpenBanking_200.createAccountJSON(accounts), callContext)
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      getAccountBalances,
      implementedInApiVersion,
      "getAccountBalances",
      "GET",
      "/accounts/ACCOUNT_ID/balances",
      "UK Open Banking: Get Account Balances",
      s"""
         |An AISP may retrieve the account balance information resource for a specific AccountId 
         |(which is retrieved in the call to GET /accounts).
         |
         |${authenticationRequiredMessage(true)}
         |
         |This call is work in progress - Experimental!
         |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.accountBalancesUKV200,
      List(ErrorMessages.UserNotLoggedIn,ErrorMessages.UnknownError),
      List(apiTagUKOpenBanking, apiTagAccount, apiTagPrivateData))
  
    lazy val getAccountBalances : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: AccountId(accountId) :: "balances" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)

            (account, callContext) <- Future { BankAccountX(BankId(defaultBankId), accountId, callContext) } map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(DefaultBankIdNotSet, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(account.bankId, account.accountId), callContext)
        
            moderatedAccount <- Future {account.moderatedBankAccount(view, BankIdAccountId(account.bankId, account.accountId), Full(u), callContext)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
        
          } yield {
            (JSONFactory_UKOpenBanking_200.createAccountBalanceJSON(moderatedAccount), callContext)
          }
      }
    }
  
    resourceDocs += ResourceDoc(
      getBalances,
      implementedInApiVersion,
      "getBalances",
      "GET",
      "/balances",
      "UK Open Banking: Get Balances",
      s"""
         |
         |If an ASPSP has implemented the bulk retrieval endpoints - 
         |an AISP may optionally retrieve the account information resources in bulk.
         |This will retrieve the resources for all authorised accounts linked to the account-request.
         |
         |${authenticationRequiredMessage(true)}
         |
         |This call is work in progress - Experimental!
         |""",
      emptyObjectJson,
      SwaggerDefinitionsJSON.accountBalancesUKV200,
      List(ErrorMessages.UserNotLoggedIn,ErrorMessages.UnknownError),
      List(apiTagUKOpenBanking, apiTagAccount, apiTagPrivateData))
  
    lazy val getBalances : OBPEndpoint = {
      //get private accounts for all banks
      case "balances" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)

            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
          
            (accounts, callContext)<- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
          
          } yield {
            (JSONFactory_UKOpenBanking_200.createBalancesJSON(accounts), callContext)
          }
      }
    }
}



