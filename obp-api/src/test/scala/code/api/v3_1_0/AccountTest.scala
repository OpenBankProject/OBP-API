package code.api.v3_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.updateAccountRequestJsonV310
import code.setup.DefaultUsers
import net.liftweb.json.Serialization.write
import code.api.util.APIUtil.OAuth._
import code.api.util.{ApiRole, ApiVersion}
import code.api.v3_0_0.ModeratedCoreAccountJsonV300
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.Tag

class AccountTest extends V310ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpoint extends Tag(nameOf(Implementations3_1_0.updateAccount))
  
  
  feature("test Update Account") {
    scenario("We will test Update Account Api", ApiEndpoint, VersionOfApi) {
      Given("The test bank and test account")
      val testBank = testBankId1
      val testAccount = testAccountId1
      val testPutJson = updateAccountRequestJsonV310
      
      Then(s"We call the update api without proper role:  ${ApiRole.canUpdateAccount}")
      val requestPut = (v3_1_0_Request / "management" / "banks" / testBank.value / "accounts" / testAccount.value).PUT <@ (user1)
      val responsePut = makePutRequest(requestPut, write(testPutJson))
      And("We should get  403 and the error message missing can CanUpdateAccount role")
      responsePut.code should equal(403)
      responsePut.body.toString contains("OBP-20006: User is missing one or more roles: CanUpdateAccount")


      Then(s"We grant the user ${ApiRole.canUpdateAccount} role")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canUpdateAccount.toString)
      val responsePut2 = makePutRequest(requestPut, write(testPutJson))
      And("We should get 200 and updated account data")
      responsePut2.code should equal(200)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].`type` should be (testPutJson.`type`)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].branch_id should be (testPutJson.branch_id)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].label should be (testPutJson.label)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].account_routing.scheme should be (testPutJson.account_routing.scheme)
      responsePut2.body.extract[UpdateAccountResponseJsonV310].account_routing.address should be (testPutJson.account_routing.address)


      val requestGet = (v3_1_0_Request /"my"/ "banks" / testBank.value / "accounts" / testAccount.value/"account").PUT <@ (user1)
      val responseGet = makeGetRequest(requestGet)
      And("We should get 200 and updated account data")
      responseGet.code should equal(200)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].`type` should be (testPutJson.`type`)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].label should be (testPutJson.label)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].account_routings.head.scheme should be (testPutJson.account_routing.scheme)
      responseGet.body.extract[ModeratedCoreAccountJsonV300].account_routings.head.address should be (testPutJson.account_routing.address)
      
    }
  }

} 
