package code.api.v3_1_0


import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.createPhysicalCardJsonV310
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.CanCreateCustomer
import code.api.util.ApiRole
import code.api.v3_1_0.OBPAPI3_1_0.Implementations3_1_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class CardAttributeTest extends V310ServerSetup with DefaultUsers {

  object VersionOfApi extends Tag(ApiVersion.v3_1_0.toString)
  object ApiEndpointAddCardForBank extends Tag(nameOf(Implementations3_1_0.addCardForBank))
  object ApiEndpointDeleteCardForBank extends Tag(nameOf(Implementations3_1_0.createCardAttribute))
  
  
  feature("test Card APIs") {
    scenario("We will create Card with many error cases", 
      ApiEndpointAddCardForBank, 
      ApiEndpointDeleteCardForBank, 
      VersionOfApi
    ) {
      Given("The test bank and test account")
      val testBank = testBankId1
      val testAccount = testAccountId1
      val dummyCard = createPhysicalCardJsonV310
      
      And("We need to prepare the Customer Info")

      Then("We prepare the Customer data")
      val requestForCreateCustomer = (v3_1_0_Request / "banks" / testBankId1.value / "customers").POST <@(user1)
      val postCustomerJson = SwaggerDefinitionsJSON.postCustomerJsonV310
      Entitlement.entitlement.vend.addEntitlement(testBank.value, resourceUser1.userId, CanCreateCustomer.toString)
      val responseCustomer310 = makePostRequest(requestForCreateCustomer, write(postCustomerJson))
      val customerId = responseCustomer310.body.extract[CustomerJsonV310].customer_id
      
      val properCardJson = dummyCard.copy(account_id = testAccount.value, issue_number = "123", customer_id = customerId)

      val requestForCreateCard = (v3_1_0_Request / "management" /"banks" / testBank.value / "cards" ).POST <@ (user1)

      Then(s"We grant the user ${ApiRole.canCreateCardsForBank} role, but with the wrong AccountId")
      Entitlement.entitlement.vend.addEntitlement(testBankId1.value, resourceUser1.userId, ApiRole.canCreateCardsForBank.toString)
      
 
      Then(s"We test the success case, prepare all stuff.")
      val responseProper = makePostRequest(requestForCreateCard, write(properCardJson))
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
      
      val cardId = cardJsonV31.card_id

      Then(s"We call the create card attribute")
      val requestForCreateCardAttribute = (v3_1_0_Request / "management"/ "banks" / testBankId1.value / "cards" /cardId /"attribute").POST <@(user1)
      val properCreateCardAttributeJson = OBPAPI3_1_0.allResourceDocs.filter(_.partialFunction==Implementations3_1_0.createCardAttribute).head.exampleRequestBody.asInstanceOf[CardAttributeJson]
      val responseForCreateCardAttribute = makePostRequest(requestForCreateCardAttribute, write(properCreateCardAttributeJson))
      responseForCreateCardAttribute.code should be (201)
      responseForCreateCardAttribute.body.toString contains (properCreateCardAttributeJson.name) should be (true)
      responseForCreateCardAttribute.body.toString contains (properCreateCardAttributeJson.value) should be (true)
      responseForCreateCardAttribute.body.toString contains (properCreateCardAttributeJson.`type`) should be (true)
      
    }
  }

} 
