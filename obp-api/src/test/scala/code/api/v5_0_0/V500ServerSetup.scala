package code.api.v5_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth.{Consumer, Token}
import code.api.util.ApiRole.CanCreateCustomer
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanCreateCustomer
import code.api.v3_1_0.CustomerJsonV310
import code.api.v4_0_0.BanksJson400
import code.entitlement.Entitlement
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData}
import com.openbankproject.commons.util.ApiShortVersions
import dispatch.Req
import code.api.util.APIUtil.OAuth._
import code.api.v2_0_0.BasicAccountsJSON
import net.liftweb.json.Serialization.write

import scala.util.Random.nextInt

trait V500ServerSetup extends ServerSetupWithTestData with DefaultUsers {

  def v5_0_0_Request: Req = baseRequest / "obp" / "v5.0.0"
  def dynamicEndpoint_Request: Req = baseRequest / "obp" / ApiShortVersions.`dynamic-endpoint`.toString
  def dynamicEntity_Request: Req = baseRequest / "obp" / ApiShortVersions.`dynamic-entity`.toString

  def randomBankId : String = {
    def getBanksInfo : APIResponse  = {
      val request = v5_0_0_Request / "banks"
      makeGetRequest(request)
    }
    val banksJson = getBanksInfo.body.extract[BanksJson400]
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }

  def getPrivateAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v5_0_0_Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken) //TODO, how can we know which endpoint it called? Although it is V300, but this endpoint called V200-privateAccountsAtOneBank
    makeGetRequest(request)
  }
  def randomPrivateAccountId(bankId : String) : String = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[BasicAccountsJSON].accounts //TODO, how to make this map automatically.
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition).id
  }

  // This will call create customer ,then return the customerId
  def createAndGetCustomerIdViaEndpoint(bankId:String, consumerAndToken: Option[(Consumer, Token)]) = {
    val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310
    def createCustomer(consumerAndToken: Option[(Consumer, Token)]) ={
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
      val request310 = (v5_0_0_Request / "banks" / bankId / "customers").POST <@(user1)
      val response310 = makePostRequest(request310, write(postCustomerJson))
      response310.code should equal(201)
      response310.body.extract[CustomerJsonV310]
    }
    createCustomer(consumerAndToken).customer_id
  }

  
  def createCustomerEndpointV500(bankId: String, legalName: String, mobilePhoneNumber: String): CustomerJsonV310 = {
    Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateCustomer.toString)
    val request = (v5_0_0_Request / "banks" / bankId / "customers").POST <@(user1)
    val response = makePostRequest(request, write(PostCustomerJsonV500(legal_name = legalName,mobile_phone_number = mobilePhoneNumber)))
    Then("We should get a 201")
    response.code should equal(201)
    response.body.extract[CustomerJsonV310]
  }
  
}