package code.api.v3_0_0

import code.api.util.APIUtil.OAuth.{Consumer, Token, _}
import code.api.v1_2_1.{AccountJSON, AccountsJSON, BanksJSON, ViewsJSONV121}
import code.api.v2_0_0.BasicAccountsJSON
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData, User1AllPrivileges}
import dispatch.Req

import scala.util.Random.nextInt

/**
 * Created by Hongwei Zhang on 05/05/17.
 */
trait V300ServerSetup extends ServerSetupWithTestData with User1AllPrivileges with DefaultUsers {

  def v3_0Request: Req = baseRequest / "obp" / "v3.0.0"
  
  
  //When new version, this would be the first endpoint to test, to make sure it works well. 
  def getAPIInfo : APIResponse = {
    val request = v3_0Request
    makeGetRequest(request)
  }
  // When Test the endpoints, we need some Ids and some roles to test it.
  //1 roles: we used the super users: <@(consumerAndToken)
  //2 Ids: we random get it by helper Methods.
  
  
  //If you want to test the apis, you always need some ids first: Such as the BankId, AccountId, Transactionid....
  //Here is the helper method to get these Ids ....
  //TODO, maybe these can be moved to top level 
  def randomBankId : String = {
  
    def getBanksInfo : APIResponse  = {
      val request = v3_0Request / "banks" //TODO, how can we know which endpoint it called? Although it is V300, but this endpoint called V121-getBanks
      makeGetRequest(request)
    }
    
    val banksJson = getBanksInfo.body.extract[BanksJSON] //TODO, how to make this map automatically.
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }

  def getPrivateAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v3_0Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken) //TODO, how can we know which endpoint it called? Although it is V300, but this endpoint called V200-privateAccountsAtOneBank
    makeGetRequest(request)
  }

  def randomPrivateAccountId(bankId : String) : String = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[BasicAccountsJSON].accounts //TODO, how to make this map automatically.
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition).id
  }

  def randomPrivateAccount(bankId : String): AccountJSON = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[AccountsJSON].accounts //TODO, how to make this map automatically.
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition)
  }

  def randomViewPermalink(bankId: String, account: AccountJSON) : String = {
    val request = v3_0Request / "banks" / bankId / "accounts" / account.id / "views" <@(consumer, token1)
    val reply = makeGetRequest(request)
    val possibleViewsPermalinks = reply.body.extract[ViewsJSONV121].views.filterNot(_.is_public==true)
    val randomPosition = nextInt(possibleViewsPermalinks.size)
    possibleViewsPermalinks(randomPosition).id
  }

  def getTransactions(bankId : String, accountId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)], params: List[(String, String)] = Nil): APIResponse = {
    val request = v3_0Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" <@(consumerAndToken)
    makeGetRequest(request, params)
  }
  
  /**
    * Get Transactions for Account (Full)-- V300
    */
  def getTransactionsForAccountFull(bankId:String, accountId:String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v3_0Request / "banks" / bankId / "accounts" / accountId / "owner" / "transactions").GET <@ (user1)
    makeGetRequest(request)
  }
  
  /**
    * Get Transactions for Account (Core) -- V300
    */
  def getTransactionsForAccountCore(bankId:String, accountId:String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v3_0Request / "my"/"banks" / bankId / "accounts" / accountId / "transactions").GET <@ (user1)
    makeGetRequest(request)
  }
  
  
  //"getOtherAccountsForBankAccount",
  //"GET",
  //"/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts",
  //"Get Other Accounts of one Account.",
  def getOtherAccountsForBankAccount(bankId:String, accountId:String, viewId:String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = v3_0Request / "banks" /bankId/"accounts"/accountId/viewId/"other_accounts" <@ (consumerAndToken)
    makeGetRequest(request)
  }
  
  //"getOtherAccountByIdForBankAccount",
  //"GET",
  //"/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID",
  //"Get Other Account by Id.",
  def getOtherAccountByIdForBankAccount(bankId:String, accountId:String, viewId:String, otherAccountId:String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = v3_0Request / "banks" /bankId/"accounts"/accountId/viewId/"other_accounts"/otherAccountId <@ (consumerAndToken)
    makeGetRequest(request)
  }
  
}