package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.{canUpdateAtm, canUpdateAtmAtAnyBank}
import code.api.util.ErrorMessages.{$UserNotLoggedIn, UserHasMissingRoles}
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class AtmsTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.updateAtmSupportedCurrencies))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.createAtm))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.updateAtmSupportedLanguages))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.updateAtmAccessibilityFeatures))
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.updateAtmServices))
  object ApiEndpoint6 extends Tag(nameOf(Implementations4_0_0.updateAtmNotes))
  object ApiEndpoint7 extends Tag(nameOf(Implementations4_0_0.updateAtmLocationCategories))
  object ApiEndpoint8 extends Tag(nameOf(Implementations4_0_0.updateAtm))
  object ApiEndpoint9 extends Tag(nameOf(Implementations4_0_0.getAtm))
  object ApiEndpoint10 extends Tag(nameOf(Implementations4_0_0.getAtms))
  object ApiEndpoint11 extends Tag(nameOf(Implementations4_0_0.deleteAtm))

  val bankId = testBankId1;
  val postAtmJson = SwaggerDefinitionsJSON.atmJsonV400.copy(bank_id= testBankId1.value)

  feature("Test Create/Update -- error cases ") {
    scenario("Create-error cases", ApiEndpoint1,ApiEndpoint8, VersionOfApi) {

      When(" no authentications")
      val requestCreateAtmNoAuth = (v4_0_0_Request / "banks" /bankId.value / "atms").POST
      val responseCreateAtmNoAuth = makePostRequest(requestCreateAtmNoAuth, write(postAtmJson))
      responseCreateAtmNoAuth.code should be (401)
      responseCreateAtmNoAuth.body.extract[ErrorMessage].message should equal($UserNotLoggedIn)
      
      When(" missing roles")
      val requestCreateAtmNoRole = (v4_0_0_Request / "banks" /bankId.value / "atms").POST <@ (user1)
      val responseCreateAtmNoRole = makePostRequest(requestCreateAtmNoRole, write(postAtmJson))
      responseCreateAtmNoRole.code should be (403)
      responseCreateAtmNoRole.body.extract[ErrorMessage].message.contains(UserHasMissingRoles)
      responseCreateAtmNoRole.body.extract[ErrorMessage].message.contains(canUpdateAtm)
      responseCreateAtmNoRole.body.extract[ErrorMessage].message.contains(canUpdateAtmAtAnyBank)
    }

    scenario("Put - error cases", ApiEndpoint1,ApiEndpoint8, VersionOfApi) {
      When(" Put - no authentications")
      val requestUpdateAtmNoAuth = (v4_0_0_Request / "banks" /bankId.value / "atms"/ "xxx").PUT
      val responseCreateAtmNoAuth = makePutRequest(requestUpdateAtmNoAuth, write(postAtmJson))
      responseCreateAtmNoAuth.code should be (401)
      responseCreateAtmNoAuth.body.extract[ErrorMessage].message should equal($UserNotLoggedIn)

      When(" Put - missing roles")
      val requestUpdateAtmNoRole = (v4_0_0_Request / "banks" /bankId.value / "atms"/ "xxx").PUT <@ (user1)
      val responseUpdateAtmNoRole = makePutRequest(requestUpdateAtmNoRole, write(postAtmJson))
      responseUpdateAtmNoRole.code should be (403)
      responseUpdateAtmNoRole.body.extract[ErrorMessage].message.contains(UserHasMissingRoles)
      responseUpdateAtmNoRole.body.extract[ErrorMessage].message.contains(canUpdateAtm)
      responseUpdateAtmNoRole.body.extract[ErrorMessage].message.contains(canUpdateAtmAtAnyBank)
    }
  }
  
  feature("Test Create/Update/Get -- successful cases") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1,ApiEndpoint8, ApiEndpoint9, ApiEndpoint10, VersionOfApi) {
      When("We need to grant role and create atm")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
      val requestCreateAtm = (v4_0_0_Request / "banks" /bankId.value / "atms").POST <@ (user1)
      val responseCreateAtm = makePostRequest(requestCreateAtm, write(postAtmJson))

      val responseBodyCreateAtm = responseCreateAtm.body.extract[AtmJsonV400]
      
      responseBodyCreateAtm should be (postAtmJson)
      
      responseCreateAtm.code should be (201)
      val atmId = responseBodyCreateAtm.id.getOrElse("")

      Then("We test the Update Atm")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUpdateAtmAtAnyBank.toString)
      val update = (v4_0_0_Request / "banks" /bankId.value / "atms" / atmId ).PUT <@ (user1)
      val postAtmJsonUpdate = SwaggerDefinitionsJSON.atmJsonV400.copy(bank_id= testBankId1.value, name="TestATM")
      
      val responseUpdate  = makePutRequest(update, write(postAtmJsonUpdate))
      responseUpdate.code should equal(201)
      responseUpdate.body.extract[AtmJsonV400] should be (postAtmJsonUpdate)
      
      Then("We test the Get Atms")
      val getOne = (v4_0_0_Request / "banks" /bankId.value / "atms" / atmId ).GET <@ (user1)
      val responseGetOne  = makeGetRequest(getOne)
      responseGetOne.code should equal(200)
      responseGetOne.body.extract[AtmJsonV400] should be (postAtmJsonUpdate)
      
      Then("We test the Get Atm")
      val getAll = (v4_0_0_Request / "banks" /bankId.value / "atms").GET <@ (user1)
      val responseGetAll  = makeGetRequest(getAll)
      responseGetAll.code should equal(200)
      responseGetAll.body.extract[AtmsJsonV400].atms.head should be (postAtmJsonUpdate)
      
      Then("We test the Delete Atm")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanDeleteAtmAtAnyBank.toString)
      val deleteOne = (v4_0_0_Request / "banks" / bankId.value / "atms" / atmId ).DELETE <@ (user1)
      val responseDeleteOne = makeDeleteRequest(deleteOne)
      responseDeleteOne.code should equal(204)

      Then("We test the Get Atm")
      val responseGetOneSecondTime  = makeGetRequest(getOne)
      responseGetOneSecondTime.code should equal(404)
    }
  }

  feature("We need to first create Atm and update the supported-currencies") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1,ApiEndpoint2, VersionOfApi) {
      
      val postSupportedCurrenciesJson = SwaggerDefinitionsJSON.supportedCurrenciesJson
      When("We need to grant role and create atm")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
      val requestCreateAtm = (v4_0_0_Request / "banks" /bankId.value / "atms").POST <@ (user1)
      val responseCreateAtm = makePostRequest(requestCreateAtm, write(postAtmJson))

      
      responseCreateAtm.code should be (201)
      val atmId = responseCreateAtm.body.extract[AtmJsonV400].id.getOrElse("")
      
      val update = (v4_0_0_Request / "banks" /bankId.value / "atms" / atmId / "supported-currencies").PUT <@ (user1)
      
      val responseUpdate  = makePutRequest(update, write(postSupportedCurrenciesJson))
      responseUpdate.code should equal(201)
      responseUpdate.body.extract[AtmSupportedCurrenciesJson].supported_currencies should be (postSupportedCurrenciesJson.supported_currencies)
    }
  }
 
  feature("We need to first create Atm and update the accessibility features") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint4,ApiEndpoint2, VersionOfApi) {
      val postAccessibilityFeaturesJson = SwaggerDefinitionsJSON.accessibilityFeaturesJson

      When("We need to grant role and create atm")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
      val requestCreateAtm = (v4_0_0_Request / "banks" /bankId.value / "atms").POST <@ (user1)
      val responseCreateAtm = makePostRequest(requestCreateAtm, write(postAtmJson))


      responseCreateAtm.code should be (201)
      val atmId = responseCreateAtm.body.extract[AtmJsonV400].id.getOrElse("")

      val update = (v4_0_0_Request / "banks" /bankId.value / "atms" / atmId / "accessibility-features").PUT <@ (user1)

      val responseUpdate  = makePutRequest(update, write(postAccessibilityFeaturesJson))
      responseUpdate.code should equal(201)
      responseUpdate.body.extract[AtmAccessibilityFeaturesJson].accessibility_features should be (postAccessibilityFeaturesJson.accessibility_features)
    }
  }

  feature("We need to first create Atm and update the supported-languages") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint5, ApiEndpoint2, VersionOfApi) {
      val postSupportedLanguagesJson = SwaggerDefinitionsJSON.supportedLanguagesJson

      When("We need to grant role and create atm")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
      val requestCreateAtm = (v4_0_0_Request / "banks" /bankId.value / "atms").POST <@ (user1)
      val responseCreateAtm = makePostRequest(requestCreateAtm, write(postAtmJson))


      responseCreateAtm.code should be (201)
      val atmId = responseCreateAtm.body.extract[AtmJsonV400].id.getOrElse("")

      val update = (v4_0_0_Request / "banks" /bankId.value / "atms" / atmId / "supported-languages").PUT <@ (user1)

      val responseUpdate  = makePutRequest(update, write(postSupportedLanguagesJson))
      responseUpdate.code should equal(201)
      responseUpdate.body.extract[AtmSupportedLanguagesJson].supported_languages should be (postSupportedLanguagesJson.supported_languages)
    }
  }

  feature("We need to first create Atm and update the services") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2,ApiEndpoint3, VersionOfApi) {
      val postAtmServicesJson = SwaggerDefinitionsJSON.atmServicesJson

      When("We need to grant role and create atm")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
      val requestCreateAtm = (v4_0_0_Request / "banks" /bankId.value / "atms").POST <@ (user1)
      val responseCreateAtm = makePostRequest(requestCreateAtm, write(postAtmJson))


      responseCreateAtm.code should be (201)
      val atmId = responseCreateAtm.body.extract[AtmJsonV400].id.getOrElse("")

      val update = (v4_0_0_Request / "banks" /bankId.value / "atms" / atmId / "services").PUT <@ (user1)

      val responseUpdate  = makePutRequest(update, write(postAtmServicesJson))
      responseUpdate.code should equal(201)
      responseUpdate.body.extract[AtmServicesJsonV400].services should be (postAtmServicesJson.services)
    }
  }

  feature("We need to first create Atm and update the notes") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2,ApiEndpoint6, VersionOfApi) {
      val postAtmNotesJson = SwaggerDefinitionsJSON.atmNotesJson

      When("We need to grant role and create atm")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
      val requestCreateAtm = (v4_0_0_Request / "banks" /bankId.value / "atms").POST <@ (user1)
      val responseCreateAtm = makePostRequest(requestCreateAtm, write(postAtmJson))


      responseCreateAtm.code should be (201)
      val atmId = responseCreateAtm.body.extract[AtmJsonV400].id.getOrElse("")

      val update = (v4_0_0_Request / "banks" /bankId.value / "atms" / atmId / "notes").PUT <@ (user1)

      val responseUpdate  = makePutRequest(update, write(postAtmNotesJson))
      responseUpdate.code should equal(201)
      responseUpdate.body.extract[AtmServicesResponseJsonV400].services should be (postAtmNotesJson.notes)
    }
  }

  feature("We need to first create Atm and update the location-categories") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2,ApiEndpoint7, VersionOfApi) {
      val postAtmLocationCategoriesJson = SwaggerDefinitionsJSON.atmLocationCategoriesJsonV400

      When("We need to grant role and create atm")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
      val requestCreateAtm = (v4_0_0_Request / "banks" /bankId.value / "atms").POST <@ (user1)
      val responseCreateAtm = makePostRequest(requestCreateAtm, write(postAtmJson))


      responseCreateAtm.code should be (201)
      val atmId = responseCreateAtm.body.extract[AtmJsonV400].id.getOrElse("")

      val update = (v4_0_0_Request / "banks" /bankId.value / "atms" / atmId / "location-categories").PUT <@ (user1)

      val responseUpdate  = makePutRequest(update, write(postAtmLocationCategoriesJson))
      responseUpdate.code should equal(201)
      responseUpdate.body.extract[AtmLocationCategoriesResponseJsonV400].location_categories should be (postAtmLocationCategoriesJson.location_categories)
    }
  }
}
