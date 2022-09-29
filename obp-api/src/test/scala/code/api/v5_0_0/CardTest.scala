package code.api.v5_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{createPhysicalCardJsonV500}
import code.api.util.ApiRole
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanCreateCustomer
import code.api.util.ErrorMessages._
import code.api.v1_3_0.ReplacementJSON
import code.api.v3_1_0.CustomerJsonV310
import code.api.v5_0_0.APIMethods500.Implementations5_0_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{CardAction, CardReplacementReason}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import java.util.Date

class CardTest extends V500ServerSetupAsync with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v5_0_0.toString)
  object ApiEndpointAddCardForBank extends Tag(nameOf(Implementations5_0_0.addCardForBank))



  feature("test Card APIs") {
    scenario("We will create Card with many error cases", 
      ApiEndpointAddCardForBank, 
      VersionOfApi
    ) {
      Given("The test bank and test account")
      val testBank = testBankId1
      val testAccount = testAccountId1
      val dummyCard = createPhysicalCardJsonV500

      And("We need to prepare the Customer Info")

      Then("We prepare the Customer data")
      val request310 = (v5_0_0_Request / "banks" / testBankId1.value / "customers").POST <@(user1)
      val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310
      Entitlement.entitlement.vend.addEntitlement(testBank.value, resourceUser1.userId, CanCreateCustomer.toString)
      val responseCustomer310 = makePostRequest(request310, write(postCustomerJson))
      val customerId = responseCustomer310.body.extract[CustomerJsonV310].customer_id

      val properCardJson = dummyCard.copy(account_id = testAccount.value, issue_number = "123", customer_id = customerId)

      val requestAnonymous = (v5_0_0_Request / "management"/"banks" / testBank.value / "cards" ).POST 
      val requestWithAuthUser = (v5_0_0_Request / "management" /"banks" / testBank.value / "cards" ).POST <@ (user1)

      Then(s"We test with anonymous user.")
      val responseAnonymous = makePostRequest(requestAnonymous, write(properCardJson))
      And(s"We should get  401 and get the authentication error")
      responseAnonymous.code should equal(401)
      responseAnonymous.body.toString contains(s"$UserNotLoggedIn") should be (true)

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
      val cardJsonV500 = responseProper.body.extract[PhysicalCardJsonV500]
      cardJsonV500.card_number should be (properCardJson.card_number)
      cardJsonV500.card_type should be (properCardJson.card_type)
      cardJsonV500.name_on_card should be (properCardJson.name_on_card)
      cardJsonV500.issue_number should be (properCardJson.issue_number)
      cardJsonV500.serial_number should be (properCardJson.serial_number )
      cardJsonV500.valid_from_date should be (properCardJson.valid_from_date )
      cardJsonV500.expires_date should be (properCardJson.expires_date )
      cardJsonV500.enabled should be (properCardJson.enabled )
      cardJsonV500.cancelled should be (false)
      cardJsonV500.on_hot_list should be (false)
      cardJsonV500.technology should be (properCardJson.technology )
      cardJsonV500.networks should be (properCardJson.networks )
      cardJsonV500.allows should be (properCardJson.allows )
      cardJsonV500.account.id should be (properCardJson.account_id )
      cardJsonV500.replacement should be {
        properCardJson.replacement match {
          case Some(x) => x
          case None => null
        }
      }
      cardJsonV500.pin_reset.toString() should be(properCardJson.pin_reset.toString())
      cardJsonV500.collected should be {
        properCardJson.collected match {
          case Some(x) => x
          case None => null
        }
      }
      cardJsonV500.posted should be {
        properCardJson.posted match {
          case Some(x) => x
          case None => null
        }
      }

      cardJsonV500.cvv.length equals (3) should be(true)
      cardJsonV500.brand equals ("Visa") should be(true)

      Then(s"We create the card with same bankId, cardNumber and issueNumber")
      val responseDeplicated = makePostRequest(requestWithAuthUser, write(properCardJson))
      And("We should get 400 and get the error message: the card already existing .")
      responseDeplicated.code should equal(400)
      responseDeplicated.body.toString contains(s"$CardAlreadyExists") should be (true)
    }
  }

} 
