package code.api.v4_0_0

import code.api.Constant.SYSTEM_OWNER_VIEW_ID
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, ErrorMessage, ViewId, ViewIdBankIdAccountId}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class CounterpartyTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createCounterpartyForAnyAccount))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getCounterpartyByNameForAnyAccount))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getCounterpartyByIdForAnyAccount))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.deleteCounterpartyForAnyAccount))
  object ApiEndpoint9 extends Tag(nameOf(Implementations4_0_0.getCounterpartiesForAnyAccount))
  
  
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.createExplicitCounterparty))
  object ApiEndpoint6 extends Tag(nameOf(Implementations4_0_0.getExplictCounterpartyById))
  object ApiEndpoint7 extends Tag(nameOf(Implementations4_0_0.deleteExplicitCounterparty))
  object ApiEndpoint8 extends Tag(nameOf(Implementations4_0_0.getExplictCounterpartiesForAccount))


  feature(s" test manage counterparties endpoints.") {

    scenario(s"Successful Case $ApiEndpoint1 + $ApiEndpoint2 +$ApiEndpoint3+$ApiEndpoint4 + $ApiEndpoint9") {

      Given("The user owner access and BankAccount")
      val bankId = testBankId1
      val accountId = testAccountId1
      val viewId =ViewId(SYSTEM_OWNER_VIEW_ID)

      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJson400.copy(other_bank_routing_address=bankId.value,other_account_routing_address=accountId.value)
      val canCreateCounterpartyAtAnyBank = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCounterpartyAtAnyBank.toString)
      val canGetCounterpartyAtAnyBank = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCounterpartyAtAnyBank.toString)
      val canDeleteCounterpartyAtAnyBank = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteCounterpartyAtAnyBank.toString)
      val canDeleteCounterpartiesAtAnyBank = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCounterpartiesAtAnyBank.toString)

      When(s"We make the request $ApiEndpoint1")
      val requestPost = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)
      val counterpartyWithMetadataJson = responsePost.body.extract[CounterpartyWithMetadataJson400]
      counterpartyWithMetadataJson.other_account_routing_address should equal(counterpartyPostJSON.other_account_routing_address)
      counterpartyWithMetadataJson.other_account_secondary_routing_scheme should equal(counterpartyPostJSON.other_account_secondary_routing_scheme)

      val counterpartyId = counterpartyWithMetadataJson.counterparty_id
      val counterpartyName = counterpartyWithMetadataJson.name

      Then(s"we can test the $ApiEndpoint2")
      val requestGet = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparty-names" / counterpartyName ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      Then("We should get a 200 and check all the fields")
      responseGet.code should equal(200)

      val counterpartyWithMetadataJsonGet = responseGet.body.extract[CounterpartyWithMetadataJson400]

      counterpartyWithMetadataJsonGet.other_account_routing_address should equal(counterpartyPostJSON.other_account_routing_address)
      counterpartyWithMetadataJsonGet.counterparty_id should equal(counterpartyId)

      {
        Then(s"we can test the $ApiEndpoint3")
        val requestGet = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / counterpartyId ).GET <@ (user1)
        val responseGet = makeGetRequest(requestGet)

        Then("We should get a 200 and check all the fields")
        responseGet.code should equal(200)

        val counterpartyWithMetadataJsonGet = responseGet.body.extract[CounterpartyWithMetadataJson400]

        counterpartyWithMetadataJsonGet.other_account_routing_address should equal(counterpartyPostJSON.other_account_routing_address)
        counterpartyWithMetadataJsonGet.counterparty_id should equal(counterpartyId)
      }

      {
        Then(s"we can test the $ApiEndpoint9")
        val requestGet = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).GET <@ (user1)
        val responseGet = makeGetRequest(requestGet)

        Then("We should get a 200 and check all the fields")
        responseGet.code should equal(200)

        val counterpartiesWithMetadataJsonGet = responseGet.body.extract[CounterpartiesJson400]

        (counterpartiesWithMetadataJsonGet.counterparties.length > 0 ) should be (true)
        counterpartiesWithMetadataJsonGet.counterparties.head.other_account_routing_address should equal(counterpartyPostJSON.other_account_routing_address)
        counterpartiesWithMetadataJsonGet.counterparties.head.counterparty_id should equal(counterpartyId)
      }

      Then(s"we can test the $ApiEndpoint4")
      val requestDelete = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / counterpartyId ).DELETE <@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)

      Then("We should get a 200 and check all the fields")
      responseDelete.code should equal(200)

      {
        Then(s"after delete, we try to get it again, it should show error")
        val requestGet = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparty-names" / counterpartyName ).GET <@ (user1)
        val responseGet = makeGetRequest(requestGet)

        Then("We should get a 200 and check all the fields")
        responseGet.code should equal(400)

        {
          val requestGet = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / counterpartyId ).GET <@ (user1)
          val responseGet = makeGetRequest(requestGet)

          Then("We should get a 200 and check all the fields")
          responseGet.code should equal(400)
        }
      }

    }

    scenario("Successful Case - no mapping account in counterparty body") {

      Given("The user owner access and BankAccount")
      val bankId = testBankId1
      val accountId = testAccountId1
      val viewId =ViewId(SYSTEM_OWNER_VIEW_ID)


      //This will use a non existing obp account, and it should also work 
      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJson400.copy(other_bank_routing_scheme = "xx")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCounterpartyAtAnyBank.toString)

      When("We make the request Create counterparty for an account")
      val requestPost = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)
      val counterpartyWithMetadataJson = responsePost.body.extract[CounterpartyWithMetadataJson400]
      counterpartyWithMetadataJson.other_account_routing_address should equal(counterpartyPostJSON.other_account_routing_address)
      counterpartyWithMetadataJson.other_account_secondary_routing_scheme should equal(counterpartyPostJSON.other_account_secondary_routing_scheme)

      val counterpartyId = counterpartyWithMetadataJson.counterparty_id

      Then("we can test the `Get Counterparty by Id, endpoint`")
      val requestGet = (v4_0_0_Request / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / counterpartyId ).POST <@ (user1)
      val responseGet = makeGetRequest(requestGet)


      Then("We should get a 200 and check all the fields")
      responseGet.code should equal(200)

      val counterpartyWithMetadataJsonGet = responseGet.body.extract[CounterpartyWithMetadataJson400]

      counterpartyWithMetadataJsonGet.other_account_routing_address should equal(counterpartyPostJSON.other_account_routing_address)


      counterpartyWithMetadataJsonGet.counterparty_id should equal(counterpartyId)

    }

    scenario(s"Error - Missing Roles") {
      Given("The user, but no role")

      val bankId = testBankId1
      val accountId = testAccountId1
      val viewId =ViewId(SYSTEM_OWNER_VIEW_ID)

      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJson400.copy(other_bank_routing_address=bankId.value,other_account_routing_address=accountId.value)

      val requestPost = (v4_0_0_Request / "management" /"banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))
      Then("We should get a 403")
      responsePost.code should equal(403)

      responsePost.body.extract[ErrorMessage].message contains (CanCreateCounterpartyAtAnyBank.toString()) should be (true)
      responsePost.body.extract[ErrorMessage].message contains (CanCreateCounterparty.toString()) should be (true)

      Then(s"we grant one role $CanCreateCounterpartyAtAnyBank should work")
      val canCreateCounterparty = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCounterpartyAtAnyBank.toString)
      val responsePost2 = makePostRequest(requestPost, write(counterpartyPostJSON))
      Then("We should get a 201")
      responsePost2.code should equal(201)

      {
        Entitlement.entitlement.vend.deleteEntitlement(canCreateCounterparty)
        val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))
        responsePost.code should equal(403)
      }

      {
        Then(s"we grant one role $CanCreateCounterparty should work")
        val canCreateCounterparty = Entitlement.entitlement.vend.addEntitlement(bankId.value, resourceUser1.userId, CanCreateCounterparty.toString)
        val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON.copy(name ="new")))
        Then("We should get a 201")
        responsePost.code should equal(201)
        Entitlement.entitlement.vend.deleteEntitlement(canCreateCounterparty)

        {
          val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))
          responsePost.code should equal(403)
        }
      }

      {
        Then(s"we can test the $ApiEndpoint2")
        val requestGet = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparty-names" / "abc" ).GET <@ (user1)
        val responseGet = makeGetRequest(requestGet)
        responseGet.code should equal(403)
      }
      { 
        Then(s"we can test the $ApiEndpoint3")
        val requestGet = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / "x" ).GET <@ (user1)
        val responseGet = makeGetRequest(requestGet)

        Then("We should get a 403 and check all the fields")
        responseGet.code should equal(403)
      }

      {
        Then(s"we can test the $ApiEndpoint9")
        val requestGet = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).GET <@ (user1)
        val responseGet = makeGetRequest(requestGet)
        responseGet.code should equal(403)
      }

      Then(s"we can test the $ApiEndpoint4")
      val requestDelete = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / "x" ).DELETE <@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      responseDelete.code should equal(403)
    }

    scenario("No BankAccount in Database") {
      Given("The user, but no BankAccount")

      val testBank = createBank("transactions-test-bank")
      val bankId = testBank.bankId
      val accountId = AccountId("notExistingAccountId")
      val viewId =ViewId(SYSTEM_OWNER_VIEW_ID)

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCounterpartyAtAnyBank.toString)

      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJson400.copy(other_bank_routing_address=bankId.value,other_account_routing_address=accountId.value)

      val requestPost = (v4_0_0_Request / "management" /"banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))
      Then("We should get a 404")
      responsePost.code should equal(404)

      responsePost.body.extract[ErrorMessage].message should startWith(ErrorMessages.BankAccountNotFound)
    }

    scenario("counterparty is not unique for name/bank_id/account_id/view_id") {
      Given("The user owner access and BankAccount")
      val bankId = testBankId1
      val accountId = testAccountId1
      val viewId =ViewId(SYSTEM_OWNER_VIEW_ID)

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCounterpartyAtAnyBank.toString)
      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJson400.copy(other_bank_routing_address=bankId.value,other_account_routing_address=accountId.value)

      When("We make the request Create counterparty for an account")
      val requestPost = (v4_0_0_Request /"management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      var responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We make the request again, the same name/bank_id/account_id/view_id")
      responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We should get a 400 and check the error massage")
      responsePost.code should equal(400)

      responsePost.body.extract[ErrorMessage].message should startWith("OBP-30014")
    }
  }

  feature(s"test account level counterparties.") {

    scenario(s"Successful Case $ApiEndpoint5 + $ApiEndpoint6 +$ApiEndpoint7+$ApiEndpoint8") {

      Given("The user owner access and BankAccount")
      val bankId = testBankId1
      val accountId = testAccountId1
      val viewId =ViewId(SYSTEM_OWNER_VIEW_ID)

      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJson400.copy(other_bank_routing_address=bankId.value,other_account_routing_address=accountId.value)

      When(s"We make the request $ApiEndpoint5")
      val requestPost = (v4_0_0_Request /  "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)
      val counterpartyWithMetadataJson = responsePost.body.extract[CounterpartyWithMetadataJson400]
      counterpartyWithMetadataJson.other_account_routing_address should equal(counterpartyPostJSON.other_account_routing_address)
      counterpartyWithMetadataJson.other_account_secondary_routing_scheme should equal(counterpartyPostJSON.other_account_secondary_routing_scheme)

      val counterpartyId = counterpartyWithMetadataJson.counterparty_id

      Then(s"we can test the $ApiEndpoint6")
      val requestGet = (v4_0_0_Request /  "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / counterpartyId ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      Then("We should get a 200 and check all the fields")
      responseGet.code should equal(200)

      val counterpartyWithMetadataJsonGet = responseGet.body.extract[CounterpartyWithMetadataJson400]

      counterpartyWithMetadataJsonGet.other_account_routing_address should equal(counterpartyPostJSON.other_account_routing_address)
      counterpartyWithMetadataJsonGet.counterparty_id should equal(counterpartyId)

      {
        Then(s"we can test the $ApiEndpoint8")
        val requestGet = (v4_0_0_Request /  "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).GET <@ (user1)
        val responseGet = makeGetRequest(requestGet)

        Then("We should get a 200 and check all the fields")
        responseGet.code should equal(200)

        val counterpartiesWithMetadataJsonGet = responseGet.body.extract[CounterpartiesJson400]

        (counterpartiesWithMetadataJsonGet.counterparties.length > 0 ) should be (true)
        counterpartiesWithMetadataJsonGet.counterparties.head.other_account_routing_address should equal(counterpartyPostJSON.other_account_routing_address)
        counterpartiesWithMetadataJsonGet.counterparties.head.counterparty_id should equal(counterpartyId)
      }

      Then(s"we can test the $ApiEndpoint7")
      val requestDelete = (v4_0_0_Request /  "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / counterpartyId ).DELETE <@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)

      Then("We should get a 200 and check all the fields")
      responseDelete.code should equal(200)

      {
        val requestGet = (v4_0_0_Request /  "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / counterpartyId ).GET <@ (user1)
        val responseGet = makeGetRequest(requestGet)

        Then("We should get a 200 and check all the fields")
        responseGet.code should equal(400)
      }
    }
    scenario(s"no view permissions") {

      Given("The user owner access and BankAccount")
      val bankId = testBankId1
      val accountId = testAccountId1
      val customRandomView = createCustomRandomView(bankId, accountId)
      Views.views.vend.grantAccessToCustomView(customRandomView.uid, resourceUser1)
      val viewId = customRandomView.viewId

      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJson400.copy(other_bank_routing_address=bankId.value,other_account_routing_address=accountId.value)

      When(s"We make the request $ApiEndpoint5")
      val requestPost = (v4_0_0_Request /  "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We should get a 403 and missing the can_add_counterparty permission ")
      responsePost.code should equal(403)
      responsePost.body.toString contains(s"${ErrorMessages.NoViewPermission}")
      responsePost.body.toString contains("can_add_counterparty")
 

      Then(s"we can test the $ApiEndpoint6")
      val requestGet = (v4_0_0_Request /  "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / "xxx" ).GET <@ (user1)
      responsePost.code should equal(403)
      responsePost.body.toString contains(s"${ErrorMessages.NoViewPermission}")
      responsePost.body.toString contains("can_get_counterparty")

      {
        Then(s"we can test the $ApiEndpoint8")
        val requestGet = (v4_0_0_Request /  "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).GET <@ (user1)
        val responseGet = makeGetRequest(requestGet)
        responseGet.code should equal(403)
        responseGet.body.toString contains(s"${ErrorMessages.NoViewPermission}")
        responseGet.body.toString contains("can_get_counterparties")
      }

      Then(s"we can test the $ApiEndpoint7")
      val requestDelete = (v4_0_0_Request /  "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / "xxx" ).DELETE <@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      responseDelete.code should equal(403)
      responseDelete.body.toString contains(s"${ErrorMessages.NoViewPermission}")
      responseDelete.body.toString contains("can_delete_counterparty")
    
    }

  }

}
