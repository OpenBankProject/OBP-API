package code.api.v3_1_0

import java.util.Date

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{createPhysicalCardJsonV310, updatePhysicalCardJsonV310}
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanCreateCustomer
import code.api.util.ApiRole
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import net.liftweb.json.Serialization.write
import org.scalatest.Tag
import code.api.util.ErrorMessages._
import code.api.v1_3_0.ReplacementJSON
import com.openbankproject.commons.model.{CardAction, CardReplacementReason}
import com.openbankproject.commons.util.ApiVersion

class CardTest extends V310ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpointAddCardForBank extends Tag(nameOf(Implementations3_1_0.addCardForBank))
  object ApiEndpointUpdatedCardForBank extends Tag(nameOf(Implementations3_1_0.updatedCardForBank))
  object ApiEndpointGetCardForBank extends Tag(nameOf(Implementations3_1_0.getCardForBank))
  object ApiEndpointGetCardsForBank extends Tag(nameOf(Implementations3_1_0.getCardsForBank))
  object ApiEndpointDeleteCardForBank extends Tag(nameOf(Implementations3_1_0.deleteCardForBank))
  
  
  feature("test Card APIs") {
    scenario("We will create Card with many error cases", 
      ApiEndpointAddCardForBank, 
      ApiEndpointUpdatedCardForBank,
      ApiEndpointGetCardForBank,
      ApiEndpointGetCardsForBank, 
      ApiEndpointDeleteCardForBank, 
      VersionOfApi
    ) {
      Given("The test bank and test account")
      val testBank = testBankId1
      val testAccount = testAccountId1
      val dummyCard = createPhysicalCardJsonV310
      
      And("We need to prepare the Customer Info")

      Then("We prepare the Customer data")
      val request310 = (v3_1_0_Request / "banks" / testBankId1.value / "customers").POST <@(user1)
      val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310
      Entitlement.entitlement.vend.addEntitlement(testBank.value, resourceUser1.userId, CanCreateCustomer.toString)
      val responseCustomer310 = makePostRequest(request310, write(postCustomerJson))
      val customerId = responseCustomer310.body.extract[CustomerJsonV310].customer_id
      
      val properCardJson = dummyCard.copy(account_id = testAccount.value, issue_number = "123", customer_id = customerId)

      val requestAnonymous = (v3_1_0_Request / "management"/"banks" / testBank.value / "cards" ).POST 
      val requestWithAuthUser = (v3_1_0_Request / "management" /"banks" / testBank.value / "cards" ).POST <@ (user1)

      Then(s"We test with anonymous user.")
      val responseAnonymous = makePostRequest(requestAnonymous, write(properCardJson))
      And(s"We should get  401 and get the authentication error")
      responseAnonymous.code should equal(401)
      responseAnonymous.body.toString contains(s"$UserNotLoggedIn") should be (true)


      Then(s"We call the authentication user, but totally wrong Json format.")
      val wrongPostJsonFormat = testBankId1
      val responseWrongJsonFormat = makePostRequest(requestWithAuthUser, write(wrongPostJsonFormat))
      And(s"We should get 400 and get the error message: $InvalidJsonFormat.")
      responseWrongJsonFormat.code should equal(400)
      responseWrongJsonFormat.body.toString contains(s"$InvalidJsonFormat")

      Then(s"We call the authentication user, but wrong card.allows value")
      val withWrongVlaueForAllows = properCardJson.copy(allows = List("123"))
      val responseWithWrongVlaueForAllows = makePostRequest(requestWithAuthUser, write(withWrongVlaueForAllows))
      And(s"We should get 400 and get the error message")
      responseWithWrongVlaueForAllows.code should equal(400)
      responseWithWrongVlaueForAllows.body.toString contains(AllowedValuesAre++ CardAction.availableValues.mkString(", "))

      Then(s"We call the authentication user, but wrong card.replacement value")
      val wrongCardReplacementReasonJson = dummyCard.copy(replacement = Some(ReplacementJSON(new Date(),"Wrong"))) // The replacement must be Enum of `CardReplacementReason` 
      val responseWrongCardReplacementReasonJson = makePostRequest(requestWithAuthUser, write(wrongCardReplacementReasonJson))
      And(s"We should get 400 and get the error message")
      responseWrongCardReplacementReasonJson.code should equal(400)
      responseWrongCardReplacementReasonJson.body.toString contains(AllowedValuesAre + CardReplacementReason.availableValues.mkString(", "))

      Then(s"We call the authentication user, but too long issue number.")
      val properCardJsonTooLongIssueNumber = dummyCard.copy(account_id = testAccount.value, issue_number = "01234567891")
      val responseUserWrongIssueNumber = makePostRequest(requestWithAuthUser, write(properCardJsonTooLongIssueNumber))
      And(s"We should get  400 and the error message missing can ${ApiRole.canCreateCardsForBank} role")
      responseUserWrongIssueNumber.code should equal(400)
      responseUserWrongIssueNumber.body.toString contains(s"${maximumLimitExceeded.replace("10000", "10")}") should be (true)
      
      Then(s"We call the authentication user, but without proper role:  ${ApiRole.canCreateCardsForBank}")
      val responseUserButNoRole = makePostRequest(requestWithAuthUser, write(properCardJson))
      And(s"We should get  403 and the error message missing can ${ApiRole.canCreateCardsForBank} role")
      responseUserButNoRole.code should equal(403)
      responseUserButNoRole.body.toString contains(s"${ApiRole.canCreateCardsForBank}") should be (true)

      Then(s"We grant the user ${ApiRole.canCreateCardsForBank} role, but with the wrong AccountId")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canCreateCardsForBank.toString)
      val wrongAccountCardJson = dummyCard
      val responseHasRoleWrongAccountId = makePostRequest(requestWithAuthUser, write(wrongAccountCardJson))
      And(s"We should get 404 and get the error message: $BankAccountNotFound.")
      responseHasRoleWrongAccountId.code should equal(404)
      responseHasRoleWrongAccountId.body.toString contains(s"$BankAccountNotFound")

      Then(s"We grant the user ${ApiRole.canCreateCardsForBank} role, but wrong customerId")
      val wrongCustomerCardJson = properCardJson.copy(customer_id = "wrongId")
      val responsewrongCustomerCardJson = makePostRequest(requestWithAuthUser, write(wrongCustomerCardJson))
      And(s"We should get 400 and get the error message: $CustomerNotFoundByCustomerId.")
      responsewrongCustomerCardJson.code should equal(404)
      responsewrongCustomerCardJson.body.toString contains(s"$CustomerNotFoundByCustomerId") should be (true)
 
      Then(s"We test the success case, prepare all stuff.")
      val responseProper = makePostRequest(requestWithAuthUser, write(properCardJson))
      And("We should get 400 and get the error message: Not Found the BankAccount.")
      responseProper.code should equal(201)
      val cardJsonV31 = responseProper.body.extract[PhysicalCardJsonV310]
      cardJsonV31.card_number should be (properCardJson.card_number)
      cardJsonV31.card_type should be (properCardJson.card_type)
      cardJsonV31.name_on_card should be (properCardJson.name_on_card)
      cardJsonV31.issue_number should be (properCardJson.issue_number)
      cardJsonV31.serial_number should be (properCardJson.serial_number )
      cardJsonV31.valid_from_date should be (properCardJson.valid_from_date )
      cardJsonV31.expires_date should be (properCardJson.expires_date )
      cardJsonV31.enabled should be (properCardJson.enabled )
      cardJsonV31.cancelled should be (false)
      cardJsonV31.on_hot_list should be (false)
      cardJsonV31.technology should be (properCardJson.technology )
      cardJsonV31.networks should be (properCardJson.networks )
      cardJsonV31.allows should be (properCardJson.allows )
      cardJsonV31.account.id should be (properCardJson.account_id )
      cardJsonV31.replacement should be {
        properCardJson.replacement match {
          case Some(x) => x
          case None => null
        }
      }
      cardJsonV31.pin_reset.toString() should be(properCardJson.pin_reset.toString())
      cardJsonV31.collected should be {
        properCardJson.collected match {
          case Some(x) => x
          case None => null
        }
      }
      cardJsonV31.posted should be {
        properCardJson.posted match {
          case Some(x) => x
          case None => null
        }
      }
      
      

      Then(s"We create the card with same bankId, cardNumber and issueNumber")
      val responseDeplicated = makePostRequest(requestWithAuthUser, write(properCardJson))
      And("We should get 400 and get the error message: the card already exsiting .")
      responseDeplicated.code should equal(400)
      responseDeplicated.body.toString contains(s"$CardAlreadyExists") should be (true)
      
      
      Then("We test the getCards")
      val requestGetWihtWrongCustomerId = (v3_1_0_Request / "management"/"banks" / testBank.value / "cards").GET <@ user1 <<? (List(("customer_id","12323")))
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canGetCardsForBank.toString)
      val responseGetWrongCustomerId = makeGetRequest(requestGetWihtWrongCustomerId)
      And("We should get 200 and updated card data")
      responseGetWrongCustomerId.code should equal(200)
      responseGetWrongCustomerId.body.extract[PhysicalCardsJsonV310].cards.size should be (0)

      val requestGetWihtWrongAccountId = (v3_1_0_Request / "management"/"banks" / testBank.value / "cards").GET <@ user1 <<? List(("account_id","12323"))
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canGetCardsForBank.toString)
      val responseGetWihtWrongAccountId = makeGetRequest(requestGetWihtWrongAccountId)
      And("We should get 200 and updated card data")
      responseGetWihtWrongAccountId.code should equal(200)
      responseGetWihtWrongAccountId.body.extract[PhysicalCardsJsonV310].cards.size should be (0)

      val requestGetWihtProperAccountId = (v3_1_0_Request / "management"/"banks" / testBank.value / "cards").GET <@ user1 <<? List(("account_id",testAccountId1.value))
      val responseGetWihtProperAccountId = makeGetRequest(requestGetWihtProperAccountId)
      And("We should get 200 and updated card data")
      responseGetWihtProperAccountId.code should equal(200)
      responseGetWihtProperAccountId.body.extract[PhysicalCardsJsonV310].cards.size should be (1)
      responseGetWihtProperAccountId.body.extract[PhysicalCardsJsonV310].cards.head should be (cardJsonV31)

      val requestGetWihtProperCustomterId = (v3_1_0_Request / "management"/"banks" / testBank.value / "cards").GET <@ user1 <<? List(("customer_id",customerId),("account_id",testAccountId1.value))
      val responseGetWihtProperCustomterId = makeGetRequest(requestGetWihtProperCustomterId)
      And("We should get 200 and updated card data")
      responseGetWihtProperCustomterId.code should equal(200)
      responseGetWihtProperCustomterId.body.extract[PhysicalCardsJsonV310].cards.size should be (1)
      responseGetWihtProperCustomterId.body.extract[PhysicalCardsJsonV310].cards.head should be (cardJsonV31)

      Then("We test the getCard by cardId")
      val cardId = cardJsonV31.card_id
      val requestGetCard = (v3_1_0_Request / "management"/"banks" / testBank.value / "cards" / cardId).GET <@ (user1)
      val responseGetCard = makeGetRequest(requestGetCard)
      And("We should get 200 and updated card data")
      responseGetCard.code should equal(200)
      responseGetCard.body.extract[PhysicalCardWithAttributesJsonV310].bank_id should be (cardJsonV31.bank_id)
      responseGetCard.body.extract[PhysicalCardWithAttributesJsonV310].allows should be (cardJsonV31.allows)
      responseGetCard.body.extract[PhysicalCardWithAttributesJsonV310].card_number should be (cardJsonV31.card_number)

      Then("We test the update card by cardId")
      val updatedCardJson = properCardJson.copy(name_on_card = "testName1")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canUpdateCardsForBank.toString)
      val requestUpdateCard = (v3_1_0_Request / "management"/"banks" / testBank.value / "cards" / cardId).PUT <@ (user1)
      val responseUpdateCard = makePutRequest(requestUpdateCard, write(updatedCardJson))
      And("We should get 200 and updated card data")
      responseUpdateCard.code should equal(200)
      responseUpdateCard.body.extract[PhysicalCardJsonV310] should be (cardJsonV31.copy(name_on_card = "testName1"))

      Then("We test the getCard by cardId again to make sure, card has been updated.")
      val requestGetCardAfterUpdated = (v3_1_0_Request / "management"/"banks" / testBank.value / "cards" / cardId).GET <@ (user1)
      val responseGetCardAfterUpdated = makeGetRequest(requestGetCardAfterUpdated)
      And("We should get 200 and updated card data")
      responseGetCardAfterUpdated.code should equal(200)
      responseGetCardAfterUpdated.body.extract[PhysicalCardWithAttributesJsonV310].card_type should be (cardJsonV31.copy(name_on_card = "testName1").card_type)
      responseGetCardAfterUpdated.body.extract[PhysicalCardWithAttributesJsonV310].card_number should be (cardJsonV31.copy(name_on_card = "testName1").card_number)
      responseGetCardAfterUpdated.body.extract[PhysicalCardWithAttributesJsonV310].card_type should be (cardJsonV31.copy(name_on_card = "testName1").card_type)
      
      
      Then("We test the delete card by cardId")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canDeleteCardsForBank.toString)
      val requestDeleteCard = (v3_1_0_Request / "management"/"banks" / testBank.value / "cards" / cardId).DELETE <@ (user1)
      val responseDeleteCard = makeDeleteRequest(requestDeleteCard)
      And("We should get 204 and card has been delete")
      responseDeleteCard.code should equal(204)

      Then("We test the getCard by cardId again to make sure, card has been deleted.")
      val requestGetCardAfterDelete = (v3_1_0_Request / "management"/"banks" / testBank.value / "cards" / cardId).GET <@ (user1)
      val responseGetCardAfterDelete = makeGetRequest(requestGetCardAfterDelete)
      And("We could not find the card and get proper error ")
      responseGetCardAfterDelete.code should equal(400)
      responseGetCardAfterDelete.body.toString contains(s"$CardNotFound")

      Then("We test the getCards again to make sure card has been deleted")
      val responseGetCardsAfterdelete = makeGetRequest(requestGetWihtProperCustomterId)
      And("We should get 200 and updated card data")
      responseGetCardsAfterdelete.code should equal(200)
      responseGetCardsAfterdelete.body.extract[PhysicalCardsJsonV310].cards.size should be (0)

      
    }
  }

} 
