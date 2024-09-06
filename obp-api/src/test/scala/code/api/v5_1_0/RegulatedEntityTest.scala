package code.api.v5_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.regulatedEntityPostJsonV510
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{CanCreateRegulatedEntity, CanDeleteRegulatedEntity, CanGetSystemIntegrity}
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization._
import org.scalatest.Tag

class RegulatedEntityTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.createRegulatedEntity))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.getRegulatedEntityById))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_1_0.getRegulatedEntityById))
  object ApiEndpoint4 extends Tag(nameOf(Implementations5_1_0.deleteRegulatedEntity))
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "regulated-entities").POST
      val response510 = makePostRequest(request510, write(regulatedEntityPostJsonV510))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "regulated-entities").POST <@(user1)
      val response510 = makePostRequest(request510, write(regulatedEntityPostJsonV510))
      Then("error should be " + UserHasMissingRoles + CanCreateRegulatedEntity)
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message should be (UserHasMissingRoles + CanCreateRegulatedEntity)
    }
  }
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials and a proper entitlement", ApiEndpoint1, VersionOfApi) {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateRegulatedEntity.toString)
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "regulated-entities").POST <@ (user1)
      val response510 = makePostRequest(request510, write(regulatedEntityPostJsonV510))
      Then("We get successful response")
      response510.code should equal(201)
      response510.body.extract[RegulatedEntityJsonV510]
    }
  }

  // ApiEndpoint4 - deleteRegulatedEntity
  feature(s"test $ApiEndpoint4 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "regulated-entities" / "some id").DELETE
      val response510 = makeDeleteRequest(request510)
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }
  feature(s"test $ApiEndpoint4 version $VersionOfApi - Authorized access") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "regulated-entities" / "some id").DELETE <@ (user1)
      val response510 = makeDeleteRequest(request510)
      Then("error should be " + UserHasMissingRoles + CanDeleteRegulatedEntity)
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message should be(UserHasMissingRoles + CanDeleteRegulatedEntity)
    }
  }


  feature(s"test $ApiEndpoint1, $ApiEndpoint2, $ApiEndpoint3, $ApiEndpoint4 version $VersionOfApi - CRUD") {
    scenario("We will call the endpoint with user credentials but without a proper entitlement", ApiEndpoint1, VersionOfApi) {
      // Create a row
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanCreateRegulatedEntity.toString)
      val request510 = (v5_1_0_Request / "regulated-entities").POST <@ (user1)
      val response510 = makePostRequest(request510, write(regulatedEntityPostJsonV510))
      Then("We get successful response")
      response510.code should equal(201)
      val createdRow: RegulatedEntityJsonV510 = response510.body.extract[RegulatedEntityJsonV510]

      // Get the row by id
      val getRequest510 = (v5_1_0_Request / "regulated-entities" / createdRow.entity_id).GET
      val getResponse510 = makeGetRequest(getRequest510)
      getResponse510.code should equal(200)
      val gottenRow: RegulatedEntityJsonV510 = getResponse510.body.extract[RegulatedEntityJsonV510]

      // TRy to match responses
      createdRow should equal(gottenRow)

      // Delete the row
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, CanDeleteRegulatedEntity.toString)
      val deleteRequest510 = (v5_1_0_Request / "regulated-entities" / gottenRow.entity_id).DELETE <@ (user1)
      val deleteResponse510 = makeDeleteRequest(deleteRequest510)
      deleteResponse510.code should equal(200)


      // Get all rows
      val getAllRequest510 = (v5_1_0_Request / "regulated-entities").GET
      val getAllResponse510 = makeGetRequest(getAllRequest510)
      getAllResponse510.code should equal(200)
      val allRows = getResponse510.body.extract[RegulatedEntitiesJsonV510]
      allRows.entities.length should equal(0)
    }
  }
}
