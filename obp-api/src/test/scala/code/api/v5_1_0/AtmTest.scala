package code.api.v5_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.{AtmNotFoundByAtmId, UserHasMissingRoles}
import code.api.util.ExampleValue.atmTypeExample
import code.api.util.{ApiRole, ErrorMessages}
import code.api.v5_1_0.APIMethods510.Implementations5_1_0
import code.atmattribute.AtmAttribute
import code.entitlement.Entitlement
import code.setup.DefaultUsers
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class AtmTest extends V510ServerSetup with DefaultUsers {

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
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.createAtm))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.updateAtm))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_1_0.getAtms))
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_1_0.getAtm))
  object ApiEndpoint5 extends Tag(nameOf(Implementations5_1_0.deleteAtm))

  lazy val bankId = randomBankId

  feature(s"Test$ApiEndpoint1 test the error cases - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint1 - Anonymous access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms").POST
      val responseGet = makePostRequest(requestGet, write(atmJsonV510))
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet.code should equal(401)
      responseGet.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
    }

    scenario(s"We try to consume endpoint $ApiEndpoint1 without proper role - Authorized access", ApiEndpoint1, VersionOfApi) {
      When("We make the request")
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms").POST <@ (user1)
      val responseGet = makePostRequest(requestGet, write(atmJsonV510))
      Then("We should get a 403")
      And("We should get a message: " + s"$canCreateAtmAtAnyBank or $canCreateAtm entitlement required")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles)
      responseGet.body.extract[ErrorMessage].message contains (canCreateAtmAtAnyBank.toString()) shouldBe (true)
      responseGet.body.extract[ErrorMessage].message contains (canCreateAtm.toString()) shouldBe (true)
    }
  }


  feature(s"Test$ApiEndpoint2 test the error cases - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint2 - Anonymous access", ApiEndpoint2, VersionOfApi) {
      When("We make the request")
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms" / "atmId" ).PUT
      val responseGet = makePutRequest(requestGet, write(atmJsonV510))
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseGet.code should equal(401)
      responseGet.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint2 without proper role - Authorized access", ApiEndpoint2, VersionOfApi) {
      When("We make the request")
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms" / "atmId" ).PUT <@ (user1)
      val responseGet = makePutRequest(requestGet, write(atmJsonV510))
      Then("We should get a 403")
      And("We should get a message: " + s"$canCreateAtmAtAnyBank or $canCreateAtm entitlement required")
      responseGet.code should equal(403)
      responseGet.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles)
      responseGet.body.extract[ErrorMessage].message contains (canUpdateAtmAtAnyBank.toString()) shouldBe (true)
      responseGet.body.extract[ErrorMessage].message contains (canUpdateAtm.toString()) shouldBe (true)
    }
    scenario(s"We try to consume endpoint $ApiEndpoint2 with proper role but invalid ATM - Authorized access", ApiEndpoint2, VersionOfApi) {
      When("We make the request")
      val entitlement = Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUpdateAtmAtAnyBank.toString)
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms" / "atmId-invalid" ).PUT <@ (user1)
      val responseGet = makePutRequest(requestGet, write(atmJsonV510))
      Then("We should get a 404")
      And("We should get a message: " + s"$AtmNotFoundByAtmId")
      responseGet.code should equal(404)
      responseGet.body.extract[ErrorMessage].message should startWith(AtmNotFoundByAtmId)
      responseGet.body.extract[ErrorMessage].message contains (AtmNotFoundByAtmId) shouldBe (true)
      Entitlement.entitlement.vend.deleteEntitlement(entitlement)
    }
  }

  feature(s"Test$ApiEndpoint3 test the error cases - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint3 - Anonymous access", ApiEndpoint3, VersionOfApi) {
      When("We make the request")
      val request = (v5_1_0_Request / "banks" / bankId / "atms").GET
      val response = makeGetRequest(request)
      Then("We should get a 200")
      response.code should equal(200)
    }
  }

  feature(s"Test$ApiEndpoint5 test the error cases - $VersionOfApi") {
    scenario(s"We try to consume endpoint $ApiEndpoint5 - Anonymous access", ApiEndpoint5, VersionOfApi) {
      When("We make the request")
      val requestDelete = (v5_1_0_Request / "banks" / bankId / "atms"/ "amtId").DELETE
      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 401")
      And("We should get a message: " + ErrorMessages.UserNotLoggedIn)
      responseDelete.code should equal(401)
      responseDelete.body.extract[ErrorMessage].message should equal(ErrorMessages.UserNotLoggedIn)
    }

    scenario(s"We try to consume endpoint $ApiEndpoint5 without proper role - Authorized access", ApiEndpoint5, VersionOfApi) {
      When("We make the request")
      val requestDelete = (v5_1_0_Request / "banks" / bankId / "atms"/"atm1").DELETE <@ (user1)
      val responseDelete = makeDeleteRequest(requestDelete)
      Then("We should get a 403")
      And("We should get a message: " + s"$canDeleteAtmAtAnyBank or $canDeleteAtm entitlement required")
      responseDelete.code should equal(403)
      responseDelete.body.extract[ErrorMessage].message should startWith(UserHasMissingRoles)
      responseDelete.body.extract[ErrorMessage].message contains (canDeleteAtmAtAnyBank.toString()) shouldBe (true)
      responseDelete.body.extract[ErrorMessage].message contains (canDeleteAtm.toString()) shouldBe (true)
    }
  }
  
  feature(s"Test$ApiEndpoint1 $ApiEndpoint2  $ApiEndpoint3  $ApiEndpoint4 $ApiEndpoint5 - $VersionOfApi") {
    scenario(s"Test the CUR methods", ApiEndpoint1, VersionOfApi) {
      When("We make the CREATE ATMs")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateAtmAtAnyBank.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanDeleteAtmAtAnyBank.toString)
      val requestCreate = (v5_1_0_Request / "banks" / bankId / "atms").POST <@ (user1)
      val responseCreate = makePostRequest(requestCreate, write(atmJsonV510.copy(
        bank_id = bankId,
        atm_type = "atm_type1",
        phone = "12345")))
      Then("We should get a 201")
      responseCreate.code should equal(201)
      responseCreate.body.extract[AtmJsonV510].atm_type shouldBe("atm_type1")
      responseCreate.body.extract[AtmJsonV510].phone shouldBe("12345")
      val atmId = responseCreate.body.extract[AtmJsonV510].id.getOrElse("")

      Then("We create three ATM attributes")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, ApiRole.CanCreateAtmAttribute.toString)
      val requestGet = (v5_1_0_Request / "banks" / bankId / "atms" / atmId / "attributes").POST <@ (user1)
      makePostRequest(requestGet, write(atmAttributeJsonV510.copy(name = "1")))
      makePostRequest(requestGet, write(atmAttributeJsonV510.copy(name = "2")))
      makePostRequest(requestGet, write(atmAttributeJsonV510.copy(name = "3")))
      
      
      Then("We Update the ATMs")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanUpdateAtmAtAnyBank.toString)
      val requestUpdate = (v5_1_0_Request / "banks" / bankId / "atms" / atmId).PUT <@ (user1)
      val responseUpdate = makePutRequest(requestUpdate, write(atmJsonV510.copy(
        bank_id = bankId,
        atm_type = "atm_type_111",
        phone = "123456")))
      Then("We should get a 201")
      responseUpdate.code should equal(201)
      responseUpdate.body.extract[AtmJsonV510].atm_type shouldBe ("atm_type_111")
      responseUpdate.body.extract[AtmJsonV510].phone shouldBe ("123456")
      
      Then("We create 2 more ATMs")
      makePostRequest(requestCreate, write(atmJsonV510.copy(
        bank_id = bankId,
        id = Some("id2"),
        atm_type = "atm_type2",
        phone = "12345-2")))
      makePostRequest(requestCreate, write(atmJsonV510.copy(
        bank_id = bankId,
        id = Some("id3"),
        atm_type = "atm_type3",
        phone = "12345-3")))
      
      Then("We Get the ATMs")
      val request = (v5_1_0_Request / "banks" / bankId / "atms").GET
      Then("We should get a 200")
      val responseGet = makeGetRequest(request)
      responseGet.code should equal(200)
      val atms = responseGet.body.extract[AtmsJsonV510].atms
      atms.length should be (3)
      atms(0).atm_type equals ("atm_type_111")
      atms(1).atm_type equals ("atm_type2")
      atms(2).atm_type equals ("atm_type3")
      val attibutes = atms.find(_.id == Some(atmId)).get.attributes.get
      attibutes.length shouldBe(3)
      attibutes(0).name equals ("1")
      attibutes(1).name equals ("2")
      attibutes(2).name equals ("3")


      Then("We Get the ATM")
      val requestOne = (v5_1_0_Request / "banks" / bankId / "atms" /atmId ).GET
      Then("We should get a 200")
      val responseOne = makeGetRequest(requestOne)
      
      responseOne.code should equal(200)
      val atm = responseOne.body.extract[AtmJsonV510]
      atm.atm_type shouldBe ("atm_type_111")
      val atmAttributes = atm.attributes.get
      atmAttributes.length shouldBe(3)
      atmAttributes(0).name equals ("1")
      atmAttributes(1).name equals ("2")
      atmAttributes(2).name equals ("3")


      Then("We Delete the ATM")
      val requestOneDelete = (v5_1_0_Request / "banks" / bankId / "atms" / atmId).DELETE<@ (user1)
      Then("We should get a 204")
      val responseOneDelete = makeDeleteRequest(requestOneDelete)
      responseOneDelete.code should equal(204)
      
      {
        Then("We Get the ATM again")
        val requestOne = (v5_1_0_Request / "banks" / bankId / "atms" / atmId).GET
        Then("We should get a 200")
        val responseOne = makeGetRequest(requestOne)

        responseOne.code should equal(404)
      }

      {
        Then("We check the atmAttributes")
        AtmAttribute.findAll().length shouldBe(0)
      }
      
    }
  }
}