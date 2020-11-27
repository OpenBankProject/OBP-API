package code.api.v4_0_0

import code.api.Constant.SYSTEM_OWNER_VIEW_ID
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateCounterpartyAtAnyBank, CanGetCounterpartyAtAnyBank}
import code.api.util.ErrorMessages
import code.api.v2_2_0.CounterpartyWithMetadataJson
import code.api.v4_0_0.OBPAPI4_0_0.{Implementations2_2_0, Implementations4_0_0}
import code.entitlement.Entitlement
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, ErrorMessage, ViewId, ViewIdBankIdAccountId}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.JsonAST.JString
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
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.getExplictCounterpartyById))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getCounterpartyByNameForAnyAccount))


  feature(s"$ApiEndpoint1") {

    scenario("Successful Case") {

      Given("The user owner access and BankAccount")
      val bankId = testBankId1
      val accountId = testAccountId1
      val viewId =ViewId(SYSTEM_OWNER_VIEW_ID)


      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJson400.copy(other_bank_routing_address=bankId.value,other_account_routing_address=accountId.value)
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

    scenario(s"Error - No Role $CanCreateCounterpartyAtAnyBank") {
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

  feature(s"$ApiEndpoint3") {

    scenario("Successful Case") {

      Given("The user owner access and BankAccount")
      val bankId = testBankId1
      val accountId = testAccountId1
      val viewId =ViewId(SYSTEM_OWNER_VIEW_ID)


      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJson400.copy(other_bank_routing_address=bankId.value,other_account_routing_address=accountId.value)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCounterpartyAtAnyBank.toString)

      When("We make the request Create counterparty for an account")
      val requestPost = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)
      val counterpartyWithMetadataJson = responsePost.body.extract[CounterpartyWithMetadataJson]
      counterpartyWithMetadataJson.other_account_routing_address should equal(counterpartyPostJSON.other_account_routing_address)
      counterpartyWithMetadataJson.other_account_secondary_routing_scheme should equal(counterpartyPostJSON.other_account_secondary_routing_scheme)

      val counterpartyName = counterpartyWithMetadataJson.name
      Then(s"we grant the $CanGetCounterpartyAtAnyBank role")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanGetCounterpartyAtAnyBank.toString)
      
      
      Then(s"we can test the `$ApiEndpoint3`")
      val requestGet = (v4_0_0_Request /"management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / counterpartyName ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      Then("We should get a 200 and check all the fields")
      responseGet.code should equal(200)

      val counterpartyWithMetadataJsonGet = responseGet.body.extract[CounterpartyWithMetadataJson400]

      counterpartyWithMetadataJsonGet.other_account_routing_address should equal(counterpartyPostJSON.other_account_routing_address)

      counterpartyWithMetadataJsonGet.name should equal(counterpartyName)
    }

    scenario(s"no role $CanGetCounterpartyAtAnyBank") {

      Given("The user owner access and BankAccount")
      val bankId = testBankId1
      val accountId = testAccountId1
      val viewId =ViewId(SYSTEM_OWNER_VIEW_ID)


      val counterpartyPostJSON = SwaggerDefinitionsJSON.postCounterpartyJson400.copy(other_bank_routing_address=bankId.value,other_account_routing_address=accountId.value)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateCounterpartyAtAnyBank.toString)

      When("We make the request Create counterparty for an account")
      val requestPost = (v4_0_0_Request / "management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" ).POST <@ (user1)
      val responsePost = makePostRequest(requestPost, write(counterpartyPostJSON))

      Then("We should get a 201 and check all the fields")
      responsePost.code should equal(201)
      val counterpartyWithMetadataJson = responsePost.body.extract[CounterpartyWithMetadataJson400]
      counterpartyWithMetadataJson.other_account_routing_address should equal(counterpartyPostJSON.other_account_routing_address)
      counterpartyWithMetadataJson.other_account_secondary_routing_scheme should equal(counterpartyPostJSON.other_account_secondary_routing_scheme)

      val counterpartyName = counterpartyWithMetadataJson.name


      Then(s"we can test the `$ApiEndpoint3`")
      val requestGet = (v4_0_0_Request /"management" / "banks" / bankId.value / "accounts" / accountId.value / viewId.value / "counterparties" / counterpartyName ).GET <@ (user1)
      val responseGet = makeGetRequest(requestGet)

      Then("We should get a 403 and check all the fields")
      responseGet.code should equal(403)

      responseGet.body.toString contains(s"${ErrorMessages.UserHasMissingRoles}")


    }



  }

  
}
