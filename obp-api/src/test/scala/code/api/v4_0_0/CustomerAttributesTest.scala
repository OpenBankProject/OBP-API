package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole._
import code.api.util.ErrorMessages.{CustomerAttributeNotFound, UserHasMissingRoles, UserNotLoggedIn}
import code.api.util.ExampleValue.{customerAttributeValueExample,customerAttributeNameExample}
import code.api.v3_0_0.CustomerAttributeResponseJsonV300
import code.api.v3_1_0.CustomerWithAttributesJsonV310
import com.openbankproject.commons.model.ListResult
import code.api.v4_0_0.OBPAPI4_0_0.Implementations4_0_0
import code.entitlement.Entitlement
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.json.Serialization.write
import org.scalatest.Tag

import scala.collection.immutable.List

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
  object ApiEndpoint5 extends Tag(nameOf(Implementations4_0_0.getCustomersByAttributes))
  object ApiEndpoint6 extends Tag(nameOf(Implementations4_0_0.deleteCustomerAttribute))
  

  feature(s"test $ApiEndpoint1 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint1, VersionOfApi) {
      val bankId = randomBankId
      val postCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400
      val putCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name="test")
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attribute").POST
      val response400 = makePostRequest(request400, write(postCustomerAttributeJsonV400))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access- missing role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      val bankId = randomBankId
      val postCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400
      val putCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name="test")
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attribute").POST <@ (user1)
      val response400 = makePostRequest(request400, write(postCustomerAttributeJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
    }
  }

  feature(s"test $ApiEndpoint1 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint1, VersionOfApi) {
      When("We make a request v4.0.0")
      val bankId = randomBankId
      val postCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400
      val putCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name="test")
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attribute").POST <@ (user1)
      val response400 = makePostRequest(request400, write(putCustomerAttributeJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateCustomerAttributeAtOneBank.toString)

      val responseWithRole = makePostRequest(request400, write(putCustomerAttributeJsonV400))
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.extract[CustomerAttributeResponseJsonV300].name equals("test") should be (true) 
      responseWithRole.body.extract[CustomerAttributeResponseJsonV300].value equals(postCustomerAttributeJsonV400.value) should be (true)
      responseWithRole.body.extract[CustomerAttributeResponseJsonV300].`type` equals(postCustomerAttributeJsonV400.`type`) should be (true)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - Unauthorized access") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      val postCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400
      val putCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name="test")
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attributes" / "customerAttributeId").PUT
      val response400 = makePutRequest(request400, write(putCustomerAttributeJsonV400))
      Then("We should get a 401")
      response400.code should equal(401)
      response400.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access- missing role") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      val postCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400
      val putCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name="test")
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attributes" / "customerAttributeId").PUT <@ (user1)
      val response400 = makePutRequest(request400, write(putCustomerAttributeJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access - with role - should be success!") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      val postCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400
      val putCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name="test")
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attribute").POST <@ (user1)
      val response400 = makePostRequest(request400, write(putCustomerAttributeJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateCustomerAttributeAtOneBank.toString)

      val responseWithRole = makePostRequest(request400, write(putCustomerAttributeJsonV400))
      Then("We should get a 201")
      responseWithRole.code should equal(201)
      responseWithRole.body.extract[CustomerAttributeResponseJsonV300].name equals("test") should be (true)
      responseWithRole.body.extract[CustomerAttributeResponseJsonV300].value equals(postCustomerAttributeJsonV400.value) should be (true)
      responseWithRole.body.extract[CustomerAttributeResponseJsonV300].`type` equals(postCustomerAttributeJsonV400.`type`) should be (true)
    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access - with role - wrong customerAttributeId") {
    scenario("We will call the endpoint without user credentials", ApiEndpoint2, VersionOfApi) {
      val bankId = randomBankId
      val postCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400
      val putCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name="test")
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      When("We make a request v4.0.0")
      val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attributes" / "customerAttributeId").PUT <@ (user1)
      val response400 = makePutRequest(request400, write(putCustomerAttributeJsonV400))
      Then("We should get a 403")
      response400.code should equal(403)
      response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canUpdateCustomerAttributeAtOneBank.toString)

      val responseWithRole = makePutRequest(request400, write(putCustomerAttributeJsonV400))
      Then("We should get a 201")
      responseWithRole.code should equal(400)
      responseWithRole.toString contains CustomerAttributeNotFound should be (true)

    }
  }

  feature(s"test $ApiEndpoint2 version $VersionOfApi - authorized access - with role - with customerAttributeId") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint2, VersionOfApi) {

      val bankId = randomBankId
      val postCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400
      val putCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name="test")
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canUpdateCustomerAttributeAtOneBank.toString)

      Then("we create the Customer Attribute ")
      val customerAttributeId = createAndGetCustomerAttributeIdViaEndpoint(bankId:String, customerId:String, user1)

      val requestWithId = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attributes" / customerAttributeId).PUT <@ (user1)
      val responseWithId = makePutRequest(requestWithId, write(putCustomerAttributeJsonV400))

      responseWithId.body.extract[CustomerAttributeResponseJsonV300].name  equals("test") should be (true)
      responseWithId.body.extract[CustomerAttributeResponseJsonV300].value  equals(putCustomerAttributeJsonV400.value) should be (true)
      responseWithId.body.extract[CustomerAttributeResponseJsonV300].`type`  equals(putCustomerAttributeJsonV400.`type`) should be (true)
    }
  }

    feature(s"test $ApiEndpoint3 version $VersionOfApi - authorized access - with role - wrong customerAttributeId") {
      scenario("We will call the endpoint without user credentials", ApiEndpoint3, VersionOfApi) {
        val bankId = randomBankId
        val postCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400
        val putCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name="test")
        val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

        When("We make a request v4.0.0")
        Then("we create the Customer Attribute ")
        val customerAttributeId = createAndGetCustomerAttributeIdViaEndpoint(bankId:String, customerId:String, user1)


        val request400 = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attributes" ).GET <@ (user1)
        val response400 = makeGetRequest(request400)
        Then("We should get a 403")
        response400.code should equal(403)
        response400.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

        Then("We grant the role to the user1")
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanGetCustomerAttributesAtOneBank.toString)

        val responseWithRole = makeGetRequest(request400)
        Then("We should get a 200")
        responseWithRole.code should equal(200)
      }
    }

    feature(s"test $ApiEndpoint4 version $VersionOfApi - authorized access - with role - with customerAttributeId") {
      scenario("We will call the endpoint with user credentials", ApiEndpoint4, VersionOfApi) {

        val bankId = randomBankId
        val postCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400
        val putCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name="test")
        val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

        Then("we create the Customer Attribute ")
        val customerAttributeId = createAndGetCustomerAttributeIdViaEndpoint(bankId:String, customerId:String, user1)

        Then("We grant the role to the user1")
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canGetCustomerAttributeAtOneBank.toString)

        val requestWithId = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attributes" / customerAttributeId).GET <@ (user1)
        val responseWithId = makeGetRequest(requestWithId)

        responseWithId.body.extract[CustomerAttributeResponseJsonV300].name equals(postCustomerAttributeJsonV400.name) should be (true)
        responseWithId.body.extract[CustomerAttributeResponseJsonV300].value equals(postCustomerAttributeJsonV400.value) should be (true)
        responseWithId.body.extract[CustomerAttributeResponseJsonV300].`type` equals(postCustomerAttributeJsonV400.`type`) should be (true)
      }
    }
  feature(s"test $ApiEndpoint5 version $VersionOfApi ") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint5, VersionOfApi) {

      val bankId = randomBankId
      val postCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      Then("we create the Customer Attribute ")
      createAndGetCustomerAttributeIdViaEndpoint(bankId:String, customerId:String, user1)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canGetCustomer.toString)

      Then(s"We can the $ApiEndpoint5")

      val requestGetCustomersByAttributes = (v4_0_0_Request / "banks" / bankId / "customers").GET <@ (user1)
      val responseGetCustomersByAttributes = makeGetRequest(requestGetCustomersByAttributes)

      responseGetCustomersByAttributes.code should be (200)
      val response: ListResult[List[CustomerWithAttributesJsonV310]] = responseGetCustomersByAttributes.body.extract[ListResult[List[CustomerWithAttributesJsonV310]]]
      response.results.head.customer_attributes.head.name should be (customerAttributeNameExample.value)
      response.results.head.customer_attributes.head.value should be (customerAttributeValueExample.value)
    }
  }

  feature(s"test $ApiEndpoint5 version $VersionOfApi test the query parameters") {
    scenario("We will call the endpoint with user credentials", ApiEndpoint5, VersionOfApi) {

      val bankId = randomBankId
      val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

      Then("we create the Customer Attribute ")
      createAndGetCustomerAttributeIdViaEndpoint(bankId: String, customerId: String, user1)

      Then("We grant the role to the user1")
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canGetCustomer.toString)

      Then(s"We can the $ApiEndpoint5 with proper parameters")
      val requestGetCustomersByAttributesWithParameter = (v4_0_0_Request / "banks" / bankId / "customers").GET <@ (user1) <<? (List(("SPECIAL_TAX_NUMBER", "123456789")))
      val responseGetCustomersByAttributesWithParameter = makeGetRequest(requestGetCustomersByAttributesWithParameter)

      responseGetCustomersByAttributesWithParameter.code should be(200)
      val response = responseGetCustomersByAttributesWithParameter.body.extract[ListResult[List[CustomerWithAttributesJsonV310]]]
      response.results.head.customer_attributes.head.name should be(customerAttributeNameExample.value)
      response.results.head.customer_attributes.head.value should be(customerAttributeValueExample.value)

      Then(s"We can the $ApiEndpoint5 with wrong parameters")
      val requestGetCustomersByAttributesWithParameter2 = (v4_0_0_Request / "banks" / bankId / "customers").GET <@ (user1) <<? (List(("SPECIAL_TAX_NUMBER", "1234567891")))
      val responseGetCustomersByAttributesWithParameter2 = makeGetRequest(requestGetCustomersByAttributesWithParameter2)

      responseGetCustomersByAttributesWithParameter2.code should be(200)
      val response2 = responseGetCustomersByAttributesWithParameter2.body.extract[ListResult[List[CustomerWithAttributesJsonV310]]]
      response2.results.length should be(0)

      Then(s"We can the $ApiEndpoint5 with wrong parameters")
      val requestGetCustomersByAttributesWithParameter3 = (v4_0_0_Request / "banks" / bankId / "customers").GET <@ (user1) <<? (List(("SPECIAL_TAX_NUMBER1", "1234567891")))
      val responseGetCustomersByAttributesWithParameter3 = makeGetRequest(requestGetCustomersByAttributesWithParameter3)

      responseGetCustomersByAttributesWithParameter3.code should be(200)
      val response3 = responseGetCustomersByAttributesWithParameter3.body.extract[ListResult[List[CustomerWithAttributesJsonV310]]]
      response3.results.length should be(0)

      Then("we create more Customer Attribute ")
      val postCustomerAttributeJsonV4001 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name = "Tax", value = "tax123")
      val postCustomerAttributeJsonV4002 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name = "Hause", value = "1230")
      createAndGetCustomerAttributeIdViaEndpoint(bankId: String, customerId: String, user1, Some(postCustomerAttributeJsonV4001))
      createAndGetCustomerAttributeIdViaEndpoint(bankId: String, customerId: String, user1, Some(postCustomerAttributeJsonV4002))

      Then(s"We can the $ApiEndpoint5 with proper parameters")
      val requestGetCustomersByAttributesWithParameter4 = (v4_0_0_Request / "banks" / bankId / "customers").GET <@ (user1) <<? (List(("Tax", "tax123"), ("Hause", "1230")))
      val responseGetCustomersByAttributesWithParameter4 = makeGetRequest(requestGetCustomersByAttributesWithParameter4)

      responseGetCustomersByAttributesWithParameter4.code should be(200)
      val response4 = responseGetCustomersByAttributesWithParameter4.body.extract[ListResult[List[CustomerWithAttributesJsonV310]]]
      response4.results.head.customer_attributes.head.name should be(customerAttributeNameExample.value)
      response4.results.head.customer_attributes.head.value should be(customerAttributeValueExample.value)
      response4.results.length should be(2)


      Then(s"We can the $ApiEndpoint5 with proper parameters")
      val requestGetCustomersByAttributesWithParameter5 = (v4_0_0_Request / "banks" / bankId / "customers").GET <@ (user1) <<? (List(("Tax", "123tax"), ("Hause", "1230")))
      val responseGetCustomersByAttributesWithParameter5 = makeGetRequest(requestGetCustomersByAttributesWithParameter5)

      responseGetCustomersByAttributesWithParameter5.code should be(200)
      val response5 = responseGetCustomersByAttributesWithParameter5.body.extract[ListResult[List[CustomerWithAttributesJsonV310]]]
      response5.results.length should be(1)
    }
  }
  feature(s"test $ApiEndpoint6 version $VersionOfApi will enforce proper entitlements") {
      scenario("We will call the endpoint", ApiEndpoint6, VersionOfApi) {
        When("We create an attribute for later deletion")
        val bankId = randomBankId
        val putCustomerAttributeJsonV400 = SwaggerDefinitionsJSON.customerAttributeJsonV400.copy(name="test")
        val customerId = createAndGetCustomerIdViaEndpoint(bankId, user1)

        val requestCreation = (v4_0_0_Request / "banks" / bankId / "customers" / customerId / "attribute").POST <@ (user1)
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateCustomerAttributeAtOneBank.toString)

        val responseCreation = makePostRequest(requestCreation, write(putCustomerAttributeJsonV400))
        Then("We should get a 201")
        responseCreation.code should equal(201)
        val customer_attribute_id = responseCreation.body.extract[CustomerAttributeResponseJsonV300].customer_attribute_id

        When("We try to delete the customer attribute without login")
        val requestNoLogin = (v4_0_0_Request / "banks" / bankId / "customers" / "attributes" / customer_attribute_id).DELETE
        val responseNoLogin = makeDeleteRequest(requestNoLogin)
        Then("We should get a 401")
        responseNoLogin.code should equal(401)
        responseNoLogin.body.extract[ErrorMessage].message should equal(UserNotLoggedIn)

        When("We try to delete the customer attribute with login and without Role")
        val requestNoRole = (v4_0_0_Request / "banks" / bankId / "customers" / "attributes" / customer_attribute_id).DELETE <@ (user1)
        val responseNoRole = makeDeleteRequest(requestNoRole)
        Then("We should get a 403")
        responseNoRole.code should equal(403)
        responseNoRole.body.extract[ErrorMessage].message.toString contains (UserHasMissingRoles) should be (true)

        When("We try to delete the customer attribute with login and without Role")
        Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canDeleteCustomerAttributeAtOneBank.toString)
        val requestWithRole = (v4_0_0_Request / "banks" / bankId / "customers" / "attributes" / customer_attribute_id).DELETE <@ (user1)
        val responseWithRole = makeDeleteRequest(requestWithRole)
        Then("We should get a 204")
        responseWithRole.code should equal(204)

      }
    }


}
