package code.api.v2_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.postPhysicalCardJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.{APIUtil, ApiRole}
import code.api.util.ApiRole.CanCreateCardsForBank
import code.api.v1_3_0.PhysicalCardJSON
import code.setup.DefaultUsers
import net.liftweb.json.Serialization.write
import code.api.util.ErrorMessages._

class CreateCreditCardTest extends V210ServerSetup with DefaultUsers {

  feature("Assuring that endpoint 'Create Credit Card' works as expected - v2.1.0") {
    scenario("Create Credit Card successfully ") {
      Given("The Bank_ID")
      val bankId = testBankId1
      val accountId = testAccountId1
      val physicalCardWrongIssueNumberJson = postPhysicalCardJSON.copy(account_id = accountId.value, issue_number = "12313123123")
      val physicalCardJson = postPhysicalCardJSON.copy(account_id = accountId.value, issue_number = "123")

      Then("We add entitlement to user1")
      addEntitlement(bankId.value, resourceUser1.userId, CanCreateCardsForBank.toString)
      val hasEntitlement = APIUtil.hasEntitlement(bankId.value, resourceUser1.userId, ApiRole.canCreateCardsForBank)
      hasEntitlement should equal(true)

      When("We make the request Create Credit Card")
      val requestPost = (v2_1Request / "banks" / bankId.value / "cards" ).POST <@ (user1)
      val responsePostWrong = makePostRequest(requestPost, write(physicalCardWrongIssueNumberJson))
      Then("We should get a 400 and check all the fields")
      responsePostWrong.code should equal(400)
      responsePostWrong.body.toString contains (maximumLimitExceeded.replace("10000", "10")) should be (true)


      val responsePost = makePostRequest(requestPost, write(physicalCardJson))
      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)
      val responseJson: PhysicalCardJSON = responsePost.body.extract[PhysicalCardJSON]
      responseJson.bank_id should equal(bankId.value)
      responseJson.bank_card_number should equal(physicalCardJson.bank_card_number)
      responseJson.name_on_card should equal(physicalCardJson.name_on_card)
      responseJson.issue_number should equal(physicalCardJson.issue_number)
      responseJson.serial_number should equal(physicalCardJson.serial_number)
      responseJson.valid_from_date should equal(physicalCardJson.valid_from_date)
      responseJson.expires_date should equal(physicalCardJson.expires_date)
      responseJson.enabled should equal(physicalCardJson.enabled)
      responseJson.cancelled should equal(false)
      responseJson.on_hot_list should equal(false)
      responseJson.technology should equal(physicalCardJson.technology)
      responseJson.networks should equal(physicalCardJson.networks)
      responseJson.allows should equal(physicalCardJson.allows)
      responseJson.account.id should equal(physicalCardJson.account_id)
      responseJson.replacement should equal(physicalCardJson.replacement)
      responseJson.collected should equal(physicalCardJson.collected)
      responseJson.posted should equal(physicalCardJson.posted)
      responseJson.pin_reset.toString() should equal(physicalCardJson.pin_reset.toString())
    }

  }


}
