package code.api.MXOpenFinance_0_0_1

import code.api.util.APIUtil.OAuth.{Consumer, Token, _}
import code.api.v1_2_1.{AccountJSON, AccountsJSON}
import code.api.v4_0_0.BanksJson400
import code.setup.{APIResponse, ServerSetupWithTestData}
import dispatch.Req

import scala.util.Random.nextInt

trait MXOpenFinanceV001ServerSetup extends ServerSetupWithTestData {

  def MXOpenFinance001Request = baseRequest / "mx-open-finance" / "v0.0.1"
  def v4_0_0_Request: Req = baseRequest / "obp" / "v4.0.0"

  def randomBankId : String = {
    def getBanksInfo : APIResponse  = {
      val request = v4_0_0_Request / "banks"
      makeGetRequest(request)
    }
    val banksJson = getBanksInfo.body.extract[BanksJson400]
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }

  def randomPrivateAccountViaEndpoint(bankId : String): AccountJSON = {
    val accountsJson = getPrivateAccountsViaEndpoint(bankId, user1).body.extract[AccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition)
  }

  def getPrivateAccountsViaEndpoint(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v4_0_0_Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken)
    makeGetRequest(request)
  }

}
