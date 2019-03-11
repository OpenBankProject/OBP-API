package code.api.v2_2_0

import code.api.util.APIUtil.OAuth._
import code.api.v1_2_1.BanksJSON
import code.api.v2_0_0.BasicAccountsJSON
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData, User1AllPrivileges}

import scala.util.Random.nextInt

/**
 * Created by markom on 10/14/16.
 */
trait V220ServerSetup extends ServerSetupWithTestData with User1AllPrivileges with DefaultUsers{

  def v1_2_1Request = baseRequest / "obp" / "v1.2.1"
  def v1_4Request = baseRequest / "obp" / "v1.4.0"
  def v2_0Request = baseRequest / "obp" / "v2.0.0"
  def v2_1Request = baseRequest / "obp" / "v2.1.0"
  def v2_2Request = baseRequest / "obp" / "v2.2.0"
  
  //When new version, this would be the first endpoint to test, to make sure it works well. 
  def getAPIInfo : APIResponse = {
    val request = v2_2Request
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
      val request = v2_2Request / "banks" //TODO, how can we know which endpoint it called? Although it is V220, but this endpoint called V121-getBanks
      makeGetRequest(request)
    }
    
    val banksJson = getBanksInfo.body.extract[BanksJSON] //TODO, how to make this map automatically.
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }
  
  
  def randomPrivateAccountId(bankId : String) : String = {
    
    def getPrivateAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
      val request = v2_2Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken) //TODO, how can we know which endpoint it called? Although it is V300, but this endpoint called V200-privateAccountsAtOneBank
      makeGetRequest(request)
    }
    
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[BasicAccountsJSON].accounts //TODO, how to make this map automatically. 
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition).id
    
  }
}