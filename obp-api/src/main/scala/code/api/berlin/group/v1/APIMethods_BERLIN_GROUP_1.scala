package code.api.berlin.group.v1

import code.api.APIFailureNewStyle
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.berlin.group.v1.JSONFactory_BERLIN_GROUP_1.{Balances, CoreAccountJsonV1, CoreAccountsJsonV1, Transactions}
import code.api.util.APIUtil.{defaultBankId, _}
import code.api.util.{NewStyle}
import code.api.util.ErrorMessages._
import code.api.util.ApiTag._
import code.api.util.NewStyle.HttpCode
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

object APIMethods_BERLIN_GROUP_1 extends RestHelper{
    val implementedInApiVersion = OBP_BERLIN_GROUP_1.apiVersion

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
      CoreAccountsJsonV1(List(CoreAccountJsonV1(
        id = "3dc3d5b3-7023-4848-9853-f5400a64e80f",
        iban = "DE2310010010123456789",
        currency = "EUR",
        accountType = "Girokonto",
        cashAccountType = "CurrentAccount",
        _links = List(
          Balances("/v1/accounts/3dc3d5b3-7023-4848-9853-f5400a64e80f/balances"),
          Transactions("/v1/accounts/3dc3d5b3-7023-4848-9853-f5400a64e80f/transactions")
        ),
        name = "Main Account"
      ))),
      List(UserNotLoggedIn,UnknownError),
      List(apiTagBerlinGroup, apiTagAccount, apiTagPrivateData, apiTagPsd2))


    apiRelations += ApiRelation(getAccountList, getAccountList, "self")



    lazy val getAccountList : OBPEndpoint = {
      //get private accounts for one bank
      case "accounts" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
  
            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
  
            bankId = BankId(defaultBankId)
  
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
  
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
            
            Full((coreAccounts,callContext1)) <- {Connector.connector.vend.getCoreBankAccounts(availablePrivateAccounts, callContext)}
            
          } yield {
            (JSONFactory_BERLIN_GROUP_1.createTransactionListJSON(coreAccounts), callContext)
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
      List(apiTagBerlinGroup, apiTagAccount, apiTagPrivateData, apiTagPsd2))
  
    lazy val getAccountBalances : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: AccountId(accountId) :: "balances" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) { defaultBankId != "DEFAULT_BANK_ID_NOT_SET" }
            (_, callContext) <- NewStyle.function.getBank(BankId(defaultBankId), callContext)
            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(BankId(defaultBankId), accountId, callContext)
            view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)
            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            moderatedAccount <- Future {bankAccount.moderatedBankAccount(view, BankIdAccountId(bankAccount.bankId, accountId), Full(u), callContext)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          } yield {
            (JSONFactory_BERLIN_GROUP_1.createAccountBalanceJSON(moderatedAccount, transactionRequests), HttpCode.`200`(callContext))
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
      List(apiTagBerlinGroup, apiTagTransaction, apiTagPrivateData, apiTagPsd2))
  
    lazy val getTransactionList : OBPEndpoint = {
      //get private accounts for all banks
      case "accounts" :: AccountId(accountId) :: "transactions" :: Nil JsonGet _ => {
        cc =>
          for {
            
            (Full(u), callContext) <- authenticatedAccess(cc)
            
            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
            
            bankId = BankId(defaultBankId)
            
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            
            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
            
            view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(u, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext) 
            
            params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          
            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            (transactions, callContext) <- Future { bankAccount.getModeratedTransactions(bank, Full(u), view, BankIdAccountId(bankId, accountId), callContext, params)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
            
            } yield {
              (JSONFactory_BERLIN_GROUP_1.createTransactionsJson(transactions, transactionRequests), callContext)
            }
      }
    }

}