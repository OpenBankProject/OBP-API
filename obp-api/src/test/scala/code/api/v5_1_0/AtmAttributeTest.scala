package code.api.v5_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.{ApiRole, ErrorMessages}
import code.api.util.ErrorMessages.{AtmNotFoundByAtmId, UserHasMissingRoles}
import code.api.v5_1_0.APIMethods510.Implementations5_1_0
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class AtmAttributeTest extends V510ServerSetup with DefaultUsers {

   override def beforeAll() {
     super.beforeAll()
   }

   override def afterAll() {
     super.afterAll()
   }

  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.createAtmAttribute))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.updateAtmAttribute))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_1_0.deleteAtmAttribute))
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_1_0.getAtmAttributes))
  object ApiEndpoint5 extends Tag(nameOf(Implementations5_1_0.getAtmAttribute))

  lazy val bankId = randomBankId
  lazy val atmId = createAtmAtBank(bankId).id.getOrElse("")

  feature(s"Assuring that endpoint $ApiEndpoint1 works as expected - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint1 - Anonymous access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms" / atmId / "attributes").POST
      val responseGet = makePostRequest(requestGet, write(atmAttributeJsonV510))
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet.code should equal(401)
      responseGet.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint1 without proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms" / atmId / "attributes").POST <@ (user1)
      val responseGet = makePostRequest(requestGet, write(atmAttributeJsonV510))
      Then("We should get a 403")
      And("We should get a message: " + s"$CanCreateAtmAttribute entitlement required")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanCreateAtmAttribute)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint1 with proper role but invalid ATM - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanCreateAtmAttribute.toString)
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms" / "atmId-invalid" / "attributes").POST <@ (user1)
      val responseGet = makePostRequest(requestGet, write(atmAttributeJsonV510))
      Then("We should get a 404")
      And("We should get a message: " + s"$AtmNotFoundByAtmId")
      responseGet.code should equal(404)
      responseGet.body.extract[ErrorMessage].message should startWith(AtmNotFoundByAtmId)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint1 with proper systemm role but invalid ATM - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAttributeAtAnyBank.toString)
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms" / "atmId-invalid" / "attributes").POST <@ (user1)
      val responseGet = makePostRequest(requestGet, write(atmAttributeJsonV510))
      Then("We should get a 404")
      And("We should get a message: " + s"$AtmNotFoundByAtmId")
      responseGet.code should equal(404)
      responseGet.body.extract[ErrorMessage].message should startWith(AtmNotFoundByAtmId)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }


  feature(s"Assuring that endpoint $ApiEndpoint2 works as expected - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint2 - Anonymous access", ApiEndpoint2, VersionOfApi) {
      When("We make the request")
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms" / atmId / "attributes" / "DOES_NOT_MATTER").PUT
      val responseGet = makePutRequest(requestGet, write(atmAttributeJsonV510))
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet.code should equal(401)
      responseGet.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint2 without proper role - Authorized access", ApiEndpoint2, VersionOfApi) {
      When("We make the request")
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms" / atmId / "attributes" / "DOES_NOT_MATTER").PUT <@ (user1)
      val responseGet = makePutRequest(requestGet, write(atmAttributeJsonV510))
      Then("We should get a 403")
      And("We should get a message: " + s"$CanUpdateAtmAttribute entitlement required")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanUpdateAtmAttribute)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint2 with proper role but invalid ATM - Authorized access", ApiEndpoint2, VersionOfApi) {
      When("We make the request")
      val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanUpdateAtmAttribute.toString)
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms" / "atmId-invalid" / "attributes" / "DOES_NOT_MATTER").PUT <@ (user1)
      val responseGet = makePutRequest(requestGet, write(atmAttributeJsonV510))
      Then("We should get a 404")
      And("We should get a message: " + s"$AtmNotFoundByAtmId")
      responseGet.code should equal(404)
      responseGet.body.extract[ErrorMessage].message should startWith(AtmNotFoundByAtmId)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint2 with proper system role but invalid ATM - Authorized access", ApiEndpoint2, VersionOfApi) {
      When("We make the request")
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUpdateAtmAttributeAtAnyBank.toString)
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms" / "atmId-invalid" / "attributes" / "DOES_NOT_MATTER").PUT <@ (user1)
      val responseGet = makePutRequest(requestGet, write(atmAttributeJsonV510))
      Then("We should get a 404")
      And("We should get a message: " + s"$AtmNotFoundByAtmId")
      responseGet.code should equal(404)
      responseGet.body.extract[ErrorMessage].message should startWith(AtmNotFoundByAtmId)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }



  feature(s"Assuring that endpoint $ApiEndpoint3 works as expected - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint3 - Anonymous access", ApiEndpoint3, VersionOfApi) {
      When("We make the request")
      val request = (v5_1_0_Request / "banks" / bankId / "atms" / atmId / "attributes" / "DOES_NOT_MATTER").DELETE
      val response = makeDeleteRequest(request)
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint3 without proper role - Authorized access", ApiEndpoint3, VersionOfApi) {
      When("We make the request")
      val request = (v5_1_0_Request / "banks" / bankId / "atms" / atmId / "attributes" / "DOES_NOT_MATTER").DELETE <@ (user1)
      val response = makeDeleteRequest(request)
      Then("We should get a 403")
      And("We should get a message: " + s"$CanDeleteAtmAttribute entitlement required")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanDeleteAtmAttribute)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint3 with proper role but invalid ATM - Authorized access", ApiEndpoint3, VersionOfApi) {
      When("We make the request")
      val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanDeleteAtmAttribute.toString)
      val request = (v5_1_0_Request / "banks" / bankId / "atms" / "atmId-invalid" / "attributes" / "DOES_NOT_MATTER").DELETE <@ (user1)
      val response = makeDeleteRequest(request)
      Then("We should get a 404")
      And("We should get a message: " + s"$AtmNotFoundByAtmId")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should startWith(AtmNotFoundByAtmId)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint3 with proper system role but invalid ATM - Authorized access", ApiEndpoint3, VersionOfApi) {
      When("We make the request")
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanDeleteAtmAttributeAtAnyBank.toString)
      val request = (v5_1_0_Request / "banks" / bankId / "atms" / "atmId-invalid" / "attributes" / "DOES_NOT_MATTER").DELETE <@ (user1)
      val response = makeDeleteRequest(request)
      Then("We should get a 404")
      And("We should get a message: " + s"$AtmNotFoundByAtmId")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should startWith(AtmNotFoundByAtmId)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }

  
  feature(s"Assuring that endpoint $ApiEndpoint4 works as expected - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint4 - Anonymous access", ApiEndpoint4, VersionOfApi) {
      When("We make the request")
      val request = (v5_1_0_Request / "banks" / bankId / "atms" / atmId / "attributes").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint4 without proper role - Authorized access", ApiEndpoint4, VersionOfApi) {
      When("We make the request")
      val request = (v5_1_0_Request / "banks" / bankId / "atms" / atmId / "attributes").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403")
      And("We should get a message: " + s"$CanGetAtmAttribute entitlement required")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanGetAtmAttribute)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint4 with proper role but invalid ATM - Authorized access", ApiEndpoint4, VersionOfApi) {
      When("We make the request")
      val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanGetAtmAttribute.toString)
      val request = (v5_1_0_Request / "banks" / bankId / "atms" / "atmId-invalid" / "attributes").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 404")
      And("We should get a message: " + s"$AtmNotFoundByAtmId")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should startWith(AtmNotFoundByAtmId)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint4 with proper system role but invalid ATM - Authorized access", ApiEndpoint4, VersionOfApi) {
      When("We make the request")
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAtmAttributeAtAnyBank.toString)
      val request = (v5_1_0_Request / "banks" / bankId / "atms" / "atmId-invalid" / "attributes").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 404")
      And("We should get a message: " + s"$AtmNotFoundByAtmId")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should startWith(AtmNotFoundByAtmId)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }
  
  feature(s"Assuring that endpoint $ApiEndpoint5 works as expected - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint4 - Anonymous access", ApiEndpoint5, VersionOfApi) {
      When("We make the request")
      val request = (v5_1_0_Request / "banks" / bankId / "atms" / atmId / "attributes" / "DOES_NOT_MATTER").GET
      val response = makeGetRequest(request)
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      response.code should equal(401)
      response.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint5 without proper role - Authorized access", ApiEndpoint5, VersionOfApi) {
      When("We make the request")
      val request = (v5_1_0_Request / "banks" / bankId / "atms" / atmId / "attributes" / "DOES_NOT_MATTER").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 403")
      And("We should get a message: " + s"$CanGetAtmAttribute entitlement required")
      response.code should equal(403)
      response.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles + CanGetAtmAttribute)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint5 with proper role but invalid ATM - Authorized access", ApiEndpoint5, VersionOfApi) {
      When("We make the request")
      val entitlement = Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanGetAtmAttribute.toString)
      val request = (v5_1_0_Request / "banks" / bankId / "atms" / "atmId-invalid" / "attributes" / "DOES_NOT_MATTER").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 404")
      And("We should get a message: " + s"$AtmNotFoundByAtmId")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should startWith(AtmNotFoundByAtmId)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint5 with proper system role but invalid ATM - Authorized access", ApiEndpoint5, VersionOfApi) {
      When("We make the request")
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAtmAttributeAtAnyBank.toString)
      val request = (v5_1_0_Request / "banks" / bankId / "atms" / "atmId-invalid" / "attributes" / "DOES_NOT_MATTER").GET <@ (user1)
      val response = makeGetRequest(request)
      Then("We should get a 404")
      And("We should get a message: " + s"$AtmNotFoundByAtmId")
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should startWith(AtmNotFoundByAtmId)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }


 }