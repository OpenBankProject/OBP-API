package code.api.v5_1_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ApiRole.CanCreateUserAttribute
import code.api.util.ErrorMessages._
import code.api.v4_0_0.{UserAttributeResponseJsonV400, UsersJsonV400}
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
  object ApiEndpoint1 extends Tag(nameOf(Implementations5_1_0.createUserAttribute))
  object ApiEndpoint2 extends Tag(nameOf(Implementations5_1_0.deleteUserAttribute))


  lazy val bankId = testBankId1.value
  lazy val accountId = testAccountId1.value
  lazy val batteryLevel = "BATTERY_LEVEL"
  lazy val postUserAttributeJsonV510 = SwaggerDefinitionsJSON.userAttributeJsonV400.copy(name = batteryLevel)
  lazy val putUserAttributeJsonV510 = SwaggerDefinitionsJSON.userAttributeJsonV400.copy(name = "ROLE_2")

  

  feature(s"test $ApiEndpoint1 $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario(s"We will call the end $ApiEndpoint1  without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "users" /"testUserId"/ "attributes").POST
      val response510 = makePostRequest(request510, write(postUserAttributeJsonV510))
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
    
    scenario(s"We will call the  $ApiEndpoint2 without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v5.1.0")
      val request510 = (v5_1_0_Request / "users" /"testUserId"/ "attributes"/"testUserAttributeId").POST
      val response510 = makeDeleteRequest(request510)
      Then("We should get a 401")
      response510.code should equal(401)
      response510.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 $ApiEndpoint2 version $VersionOfApi - authorized access") {
    scenario(s"We will call the $ApiEndpoint1 $ApiEndpoint2 with user credentials", ApiEndpoint1, ApiEndpoint2,VersionOfApi) {
      When("We make a request v5.1.0, we need to prepare the roles and users")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAnyUser.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanCreateUserAttribute.toString)
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanDeleteUserAttribute.toString)
      
      val requestGetUsers = (v5_1_0_Request / "users").GET <@ (user1)
      val responseGetUsers = makeGetRequest(requestGetUsers)
      val userIds = responseGetUsers.body.extract[UsersJsonV400].users.map(_.user_id)
      val userId = userIds(scala.util.Random.nextInt(userIds.size))
      
      val request510 = (v5_1_0_Request / "users"/ userId / "attributes").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postUserAttributeJsonV510))
      Then("We should get a 201")
      response510.code should equal(201)
      val jsonResponse = response510.body.extract[UserAttributeResponseJsonV400]
      val userAttributeId = jsonResponse.user_attribute_id


      val requestDeleteUserAttribute = (v5_1_0_Request / "users"/ userId/"attributes"/userAttributeId).DELETE <@ (user1)
      val responseDeleteUserAttribute = makeDeleteRequest(requestDeleteUserAttribute)
      Then("We should get a 204")
      responseDeleteUserAttribute.code should equal(204)

      Then("We delete it again, we should get the can not find error")
      val responseDeleteUserAttributeAgain = makeDeleteRequest(requestDeleteUserAttribute)
      Then("We should get a 400")
      responseDeleteUserAttributeAgain.code should equal(400)
      responseDeleteUserAttributeAgain.body.extract[ErrorMessage].message contains (UserAttributeNotFound) shouldBe( true)
      
    }
    
    scenario(s"We will call the $ApiEndpoint1 with user credentials, but missing roles", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      When("We make a request v5.1.0, we need to prepare the roles and users")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAnyUser.toString)

      val requestGetUsers = (v5_1_0_Request / "users").GET <@ (user1)
      val responseGetUsers = makeGetRequest(requestGetUsers)
      val userIds = responseGetUsers.body.extract[UsersJsonV400].users.map(_.user_id)
      val userId = userIds(scala.util.Random.nextInt(userIds.size))

      val request510 = (v5_1_0_Request / "users" / userId / "attributes").POST <@ (user1)
      val response510 = makePostRequest(request510, write(postUserAttributeJsonV510))
      Then("We should get a 403")
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message contains (UserHasMissingRoles) shouldBe (true)
      response510.body.extract[ErrorMessage].message contains (ApiRole.CanCreateUserAttribute.toString()) shouldBe (true)
    }
    
    scenario(s"We will call the $ApiEndpoint2 with user credentials, but missing roles", ApiEndpoint1, ApiEndpoint2, VersionOfApi) {
      When("We make a request v5.1.0, we need to prepare the roles and users")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.CanGetAnyUser.toString)

      val requestGetUsers = (v5_1_0_Request / "users").GET <@ (user1)
      val responseGetUsers = makeGetRequest(requestGetUsers)
      val userIds = responseGetUsers.body.extract[UsersJsonV400].users.map(_.user_id)
      val userId = userIds(scala.util.Random.nextInt(userIds.size))

      val request510 = (v5_1_0_Request / "users" / userId / "attributes" / "attributeId").DELETE <@ (user1)
      val response510 = makeDeleteRequest(request510)
      Then("We should get a 403")
      response510.code should equal(403)
      response510.body.extract[ErrorMessage].message contains (UserHasMissingRoles) shouldBe (true)
      response510.body.extract[ErrorMessage].message contains (ApiRole.CanDeleteUserAttribute.toString()) shouldBe (true)
    }
  }
  
}
