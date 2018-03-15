package code.api.v2_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.postPhysicalCardJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.CanCreateCardsForBank
import code.api.v1_3_0.PhysicalCardJSON
import code.model.AccountId
import code.setup.DefaultUsers
import net.liftweb.json.Serialization.write

class CreateCreditCardTest extends V210ServerSetup with DefaultUsers {

  override def beforeAll() {
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }

  feature("Assuring that endpoint 'Create Credit Card' works as expected - v2.1.0") {
    scenario("Create Credit Card successfully ") {
      Given("The Bank_ID")
      val testBank = createBank("testBankId")
      val bankId = testBank.bankId
      val accountId = AccountId("__acc1")
      createAccountAndOwnerView(Some(resourceUser1), bankId, accountId, "EUR")
      val physicalCardJSON = postPhysicalCardJSON.copy(account_id = accountId.value)

      Then("We add entitlement to user1")
      addEntitlement(bankId.value, resourceUser1.userId, CanCreateCardsForBank.toString)
      val hasEntitlement = code.api.util.APIUtil.hasEntitlement(bankId.value, resourceUser1.userId, ApiRole.canCreateCardsForBank)
      hasEntitlement should equal(true)

      When("We make the request Create Credit Card")
      val requestPost = (v2_1Request / "banks" / bankId.value / "cards" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(physicalCardJSON))
      Then("We should get a 200 and check all the fields")
      responsePost.code should equal(200)

      val responseJson: PhysicalCardJSON = responsePost.body.extract[PhysicalCardJSON]
      responseJson.bank_id should equal(bankId.value)
      responseJson.bank_card_number should equal(physicalCardJSON.bank_card_number)
      responseJson.name_on_card should equal(physicalCardJSON.name_on_card)
      responseJson.issue_number should equal(physicalCardJSON.issue_number)
      responseJson.serial_number should equal(physicalCardJSON.serial_number)
      responseJson.valid_from_date should equal(physicalCardJSON.valid_from_date)
      responseJson.expires_date should equal(physicalCardJSON.expires_date)
      responseJson.enabled should equal(physicalCardJSON.enabled)
      responseJson.cancelled should equal(false)
      responseJson.on_hot_list should equal(false)
      responseJson.technology should equal(physicalCardJSON.technology)
      responseJson.networks should equal(physicalCardJSON.networks)
      responseJson.allows should equal(physicalCardJSON.allows)
      responseJson.account.id should equal(physicalCardJSON.account_id)
      responseJson.replacement should equal(physicalCardJSON.replacement)
      responseJson.collected should equal(physicalCardJSON.collected)
      responseJson.posted should equal(physicalCardJSON.posted)
      responseJson.pin_reset should equal(physicalCardJSON.pin_reset)
    }

  }


}
