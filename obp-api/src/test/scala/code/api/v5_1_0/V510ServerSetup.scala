package code.api.v5_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth.{Consumer, Token, _}
import code.api.util.ApiRole
import code.api.v2_0_0.BasicAccountsJSON
import code.api.v4_0_0.{AtmJsonV400, BanksJson400}
import code.entitlement.Entitlement
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData}
import com.openbankproject.commons.util.ApiShortVersions
import dispatch.Req
import net.liftweb.json.Serialization.write

import scala.util.Random.nextInt

trait V510ServerSetup extends ServerSetupWithTestData with DefaultUsers {

  def v4_0_0_Request: Req = baseRequest / "obp" / "v4.0.0"
  def v5_0_0_Request: Req = baseRequest / "obp" / "v5.0.0"
  def v5_1_0_Request: Req = baseRequest / "obp" / "v5.1.0"
  def dynamicEndpoint_Request: Req = baseRequest / "obp" / ApiShortVersions.`dynamic-endpoint`.toString
  def dynamicEntity_Request: Req = baseRequest / "obp" / ApiShortVersions.`dynamic-entity`.toString

  def randomBankId : String = {
    def getBanksInfo : APIResponse  = {
      val request = v5_1_0_Request / "banks"
      makeGetRequest(request)
    }
    val banksJson = getBanksInfo.body.extract[BanksJson400]
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }
  
  def createAtmAtBank(bankId: String): AtmJsonV400 = {
    val postAtmJson = SwaggerDefinitionsJSON.atmJsonV400.copy(bank_id = bankId)
    val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
    val requestCreateAtm = (v4_0_0_Request / "banks" / bankId / "atms").POST <@ (user1)
    val responseCreateAtm = makePostRequest(requestCreateAtm, write(postAtmJson))
    val responseBodyCreateAtm = responseCreateAtm.body.extract[AtmJsonV400]
    responseBodyCreateAtm should be (postAtmJson)
    responseCreateAtm.code should be (201)
    Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    responseBodyCreateAtm
  }

  def getPrivateAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v5_1_0_Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken) //TODO, how can we know which endpoint it called? Although it is V300, but this endpoint called V200-privateAccountsAtOneBank
    makeGetRequest(request)
  }
  def randomPrivateAccountId(bankId : String) : String = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[BasicAccountsJSON].accounts //TODO, how to make this map automatically.
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition).id
  }
  
}