package code.api.v5_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages._
import code.api.v4_0_0.UsersJsonV400
import code.api.v5_1_0.OBPAPI5_1_0.Implementations5_1_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag


class UserAttributesTest extends V510ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v5_1_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.createNonPersonalUserAttribute))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.deleteNonPersonalUserAttribute))
  object ApiEndpoint3 extends Tag(nameOf(Implementations5_1_0.getNonPersonalUserAttributes))


  lazy val bankId = testBankId1.value
  lazy val accountId = testAccountId1.value
  lazy val batteryLevel = "BATTERY_LEVEL"
  lazy val postUserAttributeJsonV510 = SwaggerDefinitionsJSON.userAttributeJsonV510.copy(name = batteryLevel)
  lazy val putUserAttributeJsonV510 = SwaggerDefinitionsJSON.userAttributeJsonV510.copy(name = "ROLE_2")

  feature(s"test $ApiEndpoint1 $ApiEndpoint2 $ApiEndpoint3 version $VersionOfApi - Unauthorized access") {
    scenario(s"We will call the end $ApiEndpoint1  without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "users" /"testUserId"/ "non-personal" / "attributes").POST
      val response510 = makePostRequest(request510, write(postUserAttributeJsonV510))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
    
    scenario(s"We will call the  $ApiEndpoint2 without user credentials", ApiEndpoint2, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "users" /"testUserId" / "non-personal" /"attributes"/"testUserAttributeId").DELETE
      val response510 = makeDeleteRequest(request510)
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
    
    scenario(s"We will call the  $ApiEndpoint3 without user credentials", ApiEndpoint3, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "users" /"testUserId" / "non-personal" /"attributes").GET
      val response510 = makeGetRequest(request510)
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 $ApiEndpoint2 $ApiEndpoint3 version $VersionOfApi - authorized access") {
    scenario(s"We will call the $ApiEndpoint1 $ApiEndpoint2 $ApiEndpoint3 with user credentials", ApiEndpoint1, 
      ApiEndpoint2, ApiEndpoint3,VersionOfApi) {
      When("We make a request v5.1.0, we need to prepare the roles and users")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAnyUser.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateNonPersonalUserAttribute.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanDeleteNonPersonalUserAttribute.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetNonPersonalUserAttributes.toString)
      
      val requestGetUsers = (v5_1_0_Request / "users").GET <@ (user1)
      val responseGetUsers = makeGetRequest(requestGetUsers)
      val userIds = responseGetUsers.body.extract[UsersJsonV400].users.map(_.user_id)
      val userId = userIds(scala.util.Random.nextInt(userIds.size))
      
      val request510 = (v5_1_0_Request / "users"/ userId / "non-personal" /"attributes").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postUserAttributeJsonV510))
      Then("We should get a 201")
      response510.code should equal(201)
      val jsonResponse = response510.body.extract[UserAttributeResponseJsonV510]
      jsonResponse.is_personal shouldBe(false)
      jsonResponse.name shouldBe(batteryLevel)
      val userAttributeId = jsonResponse.user_attribute_id

      {
        val request510 = (v5_1_0_Request / "users" / userId / "non-personal" /"attributes").GET <@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 200")
        response510.code should equal(200)
        val jsonResponse = response510.body.extract[UserAttributesResponseJsonV510]
        jsonResponse.user_attributes.length shouldBe (1)
        jsonResponse.user_attributes.head.name shouldBe (batteryLevel)
        jsonResponse.user_attributes.head.user_attribute_id shouldBe (userAttributeId)
      }
      val requestDeleteUserAttribute = (v5_1_0_Request / "users"/ userId/"non-personal"/"attributes"/userAttributeId).DELETE <@ (user1)
      val responseDeleteUserAttribute = makeDeleteRequest(requestDeleteUserAttribute)
      Then("We should get a 204")
      responseDeleteUserAttribute.code should equal(204)

      Then("We delete it again, we should get the can not find error")
      val responseDeleteUserAttributeAgain = makeDeleteRequest(requestDeleteUserAttribute)
      Then("We should get a 400")
      responseDeleteUserAttributeAgain.code should equal(400)
      responseDeleteUserAttributeAgain.body.extract[ErrorMessage].message contains (UserAttributeNotFound) shouldBe( true)


      {
        val request510 = (v5_1_0_Request / "users" / userId / "non-personal" /"attributes").GET <@ (user1)
        val response510 = makeGetRequest(request510)
        Then("We should get a 200")
        response510.code should equal(200)
        val jsonResponse = response510.body.extract[UserAttributesResponseJsonV510]
        jsonResponse.user_attributes.length shouldBe (0)
      }
      
    }
    
    scenario(s"We will call the $ApiEndpoint1 with user credentials, but missing roles", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      When("We make a request v5.1.0, we need to prepare the roles and users")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAnyUser.toString)

      val requestGetUsers = (v5_1_0_Request / "users").GET <@ (user1)
      val responseGetUsers = makeGetRequest(requestGetUsers)
      val userIds = responseGetUsers.body.extract[UsersJsonV400].users.map(_.user_id)
      val userId = userIds(scala.util.Random.nextInt(userIds.size))

      val request510 = (v5_1_0_Request / "users" / userId / "non-personal" /"attributes").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postUserAttributeJsonV510))
      Then("We should get a 403")
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message contains (UserHasMissingRoles) shouldBe (true)
      response510.body.extract[ErrorMessage].message contains (ApiRole.CanCreateNonPersonalUserAttribute.toString()) shouldBe (true)
    }
    
    scenario(s"We will call the $ApiEndpoint2 with user credentials, but missing roles", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      When("We make a request v5.1.0, we need to prepare the roles and users")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAnyUser.toString)

      val requestGetUsers = (v5_1_0_Request / "users").GET <@ (user1)
      val responseGetUsers = makeGetRequest(requestGetUsers)
      val userIds = responseGetUsers.body.extract[UsersJsonV400].users.map(_.user_id)
      val userId = userIds(scala.util.Random.nextInt(userIds.size))

      val request510 = (v5_1_0_Request / "users" / userId / "non-personal" /"attributes" / "attributeId").DELETE <@ (user1)
      val response510 = makeDeleteRequest(request510)
      Then("We should get a 403")
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message contains (UserHasMissingRoles) shouldBe (true)
      response510.body.extract[ErrorMessage].message contains (ApiRole.CanDeleteNonPersonalUserAttribute.toString()) shouldBe (true)
    }
  
    scenario(s"We will call the $ApiEndpoint3 with user credentials, but missing roles", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      When("We make a request v5.1.0, we need to prepare the roles and users")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAnyUser.toString)

      val requestGetUsers = (v5_1_0_Request / "users").GET <@ (user1)
      val responseGetUsers = makeGetRequest(requestGetUsers)
      val userIds = responseGetUsers.body.extract[UsersJsonV400].users.map(_.user_id)
      val userId = userIds(scala.util.Random.nextInt(userIds.size))

      val request510 = (v5_1_0_Request / "users" / userId / "non-personal" /"attributes" ).GET <@ (user1)
      val response510 = makeGetRequest(request510)
      Then("We should get a 403")
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message contains (UserHasMissingRoles) shouldBe (true)
      response510.body.extract[ErrorMessage].message contains (ApiRole.CanGetNonPersonalUserAttributes.toString()) shouldBe (true)
    }
  }
  
}
