package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.canCreateCustomerAttributeAtOneBank
import code.api.util.ErrorMessages.{UserHasMissingRoles, UserNotLoggedIn}
import code.api.v3_1_0.CustomerAttributeResponseJson
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

class CustomerAttributesTest extends V400ServerSetup {
  /**
    * Test tags
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude
    *
    *  This is made possible by the scalatest maven plugin
    */
  object VersionOfApi extends Tag(ApiVersion.v4_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations4_0_0.createCustomerAttribute))
  object ApiEndpoint2 extends Tag(nameOf(Implementations4_0_0.updateCustomerAttribute))
  object ApiEndpoint3 extends Tag(nameOf(Implementations4_0_0.getCustomerAttributes))
  object ApiEndpoint4 extends Tag(nameOf(Implementations4_0_0.getCustomerAttributeById))

  
  lazy val bankId = randomBankId
  lazy val postCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400
  lazy val putCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name="test")
  lazy val customerId = createAndGetCustomerId(bankId, user1)
  

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attribute").POST
      val response400 = makePostRequest(request400, write(postCustomerAttributeJsonV400))
      Then("We should get a 400")
      response400.code should equal(400)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access- missing role") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attribute").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postCustomerAttributeJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
    }
  }
  
  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attribute").POST <@ (user1)
      val response400 = makePostRequest(request400, write(putCustomerAttributeJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
      
      Then("We should get a 403")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateCustomerAttributeAtOneBank.toString)

      val responseWithRole = makePostRequest(request400, write(putCustomerAttributeJsonV400))
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.extract[CustomerAttributeResponseJson].name equals(postCustomerAttributeJsonV400.name)
      responseWithRole.body.extract[CustomerAttributeResponseJson].value equals(postCustomerAttributeJsonV400.value)
      responseWithRole.body.extract[CustomerAttributeResponseJson].`type` equals(postCustomerAttributeJsonV400.`type`)
    }
  }
 
  
}
